package com.example.ui.parent

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
import com.example.data.models.*
import com.example.ui.AcademyViewModel
import com.example.ui.admin.QuranProgressCardItem
import com.example.ui.components.AttendanceStatusChip
import com.example.ui.components.CertificatePreviewAndPrintDialog
import com.example.ui.components.ChallanStatusBadge
import com.example.ui.components.EmptyPlaceholderCard
import com.example.ui.components.OnlinePaymentQrDialog
import com.example.util.ChallanPdfGenerator
import java.text.NumberFormat
import java.util.*

@Composable
fun ParentPortalMainScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allStudents by viewModel.students.collectAsState(initial = emptyList())
    val announcements by viewModel.announcements.collectAsState(initial = emptyList())
    val settings = viewModel.repository.settings

    // STRICT PARENT PRIVACY ISOLATION: Only show students explicitly linked by Admin
    val myChildren = remember(allStudents, currentUser) {
        val linked = currentUser.linkedStudentIds.toSet()
        allStudents.filter { it.id in linked }
    }

    var selectedChild by remember(myChildren) {
        mutableStateOf(myChildren.firstOrNull())
    }

    var selectedTab by remember { mutableStateOf(0) }
    var challanForOnlineQrPay by remember { mutableStateOf<FeeChallan?>(null) }
    var showChildCertificatePreview by remember { mutableStateOf(false) }
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    val childProgress by remember(selectedChild) {
        if (selectedChild != null) viewModel.repository.getProgressByStudent(selectedChild!!.id)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val childAttendance by remember(selectedChild) {
        if (selectedChild != null) viewModel.repository.getAttendanceByStudent(selectedChild!!.id)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val childChallans by remember(selectedChild) {
        if (selectedChild != null) viewModel.repository.getChallansByStudent(selectedChild!!.id)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize()) {
        // Parent & Child Header Banner
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Parent Portal (Isolated Family Session)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = currentUser.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.FamilyRestroom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (myChildren.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Select Linked Child / Talib-e-Ilm:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(myChildren) { child ->
                            FilterChip(
                                selected = selectedChild?.id == child.id,
                                onClick = { selectedChild = child },
                                label = { Text(child.fullName) }
                            )
                        }
                    }
                } else if (selectedChild != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Child: ${selectedChild?.fullName} (${selectedChild?.admissionNumber}) • ${selectedChild?.className}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (myChildren.isEmpty()) {
            EmptyPlaceholderCard(
                title = "No linked student records",
                subtitle = "Please contact Academy Administration to link your child's admission profile to your Parent login.",
                icon = Icons.Default.FamilyRestroom
            )
            return@Column
        }

        if (selectedChild?.status == StudentStatus.COMPLETED) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "MashaAllah! Course Completed",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = selectedChild?.completedCourse?.ifBlank { selectedChild?.quranLevel ?: "" } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                    Button(
                        onClick = { showChildCertificatePreview = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0B4F32),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("parent_view_certificate_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sanad PDF", fontSize = 12.sp)
                    }
                }
            }
        }

        if (showChildCertificatePreview && selectedChild != null) {
            CertificatePreviewAndPrintDialog(
                student = selectedChild!!,
                settings = settings,
                onEditDetails = null,
                onDismiss = { showChildCertificatePreview = false }
            )
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Quran") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Attendance") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Fee Challan") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Notices") })
        }

        when (selectedTab) {
            0 -> {
                if (childProgress.isEmpty()) {
                    EmptyPlaceholderCard(
                        title = "No Quran recitation records yet",
                        subtitle = "Today's lesson update will be posted by the Halaqah teacher.",
                        icon = Icons.Default.AutoStories
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            val latest = childProgress.first()
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Current Hifz Status",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Juz (Para) ${latest.juzNumber} • ${latest.surahName}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Last Sabaq: Ayat ${latest.sabaqAyatStart} - ${latest.sabaqAyatEnd}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (latest.nextTarget.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Next Target: ${latest.nextTarget}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        items(childProgress) { rec ->
                            QuranProgressCardItem(rec)
                        }
                    }
                }
            }
            1 -> {
                val totalAtt = childAttendance.size
                val presentAtt = childAttendance.count { it.status == AttendanceStatus.PRESENT }
                val percent = if (totalAtt > 0) (presentAtt.toDouble() / totalAtt) * 100.0 else 100.0

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E7DD))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Overall Attendance Rate", style = MaterialTheme.typography.labelMedium, color = Color(0xFF0F5132))
                                    Text(String.format(Locale.US, "%.0f%%", percent), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0F5132))
                                    Text("$presentAtt Present of $totalAtt Days", style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F5132))
                                }
                                Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF0F5132), modifier = Modifier.size(36.dp))
                            }
                        }
                    }

                    if (childAttendance.isEmpty()) {
                        item {
                            Text("No attendance recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(childAttendance) { att ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(att.date, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        if (att.remarks.isNotBlank()) {
                                            Text(att.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    AttendanceStatusChip(att.status)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                if (childChallans.isEmpty()) {
                    EmptyPlaceholderCard(
                        title = "No fee challans found",
                        subtitle = "Challans issued by the academy will appear here.",
                        icon = Icons.Default.ReceiptLong
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            // Online Payment Accounts Info Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E7DD))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF0F5132))
                                        Text(
                                            text = "Online Fee Payment (JazzCash & EasyPaisa)",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF0F5132)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "JazzCash Account: ${settings.jazzCashNumber}\nEasyPaisa Account: ${settings.easyPaisaNumber}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = Color(0xFF0F5132)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Printed 3-copy PDF challans include scannable QR codes for JazzCash, EasyPaisa, and Banking Apps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF0F5132).copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }

                        items(childChallans) { challan ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "${challan.billingMonth} ${challan.billingYear}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Challan #${challan.challanNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        ChallanStatusBadge(challan.status)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Monthly Tuition:", style = MaterialTheme.typography.bodySmall)
                                        Text("Rs. ${formatter.format(challan.monthlyTuitionFee)}", style = MaterialTheme.typography.bodySmall)
                                    }

                                    if (challan.fsAssistanceAmount > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Fee Sabilillah (FS) Waiver:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            Text("- Rs. ${formatter.format(challan.fsAssistanceAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    if (challan.discountAmount > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Discount:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            Text("- Rs. ${formatter.format(challan.discountAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    if (challan.previousBalance > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Previous Arrears:", style = MaterialTheme.typography.bodySmall)
                                            Text("Rs. ${formatter.format(challan.previousBalance)}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total Payable:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("Rs. ${formatter.format(challan.totalPayable)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Amount Paid:", style = MaterialTheme.typography.bodySmall)
                                        Text("Rs. ${formatter.format(challan.amountPaid)}", style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Remaining Due:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text(
                                            "Rs. ${formatter.format(challan.remainingBalance)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (challan.remainingBalance > 0) Color(0xFFDC2626) else Color(0xFF15803D)
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Due Date: ${challan.dueDate}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                                    if (challan.transactionRef.isNotBlank()) {
                                        Text(
                                            "Payment Ref: ${challan.transactionRef} (${challan.paymentMethod})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF15803D)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    if (challan.remainingBalance > 0) {
                                        Button(
                                            onClick = { challanForOnlineQrPay = challan },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("parent_online_qr_pay_btn")
                                        ) {
                                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Pay Online via JazzCash / EasyPaisa QR")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val file = ChallanPdfGenerator.generateThreeCopyChallanPdf(
                                                context,
                                                challan,
                                                settings
                                            )
                                            ChallanPdfGenerator.printOrOpenPdf(context, file)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("parent_print_qr_challan_btn")
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download / Print 3-Copy QR Challan PDF")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(announcements) { ann ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(ann.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(ann.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(ann.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    challanForOnlineQrPay?.let { ch ->
        OnlinePaymentQrDialog(
            challan = ch,
            settings = settings,
            allowSubmitPayment = true,
            onDismiss = { challanForOnlineQrPay = null },
            onSubmitOnlinePayment = { amount, method, tid, remarks ->
                viewModel.recordPayment(ch.id, amount, method, remarks, tid) {
                    challanForOnlineQrPay = null
                }
            }
        )
    }
}
