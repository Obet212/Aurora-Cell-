package com.example.util

import com.example.model.ServiceMode
import com.example.model.StoreSettings
import com.example.model.TransactionForm
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptFormatter {

    const val LINE_WIDTH = 32

    val BANK_CHIPS = listOf("BCA", "BRI", "Mandiri", "BNI", "SeaBank", "BSI")
    val EWALLET_CHIPS = listOf("DANA", "OVO", "GoPay", "ShopeePay", "LinkAja")
    val EWALLET_KEYWORDS = listOf(
        "DANA", "OVO", "GOPAY", "GO-PAY", "SHOPEEPAY", "SHOPEE",
        "LINKAJA", "LINK AJA", "ISAKU", "SAKUKU"
    )
    val QUICK_ADMIN_FEES = listOf(0L, 3_000L, 5_000L, 6_000L, 10_000L)

    fun parseCleanNumber(raw: String): Long {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return 0L
        return digits.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    }

    fun formatThousands(num: Long): String {
        val clean = num.coerceAtLeast(0L).toString()
        val sb = StringBuilder()
        var count = 0
        for (i in clean.length - 1 downTo 0) {
            sb.append(clean[i])
            count++
            if (count % 3 == 0 && i > 0) {
                sb.append('.')
            }
        }
        return sb.reverse().toString()
    }

    fun formatRupiah(num: Long): String = "Rp ${formatThousands(num)}"

    fun sanitizeSingleLine(str: String?): String =
        (str ?: "").replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex("\\s+"), " ").trim()

    fun centerPad(text: String, width: Int = LINE_WIDTH): String {
        val clean = sanitizeSingleLine(text)
        if (clean.length >= width) return clean.take(width)
        val totalSpaces = width - clean.length
        val left = totalSpaces / 2
        val right = totalSpaces - left
        return " ".repeat(left) + clean + " ".repeat(right)
    }

    fun wrapCenterLines(text: String, width: Int = LINE_WIDTH): List<String> {
        val clean = sanitizeSingleLine(text)
        if (clean.isEmpty()) return listOf(" ".repeat(width))
        if (clean.length <= width) return listOf(centerPad(clean, width))

        val words = clean.split(' ')
        val lines = mutableListOf<String>()
        var current = ""

        for (rawWord in words) {
            var word = rawWord
            while (word.length > width) {
                if (current.isNotEmpty()) {
                    lines.add(centerPad(current, width))
                    current = ""
                }
                lines.add(centerPad(word.take(width), width))
                word = word.drop(width)
            }
            if (current.isEmpty()) {
                current = word
            } else if (current.length + 1 + word.length <= width) {
                current = "$current $word"
            } else {
                lines.add(centerPad(current, width))
                current = word
            }
        }
        if (current.isNotEmpty()) {
            lines.add(centerPad(current, width))
        }
        return lines
    }

    fun formatReferenceLines(refText: String): List<String> {
        val clean = sanitizeSingleLine(refText).ifEmpty { "-" }
        if (clean.length <= LINE_WIDTH) {
            return listOf(centerPad(clean, LINE_WIDTH))
        }
        if (!clean.contains(' ') && clean.length <= LINE_WIDTH * 2 - 2) {
            val mid = (clean.length + 1) / 2
            return listOf(
                centerPad(clean.take(mid), LINE_WIDTH),
                centerPad(clean.drop(mid), LINE_WIDTH)
            )
        }
        return clean.chunked(LINE_WIDTH).map { centerPad(it, LINE_WIDTH) }
    }

    fun formatLeftRightLines(label: String, value: String): List<String> {
        val left = sanitizeSingleLine(label)
        val right = sanitizeSingleLine(value).ifEmpty { "-" }

        if (left.length + 1 + right.length <= LINE_WIDTH) {
            val spaces = LINE_WIDTH - left.length - right.length
            return listOf(left + " ".repeat(spaces) + right)
        }

        val firstLineMax = (LINE_WIDTH - left.length - 1).coerceAtLeast(8)
        val nextLineMax = LINE_WIDTH - 2
        val words = right.split(' ')
        val lines = mutableListOf<String>()
        var current = ""
        var isFirst = true

        fun flush(chunk: String) {
            if (isFirst) {
                val gap = (LINE_WIDTH - left.length - chunk.length).coerceAtLeast(1)
                lines.add(left + " ".repeat(gap) + chunk)
                isFirst = false
            } else {
                val gap = (LINE_WIDTH - chunk.length).coerceAtLeast(0)
                lines.add(" ".repeat(gap) + chunk)
            }
        }

        for (rawWord in words) {
            var word = rawWord
            while (word.length > if (isFirst) firstLineMax else nextLineMax) {
                if (current.isNotEmpty()) {
                    flush(current)
                    current = ""
                }
                val limit = if (isFirst) firstLineMax else nextLineMax
                flush(word.take(limit))
                word = word.drop(limit)
            }

            val activeLimit = if (isFirst) firstLineMax else nextLineMax
            if (current.isEmpty()) {
                current = word
            } else if (current.length + 1 + word.length <= activeLimit) {
                current = "$current $word"
            } else {
                flush(current)
                current = word
            }
        }

        if (current.isNotEmpty()) {
            flush(current)
        }

        return lines
    }

    fun calculateAutoAdminFee(nominal: Long, settings: StoreSettings): Long {
        if (nominal <= 0L) return 0L
        val sortedTiers = settings.tiers.filter { it.maxNominal > 0 }.sortedBy { it.maxNominal }
        for (tier in sortedTiers) {
            if (nominal <= tier.maxNominal) {
                return tier.fee
            }
        }
        return settings.aboveMaxFee
    }

    fun tujuanLabel(mode: ServiceMode): String =
        if (mode == ServiceMode.EWALLET) "E-Wallet" else "Bank Tujuan"

    fun nomorLabel(mode: ServiceMode): String =
        if (mode == ServiceMode.EWALLET) "Nomor" else "Rekening"

    fun currentDateFormatted(): String {
        val bulan = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
        val now = Date()
        val day = SimpleDateFormat("dd", Locale.US).format(now)
        val monthIdx = SimpleDateFormat("M", Locale.US).format(now).toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: 0
        val year = SimpleDateFormat("yyyy", Locale.US).format(now)
        return "$day ${bulan[monthIdx]} $year"
    }

    fun currentTimeFormatted(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        return "$time WIB"
    }

    fun buildThermal32String(tx: TransactionForm, settings: StoreSettings): String {
        val lines = mutableListOf<String>()
        val tujuan = sanitizeSingleLine(tx.tujuan).uppercase().ifEmpty { "-" }
        val penerima = sanitizeSingleLine(tx.penerima).uppercase().ifEmpty { "-" }

        lines.addAll(wrapCenterLines(settings.storeName.uppercase(), LINE_WIDTH))
        lines.addAll(wrapCenterLines(settings.storeAddress, LINE_WIDTH))
        lines.add("================================")
        lines.addAll(formatLeftRightLines("Tanggal", tx.tanggal.ifBlank { "-" }))
        lines.addAll(formatLeftRightLines("Waktu", tx.waktu.ifBlank { "-" }))
        lines.add("--------------------------------")
        lines.add(centerPad("No. Referensi", LINE_WIDTH))
        lines.addAll(formatReferenceLines(tx.referensi))
        lines.add("--------------------------------")
        lines.add(centerPad("DATA PENERIMA", LINE_WIDTH))
        lines.addAll(formatLeftRightLines(tujuanLabel(tx.mode), tujuan))
        lines.addAll(formatLeftRightLines(nomorLabel(tx.mode), tx.nomor.ifBlank { "-" }))
        lines.addAll(formatLeftRightLines("Penerima", penerima))
        lines.add("--------------------------------")
        lines.addAll(formatLeftRightLines("Nominal", formatRupiah(tx.nominal)))
        lines.addAll(formatLeftRightLines("Admin", formatRupiah(tx.admin)))
        lines.add("================================")
        lines.addAll(formatLeftRightLines("TOTAL", formatRupiah(tx.total)))
        lines.add("================================")
        lines.addAll(wrapCenterLines(settings.footerLine1, LINE_WIDTH))
        if (settings.footerLine2.isNotBlank()) {
            lines.addAll(wrapCenterLines(settings.footerLine2, LINE_WIDTH))
        }
        return lines.joinToString("\n")
    }

    fun buildEscPosPayload(tx: TransactionForm, settings: StoreSettings): ByteArray {
        val out = ByteArrayOutputStream()
        val tujuan = sanitizeSingleLine(tx.tujuan).uppercase().ifEmpty { "-" }
        val penerima = sanitizeSingleLine(tx.penerima).uppercase().ifEmpty { "-" }

        fun writeBytes(vararg bytes: Int) {
            for (b in bytes) out.write(b)
        }

        fun writeLine(text: String) {
            out.write((text + "\n").toByteArray(Charsets.UTF_8))
        }

        // ESC @ Reset printer
        writeBytes(0x1B, 0x40)

        // Store name: Centered, Bold, Double Width + Height
        writeBytes(0x1B, 0x61, 0x01)
        writeBytes(0x1B, 0x45, 0x01)
        writeBytes(0x1D, 0x21, 0x11)
        writeLine(settings.storeName.uppercase())

        // Reset normal size & bold off for store address
        writeBytes(0x1D, 0x21, 0x00)
        writeBytes(0x1B, 0x45, 0x00)
        writeLine(settings.storeAddress)

        // Align left for 32-column fixed lines
        writeBytes(0x1B, 0x61, 0x00)
        writeLine("================================")
        formatLeftRightLines("Tanggal", tx.tanggal.ifBlank { "-" }).forEach(::writeLine)
        formatLeftRightLines("Waktu", tx.waktu.ifBlank { "-" }).forEach(::writeLine)
        writeLine("--------------------------------")
        writeLine(centerPad("No. Referensi", LINE_WIDTH))
        formatReferenceLines(tx.referensi).forEach(::writeLine)
        writeLine("--------------------------------")

        // DATA PENERIMA bold
        writeBytes(0x1B, 0x45, 0x01)
        writeLine(centerPad("DATA PENERIMA", LINE_WIDTH))
        writeBytes(0x1B, 0x45, 0x00)

        formatLeftRightLines(tujuanLabel(tx.mode), tujuan).forEach(::writeLine)
        formatLeftRightLines(nomorLabel(tx.mode), tx.nomor.ifBlank { "-" }).forEach(::writeLine)
        formatLeftRightLines("Penerima", penerima).forEach(::writeLine)
        writeLine("--------------------------------")
        formatLeftRightLines("Nominal", formatRupiah(tx.nominal)).forEach(::writeLine)
        formatLeftRightLines("Admin", formatRupiah(tx.admin)).forEach(::writeLine)
        writeLine("================================")

        // TOTAL bold
        writeBytes(0x1B, 0x45, 0x01)
        formatLeftRightLines("TOTAL", formatRupiah(tx.total)).forEach(::writeLine)
        writeBytes(0x1B, 0x45, 0x00)
        writeLine("================================")

        wrapCenterLines(settings.footerLine1, LINE_WIDTH).forEach(::writeLine)
        if (settings.footerLine2.isNotBlank()) {
            wrapCenterLines(settings.footerLine2, LINE_WIDTH).forEach(::writeLine)
        }

        // Feed 3 lines (ESC d 3)
        writeBytes(0x1B, 0x64, 0x03)
        return out.toByteArray()
    }

    fun buildWhatsAppMessage(tx: TransactionForm, settings: StoreSettings): String {
        val tujuan = sanitizeSingleLine(tx.tujuan).uppercase().ifEmpty { "-" }
        val penerima = sanitizeSingleLine(tx.penerima).uppercase().ifEmpty { "-" }
        val ref = sanitizeSingleLine(tx.referensi).ifEmpty { "-" }

        val dtCode = (formatLeftRightLines("Tanggal", tx.tanggal.ifBlank { "-" }) +
            formatLeftRightLines("Waktu", tx.waktu.ifBlank { "-" }))
            .map { "```$it```" }

        val penerimaCode = (formatLeftRightLines(tujuanLabel(tx.mode), tujuan) +
            formatLeftRightLines(nomorLabel(tx.mode), tx.nomor.ifBlank { "-" }) +
            formatLeftRightLines("Penerima", penerima))
            .map { "```$it```" }

        val nomCode = (formatLeftRightLines("Nominal", formatRupiah(tx.nominal)) +
            formatLeftRightLines("Admin", formatRupiah(tx.admin)))
            .map { "```$it```" }

        val totCode = formatLeftRightLines("TOTAL", formatRupiah(tx.total))
            .map { "*```$it```*" }

        val lines = mutableListOf(
            "*${settings.storeName.uppercase()}*",
            "_${settings.storeAddress}_",
            "================================"
        )
        lines.addAll(dtCode)
        lines.add("--------------------------------")
        lines.add("No. Referensi")
        lines.add("`$ref`")
        lines.add("--------------------------------")
        lines.add("*DATA PENERIMA*")
        lines.addAll(penerimaCode)
        lines.add("--------------------------------")
        lines.addAll(nomCode)
        lines.add("================================")
        lines.addAll(totCode)
        lines.add("================================")
        lines.add("_${settings.footerLine1}_")
        if (settings.footerLine2.isNotBlank()) {
            lines.add("_${settings.footerLine2}_")
        }
        return lines.joinToString("\n")
    }
}
