package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.*
import com.example.ui.AcademyViewModel
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.LeaveRequestCardItem
import com.example.ui.components.ReviewLeaveDialog
import com.example.ui.components.StatusBadge
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceManagementScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val allLeaves by viewModel.leaveRequestsList.collectAsState(initial = emptyList())
    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Daily Attendance, 1 = Leave Requests
    var leaveToReview by remember { mutableStateOf<Pair<LeaveRequest, Boolean>?>(null) }
    var leaveToDelete by remember { mutableStateOf<LeaveRequest?>(null) }

    var selectedClass by remember { mutableStateOf<AcademyClass?>(null) }
    var selectedDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var showClearDateConfirm by remember { mutableStateOf(false) }

    val sortedLeaves = remember(allLeaves) {
        allLeaves.sortedWith(compareBy<LeaveRequest> { it.status != ApprovalStatus.PENDING }.thenByDescending { it.createdAt })
    }
    val pendingLeavesCount = remember(allLeaves) { allLeaves.count { it.status == ApprovalStatus.PENDING } }

    LaunchedEffect(classes) {
        if (selectedClass == null || classes.none { it.id == selectedClass?.id }) {
            selectedClass = classes.firstOrNull()
        }
    }

    val studentsInClass by remember(selectedClass) {
        if (selectedClass != null) {
            viewModel.repository.getStudentsByClass(selectedClass!!.id)
        } else {
            flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val existingAttendance by remember(selectedClass, selectedDate) {
        if (selectedClass != null) {
            viewModel.repository.getAttendanceByClassAndDate(selectedClass!!.id, selectedDate)
        } else {
            flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val existingByStudentId = remember(existingAttendance) {
        existingAttendance.associateBy { it.studentId }
    }

    val attendanceMap = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val remarksMap = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(studentsInClass, existingAttendance) {
        attendanceMap.clear()
        remarksMap.clear()
        studentsInClass.forEach { student ->
            val rec = existingByStudentId[student.id]
            attendanceMap[student.id] = rec?.status ?: AttendanceStatus.PRESENT
            if (rec != null && rec.remarks.isNotBlank()) {
                remarksMap[student.id] = rec.remarks
            }
        }
    }

    var classExpanded by remember { mutableStateOf(false) }

    val presentCount = attendanceMap.values.count { it == AttendanceStatus.PRESENT }
    val absentCount = attendanceMap.values.count { it == AttendanceStatus.ABSENT }
    val lateCount = attendanceMap.values.count { it == AttendanceStatus.LATE }
    val leaveCount = attendanceMap.values.count { it == AttendanceStatus.LEAVE }

    if (showClearDateConfirm && selectedClass != null) {
        AlertDialog(
            onDismissRequest = { showClearDateConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Clear Attendance for $selectedDate?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove all ${existingAttendance.size} saved attendance records for ${selectedClass!!.name} on $selectedDate?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearClassAttendanceForDate(selectedClass!!.id, selectedDate)
                        showClearDateConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_clear_attendance_date_btn")
                ) {
                    Text("Clear Records")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDateConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (selectedSubTab == 0 && studentsInClass.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "$presentCount / ${studentsInClass.size} Present",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (existingAttendance.isNotEmpty()) "Saved (${existingAttendance.size}) • Tap Save to Update"
                                else if (absentCount > 0) "$absentCount Absent"
                                else "Not saved yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (absentCount > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (existingAttendance.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showClearDateConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                    modifier = Modifier.testTag("clear_attendance_date_btn")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Date", fontSize = 12.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    val cls = selectedClass ?: return@Button
                                    viewModel.saveAttendance(
                                        classId = cls.id,
                                        className = cls.name,
                                        date = selectedDate,
                                        records = attendanceMap.toMap(),
                                        remarks = remarksMap.toMap()
                                    ) {}
                                },
                                modifier = Modifier.testTag("save_attendance_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (existingAttendance.isNotEmpty()) "Update" else "Save")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedSubTab) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Daily Attendance", fontSize = 13.sp) },
                    modifier = Modifier.testTag("admin_tab_daily_attendance")
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = {
                        Text(
                            if (pendingLeavesCount > 0) "Leave Requests ($pendingLeavesCount Pending)" else "Leave Requests (${allLeaves.size})",
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.testTag("admin_tab_leave_requests")
                )
            }

            if (selectedSubTab == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Student Leave Applications",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Approving a leave request automatically marks LEAVE in the Attendance Register.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (sortedLeaves.isEmpty()) {
                        EmptyPlaceholderCard(
                            title = "No leave requests yet",
                            subtitle = "Leave applications submitted from Parent Portals will appear here for Admin & Teacher approval.",
                            icon = Icons.Default.EventBusy
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedLeaves, key = { it.id }) { leave ->
                                LeaveRequestCardItem(
                                    leave = leave,
                                    showReviewerActions = true,
                                    showCancelOrDeleteAction = true,
                                    deleteLabel = "Delete",
                                    onApprove = { leaveToReview = leave to true },
                                    onReject = { leaveToReview = leave to false },
                                    onDelete = { leaveToDelete = leave }
                                )
                            }
                        }
                    }
                }
            } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = classExpanded,
                    onExpandedChange = { classExpanded = !classExpanded },
                    modifier = Modifier.weight(1.3f)
                ) {
                    OutlinedTextField(
                        value = selectedClass?.name ?: "Select Class",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Halaqah / Class") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = classExpanded,
                        onDismissRequest = { classExpanded = false }
                    ) {
                        classes.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls.name) },
                                onClick = {
                                    selectedClass = cls
                                    classExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = selectedDate,
                    onValueChange = { selectedDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatPill("Present: $presentCount", Color(0xFFD1E7DD), Color(0xFF0F5132), Modifier.weight(1f))
                StatPill("Absent: $absentCount", Color(0xFFF8D7DA), Color(0xFF842029), Modifier.weight(1f))
                StatPill("Late: $lateCount", Color(0xFFFFF3CD), Color(0xFF664D03), Modifier.weight(1f))
                StatPill("Leave: $leaveCount", Color(0xFFCFF4FC), Color(0xFF055160), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (studentsInClass.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No students in this class",
                    subtitle = "Assign students to this class in the Admissions tab to mark daily attendance.",
                    icon = Icons.Default.FactCheck
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(studentsInClass, key = { it.id }) { student ->
                        val currentStatus = attendanceMap[student.id] ?: AttendanceStatus.PRESENT
                        val savedRecord = existingByStudentId[student.id]
                        AttendanceStudentRow(
                            student = student,
                            status = currentStatus,
                            remark = remarksMap[student.id] ?: "",
                            isSavedInCloud = savedRecord != null,
                            onStatusChange = { newStatus ->
                                attendanceMap[student.id] = newStatus
                            },
                            onRemarkChange = { newRemark ->
                                remarksMap[student.id] = newRemark
                            },
                            onDeleteRecord = savedRecord?.let { rec ->
                                { viewModel.deleteAttendanceRecord(rec.id) }
                            }
                        )
                    }
                }
            }
        }
            }
        }
    }

    leaveToReview?.let { (leave, approve) ->
        ReviewLeaveDialog(
            leave = leave,
            initialApprove = approve,
            onDismiss = { leaveToReview = null },
            onConfirm = { approvedChoice, remarks ->
                viewModel.reviewLeaveRequest(leave.id, approvedChoice, remarks) {
                    leaveToReview = null
                }
            }
        )
    }

    leaveToDelete?.let { leave ->
        AlertDialog(
            onDismissRequest = { leaveToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Leave Request?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove leave request for ${leave.studentName} (${leave.fromDate} to ${leave.toDate})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLeaveRequest(leave.id)
                        leaveToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { leaveToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AttendanceStudentRow(
    student: Student,
    status: AttendanceStatus,
    remark: String,
    isSavedInCloud: Boolean = false,
    onStatusChange: (AttendanceStatus) -> Unit,
    onRemarkChange: (String) -> Unit,
    onDeleteRecord: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (isSavedInCloud) {
                            StatusBadge("SAVED", Color(0xFFD1E7DD), Color(0xFF0F5132))
                        }
                    }
                    Text(
                        text = "Adm #${student.admissionNumber} • S/o ${student.guardianName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusButton("P", AttendanceStatus.PRESENT, status == AttendanceStatus.PRESENT, Color(0xFF15803D)) {
                        onStatusChange(AttendanceStatus.PRESENT)
                    }
                    StatusButton("A", AttendanceStatus.ABSENT, status == AttendanceStatus.ABSENT, Color(0xFFDC2626)) {
                        onStatusChange(AttendanceStatus.ABSENT)
                    }
                    StatusButton("L", AttendanceStatus.LATE, status == AttendanceStatus.LATE, Color(0xFFD97706)) {
                        onStatusChange(AttendanceStatus.LATE)
                    }
                    StatusButton("Lv", AttendanceStatus.LEAVE, status == AttendanceStatus.LEAVE, Color(0xFF2563EB)) {
                        onStatusChange(AttendanceStatus.LEAVE)
                    }
                    if (onDeleteRecord != null) {
                        IconButton(
                            onClick = onDeleteRecord,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("delete_attendance_${student.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove Attendance Record",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (status != AttendanceStatus.PRESENT || remark.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = remark,
                    onValueChange = onRemarkChange,
                    placeholder = { Text("Reason / remark (optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun StatusButton(
    label: String,
    status: AttendanceStatus,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun StatPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}
