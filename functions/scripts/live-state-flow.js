"use strict";
// Real photo -> real NIM; changed user answers exercise workflow and are not visual ground truth.
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFileSync } = require("node:child_process");
const assert = require("node:assert/strict");
const repo = path.resolve(__dirname, "../..");
const mode = process.argv[2];
const photoPath = process.argv[3];
const reportPath = process.argv[4];
const extraPhotos = process.argv.slice(5);
const report = { startedAt: new Date().toISOString(), environment: mode, realNim: true, accuracyBenchmark: false,
  userAnswersAreTestInputs: true, scenarios: [], cleanup: [] };
async function main() {
  assert.ok(["local", "cloud"].includes(mode) && photoPath && reportPath, "Usage: live-state-flow.js local|cloud photo.jpg report.json [extra photos]");
  let authBase, apiKey, base, appCheck;
  if (mode === "local") {
    authBase = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/";
    apiKey = "local";
    base = "http://127.0.0.1:5001/demo-ecosort/us-central1/";
  } else {
    const config = JSON.parse(fs.readFileSync(path.join(repo, "app/google-services.json"), "utf8").replace(/^\uFEFF/, ""));
    const client = config.client.find(c => c.client_info.android_client_info.package_name === "com.aistudio.ecosort.kxmpzq");
    apiKey = client.api_key[0].current_key; // Public Firebase client identifier, not the NVIDIA secret.
    authBase = "https://identitytoolkit.googleapis.com/v1/";
    base = "https://us-central1-" + config.project_info.project_id + ".cloudfunctions.net/";
    const adb = path.join(process.env.LOCALAPPDATA, "Android/Sdk/platform-tools/adb.exe");
    const args = ["-s", process.env.ECOSORT_DEVICE || "emulator-5554", "shell", "run-as", "com.aistudio.ecosort.kxmpzq"];
    const names = execFileSync(adb, [...args, "ls", "shared_prefs"], { encoding: "utf8", windowsHide: true }).trim().split(/\r?\n/);
    const filename = names.find(x => x.startsWith("com.google.firebase.appcheck.debug.store."));
    assert.ok(filename && /^[-A-Za-z0-9_.+]+$/.test(filename), "registered debug device required");
    const prefs = execFileSync(adb, [...args, "cat", "shared_prefs/" + filename], { encoding: "utf8", windowsHide: true });
    const match = prefs.match(/>([a-f0-9]{8}-[a-f0-9-]{27})</i);
    assert.ok(match, "debug provider initialized");
    const app = "projects/" + config.project_info.project_number + "/apps/" + client.client_info.mobilesdk_app_id;
    const exchange = await fetch("https://firebaseappcheck.googleapis.com/v1/" + app + ":exchangeDebugToken?key=" + apiKey,
      { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ debugToken: match[1] }) });
    const body = await exchange.json(); assert.equal(exchange.status, 200, "App Check exchange"); appCheck = body.token;
  }
  const signup = async () => {
    const r = await fetch(authBase + "accounts:signUp?key=" + apiKey, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ returnSecureToken: true }) });
    const auth = await r.json(); assert.equal(r.status, 200, "test sign-in"); return auth;
  };
  const auth = await signup();
  let second;
  const invoke = async (name, data, options = {}) => {
    const start = Date.now();
    const headers = { "Content-Type": "application/json" };
    if (options.auth !== false) headers.Authorization = "Bearer " + (options.auth || auth).idToken;
    if (appCheck && options.appCheck !== false) headers["X-Firebase-AppCheck"] = appCheck;
    const r = await fetch(base + "analyzeImage", { method: "POST", headers, body: JSON.stringify({ data }), signal: AbortSignal.timeout(125000) });
    const body = await r.json();
    report.scenarios.push({ name, httpStatus: r.status, elapsedMs: Date.now() - start, ...body });
    console.log(JSON.stringify({ name, http: r.status, status: body.result?.status || body.error?.status, item: body.result?.itemName, revision: body.result?.revision, elapsedMs: Date.now() - start }));
    if (body.result) {
      assert.equal(body.result.schemaVersion, 2); assert.equal(body.result.contaminationScore, null); assert.equal(body.result.decision, null);
      assert.equal(body.result.confidence, undefined); assert.equal(body.result.guideScope, "COMMON_PREPARATION");
    }
    return body;
  };
  const imageRequest = file => ({ schemaVersion: 2, operation: "analyze", requestId: crypto.randomUUID(), image: fs.readFileSync(file).toString("base64"), answers: { purpose: "DISPOSE_NOW", useState: "USED" } });
  const resolveRequest = (r, answers) => ({ schemaVersion: 2, operation: "resolve", requestId: crypto.randomUUID(), scanId: r.scanId, expectedRevision: r.revision, answers });
  try {
    assert.equal((await invoke("missing-login", {}, { auth: false })).error?.status, "UNAUTHENTICATED");
    if (mode === "cloud") assert.equal((await invoke("missing-app-check", {}, { appCheck: false })).error?.status, "UNAUTHENTICATED");
    assert.equal((await invoke("legacy-client-upgrade", { image: "bad" })).error?.status, "FAILED_PRECONDITION");
    const invalid = { ...imageRequest(photoPath), image: "bad" };
    assert.equal((await invoke("invalid-photo", invalid)).error?.status, "INVALID_ARGUMENT");
    assert.equal((await invoke("client-model-rejected", { ...invalid, model: "injected" })).error?.status, "INVALID_ARGUMENT");
    const request = imageRequest(photoPath);
    let r = (await invoke("single-bottle-real-nim", request)).result;
    assert.ok(r, "real image response");
    assert.equal(r.status, "NEEDS_CONFIRMATION", "single bottle should request content state");
    assert.equal(r.questions[0]?.key, "contents");
    const initial = r;
    assert.deepEqual((await invoke("duplicate-analysis-cached", request)).result, initial);
    const correction = resolveRequest(r, { purpose: "DISPOSE_NOW", useState: "USED", contents: "PRESENT" });
    r = (await invoke("remaining-contents", correction)).result;
    assert.equal(r.status, "PREPARATION");
    assert.deepEqual((await invoke("duplicate-correction-cached", correction)).result, r);
    assert.equal((await invoke("stale-revision", resolveRequest(initial, { purpose: "PREVIEW", useState: "UNUSED" }))).error?.status, "ABORTED");
    second = await signup();
    assert.equal((await invoke("other-account-cannot-resolve", resolveRequest(r, { purpose: "PREVIEW", useState: "UNUSED" }), { auth: second })).error?.status, "NOT_FOUND");
    r = (await invoke("unused-preview-synthetic-user-correction", resolveRequest(r, { purpose: "PREVIEW", useState: "UNUSED", material: "PET" }))).result;
    assert.equal(r.status, "PREVIEW");
    assert.ok(r.evidence.some(x => x.source === "USER" && x.value.includes("미개봉")));
    r = (await invoke("unused-dispose-synthetic-user-correction", resolveRequest(r, { purpose: "DISPOSE_NOW", useState: "UNUSED" }))).result;
    assert.equal(r.status, "PREPARATION"); assert.equal(r.steps.length, 0);
    r = (await invoke("unknown-contents-no-repeat", resolveRequest(r, { purpose: "DISPOSE_NOW", useState: "USED", contents: "UNKNOWN" }))).result;
    assert.equal(r.status, "HOLD"); assert.equal(r.questions.length, 0);
    r = (await invoke("unknown-residue-no-repeat", resolveRequest(r, { purpose: "DISPOSE_NOW", useState: "USED", contents: "EMPTY", residue: "UNKNOWN" }))).result;
    assert.equal(r.status, "HOLD"); assert.equal(r.questions.length, 0);
    r = (await invoke("residue-preparation-synthetic-confirmation", resolveRequest(r, { purpose: "DISPOSE_NOW", useState: "USED", contents: "EMPTY", residue: "VISIBLE", material: "PET" }))).result;
    assert.equal(r.status, "PREPARATION"); assert.ok(r.steps.length > 0);
    report.singleImageScanId = r.scanId;
    for (const file of extraPhotos) {
      const extra = (await invoke("extra-photo-" + path.basename(file), imageRequest(file))).result;
      assert.ok(extra);
      assert.ok(["HOLD", "NEEDS_PHOTO"].includes(extra.status), "unsupported/multi/blank image must not produce disposal instructions");
      assert.equal(extra.steps.length, 0);
    }
    report.workflowPassed = true;
  } finally {
    for (const account of [auth, second].filter(Boolean)) {
      const headers = { "Content-Type": "application/json", Authorization: "Bearer " + account.idToken, ...(appCheck ? { "X-Firebase-AppCheck": appCheck } : {}) };
      const r = await fetch(base + "deleteAccount", { method: "POST", headers, body: JSON.stringify({ data: {} }) });
      const body = await r.json();
      report.cleanup.push({ httpStatus: r.status, success: body.result?.success === true });
      assert.equal(body.result?.success, true, "test account cleanup");
    }
  }
}
main().catch(error => { report.failure = { type: error.name, code: error.code, assertion: error.code === "ERR_ASSERTION" ? error.message.split("\n")[0] : undefined }; console.error(JSON.stringify(report.failure)); process.exitCode = 1; })
 .finally(() => { report.finishedAt = new Date().toISOString(); if (reportPath) fs.writeFileSync(reportPath, JSON.stringify(report, null, 2)); });
