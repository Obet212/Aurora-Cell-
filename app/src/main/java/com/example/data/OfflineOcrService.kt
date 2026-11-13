package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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

class OcrUnrecognizedException(
    message: String,
    val rawText: String
) : IllegalStateException(message)

class OfflineOcrService(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun scanReceiptImage(imageUri: Uri): Result<OcrParsedResult> =
        withContext(Dispatchers.IO) {
            var fullBitmap: Bitmap? = null
            var processedBitmap: Bitmap? = null
            try {
                fullBitmap = decodeFullResolutionBitmap(imageUri)
                processedBitmap = preprocessReceiptBitmap(fullBitmap)

                val imageW = processedBitmap.width.toFloat()
                val imageH = processedBitmap.height.toFloat()

                val inputImage = InputImage.fromBitmap(processedBitmap, 0)
                val visionText = recognizeText(inputImage)

                // Iterate Text.Line boundingBox coordinates directly (never raw visionText.text)
                val lineBoxes = extractLineBoxes(visionText, imageW)
                if (lineBoxes.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Tidak ada teks yang terbaca pada gambar. Pastikan gambar bukti transfer jelas dan tidak buram."
                        )
                    )
                }

                val parsed = ReceiptTextParser.parseCoordinateLayout(
                    lineBoxes = lineBoxes,
                    imageWidth = imageW,
                    imageHeight = imageH
                )

                val hasUsefulField = parsed.nominal > 0L ||
                    parsed.referensi.isNotBlank() ||
                    parsed.penerima.isNotBlank() ||
                    parsed.nomor.isNotBlank() ||
                    parsed.tujuan.isNotBlank()

                if (!hasUsefulField) {
                    return@withContext Result.failure(
                        OcrUnrecognizedException(
                            message = "Data bukti transfer belum terbaca lengkap. Anda dapat menyalin teks hasil OCR di bawah atau melengkapi secara manual.",
                            rawText = parsed.rawText
                        )
                    )
                }

                Result.success(parsed)
            } catch (e: OcrUnrecognizedException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        e.localizedMessage ?: "Gagal memproses gambar bukti transaksi."
                    )
                )
            } finally {
                if (processedBitmap != null && processedBitmap !== fullBitmap) {
                    processedBitmap.recycle()
                }
                fullBitmap?.recycle()
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
     * Loads the image at 100% full original resolution without quality compression
     * and without cropping top/bottom margins so boundingBox coordinates remain intact.
     */
    internal fun decodeFullResolutionBitmap(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val stream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Tidak dapat membuka file gambar.")

        val decodeOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = stream.use { s ->
            BitmapFactory.decodeStream(s, null, decodeOpts)
        } ?: throw IllegalStateException("Format gambar tidak didukung.")

        val rotated = applyExifRotationIfNeeded(uri, decoded)
        if (rotated !== decoded) {
            decoded.recycle()
        }
        return rotated
    }

    /**
     * Preprocessing specifically tuned for downloaded/shared M-Banking & E-Wallet receipts:
     * 1. Integer Upscale 2x using filter = false to keep small pixel edges sharp.
     * 2. Binarization / Luminance Thresholding (luminance > 200 -> WHITE, <= 200 -> BLACK)
     *    to strip repeating background watermarks (e.g., BCA/DANA/BRI watermarks).
     */
    internal fun preprocessReceiptBitmap(source: Bitmap): Bitmap {
        val upscaled = Bitmap.createScaledBitmap(
            source,
            max(1, source.width * 2),
            max(1, source.height * 2),
            false
        )

        val w = upscaled.width
        val h = upscaled.height
        val pixels = IntArray(w * h)
        upscaled.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = (px ushr 24) and 0xFF
            if (a < 128) {
                pixels[i] = Color.WHITE
                continue
            }
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val luminance = (299 * r + 587 * g + 114 * b) / 1000
            pixels[i] = if (luminance > 200) Color.WHITE else Color.BLACK
        }

        val output = if (upscaled.isMutable) {
            upscaled.apply { setPixels(pixels, 0, w, 0, 0, w, h) }
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
                upscaled.recycle()
            }
        }
        return output
    }

    /**
     * Extracts Text.Line items with exact boundingBox coordinates.
     * If ML Kit merges a left label and a right value into a single Text.Line across a wide
     * horizontal gap, splits at element boundaries so left < 0.45*W and right >= 0.45*W
     * retain accurate per-column coordinates.
     */
    private fun extractLineBoxes(visionText: Text, imageWidth: Float): List<OcrLineBox> {
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
                    .coerceAtLeast(16f)
                val gapThreshold = max(lineHeight * 1.35f, imageWidth * 0.055f)

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
        return boxes.sortedWith(compareBy<OcrLineBox> { it.top }.thenBy { it.left })
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
