package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.QuranProgressRating
import com.example.data.models.QuranProgressRecord
import com.example.data.models.Student
import com.example.ui.components.StatusBadge
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun QuranProgressCardItem(
    record: QuranProgressRecord,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
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
                        text = record.studentName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Adm #${record.admissionNumber} • ${record.date} • Juz ${record.juzNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (record.sabaqRating) {
                    QuranProgressRating.EXCELLENT -> StatusBadge("EXCELLENT", Color(0xFFD1E7DD), Color(0xFF0F5132))
                    QuranProgressRating.VERY_GOOD -> StatusBadge("VERY GOOD", Color(0xFFD1E7DD), Color(0xFF0F5132))
                    QuranProgressRating.GOOD -> StatusBadge("GOOD", Color(0xFFFEF3C7), Color(0xFF92400E))
                    QuranProgressRating.AVERAGE -> StatusBadge("AVERAGE", Color(0xFFFFF3CD), Color(0xFF856404))
                    QuranProgressRating.NEEDS_REVISION -> StatusBadge("REVISE", Color(0xFFF8D7DA), Color(0xFF842029))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sabaq: ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "${record.surahName} (Ayat ${record.sabaqAyatStart} - ${record.sabaqAyatEnd})",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            if (record.sabqiSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Sabqi (Recent): ${record.sabqiSummary}", style = MaterialTheme.typography.bodySmall)
            }

            if (record.manzilSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Manzil (Daur): ${record.manzilSummary}", style = MaterialTheme.typography.bodySmall)
            }

            if (record.teacherRemarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Remarks: \"${record.teacherRemarks}\"",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.nextTarget.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Next Target: ${record.nextTarget}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (onEdit != null || onDelete != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    onEdit?.let { editCb ->
                        OutlinedButton(
                            onClick = editCb,
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("edit_progress_${record.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Lesson", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp)
                        }
                    }
                    onDelete?.let { deleteCb ->
                        OutlinedButton(
                            onClick = deleteCb,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("delete_progress_${record.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Lesson", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordQuranLessonDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSubmit: (
        studentId: String,
        date: String,
        surah: String,
        surahNumber: Int,
        juz: Int,
        startAyat: Int,
        endAyat: Int,
        rating: QuranProgressRating,
        sabqi: String,
        manzil: String,
        mistakes: Int,
        remarks: String,
        nextTarget: String
    ) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var surahName by remember { mutableStateOf("Surah Al-Baqarah") }
    var juzNumberStr by remember { mutableStateOf("1") }
    var startAyatStr by remember { mutableStateOf("1") }
    var endAyatStr by remember { mutableStateOf("10") }
    var selectedRating by remember { mutableStateOf(QuranProgressRating.GOOD) }
    var sabqiSummary by remember { mutableStateOf("") }
    var manzilSummary by remember { mutableStateOf("") }
    var mistakesStr by remember { mutableStateOf("0") }
    var remarks by remember { mutableStateOf("") }
    var nextTarget by remember { mutableStateOf("") }

    var studentExpanded by remember { mutableStateOf(false) }

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
                Text("Record Daily Quran Lesson", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

                Spacer(modifier = Modifier.height(14.dp))

                ExposedDropdownMenuBox(
                    expanded = studentExpanded,
                    onExpandedChange = { studentExpanded = !studentExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedStudent?.fullName ?: "Select Student",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Student") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = studentExpanded,
                        onDismissRequest = { studentExpanded = false }
                    ) {
                        students.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.fullName} (${s.admissionNumber})") },
                                onClick = { selectedStudent = s; studentExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Lesson Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = surahName,
                        onValueChange = { surahName = it },
                        label = { Text("Surah Name") },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = juzNumberStr,
                        onValueChange = { juzNumberStr = it },
                        label = { Text("Juz (1-30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startAyatStr,
                        onValueChange = { startAyatStr = it },
                        label = { Text("Start Ayat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endAyatStr,
                        onValueChange = { endAyatStr = it },
                        label = { Text("End Ayat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Recitation Quality Rating:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(QuranProgressRating.values().toList()) { r ->
                        FilterChip(
                            selected = selectedRating == r,
                            onClick = { selectedRating = r },
                            label = { Text(r.name.replace("_", " "), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = sabqiSummary,
                    onValueChange = { sabqiSummary = it },
                    label = { Text("Sabqi (Recent Revision)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manzilSummary,
                    onValueChange = { manzilSummary = it },
                    label = { Text("Manzil / Daur (Older Revision)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Teacher Remarks / Tajweed Feedback") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nextTarget,
                    onValueChange = { nextTarget = it },
                    label = { Text("Next Lesson Target") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val stud = selectedStudent ?: return@Button
                            val juzVal = (juzNumberStr.toIntOrNull() ?: 1).coerceIn(1, 30)
                            val startVal = (startAyatStr.toIntOrNull() ?: 1).coerceIn(1, 300)
                            val endVal = (endAyatStr.toIntOrNull() ?: 10).coerceIn(1, 300)
                            val mistVal = (mistakesStr.toIntOrNull() ?: 0).coerceIn(0, 200)
                            onSubmit(stud.id, date, surahName, 1, juzVal, startVal, endVal, selectedRating, sabqiSummary, manzilSummary, mistVal, remarks, nextTarget)
                        },
                        enabled = selectedStudent != null && surahName.isNotBlank()
                    ) {
                        Text("Save Lesson Record")
                    }
                }
            }
        }
    }
}

@Composable
fun EditQuranLessonDialog(
    record: QuranProgressRecord,
    onDismiss: () -> Unit,
    onSave: (QuranProgressRecord) -> Unit
) {
    var date by remember { mutableStateOf(record.date) }
    var surahName by remember { mutableStateOf(record.surahName) }
    var juzNumberStr by remember { mutableStateOf(record.juzNumber.toString()) }
    var startAyatStr by remember { mutableStateOf(record.sabaqAyatStart.toString()) }
    var endAyatStr by remember { mutableStateOf(record.sabaqAyatEnd.toString()) }
    var selectedRating by remember { mutableStateOf(record.sabaqRating) }
    var sabqiSummary by remember { mutableStateOf(record.sabqiSummary) }
    var manzilSummary by remember { mutableStateOf(record.manzilSummary) }
    var mistakesStr by remember { mutableStateOf(record.mistakesCount.toString()) }
    var remarks by remember { mutableStateOf(record.teacherRemarks) }
    var nextTarget by remember { mutableStateOf(record.nextTarget) }

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
                Text("Edit Quran Lesson Log", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${record.studentName} (${record.admissionNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mistakesStr,
                        onValueChange = { mistakesStr = it },
                        label = { Text("Mistakes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = surahName,
                        onValueChange = { surahName = it },
                        label = { Text("Surah Name *") },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = juzNumberStr,
                        onValueChange = { juzNumberStr = it },
                        label = { Text("Juz (1-30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startAyatStr,
                        onValueChange = { startAyatStr = it },
                        label = { Text("Start Ayat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endAyatStr,
                        onValueChange = { endAyatStr = it },
                        label = { Text("End Ayat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Recitation Quality Rating:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(QuranProgressRating.values().toList()) { r ->
                        FilterChip(
                            selected = selectedRating == r,
                            onClick = { selectedRating = r },
                            label = { Text(r.name.replace("_", " "), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = sabqiSummary,
                    onValueChange = { sabqiSummary = it },
                    label = { Text("Sabqi (Recent Revision)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manzilSummary,
                    onValueChange = { manzilSummary = it },
                    label = { Text("Manzil / Daur") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Teacher Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nextTarget,
                    onValueChange = { nextTarget = it },
                    label = { Text("Next Lesson Target") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                record.copy(
                                    date = date.ifBlank { record.date },
                                    surahName = surahName.trim(),
                                    juzNumber = (juzNumberStr.toIntOrNull() ?: record.juzNumber).coerceIn(1, 30),
                                    sabaqAyatStart = (startAyatStr.toIntOrNull() ?: record.sabaqAyatStart).coerceIn(1, 300),
                                    sabaqAyatEnd = (endAyatStr.toIntOrNull() ?: record.sabaqAyatEnd).coerceIn(1, 300),
                                    sabaqRating = selectedRating,
                                    sabqiSummary = sabqiSummary,
                                    manzilSummary = manzilSummary,
                                    mistakesCount = (mistakesStr.toIntOrNull() ?: record.mistakesCount).coerceIn(0, 200),
                                    teacherRemarks = remarks,
                                    nextTarget = nextTarget
                                )
                            )
                        },
                        enabled = surahName.isNotBlank()
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
