package com.example

import com.example.data.OcrLineBox
import com.example.data.ReceiptTextParser
import com.example.model.ServiceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTextParserTest {

    @Test
    fun `parses spatial DANA receipt with wrapped transaction id accurately`() {
        val boxes = listOf(
            OcrLineBox("13 Nov 2026 • 20:45", 40f, 120f, 360f, 150f),
            OcrLineBox("ID DANA", 660f, 120f, 800f, 150f),
            OcrLineBox("Transaksi berhasil!", 300f, 200f, 700f, 235f),
            OcrLineBox("Kirim Uang Rp140.000 ke Imam Su***no -", 120f, 260f, 880f, 295f),
            OcrLineBox("0895811115111", 350f, 300f, 650f, 335f),
            OcrLineBox("Total Bayar", 60f, 400f, 260f, 435f),
            OcrLineBox("Rp140.000", 720f, 400f, 940f, 435f),
            OcrLineBox("Metode Pembayaran", 60f, 470f, 350f, 505f),
            OcrLineBox("Saldo DANA", 740f, 470f, 940f, 505f),
            OcrLineBox("Detail Penerima", 60f, 560f, 320f, 595f),
            OcrLineBox("Nama", 60f, 620f, 160f, 655f),
            OcrLineBox("Imam Su***no", 700f, 620f, 940f, 655f),
            OcrLineBox("Akun DANA", 60f, 680f, 240f, 715f),
            OcrLineBox("0895811115111", 680f, 680f, 940f, 715f),
            OcrLineBox("Detail Transaksi", 60f, 760f, 340f, 795f),
            OcrLineBox("ID Transaksi ©", 60f, 820f, 280f, 855f),
            OcrLineBox("202611131012141001", 610f, 820f, 940f, 855f),
            OcrLineBox("010016611231191507", 610f, 865f, 940f, 900f),
            OcrLineBox("6", 915f, 910f, 940f, 945f),
            OcrLineBox("ID Order Merchant", 60f, 970f, 340f, 1005f),
            OcrLineBox("202611131012141001", 610f, 970f, 940f, 1005f),
            OcrLineBox("010016611231191507", 610f, 1015f, 940f, 1050f),
            OcrLineBox("5", 915f, 1060f, 940f, 1095f)
        )

        val result = ReceiptTextParser.parseSpatialLines(boxes)

        assertEquals(ServiceMode.EWALLET, result.tipe)
        assertEquals("DANA", result.tujuan)
        assertEquals("13 Nov 2026", result.tanggal)
        assertEquals("20:45 WIB", result.waktu)
        assertEquals(140_000L, result.nominal)
        assertEquals("0895811115111", result.nomor)
        assertEquals("IMAM SU***NO", result.penerima)
        assertEquals("2026111310121410010100166112311915076", result.referensi)
    }

    @Test
    fun `parses BCA m-Transfer receipt with IDR cents and reference`() {
        val raw = """
            m-Transfer
            BERHASIL
            12/11 15:17:50
            Ke: 758-096-8702
            Nama Penerima: SINERGI KOMUNIKASI SEJAH
            Nominal: IDR 999,236.00
            No. Referensi: 9527120261112151750379TRF1737080495
        """.trimIndent()

        val result = ReceiptTextParser.parseRawText(raw)

        assertEquals(ServiceMode.BANK, result.tipe)
        assertEquals("BCA", result.tujuan)
        assertEquals("15:17:50 WIB", result.waktu)
        assertEquals(999_236L, result.nominal)
        assertEquals("758-096-8702", result.nomor)
        assertEquals("SINERGI KOMUNIKASI SEJAH", result.penerima)
        assertEquals("9527120261112151750379TRF1737080495", result.referensi)
    }

    @Test
    fun `parses BRImo receipt prioritizing destination bank and nominal over total`() {
        val boxes = listOf(
            OcrLineBox("BRImo", 400f, 60f, 600f, 100f),
            OcrLineBox("Transaksi Berhasil", 300f, 120f, 700f, 155f),
            OcrLineBox("Tanggal", 60f, 200f, 200f, 235f),
            OcrLineBox("10 Mei 2026, 09:30:12 WIB", 540f, 200f, 940f, 235f),
            OcrLineBox("Nomor Referensi", 60f, 260f, 300f, 295f),
            OcrLineBox("88991122334455", 640f, 260f, 940f, 295f),
            OcrLineBox("Sumber Dana", 60f, 330f, 260f, 365f),
            OcrLineBox("BRI", 850f, 330f, 940f, 365f),
            OcrLineBox("Bank Tujuan", 60f, 400f, 250f, 435f),
            OcrLineBox("MANDIRI", 780f, 400f, 940f, 435f),
            OcrLineBox("Nomor Tujuan", 60f, 460f, 260f, 495f),
            OcrLineBox("1370012345678", 660f, 460f, 940f, 495f),
            OcrLineBox("Nama Penerima", 60f, 520f, 280f, 555f),
            OcrLineBox("BUDI SANTOSO", 660f, 520f, 940f, 555f),
            OcrLineBox("Nominal", 60f, 590f, 200f, 625f),
            OcrLineBox("Rp 250.000", 720f, 590f, 940f, 625f),
            OcrLineBox("Biaya Admin", 60f, 650f, 250f, 685f),
            OcrLineBox("Rp 2.500", 760f, 650f, 940f, 685f),
            OcrLineBox("Total", 60f, 710f, 160f, 745f),
            OcrLineBox("Rp 252.500", 720f, 710f, 940f, 745f)
        )

        val result = ReceiptTextParser.parseSpatialLines(boxes)

        assertEquals(ServiceMode.BANK, result.tipe)
        assertEquals("MANDIRI", result.tujuan)
        assertEquals("10 Mei 2026", result.tanggal)
        assertEquals("09:30:12 WIB", result.waktu)
        assertEquals(250_000L, result.nominal)
        assertEquals("1370012345678", result.nomor)
        assertEquals("BUDI SANTOSO", result.penerima)
        assertEquals("88991122334455", result.referensi)
    }

    @Test
    fun `normalizes OCR letter O confusion in rupiah amounts`() {
        assertEquals(140_000L, ReceiptTextParser.parseRupiahAmount("Rp14O.OOO"))
        assertEquals(999_236L, ReceiptTextParser.parseRupiahAmount("Rp 999.236,00"))
        assertEquals(50_000L, ReceiptTextParser.parseRupiahAmount("IDR 50,000.00"))
    }
}
