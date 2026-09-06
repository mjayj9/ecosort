package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.util.GlobalState

@Composable
fun MainTabScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val apartmentId = GlobalState.apartmentId

    Scaffold(
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "스캐너") },
                    label = { Text("스캐너") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Leaderboard, contentDescription = "대시보드") },
                    label = { Text("단지 · 계획") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Store, contentDescription = "포인트샵") },
                    label = { Text("보상 · 계획") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "설정") },
                    label = { Text("설정") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
            }
        }
    }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            when (selectedTab) {
                0 -> AiScannerScreen()
                1 -> PlannedFeature("단지 순위", "실제 배출 인증과 단지별 집계가 검증된 후 제공할 예정입니다. 현재 공개할 실적이나 순위가 없습니다.")
                2 -> PlannedFeature("포인트와 쿠폰", "배출 인증, 보상 재원, 실제 쿠폰 공급을 확인한 후 제공할 예정입니다. 사진 분석만으로 포인트를 지급하지 않습니다.")
                3 -> SettingsScreen(onLogout = onLogout)
            }
        }
    }
}


@Composable
private fun PlannedFeature(title: String, description: String) {
    androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
        Text("향후 계획", style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description)
    }
}
