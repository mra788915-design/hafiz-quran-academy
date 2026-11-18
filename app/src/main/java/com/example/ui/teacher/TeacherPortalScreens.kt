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
import com.example.ui.admin.EditQuranLessonDialog
import com.example.ui.admin.QuranProgressCardItem
import com.example.ui.admin.RecordQuranLessonDialog
import com.example.ui.components.EmptyPlaceholderCard
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

    // Strictly isolate to classes & students assigned to this authenticated Teacher ONLY
    val assignedClasses = remember(allClasses, currentUser) {
        allClasses.filter { cls ->
            (currentUser.teacherId != null && cls.assignedTeacherId == currentUser.teacherId) ||
                    cls.id in currentUser.assignedClassIds
        }
    }

    val teacherStudents = remember(allStudents, currentUser, assignedClasses) {
        val assignedClsIds = assignedClasses.map { it.id }.toSet() + currentUser.assignedClassIds
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

    var selectedTab by remember { mutableStateOf(0) }
    var showLessonDialog by remember { mutableStateOf(false) }
    var editingProgress by remember { mutableStateOf<QuranProgressRecord?>(null) }
    var deletingProgress by remember { mutableStateOf<QuranProgressRecord?>(null) }

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
                ExtendedFloatingActionButton(
                    onClick = { showLessonDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Log Recitation") },
                    modifier = Modifier.testTag("teacher_log_recitation_fab")
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

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Students (${teacherStudents.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Attendance", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Quran Log (${teacherProgressList.size})", fontSize = 12.sp) }
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
                    if (teacherProgressList.isEmpty()) {
                        EmptyPlaceholderCard(
                            title = "No lesson records",
                            subtitle = "Tap 'Log Recitation' to record Sabaq, Sabqi, and Manzil for your assigned students.",
                            icon = Icons.Default.AutoStories
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 84.dp),
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
}
