"use strict";
// Real local Firestore transactions; the injected provider below is a TEST fixture, not AI evidence.
process.env.GCLOUD_PROJECT = "demo-ecosort";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
const admin = require("firebase-admin");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const { createScanService, SESSION_MS } = require("../scan-service");
const { OMNI_MODEL } = require("../analysis");
admin.initializeApp({ projectId: "demo-ecosort" });
const db = admin.firestore();
const uid = "phase1-test-" + crypto.randomUUID();
const paths = new Set();
const initial = { purpose: "DISPOSE_NOW", useState: "USED" };
const photo = { schemaVersion: 2, target: "SINGLE", subject: "CARTON", material: "UNKNOWN", materialEvidence: "UNKNOWN", opening: "UNKNOWN", interior: "NOT_VISIBLE", interiorResidue: "UNKNOWN", externalResidue: "NONE_VISIBLE", risk: "NONE_OBSERVED", confidence: 0.99 };
let providerCalls = 0, quotaCalls = 0, clock = Date.now(), providerFailure = false;
const service = createScanService({ db, now: () => clock, timestamp: () => admin.firestore.FieldValue.serverTimestamp(),
  enforceDailyLimit: async () => { quotaCalls++; },
  getProvider: () => ({ model: OMNI_MODEL, analyze: async () => {
    providerCalls++;
    await new Promise(resolve => setTimeout(resolve, 50));
    if (providerFailure) throw new Error("test provider failure");
    return photo;
  } }),
});
const analyzeRequest = () => ({ schemaVersion: 2, operation: "analyze", image: Buffer.from([255,216,255,217]).toString("base64"), answers: initial, requestId: crypto.randomUUID() });
const resolveRequest = (scan, answers = initial) => ({ schemaVersion: 2, operation: "resolve", scanId: scan.scanId, expectedRevision: scan.revision, answers, requestId: crypto.randomUUID() });
const report = [];
async function main() {
  const request = analyzeRequest();
  const racing = await Promise.allSettled([service.execute(uid, request), service.execute(uid, request)]);
  assert.equal(providerCalls, 1);
  const first = racing.find(x => x.status === "fulfilled").value;
  paths.add(first.scanId);
  assert.equal(first.status, "NEEDS_CONFIRMATION");
  report.push("concurrent identical analysis invokes provider once");
  assert.deepEqual(await service.execute(uid, request), first);
  assert.equal(quotaCalls, 1);
  report.push("lost initial response retry returns cached result without quota");
  await assert.rejects(service.execute(uid, { ...request, answers: { purpose: "PREVIEW", useState: "UNUSED" } }), { code: "invalid-argument" });
  report.push("request identifier cannot bind different answers");
  const confirmation = resolveRequest(first, { purpose: "PREVIEW", useState: "UNUSED", material: "CARTON_ASEPTIC" });
  const changed = await service.execute(uid, confirmation);
  assert.equal(changed.status, "PREVIEW");
  assert.equal(changed.revision, 1);
  assert.equal(providerCalls, 1); assert.equal(quotaCalls, 1);
  report.push("state correction reuses observations without provider or AI quota");
  assert.deepEqual(await service.execute(uid, confirmation), changed);
  report.push("lost confirmation response retry is idempotent");
  await assert.rejects(service.execute(uid, { ...confirmation, answers: { ...confirmation.answers, material: "UNKNOWN" } }), { code: "invalid-argument" });
  await assert.rejects(service.execute("other-user", resolveRequest(changed)), { code: "not-found" });
  report.push("request substitution and cross-user scan access rejected");
  await assert.rejects(service.execute(uid, resolveRequest(first)), { code: "aborted" });
  report.push("stale answer revision rejected");
  const two = await Promise.allSettled([service.execute(uid, resolveRequest(changed)), service.execute(uid, resolveRequest(changed))]);
  assert.equal(two.filter(x => x.status === "fulfilled").length, 1);
  assert.equal(two.filter(x => x.status === "rejected" && x.reason.code === "aborted").length, 1);
  report.push("competing revisions commit once in real Firestore transaction");
  const record = (await db.collection("scans").doc(first.scanId).get()).data();
  for (const forbidden of ["image", "apiKey", "rawOutput", "prompt"]) assert.equal(record[forbidden], undefined);
  assert.equal(record.result.contaminationScore, null);
  report.push("scan stores normalized observations, not photo, key or raw model output");
  clock += SESSION_MS + 1;
  await assert.rejects(service.execute(uid, resolveRequest(record.result)), { code: "failed-precondition" });
  clock -= SESSION_MS + 1;
  await db.collection("scans").doc(first.scanId).update({ revision: 20 });
  await assert.rejects(service.execute(uid, { ...resolveRequest(record.result), expectedRevision: 20 }), { code: "resource-exhausted" });
  report.push("expired sessions and excessive revisions rejected");
  providerFailure = true;
  const retry = analyzeRequest();
  await assert.rejects(service.execute(uid, retry));
  providerFailure = false;
  const recovered = await service.execute(uid, retry);
  paths.add(recovered.scanId);
  assert.equal(recovered.status, "NEEDS_CONFIRMATION");
  report.push("failed provider attempt can recover without fake result");
  console.log(JSON.stringify({ environment: "local Firestore emulator", provider: "test fixture only", checks: report.length, passed: report }, null, 2));
}
main().catch(error => { console.error(JSON.stringify({ failed: true, type: error.name, code: error.code, assertion: error.code === "ERR_ASSERTION" ? error.message.split("\n")[0] : undefined })); process.exitCode = 1; })
  .finally(async () => {
    // Delete only records with this fresh test uid in the demo project.
    const own = await db.collection("scans").where("uid", "==", uid).get();
    for (const doc of own.docs) await doc.ref.delete();
    await db.terminate();
  });
