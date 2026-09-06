"use strict";

const crypto = require("node:crypto");
const { AnalysisError, validateImage } = require("./analysis");
const { validateAnswers } = require("./context");
const { parseObservation } = require("./observations");
const { decide } = require("./policy");

const SESSION_MS = 30 * 60 * 1000;
const LEASE_MS = 180 * 1000;
const MAX_REVISIONS = 20;
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
function fail(code, message) { throw new AnalysisError(code, message); }
function digest(value) { return crypto.createHash("sha256").update(value).digest("hex"); }
function answersDigest(answers) { return digest(JSON.stringify(Object.fromEntries(Object.entries(answers).sort(([a], [b]) => a.localeCompare(b))))); }
function validateRequest(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) fail("invalid-argument", "분석 요청 형식을 확인해 주세요.");
  if (Object.keys(data).some(k => ["apiKey", "key", "model", "decision", "observation"].includes(k))) fail("invalid-argument", "앱에서 서버 키·모델·판정을 지정할 수 없습니다.");
  if (data.schemaVersion !== 2) fail("failed-precondition", "상태 확인 기능이 추가되었습니다. 최신 EcoSort 앱으로 업데이트해 주세요.");
  const allowed = data.operation === "analyze" ? ["schemaVersion", "operation", "image", "answers", "requestId"] :
    data.operation === "resolve" ? ["schemaVersion", "operation", "scanId", "expectedRevision", "answers", "requestId"] : [];
  if (!allowed.length || Object.keys(data).some(k => !allowed.includes(k))) fail("invalid-argument", "지원되지 않는 분석 요청입니다.");
  if (typeof data.requestId !== "string" || !uuid.test(data.requestId)) fail("invalid-argument", "분석 요청 식별자를 확인해 주세요.");
  const answers = validateAnswers(data.answers);
  if (data.operation === "analyze") return { ...data, answers, image: validateImage(data.image) };
  if (typeof data.scanId !== "string" || !/^[a-f0-9]{40}$/.test(data.scanId) || !Number.isInteger(data.expectedRevision) || data.expectedRevision < 0 || data.expectedRevision > MAX_REVISIONS) fail("invalid-argument", "안내 기록과 버전을 확인해 주세요.");
  return { ...data, answers };
}

function createScanService({ db, getProvider, enforceDailyLimit, timestamp, now = Date.now }) {
  function response(o, answers, scanId, model, revision) { return { ...decide(o, answers), scanId, model, revision }; }
  async function analyze(uid, data) {
    const provider = getProvider();
    const scanId = digest(uid + ":" + data.requestId).slice(0, 40);
    const ref = db.collection("scans").doc(scanId);
    const fingerprint = digest(data.image + ":" + answersDigest(data.answers));
    const lease = crypto.randomUUID();
    const claimed = await db.runTransaction(async tx => {
      const snap = await tx.get(ref);
      const old = snap.exists ? snap.data() : null;
      if (old) {
        if (old.uid !== uid || old.fingerprint !== fingerprint) fail("invalid-argument", "같은 요청 식별자에 다른 사진이나 답변을 사용할 수 없습니다.");
        if (old.state === "complete") return { cached: old.initialResult };
        if (old.state === "pending" && old.leaseUntil > now()) fail("aborted", "같은 사진의 분석이 진행 중입니다. 잠시 후 같은 요청으로 다시 시도해 주세요.");
      }
      tx.set(ref, { uid, schemaVersion: 2, fingerprint, state: "pending", lease, leaseUntil: now() + LEASE_MS,
        createdAt: old?.createdAt || timestamp(), updatedAt: timestamp() });
      return { cached: null };
    });
    if (claimed.cached) return claimed.cached;
    try {
      await enforceDailyLimit(uid);
      const observation = parseObservation(JSON.stringify(await provider.analyze(data.image)));
      const result = response(observation, data.answers, scanId, provider.model, 0);
      await db.runTransaction(async tx => {
        const snap = await tx.get(ref);
        const current = snap.data();
        if (!current || current.uid !== uid || current.lease !== lease) fail("aborted", "이 분석 요청이 변경되었습니다. 사진을 다시 분석해 주세요.");
        tx.update(ref, { state: "complete", observation, answers: data.answers, model: provider.model,
          revision: 0, initialResult: result, result, expiresAtMs: now() + SESSION_MS, updatedAt: timestamp() });
      });
      return result;
    } catch (error) {
      // Retrying a failed request is allowed; a completed or different lease is never overwritten.
      await db.runTransaction(async tx => {
        const snap = await tx.get(ref);
        if (snap.exists && snap.get("uid") === uid && snap.get("lease") === lease && snap.get("state") === "pending") {
          tx.update(ref, { state: "failed", updatedAt: timestamp() });
        }
      }).catch(() => {});
      throw error;
    }
  }
  async function resolve(uid, data) {
    const ref = db.collection("scans").doc(data.scanId);
    const fingerprint = answersDigest(data.answers);
    return db.runTransaction(async tx => {
      const snap = await tx.get(ref);
      if (!snap.exists || snap.get("uid") !== uid) fail("not-found", "이 계정의 분석 기록을 찾지 못했습니다. 사진을 다시 분석해 주세요.");
      const record = snap.data();
      if (record.schemaVersion !== 2 || record.state !== "complete") fail("failed-precondition", "사진 분석이 완료된 뒤 상태를 확인할 수 있습니다.");
      if (record.expiresAtMs < now()) fail("failed-precondition", "상태 확인 시간이 지났습니다. 현재 사진을 다시 분석해 주세요.");
      if (record.lastRequestId === data.requestId) {
        if (record.lastFingerprint !== fingerprint) fail("invalid-argument", "같은 요청 식별자에 다른 답변을 사용할 수 없습니다.");
        return record.result;
      }
      if (data.expectedRevision !== record.revision) fail("aborted", "다른 상태 수정이 먼저 반영되었습니다. 현재 사진을 다시 분석해 주세요.");
      if (record.revision >= MAX_REVISIONS) fail("resource-exhausted", "이 사진의 상태 수정 한도에 도달했습니다. 현재 사진을 다시 분석해 주세요.");
      const revision = record.revision + 1;
      const result = response(record.observation, data.answers, data.scanId, record.model, revision);
      tx.update(ref, { answers: data.answers, result, revision, lastRequestId: data.requestId, lastFingerprint: fingerprint, updatedAt: timestamp() });
      return result;
    });
  }
  return { execute: async (uid, input) => {
    if (typeof uid !== "string" || !uid) fail("unauthenticated", "로그인이 필요합니다.");
    const data = validateRequest(input);
    return data.operation === "analyze" ? analyze(uid, data) : resolve(uid, data);
  } };
}
module.exports = { validateRequest, createScanService, SESSION_MS, MAX_REVISIONS };
