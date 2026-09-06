package com.example.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.AiVisionRepository
import com.example.repository.AnalysisResult
import com.example.repository.ImageCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    var photo by mutableStateOf<Bitmap?>(null); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var result by mutableStateOf<AnalysisResult?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    fun showError(message: String) { error = message }
    fun select(uri: Uri, cleanup: (() -> Unit)? = null) {
        if (busy) return
        busy = true; result = null; error = null; status = "사진을 준비하고 있습니다"
        viewModelScope.launch {
            try { photo = withContext(Dispatchers.IO) { ImageCodec.read(getApplication(), uri) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { photo = null; error = "사진을 읽을 수 없습니다. 지원되는 사진을 다시 선택해 주세요." }
            finally { busy = false; cleanup?.invoke() }
        }
    }
    fun analyze() {
        val selected = photo ?: return
        if (busy) return
        val network = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val capabilities = network.getNetworkCapabilities(network.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            error = "오프라인입니다. Wi-Fi 또는 모바일 데이터를 연결한 뒤 다시 시도해 주세요."
            return
        }
        busy = true; error = null; result = null; status = "사진을 분석하고 있습니다. 서버 상태에 따라 약 2분이 걸릴 수 있어요."
        viewModelScope.launch {
            try { result = AiVisionRepository.analyzeWasteImage(selected) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "분석에 실패했습니다. 다시 시도해 주세요." }
            finally { busy = false }
        }
    }
}
