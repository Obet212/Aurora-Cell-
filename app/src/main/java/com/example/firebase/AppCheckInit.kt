package com.example.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@SuppressLint("DiscouragedApi")
private fun readStringResource(context: Context, name: String): String {
    val resId = context.resources.getIdentifier(name, "string", context.packageName)
    if (resId == 0) return ""
    return runCatching { context.getString(resId).trim() }.getOrDefault("")
}

@Synchronized
fun ensureFirebaseInitialized(context: Context): Boolean {
    val appContext = context.applicationContext ?: context
    if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        return true
    }

    val fromGoogleServices = FirebaseApp.initializeApp(appContext)
    if (fromGoogleServices != null || FirebaseApp.getApps(appContext).isNotEmpty()) {
        return true
    }

    val appId = readStringResource(appContext, "fallback_google_app_id")
    val apiKey = readStringResource(appContext, "fallback_google_api_key")
    val projectId = readStringResource(appContext, "fallback_project_id")
    val senderId = readStringResource(appContext, "fallback_gcm_default_sender_id")
    val storageBucket = readStringResource(appContext, "fallback_google_storage_bucket")

    if (appId.isBlank() || apiKey.isBlank() || projectId.isBlank()) {
        Log.e(TAG, "Missing both google-services resources and fallback Firebase resources.")
        return false
    }

    val options = FirebaseOptions.Builder()
        .setApplicationId(appId)
        .setApiKey(apiKey)
        .setProjectId(projectId)
        .apply {
            if (senderId.isNotBlank()) setGcmSenderId(senderId)
            if (storageBucket.isNotBlank()) setStorageBucket(storageBucket)
        }
        .build()

    return try {
        FirebaseApp.initializeApp(appContext, options)
        FirebaseApp.getApps(appContext).isNotEmpty()
    } catch (e: Exception) {
        Log.e(TAG, "Fallback FirebaseApp initialization failed", e)
        FirebaseApp.getApps(appContext).isNotEmpty()
    }
}

@SuppressLint("ApplySharedPref", "DiscouragedApi")
fun configureAppCheck(context: Context, intent: Intent?) {
    val appContext = context.applicationContext ?: context
    try {
        if (!ensureFirebaseInitialized(appContext)) {
            Log.e(TAG, "FirebaseApp could not be initialized; skipping App Check setup.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the debug secret is persisted before
                // DebugAppCheckProviderFactory initializes.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = appContext.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                appContext.packageName
            )
            if (resId != 0) {
                val siteKey = appContext.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
