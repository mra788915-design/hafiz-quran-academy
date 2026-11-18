package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.models.AcademyClass
import com.example.data.models.Student
import com.example.data.models.StudentStatus
import com.example.data.models.Teacher
import com.example.ui.components.StatusBadge
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StudentProfileDialog(
    student: Student,
    onDismiss: () -> Unit,
    onEditRequest: () -> Unit,
    onIssueOrViewCertificate: () -> Unit = {},
    onStatusChange: (StudentStatus) -> Unit
) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    var showWithdrawConfirm by remember { mutableStateOf(false) }

    if (showWithdrawConfirm) {
        AlertDialog(
            onDismissRequest = { showWithdrawConfirm = false },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFDC2626)
                )
            },
            title = {
                Text(
                    "Withdraw & Remove Permanently?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Withdrawing ${student.fullName} (${student.admissionNumber}) will permanently delete this student's admission record, attendance, progress, fee challans, and unlinked parent access from the academy."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWithdrawConfirm = false
                        onStatusChange(StudentStatus.WITHDRAWN)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_permanent_withdraw_btn")
                ) {
                    Text("Withdraw & Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Admission #${student.admissionNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    when (student.status) {
                        StudentStatus.ACTIVE -> StatusBadge("ACTIVE", Color(0xFFD1E7DD), Color(0xFF0F5132))
                        StudentStatus.COMPLETED -> StatusBadge("COMPLETED • SANAD", Color(0xFFFEF3C7), Color(0xFF92400E))
                        StudentStatus.INACTIVE -> StatusBadge("INACTIVE", Color(0xFFE5E7EB), Color(0xFF374151))
                        StudentStatus.WITHDRAWN -> StatusBadge("WITHDRAWN", Color(0xFFFEE2E2), Color(0xFF991B1B))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                ProfileFieldRow("Father/Guardian:", student.guardianName)
                ProfileFieldRow("Contact Phone:", student.contactNumber)
                if (student.alternateContact.isNotBlank()) {
                    ProfileFieldRow("Alternate Phone:", student.alternateContact)
                }
                ProfileFieldRow("Class / Halaqah:", student.className)
                ProfileFieldRow("Assigned Teacher:", student.teacherName.ifBlank { "Not assigned" })
                ProfileFieldRow("Quran Level:", student.quranLevel)
                ProfileFieldRow("Monthly Tuition:", "Rs. ${formatter.format(student.monthlyFee)}")
                ProfileFieldRow("Fee Category:", student.feeCategory)
                ProfileFieldRow("Fee Sabilillah (FS):", if (student.isFsEligible) "Eligible (${student.fsStatus})" else "Regular")
                ProfileFieldRow("Residential Address:", student.address.ifBlank { "Main Academy Campus" })
                ProfileFieldRow("Admission Date:", student.admissionDate)
                if (student.status == StudentStatus.COMPLETED || student.certificateNumber.isNotBlank()) {
                    ProfileFieldRow("Completed Course:", student.completedCourse.ifBlank { student.quranLevel })
                    ProfileFieldRow("Sanad Serial #:", student.certificateNumber.ifBlank { "Issued" })
                    ProfileFieldRow("Final Grade:", student.finalGrade.ifBlank { "Mumtaz" })
                    ProfileFieldRow("Completion Date:", student.completionDate.ifBlank { "-" })
                }

                if (student.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Notes: ${student.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onIssueOrViewCertificate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0B4F32),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_certificate_action_btn")
                ) {
                    Icon(
                        imageVector = if (student.status == StudentStatus.COMPLETED) Icons.Default.Print else Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (student.status == StudentStatus.COMPLETED) {
                            "View & Print Certificate (A4 Sanad)"
                        } else {
                            "Complete Course & Issue Printable Certificate"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onEditRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_edit_student_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Student Details & Class")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Update Enrollment Status",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (student.status != StudentStatus.ACTIVE) {
                        OutlinedButton(
                            onClick = { onStatusChange(StudentStatus.ACTIVE) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Activate")
                        }
                    }
                    if (student.status != StudentStatus.INACTIVE) {
                        OutlinedButton(
                            onClick = { onStatusChange(StudentStatus.INACTIVE) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Deactivate")
                        }
                    }
                    OutlinedButton(
                        onClick = { showWithdrawConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("withdraw_student_btn")
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Withdraw")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStudentDialog(
    student: Student,
    classes: List<AcademyClass>,
    teachers: List<Teacher>,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    var fullName by remember { mutableStateOf(student.fullName) }
    var guardianName by remember { mutableStateOf(student.guardianName) }
    var dob by remember { mutableStateOf(student.dateOfBirth) }
    var gender by remember { mutableStateOf(student.gender) }
    var phone by remember { mutableStateOf(student.contactNumber) }
    var altPhone by remember { mutableStateOf(student.alternateContact) }
    var address by remember { mutableStateOf(student.address) }
    var selectedClass by remember {
        mutableStateOf(classes.firstOrNull { it.id == student.classId })
    }
    var quranLevel by remember { mutableStateOf(student.quranLevel) }
    var monthlyFeeStr by remember { mutableStateOf(student.monthlyFee.toInt().toString()) }
    var feeCategory by remember { mutableStateOf(student.feeCategory) }
    var isFsEligible by remember { mutableStateOf(student.isFsEligible) }
    var status by remember { mutableStateOf(student.status) }
    var notes by remember { mutableStateOf(student.notes) }

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
                    text = "Edit Student Profile",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Admission #${student.admissionNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                    onValueChange = { guardianName = it },
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
                        value = altPhone,
                        onValueChange = { altPhone = it },
                        label = { Text("Alt Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = { Text("DOB (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = { Text("Gender") },
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

                Spacer(modifier = Modifier.height(10.dp))

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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyFeeStr,
                        onValueChange = { monthlyFeeStr = it },
                        label = { Text("Monthly Fee (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = feeCategory,
                        onValueChange = { feeCategory = it },
                        label = { Text("Fee Category") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(StudentStatus.ACTIVE, StudentStatus.COMPLETED, StudentStatus.INACTIVE).forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.name) }
                        )
                    }
                }

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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Admin Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

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
                            val feeVal = monthlyFeeStr.toDoubleOrNull() ?: student.monthlyFee
                            val cls = selectedClass
                            val assignedTeacher = teachers.firstOrNull { it.id == cls?.assignedTeacherId }
                            onSave(
                                student.copy(
                                    fullName = fullName.trim(),
                                    guardianName = guardianName.trim(),
                                    dateOfBirth = dob.trim(),
                                    gender = gender.ifBlank { "Male" },
                                    contactNumber = phone.trim(),
                                    alternateContact = altPhone.trim(),
                                    address = address.trim(),
                                    classId = cls?.id ?: "unassigned_class",
                                    className = cls?.name ?: "Unassigned",
                                    teacherId = cls?.assignedTeacherId ?: assignedTeacher?.id.orEmpty(),
                                    teacherName = cls?.assignedTeacherName ?: assignedTeacher?.fullName.orEmpty(),
                                    quranLevel = quranLevel,
                                    monthlyFee = feeVal,
                                    feeCategory = feeCategory.ifBlank { "Regular" },
                                    isFsEligible = isFsEligible,
                                    fsStatus = if (!isFsEligible) "None" else student.fsStatus.ifBlank { "Pending" },
                                    status = status,
                                    notes = notes.trim()
                                )
                            )
                        },
                        enabled = fullName.isNotBlank() && guardianName.isNotBlank() && phone.isNotBlank()
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
