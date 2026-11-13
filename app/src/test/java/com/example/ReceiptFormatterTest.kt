package com.example

import com.example.model.ServiceMode
import com.example.model.StoreSettings
import com.example.model.TransactionForm
import com.example.util.ReceiptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormatterTest {

    @Test
    fun `formatThousands and formatRupiah format Indonesian numbers accurately`() {
        assertEquals("0", ReceiptFormatter.formatThousands(0L))
        assertEquals("6.000", ReceiptFormatter.formatThousands(6000L))
        assertEquals("999.236", ReceiptFormatter.formatThousands(999236L))
        assertEquals("Rp 1.005.236", ReceiptFormatter.formatRupiah(1005236L))
    }

    @Test
    fun `calculateAutoAdminFee respects configured tiers and aboveMaxFee`() {
        val settings = StoreSettings()
        assertEquals(0L, ReceiptFormatter.calculateAutoAdminFee(0L, settings))
        assertEquals(3000L, ReceiptFormatter.calculateAutoAdminFee(50_000L, settings))
        assertEquals(3000L, ReceiptFormatter.calculateAutoAdminFee(100_000L, settings))
        assertEquals(5000L, ReceiptFormatter.calculateAutoAdminFee(500_000L, settings))
        assertEquals(6000L, ReceiptFormatter.calculateAutoAdminFee(999_236L, settings))
        assertEquals(10000L, ReceiptFormatter.calculateAutoAdminFee(2_500_000L, settings))
        assertEquals(15000L, ReceiptFormatter.calculateAutoAdminFee(5_000_000L, settings))
    }

    @Test
    fun `thermal 32-char receipt lines never exceed 32 characters`() {
        val tx = TransactionForm(
            mode = ServiceMode.BANK,
            tanggal = "12 Nov 2026",
            waktu = "15:17:50 WIB",
            referensi = "9527120261112151750379TRF1737080495",
            tujuan = "BCA",
            nomor = "758-096-8702",
            penerima = "SINERGI KOMUNIKASI SEJAH",
            nominal = 999_236L,
            admin = 6_000L
        )
        val output = ReceiptFormatter.buildThermal32String(tx, StoreSettings())
        val lines = output.lines()
        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            assertTrue("Line exceeded 32 chars: '$line' (${line.length})", line.length <= 32)
        }
        assertTrue(output.contains("Bank Tujuan                  BCA"))
        assertTrue(output.contains("TOTAL               Rp 1.005.236"))
    }

    @Test
    fun `ewallet mode switches labels to E-Wallet and Nomor`() {
        val tx = TransactionForm(
            mode = ServiceMode.EWALLET,
            tujuan = "DANA",
            nomor = "081234567890",
            penerima = "BUDI SANTOSO",
            nominal = 100_000L,
            admin = 3_000L
        )
        val output = ReceiptFormatter.buildThermal32String(tx, StoreSettings())
        assertTrue(output.contains("E-Wallet                    DANA"))
        assertTrue(output.contains("Nomor               081234567890"))
    }

    @Test
    fun `esc pos payload starts with ESC reset and ends with feed command`() {
        val payload = ReceiptFormatter.buildEscPosPayload(TransactionForm(), StoreSettings())
        assertTrue(payload.size > 50)
        assertEquals(0x1B.toByte(), payload[0])
        assertEquals(0x40.toByte(), payload[1])
        assertEquals(0x1B.toByte(), payload[payload.size - 3])
        assertEquals(0x64.toByte(), payload[payload.size - 2])
        assertEquals(0x03.toByte(), payload[payload.size - 1])
    }
}
