"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { parseAnalysis, validateImage, analyzeWithNim, MODEL, ENDPOINT, LOCAL_GUIDANCE } = require("../analysis");
// Transport stub only: no test response is imported by the application or deployed callable.
const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0xff, 0xd9]).toString("base64");
const fixture = () => ({ itemName: "음식 용기", material: "PP 플라스틱", contaminationScore: 58,
  confidence: 0.91, decision: "WASH_THEN_RECYCLE", washSteps: ["내용물을 비우세요.", "물로 헹구세요."],
  disposalGuide: "잔여물 제거 후 거주지 플라스틱 배출 기준을 확인하세요.", reason: "용기 안쪽에 음식물이 보입니다.", warnings: [] });
const answer = value => ({ ok: true, status: 200, json: async () => ({ choices: [{ finish_reason: "stop", message: { content: JSON.stringify(value) } }] }) });
test("clean container contract", () => {
  const x = parseAnalysis(JSON.stringify({ ...fixture(), contaminationScore: 1, decision: "RECYCLE", washSteps: [] }));
  assert.equal(x.decision, "RECYCLE"); assert.ok(x.warnings.includes(LOCAL_GUIDANCE));
});
test("dirty container retains wash and disposal guidance", () => {
  const x = parseAnalysis(JSON.stringify(fixture())); assert.equal(x.washSteps.length, 2); assert.equal(x.contaminationScore, 58);
});
test("general waste contract", () => assert.equal(parseAnalysis(JSON.stringify({ ...fixture(), decision: "GENERAL_WASTE", washSteps: [] })).decision, "GENERAL_WASTE"));
test("case insensitive JSON code fences", () => assert.equal(parseAnalysis("```JSON\n" + JSON.stringify(fixture()) + "\n```").itemName, "음식 용기"));
test("low confidence suppresses a positive decision and wash advice", () => {
  const x = parseAnalysis(JSON.stringify({ ...fixture(), confidence: 0.69 }));
  assert.equal(x.decision, "UNKNOWN"); assert.deepEqual(x.washSteps, []); assert.match(x.disposalGuide, /다시 촬영/);
});
test("explicit UNKNOWN always requests retake", () => assert.equal(parseAnalysis(JSON.stringify({ ...fixture(), decision: "UNKNOWN" })).decision, "UNKNOWN"));
for (const key of Object.keys(fixture())) test(`missing required ${key} fails closed`, () => {
  const x = fixture(); delete x[key]; assert.throws(() => parseAnalysis(JSON.stringify(x)), { code: "data-loss" });
});
for (const input of ["", " ", "not JSON", "{}", "[]", "null", '{"itemName":', "prefix " + JSON.stringify(fixture())]) {
  test(`malformed response ${input.slice(0, 18)}`, () => assert.throws(() => parseAnalysis(input), { code: "data-loss" }));
}
for (const change of [{ confidence: 2 }, { confidence: "0.9" }, { contaminationScore: -1 }, { contaminationScore: 2.5 }, { contaminationScore: 101 }, { decision: "YES" }, { washSteps: [] }, { washSteps: [7] }, { warnings: "none" }, { itemName: "" }]) {
  test(`invalid schema ${JSON.stringify(change)}`, () => assert.throws(() => parseAnalysis(JSON.stringify({ ...fixture(), ...change })), { code: "data-loss" }));
}
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
  assert.equal(result.decision, "WASH_THEN_RECYCLE");
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


test("general waste never recommends unnecessary washing", () => {
 const result=parseAnalysis(JSON.stringify({...fixture(),decision:"GENERAL_WASTE"}));
 assert.deepEqual(result.washSteps,[]);
});
