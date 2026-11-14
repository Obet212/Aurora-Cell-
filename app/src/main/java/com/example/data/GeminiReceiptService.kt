package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.firebase.diagnoseAppCheck
import com.example.firebase.ensureFirebaseInitialized
import com.example.model.OcrParsedResult
import com.example.model.ServiceMode
import com.example.util.ReceiptFormatter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "GeminiReceiptService"
private const val GEMINI_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_IMAGE_DIMENSION = 2048

class GeminiReceiptService(private val context: Context) {

    private val receiptSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "mode" to Schema.enumeration(
                    values = listOf("BANK", "EWALLET"),
                    description = "Gunakan EWALLET jika tujuan penerima adalah dompet digital (DANA, OVO, GOPAY, SHOPEEPAY, LINKAJA, ISAKU, SAKUKU). Selain itu gunakan BANK."
                ),
                "tanggal" to Schema.string(
                    description = "Tanggal transaksi format dd MMM yyyy (contoh: 14 Sep 2026). Abaikan tanggal di luar struk."
                ),
                "waktu" to Schema.string(
                    description = "Jam transaksi format HH:mm:ss WIB atau HH:mm WIB (contoh: 14:51 WIB). WAJIB ambil jam transaksi pada isi struk (misal Waktu Transaksi), BUKAN jam status bar ponsel di pojok kiri atas."
                ),
                "referensi" to Schema.string(
                    description = "Nomor Referensi, No. Transaksi, atau ID Transaksi lengkap digabung menjadi satu tanpa spasi, huruf kapital."
                ),
                "tujuan" to Schema.string(
                    description = "Nama Bank atau E-Wallet TUJUAN PENERIMA (contoh: BCA, BRI, MANDIRI, BNI, SEABANK, DANA, OVO, GOPAY, SHOPEEPAY). WAJIB lihat bagian 'Ke' / 'Penerima' / 'Bank Tujuan', BUKAN bank pengirim pada bagian 'Dari' / 'Sumber Dana' / 'Metode Pembayaran' dan BUKAN ikon notifikasi status bar."
                ),
                "nomor" to Schema.string(
                    description = "Nomor rekening bank tujuan atau nomor HP/akun e-wallet penerima (hanya angka tanpa spasi/tanda hubung, atau tanda * bila memang disensor di gambar). Abaikan rekening pengirim."
                ),
                "penerima" to Schema.string(
                    description = "Nama lengkap penerima transfer (huruf kapital). Abaikan nama pengirim."
                ),
                "merchant" to Schema.string(
                    description = "Nama merchant/toko/penyedia layanan jika transaksi berupa pembayaran QRIS, belanja merchant, atau tagihan."
                ),
                "nominal" to Schema.integer(
                    description = "Nominal transfer/pembayaran utama dalam Rupiah bulat (contoh: 50000 untuk Rp 50.000 atau 999236 untuk IDR 999,236.00). Abaikan desimal .00/,00 dan biaya admin."
                ),
                "ringkasanTeks" to Schema.string(
                    description = "Ringkasan poin transaksi yang terbaca dalam format baris 'Label: Nilai' agar pengguna mudah menyalin."
                )
            ),
            optionalProperties = listOf("merchant", "ringkasanTeks")
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = receiptSchema
            }
        )
    }

    suspend fun scanReceiptImage(imageUri: Uri): Result<OcrParsedResult> =
        withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                if (!ensureFirebaseInitialized(context)) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Konfigurasi Firebase belum tersedia di APK ini. Gunakan OCR Offline sebagai alternatif."
                        )
                    )
                }

                bitmap = loadNormalizedBitmap(imageUri)
                val promptText = buildString {
                    appendLine("Baca gambar bukti transaksi M-Banking / E-Wallet / QRIS Indonesia ini dengan sangat teliti dan ekstrak ke JSON.")
                    appendLine("Aturan penting:")
                    appendLine("1. Jika bukti berasal dari SeaBank, perhatikan baris 'Ke': di sana tercantum nama penerima dan bank/e-wallet tujuan (bisa SeaBank atau bank lain seperti BCA/BRI/Mandiri/BNI atau e-wallet seperti DANA/OVO/GoPay/ShopeePay). Abaikan baris 'Dari' (pengirim).")
                    appendLine("2. Jika bukti berasal dari BRI/Qita/myBCA/Livin/DANA/OVO/GoPay/ShopeePay, selalu bedakan pengirim vs penerima dan ambil DATA PENERIMA (atau merchant untuk pembayaran QRIS/tagihan).")
                    appendLine("3. Abaikan jam baterai/sinyal serta ikon aplikasi pada status bar ponsel paling atas.")
                    appendLine("4. Untuk No. Referensi / No. Transaksi / ID Transaksi yang terpotong beberapa baris, gabungkan semua baris menjadi satu string utuh tanpa spasi.")
                    appendLine("5. Untuk Nominal, hilangkan angka sen desimal (.00 atau ,00) sebelum mengambil angka bulat Rupiah.")
                }

                val response = withTimeout(GEMINI_TIMEOUT_MS) {
                    model.generateContent(
                        content {
                            image(bitmap)
                            text(promptText)
                        }
                    )
                }

                val jsonString = response.text
                if (jsonString.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("AI Gemini mengembalikan respons kosong.")
                    )
                }

                val parsed = parseGeminiJsonResponse(jsonString)
                val hasUsefulField = parsed.nominal > 0L ||
                    parsed.referensi.isNotBlank() ||
                    parsed.penerima.isNotBlank() ||
                    parsed.nomor.isNotBlank() ||
                    parsed.tujuan.isNotBlank()

                if (!hasUsefulField) {
                    return@withContext Result.failure(
                        OcrUnrecognizedException(
                            message = "AI Gemini tidak menemukan rincian transfer yang valid pada gambar ini.",
                            rawText = parsed.rawText
                        )
                    )
                }

                Result.success(parsed)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini AI scan timed out", e)
                Result.failure(
                    IllegalStateException("Waktu pemindaian AI Gemini habis (60 detik). Periksa koneksi internet atau coba mode OCR Offline.")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini AI receipt scan failed", e)
                val rawMessage = e.localizedMessage ?: "Gagal memindai gambar dengan AI Gemini."
                val enrichedMessage = if (isAppCheckFailure(rawMessage)) {
                    buildAppCheckDiagnosticMessage(rawMessage)
                } else {
                    rawMessage
                }
                Result.failure(IllegalStateException(enrichedMessage))
            } finally {
                bitmap?.recycle()
            }
        }

    private fun isAppCheckFailure(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("app check") ||
            lower.contains("token is invalid") ||
            lower.contains("attestation") ||
            lower.contains("403")
    }

    private suspend fun buildAppCheckDiagnosticMessage(originalError: String): String {
        val diag = runCatching { diagnoseAppCheck(context) }.getOrNull() ?: return originalError
        return buildString {
            appendLine(originalError)
            if (diag.isDebugBuild) {
                append("Mode APK: DEBUG (DebugAppCheckProvider). ")
                if (!diag.hasIntentDebugToken) {
                    append("APK Debug yang dipasang langsung di HP tidak membawa Debug Token bawaan sehingga ditolak server App Check. ")
                }
                if (!diag.tokenError.isNullOrBlank()) {
                    append("Penyebab: ${diag.tokenError}. ")
                }
                if (!diag.debugSecretUuid.isNullOrBlank()) {
                    append("Debug Token HP ini (salin ke Firebase Console -> App Check -> Apps -> Manage debug tokens): ${diag.debugSecretUuid}")
                } else {
                    append("Gunakan APK Release atau daftarkan Debug Token di Firebase Console.")
                }
            } else {
                append("Mode APK: RELEASE (RecaptchaAppCheckProvider). ")
                if (diag.recaptchaSiteKey.isBlank()) {
                    append("recaptcha_site_key tidak ditemukan di APK.")
                } else if (!diag.tokenError.isNullOrBlank()) {
                    append("Penyebab token gagal dibuat: ${diag.tokenError}")
                } else {
                    append("Token berhasil dibuat oleh reCAPTCHA, tetapi ditolak server project 'aurora-cell'. Pastikan reCAPTCHA Enterprise aktif dan terhubung di Firebase Console -> App Check -> Apps.")
                }
            }
        }
    }

    private fun loadNormalizedBitmap(uri: Uri): Bitmap {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: throw IllegalStateException("Tidak dapat membuka file gambar.")

        val rotated = applyExifRotationIfNeeded(uri, raw)
        if (rotated !== raw) {
            raw.recycle()
        }

        val maxDim = max(rotated.width, rotated.height)
        if (maxDim <= MAX_INLINE_IMAGE_DIMENSION) {
            return rotated
        }

        val scale = MAX_INLINE_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
        val targetW = max(1, (rotated.width * scale).roundToInt())
        val targetH = max(1, (rotated.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
        if (scaled !== rotated) {
            rotated.recycle()
        }
        return scaled
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

    companion object {
        fun parseGeminiJsonResponse(jsonString: String): OcrParsedResult {
            val json = JSONObject(jsonString)

            val rawTujuan = ReceiptFormatter.sanitizeSingleLine(json.optString("tujuan", ""))
                .uppercase()
            val rawMode = json.optString("mode", "BANK").uppercase()
            val mode = if (
                rawMode == "EWALLET" ||
                ReceiptFormatter.EWALLET_KEYWORDS.any { rawTujuan.contains(it) }
            ) {
                ServiceMode.EWALLET
            } else {
                ServiceMode.BANK
            }

            val rawTanggal = ReceiptFormatter.sanitizeSingleLine(json.optString("tanggal", ""))
            val tanggal = ReceiptTextParser.parseDateCandidate(rawTanggal) ?: rawTanggal

            val rawWaktu = ReceiptFormatter.sanitizeSingleLine(json.optString("waktu", ""))
            val waktu = ReceiptTextParser.parseTimeCandidate(rawWaktu) ?: rawWaktu

            val referensi = json.optString("referensi", "")
                .replace(Regex("\\s+"), "")
                .replace(Regex("[^A-Za-z0-9\\-_.]"), "")
                .uppercase()

            val rawNomor = ReceiptFormatter.sanitizeSingleLine(json.optString("nomor", ""))
            val nomor = if (rawNomor.contains('*') || rawNomor.contains('•')) {
                rawNomor.replace(Regex("\\s+"), "")
            } else {
                val pureDigits = ReceiptTextParser.sanitizeDigitsOnlyAccount(rawNomor)
                pureDigits.ifBlank { rawNomor.replace(Regex("\\s+"), "") }
            }

            val merchant = ReceiptFormatter.sanitizeSingleLine(json.optString("merchant", ""))
                .replace('•', '*')
                .replace('·', '*')
                .uppercase()

            val rawPenerima = ReceiptFormatter.sanitizeSingleLine(json.optString("penerima", ""))
                .replace('•', '*')
                .replace('·', '*')
                .uppercase()
            val penerima = rawPenerima.ifBlank { merchant }
            val resolvedTujuan = rawTujuan.ifBlank { merchant }

            val nominal = json.optLong("nominal", 0L).coerceAtLeast(0L)

            val rawSummary = json.optString("ringkasanTeks", "").trim()
            val formattedRawText = if (rawSummary.isNotBlank()) {
                rawSummary
            } else {
                listOfNotNull(
                    tanggal.takeIf { it.isNotBlank() }?.let { "Tanggal: $it" },
                    waktu.takeIf { it.isNotBlank() }?.let { "Waktu: $it" },
                    referensi.takeIf { it.isNotBlank() }?.let { "No. Referensi: $it" },
                    resolvedTujuan.takeIf { it.isNotBlank() }?.let {
                        val label = if (mode == ServiceMode.EWALLET) "E-Wallet" else "Bank Tujuan"
                        "$label: $it"
                    },
                    nomor.takeIf { it.isNotBlank() }?.let {
                        val label = if (mode == ServiceMode.EWALLET) "Nomor" else "Rekening"
                        "$label: $it"
                    },
                    penerima.takeIf { it.isNotBlank() }?.let { "Penerima: $it" },
                    nominal.takeIf { it > 0L }?.let { "Nominal: ${ReceiptFormatter.formatRupiah(it)}" }
                ).joinToString("\n")
            }

            return OcrParsedResult(
                tipe = mode,
                tanggal = tanggal,
                waktu = waktu,
                referensi = referensi,
                tujuan = resolvedTujuan,
                nomor = nomor,
                penerima = penerima,
                nominal = nominal,
                rawText = formattedRawText
            )
        }
    }
}
