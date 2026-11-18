package com.example.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.AcademyClass
import com.example.data.models.Student
import com.example.data.models.UserAccount
import com.example.data.models.UserRole
import com.example.ui.AcademyViewModel
import com.example.ui.components.ChangeOrResetAdminCredentialsDialog
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.StatusBadge

@Composable
fun PortalCredentialsScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val accounts by viewModel.allUserAccounts.collectAsState(initial = emptyList())
    val teachers by viewModel.teachers.collectAsState(initial = emptyList())
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val students by viewModel.students.collectAsState(initial = emptyList())

    if (!currentUser.isAuthenticated || currentUser.role != UserRole.ADMIN) {
        EmptyPlaceholderCard(
            title = "Restricted to Administration",
            subtitle = "Teacher and Parent login usernames and passwords are confidential to Academy Admin only.",
            icon = Icons.Default.Lock
        )
        return
    }

    var roleFilter by remember { mutableStateOf<UserRole?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateOrEditDialog by remember { mutableStateOf(false) }
    var showChangeAdminDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<UserAccount?>(null) }
    var deletingAccount by remember { mutableStateOf<UserAccount?>(null) }
    val primaryAdminAccount = remember(accounts) { accounts.firstOrNull { it.role == UserRole.ADMIN } }

    val filteredAccounts = remember(accounts, roleFilter, searchQuery) {
        accounts.filter { acc ->
            val matchesRole = roleFilter == null || acc.role == roleFilter
            val matchesQuery = acc.name.contains(searchQuery, ignoreCase = true) ||
                    acc.username.contains(searchQuery, ignoreCase = true) ||
                    acc.phone.contains(searchQuery, ignoreCase = true)
            matchesRole && matchesQuery
        }
    }

    val teacherCount = remember(accounts) { accounts.count { it.role == UserRole.TEACHER } }
    val parentCount = remember(accounts) { accounts.count { it.role == UserRole.PARENT } }

    fun formatShareMessage(account: UserAccount): String {
        val dedicatedAppName = when (account.role) {
            UserRole.ADMIN -> "HQA Admin App"
            UserRole.TEACHER -> "HQA Teacher App"
            UserRole.PARENT -> "HQA Parent App"
        }
        return buildString {
            appendLine("Assalam-o-Alaikum!")
            appendLine("*Hafiz Quran Academy — $dedicatedAppName Login*")
            appendLine("Account: ${account.name}")
            appendLine("Username: ${account.username}")
            appendLine("Password: ${account.password}")
            appendLine()
            append("Apne phone par Hafiz Quran Academy app open karein, Google se connect karein aur yeh Username & Password darj karein. Apke phone par sirf $dedicatedAppName open hogi aur live sync rahegi.")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingAccount = null
                    showCreateOrEditDialog = true
                },
                icon = { Icon(Icons.Default.Key, contentDescription = null) },
                text = { Text("Issue New Login") },
                modifier = Modifier.testTag("issue_login_fab")
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Admin-Only Cloud Login Vault",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Teachers ($teacherCount) & Parents ($parentCount) ke logins Edit, Reset ya Remove karein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { showChangeAdminDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("change_admin_own_login_btn")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Change Admin Login (@${primaryAdminAccount?.username ?: currentUser.username.ifBlank { "admin" }})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search teacher/parent name or username...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("credentials_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = roleFilter == null,
                        onClick = { roleFilter = null },
                        label = { Text("All (${accounts.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = roleFilter == UserRole.TEACHER,
                        onClick = { roleFilter = if (roleFilter == UserRole.TEACHER) null else UserRole.TEACHER },
                        label = { Text("Teachers ($teacherCount)") }
                    )
                }
                item {
                    FilterChip(
                        selected = roleFilter == UserRole.PARENT,
                        onClick = { roleFilter = if (roleFilter == UserRole.PARENT) null else UserRole.PARENT },
                        label = { Text("Parents ($parentCount)") }
                    )
                }
                item {
                    FilterChip(
                        selected = roleFilter == UserRole.ADMIN,
                        onClick = { roleFilter = if (roleFilter == UserRole.ADMIN) null else UserRole.ADMIN },
                        label = { Text("Admin") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredAccounts.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No login accounts found",
                    subtitle = "Tap 'Issue New Login' to create Teacher or Parent Cloud login credentials.",
                    icon = Icons.Default.Key
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredAccounts, key = { it.id }) { account ->
                        CredentialVaultCard(
                            account = account,
                            classes = classes,
                            students = students,
                            onEdit = {
                                if (account.role == UserRole.ADMIN) {
                                    showChangeAdminDialog = true
                                } else {
                                    editingAccount = account
                                    showCreateOrEditDialog = true
                                }
                            },
                            onDelete = if (account.role != UserRole.ADMIN) {
                                { deletingAccount = account }
                            } else null,
                            onToggleActive = { active ->
                                viewModel.toggleUserAccountActive(account.id, active)
                            },
                            onCopyCredentials = {
                                val shareText = formatShareMessage(account)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Portal Login", shareText))
                                Toast.makeText(context, "Copied credentials for ${account.name}", Toast.LENGTH_SHORT).show()
                            },
                            onShareCredentials = {
                                val shareText = formatShareMessage(account)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Hafiz Quran Academy Login")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Send Login via WhatsApp / SMS"))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateOrEditDialog) {
        IssueOrEditCredentialDialog(
            existingAccount = editingAccount,
            teachers = teachers,
            classes = classes,
            students = students,
            onDismiss = { showCreateOrEditDialog = false },
            onSave = { id, name, username, password, role, phone, email, teacherId, classIds, studentIds, active ->
                viewModel.savePortalCredentials(
                    existingId = id,
                    name = name,
                    username = username,
                    password = password,
                    role = role,
                    phone = phone,
                    email = email,
                    teacherId = teacherId,
                    assignedClassIds = classIds,
                    linkedStudentIds = studentIds,
                    isActive = active
                ) {
                    showCreateOrEditDialog = false
                }
            }
        )
    }

    if (showChangeAdminDialog) {
        ChangeOrResetAdminCredentialsDialog(
            currentUsername = primaryAdminAccount?.username ?: currentUser.username.ifBlank { "admin" },
            isRecoveryMode = false,
            googleEmail = viewModel.firebaseEmail,
            onDismiss = { showChangeAdminDialog = false },
            onSubmit = { newU, newP, onDone ->
                viewModel.changeAdminOwnCredentials(
                    newUsername = newU,
                    newPassword = newP,
                    displayName = primaryAdminAccount?.name ?: currentUser.name,
                    onResult = onDone
                )
            }
        )
    }

    deletingAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Login Account?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Permanently remove login @${acc.username} for ${acc.name}? Any device signed in with this login will lose access.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUserAccount(acc.id)
                        deletingAccount = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_credential_btn")
                ) {
                    Text("Remove Login")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccount = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CredentialVaultCard(
    account: UserAccount,
    classes: List<AcademyClass>,
    students: List<Student>,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onToggleActive: (Boolean) -> Unit,
    onCopyCredentials: () -> Unit,
    onShareCredentials: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }

    val linkedStudentNames = remember(account.linkedStudentIds, students) {
        val ids = account.linkedStudentIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
        students.filter { it.id in ids }.joinToString(", ") { "${it.fullName} (${it.admissionNumber})" }
    }

    val linkedClassNames = remember(account.assignedClassIds, account.teacherId, classes) {
        val ids = account.assignedClassIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        classes.filter { it.id in ids || (account.teacherId.isNotBlank() && it.assignedTeacherId == account.teacherId) }
            .joinToString(", ") { it.name }
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (account.phone.isNotBlank()) {
                        Text(
                            text = "Phone: ${account.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (account.role) {
                        UserRole.ADMIN -> StatusBadge("ADMIN APP", Color(0xFFD1E7DD), Color(0xFF0F5132))
                        UserRole.TEACHER -> StatusBadge("TEACHER APP", Color(0xFFEDE9FE), Color(0xFF5B21B6))
                        UserRole.PARENT -> StatusBadge("PARENT APP", Color(0xFFFEF3C7), Color(0xFF92400E))
                    }
                    if (!account.isActive) {
                        StatusBadge("SUSPENDED", Color(0xFFFEE2E2), Color(0xFF991B1B))
                    }
                }
            }

            if (account.role == UserRole.TEACHER) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Assigned Halaqahs: ${linkedClassNames.ifBlank { "None" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (account.role == UserRole.PARENT && linkedStudentNames.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Linked Children: $linkedStudentNames",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Username: ${account.username}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = "Password: ${if (showPassword) account.password else "••••••••"}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = if (showPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Row {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide Password" else "Show Password"
                            )
                        }
                        IconButton(onClick = onCopyCredentials) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Username and Password"
                            )
                        }
                        IconButton(
                            onClick = onShareCredentials,
                            modifier = Modifier.testTag("share_credential_${account.username}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Send Login via WhatsApp or SMS",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (account.role != UserRole.ADMIN) {
                    TextButton(
                        onClick = { onToggleActive(!account.isActive) }
                    ) {
                        Text(
                            text = if (account.isActive) "Suspend" else "Activate",
                            color = if (account.isActive) MaterialTheme.colorScheme.error else Color(0xFF15803D)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("edit_credential_${account.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp)
                    }
                    if (onDelete != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("delete_credential_${account.id}"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
