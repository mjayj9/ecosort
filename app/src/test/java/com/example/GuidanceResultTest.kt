package com.example

import com.example.repository.*
import org.junit.Assert.*
import org.junit.Test

fun guidanceFixture(): MutableMap<String, Any?> = mutableMapOf(
    "schemaVersion" to 2, "status" to "HOLD", "title" to "내부 상태를 확인하지 못했어요", "itemName" to "음료팩",
    "materialLabel" to "세부 재질 확인 전", "summary" to "보이지 않는 내부를 깨끗하다고 판단하지 않았어요.",
    "decision" to null, "contaminationScore" to null, "guideScope" to "COMMON_PREPARATION",
    "ruleVersion" to "2026-09-06.1", "ruleId" to null, "scanId" to "unit-test-scan", "revision" to 0,
    "model" to "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning", "questions" to emptyList<Any>(),
    "steps" to emptyList<String>(), "sources" to emptyList<Any>(), "limits" to listOf("거주지 수거 기준을 확인하세요."),
    "evidence" to listOf(mapOf("label" to "사용 상태", "value" to "미개봉·미사용", "source" to "USER"))
)
class GuidanceResultTest {
    @Test fun acceptsExplicitNullWithoutConvertingToZeroOrRecycle() {
        val r = GuidanceResult.fromMap(guidanceFixture())
        assertEquals(GuidanceStatus.HOLD, r.status)
        assertEquals(EvidenceSource.USER, r.evidence.single().source)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsAClaimedFinalDecisionInCommonGuidance() {
        GuidanceResult.fromMap(guidanceFixture().apply { put("decision", "RECYCLE") })
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNumericalContamination() {
        GuidanceResult.fromMap(guidanceFixture().apply { put("contaminationScore", 30) })
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsLegacyContract() {
        GuidanceResult.fromMap(guidanceFixture().apply { put("schemaVersion", 1) })
    }
    @Test(expected = IllegalArgumentException::class) fun unknownChoiceMustRemainAvailable() {
        GuidanceResult.fromMap(guidanceFixture().apply {
            put("status", "NEEDS_CONFIRMATION")
            put("questions", listOf(mapOf("key" to "contents", "title" to "내용물 확인", "choices" to listOf(mapOf("value" to "EMPTY", "label" to "비었어요")))))
        })
    }
    @Test(expected = IllegalArgumentException::class) fun sourceLinksCannotOpenAnArbitraryProviderUrl() {
        GuidanceResult.fromMap(guidanceFixture().apply { put("sources", listOf(mapOf("title" to "잘못된 주소", "url" to "https://example.com/"))) })
    }
}
