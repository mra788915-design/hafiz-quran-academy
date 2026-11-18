package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.models.UserRole
import com.example.ui.AcademyViewModel
import com.example.ui.AppPortalState
import com.example.ui.components.ChangeOrResetAdminCredentialsDialog

@Composable
fun PortalSelectionHubScreen(
    viewModel: AcademyViewModel,
    onSelectPortal: (AppPortalState, Boolean) -> Unit,
    onSwitchGoogleAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var directUsername by remember { mutableStateOf("") }
    var directPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isDirectLoading by remember { mutableStateOf(false) }
    var directError by remember { mutableStateOf<String?>(null) }
    var showAdminRecoveryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = "Hafiz Quran Academy",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "حفظ قرآن اکیڈمی",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = "Single App Setup • Live Cloud Connected",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Apke phone par sirf 1 hi app chalegi (Admin, Teacher ya Parent) aur baqi phones ke sath live connected rahegi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cloud Google connection badge
        Surface(
            color = Color(0xFFD1E7DD),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF0F5132),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Cloud Active: ${viewModel.firebaseEmail ?: "Google Connected"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F5132)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Direct Login Card: Enter Admin-issued Username & Password -> Auto-locks phone to ONLY that 1 app
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhonelinkLock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Direct Login (Auto-Lock Your Single App)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Admin ka diya hua Username & Password enter karein — yeh phone khud apki 1 app (Admin, Teacher ya Parent) ban jayega.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = directUsername,
                    onValueChange = { directUsername = it; directError = null },
                    label = { Text("Username (Issued by Admin)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_username_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = directPassword,
                    onValueChange = { directPassword = it; directError = null },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide Password" else "Show Password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_password_input")
                )

                if (directError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = directError!!,
                            color = Color(0xFF991B1B),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (directUsername.isBlank() || directPassword.isBlank()) {
                            directError = "Apna Username aur Password dono darj karein."
                            return@Button
                        }
                        isDirectLoading = true
                        viewModel.authenticateDirectSingleApp(
                            usernameInput = directUsername,
                            passwordInput = directPassword,
                            requiredRole = null,
                            lockDeviceToRole = true
                        ) { success, err ->
                            isDirectLoading = false
                            if (!success) {
                                directError = err ?: "Invalid credentials."
                            }
                        }
                    },
                    enabled = !isDirectLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("direct_login_submit_btn")
                ) {
                    if (isDirectLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying & Syncing Cloud...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Login & Activate My Single App", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showAdminRecoveryDialog = true },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("hub_reset_admin_credentials_btn")
                ) {
                    Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Admin Login Invalid? Reset / Change Admin Username & Password")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Ya neechay se is phone ke liye 1 makhsoos app muntakhib karein:",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        PortalChoiceCard(
            title = "HQA Admin App",
            urduTitle = "صرف ایڈمن ایپ • نظامت اکیڈمی",
            description = "Sirf Admin ke phone ke liye: admissions, teachers, classes, fee challans, QR payments, FS waivers aur Teacher/Parent logins banana.",
            icon = Icons.Default.AdminPanelSettings,
            badge = "ADMIN PHONE ONLY",
            badgeColor = Color(0xFFD1E7DD),
            badgeTextColor = Color(0xFF0F5132),
            containerColor = MaterialTheme.colorScheme.surface,
            onClick = { onSelectPortal(AppPortalState.ADMIN_LOGIN, true) },
            testTag = "hub_card_admin"
        )

        Spacer(modifier = Modifier.height(12.dp))

        PortalChoiceCard(
            title = "HQA Teacher App",
            urduTitle = "صرف اساتذہ ایپ • اساتذہ کرام",
            description = "Sirf Ustadh ke phone ke liye: apni classes ki hazri (attendance) aur Sabaq, Sabqi, Manzil darj karna — Admin aur Parents ke sath live sync.",
            icon = Icons.Default.School,
            badge = "TEACHER PHONE ONLY",
            badgeColor = Color(0xFFEDE9FE),
            badgeTextColor = Color(0xFF5B21B6),
            containerColor = MaterialTheme.colorScheme.surface,
            onClick = { onSelectPortal(AppPortalState.TEACHER_LOGIN, true) },
            testTag = "hub_card_teacher"
        )

        Spacer(modifier = Modifier.height(12.dp))

        PortalChoiceCard(
            title = "HQA Parent App",
            urduTitle = "صرف والدین ایپ • سرپرست و والدین",
            description = "Sirf Walidain ke phone ke liye: apne bachon ki Quran progress, hazri, 3-copy QR fee challan aur JazzCash/EasyPaisa online fee payment.",
            icon = Icons.Default.FamilyRestroom,
            badge = "PARENT PHONE ONLY",
            badgeColor = Color(0xFFFEF3C7),
            badgeTextColor = Color(0xFF92400E),
            containerColor = MaterialTheme.colorScheme.surface,
            onClick = { onSelectPortal(AppPortalState.PARENT_LOGIN, true) },
            testTag = "hub_card_parent"
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onSwitchGoogleAccount,
            modifier = Modifier.testTag("hub_switch_google_btn")
        ) {
            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Switch Connected Google Account")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showAdminRecoveryDialog) {
        ChangeOrResetAdminCredentialsDialog(
            currentUsername = directUsername,
            isRecoveryMode = true,
            googleEmail = viewModel.firebaseEmail,
            onDismiss = { showAdminRecoveryDialog = false },
            onSubmit = { newU, newP, onDone ->
                viewModel.recoverOrResetAdminCredentials(newU, newP, onDone)
            }
        )
    }
}

@Composable
fun PortalChoiceCard(
    title: String,
    urduTitle: String,
    description: String,
    icon: ImageVector,
    badge: String,
    badgeColor: Color,
    badgeTextColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = badgeTextColor)
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = urduTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeTextColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set Up This Phone as $title",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AdminLoginScreen(
    viewModel: AcademyViewModel,
    allowPortalModeSwitch: Boolean = true,
    onChangePortalModeWhenUnlocked: () -> Unit,
    onSwitchGoogleAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    IsolatedPortalLoginTemplate(
        title = "HQA Admin App",
        urduSubtitle = "لاگ ان نظامت اکیڈمی • Dedicated Admin Phone",
        usernameLabel = "Admin Username",
        icon = Icons.Default.AdminPanelSettings,
        iconBgColor = Color(0xFFD1E7DD),
        iconTintColor = Color(0xFF0F5132),
        securityNotice = "Yeh phone sirf HQA Admin App par lock hai. Teacher aur Parent logins yahan kaam nahi kar sakte. (First-time default Admin: admin / Admin@123)",
        usernameTag = "admin_email_input",
        passwordTag = "admin_password_input",
        submitTag = "admin_login_submit_btn",
        requiredRole = UserRole.ADMIN,
        allowPortalModeSwitch = allowPortalModeSwitch,
        viewModel = viewModel,
        onChangePortalMode = onChangePortalModeWhenUnlocked,
        onSwitchGoogleAccount = onSwitchGoogleAccount,
        modifier = modifier
    )
}

@Composable
fun TeacherLoginScreen(
    viewModel: AcademyViewModel,
    allowPortalModeSwitch: Boolean = true,
    onChangePortalModeWhenUnlocked: () -> Unit,
    onSwitchGoogleAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    IsolatedPortalLoginTemplate(
        title = "HQA Teacher App",
        urduSubtitle = "لاگ ان اساتذہ کرام • Dedicated Teacher Phone",
        usernameLabel = "Teacher Username (Issued by Admin)",
        icon = Icons.Default.School,
        iconBgColor = Color(0xFFEDE9FE),
        iconTintColor = Color(0xFF5B21B6),
        securityNotice = "Yeh phone sirf HQA Teacher App par lock hai. Username aur Password sirf Academy Admin ki taraf se milta hai aur live Cloud ke sath connected hai.",
        usernameTag = "teacher_username_input",
        passwordTag = "teacher_password_input",
        submitTag = "teacher_login_submit_btn",
        requiredRole = UserRole.TEACHER,
        allowPortalModeSwitch = allowPortalModeSwitch,
        viewModel = viewModel,
        onChangePortalMode = onChangePortalModeWhenUnlocked,
        onSwitchGoogleAccount = onSwitchGoogleAccount,
        modifier = modifier
    )
}

@Composable
fun ParentLoginScreen(
    viewModel: AcademyViewModel,
    allowPortalModeSwitch: Boolean = true,
    onChangePortalModeWhenUnlocked: () -> Unit,
    onSwitchGoogleAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    IsolatedPortalLoginTemplate(
        title = "HQA Parent App",
        urduSubtitle = "لاگ ان والدین • Dedicated Parent Phone",
        usernameLabel = "Parent Username (Issued by Admin)",
        icon = Icons.Default.FamilyRestroom,
        iconBgColor = Color(0xFFFEF3C7),
        iconTintColor = Color(0xFF92400E),
        securityNotice = "Yeh phone sirf HQA Parent App par lock hai. Username aur Password sirf Academy Admin ki taraf se milta hai aur apke bachon ka live record dikhata hai.",
        usernameTag = "parent_username_input",
        passwordTag = "parent_password_input",
        submitTag = "parent_login_submit_btn",
        requiredRole = UserRole.PARENT,
        allowPortalModeSwitch = allowPortalModeSwitch,
        viewModel = viewModel,
        onChangePortalMode = onChangePortalModeWhenUnlocked,
        onSwitchGoogleAccount = onSwitchGoogleAccount,
        modifier = modifier
    )
}

@Composable
private fun IsolatedPortalLoginTemplate(
    title: String,
    urduSubtitle: String,
    usernameLabel: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    securityNotice: String,
    usernameTag: String,
    passwordTag: String,
    submitTag: String,
    requiredRole: UserRole,
    allowPortalModeSwitch: Boolean,
    viewModel: AcademyViewModel,
    onChangePortalMode: () -> Unit,
    onSwitchGoogleAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showAdminRecoveryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTintColor,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = iconBgColor,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "SINGLE DEDICATED APP • LIVE CLOUD",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = iconTintColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = urduSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (!viewModel.firebaseEmail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cloud Sync: ${viewModel.firebaseEmail}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            label = { Text(usernameLabel) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .testTag(usernameTag)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide Password" else "Show Password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .testTag(passwordTag)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Color(0xFFFEE2E2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    color = Color(0xFF991B1B),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter your assigned username and password."
                    return@Button
                }
                isLoading = true
                viewModel.authenticateForPortal(
                    usernameInput = username,
                    passwordInput = password,
                    requiredRole = requiredRole,
                    lockDeviceToRole = true
                ) { success, err ->
                    isLoading = false
                    if (!success) {
                        errorMessage = err ?: "Invalid credentials."
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(50.dp)
                .testTag(submitTag)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connecting Live Cloud...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign In (${requiredRole.name} ONLY)", fontWeight = FontWeight.Bold)
            }
        }

        if (requiredRole == UserRole.ADMIN) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showAdminRecoveryDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .height(48.dp)
                    .testTag("reset_admin_credentials_btn")
            ) {
                Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset / Change Admin Username & Password")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = securityNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allowPortalModeSwitch && AppEditionConfig.ACTIVE_EDITION == AppEditionMode.AUTO_SINGLE_APP) {
                TextButton(
                    onClick = onChangePortalMode,
                    modifier = Modifier.testTag("switch_portal_setup_btn")
                ) {
                    Icon(Icons.Default.AppSettingsAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change Phone App Setup")
                }
            }

            TextButton(
                onClick = onSwitchGoogleAccount,
                modifier = Modifier.testTag("login_switch_google_btn")
            ) {
                Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Switch Google")
            }
        }
    }

    if (showAdminRecoveryDialog && requiredRole == UserRole.ADMIN) {
        ChangeOrResetAdminCredentialsDialog(
            currentUsername = username,
            isRecoveryMode = true,
            googleEmail = viewModel.firebaseEmail,
            onDismiss = { showAdminRecoveryDialog = false },
            onSubmit = { newU, newP, onDone ->
                viewModel.recoverOrResetAdminCredentials(newU, newP, onDone)
            }
        )
    }
}
