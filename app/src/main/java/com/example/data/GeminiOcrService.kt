package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.model.OcrParsedResult
import com.example.model.ServiceMode
import com.example.util.ReceiptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

class GeminiOcrService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    fun resolveEffectiveApiKey(settingsOverride: String): String {
        val override = settingsOverride.trim()
        if (override.isNotEmpty()) return override
        val buildConfigKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            ""
        }
    }

    suspend fun scanReceiptImage(imageUri: Uri, apiKeyOverride: String): Result<OcrParsedResult> =
        withContext(Dispatchers.IO) {
            val apiKey = resolveEffectiveApiKey(apiKeyOverride)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Gemini API Key belum diatur. Masukkan API Key Anda di tab Pengaturan atau panel Secrets AI Studio."
                    )
                )
            }

            try {
                val jpegBase64 = compressUriToJpegBase64(imageUri, maxWidth = 1024)
                val payloadJson = buildRequestPayload(jpegBase64)
                val endpoints = buildCandidateEndpoints(apiKey)

                var lastErrorMessage = "Gagal memindai bukti transaksi."
                for (endpoint in endpoints) {
                    val responseResult = executePost(endpoint, payloadJson)
                    if (responseResult.isSuccess) {
                        val parsed = parseGeminiJsonResponse(responseResult.getOrThrow())
                        return@withContext Result.success(parsed)
                    } else {
                        lastErrorMessage = responseResult.exceptionOrNull()?.message ?: lastErrorMessage
                    }
                }
                Result.failure(IllegalStateException(lastErrorMessage))
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        e.localizedMessage ?: "Gagal memproses gambar bukti transaksi."
                    )
                )
            }
        }

    private fun buildCandidateEndpoints(apiKey: String): List<String> {
        val encoded = URLEncoder.encode(apiKey, "UTF-8")
        return if (apiKey.startsWith("AQ.")) {
            listOf(
                "https://aiplatform.googleapis.com/v1/publishers/google/models/gemini-2.5-flash:generateContent?key=$encoded",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$encoded"
            )
        } else {
            listOf(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$encoded",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=$encoded"
            )
        }
    }

    private fun executePost(url: String, payload: String): Result<String> {
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = extractApiError(respBody)
                    val userMsg = when (resp.code) {
                        400, 401, 403 -> "API Key tidak valid atau belum memiliki izin Gemini ($detail). Periksa API Key di tab Pengaturan."
                        429 -> "Batas permintaan AI sementara tercapai (429). Coba lagi beberapa saat."
                        else -> "Layanan AI merespons kode ${resp.code}: $detail"
                    }
                    Result.failure(IOException(userMsg))
                } else {
                    Result.success(respBody)
                }
            }
        } catch (e: Exception) {
            Result.failure(IOException("Koneksi jaringan gagal: ${e.localizedMessage ?: "Timeout"}"))
        }
    }

    private fun extractApiError(body: String): String {
        return try {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "Error" }
        } catch (_: Exception) {
            body.take(120).ifBlank { "Error" }
        }
    }

    private fun buildRequestPayload(base64Jpeg: String): String {
        val promptText = """
            Ekstrak data struk bukti transfer bank atau top up e-wallet Indonesia dari gambar ini menjadi JSON.
            Aturan pengisian:
            1. tipe: isi "ewallet" jika tujuan adalah DANA, OVO, GoPay, ShopeePay, LinkAja, atau dompet digital lainnya. Selain itu isi "bank".
            2. tanggal: format ringkas seperti "12 Nov 2026".
            3. waktu: format jam lengkap seperti "15:17:50 WIB" (tambahkan WIB bila belum ada zona waktu).
            4. referensi: nomor referensi / ID transaksi / RRN lengkap tanpa spasi baris.
            5. tujuan: nama bank tujuan (misal "BCA", "BRI", "Mandiri", "BNI", "SeaBank", "BSI") atau e-wallet ("DANA", "OVO", "GoPay", "ShopeePay", "LinkAja").
            6. nomor: nomor rekening tujuan atau nomor HP tujuan.
            7. penerima: nama lengkap penerima (gabungkan bila terpotong 2 baris).
            8. nominal: angka integer bersih nominal utama TANPA angka sen (.00) dan tanpa pemisah ribuan (contoh "IDR 999,236.00" -> 999236).
        """.trimIndent()

        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put("tipe", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray(listOf("bank", "ewallet")))
                    })
                    put("tanggal", JSONObject().apply { put("type", "STRING") })
                    put("waktu", JSONObject().apply { put("type", "STRING") })
                    put("referensi", JSONObject().apply { put("type", "STRING") })
                    put("tujuan", JSONObject().apply { put("type", "STRING") })
                    put("nomor", JSONObject().apply { put("type", "STRING") })
                    put("penerima", JSONObject().apply { put("type", "STRING") })
                    put("nominal", JSONObject().apply { put("type", "INTEGER") })
                }
            )
            put(
                "required",
                JSONArray(listOf("tipe", "tanggal", "waktu", "referensi", "tujuan", "nomor", "penerima", "nominal"))
            )
        }

        val parts = JSONArray().apply {
            put(JSONObject().put("text", promptText))
            put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Jpeg)
                    }
                )
            )
        }

        return JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", parts)
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                }
            )
        }.toString()
    }

    private fun parseGeminiJsonResponse(rawBody: String): OcrParsedResult {
        val root = JSONObject(rawBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Hasil OCR kosong. Pastikan foto bukti transfer terlihat jelas.")
        val first = candidates.optJSONObject(0)
            ?: throw IllegalStateException("Hasil OCR kosong.")
        val parts = first.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException("Format respons OCR tidak dikenali.")
        val jsonText = parts.optJSONObject(0)?.optString("text").orEmpty()
        if (jsonText.isBlank()) {
            throw IllegalStateException("Hasil OCR kosong. Pastikan foto bukti transfer terlihat jelas.")
        }

        val data = JSONObject(jsonText)
        val tujuanUpper = ReceiptFormatter.sanitizeSingleLine(data.optString("tujuan")).uppercase()
        val tipeStr = data.optString("tipe", "bank").lowercase()
        val isEwallet = tipeStr == "ewallet" ||
            ReceiptFormatter.EWALLET_KEYWORDS.any { kw -> tujuanUpper.contains(kw) }

        return OcrParsedResult(
            tipe = if (isEwallet) ServiceMode.EWALLET else ServiceMode.BANK,
            tanggal = ReceiptFormatter.sanitizeSingleLine(data.optString("tanggal")),
            waktu = ReceiptFormatter.sanitizeSingleLine(data.optString("waktu")),
            referensi = data.optString("referensi").replace(Regex("\\s+"), ""),
            tujuan = tujuanUpper,
            nomor = ReceiptFormatter.sanitizeSingleLine(data.optString("nomor")),
            penerima = ReceiptFormatter.sanitizeSingleLine(data.optString("penerima")).uppercase(),
            nominal = data.optLong("nominal", 0L).coerceAtLeast(0L)
        )
    }

    private fun compressUriToJpegBase64(uri: Uri, maxWidth: Int): String {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: throw IllegalStateException("Tidak dapat membuka file gambar.")

        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxWidth * 2 ||
            boundsOpts.outHeight / sampleSize > maxWidth * 2
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Format gambar tidak didukung.")

        val rotated = applyExifRotationIfNeeded(uri, decoded)
        val finalBitmap = if (rotated.width > maxWidth) {
            val ratio = maxWidth.toFloat() / rotated.width.toFloat()
            val targetH = max(1, (rotated.height * ratio).roundToInt())
            Bitmap.createScaledBitmap(rotated, maxWidth, targetH, true).also {
                if (rotated !== decoded) rotated.recycle()
            }
        } else {
            rotated
        }

        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (finalBitmap !== decoded) finalBitmap.recycle()
        decoded.recycle()

        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun applyExifRotationIfNeeded(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (_: Exception) {
            bitmap
        }
    }
}
