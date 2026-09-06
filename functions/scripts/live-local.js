"use strict";
// Real local callable -> real NIM. Unknown user state is deliberate; this is connectivity, not accuracy evidence.
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const { validateImage } = require("../analysis");
const base = "http://127.0.0.1:5001/demo-ecosort/us-central1/";
async function main() {
  const files = process.argv.slice(2);
  if (!files.length) throw new Error("Pass JPEG file paths after npm run smoke:local --");
  const signup = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ returnSecureToken: true }),
  });
  const auth = await signup.json();
  assert.equal(signup.status, 200);
  const headers = { "Content-Type": "application/json", Authorization: "Bearer " + auth.idToken };
  try {
    for (const file of files) {
      const image = validateImage(fs.readFileSync(file).toString("base64"));
      const start = Date.now();
      const response = await fetch(base + "analyzeImage", { method: "POST", headers, signal: AbortSignal.timeout(125000),
        body: JSON.stringify({ data: { schemaVersion: 2, operation: "analyze", image, requestId: crypto.randomUUID(), answers: { purpose: "UNKNOWN", useState: "UNKNOWN" } } }),
      });
      const body = await response.json();
      if (!response.ok) {
        console.log(JSON.stringify({ file: path.basename(file), http: response.status, code: body.error?.status }));
        throw new Error("Local real-provider request failed");
      }
      assert.equal(body.result.schemaVersion, 2);
      assert.equal(body.result.contaminationScore, null);
      assert.equal(body.result.decision, null);
      console.log(JSON.stringify({ file: path.basename(file), elapsedMs: Date.now() - start, model: body.result.model,
        status: body.result.status, itemName: body.result.itemName, userState: "UNKNOWN", accuracyVerified: false }));
    }
  } finally {
    const response = await fetch(base + "deleteAccount", { method: "POST", headers, body: JSON.stringify({ data: {} }) });
    const body = await response.json();
    assert.equal(body.result?.success, true, "local test account cleanup");
  }
}
main().catch(error => { console.error(JSON.stringify({ failed: true, type: error.name, code: error.code })); process.exitCode = 1; });
