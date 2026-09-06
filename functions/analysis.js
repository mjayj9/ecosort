"use strict";

const MODEL = "moonshotai/kimi-k3";
const OMNI_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning";
const ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions";
const MAX_IMAGE_BYTES = 1500 * 1024;
class AnalysisError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function validateModel(model) {
  if (![MODEL, OMNI_MODEL].includes(model)) throw new AnalysisError("failed-precondition", "지원되지 않는 AI 모델 설정입니다. 운영자가 서버 설정을 확인해야 합니다.");
  return model;
}
function invalidResponse(check = "schema") {
  const failure = new AnalysisError("data-loss", "AI 응답을 확인하지 못했습니다. 다시 분석하거나 재촬영해 주세요.");
  failure.check = check;
  return failure;
}
function validateImage(image) {
  if (typeof image !== "string" || !image.length || image.length > Math.ceil(MAX_IMAGE_BYTES / 3) * 4 ||
      image.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(image)) {
    throw new AnalysisError("invalid-argument", "사진 형식이나 크기를 확인해 주세요. JPEG 사진을 다시 선택해 주세요.");
  }
  const bytes = Buffer.from(image, "base64");
  if (bytes.length < 4 || bytes.length > MAX_IMAGE_BYTES || bytes.toString("base64") !== image ||
      bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff ||
      bytes[bytes.length - 2] !== 0xff || bytes[bytes.length - 1] !== 0xd9) {
    throw new AnalysisError("invalid-argument", "유효한 JPEG 사진을 다시 선택해 주세요.");
  }
  return image;
}
// Only final answer content is accumulated. Reasoning deltas are discarded immediately.
async function readStream(response, parseContent) {
  if (!response.body) throw invalidResponse("empty-stream");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = "", event = [], content = "", totalBytes = 0, stopped = false, done = false;
  function consumeEvent() {
    if (!event.length) return;
    const data = event.join("\n"); event = [];
    if (data === "[DONE]") { done = true; return; }
    let chunk;
    try { chunk = JSON.parse(data); } catch { throw invalidResponse("sse-json-syntax"); }
    const choice = chunk?.choices?.[0];
    if (chunk?.error) {
      const code = String(chunk.error.code ?? chunk.error.status ?? "").toLowerCase();
      if (["429", "resource_exhausted", "rate_limit_exceeded"].includes(code)) throw new AnalysisError("resource-exhausted", "AI 사용량 제한에 도달했습니다. 잠시 후 다시 시도해 주세요.");
      if (["401", "403", "unauthorized", "permission_denied"].includes(code)) throw new AnalysisError("failed-precondition", "AI 서버 인증에 실패했습니다. 운영자가 서버 키와 이용 권한을 확인해야 합니다.");
      const failure = new AnalysisError("unavailable", "AI 제공사에서 일시적인 오류가 발생했습니다. 잠시 후 같은 사진으로 다시 시도해 주세요.");
      failure.retryable = true;
      throw failure;
    }
    if (!choice) return; // Optional usage-only chunk.
    const delta = choice.delta?.content;
    if (delta != null && typeof delta !== "string") throw invalidResponse("stream-content-type");
    if (typeof delta === "string") content += delta;
    if (content.length > 16000) throw invalidResponse();
    if (choice.finish_reason) {
      if (choice.finish_reason !== "stop") throw invalidResponse("stream-interrupted");
      stopped = true;
    }
  }
  try {
    while (!done) {
      const chunk = await reader.read();
      if (chunk.done) break;
      totalBytes += chunk.value.byteLength;
      if (totalBytes > 512 * 1024) throw invalidResponse();
      pending += decoder.decode(chunk.value, { stream: true });
      if (pending.length > 65536) throw invalidResponse();
      let newline;
      while ((newline = pending.indexOf("\n")) >= 0) {
        const line = pending.slice(0, newline).replace(/\r$/, "");
        pending = pending.slice(newline + 1);
        if (line === "") consumeEvent();
        else if (line.startsWith("data:")) event.push(line.slice(5).trimStart());
        if (done) break;
      }
    }
    if (!done || !stopped) throw invalidResponse("stream-incomplete");
    return parseContent(content);
  } finally { await reader.cancel().catch(() => {}); }
}

