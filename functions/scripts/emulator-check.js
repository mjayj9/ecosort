"use strict";
// Local Firestore integration checks. NIM responses are transport stubs, never real model evidence.
process.env.GCLOUD_PROJECT = "demo-ecosort";
process.env.FUNCTIONS_EMULATOR = "true";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
const assert = require("node:assert/strict");
const admin = require("firebase-admin");
const nativeFetch = globalThis.fetch;
const api = require("../index");
const db = admin.firestore();
const image = Buffer.from([255, 216, 255, 0, 255, 217]).toString("base64");
const responseFixture = { itemName: "test fixture", material: "PP", contaminationScore: 3, confidence: 0.9,
 decision: "RECYCLE", washSteps: [], disposalGuide: "test disposal guide", reason: "test observed feature", warnings: [] };
async function main() {
 const signUp = await nativeFetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local", {
  method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({returnSecureToken:true})});
 const {localId:uid,idToken}=await signUp.json(); assert.ok(uid && idToken);
 const docBase=`http://127.0.0.1:8080/v1/projects/demo-ecosort/databases/(default)/documents`;
 const headers={"Content-Type":"application/json",Authorization:`Bearer ${idToken}`};
 const results=[];
 const profile=await nativeFetch(`${docBase}/users/${uid}`, {method:"PATCH",headers,body:JSON.stringify({fields:{displayName:{stringValue:"emulator-test"}}})});
 assert.equal(profile.status,200); results.push("PASS: own profile write allowed");
 const points=await nativeFetch(`${docBase}/users/${uid}?updateMask.fieldPaths=points`, {method:"PATCH",headers,body:JSON.stringify({fields:{points:{integerValue:"5000"}}})});
 assert.equal(points.status,403); results.push("PASS: client cannot mint points");
 const ledger=await nativeFetch(`${docBase}/pointLedger/test-${uid}`, {method:"PATCH",headers,body:JSON.stringify({fields:{uid:{stringValue:uid},amount:{integerValue:"50"}}})});
 assert.equal(ledger.status,403); results.push("PASS: client cannot write reward ledger");
 const other=await nativeFetch(`${docBase}/users/not-${uid}`,{headers});
 assert.equal(other.status,403); results.push("PASS: other user profile read denied");
 const keyBefore=process.env.NVIDIA_API_KEY;
 process.env.NVIDIA_API_KEY="unit-test-placeholder";
 const date=new Date().toLocaleDateString("sv-SE",{timeZone:"Asia/Seoul"}).replace(/-/g,"");
 const usage=db.collection("usage").doc(`${uid}_${date}`);
 await usage.set({analyze:29});
 let providerCalls=0;
 globalThis.fetch=async url=>{
  assert.equal(url,"https://integrate.api.nvidia.com/v1/chat/completions"); providerCalls++;
  return {ok:true,status:200,json:async()=>({choices:[{finish_reason:"stop",message:{content:JSON.stringify(responseFixture)}}]})};
 };
 try {
  const attempts=await Promise.allSettled(Array.from({length:5},()=>api.analyzeImage.run({auth:{uid},data:{image}})));
  assert.equal(attempts.filter(x=>x.status==="fulfilled").length,1);
  assert.equal(attempts.filter(x=>x.status==="rejected" && x.reason.code==="resource-exhausted").length,4);
  assert.equal(providerCalls,1); assert.equal((await usage.get()).get("analyze"),30);
  const success=attempts.find(x=>x.status==="fulfilled").value;
  const scan=await db.collection("scans").doc(success.scanId).get();
  assert.deepEqual(Object.keys(scan.data()).sort(),["confidence","createdAt","decision","model","uid"]);
  results.push("PASS: concurrent requests enforce remaining quota of one");
  results.push("PASS: scan records contain no image or key");
 } finally {
  globalThis.fetch=nativeFetch;
  if(keyBefore===undefined) delete process.env.NVIDIA_API_KEY; else process.env.NVIDIA_API_KEY=keyBefore;
 }
 await db.collection("users").doc(uid).set({points:6000});
 const inventory=await db.collection("couponInventory").where("itemId","==","cu1000").where("status","==","available").limit(1).get();
 assert.equal(inventory.empty,true,"requires an empty demo coupon inventory");
 await assert.rejects(api.redeemCoupon.run({auth:{uid},data:{itemId:"cu1000"}}),{code:"failed-precondition"});
 assert.equal((await db.collection("users").doc(uid).get()).get("points"),6000);
 results.push("PASS: no coupon stock means no charge and no demo coupon");
 // Test documents are confined to demo-ecosort and are removed by the existing account lifecycle.
 await api.deleteAccount.run({auth:{uid}});
 assert.equal((await db.collection("users").doc(uid).get()).exists,false);
 assert.equal((await usage.get()).exists,false);
 results.push("PASS: account deletion removes profile and usage");
 console.log(results.join("\n"));
 console.log("NIM transport was stubbed only inside this test process.");
}
main().catch(()=>{console.error("FAIL: emulator integration assertion; inspect local test code. Credentials suppressed.");process.exitCode=1;}).finally(()=>db.terminate());
