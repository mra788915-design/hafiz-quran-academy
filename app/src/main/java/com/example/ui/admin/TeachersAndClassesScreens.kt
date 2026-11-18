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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.AcademyClass
import com.example.data.models.Teacher
import com.example.data.models.UserAccount
import com.example.data.models.UserRole
import com.example.ui.AcademyViewModel
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.StatusBadge
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TeachersScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val teachers by viewModel.teachers.collectAsState(initial = emptyList())
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val accounts by viewModel.allUserAccounts.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTeacher by remember { mutableStateOf<Teacher?>(null) }
    var deletingTeacher by remember { mutableStateOf<Teacher?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add Teacher") },
                modifier = Modifier.testTag("add_teacher_fab")
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
                "Academy Asatizah & Teachers",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Certified Huffaz, Quraa & Islamic scholars — Edit or Remove anytime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (teachers.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No teachers registered",
                    subtitle = "Tap 'Add Teacher' to register academy instructors and assign Halaqahs.",
                    icon = Icons.Default.School
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(teachers, key = { it.id }) { teacher ->
                        val assignedIds = remember(teacher.assignedClassIds, classes, teacher.id) {
                            val explicit = teacher.assignedClassIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val viaClass = classes.filter { it.assignedTeacherId == teacher.id }.map { it.id }
                            (explicit + viaClass).distinct()
                        }
                        val assignedNames = remember(assignedIds, classes, teacher.assignedClasses) {
                            val resolved = classes.filter { it.id in assignedIds }.joinToString(", ") { it.name }
                            resolved.ifBlank { teacher.assignedClasses.ifBlank { "None" } }
                        }
                        val linkedAccount = remember(accounts, teacher.id) {
                            accounts.firstOrNull { it.role == UserRole.TEACHER && it.teacherId == teacher.id }
                        }

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
                                            teacher.fullName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            teacher.contactNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (teacher.isActive) {
                                        StatusBadge("ACTIVE", Color(0xFFD1E7DD), Color(0xFF0F5132))
                                    } else {
                                        StatusBadge("INACTIVE", Color(0xFFE5E7EB), Color(0xFF374151))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    "Assigned Halaqahs: $assignedNames",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (linkedAccount != null) {
                                    Text(
                                        "Portal Username: @${linkedAccount.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (teacher.email.isNotBlank()) {
                                    Text("Email: ${teacher.email}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (teacher.notes.isNotBlank()) {
                                    Text(
                                        "Specialization: ${teacher.notes}",
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
                                        onClick = { editingTeacher = teacher },
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("edit_teacher_${teacher.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Teacher", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { deletingTeacher = teacher },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("delete_teacher_${teacher.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Teacher", modifier = Modifier.size(16.dp))
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

    if (showAddDialog) {
        TeacherFormDialog(
            existingTeacher = null,
            existingAccount = null,
            classes = classes,
            onDismiss = { showAddDialog = false },
            onSubmit = { name, phone, email, address, assignedNames, assignedIdsCsv, joining, isActive, notes, tUsername, tPassword ->
                viewModel.addTeacher(
                    fullName = name,
                    contactNumber = phone,
                    email = email,
                    address = address,
                    assignedClasses = assignedNames,
                    assignedClassIds = assignedIdsCsv,
                    joiningDate = joining,
                    notes = notes,
                    teacherUsername = tUsername,
                    teacherPassword = tPassword
                ) {
                    showAddDialog = false
                }
            }
        )
    }

    editingTeacher?.let { teacher ->
        val linkedAccount = accounts.firstOrNull { it.role == UserRole.TEACHER && it.teacherId == teacher.id }
        TeacherFormDialog(
            existingTeacher = teacher,
            existingAccount = linkedAccount,
            classes = classes,
            onDismiss = { editingTeacher = null },
            onSubmit = { name, phone, email, address, assignedNames, assignedIdsCsv, joining, isActive, notes, tUsername, tPassword ->
                viewModel.updateTeacherDetails(
                    teacher = teacher.copy(
                        fullName = name,
                        contactNumber = phone,
                        email = email,
                        address = address,
                        assignedClasses = assignedNames,
                        assignedClassIds = assignedIdsCsv,
                        joiningDate = joining,
                        isActive = isActive,
                        notes = notes
                    ),
                    assignedClassIdsCsv = assignedIdsCsv,
                    teacherUsername = tUsername,
                    teacherPassword = tPassword
                ) {
                    editingTeacher = null
                }
            }
        )
    }

    deletingTeacher?.let { teacher ->
        AlertDialog(
            onDismissRequest = { deletingTeacher = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Teacher?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Removing ${teacher.fullName} will delete their instructor profile, revoke their Teacher App login, and unassign their Halaqahs (students remain safely enrolled in their classes)."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTeacher(teacher.id)
                        deletingTeacher = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_teacher_btn")
                ) {
                    Text("Remove Teacher")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTeacher = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TeacherFormDialog(
    existingTeacher: Teacher?,
    existingAccount: UserAccount?,
    classes: List<AcademyClass>,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        phone: String,
        email: String,
        address: String,
        assignedNames: String,
        assignedIdsCsv: String,
        joining: String,
        isActive: Boolean,
        notes: String,
        teacherUsername: String,
        teacherPassword: String
    ) -> Unit
) {
    val isEditing = existingTeacher != null
    var name by remember { mutableStateOf(existingTeacher?.fullName ?: "") }
    var phone by remember { mutableStateOf(existingTeacher?.contactNumber ?: "") }
    var email by remember { mutableStateOf(existingTeacher?.email ?: "") }
    var address by remember { mutableStateOf(existingTeacher?.address ?: "") }
    var notes by remember { mutableStateOf(existingTeacher?.notes ?: "") }
    var joiningDate by remember {
        mutableStateOf(existingTeacher?.joiningDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var isActive by remember { mutableStateOf(existingTeacher?.isActive ?: true) }

    val selectedClassIds = remember(existingTeacher, classes) {
        val initialIds = buildSet {
            existingTeacher?.assignedClassIds
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.let { addAll(it) }
            if (existingTeacher != null) {
                classes.filter { it.assignedTeacherId == existingTeacher.id }.forEach { add(it.id) }
            }
        }
        mutableStateListOf<String>().apply { addAll(initialIds) }
    }

    var teacherUsername by remember { mutableStateOf(existingAccount?.username ?: "") }
    var teacherPassword by remember {
        mutableStateOf(
            existingAccount?.password ?: if (!isEditing) "Tchr@" + (1000..9999).random() else ""
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
                Text(
                    if (isEditing) "Edit Teacher Details" else "Register New Teacher",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Select assigned Halaqahs below so students immediately appear in Teacher Portal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!isEditing && teacherUsername.isBlank() && it.length >= 3) {
                            teacherUsername = "t." + it.lowercase(Locale.US).replace("[^a-z0-9]".toRegex(), "").take(8)
                        }
                    },
                    label = { Text("Teacher Name (with title) *") },
                    placeholder = { Text("e.g. Qari Abdullah") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Contact Phone *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = joiningDate,
                        onValueChange = { joiningDate = it },
                        label = { Text("Joining Date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Residential Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Certification / Specialization") },
                    placeholder = { Text("e.g. Hafiz, Tajweed certified") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEditing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isActive = !isActive }
                    ) {
                        Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                        Text("Active Instructor Status", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Assign Classes / Halaqahs:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (classes.isEmpty()) {
                    Text(
                        "No classes created yet. You can create classes in the Classes tab and assign them anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    classes.forEach { cls ->
                        val checked = cls.id in selectedClassIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (checked) selectedClassIds.remove(cls.id) else selectedClassIds.add(cls.id)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (cls.id !in selectedClassIds) selectedClassIds.add(cls.id)
                                    } else {
                                        selectedClassIds.remove(cls.id)
                                    }
                                }
                            )
                            Column {
                                Text(cls.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                if (cls.assignedTeacherName.isNotBlank() && cls.assignedTeacherId != existingTeacher?.id) {
                                    Text(
                                        "Currently: ${cls.assignedTeacherName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Teacher Portal Login (Admin Only)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = teacherUsername,
                        onValueChange = { teacherUsername = it },
                        label = { Text(if (isEditing) "Username" else "Username *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = teacherPassword,
                        onValueChange = { teacherPassword = it },
                        label = { Text(if (isEditing) "Password" else "Password *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                val credentialsValid = if (isEditing) {
                    (teacherUsername.isBlank() && teacherPassword.isBlank()) ||
                            (teacherUsername.length >= 3 && teacherPassword.length >= 4)
                } else {
                    teacherUsername.length >= 3 && teacherPassword.length >= 4
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val assignedNames = classes
                                .filter { it.id in selectedClassIds }
                                .joinToString(", ") { it.name }
                            val assignedIdsCsv = selectedClassIds.joinToString(",")
                            onSubmit(
                                name,
                                phone,
                                email,
                                address,
                                assignedNames,
                                assignedIdsCsv,
                                joiningDate,
                                isActive,
                                notes,
                                teacherUsername,
                                teacherPassword
                            )
                        },
                        enabled = name.isNotBlank() && phone.isNotBlank() && credentialsValid
                    ) {
                        Text(if (isEditing) "Save Changes" else "Add Teacher")
                    }
                }
            }
        }
    }
}

@Composable
fun ClassesScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val teachers by viewModel.teachers.collectAsState(initial = emptyList())
    val students by viewModel.activeStudents.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<AcademyClass?>(null) }
    var deletingClass by remember { mutableStateOf<AcademyClass?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Class") },
                modifier = Modifier.testTag("add_class_fab")
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
                "Class Halaqahs & Sections",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Manage Halaqah groups, room assignments & teachers — Edit or Remove anytime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (classes.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No classes created",
                    subtitle = "Tap 'Add Class' to setup new Halaqah groups.",
                    icon = Icons.Default.MeetingRoom
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(classes, key = { it.id }) { cls ->
                        val enrolledCount = remember(students, cls.id) {
                            students.count { it.classId == cls.id }
                        }

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
                                            cls.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "${cls.section} Section • ${cls.roomNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusBadge(
                                        "$enrolledCount / ${cls.capacity} Students",
                                        Color(0xFFD1E7DD),
                                        Color(0xFF0F5132)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    "Teacher: ${cls.assignedTeacherName.ifBlank { "Unassigned" }}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Schedule: ${cls.scheduleInfo}", style = MaterialTheme.typography.bodySmall)

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    OutlinedButton(
                                        onClick = { editingClass = cls },
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("edit_class_${cls.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Class", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { deletingClass = cls },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("delete_class_${cls.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Class", modifier = Modifier.size(16.dp))
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

    if (showAddDialog) {
        ClassFormDialog(
            existingClass = null,
            teachers = teachers,
            onDismiss = { showAddDialog = false },
            onSubmit = { name, sec, room, cap, tId, tName, sch, _ ->
                viewModel.addClass(name, sec, room, cap, tId, tName, sch) {
                    showAddDialog = false
                }
            }
        )
    }

    editingClass?.let { cls ->
        ClassFormDialog(
            existingClass = cls,
            teachers = teachers,
            onDismiss = { editingClass = null },
            onSubmit = { name, sec, room, cap, tId, tName, sch, isActive ->
                viewModel.updateClassDetails(
                    cls.copy(
                        name = name,
                        section = sec,
                        roomNumber = room,
                        capacity = cap,
                        assignedTeacherId = tId,
                        assignedTeacherName = tName,
                        scheduleInfo = sch,
                        isActive = isActive
                    )
                ) {
                    editingClass = null
                }
            }
        )
    }

    deletingClass?.let { cls ->
        AlertDialog(
            onDismissRequest = { deletingClass = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Class / Halaqah?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Removing '${cls.name}' will delete this class from the Academy. Any students currently in this class will be safely marked as 'Unassigned' so you can move them to another Halaqah."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClass(cls.id)
                        deletingClass = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_class_btn")
                ) {
                    Text("Remove Class")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingClass = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassFormDialog(
    existingClass: AcademyClass?,
    teachers: List<Teacher>,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        section: String,
        room: String,
        cap: Int,
        teacherId: String,
        teacherName: String,
        schedule: String,
        isActive: Boolean
    ) -> Unit
) {
    val isEditing = existingClass != null
    var name by remember { mutableStateOf(existingClass?.name ?: "") }
    var section by remember { mutableStateOf(existingClass?.section ?: "Morning") }
    var room by remember { mutableStateOf(existingClass?.roomNumber ?: "Hall A") }
    var capacityStr by remember { mutableStateOf((existingClass?.capacity ?: 20).toString()) }
    var schedule by remember { mutableStateOf(existingClass?.scheduleInfo ?: "Mon-Sat: 07:30 AM - 12:30 PM") }
    var isActive by remember { mutableStateOf(existingClass?.isActive ?: true) }

    var selectedTeacher by remember(existingClass, teachers) {
        mutableStateOf(
            if (existingClass != null) {
                teachers.firstOrNull { it.id == existingClass.assignedTeacherId }
            } else {
                teachers.firstOrNull()
            }
        )
    }
    var teacherExpanded by remember { mutableStateOf(false) }

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
                Text(
                    if (isEditing) "Edit Class / Halaqah" else "Create New Class / Halaqah",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Class / Halaqah Name *") },
                    placeholder = { Text("e.g. Hifz Halaqah 3") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Section") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Room / Hall") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = capacityStr,
                    onValueChange = { capacityStr = it },
                    label = { Text("Student Capacity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = teacherExpanded,
                    onExpandedChange = { teacherExpanded = !teacherExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedTeacher?.fullName ?: "Unassigned / No Teacher",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assigned Teacher") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teacherExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = teacherExpanded,
                        onDismissRequest = { teacherExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unassigned / No Teacher") },
                            onClick = {
                                selectedTeacher = null
                                teacherExpanded = false
                            }
                        )
                        teachers.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.fullName) },
                                onClick = {
                                    selectedTeacher = t
                                    teacherExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("Timetable Schedule") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEditing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isActive = !isActive }
                    ) {
                        Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                        Text("Class is Active", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cap = (capacityStr.toIntOrNull() ?: 20).coerceIn(1, 200)
                            onSubmit(
                                name,
                                section.ifBlank { "Morning" },
                                room.ifBlank { "Hall A" },
                                cap,
                                selectedTeacher?.id ?: "",
                                selectedTeacher?.fullName ?: "",
                                schedule,
                                isActive
                            )
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text(if (isEditing) "Save Changes" else "Save Class")
                    }
                }
            }
        }
    }
}
