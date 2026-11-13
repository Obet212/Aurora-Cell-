package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.OcrUiState
import com.example.model.ServiceMode
import com.example.ui.MainUiState
import com.example.ui.components.AuroraSectionCard
import com.example.ui.components.LabeledInputField
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.ThousandSeparatorTransformation
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerSoft
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EwalletSky
import com.example.ui.theme.IndigoBorder
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoft
import com.example.ui.theme.SlateDark900
import com.example.util.ReceiptFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputOcrScreen(
    state: MainUiState,
    onPickGallery: () -> Unit,
    onTakeCamera: () -> Unit,
    onClearImage: () -> Unit,
    onFillSample: () -> Unit,
    onResetForm: () -> Unit,
    onSelectMode: (ServiceMode) -> Unit,
    onFillNowDateTime: () -> Unit,
    onTanggalChange: (String) -> Unit,
    onWaktuChange: (String) -> Unit,
    onReferensiChange: (String) -> Unit,
    onTujuanChange: (String) -> Unit,
    onNomorChange: (String) -> Unit,
    onPenerimaChange: (String) -> Unit,
    onNominalChange: (String) -> Unit,
    onAdminChange: (String) -> Unit,
    onToggleAdminMode: () -> Unit,
    onQuickAdminSelect: (Long) -> Unit,
    onGoToPreview: () -> Unit,
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thousandTransform = remember { ThousandSeparatorTransformation() }
    val tx = state.tx

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Scan OCR Bukti Transaksi
        AuroraSectionCard {
            SectionHeaderRow(
                badgeText = "1",
                title = "Scan OCR Bukti Transaksi",
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = IndigoSoft,
                        border = BorderStroke(1.dp, IndigoBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Gemini Vision AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndigoPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pilih screenshot bukti transfer M-Banking (BCA, BRI, Mandiri, BNI, SeaBank) atau E-Wallet (DANA, OVO, GoPay, ShopeePay) untuk mengisi rincian struk otomatis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!state.hasConfiguredApiKey) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onGoToSettings)
                        .testTag("api_key_notice_banner"),
                    color = AmberSoft,
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Text(
                        text = "API Key Gemini belum diatur. Ketuk di sini untuk memasukkan API Key di Pengaturan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dashed OCR Box
            val dashColor = IndigoBorder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndigoSoft.copy(alpha = 0.45f))
                    .drawBehind {
                        drawRoundRect(
                            color = dashColor,
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                            )
                        )
                    }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(visible = state.selectedImageUri != null) {
                        SelectedImagePreview(
                            uri = state.selectedImageUri,
                            onClear = onClearImage
                        )
                    }

                    AnimatedVisibility(visible = state.ocrState is OcrUiState.Loading) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IndigoPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.2.dp
                                )
                                Text(
                                    text = "Memindai bukti transaksi...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    when (val ocr = state.ocrState) {
                        is OcrUiState.Success -> {
                            StatusBanner(
                                text = ocr.message,
                                containerColor = EmeraldSoft,
                                borderColor = Color(0xFFA7F3D0),
                                textColor = Color(0xFF065F46)
                            )
                        }
                        is OcrUiState.Error -> {
                            StatusBanner(
                                text = ocr.message,
                                containerColor = DangerSoft,
                                borderColor = Color(0xFFFECACA),
                                textColor = Color(0xFF991B1B)
                            )
                        }
                        else -> Unit
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPickGallery,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("pick_gallery_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pilih Galeri",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = onTakeCamera,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("take_camera_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateDark900),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kamera",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Card 2: Detail Transaksi Struk
        AuroraSectionCard {
            SectionHeaderRow(
                badgeText = "2",
                title = "Detail Transaksi Struk",
                trailing = {
                    OutlinedButton(
                        onClick = onFillSample,
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("fill_sample_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Contoh", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onResetForm,
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("reset_form_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Reset", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Layanan Segmented Toggle
            Text(
                text = "MODE LAYANAN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeSegmentButton(
                    selected = tx.mode == ServiceMode.BANK,
                    label = "Transfer Bank",
                    icon = Icons.Filled.AccountBalance,
                    activeColor = IndigoPrimary,
                    onClick = { onSelectMode(ServiceMode.BANK) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_bank_button")
                )
                ModeSegmentButton(
                    selected = tx.mode == ServiceMode.EWALLET,
                    label = "E-Wallet",
                    icon = Icons.Filled.AccountBalanceWallet,
                    activeColor = EwalletSky,
                    onClick = { onSelectMode(ServiceMode.EWALLET) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_ewallet_button")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tanggal & Waktu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LabeledInputField(
                    label = "Tanggal",
                    value = tx.tanggal,
                    onValueChange = onTanggalChange,
                    placeholder = "",
                    modifier = Modifier.weight(1f),
                    trailingHeader = {
                        TextButton(
                            onClick = onFillNowDateTime,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Sekarang",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
                LabeledInputField(
                    label = "Waktu",
                    value = tx.waktu,
                    onValueChange = onWaktuChange,
                    placeholder = "",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // No. Referensi
            LabeledInputField(
                label = "No. Referensi",
                value = tx.referensi,
                onValueChange = onReferensiChange,
                placeholder = "",
                isMonospace = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bank Tujuan / E-Wallet + Chips
            val tujuanLabel = ReceiptFormatter.tujuanLabel(tx.mode)
            val chipItems = if (tx.mode == ServiceMode.EWALLET) {
                ReceiptFormatter.EWALLET_CHIPS
            } else {
                ReceiptFormatter.BANK_CHIPS
            }

            LabeledInputField(
                label = tujuanLabel,
                value = tx.tujuan,
                onValueChange = onTujuanChange,
                placeholder = ""
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chipItems.forEach { item ->
                    QuickChip(
                        text = item,
                        selected = tx.tujuan.equals(item, ignoreCase = true),
                        onClick = { onTujuanChange(item.uppercase()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rekening/Nomor & Penerima
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LabeledInputField(
                    label = ReceiptFormatter.nomorLabel(tx.mode),
                    value = tx.nomor,
                    onValueChange = onNomorChange,
                    placeholder = "",
                    modifier = Modifier.weight(1f)
                )
                LabeledInputField(
                    label = "Nama Penerima",
                    value = tx.penerima,
                    onValueChange = onPenerimaChange,
                    placeholder = "",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nominal & Biaya Admin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LabeledInputField(
                    label = "Nominal (Rp)",
                    value = if (tx.nominal > 0L) tx.nominal.toString() else "",
                    onValueChange = onNominalChange,
                    placeholder = "0",
                    isBoldLarge = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = thousandTransform,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nominal_input")
                )

                LabeledInputField(
                    label = "Biaya Admin",
                    value = if (tx.admin > 0L || !tx.adminAutoMode) tx.admin.toString() else "",
                    onValueChange = onAdminChange,
                    placeholder = "0",
                    isBoldLarge = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = thousandTransform,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_input"),
                    trailingHeader = {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onToggleAdminMode)
                                .testTag("toggle_admin_mode_button"),
                            color = if (tx.adminAutoMode) EmeraldSoft else AmberSoft,
                            border = BorderStroke(
                                1.dp,
                                if (tx.adminAutoMode) Color(0xFFA7F3D0) else Color(0xFFFDE68A)
                            )
                        ) {
                            Text(
                                text = if (tx.adminAutoMode) "Otomatis" else "Manual",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (tx.adminAutoMode) Color(0xFF065F46) else Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "PILIH CEPAT BIAYA ADMIN (MANUAL):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReceiptFormatter.QUICK_ADMIN_FEES.forEach { fee ->
                    QuickChip(
                        text = ReceiptFormatter.formatRupiah(fee),
                        selected = !tx.adminAutoMode && tx.admin == fee,
                        onClick = { onQuickAdminSelect(fee) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dark Slate Summary Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SlateDark900
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Nominal Transfer",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = ReceiptFormatter.formatRupiah(tx.nominal),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Biaya Admin",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = ReceiptFormatter.formatRupiah(tx.admin),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = Color(0xFF334155)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TOTAL BAYAR",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = ReceiptFormatter.formatRupiah(tx.total),
                            style = MaterialTheme.typography.headlineSmall,
                            color = EmeraldBright,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onGoToPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("goto_preview_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text(
                    text = "Lihat Pratinjau & Cetak Struk",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun SelectedImagePreview(
    uri: Uri?,
    onClear: () -> Unit
) {
    if (uri == null) return
    Box(modifier = Modifier.padding(top = 4.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = "Pratinjau Bukti Transfer",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .heightIn(max = 180.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .background(Color.White)
        )
        IconButton(
            onClick = onClear,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .minimumInteractiveComponentSize()
                .size(32.dp)
                .clip(CircleShape)
                .background(SlateDark900)
                .testTag("clear_ocr_image_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Hapus Foto",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StatusBanner(
    text: String,
    containerColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ModeSegmentButton(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) activeColor else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) IndigoSoft else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) IndigoPrimary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) IndigoPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}
