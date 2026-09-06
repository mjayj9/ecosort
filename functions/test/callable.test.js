const { test } = require("node:test");
const assert = require("node:assert/strict");
process.env.FUNCTIONS_EMULATOR = "true";
process.env.GCLOUD_PROJECT = "demo-ecosort";
delete process.env.NVIDIA_API_KEY;
const api = require("../index");
test("callable rejects unauthenticated requests", async () => {
  await assert.rejects(api.analyzeImage.run({ data: {} }), { code: "unauthenticated" });
});
test("client-supplied API keys are rejected before provider access", async () => {
  await assert.rejects(api.analyzeImage.run({ auth: { uid: "test-user" }, data: { apiKey: "unit-test-placeholder" } }), { code: "invalid-argument" });
});
test("callable rejects invalid image", async () => {
  await assert.rejects(api.analyzeImage.run({ auth: { uid: "test-user" }, data: { image: "bad" } }), { code: "invalid-argument" });
});
test("missing server key is a setup failure, never a success response", async () => {
  const image = Buffer.from([255, 216, 255, 0, 255, 217]).toString("base64");
  await assert.rejects(api.analyzeImage.run({ auth: { uid: "test-user" }, data: { image } }), { code: "failed-precondition" });
});
test("automatic disposal reward is disabled", async () => {
  await assert.rejects(api.verifyDisposal.run({ auth: { uid: "test-user" }, data: {} }), { code: "failed-precondition" });
});
test("admin grant rejects ordinary user", async () => {
  await assert.rejects(api.grantPoints.run({ auth: { uid: "test-user", token: {} }, data: { points: 50 } }), { code: "permission-denied" });
});
