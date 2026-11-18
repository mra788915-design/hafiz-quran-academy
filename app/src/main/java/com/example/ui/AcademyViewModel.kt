package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.local.AcademyDatabase
import com.example.data.models.*
import com.example.data.remote.CloudUserProfile
import com.example.data.remote.FirestoreCloudRepository
import com.example.data.repository.AcademyRepository
import com.example.ui.auth.AppEditionConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class UserSession(
    val userId: String = "",
    val role: UserRole,
    val name: String,
    val username: String,
    val email: String = "",
    val teacherId: String? = null,
    val assignedClassIds: List<String> = emptyList(),
    val linkedStudentIds: List<String> = emptyList(),
    val isAuthenticated: Boolean = false
)

enum class AppPortalState {
    HUB,            // Initial Single-App Setup & Direct Login (only when phone not yet locked)
    ADMIN_LOGIN,    // Dedicated Login for Admin App Only
    ADMIN_APP,      // Strictly Isolated Admin App
    TEACHER_LOGIN,  // Dedicated Login for Teacher App Only
    TEACHER_APP,    // Strictly Isolated Teacher App
    PARENT_LOGIN,   // Dedicated Login for Parent App Only
    PARENT_APP      // Strictly Isolated Parent App
}

data class DashboardMetrics(
    val activeStudentsCount: Int = 0,
    val completedStudentsCount: Int = 0,
    val activeTeachersCount: Int = 0,
    val classesCount: Int = 0,
    val todayAttendancePresent: Int = 0,
    val todayAttendanceTotal: Int = 0,
    val todayAttendancePercent: Double = 0.0,
    val totalFeesBilled: Double = 0.0,
    val totalFeesCollected: Double = 0.0,
    val totalFeesOutstanding: Double = 0.0,
    val pendingFsCount: Int = 0,
    val unpaidChallansCount: Int = 0
)

