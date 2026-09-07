package com.example.vision

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.ImageCodec
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class RecognitionViewModel(application:Application):AndroidViewModel(application) {
    private val executor=Executors.newSingleThreadExecutor()
    private val dispatcher=executor.asCoroutineDispatcher()
    private var detector:CartonDetector?=null
    private val tracker=StablePanelTracker()
    var panels by mutableStateOf<List<Panel>>(emptyList());private set
    var frame by mutableStateOf<Bitmap?>(null);private set
    var detection by mutableStateOf<Detection?>(null);private set
    var tracked by mutableStateOf(StablePanelTracker.State(null,false,0));private set
    var selectedPhoto by mutableStateOf<Bitmap?>(null);private set
    var selectedUri by mutableStateOf<Uri?>(null);private set
    var error by mutableStateOf<String?>(null);private set
    var photoRevision by mutableIntStateOf(0);private set
    var loading by mutableStateOf(false);private set
    init { viewModelScope.launch {
        try { panels=withContext(dispatcher){CartonDetector(application).also { detector=it }.panels} }
        catch(e:CancellationException){throw e}
        catch(_:Exception){error="이 기기에서 로컬 인식 모듈을 시작하지 못했어요."}
    } }
    fun select(uri:Uri){
        photoRevision++;val revision=photoRevision
        frame=null;detection=null;tracked=StablePanelTracker.State(null,false,0);selectedPhoto=null;selectedUri=null;loading=true
        viewModelScope.launch {
            try {
                val photo=withContext(Dispatchers.IO){ImageCodec.read(getApplication(),uri)}
                if(revision==photoRevision){selectedPhoto=photo;selectedUri=uri;error=null}
            }catch(e:CancellationException){throw e}
            catch(_:Exception){if(revision==photoRevision)error="사진을 읽지 못했어요. 다른 사진을 선택해 주세요."}
            finally {if(revision==photoRevision)loading=false}
        }
    }
    fun synthetic(){photoRevision++;selectedPhoto=null;selectedUri=null;frame=null;detection=null;tracked=StablePanelTracker.State(null,false,0);error=null;loading=false}
    suspend fun resetTracking(){withContext(dispatcher){tracker.reset()};tracked=StablePanelTracker.State(null,false,0);detection=null}
    suspend fun evaluate(panel:Int,pose:PanelPose,photo:Bitmap?){
        val result=withContext(dispatcher){
            val d=detector?:return@withContext null
            val image=if(photo==null)PanelRenderer.render(d.panels[panel].bitmap,pose) else {
                val ratio=minOf(640f/photo.width,640f/photo.height,1f)
                Bitmap.createScaledBitmap(photo,(photo.width*ratio).toInt().coerceAtLeast(1),(photo.height*ratio).toInt().coerceAtLeast(1),true)
            }
            val found=d.detect(image)
            Triple(image,found,tracker.update(found.match,SystemClock.elapsedRealtime()))
        }?:return
        currentCoroutineContext().ensureActive()
        frame=result.first;detection=result.second;tracked=result.third
    }
    override fun onCleared(){
        // Queue cleanup after any active native call; do not release Mats while matching.
        executor.execute { detector?.close() };executor.shutdown()
        super.onCleared()
    }
}
