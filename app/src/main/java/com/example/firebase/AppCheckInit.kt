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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var appCheckProviderInstalled = false

@Volatile
private var lastIntentHadDebugToken = false

data class AppCheckDiagnostic(
    val isDebugBuild: Boolean,
    val hasIntentDebugToken: Boolean,
    val recaptchaSiteKey: String,
    val debugSecretUuid: String?,
    val tokenAcquired: Boolean,
    val tokenError: String?
)

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
                lastIntentHadDebugToken = true
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

            if (!appCheckProviderInstalled || !debugToken.isNullOrEmpty()) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckProviderInstalled = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            if (appCheckProviderInstalled) return
            val siteKey = readStringResource(appContext, RECAPTCHA_SITE_KEY_RES)
            if (siteKey.isNotBlank()) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
                appCheckProviderInstalled = true
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found or blank in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

fun readDebugAppCheckSecret(context: Context): String? {
    if (!BuildConfig.DEBUG) return null
    val appContext = context.applicationContext ?: context
    return runCatching {
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = appContext.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        prefs.getString(DEBUG_SECRET_KEY, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

suspend fun diagnoseAppCheck(context: Context): AppCheckDiagnostic {
    val appContext = context.applicationContext ?: context
    val siteKey = readStringResource(appContext, RECAPTCHA_SITE_KEY_RES)

    if (!ensureFirebaseInitialized(appContext)) {
        return AppCheckDiagnostic(
            isDebugBuild = BuildConfig.DEBUG,
            hasIntentDebugToken = lastIntentHadDebugToken,
            recaptchaSiteKey = siteKey,
            debugSecretUuid = readDebugAppCheckSecret(appContext),
            tokenAcquired = false,
            tokenError = "FirebaseApp belum terinisialisasi."
        )
    }

    val tokenResult: Result<String>? = withTimeoutOrNull(8_000L) {
        suspendCancellableCoroutine { cont ->
            FirebaseAppCheck.getInstance()
                .getAppCheckToken(false)
                .addOnSuccessListener { appCheckToken ->
                    if (cont.isActive) {
                        cont.resume(Result.success(appCheckToken.token))
                    }
                }
                .addOnFailureListener { exc ->
                    if (cont.isActive) {
                        cont.resume(Result.failure(exc))
                    }
                }
        }
    }

    val debugSecret = readDebugAppCheckSecret(appContext)
    val errorText = when {
        tokenResult == null -> "Timeout (8 detik) saat mengambil token App Check."
        tokenResult.isFailure -> {
            val exc = tokenResult.exceptionOrNull()
            val detail = exc?.localizedMessage ?: exc?.javaClass?.simpleName ?: "Unknown App Check error"
            val causeMsg = exc?.cause?.localizedMessage?.takeIf { it.isNotBlank() && it != detail }
            if (causeMsg != null) "$detail ($causeMsg)" else detail
        }
        else -> null
    }

    return AppCheckDiagnostic(
        isDebugBuild = BuildConfig.DEBUG,
        hasIntentDebugToken = lastIntentHadDebugToken,
        recaptchaSiteKey = siteKey,
        debugSecretUuid = debugSecret,
        tokenAcquired = tokenResult?.isSuccess == true,
        tokenError = errorText
    )
}