class AcademyViewModel(
    val repository: AcademyRepository,
    private val cloudRepository: FirestoreCloudRepository,
    private val localDb: AcademyDatabase,
    private val prefs: SharedPreferences,
    val firebaseUid: String,
    val firebaseEmail: String?
) : ViewModel() {

    private var syncJob: Job? = null
    @Volatile
    private var isRotatingOwnAdminTicket: Boolean = false

    private val _isRestoringCloudSession = MutableStateFlow(true)
    val isRestoringCloudSession: StateFlow<Boolean> = _isRestoringCloudSession.asStateFlow()

    private val _isCloudSyncActive = MutableStateFlow(false)
    val isCloudSyncActive: StateFlow<Boolean> = _isCloudSyncActive.asStateFlow()

    private val _currentUser = MutableStateFlow(
        UserSession(
            role = UserRole.ADMIN,
            name = "Unauthenticated",
            username = "",
            isAuthenticated = false
        )
    )
    val currentUser: StateFlow<UserSession> = _currentUser.asStateFlow()

    // Dedicated/Locked Single App Role: once set, this phone operates strictly as ONLY 1 app (Admin OR Teacher OR Parent)
    private val _lockedPortalRole = MutableStateFlow<UserRole?>(null)
    val lockedPortalRole: StateFlow<UserRole?> = _lockedPortalRole.asStateFlow()

    private val _portalState = MutableStateFlow(AppPortalState.HUB)
    val portalState: StateFlow<AppPortalState> = _portalState.asStateFlow()

    // Data Flows from Local Room Cache (live-populated by FirestoreCloudRepository)
    val students: Flow<List<Student>> = repository.allStudents
    val activeStudents: Flow<List<Student>> = repository.activeStudents
    val teachers: Flow<List<Teacher>> = repository.allTeachers
    val classes: Flow<List<AcademyClass>> = repository.allClasses
    val challans: Flow<List<FeeChallan>> = repository.allChallans
    val payments: Flow<List<FeePayment>> = repository.allPayments
    val fsRecords: Flow<List<FeeSabilillahRecord>> = repository.allFsRecords
    val announcements: Flow<List<Announcement>> = repository.allAnnouncements
    val quranProgressList: Flow<List<QuranProgressRecord>> = repository.allProgress
    val allAttendance: Flow<List<AttendanceRecord>> = repository.allAttendance
    val allUserAccounts: Flow<List<UserAccount>> = repository.allUsers

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        combine(students, teachers, classes) { s, t, c -> Triple(s, t, c) },
        combine(allAttendance, challans, fsRecords) { a, ch, f -> Triple(a, ch, f) }
    ) { group1, group2 ->
        val studList = group1.first
        val teachList = group1.second
        val classList = group1.third

        val attList = group2.first
        val chList = group2.second
        val fsList = group2.third

        val activeStuds = studList.filter { it.status == StudentStatus.ACTIVE }
        val completedStuds = studList.count { it.status == StudentStatus.COMPLETED }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayAtt = attList.filter { it.date == today }
        val presentCount = todayAtt.count { it.status == AttendanceStatus.PRESENT }
        val totalAtt = todayAtt.size
        val attPercent = if (totalAtt > 0) (presentCount.toDouble() / totalAtt) * 100.0 else 100.0

        val totalBilled = chList.sumOf { it.totalPayable }
        val totalCollected = chList.sumOf { it.amountPaid }
        val totalOutstanding = chList.sumOf { it.remainingBalance }
        val unpaidCount = chList.count { it.status == ChallanStatus.UNPAID || it.status == ChallanStatus.OVERDUE }
        val pendingFs = fsList.count { it.approvalStatus == ApprovalStatus.PENDING }

        DashboardMetrics(
            activeStudentsCount = activeStuds.size,
            completedStudentsCount = completedStuds,
            activeTeachersCount = teachList.count { it.isActive },
            classesCount = classList.count { it.isActive },
            todayAttendancePresent = presentCount,
            todayAttendanceTotal = totalAtt,
            todayAttendancePercent = attPercent,
            totalFeesBilled = totalBilled,
            totalFeesCollected = totalCollected,
            totalFeesOutstanding = totalOutstanding,
            pendingFsCount = pendingFs,
            unpaidChallansCount = unpaidCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    init {
        repository.activeCloudUid = firebaseUid
        val editionRole = AppEditionConfig.ACTIVE_EDITION.prelockedRole
        val savedLockedRole = editionRole ?: prefs.getString("locked_portal_role", null)?.let {
            runCatching { UserRole.valueOf(it) }.getOrNull()
        }
        if (savedLockedRole != null) {
            _lockedPortalRole.value = savedLockedRole
            _portalState.value = loginStateForRole(savedLockedRole)
        }

        viewModelScope.launch {
            try {
                repository.seedDefaultSchedulesIfEmpty()
                val restoredProfile = cloudRepository.restoreCloudSession(firebaseUid)
                if (restoredProfile != null && (editionRole == null || restoredProfile.role == editionRole)) {
                    val savedUsername = prefs.getString("active_username", restoredProfile.role.name.lowercase(Locale.US)) ?: ""
                    activateCloudPortalSession(
                        profile = restoredProfile,
                        username = savedUsername,
                        lockDevice = true
                    )
                } else {
                    if (savedLockedRole != null) {
                        _portalState.value = loginStateForRole(savedLockedRole)
                    } else {
                        _portalState.value = AppPortalState.HUB
                    }
                }
            } finally {
                _isRestoringCloudSession.value = false
            }
        }
    }

    private fun loginStateForRole(role: UserRole): AppPortalState = when (role) {
        UserRole.ADMIN -> AppPortalState.ADMIN_LOGIN
        UserRole.TEACHER -> AppPortalState.TEACHER_LOGIN
        UserRole.PARENT -> AppPortalState.PARENT_LOGIN
    }

    private fun appStateForRole(role: UserRole): AppPortalState = when (role) {
        UserRole.ADMIN -> AppPortalState.ADMIN_APP
        UserRole.TEACHER -> AppPortalState.TEACHER_APP
        UserRole.PARENT -> AppPortalState.PARENT_APP
    }

    fun lockDeviceToPortal(role: UserRole?) {
        val enforcedRole = AppEditionConfig.ACTIVE_EDITION.prelockedRole ?: role
        _lockedPortalRole.value = enforcedRole
        if (enforcedRole != null) {
            prefs.edit().putString("locked_portal_role", enforcedRole.name).apply()
        } else {
            prefs.edit().remove("locked_portal_role").apply()
        }
    }

    fun openPortal(state: AppPortalState, lockToThisPortal: Boolean = true) {
        if (_currentUser.value.isAuthenticated) {
            _portalState.value = appStateForRole(_currentUser.value.role)
            return
        }
        val locked = _lockedPortalRole.value
        if (locked != null) {
            _portalState.value = loginStateForRole(locked)
            return
        }
        if (lockToThisPortal) {
            when (state) {
                AppPortalState.ADMIN_LOGIN -> lockDeviceToPortal(UserRole.ADMIN)
                AppPortalState.TEACHER_LOGIN -> lockDeviceToPortal(UserRole.TEACHER)
                AppPortalState.PARENT_LOGIN -> lockDeviceToPortal(UserRole.PARENT)
                else -> {}
            }
        }
        _portalState.value = state
    }

    private fun activateCloudPortalSession(
        profile: CloudUserProfile,
        username: String,
        lockDevice: Boolean
    ) {
        if (lockDevice) {
            lockDeviceToPortal(profile.role)
        }
        _currentUser.value = UserSession(
            userId = profile.userId,
            role = profile.role,
            name = profile.displayName,
            username = username,
            email = firebaseEmail ?: "",
            teacherId = profile.teacherId.ifBlank { null },
            assignedClassIds = profile.assignedClassIds,
            linkedStudentIds = profile.linkedStudentIds,
            isAuthenticated = true
        )
        _portalState.value = appStateForRole(profile.role)
        startRealtimeSync(profile)
    }

    private fun startRealtimeSync(profile: CloudUserProfile) {
        syncJob?.cancel()
        _isCloudSyncActive.value = true
        syncJob = viewModelScope.launch {
            try {
                cloudRepository.syncRoleDataRealtime(
                    uid = firebaseUid,
                    profile = profile,
                    localDb = localDb,
                    onSessionRevoked = {
                        if (!isRotatingOwnAdminTicket) {
                            viewModelScope.launch {
                                _snackbarMessage.emit("Login session updated or suspended by Admin. Please sign in again.")
                                logout()
                            }
                        }
                    },
                    onProfileUpdated = { updatedProfile ->
                        val classesChanged = updatedProfile.assignedClassIds != _currentUser.value.assignedClassIds
                        val studentsChanged = updatedProfile.linkedStudentIds != _currentUser.value.linkedStudentIds
                        _currentUser.update { curr ->
                            curr.copy(
                                name = updatedProfile.displayName,
                                assignedClassIds = updatedProfile.assignedClassIds,
                                linkedStudentIds = updatedProfile.linkedStudentIds
                            )
                        }
                        if (classesChanged || studentsChanged) {
                            startRealtimeSync(updatedProfile)
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isCloudSyncActive.value = false
            }
        }
    }

    /**
     * Direct Single-App Cloud Login:
     * Verifies Admin-issued username & password against Firebase `/auth_tickets`, automatically detects
     * whether the user is ADMIN, TEACHER, or PARENT (or enforces `requiredRole` if phone is already locked),
     * locks this phone strictly into ONLY that 1 single app, and starts live real-time Cloud Sync.
     */
    fun authenticateDirectSingleApp(
        usernameInput: String,
        passwordInput: String,
        requiredRole: UserRole? = null,
        lockDeviceToRole: Boolean = true,
        onResult: (Boolean, String?) -> Unit
    ) {
        val enforcedRole = AppEditionConfig.ACTIVE_EDITION.prelockedRole ?: _lockedPortalRole.value ?: requiredRole
        if (_currentUser.value.isAuthenticated && enforcedRole != null && _currentUser.value.role != enforcedRole) {
            onResult(false, "Another app session is active. Only one dedicated app login is allowed.")
            return
        }

        viewModelScope.launch {
            val result = cloudRepository.authenticateAndClaimSession(
                uid = firebaseUid,
                email = firebaseEmail,
                usernameInput = usernameInput,
                passwordInput = passwordInput,
                requiredRole = enforcedRole
            )
            result.onSuccess { profile ->
                prefs.edit().putString("active_username", usernameInput.trim()).apply()
                activateCloudPortalSession(
                    profile = profile,
                    username = usernameInput.trim(),
                    lockDevice = lockDeviceToRole
                )
                val appLabel = when (profile.role) {
                    UserRole.ADMIN -> "HQA Admin App"
                    UserRole.TEACHER -> "HQA Teacher App"
                    UserRole.PARENT -> "HQA Parent App"
                }
                _snackbarMessage.emit("Live Connected to $appLabel: ${profile.displayName}")
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "Invalid username or password.")
            }
        }
    }

    fun authenticateForPortal(
        usernameInput: String,
        passwordInput: String,
        requiredRole: UserRole,
        lockDeviceToRole: Boolean = true,
        onResult: (Boolean, String?) -> Unit
    ) {
        authenticateDirectSingleApp(
            usernameInput = usernameInput,
            passwordInput = passwordInput,
            requiredRole = requiredRole,
            lockDeviceToRole = lockDeviceToRole,
            onResult = onResult
        )
    }

    fun logout() {
        syncJob?.cancel()
        _isCloudSyncActive.value = false
        val previousRole = _currentUser.value.role
        prefs.edit().remove("active_username").apply()
        _currentUser.value = UserSession(
            role = previousRole,
            name = "Unauthenticated",
            username = "",
            isAuthenticated = false
        )
        val locked = _lockedPortalRole.value
        _portalState.value = if (locked != null) loginStateForRole(locked) else loginStateForRole(previousRole)
        viewModelScope.launch {
            cloudRepository.clearCloudSession(firebaseUid)
            _snackbarMessage.emit("Logged out safely.")
        }
    }

    /**
     * Allows resetting which single dedicated app this phone is configured for ONLY when logged out
     * (and when not using a prelocked build edition).
     */
    fun unlockPortalSelectionWhenLoggedOut() {
        if (_currentUser.value.isAuthenticated) return
        if (AppEditionConfig.ACTIVE_EDITION.prelockedRole != null) return
        lockDeviceToPortal(null)
        _portalState.value = AppPortalState.HUB
    }

    // --- Admin-Only Credential Management Actions ---
    fun savePortalCredentials(
        existingId: String? = null,
        name: String,
        username: String,
        password: String,
        role: UserRole,
        phone: String = "",
        email: String = "",
        teacherId: String = "",
        assignedClassIds: String = "",
        linkedStudentIds: String = "",
        isActive: Boolean = true,
        onSuccess: () -> Unit
    ) {
        if (!_currentUser.value.isAuthenticated || _currentUser.value.role != UserRole.ADMIN) {
            viewModelScope.launch {
                _snackbarMessage.emit("Unauthorized: Only Admin can manage login credentials.")
            }
            return
        }
        viewModelScope.launch {
            if (role == UserRole.ADMIN) {
                isRotatingOwnAdminTicket = true
            }
            try {
                val res = repository.savePortalCredentials(
                    existingId = existingId,
                    name = name,
                    username = username,
                    password = password,
                    role = role,
                    phone = phone,
                    email = email,
                    teacherId = teacherId,
                    assignedClassIds = assignedClassIds,
                    linkedStudentIds = linkedStudentIds,
                    isActive = isActive
                )
                res.onSuccess { savedAcc ->
                    if (role == UserRole.ADMIN) {
                        val newHash = com.example.data.remote.FirestoreCloudMappers.computeTicketHash(
                            savedAcc.username,
                            savedAcc.password
                        )
                        prefs.edit().putString("active_username", savedAcc.username).apply()
                        activateCloudPortalSession(
                            profile = CloudUserProfile(
                                userId = firebaseUid,
                                displayName = savedAcc.name,
                                role = UserRole.ADMIN,
                                ticketHash = newHash
                            ),
                            username = savedAcc.username,
                            lockDevice = true
                        )
                    }
                    _snackbarMessage.emit("Cloud Login saved for ${savedAcc.name} (@${savedAcc.username})")
                    onSuccess()
                }.onFailure {
                    _snackbarMessage.emit("Credential error: ${it.localizedMessage}")
                }
            } finally {
                isRotatingOwnAdminTicket = false
            }
        }
    }

    fun changeAdminOwnCredentials(
        newUsername: String,
        newPassword: String,
        displayName: String = "Nazim-e-Aala (Admin)",
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!_currentUser.value.isAuthenticated || _currentUser.value.role != UserRole.ADMIN) {
            onResult(false, "Unauthorized: Only signed-in Admin can change Admin credentials.")
            return
        }
        viewModelScope.launch {
            isRotatingOwnAdminTicket = true
            try {
                val res = repository.changeAdminOwnCredentials(newUsername, newPassword, displayName)
                res.onSuccess { (account, cloudProfile) ->
                    val effectiveProfile = cloudProfile ?: CloudUserProfile(
                        userId = firebaseUid,
                        displayName = account.name,
                        role = UserRole.ADMIN,
                        ticketHash = com.example.data.remote.FirestoreCloudMappers.computeTicketHash(
                            account.username,
                            account.password
                        )
                    )
                    prefs.edit().putString("active_username", account.username).apply()
                    activateCloudPortalSession(
                        profile = effectiveProfile,
                        username = account.username,
                        lockDevice = true
                    )
                    _snackbarMessage.emit("Admin Username & Password changed to @${account.username}")
                    onResult(true, null)
                }.onFailure { err ->
                    onResult(false, err.localizedMessage ?: "Failed to update Admin credentials.")
                }
            } finally {
                isRotatingOwnAdminTicket = false
            }
        }
    }

    fun recoverOrResetAdminCredentials(
        newUsername: String,
        newPassword: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = cloudRepository.recoverOrResetAdminCredentials(
                uid = firebaseUid,
                email = firebaseEmail,
                newUsername = newUsername,
                newPassword = newPassword
            )
            result.onSuccess { profile ->
                val cleanUser = newUsername.trim()
                prefs.edit().putString("active_username", cleanUser).apply()
                activateCloudPortalSession(
                    profile = profile,
                    username = cleanUser,
                    lockDevice = true
                )
                _snackbarMessage.emit("Admin Login Reset & Connected: @${cleanUser}")
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "Admin reset failed.")
            }
        }
    }

    fun toggleUserAccountActive(userId: String, isActive: Boolean) {
        if (!_currentUser.value.isAuthenticated || _currentUser.value.role != UserRole.ADMIN) return
        viewModelScope.launch {
            repository.toggleUserAccountActive(userId, isActive)
                .onSuccess {
                    _snackbarMessage.emit(if (isActive) "Login account activated in Cloud" else "Login account suspended in Cloud")
                }
                .onFailure {
                    _snackbarMessage.emit("Error: ${it.localizedMessage}")
                }
        }
    }

    fun deleteUserAccount(userId: String) {
        if (!_currentUser.value.isAuthenticated || _currentUser.value.role != UserRole.ADMIN) return
        viewModelScope.launch {
            repository.deleteUserAccount(userId)
                .onSuccess { _snackbarMessage.emit("Login account deleted from Cloud") }
                .onFailure { _snackbarMessage.emit("Error: ${it.localizedMessage}") }
        }
    }

    // --- Student Actions ---
    fun admitStudent(
        fullName: String,
        guardianName: String,
        dateOfBirth: String,
        admissionDate: String,
        gender: String,
        contactNumber: String,
        alternateContact: String,
        address: String,
        classId: String,
        className: String,
        teacherId: String,
        teacherName: String,
        quranLevel: String,
        monthlyFee: Double,
        feeCategory: String,
        isFsEligible: Boolean,
        notes: String,
        parentUsername: String = "",
        parentPassword: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.admitStudent(
                fullName, guardianName, dateOfBirth, admissionDate, gender,
                contactNumber, alternateContact, address, classId, className,
                teacherId, teacherName, quranLevel, monthlyFee, feeCategory,
                isFsEligible, notes, parentUsername, parentPassword
            )
            result.onSuccess {
                _snackbarMessage.emit("Student admitted & synced: ${it.fullName} (${it.admissionNumber})")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Failed: ${it.localizedMessage}")
            }
        }
    }

    fun updateStudent(student: Student, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateStudent(student)
                .onSuccess {
                    _snackbarMessage.emit("Student details updated & synced: ${it.fullName}")
                    onComplete()
                }
                .onFailure {
                    _snackbarMessage.emit("Failed to update student: ${it.localizedMessage}")
                }
        }
    }

    fun setStudentStatus(studentId: String, status: StudentStatus) {
        if (status == StudentStatus.WITHDRAWN) {
            withdrawStudentPermanently(studentId)
            return
        }
        viewModelScope.launch {
            repository.setStudentStatus(studentId, status)
            _snackbarMessage.emit("Student status set to ${status.name}")
        }
    }

    fun completeStudentCourseAndIssueCertificate(
        studentId: String,
        completedCourse: String,
        completionDate: String,
        certificateNumber: String,
        finalGrade: String,
        teacherNameOverride: String,
        certificateRemarks: String,
        onCompleted: (Student) -> Unit
    ) {
        viewModelScope.launch {
            repository.completeStudentCourseAndIssueCertificate(
                studentId = studentId,
                completedCourse = completedCourse,
                completionDate = completionDate,
                certificateNumber = certificateNumber,
                finalGrade = finalGrade,
                teacherNameOverride = teacherNameOverride,
                certificateRemarks = certificateRemarks
            ).onSuccess { updated ->
                _snackbarMessage.emit("MashaAllah! Course Completed & Sanad Ready: ${updated.fullName}")
                onCompleted(updated)
            }.onFailure { err ->
                _snackbarMessage.emit("Certificate error: ${err.localizedMessage}")
            }
        }
    }

    fun withdrawStudentPermanently(studentId: String) {
        viewModelScope.launch {
            repository.withdrawAndRemoveStudentPermanently(studentId)
                .onSuccess { label ->
                    _snackbarMessage.emit("Withdrawn & permanently removed from Cloud: $label")
                }
                .onFailure { err ->
                    _snackbarMessage.emit("Error removing student: ${err.localizedMessage}")
                }
        }
    }

    // --- Teacher Actions ---
    fun addTeacher(
        fullName: String,
        contactNumber: String,
        email: String,
        address: String,
        assignedClasses: String,
        assignedClassIds: String,
        joiningDate: String,
        notes: String,
        teacherUsername: String = "",
        teacherPassword: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addTeacher(
                fullName, contactNumber, email, address,
                assignedClasses, assignedClassIds, joiningDate, notes,
                teacherUsername, teacherPassword
            )
            result.onSuccess {
                _snackbarMessage.emit("Teacher registered & synced: ${it.fullName}")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Error: ${it.localizedMessage}")
            }
        }
    }

    fun updateTeacherDetails(
        teacher: Teacher,
        assignedClassIdsCsv: String,
        teacherUsername: String = "",
        teacherPassword: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateTeacherDetails(teacher, assignedClassIdsCsv, teacherUsername, teacherPassword)
                .onSuccess {
                    _snackbarMessage.emit("Teacher updated & synced: ${it.fullName}")
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessage.emit("Error updating teacher: ${it.localizedMessage}")
                }
        }
    }

    fun deleteTeacher(teacherId: String) {
        viewModelScope.launch {
            repository.deleteTeacher(teacherId)
                .onSuccess { name ->
                    _snackbarMessage.emit("Teacher removed from Cloud: $name")
                }
                .onFailure {
                    _snackbarMessage.emit("Error removing teacher: ${it.localizedMessage}")
                }
        }
    }

    // --- Class Actions ---
    fun addClass(
        name: String,
        section: String,
        roomNumber: String,
        capacity: Int,
        teacherId: String,
        teacherName: String,
        scheduleInfo: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.addClass(name, section, roomNumber, capacity, teacherId, teacherName, scheduleInfo)
            res.onSuccess {
                _snackbarMessage.emit("Class added & synced: ${it.name}")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Error: ${it.localizedMessage}")
            }
        }
    }

    fun updateClassDetails(academyClass: AcademyClass, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateClassDetails(academyClass)
                .onSuccess {
                    _snackbarMessage.emit("Class updated & synced: ${it.name}")
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessage.emit("Error updating class: ${it.localizedMessage}")
                }
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            repository.deleteClass(classId)
                .onSuccess { name ->
                    _snackbarMessage.emit("Class deleted & students unassigned: $name")
                }
                .onFailure {
                    _snackbarMessage.emit("Error deleting class: ${it.localizedMessage}")
                }
        }
    }

    // --- Attendance ---
    fun saveAttendance(
        classId: String,
        className: String,
        date: String,
        records: Map<String, AttendanceStatus>,
        remarks: Map<String, String>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val teacherId = _currentUser.value.teacherId ?: "Admin"
            val res = repository.saveClassAttendance(classId, className, date, records, remarks, teacherId)
            res.onSuccess { count ->
                _snackbarMessage.emit("Attendance synced live for $count students")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Attendance save failed: ${it.localizedMessage}")
            }
        }
    }

    fun deleteAttendanceRecord(attendanceId: String) {
        viewModelScope.launch {
            repository.deleteAttendanceRecord(attendanceId)
                .onSuccess { _snackbarMessage.emit("Attendance record removed") }
                .onFailure { _snackbarMessage.emit("Error removing attendance: ${it.localizedMessage}") }
        }
    }

    fun clearClassAttendanceForDate(classId: String, date: String) {
        viewModelScope.launch {
            repository.deleteClassAttendanceForDate(classId, date)
                .onSuccess { count ->
                    _snackbarMessage.emit("Cleared $count attendance records for $date")
                }
                .onFailure {
                    _snackbarMessage.emit("Error clearing attendance: ${it.localizedMessage}")
                }
        }
    }

    // --- Quran Progress ---
    fun recordQuranProgress(
        studentId: String,
        date: String,
        surahName: String,
        surahNumber: Int,
        juzNumber: Int,
        sabaqStart: Int,
        sabaqEnd: Int,
        rating: QuranProgressRating,
        sabqiSummary: String,
        manzilSummary: String,
        mistakesCount: Int,
        teacherRemarks: String,
        nextTarget: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val teacherId = _currentUser.value.teacherId ?: "Teacher"
            val teacherName = _currentUser.value.name
            val res = repository.recordQuranProgress(
                studentId, teacherId, teacherName, date,
                surahName, surahNumber, juzNumber, sabaqStart, sabaqEnd,
                rating, sabqiSummary, manzilSummary, mistakesCount,
                teacherRemarks, nextTarget
            )
            res.onSuccess {
                _snackbarMessage.emit("Quran progress synced live for ${it.studentName}")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Failed to record progress: ${it.localizedMessage}")
            }
        }
    }

    fun updateQuranProgress(record: QuranProgressRecord, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateQuranProgress(record)
                .onSuccess {
                    _snackbarMessage.emit("Quran lesson updated for ${it.studentName}")
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessage.emit("Failed to update lesson: ${it.localizedMessage}")
                }
        }
    }

    fun deleteQuranProgress(progressId: String) {
        viewModelScope.launch {
            repository.deleteQuranProgress(progressId)
                .onSuccess { _snackbarMessage.emit("Quran lesson record deleted") }
                .onFailure { _snackbarMessage.emit("Failed to delete lesson: ${it.localizedMessage}") }
        }
    }

    // --- Fees & Challans ---
    fun generateChallan(
        studentId: String,
        billingMonth: String,
        billingYear: Int,
        dueDate: String,
        additionalCharges: Double = 0.0,
        manualDiscount: Double = 0.0,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.generateChallanForStudent(
                studentId, billingMonth, billingYear, dueDate, additionalCharges, manualDiscount
            )
            res.onSuccess {
                _snackbarMessage.emit("Challan #${it.challanNumber} generated & synced live")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Challan generation error: ${it.localizedMessage}")
            }
        }
    }

    fun generateBulkChallans(
        classId: String?,
        billingMonth: String,
        billingYear: Int,
        dueDate: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.generateBulkChallans(classId, billingMonth, billingYear, dueDate)
            res.onSuccess { count ->
                _snackbarMessage.emit("Generated & synced $count fee challans for $billingMonth $billingYear")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Bulk generation error: ${it.localizedMessage}")
            }
        }
    }

    fun updateChallanDetails(challan: FeeChallan, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateChallanDetails(challan)
                .onSuccess {
                    _snackbarMessage.emit("Challan #${it.challanNumber} updated & synced")
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessage.emit("Error updating challan: ${it.localizedMessage}")
                }
        }
    }

    fun deleteChallan(challanId: String) {
        viewModelScope.launch {
            repository.deleteChallan(challanId)
                .onSuccess { chNo ->
                    _snackbarMessage.emit("Challan #$chNo deleted from Cloud")
                }
                .onFailure {
                    _snackbarMessage.emit("Error deleting challan: ${it.localizedMessage}")
                }
        }
    }

    fun recordPayment(
        challanId: String,
        amount: Double,
        paymentMethod: String,
        remarks: String,
        transactionId: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val receivedBy = _currentUser.value.name
            val res = repository.recordPayment(challanId, amount, paymentMethod, receivedBy, remarks, transactionId)
            res.onSuccess { payment ->
                _snackbarMessage.emit("Payment Rs. ${payment.amount.toInt()} synced live (${payment.receiptNumber})")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("Payment error: ${it.localizedMessage}")
            }
        }
    }

    fun updatePaymentDetails(payment: FeePayment, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updatePaymentDetails(payment)
                .onSuccess {
                    _snackbarMessage.emit("Payment receipt ${it.receiptNumber} updated & challan synced")
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessage.emit("Error updating payment: ${it.localizedMessage}")
                }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            repository.deletePayment(paymentId)
                .onSuccess { recNo ->
                    _snackbarMessage.emit("Payment receipt $recNo removed & challan balance adjusted")
                }
                .onFailure {
                    _snackbarMessage.emit("Error deleting payment: ${it.localizedMessage}")
                }
        }
    }

    // --- Fee Sabilillah (FS) ---
    fun submitFsApplication(
        studentId: String,
        category: String,
        assistanceType: AssistanceType,
        amount: Double,
        startDate: String,
        confidentialRemarks: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.submitFsApplication(
                studentId, category, assistanceType, amount, startDate, confidentialRemarks
            )
            res.onSuccess {
                _snackbarMessage.emit("FS application submitted & synced for ${it.studentName}")
                onSuccess()
            }.onFailure {
                _snackbarMessage.emit("FS submission error: ${it.localizedMessage}")
            }
        }
    }

    fun updateFsStatus(record: FeeSabilillahRecord, approved: Boolean, remarks: String) {
        viewModelScope.launch {
            val updated = record.copy(
                approvalStatus = if (approved) ApprovalStatus.APPROVED else ApprovalStatus.REJECTED,
                approverName = _currentUser.value.name,
                approvalDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                confidentialRemarks = if (remarks.isNotBlank()) remarks else record.confidentialRemarks
            )
            repository.updateFsRecord(updated)
            _snackbarMessage.emit("FS application ${if (approved) "Approved" else "Rejected"} & synced")
        }
    }

    fun updateFsDetails(record: FeeSabilillahRecord, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val updated = if (record.approvalStatus != ApprovalStatus.PENDING && record.approverName.isBlank()) {
                record.copy(
                    approverName = _currentUser.value.name,
                    approvalDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                )
            } else {
                record
            }
            repository.updateFsRecord(updated)
            _snackbarMessage.emit("FS record updated & synced for ${updated.studentName}")
            onSuccess()
        }
    }

    fun deleteFsRecord(fsId: String) {
        viewModelScope.launch {
            repository.deleteFsRecord(fsId)
                .onSuccess { name ->
                    _snackbarMessage.emit("FS record removed for $name")
                }
                .onFailure {
                    _snackbarMessage.emit("Error removing FS record: ${it.localizedMessage}")
                }
        }
    }

    // --- Announcements ---
    fun postAnnouncement(title: String, content: String, targetRole: String, priority: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.postAnnouncement(title, content, targetRole, priority)
            _snackbarMessage.emit("Announcement published live to connected apps")
            onSuccess()
        }
    }

    fun updateAnnouncement(announcement: Announcement, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateAnnouncementDetails(announcement)
                .onSuccess {
                    _snackbarMessage.emit("Notice updated & synced: ${it.title}")
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessage.emit("Error updating notice: ${it.localizedMessage}")
                }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        viewModelScope.launch {
            repository.deleteAnnouncement(announcementId)
                .onSuccess { title ->
                    _snackbarMessage.emit("Notice removed: $title")
                }
                .onFailure {
                    _snackbarMessage.emit("Error removing notice: ${it.localizedMessage}")
                }
        }
    }

    override fun onCleared() {
        syncJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            firebaseUid: String,
            firebaseEmail: String?
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val cloudRepo = FirestoreCloudRepository(firestore)
                val localDb = AcademyDatabase.getDatabase(app)
                val repository = AcademyRepository(localDb, cloudRepo).apply {
                    activeCloudUid = firebaseUid
                }
                val prefs = app.getSharedPreferences("hqa_single_portal_lock", Context.MODE_PRIVATE)
                AcademyViewModel(
                    repository = repository,
                    cloudRepository = cloudRepo,
                    localDb = localDb,
                    prefs = prefs,
                    firebaseUid = firebaseUid,
                    firebaseEmail = firebaseEmail
                )
            }
        }
    }
}
