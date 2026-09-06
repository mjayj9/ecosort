"use strict";
const {test} = require("node:test");
const assert = require("node:assert/strict");
const {readStream, analyzeWithNim} = require("../analysis");
const fixture = {itemName:"용기", material:"PP", contaminationScore:45, confidence:0.91, decision:"WASH_THEN_RECYCLE", washSteps:["내용물을 비우고 헹구세요."], disposalGuide:"지역 플라스틱 배출 기준을 확인하세요.", reason:"잔여물이 보입니다.", warnings:[]};
const event = value => `data: ${JSON.stringify(value)}\r\n\r\n`;
const delta = text => event({choices:[{delta:{content:text},finish_reason:null}]});
const finish = event({choices:[{delta:{},finish_reason:"stop"}]}) + "data: [DONE]\n\n";
function response(text, width = 13) {
 const bytes = new TextEncoder().encode(text);
 let offset = 0;
 return new Response(new ReadableStream({pull(controller) {
  if (offset >= bytes.length) return controller.close();
  controller.enqueue(bytes.slice(offset, offset + width)); offset += width;
 }}), {headers:{"Content-Type":"text/event-stream"}});
}
test("SSE preserves split UTF-8 content and ignores reasoning", async()=>{
 const text = event({choices:[{delta:{reasoning_content:"PRIVATE_REASONING_DO_NOT_RETURN"}}]}) + delta(JSON.stringify(fixture)) + finish;
 const result = await readStream(response(text));
 assert.equal(result.itemName,"용기");
 assert.equal(result.decision,"WASH_THEN_RECYCLE");
 assert(!JSON.stringify(result).includes("PRIVATE"));
});
test("SSE truncated before DONE fails closed",async()=>assert.rejects(readStream(response(delta(JSON.stringify(fixture)))),{code:"data-loss"}));
test("SSE malformed event fails closed",async()=>assert.rejects(readStream(response("data: broken\n\n"+finish)),{code:"data-loss"}));
test("SSE reasoning-only response is not a result",async()=>assert.rejects(readStream(response(event({choices:[{delta:{reasoning_content:"ignored"}}]})+finish)),{code:"data-loss"}));
test("SSE token limit interruption fails closed",async()=>assert.rejects(readStream(response(delta(JSON.stringify(fixture))+event({choices:[{delta:{},finish_reason:"length"}]}))),{code:"data-loss"}));
test("SSE oversized answer fails closed",async()=>assert.rejects(readStream(response(delta("x".repeat(16001))+finish)),{code:"data-loss"}));
test("SSE stalled body obeys provider deadline",async()=>{
 const jpeg=Buffer.from([255,216,255,0,255,217]).toString("base64");
 await assert.rejects(analyzeWithNim(jpeg,{apiKey:"unit-test-placeholder",timeoutMs:10,fetchImpl:async(_, {signal})=>{
  return new Response(new ReadableStream({start(controller){signal.addEventListener("abort",()=>controller.error(new Error("aborted")));}}),{headers:{"Content-Type":"text/event-stream"}});
 }}), error=>error.code==="deadline-exceeded" && error.phase==="body");
});

const validJpeg=Buffer.from([255,216,255,0,255,217]).toString("base64");
test("provider stream error retries once and then returns the real next response",async()=>{
 let calls=0;
 const result=await analyzeWithNim(validJpeg,{apiKey:"unit-test-placeholder",fetchImpl:async()=>{
  calls++;return response(calls===1?event({error:{code:500,message:"PRIVATE_PROVIDER_ERROR"}}):delta(JSON.stringify(fixture))+finish);
 }});
 assert.equal(calls,2);assert.equal(result.itemName,fixture.itemName);
});
test("repeated provider stream errors stop after two attempts",async()=>{
 let calls=0;
 await assert.rejects(analyzeWithNim(validJpeg,{apiKey:"unit-test-placeholder",fetchImpl:async()=>{calls++;return response(event({error:{code:500}}));}}),{code:"unavailable"});
 assert.equal(calls,2);
});
test("provider stream rate limit never retries",async()=>{
 let calls=0;
 await assert.rejects(analyzeWithNim(validJpeg,{apiKey:"unit-test-placeholder",fetchImpl:async()=>{calls++;return response(event({error:{code:429}}));}}),{code:"resource-exhausted"});
 assert.equal(calls,1);
});
test("retry delay is cancelled by the shared deadline",async()=>{
 let calls=0;
 await assert.rejects(analyzeWithNim(validJpeg,{apiKey:"unit-test-placeholder",timeoutMs:25,fetchImpl:async()=>{calls++;return response(event({error:{code:500}}));}}),{code:"deadline-exceeded"});
 assert.equal(calls,1);
});
