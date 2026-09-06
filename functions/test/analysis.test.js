"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { validateImage, MODEL, ENDPOINT } = require("../analysis");
const { analyzeObservation: analyzeWithNim } = require("../observations");
const fixture = require("./fixtures/observation");
const jpeg = Buffer.from([255,216,255,0,255,217]).toString("base64");
const answer = value => ({ ok: true, status: 200, json: async () => ({ choices: [{ finish_reason: "stop", message: { content: JSON.stringify(value) } }] }) });
test("rejects empty, invalid, non-JPEG and oversized image payloads", () => {
  for (const image of [undefined, "", "abcd!", Buffer.from("hello").toString("base64"), "A".repeat(3 * 1024 * 1024)]) assert.throws(() => validateImage(image), { code: "invalid-argument" });
});
test("NIM request uses server-selected model and base64 image", async () => {
  const result = await analyzeWithNim(jpeg, { apiKey: "unit-test-placeholder", fetchImpl: async (url, request) => {
    assert.equal(url, ENDPOINT); assert.equal(request.headers.Authorization, "Bearer unit-test-placeholder");
    const body = JSON.parse(request.body); assert.equal(body.model, MODEL); assert.equal(body.reasoning_effort, "low");
    assert.equal(body.messages[1].content[1].image_url.url, `data:image/jpeg;base64,${jpeg}`);
    assert.equal(body.stream, true); return answer(fixture());
  } });
  assert.equal(result.subject, "PLASTIC_CONTAINER");
});
test("missing server key never calls the provider", async () => {
  await assert.rejects(analyzeWithNim(jpeg, { fetchImpl: () => assert.fail("provider should not be called") }), { code: "failed-precondition" });
});
for (const [status, code] of [[401, "failed-precondition"], [403, "failed-precondition"], [429, "resource-exhausted"], [500, "unavailable"], [422, "unavailable"], [202, "unavailable"], [504, "deadline-exceeded"]]) {
  test(`provider HTTP ${status} is sanitized`, async () => {
    await assert.rejects(analyzeWithNim(jpeg, { apiKey: "unit-test-placeholder", fetchImpl: async () => ({ status, ok: false, text: () => assert.fail("must not read provider error body") }) }), { code });
  });
}
test("network failure is sanitized", async () => {
  await assert.rejects(analyzeWithNim(jpeg, { apiKey: "unit-test-placeholder", fetchImpl: async () => { throw new Error("PRIVATE_TRANSPORT_DETAILS"); } }), error => error.code === "unavailable" && !error.message.includes("PRIVATE"));
});
test("provider timeout aborts in-flight request", async () => {
  await assert.rejects(analyzeWithNim(jpeg, { apiKey: "unit-test-placeholder", timeoutMs: 10,
    fetchImpl: (_, { signal }) => new Promise((_, reject) => signal.addEventListener("abort", () => reject(new Error("aborted")))) }), { code: "deadline-exceeded" });
});
test("response body timeout is also bounded", async () => {
  await assert.rejects(analyzeWithNim(jpeg, { apiKey: "unit-test-placeholder", timeoutMs: 10,
    fetchImpl: async (_, { signal }) => ({ ok: true, status: 200, json: () => new Promise((_, reject) => signal.addEventListener("abort", () => reject(new Error("aborted")))) }) }), { code: "deadline-exceeded" });
});
for (const data of [{}, { choices: [] }, { choices: [{ message: { content: "" } }] }, { choices: [{ finish_reason: "length", message: { content: JSON.stringify(fixture()) } }] }]) {
  test("incomplete provider envelope fails closed", async () => {
    await assert.rejects(analyzeWithNim(jpeg, { apiKey: "unit-test-placeholder", fetchImpl: async () => ({ ok: true, status: 200, json: async () => data }) }), { code: "data-loss" });
  });
}
test("non-JSON HTTP body fails closed", async () => {
  await assert.rejects(analyzeWithNim(jpeg, { apiKey: "unit-test-placeholder", fetchImpl: async () => ({ ok: true, status: 200, json: async () => { throw new Error("parse"); } }) }), { code: "data-loss" });
});


test("Nemotron uses its own reasoning budget and preserves real model identifier", async () => {
  const {OMNI_MODEL}=require("../analysis");
  await analyzeWithNim(jpeg,{apiKey:"unit-test-placeholder",model:OMNI_MODEL,fetchImpl:async (_,request)=>{
    const body=JSON.parse(request.body);assert.equal(body.model,OMNI_MODEL);
    assert.equal(body.reasoning_budget,1024);assert.equal(body.reasoning_effort,undefined);
    return answer(fixture());
  }});
});
test("unsupported server model is rejected before provider access", async () => {
 await assert.rejects(analyzeWithNim(jpeg,{apiKey:"unit-test-placeholder",model:"unknown",fetchImpl:()=>assert.fail("must not call")}),{code:"failed-precondition"});
});
