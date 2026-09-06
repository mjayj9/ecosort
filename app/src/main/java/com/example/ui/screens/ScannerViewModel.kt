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
import com.example.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    var photo by mutableStateOf<Bitmap?>(null); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var result by mutableStateOf<GuidanceResult?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var draft by mutableStateOf(ScanDraft()); private set
    private var questionHistory by mutableStateOf<List<GuidanceQuestion>>(emptyList())
    val editableQuestions: List<GuidanceQuestion> get() = questionHistory.filter { draft.answers.toMap().containsKey(it.key) && questionApplies(it, draft.answers) && result?.questions?.none { current -> current.key == it.key } == true }
    private var acceptedTicket: ScanTicket? = null
    private var analysisRequestId = UUID.randomUUID().toString()
    private var correctionRequestId = UUID.randomUUID().toString()
    val pendingChanges: Boolean get() = result != null && acceptedTicket != draft.ticket
    val canSubmit: Boolean get() = photo != null && draft.answers.readyForAnalysis && !busy && (result == null || pendingChanges)

    fun showError(message: String) { error = message }
    fun choose(key: String, value: String) {
        if (busy) return
        draft = draft.choose(key, value)
        error = null
        correctionRequestId = UUID.randomUUID().toString()
        if (result == null) analysisRequestId = UUID.randomUUID().toString()
    }
    fun select(uri: Uri, cleanup: (() -> Unit)? = null) {
        if (busy) return
        draft = draft.newPhoto()
        questionHistory = emptyList()
        acceptedTicket = null; result = null; error = null; photo = null
        analysisRequestId = UUID.randomUUID().toString()
        correctionRequestId = UUID.randomUUID().toString()
        val ticket = draft.ticket
        busy = true; status = "사진을 준비하고 있어요"
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { ImageCodec.read(getApplication(), uri) }
                if (draft.accepts(ticket)) photo = loaded
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "사진을 읽을 수 없습니다. 지원되는 사진을 다시 선택해 주세요." }
            finally { busy = false; cleanup?.invoke() }
        }
    }
    fun submit() {
        val selected = photo ?: return
        if (!canSubmit) return
        val network = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val capabilities = network.getNetworkCapabilities(network.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            error = "오프라인입니다. 사진과 선택한 답변은 유지됩니다. 연결한 뒤 다시 시도해 주세요."
            return
        }
        val previous = result
        val ticket = draft.ticket
        val answers = draft.answers
        busy = true; error = null
        status = if (previous == null) "사진에서 확인 가능한 정보를 분석하고 있어요. 서버 상태에 따라 약 2분이 걸릴 수 있어요."
                 else "사진을 다시 전송하지 않고 선택한 상태로 안내를 갱신하고 있어요."
        viewModelScope.launch {
            try {
                val response = if (previous == null) AiVisionRepository.analyzeWasteImage(selected, answers, analysisRequestId)
                    else AiVisionRepository.resolveWasteState(previous.scanId, previous.revision, answers, correctionRequestId)
                if (draft.accepts(ticket)) {
                    questionHistory = (questionHistory + response.questions).distinctBy { it.key }
                    result = response
                    acceptedTicket = ticket
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (draft.accepts(ticket)) error = e.message ?: "안내를 완료하지 못했습니다. 다시 시도해 주세요." }
            finally { busy = false }
        }
    }
    fun analyzeAgain() {
        if (busy || photo == null || !draft.answers.readyForAnalysis) return
        result = null; acceptedTicket = null
        analysisRequestId = UUID.randomUUID().toString()
        correctionRequestId = UUID.randomUUID().toString()
        submit()
    }
}
