"use strict";
// Exercises only local Firebase emulators. The NVIDIA key stays in the server process.
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { validateImage, parseAnalysis, validateModel } = require("../analysis");
const origin = "http://127.0.0.1";
async function main() {
  const files = process.argv.slice(2);
  assert(files.length > 0, "Provide JPEG files (maximum 1.5 MB each).");
  const images = files.map(file => ({ file: path.basename(file), image: validateImage(fs.readFileSync(file).toString("base64")) }));
  const auth = await fetch(`${origin}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-demo-public-key`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ returnSecureToken: true }), signal: AbortSignal.timeout(15000)
  });
  const account = await auth.json();
  assert(auth.ok && account.idToken, "Local Firebase Auth signup failed.");
  const results = [];
  for (const { file, image } of images) {
    const started = Date.now();
    try {
      const response = await fetch(`${origin}:5001/demo-ecosort/us-central1/analyzeImage`, {
        method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${account.idToken}` },
        body: JSON.stringify({ data: { image } }), signal: AbortSignal.timeout(135000)
      });
      const payload = await response.json();
      if (!response.ok || payload.error) {
        // The callable deliberately returns sanitized messages, never provider error bodies.
        results.push({ file, elapsedMs: Date.now() - started, passed: false, httpStatus: response.status,
          code: payload.error?.status || "HTTP_ERROR", diagnostics: payload.error?.details, message: payload.error?.message || "Local callable failed." });
      } else {
        const result = payload.result;
        validateModel(result.model);
        assert.match(result.scanId, /^[A-Za-z0-9]{20}$/);
        parseAnalysis(JSON.stringify(result));
        assert(result.confidence >= 0.7 || result.decision === "UNKNOWN");
        results.push({ file, elapsedMs: Date.now() - started, passed: true, ...result });
      }
    } catch (error) {
      results.push({ file, elapsedMs: Date.now() - started, passed: false, code: error.name, message: "Local connection, timeout, or response-contract check failed." });
    }
    console.log(JSON.stringify(results[results.length - 1], null, 2));
  }
  if (results.some(result => !result.passed)) process.exitCode = 1;
}
main().catch(() => { console.error("Local emulator not ready, invalid JPEG, or authentication setup failed."); process.exitCode = 1; });
