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
import com.example.data.models.MadniQaidaLogRecord
import com.example.data.models.NamazLogRecord
import com.example.data.models.NamazProgressRating
import com.example.data.models.QuranProgressRating
import com.example.data.models.Student
import com.example.ui.components.StatusBadge
import java.text.SimpleDateFormat
import java.util.*

private val PRAYER_PRESETS = listOf(
    "5-Time Salah Check",
    "Fajr",
    "Zuhr",
    "Asr",
    "Maghrib",
    "Isha",
    "Wudu & Masnoon Duas"
)

private val MADNI_QAIDA_LESSONS = listOf(
    1 to "Lesson 1: Huroof-e-Mufridat (Alphabets)",
    2 to "Lesson 2: Huroof-e-Murakkabat (Joint Letters)",
    3 to "Lesson 3: Harakat (Zabar, Zer, Pesh)",
    4 to "Lesson 4: Huroof-e-Maddah",
    5 to "Lesson 5: Harakat & Standing Zabar/Zer",
    6 to "Lesson 6: Tanween (Do-Zabar/Zer/Pesh)",
    7 to "Lesson 7: Izhar & Ikhfa Basics",
    8 to "Lesson 8: Huroof-e-Leen",
    9 to "Lesson 9: Sukoon / Jazm",
    10 to "Lesson 10: Qalqalah Letters",
    11 to "Lesson 11: Sukoon Revision",
    12 to "Lesson 12: Tashdeed (Shaddah)",
    13 to "Lesson 13: Tashdeed with Sukoon",
    14 to "Lesson 14: Tashdeed with Madd",
    15 to "Lesson 15: Mim & Noon Mushaddadah",
    16 to "Lesson 16: Rules of Laam (Allah)",
    17 to "Lesson 17: Rules of Raa",
    18 to "Lesson 18: Noon Sakinah & Tanween",
    19 to "Lesson 19: Meem Sakinah Rules",
    20 to "Lesson 20: Madd-e-Asli & Muttasil",
    21 to "Lesson 21: Madd-e-Munfasil & Lazim",
    22 to "Lesson 22: Huroof-e-Muqatta'at",
    23 to "Lesson 23: Waqf (Signs of Stopping)",
    24 to "Lesson 24: Makharij & Sifaat Revision",
    25 to "Lesson 25: Short Surahs Practice",
    26 to "Lesson 26: Complete Madni Qaida Test"
)

private fun formatNamazRatingLabel(rating: NamazProgressRating): String = when (rating) {
    NamazProgressRating.EXCELLENT -> "EXCELLENT"
    NamazProgressRating.MEMORIZED -> "MEMORIZED"
    NamazProgressRating.PRACTICED_GOOD -> "PRACTICED GOOD"
    NamazProgressRating.NEEDS_CORRECTION -> "CORRECTION"
    NamazProgressRating.MISSED -> "MISSED"
}

