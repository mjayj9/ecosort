"use strict";

const RULE_VERSION = "2026-09-07.1";
const LOCAL_NOTICE = "지자체·공동주택별 수거 기준은 확인하지 않았습니다. 아래 내용은 공통 준비 안내이며, 최종 배출 장소와 수거 가능 여부는 거주지 안내를 확인하세요.";
const SOURCES = Object.freeze({
  preparation: { title: "정책브리핑 · 품목별 분리배출 준비 방법 (2021)", url: "https://www.korea.kr/news/policyFocusView.do?newsId=148889163&pkgId=49500758" },
  carton: { title: "환경부 · 종이팩·금속캔 분리배출 안내 (2022)", url: "https://me.go.kr/m/mob/board/read.do?boardId=1525580&boardMasterId=53&menuId=58" },
  collection: { title: "서울시 · 종이팩 전용수거함 안내 (2025, 서울시 사례)", url: "https://news.seoul.go.kr/env/archives/564359" },
});
const SUBJECTS = Object.freeze({ CARTON: "음료팩", PET_BOTTLE: "음료용 페트병", PLASTIC_CONTAINER: "플라스틱 식품 용기", METAL_CAN: "음료·식품용 금속캔", GLASS_BOTTLE: "음료·식품용 유리병", PAPER_BOX: "종이상자", OTHER: "지원 범위 밖의 물품", UNKNOWN: "물품 확인 불가" });
const MATERIALS = Object.freeze({ PET: "페트(PET)", PP: "폴리프로필렌(PP)", PE: "폴리에틸렌(PE)", METAL: "금속", GLASS: "유리", PAPER: "코팅되지 않은 종이상자", CARTON_GENERAL: "일반팩(살균팩)", CARTON_ASEPTIC: "멸균팩", UNKNOWN: "세부 재질 확인 전" });
const MATERIAL_OPTIONS = Object.freeze({
  CARTON: ["CARTON_GENERAL", "CARTON_ASEPTIC", "UNKNOWN"], PET_BOTTLE: ["PET", "UNKNOWN"],
  PLASTIC_CONTAINER: ["PET", "PP", "PE", "UNKNOWN"], METAL_CAN: ["METAL", "UNKNOWN"],
  GLASS_BOTTLE: ["GLASS", "UNKNOWN"], PAPER_BOX: ["PAPER", "UNKNOWN"],
});
// Curated common preparation only. None of these entries certifies local collection acceptance.
function guideFor(material) {
  if (["CARTON_GENERAL", "CARTON_ASEPTIC"].includes(material)) return {
    id: "carton-preparation", sources: [SOURCES.carton, SOURCES.collection],
    steps: ["사용 후 내용물을 비운 음료팩은 안쪽의 잔여물을 물로 헹궈 제거하세요.", "물기를 말리고, 쉽게 분리되는 다른 재질의 부속품은 분리하세요.", "일반 종이와 섞어도 된다고 가정하지 말고, 해당 종류의 종이팩 수거 방법을 확인하세요."],
  };
  if (["PET", "PP", "PE"].includes(material)) return {
    id: "food-plastic-preparation", sources: [SOURCES.preparation],
    steps: ["내용물을 비운 식품·음료 용기의 잔여물을 물로 헹궈 제거하세요.", "쉽게 분리되는 라벨 등 다른 재질의 부속품을 확인하세요.", "생수·음료용 무색 페트병과 다른 플라스틱은 수거 구분이 다를 수 있으므로 표시와 거주지 안내를 확인하세요."],
  };
  if (material === "METAL") return {
    id: "food-can-preparation", sources: [SOURCES.preparation],
    steps: ["내용물을 비운 음료·식품 캔의 잔여물을 물로 헹궈 제거하세요.", "쉽게 분리되는 플라스틱 부속품은 분리하세요. 날카로운 절단면에는 손을 넣지 마세요.", "거주지의 금속캔 수거 안내를 확인하세요."],
  };
  if (material === "GLASS") return {
    id: "food-bottle-preparation", sources: [SOURCES.preparation],
    steps: ["온전한 음료·식품 유리병의 내용물을 비우고 잔여물을 물로 헹궈 제거하세요.", "빈용기보증금 대상 표시가 있으면 반환 방법을 확인하세요.", "깨진 병·도자기·내열유리는 이 안내의 대상이 아닙니다. 거주지의 해당 품목 안내를 확인하세요."],
  };
  if (material === "PAPER") return {
    id: "paper-box-preparation", sources: [SOURCES.preparation],
    steps: ["코팅되지 않은 종이상자는 부착된 테이프 등 쉽게 분리되는 다른 재질을 제거하세요.", "상자를 접고 거주지의 종이상자 수거 안내를 확인하세요.", "종이팩·보냉상자·음식물로 오염된 상자에는 이 안내를 그대로 적용하지 마세요."],
  };
  return null;
}
module.exports = { RULE_VERSION, LOCAL_NOTICE, SOURCES, SUBJECTS, MATERIALS, MATERIAL_OPTIONS, guideFor };
