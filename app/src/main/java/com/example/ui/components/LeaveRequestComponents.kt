package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.ApprovalStatus
import com.example.data.models.LeaveRequest
import com.example.data.models.Student
import java.text.SimpleDateFormat
import java.util.*

private val LEAVE_TYPES = listOf(
    "Sick / Medical",
    "Family Emergency",
    "Out of Station",
    "Urgent Work",
    "Other"
)

@Composable
fun LeaveRequestCardItem(
    leave: LeaveRequest,
    showReviewerActions: Boolean = false,
    showCancelOrDeleteAction: Boolean = false,
    deleteLabel: String = "Cancel Request",
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
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
                        text = leave.studentName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Adm #${leave.admissionNumber} • ${leave.className}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (leave.status) {
                    ApprovalStatus.APPROVED -> StatusBadge("APPROVED", Color(0xFFD1E7DD), Color(0xFF0F5132))
                    ApprovalStatus.PENDING -> StatusBadge("PENDING", Color(0xFFFEF3C7), Color(0xFF92400E))
                    ApprovalStatus.REJECTED -> StatusBadge("REJECTED", Color(0xFFFEE2E2), Color(0xFF991B1B))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Type: ${leave.leaveType}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = if (leave.fromDate == leave.toDate) leave.fromDate else "${leave.fromDate} → ${leave.toDate}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Reason: ${leave.reason}",
                style = MaterialTheme.typography.bodySmall
            )

            if (leave.parentName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Submitted by: ${leave.parentName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (leave.status != ApprovalStatus.PENDING && leave.reviewerName.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reviewed by ${leave.reviewerName} (${leave.reviewerRole}) on ${leave.reviewedDate}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (leave.status == ApprovalStatus.APPROVED) Color(0xFF0F5132) else Color(0xFF991B1B)
                )
                if (leave.reviewerRemarks.isNotBlank()) {
                    Text(
                        text = "Note: \"${leave.reviewerRemarks}\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val canApproveReject = showReviewerActions && leave.status == ApprovalStatus.PENDING
            val canDelete = showCancelOrDeleteAction && onDelete != null
            if (canApproveReject || canDelete) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canDelete) {
                        OutlinedButton(
                            onClick = { onDelete?.invoke() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("delete_leave_${leave.id}"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = deleteLabel, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(deleteLabel, fontSize = 12.sp)
                        }
                    }
                    if (canApproveReject) {
                        OutlinedButton(
                            onClick = { onReject?.invoke() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("reject_leave_${leave.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Reject Leave", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onApprove?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("approve_leave_${leave.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Approve Leave", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitLeaveRequestDialog(
    children: List<Student>,
    initialChild: Student?,
    onDismiss: () -> Unit,
    onSubmit: (studentId: String, leaveType: String, fromDate: String, toDate: String, reason: String) -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedChild by remember { mutableStateOf(initialChild ?: children.firstOrNull()) }
    var childExpanded by remember { mutableStateOf(false) }
    var leaveType by remember { mutableStateOf(LEAVE_TYPES.first()) }
    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }
    var reason by remember { mutableStateOf("") }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Submit Leave Request", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
                Text(
                    text = "Application will be sent live to Admin and Halaqah Teacher for approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (children.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = childExpanded,
                        onExpandedChange = { childExpanded = !childExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedChild?.let { "${it.fullName} (${it.className})" } ?: "Select Child",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Child / Talib-e-Ilm *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = childExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = childExpanded,
                            onDismissRequest = { childExpanded = false }
                        ) {
                            children.forEach { child ->
                                DropdownMenuItem(
                                    text = { Text("${child.fullName} (${child.admissionNumber})") },
                                    onClick = {
                                        selectedChild = child
                                        childExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (selectedChild != null) {
                    Text(
                        text = "Student: ${selectedChild!!.fullName} (${selectedChild!!.className})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text("Leave Type:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LEAVE_TYPES) { type ->
                        FilterChip(
                            selected = leaveType == type,
                            onClick = { leaveType = type },
                            label = { Text(type, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromDate,
                        onValueChange = { fromDate = it },
                        label = { Text("From Date *") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = toDate,
                        onValueChange = { toDate = it },
                        label = { Text("To Date *") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Absence / Leave *") },
                    placeholder = { Text("e.g. Student has fever and doctor advised rest") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_reason_input")
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val child = selectedChild ?: return@Button
                            onSubmit(child.id, leaveType, fromDate, toDate.ifBlank { fromDate }, reason)
                        },
                        enabled = selectedChild != null && fromDate.length >= 8 && reason.trim().length >= 2,
                        modifier = Modifier.testTag("submit_leave_request_btn")
                    ) {
                        Text("Send Leave Request")
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewLeaveDialog(
    leave: LeaveRequest,
    initialApprove: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (approved: Boolean, remarks: String) -> Unit
) {
    var remarks by remember { mutableStateOf("") }
    var approveChoice by remember { mutableStateOf(initialApprove) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (approveChoice) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (approveChoice) Color(0xFF15803D) else Color(0xFFDC2626)
            )
        },
        title = {
            Text(
                text = if (approveChoice) "Approve Leave Request?" else "Reject Leave Request?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${leave.studentName} (${leave.className})\nDates: ${leave.fromDate} to ${leave.toDate}\nReason: ${leave.reason}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (approveChoice) {
                    Text(
                        text = "Approving will automatically mark LEAVE in the Attendance Register for these dates.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0F5132)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = approveChoice,
                        onClick = { approveChoice = true },
                        label = { Text("Approve") }
                    )
                    FilterChip(
                        selected = !approveChoice,
                        onClick = { approveChoice = false },
                        label = { Text("Reject") }
                    )
                }
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Teacher / Admin Remarks (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_leave_remarks_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(approveChoice, remarks) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (approveChoice) Color(0xFF15803D) else Color(0xFFDC2626)
                ),
                modifier = Modifier.testTag("confirm_leave_review_btn")
            ) {
                Text(if (approveChoice) "Approve Leave" else "Reject Leave")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
