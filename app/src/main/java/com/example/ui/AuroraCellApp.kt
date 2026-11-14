package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ServiceMode
import com.example.print.ReceiptOutputManager
import com.example.ui.screens.InputOcrScreen
import com.example.ui.screens.PreviewPrintScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.SlateDark900
import com.example.ui.theme.SlateDark950
import kotlinx.coroutines.launch

@Composable
fun AuroraCellApp(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Return to Tab 1 on Back press when viewing Tab 2 or Tab 3
    BackHandler(enabled = state.activeTab != AppTab.INPUT_OCR) {
        viewModel.selectTab(AppTab.INPUT_OCR)
    }

    // Zero-permission Android Photo Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processOcrImage(uri)
        }
    }

    // Camera capture via FileProvider URI
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            viewModel.processOcrImage(captured)
        }
    }

    // Android 12+ Bluetooth permission launcher
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it }
        if (granted) {
            viewModel.openBluetoothPrinterPicker()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Izin Bluetooth diperlukan untuk mencetak ke printer thermal 58mm."
                )
            }
        }
    }

    fun handleBluetoothPrintAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val scanGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (connectGranted && scanGranted) {
                viewModel.openBluetoothPrinterPicker()
            } else {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        } else {
            viewModel.openBluetoothPrinterPicker()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                AuroraTopHeader(
                    storeName = state.settings.storeName,
                    serviceMode = state.tx.mode
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AuroraNavItems().forEach { item ->
                            val selected = state.activeTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                modifier = Modifier.testTag(item.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AuroraNavItems().forEach { item ->
                            val selected = state.activeTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                modifier = Modifier.testTag(item.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)

                    when (state.activeTab) {
                        AppTab.INPUT_OCR -> {
                            InputOcrScreen(
                                state = state,
                                onSelectScanEngine = viewModel::setScanEngine,
                                onRescanImage = viewModel::rescanCurrentImage,
                                onPickGallery = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onTakeCamera = {
                                    val uri = ReceiptOutputManager.createCameraOutputUri(context)
                                    pendingCameraUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                onClearImage = viewModel::clearSelectedImage,
                                onCopyOcrRawText = viewModel::copyOcrRawText,
                                onFillSample = viewModel::fillSampleTransaction,
                                onResetForm = viewModel::resetForm,
                                onSelectMode = { mode -> viewModel.setServiceMode(mode) },
                                onFillNowDateTime = viewModel::fillNowDateTime,
                                onTanggalChange = viewModel::updateTanggal,
                                onWaktuChange = viewModel::updateWaktu,
                                onReferensiChange = viewModel::updateReferensi,
                                onTujuanChange = viewModel::updateTujuan,
                                onNomorChange = viewModel::updateNomor,
                                onPenerimaChange = viewModel::updatePenerima,
                                onNominalChange = viewModel::updateNominal,
                                onAdminChange = viewModel::updateAdminManual,
                                onToggleAdminMode = viewModel::toggleAdminMode,
                                onQuickAdminSelect = viewModel::selectQuickAdmin,
                                onGoToPreview = { viewModel.selectTab(AppTab.PREVIEW_PRINT) },
                                modifier = contentModifier
                            )
                        }
                        AppTab.PREVIEW_PRINT -> {
                            PreviewPrintScreen(
                                state = state,
                                onPrintBluetoothClick = ::handleBluetoothPrintAction,
                                onPrintRawBtClick = viewModel::printViaRawBt,
                                onPrintSystemClick = {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        ReceiptOutputManager.printViaSystemPrintService(
                                            activity = activity,
                                            tx = state.tx,
                                            settings = state.settings
                                        )
                                    }
                                },
                                onCopyTextClick = viewModel::copyReceiptText,
                                onExportPngClick = viewModel::sharePngReceipt,
                                onShareWhatsAppClick = viewModel::shareWhatsApp,
                                onDismissPrinterDialog = viewModel::dismissBluetoothPrinterPicker,
                                onSelectBluetoothDevice = viewModel::printToBluetoothDevice,
                                modifier = contentModifier
                            )
                        }
                        AppTab.SETTINGS -> {
                            SettingsScreen(
                                settings = state.settings,
                                onSaveSettings = viewModel::saveSettings,
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraTopHeader(
    storeName: String,
    serviceMode: ServiceMode
) {
    val headerBrush = Brush.linearGradient(
        colors = listOf(SlateDark950, IndigoDeep, SlateDark900)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBrush)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x426366F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Color(0xFFC7D2FE),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = storeName.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "OCR & Struk Thermal 58mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0x1FFFFFFF),
                border = BorderStroke(1.dp, Color(0x2EFFFFFF))
            ) {
                Text(
                    text = if (serviceMode == ServiceMode.EWALLET) "E-Wallet" else "Transfer Bank",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE0E7FF),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

private data class NavItemData(
    val tab: AppTab,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)

private fun AuroraNavItems(): List<NavItemData> = listOf(
    NavItemData(
        tab = AppTab.INPUT_OCR,
        label = "Input & OCR",
        selectedIcon = Icons.Filled.DocumentScanner,
        unselectedIcon = Icons.Outlined.DocumentScanner,
        testTag = "nav_tab_input_ocr"
    ),
    NavItemData(
        tab = AppTab.PREVIEW_PRINT,
        label = "Pratinjau",
        selectedIcon = Icons.Filled.Print,
        unselectedIcon = Icons.Outlined.Print,
        testTag = "nav_tab_preview_print"
    ),
    NavItemData(
        tab = AppTab.SETTINGS,
        label = "Pengaturan",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)
