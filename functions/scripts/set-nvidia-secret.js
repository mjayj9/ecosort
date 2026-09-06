"use strict";
// Never use the CLI Secret upload helper: this request explicitly suppresses body logging.
const path = require("node:path");
const cli = path.join(process.env.APPDATA, "npm/node_modules/firebase-tools");
async function main() {
  const key = process.env.NVIDIA_API_KEY;
  if (!key?.startsWith("nvapi-")) throw new Error("missing-key");
  const project = "focused-rig-vcf5x";
  await require(cli).apps.list({ project, nonInteractive: true });
  const { Client } = require(cli + "/lib/apiv2");
  const client = new Client({ urlPrefix: "https://secretmanager.googleapis.com", apiVersion: "v1" });
  const name = `projects/${project}/secrets/NVIDIA_API_KEY`;
  try { await client.get(name, { skipLog: { resBody: true } }); }
  catch (error) {
    if ((error.status || error.original?.status) !== 404) throw error;
    await client.post(`projects/${project}/secrets`, { replication: { automatic: {} }, labels: { "firebase-managed": "functions" } },
      { queryParams: { secretId: "NVIDIA_API_KEY" }, skipLog: { body: true, resBody: true } });
  }
  const response = await client.post(`${name}:addVersion`, { payload: { data: Buffer.from(key.trim()).toString("base64") } },
    { skipLog: { body: true, resBody: true } });
  console.log(JSON.stringify({ secretConfigured: true, version: response.body.name.split("/").at(-1) }));
}
main().catch(() => { console.error("Secret setup failed. Check Firebase CLI login, project permission, and hidden key input."); process.exitCode = 1; });
