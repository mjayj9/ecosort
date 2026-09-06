package com.example.repository

enum class Decision(val title: String) {
    RECYCLE("분리배출 가능"), WASH_THEN_RECYCLE("씻은 뒤 분리배출"),
    GENERAL_WASTE("일반쓰레기 배출"), UNKNOWN("판단 보류 · 재촬영 필요")
}
data class AnalysisResult(
    val itemName: String, val material: String, val contaminationScore: Int,
    val confidence: Double, val decision: Decision, val washSteps: List<String>,
    val disposalGuide: String, val reason: String, val warnings: List<String>, val scanId: String, val model: String = "moonshotai/kimi-k3"
) {
    val modelLabel: String get() = when (model) {
        "moonshotai/kimi-k3" -> "Kimi K3"
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning" -> "Nemotron 3 Nano Omni"
        else -> "확인되지 않은 모델"
    }
    companion object {
        fun fromMap(raw: Map<*, *>): AnalysisResult {
            fun text(key: String, max: Int = 1200): String {
                val value = raw[key] as? String ?: error("응답 필드 누락")
                require(value.isNotBlank() && value.length <= max)
                return value.trim()
            }
            fun lines(key: String): List<String> {
                val values = raw[key] as? List<*> ?: error("응답 목록 누락")
                require(values.size <= 12)
                return values.map { require(it is String && it.isNotBlank() && it.length <= 600); (it as String).trim() }
            }
            val score = (raw["contaminationScore"] as? Number)?.toDouble() ?: error("오염도 누락")
            val confidence = (raw["confidence"] as? Number)?.toDouble() ?: error("신뢰도 누락")
            require(score.isFinite() && score in 0.0..100.0 && score % 1 == 0.0)
            require(confidence.isFinite() && confidence in 0.0..1.0)
            var decision = Decision.valueOf(text("decision", 30))
            var steps = lines("washSteps")
            require(decision != Decision.WASH_THEN_RECYCLE || steps.isNotEmpty())
            var guide = text("disposalGuide")
            if (confidence < 0.7 || decision == Decision.UNKNOWN) {
                decision = Decision.UNKNOWN
                steps = emptyList()
                guide = "한 번에 물품 하나만, 오염된 면과 용기 내부가 선명하게 보이도록 다시 촬영해 주세요."
            }
            return AnalysisResult(text("itemName", 120), text("material", 120), score.toInt(), confidence,
                decision, steps, guide, text("reason"), lines("warnings"), text("scanId", 150), text("model", 120))
        }
    }
}
