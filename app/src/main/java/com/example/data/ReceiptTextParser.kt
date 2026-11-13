package com.example.data

import com.example.model.OcrParsedResult
import com.example.model.ServiceMode
import com.example.util.ReceiptFormatter
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

data class OcrLineBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

internal data class LogicalRow(
    val fullText: String,
    val leftLabel: String?,
    val rightValue: String?
)

internal data class TransferParty(
    val name: String,
    val institution: String,
    val mode: ServiceMode,
    val account: String,
    val isMasked: Boolean
)

object ReceiptTextParser {

    private val MONTH_MAP = mapOf(
        "jan" to "Jan", "januari" to "Jan", "january" to "Jan",
        "feb" to "Feb", "februari" to "Feb", "february" to "Feb",
        "mar" to "Mar", "maret" to "Mar", "march" to "Mar",
        "apr" to "Apr", "april" to "Apr",
        "mei" to "Mei", "may" to "Mei",
        "jun" to "Jun", "juni" to "Jun", "june" to "Jun",
        "jul" to "Jul", "juli" to "Jul", "july" to "Jul",
        "agu" to "Agu", "agt" to "Agu", "agustus" to "Agu", "aug" to "Agu", "august" to "Agu",
        "sep" to "Sep", "sept" to "Sep", "september" to "Sep",
        "okt" to "Okt", "oktober" to "Okt", "oct" to "Okt", "october" to "Okt",
        "nov" to "Nov", "november" to "Nov",
        "des" to "Des", "desember" to "Des", "dec" to "Des", "december" to "Des"
    )

    private val MONTH_INDEX_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    private val EWALLET_RULES: List<Pair<String, List<String>>> = listOf(
        "DANA" to listOf("akun dana", "saldo dana", "dana protection", "dana"),
        "OVO" to listOf("ovo cash", "ovo"),
        "GOPAY" to listOf("gopay", "go-pay", "go pay"),
        "SHOPEEPAY" to listOf("shopeepay", "shopee pay", "spay"),
        "LINKAJA" to listOf("linkaja", "link aja"),
        "ISAKU" to listOf("i.saku", "isaku"),
        "SAKUKU" to listOf("sakuku")
    )

    private val BANK_RULES: List<Pair<String, List<String>>> = listOf(
        "BCA" to listOf("bank central asia", "rekening bca", "m-transfer", "mybca", "bca"),
        "BRI" to listOf("bank rakyat indonesia", "bank bri", "brimo", "bri"),
        "MANDIRI" to listOf("bank mandiri", "livin", "mandiri"),
        "BNI" to listOf("bank negara indonesia", "bank bni", "wondr", "bni"),
        "SEABANK" to listOf("seabank", "sea bank"),
        "BSI" to listOf("bank syariah indonesia", "bank bsi", "bsi"),
        "CIMB NIAGA" to listOf("cimb niaga", "cimb", "octo"),
        "PERMATA" to listOf("bank permata", "permata"),
        "DANAMON" to listOf("bank danamon", "danamon"),
        "BANK JAGO" to listOf("bank jago", "jago"),
        "BTN" to listOf("bank btn", "btn"),
        "BTPN" to listOf("btpn", "jenius"),
        "OCBC" to listOf("ocbc"),
        "PANIN" to listOf("panin"),
        "BANK MEGA" to listOf("bank mega"),
        "NEO BANK" to listOf("neobank", "neo bank"),
        "ALLO BANK" to listOf("allo bank")
    )

    private val KNOWN_LABEL_PREFIXES = listOf(
        "nomor rekening tujuan", "no. rekening tujuan", "rekening tujuan",
        "nomor referensi", "no. referensi", "no referensi", "kode referensi",
        "id order merchant", "id transaksi", "no. transaksi", "nomor transaksi",
        "metode pembayaran", "jenis transaksi", "mata uang tujuan", "mata uang asal",
        "nominal tujuan", "nominal transfer", "nominal transaksi",
        "jumlah transfer", "jumlah transaksi",
        "nama penerima", "nama tujuan", "nama pengirim", "nama sumber", "nama bank", "nama toko",
        "bank penerima", "bank tujuan", "tujuan transfer",
        "dari rekening", "sumber dana", "nomor tujuan", "no. tujuan",
        "nomor rekening", "no. rekening", "no rekening",
        "akun shopeepay", "akun gopay", "akun dana", "akun ovo", "akun linkaja",
        "biaya admin", "biaya transaksi", "total bayar", "total transfer",
        "nominal", "jumlah", "catatan", "berita", "penerima", "nama"
    )

