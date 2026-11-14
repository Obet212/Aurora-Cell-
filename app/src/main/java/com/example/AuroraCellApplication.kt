package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class AuroraCellApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app == null) {
                    Log.e(
                        "AuroraCellApp",
                        "FirebaseApp.initializeApp returned null (google_app_id resource missing)."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AuroraCellApp", "Failed to initialize FirebaseApp in Application.onCreate", e)
        }
    }
}
