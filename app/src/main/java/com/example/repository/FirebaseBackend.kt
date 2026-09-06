package com.example.repository

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

object FirebaseBackend {
    private var app: FirebaseApp? = null
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance(checkNotNull(app) { "서버 초기화가 필요합니다." })
    val functions: FirebaseFunctions get() = FirebaseFunctions.getInstance(checkNotNull(app), "us-central1")

    fun initialize(context: Context) {
        if (app != null) return
        if (BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setProjectId("demo-ecosort")
                .setApplicationId("1:123456789:android:ecosortlocal")
                // Firebase SDK validates format even when using local emulators. This is a public dummy ID.
                .setApiKey("A" + "0".repeat(38))
                .build(), "ecosort-local")
            auth.useEmulator("10.0.2.2", 9099)
            functions.useEmulator("10.0.2.2", 5001)
        } else {
            app = FirebaseApp.getInstance()
            FirebaseAppCheck.getInstance(checkNotNull(app)).installAppCheckProviderFactory(
                appCheckProvider())
        }
    }
}
