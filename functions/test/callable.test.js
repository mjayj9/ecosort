"use strict";
process.env.FUNCTIONS_EMULATOR = "true";
const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const api = require("../index");
const image = Buffer.from([255,216,255,217]).toString("base64");
const valid = () => ({ schemaVersion: 2, operation: "analyze", requestId: crypto.randomUUID(), image, answers: { purpose: "PREVIEW", useState: "UNUSED" } });
test("callable rejects unauthenticated requests", async () => {
  await assert.rejects(api.analyzeImage.run({ data: valid() }), { code: "unauthenticated" });
});
test("client-supplied API keys are rejected before provider access", async () => {
  await assert.rejects(api.analyzeImage.run({ auth: { uid: "test-user" }, data: { ...valid(), apiKey: "unit-test-placeholder" } }), { code: "invalid-argument" });
});
test("callable rejects invalid V2 image", async () => {
  await assert.rejects(api.analyzeImage.run({ auth: { uid: "test-user" }, data: { ...valid(), image: "bad" } }), { code: "invalid-argument" });
});
test("old client fails closed with update instruction", async () => {
  await assert.rejects(api.analyzeImage.run({ auth: { uid: "test-user" }, data: { image } }), { code: "failed-precondition" });
});
test("missing server key fails before Firestore or provider access", async () => {
  const before = process.env.NVIDIA_API_KEY;
  delete process.env.NVIDIA_API_KEY;
  try { await assert.rejects(api.analyzeImage.run({ auth: { uid: "test-user" }, data: valid() }), { code: "failed-precondition" }); }
  finally { if (before !== undefined) process.env.NVIDIA_API_KEY = before; }
});
test("automatic disposal reward is disabled", async () => {
  await assert.rejects(api.verifyDisposal.run({ auth: { uid: "test-user" } }), { code: "failed-precondition" });
});
test("admin grant rejects ordinary user", async () => {
  await assert.rejects(api.grantPoints.run({ auth: { uid: "test-user", token: {} }, data: {} }), { code: "permission-denied" });
});