async function analyzeWithNim(image, { apiKey, model = MODEL, fetchImpl = globalThis.fetch, timeoutMs = 105000, prompt, parseContent } = {}) {
  validateImage(image);
  validateModel(model);
  if (typeof prompt !== "string" || !prompt || typeof parseContent !== "function") throw new AnalysisError("failed-precondition", "서버 분석 계약 설정을 확인해 주세요.");
  if (typeof apiKey !== "string" || !apiKey.trim()) {
    throw new AnalysisError("failed-precondition", "AI 서버 키가 설정되지 않았습니다. 운영자에게 서버 설정을 요청해 주세요.");
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  let phase = "headers";
  try {
    // One retry only for an explicit provider error event; both attempts share the deadline.
    for (let attempt = 0; attempt < 2; attempt++) {
     try {
    const response = await fetchImpl(ENDPOINT, {
      method: "POST", signal: controller.signal,
      headers: { "Authorization": `Bearer ${apiKey.trim()}`, "Content-Type": "application/json", "Accept": "text/event-stream" },
      body: JSON.stringify({ model, stream: true, max_tokens: 4096,
        ...(model === MODEL ? { temperature: 1, reasoning_effort: "low" } : { temperature: 0.2, reasoning_budget: 1024 }),
        messages: [ { role: "system", content: prompt }, { role: "user", content: [
          { type: "text", text: "이 사진의 물품과 오염 상태를 분석하고 지정한 JSON만 반환해 주세요." },
          { type: "image_url", image_url: { url: `data:image/jpeg;base64,${image}` } },
        ] } ] }),
    });
    phase = "body";
    // Provider error bodies and reasoning_content are never logged or returned.
    if (response.status === 401 || response.status === 403) throw new AnalysisError("failed-precondition", "AI 서버 인증에 실패했습니다. 운영자가 서버 키와 이용 권한을 확인해야 합니다.");
    if (response.status === 429) throw new AnalysisError("resource-exhausted", "AI 사용량 제한에 도달했습니다. 잠시 후 다시 시도해 주세요.");
    if (response.status === 202) throw new AnalysisError("unavailable", "AI 처리가 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");
    if (response.status === 408 || response.status === 504) throw new AnalysisError("deadline-exceeded", "분석 시간이 초과되었습니다. 연결 상태를 확인하고 다시 시도해 주세요.");
    if (!response.ok) throw new AnalysisError("unavailable", "AI 서버에서 분석하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    if (response.headers?.get("content-type")?.includes("text/event-stream")) return await readStream(response, parseContent);
    let data;
    try { data = await response.json(); } catch (error) {
      if (controller.signal.aborted) throw error;
      throw invalidResponse();
    }
    const choice = data?.choices?.[0];
    if (choice?.finish_reason && choice.finish_reason !== "stop") throw invalidResponse();
    return parseContent(choice?.message?.content);
     } catch (error) {
       if (!error.retryable || attempt !== 0 || controller.signal.aborted) throw error;
       // No provider error body is logged. Never retry malformed JSON, auth, quota or ordinary HTTP errors.
       await new Promise((resolve, reject) => {
         const retryTimer = setTimeout(() => { controller.signal.removeEventListener("abort", abort); resolve(); }, 700);
         function abort() { clearTimeout(retryTimer); reject(new Error("aborted")); }
         controller.signal.addEventListener("abort", abort, { once: true });
         if (controller.signal.aborted) abort();
       });
       phase = "headers";
     }
    }
  } catch (error) {
    if (error instanceof AnalysisError) throw error;
    if (controller.signal.aborted) {
      const failure = new AnalysisError("deadline-exceeded", "분석 시간이 초과되었습니다. 다시 시도해 주세요.");
      failure.phase = phase;
      throw failure;
    }
    throw new AnalysisError("unavailable", "AI 서버 연결에 실패했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.");
  } finally { clearTimeout(timer); }
}
module.exports = { MODEL, OMNI_MODEL, validateModel, ENDPOINT, AnalysisError, validateImage, readStream, analyzeWithNim };
