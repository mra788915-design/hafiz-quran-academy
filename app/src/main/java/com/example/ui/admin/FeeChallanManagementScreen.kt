package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.*
import com.example.ui.AcademyViewModel
import com.example.ui.components.ChallanStatusBadge
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.OnlinePaymentQrDialog
import com.example.ui.components.StatusBadge
import com.example.util.ChallanPdfGenerator
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeChallanManagementScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val challans by viewModel.challans.collectAsState(initial = emptyList())
    val payments by viewModel.payments.collectAsState(initial = emptyList())
    val students by viewModel.activeStudents.collectAsState(initial = emptyList())
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val settings = viewModel.repository.settings

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ChallanStatus?>(null) }

    var showBulkDialog by remember { mutableStateOf(false) }
    var showSingleDialog by remember { mutableStateOf(false) }
    var challanToPay by remember { mutableStateOf<FeeChallan?>(null) }
    var challanForQrModal by remember { mutableStateOf<FeeChallan?>(null) }
    var editingChallan by remember { mutableStateOf<FeeChallan?>(null) }
    var deletingChallan by remember { mutableStateOf<FeeChallan?>(null) }
    var editingPayment by remember { mutableStateOf<FeePayment?>(null) }
    var deletingPayment by remember { mutableStateOf<FeePayment?>(null) }

    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    val filteredChallans = remember(challans, searchQuery, statusFilter) {
        challans.filter { ch ->
            val matchQuery = ch.studentName.contains(searchQuery, ignoreCase = true) ||
                    ch.challanNumber.contains(searchQuery, ignoreCase = true) ||
                    ch.admissionNumber.contains(searchQuery, ignoreCase = true)
            val matchStatus = statusFilter == null || ch.status == statusFilter
            matchQuery && matchStatus
        }
    }

    val filteredPayments = remember(payments, searchQuery) {
        payments.filter { p ->
            p.studentName.contains(searchQuery, ignoreCase = true) ||
                    p.challanNumber.contains(searchQuery, ignoreCase = true) ||
                    p.receiptNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalBilled = remember(challans) { challans.sumOf { it.totalPayable } }
    val totalCollected = remember(challans) { challans.sumOf { it.amountPaid } }
    val totalOutstanding = remember(challans) { challans.sumOf { it.remainingBalance } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedTab == 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallFloatingActionButton(
                        onClick = { showSingleDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Individual Challan")
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showBulkDialog = true },
                        icon = { Icon(Icons.Default.AutoMode, contentDescription = null) },
                        text = { Text("Generate Challans") },
                        modifier = Modifier.testTag("bulk_challan_fab")
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Financial Summary Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Billed", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "Rs. ${formatter.format(totalBilled)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Column {
                            Text("Collected", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "Rs. ${formatter.format(totalCollected)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            )
                        }
                        Column {
                            Text("Outstanding", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "Rs. ${formatter.format(totalOutstanding)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Online QR Accounts • JazzCash: ${settings.jazzCashNumber} | EasyPaisa: ${settings.easyPaisaNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Fee Challans (${challans.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Payment Receipts (${payments.size})", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("challan_search_input"),
                placeholder = {
                    Text(
                        if (selectedTab == 0) "Search challan #, student name, admission #..."
                        else "Search receipt #, TID, student name..."
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("All (${challans.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == ChallanStatus.UNPAID,
                            onClick = { statusFilter = if (statusFilter == ChallanStatus.UNPAID) null else ChallanStatus.UNPAID },
                            label = { Text("Unpaid") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == ChallanStatus.PARTIALLY_PAID,
                            onClick = { statusFilter = if (statusFilter == ChallanStatus.PARTIALLY_PAID) null else ChallanStatus.PARTIALLY_PAID },
                            label = { Text("Partial") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == ChallanStatus.PAID,
                            onClick = { statusFilter = if (statusFilter == ChallanStatus.PAID) null else ChallanStatus.PAID },
                            label = { Text("Paid") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredChallans.isEmpty()) {
                    EmptyPlaceholderCard(
                        title = "No fee challans found",
                        subtitle = "Tap 'Generate Challans' to issue monthly billing with JazzCash & EasyPaisa QR codes.",
                        icon = Icons.Default.ReceiptLong
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredChallans, key = { it.id }) { challan ->
                            ChallanCardItem(
                                challan = challan,
                                onQrClick = { challanForQrModal = challan },
                                onPayClick = { challanToPay = challan },
                                onPdfClick = {
                                    val file = ChallanPdfGenerator.generateThreeCopyChallanPdf(
                                        context,
                                        challan,
                                        settings
                                    )
                                    ChallanPdfGenerator.printOrOpenPdf(context, file)
                                },
                                onEditClick = { editingChallan = challan },
                                onDeleteClick = { deletingChallan = challan }
                            )
                        }
                    }
                }
            } else {
                if (filteredPayments.isEmpty()) {
                    EmptyPlaceholderCard(
                        title = "No payment receipts found",
                        subtitle = "Recorded fee payments and online QR payments appear here.",
                        icon = Icons.Default.Payments
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredPayments, key = { it.id }) { payment ->
                            PaymentReceiptCardItem(
                                payment = payment,
                                onEditClick = { editingPayment = payment },
                                onDeleteClick = { deletingPayment = payment }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBulkDialog) {
        BulkChallanDialog(
            classes = classes,
            onDismiss = { showBulkDialog = false },
            onGenerate = { classId, month, year, dueDate ->
                viewModel.generateBulkChallans(classId, month, year, dueDate) {
                    showBulkDialog = false
                }
            }
        )
    }

    if (showSingleDialog) {
        SingleChallanDialog(
            students = students,
            onDismiss = { showSingleDialog = false },
            onGenerate = { studentId, month, year, dueDate, additional, discount ->
                viewModel.generateChallan(studentId, month, year, dueDate, additional, discount) {
                    showSingleDialog = false
                }
            }
        )
    }

    challanToPay?.let { ch ->
        RecordPaymentDialog(
            challan = ch,
            settings = settings,
            onDismiss = { challanToPay = null },
            onConfirm = { amount, method, tid, remarks ->
                viewModel.recordPayment(ch.id, amount, method, remarks, tid) {
                    challanToPay = null
                }
            }
        )
    }

    challanForQrModal?.let { ch ->
        OnlinePaymentQrDialog(
            challan = ch,
            settings = settings,
            allowSubmitPayment = true,
            onDismiss = { challanForQrModal = null },
            onSubmitOnlinePayment = { amount, method, tid, remarks ->
                viewModel.recordPayment(ch.id, amount, method, remarks, tid) {
                    challanForQrModal = null
                }
            }
        )
    }

    editingChallan?.let { ch ->
        EditChallanDialog(
            challan = ch,
            onDismiss = { editingChallan = null },
            onSave = { updated ->
                viewModel.updateChallanDetails(updated) {
                    editingChallan = null
                }
            }
        )
    }

    deletingChallan?.let { ch ->
        AlertDialog(
            onDismissRequest = { deletingChallan = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Fee Challan?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete Challan #${ch.challanNumber} for ${ch.studentName} (${ch.billingMonth} ${ch.billingYear}) and any linked payment receipts?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChallan(ch.id)
                        deletingChallan = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_challan_btn")
                ) {
                    Text("Delete Challan")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingChallan = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingPayment?.let { pay ->
        EditPaymentDialog(
            payment = pay,
            settings = settings,
            onDismiss = { editingPayment = null },
            onSave = { updated ->
                viewModel.updatePaymentDetails(updated) {
                    editingPayment = null
                }
            }
        )
    }

    deletingPayment?.let { pay ->
        AlertDialog(
            onDismissRequest = { deletingPayment = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Payment Receipt?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove Receipt ${pay.receiptNumber} (Rs. ${pay.amount.toInt()}) for ${pay.studentName}? The linked Challan #${pay.challanNumber} balance will automatically recalculate."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(pay.id)
                        deletingPayment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_payment_btn")
                ) {
                    Text("Remove Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPayment = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChallanCardItem(
    challan: FeeChallan,
    onQrClick: () -> Unit,
    onPayClick: () -> Unit,
    onPdfClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = challan.studentName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Challan #${challan.challanNumber} • ${challan.billingMonth} ${challan.billingYear}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChallanStatusBadge(challan.status)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Due Date: ${challan.dueDate}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                    if (challan.fsAssistanceAmount > 0) {
                        Text(
                            "FS Sabilillah: -Rs. ${formatter.format(challan.fsAssistanceAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (challan.transactionRef.isNotBlank()) {
                        Text(
                            "Ref: ${challan.transactionRef} (${challan.paymentMethod})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF15803D)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Payable: Rs. ${formatter.format(challan.totalPayable)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (challan.remainingBalance > 0) {
                        Text(
                            text = "Due: Rs. ${formatter.format(challan.remainingBalance)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        )
                    } else {
                        Text(
                            text = "Fully Paid",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.testTag("edit_challan_${challan.id}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Challan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_challan_${challan.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Challan",
                            tint = Color(0xFFDC2626)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onQrClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("QR", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onPdfClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 11.sp)
                    }

                    if (challan.remainingBalance > 0) {
                        Button(
                            onClick = onPayClick,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pay", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentReceiptCardItem(
    payment: FeePayment,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        payment.studentName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Receipt / TID: ${payment.receiptNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StatusBadge(
                    "Rs. ${formatter.format(payment.amount)}",
                    Color(0xFFD1E7DD),
                    Color(0xFF0F5132)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text("Challan #${payment.challanNumber} • ${payment.paymentDate}", style = MaterialTheme.typography.bodySmall)
            Text("Method: ${payment.paymentMethod} • By: ${payment.receivedBy}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (payment.remarks.isNotBlank()) {
                Text("Remarks: ${payment.remarks}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("edit_payment_${payment.id}"),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Payment", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("delete_payment_${payment.id}"),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Payment", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", fontSize = 12.sp)
                }
            }
        }
    }
}
