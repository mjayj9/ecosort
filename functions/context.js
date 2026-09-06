"use strict";

const CHOICES = Object.freeze({
  purpose: { DISPOSE_NOW: "지금 배출하려는 물품", PREVIEW: "사용 후 배출법 미리 보기", UNKNOWN: "아직 모르겠음" },
  useState: { UNUSED: "미개봉·미사용", USED: "사용한 물품", UNKNOWN: "잘 모르겠음" },
  contents: { PRESENT: "내용물이 남아 있어요", EMPTY: "내용물을 비웠어요", UNKNOWN: "확인할 수 없어요" },
  residue: { VISIBLE: "눈에 보이는 잔여물이 있어요", NONE_VISIBLE: "직접 확인한 범위에는 잔여물이 없어요", UNKNOWN: "확인할 수 없어요" },
  material: { PET: "표시: PET (페트)", PP: "표시: PP", PE: "표시: PE / HDPE / LDPE", METAL: "음료·식품용 금속캔", GLASS: "음료·식품용 유리병", PAPER: "코팅되지 않은 종이상자", CARTON_GENERAL: "표시: 일반팩(살균팩)", CARTON_ASEPTIC: "표시: 멸균팩", UNKNOWN: "표시나 재질을 확인할 수 없어요" },
});
const TITLES = Object.freeze({
  purpose: "어떤 안내가 필요한가요?", useState: "물품의 현재 상태는 어떤가요?",
  contents: "내용물을 비웠나요?", residue: "안전하게 볼 수 있는 안쪽과 입구에 잔여물이 있나요?",
  material: "물품의 표시나 종류를 직접 확인해 주세요. 확인하기 위해 미개봉 제품을 뜯지 마세요.",
});
class ContextError extends Error {
  constructor(message = "물품 상태 선택값을 확인해 주세요.") { super(message); this.code = "invalid-argument"; }
}
function validateAnswers(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw) || Object.keys(raw).some(k => !Object.hasOwn(CHOICES, k))) throw new ContextError();
  const clean = {};
  for (const [key, value] of Object.entries(raw)) {
    if (typeof value !== "string" || !Object.hasOwn(CHOICES[key], value)) throw new ContextError();
    clean[key] = value;
  }
  if (!clean.purpose || !clean.useState) throw new ContextError("사용 목적과 현재 상태를 선택해 주세요. 모르겠음도 선택할 수 있어요.");
  if (clean.useState === "UNUSED" && (clean.contents || clean.residue)) throw new ContextError("미개봉·미사용 상태에서는 이전 내용물·잔여물 답변을 지워 주세요.");
  if (clean.residue && clean.contents !== "EMPTY") throw new ContextError("잔여물 확인 전 내용물 상태를 확인해 주세요.");
  return clean;
}
function question(key, allowed) {
  return { key, title: TITLES[key], choices: Object.entries(CHOICES[key]).filter(([value]) => !allowed || allowed.includes(value)).map(([value, label]) => ({ value, label })) };
}
module.exports = { CHOICES, ContextError, validateAnswers, question };
