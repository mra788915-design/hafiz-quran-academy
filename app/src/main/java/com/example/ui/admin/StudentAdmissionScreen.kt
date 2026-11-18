package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.*
import com.example.ui.AcademyViewModel
import com.example.ui.components.CertificatePreviewAndPrintDialog
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.IssueCourseCertificateDialog
import com.example.ui.components.StatusBadge
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAdmissionScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val allStudents by viewModel.students.collectAsState(initial = emptyList())
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val teachers by viewModel.teachers.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<StudentStatus?>(null) }

    var showAdmissionDialog by remember { mutableStateOf(false) }
    var selectedStudentForDetail by remember { mutableStateOf<Student?>(null) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var studentPendingWithdraw by remember { mutableStateOf<Student?>(null) }
    var studentForIssueSanad by remember { mutableStateOf<Student?>(null) }
    var studentForSanadPreview by remember { mutableStateOf<Student?>(null) }

    val filteredStudents = remember(allStudents, searchQuery, selectedClassFilter, selectedStatusFilter) {
        allStudents.filter { student ->
            val matchesQuery = student.fullName.contains(searchQuery, ignoreCase = true) ||
                    student.admissionNumber.contains(searchQuery, ignoreCase = true) ||
                    student.guardianName.contains(searchQuery, ignoreCase = true)
            val matchesClass = selectedClassFilter == null || student.classId == selectedClassFilter
            val matchesStatus = selectedStatusFilter == null || student.status == selectedStatusFilter
            matchesQuery && matchesClass && matchesStatus
        }
    }

    val hasUnassigned = remember(allStudents) { allStudents.any { it.classId == "unassigned_class" } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdmissionDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("New Admission") },
                modifier = Modifier.testTag("new_admission_fab")
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

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_search_input"),
                placeholder = { Text("Search by student name, admission #, guardian...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedClassFilter == null && selectedStatusFilter == null,
                        onClick = {
                            selectedClassFilter = null
                            selectedStatusFilter = null
                        },
                        label = { Text("All (${allStudents.size})") }
                    )
                }
                items(classes) { cls ->
                    FilterChip(
                        selected = selectedClassFilter == cls.id,
                        onClick = {
                            selectedClassFilter = if (selectedClassFilter == cls.id) null else cls.id
                        },
                        label = { Text(cls.name) }
                    )
                }
                if (hasUnassigned) {
                    item {
                        FilterChip(
                            selected = selectedClassFilter == "unassigned_class",
                            onClick = {
                                selectedClassFilter = if (selectedClassFilter == "unassigned_class") null else "unassigned_class"
                            },
                            label = { Text("Unassigned") }
                        )
                    }
                }
                item {
                    FilterChip(
                        selected = selectedStatusFilter == StudentStatus.ACTIVE,
                        onClick = {
                            selectedStatusFilter = if (selectedStatusFilter == StudentStatus.ACTIVE) null else StudentStatus.ACTIVE
                        },
                        label = { Text("Active") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatusFilter == StudentStatus.COMPLETED,
                        onClick = {
                            selectedStatusFilter = if (selectedStatusFilter == StudentStatus.COMPLETED) null else StudentStatus.COMPLETED
                        },
                        label = { Text("Completed / Sanad") },
                        modifier = Modifier.testTag("filter_completed_students_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatusFilter == StudentStatus.INACTIVE,
                        onClick = {
                            selectedStatusFilter = if (selectedStatusFilter == StudentStatus.INACTIVE) null else StudentStatus.INACTIVE
                        },
                        label = { Text("Inactive") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredStudents.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No students found",
                    subtitle = if (searchQuery.isNotEmpty()) "Try adjusting your search criteria" else "Tap 'New Admission' to enroll students.",
                    icon = Icons.Default.School
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        StudentItemCard(
                            student = student,
                            onClick = { selectedStudentForDetail = student },
                            onEditClick = { editingStudent = student },
                            onRemoveClick = { studentPendingWithdraw = student },
                            onCompleteCourseClick = { studentForIssueSanad = student },
                            onPrintSanadClick = { studentForSanadPreview = student }
                        )
                    }
                }
            }
        }
    }

    if (showAdmissionDialog) {
        StudentAdmissionDialog(
            classes = classes,
            teachers = teachers,
            onDismiss = { showAdmissionDialog = false },
            onSubmit = { name, guardian, dob, admDate, gender, phone, altPhone, addr, clsId, clsName, tId, tName, qLevel, fee, feeCat, isFs, notes, pUsername, pPassword ->
                viewModel.admitStudent(
                    fullName = name,
                    guardianName = guardian,
                    dateOfBirth = dob,
                    admissionDate = admDate,
                    gender = gender,
                    contactNumber = phone,
                    alternateContact = altPhone,
                    address = addr,
                    classId = clsId,
                    className = clsName,
                    teacherId = tId,
                    teacherName = tName,
                    quranLevel = qLevel,
                    monthlyFee = fee,
                    feeCategory = feeCat,
                    isFsEligible = isFs,
                    notes = notes,
                    parentUsername = pUsername,
                    parentPassword = pPassword
                ) {
                    showAdmissionDialog = false
                }
            }
        )
    }

    selectedStudentForDetail?.let { student ->
        StudentProfileDialog(
            student = student,
            onDismiss = { selectedStudentForDetail = null },
            onEditRequest = {
                selectedStudentForDetail = null
                editingStudent = student
            },
            onIssueOrViewCertificate = {
                selectedStudentForDetail = null
                if (student.status == StudentStatus.COMPLETED) {
                    studentForSanadPreview = student
                } else {
                    studentForIssueSanad = student
                }
            },
            onStatusChange = { newStatus ->
                viewModel.setStudentStatus(student.id, newStatus)
                selectedStudentForDetail = null
            }
        )
    }

    studentForIssueSanad?.let { student ->
        IssueCourseCertificateDialog(
            student = student,
            onDismiss = { studentForIssueSanad = null },
            onConfirmIssue = { course, date, serialNo, grade, ustadh, remarks ->
                viewModel.completeStudentCourseAndIssueCertificate(
                    studentId = student.id,
                    completedCourse = course,
                    completionDate = date,
                    certificateNumber = serialNo,
                    finalGrade = grade,
                    teacherNameOverride = ustadh,
                    certificateRemarks = remarks
                ) { updatedStudent ->
                    studentForIssueSanad = null
                    studentForSanadPreview = updatedStudent
                }
            }
        )
    }

    studentForSanadPreview?.let { student ->
        CertificatePreviewAndPrintDialog(
            student = student,
            settings = viewModel.repository.settings,
            onEditDetails = {
                val target = student
                studentForSanadPreview = null
                studentForIssueSanad = target
            },
            onDismiss = { studentForSanadPreview = null }
        )
    }

    editingStudent?.let { student ->
        EditStudentDialog(
            student = student,
            classes = classes,
            teachers = teachers,
            onDismiss = { editingStudent = null },
            onSave = { updated ->
                viewModel.updateStudent(updated) {
                    editingStudent = null
                }
            }
        )
    }

    studentPendingWithdraw?.let { student ->
        AlertDialog(
            onDismissRequest = { studentPendingWithdraw = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Withdraw & Remove Permanently?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Withdrawing ${student.fullName} (${student.admissionNumber}) will permanently delete this student's admission record, attendance, progress, fee challans, and unlinked parent access from Cloud."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.withdrawStudentPermanently(student.id)
                        studentPendingWithdraw = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_card_withdraw_btn")
                ) {
                    Text("Withdraw & Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentPendingWithdraw = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StudentItemCard(
    student: Student,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onCompleteCourseClick: () -> Unit = {},
    onPrintSanadClick: () -> Unit = {}
) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.fullName.take(1).uppercase(Locale.US),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        if (student.isFsEligible) {
                            StatusBadge("FS", Color(0xFFFEF3C7), Color(0xFF92400E))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Adm #${student.admissionNumber} • S/o ${student.guardianName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${student.className} • ${student.quranLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Rs. ${formatter.format(student.monthlyFee)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    when (student.status) {
                        StudentStatus.ACTIVE -> StatusBadge("ACTIVE", Color(0xFFD1E7DD), Color(0xFF0F5132))
                        StudentStatus.COMPLETED -> StatusBadge("COMPLETED • SANAD", Color(0xFFFEF3C7), Color(0xFF92400E))
                        StudentStatus.INACTIVE -> StatusBadge("INACTIVE", Color(0xFFE5E7EB), Color(0xFF374151))
                        StudentStatus.WITHDRAWN -> StatusBadge("WITHDRAWN", Color(0xFFFEE2E2), Color(0xFF991B1B))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (student.status == StudentStatus.COMPLETED) {
                    Button(
                        onClick = onPrintSanadClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0B4F32),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("print_sanad_${student.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print Certificate", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print Sanad", fontSize = 12.sp)
                    }
                } else if (student.status == StudentStatus.ACTIVE) {
                    OutlinedButton(
                        onClick = onCompleteCourseClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309)),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("complete_course_${student.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = "Complete Course & Sanad", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Complete & Sanad", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("edit_student_${student.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Student", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onRemoveClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("remove_student_${student.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Student", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAdmissionDialog(
    classes: List<AcademyClass>,
    teachers: List<Teacher>,
    onDismiss: () -> Unit,
    onSubmit: (
        fullName: String,
        guardianName: String,
        dob: String,
        admDate: String,
        gender: String,
        phone: String,
        altPhone: String,
        address: String,
        classId: String,
        className: String,
        teacherId: String,
        teacherName: String,
        quranLevel: String,
        monthlyFee: Double,
        feeCategory: String,
        isFsEligible: Boolean,
        notes: String,
        parentUsername: String,
        parentPassword: String
    ) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var guardianName by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var admDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var gender by remember { mutableStateOf("Male") }
    var phone by remember { mutableStateOf("") }
    var altPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    var selectedClass by remember { mutableStateOf(classes.firstOrNull()) }
    var quranLevel by remember { mutableStateOf("Hifz") }
    var monthlyFeeStr by remember { mutableStateOf("4500") }
    var feeCategory by remember { mutableStateOf("Regular") }
    var isFsEligible by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var parentUsername by remember { mutableStateOf("") }
    var parentPassword by remember { mutableStateOf("Prnt@" + (1000..9999).random()) }

    var classExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }

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
                Text(
                    text = "New Student Admission",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Enroll student and issue Admin-managed Parent Portal credentials",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Student Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = guardianName,
                    onValueChange = {
                        guardianName = it
                        if (parentUsername.isBlank() && it.length >= 3) {
                            parentUsername = "p." + it.lowercase(Locale.US).replace("[^a-z0-9]".toRegex(), "").take(8)
                        }
                    },
                    label = { Text("Father / Guardian Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Primary Phone *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = { Text("DOB (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Residential Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = classExpanded,
                    onExpandedChange = { classExpanded = !classExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedClass?.name ?: "Unassigned",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assigned Class / Halaqah") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = classExpanded,
                        onDismissRequest = { classExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unassigned") },
                            onClick = {
                                selectedClass = null
                                classExpanded = false
                            }
                        )
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

                Spacer(modifier = Modifier.height(8.dp))

                val levels = listOf("Hifz", "Nazra with Tajweed", "Qaida Foundation", "Tarteel & Qira'at")
                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { levelExpanded = !levelExpanded }
                ) {
                    OutlinedTextField(
                        value = quranLevel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Quran Learning Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false }
                    ) {
                        levels.forEach { lvl ->
                            DropdownMenuItem(
                                text = { Text(lvl) },
                                onClick = {
                                    quranLevel = lvl
                                    levelExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = monthlyFeeStr,
                    onValueChange = { monthlyFeeStr = it },
                    label = { Text("Monthly Fee (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isFsEligible = !isFsEligible }
                ) {
                    Checkbox(
                        checked = isFsEligible,
                        onCheckedChange = { isFsEligible = it }
                    )
                    Text(
                        text = "Candidate for Fee Sabilillah (FS) Assistance",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Parent Portal Login (Issued Exclusively by Admin)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = parentUsername,
                        onValueChange = { parentUsername = it },
                        label = { Text("Parent Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = parentPassword,
                        onValueChange = { parentPassword = it },
                        label = { Text("Parent Password") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val feeVal = monthlyFeeStr.toDoubleOrNull() ?: 4500.0
                            val cls = selectedClass
                            val assignedTeacher = teachers.firstOrNull { it.id == cls?.assignedTeacherId }
                            onSubmit(
                                fullName,
                                guardianName,
                                dob,
                                admDate,
                                gender,
                                phone,
                                altPhone,
                                address,
                                cls?.id ?: "unassigned_class",
                                cls?.name ?: "Unassigned",
                                assignedTeacher?.id ?: cls?.assignedTeacherId.orEmpty(),
                                assignedTeacher?.fullName ?: cls?.assignedTeacherName.orEmpty(),
                                quranLevel,
                                feeVal,
                                feeCategory,
                                isFsEligible,
                                notes,
                                parentUsername,
                                parentPassword
                            )
                        },
                        enabled = fullName.isNotBlank() && guardianName.isNotBlank() && phone.isNotBlank()
                    ) {
                        Text("Admit Student")
                    }
                }
            }
        }
    }
}