    fun parseRawText(rawText: String): OcrParsedResult {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { idx, line ->
                val top = idx * 40f
                OcrLineBox(
                    text = line,
                    left = 0f,
                    top = top,
                    right = 1000f,
                    bottom = top + 30f
                )
            }
        return parseSpatialLines(lines, rawText)
    }

    fun parseSpatialLines(
        lineBoxes: List<OcrLineBox>,
        rawFallbackText: String = lineBoxes.joinToString("\n") { it.text }
    ): OcrParsedResult {
        val cleanedBoxes = lineBoxes
            .map { it.copy(text = ReceiptFormatter.sanitizeSingleLine(it.text)) }
            .filter { it.text.isNotBlank() }

        val rows = buildLogicalRows(cleanedBoxes)
        val allTextLines = (rows.map { it.fullText } + rawFallbackText.lines().map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()

        val parties = extractTransferParties(rows)
        val recipientParty = selectRecipientParty(parties)

        val tanggal = extractDate(allTextLines, rows)
        val waktu = extractTime(allTextLines, rows)
        val nominal = extractNominal(rows, allTextLines)
        val referensi = extractReferensi(rows, allTextLines)
        val (tujuan, mode) = extractTujuanAndMode(rows, allTextLines, recipientParty)
        val nomor = extractNomor(rows, allTextLines, recipientParty, referensi, nominal)
        val penerima = extractPenerima(rows, allTextLines, recipientParty)

        val formattedRawText = if (rows.isNotEmpty()) {
            rows.joinToString("\n") { row ->
                if (row.leftLabel != null && row.rightValue != null) {
                    "${row.leftLabel}: ${row.rightValue}"
                } else {
                    row.fullText
                }
            }
        } else {
            rawFallbackText.trim()
        }

        return OcrParsedResult(
            tipe = mode,
            tanggal = tanggal,
            waktu = waktu,
            referensi = referensi,
            tujuan = tujuan,
            nomor = nomor,
            penerima = penerima,
            nominal = nominal,
            rawText = formattedRawText
        )
    }

    internal fun buildLogicalRows(boxes: List<OcrLineBox>): List<LogicalRow> {
        if (boxes.isEmpty()) return emptyList()

        val minLeft = boxes.minOf { it.left }
        val maxRight = boxes.maxOf { it.right }
        val totalWidth = (maxRight - minLeft).coerceAtLeast(1f)

        val sorted = boxes.sortedWith(compareBy<OcrLineBox> { it.centerY }.thenBy { it.left })
        val visualRows = mutableListOf<MutableList<OcrLineBox>>()

        for (box in sorted) {
            val lastRow = visualRows.lastOrNull()
            if (lastRow == null) {
                visualRows.add(mutableListOf(box))
            } else {
                val avgCenterY = lastRow.map { it.centerY }.average().toFloat()
                val avgHeight = lastRow.map { it.height }.average().toFloat()
                val tolerance = max(avgHeight, box.height) * 0.55f
                if (abs(box.centerY - avgCenterY) <= tolerance) {
                    lastRow.add(box)
                } else {
                    visualRows.add(mutableListOf(box))
                }
            }
        }

        val result = mutableListOf<LogicalRow>()
        var lastRowHadSplit = false
        var lastLeftLabelRight = minLeft
        var lastRightAnchorLeft = Float.MAX_VALUE
        var lastRightAnchorRight = maxRight
        var lastRowBottom = 0f
        var lastRowAvgHeight = 20f

        for (rowItems in visualRows) {
            val ordered = rowItems.sortedBy { it.left }
            val fullText = ordered.joinToString(" ") { it.text }
            val rowTop = ordered.minOf { it.top }
            val rowBottom = ordered.maxOf { it.bottom }
            val firstLeft = ordered.first().left
            val rowRight = ordered.last().right
            val rowHeight = ordered.map { it.height }.average().toFloat()

            var splitIdx = -1
            var maxGap = 0f
            for (i in 0 until ordered.size - 1) {
                val gap = ordered[i + 1].left - ordered[i].right
                if (gap > maxGap) {
                    maxGap = gap
                    splitIdx = i
                }
            }

            val splitThreshold = max(rowHeight * 1.3f, totalWidth * 0.055f)
            val colonSplit = parseColonSplit(fullText)
            val prefixSplit = parseKnownPrefixSplit(fullText)

            if (splitIdx >= 0 && maxGap >= splitThreshold) {
                val leftItems = ordered.subList(0, splitIdx + 1)
                val rightItems = ordered.subList(splitIdx + 1, ordered.size)
                val leftPart = leftItems.joinToString(" ") { it.text }
                val rightPart = rightItems.joinToString(" ") { it.text }
                result.add(
                    LogicalRow(
                        fullText = fullText,
                        leftLabel = cleanLabelNoise(leftPart),
                        rightValue = rightPart.trim()
                    )
                )
                val isAvatarInitialRow = leftPart.length <= 3 &&
                    leftPart.all { it.isLetter() } &&
                    rightItems.first().left < minLeft + totalWidth * 0.45f
                lastRowHadSplit = !isAvatarInitialRow
                lastLeftLabelRight = leftItems.last().right
                lastRightAnchorLeft = rightItems.first().left
                lastRightAnchorRight = rightItems.last().right
                lastRowBottom = rowBottom
                lastRowAvgHeight = rowHeight
            } else if (colonSplit != null) {
                result.add(
                    LogicalRow(
                        fullText = fullText,
                        leftLabel = cleanLabelNoise(colonSplit.first),
                        rightValue = colonSplit.second.trim()
                    )
                )
                lastRowHadSplit = false
                lastRowBottom = rowBottom
                lastRowAvgHeight = rowHeight
            } else if (prefixSplit != null) {
                result.add(
                    LogicalRow(
                        fullText = fullText,
                        leftLabel = cleanLabelNoise(prefixSplit.first),
                        rightValue = prefixSplit.second.trim()
                    )
                )
                lastRowHadSplit = false
                lastRowBottom = rowBottom
                lastRowAvgHeight = rowHeight
            } else {
                val verticalClose = (rowTop - lastRowBottom) <= max(lastRowAvgHeight, rowHeight) * 2.35f
                val clearedLeftColumn = firstLeft >= lastLeftLabelRight + rowHeight * 0.4f ||
                    firstLeft >= minLeft + totalWidth * 0.30f
                val alignedLeftOfRightCol = firstLeft >= max(
                    lastRightAnchorLeft - totalWidth * 0.12f,
                    minLeft + totalWidth * 0.35f
                )
                val alignedRightEdge = abs(rowRight - lastRightAnchorRight) <= totalWidth * 0.12f

                val isRightContinuation = lastRowHadSplit &&
                    verticalClose &&
                    clearedLeftColumn &&
                    (alignedLeftOfRightCol || alignedRightEdge) &&
                    !looksLikeStandaloneLabel(fullText)

                if (isRightContinuation && result.isNotEmpty()) {
                    val prev = result.removeAt(result.lastIndex)
                    val joinedRight = joinContinuation(prev.rightValue.orEmpty(), fullText)
                    result.add(
                        prev.copy(
                            fullText = "${prev.fullText} $fullText",
                            rightValue = joinedRight
                        )
                    )
                    lastRowBottom = rowBottom
                    lastRightAnchorLeft = minOf(lastRightAnchorLeft, firstLeft)
                    lastRightAnchorRight = maxOf(lastRightAnchorRight, rowRight)
                } else {
                    result.add(
                        LogicalRow(
                            fullText = fullText,
                            leftLabel = null,
                            rightValue = null
                        )
                    )
                    lastRowHadSplit = false
                    lastRowBottom = rowBottom
                    lastRowAvgHeight = rowHeight
                }
            }
        }

        return result
    }

    private fun joinContinuation(existing: String, continuation: String): String {
        val a = existing.trim()
        val b = continuation.trim()
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val aNoSpace = !a.contains(' ')
        val bNoSpace = !b.contains(' ')
        val bothIdLike = aNoSpace && bNoSpace &&
            a.all { it.isLetterOrDigit() || it == '-' } &&
            b.all { it.isLetterOrDigit() || it == '-' } &&
            (a.any { it.isDigit() } && (b.any { it.isDigit() } || b.length <= 4))
        return if (bothIdLike) "$a$b" else "$a $b"
    }

    private fun parseColonSplit(text: String): Pair<String, String>? {
        val idx = text.indexOf(':')
        if (idx <= 1 || idx >= text.length - 1) return null
        val left = text.substring(0, idx).trim()
        val right = text.substring(idx + 1).trim()
        if (left.lastOrNull()?.isDigit() == true && right.firstOrNull()?.isDigit() == true) {
            return null
        }
        if (left.length > 35 || right.isEmpty()) return null
        return left to right
    }

    private fun parseKnownPrefixSplit(text: String): Pair<String, String>? {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        for (prefix in KNOWN_LABEL_PREFIXES) {
            if (lower.startsWith(prefix) && trimmed.length > prefix.length) {
                val nextChar = trimmed[prefix.length]
                if (nextChar == ' ' || nextChar == ':' || nextChar == '\t') {
                    val rest = trimmed.substring(prefix.length).trimStart(' ', ':', '\t').trim()
                    if (rest.isNotEmpty() && !rest.equals("oleh", ignoreCase = true)) {
                        return trimmed.substring(0, prefix.length) to rest
                    }
                }
            }
        }
        return null
    }

    private fun looksLikeStandaloneLabel(text: String): Boolean {
        val norm = text.lowercase().trim()
        if (KNOWN_LABEL_PREFIXES.any { norm.startsWith(it) }) return true
        if (looksLikeCardAccountLine(text)) return true
        return norm.startsWith("id ") || norm.startsWith("no.") || norm.startsWith("nomor ") ||
            norm.startsWith("detail ") || norm.startsWith("metode ") || norm.startsWith("total ") ||
            norm.startsWith("rekening ") || norm.startsWith("dari ") || norm.startsWith("berita") ||
            norm.startsWith("catatan") || norm.startsWith("informasi ") || norm.startsWith("diamankan ")
    }

    private fun looksLikeCardAccountLine(text: String): Boolean {
        val cardLineRegex = Regex("""^(.{2,28}?)\s*[-•|:]\s*([0-9*xX•.\s\-]{6,28})$""")
        val match = cardLineRegex.find(text.trim()) ?: return false
        val instRaw = match.groupValues[1].trim()
        val accountRaw = match.groupValues[2].trim()
        return detectInstitution(instRaw) != null &&
            accountRaw.count { it.isDigit() } >= 3 &&
            !accountRaw.contains(':')
    }

    private fun cleanLabelNoise(label: String): String {
        return label.replace(Regex("[©®™📋❐]+"), "")
            .replace(Regex("\\s+[oOcC]$"), "")
            .trim()
    }

    /**
     * Detects card-style Sender/Recipient blocks common in Qita by BRI, BRImo, Livin', SeaBank, etc.:
     *   ADHI NUGROHO
     *   BANK BRI - 6051 **** **** 500
     *   ORDER KUOTA EVOLUSI
     *   BANK BRI - 0055 0100 2452 301
     */
    internal fun extractTransferParties(rows: List<LogicalRow>): List<TransferParty> {
        val parties = mutableListOf<TransferParty>()
        val cardLineRegex = Regex(
            """^(.{2,28}?)\s*[-•|:]\s*([0-9*xX•.\s\-]{6,28})$"""
        )

        for (idx in rows.indices) {
            val rowText = rows[idx].fullText.trim()
            val match = cardLineRegex.find(rowText) ?: continue
            val instRaw = match.groupValues[1].trim()
            val accountRaw = match.groupValues[2].trim()

            // Verify left side is a known bank/e-wallet and right side has at least 3 digits
            val detected = detectInstitution(instRaw) ?: continue
            if (accountRaw.count { it.isDigit() } < 3) continue
            // Avoid matching date/time lines like "13 Nov 2026 - 17:55"
            if (accountRaw.contains(':')) continue

            val candidateName = findPrecedingPartyName(rows, idx)
            val isMasked = accountRaw.contains('*') || accountRaw.contains('•') || accountRaw.contains('x', ignoreCase = true)

            parties.add(
                TransferParty(
                    name = normalizeDisplayName(candidateName),
                    institution = detected.first,
                    mode = detected.second,
                    account = normalizeAccountNumber(accountRaw),
                    isMasked = isMasked
                )
            )
        }
        return parties
    }

    private fun findPrecedingPartyName(rows: List<LogicalRow>, accountRowIdx: Int): String {
        for (i in accountRowIdx - 1 downTo maxOf(0, accountRowIdx - 2)) {
            val r = rows[i]
            // If avatar initials ("OE", "AN") got split as a short leftLabel, take rightValue!
            val candidate = if (r.leftLabel != null && r.rightValue != null && r.leftLabel.length <= 4) {
                r.rightValue.trim()
            } else {
                r.fullText.trim()
            }
            val lower = candidate.lowercase()
            if (candidate.length <= 2) continue
            if (lower.startsWith("detail ") || lower.startsWith("transfer ") ||
                lower.startsWith("transaksi ") || lower == "dari" || lower == "ke" ||
                lower == "pengirim" || lower == "penerima" ||
                lower.contains("bank ") || lower.contains("rp") ||
                parseDateCandidate(candidate) != null
            ) {
                continue
            }
            if (candidate.any { it.isLetter() }) {
                return candidate
            }
        }
        return ""
    }

    private fun selectRecipientParty(parties: List<TransferParty>): TransferParty? {
        if (parties.isEmpty()) return null
        if (parties.size == 1) return parties.first()
        // When both Sender and Recipient cards appear, Sender is first (usually masked) and Recipient is last
        return parties.lastOrNull { !it.isMasked } ?: parties.last()
    }

    private fun extractDate(lines: List<String>, rows: List<LogicalRow>): String {
        for (row in rows) {
            val label = row.leftLabel?.lowercase().orEmpty()
            val value = row.rightValue.orEmpty()
            if (value.isNotBlank() && (label.contains("tanggal") || label.contains("waktu") || label.contains("tgl"))) {
                parseDateCandidate(value)?.let { return it }
            }
        }
        for (line in lines) {
            parseDateCandidate(line)?.let { return it }
        }
        return ""
    }

    internal fun parseDateCandidate(text: String): String? {
        val namedMonthRegex = Regex(
            """\b(\d{1,2})\s+([A-Za-z]{3,10})\s+(20\d{2})\b""",
            RegexOption.IGNORE_CASE
        )
        namedMonthRegex.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthRaw = match.groupValues[2].lowercase()
            val year = match.groupValues[3]
            val month = MONTH_MAP[monthRaw]
            if (day != null && day in 1..31 && month != null) {
                return "${day.toString().padStart(2, '0')} $month $year"
            }
        }

        val dmyNumericRegex = Regex("""\b(\d{1,2})[/\-](\d{1,2})[/\-](20\d{2})\b""")
        dmyNumericRegex.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthIdx = match.groupValues[2].toIntOrNull()
            val year = match.groupValues[3]
            if (day != null && day in 1..31 && monthIdx != null && monthIdx in 1..12) {
                return "${day.toString().padStart(2, '0')} ${MONTH_INDEX_NAMES[monthIdx - 1]} $year"
            }
        }

