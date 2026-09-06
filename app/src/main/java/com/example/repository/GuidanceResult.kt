package com.example.repository

import java.net.URI

enum class GuidanceStatus { NEEDS_CONFIRMATION, NEEDS_PHOTO, PREVIEW, PREPARATION, READY, HOLD }
enum class EvidenceSource(val label: String) { USER("사용자 확인"), AI("AI 관찰·추정"), LIMIT("확인하지 못한 정보") }
data class GuidanceEvidence(val label: String, val value: String, val source: EvidenceSource)
data class GuidanceChoice(val value: String, val label: String)
data class GuidanceQuestion(val key: String, val title: String, val choices: List<GuidanceChoice>)
data class GuidanceSource(val title: String, val url: String)
data class GuidanceResult(
    val status: GuidanceStatus, val title: String, val itemName: String, val materialLabel: String,
    val summary: String, val evidence: List<GuidanceEvidence>, val limits: List<String>,
    val questions: List<GuidanceQuestion>, val steps: List<String>, val sources: List<GuidanceSource>,
    val scanId: String, val revision: Int, val model: String, val ruleId: String?, val ruleVersion: String
) {
    val modelLabel: String get() = when (model) {
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning" -> "Nemotron 3 Nano Omni"
        "moonshotai/kimi-k3" -> "Kimi K3"
        else -> "확인되지 않은 모델"
    }
    companion object {
        fun fromMap(raw: Map<*, *>): GuidanceResult {
            fun text(map: Map<*, *>, key: String, max: Int = 1200): String {
                val v = map[key] as? String ?: error("안내 필드 누락")
                require(v.isNotBlank() && v.length <= max)
                return v.trim()
            }
            fun rows(key: String, max: Int = 12): List<*> {
                val v = raw[key] as? List<*> ?: error("안내 목록 누락")
                require(v.size <= max)
                return v
            }
            fun lines(key: String): List<String> = rows(key).map { v ->
                require(v is String && v.isNotBlank() && v.length <= 1200)
                v
            }
            require((raw["schemaVersion"] as? Number)?.toDouble() == 2.0)
            require(raw["guideScope"] == "COMMON_PREPARATION")
            require(raw.containsKey("decision") && raw["decision"] == null)
            require(raw.containsKey("contaminationScore") && raw["contaminationScore"] == null)
            val revision = (raw["revision"] as? Number)?.toDouble() ?: error("안내 버전 누락")
            require(revision.isFinite() && revision in 0.0..20.0 && revision % 1.0 == 0.0)
            val status = GuidanceStatus.valueOf(text(raw, "status", 30))
            val evidence = rows("evidence").map {
                require(it is Map<*, *>)
                GuidanceEvidence(text(it, "label", 100), text(it, "value", 600), EvidenceSource.valueOf(text(it, "source", 10)))
            }
            val questions = rows("questions", 2).map {
                require(it is Map<*, *>)
                val key = text(it, "key", 30)
                val choices = it["choices"] as? List<*> ?: error("선택지 누락")
                require(choices.isNotEmpty() && choices.size <= 9)
                val parsed = choices.map { choice ->
                    require(choice is Map<*, *>)
                    val value = text(choice, "value", 30)
                    ScanAnswers().choose(key, value) // Reject keys and enum values outside the approved answer contract.
                    GuidanceChoice(value, text(choice, "label", 160))
                }
                require(parsed.map { c -> c.value }.distinct().size == parsed.size)
                require(parsed.any { c -> c.value == "UNKNOWN" })
                GuidanceQuestion(key, text(it, "title", 300), parsed)
            }
            require((status == GuidanceStatus.NEEDS_CONFIRMATION) == questions.isNotEmpty())
            require(questions.map { it.key }.distinct().size == questions.size)
            val steps = lines("steps")
            if (status in setOf(GuidanceStatus.HOLD, GuidanceStatus.NEEDS_PHOTO, GuidanceStatus.NEEDS_CONFIRMATION)) require(steps.isEmpty())
            val sources = rows("sources", 4).map {
                require(it is Map<*, *>)
                val url = text(it, "url", 500)
                val uri = URI(url)
                require(uri.scheme == "https" && uri.host in setOf("www.korea.kr", "me.go.kr", "news.seoul.go.kr") && uri.userInfo == null && uri.port == -1)
                GuidanceSource(text(it, "title", 200), url)
            }
            val ruleId = raw["ruleId"]?.let { require(it is String && it.matches(Regex("[a-z-]{1,80}"))); it }
            if (steps.isNotEmpty()) require(ruleId != null && sources.isNotEmpty())
            return GuidanceResult(status, text(raw, "title", 160), text(raw, "itemName", 120), text(raw, "materialLabel", 180),
                text(raw, "summary"), evidence, lines("limits"), questions, steps, sources,
                text(raw, "scanId", 100), revision.toInt(), text(raw, "model", 120), ruleId, text(raw, "ruleVersion", 60))
        }
    }
}
