package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.AcademyDatabase
import com.example.data.models.*
import com.example.data.remote.FirestoreCloudMappers.ACADEMY_ID
import com.example.data.remote.FirestoreCloudMappers.getStringList
import com.example.data.remote.FirestoreCloudMappers.sanitizeId
import com.example.data.remote.FirestoreCloudMappers.toAcademyClass
import com.example.data.remote.FirestoreCloudMappers.toAcademyExam
import com.example.data.remote.FirestoreCloudMappers.toAnnouncement
import com.example.data.remote.FirestoreCloudMappers.toAttendanceRecord
import com.example.data.remote.FirestoreCloudMappers.toCloudUserProfile
import com.example.data.remote.FirestoreCloudMappers.toExamMarkRecord
import com.example.data.remote.FirestoreCloudMappers.toFeeChallan
import com.example.data.remote.FirestoreCloudMappers.toFeePayment
import com.example.data.remote.FirestoreCloudMappers.toFsRecord
import com.example.data.remote.FirestoreCloudMappers.toLeaveRequest
import com.example.data.remote.FirestoreCloudMappers.toMadniQaidaLogRecord
import com.example.data.remote.FirestoreCloudMappers.toNamazLogRecord
import com.example.data.remote.FirestoreCloudMappers.toQuranProgressRecord
import com.example.data.remote.FirestoreCloudMappers.toStudent
import com.example.data.remote.FirestoreCloudMappers.toTeacher
import com.example.data.remote.FirestoreCloudMappers.toUserAccount
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class FirestoreCloudRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    // --- 1. Authentication, Role Ticket Claiming & Cloud Bootstrap ---

    suspend fun authenticateAndClaimSession(
        uid: String,
        email: String?,
        usernameInput: String,
        passwordInput: String,
        requiredRole: UserRole? = null
    ): Result<CloudUserProfile> {
        val cleanUser = usernameInput.trim()
        val cleanPass = passwordInput.trim()
        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your assigned username and password."))
        }

        val ticketHash = FirestoreCloudMappers.computeTicketHash(cleanUser, cleanPass)
        val ticketPath = "auth_tickets/$ticketHash"
        try {
            val ticketSnap = db.collection("auth_tickets").document(ticketHash).get().await()

            // Fresh cloud database bootstrap OR self-healing recovery when Admin ticket was orphaned
            if (!ticketSnap.exists()) {
                val isBootstrapAdmin = cleanUser.equals("admin", ignoreCase = true) && cleanPass == "Admin@123"
                val isVerifiedAdminEmail = email?.equals("mra788915@gmail.com", ignoreCase = true) == true
                if (requiredRole == null || requiredRole == UserRole.ADMIN) {
                    val academySnap = runCatching {
                        db.collection("academies").document(ACADEMY_ID).get().await()
                    }.getOrNull()

                    if (academySnap == null || !academySnap.exists()) {
                        if (isBootstrapAdmin || isVerifiedAdminEmail) {
                            val profile = CloudUserProfile(
                                userId = uid,
                                displayName = "Nazim-e-Aala (Admin)",
                                role = UserRole.ADMIN,
                                ticketHash = ticketHash,
                                academyId = ACADEMY_ID
                            )
                            writeUserSessionDocument(profile)
                            FirestoreAcademySeeder.bootstrapAcademyWithInitialData(db, uid)
                            if (!isBootstrapAdmin && cleanUser.length >= 3 && cleanPass.length >= 4) {
                                val customAdmin = UserAccount(
                                    id = "acc_admin_main",
                                    name = "Nazim-e-Aala (Admin)",
                                    username = cleanUser,
                                    password = cleanPass,
                                    email = email ?: "admin@hafizquranacademy.edu",
                                    role = UserRole.ADMIN,
                                    phone = "03707304820"
                                )
                                upsertVaultAndTicket(customAdmin, uid, activeCallerUid = uid)
                            }
                            return Result.success(profile)
                        }
                    } else {
                        val canonicalAdminUid = academySnap.getString("adminUid")?.takeIf { it.isNotBlank() } ?: uid
                        val isVerifiedOwner = (canonicalAdminUid == uid) || isVerifiedAdminEmail
                        if (isVerifiedOwner && cleanUser.length >= 3 && cleanPass.length >= 4) {
                            val vaultSnap = runCatching {
                                db.collection("admin_vault").document("acc_admin_main").get().await()
                            }.getOrNull()
                            val vaultExists = vaultSnap != null && vaultSnap.exists()
                            val vaultUser = vaultSnap?.getString("username")?.trim().orEmpty()
                            val vaultPass = vaultSnap?.getString("password")?.trim().orEmpty()
                            val vaultTicketHash = vaultSnap?.getString("ticketHash").orEmpty()

                            val matchesVaultCredentials = vaultExists &&
                                    vaultUser.equals(cleanUser, ignoreCase = true) &&
                                    vaultPass == cleanPass

                            val isAdminTicketOrphaned = if (!vaultExists || vaultTicketHash.length != 64) {
                                true
                            } else {
                                val existingAdminTicket = runCatching {
                                    db.collection("auth_tickets").document(vaultTicketHash).get().await()
                                }.getOrNull()
                                existingAdminTicket == null || !existingAdminTicket.exists()
                            }

                            if (matchesVaultCredentials || isAdminTicketOrphaned) {
                                val recoveredAccount = UserAccount(
                                    id = "acc_admin_main",
                                    name = vaultSnap?.getString("name")?.takeIf { it.isNotBlank() } ?: "Nazim-e-Aala (Admin)",
                                    username = cleanUser,
                                    password = cleanPass,
                                    email = vaultSnap?.getString("email")?.takeIf { it.isNotBlank() } ?: (email ?: "admin@hafizquranacademy.edu"),
                                    role = UserRole.ADMIN,
                                    phone = vaultSnap?.getString("phone") ?: "03707304820",
                                    isActive = true
                                )
                                val healedProfile = upsertVaultAndTicket(
                                    account = recoveredAccount,
                                    adminUid = canonicalAdminUid,
                                    activeCallerUid = uid
                                ) ?: CloudUserProfile(
                                    userId = uid,
                                    displayName = recoveredAccount.name,
                                    role = UserRole.ADMIN,
                                    ticketHash = ticketHash,
                                    academyId = ACADEMY_ID
                                )
                                return Result.success(healedProfile)
                            }
                        }
                    }
                }
                return Result.failure(IllegalArgumentException("Invalid username or password. Credentials are issued only by Academy Admin."))
            }

            val ticketRoleStr = ticketSnap.getString("role") ?: "PARENT"
            val ticketRole = runCatching { UserRole.valueOf(ticketRoleStr) }.getOrDefault(UserRole.PARENT)
            val isActive = ticketSnap.getBoolean("isActive") ?: false

            if (requiredRole != null && ticketRole != requiredRole) {
                return Result.failure(
                    SecurityException("Access Denied: Yeh login ${ticketRole.name} App ka hai aur ${requiredRole.name} App mein istemal nahi ho sakta.")
                )
            }
            if (!isActive) {
                return Result.failure(
                    SecurityException("Yeh login account Academy Administration ki taraf se suspend hai.")
                )
            }

            val profile = CloudUserProfile(
                userId = uid,
                displayName = ticketSnap.getString("displayName") ?: cleanUser,
                role = ticketRole,
                ticketHash = ticketHash,
                academyId = ticketSnap.getString("academyId") ?: ACADEMY_ID,
                teacherId = ticketSnap.getString("teacherId") ?: "",
                assignedClassIds = ticketSnap.getStringList("assignedClassIds"),
                linkedStudentIds = ticketSnap.getStringList("linkedStudentIds")
            )
            writeUserSessionDocument(profile)
            return Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ticketPath)
            return Result.failure(e)
        }
    }

    /**
     * Verified Academy Owner Recovery / Reset:
     * Allows the Academy Owner (matching `academies/hqa_main.adminUid` or verified bootstrap owner email)
     * to reset or change the primary Admin username and password directly without losing any Academy data.
     */
    suspend fun recoverOrResetAdminCredentials(
        uid: String,
        email: String?,
        newUsername: String,
        newPassword: String,
        displayName: String = "Nazim-e-Aala (Admin)"
    ): Result<CloudUserProfile> {
        val cleanUser = newUsername.trim()
        val cleanPass = newPassword.trim()
        if (cleanUser.length < 3) {
            return Result.failure(IllegalArgumentException("Admin username kam az kam 3 characters ka hona chahiye."))
        }
        if (cleanPass.length < 4) {
            return Result.failure(IllegalArgumentException("Admin password kam az kam 4 characters ka hona chahiye."))
        }

        return try {
            val isVerifiedAdminEmail = email?.equals("mra788915@gmail.com", ignoreCase = true) == true
            val academySnap = runCatching {
                db.collection("academies").document(ACADEMY_ID).get().await()
            }.getOrNull()

            if (academySnap == null || !academySnap.exists()) {
                val newHash = FirestoreCloudMappers.computeTicketHash(cleanUser, cleanPass)
                val profile = CloudUserProfile(
                    userId = uid,
                    displayName = displayName.ifBlank { "Nazim-e-Aala (Admin)" },
                    role = UserRole.ADMIN,
                    ticketHash = newHash,
                    academyId = ACADEMY_ID
                )
                writeUserSessionDocument(profile)
                FirestoreAcademySeeder.bootstrapAcademyWithInitialData(db, uid)
                val adminAcc = UserAccount(
                    id = "acc_admin_main",
                    name = profile.displayName,
                    username = cleanUser,
                    password = cleanPass,
                    email = email ?: "admin@hafizquranacademy.edu",
                    role = UserRole.ADMIN,
                    phone = "03707304820",
                    isActive = true
                )
                upsertVaultAndTicket(adminAcc, uid, activeCallerUid = uid)
                return Result.success(profile)
            }

            val canonicalAdminUid = academySnap.getString("adminUid")?.takeIf { it.isNotBlank() } ?: uid
            val isVerifiedOwner = (canonicalAdminUid == uid) || isVerifiedAdminEmail
            if (!isVerifiedOwner) {
                return Result.failure(
                    SecurityException("Yeh option sirf Academy Owner ke Google account par kaam karta hai. Jis Google account se Academy setup ki thi us se connect karein.")
                )
            }

            val existingVault = runCatching {
                db.collection("admin_vault").document("acc_admin_main").get().await()
            }.getOrNull()

            val adminAcc = UserAccount(
                id = "acc_admin_main",
                name = existingVault?.getString("name")?.takeIf { it.isNotBlank() } ?: displayName.ifBlank { "Nazim-e-Aala (Admin)" },
                username = cleanUser,
                password = cleanPass,
                email = existingVault?.getString("email")?.takeIf { it.isNotBlank() } ?: (email ?: "admin@hafizquranacademy.edu"),
                role = UserRole.ADMIN,
                phone = existingVault?.getString("phone") ?: "03707304820",
                isActive = true
            )

            // Also clean up any secondary orphaned ADMIN vault accounts if they exist
            val extraAdminDocs = runCatching {
                db.collection("admin_vault").whereEqualTo("adminUid", canonicalAdminUid).get().await()
                    .documents.filter { it.id != "acc_admin_main" && it.getString("role") == "ADMIN" }
            }.getOrDefault(emptyList())

            val updatedProfile = upsertVaultAndTicket(
                account = adminAcc,
                adminUid = canonicalAdminUid,
                activeCallerUid = uid
            ) ?: CloudUserProfile(
                userId = uid,
                displayName = adminAcc.name,
                role = UserRole.ADMIN,
                ticketHash = FirestoreCloudMappers.computeTicketHash(cleanUser, cleanPass),
                academyId = ACADEMY_ID
            )

            for (extra in extraAdminDocs) {
                val extraHash = extra.getString("ticketHash")
                if (!extraHash.isNullOrBlank() && extraHash != updatedProfile.ticketHash) {
                    runCatching { db.collection("auth_tickets").document(extraHash).delete().await() }
                }
                runCatching { extra.reference.delete().await() }
            }

            Result.success(updatedProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admin_vault/acc_admin_main")
            Result.failure(e)
        }
    }

    private suspend fun writeUserSessionDocument(profile: CloudUserProfile) {
        val docRef = db.collection("users").document(profile.userId)
        val existingSnap = runCatching { docRef.get().await() }.getOrNull()
        val existingCreatedAt = existingSnap?.takeIf { it.exists() }
            ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val payload = mapOf<String, Any>(
            "userId" to profile.userId,
            "displayName" to profile.displayName.take(120).ifBlank { "Portal User" },
            "role" to profile.role.name,
            "ticketHash" to profile.ticketHash,
            "academyId" to profile.academyId,
            "teacherId" to profile.teacherId.take(128),
            "assignedClassIds" to profile.assignedClassIds.take(20),
            "linkedStudentIds" to profile.linkedStudentIds.take(20),
            "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun restoreCloudSession(uid: String): CloudUserProfile? {
        val path = "users/$uid"
        return try {
            val snap = db.collection("users").document(uid).get().await()
            val profile = snap.toCloudUserProfile() ?: return null
            val ticketSnap = db.collection("auth_tickets").document(profile.ticketHash).get().await()
            if (!ticketSnap.exists() || ticketSnap.getBoolean("isActive") == false) {
                runCatching { db.collection("users").document(uid).delete().await() }
                return null
            }
            val updatedAssigned = ticketSnap.getStringList("assignedClassIds")
            val updatedLinked = ticketSnap.getStringList("linkedStudentIds")
            val refreshed = profile.copy(
                displayName = ticketSnap.getString("displayName") ?: profile.displayName,
                assignedClassIds = updatedAssigned,
                linkedStudentIds = updatedLinked
            )
            if (updatedAssigned != profile.assignedClassIds || updatedLinked != profile.linkedStudentIds) {
                writeUserSessionDocument(refreshed)
            }
            refreshed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun clearCloudSession(uid: String) {
        val docRef = db.collection("users").document(uid)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }

    suspend fun resolveAcademyAdminUid(fallbackUid: String): String {
        val snap = runCatching {
            db.collection("academies").document(ACADEMY_ID).get().await()
        }.getOrNull()
        return snap?.getString("adminUid")?.takeIf { it.isNotBlank() } ?: fallbackUid
    }

    // --- 2. Direct Reactive Flows & Scoped Reads (Verified in RuleTest & Realtime Sync) ---

    fun observeAdminStudents(adminUid: String): Flow<List<Student>> =
        db.collection("students")
            .whereEqualTo("adminUid", adminUid)
            .snapshots()
            .map { qs -> qs.documents.mapNotNull { it.toStudent() } }

    fun observeStudentById(studentId: String): Flow<Student?> =
        db.collection("students").document(sanitizeId(studentId))
            .snapshots()
            .map { it.toStudent() }

    fun observeChallanById(challanId: String): Flow<FeeChallan?> =
        db.collection("fee_challans").document(sanitizeId(challanId))
            .snapshots()
            .map { it.toFeeChallan() }

    suspend fun getStudentById(studentId: String): Result<Student> {
        val docRef = db.collection("students").document(sanitizeId(studentId))
        return try {
            val snap = docRef.get().await()
            val student = snap.toStudent()
                ?: return Result.failure(NoSuchElementException("Student not found"))
            Result.success(student)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getAdminVaultAccount(accountId: String): Result<UserAccount> {
        val docRef = db.collection("admin_vault").document(sanitizeId(accountId))
        return try {
            val snap = docRef.get().await()
            val acc = snap.toUserAccount()
                ?: return Result.failure(NoSuchElementException("Account not found"))
            Result.success(acc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    // --- 3. Real-Time Multi-Device Synchronization into Local Room Cache ---

    suspend fun syncRoleDataRealtime(
        uid: String,
        profile: CloudUserProfile,
        localDb: AcademyDatabase,
        onSessionRevoked: () -> Unit,
        onProfileUpdated: (CloudUserProfile) -> Unit
    ) = coroutineScope {
        val cleanup = localDb.syncCleanupDao()

        // Watch auth_ticket in real-time for suspension, deletion, or linked student/class updates
        launch {
            db.collection("auth_tickets").document(profile.ticketHash)
                .snapshots()
                .catch { e -> handleFirestoreError(e as? Exception ?: Exception(e), OperationType.GET, "auth_tickets/${profile.ticketHash}") }
                .collectLatest { snap ->
                    if (!snap.exists() || snap.getBoolean("isActive") == false) {
                        onSessionRevoked()
                        return@collectLatest
                    }
                    val newClasses = snap.getStringList("assignedClassIds")
                    val newStudents = snap.getStringList("linkedStudentIds")
                    if (newClasses != profile.assignedClassIds || newStudents != profile.linkedStudentIds) {
                        val updated = profile.copy(assignedClassIds = newClasses, linkedStudentIds = newStudents)
                        runCatching { writeUserSessionDocument(updated) }
                        onProfileUpdated(updated)
                    }
                }
        }

        when (profile.role) {
            UserRole.ADMIN -> syncAdminCollectionsRealtime(uid, localDb)
            UserRole.TEACHER -> {
                cleanup.clearAllChallans()
                cleanup.clearAllPayments()
                cleanup.clearAllFsRecords()
                localDb.userDao().clearAllUsers()
                syncTeacherRealtime(profile, localDb)
            }
            UserRole.PARENT -> {
                cleanup.clearAllTeachers()
                cleanup.clearAllFsRecords()
                localDb.userDao().clearAllUsers()
                syncParentRealtime(profile, localDb)
            }
        }
    }

    private suspend fun syncAdminCollectionsRealtime(uid: String, localDb: AcademyDatabase) = coroutineScope {
        val adminUid = resolveAcademyAdminUid(uid)
        val cleanup = localDb.syncCleanupDao()

        launch {
            runCatching { reconcileAcademyLinksCloud(adminUid) }
        }
        launch {
            db.collection("classes").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "classes") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toAcademyClass() }
                    cleanup.clearAllClasses()
                    if (items.isNotEmpty()) localDb.classDao().insertClasses(items)
                }
        }
        launch {
            db.collection("teachers").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "teachers") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toTeacher() }
                    cleanup.clearAllTeachers()
                    if (items.isNotEmpty()) localDb.teacherDao().insertTeachers(items)
                }
        }
        launch {
            db.collection("students").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "students") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toStudent() }
                    cleanup.clearAllStudents()
                    if (items.isNotEmpty()) localDb.studentDao().insertStudents(items)
                }
        }
        launch {
            db.collection("attendance").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "attendance") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toAttendanceRecord() }
                    cleanup.clearAllAttendance()
                    if (items.isNotEmpty()) localDb.attendanceDao().insertAttendanceRecords(items)
                }
        }
        launch {
            db.collection("quran_progress").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "quran_progress") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toQuranProgressRecord() }
                    cleanup.clearAllProgress()
                    if (items.isNotEmpty()) localDb.quranProgressDao().insertProgressList(items)
                }
        }
        launch {
            db.collection("namaz_logs").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "namaz_logs") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toNamazLogRecord() }
                    cleanup.clearAllNamazLogs()
                    if (items.isNotEmpty()) localDb.namazLogDao().insertNamazLogs(items)
                }
        }
        launch {
            db.collection("madni_qaida_logs").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "madni_qaida_logs") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toMadniQaidaLogRecord() }
                    cleanup.clearAllQaidaLogs()
                    if (items.isNotEmpty()) localDb.madniQaidaLogDao().insertQaidaLogs(items)
                }
        }
        launch {
            db.collection("leave_requests").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "leave_requests") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toLeaveRequest() }
                    cleanup.clearAllLeaveRequests()
                    if (items.isNotEmpty()) localDb.leaveRequestDao().insertLeaveRequests(items)
                }
        }
        launch {
            db.collection("fee_challans").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "fee_challans") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toFeeChallan() }
                    cleanup.clearAllChallans()
                    if (items.isNotEmpty()) localDb.feeChallanDao().insertChallans(items)
                }
        }
        launch {
            db.collection("fee_payments").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "fee_payments") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toFeePayment() }
                    cleanup.clearAllPayments()
                    if (items.isNotEmpty()) localDb.feePaymentDao().insertPayments(items)
                }
        }
        launch {
            db.collection("fs_records").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "fs_records") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toFsRecord() }
                    cleanup.clearAllFsRecords()
                    if (items.isNotEmpty()) localDb.feeSabilillahDao().insertFsRecords(items)
                }
        }
        launch {
            db.collection("announcements").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "announcements") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toAnnouncement() }
                    cleanup.clearAllAnnouncements()
                    if (items.isNotEmpty()) localDb.announcementDao().insertAnnouncements(items)
                }
        }
        launch {
            db.collection("exams").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "exams") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toAcademyExam() }
                    cleanup.clearAllExams()
                    if (items.isNotEmpty()) localDb.examDao().insertExams(items)
                }
        }
        launch {
            db.collection("exam_marks").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "exam_marks") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toExamMarkRecord() }
                    cleanup.clearAllExamMarks()
                    if (items.isNotEmpty()) localDb.examMarkDao().insertMarks(items)
                }
        }
        launch {
            db.collection("admin_vault").whereEqualTo("adminUid", adminUid).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "admin_vault") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toUserAccount() }
                    localDb.userDao().clearAllUsers()
                    if (items.isNotEmpty()) localDb.userDao().insertUsers(items)
                }
        }
    }

    private suspend fun syncTeacherRealtime(profile: CloudUserProfile, localDb: AcademyDatabase) = coroutineScope {
        val cleanup = localDb.syncCleanupDao()
        val observedClassListeners = mutableSetOf<String>()

        suspend fun refreshTeacherPortalData() {
            val teacherDoc = if (profile.teacherId.isNotBlank()) {
                runCatching { db.collection("teachers").document(profile.teacherId).get().await() }.getOrNull()
            } else null
            val teacherObj = teacherDoc?.toTeacher()
            cleanup.clearAllTeachers()
            if (teacherObj != null) {
                localDb.teacherDao().insertTeacher(teacherObj)
            }

            val dirSnap = runCatching { db.collection("academies").document(ACADEMY_ID).get().await() }.getOrNull()
            val dirClassIds = dirSnap?.takeIf { it.exists() }?.getStringList("classIds") ?: emptyList()
            val dirStudentIds = dirSnap?.takeIf { it.exists() }?.getStringList("studentIds") ?: emptyList()
            val teacherClassIds = teacherObj?.let { FirestoreCloudMappers.parseIdList(it.assignedClassIds) } ?: emptyList()
            val teacherClassNames = teacherObj?.assignedClasses
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val candidateClassIds = (dirClassIds + profile.assignedClassIds + teacherClassIds)
                .map { sanitizeId(it) }
                .distinct()

            val matchedClassSnaps = mutableListOf<DocumentSnapshot>()
            val matchedClasses = mutableListOf<AcademyClass>()
            for (cid in candidateClassIds) {
                val cSnap = runCatching { db.collection("classes").document(cid).get().await() }.getOrNull()
                val cls = cSnap?.toAcademyClass() ?: continue
                val isAssignedToTeacher = cid in profile.assignedClassIds ||
                        cid in teacherClassIds ||
                        (profile.teacherId.isNotBlank() && cls.assignedTeacherId == profile.teacherId) ||
                        teacherClassNames.any { it.equals(cls.name, ignoreCase = true) }
                if (isAssignedToTeacher) {
                    matchedClassSnaps.add(cSnap)
                    matchedClasses.add(cls)
                }
            }

            cleanup.clearAllClasses()
            if (matchedClasses.isNotEmpty()) {
                localDb.classDao().insertClasses(matchedClasses)
            }

            val matchedClassIdSet = matchedClasses.map { it.id }.toSet()
            val studentIdsToFetch = mutableSetOf<String>()
            matchedClassSnaps.forEach { snap ->
                studentIdsToFetch.addAll(snap.getStringList("studentIds"))
            }
            // Fallback discovery: also inspect directory studentIds in case a student was admitted before class.studentIds synced
            studentIdsToFetch.addAll(dirStudentIds)

            val validStudentSnaps = studentIdsToFetch.mapNotNull { sid ->
                val snap = runCatching { db.collection("students").document(sanitizeId(sid)).get().await() }
                    .getOrNull()
                    ?.takeIf { it.exists() } ?: return@mapNotNull null
                val stud = snap.toStudent() ?: return@mapNotNull null
                val belongsToTeacher = stud.classId in matchedClassIdSet ||
                        stud.classId in profile.assignedClassIds ||
                        (profile.teacherId.isNotBlank() && stud.teacherId == profile.teacherId)
                if (belongsToTeacher) snap else null
            }

            cleanup.clearAllStudents()
            val students = validStudentSnaps.mapNotNull { it.toStudent() }
            if (students.isNotEmpty()) {
                localDb.studentDao().insertStudents(students)
            }
            syncStudentIndexedRecords(validStudentSnaps, includeBilling = false, localDb = localDb)

            // Fetch Admin-created Exams for Teacher's assigned classes (Teacher sees only their classes' exams)
            val dirExamIds = dirSnap?.takeIf { it.exists() }?.getStringList("examIds") ?: emptyList()
            val teacherExams = dirExamIds.mapNotNull { exId ->
                runCatching { db.collection("exams").document(sanitizeId(exId)).get().await().toAcademyExam() }.getOrNull()
            }.filter { ex ->
                ex.classId in matchedClassIdSet || ex.classId in profile.assignedClassIds
            }
            cleanup.clearAllExams()
            if (teacherExams.isNotEmpty()) {
                localDb.examDao().insertExams(teacherExams)
            }

            // Attach live snapshot listeners to any newly discovered assigned classes
            for (cid in matchedClassIdSet) {
                if (observedClassListeners.add(cid)) {
                    this@coroutineScope.launch {
                        db.collection("classes").document(cid).snapshots()
                            .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.GET, "classes/$cid") }
                            .collectLatest {
                                refreshTeacherPortalData()
                            }
                    }
                }
            }
        }

        if (profile.teacherId.isNotBlank()) {
            launch {
                db.collection("teachers").document(profile.teacherId).snapshots()
                    .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.GET, "teachers/${profile.teacherId}") }
                    .collectLatest {
                        refreshTeacherPortalData()
                    }
            }
        }

        launch {
            db.collection("academies").document(ACADEMY_ID).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.GET, "academies/$ACADEMY_ID") }
                .collectLatest { dirSnap ->
                    if (dirSnap.exists()) {
                        val anns = dirSnap.getStringList("announcementIds").mapNotNull { id ->
                            runCatching { db.collection("announcements").document(id).get().await().toAnnouncement() }.getOrNull()
                        }
                        cleanup.clearAllAnnouncements()
                        if (anns.isNotEmpty()) localDb.announcementDao().insertAnnouncements(anns)
                    }
                    refreshTeacherPortalData()
                }
        }

        for (classId in profile.assignedClassIds) {
            val cleanCid = sanitizeId(classId)
            if (observedClassListeners.add(cleanCid)) {
                launch {
                    db.collection("classes").document(cleanCid).snapshots()
                        .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.GET, "classes/$cleanCid") }
                        .collectLatest {
                            refreshTeacherPortalData()
                        }
                }
            }
        }
    }

    /**
     * Automatically heals and synchronizes any pre-existing Teacher <-> Class <-> Student <-> AuthTicket links
     * in Firestore when Admin connects so that Teachers always see every student in their assigned classes.
     */
    suspend fun reconcileAcademyLinksCloud(adminUid: String) {
        val classSnaps = runCatching { db.collection("classes").whereEqualTo("adminUid", adminUid).get().await() }.getOrNull()?.documents ?: return
        val teacherSnaps = runCatching { db.collection("teachers").whereEqualTo("adminUid", adminUid).get().await() }.getOrNull()?.documents ?: return
        val studentSnaps = runCatching { db.collection("students").whereEqualTo("adminUid", adminUid).get().await() }.getOrNull()?.documents ?: return
        val vaultSnaps = runCatching { db.collection("admin_vault").whereEqualTo("adminUid", adminUid).get().await() }.getOrNull()?.documents ?: emptyList()

        val classesById = classSnaps.mapNotNull { it.toAcademyClass() }.associateBy { it.id }.toMutableMap()
        val teachersById = teacherSnaps.mapNotNull { it.toTeacher() }.associateBy { it.id }.toMutableMap()
        val students = studentSnaps.mapNotNull { it.toStudent() }

        // 1. Reconcile Teacher <-> Class links (both ID and name matches)
        for (teacher in teachersById.values.toList()) {
            val explicitIds = FirestoreCloudMappers.parseIdList(teacher.assignedClassIds).toMutableSet()
            val nameTokens = teacher.assignedClasses.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (cls in classesById.values) {
                if (cls.assignedTeacherId == teacher.id || (cls.assignedTeacherId.isBlank() && nameTokens.any { it.equals(cls.name, ignoreCase = true) })) {
                    explicitIds.add(cls.id)
                    if (cls.assignedTeacherId != teacher.id || cls.assignedTeacherName != teacher.fullName) {
                        val updatedCls = cls.copy(assignedTeacherId = teacher.id, assignedTeacherName = teacher.fullName)
                        classesById[cls.id] = updatedCls
                    }
                }
            }
            val validIds = explicitIds.filter { it in classesById.keys }
            val resolvedNames = validIds.mapNotNull { classesById[it]?.name }.joinToString(", ")
            val resolvedIdsCsv = validIds.joinToString(",")
            if (resolvedIdsCsv != teacher.assignedClassIds || (validIds.isNotEmpty() && resolvedNames != teacher.assignedClasses)) {
                val updatedTeacher = teacher.copy(
                    assignedClassIds = resolvedIdsCsv,
                    assignedClasses = if (validIds.isNotEmpty()) resolvedNames else teacher.assignedClasses
                )
                teachersById[teacher.id] = updatedTeacher
                runCatching { createOrUpdateTeacherCloud(updatedTeacher, adminUid) }
            }
        }

        // 2. Reconcile Class studentIds & teacher assignments
        val studentsByClass = students.filter { it.status != StudentStatus.WITHDRAWN }.groupBy { sanitizeId(it.classId) }
        for (cSnap in classSnaps) {
            val origCls = cSnap.toAcademyClass() ?: continue
            val currentCls = classesById[origCls.id] ?: origCls
            val expectedStudentIds = (cSnap.getStringList("studentIds") + (studentsByClass[currentCls.id]?.map { sanitizeId(it.id) } ?: emptyList()))
                .distinct()
                .filter { sid -> students.any { sanitizeId(it.id) == sid && sanitizeId(it.classId) == currentCls.id } }
            if (expectedStudentIds != cSnap.getStringList("studentIds") ||
                currentCls.assignedTeacherId != origCls.assignedTeacherId ||
                currentCls.assignedTeacherName != origCls.assignedTeacherName
            ) {
                runCatching { createOrUpdateClassCloud(currentCls, adminUid, expectedStudentIds) }
            }
        }

        // 3. Ensure Students reflect their Class's assigned teacher
        for (stud in students) {
            val cls = classesById[sanitizeId(stud.classId)] ?: continue
            if (cls.assignedTeacherId.isNotBlank() && (stud.teacherId != cls.assignedTeacherId || stud.teacherName != cls.assignedTeacherName)) {
                runCatching {
                    createOrUpdateStudentCloud(
                        stud.copy(teacherId = cls.assignedTeacherId, teacherName = cls.assignedTeacherName),
                        adminUid
                    )
                }
            }
        }

        // 4. Reconcile Teacher Vault & AuthTicket assignedClassIds
        for (vSnap in vaultSnaps) {
            val acc = vSnap.toUserAccount() ?: continue
            if (acc.role == UserRole.TEACHER && acc.teacherId.isNotBlank()) {
                val teacher = teachersById[acc.teacherId] ?: continue
                if (acc.assignedClassIds != teacher.assignedClassIds) {
                    runCatching {
                        upsertVaultAndTicket(acc.copy(assignedClassIds = teacher.assignedClassIds), adminUid)
                    }
                }
            }
        }
    }

    private suspend fun syncParentRealtime(profile: CloudUserProfile, localDb: AcademyDatabase) = coroutineScope {
        val cleanup = localDb.syncCleanupDao()

        launch {
            db.collection("academies").document(ACADEMY_ID).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.GET, "academies/$ACADEMY_ID") }
                .collectLatest { dirSnap ->
                    if (!dirSnap.exists()) return@collectLatest
                    val anns = dirSnap.getStringList("announcementIds").mapNotNull { id ->
                        runCatching { db.collection("announcements").document(id).get().await().toAnnouncement() }.getOrNull()
                    }
                    cleanup.clearAllAnnouncements()
                    if (anns.isNotEmpty()) localDb.announcementDao().insertAnnouncements(anns)
                }
        }

        if (profile.linkedStudentIds.isEmpty()) {
            cleanup.clearAllStudents()
            cleanup.clearAllChallans()
            cleanup.clearAllPayments()
            cleanup.clearAllAttendance()
            cleanup.clearAllProgress()
            cleanup.clearAllNamazLogs()
            cleanup.clearAllQaidaLogs()
            cleanup.clearAllLeaveRequests()
            cleanup.clearAllExams()
            cleanup.clearAllExamMarks()
            return@coroutineScope
        }

        launch {
            db.collection("leave_requests").whereEqualTo("parentUid", profile.userId).snapshots()
                .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.LIST, "leave_requests") }
                .collectLatest { qs ->
                    val items = qs.documents.mapNotNull { it.toLeaveRequest() }
                    if (items.isNotEmpty()) {
                        localDb.leaveRequestDao().insertLeaveRequests(items)
                    }
                }
        }

        for (studentId in profile.linkedStudentIds) {
            launch {
                db.collection("students").document(studentId).snapshots()
                    .catch { handleFirestoreError(it as? Exception ?: Exception(it), OperationType.GET, "students/$studentId") }
                    .collectLatest {
                        refreshParentChildren(profile.linkedStudentIds, localDb)
                    }
            }
        }
    }

    private suspend fun refreshParentChildren(linkedStudentIds: List<String>, localDb: AcademyDatabase) {
        val cleanup = localDb.syncCleanupDao()
        val studentSnaps = linkedStudentIds.mapNotNull { sid ->
            runCatching { db.collection("students").document(sid).get().await() }.getOrNull()?.takeIf { it.exists() }
        }
        val students = studentSnaps.mapNotNull { it.toStudent() }
        cleanup.clearAllStudents()
        if (students.isNotEmpty()) localDb.studentDao().insertStudents(students)

        val classIds = students.map { it.classId }.distinct()
        val classes = classIds.mapNotNull { cid ->
            runCatching { db.collection("classes").document(cid).get().await().toAcademyClass() }.getOrNull()
        }
        cleanup.clearAllClasses()
        if (classes.isNotEmpty()) localDb.classDao().insertClasses(classes)

        val dirSnap = runCatching { db.collection("academies").document(ACADEMY_ID).get().await() }.getOrNull()
        val dirExamIds = dirSnap?.takeIf { it.exists() }?.getStringList("examIds") ?: emptyList()
        val childExams = dirExamIds.mapNotNull { exId ->
            runCatching { db.collection("exams").document(sanitizeId(exId)).get().await().toAcademyExam() }.getOrNull()
        }.filter { it.classId in classIds }
        cleanup.clearAllExams()
        if (childExams.isNotEmpty()) localDb.examDao().insertExams(childExams)

        syncStudentIndexedRecords(studentSnaps, includeBilling = true, localDb = localDb)
    }

    private suspend fun syncStudentIndexedRecords(
        studentSnaps: List<DocumentSnapshot>,
        includeBilling: Boolean,
        localDb: AcademyDatabase
    ) {
        val cleanup = localDb.syncCleanupDao()
        val attIds = studentSnaps.flatMap { it.getStringList("recentAttendanceIds") }.distinct()
        val progIds = studentSnaps.flatMap { it.getStringList("recentProgressIds") }.distinct()
        val namazIds = studentSnaps.flatMap { it.getStringList("recentNamazIds") }.distinct()
        val qaidaIds = studentSnaps.flatMap { it.getStringList("recentQaidaIds") }.distinct()
        val leaveIds = studentSnaps.flatMap { it.getStringList("recentLeaveIds") }.distinct()
        val markIds = studentSnaps.flatMap { it.getStringList("recentExamMarkIds") }.distinct()

        val attRecords = attIds.mapNotNull { id ->
            runCatching { db.collection("attendance").document(id).get().await().toAttendanceRecord() }.getOrNull()
        }
        cleanup.clearAllAttendance()
        if (attRecords.isNotEmpty()) localDb.attendanceDao().insertAttendanceRecords(attRecords)

        val progRecords = progIds.mapNotNull { id ->
            runCatching { db.collection("quran_progress").document(id).get().await().toQuranProgressRecord() }.getOrNull()
        }
        cleanup.clearAllProgress()
        if (progRecords.isNotEmpty()) localDb.quranProgressDao().insertProgressList(progRecords)

        val namazRecords = namazIds.mapNotNull { id ->
            runCatching { db.collection("namaz_logs").document(id).get().await().toNamazLogRecord() }.getOrNull()
        }
        cleanup.clearAllNamazLogs()
        if (namazRecords.isNotEmpty()) localDb.namazLogDao().insertNamazLogs(namazRecords)

        val qaidaRecords = qaidaIds.mapNotNull { id ->
            runCatching { db.collection("madni_qaida_logs").document(id).get().await().toMadniQaidaLogRecord() }.getOrNull()
        }
        cleanup.clearAllQaidaLogs()
        if (qaidaRecords.isNotEmpty()) localDb.madniQaidaLogDao().insertQaidaLogs(qaidaRecords)

        val leaveRecords = leaveIds.mapNotNull { id ->
            runCatching { db.collection("leave_requests").document(id).get().await().toLeaveRequest() }.getOrNull()
        }
        cleanup.clearAllLeaveRequests()
        if (leaveRecords.isNotEmpty()) localDb.leaveRequestDao().insertLeaveRequests(leaveRecords)

        val examMarkRecords = markIds.mapNotNull { id ->
            runCatching { db.collection("exam_marks").document(id).get().await().toExamMarkRecord() }.getOrNull()
        }
        cleanup.clearAllExamMarks()
        if (examMarkRecords.isNotEmpty()) localDb.examMarkDao().insertMarks(examMarkRecords)

        if (includeBilling) {
            val chIds = studentSnaps.flatMap { it.getStringList("recentChallanIds") }.distinct()
            val payIds = studentSnaps.flatMap { it.getStringList("recentPaymentIds") }.distinct()

            val challans = chIds.mapNotNull { id ->
                runCatching { db.collection("fee_challans").document(id).get().await().toFeeChallan() }.getOrNull()
            }
            cleanup.clearAllChallans()
            if (challans.isNotEmpty()) localDb.feeChallanDao().insertChallans(challans)

            val payments = payIds.mapNotNull { id ->
                runCatching { db.collection("fee_payments").document(id).get().await().toFeePayment() }.getOrNull()
            }
            cleanup.clearAllPayments()
            if (payments.isNotEmpty()) localDb.feePaymentDao().insertPayments(payments)
        }
    }

    // --- 4. Cloud Write Operations (Admin, Teacher & Parent) ---

    suspend fun upsertVaultAndTicket(
        account: UserAccount,
        adminUid: String,
        activeCallerUid: String? = null
    ): CloudUserProfile? {
        val cleanId = sanitizeId(account.id)
        val vaultRef = db.collection("admin_vault").document(cleanId)
        try {
            val existingVault = runCatching { vaultRef.get().await() }.getOrNull()
            val oldTicketHash = existingVault?.takeIf { it.exists() }?.getString("ticketHash")
            val existingVaultCreatedAt = existingVault?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val candidateHash = FirestoreCloudMappers.computeTicketHash(account.username, account.password)
            val existingTicket = runCatching { db.collection("auth_tickets").document(candidateHash).get().await() }.getOrNull()
            val existingTicketCreatedAt = existingTicket?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val (newTicketHash, ticketPayload) = FirestoreCloudMappers.buildAuthTicketPayload(account, adminUid, existingTicketCreatedAt)
            val vaultPayload = FirestoreCloudMappers.buildVaultPayload(account.copy(id = cleanId), adminUid, newTicketHash, existingVaultCreatedAt)

            // CRITICAL ORDER: Write new auth_ticket and vault FIRST, update active Admin session SECOND,
            // and delete oldTicketHash LAST so active Admin sessions are never prematurely revoked!
            db.collection("auth_tickets").document(newTicketHash).set(ticketPayload).await()
            vaultRef.set(vaultPayload).await()

            var updatedCallerProfile: CloudUserProfile? = null
            if (account.role == UserRole.ADMIN && !activeCallerUid.isNullOrBlank()) {
                val adminProfile = CloudUserProfile(
                    userId = activeCallerUid,
                    displayName = account.name.take(120).ifBlank { "Nazim-e-Aala (Admin)" },
                    role = UserRole.ADMIN,
                    ticketHash = newTicketHash,
                    academyId = ACADEMY_ID
                )
                writeUserSessionDocument(adminProfile)
                updatedCallerProfile = adminProfile
            }

            if (!oldTicketHash.isNullOrBlank() && oldTicketHash != newTicketHash) {
                runCatching { db.collection("auth_tickets").document(oldTicketHash).delete().await() }
            }

            return updatedCallerProfile
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, vaultRef.path)
            throw e
        }
    }

    suspend fun deleteVaultAndTicket(accountId: String) {
        val cleanId = sanitizeId(accountId)
        val vaultRef = db.collection("admin_vault").document(cleanId)
        try {
            val existing = vaultRef.get().await()
            val ticketHash = existing.getString("ticketHash")
            if (!ticketHash.isNullOrBlank()) {
                runCatching { db.collection("auth_tickets").document(ticketHash).delete().await() }
            }
            if (existing.exists()) {
                vaultRef.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, vaultRef.path)
            throw e
        }
    }

    suspend fun createOrUpdateClassCloud(
        academyClass: AcademyClass,
        adminUid: String,
        explicitStudentIds: List<String>? = null
    ) {
        val cid = sanitizeId(academyClass.id)
        val classRef = db.collection("classes").document(cid)
        try {
            val existing = classRef.get().await()
            val studentIds = explicitStudentIds
                ?: if (existing.exists()) existing.getStringList("studentIds") else emptyList()
            val createdAt = existing.takeIf { it.exists() }?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            classRef.set(FirestoreCloudMappers.buildClassPayload(academyClass.copy(id = cid), adminUid, studentIds, createdAt)).await()
            appendIdToAcademyDirectory("classIds", cid, 50)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, classRef.path)
            throw e
        }
    }

    suspend fun deleteClassCloud(classId: String) {
        val cid = sanitizeId(classId)
        val classRef = db.collection("classes").document(cid)
        try {
            classRef.delete().await()
            removeIdFromAcademyDirectory("classIds", cid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, classRef.path)
            throw e
        }
    }

    suspend fun createOrUpdateTeacherCloud(teacher: Teacher, adminUid: String) {
        val tid = sanitizeId(teacher.id)
        val ref = db.collection("teachers").document(tid)
        try {
            val existing = ref.get().await()
            val createdAt = existing.takeIf { it.exists() }?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ref.set(FirestoreCloudMappers.buildTeacherPayload(teacher.copy(id = tid), adminUid, createdAt)).await()
            appendIdToAcademyDirectory("teacherIds", tid, 60)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun deleteTeacherCloud(teacherId: String) {
        val tid = sanitizeId(teacherId)
        val ref = db.collection("teachers").document(tid)
        try {
            ref.delete().await()
            removeIdFromAcademyDirectory("teacherIds", tid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun createOrUpdateStudentCloud(student: Student, adminUid: String) {
        val sid = sanitizeId(student.id)
        val cid = sanitizeId(student.classId)
        val ref = db.collection("students").document(sid)
        try {
            val existing = ref.get().await()
            val oldClassId = existing.takeIf { it.exists() }?.getString("classId")
            val attIds = if (existing.exists()) existing.getStringList("recentAttendanceIds") else emptyList()
            val progIds = if (existing.exists()) existing.getStringList("recentProgressIds") else emptyList()
            val namazIds = if (existing.exists()) existing.getStringList("recentNamazIds") else emptyList()
            val qaidaIds = if (existing.exists()) existing.getStringList("recentQaidaIds") else emptyList()
            val leaveIds = if (existing.exists()) existing.getStringList("recentLeaveIds") else emptyList()
            val chIds = if (existing.exists()) existing.getStringList("recentChallanIds") else emptyList()
            val payIds = if (existing.exists()) existing.getStringList("recentPaymentIds") else emptyList()
            val markIds = if (existing.exists()) existing.getStringList("recentExamMarkIds") else emptyList()
            val createdAt = existing.takeIf { it.exists() }?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            ref.set(
                FirestoreCloudMappers.buildStudentPayload(
                    student.copy(id = sid, classId = cid),
                    adminUid,
                    attIds,
                    progIds,
                    namazIds,
                    qaidaIds,
                    leaveIds,
                    chIds,
                    payIds,
                    markIds,
                    createdAt
                )
            ).await()

            if (!oldClassId.isNullOrBlank() && oldClassId != cid) {
                removeStudentFromClass(oldClassId, sid)
            }
            addStudentToClass(cid, sid)
            appendIdToAcademyDirectory("studentIds", sid, 300)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun withdrawStudentPermanentlyCloud(studentId: String) {
        val sid = sanitizeId(studentId)
        val studentRef = db.collection("students").document(sid)
        try {
            val snap = studentRef.get().await()
            if (snap.exists()) {
                val classId = snap.getString("classId") ?: ""
                for (aid in snap.getStringList("recentAttendanceIds")) {
                    runCatching { db.collection("attendance").document(aid).delete().await() }
                }
                for (pid in snap.getStringList("recentProgressIds")) {
                    runCatching { db.collection("quran_progress").document(pid).delete().await() }
                }
                for (nid in snap.getStringList("recentNamazIds")) {
                    runCatching { db.collection("namaz_logs").document(nid).delete().await() }
                }
                for (qid in snap.getStringList("recentQaidaIds")) {
                    runCatching { db.collection("madni_qaida_logs").document(qid).delete().await() }
                }
                for (lid in snap.getStringList("recentLeaveIds")) {
                    runCatching { db.collection("leave_requests").document(lid).delete().await() }
                }
                for (payId in snap.getStringList("recentPaymentIds")) {
                    runCatching { db.collection("fee_payments").document(payId).delete().await() }
                }
                for (chId in snap.getStringList("recentChallanIds")) {
                    runCatching { db.collection("fee_challans").document(chId).delete().await() }
                }
                for (mkId in snap.getStringList("recentExamMarkIds")) {
                    runCatching { db.collection("exam_marks").document(mkId).delete().await() }
                }
                runCatching { db.collection("fs_records").document("fs_$sid").delete().await() }
                studentRef.delete().await()
                if (classId.isNotBlank()) {
                    removeStudentFromClass(classId, sid)
                }
            }
            removeIdFromAcademyDirectory("studentIds", sid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, studentRef.path)
            throw e
        }
    }

    suspend fun saveAttendanceCloud(records: List<AttendanceRecord>, callerUid: String) {
        val adminUid = resolveAcademyAdminUid(callerUid)
        for (rec in records) {
            val sid = sanitizeId(rec.studentId)
            val attId = sanitizeId("att_${sid}_${rec.date}")
            val attRef = db.collection("attendance").document(attId)
            try {
                val existing = attRef.get().await()
                if (existing.exists()) {
                    attRef.update(
                        mapOf(
                            "status" to rec.status.name,
                            "remarks" to rec.remarks.take(250),
                            "markedByTeacherId" to rec.markedByTeacherId.take(128).ifBlank { "Teacher" },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    attRef.set(
                        FirestoreCloudMappers.buildAttendancePayload(rec.copy(id = attId, studentId = sid), adminUid)
                    ).await()
                }
                val studRef = db.collection("students").document(sid)
                val studSnap = studRef.get().await()
                if (studSnap.exists()) {
                    val updatedList = (listOf(attId) + studSnap.getStringList("recentAttendanceIds"))
                        .distinct()
                        .take(40)
                    studRef.update(
                        mapOf(
                            "recentAttendanceIds" to updatedList,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, attRef.path)
                throw e
            }
        }
    }

    suspend fun deleteAttendanceRecordCloud(attendanceId: String, studentId: String) {
        val aid = sanitizeId(attendanceId)
        val sid = sanitizeId(studentId)
        val attRef = db.collection("attendance").document(aid)
        try {
            attRef.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updated = studSnap.getStringList("recentAttendanceIds").filter { it != aid }
                studRef.update(
                    mapOf(
                        "recentAttendanceIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, attRef.path)
            throw e
        }
    }

    suspend fun recordQuranProgressCloud(record: QuranProgressRecord, classId: String, callerUid: String) {
        upsertQuranProgressCloud(record, classId, callerUid)
    }

    suspend fun upsertQuranProgressCloud(record: QuranProgressRecord, classId: String, callerUid: String) {
        val adminUid = resolveAcademyAdminUid(callerUid)
        val sid = sanitizeId(record.studentId)
        val pid = sanitizeId(record.id)
        val progRef = db.collection("quran_progress").document(pid)
        try {
            val existing = runCatching { progRef.get().await() }.getOrNull()
            val createdAt = existing?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val effectiveAdminUid = existing?.takeIf { it.exists() }?.getString("adminUid")?.takeIf { it.isNotBlank() } ?: adminUid
            val effectiveClassId = existing?.takeIf { it.exists() }?.getString("classId")?.takeIf { it.isNotBlank() } ?: classId

            progRef.set(
                FirestoreCloudMappers.buildProgressPayload(
                    record.copy(id = pid, studentId = sid),
                    effectiveClassId,
                    effectiveAdminUid,
                    createdAt
                )
            ).await()
            if (existing == null || !existing.exists()) {
                val studRef = db.collection("students").document(sid)
                val studSnap = studRef.get().await()
                if (studSnap.exists()) {
                    val updatedList = (listOf(pid) + studSnap.getStringList("recentProgressIds"))
                        .distinct()
                        .take(30)
                    studRef.update(
                        mapOf(
                            "recentProgressIds" to updatedList,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, progRef.path)
            throw e
        }
    }

    suspend fun deleteQuranProgressCloud(progressId: String, studentId: String) {
        val pid = sanitizeId(progressId)
        val sid = sanitizeId(studentId)
        val progRef = db.collection("quran_progress").document(pid)
        try {
            progRef.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updatedList = studSnap.getStringList("recentProgressIds").filter { it != pid }
                studRef.update(
                    mapOf(
                        "recentProgressIds" to updatedList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, progRef.path)
            throw e
        }
    }

    suspend fun upsertNamazLogCloud(record: NamazLogRecord, classId: String, callerUid: String) {
        val adminUid = resolveAcademyAdminUid(callerUid)
        val sid = sanitizeId(record.studentId)
        val lid = sanitizeId(record.id)
        val logRef = db.collection("namaz_logs").document(lid)
        try {
            val existing = runCatching { logRef.get().await() }.getOrNull()
            val createdAt = existing?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val effectiveAdminUid = existing?.takeIf { it.exists() }?.getString("adminUid")?.takeIf { it.isNotBlank() } ?: adminUid
            val effectiveClassId = existing?.takeIf { it.exists() }?.getString("classId")?.takeIf { it.isNotBlank() } ?: classId

            logRef.set(
                FirestoreCloudMappers.buildNamazLogPayload(
                    record.copy(id = lid, studentId = sid),
                    effectiveClassId,
                    effectiveAdminUid,
                    createdAt
                )
            ).await()
            if (existing == null || !existing.exists()) {
                val studRef = db.collection("students").document(sid)
                val studSnap = studRef.get().await()
                if (studSnap.exists()) {
                    val updatedList = (listOf(lid) + studSnap.getStringList("recentNamazIds"))
                        .distinct()
                        .take(30)
                    studRef.update(
                        mapOf(
                            "recentNamazIds" to updatedList,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logRef.path)
            throw e
        }
    }

    suspend fun deleteNamazLogCloud(logId: String, studentId: String) {
        val lid = sanitizeId(logId)
        val sid = sanitizeId(studentId)
        val logRef = db.collection("namaz_logs").document(lid)
        try {
            logRef.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updatedList = studSnap.getStringList("recentNamazIds").filter { it != lid }
                studRef.update(
                    mapOf(
                        "recentNamazIds" to updatedList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, logRef.path)
            throw e
        }
    }

    suspend fun upsertMadniQaidaLogCloud(record: MadniQaidaLogRecord, classId: String, callerUid: String) {
        val adminUid = resolveAcademyAdminUid(callerUid)
        val sid = sanitizeId(record.studentId)
        val lid = sanitizeId(record.id)
        val logRef = db.collection("madni_qaida_logs").document(lid)
        try {
            val existing = runCatching { logRef.get().await() }.getOrNull()
            val createdAt = existing?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val effectiveAdminUid = existing?.takeIf { it.exists() }?.getString("adminUid")?.takeIf { it.isNotBlank() } ?: adminUid
            val effectiveClassId = existing?.takeIf { it.exists() }?.getString("classId")?.takeIf { it.isNotBlank() } ?: classId

            logRef.set(
                FirestoreCloudMappers.buildMadniQaidaLogPayload(
                    record.copy(id = lid, studentId = sid),
                    effectiveClassId,
                    effectiveAdminUid,
                    createdAt
                )
            ).await()
            if (existing == null || !existing.exists()) {
                val studRef = db.collection("students").document(sid)
                val studSnap = studRef.get().await()
                if (studSnap.exists()) {
                    val updatedList = (listOf(lid) + studSnap.getStringList("recentQaidaIds"))
                        .distinct()
                        .take(30)
                    studRef.update(
                        mapOf(
                            "recentQaidaIds" to updatedList,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logRef.path)
            throw e
        }
    }

    suspend fun deleteMadniQaidaLogCloud(logId: String, studentId: String) {
        val lid = sanitizeId(logId)
        val sid = sanitizeId(studentId)
        val logRef = db.collection("madni_qaida_logs").document(lid)
        try {
            logRef.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updatedList = studSnap.getStringList("recentQaidaIds").filter { it != lid }
                studRef.update(
                    mapOf(
                        "recentQaidaIds" to updatedList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, logRef.path)
            throw e
        }
    }

    suspend fun submitLeaveRequestCloud(request: LeaveRequest, callerUid: String): LeaveRequest {
        val adminUid = resolveAcademyAdminUid(callerUid)
        val sid = sanitizeId(request.studentId)
        val lid = sanitizeId(request.id)
        val leaveRef = db.collection("leave_requests").document(lid)
        val effectiveRequest = request.copy(
            id = lid,
            studentId = sid,
            parentUid = request.parentUid.ifBlank { callerUid },
            status = ApprovalStatus.PENDING,
            reviewerName = "",
            reviewerRole = "",
            reviewedDate = "",
            reviewerRemarks = ""
        )
        try {
            leaveRef.set(
                FirestoreCloudMappers.buildLeaveRequestPayload(effectiveRequest, adminUid)
            ).await()

            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updatedList = (listOf(lid) + studSnap.getStringList("recentLeaveIds"))
                    .distinct()
                    .take(24)
                studRef.update(
                    mapOf(
                        "recentLeaveIds" to updatedList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            return effectiveRequest
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, leaveRef.path)
            throw e
        }
    }

    suspend fun reviewLeaveRequestCloud(
        leaveId: String,
        approved: Boolean,
        reviewerName: String,
        reviewerRole: String,
        reviewedDate: String,
        reviewerRemarks: String
    ) {
        val lid = sanitizeId(leaveId)
        val leaveRef = db.collection("leave_requests").document(lid)
        val statusStr = if (approved) ApprovalStatus.APPROVED.name else ApprovalStatus.REJECTED.name
        try {
            leaveRef.update(
                mapOf(
                    "status" to statusStr,
                    "reviewerName" to reviewerName.take(120).ifBlank { "Reviewer" },
                    "reviewerRole" to reviewerRole.take(30).ifBlank { "ADMIN" },
                    "reviewedDate" to reviewedDate.take(25),
                    "reviewerRemarks" to reviewerRemarks.trim().take(350),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, leaveRef.path)
            throw e
        }
    }

    suspend fun deleteLeaveRequestCloud(leaveId: String, studentId: String) {
        val lid = sanitizeId(leaveId)
        val sid = sanitizeId(studentId)
        val leaveRef = db.collection("leave_requests").document(lid)
        try {
            leaveRef.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val existingList = studSnap.getStringList("recentLeaveIds")
                if (lid in existingList) {
                    studRef.update(
                        mapOf(
                            "recentLeaveIds" to existingList.filter { it != lid },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, leaveRef.path)
            throw e
        }
    }

    suspend fun createChallanCloud(challan: FeeChallan, adminUid: String) {
        upsertChallanCloud(challan, adminUid)
    }

    suspend fun upsertChallanCloud(challan: FeeChallan, adminUid: String) {
        val chId = sanitizeId(challan.id)
        val sid = sanitizeId(challan.studentId)
        val chRef = db.collection("fee_challans").document(chId)
        try {
            val existing = runCatching { chRef.get().await() }.getOrNull()
            val createdAt = existing?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            chRef.set(FirestoreCloudMappers.buildChallanPayload(challan.copy(id = chId, studentId = sid), adminUid, createdAt)).await()
            if (existing == null || !existing.exists()) {
                val studRef = db.collection("students").document(sid)
                val studSnap = studRef.get().await()
                if (studSnap.exists()) {
                    val updated = (listOf(chId) + studSnap.getStringList("recentChallanIds")).distinct().take(24)
                    studRef.update(
                        mapOf(
                            "recentChallanIds" to updated,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, chRef.path)
            throw e
        }
    }

    suspend fun deleteChallanCloud(challanId: String, studentId: String, paymentIds: List<String>) {
        val chId = sanitizeId(challanId)
        val sid = sanitizeId(studentId)
        val chRef = db.collection("fee_challans").document(chId)
        try {
            val cleanPayIds = paymentIds.map { sanitizeId(it) }.toSet()
            for (pid in cleanPayIds) {
                runCatching { db.collection("fee_payments").document(pid).delete().await() }
            }
            chRef.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updatedCh = studSnap.getStringList("recentChallanIds").filter { it != chId }
                val updatedPay = studSnap.getStringList("recentPaymentIds").filter { it !in cleanPayIds }
                studRef.update(
                    mapOf(
                        "recentChallanIds" to updatedCh,
                        "recentPaymentIds" to updatedPay,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, chRef.path)
            throw e
        }
    }

    suspend fun recordPaymentCloud(
        challanId: String,
        amount: Double,
        paymentMethod: String,
        receivedBy: String,
        remarks: String,
        transactionId: String,
        callerUid: String
    ): Result<FeePayment> {
        val chId = sanitizeId(challanId)
        val chRef = db.collection("fee_challans").document(chId)
        return try {
            val chSnap = chRef.get().await()
            val challan = chSnap.toFeeChallan()
                ?: return Result.failure(IllegalArgumentException("Challan not found in cloud."))
            val adminUid = chSnap.getString("adminUid")?.takeIf { it.isNotBlank() } ?: resolveAcademyAdminUid(callerUid)

            if (challan.status == ChallanStatus.PAID) {
                return Result.failure(IllegalStateException("This challan is already marked PAID."))
            }
            if (amount <= 0.0) {
                return Result.failure(IllegalArgumentException("Payment amount must be greater than 0."))
            }

            val newPaid = challan.amountPaid + amount
            val newRemaining = (challan.totalPayable - newPaid).coerceAtLeast(0.0)
            val newStatus = when {
                newRemaining <= 0.0 -> ChallanStatus.PAID
                newPaid > 0.0 -> ChallanStatus.PARTIALLY_PAID
                else -> ChallanStatus.UNPAID
            }
            val paymentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val receiptNo = if (transactionId.isNotBlank()) {
                "TID-${transactionId.trim()}"
            } else {
                "REC-${System.currentTimeMillis().toString().takeLast(6)}"
            }
            val payId = sanitizeId("pay_${UUID.randomUUID().toString().take(12)}")
            val payment = FeePayment(
                id = payId,
                challanId = chId,
                challanNumber = challan.challanNumber,
                studentId = challan.studentId,
                studentName = challan.studentName,
                amount = amount,
                paymentDate = paymentDate,
                paymentMethod = paymentMethod,
                receiptNumber = receiptNo,
                receivedBy = receivedBy,
                remarks = remarks.trim()
            )

            db.collection("fee_payments").document(payId)
                .set(FirestoreCloudMappers.buildPaymentPayload(payment, adminUid))
                .await()

            chRef.update(
                mapOf(
                    "amountPaid" to newPaid,
                    "remainingBalance" to newRemaining,
                    "status" to newStatus.name,
                    "paymentDate" to paymentDate.take(35),
                    "paymentMethod" to paymentMethod.take(80),
                    "transactionRef" to receiptNo.take(80),
                    "remarks" to remarks.trim().take(250),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val studRef = db.collection("students").document(sanitizeId(challan.studentId))
            val studSnap = studRef.get().await()
            if (studSnap.exists()) {
                val updatedPayIds = (listOf(payId) + studSnap.getStringList("recentPaymentIds")).distinct().take(24)
                studRef.update(
                    mapOf(
                        "recentPaymentIds" to updatedPayIds,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(payment)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePaymentAndSyncChallanCloud(
        payment: FeePayment,
        updatedChallan: FeeChallan?,
        adminUid: String
    ) {
        val pid = sanitizeId(payment.id)
        val payRef = db.collection("fee_payments").document(pid)
        try {
            val existing = runCatching { payRef.get().await() }.getOrNull()
            val createdAt = existing?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            payRef.set(FirestoreCloudMappers.buildPaymentPayload(payment.copy(id = pid), adminUid, createdAt)).await()
            if (updatedChallan != null) {
                upsertChallanCloud(updatedChallan, adminUid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, payRef.path)
            throw e
        }
    }

    suspend fun deletePaymentAndSyncChallanCloud(
        paymentId: String,
        studentId: String,
        updatedChallan: FeeChallan?,
        adminUid: String
    ) {
        val pid = sanitizeId(paymentId)
        val sid = sanitizeId(studentId)
        val payRef = db.collection("fee_payments").document(pid)
        try {
            payRef.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updated = studSnap.getStringList("recentPaymentIds").filter { it != pid }
                studRef.update(
                    mapOf(
                        "recentPaymentIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (updatedChallan != null) {
                upsertChallanCloud(updatedChallan, adminUid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, payRef.path)
            throw e
        }
    }

    suspend fun upsertFsRecordCloud(record: FeeSabilillahRecord, adminUid: String) {
        val fsId = sanitizeId(record.id)
        val ref = db.collection("fs_records").document(fsId)
        try {
            val existing = ref.get().await()
            val createdAt = existing.takeIf { it.exists() }?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ref.set(FirestoreCloudMappers.buildFsPayload(record.copy(id = fsId), adminUid, createdAt)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun deleteFsRecordCloud(fsId: String) {
        val cleanId = sanitizeId(fsId)
        val ref = db.collection("fs_records").document(cleanId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun createAnnouncementCloud(announcement: Announcement, adminUid: String) {
        upsertAnnouncementCloud(announcement, adminUid)
    }

    suspend fun upsertAnnouncementCloud(announcement: Announcement, adminUid: String) {
        val annId = sanitizeId(announcement.id)
        val ref = db.collection("announcements").document(annId)
        try {
            val existing = runCatching { ref.get().await() }.getOrNull()
            val createdAt = existing?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ref.set(FirestoreCloudMappers.buildAnnouncementPayload(announcement.copy(id = annId), adminUid, createdAt)).await()
            appendIdToAcademyDirectory("announcementIds", annId, 50)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun deleteAnnouncementCloud(announcementId: String) {
        val annId = sanitizeId(announcementId)
        val ref = db.collection("announcements").document(annId)
        try {
            ref.delete().await()
            removeIdFromAcademyDirectory("announcementIds", annId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun upsertExamCloud(exam: AcademyExam, adminUid: String) {
        val exId = sanitizeId(exam.id)
        val ref = db.collection("exams").document(exId)
        try {
            val existing = runCatching { ref.get().await() }.getOrNull()
            val createdAt = existing?.takeIf { it.exists() }
                ?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ref.set(FirestoreCloudMappers.buildExamPayload(exam.copy(id = exId), adminUid, createdAt)).await()
            appendIdToAcademyDirectory("examIds", exId, 200)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun deleteExamCloud(examId: String, linkedMarks: List<ExamMarkRecord>) {
        val exId = sanitizeId(examId)
        val ref = db.collection("exams").document(exId)
        try {
            for (mk in linkedMarks) {
                val mkId = sanitizeId(mk.id)
                val sid = sanitizeId(mk.studentId)
                runCatching { db.collection("exam_marks").document(mkId).delete().await() }
                val studRef = db.collection("students").document(sid)
                val studSnap = runCatching { studRef.get().await() }.getOrNull()
                if (studSnap != null && studSnap.exists()) {
                    val existingIds = studSnap.getStringList("recentExamMarkIds")
                    if (mkId in existingIds) {
                        runCatching {
                            studRef.update(
                                mapOf(
                                    "recentExamMarkIds" to existingIds.filter { it != mkId },
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
            }
            ref.delete().await()
            removeIdFromAcademyDirectory("examIds", exId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun upsertExamMarkCloud(mark: ExamMarkRecord, callerUid: String) {
        val adminUid = resolveAcademyAdminUid(callerUid)
        val mkId = sanitizeId(mark.id)
        val sid = sanitizeId(mark.studentId)
        val exId = sanitizeId(mark.examId)
        val cid = sanitizeId(mark.classId)
        val ref = db.collection("exam_marks").document(mkId)
        try {
            val existing = runCatching { ref.get().await() }.getOrNull()
            val total = mark.totalMarks.coerceIn(1.0, 1000.0)
            val passing = mark.passingMarks.coerceIn(0.0, total)
            val obtained = if (mark.isAbsent) 0.0 else mark.obtainedMarks.coerceIn(0.0, total)
            val computedGrade = mark.grade.ifBlank {
                ExamGradeHelper.computeGrade(obtained, total, passing, mark.isAbsent)
            }

            if (existing != null && existing.exists()) {
                // Use restricted update field set so both Admin & Assigned Teacher pass security rules cleanly
                ref.update(
                    mapOf(
                        "obtainedMarks" to obtained,
                        "grade" to computedGrade.take(40),
                        "isAbsent" to mark.isAbsent,
                        "remarks" to mark.remarks.trim().take(300),
                        "gradedByTeacherId" to mark.gradedByTeacherId.take(128).ifBlank { "Teacher" },
                        "gradedByTeacherName" to mark.gradedByTeacherName.take(120).ifBlank { "Academy Ustad" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val normalized = mark.copy(
                    id = mkId,
                    examId = exId,
                    studentId = sid,
                    classId = cid,
                    obtainedMarks = obtained,
                    totalMarks = total,
                    passingMarks = passing,
                    grade = computedGrade
                )
                ref.set(FirestoreCloudMappers.buildExamMarkPayload(normalized, adminUid)).await()
            }

            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val updatedIds = (listOf(mkId) + studSnap.getStringList("recentExamMarkIds"))
                    .distinct()
                    .take(60)
                studRef.update(
                    mapOf(
                        "recentExamMarkIds" to updatedIds,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun deleteExamMarkCloud(markId: String, studentId: String) {
        val mkId = sanitizeId(markId)
        val sid = sanitizeId(studentId)
        val ref = db.collection("exam_marks").document(mkId)
        try {
            ref.delete().await()
            val studRef = db.collection("students").document(sid)
            val studSnap = runCatching { studRef.get().await() }.getOrNull()
            if (studSnap != null && studSnap.exists()) {
                val existingIds = studSnap.getStringList("recentExamMarkIds")
                if (mkId in existingIds) {
                    studRef.update(
                        mapOf(
                            "recentExamMarkIds" to existingIds.filter { it != mkId },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    private suspend fun addStudentToClass(classId: String, studentId: String) {
        val ref = db.collection("classes").document(classId)
        val snap = runCatching { ref.get().await() }.getOrNull() ?: return
        if (!snap.exists()) return
        val updated = (snap.getStringList("studentIds") + studentId).distinct().take(60)
        ref.update(mapOf("studentIds" to updated, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    private suspend fun removeStudentFromClass(classId: String, studentId: String) {
        val ref = db.collection("classes").document(classId)
        val snap = runCatching { ref.get().await() }.getOrNull() ?: return
        if (!snap.exists()) return
        val updated = snap.getStringList("studentIds").filter { it != studentId }
        ref.update(mapOf("studentIds" to updated, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    private suspend fun appendIdToAcademyDirectory(field: String, id: String, maxItems: Int) {
        val ref = db.collection("academies").document(ACADEMY_ID)
        val snap = runCatching { ref.get().await() }.getOrNull() ?: return
        if (!snap.exists()) return
        val updated = (listOf(id) + snap.getStringList(field)).distinct().take(maxItems)
        ref.update(mapOf(field to updated, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    private suspend fun removeIdFromAcademyDirectory(field: String, id: String) {
        val ref = db.collection("academies").document(ACADEMY_ID)
        val snap = runCatching { ref.get().await() }.getOrNull() ?: return
        if (!snap.exists()) return
        val updated = snap.getStringList(field).filter { it != id }
        ref.update(mapOf(field to updated, "updatedAt" to FieldValue.serverTimestamp())).await()
    }
}
