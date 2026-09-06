package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.repository.AiVisionRepository
import com.example.repository.FirebaseBackend
import com.example.util.GlobalState
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun logout() {
        FirebaseBackend.auth.signOut()
        GlobalState.userEmail = ""; GlobalState.apartmentId = ""; GlobalState.apartmentName = ""
        GlobalState.currentPoints = 0; GlobalState.currentCount = 0; GlobalState.isAdmin = false
        onLogout()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Text(if (BuildConfig.USE_FIREBASE_EMULATORS) "로컬 시연 계정 · 실제 Firebase Auth 에뮬레이터" else "계정: ${GlobalState.userEmail}")
        Text("AI: NVIDIA NIM · 사용 모델은 분석 결과에 표시")
        Text("사진은 1024px 이내로 축소·JPEG 압축 후 분석 서버로 전송합니다. 앱 서버는 사진을 저장하지 않고 분석 기록 번호와 판단·신뢰도를 기록합니다.")
        Text("오염도와 신뢰도는 AI 추정이며, 정확도를 검증한 수치가 아닙니다. 거주지 배출 안내를 우선하세요.")
        Text("포인트·쿠폰·단지 순위·광고는 현재 MVP에서 제공하지 않습니다.")
        Button(onClick = ::logout, enabled = !deleting) { Text(if (BuildConfig.USE_FIREBASE_EMULATORS) "로컬 인증 다시 연결" else "로그아웃") }
        if (!BuildConfig.USE_FIREBASE_EMULATORS) OutlinedButton(onClick = { confirmDelete = true }, enabled = !deleting) { Text("회원 탈퇴") }
        if (message.isNotBlank()) Text(message)
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("회원 탈퇴") },
        text = { Text("프로필을 삭제하고 사용 기록을 익명화합니다. 계정 삭제를 진행할까요?") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false; deleting = true
            scope.launch {
                val result = JSONObject(AiVisionRepository.deleteAccount())
                deleting = false
                if (result.optBoolean("success")) logout() else message = result.optString("error", "회원 탈퇴를 완료하지 못했습니다.")
            }
        }) { Text("탈퇴") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } })
}
