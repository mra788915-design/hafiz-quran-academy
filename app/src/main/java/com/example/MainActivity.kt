package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.models.UserRole
import com.example.ui.AcademyViewModel
import com.example.ui.AppPortalState
import com.example.ui.admin.*
import com.example.ui.auth.*
import com.example.ui.components.AcademyTopAppBar
import com.example.ui.parent.ParentPortalMainScreen
import com.example.ui.teacher.TeacherPortalMainScreen
import com.example.ui.theme.HafizQuranAcademyTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HafizQuranAcademyTheme {
                AcademyAuthGatedRoot()
            }
        }
    }
}

@Composable
fun AcademyAuthGatedRoot() {
    val firebaseUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val activeFirebaseUser = firebaseUser
    if (activeFirebaseUser == null) {
        GoogleSignInGateScreen(
            onAuthSuccess = { /* authStateFlow automatically emits authenticated user */ }
        )
    } else {
        val academyViewModel: AcademyViewModel = viewModel(
            key = activeFirebaseUser.uid,
            factory = AcademyViewModel.provideFactory(
                firebaseUid = activeFirebaseUser.uid,
                firebaseEmail = activeFirebaseUser.email
            )
        )

        AcademyApp(
            viewModel = academyViewModel,
            onSwitchGoogleAccount = {
                signOutGoogleAndFirebase(
                    credentialManager = credentialManager,
                    scope = scope
                )
            }
        )
    }
}

