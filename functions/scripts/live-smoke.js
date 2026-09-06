// Real provider smoke test, never substitutes a result or reads a key file.
const fs = require("node:fs");
const { analyzeWithNim, OMNI_MODEL, validateModel } = require("../analysis");
async function main() {
  if (!process.env.NVIDIA_API_KEY) throw new Error("NVIDIA_API_KEY 환경변수가 필요합니다.");
  const paths = process.argv.slice(2);
  if (!paths.length) throw new Error("사용법: npm run smoke:live -- 사진1.jpg 사진2.jpg (각 1.5MB 이하)");
  for (const path of paths) {
    const start = Date.now();
    const result = await analyzeWithNim(fs.readFileSync(path).toString("base64"), { apiKey: process.env.NVIDIA_API_KEY, model: validateModel(process.env.NVIDIA_MODEL || OMNI_MODEL) });
    console.log(JSON.stringify({ file: require("node:path").basename(path), elapsedMs: Date.now() - start, ...result }, null, 2));
  }
}
main().catch(error => { console.error(error.code || "setup-error", error.message); process.exitCode = 1; });
