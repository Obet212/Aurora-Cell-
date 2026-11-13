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

    /**
     * Splits ML Kit Text.Line into horizontally separated element chunks when a single
     * Text.Line spans both a left-column label and a right-column value (e.g. "Nama   Cahyo").
     */
    private fun extractLineBoxes(visionText: Text): List<OcrLineBox> {
        val boxes = mutableListOf<OcrLineBox>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineRect = line.boundingBox
                val elementsWithBox = line.elements.mapNotNull { elem ->
                    val r = elem.boundingBox
                    if (r != null && elem.text.isNotBlank()) elem to r else null
                }.sortedBy { it.second.left }

                if (elementsWithBox.size <= 1) {
                    if (lineRect != null && line.text.isNotBlank()) {
                        boxes.add(
                            OcrLineBox(
                                text = line.text,
                                left = lineRect.left.toFloat(),
                                top = lineRect.top.toFloat(),
                                right = lineRect.right.toFloat(),
                                bottom = lineRect.bottom.toFloat()
                            )
                        )
                    }
                    continue
                }

                val lineHeight = (lineRect?.height()?.toFloat()
                    ?: elementsWithBox.map { it.second.height().toFloat() }.average().toFloat())
                    .coerceAtLeast(12f)
                val gapThreshold = max(lineHeight * 1.35f, 24f)

                var chunkStart = 0
                for (i in 0 until elementsWithBox.size) {
                    val isLast = i == elementsWithBox.lastIndex
                    val hasColumnGap = !isLast &&
                        (elementsWithBox[i + 1].second.left - elementsWithBox[i].second.right) > gapThreshold

                    if (isLast || hasColumnGap) {
                        val slice = elementsWithBox.subList(chunkStart, i + 1)
                        val chunkText = slice.joinToString(" ") { it.first.text }
                        val left = slice.minOf { it.second.left }.toFloat()
                        val top = slice.minOf { it.second.top }.toFloat()
                        val right = slice.maxOf { it.second.right }.toFloat()
                        val bottom = slice.maxOf { it.second.bottom }.toFloat()
                        boxes.add(
                            OcrLineBox(
                                text = chunkText,
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom
                            )
                        )
                        chunkStart = i + 1
                    }
                }
            }
        }
        return boxes
    }

    internal fun decodePreparedBitmap(
        uri: Uri,
        targetMinWidth: Int = 1080,
        targetMaxWidth: Int = 1600,
        maxHeight: Int = 4096
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
            while (boundsOpts.outWidth / sampleSize > targetMaxWidth * 2 &&
                boundsOpts.outHeight / sampleSize > maxHeight * 2
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

        val scaled = when {
            rotated.width > targetMaxWidth || rotated.height > maxHeight -> {
                val ratioW = targetMaxWidth.toFloat() / rotated.width.toFloat()
                val ratioH = maxHeight.toFloat() / rotated.height.toFloat()
                val ratio = minOf(ratioW, ratioH)
                val targetW = max(1, (rotated.width * ratio).roundToInt())
                val targetH = max(1, (rotated.height * ratio).roundToInt())
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                    rotated.recycle()
                }
            }
            rotated.width in 1 until targetMinWidth -> {
                val maxRatioByHeight = maxHeight.toFloat() / max(1, rotated.height).toFloat()
                val ratio = minOf(
                    (targetMinWidth.toFloat() / rotated.width.toFloat()).coerceAtMost(2.5f),
                    maxRatioByHeight.coerceAtLeast(1f)
                )
                if (ratio > 1.05f) {
                    val targetW = max(1, (rotated.width * ratio).roundToInt())
                    val targetH = max(1, (rotated.height * ratio).roundToInt())
                    Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                        rotated.recycle()
                    }
                } else {
                    rotated
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
