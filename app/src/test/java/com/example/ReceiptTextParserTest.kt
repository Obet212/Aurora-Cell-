package com.example

import com.example.data.OcrLineBox
import com.example.data.ReceiptTextParser
import com.example.model.ServiceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTextParserTest {

    @Test
    fun `parses myBCA screenshot with right-aligned wrapped name, spaced rekening, and 3-line ref`() {
        val boxes = listOf(
            OcrLineBox("Transfer Berhasil", 320f, 240f, 680f, 280f),
            OcrLineBox("12 Nov 2026 15:17:50", 330f, 290f, 670f, 320f),
            OcrLineBox("IDR 999,236.00", 240f, 335f, 760f, 385f),
            OcrLineBox("Nama Penerima", 50f, 440f, 320f, 475f),
            OcrLineBox("SINERGI", 760f, 440f, 950f, 475f),
            OcrLineBox("KOMUNIKASI SEJAH", 520f, 485f, 950f, 520f),
            OcrLineBox("Rekening Tujuan", 50f, 560f, 350f, 595f),
            OcrLineBox("758 - 096 - 8702", 620f, 560f, 950f, 595f),
            OcrLineBox("Jenis Transaksi", 50f, 640f, 340f, 675f),
            OcrLineBox("Transfer ke", 700f, 640f, 950f, 675f),
            OcrLineBox("Rekening BCA", 650f, 685f, 950f, 720f),
            OcrLineBox("Dari Rekening", 50f, 820f, 310f, 855f),
            OcrLineBox("816 - 5** - **80", 630f, 820f, 950f, 855f),
            OcrLineBox("Nominal Tujuan", 50f, 940f, 340f, 975f),
            OcrLineBox("IDR 999,236.00", 630f, 940f, 950f, 975f),
            OcrLineBox("Berita", 50f, 1020f, 160f, 1055f),
            OcrLineBox("No. Referensi", 50f, 1100f, 310f, 1135f),
            OcrLineBox("952712026111215175", 500f, 1100f, 950f, 1135f),
            OcrLineBox("0379TRF173708049", 505f, 1140f, 950f, 1175f),
            OcrLineBox("5", 920f, 1180f, 950f, 1215f)
        )

        val result = ReceiptTextParser.parseSpatialLines(boxes)

        assertEquals(ServiceMode.BANK, result.tipe)
        assertEquals("BCA", result.tujuan)
        assertEquals("12 Nov 2026", result.tanggal)
        assertEquals("15:17:50 WIB", result.waktu)
        assertEquals(999_236L, result.nominal)
        assertEquals("758-096-8702", result.nomor)
        assertEquals("SINERGI KOMUNIKASI SEJAH", result.penerima)
        assertEquals("9527120261112151750379TRF1737080495", result.referensi)
        assertTrue(result.rawText.contains("Nama Penerima: SINERGI KOMUNIKASI SEJAH"))
        assertTrue(result.rawText.contains("Rekening Tujuan: 758 - 096 - 8702"))
    }

    @Test
    fun `parses spatial DANA receipt with masked bullet name and wrapped transaction id`() {
        val boxes = listOf(
            OcrLineBox("13 Nov 2026 • 17:55", 80f, 180f, 400f, 210f),
            OcrLineBox("ID DANA 0896••••5322", 580f, 180f, 920f, 210f),
            OcrLineBox("Transaksi berhasil!", 160f, 240f, 500f, 275f),
            OcrLineBox("Kirim Uang Rp50.000 ke Cahyo A•i Nu••••o -", 80f, 295f, 910f, 330f),
            OcrLineBox("085559659554", 80f, 335f, 340f, 370f),
            OcrLineBox("Total Bayar", 100f, 410f, 310f, 445f),
            OcrLineBox("Rp50.000", 720f, 410f, 900f, 445f),
            OcrLineBox("Metode Pembayaran", 80f, 480f, 380f, 515f),
            OcrLineBox("Saldo DANA", 700f, 480f, 920f, 515f),
            OcrLineBox("Detail Penerima", 80f, 560f, 360f, 595f),
            OcrLineBox("Nama", 80f, 620f, 190f, 655f),
            OcrLineBox("Cahyo A•i Nu••••o", 600f, 620f, 920f, 655f),
            OcrLineBox("Akun DANA", 80f, 680f, 270f, 715f),
            OcrLineBox("085559659554", 660f, 680f, 920f, 715f),
            OcrLineBox("Detail Transaksi", 80f, 760f, 360f, 795f),
            OcrLineBox("ID Transaksi ©", 80f, 820f, 300f, 855f),
            OcrLineBox("202611131012142001", 560f, 820f, 920f, 855f),
            OcrLineBox("010016611231278546", 560f, 865f, 920f, 900f),
            OcrLineBox("6", 895f, 910f, 920f, 945f),
            OcrLineBox("ID Order Merchant", 80f, 970f, 360f, 1005f),
            OcrLineBox("19c81a17-08f0-4854-9", 560f, 970f, 920f, 1005f),
            OcrLineBox("799-1b4b5b185fc3", 610f, 1015f, 920f, 1050f)
        )

        val result = ReceiptTextParser.parseSpatialLines(boxes)

        assertEquals(ServiceMode.EWALLET, result.tipe)
        assertEquals("DANA", result.tujuan)
        assertEquals("13 Nov 2026", result.tanggal)
        assertEquals("17:55 WIB", result.waktu)
        assertEquals(50_000L, result.nominal)
        assertEquals("085559659554", result.nomor)
        assertEquals("CAHYO A*I NU****O", result.penerima)
        assertEquals("2026111310121420010100166112312785466", result.referensi)
    }

    @Test
    fun `parses Qita by BRI sender-recipient card layout accurately`() {
        val boxes = listOf(
            OcrLineBox("Qita", 420f, 40f, 580f, 80f),
            OcrLineBox("Transfer Berhasil", 310f, 240f, 690f, 275f),
            OcrLineBox("Rp202.006", 290f, 285f, 710f, 340f),
            OcrLineBox("11 November 2026, 15:28:17 WIB", 210f, 350f, 790f, 385f),
            OcrLineBox("Detail Transaksi", 90f, 410f, 380f, 445f),
            OcrLineBox("BRI", 130f, 470f, 200f, 500f),
            OcrLineBox("ADHI NUGROHO", 260f, 470f, 560f, 505f),
            OcrLineBox("BANK BRI - 6051 **** **** 500", 260f, 515f, 760f, 550f),
            OcrLineBox("OE", 140f, 610f, 200f, 645f),
            OcrLineBox("ORDER KUOTA EVOLUSI", 260f, 610f, 720f, 645f),
            OcrLineBox("BANK BRI - 0055 0100 2452 301", 260f, 655f, 780f, 690f),
            OcrLineBox("No. Referensi", 90f, 770f, 320f, 805f),
            OcrLineBox("250029825490", 650f, 770f, 910f, 805f),
            OcrLineBox("Jenis Transaksi", 90f, 825f, 350f, 860f),
            OcrLineBox("Transfer Bank BRI", 600f, 825f, 910f, 860f),
            OcrLineBox("Catatan", 90f, 900f, 220f, 935f),
            OcrLineBox("-", 890f, 900f, 910f, 935f),
            OcrLineBox("Nominal", 90f, 975f, 240f, 1010f),
            OcrLineBox("Rp202.006", 700f, 975f, 910f, 1010f),
            OcrLineBox("Biaya Admin", 90f, 1030f, 300f, 1065f),
            OcrLineBox("Rp0", 830f, 1030f, 910f, 1065f)
        )

        val result = ReceiptTextParser.parseSpatialLines(boxes)

        assertEquals(ServiceMode.BANK, result.tipe)
        assertEquals("BRI", result.tujuan)
        assertEquals("11 Nov 2026", result.tanggal)
        assertEquals("15:28:17 WIB", result.waktu)
        assertEquals(202_006L, result.nominal)
        assertEquals("0055 0100 2452 301", result.nomor)
        assertEquals("ORDER KUOTA EVOLUSI", result.penerima)
        assertEquals("250029825490", result.referensi)
    }

    @Test
    fun `parses unsplit prefix label rows when ML Kit omits column gap`() {
        val raw = """
            Transfer Berhasil
            12 Nov 2026 15:17:50
            Nama Penerima SINERGI KOMUNIKASI SEJAH
            Rekening Tujuan 758 - 096 - 8702
            Nominal Tujuan IDR 999,236.00
            No. Referensi 9527120261112151750379TRF1737080495
        """.trimIndent()

        val result = ReceiptTextParser.parseRawText(raw)

        assertEquals(ServiceMode.BANK, result.tipe)
        assertEquals(999_236L, result.nominal)
        assertEquals("758-096-8702", result.nomor)
        assertEquals("SINERGI KOMUNIKASI SEJAH", result.penerima)
        assertEquals("9527120261112151750379TRF1737080495", result.referensi)
    }

    @Test
    fun `normalizes OCR letter O confusion in rupiah amounts`() {
        assertEquals(140_000L, ReceiptTextParser.parseRupiahAmount("Rp14O.OOO"))
        assertEquals(999_236L, ReceiptTextParser.parseRupiahAmount("Rp 999.236,00"))
        assertEquals(50_000L, ReceiptTextParser.parseRupiahAmount("IDR 50,000.00"))
    }
}
