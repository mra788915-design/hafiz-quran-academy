package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.*
import com.example.ui.AcademyViewModel
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.StatusBadge
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FeeSabilillahScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val fsRecords by viewModel.fsRecords.collectAsState(initial = emptyList())
    val students by viewModel.activeStudents.collectAsState(initial = emptyList())

    var showApplyDialog by remember { mutableStateOf(false) }
    var recordToReview by remember { mutableStateOf<FeeSabilillahRecord?>(null) }
    var editingFsRecord by remember { mutableStateOf<FeeSabilillahRecord?>(null) }
    var deletingFsRecord by remember { mutableStateOf<FeeSabilillahRecord?>(null) }
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showApplyDialog = true },
                icon = { Icon(Icons.Default.VolunteerActivism, contentDescription = null) },
                text = { Text("New FS Application") },
                modifier = Modifier.testTag("new_fs_fab")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Fee Sabilillah (FS) Trust Program",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Provides verified financial assistance and full fee waivers for deserving Quran students and orphans funded through the Academy Waqf.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (fsRecords.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No FS applications on record",
                    subtitle = "Tap 'New FS Application' to register deserving or orphan students for fee sponsorship.",
                    icon = Icons.Default.VolunteerActivism
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(fsRecords, key = { it.id }) { record ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { recordToReview = record },
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
                                            text = record.studentName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Adm #${record.admissionNumber} • ${record.className}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    when (record.approvalStatus) {
                                        ApprovalStatus.APPROVED -> StatusBadge("APPROVED", Color(0xFFD1E7DD), Color(0xFF0F5132))
                                        ApprovalStatus.PENDING -> StatusBadge("PENDING", Color(0xFFFEF3C7), Color(0xFF92400E))
                                        ApprovalStatus.REJECTED -> StatusBadge("REJECTED", Color(0xFFFEE2E2), Color(0xFF991B1B))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Category: ${record.category}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${record.assistanceType.name} (Rs. ${formatter.format(record.approvedAmount)})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    )
                                }

                                if (record.confidentialRemarks.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Remarks: ${record.confidentialRemarks}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    OutlinedButton(
                                        onClick = { recordToReview = record },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Decision", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { editingFsRecord = record },
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("edit_fs_${record.id}"),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit FS Record", modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { deletingFsRecord = record },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("delete_fs_${record.id}"),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove FS Record", modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remove", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApplyDialog) {
        NewFsDialog(
            students = students,
            onDismiss = { showApplyDialog = false },
            onSubmit = { studentId, cat, type, amount, start, remarks ->
                viewModel.submitFsApplication(studentId, cat, type, amount, start, remarks) {
                    showApplyDialog = false
                }
            }
        )
    }

    recordToReview?.let { rec ->
        ReviewFsDialog(
            record = rec,
            onDismiss = { recordToReview = null },
            onUpdate = { approved, remarks ->
                viewModel.updateFsStatus(rec, approved, remarks)
                recordToReview = null
            }
        )
    }

    editingFsRecord?.let { rec ->
        EditFsDialog(
            record = rec,
            onDismiss = { editingFsRecord = null },
            onSave = { updated ->
                viewModel.updateFsDetails(updated) {
                    editingFsRecord = null
                }
            }
        )
    }

    deletingFsRecord?.let { rec ->
        AlertDialog(
            onDismissRequest = { deletingFsRecord = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove FS Record?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove Fee Sabilillah sponsorship record for ${rec.studentName}? Their enrollment will revert to Regular fee status."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFsRecord(rec.id)
                        deletingFsRecord = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_fs_btn")
                ) {
                    Text("Remove Record")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFsRecord = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewFsDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSubmit: (studentId: String, category: String, type: AssistanceType, amount: Double, startDate: String, remarks: String) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var category by remember { mutableStateOf("Orphan / Deserving") }
    var assistanceType by remember { mutableStateOf(AssistanceType.FULL) }
    var amountStr by remember { mutableStateOf("4500") }
    var startDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-01", Locale.US).format(Date())) }
    var remarks by remember { mutableStateOf("") }
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
                Text("New Fee Sabilillah Application", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

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
                                onClick = {
                                    selectedStudent = s
                                    amountStr = s.monthlyFee.toInt().toString()
                                    studentExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Assistance Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = assistanceType == AssistanceType.FULL,
                        onClick = { assistanceType = AssistanceType.FULL },
                        label = { Text("100% Full Waiver") }
                    )
                    FilterChip(
                        selected = assistanceType == AssistanceType.PARTIAL,
                        onClick = { assistanceType = AssistanceType.PARTIAL },
                        label = { Text("Partial Reduction") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Approved Waiver Amount (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Confidential Verification Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val stud = selectedStudent ?: return@Button
                            val amt = amountStr.toDoubleOrNull() ?: stud.monthlyFee
                            onSubmit(stud.id, category, assistanceType, amt, startDate, remarks)
                        },
                        enabled = selectedStudent != null
                    ) {
                        Text("Submit Application")
                    }
                }
            }
        }
    }
}

@Composable
fun EditFsDialog(
    record: FeeSabilillahRecord,
    onDismiss: () -> Unit,
    onSave: (FeeSabilillahRecord) -> Unit
) {
    var category by remember { mutableStateOf(record.category) }
    var assistanceType by remember { mutableStateOf(record.assistanceType) }
    var approvedAmountStr by remember { mutableStateOf(record.approvedAmount.toInt().toString()) }
    var startDate by remember { mutableStateOf(record.effectiveStartDate) }
    var endDate by remember { mutableStateOf(record.effectiveEndDate) }
    var approvalStatus by remember { mutableStateOf(record.approvalStatus) }
    var confidentialRemarks by remember { mutableStateOf(record.confidentialRemarks) }

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
                Text("Edit FS Sponsorship", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${record.studentName} (${record.admissionNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Assistance Category *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = assistanceType == AssistanceType.FULL,
                        onClick = { assistanceType = AssistanceType.FULL },
                        label = { Text("FULL Waiver") }
                    )
                    FilterChip(
                        selected = assistanceType == AssistanceType.PARTIAL,
                        onClick = { assistanceType = AssistanceType.PARTIAL },
                        label = { Text("PARTIAL") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = approvedAmountStr,
                    onValueChange = { approvedAmountStr = it },
                    label = { Text("Approved Waiver Amount (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start Date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End Date (Opt)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Approval Status:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApprovalStatus.values().forEach { st ->
                        FilterChip(
                            selected = approvalStatus == st,
                            onClick = { approvalStatus = st },
                            label = { Text(st.name, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confidentialRemarks,
                    onValueChange = { confidentialRemarks = it },
                    label = { Text("Confidential Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                record.copy(
                                    category = category.ifBlank { record.category },
                                    assistanceType = assistanceType,
                                    approvedAmount = (approvedAmountStr.toDoubleOrNull() ?: record.approvedAmount).coerceAtLeast(0.0),
                                    effectiveStartDate = startDate.ifBlank { record.effectiveStartDate },
                                    effectiveEndDate = endDate,
                                    approvalStatus = approvalStatus,
                                    confidentialRemarks = confidentialRemarks
                                )
                            )
                        },
                        enabled = category.isNotBlank()
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewFsDialog(
    record: FeeSabilillahRecord,
    onDismiss: () -> Unit,
    onUpdate: (approved: Boolean, remarks: String) -> Unit
) {
    var remarks by remember { mutableStateOf(record.confidentialRemarks) }

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
                Text("Review FS Application", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Student: ${record.studentName} (${record.admissionNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Category: ${record.category}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Assistance: ${record.assistanceType.name} (PKR ${record.approvedAmount.toInt()})",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Confidential Admin Decision Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onUpdate(false, remarks) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject")
                    }
                    Button(
                        onClick = { onUpdate(true, remarks) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve")
                    }
                }
            }
        }
    }
}

@Composable
fun QuranProgressReportsScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val progressList by viewModel.quranProgressList.collectAsState(initial = emptyList())
    val students by viewModel.activeStudents.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProgress by remember { mutableStateOf<QuranProgressRecord?>(null) }
    var deletingProgress by remember { mutableStateOf<QuranProgressRecord?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log Lesson") },
                modifier = Modifier.testTag("log_quran_fab")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Quran Memorization & Revision Tracking",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Daily Sabaq, Sabqi & Manzil — Edit or Remove any lesson log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (progressList.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No Quran progress records yet",
                    subtitle = "Tap 'Log Lesson' to record today's Sabaq, Sabqi, and Daur recitation.",
                    icon = Icons.Default.AutoStories
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(progressList, key = { it.id }) { rec ->
                        QuranProgressCardItem(
                            record = rec,
                            onEdit = { editingProgress = rec },
                            onDelete = { deletingProgress = rec }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RecordQuranLessonDialog(
            students = students,
            onDismiss = { showAddDialog = false },
            onSubmit = { studentId, date, surah, sNum, juz, start, end, rating, sabqi, manzil, mistakes, remarks, target ->
                viewModel.recordQuranProgress(
                    studentId, date, surah, sNum, juz, start, end, rating, sabqi, manzil, mistakes, remarks, target
                ) {
                    showAddDialog = false
                }
            }
        )
    }

    editingProgress?.let { rec ->
        EditQuranLessonDialog(
            record = rec,
            onDismiss = { editingProgress = null },
            onSave = { updated ->
                viewModel.updateQuranProgress(updated) {
                    editingProgress = null
                }
            }
        )
    }

    deletingProgress?.let { rec ->
        AlertDialog(
            onDismissRequest = { deletingProgress = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Quran Lesson Log?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove ${rec.studentName}'s ${rec.surahName} lesson log from ${rec.date}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuranProgress(rec.id)
                        deletingProgress = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_progress_btn")
                ) {
                    Text("Remove Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProgress = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
