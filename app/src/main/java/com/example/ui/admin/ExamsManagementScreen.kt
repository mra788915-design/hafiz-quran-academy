package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.*
import com.example.ui.AcademyViewModel
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.StatusBadge
import com.example.util.ExamReportPdfGenerator
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExamsManagementScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exams by viewModel.exams.collectAsState(initial = emptyList())
    val examMarks by viewModel.examMarks.collectAsState(initial = emptyList())
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val allStudents by viewModel.students.collectAsState(initial = emptyList())
    val activeStudents by viewModel.activeStudents.collectAsState(initial = emptyList())
    val settings = viewModel.repository.settings

    var subTab by remember { mutableStateOf(0) } // 0: Exams & Marks, 1: Student Result Cards
    var typeFilter by remember { mutableStateOf<ExamType?>(null) }
    var classFilterId by remember { mutableStateOf<String?>(null) }

    var showCreateEditExamDialog by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<AcademyExam?>(null) }
    var deletingExam by remember { mutableStateOf<AcademyExam?>(null) }
    var uploadingMarksForExam by remember { mutableStateOf<AcademyExam?>(null) }
    var deletingMark by remember { mutableStateOf<ExamMarkRecord?>(null) }

    var selectedStudentForCard by remember(allStudents) {
        mutableStateOf(allStudents.firstOrNull())
    }
    var cardTypeFilter by remember { mutableStateOf<ExamType?>(null) }

    val filteredExams = remember(exams, typeFilter, classFilterId) {
        exams.filter { ex ->
            (typeFilter == null || ex.examType == typeFilter) &&
                (classFilterId == null || ex.classId == classFilterId)
        }
    }

    val marksByExam = remember(examMarks) { examMarks.groupBy { it.examId } }
    val studentsByClass = remember(activeStudents) { activeStudents.groupBy { it.classId } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (subTab == 0 && classes.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingExam = null
                        showCreateEditExamDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Schedule Exam") },
                    text = { Text("Create Exam") },
                    modifier = Modifier.testTag("create_exam_fab")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = subTab) {
                Tab(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    text = { Text("Exams & Marks (${exams.size})", fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.testTag("admin_exams_tab_exams")
                )
                Tab(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    text = { Text("Result Cards (PDF)", fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.testTag("admin_exams_tab_results")
                )
            }

            if (subTab == 0) {
                // Type & Class Filters
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    item {
                        FilterChip(
                            selected = typeFilter == null,
                            onClick = { typeFilter = null },
                            label = { Text("All Types") }
                        )
                    }
                    items(ExamType.values()) { t ->
                        FilterChip(
                            selected = typeFilter == t,
                            onClick = { typeFilter = t },
                            label = { Text(t.shortLabel) }
                        )
                    }
                    if (classes.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = classFilterId == null,
                                onClick = { classFilterId = null },
                                label = { Text("All Classes") }
                            )
                        }
                        items(classes, key = { it.id }) { cls ->
                            FilterChip(
                                selected = classFilterId == cls.id,
                                onClick = {
                                    classFilterId = if (classFilterId == cls.id) null else cls.id
                                },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }

                if (filteredExams.isEmpty()) {
                    EmptyPlaceholderCard(
                        title = "No Exams Scheduled",
                        subtitle = "Tap 'Create Exam' to schedule Tests, Mid-Term, or Final-Term exams. Teachers can then upload marks for their classes.",
                        icon = Icons.Default.Assignment
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredExams, key = { it.id }) { exam ->
                            val marksForExam = marksByExam[exam.id].orEmpty()
                            val classStuds = studentsByClass[exam.classId].orEmpty()
                            AdminExamCardItem(
                                exam = exam,
                                marks = marksForExam,
                                enrolledCount = classStuds.size,
                                onUploadMarks = { uploadingMarksForExam = exam },
                                onEditExam = {
                                    editingExam = exam
                                    showCreateEditExamDialog = true
                                },
                                onDeleteExam = { deletingExam = exam },
                                onPrintReport = {
                                    ExamReportPdfGenerator.printExamMarksReport(
                                        context = context,
                                        exam = exam,
                                        marks = marksForExam,
                                        enrolledStudents = classStuds,
                                        settings = settings
                                    )
                                },
                                onShareReport = {
                                    ExamReportPdfGenerator.shareExamMarksReportPdf(
                                        context = context,
                                        exam = exam,
                                        marks = marksForExam,
                                        enrolledStudents = classStuds,
                                        settings = settings
                                    )
                                },
                                onDeleteMark = { mark -> deletingMark = mark }
                            )
                        }
                    }
                }
            } else {
                // Student Result Cards PDF Sub-tab
                if (allStudents.isEmpty()) {
                    EmptyPlaceholderCard(
                        title = "No Students Found",
                        subtitle = "Enroll students first to generate individual Exam Result Cards.",
                        icon = Icons.Default.People
                    )
                } else {
                    val activeStudent = selectedStudentForCard ?: allStudents.first()
                    val studentMarksAll = remember(examMarks, activeStudent) {
                        examMarks.filter { it.studentId == activeStudent.id }
                    }
                    val filteredStudentMarks = remember(studentMarksAll, cardTypeFilter) {
                        if (cardTypeFilter == null) studentMarksAll
                        else studentMarksAll.filter { it.examType == cardTypeFilter }
                    }
                    val filterLabel = cardTypeFilter?.displayLabel ?: "All Tests, Mid & Final Exams"

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "Select Student for Official Result Card:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allStudents, key = { it.id }) { stud ->
                                    FilterChip(
                                        selected = activeStudent.id == stud.id,
                                        onClick = { selectedStudentForCard = stud },
                                        label = { Text("${stud.fullName} (${stud.admissionNumber})") }
                                    )
                                }
                            }
                        }

                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = cardTypeFilter == null,
                                        onClick = { cardTypeFilter = null },
                                        label = { Text("All (${studentMarksAll.size})") }
                                    )
                                }
                                items(ExamType.values()) { t ->
                                    val cnt = studentMarksAll.count { it.examType == t }
                                    FilterChip(
                                        selected = cardTypeFilter == t,
                                        onClick = { cardTypeFilter = t },
                                        label = { Text("${t.shortLabel} ($cnt)") }
                                    )
                                }
                            }
                        }

                        item {
                            StudentOverallPerformanceSummaryCard(
                                student = activeStudent,
                                marks = filteredStudentMarks,
                                onPrintResultCard = {
                                    ExamReportPdfGenerator.printStudentResultCard(
                                        context = context,
                                        student = activeStudent,
                                        marks = filteredStudentMarks,
                                        filterTitle = filterLabel,
                                        settings = settings
                                    )
                                },
                                onShareResultCard = {
                                    ExamReportPdfGenerator.shareStudentResultCardPdf(
                                        context = context,
                                        student = activeStudent,
                                        marks = filteredStudentMarks,
                                        filterTitle = filterLabel,
                                        settings = settings
                                    )
                                },
                                printButtonTag = "admin_print_result_card_btn",
                                shareButtonTag = "admin_share_result_card_btn"
                            )
                        }

                        if (filteredStudentMarks.isEmpty()) {
                            item {
                                EmptyPlaceholderCard(
                                    title = "No exam marks recorded yet",
                                    subtitle = "Upload marks in 'Exams & Marks' or via the Teacher Portal to populate ${activeStudent.fullName}'s Result Card.",
                                    icon = Icons.Default.Assessment
                                )
                            }
                        } else {
                            items(filteredStudentMarks, key = { it.id }) { mark ->
                                StudentExamMarkRowCard(
                                    mark = mark,
                                    onDelete = { deletingMark = mark }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateEditExamDialog) {
        CreateOrEditExamDialog(
            classes = classes,
            existingExam = editingExam,
            onDismiss = {
                showCreateEditExamDialog = false
                editingExam = null
            },
            onSave = { title, type, subject, classId, term, date, totalMarks, passingMarks, instructions ->
                viewModel.createOrUpdateExam(
                    existingId = editingExam?.id,
                    title = title,
                    examType = type,
                    subjectName = subject,
                    classId = classId,
                    academicTerm = term,
                    examDate = date,
                    totalMarks = totalMarks,
                    passingMarks = passingMarks,
                    instructions = instructions
                ) {
                    showCreateEditExamDialog = false
                    editingExam = null
                }
            }
        )
    }

    uploadingMarksForExam?.let { exam ->
        val classStudents = allStudents.filter {
            it.classId == exam.classId && it.status != StudentStatus.WITHDRAWN
        }
        val existingMarks = marksByExam[exam.id].orEmpty()
        UploadBatchExamMarksDialog(
            exam = exam,
            studentsInClass = classStudents,
            existingMarks = existingMarks,
            onDismiss = { uploadingMarksForExam = null },
            onSaveBatch = { marksPayload, remarksMap ->
                viewModel.saveBatchExamMarks(
                    examId = exam.id,
                    studentMarks = marksPayload,
                    remarksMap = remarksMap
                ) {
                    uploadingMarksForExam = null
                }
            }
        )
    }

    deletingExam?.let { ex ->
        AlertDialog(
            onDismissRequest = { deletingExam = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Exam & All Marks?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently remove \"${ex.title}\" (${ex.className}) and all uploaded student marks for this exam.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExam(ex.id)
                        deletingExam = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_exam_btn")
                ) {
                    Text("Delete Exam")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExam = null }) { Text("Cancel") }
            }
        )
    }

    deletingMark?.let { mark ->
        AlertDialog(
            onDismissRequest = { deletingMark = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Student Mark?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove marks entry for ${mark.studentName} in ${mark.examTitle}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExamMark(mark.id)
                        deletingMark = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMark = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminExamCardItem(
    exam: AcademyExam,
    marks: List<ExamMarkRecord>,
    enrolledCount: Int,
    onUploadMarks: () -> Unit,
    onEditExam: () -> Unit,
    onDeleteExam: () -> Unit,
    onPrintReport: () -> Unit,
    onShareReport: () -> Unit,
    onDeleteMark: (ExamMarkRecord) -> Unit
) {
    var expandedMarks by remember { mutableStateOf(false) }
    val appeared = remember(marks) { marks.filter { !it.isAbsent } }
    val passedCount = remember(appeared) { appeared.count { it.obtainedMarks >= it.passingMarks } }
    val avgPct = remember(appeared, exam.totalMarks) {
        if (appeared.isEmpty()) 0.0
        else ExamGradeHelper.computePercentage(appeared.map { it.obtainedMarks }.average(), exam.totalMarks)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_card_${exam.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                }

                Row {
                    IconButton(
                        onClick = onEditExam,
                        modifier = Modifier.testTag("edit_exam_btn_${exam.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Exam", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onDeleteExam,
                        modifier = Modifier.testTag("delete_exam_btn_${exam.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Exam",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Max: ${exam.totalMarks.toInt()} (Pass: ${exam.passingMarks.toInt()})",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Marks: ${marks.size}/$enrolledCount • Pass: $passedCount • Avg: ${String.format(Locale.US, "%.0f%%", avgPct)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (exam.instructions.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: ${exam.instructions}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onUploadMarks,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_upload_marks_btn_${exam.id}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Marks", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onPrintReport,
                    modifier = Modifier.testTag("admin_print_exam_report_btn_${exam.id}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Report PDF", fontSize = 12.sp)
                }
                IconButton(
                    onClick = onShareReport,
                    modifier = Modifier.testTag("admin_share_exam_report_btn_${exam.id}")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Class Report PDF")
                }
            }

            if (marks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { expandedMarks = !expandedMarks },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (expandedMarks) "Hide Student Marks (${marks.size})" else "Show Uploaded Marks (${marks.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (expandedMarks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (expandedMarks) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        marks.sortedByDescending { it.obtainedMarks }.forEach { m ->
                            StudentExamMarkRowCard(
                                mark = m,
                                showStudentHeader = true,
                                onDelete = { onDeleteMark(m) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExamTypeBadge(examType: ExamType) {
    val (bg, fg) = when (examType) {
        ExamType.TEST -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        ExamType.MID_EXAM -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        ExamType.FINAL_EXAM -> Color(0xFFEDE9FE) to Color(0xFF5B21B6)
    }
    StatusBadge(text = examType.shortLabel.uppercase(Locale.US), containerColor = bg, contentColor = fg)
}

@Composable
fun StudentOverallPerformanceSummaryCard(
    student: Student,
    marks: List<ExamMarkRecord>,
    onPrintResultCard: () -> Unit,
    onShareResultCard: () -> Unit,
    printButtonTag: String,
    shareButtonTag: String
) {
    val appeared = remember(marks) { marks.filter { !it.isAbsent } }
    val totalObtained = remember(appeared) { appeared.sumOf { it.obtainedMarks } }
    val totalMax = remember(marks) { marks.sumOf { it.totalMarks } }
    val pct = ExamGradeHelper.computePercentage(totalObtained, totalMax)
    val passed = marks.count { !it.isAbsent && it.obtainedMarks >= it.passingMarks }
    val failed = marks.count { !it.isAbsent && it.obtainedMarks < it.passingMarks }
    val absent = marks.count { it.isAbsent }
    val overallGrade = if (marks.isEmpty()) "N/A" else ExamGradeHelper.computeGrade(
        obtainedMarks = pct,
        totalMarks = 100.0,
        passingMarks = 40.0,
        isAbsent = appeared.isEmpty() && absent > 0
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E7DD))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F5132)
                    )
                    Text(
                        text = "Adm #${student.admissionNumber} • ${student.className}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0F5132).copy(alpha = 0.85f)
                    )
                }
                StatusBadge(overallGrade, Color(0xFF0B4F32), Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Aggregate", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0F5132))
                    Text(
                        text = "${totalObtained.toInt()} / ${totalMax.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F5132)
                    )
                }
                Column {
                    Text("Percentage", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0F5132))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", pct),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F5132)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Exams (${marks.size})", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0F5132))
                    Text(
                        text = "$passed Pass • $failed Fail • $absent AB",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F5132)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPrintResultCard,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(printButtonTag),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B4F32))
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print / Save Result Card PDF", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onShareResultCard,
                    modifier = Modifier.testTag(shareButtonTag)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Result Card PDF", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun StudentExamMarkRowCard(
    mark: ExamMarkRecord,
    showStudentHeader: Boolean = false,
    onDelete: (() -> Unit)? = null
) {
    val pct = if (mark.isAbsent) 0.0 else ExamGradeHelper.computePercentage(mark.obtainedMarks, mark.totalMarks)
    val passed = !mark.isAbsent && mark.obtainedMarks >= mark.passingMarks
    val (statusLabel, badgeBg, badgeFg) = when {
        mark.isAbsent -> Triple("ABSENT", Color(0xFFFEF3C7), Color(0xFF92400E))
        passed -> Triple("PASS", Color(0xFFD1E7DD), Color(0xFF0F5132))
        else -> Triple("FAIL", Color(0xFFFEE2E2), Color(0xFF991B1B))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (showStudentHeader) {
                        Text(
                            text = "${mark.studentName} (${mark.admissionNumber})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExamTypeBadge(mark.examType)
                            Text(
                                text = mark.examDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mark.examTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        if (mark.subjectName.isNotBlank()) {
                            Text(
                                text = "${mark.subjectName} • Term ${mark.academicTerm}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusBadge(statusLabel, badgeBg, badgeFg)
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("delete_mark_btn_${mark.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Mark",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mark.isAbsent) {
                        "Score: Absent / ${mark.totalMarks.toInt()}"
                    } else {
                        "Score: ${mark.obtainedMarks.toInt()} / ${mark.totalMarks.toInt()} (${String.format(Locale.US, "%.0f%%", pct)})"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Grade: ${mark.grade}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (mark.remarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Remarks: ${mark.remarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateOrEditExamDialog(
    classes: List<AcademyClass>,
    existingExam: AcademyExam?,
    onDismiss: () -> Unit,
    onSave: (String, ExamType, String, String, String, String, Double, Double, String) -> Unit
) {
    var title by remember { mutableStateOf(existingExam?.title ?: "") }
    var examType by remember { mutableStateOf(existingExam?.examType ?: ExamType.TEST) }
    var subject by remember { mutableStateOf(existingExam?.subjectName ?: "Hifz / Nazra & Tajweed") }
    var selectedClassId by remember { mutableStateOf(existingExam?.classId ?: classes.firstOrNull()?.id.orEmpty()) }
    var academicTerm by remember { mutableStateOf(existingExam?.academicTerm ?: "2026-2027") }
    var examDate by remember {
        mutableStateOf(existingExam?.examDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var totalMarksStr by remember { mutableStateOf((existingExam?.totalMarks?.toInt() ?: 100).toString()) }
    var passingMarksStr by remember { mutableStateOf((existingExam?.passingMarks?.toInt() ?: 40).toString()) }
    var instructions by remember { mutableStateOf(existingExam?.instructions ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (existingExam == null) "Schedule New Exam / Test" else "Edit Exam Details",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; errorMsg = null },
                        label = { Text("Exam Title (e.g., Monthly Tajweed Test 1) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exam_title_input")
                    )
                }

                item {
                    Text("Exam Category *", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ExamType.values()) { t ->
                            FilterChip(
                                selected = examType == t,
                                onClick = { examType = t },
                                label = { Text(t.shortLabel) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject / Syllabus *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text("Select Target Class / Halaqah *", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(classes, key = { it.id }) { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = examDate,
                            onValueChange = { examDate = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = academicTerm,
                            onValueChange = { academicTerm = it },
                            label = { Text("Academic Term") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = totalMarksStr,
                            onValueChange = { totalMarksStr = it; errorMsg = null },
                            label = { Text("Total Marks") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exam_total_marks_input")
                        )
                        OutlinedTextField(
                            value = passingMarksStr,
                            onValueChange = { passingMarksStr = it; errorMsg = null },
                            label = { Text("Passing Marks") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exam_passing_marks_input")
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Instructions / Notes (Optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMsg != null) {
                    item {
                        Text(errorMsg!!, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val total = totalMarksStr.toDoubleOrNull()
                                val passing = passingMarksStr.toDoubleOrNull()
                                when {
                                    title.isBlank() -> errorMsg = "Exam title is required."
                                    selectedClassId.isBlank() -> errorMsg = "Please select a class."
                                    total == null || total < 1.0 || total > 1000.0 -> errorMsg = "Total marks must be 1 to 1000."
                                    passing == null || passing < 0.0 || passing > total -> errorMsg = "Passing marks must be 0 to $total."
                                    else -> onSave(
                                        title.trim(),
                                        examType,
                                        subject.trim(),
                                        selectedClassId,
                                        academicTerm.trim(),
                                        examDate.trim(),
                                        total,
                                        passing,
                                        instructions.trim()
                                    )
                                }
                            },
                            modifier = Modifier.testTag("save_exam_btn")
                        ) {
                            Text(if (existingExam == null) "Schedule Exam" else "Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadBatchExamMarksDialog(
    exam: AcademyExam,
    studentsInClass: List<Student>,
    existingMarks: List<ExamMarkRecord>,
    onDismiss: () -> Unit,
    onSaveBatch: (List<Triple<String, Double, Boolean>>, Map<String, String>) -> Unit
) {
    val existingMap = remember(existingMarks) { existingMarks.associateBy { it.studentId } }
    val scoreInputs = remember(exam.id, studentsInClass, existingMarks) {
        mutableStateMapOf<String, String>().apply {
            studentsInClass.forEach { stud ->
                val prev = existingMap[stud.id]
                put(
                    stud.id,
                    if (prev == null || prev.isAbsent) "" else {
                        if (prev.obtainedMarks % 1.0 == 0.0) prev.obtainedMarks.toInt().toString()
                        else prev.obtainedMarks.toString()
                    }
                )
            }
        }
    }
    val absentFlags = remember(exam.id, studentsInClass, existingMarks) {
        mutableStateMapOf<String, Boolean>().apply {
            studentsInClass.forEach { stud ->
                put(stud.id, existingMap[stud.id]?.isAbsent ?: false)
            }
        }
    }
    val remarksInputs = remember(exam.id, studentsInClass, existingMarks) {
        mutableStateMapOf<String, String>().apply {
            studentsInClass.forEach { stud ->
                put(stud.id, existingMap[stud.id]?.remarks ?: "")
            }
        }
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Upload Exam Marks",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${exam.title} (${exam.examType.shortLabel}) • ${exam.className} • Max: ${exam.totalMarks.toInt()} (Pass: ${exam.passingMarks.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (studentsInClass.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No active students enrolled in ${exam.className} yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(studentsInClass, key = { it.id }) { stud ->
                            val isAbsent = absentFlags[stud.id] ?: false
                            val rawScore = scoreInputs[stud.id].orEmpty()
                            val parsedScore = rawScore.toDoubleOrNull()
                            val previewGrade = when {
                                isAbsent -> "AB (Absent)"
                                parsedScore != null -> ExamGradeHelper.computeGrade(
                                    obtainedMarks = parsedScore.coerceIn(0.0, exam.totalMarks),
                                    totalMarks = exam.totalMarks,
                                    passingMarks = exam.passingMarks,
                                    isAbsent = false
                                )
                                else -> null
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stud.fullName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Adm #${stud.admissionNumber}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (previewGrade != null) {
                                            StatusBadge(
                                                text = previewGrade,
                                                containerColor = if (isAbsent) Color(0xFFFEF3C7) else Color(0xFFD1E7DD),
                                                contentColor = if (isAbsent) Color(0xFF92400E) else Color(0xFF0F5132)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = rawScore,
                                            onValueChange = {
                                                scoreInputs[stud.id] = it
                                                validationError = null
                                            },
                                            enabled = !isAbsent,
                                            label = { Text("Marks / ${exam.totalMarks.toInt()}") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("marks_input_${stud.id}")
                                        )
                                        FilterChip(
                                            selected = isAbsent,
                                            onClick = {
                                                absentFlags[stud.id] = !isAbsent
                                                validationError = null
                                            },
                                            label = { Text("Absent", fontSize = 11.sp) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = remarksInputs[stud.id].orEmpty(),
                                        onValueChange = { remarksInputs[stud.id] = it },
                                        label = { Text("Remarks (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = validationError!!,
                        color = Color(0xFFDC2626),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = studentsInClass.isNotEmpty(),
                        onClick = {
                            val entries = mutableListOf<Triple<String, Double, Boolean>>()
                            for (stud in studentsInClass) {
                                val isAbs = absentFlags[stud.id] ?: false
                                val raw = scoreInputs[stud.id].orEmpty().trim()
                                if (isAbs) {
                                    entries.add(Triple(stud.id, 0.0, true))
                                } else if (raw.isNotEmpty()) {
                                    val num = raw.toDoubleOrNull()
                                    if (num == null || num < 0.0 || num > exam.totalMarks) {
                                        validationError = "Invalid marks for ${stud.fullName}: enter 0 to ${exam.totalMarks.toInt()}."
                                        return@Button
                                    }
                                    entries.add(Triple(stud.id, num, false))
                                }
                            }
                            if (entries.isEmpty()) {
                                validationError = "Enter marks or mark Absent for at least 1 student."
                                return@Button
                            }
                            onSaveBatch(entries, remarksInputs.toMap())
                        },
                        modifier = Modifier.testTag("save_batch_marks_btn")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Upload Marks")
                    }
                }
            }
        }
    }
}
