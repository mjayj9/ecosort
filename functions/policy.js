"use strict";

const { parseObservation } = require("./observations");
const { validateAnswers, CHOICES, question } = require("./context");
const { RULE_VERSION, LOCAL_NOTICE, SUBJECTS, MATERIALS, MATERIAL_OPTIONS, guideFor } = require("./guidance");

function decide(observation, inputAnswers) {
  const o = parseObservation(JSON.stringify(observation));
  const a = validateAnswers(inputAnswers);
  const result = {
    schemaVersion: 2, status: "HOLD", title: "판단을 보류했어요", itemName: SUBJECTS[o.subject],
    materialLabel: a.material && a.material !== "UNKNOWN" ? MATERIALS[a.material] + " · 사용자 확인" : "세부 재질 확인 전",
    decision: null, contaminationScore: null, guideScope: "COMMON_PREPARATION", ruleVersion: RULE_VERSION,
    ruleId: null, summary: "", evidence: [], limits: [LOCAL_NOTICE], questions: [], steps: [], sources: [],
  };
  const fact = (label, value, source) => result.evidence.push({ label, value, source });
  fact("사용 목적", CHOICES.purpose[a.purpose], "USER");
  fact("사용 상태", CHOICES.useState[a.useState], "USER");
  fact("물품 종류", SUBJECTS[o.subject], "AI");
  // Unused user confirmation overrides inferred opening/inside state, without claiming AI agreement.
  if (a.useState === "UNUSED") {
    fact("내부 상태", "현재 내부의 오염 상태를 판단하지 않았어요", "LIMIT");
    if (o.opening === "OPEN") result.limits.push("사진의 개봉 추정과 사용자 답변이 달라, 개봉 추정을 배출 판단에 사용하지 않았어요.");
  } else {
    fact("사진의 내부 관찰", o.interior === "VISIBLE" ? "보이는 범위만 관찰했어요" : o.interior === "NOT_APPLICABLE" ? "내부 관찰을 적용하지 않는 물품이에요" : "사진에서 내부를 확인할 수 없어요", "AI");
  }
  if (a.contents) fact("내용물", CHOICES.contents[a.contents], "USER");
  if (a.residue) fact("잔여물", CHOICES.residue[a.residue], "USER");
  if (a.material) fact("포장재 표시·종류", MATERIALS[a.material], "USER");
  function finish(status, title, summary, patch = {}) { return { ...result, status, title, summary, ...patch }; }
  function ask(key) {
    const allowed = key === "material" ? MATERIAL_OPTIONS[o.subject] : undefined;
    return finish("NEEDS_CONFIRMATION", "상태를 조금 더 확인해 주세요", "사진만으로 확인하지 못한 정보가 있어요. 직접 확인할 수 없다면 모르겠음을 선택하세요.", { questions: [question(key, allowed)] });
  }
  if (o.risk === "SUSPECTED") return finish("HOLD", "일반 세척 안내를 보류했어요", "위험 여부를 확신할 수 없는 물품이에요. 열거나 씻는 방법을 안내하지 않습니다. 제품 표시와 거주지의 해당 품목 안내를 확인하세요.");
  if (o.target !== "SINGLE" || o.subject === "UNKNOWN" || o.confidence < 0.7) {
    const message = o.target === "MULTIPLE" ? "판단할 물품 한 개를 화면 중앙에 크게 보여 주세요." : o.target === "NONE" ? "분석할 물품을 찾지 못했어요. 물품 한 개를 화면 중앙에 보여 주세요." : "물품 한 개가 밝고 선명하게 보이도록 가까이 촬영해 주세요. 미개봉 제품을 뜯을 필요는 없어요.";
    return finish("NEEDS_PHOTO", "물품을 다시 보여 주세요", message);
  }
  if (!MATERIAL_OPTIONS[o.subject]) return finish("HOLD", "현재 지원하는 품목이 아니에요", "현재는 음료팩·음료병·식품 용기·식품캔·종이상자의 공통 준비 방법을 안내합니다. 이 물품의 배출 방법은 별도로 확인해 주세요.");
  if (o.risk === "UNKNOWN") return finish("HOLD", "일반 세척 안내를 보류했어요", "이 물품의 위험 여부를 확인하지 못했어요. 제품의 용도와 표시가 보이는 사진으로 다시 확인해 주세요.");
  if (a.purpose === "UNKNOWN" || a.useState === "UNKNOWN") return finish("HOLD", "사용 목적과 상태를 확인하면 안내할 수 있어요", "확인할 수 없는 답변을 사용한 물품이나 빈 용기로 바꾸지 않았어요. 알게 되면 아래의 목적·상태 선택을 수정해 주세요.");
  if (a.material && !MATERIAL_OPTIONS[o.subject].includes(a.material)) return finish("HOLD", "물품 종류와 확인한 재질이 달라요", "사진의 물품 종류와 선택한 포장재를 함께 적용할 수 없어요. 해당 물품의 표시가 선명하게 보이도록 다시 촬영해 주세요.");
  if (a.material && a.material !== "UNKNOWN" && o.material !== "UNKNOWN" && o.material !== a.material) return finish("HOLD", "재질 표시를 다시 확인해 주세요", "사진에서 추정한 표시와 사용자 확인이 달라 배출 판단을 보류했어요. 표시가 보이는 면을 다시 보여 주세요.");

  const rule = guideFor(a.material);
  function withRule(status, title, summary, includeSteps = true) {
    return finish(status, title, summary, { ruleId: rule?.id || null, sources: rule?.sources || [], steps: rule ? (includeSteps ? rule.steps : rule.steps.slice(1)) : [] });
  }
  if (a.purpose === "PREVIEW") {
    if (!a.material) return ask("material");
    if (!rule) return finish("PREVIEW", "사용 후 안내 · 재질 확인 전", "현재 제품의 오염 여부를 판단한 결과가 아닙니다. 포장의 분리배출 표시를 확인하면 해당 품목의 사용 후 공통 준비 방법을 안내할 수 있어요.");
    return withRule("PREVIEW", "사용 후 배출법 미리 보기", "아래는 나중에 사용을 마치고 내용물을 비운 뒤 참고할 공통 준비 방법입니다. 현재 제품에 세척이 필요하다는 판정이 아닙니다.");
  }
  if (a.useState === "UNUSED") return finish("PREPARATION", "미개봉·미사용 제품의 배출 준비", "사용하지 않은 제품을 빈 용기로 판정하지 않았어요. 지금 배출해야 한다면 내용물과 포장재의 처리 방법을 먼저 확인하세요. 음식물·액체의 처리 방법은 거주지 안내에 따르고, 분석을 위해 제품을 뜯지 마세요.");
  if (o.opening === "SEALED") return finish("HOLD", "개봉 상태를 다시 확인해 주세요", "사용한 물품이라는 답변과 사진의 미개봉 추정이 달라 판단을 보류했어요. 현재 상태를 확인하거나 개봉부가 보이는 사진을 선택해 주세요.");

  if (o.subject !== "PAPER_BOX") {
    if (!a.contents) return ask("contents");
    if (a.contents === "UNKNOWN") return finish("HOLD", "내용물 상태를 확인하지 못했어요", "내용물이 비었다고 가정하지 않았어요. 안전하게 확인할 수 있을 때 상태 답변을 수정해 주세요. 같은 질문을 반복하지 않습니다.");
    if (a.contents === "PRESENT") return finish("PREPARATION", "내용물 처리부터 확인해 주세요", "내용물이 남아 있는 용기는 바로 배출 가능으로 판정하지 않습니다. 내용물의 처리 방법을 거주지 안내에서 확인하고, 처리를 마친 뒤 내용물 상태를 수정해 주세요.");
    if (!a.residue) return ask("residue");
    if (a.residue === "UNKNOWN") return finish("HOLD", "잔여물 상태를 확인하지 못했어요", "사진에서 보이지 않는 부분을 깨끗하다고 판단하지 않았어요. 안전하게 볼 수 있는 범위를 확인하거나, 확인 가능한 면을 촬영해 주세요. 억지로 열거나 손을 넣지 마세요.");
    if (a.residue === "NONE_VISIBLE" && (o.interiorResidue === "VISIBLE" || o.externalResidue === "VISIBLE")) return finish("NEEDS_PHOTO", "잔여물 상태를 다시 보여 주세요", "잔여물이 없다는 답변과 사진의 잔여물 추정이 달라요. 현재 상태를 선명하게 촬영해 주세요. 이전 사진으로 깨끗함을 확정하지 않습니다.");
  }
  if (!a.material) return ask("material");
  if (!rule) return finish("HOLD", "포장재 종류를 확인해 주세요", "세부 재질을 확인하지 못해 배출 종류를 정하지 않았어요. 분리배출 표시를 확인하거나 표시가 보이는 면을 촬영해 주세요. 음료팩을 일반 종이로 간주하지 않습니다.");
  if (o.subject === "PAPER_BOX" && o.externalResidue !== "NONE_VISIBLE") return finish("HOLD", "종이상자의 표면 상태를 확인해 주세요", "오염된 상자에 일반 종이상자의 안내를 그대로 적용하지 않았어요. 표면이 선명한 사진이나 거주지의 해당 품목 안내를 확인해 주세요.");
  if (a.residue === "VISIBLE") return withRule("PREPARATION", "잔여물 제거 후 다시 확인해 주세요", "확인한 포장재의 일반적인 세척 준비 방법입니다. 세척 후에도 잔여물이 남으면 배출 가능으로 판단하지 말고 해당 품목의 지역 안내를 확인하세요.");
  return withRule("READY", "분리배출 준비 안내", "사용자 확인과 사진에서 확인 가능한 범위를 바탕으로 안내합니다. 실제 수거 가능 여부와 보이지 않는 부분의 상태를 인증한 결과는 아닙니다.", o.subject === "PAPER_BOX");
}
module.exports = { decide };
