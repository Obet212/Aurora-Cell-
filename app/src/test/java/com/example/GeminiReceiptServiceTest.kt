package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeminiReceiptService
import com.example.firebase.configureAppCheck
import com.example.model.ServiceMode
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiReceiptServiceTest {

    @Test
    fun `initializes default FirebaseApp and Firebase AI model from google-services resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureAppCheck(context, null)

        val apps = FirebaseApp.getApps(context)
        assertTrue("Expected FirebaseApp to be initialized", apps.isNotEmpty())
        assertEquals("aurora-cell", FirebaseApp.getInstance().options.projectId)
        assertNotNull(Firebase.ai.generativeModel("gemini-3.8-flash"))
    }

    @Test
    fun `parses structured Gemini JSON for SeaBank cross-bank transfer`() {
        val json = """
            {
              "mode": "BANK",
              "tanggal": "14 September 2026",
              "waktu": "14:51",
              "referensi": "202609144350 8949687858639",
              "tujuan": "bca",
              "nomor": "758 - 096 - 8702",
              "penerima": "Sinergi Komunikasi Sejah",
              "nominal": 250000,
              "ringkasanTeks": "Tujuan: BCA\nPenerima: SINERGI KOMUNIKASI SEJAH"
            }
        """.trimIndent()

        val result = GeminiReceiptService.parseGeminiJsonResponse(json)

        assertEquals(ServiceMode.BANK, result.tipe)
        assertEquals("14 Sep 2026", result.tanggal)
        assertEquals("14:51 WIB", result.waktu)
        assertEquals("2026091443508949687858639", result.referensi)
        assertEquals("BCA", result.tujuan)
        assertEquals("7580968702", result.nomor)
        assertEquals("SINERGI KOMUNIKASI SEJAH", result.penerima)
        assertEquals(250_000L, result.nominal)
        assertTrue(result.rawText.contains("SINERGI KOMUNIKASI SEJAH"))
    }

    @Test
    fun `forces EWALLET mode when tujuan is an e-wallet keyword`() {
        val json = """
            {
              "mode": "BANK",
              "tanggal": "13 Nov 2026",
              "waktu": "17:55 WIB",
              "referensi": "2026111310121420010100166112312785466",
              "tujuan": "DANA",
              "nomor": "0855 5965 9554",
              "penerima": "Cahyo A•i Nu••••o",
              "nominal": 50000
            }
        """.trimIndent()

        val result = GeminiReceiptService.parseGeminiJsonResponse(json)

        assertEquals(ServiceMode.EWALLET, result.tipe)
        assertEquals("DANA", result.tujuan)
        assertEquals("085559659554", result.nomor)
        assertEquals("CAHYO A*I NU****O", result.penerima)
        assertEquals(50_000L, result.nominal)
        assertTrue(result.rawText.contains("E-Wallet: DANA"))
    }

    @Test
    fun `maps merchant to recipient when penerima is empty on QRIS or retail receipt`() {
        val json = """
            {
              "mode": "EWALLET",
              "tanggal": "13 Nov 2026",
              "waktu": "19:10:00",
              "referensi": "QRIS99887766",
              "tujuan": "ShopeePay",
              "nomor": "081234567890",
              "penerima": "",
              "merchant": "Toko Berkah Jaya",
              "nominal": 75000
            }
        """.trimIndent()

        val result = GeminiReceiptService.parseGeminiJsonResponse(json)

        assertEquals(ServiceMode.EWALLET, result.tipe)
        assertEquals("SHOPEEPAY", result.tujuan)
        assertEquals("TOKO BERKAH JAYA", result.penerima)
        assertEquals("QRIS99887766", result.referensi)
        assertEquals(75_000L, result.nominal)
    }
}
