package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.AcademyClass
import com.example.data.models.Student
import com.example.data.models.Teacher
import com.example.data.models.UserAccount
import com.example.data.models.UserRole
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueOrEditCredentialDialog(
    existingAccount: UserAccount?,
    teachers: List<Teacher>,
    classes: List<AcademyClass>,
    students: List<Student>,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        name: String,
        username: String,
        password: String,
        role: UserRole,
        phone: String,
        email: String,
        teacherId: String,
        assignedClassIds: String,
        linkedStudentIds: String,
        isActive: Boolean
    ) -> Unit
) {
    var selectedRole by remember { mutableStateOf(existingAccount?.role ?: UserRole.PARENT) }
    var name by remember { mutableStateOf(existingAccount?.name ?: "") }
    var username by remember { mutableStateOf(existingAccount?.username ?: "") }
    var password by remember { mutableStateOf(existingAccount?.password ?: "") }
    var phone by remember { mutableStateOf(existingAccount?.phone ?: "") }
    var email by remember { mutableStateOf(existingAccount?.email ?: "") }

    var selectedTeacherId by remember { mutableStateOf(existingAccount?.teacherId ?: "") }

    val selectedClassIds = remember(existingAccount, classes) {
        val initial = buildSet {
            existingAccount?.assignedClassIds
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.let { addAll(it) }
            if (existingAccount != null && existingAccount.teacherId.isNotBlank()) {
                classes.filter { it.assignedTeacherId == existingAccount.teacherId }.forEach { add(it.id) }
            }
        }
        mutableStateListOf<String>().apply { addAll(initial) }
    }

    val selectedStudentIds = remember {
        mutableStateListOf<String>().apply {
            existingAccount?.linkedStudentIds
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.let { addAll(it) }
        }
    }

    var teacherMenuExpanded by remember { mutableStateOf(false) }

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
                    text = if (existingAccount == null) "Issue Cloud App Login" else "Update Login Credentials",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Only Admin holds and distributes Teacher & Parent credentials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (existingAccount == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(UserRole.PARENT, UserRole.TEACHER).forEach { r ->
                            FilterChip(
                                selected = selectedRole == r,
                                onClick = { selectedRole = r },
                                label = { Text("${r.name} APP") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selectedRole == UserRole.TEACHER && teachers.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = teacherMenuExpanded,
                        onExpandedChange = { teacherMenuExpanded = !teacherMenuExpanded }
                    ) {
                        val currentTeacher = teachers.firstOrNull { it.id == selectedTeacherId }
                        OutlinedTextField(
                            value = currentTeacher?.fullName ?: "Select Registered Teacher",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Link to Registered Teacher") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teacherMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = teacherMenuExpanded,
                            onDismissRequest = { teacherMenuExpanded = false }
                        ) {
                            teachers.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.fullName) },
                                    onClick = {
                                        selectedTeacherId = t.id
                                        selectedClassIds.clear()
                                        t.assignedClassIds.split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                            .forEach { selectedClassIds.add(it) }
                                        classes.filter { it.assignedTeacherId == t.id }.forEach {
                                            if (it.id !in selectedClassIds) selectedClassIds.add(it.id)
                                        }
                                        name = t.fullName
                                        phone = t.contactNumber
                                        email = t.email
                                        if (username.isBlank()) {
                                            username = "ustadh." + t.fullName.lowercase(Locale.US)
                                                .replace("[^a-z0-9]".toRegex(), "")
                                                .take(8)
                                        }
                                        teacherMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Holder Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Login Username *") },
                    placeholder = { Text("e.g. p.ahmed or qari.usman") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Login Password *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val prefix = when (selectedRole) {
                                UserRole.ADMIN -> "Admin@"
                                UserRole.TEACHER -> "Tchr@"
                                UserRole.PARENT -> "Prnt@"
                            }
                            password = prefix + (1000..9999).random()
                        }
                    ) {
                        Text("Generate", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedRole == UserRole.TEACHER && classes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Assigned Classes / Halaqahs:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    classes.forEach { cls ->
                        val isChecked = cls.id in selectedClassIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isChecked) selectedClassIds.remove(cls.id) else selectedClassIds.add(cls.id)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (cls.id !in selectedClassIds) selectedClassIds.add(cls.id)
                                    } else {
                                        selectedClassIds.remove(cls.id)
                                    }
                                }
                            )
                            Text(
                                text = cls.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (selectedRole == UserRole.PARENT && students.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Link Children to This Parent Account:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    students.forEach { stud ->
                        val isChecked = stud.id in selectedStudentIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isChecked) selectedStudentIds.remove(stud.id)
                                    else {
                                        selectedStudentIds.add(stud.id)
                                        if (name.isBlank()) name = "${stud.guardianName} (Parent)"
                                        if (phone.isBlank()) phone = stud.contactNumber
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedStudentIds.add(stud.id)
                                        if (name.isBlank()) name = "${stud.guardianName} (Parent)"
                                        if (phone.isBlank()) phone = stud.contactNumber
                                    } else {
                                        selectedStudentIds.remove(stud.id)
                                    }
                                }
                            )
                            Text(
                                text = "${stud.fullName} (${stud.admissionNumber})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                existingAccount?.id,
                                name,
                                username,
                                password,
                                selectedRole,
                                phone,
                                email,
                                selectedTeacherId,
                                selectedClassIds.joinToString(","),
                                selectedStudentIds.joinToString(","),
                                existingAccount?.isActive ?: true
                            )
                        },
                        enabled = name.isNotBlank() && username.length >= 3 && password.length >= 4
                    ) {
                        Text("Save & Sync Cloud")
                    }
                }
            }
        }
    }
}
