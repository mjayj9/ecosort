package com.example
import com.example.repository.AnalysisResult
import com.example.repository.Decision
import org.junit.Assert.*
import org.junit.Test

class AnalysisResultTest {
    private fun valid(): Map<String, Any> = mapOf("itemName" to "용기", "material" to "PP", "contaminationScore" to 42,
        "confidence" to 0.9, "decision" to "WASH_THEN_RECYCLE", "washSteps" to listOf("비우기", "헹구기"),
        "disposalGuide" to "배출 기준 확인", "reason" to "잔여물 관찰", "warnings" to listOf("지역 확인"), "scanId" to "test-scan", "model" to "moonshotai/kimi-k3")
    @Test fun dirtyContainerShowsWashing() { assertEquals(2, AnalysisResult.fromMap(valid()).washSteps.size) }
    @Test fun lowConfidenceOverridesRecycle() {
        val result = AnalysisResult.fromMap(valid() + ("confidence" to 0.5))
        assertEquals(Decision.UNKNOWN, result.decision); assertTrue(result.washSteps.isEmpty()); assertTrue(result.disposalGuide.contains("다시 촬영"))
    }
    @Test fun requiredFieldsCannotSilentlyDefault() {
        valid().keys.forEach { key -> assertThrows(Exception::class.java) { AnalysisResult.fromMap(valid() - key) } }
    }
    @Test fun invalidValuesAreRejected() {
        listOf("contaminationScore" to 101, "contaminationScore" to 2.3, "confidence" to Double.NaN,
            "confidence" to "0.8", "decision" to "PASS", "washSteps" to emptyList<String>()).forEach {
            assertThrows(Exception::class.java) { AnalysisResult.fromMap(valid() + it) }
        }
    }
    @Test fun cleanContainerKeepsDecision() { assertEquals(Decision.RECYCLE, AnalysisResult.fromMap(valid() + mapOf("decision" to "RECYCLE", "washSteps" to emptyList<String>())).decision) }
}
