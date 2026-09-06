package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.repository.*

@Composable
private fun AnswerRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
    }
}

@Composable
fun ScanContextCard(answers: ScanAnswers, enabled: Boolean, onChoose: (String, String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("어떤 안내가 필요한가요?", fontWeight = FontWeight.Bold)
            ScanPurpose.entries.forEach { option -> AnswerRow(option.label, answers.purpose == option, enabled) { onChoose("purpose", option.name) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("물품의 현재 상태는 어떤가요?", fontWeight = FontWeight.Bold)
            UseState.entries.forEach { option -> AnswerRow(option.label, answers.useState == option, enabled) { onChoose("useState", option.name) } }
        }
    }
}

@Composable
fun GuidanceCard(result: GuidanceResult, answers: ScanAnswers, enabled: Boolean, pendingChanges: Boolean = false,
                 onChoose: (String, String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val attention = result.status in setOf(GuidanceStatus.HOLD, GuidanceStatus.NEEDS_PHOTO, GuidanceStatus.NEEDS_CONFIRMATION) || pendingChanges
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
        if (attention) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (pendingChanges) "답변이 바뀌었어요 · 안내를 갱신해 주세요" else result.title,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!pendingChanges) {
                Text(result.itemName + " · " + result.materialLabel, style = MaterialTheme.typography.titleMedium)
                Text(result.summary)
            }
            result.questions.filter { questionApplies(it, answers) }.forEach { question ->
                Text(question.title, fontWeight = FontWeight.Bold)
                question.choices.forEach { choice ->
                    AnswerRow(choice.label, answers.toMap()[question.key] == choice.value, enabled) { onChoose(question.key, choice.value) }
                }
            }
            if (!pendingChanges) {
                Text("확인한 정보와 한계", fontWeight = FontWeight.Bold)
                result.evidence.forEach { item ->
                    Column {
                        Text(item.label + " · " + item.source.label, style = MaterialTheme.typography.labelMedium)
                        Text(item.value)
                    }
                }
                if (result.steps.isNotEmpty()) {
                    Text(if (result.status == GuidanceStatus.PREVIEW) "사용 후 참고할 공통 준비 방법" else "다음 준비 방법", fontWeight = FontWeight.Bold)
                    result.steps.forEachIndexed { i, step -> Text((i + 1).toString() + ". " + step) }
                }
                Text("배출 전에 확인할 사항", fontWeight = FontWeight.Bold)
                result.limits.forEach { Text("• " + it) }
                if (result.sources.isNotEmpty()) {
                    Text("안내 근거", fontWeight = FontWeight.Bold)
                    result.sources.forEach { source ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(source.url) } }, contentPadding = PaddingValues(0.dp)) { Text(source.title) }
                    }
                }
                Text("분석 모델: " + result.modelLabel + " · NVIDIA NIM", style = MaterialTheme.typography.labelSmall)
                Text("분석 기록: " + result.scanId + " · 안내 " + result.revision, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
