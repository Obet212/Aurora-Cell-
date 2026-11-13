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

internal data class BandedField(
    val label: String,
    val valueLines: List<String>
) {
    val joinedWithSpace: String
        get() = ReceiptFormatter.sanitizeSingleLine(valueLines.joinToString(" "))
    val joinedNoSpace: String
        get() = valueLines.joinToString("") { it.replace(Regex("\\s+"), "") }
}

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

    private val ANCHOR_LABEL_KEYWORDS = listOf(
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
        "detail penerima", "detail transaksi",
        "tujuan", "nominal", "jumlah", "catatan", "berita", "penerima", "nama"
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
        return parseCoordinateLayout(lines, imageWidth = 1000f, imageHeight = (lines.size * 40f).coerceAtLeast(100f))
    }

    fun parseSpatialLines(
        lineBoxes: List<OcrLineBox>,
        rawFallbackText: String = ""
    ): OcrParsedResult {
        val maxRight = lineBoxes.maxOfOrNull { it.right } ?: 1000f
        val maxBottom = lineBoxes.maxOfOrNull { it.bottom } ?: 1000f
        return parseCoordinateLayout(lineBoxes, imageWidth = maxRight, imageHeight = maxBottom)
    }

    /**
     * Coordinate-based OCR layout engine:
     * 1. Sorts Text.Line bounding boxes vertically by `top`.
     * 2. Detects topmost left-column anchor label (`firstAnchorTop`) to separate Header Zone
     *    from Two-Column Detail Zone.
     * 3. Applies X-Threshold = 0.45 * imageWidth to separate Anchor Labels (< 0.45*W) from
     *    Value Lines (>= 0.45*W).
     * 4. Applies Dynamic Y-Row Banding: [label[i].top - 10px, label[i+1].top - 10px) using
     *    each value box's `centerY` so multi-line names and multi-line references merge cleanly.
     */
    fun parseCoordinateLayout(
        lineBoxes: List<OcrLineBox>,
        imageWidth: Float,
        imageHeight: Float = 0f
    ): OcrParsedResult {
        val cleaned = lineBoxes
            .map { it.copy(text = ReceiptFormatter.sanitizeSingleLine(it.text)) }
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<OcrLineBox> { it.top }.thenBy { it.left })

        if (cleaned.isEmpty()) {
            return OcrParsedResult(
                tipe = ServiceMode.BANK,
                tanggal = "",
                waktu = "",
                referensi = "",
                tujuan = "",
                nomor = "",
                penerima = "",
                nominal = 0L,
                rawText = ""
            )
        }

        val effectiveWidth = if (imageWidth > 0f) imageWidth else cleaned.maxOf { it.right }.coerceAtLeast(1f)
        val xThreshold = 0.45f * effectiveWidth

        // Find topmost anchor label coordinate in left column (left < 0.45 * imageWidth)
        val firstAnchorTop = cleaned
            .firstOrNull { box ->
                box.left < xThreshold && isKnownAnchorLabel(cleanLabelNoise(box.text))
            }?.top ?: Float.MAX_VALUE

        val headerBoxes = cleaned.filter { it.top < firstAnchorTop }
        val bodyBoxes = cleaned.filter { it.top >= firstAnchorTop }

        // Build Header Zone text lines
        val headerLines = groupHorizontalRows(headerBoxes).map { row ->
            row.joinToString(" ") { it.text }
        }

        // Separate 2 columns in body zone: left < xThreshold -> Anchor Labels, left >= xThreshold -> Values
        val leftBoxesRaw = bodyBoxes.filter { it.left < xThreshold }
        val rightBoxes = bodyBoxes.filter { it.left >= xThreshold }
            .sortedWith(compareBy<OcrLineBox> { it.top }.thenBy { it.left })

        // Group same-row tokens on left column (e.g. "ID Transaksi" + "©")
        val mergedAnchorBoxes = groupHorizontalRows(leftBoxesRaw).map { row ->
            OcrLineBox(
                text = row.joinToString(" ") { it.text },
                left = row.minOf { it.left },
                top = row.minOf { it.top },
                right = row.maxOf { it.right },
                bottom = row.maxOf { it.bottom }
            )
        }.sortedBy { it.top }

        // Keep left-column anchor labels intact; only split colon/prefix when a box combines label + value
        val extraBandedFields = mutableListOf<BandedField>()
        val activeAnchors = mergedAnchorBoxes.filter { box ->
            val labelText = cleanLabelNoise(box.text)
            when {
                isKnownAnchorLabelExact(labelText) -> true
                else -> {
                    val split = parseColonSplit(box.text) ?: parseKnownPrefixSplit(labelText)
                    if (split != null) {
                        extraBandedFields.add(BandedField(cleanLabelNoise(split.first), listOf(split.second)))
                        false
                    } else {
                        isKnownAnchorLabel(labelText)
                    }
                }
            }
        }

        // Dynamic Y-Row Banding: [label[i].top - 10px, label[i+1].top - 10px) matching value.centerY
        val bandedFields = mutableListOf<BandedField>()
        for (i in activeAnchors.indices) {
            val anchor = activeAnchors[i]
            val bandStartY = anchor.top - 10f
            val bandEndY = if (i + 1 < activeAnchors.size) {
                activeAnchors[i + 1].top - 10f
            } else {
                Float.MAX_VALUE
            }

            val bandValues = rightBoxes
                .filter { it.centerY >= bandStartY && it.centerY < bandEndY }
                .sortedWith(compareBy<OcrLineBox> { it.top }.thenBy { it.left })
                .map { it.text }

            bandedFields.add(
                BandedField(
                    label = cleanLabelNoise(anchor.text),
                    valueLines = bandValues
                )
            )
        }

        val allBandedFields = (bandedFields + extraBandedFields)
        val bodyAllLines = groupHorizontalRows(bodyBoxes).map { row ->
            row.joinToString(" ") { it.text }
        }
        val allLines = (headerLines + bodyAllLines).distinct()

        // Also detect Sender -> Recipient card blocks (Qita by BRI, BRImo, Livin')
        val parties = extractTransferParties(bodyAllLines)
        val recipientParty = selectRecipientParty(parties)

        val tanggal = extractDate(headerLines, allBandedFields, allLines)
        val waktu = extractTime(headerLines, allBandedFields, allLines)
        val nominal = extractNominal(headerLines, allBandedFields, allLines)
        val referensi = extractReferensi(allBandedFields, allLines)
        val (tujuan, mode) = extractTujuanAndMode(allBandedFields, recipientParty, headerLines, allLines)
        val nomor = extractNomor(allBandedFields, recipientParty, allLines, referensi, nominal)
        val penerima = extractPenerima(allBandedFields, recipientParty, allLines)

        val formattedRawText = buildFormattedRawText(headerLines, allBandedFields, bodyAllLines)

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

    private fun groupHorizontalRows(boxes: List<OcrLineBox>): List<List<OcrLineBox>> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedWith(compareBy<OcrLineBox> { it.centerY }.thenBy { it.left })
        val rows = mutableListOf<MutableList<OcrLineBox>>()
        for (box in sorted) {
            val last = rows.lastOrNull()
            if (last == null) {
                rows.add(mutableListOf(box))
            } else {
                val avgY = last.map { it.centerY }.average().toFloat()
                val avgH = last.map { it.height }.average().toFloat()
                if (abs(box.centerY - avgY) <= max(avgH, box.height) * 0.55f) {
                    last.add(box)
                } else {
                    rows.add(mutableListOf(box))
                }
            }
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    private fun isKnownAnchorLabelExact(text: String): Boolean {
        val norm = text.lowercase().trim().trimEnd(':')
        if (norm.isEmpty()) return false
        return ANCHOR_LABEL_KEYWORDS.any { it == norm }
    }

    private fun isKnownAnchorLabel(text: String): Boolean {
        val norm = text.lowercase().trim().trimEnd(':')
        if (norm.isEmpty()) return false
        return ANCHOR_LABEL_KEYWORDS.any { kw ->
            norm == kw || norm.startsWith(kw)
        }
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
        val trimmed = cleanLabelNoise(text)
        if (isKnownAnchorLabelExact(trimmed)) return null
        val lower = trimmed.lowercase()
        for (prefix in ANCHOR_LABEL_KEYWORDS) {
            if (lower.startsWith(prefix) && trimmed.length > prefix.length) {
                val nextChar = trimmed[prefix.length]
                if (nextChar == ' ' || nextChar == ':' || nextChar == '\t') {
                    val rest = trimmed.substring(prefix.length).trimStart(' ', ':', '\t').trim()
                    if (rest.any { it.isLetterOrDigit() } && !rest.equals("oleh", ignoreCase = true)) {
                        return trimmed.substring(0, prefix.length) to rest
                    }
                }
            }
        }
        return null
    }

    private fun cleanLabelNoise(label: String): String {
        return label.replace(Regex("[©®™📋❐]+"), "")
            .replace(Regex("\\s+[oOcC]$"), "")
            .trim()
            .trimEnd(':')
    }

    private fun buildFormattedRawText(
        headerLines: List<String>,
        bandedFields: List<BandedField>,
        bodyAllLines: List<String>
    ): String {
        val out = mutableListOf<String>()
        out.addAll(headerLines)
        if (bandedFields.isNotEmpty()) {
            bandedFields.forEach { field ->
                if (field.valueLines.isNotEmpty()) {
                    out.add("${field.label}: ${field.joinedWithSpace}")
                } else {
                    out.add(field.label)
                }
            }
        } else {
            out.addAll(bodyAllLines)
        }
        return out.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    internal fun extractTransferParties(lines: List<String>): List<TransferParty> {
        val parties = mutableListOf<TransferParty>()
        val cardLineRegex = Regex("""^(.{2,28}?)\s*[-•|:]\s*([0-9*xX•.\s\-]{6,28})$""")

        for (idx in lines.indices) {
            val rowText = lines[idx].trim()
            val match = cardLineRegex.find(rowText) ?: continue
            val instRaw = match.groupValues[1].trim()
            val accountRaw = match.groupValues[2].trim()

            val detected = detectInstitution(instRaw) ?: continue
            if (accountRaw.count { it.isDigit() } < 3) continue
            if (accountRaw.contains(':')) continue

            val candidateName = findPrecedingPartyName(lines, idx)
            val isMasked = accountRaw.contains('*') || accountRaw.contains('•') || accountRaw.contains('x', ignoreCase = true)

            parties.add(
                TransferParty(
                    name = normalizeDisplayName(candidateName),
                    institution = detected.first,
                    mode = detected.second,
                    account = sanitizeDigitsOnlyAccount(accountRaw),
                    isMasked = isMasked
                )
            )
        }
        return parties
    }

    private fun findPrecedingPartyName(lines: List<String>, accountIdx: Int): String {
        for (i in accountIdx - 1 downTo maxOf(0, accountIdx - 2)) {
            var candidate = lines[i].trim()
            // Strip leading 2-letter avatar initial or 3-4 letter bank badge if merged onto same line
            val avatarPrefix = Regex("""^(?:[A-Z]{2}|BRI|BCA|BNI|MANDIRI)\s+([A-Z][A-Z\s]{3,})$""")
            avatarPrefix.find(candidate)?.let { m ->
                candidate = m.groupValues[1].trim()
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
        return parties.lastOrNull { !it.isMasked } ?: parties.last()
    }

    private fun extractDate(
        headerLines: List<String>,
        bandedFields: List<BandedField>,
        allLines: List<String>
    ): String {
        for (line in headerLines) {
            parseDateCandidate(line)?.let { return it }
        }
        for (field in bandedFields) {
            val l = field.label.lowercase()
            if (l.contains("tanggal") || l.contains("waktu") || l.contains("tgl")) {
                parseDateCandidate(field.joinedWithSpace)?.let { return it }
            }
        }
        for (line in allLines) {
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

    private fun extractTime(
        headerLines: List<String>,
        bandedFields: List<BandedField>,
        allLines: List<String>
    ): String {
        for (line in headerLines) {
            parseTimeCandidate(line)?.let { return it }
        }
        for (field in bandedFields) {
            val l = field.label.lowercase()
            if (l.contains("waktu") || l.contains("jam") || l.contains("tanggal")) {
                parseTimeCandidate(field.joinedWithSpace)?.let { return it }
            }
        }
        for (line in allLines) {
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

    private fun extractNominal(
        headerLines: List<String>,
        bandedFields: List<BandedField>,
        allLines: List<String>
    ): Long {
        val primaryLabels = listOf(
            "nominal tujuan", "nominal transfer", "nominal transaksi", "jumlah transfer",
            "jumlah transaksi", "nilai transfer", "besar transfer", "nominal", "jumlah"
        )
        // Priority 1: Explicit nominal field in two-column bands
        for (target in primaryLabels) {
            for (field in bandedFields) {
                val label = field.label.lowercase()
                if (field.valueLines.isNotEmpty() && label.contains(target) &&
                    !label.contains("admin") && !label.contains("biaya")
                ) {
                    parseRupiahAmount(field.joinedWithSpace)?.let { if (it > 0L) return it }
                }
            }
        }

        // Priority 2: Header Zone main amount (digits after IDR/Rp)
        for (line in headerLines) {
            val lower = line.lowercase()
            if (lower.contains("admin") || lower.contains("biaya") || lower.contains("saldo")) continue
            parseRupiahAmount(line, requirePrefix = true)?.let { if (it > 0L) return it }
        }

        // Priority 3: Total labels in bands (e.g. DANA "Total Bayar Rp50.000")
        val totalLabels = listOf("total bayar", "total pembayaran", "total transfer", "total transaksi", "total")
        for (target in totalLabels) {
            for (field in bandedFields) {
                val label = field.label.lowercase()
                if (field.valueLines.isNotEmpty() && label.contains(target) &&
                    !label.contains("admin") && !label.contains("biaya")
                ) {
                    parseRupiahAmount(field.joinedWithSpace)?.let { if (it > 0L) return it }
                }
            }
        }

        // Priority 4: Any currency-prefixed amount in lines
        val candidates = mutableListOf<Long>()
        for (line in allLines) {
            val lower = line.lowercase()
            if (lower.contains("admin") || lower.contains("biaya") || lower.contains("saldo") || lower.contains("npwp")) {
                continue
            }
            parseRupiahAmount(line, requirePrefix = true)?.let { if (it >= 1_000L) candidates.add(it) }
        }
        return candidates.maxOrNull() ?: 0L
    }

    /**
     * Post-processing for Nominal:
     * 1. Strips ".00" or ",00" decimal suffix BEFORE removing thousand separators.
     * 2. Converts digit-lookalike OCR mistakes (O->0, I/l->1, S->5, B->8) inside numeric tokens.
     */
    internal fun parseRupiahAmount(raw: String, requirePrefix: Boolean = false): Long? {
        val prefixed = raw.replace(Regex("(?i)\\b(rp|idr)\\s*\\.?"), "Rp ")
        val pattern = if (requirePrefix) {
            Regex("""Rp\s*([0-9OolISsB][0-9OolISsB.,]*)""")
        } else {
            Regex("""(?:Rp\s*)?([0-9OolISsB][0-9OolISsB.,]*)""")
        }

        for (match in pattern.findAll(prefixed)) {
            val rawToken = match.groupValues[1].trim('.', ',')
            if (rawToken.isEmpty()) continue
            val corrected = convertLookalikeCharsToDigits(rawToken, keepSeparators = true)
            if (corrected.count { it.isDigit() } == 0) continue

            // Strip trailing ".00" or ",00" cents BEFORE stripping thousand separators
            val withoutCents = when {
                Regex("""^[\d.]+,\d{2}$""").matches(corrected) ->
                    corrected.substringBeforeLast(',')
                Regex("""^[\d,]+,\d{2}$""").matches(corrected) && corrected.count { it == ',' } >= 2 ->
                    corrected.substringBeforeLast(',')
                Regex("""^[\d,]+\.\d{2}$""").matches(corrected) ->
                    corrected.substringBeforeLast('.')
                Regex("""^[\d.]+\.\d{2}$""").matches(corrected) && corrected.count { it == '.' } >= 2 ->
                    corrected.substringBeforeLast('.')
                else -> corrected
            }

            if (!requirePrefix && !match.value.contains("Rp") &&
                !withoutCents.contains('.') && !withoutCents.contains(',') &&
                withoutCents.length > 9
            ) {
                continue
            }

            val digits = withoutCents.filter { it.isDigit() }
            val parsed = digits.toLongOrNull() ?: continue
            if (parsed in 1L..1_000_000_000L) {
                return parsed
            }
        }
        return null
    }

    private fun extractReferensi(bandedFields: List<BandedField>, allLines: List<String>): String {
        val refLabels = listOf(
            "no. referensi", "nomor referensi", "no referensi", "kode referensi",
            "id transaksi", "no. transaksi", "nomor transaksi",
            "no. ref", "nomor ref", "referensi", "rrn", "ref id", "id ref"
        )
        for (target in refLabels) {
            for (field in bandedFields) {
                val label = field.label.lowercase()
                if (field.valueLines.isNotEmpty() && label.contains(target) && !label.contains("merchant")) {
                    // Join all lines in reference band without spaces and uppercase
                    val cleaned = field.joinedNoSpace.uppercase()
                    if (cleaned.length >= 6 && cleaned.any { it.isDigit() }) {
                        return cleaned
                    }
                }
            }
        }

        for (field in bandedFields) {
            val label = field.label.lowercase()
            if (field.valueLines.isNotEmpty() && (label == "ref" || label.contains("id order"))) {
                val cleaned = field.joinedNoSpace.uppercase()
                if (cleaned.length >= 8 && cleaned.any { it.isDigit() }) {
                    return cleaned
                }
            }
        }

        val refTokenRegex = Regex("""\b(?=[A-Z0-9]*\d)[A-Z0-9]{12,45}\b""")
        for (line in allLines) {
            val lower = line.lowercase()
            if (lower.contains("npwp") || lower.contains("call center")) continue
            refTokenRegex.find(line.uppercase())?.let { return it.value }
        }
        return ""
    }

    private fun extractTujuanAndMode(
        bandedFields: List<BandedField>,
        recipientParty: TransferParty?,
        headerLines: List<String>,
        allLines: List<String>
    ): Pair<String, ServiceMode> {
        val destLabels = listOf(
            "bank tujuan", "bank penerima", "tujuan transfer", "e-wallet", "ewallet",
            "akun dana", "akun ovo", "akun gopay", "akun shopeepay", "akun linkaja",
            "nomor dana", "tujuan", "layanan", "produk"
        )

        // Priority 1: Explicit destination anchor band
        for (target in destLabels) {
            for (field in bandedFields) {
                val label = field.label.lowercase()
                val valStr = field.joinedWithSpace
                if (label.contains(target) && !label.contains("rekening") && !label.contains("nomor") &&
                    !label.contains("mata uang") && !label.contains("nominal") ||
                    (label.contains("akun ") && destLabels.any { label.contains(it) })
                ) {
                    detectInstitution(label)?.let { return it }
                    if (valStr.isNotBlank()) {
                        detectInstitution(valStr)?.let { return it }
                        val cleaned = valStr.uppercase()
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

        // Priority 2: Recipient party card
        if (recipientParty != null) {
            return recipientParty.institution to recipientParty.mode
        }

        // Priority 3: "Jenis Transaksi" band
        for (field in bandedFields) {
            if (field.label.lowercase().contains("jenis transaksi") && field.valueLines.isNotEmpty()) {
                detectInstitution(field.joinedWithSpace)?.let { return it }
            }
        }

        // Priority 4: Header or non-source lines
        for (line in headerLines) {
            detectInstitution(line)?.let { return it }
        }
        val nonSourceLines = allLines.filter { line ->
            val l = line.lowercase()
            !l.contains("sumber dana") && !l.contains("metode pembayaran") &&
                !l.contains("rekening sumber") && !l.contains("dari rekening")
        }
        for (line in nonSourceLines) {
            detectInstitution(line)?.let { return it }
        }
        for (line in allLines) {
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
        bandedFields: List<BandedField>,
        recipientParty: TransferParty?,
        allLines: List<String>,
        referensi: String,
        nominal: Long
    ): String {
        val nomorLabels = listOf(
            "nomor rekening tujuan", "no. rekening tujuan", "rekening tujuan", "no rek tujuan",
            "nomor tujuan", "no. tujuan", "akun dana", "akun ovo", "akun gopay", "akun shopeepay",
            "nomor rekening", "no. rekening", "no rekening", "no. rek", "rekening",
            "nomor hp", "no. hp", "no hp", "nomor telepon", "nomor"
        )

        // Priority 1: Explicit rekening/nomor band -> join without spaces + convert lookalikes to pure digits
        for (target in nomorLabels) {
            for (field in bandedFields) {
                val label = field.label.lowercase()
                if (field.valueLines.isNotEmpty() &&
                    label.contains(target) &&
                    !label.contains("dari") &&
                    !label.contains("sumber") &&
                    !label.contains("pengirim") &&
                    !label.contains("referensi")
                ) {
                    val pureDigits = sanitizeDigitsOnlyAccount(field.joinedNoSpace)
                    if (pureDigits.isNotEmpty() && pureDigits != referensi) {
                        return pureDigits
                    }
                }
            }
        }

        // Priority 2: Recipient party card
        if (recipientParty != null && recipientParty.account.isNotEmpty()) {
            return recipientParty.account
        }

        // Priority 3: Inline "- 085559659554"
        val fullJoined = allLines.joinToString(" ")
        Regex("""-\s*(08\d{7,12}|\+62\d{8,13}|\d{3,4}\s*-\s*\d{3,4}\s*-\s*\d{3,6})\b""")
            .find(fullJoined)
            ?.let { return sanitizeDigitsOnlyAccount(it.groupValues[1]) }

        // Priority 4: Standalone account or phone number
        val nominalDigits = nominal.toString()
        for (line in allLines) {
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
                val digits = sanitizeDigitsOnlyAccount(it.value)
                if (digits != referensi) return digits
            }
            Regex("""\b((?:\d{4}\s+){2,4}\d{2,4})\b""").find(line)?.let {
                val digits = sanitizeDigitsOnlyAccount(it.value)
                if (digits != referensi) return digits
            }
            Regex("""\b(\d{8,16})\b""").find(line)?.let { m ->
                if (!lower.contains("rp") && m.value != referensi && m.value != nominalDigits) {
                    return m.value
                }
            }
        }

        return ""
    }

    private fun extractPenerima(
        bandedFields: List<BandedField>,
        recipientParty: TransferParty?,
        allLines: List<String>
    ): String {
        val nameLabels = listOf("nama penerima", "nama tujuan", "penerima", "nama")

        // Priority 1: Explicit recipient name band -> join with single space
        for (target in nameLabels) {
            for (field in bandedFields) {
                val label = field.label.lowercase()
                if (field.valueLines.isNotEmpty() &&
                    (label == target || label.contains(target)) &&
                    !label.contains("pengirim") &&
                    !label.contains("sumber") &&
                    !label.contains("bank") &&
                    !label.contains("toko") &&
                    !label.contains("produk")
                ) {
                    val cleaned = normalizeDisplayName(field.joinedWithSpace)
                    if (cleaned.any { it.isLetter() }) {
                        return cleaned
                    }
                }
            }
        }

        // Priority 2: Recipient party card
        if (recipientParty != null && recipientParty.name.any { it.isLetter() }) {
            return recipientParty.name
        }

        // Priority 3: Inline "ke <Name> - <Number>"
        val joined = allLines.joinToString(" ")
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

    internal fun convertLookalikeCharsToDigits(raw: String, keepSeparators: Boolean = false): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val mapped = when (ch) {
                'O', 'o' -> '0'
                'I', 'l', 'i' -> '1'
                'S', 's' -> '5'
                'B' -> '8'
                else -> ch
            }
            if (mapped.isDigit() || (keepSeparators && (mapped == '.' || mapped == ','))) {
                sb.append(mapped)
            }
        }
        return sb.toString()
    }

    internal fun sanitizeDigitsOnlyAccount(raw: String): String {
        val noSpaces = raw.replace(Regex("\\s+"), "")
        return convertLookalikeCharsToDigits(noSpaces, keepSeparators = false)
    }

    private fun normalizeDisplayName(raw: String): String {
        return ReceiptFormatter.sanitizeSingleLine(raw)
            .replace('•', '*')
            .replace('·', '*')
            .uppercase()
            .trim()
    }
}
