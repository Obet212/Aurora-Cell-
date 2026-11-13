package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BluetoothPrinterDevice
import com.example.model.StoreSettings
import com.example.model.TransactionForm
import com.example.ui.MainUiState
import com.example.ui.components.AuroraSectionCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EwalletSky
import com.example.ui.theme.IndigoBorder
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoft
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SlateDark900
import com.example.ui.theme.SlateDark950
import com.example.util.ReceiptFormatter

@Composable
fun PreviewPrintScreen(
    state: MainUiState,
    onPrintBluetoothClick: () -> Unit,
    onPrintRawBtClick: () -> Unit,
    onPrintSystemClick: () -> Unit,
    onCopyTextClick: () -> Unit,
    onExportPngClick: () -> Unit,
    onShareWhatsAppClick: () -> Unit,
    onDismissPrinterDialog: () -> Unit,
    onSelectBluetoothDevice: (BluetoothPrinterDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AuroraSectionCard {
            SectionHeaderRow(
                badgeText = "3",
                title = "Pratinjau Kertas Thermal 58mm",
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = IndigoSoft,
                        border = BorderStroke(1.dp, IndigoBorder)
                    ) {
                        Text(
                            text = "32 Karakter",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndigoPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Receipt Paper Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE2E8F0))
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 22.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                ThermalReceiptPaper(
                    tx = state.tx,
                    settings = state.settings
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cetak Fisik Section
            Text(
                text = "CETAK FISIK KE PRINTER THERMAL 58MM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onPrintBluetoothClick,
                enabled = !state.isPrintingBluetooth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("print_bluetooth_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SlateDark900)
            ) {
                if (state.isPrintingBluetooth) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mengirim ESC/POS ke Printer...",
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cetak Bluetooth 58mm (ESC/POS)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPrintRawBtClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("print_rawbt_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "RawBT",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onPrintSystemClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("print_system_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EwalletSky),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Print Service",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bagikan Struk Digital Section
            Text(
                text = "SALIN & BAGIKAN STRUK DIGITAL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCopyTextClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("copy_text_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Salin Teks Struk 58mm",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportPngClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_png_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberAccent,
                        contentColor = SlateDark900
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Simpan / PNG",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Button(
                    onClick = onShareWhatsAppClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("share_whatsapp_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "WhatsApp",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    if (state.showPrinterDialog) {
        BluetoothPrinterPickerDialog(
            devices = state.pairedPrinters,
            onDismiss = onDismissPrinterDialog,
            onSelectDevice = onSelectBluetoothDevice
        )
    }
}

@Composable
private fun ThermalReceiptPaper(
    tx: TransactionForm,
    settings: StoreSettings
) {
    val tujuan = ReceiptFormatter.sanitizeSingleLine(tx.tujuan).uppercase().ifEmpty { "-" }
    val penerima = ReceiptFormatter.sanitizeSingleLine(tx.penerima).uppercase().ifEmpty { "-" }

    val dateTimeBlock = remember(tx.tanggal, tx.waktu) {
        (ReceiptFormatter.formatLeftRightLines("Tanggal", tx.tanggal.ifBlank { "-" }) +
            ReceiptFormatter.formatLeftRightLines("Waktu", tx.waktu.ifBlank { "-" }))
            .joinToString("\n")
    }

    val refBlock = remember(tx.referensi) {
        ReceiptFormatter.formatReferenceLines(tx.referensi).joinToString("\n")
    }

    val penerimaBlock = remember(tx.mode, tujuan, tx.nomor, penerima) {
        (ReceiptFormatter.formatLeftRightLines(ReceiptFormatter.tujuanLabel(tx.mode), tujuan) +
            ReceiptFormatter.formatLeftRightLines(ReceiptFormatter.nomorLabel(tx.mode), tx.nomor.ifBlank { "-" }) +
            ReceiptFormatter.formatLeftRightLines("Penerima", penerima))
            .joinToString("\n")
    }

    val nominalBlock = remember(tx.nominal, tx.admin) {
        (ReceiptFormatter.formatLeftRightLines("Nominal", ReceiptFormatter.formatRupiah(tx.nominal)) +
            ReceiptFormatter.formatLeftRightLines("Admin", ReceiptFormatter.formatRupiah(tx.admin)))
            .joinToString("\n")
    }

    val totalLine = remember(tx.total) {
        ReceiptFormatter.formatLeftRightLines("TOTAL", ReceiptFormatter.formatRupiah(tx.total))
            .joinToString("\n")
    }

    Column(
        modifier = Modifier
            .width(304.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(2.dp))
    ) {
        // Serrated Top Edge
        SerratedEdge(isTop = true)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = settings.storeName.uppercase(),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.5.sp,
                color = SlateDark950,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = settings.storeAddress,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = Color(0xFF334155),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))

            ReceiptMonoLine("================================")
            ReceiptMonoLine(dateTimeBlock)
            ReceiptMonoLine("--------------------------------")
            ReceiptMonoLine(ReceiptFormatter.centerPad("No. Referensi"))
            ReceiptMonoLine(refBlock)
            ReceiptMonoLine("--------------------------------")
            ReceiptMonoLine(ReceiptFormatter.centerPad("DATA PENERIMA"), bold = true)
            ReceiptMonoLine(penerimaBlock)
            ReceiptMonoLine("--------------------------------")
            ReceiptMonoLine(nominalBlock)
            ReceiptMonoLine("================================")
            ReceiptMonoLine(totalLine, bold = true)
            ReceiptMonoLine("================================")

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = settings.footerLine1,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = Color(0xFF334155),
                textAlign = TextAlign.Center
            )
            if (settings.footerLine2.isNotBlank()) {
                Text(
                    text = settings.footerLine2,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = Color(0xFF334155),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Serrated Bottom Edge
        SerratedEdge(isTop = false)
    }
}

@Composable
private fun ReceiptMonoLine(text: String, bold: Boolean = false) {
    Text(
        text = text,
        fontFamily = JetBrainsMonoFamily,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.5.sp,
        color = SlateDark950,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )
}

@Composable
private fun SerratedEdge(isTop: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val toothW = 12.dp.toPx()
        val h = size.height
        val path = Path()
        var x = 0f
        while (x < size.width) {
            if (isTop) {
                path.moveTo(x, h)
                path.lineTo(x + toothW / 2f, 0f)
                path.lineTo(x + toothW, h)
            } else {
                path.moveTo(x, 0f)
                path.lineTo(x + toothW / 2f, h)
                path.lineTo(x + toothW, 0f)
            }
            path.close()
            x += toothW
        }
        drawPath(path = path, color = Color.White)
    }
}

@Composable
private fun BluetoothPrinterPickerDialog(
    devices: List<BluetoothPrinterDevice>,
    onDismiss: () -> Unit,
    onSelectDevice: (BluetoothPrinterDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pilih Printer Bluetooth 58mm",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            if (devices.isEmpty()) {
                Text(
                    text = "Tidak ada printer Bluetooth yang terpasang (paired). Buka Pengaturan Bluetooth HP Anda, pasangkan printer thermal 58mm terlebih dahulu, atau gunakan tombol RawBT / Print Service.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ketuk perangkat printer thermal yang sudah dipasangkan:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    devices.forEach { dev ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectDevice(dev) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = dev.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = dev.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
