package com.example.repository

import android.content.Context
import android.graphics.Bitmap
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiVisionRepository {
    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> {
        check(FirebaseBackend.auth.currentUser != null) { "로그인이 만료되었습니다. 설정에서 다시 로그인해 주세요." }
        try {
            val timeoutMs = if (data["operation"] == "resolve") 20000L else 125000L
            return withTimeout(timeoutMs) {
                val callable = FirebaseBackend.functions.getHttpsCallable(name)
                callable.setTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                callable.call(data).await().data as? Map<*, *> ?: error("서버 응답 형식이 올바르지 않습니다.")
            }
        } catch (e: TimeoutCancellationException) {
            error("분석 시간이 초과되었습니다. 같은 사진으로 다시 시도할 수 있습니다.")
        } catch (e: CancellationException) { throw e
        } catch (_: com.google.firebase.auth.FirebaseAuthException) {
            error("로그인이 만료되었습니다. 설정에서 다시 로그인해 주세요.")
        } catch (_: java.io.InterruptedIOException) {
            error("분석 시간이 초과되었습니다. 같은 사진으로 다시 시도할 수 있습니다.")
        } catch (_: java.io.IOException) {
            error("인터넷 연결 또는 서버 연결이 끊겼습니다. 연결을 확인하고 다시 시도해 주세요.")
        } catch (e: FirebaseFunctionsException) {
            val message = when (e.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "로그인이 만료되었습니다. 설정에서 다시 로그인해 주세요."
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> "앱 인증에 실패했습니다. 운영자가 App Check 설정을 확인해야 합니다."
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> e.message ?: "사용량 제한에 도달했습니다. 잠시 후 다시 시도해 주세요."
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "분석 시간이 초과되었습니다. 다시 시도해 주세요."
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> e.message ?: "서버 설정을 확인해 주세요."
                FirebaseFunctionsException.Code.DATA_LOSS -> "AI 응답을 해석하지 못했습니다. 다시 분석하거나 재촬영해 주세요."
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> e.message ?: "사진과 상태 선택값을 확인해 주세요."
                FirebaseFunctionsException.Code.NOT_FOUND -> "분석 서버 또는 이 계정의 기록을 찾지 못했습니다. 현재 사진을 다시 분석해 주세요."
                FirebaseFunctionsException.Code.ABORTED -> e.message ?: "다른 요청이 먼저 처리되었습니다. 잠시 후 다시 시도하거나 현재 사진을 다시 분석해 주세요."
                else -> "서버에 연결하지 못했습니다. 인터넷 연결과 서버 상태를 확인하고 다시 시도해 주세요."
            }
            error(message)
        } catch (_: Exception) {
            error("서버 연결을 준비하지 못했습니다. 인터넷 연결과 앱 서버 설정을 확인해 주세요.")
        }
    }
    private fun parseGuidance(raw: Map<*, *>): GuidanceResult = try {
        GuidanceResult.fromMap(raw)
    } catch (_: Exception) { error("서버 안내를 확인하지 못했습니다. 최신 앱인지 확인하고 다시 시도해 주세요.") }

    suspend fun analyzeWasteImage(bitmap: Bitmap, answers: ScanAnswers, requestId: String): GuidanceResult = withContext(Dispatchers.IO) {
        check(answers.readyForAnalysis) { "사용 목적과 현재 상태를 선택해 주세요." }
        parseGuidance(call("analyzeImage", mapOf("schemaVersion" to 2, "operation" to "analyze", "requestId" to requestId,
            "image" to ImageCodec.encode(bitmap), "answers" to answers.toMap())))
    }
    suspend fun resolveWasteState(scanId: String, revision: Int, answers: ScanAnswers, requestId: String): GuidanceResult = withContext(Dispatchers.IO) {
        parseGuidance(call("analyzeImage", mapOf("schemaVersion" to 2, "operation" to "resolve", "requestId" to requestId,
            "scanId" to scanId, "expectedRevision" to revision, "answers" to answers.toMap())))
    }
    suspend fun deleteAccount(): String = try {
        JSONObject(call("deleteAccount", emptyMap())).toString()
    } catch (e: CancellationException) { throw e
    } catch (_: Exception) { JSONObject().put("error", "회원 탈퇴에 실패했습니다. 연결 상태를 확인하고 다시 시도해 주세요.").toString() }

    // Preserved signatures for the inactive expansion screens; no local rewards or demo coupons.
    suspend fun redeemCoupon(context: Context, itemId: String): String = JSONObject().put("error", "쿠폰 교환은 향후 계획입니다.").toString()
    suspend fun grantPoints(context: Context, points: Int, reason: String): String = JSONObject().put("error", "포인트 기능은 현재 MVP에서 제공하지 않습니다.").toString()
}
