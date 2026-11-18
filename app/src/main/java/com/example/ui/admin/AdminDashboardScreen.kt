package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.UserRole
import com.example.ui.AcademyViewModel
import com.example.ui.components.ChangeOrResetAdminCredentialsDialog
import com.example.ui.components.MetricStatCard
import java.text.NumberFormat
import java.util.*

@Composable
fun AdminDashboardScreen(
    viewModel: AcademyViewModel,
    onNavigateTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val metrics by viewModel.dashboardMetrics.collectAsState()
    val payments by viewModel.payments.collectAsState(initial = emptyList())
    val userAccounts by viewModel.allUserAccounts.collectAsState(initial = emptyList())
    val settings = viewModel.repository.settings
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    var showChangeAdminDialog by remember { mutableStateOf(false) }

    val teacherLoginsCount = remember(userAccounts) { userAccounts.count { it.role == UserRole.TEACHER } }
    val parentLoginsCount = remember(userAccounts) { userAccounts.count { it.role == UserRole.PARENT } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Academy Header Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "حفظ قرآن اکیڈمی",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Admin Control Portal",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "JazzCash QR: ${settings.jazzCashNumber} • EasyPaisa QR: ${settings.easyPaisaNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { showChangeAdminDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dashboard_change_admin_login_btn")
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Change Admin Login (@${currentUser.username.ifBlank { "admin" }})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Admin-Only Teacher & Parent Login Vault Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTab(9) }
                    .testTag("dashboard_credentials_vault_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E7DD))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFF0F5132),
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Teacher & Parent Login Credentials Vault",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F5132)
                            )
                            Text(
                                text = "$teacherLoginsCount Teacher Logins • $parentLoginsCount Parent Logins (Confidential to Admin)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0F5132).copy(alpha = 0.85f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Credentials Vault",
                        tint = Color(0xFF0F5132)
                    )
                }
            }
        }

        // Metrics Grid (Row 1)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "Active Students",
                    value = "${metrics.activeStudentsCount}",
                    subtitle = "${metrics.classesCount} Classes • ${metrics.completedStudentsCount} Sanad",
                    icon = Icons.Default.People,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(1) }
                )
                MetricStatCard(
                    title = "Today Attendance",
                    value = String.format(Locale.US, "%.0f%%", metrics.todayAttendancePercent),
                    subtitle = "${metrics.todayAttendancePresent} Present",
                    icon = Icons.Default.FactCheck,
                    containerColor = Color(0xFFD1E7DD),
                    contentColor = Color(0xFF0F5132),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(3) }
                )
            }
        }

        // Metrics Grid (Row 2 - Financials)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "Collected (PKR)",
                    value = "Rs. ${formatter.format(metrics.totalFeesCollected)}",
                    subtitle = "Billed: Rs. ${formatter.format(metrics.totalFeesBilled)}",
                    icon = Icons.Default.AccountBalanceWallet,
                    containerColor = Color(0xFFEDE9FE),
                    contentColor = Color(0xFF5B21B6),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(4) }
                )
                MetricStatCard(
                    title = "Outstanding Arrears",
                    value = "Rs. ${formatter.format(metrics.totalFeesOutstanding)}",
                    subtitle = "${metrics.unpaidChallansCount} Unpaid Challans",
                    icon = Icons.Default.WarningAmber,
                    containerColor = Color(0xFFFEE2E2),
                    contentColor = Color(0xFF991B1B),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(4) }
                )
            }
        }

        // Fee Sabilillah (FS) Summary Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTab(5) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolunteerActivism,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = "Fee Sabilillah (FS) Department",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${metrics.pendingFsCount} Applications Awaiting Review",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open FS",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Quick Administrative Actions (2 rows covering all modules)
        item {
            Text(
                text = "Academy Management Modules",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigateTab(2) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Teachers", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onNavigateTab(7) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Classes", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onNavigateTab(3) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Attendance", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigateTab(6) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quran Log", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onNavigateTab(8) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Notices", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onNavigateTab(9) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Logins", fontSize = 12.sp)
                }
            }
        }

        // Recent Payments Stream
        item {
            Text(
                text = "Recent Fee Collection & Online QR Receipts",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (payments.isEmpty()) {
            item {
                Text(
                    text = "No fee payments recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(payments.take(4)) { payment ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = payment.studentName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${payment.receiptNumber} • ${payment.paymentMethod} • ${payment.paymentDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "+ Rs. ${formatter.format(payment.amount)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showChangeAdminDialog) {
        ChangeOrResetAdminCredentialsDialog(
            currentUsername = currentUser.username.ifBlank { "admin" },
            isRecoveryMode = false,
            googleEmail = viewModel.firebaseEmail,
            onDismiss = { showChangeAdminDialog = false },
            onSubmit = { newU, newP, onDone ->
                viewModel.changeAdminOwnCredentials(
                    newUsername = newU,
                    newPassword = newP,
                    displayName = currentUser.name,
                    onResult = onDone
                )
            }
        )
    }
}
