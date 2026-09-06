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
            return withTimeout(125000) {
                val callable = FirebaseBackend.functions.getHttpsCallable(name)
                callable.setTimeout(125, TimeUnit.SECONDS)
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
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "사용량 제한에 도달했습니다. 잠시 후 다시 시도해 주세요. 일일 한도는 30회입니다."
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "분석 시간이 초과되었습니다. 다시 시도해 주세요."
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> e.message ?: "서버 설정을 확인해 주세요."
                FirebaseFunctionsException.Code.DATA_LOSS -> "AI 응답을 해석하지 못했습니다. 다시 분석하거나 재촬영해 주세요."
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "사진 형식을 확인할 수 없습니다. 다른 사진을 선택해 주세요."
                FirebaseFunctionsException.Code.NOT_FOUND -> "분석 서버를 찾을 수 없습니다. 서버 실행과 앱 연결 설정을 확인해 주세요."
                else -> "서버에 연결하지 못했습니다. 인터넷 연결과 서버 상태를 확인하고 다시 시도해 주세요."
            }
            error(message)
        } catch (_: Exception) {
            error("서버 연결을 준비하지 못했습니다. 인터넷 연결과 앱 서버 설정을 확인해 주세요.")
        }
    }
    suspend fun analyzeWasteImage(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        val result = call("analyzeImage", mapOf("image" to ImageCodec.encode(bitmap)))
        try { AnalysisResult.fromMap(result) } catch (_: Exception) {
            error("AI 응답 형식이 올바르지 않습니다. 다시 분석해 주세요.")
        }
    }
    suspend fun deleteAccount(): String = try {
        JSONObject(call("deleteAccount", emptyMap())).toString()
    } catch (e: CancellationException) { throw e
    } catch (_: Exception) { JSONObject().put("error", "회원 탈퇴에 실패했습니다. 연결 상태를 확인하고 다시 시도해 주세요.").toString() }

    // Preserved signatures for the inactive expansion screens; no local rewards or demo coupons.
    suspend fun redeemCoupon(context: Context, itemId: String): String = JSONObject().put("error", "쿠폰 교환은 향후 계획입니다.").toString()
    suspend fun grantPoints(context: Context, points: Int, reason: String): String = JSONObject().put("error", "포인트 기능은 현재 MVP에서 제공하지 않습니다.").toString()
}
