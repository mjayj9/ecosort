const test = require("node:test");
const assert = require("node:assert/strict");
const { validateAnswers, question } = require("../context");

test("explicit unknown is preserved instead of being treated as empty", () => {
  assert.deepEqual(validateAnswers({ purpose: "UNKNOWN", useState: "UNKNOWN" }), { purpose: "UNKNOWN", useState: "UNKNOWN" });
});
test("initial state cannot be silently defaulted", () => {
  assert.throws(() => validateAnswers({}), { code: "invalid-argument" });
});
test("client cannot inject a decision or AI observation", () => {
  assert.throws(() => validateAnswers({ purpose: "PREVIEW", useState: "UNUSED", decision: "RECYCLE" }));
});
test("unopened answers cannot inherit empty/clean state", () => {
  assert.throws(() => validateAnswers({ purpose: "DISPOSE_NOW", useState: "UNUSED", contents: "EMPTY" }));
});
test("unknown content cannot have a clean residue answer", () => {
  assert.throws(() => validateAnswers({ purpose: "DISPOSE_NOW", useState: "USED", contents: "UNKNOWN", residue: "NONE_VISIBLE" }));
});
test("all material questions keep an explicit unknown option", () => {
  assert.equal(question("material", ["CARTON_GENERAL", "CARTON_ASEPTIC", "UNKNOWN"]).choices.length, 3);
});
