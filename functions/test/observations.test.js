const test = require("node:test");
const assert = require("node:assert/strict");
const { parseObservation, analyzeObservation } = require("../observations");
const { OMNI_MODEL } = require("../analysis");
const sample = { schemaVersion: 2, target: "SINGLE", subject: "CARTON", material: "CARTON_ASEPTIC", materialEvidence: "APPEARANCE_ONLY", opening: "SEALED", interior: "NOT_VISIBLE", interiorResidue: "NONE_VISIBLE", externalResidue: "NONE_VISIBLE", risk: "NONE_OBSERVED", confidence: 0.99 };
module.exports = { sample };
test("unseen inside is unknown even with 99 percent confidence", () => {
  const r = parseObservation(JSON.stringify(sample));
  assert.equal(r.interiorResidue, "UNKNOWN");
  assert.equal(r.material, "UNKNOWN");
  assert.equal(r.contaminationScore, undefined);
});
test("contradictory sealed visible interior cannot become clean", () => {
  assert.equal(parseObservation(JSON.stringify({ ...sample, interior: "VISIBLE" })).interior, "NOT_VISIBLE");
});
test("valid JSON fences remain supported", () => {
  assert.equal(parseObservation('```JSON\n' + JSON.stringify(sample) + '\n```').subject, "CARTON");
});
test("multiple objects cannot yield one asserted material", () => {
  assert.equal(parseObservation(JSON.stringify({ ...sample, target: "MULTIPLE", materialEvidence: "MARK_VISIBLE" })).material, "UNKNOWN");
});
for (const [name, patch] of Object.entries({ mixedLanguage: { subject: "종이board" }, inventedAdvice: { washSteps: ["스트raw"] }, numericScore: { contaminationScore: 30 }, missingField: { interior: undefined }, stringConfidence: { confidence: "0.99" }, unknownVersion: { schemaVersion: 1 } })) {
  test(`invalid observation: ${name}`, () => assert.throws(() => parseObservation(JSON.stringify({ ...sample, ...patch })), { code: "data-loss" }));
}
for (const input of ["", "{}", "null", "[]", "not json"]) test(`empty or malformed observation ${input}`, () => assert.throws(() => parseObservation(input), { code: "data-loss" }));
test("real transport selects observation contract without exposing user answers to the model", async () => {
  let payload;
  const out = await analyzeObservation(Buffer.from([255,216,255,217]).toString("base64"), {
    apiKey: "unit-test-placeholder", model: OMNI_MODEL,
    fetchImpl: async (_, options) => {
      payload = JSON.parse(options.body);
      return { ok: true, status: 200, json: async () => ({ choices: [{ message: { content: JSON.stringify(sample) }, finish_reason: "stop" }] }) };
    },
  });
  assert.equal(out.subject, "CARTON");
  assert.match(payload.messages[0].content, /관찰기/);
  assert.equal(payload.model, OMNI_MODEL);
});
