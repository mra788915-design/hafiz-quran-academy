package com.example.ui.teacher

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.*
import com.example.ui.AcademyViewModel
import com.example.ui.admin.EditMadniQaidaLogDialog
import com.example.ui.admin.EditNamazLogDialog
import com.example.ui.admin.EditQuranLessonDialog
import com.example.ui.admin.ExamTypeBadge
import com.example.ui.admin.MadniQaidaLogCardItem
import com.example.ui.admin.NamazLogCardItem
import com.example.ui.admin.QuranProgressCardItem
import com.example.ui.admin.RecordMadniQaidaLogDialog
import com.example.ui.admin.RecordNamazLogDialog
import com.example.ui.admin.RecordQuranLessonDialog
import com.example.ui.admin.UploadBatchExamMarksDialog
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.LeaveRequestCardItem
import com.example.ui.components.ReviewLeaveDialog
import com.example.ui.components.StatusBadge
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TeacherPortalMainScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allClasses by viewModel.classes.collectAsState(initial = emptyList())
    val allStudents by viewModel.activeStudents.collectAsState(initial = emptyList())
    val progressList by viewModel.quranProgressList.collectAsState(initial = emptyList())
    val namazLogs by viewModel.namazLogsList.collectAsState(initial = emptyList())
    val qaidaLogs by viewModel.madniQaidaLogsList.collectAsState(initial = emptyList())
    val allLeaves by viewModel.leaveRequestsList.collectAsState(initial = emptyList())
    val allExams by viewModel.exams.collectAsState(initial = emptyList())
    val allExamMarks by viewModel.examMarks.collectAsState(initial = emptyList())

    // Strictly isolate to classes & students assigned to this authenticated Teacher ONLY
    val assignedClasses = remember(allClasses, currentUser) {
        allClasses.filter { cls ->
            (currentUser.teacherId != null && cls.assignedTeacherId == currentUser.teacherId) ||
                    cls.id in currentUser.assignedClassIds
        }
    }

    val assignedClsIds = remember(assignedClasses, currentUser) {
        assignedClasses.map { it.id }.toSet() + currentUser.assignedClassIds
    }

    val teacherStudents = remember(allStudents, currentUser, assignedClsIds) {
        allStudents.filter { stud ->
            (currentUser.teacherId != null && stud.teacherId == currentUser.teacherId) ||
                    stud.classId in assignedClsIds
        }
    }

    val myStudentIds = remember(teacherStudents) { teacherStudents.map { it.id }.toSet() }
    val teacherProgressList = remember(progressList, myStudentIds, currentUser) {
        progressList.filter { rec ->
            rec.studentId in myStudentIds || (currentUser.teacherId != null && rec.teacherId == currentUser.teacherId)
        }
    }
    val teacherNamazList = remember(namazLogs, myStudentIds, currentUser) {
        namazLogs.filter { rec ->
            rec.studentId in myStudentIds || (currentUser.teacherId != null && rec.teacherId == currentUser.teacherId)
        }
    }
    val teacherQaidaList = remember(qaidaLogs, myStudentIds, currentUser) {
        qaidaLogs.filter { rec ->
            rec.studentId in myStudentIds || (currentUser.teacherId != null && rec.teacherId == currentUser.teacherId)
        }
    }
    val teacherLeavesList = remember(allLeaves, myStudentIds, assignedClsIds, currentUser) {
        allLeaves.filter { req ->
            req.studentId in myStudentIds ||
                req.classId in assignedClsIds ||
                (currentUser.teacherId != null && req.teacherId == currentUser.teacherId)
        }.sortedWith(
            compareBy<LeaveRequest> { if (it.status == ApprovalStatus.PENDING) 0 else 1 }
                .thenByDescending { it.createdAt }
        )
    }
    val pendingLeavesCount = remember(teacherLeavesList) {
        teacherLeavesList.count { it.status == ApprovalStatus.PENDING }
    }
    val teacherExams = remember(allExams, assignedClsIds) {
        allExams.filter { it.classId in assignedClsIds }
    }
    val teacherMarksByExam = remember(allExamMarks, myStudentIds) {
        allExamMarks.filter { it.studentId in myStudentIds }.groupBy { it.examId }
    }

    var selectedTab by remember { mutableStateOf(0) }
    var selectedLogSubTab by remember { mutableStateOf(0) } // 0: Quran, 1: Namaz, 2: Madni Qaida
    var teacherExamTypeFilter by remember { mutableStateOf<ExamType?>(null) }
    var examForMarksUpload by remember { mutableStateOf<AcademyExam?>(null) }

    var showLessonDialog by remember { mutableStateOf(false) }
    var editingProgress by remember { mutableStateOf<QuranProgressRecord?>(null) }
    var deletingProgress by remember { mutableStateOf<QuranProgressRecord?>(null) }

    var showNamazDialog by remember { mutableStateOf(false) }
    var editingNamaz by remember { mutableStateOf<NamazLogRecord?>(null) }
    var deletingNamaz by remember { mutableStateOf<NamazLogRecord?>(null) }

    var showQaidaDialog by remember { mutableStateOf(false) }
    var editingQaida by remember { mutableStateOf<MadniQaidaLogRecord?>(null) }
    var deletingQaida by remember { mutableStateOf<MadniQaidaLogRecord?>(null) }

    var reviewingLeave by remember { mutableStateOf<Pair<LeaveRequest, Boolean>?>(null) }

    var selectedClassForAtt by remember { mutableStateOf<AcademyClass?>(null) }
    var selectedAttDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var showClearAttConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(assignedClasses) {
        if (selectedClassForAtt == null || assignedClasses.none { it.id == selectedClassForAtt?.id }) {
            selectedClassForAtt = assignedClasses.firstOrNull()
        }
    }

    val existingAttendance by remember(selectedClassForAtt, selectedAttDate) {
        if (selectedClassForAtt != null) {
            viewModel.repository.getAttendanceByClassAndDate(selectedClassForAtt!!.id, selectedAttDate)
        } else {
            flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val existingAttByStudent = remember(existingAttendance) {
        existingAttendance.associateBy { it.studentId }
    }

    val attendanceMarks = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val attendanceRemarks = remember { mutableStateMapOf<String, String>() }

    val classStudentsForAtt = remember(teacherStudents, selectedClassForAtt) {
        if (selectedClassForAtt == null) emptyList()
        else teacherStudents.filter { it.classId == selectedClassForAtt!!.id }
    }

    LaunchedEffect(classStudentsForAtt, existingAttendance) {
        attendanceMarks.clear()
        attendanceRemarks.clear()
        classStudentsForAtt.forEach { s ->
            val rec = existingAttByStudent[s.id]
            attendanceMarks[s.id] = rec?.status ?: AttendanceStatus.PRESENT
            if (rec != null && rec.remarks.isNotBlank()) {
                attendanceRemarks[s.id] = rec.remarks
            }
        }
    }

    if (showClearAttConfirm && selectedClassForAtt != null) {
        AlertDialog(
            onDismissRequest = { showClearAttConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Clear Attendance for $selectedAttDate?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove all ${existingAttendance.size} attendance records for ${selectedClassForAtt!!.name} on $selectedAttDate?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearClassAttendanceForDate(selectedClassForAtt!!.id, selectedAttDate)
                        showClearAttConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("teacher_confirm_clear_att_btn")
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAttConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedTab == 2 && teacherStudents.isNotEmpty()) {
                val (fabLabel, fabTag, onFabClick) = when (selectedLogSubTab) {
                    1 -> Triple("Log Namaz", "teacher_log_namaz_fab") { showNamazDialog = true }
                    2 -> Triple("Log Qaida", "teacher_log_qaida_fab") { showQaidaDialog = true }
                    else -> Triple("Log Quran", "teacher_log_recitation_fab") { showLessonDialog = true }
                }
                ExtendedFloatingActionButton(
                    onClick = onFabClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = fabLabel) },
                    text = { Text(fabLabel) },
                    modifier = Modifier.testTag(fabTag)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
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
                            text = "Teacher Portal (Isolated Session)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = currentUser.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${assignedClasses.size} Assigned Halaqahs • ${teacherStudents.size} Assigned Students",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Students (${teacherStudents.size})", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("teacher_tab_students")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Attendance", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("teacher_tab_attendance")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Logs", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("teacher_tab_logs")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Text(
                            text = if (pendingLeavesCount > 0) "Leaves ($pendingLeavesCount)" else "Leaves",
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.testTag("teacher_tab_leaves")
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Exams (${teacherExams.size})", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("teacher_tab_exams")
                )
            }

            when (selectedTab) {
                0 -> {
                    if (teacherStudents.isEmpty()) {
                        EmptyPlaceholderCard(
                            title = "No students assigned yet",
                            subtitle = "Students enrolled in your assigned Halaqah by Administration automatically appear here.",
                            icon = Icons.Default.People
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(teacherStudents, key = { it.id }) { student ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = student.fullName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            StatusBadge(student.quranLevel, Color(0xFFD1E7DD), Color(0xFF0F5132))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Adm #${student.admissionNumber} • S/o ${student.guardianName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Halaqah: ${student.className}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (assignedClasses.isEmpty()) {
                        EmptyPlaceholderCard(
                            title = "No assigned classes",
                            subtitle = "Attendance can only be marked for Halaqahs assigned to your account.",
                            icon = Icons.Default.FactCheck
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = selectedAttDate,
                                    onValueChange = { selectedAttDate = it },
                                    label = { Text("Date (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (existingAttendance.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showClearAttConfirm = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear Date", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(assignedClasses) { cls ->
                                    FilterChip(
                                        selected = selectedClassForAtt?.id == cls.id,
                                        onClick = { selectedClassForAtt = cls },
                                        label = { Text(cls.name) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            if (classStudentsForAtt.isEmpty()) {
                                EmptyPlaceholderCard(
                                    title = "No students in ${selectedClassForAtt?.name ?: "this class"}",
                                    subtitle = "Once Admin enrolls students into this Halaqah, you can mark attendance here.",
                                    icon = Icons.Default.People
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(classStudentsForAtt, key = { it.id }) { stud ->
                                        val currentStatus = attendanceMarks[stud.id] ?: AttendanceStatus.PRESENT
                                        val savedRec = existingAttByStudent[stud.id]
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${stud.fullName} (${stud.admissionNumber})",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    if (savedRec != null) {
                                                        IconButton(
                                                            onClick = { viewModel.deleteAttendanceRecord(savedRec.id) },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.DeleteOutline,
                                                                contentDescription = "Remove Record",
                                                                tint = Color(0xFFDC2626),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    AttendanceStatus.values().forEach { statusOption ->
                                                        FilterChip(
                                                            selected = currentStatus == statusOption,
                                                            onClick = { attendanceMarks[stud.id] = statusOption },
                                                            label = { Text(statusOption.name, fontSize = 11.sp) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val cls = selectedClassForAtt ?: return@Button
                                        val filteredMap = classStudentsForAtt.associate { s ->
                                            s.id to (attendanceMarks[s.id] ?: AttendanceStatus.PRESENT)
                                        }
                                        viewModel.saveAttendance(
                                            cls.id,
                                            cls.name,
                                            selectedAttDate,
                                            filteredMap,
                                            attendanceRemarks.toMap()
                                        ) {}
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (existingAttendance.isNotEmpty()) "Update Attendance" else "Save Class Attendance")
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedLogSubTab == 0,
                                    onClick = { selectedLogSubTab = 0 },
                                    leadingIcon = { Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Quran (${teacherProgressList.size})", fontSize = 12.sp) },
                                    modifier = Modifier.testTag("teacher_subtab_quran_logs")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedLogSubTab == 1,
                                    onClick = { selectedLogSubTab = 1 },
                                    leadingIcon = { Icon(Icons.Default.Mosque, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Namaz (${teacherNamazList.size})", fontSize = 12.sp) },
                                    modifier = Modifier.testTag("teacher_subtab_namaz_logs")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedLogSubTab == 2,
                                    onClick = { selectedLogSubTab = 2 },
                                    leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Madni Qaida (${teacherQaidaList.size})", fontSize = 12.sp) },
                                    modifier = Modifier.testTag("teacher_subtab_qaida_logs")
                                )
                            }
                        }

                        when (selectedLogSubTab) {
                            0 -> {
                                if (teacherProgressList.isEmpty()) {
                                    EmptyPlaceholderCard(
                                        title = "No Quran lesson records",
                                        subtitle = "Tap 'Log Quran' to record Sabaq, Sabqi, and Manzil for your assigned students.",
                                        icon = Icons.Default.AutoStories
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 84.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(teacherProgressList, key = { it.id }) { rec ->
                                            QuranProgressCardItem(
                                                record = rec,
                                                onEdit = { editingProgress = rec },
                                                onDelete = { deletingProgress = rec }
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (teacherNamazList.isEmpty()) {
                                    EmptyPlaceholderCard(
                                        title = "No Namaz & Dua logs yet",
                                        subtitle = "Tap 'Log Namaz' to record daily Salah, Wudu, and Masnoon Duas progress.",
                                        icon = Icons.Default.Mosque
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 84.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(teacherNamazList, key = { it.id }) { rec ->
                                            NamazLogCardItem(
                                                record = rec,
                                                onEdit = { editingNamaz = rec },
                                                onDelete = { deletingNamaz = rec }
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                if (teacherQaidaList.isEmpty()) {
                                    EmptyPlaceholderCard(
                                        title = "No Madni Qaida logs yet",
                                        subtitle = "Tap 'Log Qaida' to track Huroof, Harakat, and Tajweed Sabaq lessons.",
                                        icon = Icons.Default.MenuBook
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 84.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(teacherQaidaList, key = { it.id }) { rec ->
                                            MadniQaidaLogCardItem(
                                                record = rec,
                                                onEdit = { editingQaida = rec },
                                                onDelete = { deletingQaida = rec }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    if (teacherLeavesList.isEmpty()) {
                        EmptyPlaceholderCard(
                            title = "No student leave requests",
                            subtitle = "Leave applications submitted by Parents for students in your Halaqah will appear here for approval.",
                            icon = Icons.Default.EventBusy
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (pendingLeavesCount > 0) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Default.PendingActions, contentDescription = null, tint = Color(0xFF92400E))
                                            Text(
                                                text = "$pendingLeavesCount pending leave request(s) awaiting your review.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF78350F)
                                            )
                                        }
                                    }
                                }
                            }
                            items(teacherLeavesList, key = { it.id }) { leave ->
                                LeaveRequestCardItem(
                                    leave = leave,
                                    showReviewerActions = true,
                                    showCancelOrDeleteAction = false,
                                    onApprove = { reviewingLeave = leave to true },
                                    onReject = { reviewingLeave = leave to false }
                                )
                            }
                        }
                    }
                }
                4 -> {
                    val filteredTeacherExams = remember(teacherExams, teacherExamTypeFilter) {
                        if (teacherExamTypeFilter == null) teacherExams
                        else teacherExams.filter { it.examType == teacherExamTypeFilter }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = teacherExamTypeFilter == null,
                                    onClick = { teacherExamTypeFilter = null },
                                    label = { Text("All (${teacherExams.size})") }
                                )
                            }
                            items(ExamType.values()) { t ->
                                val cnt = teacherExams.count { it.examType == t }
                                FilterChip(
                                    selected = teacherExamTypeFilter == t,
                                    onClick = { teacherExamTypeFilter = t },
                                    label = { Text("${t.shortLabel} ($cnt)") }
                                )
                            }
                        }

                        if (filteredTeacherExams.isEmpty()) {
                            EmptyPlaceholderCard(
                                title = "No scheduled exams for your classes",
                                subtitle = "Once Administration schedules a Test, Mid Exam, or Final Exam for your Halaqah, you can upload student marks here.",
                                icon = Icons.Default.Assessment
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF0369A1))
                                            Text(
                                                text = "Teacher Marks Upload Only: Upload/update marks for your assigned classes. Exam creation & deletion remain Admin-only.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF0C4A6E)
                                            )
                                        }
                                    }
                                }

                                items(filteredTeacherExams, key = { it.id }) { exam ->
                                    val classStudents = teacherStudents.filter { it.classId == exam.classId }
                                    val examMarks = teacherMarksByExam[exam.id].orEmpty()
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
                                                ExamTypeBadge(exam.examType)
                                                Text(
                                                    text = exam.examDate,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = exam.title,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "${exam.className} • ${exam.subjectName} • Term ${exam.academicTerm}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Max Marks: ${exam.totalMarks.toInt()} (Passing: ${exam.passingMarks.toInt()}) • Marks Entered: ${examMarks.size}/${classStudents.size}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = { examForMarksUpload = exam },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("teacher_upload_marks_btn_${exam.id}"),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (examMarks.isEmpty()) "Upload Class Marks" else "Upload / Update Marks (${examMarks.size}/${classStudents.size})"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLessonDialog && teacherStudents.isNotEmpty()) {
        RecordQuranLessonDialog(
            students = teacherStudents,
            onDismiss = { showLessonDialog = false },
            onSubmit = { studentId, date, surah, sNum, juz, start, end, rating, sabqi, manzil, mistakes, remarks, target ->
                viewModel.recordQuranProgress(
                    studentId, date, surah, sNum, juz, start, end, rating, sabqi, manzil, mistakes, remarks, target
                ) {
                    showLessonDialog = false
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
                Text("Remove ${rec.studentName}'s ${rec.surahName} recitation record from ${rec.date}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuranProgress(rec.id)
                        deletingProgress = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProgress = null }) { Text("Cancel") }
            }
        )
    }

    if (showNamazDialog && teacherStudents.isNotEmpty()) {
        RecordNamazLogDialog(
            students = teacherStudents,
            onDismiss = { showNamazDialog = false },
            onSubmit = { studentId, date, prayerName, duaTopic, rating, wuduNotes, remarks, target ->
                viewModel.recordNamazLog(
                    studentId, date, prayerName, duaTopic, rating, wuduNotes, remarks, target
                ) {
                    showNamazDialog = false
                }
            }
        )
    }

    editingNamaz?.let { rec ->
        EditNamazLogDialog(
            record = rec,
            onDismiss = { editingNamaz = null },
            onSave = { updated ->
                viewModel.updateNamazLog(updated) {
                    editingNamaz = null
                }
            }
        )
    }

    deletingNamaz?.let { rec ->
        AlertDialog(
            onDismissRequest = { deletingNamaz = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Namaz Log?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove ${rec.studentName}'s ${rec.prayerName} log from ${rec.date}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNamazLog(rec.id)
                        deletingNamaz = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deletingNamaz = null }) { Text("Cancel") }
            }
        )
    }

    if (showQaidaDialog && teacherStudents.isNotEmpty()) {
        RecordMadniQaidaLogDialog(
            students = teacherStudents,
            onDismiss = { showQaidaDialog = false },
            onSubmit = { studentId, date, lessonNum, title, page, lines, rating, rev, mistakes, remarks, target ->
                viewModel.recordMadniQaidaLog(
                    studentId, date, lessonNum, title, page, lines, rating, rev, mistakes, remarks, target
                ) {
                    showQaidaDialog = false
                }
            }
        )
    }

    editingQaida?.let { rec ->
        EditMadniQaidaLogDialog(
            record = rec,
            onDismiss = { editingQaida = null },
            onSave = { updated ->
                viewModel.updateMadniQaidaLog(updated) {
                    editingQaida = null
                }
            }
        )
    }

    deletingQaida?.let { rec ->
        AlertDialog(
            onDismissRequest = { deletingQaida = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Madni Qaida Log?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove ${rec.studentName}'s ${rec.lessonTitle} record from ${rec.date}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMadniQaidaLog(rec.id)
                        deletingQaida = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deletingQaida = null }) { Text("Cancel") }
            }
        )
    }

    reviewingLeave?.let { (leave, initialApprove) ->
        ReviewLeaveDialog(
            leave = leave,
            initialApprove = initialApprove,
            onDismiss = { reviewingLeave = null },
            onConfirm = { approved, remarks ->
                viewModel.reviewLeaveRequest(leave.id, approved, remarks) {
                    reviewingLeave = null
                }
            }
        )
    }

    examForMarksUpload?.let { exam ->
        val classStudents = teacherStudents.filter { it.classId == exam.classId }
        val existingMarks = teacherMarksByExam[exam.id].orEmpty()
        UploadBatchExamMarksDialog(
            exam = exam,
            studentsInClass = classStudents,
            existingMarks = existingMarks,
            onDismiss = { examForMarksUpload = null },
            onSaveBatch = { marksPayload, remarksMap ->
                viewModel.saveBatchExamMarks(
                    examId = exam.id,
                    studentMarks = marksPayload,
                    remarksMap = remarksMap
                ) {
                    examForMarksUpload = null
                }
            }
        )
    }
}
