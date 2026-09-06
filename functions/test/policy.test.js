const test = require("node:test");
const assert = require("node:assert/strict");
const { decide } = require("../policy");
const photo = { schemaVersion: 2, target: "SINGLE", subject: "CARTON", material: "UNKNOWN", materialEvidence: "UNKNOWN", opening: "UNKNOWN", interior: "NOT_VISIBLE", interiorResidue: "UNKNOWN", externalResidue: "NONE_VISIBLE", risk: "NONE_OBSERVED", confidence: 0.99 };
const used = { purpose: "DISPOSE_NOW", useState: "USED", contents: "EMPTY", residue: "NONE_VISIBLE", material: "CARTON_ASEPTIC" };
const cases = [
  ["unopened soy carton preview", { opening: "OPEN", interior: "VISIBLE", interiorResidue: "VISIBLE" }, { purpose: "PREVIEW", useState: "UNUSED", material: "CARTON_ASEPTIC" }, "PREVIEW"],
  ["unopened product for disposal", { opening: "OPEN" }, { purpose: "DISPOSE_NOW", useState: "UNUSED" }, "PREPARATION"],
  ["unseen interior requires a user answer despite confidence .99", {}, { purpose: "DISPOSE_NOW", useState: "USED" }, "NEEDS_CONFIRMATION"],
  ["remaining contents", {}, { purpose: "DISPOSE_NOW", useState: "USED", contents: "PRESENT" }, "PREPARATION"],
  ["visible food residue, material verified by user", {}, { ...used, residue: "VISIBLE" }, "PREPARATION"],
  ["unidentified carton material", {}, { ...used, material: "UNKNOWN" }, "HOLD"],
  ["user explicitly does not know contents", {}, { purpose: "DISPOSE_NOW", useState: "USED", contents: "UNKNOWN" }, "HOLD"],
  ["user explicitly does not know residue", {}, { ...used, residue: "UNKNOWN" }, "HOLD"],
  ["unknown use state is not used", {}, { purpose: "DISPOSE_NOW", useState: "UNKNOWN" }, "HOLD"],
  ["unknown purpose is not dispose now", {}, { purpose: "UNKNOWN", useState: "UNUSED" }, "HOLD"],
  ["clean answer conflicts with visible residue", { interior: "VISIBLE", interiorResidue: "VISIBLE" }, used, "NEEDS_PHOTO"],
  ["hazard always blocks a preview", { risk: "SUSPECTED" }, { purpose: "PREVIEW", useState: "UNUSED", material: "CARTON_ASEPTIC" }, "HOLD"],
  ["blank image with unknown risk", { target: "NONE", risk: "UNKNOWN" }, used, "NEEDS_PHOTO"],
  ["blank image", { target: "NONE" }, used, "NEEDS_PHOTO"],
  ["multiple foreground subjects", { target: "MULTIPLE" }, used, "NEEDS_PHOTO"],
  ["unsupported dish", { subject: "OTHER" }, used, "HOLD"],
  ["a carton is never ordinary paper", {}, { ...used, material: "PAPER" }, "HOLD"],
  ["visible label conflicts with user material", { material: "CARTON_GENERAL", materialEvidence: "MARK_VISIBLE" }, used, "HOLD"],
  ["confirmed empty and no visible residue", {}, used, "READY"],
  ["low confidence still holds even with complete answers", { confidence: 0.4 }, used, "NEEDS_PHOTO"],
  ["paper is not rinsed", { subject: "PAPER_BOX", interior: "NOT_APPLICABLE" }, { purpose: "DISPOSE_NOW", useState: "USED", material: "PAPER" }, "READY"],
];
for (const [name, patch, answers, expected] of cases) test(name, () => {
  const r = decide({ ...photo, ...patch }, answers);
  assert.equal(r.status, expected);
  assert.equal(r.decision, null, "common preparation must not certify a local collection route");
  assert.equal(r.contaminationScore, null);
  assert.equal(r.confidence, undefined);
  if (r.status === "HOLD") { assert.equal(r.questions.length, 0); assert.equal(r.steps.length, 0); }
});
test("unopened user confirmation never leaks inferred internal contamination", () => {
  const r = decide({ ...photo, opening: "OPEN", interior: "VISIBLE", interiorResidue: "VISIBLE" }, { purpose: "PREVIEW", useState: "UNUSED", material: "CARTON_ASEPTIC" });
  assert.match(r.evidence.find(x => x.label === "사용 상태").value, /미개봉/);
  assert.ok(r.evidence.some(x => x.source === "LIMIT"));
  assert.ok(!r.evidence.some(x => x.label === "사진의 내부 관찰"));
});
test("unknown never loops into the same required question", () => {
  const r = decide(photo, { ...used, residue: "UNKNOWN" });
  assert.equal(r.questions.length, 0);
});
test("curated carton steps never claim paper bin acceptance", () => {
  const r = decide(photo, { ...used, residue: "VISIBLE" });
  assert.ok(r.sources.length > 0);
  assert.match(r.steps.join(" "), /일반 종이와 섞어도 된다고 가정하지 말고/);
  assert.equal(r.ruleId, "carton-preparation");
});
test("user observation can support preparation but not turn a hidden interior into an AI observation", () => {
  const r = decide(photo, used);
  assert.equal(r.status, "READY");
  assert.match(r.evidence.find(x => x.label === "사진의 내부 관찰").value, /확인할 수 없/);
  assert.equal(r.evidence.find(x => x.label === "잔여물").source, "USER");
});
