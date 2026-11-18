package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkChallanDialog(
    classes: List<AcademyClass>,
    onDismiss: () -> Unit,
    onGenerate: (classId: String?, month: String, year: Int, dueDate: String) -> Unit
) {
    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var selectedMonth by remember { mutableStateOf(SimpleDateFormat("MMMM", Locale.US).format(Date())) }
    var yearStr by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var dueDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-10", Locale.US).format(Date())) }

    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    var monthExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Bulk Generate Challans", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Issues monthly fee challans embedded with JazzCash (03707304820) & EasyPaisa (03493895409) QR codes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Select Scope:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedClassId == null,
                            onClick = { selectedClassId = null },
                            label = { Text("All Classes") }
                        )
                    }
                    items(classes) { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = { selectedClassId = cls.id },
                            label = { Text(cls.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = monthExpanded,
                    onExpandedChange = { monthExpanded = !monthExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Billing Month") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false }
                    ) {
                        months.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { selectedMonth = m; monthExpanded = false })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { yearStr = it },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val yearVal = yearStr.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                            onGenerate(selectedClassId, selectedMonth, yearVal, dueDate)
                        }
                    ) {
                        Text("Generate Now")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleChallanDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onGenerate: (studentId: String, month: String, year: Int, dueDate: String, additional: Double, discount: Double) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var selectedMonth by remember { mutableStateOf(SimpleDateFormat("MMMM", Locale.US).format(Date())) }
    var yearStr by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var dueDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-10", Locale.US).format(Date())) }
    var additionalChargesStr by remember { mutableStateOf("0") }
    var manualDiscountStr by remember { mutableStateOf("0") }

    var studentExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Generate Individual Challan", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

                Spacer(modifier = Modifier.height(14.dp))

                ExposedDropdownMenuBox(
                    expanded = studentExpanded,
                    onExpandedChange = { studentExpanded = !studentExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedStudent?.let { "${it.fullName} (${it.admissionNumber})" } ?: "Select Student",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Student") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = studentExpanded,
                        onDismissRequest = { studentExpanded = false }
                    ) {
                        students.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.fullName} (${s.admissionNumber})") },
                                onClick = { selectedStudent = s; studentExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = { selectedMonth = it },
                        label = { Text("Month") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = additionalChargesStr,
                        onValueChange = { additionalChargesStr = it },
                        label = { Text("Add. Charges") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualDiscountStr,
                        onValueChange = { manualDiscountStr = it },
                        label = { Text("Discount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val stud = selectedStudent ?: return@Button
                            val yearVal = yearStr.toIntOrNull() ?: 2026
                            val addVal = additionalChargesStr.toDoubleOrNull() ?: 0.0
                            val discVal = manualDiscountStr.toDoubleOrNull() ?: 0.0
                            onGenerate(stud.id, selectedMonth, yearVal, dueDate, addVal, discVal)
                        },
                        enabled = selectedStudent != null
                    ) {
                        Text("Issue Challan")
                    }
                }
            }
        }
    }
}

@Composable
fun RecordPaymentDialog(
    challan: FeeChallan,
    settings: AcademySettings,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String, tid: String, remarks: String) -> Unit
) {
    var amountStr by remember { mutableStateOf(challan.remainingBalance.toInt().toString()) }
    val methods = listOf(
        "JazzCash (${settings.jazzCashNumber})",
        "EasyPaisa (${settings.easyPaisaNumber})",
        "Cash"
    )
    var selectedMethod by remember { mutableStateOf(methods.first()) }
    var transactionId by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Record Fee Payment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Student: ${challan.studentName} • Challan #${challan.challanNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Payment Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Payment Account / Method:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    methods.forEach { m ->
                        FilterChip(
                            selected = selectedMethod == m,
                            onClick = { selectedMethod = m },
                            label = { Text(m, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = transactionId,
                    onValueChange = { transactionId = it },
                    label = { Text("JazzCash / EasyPaisa Transaction ID (TID)") },
                    placeholder = { Text("Optional for Cash, required for Online") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Receipt / Verification Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt > 0) onConfirm(amt, selectedMethod, transactionId, remarks)
                        },
                        enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
                    ) {
                        Text("Confirm Payment")
                    }
                }
            }
        }
    }
}

