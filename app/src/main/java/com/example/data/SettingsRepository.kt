package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AdminTier
import com.example.model.ServiceMode
import com.example.model.StoreSettings
import com.example.model.TransactionForm
import com.example.util.ReceiptFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.auroraDataStore: DataStore<Preferences> by preferencesDataStore(name = "aurora_cell_prefs")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val STORE_SETTINGS_JSON = stringPreferencesKey("store_settings_json")
        val DRAFT_TX_JSON = stringPreferencesKey("draft_tx_json")
    }

    val settingsFlow: Flow<StoreSettings> = context.auroraDataStore.data.map { prefs ->
        val raw = prefs[Keys.STORE_SETTINGS_JSON]
        if (raw.isNullOrBlank()) {
            StoreSettings()
        } else {
            decodeSettings(raw)
        }
    }

    val draftTransactionFlow: Flow<TransactionForm?> = context.auroraDataStore.data.map { prefs ->
        val raw = prefs[Keys.DRAFT_TX_JSON]
        if (raw.isNullOrBlank()) null else decodeDraft(raw)
    }

    suspend fun saveSettings(settings: StoreSettings) {
        val sortedTiers = settings.tiers
            .filter { it.maxNominal > 0L }
            .sortedBy { it.maxNominal }
            .ifEmpty { StoreSettings.defaultTiers() }

        val normalized = settings.copy(
            apiKeyOverride = settings.apiKeyOverride.trim(),
            storeName = ReceiptFormatter.sanitizeSingleLine(settings.storeName)
                .uppercase()
                .ifEmpty { "AURORA CELL" },
            storeAddress = ReceiptFormatter.sanitizeSingleLine(settings.storeAddress)
                .ifEmpty { "Kota Semarang, Jawa Tengah" },
            footerLine1 = ReceiptFormatter.sanitizeSingleLine(settings.footerLine1)
                .ifEmpty { "Terima kasih telah bertransaksi" },
            footerLine2 = ReceiptFormatter.sanitizeSingleLine(settings.footerLine2),
            tiers = sortedTiers
        )

        context.auroraDataStore.edit { prefs ->
            prefs[Keys.STORE_SETTINGS_JSON] = encodeSettings(normalized)
        }
    }

    suspend fun saveDraft(tx: TransactionForm) {
        context.auroraDataStore.edit { prefs ->
            prefs[Keys.DRAFT_TX_JSON] = encodeDraft(tx)
        }
    }

    private fun encodeSettings(s: StoreSettings): String {
        val tiersArray = JSONArray()
        s.tiers.forEach { t ->
            tiersArray.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("max", t.maxNominal)
                    put("fee", t.fee)
                }
            )
        }
        return JSONObject().apply {
            put("apiKeyOverride", s.apiKeyOverride)
            put("storeName", s.storeName)
            put("storeAddress", s.storeAddress)
            put("footerLine1", s.footerLine1)
            put("footerLine2", s.footerLine2)
            put("tiers", tiersArray)
            put("aboveMaxFee", s.aboveMaxFee)
        }.toString()
    }

    private fun decodeSettings(raw: String): StoreSettings {
        return try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("tiers")
            val tiers = mutableListOf<AdminTier>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val max = item.optLong("max", 0L)
                    val fee = item.optLong("fee", 0L)
                    val id = item.optLong("id", (i + 1).toLong())
                    if (max > 0L) {
                        tiers.add(AdminTier(id = id, maxNominal = max, fee = fee))
                    }
                }
            }
            StoreSettings(
                apiKeyOverride = obj.optString("apiKeyOverride", ""),
                storeName = obj.optString("storeName", "AURORA CELL").ifBlank { "AURORA CELL" },
                storeAddress = obj.optString("storeAddress", "Kota Semarang, Jawa Tengah")
                    .ifBlank { "Kota Semarang, Jawa Tengah" },
                footerLine1 = obj.optString("footerLine1", "Terima kasih telah bertransaksi")
                    .ifBlank { "Terima kasih telah bertransaksi" },
                footerLine2 = obj.optString("footerLine2", "Simpan struk sebagai bukti sah"),
                tiers = tiers.sortedBy { it.maxNominal }.ifEmpty { StoreSettings.defaultTiers() },
                aboveMaxFee = obj.optLong("aboveMaxFee", 15_000L)
            )
        } catch (_: Exception) {
            StoreSettings()
        }
    }

    private fun encodeDraft(tx: TransactionForm): String {
        return JSONObject().apply {
            put("mode", tx.mode.name)
            put("tanggal", tx.tanggal)
            put("waktu", tx.waktu)
            put("referensi", tx.referensi)
            put("tujuan", tx.tujuan)
            put("nomor", tx.nomor)
            put("penerima", tx.penerima)
            put("nominal", tx.nominal)
            put("admin", tx.admin)
            put("adminAutoMode", tx.adminAutoMode)
        }.toString()
    }

    private fun decodeDraft(raw: String): TransactionForm? {
        return try {
            val obj = JSONObject(raw)
            val mode = if (obj.optString("mode") == ServiceMode.EWALLET.name) {
                ServiceMode.EWALLET
            } else {
                ServiceMode.BANK
            }
            TransactionForm(
                mode = mode,
                tanggal = obj.optString("tanggal", ""),
                waktu = obj.optString("waktu", ""),
                referensi = obj.optString("referensi", ""),
                tujuan = obj.optString("tujuan", ""),
                nomor = obj.optString("nomor", ""),
                penerima = obj.optString("penerima", ""),
                nominal = obj.optLong("nominal", 0L),
                admin = obj.optLong("admin", 0L),
                adminAutoMode = obj.optBoolean("adminAutoMode", true)
            )
        } catch (_: Exception) {
            null
        }
    }
}
