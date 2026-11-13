package com.example.print

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.model.BluetoothPrinterDevice
import com.example.model.StoreSettings
import com.example.model.TransactionForm
import com.example.util.ReceiptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID

object ReceiptOutputManager {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun createCameraOutputUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Struk Aurora Cell 58mm", text))
    }

    fun shareToWhatsApp(context: Context, message: String): Boolean {
        return try {
            val waUrl = "https://wa.me/?text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(Intent.createChooser(fallback, "Kirim Struk Digital"))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun printViaRawBt(context: Context, tx: TransactionForm, settings: StoreSettings): Boolean {
        return try {
            val payload = ReceiptFormatter.buildEscPosPayload(tx, settings)
            val base64 = Base64.encodeToString(payload, Base64.NO_WRAP)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("rawbt:base64,$base64")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun printViaSystemPrintService(
        activity: Activity,
        tx: TransactionForm,
        settings: StoreSettings
    ) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val bitmap = renderReceiptBitmap(tx, settings, withStageBackground = false)
        val jobName = "Struk_${settings.storeName.replace(" ", "_")}_${System.currentTimeMillis()}"

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdf = PdfDocument()
                // 58mm width in PostScript points (~164 pt)
                val pageWidthPt = 164
                val scale = pageWidthPt.toFloat() / bitmap.width.toFloat()
                val pageHeightPt = (bitmap.height * scale).toInt().coerceAtLeast(200)

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, 1).create()
                val page = pdf.startPage(pageInfo)
                val destRect = RectF(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat())
                page.canvas.drawBitmap(bitmap, null, destRect, null)
                pdf.finishPage(page)

                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdf.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    pdf.close()
                }
            }
        }

        val media58mm = PrintAttributes.MediaSize("THERMAL_58MM", "Thermal 58mm", 2283, 8000)
        val attrs = PrintAttributes.Builder()
            .setMediaSize(media58mm)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, adapter, attrs)
    }

    suspend fun saveAndSharePng(
        context: Context,
        tx: TransactionForm,
        settings: StoreSettings
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val bitmap = renderReceiptBitmap(tx, settings, withStageBackground = true)
            val fileName = "struk_${settings.storeName.lowercase().replace(Regex("\\s+"), "_")}_${System.currentTimeMillis()}.png"

            // Save a copy to MediaStore Pictures/AuroraCell on Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AuroraCell")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }

            // Save to cache for FileProvider sharing
            val cacheDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val cacheFile = File(cacheDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
            Result.success(shareUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun launchPngShareSheet(context: Context, uri: Uri, storeName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Bukti Transaksi - $storeName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan Gambar Struk PNG"))
    }

    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(context: Context): List<BluetoothPrinterDevice> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter == null || !adapter.isEnabled) return emptyList()
        return adapter.bondedDevices
            ?.map { BluetoothPrinterDevice(name = it.name ?: "Printer Bluetooth", address = it.address) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun printOverBluetoothSpp(
        context: Context,
        deviceAddress: String,
        payload: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = manager?.adapter
            ?: return@withContext Result.failure(IllegalStateException("Perangkat tidak mendukung Bluetooth."))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth nonaktif. Aktifkan Bluetooth terlebih dahulu."))
        }

        val device = adapter.getRemoteDevice(deviceAddress)
        var socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        return@withContext try {
            try {
                adapter.cancelDiscovery()
            } catch (_: SecurityException) {
            }
            socket.connect()
            val out = socket.outputStream
            val chunkSize = 128
            var offset = 0
            while (offset < payload.size) {
                val len = minOf(chunkSize, payload.size - offset)
                out.write(payload, offset, len)
                out.flush()
                offset += len
                Thread.sleep(15)
            }
            Thread.sleep(180)
            socket.close()
            Result.success(Unit)
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            Result.failure(
                IllegalStateException(
                    "Gagal mencetak ke printer: ${e.localizedMessage ?: "Pastikan printer thermal menyala & terpasang."}"
                )
            )
        }
    }

    fun renderReceiptBitmap(
        tx: TransactionForm,
        settings: StoreSettings,
        withStageBackground: Boolean
    ): Bitmap {
        val width = 1080
        val centerX = width / 2f
        val lineWidth = ReceiptFormatter.LINE_WIDTH

        val storeLines = ReceiptFormatter.wrapCenterLines(settings.storeName.uppercase(), 24)
        val addrLines = ReceiptFormatter.wrapCenterLines(settings.storeAddress, 36)
        val dtLines = ReceiptFormatter.formatLeftRightLines("Tanggal", tx.tanggal.ifBlank { "-" }) +
            ReceiptFormatter.formatLeftRightLines("Waktu", tx.waktu.ifBlank { "-" })
        val refLines = ReceiptFormatter.formatReferenceLines(tx.referensi)
        val penerimaLines = ReceiptFormatter.formatLeftRightLines(
            ReceiptFormatter.tujuanLabel(tx.mode),
            ReceiptFormatter.sanitizeSingleLine(tx.tujuan).uppercase().ifEmpty { "-" }
        ) + ReceiptFormatter.formatLeftRightLines(
            ReceiptFormatter.nomorLabel(tx.mode),
            tx.nomor.ifBlank { "-" }
        ) + ReceiptFormatter.formatLeftRightLines(
            "Penerima",
            ReceiptFormatter.sanitizeSingleLine(tx.penerima).uppercase().ifEmpty { "-" }
        )
        val nomLines = ReceiptFormatter.formatLeftRightLines("Nominal", ReceiptFormatter.formatRupiah(tx.nominal)) +
            ReceiptFormatter.formatLeftRightLines("Admin", ReceiptFormatter.formatRupiah(tx.admin))
        val totLines = ReceiptFormatter.formatLeftRightLines("TOTAL", ReceiptFormatter.formatRupiah(tx.total))
        val footerLines = ReceiptFormatter.wrapCenterLines(settings.footerLine1, 36) +
            if (settings.footerLine2.isNotBlank()) ReceiptFormatter.wrapCenterLines(settings.footerLine2, 36) else emptyList()

        val titleH = 74
        val smallH = 48
        val bodyH = 58
        val bodyCount = 1 + dtLines.size + 1 + 1 + refLines.size + 1 + 1 + penerimaLines.size + 1 + nomLines.size + 1 + totLines.size + 1
        val height = 130 + storeLines.size * titleH + addrLines.size * smallH + 40 + bodyCount * bodyH + 40 + footerLines.size * smallH + 130

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (withStageBackground) {
            canvas.drawColor(Color.parseColor("#E2E8F0"))
            val paperPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val topY = 36f
            val botY = height - 36f
            canvas.drawRect(0f, topY, width.toFloat(), botY, paperPaint)

            val toothW = 36f
            val toothH = 18f
            var x = 0f
            while (x < width) {
                val topTooth = Path().apply {
                    moveTo(x, topY)
                    lineTo(x + toothW / 2f, topY - toothH)
                    lineTo(x + toothW, topY)
                    close()
                }
                canvas.drawPath(topTooth, paperPaint)

                val botTooth = Path().apply {
                    moveTo(x, botY)
                    lineTo(x + toothW / 2f, botY + toothH)
                    lineTo(x + toothW, botY)
                    close()
                }
                canvas.drawPath(botTooth, paperPaint)
                x += toothW
            }
        } else {
            canvas.drawColor(Color.WHITE)
        }

        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#090D16")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            textSize = 37f
        }

        var y = 115f
        darkPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        darkPaint.textSize = 58f
        for (line in storeLines) {
            canvas.drawText(line.trim(), centerX, y, darkPaint)
            y += titleH
        }

        for (line in addrLines) {
            canvas.drawText(line.trim(), centerX, y, mutedPaint)
            y += smallH
        }

        y += 30f

        fun drawBody(text: String, bold: Boolean = false) {
            darkPaint.typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            darkPaint.textSize = 44f
            canvas.drawText(text, centerX, y, darkPaint)
            y += bodyH
        }

        drawBody("================================")
        dtLines.forEach { drawBody(it) }
        drawBody("--------------------------------")
        drawBody(ReceiptFormatter.centerPad("No. Referensi", lineWidth))
        refLines.forEach { drawBody(it) }
        drawBody("--------------------------------")
        drawBody(ReceiptFormatter.centerPad("DATA PENERIMA", lineWidth), bold = true)
        penerimaLines.forEach { drawBody(it) }
        drawBody("--------------------------------")
        nomLines.forEach { drawBody(it) }
        drawBody("================================")
        totLines.forEach { drawBody(it, bold = true) }
        drawBody("================================")

        y += 18f
        for (line in footerLines) {
            canvas.drawText(line.trim(), centerX, y, mutedPaint)
            y += smallH
        }

        return bitmap
    }
}
