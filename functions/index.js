const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const { ContextError } = require("./context");
const { analyzeObservation } = require("./observations");
const { createScanService } = require("./scan-service");
const admin = require("firebase-admin");
const { FieldValue } = require("firebase-admin/firestore");
const crypto = require("node:crypto");
const { MODEL, OMNI_MODEL, validateModel, AnalysisError, validateImage, analyzeWithNim } = require("./analysis");
admin.initializeApp();
const db = admin.firestore();
const nvidiaApiKey = defineSecret("NVIDIA_API_KEY");
const nvidiaModel = defineString("NVIDIA_MODEL", { default: OMNI_MODEL });
const MAX_ADMIN_GRANT = 10000;
const COUPON_CATALOG = {
  cu1000: { name: "CU 모바일 상품권 1,000원권", cost: 5000 },
  gs2000: { name: "GS25 모바일 상품권 2,000원권", cost: 9500 },
  mega_americano: { name: "메가커피 아메리카노(HOT)", cost: 10000 },
};
function requireAuth(request) {
  if (!request.auth?.uid) throw new HttpsError("unauthenticated", "로그인이 필요합니다. 다시 로그인해 주세요.");
  return request.auth.uid;
}
function requireAdmin(request) {
  const uid = requireAuth(request);
  if (request.auth.token.admin !== true) throw new HttpsError("permission-denied", "관리자 권한이 필요합니다.");
  return uid;
}
async function enforceDailyLimit(uid) {
  const date = new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" }).replace(/-/g, "");
  const ref = db.collection("usage").doc(`${uid}_${date}`);
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    const count = snap.exists ? snap.get("analyze") || 0 : 0;
    if (count >= 30) throw new HttpsError("resource-exhausted", "오늘 분석 한도 30회에 도달했습니다. 내일 다시 이용해 주세요.");
    tx.set(ref, { analyze: count + 1, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  });
}

// Local emulation reads only an inherited environment variable; no secret file is required.
const localEmulator = process.env.FUNCTIONS_EMULATOR === "true";
const runtimeOptions = localEmulator ? {} : { serviceAccount: "ecosort-runtime@focused-rig-vcf5x.iam.gserviceaccount.com" };
const stateService = createScanService({
  db, enforceDailyLimit, timestamp: () => FieldValue.serverTimestamp(),
  getProvider: () => {
    const model = validateModel(localEmulator ? (process.env.NVIDIA_MODEL || MODEL) : nvidiaModel.value());
    const apiKey = localEmulator ? process.env.NVIDIA_API_KEY : nvidiaApiKey.value();
    if (!apiKey?.trim()) throw new AnalysisError("failed-precondition", "AI 서버 키가 설정되지 않았습니다. 운영자에게 서버 설정을 요청해 주세요.");
    return { model, analyze: image => analyzeObservation(image, { apiKey, model }) };
  },
});
exports.analyzeImage = onCall({
  ...runtimeOptions,
  region: "us-central1", secrets: localEmulator ? [] : [nvidiaApiKey], timeoutSeconds: 150,
  memory: "512MiB", maxInstances: 3, concurrency: 10,
  enforceAppCheck: !localEmulator,
}, async request => {
  const uid = requireAuth(request);
  try {
    return await stateService.execute(uid, request.data);
  } catch (error) {
    if (error instanceof AnalysisError && error.check) console.warn(JSON.stringify({ event: "analysis-validation", check: error.check }));
    if (error instanceof AnalysisError || error instanceof ContextError) throw new HttpsError(error.code, error.message);
    if (error instanceof HttpsError) throw error;
    throw new HttpsError("unavailable", "분석 서버 처리에 실패했습니다. 잠시 후 같은 요청으로 다시 시도해 주세요.");
  }
});

// Image analysis does not prove disposal. Rewards stay disabled until separately validated.
exports.verifyDisposal = onCall(async request => {
  requireAuth(request);
  throw new HttpsError("failed-precondition", "배출 인증과 자동 포인트 지급은 향후 계획입니다. 현재 MVP는 사진 분석과 배출 안내를 제공합니다.");
});

exports.grantPoints = onCall({ timeoutSeconds: 30 }, async (request) => {
  const adminUid = requireAdmin(request);
  const targetUid = typeof request.data?.uid === "string" && request.data.uid ? request.data.uid : adminUid;
  const points = Number(request.data?.points);
  const reason = String(request.data?.reason || "admin_grant").substring(0, 200);

  if (!Number.isInteger(points) || points <= 0 || points > MAX_ADMIN_GRANT) {
    throw new HttpsError("invalid-argument", `지급 포인트는 1~${MAX_ADMIN_GRANT} 사이 정수여야 합니다.`);
  }

  const userRef = db.collection("users").doc(targetUid);
  const ledgerRef = db.collection("pointLedger").doc();

  const totalPoints = await db.runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    const current = snap.exists ? snap.get("points") || 0 : 0;
    const newTotal = current + points;
    tx.set(userRef, { points: newTotal, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(ledgerRef, {
      uid: targetUid,
      type: "admin_grant",
      amount: points,
      grantedBy: adminUid,
      reason,
      createdAt: FieldValue.serverTimestamp(),
    });
    return newTotal;
  });

  return { success: true, totalPoints };
});

