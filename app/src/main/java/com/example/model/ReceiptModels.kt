package com.example.model

enum class ServiceMode {
    BANK,
    EWALLET
}

data class AdminTier(
    val id: Long = System.nanoTime(),
    val maxNominal: Long,
    val fee: Long
)

data class StoreSettings(
    val apiKeyOverride: String = "",
    val storeName: String = "AURORA CELL",
    val storeAddress: String = "Kota Semarang, Jawa Tengah",
    val footerLine1: String = "Terima kasih telah bertransaksi",
    val footerLine2: String = "Simpan struk sebagai bukti sah",
    val tiers: List<AdminTier> = defaultTiers(),
    val aboveMaxFee: Long = 15_000L
) {
    companion object {
        fun defaultTiers(): List<AdminTier> = listOf(
            AdminTier(id = 1L, maxNominal = 100_000L, fee = 3_000L),
            AdminTier(id = 2L, maxNominal = 500_000L, fee = 5_000L),
            AdminTier(id = 3L, maxNominal = 1_000_000L, fee = 6_000L),
            AdminTier(id = 4L, maxNominal = 2_500_000L, fee = 10_000L)
        )
    }
}

data class TransactionForm(
    val mode: ServiceMode = ServiceMode.BANK,
    val tanggal: String = "12 Nov 2026",
    val waktu: String = "15:17:50 WIB",
    val referensi: String = "9527120261112151750379TRF1737080495",
    val tujuan: String = "BCA",
    val nomor: String = "758-096-8702",
    val penerima: String = "SINERGI KOMUNIKASI SEJAH",
    val nominal: Long = 999_236L,
    val admin: Long = 6_000L,
    val adminAutoMode: Boolean = true
) {
    val total: Long get() = nominal + admin
}

sealed interface OcrUiState {
    data object Idle : OcrUiState
    data object Loading : OcrUiState
    data class Success(val message: String) : OcrUiState
    data class Error(val message: String) : OcrUiState
}

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

data class OcrParsedResult(
    val tipe: ServiceMode,
    val tanggal: String,
    val waktu: String,
    val referensi: String,
    val tujuan: String,
    val nomor: String,
    val penerima: String,
    val nominal: Long
)