@Composable
fun AcademyApp(
    viewModel: AcademyViewModel,
    onSwitchGoogleAccount: () -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val portalState by viewModel.portalState.collectAsStateWithLifecycle()
    val lockedPortalRole by viewModel.lockedPortalRole.collectAsStateWithLifecycle()
    val isRestoringSession by viewModel.isRestoringCloudSession.collectAsStateWithLifecycle()
    val isCloudSyncActive by viewModel.isCloudSyncActive.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Admin bottom navigation tab state
    var adminActiveTab by remember { mutableStateOf(0) }

    // Back navigation strictly isolated within current dedicated app
    BackHandler(
        enabled = when (portalState) {
            AppPortalState.ADMIN_APP -> adminActiveTab != 0
            AppPortalState.ADMIN_LOGIN,
            AppPortalState.TEACHER_LOGIN,
            AppPortalState.PARENT_LOGIN -> lockedPortalRole == null
            else -> false
        }
    ) {
        when (portalState) {
            AppPortalState.ADMIN_APP -> {
                adminActiveTab = 0
            }
            AppPortalState.ADMIN_LOGIN,
            AppPortalState.TEACHER_LOGIN,
            AppPortalState.PARENT_LOGIN -> {
                if (lockedPortalRole == null) {
                    viewModel.openPortal(AppPortalState.HUB, lockToThisPortal = false)
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (isRestoringSession) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Connecting Live Cloud Session...",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (portalState in listOf(AppPortalState.ADMIN_APP, AppPortalState.TEACHER_APP, AppPortalState.PARENT_APP)) {
                AcademyTopAppBar(
                    title = when (portalState) {
                        AppPortalState.ADMIN_APP -> "HQA Admin App • نظامت"
                        AppPortalState.TEACHER_APP -> "HQA Teacher App • اساتذہ"
                        AppPortalState.PARENT_APP -> "HQA Parent App • والدین"
                        else -> "Hafiz Quran Academy"
                    },
                    currentRole = currentUser.role,
                    userName = currentUser.name,
                    isCloudSyncActive = isCloudSyncActive,
                    onLogoutClick = {
                        adminActiveTab = 0
                        viewModel.logout()
                    }
                )
            }
        },
        bottomBar = {
            if (portalState == AppPortalState.ADMIN_APP && currentUser.role == UserRole.ADMIN) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = adminActiveTab == 0,
                        onClick = { adminActiveTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        modifier = Modifier.testTag("nav_dashboard")
                    )
                    NavigationBarItem(
                        selected = adminActiveTab == 1,
                        onClick = { adminActiveTab = 1 },
                        icon = { Icon(Icons.Default.People, contentDescription = "Admissions") },
                        label = { Text("Admissions") },
                        modifier = Modifier.testTag("nav_admissions")
                    )
                    NavigationBarItem(
                        selected = adminActiveTab == 4,
                        onClick = { adminActiveTab = 4 },
                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Challans") },
                        label = { Text("Challans") },
                        modifier = Modifier.testTag("nav_fees")
                    )
                    NavigationBarItem(
                        selected = adminActiveTab == 5,
                        onClick = { adminActiveTab = 5 },
                        icon = { Icon(Icons.Default.VolunteerActivism, contentDescription = "FS") },
                        label = { Text("FS") },
                        modifier = Modifier.testTag("nav_fs")
                    )
                    NavigationBarItem(
                        selected = adminActiveTab == 9,
                        onClick = { adminActiveTab = 9 },
                        icon = { Icon(Icons.Default.Key, contentDescription = "Logins") },
                        label = { Text("Logins") },
                        modifier = Modifier.testTag("nav_credentials")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (portalState) {
                AppPortalState.HUB -> {
                    PortalSelectionHubScreen(
                        viewModel = viewModel,
                        onSelectPortal = { chosenState, lockToSinglePortal ->
                            viewModel.openPortal(chosenState, lockToSinglePortal)
                        },
                        onSwitchGoogleAccount = onSwitchGoogleAccount
                    )
                }

                AppPortalState.ADMIN_LOGIN -> {
                    AdminLoginScreen(
                        viewModel = viewModel,
                        allowPortalModeSwitch = true,
                        onChangePortalModeWhenUnlocked = {
                            viewModel.unlockPortalSelectionWhenLoggedOut()
                        },
                        onSwitchGoogleAccount = onSwitchGoogleAccount
                    )
                }

                AppPortalState.TEACHER_LOGIN -> {
                    TeacherLoginScreen(
                        viewModel = viewModel,
                        allowPortalModeSwitch = true,
                        onChangePortalModeWhenUnlocked = {
                            viewModel.unlockPortalSelectionWhenLoggedOut()
                        },
                        onSwitchGoogleAccount = onSwitchGoogleAccount
                    )
                }

                AppPortalState.PARENT_LOGIN -> {
                    ParentLoginScreen(
                        viewModel = viewModel,
                        allowPortalModeSwitch = true,
                        onChangePortalModeWhenUnlocked = {
                            viewModel.unlockPortalSelectionWhenLoggedOut()
                        },
                        onSwitchGoogleAccount = onSwitchGoogleAccount
                    )
                }

                AppPortalState.ADMIN_APP -> {
                    if (currentUser.isAuthenticated && currentUser.role == UserRole.ADMIN) {
                        when (adminActiveTab) {
                            0 -> AdminDashboardScreen(
                                viewModel = viewModel,
                                onNavigateTab = { tabIndex -> adminActiveTab = tabIndex }
                            )
                            1 -> StudentAdmissionScreen(viewModel = viewModel)
                            2 -> TeachersScreen(viewModel = viewModel)
                            3 -> AttendanceManagementScreen(viewModel = viewModel)
                            4 -> FeeChallanManagementScreen(viewModel = viewModel)
                            5 -> FeeSabilillahScreen(viewModel = viewModel)
                            6 -> QuranProgressReportsScreen(viewModel = viewModel)
                            7 -> ClassesScreen(viewModel = viewModel)
                            8 -> AnnouncementsScreen(viewModel = viewModel)
                            9 -> PortalCredentialsScreen(viewModel = viewModel)
                            10 -> ExamsManagementScreen(viewModel = viewModel)
                            else -> AdminDashboardScreen(
                                viewModel = viewModel,
                                onNavigateTab = { tabIndex -> adminActiveTab = tabIndex }
                            )
                        }
                    }
                }

                AppPortalState.TEACHER_APP -> {
                    if (currentUser.isAuthenticated && currentUser.role == UserRole.TEACHER) {
                        TeacherPortalMainScreen(viewModel = viewModel)
                    }
                }

                AppPortalState.PARENT_APP -> {
                    if (currentUser.isAuthenticated && currentUser.role == UserRole.PARENT) {
                        ParentPortalMainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
