package com.example

import android.app.Application
import android.util.Log
import com.example.firebase.ensureFirebaseInitialized

class AuroraCellApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (!ensureFirebaseInitialized(this)) {
                Log.e("AuroraCellApp", "FirebaseApp failed to initialize in Application.onCreate.")
            }
        } catch (e: Exception) {
            Log.e("AuroraCellApp", "Failed to initialize FirebaseApp in Application.onCreate", e)
        }
    }
}
