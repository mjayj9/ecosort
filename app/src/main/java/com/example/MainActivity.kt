package com.example

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.tasks.await
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ApartmentSelectionScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MainTabScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    getSharedPreferences("ecosort_prefs", MODE_PRIVATE).edit().remove("gemini_api_key").apply()
    try { com.example.repository.FirebaseBackend.initialize(this) } catch (_: Exception) { }
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        if (BuildConfig.USE_FIREBASE_EMULATORS) LocalDemoEntry() else AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { isNewUser -> 
                    if (isNewUser) {
                        navController.navigate("apartmentSelection") 
                    } else {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                }
            )
        }
        composable("apartmentSelection") {
            ApartmentSelectionScreen(
                onApartmentSelected = { aptName ->
                    coroutineScope.launch {
                        com.example.repository.FirestoreRepository.saveUserApartment(context, aptName)
                    }
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainTabScreen(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}



@Composable
private fun LocalDemoEntry() {
    var ready by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var attempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(attempt) {
        error = null
        try {
            kotlinx.coroutines.withTimeout(15000) {
                val auth = com.example.repository.FirebaseBackend.auth
                // Auth emulator data can be reset while Android retains the previous account.
                try { auth.currentUser?.reload()?.await() }
                catch (_: com.google.firebase.auth.FirebaseAuthInvalidUserException) { auth.signOut() }
                auth.signInAnonymously().await()
            }
            ready = true
        } catch (_: Exception) { error = "로컬 인증 서버에 연결하지 못했습니다. PC에서 로컬 서버를 실행한 뒤 다시 시도해 주세요." }
    }
    if (ready) MainTabScreen(onLogout = { ready = false; attempt++ })
    else androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(error ?: "로컬 Firebase 인증 연결 중…")
        if (error != null) androidx.compose.material3.Button(onClick = { attempt++ }) { Text("연결 재시도") }
    }
}
