package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import java.io.File

@Composable
fun AiScannerScreen(scanner: ScannerViewModel = viewModel()) {
    val context = LocalContext.current
    var agreed by rememberSaveable { mutableStateOf(false) }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile?.let(::File)
        if (success && file != null) scanner.select(FileProvider.getUriForFile(context, context.packageName + ".photos", file)) { file.delete() }
        else file?.delete()
        pendingCameraFile = null
    }
    fun openCamera() {
        try {
            val folder = File(context.cacheDir, "scan_photos").apply { mkdirs() }
            val file = File.createTempFile("ecosort_", ".jpg", folder)
            pendingCameraFile = file.absolutePath
            camera.launch(FileProvider.getUriForFile(context, context.packageName + ".photos", file))
        } catch (_: Exception) { scanner.showError("카메라를 열 수 없습니다. 사진 선택을 이용해 주세요.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else scanner.showError("카메라 권한이 필요합니다. 사진 선택은 권한 없이 이용할 수 있어요.")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { scanner.select(it) } }
    val result = scanner.result
    val scroll = rememberScrollState()
    LaunchedEffect(result, scanner.error) { if (result != null || scanner.error != null) scroll.animateScrollTo(0) }
    Column(Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("EcoSort · 에코소트", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("물품 상태를 확인하고 배출을 준비해요", style = MaterialTheme.typography.titleMedium)
        Text("물품 한 개를 밝고 선명하게 보여 주세요. 미개봉 제품은 그대로 촬영해도 됩니다.")
        if (BuildConfig.USE_FIREBASE_EMULATORS) Text("로컬 Firebase · 실제 NVIDIA NIM 분석", style = MaterialTheme.typography.labelMedium)
        scanner.error?.let { message ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("안내를 완료하지 못했어요", fontWeight = FontWeight.Bold)
                    Text(message)
                    Button(onClick = scanner::submit, enabled = scanner.canSubmit && agreed) { Text("선택한 내용으로 재시도") }
                    if (result != null) OutlinedButton(onClick = scanner::analyzeAgain, enabled = !scanner.busy && agreed) { Text("현재 사진 다시 분석") }
                }
            }
        }
        if (scanner.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(scanner.status)
        }
        if (result != null) {
            GuidanceCard(result, scanner.draft.answers, !scanner.busy, scanner.pendingChanges, scanner::choose)
            AnswerCorrectionCard(scanner.editableQuestions, scanner.draft.answers, !scanner.busy, scanner::choose)
            if (scanner.pendingChanges) Button(onClick = scanner::submit, enabled = scanner.canSubmit && agreed, modifier = Modifier.fillMaxWidth()) {
                Text("확인한 상태로 안내 갱신")
            }
        }
        scanner.photo?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "분석할 물품 사진", modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Fit) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                else permission.launch(Manifest.permission.CAMERA)
            }, enabled = !scanner.busy, modifier = Modifier.weight(1f)) { Text(if (scanner.photo == null) "사진 촬영" else "다른 사진 촬영") }
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !scanner.busy, modifier = Modifier.weight(1f)) { Text("사진 선택") }
        }
        if (scanner.photo != null) {
            ScanContextCard(scanner.draft.answers, !scanner.busy, scanner::choose)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(modifier = Modifier.semantics { contentDescription = "사진 전송 동의" }, checked = agreed, onCheckedChange = { agreed = it }, enabled = !scanner.busy)
                Text("사진이 Firebase 서버를 거쳐 NVIDIA AI로 전송되는 데 동의합니다. 얼굴·주소 등 개인정보는 사진에서 빼 주세요.", style = MaterialTheme.typography.bodySmall)
            }
            if (result == null) Button(onClick = scanner::submit, enabled = scanner.canSubmit && agreed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (scanner.busy) "분석 중…" else "상태를 확인하고 분석하기")
            }
            if (result != null && !scanner.pendingChanges) OutlinedButton(onClick = scanner::analyzeAgain, enabled = !scanner.busy && agreed, modifier = Modifier.fillMaxWidth()) { Text("현재 사진 다시 분석") }
        }
        Text("이 안내는 사진 관찰과 사용자 확인을 바탕으로 합니다. 지자체·공동주택의 수거 기준을 확인하세요.", style = MaterialTheme.typography.bodySmall)
        Text("사진 분석은 실제 배출 인증이 아닙니다. 현재 포인트는 지급되지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }
}