// ---------------------------------------------------------------------------
// 4. redeemCoupon: 포인트 차감 + 쿠폰 발급을 하나의 트랜잭션으로 처리
// ---------------------------------------------------------------------------

exports.redeemCoupon = onCall({ timeoutSeconds: 30 }, async (request) => {
  const uid = requireAuth(request);
  const itemId = String(request.data?.itemId || "");
  const item = COUPON_CATALOG[itemId];
  if (!item) {
    throw new HttpsError("invalid-argument", "존재하지 않는 상품입니다.");
  }

  const userRef = db.collection("users").doc(uid);
  const redemptionRef = db.collection("redemptions").doc();
  const ledgerRef = db.collection("pointLedger").doc();

  const result = await db.runTransaction(async (tx) => {
    const userSnap = await tx.get(userRef);
    const currentPoints = userSnap.exists ? userSnap.get("points") || 0 : 0;
    if (currentPoints < item.cost) {
      throw new HttpsError("failed-precondition", "포인트가 부족합니다.");
    }

    // couponInventory에서 미사용 실쿠폰을 조회하고, 재고가 없으면 차감 없이 실패
    const inventoryQuery = db
      .collection("couponInventory")
      .where("itemId", "==", itemId)
      .where("status", "==", "available")
      .limit(1);
    const inventorySnap = await tx.get(inventoryQuery);

    let code;
    let isDemo;
    if (!inventorySnap.empty) {
      const couponDoc = inventorySnap.docs[0];
      code = couponDoc.get("code");
      isDemo = false;
      tx.update(couponDoc.ref, {
        status: "redeemed",
        redeemedBy: uid,
        redeemedAt: FieldValue.serverTimestamp(),
      });
    } else {
      throw new HttpsError("failed-precondition", "실제 쿠폰 재고가 없습니다. 포인트는 차감되지 않습니다.");
    }

    const remainingPoints = currentPoints - item.cost;
    tx.update(userRef, { points: remainingPoints, updatedAt: FieldValue.serverTimestamp() });
    tx.set(redemptionRef, {
      uid,
      itemId,
      itemName: item.name,
      cost: item.cost,
      code,
      isDemo,
      status: "issued",
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(ledgerRef, {
      uid,
      type: "coupon_redeem",
      amount: -item.cost,
      refId: redemptionRef.id,
      createdAt: FieldValue.serverTimestamp(),
    });

    return { code, isDemo, remainingPoints };
  });

  return { success: true, ...result, itemName: item.name };
});

// ---------------------------------------------------------------------------
// 5. deleteAccount: Auth 삭제 + Firestore 개인정보 삭제/익명화
// ---------------------------------------------------------------------------

async function anonymizeCollection(collectionName, uid) {
  // 감사/통계 목적 기록(포인트 원장, 인증 기록)은 삭제 대신 uid를 익명화해 보존
  const snap = await db.collection(collectionName).where("uid", "==", uid).get();
  const chunks = [];
  for (let i = 0; i < snap.docs.length; i += 400) {
    chunks.push(snap.docs.slice(i, i + 400));
  }
  for (const chunk of chunks) {
    const batch = db.batch();
    for (const doc of chunk) {
      batch.update(doc.ref, { uid: "deleted", anonymizedAt: FieldValue.serverTimestamp() });
    }
    await batch.commit();
  }
  return snap.size;
}

exports.deleteAccount = onCall({ ...runtimeOptions, timeoutSeconds: 150, invoker: "public", enforceAppCheck: !localEmulator, maxInstances: 3 }, async (request) => {
  const uid = requireAuth(request);

  // 1. 개인 식별 문서 삭제
  await db.collection("users").doc(uid).delete();

  // 2. 사용 기록 익명화 (포인트 원장/인증/교환/스캔 기록)
  await anonymizeCollection("pointLedger", uid);
  await anonymizeCollection("verifications", uid);
  await anonymizeCollection("redemptions", uid);
  await anonymizeCollection("scans", uid);

  // 3. 일일 사용량 문서 삭제
  const usagePrefix = `${uid}_`;
  const usageSnap = await db.collection("usage")
    .where(admin.firestore.FieldPath.documentId(), ">=", usagePrefix)
    .where(admin.firestore.FieldPath.documentId(), "<", usagePrefix + "\uf8ff")
    .get();
  if (!usageSnap.empty) {
    const batch = db.batch();
    usageSnap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }

  // 4. Firebase Auth 계정 삭제 (마지막에 수행해 실패 시 재시도 가능하게 함)
  await admin.auth().deleteUser(uid);

  return { success: true };
});