        val ymdNumericRegex = Regex("""\b(20\d{2})[/\-](\d{1,2})[/\-](\d{1,2})\b""")
        ymdNumericRegex.find(text)?.let { match ->
            val year = match.groupValues[1]
            val monthIdx = match.groupValues[2].toIntOrNull()
            val day = match.groupValues[3].toIntOrNull()
            if (day != null && day in 1..31 && monthIdx != null && monthIdx in 1..12) {
                return "${day.toString().padStart(2, '0')} ${MONTH_INDEX_NAMES[monthIdx - 1]} $year"
            }
        }

        val dmRegex = Regex("""\b(\d{1,2})/(\d{1,2})\s+\d{1,2}[:.]\d{2}""")
        dmRegex.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthIdx = match.groupValues[2].toIntOrNull()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (day != null && day in 1..31 && monthIdx != null && monthIdx in 1..12) {
                return "${day.toString().padStart(2, '0')} ${MONTH_INDEX_NAMES[monthIdx - 1]} $currentYear"
            }
        }

        return null
    }

    private fun extractTime(lines: List<String>, rows: List<LogicalRow>): String {
        for (row in rows) {
            val label = row.leftLabel?.lowercase().orEmpty()
            val value = row.rightValue.orEmpty()
            if (value.isNotBlank() && (label.contains("waktu") || label.contains("jam") || label.contains("tanggal"))) {
                parseTimeCandidate(value)?.let { return it }
            }
        }
        for (line in lines) {
            parseTimeCandidate(line)?.let { return it }
        }
        return ""
    }

    internal fun parseTimeCandidate(text: String): String? {
        val hmsRegex = Regex("""\b([01]?\d|2[0-3]):([0-5]\d):([0-5]\d)(?:\s*(WIB|WITA|WIT))?\b""", RegexOption.IGNORE_CASE)
        hmsRegex.find(text)?.let { m ->
            val hh = m.groupValues[1].padStart(2, '0')
            val mm = m.groupValues[2]
            val ss = m.groupValues[3]
            val tz = m.groupValues[4].uppercase().ifBlank { "WIB" }
            return "$hh:$mm:$ss $tz"
        }

        val hmRegex = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)(?:\s*(WIB|WITA|WIT))?\b""", RegexOption.IGNORE_CASE)
        hmRegex.find(text)?.let { m ->
            val hh = m.groupValues[1].padStart(2, '0')
            val mm = m.groupValues[2]
            val tz = m.groupValues[3].uppercase().ifBlank { "WIB" }
            return "$hh:$mm $tz"
        }

        val dotTimeRegex = Regex("""\b([01]?\d|2[0-3])\.([0-5]\d)(?:\.([0-5]\d))?\s*(WIB|WITA|WIT)\b""", RegexOption.IGNORE_CASE)
        dotTimeRegex.find(text)?.let { m ->
            val hh = m.groupValues[1].padStart(2, '0')
            val mm = m.groupValues[2]
            val ss = m.groupValues[3]
            val tz = m.groupValues[4].uppercase()
            return if (ss.isNotEmpty()) "$hh:$mm:$ss $tz" else "$hh:$mm $tz"
        }

        return null
    }

    private fun extractNominal(rows: List<LogicalRow>, lines: List<String>): Long {
        val primaryLabels = listOf(
            "nominal tujuan", "nominal transfer", "nominal transaksi", "jumlah transfer",
            "jumlah transaksi", "nilai transfer", "besar transfer",
            "nominal", "jumlah"
        )
        val totalLabels = listOf(
            "total bayar", "total pembayaran", "total transfer",
            "total transaksi", "total"
        )

        for (target in primaryLabels) {
            for (row in rows) {
                val label = row.leftLabel?.lowercase().orEmpty()
                val value = row.rightValue.orEmpty()
                if (value.isNotBlank() && label.contains(target) && !label.contains("admin") && !label.contains("biaya")) {
                    parseRupiahAmount(value)?.let { if (it > 0L) return it }
                }
            }
        }

        for (target in totalLabels) {
            for (row in rows) {
                val label = row.leftLabel?.lowercase().orEmpty()
                val value = row.rightValue.orEmpty()
                if (value.isNotBlank() && label.contains(target) && !label.contains("admin") && !label.contains("biaya")) {
                    parseRupiahAmount(value)?.let { if (it > 0L) return it }
                }
            }
        }

        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("admin") || lower.contains("biaya") || lower.contains("saldo") || lower.contains("poin") || lower.contains("npwp")) {
                continue
            }
            if (lower.contains("kirim uang") || lower.contains("transfer") || lower.contains("nominal") || lower.contains("total bayar")) {
                parseRupiahAmount(line, requirePrefix = true)?.let { if (it > 0L) return it }
            }
        }

        val candidates = mutableListOf<Long>()
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("admin") || lower.contains("biaya") || lower.contains("saldo") || lower.contains("npwp")) {
                continue
            }
            parseRupiahAmount(line, requirePrefix = true)?.let { if (it >= 1_000L) candidates.add(it) }
        }
        return candidates.maxOrNull() ?: 0L
    }

    internal fun parseRupiahAmount(raw: String, requirePrefix: Boolean = false): Long? {
        val normalized = raw
            .replace(Regex("(?i)(rp|idr)\\s*\\.?"), "Rp ")
            .replace(Regex("(?<=\\d)[oO](?=[\\d.,]|$)"), "0")
            .replace(Regex("(?<=[.,])[oO]{2,3}"), { m -> "0".repeat(m.value.length) })

        val pattern = if (requirePrefix) {
            Regex("""Rp\s*([0-9][0-9.,]*)""")
        } else {
            Regex("""(?:Rp\s*)?([0-9][0-9.,]*)""")
        }

        val matches = pattern.findAll(normalized).toList()
        for (match in matches) {
            var numToken = match.groupValues[1].trim('.', ',')
            if (numToken.isEmpty()) continue

            numToken = when {
                Regex("""^[\d.]+,\d{2}$""").matches(numToken) ->
                    numToken.substringBeforeLast(',')
                Regex("""^[\d,]+,\d{2}$""").matches(numToken) && numToken.count { it == ',' } >= 2 ->
                    numToken.substringBeforeLast(',')
                Regex("""^[\d,]+\.\d{2}$""").matches(numToken) ->
                    numToken.substringBeforeLast('.')
                Regex("""^[\d.]+\.\d{2}$""").matches(numToken) && numToken.count { it == '.' } >= 2 ->
                    numToken.substringBeforeLast('.')
                else -> numToken
            }

            if (!requirePrefix && !match.value.contains("Rp") &&
                !numToken.contains('.') && !numToken.contains(',') &&
                numToken.length > 9
            ) {
                continue
            }

            val digits = numToken.filter { it.isDigit() }
            val parsed = digits.toLongOrNull() ?: continue
            if (parsed in 1L..1_000_000_000L) {
                return parsed
            }
        }
        return null
    }

    private fun extractReferensi(rows: List<LogicalRow>, lines: List<String>): String {
        val refLabels = listOf(
            "no. referensi", "nomor referensi", "no referensi", "kode referensi",
            "id transaksi", "no. transaksi", "nomor transaksi",
            "no. ref", "nomor ref", "referensi", "rrn", "ref id", "id ref"
        )
        for (target in refLabels) {
            for (row in rows) {
                val label = row.leftLabel?.lowercase().orEmpty()
                val value = row.rightValue.orEmpty()
                if (value.isNotBlank() && label.contains(target) && !label.contains("merchant")) {
                    val cleaned = value.replace(Regex("\\s+"), "")
                    if (cleaned.length >= 6 && cleaned.any { it.isDigit() }) {
                        return cleaned
                    }
                }
            }
        }

        for (row in rows) {
            val label = row.leftLabel?.lowercase().orEmpty()
            val value = row.rightValue.orEmpty()
            if (value.isNotBlank() && (label == "ref" || label.contains("id order"))) {
                val cleaned = value.replace(Regex("\\s+"), "")
                if (cleaned.length >= 8 && cleaned.any { it.isDigit() }) {
                    return cleaned
                }
            }
        }

        val refTokenRegex = Regex("""\b(?=[A-Z0-9]*\d)[A-Z0-9]{12,45}\b""")
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("npwp") || lower.contains("call center")) continue
            refTokenRegex.find(line)?.let { return it.value }
        }
        return ""
    }

    private fun extractTujuanAndMode(
        rows: List<LogicalRow>,
        lines: List<String>,
        recipientParty: TransferParty?
    ): Pair<String, ServiceMode> {
        val destLabels = listOf(
            "bank tujuan", "bank penerima", "tujuan transfer", "e-wallet", "ewallet",
            "akun dana", "akun ovo", "akun gopay", "akun shopeepay", "akun linkaja",
            "nomor dana", "tujuan", "layanan", "produk"
        )

        // Priority 1: Explicit destination labels
        for (target in destLabels) {
            for (row in rows) {
                val label = row.leftLabel?.lowercase().orEmpty()
                val value = row.rightValue.orEmpty()
                if (label.contains(target) && !label.contains("rekening") && !label.contains("nomor") && !label.contains("mata uang") && !label.contains("nominal") ||
                    (label.contains("akun ") && destLabels.any { label.contains(it) })
                ) {
                    detectInstitution(label)?.let { return it }
                    if (value.isNotBlank()) {
                        detectInstitution(value)?.let { return it }
                        val cleaned = ReceiptFormatter.sanitizeSingleLine(value).uppercase()
                        if (cleaned.isNotBlank() && !cleaned.all { it.isDigit() || it == '-' || it == '*' }) {
                            val mode = if (ReceiptFormatter.EWALLET_KEYWORDS.any { cleaned.contains(it) }) {
                                ServiceMode.EWALLET
                            } else {
                                ServiceMode.BANK
                            }
                            return cleaned to mode
                        }
                    }
                }
            }
        }

        // Priority 2: Recipient card institution (e.g. "BANK BRI - 0055 0100 2452 301")
        if (recipientParty != null) {
            return recipientParty.institution to recipientParty.mode
        }

        // Priority 3: "Jenis Transaksi" row (e.g. "Transfer ke Rekening BCA" / "Transfer Bank BRI")
        for (row in rows) {
            val label = row.leftLabel?.lowercase().orEmpty()
            val value = row.rightValue.orEmpty()
            if (label.contains("jenis transaksi") && value.isNotBlank()) {
                detectInstitution(value)?.let { return it }
            }
        }

        // Priority 4: Non-source lines
        val nonSourceLines = lines.filter { line ->
            val l = line.lowercase()
            !l.contains("sumber dana") && !l.contains("metode pembayaran") &&
                !l.contains("rekening sumber") && !l.contains("dari rekening")
        }
        for (line in nonSourceLines) {
            detectInstitution(line)?.let { return it }
        }

        for (line in lines) {
            detectInstitution(line)?.let { return it }
        }

        return "" to ServiceMode.BANK
    }

    private fun detectInstitution(text: String): Pair<String, ServiceMode>? {
        val lower = text.lowercase()
        for ((name, keywords) in EWALLET_RULES) {
            if (keywords.any { kw -> Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(lower) }) {
                return name to ServiceMode.EWALLET
            }
        }
        for ((name, keywords) in BANK_RULES) {
            if (keywords.any { kw -> Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(lower) }) {
                return name to ServiceMode.BANK
            }
        }
        return null
    }

    private fun extractNomor(
        rows: List<LogicalRow>,
        lines: List<String>,
        recipientParty: TransferParty?,
        referensi: String,
        nominal: Long
    ): String {
        val nomorLabels = listOf(
            "nomor rekening tujuan", "no. rekening tujuan", "rekening tujuan", "no rek tujuan",
            "nomor tujuan", "no. tujuan", "akun dana", "akun ovo", "akun gopay", "akun shopeepay",
            "nomor rekening", "no. rekening", "no rekening", "no. rek", "rekening",
            "nomor hp", "no. hp", "no hp", "nomor telepon", "nomor"
        )

        // Priority 1: Explicit labeled row
        for (target in nomorLabels) {
            for (row in rows) {
                val label = row.leftLabel?.lowercase().orEmpty()
                val value = row.rightValue.orEmpty()
                if (value.isNotBlank() &&
                    label.contains(target) &&
                    !label.contains("dari") &&
                    !label.contains("sumber") &&
                    !label.contains("pengirim") &&
                    !label.contains("referensi")
                ) {
                    val cleaned = normalizeAccountNumber(value)
                    if (cleaned.any { it.isDigit() } && cleaned != referensi) {
                        return cleaned
                    }
                }
            }
        }

        // Priority 2: Recipient party from Sender -> Recipient card (e.g. Qita by BRI / BRImo / Livin')
        if (recipientParty != null && recipientParty.account.any { it.isDigit() }) {
            return recipientParty.account
        }

        // Priority 3: Inline DANA style "ke <Name> - 085559659554"
        val fullJoined = lines.joinToString(" ")
        Regex("""-\s*(08\d{7,12}|\+62\d{8,13}|\d{3,4}\s*-\s*\d{3,4}\s*-\s*\d{3,6})\b""")
            .find(fullJoined)
            ?.let { return normalizeAccountNumber(it.groupValues[1]) }

        // Priority 4: Standalone phone / dashed / space-grouped account number
        val nominalDigits = nominal.toString()
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("referensi") || lower.contains("id transaksi") ||
                lower.contains("id order") || lower.contains("id dana") ||
                lower.contains("dari rekening") || lower.contains("npwp") ||
                lower.contains("call center")
            ) {
                continue
            }
            Regex("""\b(08\d{7,12})\b""").find(line)?.let {
                if (it.value != referensi) return it.value
            }
            Regex("""\b(\d{3,4}\s*-\s*\d{3,4}\s*-\s*\d{3,6})\b""").find(line)?.let {
                val normalized = normalizeAccountNumber(it.value)
                if (normalized != referensi) return normalized
            }
            Regex("""\b((?:\d{4}\s+){2,4}\d{2,4})\b""").find(line)?.let {
                if (it.value.replace(" ", "") != referensi) return it.value.trim()
            }
        }

        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("referensi") || lower.contains("transaksi") ||
                lower.contains("order") || lower.contains("rp") ||
                lower.contains("npwp") || lower.contains("call center") ||
                lower.contains("dari ")
            ) {
                continue
            }
            Regex("""\b(\d{8,16})\b""").find(line)?.let { m ->
                if (m.value != referensi && m.value != nominalDigits) {
                    return m.value
                }
            }
        }

        return ""
    }

    private fun extractPenerima(
        rows: List<LogicalRow>,
        lines: List<String>,
        recipientParty: TransferParty?
    ): String {
        val nameLabels = listOf(
            "nama penerima", "nama tujuan", "penerima", "nama"
        )
        // Priority 1: Explicit labeled row
        for (target in nameLabels) {
            for (row in rows) {
                val label = row.leftLabel?.lowercase().orEmpty()
                val value = row.rightValue.orEmpty()
                if (value.isNotBlank() &&
                    (label == target || label.contains(target)) &&
                    !label.contains("pengirim") &&
                    !label.contains("bank") &&
                    !label.contains("toko") &&
                    !label.contains("produk")
                ) {
                    val cleaned = normalizeDisplayName(value)
                    if (cleaned.any { it.isLetter() }) {
                        return cleaned
                    }
                }
            }
        }

        // Priority 2: Recipient party from Sender -> Recipient card (e.g. "ORDER KUOTA EVOLUSI")
        if (recipientParty != null && recipientParty.name.any { it.isLetter() }) {
            return recipientParty.name
        }

        // Priority 3: Inline "ke <Name> - <Number>"
        val joined = lines.joinToString(" ")
        Regex("""\bke\s+([A-Za-z][A-Za-z0-9*.•·\s']{2,40}?)(?:\s*-\s*\d|\s+08\d|\s*$)""")
            .find(joined)
            ?.let { m ->
                val name = normalizeDisplayName(m.groupValues[1])
                if (name.any { it.isLetter() } && name != "DANA" && name != "OVO") {
                    return name
                }
            }

        return ""
    }

    private fun normalizeAccountNumber(raw: String): String {
        return ReceiptFormatter.sanitizeSingleLine(raw)
            .replace(Regex("\\s*-\\s*"), "-")
            .replace('•', '*')
            .replace('·', '*')
            .trim()
    }

    private fun normalizeDisplayName(raw: String): String {
        return ReceiptFormatter.sanitizeSingleLine(raw)
            .replace('•', '*')
            .replace('·', '*')
            .uppercase()
            .trim()
    }
}
