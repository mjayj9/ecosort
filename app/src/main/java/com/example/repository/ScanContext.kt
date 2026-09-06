package com.example.repository

enum class ScanPurpose(val label: String) {
    DISPOSE_NOW("지금 배출하려는 물품"), PREVIEW("사용 후 배출법 미리 보기"), UNKNOWN("아직 모르겠음")
}
enum class UseState(val label: String) {
    UNUSED("미개봉·미사용"), USED("사용한 물품"), UNKNOWN("잘 모르겠음")
}
enum class ContentsState { PRESENT, EMPTY, UNKNOWN }
enum class ResidueState { VISIBLE, NONE_VISIBLE, UNKNOWN }
enum class MaterialChoice { PET, PP, PE, METAL, GLASS, PAPER, CARTON_GENERAL, CARTON_ASEPTIC, UNKNOWN }

/** Null means unanswered; UNKNOWN is an explicit answer, never an empty/clean default. */
data class ScanAnswers(
    val purpose: ScanPurpose? = null,
    val useState: UseState? = null,
    val contents: ContentsState? = null,
    val residue: ResidueState? = null,
    val material: MaterialChoice? = null
) {
    val readyForAnalysis: Boolean get() = purpose != null && useState != null
    fun toMap(): Map<String, String> = buildMap {
        purpose?.let { put("purpose", it.name) }
        useState?.let { put("useState", it.name) }
        contents?.let { put("contents", it.name) }
        residue?.let { put("residue", it.name) }
        material?.let { put("material", it.name) }
    }
    fun choose(key: String, value: String): ScanAnswers = when (key) {
        "purpose" -> copy(purpose = ScanPurpose.valueOf(value), contents = null, residue = null)
        "useState" -> copy(useState = UseState.valueOf(value), contents = null, residue = null)
        "contents" -> copy(contents = ContentsState.valueOf(value), residue = null)
        "residue" -> copy(residue = ResidueState.valueOf(value))
        "material" -> copy(material = MaterialChoice.valueOf(value))
        else -> error("지원되지 않는 확인 항목")
    }
}

/** Identifies the image and answers a response belongs to; stale responses must be ignored. */
data class ScanTicket(val imageVersion: Long, val answerVersion: Long)
data class ScanDraft(
    val imageVersion: Long = 0,
    val answerVersion: Long = 0,
    val answers: ScanAnswers = ScanAnswers()
) {
    val ticket: ScanTicket get() = ScanTicket(imageVersion, answerVersion)
    fun newPhoto() = ScanDraft(imageVersion + 1)
    fun choose(key: String, value: String) = copy(answerVersion = answerVersion + 1, answers = answers.choose(key, value))
    fun accepts(ticket: ScanTicket) = this.ticket == ticket
}
