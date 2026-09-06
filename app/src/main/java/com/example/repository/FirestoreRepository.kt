package com.example.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * 클라이언트는 Firestore에 대해 "자기 프로필 읽기"와 "제한된 프로필 필드 쓰기"만 수행한다.
 * points, totalRecycled, 쿠폰, 원장 등 보상 관련 필드는 전부 Cloud Functions(Admin SDK)가
 * 트랜잭션으로만 변경하며, firestore.rules 에서도 클라이언트 쓰기가 거부된다.
 */
object FirestoreRepository {
    private fun isFirebaseAvailable(): Boolean {
        return try {
            com.google.firebase.FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    private val firestore by lazy { if (isFirebaseAvailable()) FirebaseFirestore.getInstance() else null }
    private val auth by lazy { if (isFirebaseAvailable()) FirebaseAuth.getInstance() else null }



    suspend fun loadUserProfile(context: android.content.Context): Map<String, Any>? {
        val fs = firestore
        val au = auth
        if (fs == null || au == null || au.currentUser == null) {
            return null
        }

        val userId = au.currentUser?.uid ?: return null
        val userRef = fs.collection("users").document(userId)

        return try {
            val doc = userRef.get().await()
            if (doc.exists()) {
                doc.data
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveUserApartment(context: android.content.Context, apartmentId: String): Boolean {
        val fs = firestore
        val au = auth
        if (fs == null || au == null || au.currentUser == null) {
            return false
        }

        val userId = au.currentUser?.uid ?: return false
        val userRef = fs.collection("users").document(userId)

        return try {
            // 규칙상 클라이언트가 만질 수 있는 필드는 apartmentId/displayName 뿐이다.
            userRef.set(mapOf("apartmentId" to apartmentId), com.google.firebase.firestore.SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
