package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.model.OcrParsedResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

class OfflineOcrService(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun scanReceiptImage(imageUri: Uri): Result<OcrParsedResult> =
        withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                bitmap = decodePreparedBitmap(imageUri)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizeText(inputImage)

                if (visionText.text.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Tidak ada teks yang terbaca pada gambar. Pastikan foto bukti transaksi jelas dan tidak buram."
                        )
                    )
                }

                val lineBoxes = extractLineBoxes(visionText)
                val parsed = if (lineBoxes.isNotEmpty()) {
                    ReceiptTextParser.parseSpatialLines(lineBoxes, visionText.text)
                } else {
                    ReceiptTextParser.parseRawText(visionText.text)
                }

                val hasUsefulField = parsed.nominal > 0L ||
                    parsed.referensi.isNotBlank() ||
                    parsed.penerima.isNotBlank() ||
                    parsed.nomor.isNotBlank() ||
                    parsed.tujuan.isNotBlank()

                if (!hasUsefulField) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Data bukti transfer belum terbaca lengkap. Silakan periksa foto atau lengkapi secara manual."
                        )
                    )
                }

                Result.success(parsed)
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        e.localizedMessage ?: "Gagal memproses gambar bukti transaksi."
                    )
                )
            } finally {
                bitmap?.recycle()
            }
        }

    private suspend fun recognizeText(inputImage: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { exc ->
                    if (cont.isActive) cont.resumeWithException(exc)
                }
        }

    private fun extractLineBoxes(visionText: Text): List<OcrLineBox> {
        val boxes = mutableListOf<OcrLineBox>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val rect = line.boundingBox ?: continue
                boxes.add(
                    OcrLineBox(
                        text = line.text,
                        left = rect.left.toFloat(),
                        top = rect.top.toFloat(),
                        right = rect.right.toFloat(),
                        bottom = rect.bottom.toFloat()
                    )
                )
            }
        }
        return boxes
    }

    internal fun decodePreparedBitmap(
        uri: Uri,
        minWidth: Int = 900,
        maxDimension: Int = 1800
    ): Bitmap {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Tidak dapat membuka file gambar.")
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }

        var sampleSize = 1
        if (boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
            while (boundsOpts.outWidth / sampleSize > maxDimension * 2 ||
                boundsOpts.outHeight / sampleSize > maxDimension * 2
            ) {
                sampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Tidak dapat membuka file gambar.")
        val decoded = decodeStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Format gambar tidak didukung.")

        val rotated = applyExifRotationIfNeeded(uri, decoded)
        if (rotated !== decoded) {
            decoded.recycle()
        }

        val currentMax = max(rotated.width, rotated.height)
        val scaled = when {
            currentMax > maxDimension -> {
                val ratio = maxDimension.toFloat() / currentMax.toFloat()
                val targetW = max(1, (rotated.width * ratio).roundToInt())
                val targetH = max(1, (rotated.height * ratio).roundToInt())
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                    rotated.recycle()
                }
            }
            rotated.width in 1 until minWidth -> {
                val ratio = (minWidth.toFloat() / rotated.width.toFloat()).coerceAtMost(2.5f)
                val targetW = max(1, (rotated.width * ratio).roundToInt())
                val targetH = max(1, (rotated.height * ratio).roundToInt())
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                    rotated.recycle()
                }
            }
            else -> rotated
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
}
