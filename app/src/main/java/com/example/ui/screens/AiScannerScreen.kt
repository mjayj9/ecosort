package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.repository.Decision
import java.io.File

@Composable
fun AiScannerScreen(scanner: ScannerViewModel = viewModel()) {
    val context = LocalContext.current
    var agreed by rememberSaveable { mutableStateOf(false) }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile?.let(::File)
        if (success && file != null) scanner.select(FileProvider.getUriForFile(context, "${context.packageName}.photos", file)) { file.delete() }
        else file?.delete()
        pendingCameraFile = null
    }
    fun openCamera() {
        try {
            val folder = File(context.cacheDir, "scan_photos").apply { mkdirs() }
            val file = File.createTempFile("ecosort_", ".jpg", folder)
            pendingCameraFile = file.absolutePath
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.photos", file))
        } catch (_: Exception) { scanner.showError("카메라를 열 수 없습니다. 사진 선택을 이용해 주세요.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else scanner.showError("카메라 권한이 필요합니다. 사진 선택은 권한 없이 이용할 수 있어요.")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> uri?.let { scanner.select(it) } }
    val result = scanner.result
    val scroll = rememberScrollState()
    LaunchedEffect(result, scanner.error) { if (result != null || scanner.error != null) scroll.animateScrollTo(0) }

    Column(Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("EcoSort · 에코소트", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("씻어서 재활용할까요?", style = MaterialTheme.typography.titleLarge)
        Text("물품 한 개와 오염된 면을 밝게 찍어 주세요. 용기는 내부가 보이게 열어 주세요.")
        if (BuildConfig.USE_FIREBASE_EMULATORS) Text("로컬 Firebase · 실제 NVIDIA NIM 분석", style = MaterialTheme.typography.labelMedium)

        scanner.error?.let { message ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("분석을 완료하지 못했어요", fontWeight = FontWeight.Bold)
                    Text(message)
                    if (scanner.photo != null) Button(onClick = scanner::analyze, enabled = !scanner.busy && agreed) { Text("같은 사진으로 재시도") }
                }
            }
        }
        if (result != null) AnalysisResultCard(result)
        scanner.photo?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "분석할 쓰레기 사진", modifier = Modifier.fillMaxWidth().height(210.dp), contentScale = ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                else permission.launch(Manifest.permission.CAMERA)
            }, enabled = !scanner.busy, modifier = Modifier.weight(1f)) { Text(if (result == null) "사진 촬영" else "다시 촬영") }
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !scanner.busy, modifier = Modifier.weight(1f)) { Text("사진 선택") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(modifier = Modifier.semantics { contentDescription = "사진 전송 동의" }, checked = agreed, onCheckedChange = { agreed = it }, enabled = !scanner.busy)
            Text("분석을 위해 사진이 Firebase 서버를 거쳐 NVIDIA AI로 전송되는 데 동의합니다. 얼굴·주소 등 개인정보는 사진에서 빼 주세요.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = scanner::analyze, enabled = agreed && scanner.photo != null && !scanner.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (scanner.busy) "분석 중…" else "AI로 분석하기")
        }
        if (scanner.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(scanner.status)
        }
        Text("지자체·공동주택별 배출 기준이 다를 수 있습니다. 거주지 안내를 우선 확인하세요.", style = MaterialTheme.typography.bodySmall)
        Text("사진 분석은 실제 배출 인증이 아닙니다. 현재 포인트는 지급되지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AnalysisResultCard(result: com.example.repository.AnalysisResult) {
            Card(colors = CardDefaults.cardColors(containerColor = if (result.decision == Decision.UNKNOWN) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(result.decision.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${result.itemName} · ${result.material}", style = MaterialTheme.typography.titleMedium)
                    if (result.decision != Decision.UNKNOWN) {
                        Text("시각적 오염 추정 ${result.contaminationScore}/100")
                        LinearProgressIndicator(progress = { result.contaminationScore / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    Text("AI 자체 신뢰도 ${(result.confidence * 100).toInt()}% · 검증된 정확도가 아닙니다", style = MaterialTheme.typography.bodySmall)
                    Text("세척 방법", fontWeight = FontWeight.Bold)
                    if (result.washSteps.isEmpty()) Text(if (result.decision == Decision.UNKNOWN) "판단을 보류했습니다. 먼저 재촬영해 주세요." else "추가 세척 단계가 제시되지 않았습니다. 아래 배출 안내를 확인하세요.")
                    else result.washSteps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                    Text("배출 방법", fontWeight = FontWeight.Bold)
                    Text(result.disposalGuide)
                    Text("판단 근거", fontWeight = FontWeight.Bold)
                    Text(result.reason)
                    Text("주의사항", fontWeight = FontWeight.Bold)
                    result.warnings.forEach { Text("• $it") }
                    Text("분석 모델: ${result.modelLabel} · NVIDIA NIM", style = MaterialTheme.typography.labelSmall)
                    Text("분석 기록: ${result.scanId}", style = MaterialTheme.typography.labelSmall)
                }
            }
}