@Composable
fun EditChallanDialog(
    challan: FeeChallan,
    onDismiss: () -> Unit,
    onSave: (FeeChallan) -> Unit
) {
    var billingMonth by remember { mutableStateOf(challan.billingMonth) }
    var billingYearStr by remember { mutableStateOf(challan.billingYear.toString()) }
    var dueDate by remember { mutableStateOf(challan.dueDate) }
    var tuitionStr by remember { mutableStateOf(challan.monthlyTuitionFee.toInt().toString()) }
    var additionalStr by remember { mutableStateOf(challan.additionalCharges.toInt().toString()) }
    var discountStr by remember { mutableStateOf(challan.discountAmount.toInt().toString()) }
    var fsStr by remember { mutableStateOf(challan.fsAssistanceAmount.toInt().toString()) }
    var prevBalanceStr by remember { mutableStateOf(challan.previousBalance.toInt().toString()) }
    var status by remember { mutableStateOf(challan.status) }
    var remarks by remember { mutableStateOf(challan.remarks) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Edit Fee Challan", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${challan.studentName} • #${challan.challanNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billingMonth,
                        onValueChange = { billingMonth = it },
                        label = { Text("Month") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = billingYearStr,
                        onValueChange = { billingYearStr = it },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tuitionStr,
                        onValueChange = { tuitionStr = it },
                        label = { Text("Tuition (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = additionalStr,
                        onValueChange = { additionalStr = it },
                        label = { Text("Add. Charges") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text("Discount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fsStr,
                        onValueChange = { fsStr = it },
                        label = { Text("FS Waiver") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = prevBalanceStr,
                    onValueChange = { prevBalanceStr = it },
                    label = { Text("Previous Arrears (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Status Override (Optional):", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ChallanStatus.values().toList()) { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.name, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                challan.copy(
                                    billingMonth = billingMonth.ifBlank { challan.billingMonth },
                                    billingYear = (billingYearStr.toIntOrNull() ?: challan.billingYear).coerceIn(2020, 2100),
                                    dueDate = dueDate.ifBlank { challan.dueDate },
                                    monthlyTuitionFee = (tuitionStr.toDoubleOrNull() ?: challan.monthlyTuitionFee).coerceAtLeast(0.0),
                                    additionalCharges = (additionalStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                    discountAmount = (discountStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                    fsAssistanceAmount = (fsStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                    previousBalance = (prevBalanceStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                    status = status,
                                    remarks = remarks
                                )
                            )
                        }
                    ) {
                        Text("Save Challan")
                    }
                }
            }
        }
    }
}

@Composable
fun EditPaymentDialog(
    payment: FeePayment,
    settings: AcademySettings,
    onDismiss: () -> Unit,
    onSave: (FeePayment) -> Unit
) {
    var amountStr by remember { mutableStateOf(payment.amount.toInt().toString()) }
    var paymentMethod by remember { mutableStateOf(payment.paymentMethod) }
    var receiptNumber by remember { mutableStateOf(payment.receiptNumber) }
    var paymentDate by remember { mutableStateOf(payment.paymentDate) }
    var remarks by remember { mutableStateOf(payment.remarks) }

    val quickMethods = listOf(
        "JazzCash (${settings.jazzCashNumber})",
        "EasyPaisa (${settings.easyPaisaNumber})",
        "Cash"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Edit Payment Receipt", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${payment.studentName} • Challan #${payment.challanNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Payment Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickMethods) { m ->
                        FilterChip(
                            selected = paymentMethod == m,
                            onClick = { paymentMethod = m },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = receiptNumber,
                    onValueChange = { receiptNumber = it },
                    label = { Text("Receipt # / Transaction ID *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text("Payment Date *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = (amountStr.toDoubleOrNull() ?: payment.amount).coerceAtLeast(1.0)
                            onSave(
                                payment.copy(
                                    amount = amt,
                                    paymentMethod = paymentMethod.ifBlank { payment.paymentMethod },
                                    receiptNumber = receiptNumber.ifBlank { payment.receiptNumber },
                                    paymentDate = paymentDate.ifBlank { payment.paymentDate },
                                    remarks = remarks
                                )
                            )
                        },
                        enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 && receiptNumber.isNotBlank()
                    ) {
                        Text("Save Receipt")
                    }
                }
            }
        }
    }
}
