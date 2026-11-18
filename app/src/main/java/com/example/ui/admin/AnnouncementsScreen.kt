package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.Announcement
import com.example.ui.AcademyViewModel
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.StatusBadge
import java.util.Locale

@Composable
fun AnnouncementsScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val announcements by viewModel.announcements.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAnnouncement by remember { mutableStateOf<Announcement?>(null) }
    var deletingAnnouncement by remember { mutableStateOf<Announcement?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                text = { Text("Post Notice") },
                modifier = Modifier.testTag("post_notice_fab")
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
                "Announcements & Academy Calendar",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Broadcast live notices to Parents & Teachers — Edit or Remove anytime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (announcements.isEmpty()) {
                EmptyPlaceholderCard(
                    title = "No announcements posted",
                    subtitle = "Tap 'Post Notice' to broadcast notices to parents and teachers.",
                    icon = Icons.Default.Campaign
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(announcements, key = { it.id }) { ann ->
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
                                    Text(
                                        text = ann.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusBadge(
                                            ann.targetRole.uppercase(Locale.US),
                                            Color(0xFFEDE9FE),
                                            Color(0xFF5B21B6)
                                        )
                                        when (ann.priority.lowercase(Locale.US)) {
                                            "urgent" -> StatusBadge("URGENT", Color(0xFFFEE2E2), Color(0xFF991B1B))
                                            "high" -> StatusBadge("HIGH", Color(0xFFFEF3C7), Color(0xFF92400E))
                                            else -> StatusBadge("NORMAL", Color(0xFFD1E7DD), Color(0xFF0F5132))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${ann.date} • ${ann.authorName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = ann.content,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                ) {
                                    OutlinedButton(
                                        onClick = { editingAnnouncement = ann },
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("edit_announcement_${ann.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Notice", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { deletingAnnouncement = ann },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("delete_announcement_${ann.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Notice", modifier = Modifier.size(16.dp))
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

    if (showCreateDialog) {
        AnnouncementFormDialog(
            existingAnnouncement = null,
            onDismiss = { showCreateDialog = false },
            onSubmit = { title, content, role, priority ->
                viewModel.postAnnouncement(title, content, role, priority) {
                    showCreateDialog = false
                }
            }
        )
    }

    editingAnnouncement?.let { ann ->
        AnnouncementFormDialog(
            existingAnnouncement = ann,
            onDismiss = { editingAnnouncement = null },
            onSubmit = { title, content, role, priority ->
                viewModel.updateAnnouncement(
                    ann.copy(
                        title = title,
                        content = content,
                        targetRole = role,
                        priority = priority
                    )
                ) {
                    editingAnnouncement = null
                }
            }
        )
    }

    deletingAnnouncement?.let { ann ->
        AlertDialog(
            onDismissRequest = { deletingAnnouncement = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Remove Announcement?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove '${ann.title}' from all connected Teacher and Parent apps?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnnouncement(ann.id)
                        deletingAnnouncement = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_announcement_btn")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAnnouncement = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AnnouncementFormDialog(
    existingAnnouncement: Announcement?,
    onDismiss: () -> Unit,
    onSubmit: (title: String, content: String, role: String, priority: String) -> Unit
) {
    val isEditing = existingAnnouncement != null
    var title by remember { mutableStateOf(existingAnnouncement?.title ?: "") }
    var content by remember { mutableStateOf(existingAnnouncement?.content ?: "") }
    var targetRole by remember { mutableStateOf(existingAnnouncement?.targetRole ?: "All") }
    var priority by remember { mutableStateOf(existingAnnouncement?.priority ?: "Normal") }

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
                    if (isEditing) "Edit Announcement" else "Publish Announcement",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Message Content *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Target Audience:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Teachers", "Parents").forEach { r ->
                        FilterChip(
                            selected = targetRole == r,
                            onClick = { targetRole = r },
                            label = { Text(r) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Priority:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Normal", "High", "Urgent").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(title, content, targetRole, priority) },
                        enabled = title.isNotBlank() && content.isNotBlank()
                    ) {
                        Text(if (isEditing) "Save Changes" else "Publish")
                    }
                }
            }
        }
    }
}
