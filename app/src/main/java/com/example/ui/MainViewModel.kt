package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiReceiptService
import com.example.data.OcrUnrecognizedException
import com.example.data.OfflineOcrService
import com.example.data.SettingsRepository
import com.example.model.AdminTier
import com.example.model.BluetoothPrinterDevice
import com.example.model.OcrUiState
import com.example.model.ScanEngine
import com.example.model.ServiceMode
import com.example.model.StoreSettings
import com.example.model.TransactionForm
import com.example.print.ReceiptOutputManager
import com.example.util.ReceiptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AppTab {
    INPUT_OCR,
    PREVIEW_PRINT,
    SETTINGS
}

data class MainUiState(
    val activeTab: AppTab = AppTab.INPUT_OCR,
    val tx: TransactionForm = TransactionForm(),
    val settings: StoreSettings = StoreSettings(),
    val ocrState: OcrUiState = OcrUiState.Idle,
    val selectedImageUri: Uri? = null,
    val pairedPrinters: List<BluetoothPrinterDevice> = emptyList(),
    val showPrinterDialog: Boolean = false,
    val isPrintingBluetooth: Boolean = false,
    val lastOcrRawText: String = "",
    val scanEngine: ScanEngine = ScanEngine.GEMINI_AI
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application.applicationContext)
    private val ocrService = OfflineOcrService(application.applicationContext)
    private val geminiService = GeminiReceiptService(application.applicationContext)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val savedSettings = repository.settingsFlow.first()
            val savedDraft = repository.draftTransactionFlow.first()
            val initialTx = if (savedDraft != null && (savedDraft.nominal > 0L || savedDraft.penerima.isNotBlank() || savedDraft.referensi.isNotBlank())) {
                savedDraft
            } else {
                TransactionForm()
            }
            _uiState.update {
                it.copy(
                    settings = savedSettings,
                    tx = initialTx
                )
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setServiceMode(mode: ServiceMode, notify: Boolean = true) {
        updateTx { it.copy(mode = mode) }
        if (notify) {
            emitMessage(
                if (mode == ServiceMode.EWALLET) {
                    "Mode E-Wallet: Label menjadi E-Wallet & Nomor"
                } else {
                    "Mode Transfer Bank: Label menjadi Bank Tujuan & Rekening"
                }
            )
        }
    }

    fun updateTanggal(value: String) = updateTx { it.copy(tanggal = value) }
    fun updateWaktu(value: String) = updateTx { it.copy(waktu = value) }
    fun updateReferensi(value: String) = updateTx { it.copy(referensi = value) }
    fun updateTujuan(value: String) = updateTx { it.copy(tujuan = value.uppercase()) }
    fun updateNomor(value: String) = updateTx { it.copy(nomor = value) }
    fun updatePenerima(value: String) = updateTx { it.copy(penerima = value.uppercase()) }

    fun updateNominal(rawInput: String) {
        val clean = ReceiptFormatter.parseCleanNumber(rawInput)
        updateTx { current ->
            val updatedAdmin = if (current.adminAutoMode) {
                ReceiptFormatter.calculateAutoAdminFee(clean, _uiState.value.settings)
            } else {
                current.admin
            }
            current.copy(nominal = clean, admin = updatedAdmin)
        }
    }

    fun updateAdminManual(rawInput: String) {
        val clean = ReceiptFormatter.parseCleanNumber(rawInput)
        updateTx { it.copy(admin = clean, adminAutoMode = false) }
    }

    fun selectQuickAdmin(fee: Long) {
        updateTx { it.copy(admin = fee, adminAutoMode = false) }
    }

    fun toggleAdminMode() {
        val nextAuto = !_uiState.value.tx.adminAutoMode
        updateTx { current ->
            val newFee = if (nextAuto) {
                ReceiptFormatter.calculateAutoAdminFee(current.nominal, _uiState.value.settings)
            } else {
                current.admin
            }
            current.copy(adminAutoMode = nextAuto, admin = newFee)
        }
        emitMessage(
            if (nextAuto) "Biaya admin otomatis aktif" else "Biaya admin mode input manual"
        )
    }

    fun fillNowDateTime() {
        updateTx {
            it.copy(
                tanggal = ReceiptFormatter.currentDateFormatted(),
                waktu = ReceiptFormatter.currentTimeFormatted()
            )
        }
    }

    fun fillSampleTransaction() {
        val settings = _uiState.value.settings
        val sampleNominal = 999_236L
        updateTx {
            TransactionForm(
                mode = ServiceMode.BANK,
                tanggal = "12 Nov 2026",
                waktu = "15:17:50 WIB",
                referensi = "9527120261112151750379TRF1737080495",
                tujuan = "BCA",
                nomor = "758-096-8702",
                penerima = "SINERGI KOMUNIKASI SEJAH",
                nominal = sampleNominal,
                admin = ReceiptFormatter.calculateAutoAdminFee(sampleNominal, settings),
                adminAutoMode = true
            )
        }
        emitMessage("Data contoh BCA berhasil diisi")
    }

    fun resetForm() {
        _uiState.update {
            it.copy(
                selectedImageUri = null,
                ocrState = OcrUiState.Idle,
                lastOcrRawText = ""
            )
        }
        updateTx {
            TransactionForm()
        }
        emitMessage("Formulir dikosongkan")
    }

    fun clearSelectedImage() {
        _uiState.update {
            it.copy(
                selectedImageUri = null,
                ocrState = OcrUiState.Idle,
                lastOcrRawText = ""
            )
        }
    }

    fun handleSharedImage(sharedUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeTab = AppTab.INPUT_OCR,
                    selectedImageUri = sharedUri,
                    ocrState = OcrUiState.Loading,
                    lastOcrRawText = ""
                )
            }
            val stableUri = withContext(Dispatchers.IO) {
                copySharedImageToCache(sharedUri)
            }
            processOcrImage(stableUri)
        }
    }

    private fun copySharedImageToCache(uri: Uri): Uri {
        return try {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "shared").apply { mkdirs() }
            val outFile = File(dir, "shared_${System.currentTimeMillis()}.img")
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (outFile.exists() && outFile.length() > 0L) Uri.fromFile(outFile) else uri
        } catch (_: Exception) {
            uri
        }
    }

    fun setScanEngine(engine: ScanEngine) {
        val prev = _uiState.value.scanEngine
        _uiState.update { it.copy(scanEngine = engine) }
        val existingUri = _uiState.value.selectedImageUri
        if (existingUri != null && prev != engine) {
            processOcrImage(existingUri)
        }
    }

    fun rescanCurrentImage() {
        val existingUri = _uiState.value.selectedImageUri ?: return
        processOcrImage(existingUri)
    }

    fun processOcrImage(uri: Uri) {
        viewModelScope.launch {
            val engine = _uiState.value.scanEngine
            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    ocrState = OcrUiState.Loading,
                    lastOcrRawText = ""
                )
            }
            val result = when (engine) {
                ScanEngine.GEMINI_AI -> geminiService.scanReceiptImage(uri)
                ScanEngine.OFFLINE_MLKIT -> ocrService.scanReceiptImage(uri)
            }
            val engineLabel = if (engine == ScanEngine.GEMINI_AI) "AI Gemini" else "OCR Offline"
            result.onSuccess { parsed ->
                val settings = _uiState.value.settings
                updateTx { current ->
                    val newAdmin = if (current.adminAutoMode) {
                        ReceiptFormatter.calculateAutoAdminFee(parsed.nominal, settings)
                    } else {
                        current.admin
                    }
                    current.copy(
                        mode = parsed.tipe,
                        tanggal = parsed.tanggal.ifBlank { current.tanggal },
                        waktu = parsed.waktu.ifBlank { current.waktu },
                        referensi = parsed.referensi.ifBlank { current.referensi },
                        tujuan = parsed.tujuan.ifBlank { current.tujuan },
                        nomor = parsed.nomor.ifBlank { current.nomor },
                        penerima = parsed.penerima.ifBlank { current.penerima },
                        nominal = parsed.nominal,
                        admin = newAdmin
                    )
                }
                _uiState.update {
                    it.copy(
                        ocrState = OcrUiState.Success(
                            "Scan $engineLabel berhasil! Data transaksi (${parsed.tujuan.ifBlank { "-" }}) telah diisikan otomatis."
                        ),
                        lastOcrRawText = parsed.rawText
                    )
                }
                emitMessage("Bukti transaksi berhasil dipindai ($engineLabel)!")
            }.onFailure { err ->
                val partialRaw = (err as? OcrUnrecognizedException)?.rawText.orEmpty()
                _uiState.update {
                    it.copy(
                        ocrState = OcrUiState.Error(
                            (err.message ?: "Gagal memindai bukti transaksi.") +
                                " Anda dapat beralih metode scan atau mengisi data secara manual."
                        ),
                        lastOcrRawText = partialRaw
                    )
                }
            }
        }
    }

    fun saveSettings(
        storeName: String,
        storeAddress: String,
        footerLine1: String,
        footerLine2: String,
        tiers: List<AdminTier>,
        aboveMaxFee: Long
    ) {
        val cleanedTiers = tiers.filter { it.maxNominal > 0L }.sortedBy { it.maxNominal }
        if (cleanedTiers.isEmpty()) {
            emitMessage("Isi minimal 1 aturan maksimal nominal yang valid.")
            return
        }

        val updated = StoreSettings(
            storeName = ReceiptFormatter.sanitizeSingleLine(storeName).uppercase().ifEmpty { "AURORA CELL" },
            storeAddress = ReceiptFormatter.sanitizeSingleLine(storeAddress).ifEmpty { "Kota Semarang, Jawa Tengah" },
            footerLine1 = ReceiptFormatter.sanitizeSingleLine(footerLine1).ifEmpty { "Terima kasih telah bertransaksi" },
            footerLine2 = ReceiptFormatter.sanitizeSingleLine(footerLine2),
            tiers = cleanedTiers,
            aboveMaxFee = aboveMaxFee.coerceAtLeast(0L)
        )

        viewModelScope.launch {
            repository.saveSettings(updated)
            _uiState.update { state ->
                val adjustedTx = if (state.tx.adminAutoMode) {
                    state.tx.copy(admin = ReceiptFormatter.calculateAutoAdminFee(state.tx.nominal, updated))
                } else {
                    state.tx
                }
                state.copy(
                    settings = updated,
                    tx = adjustedTx
                )
            }
            emitMessage("Pengaturan disimpan (${updated.tiers.size} aturan admin aktif)")
        }
    }

    fun openBluetoothPrinterPicker() {
        val devices = ReceiptOutputManager.getPairedBluetoothDevices(getApplication())
        _uiState.update {
            it.copy(
                pairedPrinters = devices,
                showPrinterDialog = true
            )
        }
    }

    fun dismissBluetoothPrinterPicker() {
        _uiState.update { it.copy(showPrinterDialog = false) }
    }

    fun printToBluetoothDevice(device: BluetoothPrinterDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPrintingBluetooth = true, showPrinterDialog = false) }
            val state = _uiState.value
            val payload = ReceiptFormatter.buildEscPosPayload(state.tx, state.settings)
            val result = ReceiptOutputManager.printOverBluetoothSpp(
                context = getApplication(),
                deviceAddress = device.address,
                payload = payload
            )
            _uiState.update { it.copy(isPrintingBluetooth = false) }
            result.onSuccess {
                emitMessage("Struk berhasil dicetak ke ${device.name}!")
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal mencetak ke printer Bluetooth.")
            }
        }
    }

    fun copyReceiptText() {
        val state = _uiState.value
        val text = ReceiptFormatter.buildThermal32String(state.tx, state.settings)
        ReceiptOutputManager.copyToClipboard(getApplication(), text)
        emitMessage("Teks struk 58mm (32 karakter) berhasil disalin!")
    }

    fun copyOcrRawText() {
        val raw = _uiState.value.lastOcrRawText.trim()
        if (raw.isBlank()) return
        ReceiptOutputManager.copyToClipboard(
            context = getApplication(),
            text = raw,
            label = "Hasil OCR Aurora Cell"
        )
        emitMessage("Teks hasil OCR berhasil disalin!")
    }

    fun printViaRawBt() {
        val state = _uiState.value
        val ok = ReceiptOutputManager.printViaRawBt(getApplication(), state.tx, state.settings)
        if (ok) {
            emitMessage("Mengirim perintah cetak 58mm ke RawBT...")
        } else {
            emitMessage("Aplikasi RawBT tidak ditemukan. Pasang RawBT dari Play Store atau gunakan Cetak Bluetooth/Sistem.")
        }
    }

    fun sharePngReceipt() {
        viewModelScope.launch {
            val state = _uiState.value
            val result = ReceiptOutputManager.saveAndSharePng(
                context = getApplication(),
                tx = state.tx,
                settings = state.settings
            )
            result.onSuccess { uri ->
                ReceiptOutputManager.launchPngShareSheet(
                    context = getApplication(),
                    uri = uri,
                    storeName = state.settings.storeName
                )
                emitMessage("Gambar struk PNG disimpan & siap dibagikan!")
            }.onFailure {
                emitMessage("Gagal membuat gambar struk PNG.")
            }
        }
    }

    fun shareWhatsApp() {
        val state = _uiState.value
        val message = ReceiptFormatter.buildWhatsAppMessage(state.tx, state.settings)
        val ok = ReceiptOutputManager.shareToWhatsApp(getApplication(), message)
        if (!ok) {
            emitMessage("Tidak dapat membuka WhatsApp atau aplikasi berbagi.")
        }
    }

    private fun updateTx(transform: (TransactionForm) -> TransactionForm) {
        _uiState.update { current ->
            val updated = transform(current.tx)
            viewModelScope.launch {
                repository.saveDraft(updated)
            }
            current.copy(tx = updated)
        }
    }

    private fun emitMessage(msg: String) {
        _messages.tryEmit(msg)
    }
}
