package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.AcademySettings
import com.example.data.models.Student
import com.example.util.CertificatePrintHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IssueCourseCertificateDialog(
    student: Student,
    onDismiss: () -> Unit,
    onConfirmIssue: (
        completedCourse: String,
        completionDate: String,
        certificateNumber: String,
        finalGrade: String,
        teacherNameOverride: String,
        certificateRemarks: String
    ) -> Unit
) {
    val defaultCourse = remember(student) {
        student.completedCourse.ifBlank {
            when {
                student.quranLevel.contains("Hifz", ignoreCase = true) -> "Hifz-ul-Quran Al-Kareem (Complete 30 Juz)"
                student.quranLevel.contains("Nazra", ignoreCase = true) -> "Nazra Quran Majeed with Tajweed"
                student.quranLevel.contains("Qaida", ignoreCase = true) -> "Noorani Qaida & Makharij Foundation"
                else -> "Tajweed & Qira'at Course"
            }
        }
    }

    var completedCourse by remember { mutableStateOf(defaultCourse) }
    var completionDate by remember {
        mutableStateOf(
            student.completionDate.ifBlank {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            }
        )
    }
    var certificateNumber by remember {
        mutableStateOf(CertificatePrintHelper.buildDefaultCertificateSerial(student))
    }
    var finalGrade by remember {
        mutableStateOf(student.finalGrade.ifBlank { "Mumtaz (A+ Distinction)" })
    }
    var teacherName by remember {
        mutableStateOf(student.teacherName)
    }
    var remarks by remember {
        mutableStateOf(
            student.certificateRemarks.ifBlank {
                "Completed with Exemplary Tajweed, Discipline & Adab."
            }
        )
    }

    val presetCourses = listOf(
        "Hifz-ul-Quran Al-Kareem (Complete 30 Juz)",
        "Nazra Quran Majeed with Tajweed",
        "Noorani Qaida & Makharij Foundation",
        "Tajweed & Qira'at Course"
    )
    val presetGrades = listOf(
        "Mumtaz (A+ Distinction)",
        "Jayyid Jiddan (Very Good)",
        "Jayyid (Good)"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(30.dp)
                    )
                    Column {
                        Text(
                            text = "Issue Course Completion Sanad",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${student.fullName} (${student.admissionNumber})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select or Customize Completed Course:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetCourses) { preset ->
                        FilterChip(
                            selected = completedCourse == preset,
                            onClick = { completedCourse = preset },
                            label = { Text(preset.substringBefore(" (")) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = completedCourse,
                    onValueChange = { completedCourse = it },
                    label = { Text("Certificate Course Title *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("certificate_course_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Final Grade / Distinction:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetGrades) { gradeOpt ->
                        FilterChip(
                            selected = finalGrade == gradeOpt,
                            onClick = { finalGrade = gradeOpt },
                            label = { Text(gradeOpt) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = completionDate,
                        onValueChange = { completionDate = it },
                        label = { Text("Completion Date") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("certificate_date_input")
                    )
                    OutlinedTextField(
                        value = certificateNumber,
                        onValueChange = { certificateNumber = it },
                        label = { Text("Sanad Serial #") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("certificate_serial_input")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Supervising Ustadh / Qari Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Citation / Special Remarks on Certificate") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirmIssue(
                                completedCourse.trim(),
                                completionDate.trim(),
                                certificateNumber.trim(),
                                finalGrade.trim(),
                                teacherName.trim(),
                                remarks.trim()
                            )
                        },
                        enabled = completedCourse.isNotBlank(),
                        modifier = Modifier.testTag("confirm_issue_certificate_btn")
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Complete & Generate Sanad")
                    }
                }
            }
        }
    }
}

@Composable
fun CertificatePreviewAndPrintDialog(
    student: Student,
    settings: AcademySettings = AcademySettings(),
    onEditDetails: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewBitmap = remember(
        student.id,
        student.fullName,
        student.guardianName,
        student.completedCourse,
        student.completionDate,
        student.certificateNumber,
        student.finalGrade,
        student.teacherName,
        student.certificateRemarks
    ) {
        CertificatePrintHelper.renderCertificateBitmap(student, settings, scale = 2f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 680.dp)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color(0xFFB45309)
                        )
                        Column {
                            Text(
                                text = "Sanad-e-Takmeel • Print Ready (A4)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${student.fullName} • ${CertificatePrintHelper.buildDefaultCertificateSerial(student)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Certificate Preview")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Crisp A4 Landscape Preview Card
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Course Completion Certificate for ${student.fullName}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, Color(0xFFC59B27), RoundedCornerShape(10.dp))
                        .testTag("certificate_preview_image")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        CertificatePrintHelper.printCertificate(context, student, settings)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0B4F32),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("print_certificate_now_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Print Certificate (Direct A4 Printout)",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            CertificatePrintHelper.shareCertificatePdf(context, student, settings)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_certificate_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share / Save PDF")
                    }

                    if (onEditDetails != null) {
                        OutlinedButton(
                            onClick = onEditDetails,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("edit_certificate_details_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Sanad Info")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeOrResetAdminCredentialsDialog(
    currentUsername: String = "",
    isRecoveryMode: Boolean,
    googleEmail: String?,
    onDismiss: () -> Unit,
    onSubmit: (newUsername: String, newPassword: String, onDone: (Boolean, String?) -> Unit) -> Unit
) {
    var newUsername by remember {
        mutableStateOf(if (currentUsername.isNotBlank()) currentUsername else "admin")
    }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (isRecoveryMode) {
                                "Reset / Set Admin Login"
                            } else {
                                "Change Admin Username & Password"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isRecoveryMode) {
                                "Verified Academy Owner (${googleEmail ?: "Google Account"})"
                            } else {
                                "First-time default: admin / Admin@123 • Apni marzi se tabdeel karein"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                AssistChip(
                    onClick = {
                        newUsername = "admin"
                        newPassword = "Admin@123"
                        confirmPassword = "Admin@123"
                        errorMsg = null
                    },
                    label = { Text("Fill Default (admin / Admin@123)") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("fill_default_admin_chip")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it; errorMsg = null },
                    label = { Text("New Admin Username *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_new_username_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; errorMsg = null },
                    label = { Text("New Admin Password *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide Password" else "Show Password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_new_password_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = null },
                    label = { Text("Confirm New Password *") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_confirm_password_input")
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = Color(0xFF991B1B),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanU = newUsername.trim()
                            val cleanP = newPassword.trim()
                            val cleanC = confirmPassword.trim()
                            when {
                                cleanU.length < 3 -> {
                                    errorMsg = "Username kam az kam 3 characters ka hona chahiye."
                                }
                                cleanP.length < 4 -> {
                                    errorMsg = "Password kam az kam 4 characters ka hona chahiye."
                                }
                                cleanP != cleanC -> {
                                    errorMsg = "Dono passwords match nahi kar rahe."
                                }
                                else -> {
                                    isSaving = true
                                    onSubmit(cleanU, cleanP) { ok, err ->
                                        isSaving = false
                                        if (ok) {
                                            onDismiss()
                                        } else {
                                            errorMsg = err ?: "Could not update Admin credentials."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("confirm_save_admin_credentials_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Text(if (isRecoveryMode) "Save & Sign In Now" else "Update Admin Login")
                        }
                    }
                }
            }
        }
    }
}
