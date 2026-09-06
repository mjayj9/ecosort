package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.repository.*

fun questionApplies(question: GuidanceQuestion, answers: ScanAnswers): Boolean = when (question.key) {
    "contents" -> answers.purpose == ScanPurpose.DISPOSE_NOW && answers.useState == UseState.USED
    "residue" -> answers.purpose == ScanPurpose.DISPOSE_NOW && answers.useState == UseState.USED && answers.contents == ContentsState.EMPTY
    "material" -> answers.purpose != ScanPurpose.UNKNOWN && answers.useState != UseState.UNKNOWN
    else -> true
}

@Composable
fun AnswerCorrectionCard(questions: List<GuidanceQuestion>, answers: ScanAnswers, enabled: Boolean, onChoose: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (questions.isEmpty()) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, enabled = enabled) { Text(if (expanded) "상태 답변 수정 닫기" else "내용물·잔여물·재질 답변 수정") }
            if (expanded) questions.forEach { question ->
                Text(question.title)
                question.choices.forEach { choice ->
                    OutlinedButton(onClick = { onChoose(question.key, choice.value) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text((if (answers.toMap()[question.key] == choice.value) "선택됨 · " else "") + choice.label)
                    }
                }
            }
        }
    }
}