@Composable
fun NamazLogCardItem(
    record: NamazLogRecord,
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
                        text = "Adm #${record.admissionNumber} • ${record.date} • ${record.prayerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (record.rating) {
                    NamazProgressRating.EXCELLENT -> StatusBadge("EXCELLENT", Color(0xFFD1E7DD), Color(0xFF0F5132))
                    NamazProgressRating.MEMORIZED -> StatusBadge("MEMORIZED", Color(0xFFD1E7DD), Color(0xFF0F5132))
                    NamazProgressRating.PRACTICED_GOOD -> StatusBadge("GOOD", Color(0xFFFEF3C7), Color(0xFF92400E))
                    NamazProgressRating.NEEDS_CORRECTION -> StatusBadge("CORRECT", Color(0xFFFFF3CD), Color(0xFF856404))
                    NamazProgressRating.MISSED -> StatusBadge("MISSED", Color(0xFFF8D7DA), Color(0xFF842029))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Salah / Prayer: ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = record.prayerName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            if (record.duaOrSurahTopic.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Dua / Surah Practiced: ${record.duaOrSurahTopic}", style = MaterialTheme.typography.bodySmall)
            }

            if (record.wuduAndPostureNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Wudu & Arkan: ${record.wuduAndPostureNotes}", style = MaterialTheme.typography.bodySmall)
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
                                .testTag("edit_namaz_${record.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Namaz Log", modifier = Modifier.size(15.dp))
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
                                .testTag("delete_namaz_${record.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Namaz Log", modifier = Modifier.size(15.dp))
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
fun RecordNamazLogDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSubmit: (
        studentId: String,
        date: String,
        prayerName: String,
        duaOrSurahTopic: String,
        rating: NamazProgressRating,
        wuduAndPostureNotes: String,
        teacherRemarks: String,
        nextTarget: String
    ) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var prayerName by remember { mutableStateOf(PRAYER_PRESETS.first()) }
    var duaTopic by remember { mutableStateOf("Sana, Tashahhud & Durood Ibrahim") }
    var rating by remember { mutableStateOf(NamazProgressRating.PRACTICED_GOOD) }
    var wuduNotes by remember { mutableStateOf("Complete Wudu & Proper Qiyam/Ruku/Sajdah") }
    var remarks by remember { mutableStateOf("") }
    var nextTarget by remember { mutableStateOf("Dua-e-Qunoot Revision") }
    var studentExpanded by remember { mutableStateOf(false) }

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
                Text("Record Namaz & Tarbiyah Log", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(14.dp))

                ExposedDropdownMenuBox(
                    expanded = studentExpanded,
                    onExpandedChange = { studentExpanded = !studentExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedStudent?.let { "${it.fullName} (${it.admissionNumber})" } ?: "Select Student",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Student *") },
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
                                onClick = {
                                    selectedStudent = s
                                    studentExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Select Salah / Namaz Session:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PRAYER_PRESETS) { preset ->
                        FilterChip(
                            selected = prayerName == preset,
                            onClick = { prayerName = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duaTopic,
                    onValueChange = { duaTopic = it },
                    label = { Text("Namaz Dua / Surah Practiced") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = wuduNotes,
                    onValueChange = { wuduNotes = it },
                    label = { Text("Wudu, Ruku, Sajdah & Sunnah Adab") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Evaluation / Grade:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NamazProgressRating.values()) { r ->
                        FilterChip(
                            selected = rating == r,
                            onClick = { rating = r },
                            label = { Text(formatNamazRatingLabel(r), fontSize = 11.sp) }
                        )
                    }
                }

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
                    label = { Text("Next Day Target") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val stud = selectedStudent ?: return@Button
                            onSubmit(stud.id, date, prayerName, duaTopic, rating, wuduNotes, remarks, nextTarget)
                        },
                        enabled = selectedStudent != null && prayerName.isNotBlank(),
                        modifier = Modifier.testTag("submit_namaz_log_btn")
                    ) {
                        Text("Save Namaz Log")
                    }
                }
            }
        }
    }
}

@Composable
fun EditNamazLogDialog(
    record: NamazLogRecord,
    onDismiss: () -> Unit,
    onSave: (NamazLogRecord) -> Unit
) {
    var date by remember { mutableStateOf(record.date) }
    var prayerName by remember { mutableStateOf(record.prayerName) }
    var duaTopic by remember { mutableStateOf(record.duaOrSurahTopic) }
    var rating by remember { mutableStateOf(record.rating) }
    var wuduNotes by remember { mutableStateOf(record.wuduAndPostureNotes) }
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
                Text("Edit Namaz Log", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${record.studentName} (${record.admissionNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PRAYER_PRESETS) { preset ->
                        FilterChip(
                            selected = prayerName == preset,
                            onClick = { prayerName = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prayerName,
                    onValueChange = { prayerName = it },
                    label = { Text("Prayer / Salah Session *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duaTopic,
                    onValueChange = { duaTopic = it },
                    label = { Text("Dua / Surah Topic") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = wuduNotes,
                    onValueChange = { wuduNotes = it },
                    label = { Text("Wudu & Posture Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NamazProgressRating.values()) { r ->
                        FilterChip(
                            selected = rating == r,
                            onClick = { rating = r },
                            label = { Text(formatNamazRatingLabel(r), fontSize = 11.sp) }
                        )
                    }
                }
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
                    label = { Text("Next Day Target") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                                    prayerName = prayerName.trim(),
                                    duaOrSurahTopic = duaTopic,
                                    rating = rating,
                                    wuduAndPostureNotes = wuduNotes,
                                    teacherRemarks = remarks,
                                    nextTarget = nextTarget
                                )
                            )
                        },
                        enabled = prayerName.isNotBlank()
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun MadniQaidaLogCardItem(
    record: MadniQaidaLogRecord,
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
                        text = "Adm #${record.admissionNumber} • ${record.date} • Page ${record.pageNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (record.rating) {
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

            Text(
                text = record.lessonTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = "Sabaq #${record.lessonNumber} • Page ${record.pageNumber} (${record.linesCovered}) • Mistakes: ${record.mistakesCount}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )

            if (record.revisionSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Dohrai / Revision: ${record.revisionSummary}", style = MaterialTheme.typography.bodySmall)
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
                                .testTag("edit_qaida_${record.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Qaida Log", modifier = Modifier.size(15.dp))
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
                                .testTag("delete_qaida_${record.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Qaida Log", modifier = Modifier.size(15.dp))
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
fun RecordMadniQaidaLogDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSubmit: (
        studentId: String,
        date: String,
        lessonNumber: Int,
        lessonTitle: String,
        pageNumber: Int,
        linesCovered: String,
        rating: QuranProgressRating,
        revisionSummary: String,
        mistakesCount: Int,
        teacherRemarks: String,
        nextTarget: String
    ) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var lessonNumberStr by remember { mutableStateOf("1") }
    var lessonTitle by remember { mutableStateOf(MADNI_QAIDA_LESSONS.first().second) }
    var pageNumberStr by remember { mutableStateOf("1") }
    var linesCovered by remember { mutableStateOf("Lines 1-6") }
    var selectedRating by remember { mutableStateOf(QuranProgressRating.GOOD) }
    var revisionSummary by remember { mutableStateOf("") }
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
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Record Madni Qaida Lesson", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(14.dp))

                ExposedDropdownMenuBox(
                    expanded = studentExpanded,
                    onExpandedChange = { studentExpanded = !studentExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedStudent?.let { "${it.fullName} (${it.admissionNumber})" } ?: "Select Student",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Student *") },
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
                                onClick = {
                                    selectedStudent = s
                                    studentExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Select Madni Qaida Lesson (Sabaq 1–26):", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MADNI_QAIDA_LESSONS) { (num, title) ->
                        FilterChip(
                            selected = lessonNumberStr == num.toString(),
                            onClick = {
                                lessonNumberStr = num.toString()
                                lessonTitle = title
                            },
                            label = { Text("L$num", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lessonTitle,
                    onValueChange = { lessonTitle = it },
                    label = { Text("Lesson Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lessonNumberStr,
                        onValueChange = { lessonNumberStr = it },
                        label = { Text("Lesson #") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pageNumberStr,
                        onValueChange = { pageNumberStr = it },
                        label = { Text("Page #") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mistakesStr,
                        onValueChange = { mistakesStr = it },
                        label = { Text("Mistakes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = linesCovered,
                    onValueChange = { linesCovered = it },
                    label = { Text("Lines Covered (e.g. Lines 1-6 / Half Page)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Recitation Rating:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(QuranProgressRating.values()) { rating ->
                        FilterChip(
                            selected = selectedRating == rating,
                            onClick = { selectedRating = rating },
                            label = { Text(rating.name.replace("_", " "), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = revisionSummary,
                    onValueChange = { revisionSummary = it },
                    label = { Text("Previous Lesson Revision (Dohrai)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Makharij & Teacher Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nextTarget,
                    onValueChange = { nextTarget = it },
                    label = { Text("Next Day Target") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val stud = selectedStudent ?: return@Button
                            onSubmit(
                                stud.id,
                                date,
                                (lessonNumberStr.toIntOrNull() ?: 1).coerceIn(1, 30),
                                lessonTitle,
                                (pageNumberStr.toIntOrNull() ?: 1).coerceIn(1, 150),
                                linesCovered,
                                selectedRating,
                                revisionSummary,
                                (mistakesStr.toIntOrNull() ?: 0).coerceIn(0, 200),
                                remarks,
                                nextTarget
                            )
                        },
                        enabled = selectedStudent != null && lessonTitle.isNotBlank(),
                        modifier = Modifier.testTag("submit_qaida_log_btn")
                    ) {
                        Text("Save Qaida Log")
                    }
                }
            }
        }
    }
}

@Composable
fun EditMadniQaidaLogDialog(
    record: MadniQaidaLogRecord,
    onDismiss: () -> Unit,
    onSave: (MadniQaidaLogRecord) -> Unit
) {
    var date by remember { mutableStateOf(record.date) }
    var lessonNumberStr by remember { mutableStateOf(record.lessonNumber.toString()) }
    var lessonTitle by remember { mutableStateOf(record.lessonTitle) }
    var pageNumberStr by remember { mutableStateOf(record.pageNumber.toString()) }
    var linesCovered by remember { mutableStateOf(record.linesCovered) }
    var selectedRating by remember { mutableStateOf(record.rating) }
    var revisionSummary by remember { mutableStateOf(record.revisionSummary) }
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
                Text("Edit Madni Qaida Log", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${record.studentName} (${record.admissionNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MADNI_QAIDA_LESSONS) { (num, title) ->
                        FilterChip(
                            selected = lessonNumberStr == num.toString(),
                            onClick = {
                                lessonNumberStr = num.toString()
                                lessonTitle = title
                            },
                            label = { Text("L$num", fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lessonTitle,
                    onValueChange = { lessonTitle = it },
                    label = { Text("Lesson Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lessonNumberStr,
                        onValueChange = { lessonNumberStr = it },
                        label = { Text("Lesson #") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pageNumberStr,
                        onValueChange = { pageNumberStr = it },
                        label = { Text("Page #") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mistakesStr,
                        onValueChange = { mistakesStr = it },
                        label = { Text("Mistakes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = linesCovered,
                    onValueChange = { linesCovered = it },
                    label = { Text("Lines Covered") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(QuranProgressRating.values()) { rating ->
                        FilterChip(
                            selected = selectedRating == rating,
                            onClick = { selectedRating = rating },
                            label = { Text(rating.name.replace("_", " "), fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = revisionSummary,
                    onValueChange = { revisionSummary = it },
                    label = { Text("Dohrai / Revision Summary") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                    label = { Text("Next Day Target") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                                    lessonNumber = (lessonNumberStr.toIntOrNull() ?: record.lessonNumber).coerceIn(1, 30),
                                    lessonTitle = lessonTitle.trim(),
                                    pageNumber = (pageNumberStr.toIntOrNull() ?: record.pageNumber).coerceIn(1, 150),
                                    linesCovered = linesCovered,
                                    rating = selectedRating,
                                    revisionSummary = revisionSummary,
                                    mistakesCount = (mistakesStr.toIntOrNull() ?: record.mistakesCount).coerceIn(0, 200),
                                    teacherRemarks = remarks,
                                    nextTarget = nextTarget
                                )
                            )
                        },
                        enabled = lessonTitle.isNotBlank()
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
