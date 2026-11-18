package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.models.AcademySettings
import com.example.data.models.FeeChallan
import com.example.util.ChallanPdfGenerator
import com.example.util.QrCodeGenerator
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OnlinePaymentQrDialog(
    challan: FeeChallan,
    settings: AcademySettings = AcademySettings(),
    allowSubmitPayment: Boolean = true,
    onDismiss: () -> Unit,
    onSubmitOnlinePayment: (amount: Double, method: String, transactionId: String, remarks: String) -> Unit
) {
    val context = LocalContext.current
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    // 0 = EasyPaisa / Raast Official QR (03493895409 - MUHAMMAD RIZWAN) [Default], 1 = JazzCash (03707304820)
    var selectedWalletIndex by remember { mutableStateOf(0) }
    // Option to view either the full official EasyPaisa card or crisp standalone QR
    var showFullEasyPaisaCard by remember { mutableStateOf(true) }
    var transactionId by remember { mutableStateOf("") }
    var amountStr by remember {
        mutableStateOf(
            if (challan.remainingBalance > 0) String.format(Locale.US, "%.0f", challan.remainingBalance) else "0"
        )
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val isEasyPaisaSelected = selectedWalletIndex == 0
    val activeWalletName = if (isEasyPaisaSelected) "EasyPaisa" else "JazzCash"
    val activeAccountNumber = if (isEasyPaisaSelected) settings.easyPaisaNumber else settings.jazzCashNumber
    val activeAccentColor = if (isEasyPaisaSelected) Color(0xFF15803D) else Color(0xFFB91C1C)

    val officialEasyPaisaQrBitmap = remember(settings) {
        QrCodeGenerator.loadOfficialEasyPaisaQrBitmap(context, settings, sizePx = 380)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("online_payment_qr_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Online Fee Payment (QR)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Official EasyPaisa QR • JazzCash • Raast",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Highlighted Challan Payable Amount Banner with 1-tap Copy Amount
                if (challan.remainingBalance > 0.0) {
                    Surface(
                        color = Color(0xFFDFF3E8),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Amount to Pay (Challan #${challan.challanNumber})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1E3A2F)
                                )
                                Text(
                                    text = "Rs. ${formatter.format(challan.remainingBalance)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF0F6841)
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    val cleanAmt = String.format(Locale.US, "%.0f", challan.remainingBalance)
                                    copyToClipboard(context, "Challan Amount", cleanAmt)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Payable Amount", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Rs.", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Wallet Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isEasyPaisaSelected,
                        onClick = { selectedWalletIndex = 0; errorMsg = null },
                        label = {
                            Text(
                                "EasyPaisa QR (${settings.easyPaisaNumber})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_easypaisa_tab")
                    )
                    FilterChip(
                        selected = !isEasyPaisaSelected,
                        onClick = { selectedWalletIndex = 1; errorMsg = null },
                        label = {
                            Text(
                                "JazzCash (${settings.jazzCashNumber})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_jazzcash_tab")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = activeAccentColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isEasyPaisaSelected) {
                                    "EasyPaisa / Raast Official QR"
                                } else {
                                    "JazzCash Account & Raast QR"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = activeAccountNumber,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, "$activeWalletName Account", activeAccountNumber)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Account Number",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isEasyPaisaSelected) {
                            Text(
                                text = "Account Title: ${settings.onlineAccountTitle}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (settings.easyPaisaIban.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "IBAN: ${settings.easyPaisaIban}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, "Raast IBAN", settings.easyPaisaIban)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Raast IBAN",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = showFullEasyPaisaCard,
                                    onClick = { showFullEasyPaisaCard = true },
                                    label = { Text("Official EasyPaisa Card", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = !showFullEasyPaisaCard,
                                    onClick = { showFullEasyPaisaCard = false },
                                    label = { Text("Zoom QR Only", fontSize = 11.sp) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (showFullEasyPaisaCard) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                        .border(2.dp, activeAccentColor, RoundedCornerShape(14.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.easypaisa_card_03493895409),
                                        contentDescription = "Official EasyPaisa QR Card - MUHAMMAD RIZWAN (03493895409)",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .widthIn(max = 260.dp)
                                            .fillMaxWidth()
                                            .testTag("payment_qr_image")
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(215.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                        .border(2.dp, activeAccentColor, RoundedCornerShape(14.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = officialEasyPaisaQrBitmap.asImageBitmap(),
                                        contentDescription = "Official EasyPaisa QR Code - MUHAMMAD RIZWAN (03493895409)",
                                        filterQuality = FilterQuality.None,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("payment_qr_image")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Scan via EasyPaisa, JazzCash (Raast), or any Bank App — ${settings.onlineAccountTitle} (${settings.easyPaisaNumber}) will appear directly, then enter Rs. ${formatter.format(challan.remainingBalance)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // JazzCash Tab: Direct copyable number + Official Raast QR
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "JazzCash Account", settings.jazzCashNumber)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy JazzCash Number (${settings.jazzCashNumber})")
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Or scan this Official Raast QR from inside JazzCash (Scan QR) and enter Rs. ${formatter.format(challan.remainingBalance)}:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(2.dp, Color(0xFF15803D), RoundedCornerShape(12.dp))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = officialEasyPaisaQrBitmap.asImageBitmap(),
                                    contentDescription = "Official Raast QR Code",
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("payment_qr_image")
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Raast Recipient: ${settings.onlineAccountTitle} (${settings.easyPaisaNumber}) • Enter Rs. ${formatter.format(challan.remainingBalance)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val pdfFile = ChallanPdfGenerator.generateThreeCopyChallanPdf(context, challan, settings)
                        ChallanPdfGenerator.printOrOpenPdf(context, pdfFile)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print 3-Copy Challan with Official QR")
                }

                if (allowSubmitPayment && challan.remainingBalance > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Paid Online? Submit Transaction ID (TID)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it; errorMsg = null },
                            label = { Text("Paid PKR *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.42f)
                        )
                        OutlinedTextField(
                            value = transactionId,
                            onValueChange = { transactionId = it; errorMsg = null },
                            label = { Text("$activeWalletName TID *") },
                            placeholder = { Text("e.g. 08491234567") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.58f)
                                .testTag("online_tid_input")
                        )
                    }

                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0.0) {
                                errorMsg = "Enter a valid paid amount."
                                return@Button
                            }
                            if (transactionId.trim().length < 5) {
                                errorMsg = "Please enter the valid $activeWalletName / Bank Transaction ID (TID)."
                                return@Button
                            }
                            val methodLabel = "$activeWalletName ($activeAccountNumber)"
                            onSubmitOnlinePayment(
                                amt,
                                methodLabel,
                                transactionId.trim(),
                                "Online QR transfer via $methodLabel"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_online_tid_btn")
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit $activeWalletName Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied: $value", Toast.LENGTH_SHORT).show()
}
