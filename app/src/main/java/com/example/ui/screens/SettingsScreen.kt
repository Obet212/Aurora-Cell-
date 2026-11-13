package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.AdminTier
import com.example.model.StoreSettings
import com.example.ui.components.AuroraSectionCard
import com.example.ui.components.LabeledInputField
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.ThousandSeparatorTransformation
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerSoft
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.IndigoBorder
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoft
import com.example.util.ReceiptFormatter

private data class EditableTierRow(
    val id: Long,
    var maxDigits: String,
    var feeDigits: String
)

@Composable
fun SettingsScreen(
    settings: StoreSettings,
    onSaveSettings: (
        storeName: String,
        storeAddress: String,
        footerLine1: String,
        footerLine2: String,
        tiers: List<AdminTier>,
        aboveMaxFee: Long
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var storeName by remember { mutableStateOf(settings.storeName) }
    var storeAddress by remember { mutableStateOf(settings.storeAddress) }
    var footer1 by remember { mutableStateOf(settings.footerLine1) }
    var footer2 by remember { mutableStateOf(settings.footerLine2) }
    var aboveMaxDigits by remember { mutableStateOf(settings.aboveMaxFee.toString()) }
    val tierRows = remember { mutableStateListOf<EditableTierRow>() }

    LaunchedEffect(settings) {
        storeName = settings.storeName
        storeAddress = settings.storeAddress
        footer1 = settings.footerLine1
        footer2 = settings.footerLine2
        aboveMaxDigits = settings.aboveMaxFee.toString()
        tierRows.clear()
        settings.tiers.forEach { t ->
            tierRows.add(
                EditableTierRow(
                    id = t.id,
                    maxDigits = t.maxNominal.toString(),
                    feeDigits = t.fee.toString()
                )
            )
        }
    }

    val thousandTransform = remember { ThousandSeparatorTransformation() }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card A: Identitas Toko & Footer Struk
        AuroraSectionCard {
            SectionHeaderRow(
                badgeText = "A",
                title = "Identitas Toko & Footer Struk"
            )

            Spacer(modifier = Modifier.height(12.dp))

            LabeledInputField(
                label = "Nama Toko (Tebal, Besar, Rata Tengah)",
                value = storeName,
                onValueChange = { storeName = it.uppercase() },
                placeholder = "AURORA CELL",
                isBoldLarge = true,
                modifier = Modifier.testTag("settings_store_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            LabeledInputField(
                label = "Alamat Toko (Kecil, Rata Tengah)",
                value = storeAddress,
                onValueChange = { storeAddress = it },
                placeholder = "Kota Semarang, Jawa Tengah"
            )

            Spacer(modifier = Modifier.height(12.dp))

            LabeledInputField(
                label = "Footer Baris 1 (Rata Tengah, Kecil)",
                value = footer1,
                onValueChange = { footer1 = it },
                placeholder = "Terima kasih telah bertransaksi"
            )

            Spacer(modifier = Modifier.height(12.dp))

            LabeledInputField(
                label = "Footer Baris 2 (Rata Tengah, Kecil)",
                value = footer2,
                onValueChange = { footer2 = it },
                placeholder = "Simpan struk sebagai bukti sah"
            )
        }

        // Card C: Aturan Admin Otomatis
        AuroraSectionCard {
            SectionHeaderRow(
                badgeText = "B",
                title = "Aturan Admin Otomatis",
                trailing = {
                    Button(
                        onClick = {
                            val lastMax = tierRows.lastOrNull()?.maxDigits?.toLongOrNull() ?: 500_000L
                            val lastFee = tierRows.lastOrNull()?.feeDigits?.toLongOrNull() ?: 5_000L
                            tierRows.add(
                                EditableTierRow(
                                    id = System.nanoTime(),
                                    maxDigits = (lastMax + 1_000_000L).toString(),
                                    feeDigits = (lastFee + 3_000L).toString()
                                )
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("add_tier_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Tambah", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tambahkan aturan tingkatan nominal sebanyak yang diperlukan. Saat disimpan, seluruh aturan otomatis diurutkan dari nominal terkecil ke terbesar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            tierRows.forEachIndexed { index, row ->
                var maxStr by remember(row.id) { mutableStateOf(row.maxDigits) }
                var feeStr by remember(row.id) { mutableStateOf(row.feeDigits) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Aturan #${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = IndigoPrimary
                            )
                            TextButton(
                                onClick = {
                                    if (tierRows.size > 1) {
                                        tierRows.removeAt(index)
                                    }
                                },
                                enabled = tierRows.size > 1,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = DangerSoft,
                                    contentColor = DangerRed
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Hapus Aturan",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Hapus", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = maxStr,
                                onValueChange = { input ->
                                    val clean = input.filter { it.isDigit() }
                                    maxStr = clean
                                    row.maxDigits = clean
                                },
                                label = { Text("Maks Nominal (Rp)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = thousandTransform,
                                textStyle = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = feeStr,
                                onValueChange = { input ->
                                    val clean = input.filter { it.isDigit() }
                                    feeStr = clean
                                    row.feeDigits = clean
                                },
                                label = { Text("Biaya Admin (Rp)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = thousandTransform,
                                textStyle = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Tarif di Atas Batas Tertinggi
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = IndigoSoft,
                border = BorderStroke(1.dp, IndigoBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "TARIF DI ATAS BATAS TERTINGGI (RP)",
                        style = MaterialTheme.typography.labelMedium,
                        color = IndigoPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aboveMaxDigits,
                        onValueChange = { aboveMaxDigits = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = thousandTransform,
                        textStyle = TextStyle(
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.End
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val mappedTiers = tierRows.mapNotNull { r ->
                        val max = ReceiptFormatter.parseCleanNumber(r.maxDigits)
                        val fee = ReceiptFormatter.parseCleanNumber(r.feeDigits)
                        if (max > 0L) AdminTier(id = r.id, maxNominal = max, fee = fee) else null
                    }
                    onSaveSettings(
                        storeName,
                        storeAddress,
                        footer1,
                        footer2,
                        mappedTiers,
                        ReceiptFormatter.parseCleanNumber(aboveMaxDigits)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Simpan Semua Pengaturan",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
