package com.example.data.repository

import com.example.data.local.AcademyDatabase
import com.example.data.models.*
import com.example.data.remote.FirestoreCloudMappers
import com.example.data.remote.FirestoreCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AcademyRepository(
    private val database: AcademyDatabase,
    private val cloudRepo: FirestoreCloudRepository? = null
) {

    var activeCloudUid: String? = null

    private val studentDao = database.studentDao()
    private val teacherDao = database.teacherDao()
    private val classDao = database.classDao()
    private val attendanceDao = database.attendanceDao()
    private val quranProgressDao = database.quranProgressDao()
    private val feeChallanDao = database.feeChallanDao()
    private val feePaymentDao = database.feePaymentDao()
    private val feeSabilillahDao = database.feeSabilillahDao()
    private val announcementDao = database.announcementDao()
    private val scheduleDao = database.scheduleDao()
    private val userDao = database.userDao()

    var settings: AcademySettings = AcademySettings()
        private set

    // --- Flows ---
    val allStudents: Flow<List<Student>> = studentDao.getAllStudentsFlow()
    val activeStudents: Flow<List<Student>> = studentDao.getActiveStudentsFlow()
    val allTeachers: Flow<List<Teacher>> = teacherDao.getAllTeachersFlow()
    val allClasses: Flow<List<AcademyClass>> = classDao.getAllClassesFlow()
    val allChallans: Flow<List<FeeChallan>> = feeChallanDao.getAllChallansFlow()
    val allPayments: Flow<List<FeePayment>> = feePaymentDao.getAllPaymentsFlow()
    val allFsRecords: Flow<List<FeeSabilillahRecord>> = feeSabilillahDao.getAllFsRecordsFlow()
    val allAnnouncements: Flow<List<Announcement>> = announcementDao.getAllAnnouncementsFlow()
    val allProgress: Flow<List<QuranProgressRecord>> = quranProgressDao.getAllProgressFlow()
    val allAttendance: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendanceFlow()
    val allUsers: Flow<List<UserAccount>> = userDao.getAllUsersFlow()

    fun getStudentsByClass(classId: String): Flow<List<Student>> = studentDao.getStudentsByClassFlow(classId)
    fun getStudentsByTeacher(teacherId: String): Flow<List<Student>> = studentDao.getStudentsByTeacherFlow(teacherId)
    fun getAttendanceByClassAndDate(classId: String, date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceByClassAndDateFlow(classId, date)
    fun getAttendanceByStudent(studentId: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceByStudentFlow(studentId)
    fun getProgressByStudent(studentId: String): Flow<List<QuranProgressRecord>> =
        quranProgressDao.getProgressByStudentFlow(studentId)
    fun getChallansByStudent(studentId: String): Flow<List<FeeChallan>> =
        feeChallanDao.getChallansByStudentFlow(studentId)
    fun getPaymentsByChallan(challanId: String): Flow<List<FeePayment>> =
        feePaymentDao.getPaymentsByChallanFlow(challanId)
    fun getScheduleByClass(classId: String): Flow<List<ScheduleItem>> =
        scheduleDao.getScheduleByClassFlow(classId)

    private fun currentCloudUid(): String? =
        activeCloudUid?.takeIf { it.isNotBlank() && cloudRepo != null }

    private suspend fun resolveAdminUidOrNull(): String? {
        val uid = currentCloudUid() ?: return null
        return cloudRepo?.resolveAcademyAdminUid(uid)
    }

    // --- Strict Role-Isolated Portal Authentication (Local Fallback / Unit Tests) ---
    suspend fun authenticatePortalUser(
        loginInput: String,
        passwordInput: String,
        requiredRole: UserRole
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            if (cloudRepo == null) {
                seedInitialDataIfEmpty()
            }

            val trimmedLogin = loginInput.trim()
            if (trimmedLogin.isBlank() || passwordInput.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Please enter both username and password."))
            }

            val user = userDao.getUserByLoginId(trimmedLogin)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid username or password."))

            // Enforce strict single-portal role boundary
            if (user.role != requiredRole) {
                return@withContext Result.failure(
                    SecurityException("Access Denied: This login belongs to ${user.role.name} and cannot sign into ${requiredRole.name} Portal.")
                )
            }

            if (!user.isActive) {
                return@withContext Result.failure(
                    SecurityException("This account has been deactivated by Academy Administration.")
                )
            }

            if (user.password.trim() != passwordInput.trim()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid username or password."))
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserAccountById(userId: String): UserAccount? = withContext(Dispatchers.IO) {
        if (cloudRepo == null) {
            seedInitialDataIfEmpty()
        }
        userDao.getUserById(userId)
    }

    // --- Admin-Only Credential Vault Operations (Cloud + Local Cache) ---
    suspend fun savePortalCredentials(
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
        isActive: Boolean = true
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = username.trim()
            val cleanPassword = password.trim()
            val cleanName = name.trim()

            if (cleanName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Account holder name is required."))
            }
            if (cleanUsername.length < 3) {
                return@withContext Result.failure(IllegalArgumentException("Username must be at least 3 characters."))
            }
            if (cleanPassword.length < 4) {
                return@withContext Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
            }

            val existing = existingId?.let { userDao.getUserById(it) }
                ?: if (role == UserRole.ADMIN) {
                    userDao.getAllUsers().firstOrNull { it.role == UserRole.ADMIN }
                        ?: cloudRepo?.getAdminVaultAccount("acc_admin_main")?.getOrNull()
                } else null

            val conflict = userDao.getUserByUsername(cleanUsername)
            if (conflict != null && conflict.id != existing?.id) {
                return@withContext Result.failure(IllegalStateException("Username '$cleanUsername' is already assigned to another account."))
            }

            val cleanTeacherId = teacherId.trim()
            val linkedTeacher = if (role == UserRole.TEACHER && cleanTeacherId.isNotBlank()) {
                teacherDao.getTeacherById(cleanTeacherId)
            } else null

            val effectiveAssignedClassIds = if (role == UserRole.TEACHER) {
                val rawIds = assignedClassIds.trim().ifBlank { linkedTeacher?.assignedClassIds ?: "" }
                FirestoreCloudMappers.parseIdList(rawIds).joinToString(",")
            } else {
                ""
            }

            val defaultId = if (role == UserRole.ADMIN) "acc_admin_main" else UUID.randomUUID().toString()
            val accountId = FirestoreCloudMappers.sanitizeId(existing?.id ?: defaultId)
            val account = UserAccount(
                id = accountId,
                name = cleanName,
                username = cleanUsername,
                password = cleanPassword,
                email = email.trim(),
                role = role,
                phone = phone.trim(),
                teacherId = cleanTeacherId,
                assignedClassIds = effectiveAssignedClassIds,
                linkedStudentIds = linkedStudentIds.trim(),
                isActive = isActive,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )

            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertVaultAndTicket(
                    account = account,
                    adminUid = adminUid,
                    activeCallerUid = if (role == UserRole.ADMIN) currentCloudUid() else null
                )
            }
            userDao.insertUser(account)

            if (role == UserRole.TEACHER && linkedTeacher != null && linkedTeacher.assignedClassIds != effectiveAssignedClassIds) {
                syncTeacherClassLinksInternal(
                    teacher = linkedTeacher,
                    requestedClassIdsCsv = effectiveAssignedClassIds,
                    fallbackClassNames = linkedTeacher.assignedClasses,
                    adminUid = adminUid,
                    syncAccount = false
                )
            }

            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changeAdminOwnCredentials(
        newUsername: String,
        newPassword: String,
        displayName: String = "Nazim-e-Aala (Admin)"
    ): Result<Pair<UserAccount, com.example.data.remote.CloudUserProfile?>> = withContext(Dispatchers.IO) {
        try {
            val cleanUser = newUsername.trim()
            val cleanPass = newPassword.trim()
            if (cleanUser.length < 3) {
                return@withContext Result.failure(IllegalArgumentException("Admin username kam az kam 3 characters ka hona chahiye."))
            }
            if (cleanPass.length < 4) {
                return@withContext Result.failure(IllegalArgumentException("Admin password kam az kam 4 characters ka hona chahiye."))
            }

            val existingAdmin = userDao.getAllUsers().firstOrNull { it.role == UserRole.ADMIN }
                ?: cloudRepo?.getAdminVaultAccount("acc_admin_main")?.getOrNull()

            val conflict = userDao.getUserByUsername(cleanUser)
            if (conflict != null && conflict.role != UserRole.ADMIN && conflict.id != existingAdmin?.id) {
                return@withContext Result.failure(IllegalStateException("Username '$cleanUser' kisi Teacher ya Parent ko pehle se mila hua hai."))
            }

            val updatedAdmin = UserAccount(
                id = FirestoreCloudMappers.sanitizeId(existingAdmin?.id ?: "acc_admin_main"),
                name = displayName.trim().ifBlank { existingAdmin?.name?.ifBlank { "Nazim-e-Aala (Admin)" } ?: "Nazim-e-Aala (Admin)" },
                username = cleanUser,
                password = cleanPass,
                email = existingAdmin?.email?.ifBlank { "admin@hafizquranacademy.edu" } ?: "admin@hafizquranacademy.edu",
                role = UserRole.ADMIN,
                phone = existingAdmin?.phone?.ifBlank { "03707304820" } ?: "03707304820",
                isActive = true,
                createdAt = existingAdmin?.createdAt ?: System.currentTimeMillis()
            )

            val adminUid = resolveAdminUidOrNull()
            val updatedCloudProfile = if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertVaultAndTicket(
                    account = updatedAdmin,
                    adminUid = adminUid,
                    activeCallerUid = currentCloudUid()
                )
            } else null

            userDao.insertUser(updatedAdmin)
            Result.success(updatedAdmin to updatedCloudProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleUserAccountActive(userId: String, isActive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId)
                ?: return@withContext Result.failure(IllegalArgumentException("Account not found."))
            if (user.role == UserRole.ADMIN && !isActive) {
                val activeAdmins = userDao.getAllUsers().count { it.role == UserRole.ADMIN && it.isActive }
                if (activeAdmins <= 1) {
                    return@withContext Result.failure(IllegalStateException("Cannot deactivate the primary Admin account."))
                }
            }
            val updated = user.copy(isActive = isActive)
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertVaultAndTicket(updated, adminUid)
            }
            userDao.updateUser(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccount(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId)
                ?: return@withContext Result.failure(IllegalArgumentException("Account not found."))
            if (user.role == UserRole.ADMIN) {
                val admins = userDao.getAllUsers().count { it.role == UserRole.ADMIN }
                if (admins <= 1) {
                    return@withContext Result.failure(IllegalStateException("Cannot delete the primary Admin account."))
                }
            }
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteVaultAndTicket(user.id)
            }
            userDao.deleteUser(user)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Internal Bidirectional Teacher <-> Class <-> Student <-> AuthTicket Sync ---
    private suspend fun syncTeacherClassLinksInternal(
        teacher: Teacher,
        requestedClassIdsCsv: String,
        fallbackClassNames: String,
        adminUid: String?,
        syncAccount: Boolean = true
    ): Teacher {
        val allClasses = classDao.getAllClasses()
        val parsedIds = FirestoreCloudMappers.parseIdList(requestedClassIdsCsv).toMutableSet()
        if (parsedIds.isEmpty() && fallbackClassNames.isNotBlank()) {
            val nameTokens = fallbackClassNames.split(",").map { it.trim() }.filter { it.isNotBlank() }
            allClasses.forEach { cls ->
                if (nameTokens.any { it.equals(cls.name, ignoreCase = true) }) {
                    parsedIds.add(cls.id)
                }
            }
        }

        val validClassIds = allClasses.filter { it.id in parsedIds }.map { it.id }
        val validClassIdSet = validClassIds.toSet()
        val resolvedClassNames = if (validClassIds.isNotEmpty()) {
            allClasses.filter { it.id in validClassIdSet }.joinToString(", ") { it.name }
        } else {
            ""
        }
        val resolvedIdsCsv = validClassIds.joinToString(",")

        val syncedTeacher = teacher.copy(
            assignedClassIds = resolvedIdsCsv,
            assignedClasses = resolvedClassNames
        )
        if (cloudRepo != null && adminUid != null) {
            cloudRepo.createOrUpdateTeacherCloud(syncedTeacher, adminUid)
        }
        teacherDao.insertTeacher(syncedTeacher)

        val allStudents = studentDao.getAllStudents()
        for (cls in allClasses) {
            if (cls.id in validClassIdSet) {
                val prevTeacherId = cls.assignedTeacherId
                if (prevTeacherId.isNotBlank() && prevTeacherId != syncedTeacher.id) {
                    removeClassFromTeacherInternal(prevTeacherId, cls.id, adminUid)
                }
                if (cls.assignedTeacherId != syncedTeacher.id || cls.assignedTeacherName != syncedTeacher.fullName) {
                    val updatedCls = cls.copy(
                        assignedTeacherId = syncedTeacher.id,
                        assignedTeacherName = syncedTeacher.fullName
                    )
                    val classStudentIds = allStudents
                        .filter { it.classId == cls.id && it.status != StudentStatus.WITHDRAWN }
                        .map { it.id }
                    if (cloudRepo != null && adminUid != null) {
                        cloudRepo.createOrUpdateClassCloud(updatedCls, adminUid, classStudentIds)
                    }
                    classDao.updateClass(updatedCls)
                }
                allStudents.filter { it.classId == cls.id }.forEach { stud ->
                    if (stud.teacherId != syncedTeacher.id || stud.teacherName != syncedTeacher.fullName) {
                        val updatedStud = stud.copy(
                            teacherId = syncedTeacher.id,
                            teacherName = syncedTeacher.fullName,
                            updatedAt = System.currentTimeMillis()
                        )
                        if (cloudRepo != null && adminUid != null) {
                            cloudRepo.createOrUpdateStudentCloud(updatedStud, adminUid)
                        }
                        studentDao.updateStudent(updatedStud)
                    }
                }
            } else if (cls.assignedTeacherId == syncedTeacher.id) {
                val clearedCls = cls.copy(assignedTeacherId = "", assignedTeacherName = "")
                val classStudentIds = allStudents
                    .filter { it.classId == cls.id && it.status != StudentStatus.WITHDRAWN }
                    .map { it.id }
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.createOrUpdateClassCloud(clearedCls, adminUid, classStudentIds)
                }
                classDao.updateClass(clearedCls)
                allStudents.filter { it.classId == cls.id && it.teacherId == syncedTeacher.id }.forEach { stud ->
                    val clearedStud = stud.copy(
                        teacherId = "",
                        teacherName = "",
                        updatedAt = System.currentTimeMillis()
                    )
                    if (cloudRepo != null && adminUid != null) {
                        cloudRepo.createOrUpdateStudentCloud(clearedStud, adminUid)
                    }
                    studentDao.updateStudent(clearedStud)
                }
            }
        }

        if (syncAccount) {
            val existingAcc = userDao.getUserByTeacherId(syncedTeacher.id)
            if (existingAcc != null) {
                val updatedAcc = existingAcc.copy(
                    name = syncedTeacher.fullName,
                    phone = syncedTeacher.contactNumber,
                    email = syncedTeacher.email,
                    assignedClassIds = resolvedIdsCsv,
                    isActive = syncedTeacher.isActive
                )
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.upsertVaultAndTicket(updatedAcc, adminUid)
                }
                userDao.updateUser(updatedAcc)
            }
        }

        return syncedTeacher
    }

    private suspend fun removeClassFromTeacherInternal(teacherId: String, classIdToRemove: String, adminUid: String?) {
        val teacher = teacherDao.getTeacherById(teacherId) ?: return
        val remainingIds = FirestoreCloudMappers.parseIdList(teacher.assignedClassIds).filter { it != classIdToRemove }
        val allClasses = classDao.getAllClasses()
        val remainingNames = remainingIds.mapNotNull { rid -> allClasses.firstOrNull { it.id == rid }?.name }.joinToString(", ")
        val updatedTeacher = teacher.copy(
            assignedClassIds = remainingIds.joinToString(","),
            assignedClasses = remainingNames
        )
        if (cloudRepo != null && adminUid != null) {
            cloudRepo.createOrUpdateTeacherCloud(updatedTeacher, adminUid)
        }
        teacherDao.updateTeacher(updatedTeacher)

        val acc = userDao.getUserByTeacherId(teacher.id)
        if (acc != null) {
            val updatedAcc = acc.copy(assignedClassIds = updatedTeacher.assignedClassIds)
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertVaultAndTicket(updatedAcc, adminUid)
            }
            userDao.updateUser(updatedAcc)
        }
    }

    private suspend fun addClassToTeacherInternal(teacherId: String, academyClass: AcademyClass, adminUid: String?) {
        val teacher = teacherDao.getTeacherById(teacherId) ?: return
        val updatedIds = (FirestoreCloudMappers.parseIdList(teacher.assignedClassIds) + academyClass.id).distinct()
        val allClasses = classDao.getAllClasses().associateBy { it.id }.toMutableMap().apply {
            put(academyClass.id, academyClass)
        }
        val updatedNames = updatedIds.mapNotNull { cid -> allClasses[cid]?.name }.joinToString(", ")
        val updatedTeacher = teacher.copy(
            assignedClassIds = updatedIds.joinToString(","),
            assignedClasses = updatedNames
        )
        if (cloudRepo != null && adminUid != null) {
            cloudRepo.createOrUpdateTeacherCloud(updatedTeacher, adminUid)
        }
        teacherDao.updateTeacher(updatedTeacher)

        val acc = userDao.getUserByTeacherId(teacher.id)
        if (acc != null) {
            val updatedAcc = acc.copy(assignedClassIds = updatedTeacher.assignedClassIds)
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertVaultAndTicket(updatedAcc, adminUid)
            }
            userDao.updateUser(updatedAcc)
        }
    }

    // --- Student Admissions (with optional Admin-issued Parent Login + Live Cloud Sync) ---
    suspend fun admitStudent(
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
        parentPassword: String = ""
    ): Result<Student> = withContext(Dispatchers.IO) {
        try {
            if (fullName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Student name cannot be empty"))
            if (guardianName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Father/Guardian name is required"))
            if (contactNumber.isBlank()) return@withContext Result.failure(IllegalArgumentException("Contact number is required"))

            val existingStudents = studentDao.getAllStudents()
            val maxSeq = existingStudents
                .mapNotNull { it.admissionNumber.takeLast(4).toIntOrNull() }
                .maxOrNull() ?: 100
            val year = SimpleDateFormat("yy", Locale.US).format(Date())
            val admissionNumber = String.format(Locale.US, "HQA-%s-%04d", year, maxSeq + 1)

            val cleanStudentId = FirestoreCloudMappers.sanitizeId("stud_${UUID.randomUUID().toString().take(12)}")
            val cleanClassId = if (classId.isBlank() || classId == "unassigned_class") {
                "unassigned_class"
            } else {
                FirestoreCloudMappers.sanitizeId(classId)
            }
            val matchedClass = if (cleanClassId != "unassigned_class") classDao.getClassById(cleanClassId) else null
            val resolvedClassName = matchedClass?.name ?: className.ifBlank { "Unassigned" }
            val resolvedTeacherId = matchedClass?.assignedTeacherId?.ifBlank { teacherId } ?: teacherId
            val resolvedTeacherName = matchedClass?.assignedTeacherName?.ifBlank { teacherName } ?: teacherName

            val student = Student(
                id = cleanStudentId,
                admissionNumber = admissionNumber,
                fullName = fullName.trim(),
                guardianName = guardianName.trim(),
                dateOfBirth = dateOfBirth.trim(),
                admissionDate = admissionDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                gender = gender,
                contactNumber = contactNumber.trim(),
                alternateContact = alternateContact.trim(),
                address = address.trim(),
                classId = cleanClassId,
                className = resolvedClassName,
                teacherId = resolvedTeacherId,
                teacherName = resolvedTeacherName,
                quranLevel = quranLevel,
                monthlyFee = monthlyFee,
                feeCategory = feeCategory,
                isFsEligible = isFsEligible,
                notes = notes.trim()
            )

            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createOrUpdateStudentCloud(student, adminUid)
            }
            studentDao.insertStudent(student)

            // Provision or link Admin-managed Parent Login credentials
            val cleanPUser = parentUsername.trim()
            val cleanPPass = parentPassword.trim()
            if (cleanPUser.isNotBlank() && cleanPPass.isNotBlank()) {
                val existingParent = userDao.getUserByUsername(cleanPUser)
                if (existingParent != null && existingParent.role == UserRole.PARENT) {
                    val updatedIds = (existingParent.linkedStudentIds.split(",").map { it.trim() }.filter { it.isNotBlank() } + student.id)
                        .distinct()
                        .joinToString(",")
                    val updatedParent = existingParent.copy(
                        linkedStudentIds = updatedIds,
                        password = cleanPPass
                    )
                    if (cloudRepo != null && adminUid != null) {
                        cloudRepo.upsertVaultAndTicket(updatedParent, adminUid)
                    }
                    userDao.updateUser(updatedParent)
                } else if (existingParent == null) {
                    val newParent = UserAccount(
                        id = FirestoreCloudMappers.sanitizeId("vault_parent_${cleanPUser.lowercase(Locale.US)}"),
                        name = "${guardianName.trim()} (Parent)",
                        username = cleanPUser,
                        password = cleanPPass,
                        role = UserRole.PARENT,
                        phone = contactNumber.trim(),
                        linkedStudentIds = student.id
                    )
                    if (cloudRepo != null && adminUid != null) {
                        cloudRepo.upsertVaultAndTicket(newParent, adminUid)
                    }
                    userDao.insertUser(newParent)
                }
            }

            Result.success(student)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStudent(student: Student): Result<Student> = withContext(Dispatchers.IO) {
        try {
            if (student.status == StudentStatus.WITHDRAWN) {
                withdrawAndRemoveStudentPermanently(student.id)
                return@withContext Result.success(student)
            }
            val cleanClassId = if (student.classId.isBlank() || student.classId == "unassigned_class") {
                "unassigned_class"
            } else {
                FirestoreCloudMappers.sanitizeId(student.classId)
            }
            val matchedClass = if (cleanClassId != "unassigned_class") classDao.getClassById(cleanClassId) else null
            val updated = student.copy(
                fullName = student.fullName.trim(),
                guardianName = student.guardianName.trim(),
                contactNumber = student.contactNumber.trim(),
                alternateContact = student.alternateContact.trim(),
                address = student.address.trim(),
                classId = cleanClassId,
                className = matchedClass?.name ?: if (cleanClassId == "unassigned_class") "Unassigned" else student.className,
                teacherId = matchedClass?.assignedTeacherId?.ifBlank { student.teacherId }
                    ?: if (cleanClassId == "unassigned_class") "" else student.teacherId,
                teacherName = matchedClass?.assignedTeacherName?.ifBlank { student.teacherName }
                    ?: student.teacherName,
                notes = student.notes.trim(),
                completedCourse = student.completedCourse.trim(),
                completionDate = student.completionDate.trim(),
                certificateNumber = student.certificateNumber.trim(),
                finalGrade = student.finalGrade.trim(),
                certificateRemarks = student.certificateRemarks.trim(),
                updatedAt = System.currentTimeMillis()
            )
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createOrUpdateStudentCloud(updated, adminUid)
            }
            studentDao.updateStudent(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeStudentCourseAndIssueCertificate(
        studentId: String,
        completedCourse: String,
        completionDate: String,
        certificateNumber: String,
        finalGrade: String,
        teacherNameOverride: String,
        certificateRemarks: String
    ): Result<Student> = withContext(Dispatchers.IO) {
        try {
            val student = studentDao.getStudentById(studentId)
                ?: cloudRepo?.getStudentById(studentId)?.getOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Student not found."))

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
            val admDigits = student.admissionNumber.filter { it.isDigit() }.takeLast(4).ifBlank { "0101" }
            val defaultCertNo = "HQA-SANAD-$year-$admDigits"

            val cleanCourse = completedCourse.trim().ifBlank {
                student.completedCourse.ifBlank {
                    student.quranLevel.ifBlank { "Hifz-ul-Quran Al-Kareem" }
                }
            }
            val cleanDate = completionDate.trim().ifBlank { today }
            val cleanCertNo = certificateNumber.trim().ifBlank {
                student.certificateNumber.ifBlank { defaultCertNo }
            }
            val cleanGrade = finalGrade.trim().ifBlank { "Mumtaz (A+ Distinction)" }
            val cleanTeacherName = teacherNameOverride.trim().ifBlank {
                student.teacherName.ifBlank { "Academy Senior Qari" }
            }

            val updated = student.copy(
                status = StudentStatus.COMPLETED,
                completedCourse = cleanCourse,
                completionDate = cleanDate,
                certificateNumber = cleanCertNo,
                finalGrade = cleanGrade,
                teacherName = cleanTeacherName,
                certificateRemarks = certificateRemarks.trim(),
                updatedAt = System.currentTimeMillis()
            )

            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createOrUpdateStudentCloud(updated, adminUid)
            }
            studentDao.insertStudent(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setStudentStatus(studentId: String, newStatus: StudentStatus) = withContext(Dispatchers.IO) {
        if (newStatus == StudentStatus.WITHDRAWN) {
            withdrawAndRemoveStudentPermanently(studentId)
            return@withContext
        }
        val student = studentDao.getStudentById(studentId) ?: return@withContext
        val updated = student.copy(status = newStatus, updatedAt = System.currentTimeMillis())
        val adminUid = resolveAdminUidOrNull()
        if (cloudRepo != null && adminUid != null) {
            cloudRepo.createOrUpdateStudentCloud(updated, adminUid)
        }
        studentDao.updateStudent(updated)
    }

    /**
     * Permanently removes a withdrawn student from Firestore Cloud and local Room database along with all linked
     * attendance, Quran progress, Fee Sabilillah, challan/payment records, and unlinks
     * (or deletes if no siblings remain) their Parent Portal login account.
     */
    suspend fun withdrawAndRemoveStudentPermanently(studentId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val student = studentDao.getStudentById(studentId)
                ?: cloudRepo?.getStudentById(studentId)?.getOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Student not found."))
            val label = "${student.fullName} (${student.admissionNumber})"

            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.withdrawStudentPermanentlyCloud(studentId)
            }

            attendanceDao.deleteAttendanceByStudentId(studentId)
            quranProgressDao.deleteProgressByStudentId(studentId)
            feePaymentsAndChallansCleanup(studentId)
            feeSabilillahDao.deleteFsByStudentId(studentId)
            studentDao.deleteStudentById(studentId)

            // Unlink from any Parent Portal account; delete Parent account if no active children remain
            val parentUsers = userDao.getAllUsers().filter { it.role == UserRole.PARENT }
            for (parent in parentUsers) {
                val linkedList = parent.linkedStudentIds
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (studentId in linkedList) {
                    val remaining = linkedList.filter { it != studentId }
                    if (remaining.isEmpty()) {
                        if (cloudRepo != null && currentCloudUid() != null) {
                            cloudRepo.deleteVaultAndTicket(parent.id)
                        }
                        userDao.deleteUser(parent)
                    } else {
                        val updatedParent = parent.copy(linkedStudentIds = remaining.joinToString(","))
                        if (cloudRepo != null && adminUid != null) {
                            cloudRepo.upsertVaultAndTicket(updatedParent, adminUid)
                        }
                        userDao.updateUser(updatedParent)
                    }
                }
            }

            Result.success(label)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun feePaymentsAndChallansCleanup(studentId: String) {
        feePaymentDao.deletePaymentsByStudentId(studentId)
        feeChallanDao.deleteChallansByStudentId(studentId)
    }

    suspend fun purgeAnyWithdrawnStudents() = withContext(Dispatchers.IO) {
        val withdrawnList = studentDao.getWithdrawnStudents()
        for (student in withdrawnList) {
            withdrawAndRemoveStudentPermanently(student.id)
        }
    }

    // --- Teachers (with Admin-issued Teacher Login + Bidirectional Class/Student Sync) ---
    suspend fun addTeacher(
        fullName: String,
        contactNumber: String,
        email: String,
        address: String,
        assignedClasses: String,
        assignedClassIds: String,
        joiningDate: String,
        notes: String,
        teacherUsername: String = "",
        teacherPassword: String = ""
    ): Result<Teacher> = withContext(Dispatchers.IO) {
        try {
            if (fullName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Teacher name is required"))
            if (contactNumber.isBlank()) return@withContext Result.failure(IllegalArgumentException("Contact number is required"))

            val cleanTUser = teacherUsername.trim()
            val cleanTPass = teacherPassword.trim()
            if (cleanTUser.isNotBlank()) {
                val existingUser = userDao.getUserByUsername(cleanTUser)
                if (existingUser != null) {
                    return@withContext Result.failure(IllegalStateException("Username '$cleanTUser' is already in use."))
                }
            }

            val baseTeacher = Teacher(
                id = FirestoreCloudMappers.sanitizeId("tchr_${UUID.randomUUID().toString().take(12)}"),
                fullName = fullName.trim(),
                contactNumber = contactNumber.trim(),
                email = email.trim(),
                address = address.trim(),
                assignedClasses = assignedClasses.trim(),
                assignedClassIds = assignedClassIds.trim(),
                joiningDate = joiningDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                notes = notes.trim()
            )

            val adminUid = resolveAdminUidOrNull()
            val syncedTeacher = syncTeacherClassLinksInternal(
                teacher = baseTeacher,
                requestedClassIdsCsv = assignedClassIds,
                fallbackClassNames = assignedClasses,
                adminUid = adminUid,
                syncAccount = false
            )

            if (cleanTUser.isNotBlank() && cleanTPass.isNotBlank()) {
                val teacherAccount = UserAccount(
                    id = FirestoreCloudMappers.sanitizeId("vault_teacher_${syncedTeacher.id}"),
                    name = syncedTeacher.fullName,
                    username = cleanTUser,
                    password = cleanTPass,
                    email = syncedTeacher.email,
                    role = UserRole.TEACHER,
                    phone = syncedTeacher.contactNumber,
                    teacherId = syncedTeacher.id,
                    assignedClassIds = syncedTeacher.assignedClassIds
                )
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.upsertVaultAndTicket(teacherAccount, adminUid)
                }
                userDao.insertUser(teacherAccount)
            }

            Result.success(syncedTeacher)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTeacher(teacher: Teacher) = withContext(Dispatchers.IO) {
        val adminUid = resolveAdminUidOrNull()
        syncTeacherClassLinksInternal(
            teacher = teacher,
            requestedClassIdsCsv = teacher.assignedClassIds,
            fallbackClassNames = teacher.assignedClasses,
            adminUid = adminUid,
            syncAccount = true
        )
    }

    suspend fun updateTeacherDetails(
        teacher: Teacher,
        assignedClassIdsCsv: String,
        teacherUsername: String = "",
        teacherPassword: String = ""
    ): Result<Teacher> = withContext(Dispatchers.IO) {
        try {
            if (teacher.fullName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Teacher name is required"))
            if (teacher.contactNumber.isBlank()) return@withContext Result.failure(IllegalArgumentException("Contact number is required"))

            val existingAcc = userDao.getUserByTeacherId(teacher.id)
            val cleanUser = teacherUsername.trim()
            val cleanPass = teacherPassword.trim()
            if (cleanUser.isNotBlank()) {
                val conflict = userDao.getUserByUsername(cleanUser)
                if (conflict != null && conflict.id != existingAcc?.id) {
                    return@withContext Result.failure(IllegalStateException("Username '$cleanUser' is already in use."))
                }
            }

            val adminUid = resolveAdminUidOrNull()
            val syncedTeacher = syncTeacherClassLinksInternal(
                teacher = teacher.copy(
                    fullName = teacher.fullName.trim(),
                    contactNumber = teacher.contactNumber.trim(),
                    email = teacher.email.trim(),
                    address = teacher.address.trim(),
                    notes = teacher.notes.trim()
                ),
                requestedClassIdsCsv = assignedClassIdsCsv,
                fallbackClassNames = "",
                adminUid = adminUid,
                syncAccount = false
            )

            if (cleanUser.isNotBlank() && cleanPass.isNotBlank()) {
                val account = UserAccount(
                    id = existingAcc?.id ?: FirestoreCloudMappers.sanitizeId("vault_teacher_${syncedTeacher.id}"),
                    name = syncedTeacher.fullName,
                    username = cleanUser,
                    password = cleanPass,
                    email = syncedTeacher.email,
                    role = UserRole.TEACHER,
                    phone = syncedTeacher.contactNumber,
                    teacherId = syncedTeacher.id,
                    assignedClassIds = syncedTeacher.assignedClassIds,
                    isActive = syncedTeacher.isActive,
                    createdAt = existingAcc?.createdAt ?: System.currentTimeMillis()
                )
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.upsertVaultAndTicket(account, adminUid)
                }
                userDao.insertUser(account)
            } else if (existingAcc != null) {
                val updatedAcc = existingAcc.copy(
                    name = syncedTeacher.fullName,
                    phone = syncedTeacher.contactNumber,
                    email = syncedTeacher.email,
                    assignedClassIds = syncedTeacher.assignedClassIds,
                    isActive = syncedTeacher.isActive
                )
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.upsertVaultAndTicket(updatedAcc, adminUid)
                }
                userDao.updateUser(updatedAcc)
            }

            Result.success(syncedTeacher)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTeacher(teacherId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val teacher = teacherDao.getTeacherById(teacherId)
                ?: return@withContext Result.failure(IllegalArgumentException("Teacher not found"))
            val adminUid = resolveAdminUidOrNull()

            // 1. Unassign any classes assigned to this teacher
            val allStudents = studentDao.getAllStudents()
            classDao.getAllClasses().filter { it.assignedTeacherId == teacher.id }.forEach { cls ->
                val clearedCls = cls.copy(assignedTeacherId = "", assignedTeacherName = "")
                val classStudentIds = allStudents
                    .filter { it.classId == cls.id && it.status != StudentStatus.WITHDRAWN }
                    .map { it.id }
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.createOrUpdateClassCloud(clearedCls, adminUid, classStudentIds)
                }
                classDao.updateClass(clearedCls)
            }

            // 2. Unassign teacher from students
            allStudents.filter { it.teacherId == teacher.id }.forEach { stud ->
                val clearedStud = stud.copy(teacherId = "", teacherName = "", updatedAt = System.currentTimeMillis())
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.createOrUpdateStudentCloud(clearedStud, adminUid)
                }
                studentDao.updateStudent(clearedStud)
            }

            // 3. Delete Teacher Portal login credentials if present
            val acc = userDao.getUserByTeacherId(teacher.id)
            if (acc != null) {
                if (cloudRepo != null && currentCloudUid() != null) {
                    cloudRepo.deleteVaultAndTicket(acc.id)
                }
                userDao.deleteUser(acc)
            }

            // 4. Delete Teacher record from Cloud & Room
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteTeacherCloud(teacher.id)
            }
            teacherDao.deleteTeacherById(teacher.id)

            Result.success(teacher.fullName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Classes (with Bidirectional Teacher, Student & AuthTicket Sync) ---
    suspend fun addClass(
        name: String,
        section: String,
        roomNumber: String,
        capacity: Int,
        teacherId: String,
        teacherName: String,
        scheduleInfo: String
    ): Result<AcademyClass> = withContext(Dispatchers.IO) {
        try {
            if (name.isBlank()) return@withContext Result.failure(IllegalArgumentException("Class name is required"))
            val assignedTeacher = if (teacherId.isNotBlank()) teacherDao.getTeacherById(teacherId) else null
            val resolvedTeacherId = assignedTeacher?.id ?: ""
            val resolvedTeacherName = assignedTeacher?.fullName ?: ""

            val academyClass = AcademyClass(
                id = FirestoreCloudMappers.sanitizeId("class_${UUID.randomUUID().toString().take(12)}"),
                name = name.trim(),
                section = section.trim().ifBlank { "Morning" },
                roomNumber = roomNumber.trim().ifBlank { "Room A" },
                capacity = capacity.coerceIn(1, 200),
                assignedTeacherId = resolvedTeacherId,
                assignedTeacherName = resolvedTeacherName,
                scheduleInfo = scheduleInfo.trim()
            )
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createOrUpdateClassCloud(academyClass, adminUid, emptyList())
            }
            classDao.insertClass(academyClass)

            if (resolvedTeacherId.isNotBlank()) {
                addClassToTeacherInternal(resolvedTeacherId, academyClass, adminUid)
            }

            Result.success(academyClass)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateClassDetails(academyClass: AcademyClass): Result<AcademyClass> = withContext(Dispatchers.IO) {
        try {
            if (academyClass.name.isBlank()) return@withContext Result.failure(IllegalArgumentException("Class name is required"))
            val existing = classDao.getClassById(academyClass.id)
                ?: return@withContext Result.failure(IllegalArgumentException("Class not found"))

            val assignedTeacher = if (academyClass.assignedTeacherId.isNotBlank()) {
                teacherDao.getTeacherById(academyClass.assignedTeacherId)
            } else null
            val resolvedTeacherId = assignedTeacher?.id ?: ""
            val resolvedTeacherName = assignedTeacher?.fullName ?: ""

            val updatedClass = academyClass.copy(
                name = academyClass.name.trim(),
                section = academyClass.section.trim().ifBlank { "Morning" },
                roomNumber = academyClass.roomNumber.trim().ifBlank { "Room A" },
                capacity = academyClass.capacity.coerceIn(1, 200),
                assignedTeacherId = resolvedTeacherId,
                assignedTeacherName = resolvedTeacherName,
                scheduleInfo = academyClass.scheduleInfo.trim()
            )

            val allStudents = studentDao.getAllStudents()
            val classStudents = allStudents.filter { it.classId == updatedClass.id && it.status != StudentStatus.WITHDRAWN }
            val adminUid = resolveAdminUidOrNull()

            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createOrUpdateClassCloud(updatedClass, adminUid, classStudents.map { it.id })
            }
            classDao.updateClass(updatedClass)

            // Sync previous teacher if teacher changed
            if (existing.assignedTeacherId.isNotBlank() && existing.assignedTeacherId != resolvedTeacherId) {
                removeClassFromTeacherInternal(existing.assignedTeacherId, updatedClass.id, adminUid)
            }
            // Sync newly assigned teacher (or refresh class name on same teacher)
            if (resolvedTeacherId.isNotBlank()) {
                addClassToTeacherInternal(resolvedTeacherId, updatedClass, adminUid)
            }

            // Update className & teacher assignment on all enrolled students in this class
            classStudents.forEach { stud ->
                if (stud.className != updatedClass.name ||
                    stud.teacherId != resolvedTeacherId ||
                    stud.teacherName != resolvedTeacherName
                ) {
                    val updatedStud = stud.copy(
                        className = updatedClass.name,
                        teacherId = resolvedTeacherId,
                        teacherName = resolvedTeacherName,
                        updatedAt = System.currentTimeMillis()
                    )
                    if (cloudRepo != null && adminUid != null) {
                        cloudRepo.createOrUpdateStudentCloud(updatedStud, adminUid)
                    }
                    studentDao.updateStudent(updatedStud)
                }
            }

            Result.success(updatedClass)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteClass(classId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val existing = classDao.getClassById(classId)
                ?: return@withContext Result.failure(IllegalArgumentException("Class not found"))
            val adminUid = resolveAdminUidOrNull()

            // 1. Reassign students in this class to unassigned_class so no student is orphaned
            studentDao.getAllStudents().filter { it.classId == existing.id }.forEach { stud ->
                val reassigned = stud.copy(
                    classId = "unassigned_class",
                    className = "Unassigned",
                    teacherId = "",
                    teacherName = "",
                    updatedAt = System.currentTimeMillis()
                )
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.createOrUpdateStudentCloud(reassigned, adminUid)
                }
                studentDao.updateStudent(reassigned)
            }

            // 2. Remove class reference from any assigned teachers & their auth tickets
            teacherDao.getAllTeachers().forEach { t ->
                val ids = FirestoreCloudMappers.parseIdList(t.assignedClassIds)
                if (existing.id in ids || t.id == existing.assignedTeacherId) {
                    removeClassFromTeacherInternal(t.id, existing.id, adminUid)
                }
            }

            // 3. Delete class from Cloud & Room
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteClassCloud(existing.id)
            }
            classDao.deleteClassById(existing.id)

            Result.success(existing.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Attendance System ---
    suspend fun saveClassAttendance(
        classId: String,
        className: String,
        date: String,
        records: Map<String, AttendanceStatus>,
        remarksMap: Map<String, String>,
        markedByTeacherId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<AttendanceRecord>()
            for ((studentId, status) in records) {
                val student = studentDao.getStudentById(studentId)
                val existing = attendanceDao.getStudentAttendanceForDate(studentId, date)
                val attId = existing?.id ?: FirestoreCloudMappers.sanitizeId("att_${studentId}_${date}")
                val record = AttendanceRecord(
                    id = attId,
                    date = date,
                    classId = classId,
                    className = className,
                    studentId = studentId,
                    studentName = student?.fullName ?: "Student",
                    admissionNumber = student?.admissionNumber ?: "",
                    status = status,
                    remarks = remarksMap[studentId] ?: "",
                    markedByTeacherId = markedByTeacherId,
                    timestamp = System.currentTimeMillis()
                )
                list.add(record)
            }

            val uid = currentCloudUid()
            if (cloudRepo != null && uid != null) {
                cloudRepo.saveAttendanceCloud(list, uid)
            }
            attendanceDao.insertAttendanceRecords(list)
            Result.success(list.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAttendanceRecord(attendanceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rec = attendanceDao.getAttendanceById(attendanceId)
                ?: return@withContext Result.failure(IllegalArgumentException("Attendance record not found"))
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteAttendanceRecordCloud(rec.id, rec.studentId)
            }
            attendanceDao.deleteAttendanceById(rec.id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteClassAttendanceForDate(classId: String, date: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val existing = attendanceDao.getAttendanceByClassAndDate(classId, date)
            if (cloudRepo != null && currentCloudUid() != null) {
                for (rec in existing) {
                    cloudRepo.deleteAttendanceRecordCloud(rec.id, rec.studentId)
                }
            }
            attendanceDao.deleteAttendanceByClassAndDate(classId, date)
            Result.success(existing.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Quran Progress Tracking ---
    suspend fun recordQuranProgress(
        studentId: String,
        teacherId: String,
        teacherName: String,
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
        nextTarget: String
    ): Result<QuranProgressRecord> = withContext(Dispatchers.IO) {
        try {
            val student = studentDao.getStudentById(studentId)
                ?: return@withContext Result.failure(IllegalArgumentException("Student not found"))

            val record = QuranProgressRecord(
                id = FirestoreCloudMappers.sanitizeId("prog_${UUID.randomUUID().toString().take(12)}"),
                date = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                studentId = studentId,
                studentName = student.fullName,
                admissionNumber = student.admissionNumber,
                teacherId = teacherId,
                teacherName = teacherName,
                surahName = surahName.trim(),
                surahNumber = surahNumber,
                juzNumber = juzNumber,
                sabaqAyatStart = sabaqStart,
                sabaqAyatEnd = sabaqEnd,
                sabaqRating = rating,
                sabqiSummary = sabqiSummary.trim(),
                manzilSummary = manzilSummary.trim(),
                mistakesCount = mistakesCount,
                teacherRemarks = teacherRemarks.trim(),
                nextTarget = nextTarget.trim()
            )

            val uid = currentCloudUid()
            if (cloudRepo != null && uid != null) {
                cloudRepo.recordQuranProgressCloud(record, student.classId, uid)
            }
            quranProgressDao.insertProgress(record)
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateQuranProgress(record: QuranProgressRecord): Result<QuranProgressRecord> = withContext(Dispatchers.IO) {
        try {
            if (record.surahName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Surah name is required"))
            }
            val student = studentDao.getStudentById(record.studentId)
            val classId = student?.classId?.ifBlank { "unassigned_class" } ?: "unassigned_class"
            val updated = record.copy(
                surahName = record.surahName.trim(),
                sabqiSummary = record.sabqiSummary.trim(),
                manzilSummary = record.manzilSummary.trim(),
                teacherRemarks = record.teacherRemarks.trim(),
                nextTarget = record.nextTarget.trim(),
                timestamp = System.currentTimeMillis()
            )
            val uid = currentCloudUid()
            if (cloudRepo != null && uid != null) {
                cloudRepo.upsertQuranProgressCloud(updated, classId, uid)
            }
            quranProgressDao.updateProgress(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteQuranProgress(progressId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rec = quranProgressDao.getProgressById(progressId)
                ?: return@withContext Result.failure(IllegalArgumentException("Progress record not found"))
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteQuranProgressCloud(rec.id, rec.studentId)
            }
            quranProgressDao.deleteProgressById(rec.id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Monthly Fee Challans & Sabilillah ---
    suspend fun generateChallanForStudent(
        studentId: String,
        billingMonth: String,
        billingYear: Int,
        dueDate: String,
        additionalCharges: Double = 0.0,
        manualDiscount: Double = 0.0
    ): Result<FeeChallan> = withContext(Dispatchers.IO) {
        try {
            val student = studentDao.getStudentById(studentId)
                ?: return@withContext Result.failure(IllegalArgumentException("Student not found"))

            val existing = feeChallanDao.getChallanByStudentAndPeriod(studentId, billingMonth, billingYear)
            if (existing != null) {
                return@withContext Result.failure(
                    IllegalStateException("Challan already exists for ${student.fullName} for $billingMonth $billingYear (Challan #${existing.challanNumber})")
                )
            }

            val studentChallans = feeChallanDao.getAllChallans().filter { it.studentId == studentId }
            val previousUnpaid = studentChallans
                .filter { it.status == ChallanStatus.UNPAID || it.status == ChallanStatus.PARTIALLY_PAID || it.status == ChallanStatus.OVERDUE }
                .sumOf { it.remainingBalance }

            val fsRecord = feeSabilillahDao.getApprovedFsForStudent(studentId)
            val fsAssistance = when {
                fsRecord != null && fsRecord.assistanceType == AssistanceType.FULL -> student.monthlyFee
                fsRecord != null && fsRecord.assistanceType == AssistanceType.PARTIAL -> fsRecord.approvedAmount.coerceAtMost(student.monthlyFee)
                else -> 0.0
            }

            val totalDiscount = manualDiscount + fsAssistance
            val totalPayable = (student.monthlyFee + additionalCharges + previousUnpaid - totalDiscount).coerceAtLeast(0.0)

            val monthIndex = when (billingMonth.lowercase(Locale.US)) {
                "january" -> "01"; "february" -> "02"; "march" -> "03"; "april" -> "04"
                "may" -> "05"; "june" -> "06"; "july" -> "07"; "august" -> "08"
                "september" -> "09"; "october" -> "10"; "november" -> "11"; "december" -> "12"
                else -> "01"
            }
            val randSuffix = (100..999).random()
            val challanNumber = "HQA-$billingYear$monthIndex-${student.admissionNumber.takeLast(4)}-$randSuffix"

            val issueDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val challan = FeeChallan(
                id = FirestoreCloudMappers.sanitizeId("ch_${UUID.randomUUID().toString().take(12)}"),
                challanNumber = challanNumber,
                studentId = studentId,
                admissionNumber = student.admissionNumber,
                studentName = student.fullName,
                classId = student.classId,
                className = student.className,
                billingMonth = billingMonth,
                billingYear = billingYear,
                issueDate = issueDate,
                dueDate = dueDate,
                monthlyTuitionFee = student.monthlyFee,
                additionalCharges = additionalCharges,
                discountAmount = manualDiscount,
                fsAssistanceAmount = fsAssistance,
                previousBalance = previousUnpaid,
                totalPayable = totalPayable,
                amountPaid = 0.0,
                remainingBalance = totalPayable,
                status = if (totalPayable == 0.0) ChallanStatus.PAID else ChallanStatus.UNPAID
            )

            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createChallanCloud(challan, adminUid)
            }
            feeChallanDao.insertChallan(challan)
            Result.success(challan)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateBulkChallans(
        classId: String?,
        billingMonth: String,
        billingYear: Int,
        dueDate: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val students = if (classId != null) {
                studentDao.getStudentsByClass(classId)
            } else {
                studentDao.getActiveStudents()
            }
            var count = 0
            for (student in students) {
                val res = generateChallanForStudent(
                    studentId = student.id,
                    billingMonth = billingMonth,
                    billingYear = billingYear,
                    dueDate = dueDate
                )
                if (res.isSuccess) count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChallanDetails(challan: FeeChallan): Result<FeeChallan> = withContext(Dispatchers.IO) {
        try {
            val recomputedTotal = (challan.monthlyTuitionFee + challan.additionalCharges + challan.previousBalance - challan.discountAmount - challan.fsAssistanceAmount)
                .coerceAtLeast(0.0)
            val recomputedRemaining = (recomputedTotal - challan.amountPaid).coerceAtLeast(0.0)
            val resolvedStatus = when {
                challan.status == ChallanStatus.CANCELLED -> ChallanStatus.CANCELLED
                recomputedRemaining <= 0.0 -> ChallanStatus.PAID
                challan.amountPaid > 0.0 -> ChallanStatus.PARTIALLY_PAID
                challan.status == ChallanStatus.OVERDUE -> ChallanStatus.OVERDUE
                else -> ChallanStatus.UNPAID
            }
            val updated = challan.copy(
                billingMonth = challan.billingMonth.trim(),
                dueDate = challan.dueDate.trim(),
                totalPayable = recomputedTotal,
                remainingBalance = recomputedRemaining,
                status = resolvedStatus,
                remarks = challan.remarks.trim()
            )
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertChallanCloud(updated, adminUid)
            }
            feeChallanDao.updateChallan(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChallan(challanId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val challan = feeChallanDao.getChallanById(challanId)
                ?: return@withContext Result.failure(IllegalArgumentException("Challan not found"))
            val linkedPayments = feePaymentDao.getPaymentsByChallan(challan.id)
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteChallanCloud(challan.id, challan.studentId, linkedPayments.map { it.id })
            }
            feePaymentDao.deletePaymentsByChallanId(challan.id)
            feeChallanDao.deleteChallanById(challan.id)
            Result.success(challan.challanNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordPayment(
        challanId: String,
        amount: Double,
        paymentMethod: String,
        receivedBy: String,
        remarks: String,
        transactionId: String = ""
    ): Result<FeePayment> = withContext(Dispatchers.IO) {
        try {
            val uid = currentCloudUid()
            if (cloudRepo != null && uid != null) {
                val cloudResult = cloudRepo.recordPaymentCloud(
                    challanId = challanId,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    receivedBy = receivedBy,
                    remarks = remarks,
                    transactionId = transactionId,
                    callerUid = uid
                )
                return@withContext cloudResult.onSuccess { payment ->
                    feePaymentDao.insertPayment(payment)
                    val localChallan = feeChallanDao.getChallanById(challanId)
                    if (localChallan != null) {
                        val newPaid = localChallan.amountPaid + amount
                        val newRemaining = (localChallan.totalPayable - newPaid).coerceAtLeast(0.0)
                        val newStatus = when {
                            newRemaining <= 0.0 -> ChallanStatus.PAID
                            newPaid > 0.0 -> ChallanStatus.PARTIALLY_PAID
                            else -> ChallanStatus.UNPAID
                        }
                        feeChallanDao.updateChallan(
                            localChallan.copy(
                                amountPaid = newPaid,
                                remainingBalance = newRemaining,
                                status = newStatus,
                                paymentDate = payment.paymentDate,
                                paymentMethod = paymentMethod,
                                transactionRef = payment.receiptNumber,
                                remarks = remarks.trim()
                            )
                        )
                    }
                }
            }

            val challan = feeChallanDao.getChallanById(challanId)
                ?: return@withContext Result.failure(IllegalArgumentException("Challan not found"))

            if (amount <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Payment amount must be greater than 0"))
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

            val payment = FeePayment(
                challanId = challanId,
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

            feePaymentDao.insertPayment(payment)
            feeChallanDao.updateChallan(
                challan.copy(
                    amountPaid = newPaid,
                    remainingBalance = newRemaining,
                    status = newStatus,
                    paymentDate = paymentDate,
                    paymentMethod = paymentMethod,
                    transactionRef = receiptNo
                )
            )

            Result.success(payment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePaymentDetails(payment: FeePayment): Result<FeePayment> = withContext(Dispatchers.IO) {
        try {
            if (payment.amount <= 0.0) {
                return@withContext Result.failure(IllegalArgumentException("Payment amount must be greater than 0"))
            }
            val cleanedPayment = payment.copy(
                paymentMethod = payment.paymentMethod.trim(),
                receiptNumber = payment.receiptNumber.trim().ifBlank { "REC-${System.currentTimeMillis().toString().takeLast(6)}" },
                remarks = payment.remarks.trim()
            )
            feePaymentDao.updatePayment(cleanedPayment)

            val challan = feeChallanDao.getChallanById(cleanedPayment.challanId)
            val updatedChallan = if (challan != null) {
                val allPayments = feePaymentDao.getPaymentsByChallan(challan.id)
                val sumPaid = allPayments.sumOf { it.amount }
                val newRemaining = (challan.totalPayable - sumPaid).coerceAtLeast(0.0)
                val newStatus = when {
                    newRemaining <= 0.0 -> ChallanStatus.PAID
                    sumPaid > 0.0 -> ChallanStatus.PARTIALLY_PAID
                    else -> ChallanStatus.UNPAID
                }
                val latest = allPayments.firstOrNull() ?: cleanedPayment
                val ch = challan.copy(
                    amountPaid = sumPaid,
                    remainingBalance = newRemaining,
                    status = newStatus,
                    paymentDate = latest.paymentDate,
                    paymentMethod = latest.paymentMethod,
                    transactionRef = latest.receiptNumber
                )
                feeChallanDao.updateChallan(ch)
                ch
            } else null

            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.updatePaymentAndSyncChallanCloud(cleanedPayment, updatedChallan, adminUid)
            }
            Result.success(cleanedPayment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePayment(paymentId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val existing = feePaymentDao.getPaymentById(paymentId)
                ?: return@withContext Result.failure(IllegalArgumentException("Payment receipt not found"))
            feePaymentDao.deletePaymentById(existing.id)

            val challan = feeChallanDao.getChallanById(existing.challanId)
            val updatedChallan = if (challan != null) {
                val remainingPayments = feePaymentDao.getPaymentsByChallan(challan.id)
                val sumPaid = remainingPayments.sumOf { it.amount }
                val newRemaining = (challan.totalPayable - sumPaid).coerceAtLeast(0.0)
                val newStatus = when {
                    newRemaining <= 0.0 && (sumPaid > 0.0 || challan.totalPayable == 0.0) -> ChallanStatus.PAID
                    sumPaid > 0.0 -> ChallanStatus.PARTIALLY_PAID
                    else -> ChallanStatus.UNPAID
                }
                val latest = remainingPayments.firstOrNull()
                val ch = challan.copy(
                    amountPaid = sumPaid,
                    remainingBalance = newRemaining,
                    status = newStatus,
                    paymentDate = latest?.paymentDate ?: "",
                    paymentMethod = latest?.paymentMethod ?: "",
                    transactionRef = latest?.receiptNumber ?: ""
                )
                feeChallanDao.updateChallan(ch)
                ch
            } else null

            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.deletePaymentAndSyncChallanCloud(existing.id, existing.studentId, updatedChallan, adminUid)
            }
            Result.success(existing.receiptNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Fee Sabilillah (FS) Management ---
    suspend fun submitFsApplication(
        studentId: String,
        category: String,
        assistanceType: AssistanceType,
        approvedAmount: Double,
        startDate: String,
        confidentialRemarks: String
    ): Result<FeeSabilillahRecord> = withContext(Dispatchers.IO) {
        try {
            val student = studentDao.getStudentById(studentId)
                ?: return@withContext Result.failure(IllegalArgumentException("Student not found"))

            val record = FeeSabilillahRecord(
                id = FirestoreCloudMappers.sanitizeId("fs_$studentId"),
                studentId = studentId,
                studentName = student.fullName,
                admissionNumber = student.admissionNumber,
                className = student.className,
                category = category.trim(),
                assistanceType = assistanceType,
                approvedAmount = if (assistanceType == AssistanceType.FULL) student.monthlyFee else approvedAmount,
                effectiveStartDate = startDate.trim(),
                confidentialRemarks = confidentialRemarks.trim(),
                approvalStatus = ApprovalStatus.PENDING
            )
            val updatedStudent = student.copy(isFsEligible = true, fsStatus = "Pending")
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertFsRecordCloud(record, adminUid)
                cloudRepo.createOrUpdateStudentCloud(updatedStudent, adminUid)
            }
            feeSabilillahDao.insertFsRecord(record)
            studentDao.updateStudent(updatedStudent)
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFsRecord(record: FeeSabilillahRecord) = withContext(Dispatchers.IO) {
        val adminUid = resolveAdminUidOrNull()
        if (cloudRepo != null && adminUid != null) {
            cloudRepo.upsertFsRecordCloud(record, adminUid)
        }
        feeSabilillahDao.updateFsRecord(record)

        val student = studentDao.getStudentById(record.studentId)
        if (student != null) {
            val statusLabel = when (record.approvalStatus) {
                ApprovalStatus.APPROVED -> "Approved"
                ApprovalStatus.REJECTED -> "Rejected"
                ApprovalStatus.PENDING -> "Pending"
            }
            val updatedStudent = student.copy(
                isFsEligible = record.approvalStatus == ApprovalStatus.APPROVED,
                fsStatus = statusLabel
            )
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createOrUpdateStudentCloud(updatedStudent, adminUid)
            }
            studentDao.updateStudent(updatedStudent)
        }
    }

    suspend fun deleteFsRecord(fsId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val record = feeSabilillahDao.getFsById(fsId)
                ?: return@withContext Result.failure(IllegalArgumentException("FS record not found"))
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteFsRecordCloud(record.id)
            }
            feeSabilillahDao.deleteFsById(record.id)

            val student = studentDao.getStudentById(record.studentId)
            if (student != null) {
                val resetStudent = student.copy(isFsEligible = false, fsStatus = "None")
                if (cloudRepo != null && adminUid != null) {
                    cloudRepo.createOrUpdateStudentCloud(resetStudent, adminUid)
                }
                studentDao.updateStudent(resetStudent)
            }
            Result.success(record.studentName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Announcements & Schedules ---
    suspend fun postAnnouncement(title: String, content: String, targetRole: String, priority: String) =
        withContext(Dispatchers.IO) {
            val announcement = Announcement(
                id = FirestoreCloudMappers.sanitizeId("ann_${UUID.randomUUID().toString().take(12)}"),
                title = title.trim(),
                content = content.trim(),
                targetRole = targetRole,
                priority = priority,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            )
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.createAnnouncementCloud(announcement, adminUid)
            }
            announcementDao.insertAnnouncement(announcement)
        }

    suspend fun updateAnnouncementDetails(announcement: Announcement): Result<Announcement> = withContext(Dispatchers.IO) {
        try {
            if (announcement.title.isBlank() || announcement.content.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Title and content are required"))
            }
            val updated = announcement.copy(
                title = announcement.title.trim(),
                content = announcement.content.trim()
            )
            val adminUid = resolveAdminUidOrNull()
            if (cloudRepo != null && adminUid != null) {
                cloudRepo.upsertAnnouncementCloud(updated, adminUid)
            }
            announcementDao.updateAnnouncement(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val existing = announcementDao.getAnnouncementById(announcementId)
                ?: return@withContext Result.failure(IllegalArgumentException("Announcement not found"))
            if (cloudRepo != null && currentCloudUid() != null) {
                cloudRepo.deleteAnnouncementCloud(existing.id)
            }
            announcementDao.deleteAnnouncementById(existing.id)
            Result.success(existing.title)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun seedDefaultSchedulesIfEmpty() = withContext(Dispatchers.IO) {
        val sch1 = ScheduleItem(
            id = "sch_hifz1_sabaq",
            classId = "class_hifz_1",
            className = "Hifz Halaqah 1 (Advanced)",
            dayOfWeek = "Mon - Sat",
            startTime = "07:00 AM",
            endTime = "08:30 AM",
            subjectOrHalaqah = "Sabaq (New Lesson Recitation)",
            teacherName = "Qari Muhammad Abdullah"
        )
        val sch2 = ScheduleItem(
            id = "sch_hifz1_sabqi",
            classId = "class_hifz_1",
            className = "Hifz Halaqah 1 (Advanced)",
            dayOfWeek = "Mon - Sat",
            startTime = "08:45 AM",
            endTime = "10:30 AM",
            subjectOrHalaqah = "Sabqi (Recent Revision)",
            teacherName = "Qari Muhammad Abdullah"
        )
        val sch3 = ScheduleItem(
            id = "sch_hifz1_manzil",
            classId = "class_hifz_1",
            className = "Hifz Halaqah 1 (Advanced)",
            dayOfWeek = "Mon - Sat",
            startTime = "10:45 AM",
            endTime = "12:00 PM",
            subjectOrHalaqah = "Manzil / Daur (Older Memorization)",
            teacherName = "Qari Muhammad Abdullah"
        )
        scheduleDao.insertSchedules(listOf(sch1, sch2, sch3))
    }

    // --- Seed Initial Data if empty (Local-only / Unit Test mode) ---
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        if (classDao.getAllClasses().isNotEmpty()) return@withContext

        // 1. Classes
        val hifzClass1 = AcademyClass(
            name = "Hifz Halaqah 1 (Advanced)",
            section = "Morning",
            roomNumber = "Halaqah Room A",
            capacity = 15,
            scheduleInfo = "Mon-Sat: 07:00 AM - 12:00 PM"
        )
        val hifzClass2 = AcademyClass(
            name = "Hifz Halaqah 2 (Intermediate)",
            section = "Morning",
            roomNumber = "Halaqah Room B",
            capacity = 15,
            scheduleInfo = "Mon-Sat: 07:00 AM - 12:00 PM"
        )
        val nazraClass = AcademyClass(
            name = "Nazra & Tajweed Foundation",
            section = "Afternoon",
            roomNumber = "Class 101",
            capacity = 25,
            scheduleInfo = "Mon-Fri: 02:30 PM - 05:00 PM"
        )
        classDao.insertClasses(listOf(hifzClass1, hifzClass2, nazraClass))

        // 2. Teachers
        val teacher1 = Teacher(
            fullName = "Qari Muhammad Abdullah",
            contactNumber = "+92 301 2345678",
            email = "abdullah@hafizquranacademy.edu",
            address = "Lahore Cantt",
            assignedClasses = "${hifzClass1.name}, ${hifzClass2.name}",
            assignedClassIds = "${hifzClass1.id},${hifzClass2.id}",
            joiningDate = "2023-01-15",
            notes = "Lead Senior Qari, 10 Qira'at certified, 15+ years experience"
        )
        val teacher2 = Teacher(
            fullName = "Hafiz Usman Tariq",
            contactNumber = "+92 302 3456789",
            email = "usman@hafizquranacademy.edu",
            address = "Johar Town, Lahore",
            assignedClasses = nazraClass.name,
            assignedClassIds = nazraClass.id,
            joiningDate = "2024-03-01",
            notes = "Specialist in Tajweed rules & Makharij for beginners"
        )
        teacherDao.insertTeachers(listOf(teacher1, teacher2))

        classDao.updateClass(hifzClass1.copy(assignedTeacherId = teacher1.id, assignedTeacherName = teacher1.fullName))
        classDao.updateClass(hifzClass2.copy(assignedTeacherId = teacher1.id, assignedTeacherName = teacher1.fullName))
        classDao.updateClass(nazraClass.copy(assignedTeacherId = teacher2.id, assignedTeacherName = teacher2.fullName))

        // 3. Students
        val s1 = Student(
            admissionNumber = "HQA-26-0101",
            fullName = "Muhammad Bilal Farooq",
            guardianName = "Farooq Ahmed",
            dateOfBirth = "2013-05-14",
            admissionDate = "2025-01-10",
            gender = "Male",
            contactNumber = "03004455667",
            address = "Model Town Block C, Lahore",
            classId = hifzClass1.id,
            className = hifzClass1.name,
            teacherId = teacher1.id,
            teacherName = teacher1.fullName,
            quranLevel = "Hifz (15 Juz Completed)",
            monthlyFee = 4500.0,
            feeCategory = "Regular",
            notes = "Excellent voice, preparing for annual Huffaz graduation"
        )

        val s2 = Student(
            admissionNumber = "HQA-26-0102",
            fullName = "Zayd Bin Asad",
            guardianName = "Asadullah Khan",
            dateOfBirth = "2014-08-20",
            admissionDate = "2025-02-01",
            gender = "Male",
            contactNumber = "03128899001",
            address = "Gulberg III, Lahore",
            classId = hifzClass2.id,
            className = hifzClass2.name,
            teacherId = teacher1.id,
            teacherName = teacher1.fullName,
            quranLevel = "Hifz (8 Juz Completed)",
            monthlyFee = 4500.0,
            feeCategory = "Need-Based",
            isFsEligible = true,
            fsStatus = "Approved",
            notes = "Fee Sabilillah approved beneficiary"
        )

        val s3 = Student(
            admissionNumber = "HQA-26-0103",
            fullName = "Hamza Noor",
            guardianName = "Farooq Ahmed",
            dateOfBirth = "2015-11-12",
            admissionDate = "2025-03-15",
            gender = "Male",
            contactNumber = "03004455667",
            address = "Model Town Block C, Lahore",
            classId = nazraClass.id,
            className = nazraClass.name,
            teacherId = teacher2.id,
            teacherName = teacher2.fullName,
            quranLevel = "Nazra with Tajweed",
            monthlyFee = 3500.0,
            feeCategory = "Sibling Discount",
            notes = "Brother of Bilal in Hifz section"
        )

        val s4 = Student(
            admissionNumber = "HQA-26-0104",
            fullName = "Ahmad Raza",
            guardianName = "Raza Ullah",
            dateOfBirth = "2012-04-05",
            admissionDate = "2024-09-01",
            gender = "Male",
            contactNumber = "03217788990",
            address = "Township, Lahore",
            classId = hifzClass1.id,
            className = hifzClass1.name,
            teacherId = teacher1.id,
            teacherName = teacher1.fullName,
            quranLevel = "Hifz (28 Juz Completed)",
            monthlyFee = 4500.0,
            feeCategory = "Regular",
            notes = "Completing Holy Quran memorization next month inshaAllah"
        )

        studentDao.insertStudents(listOf(s1, s2, s3, s4))

        // 4. Role-Isolated Portal Credentials (Username & Password controlled ONLY by Admin)
        val adminUser = UserAccount(
            name = "Nazim-e-Aala (Admin)",
            username = "admin",
            password = "Admin@123",
            email = "admin@hafizquranacademy.edu",
            role = UserRole.ADMIN,
            phone = "03707304820"
        )
        val teacherUser1 = UserAccount(
            name = teacher1.fullName,
            username = "qari.abdullah",
            password = "Tchr@101",
            email = teacher1.email,
            role = UserRole.TEACHER,
            phone = teacher1.contactNumber,
            teacherId = teacher1.id,
            assignedClassIds = "${hifzClass1.id},${hifzClass2.id}"
        )
        val teacherUser2 = UserAccount(
            name = teacher2.fullName,
            username = "hafiz.usman",
            password = "Tchr@102",
            email = teacher2.email,
            role = UserRole.TEACHER,
            phone = teacher2.contactNumber,
            teacherId = teacher2.id,
            assignedClassIds = nazraClass.id
        )
        val parentUser1 = UserAccount(
            name = "Farooq Ahmed (Parent)",
            username = "p.farooq",
            password = "Prnt@101",
            role = UserRole.PARENT,
            phone = s1.contactNumber,
            linkedStudentIds = "${s1.id},${s3.id}"
        )
        val parentUser2 = UserAccount(
            name = "Asadullah Khan (Parent)",
            username = "p.asad",
            password = "Prnt@102",
            role = UserRole.PARENT,
            phone = s2.contactNumber,
            linkedStudentIds = s2.id
        )
        val parentUser3 = UserAccount(
            name = "Raza Ullah (Parent)",
            username = "p.raza",
            password = "Prnt@104",
            role = UserRole.PARENT,
            phone = s4.contactNumber,
            linkedStudentIds = s4.id
        )
        userDao.insertUsers(listOf(adminUser, teacherUser1, teacherUser2, parentUser1, parentUser2, parentUser3))

        // 5. Fee Sabilillah record for s2
        val fsRecord = FeeSabilillahRecord(
            studentId = s2.id,
            studentName = s2.fullName,
            admissionNumber = s2.admissionNumber,
            className = s2.className,
            category = "Deserving Student",
            assistanceType = AssistanceType.FULL,
            approvedAmount = s2.monthlyFee,
            effectiveStartDate = "2026-01-01",
            approvalStatus = ApprovalStatus.APPROVED,
            approverName = "Admin Council",
            approvalDate = "2026-01-05",
            confidentialRemarks = "Family verified by Zakat & Social Committee. 100% Fee waived under Academy Waqf."
        )
        feeSabilillahDao.insertFsRecord(fsRecord)

        // 6. Recent Quran Progress Records
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val p1 = QuranProgressRecord(
            date = today,
            studentId = s1.id,
            studentName = s1.fullName,
            admissionNumber = s1.admissionNumber,
            teacherId = teacher1.id,
            teacherName = teacher1.fullName,
            surahName = "Surah Maryam",
            surahNumber = 19,
            juzNumber = 16,
            sabaqAyatStart = 1,
            sabaqAyatEnd = 25,
            sabaqRating = QuranProgressRating.EXCELLENT,
            sabqiSummary = "Surah Al-Kahf (Ayat 75-110)",
            manzilSummary = "Juz 14 Complete",
            mistakesCount = 1,
            teacherRemarks = "MashaAllah melodious recitation with strict adherence to Madd and Ghunnah rules.",
            nextTarget = "Surah Maryam Ayat 26-50"
        )

        val p2 = QuranProgressRecord(
            date = today,
            studentId = s2.id,
            studentName = s2.fullName,
            admissionNumber = s2.admissionNumber,
            teacherId = teacher1.id,
            teacherName = teacher1.fullName,
            surahName = "Surah Al-An'am",
            surahNumber = 6,
            juzNumber = 8,
            sabaqAyatStart = 40,
            sabaqAyatEnd = 58,
            sabaqRating = QuranProgressRating.GOOD,
            sabqiSummary = "Surah Al-An'am (Ayat 1-39)",
            manzilSummary = "Juz 7 Quarter 2",
            mistakesCount = 3,
            teacherRemarks = "Good effort. Needs attention on Ikhfaa pronunciation in Sabqi.",
            nextTarget = "Surah Al-An'am Ayat 59-73"
        )
        quranProgressDao.insertProgressList(listOf(p1, p2))

        // 7. Today's Attendance
        val att1 = AttendanceRecord(
            date = today,
            classId = hifzClass1.id,
            className = hifzClass1.name,
            studentId = s1.id,
            studentName = s1.fullName,
            admissionNumber = s1.admissionNumber,
            status = AttendanceStatus.PRESENT,
            markedByTeacherId = teacher1.id
        )
        val att2 = AttendanceRecord(
            date = today,
            classId = hifzClass1.id,
            className = hifzClass1.name,
            studentId = s4.id,
            studentName = s4.fullName,
            admissionNumber = s4.admissionNumber,
            status = AttendanceStatus.PRESENT,
            markedByTeacherId = teacher1.id
        )
        val att3 = AttendanceRecord(
            date = today,
            classId = hifzClass2.id,
            className = hifzClass2.name,
            studentId = s2.id,
            studentName = s2.fullName,
            admissionNumber = s2.admissionNumber,
            status = AttendanceStatus.PRESENT,
            markedByTeacherId = teacher1.id
        )
        attendanceDao.insertAttendanceRecords(listOf(att1, att2, att3))

        // 8. Challans for Current Month
        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val dueDate = SimpleDateFormat("yyyy-MM-10", Locale.US).format(Date())

        val ch1 = FeeChallan(
            challanNumber = "HQA-$currentYear-10-0101-789",
            studentId = s1.id,
            admissionNumber = s1.admissionNumber,
            studentName = s1.fullName,
            classId = s1.classId,
            className = s1.className,
            billingMonth = currentMonth,
            billingYear = currentYear,
            issueDate = today,
            dueDate = dueDate,
            monthlyTuitionFee = s1.monthlyFee,
            additionalCharges = 200.0,
            discountAmount = 0.0,
            fsAssistanceAmount = 0.0,
            previousBalance = 0.0,
            totalPayable = 4700.0,
            amountPaid = 0.0,
            remainingBalance = 4700.0,
            status = ChallanStatus.UNPAID
        )

        val ch2 = FeeChallan(
            challanNumber = "HQA-$currentYear-10-0102-456",
            studentId = s2.id,
            admissionNumber = s2.admissionNumber,
            studentName = s2.fullName,
            classId = s2.classId,
            className = s2.className,
            billingMonth = currentMonth,
            billingYear = currentYear,
            issueDate = today,
            dueDate = dueDate,
            monthlyTuitionFee = s2.monthlyFee,
            additionalCharges = 0.0,
            discountAmount = 0.0,
            fsAssistanceAmount = s2.monthlyFee,
            previousBalance = 0.0,
            totalPayable = 0.0,
            amountPaid = 0.0,
            remainingBalance = 0.0,
            status = ChallanStatus.PAID,
            remarks = "100% Fee Sabilillah (FS) Approved"
        )

        val ch4 = FeeChallan(
            challanNumber = "HQA-$currentYear-10-0104-332",
            studentId = s4.id,
            admissionNumber = s4.admissionNumber,
            studentName = s4.fullName,
            classId = s4.classId,
            className = s4.className,
            billingMonth = currentMonth,
            billingYear = currentYear,
            issueDate = today,
            dueDate = dueDate,
            monthlyTuitionFee = s4.monthlyFee,
            additionalCharges = 0.0,
            discountAmount = 0.0,
            fsAssistanceAmount = 0.0,
            previousBalance = 0.0,
            totalPayable = 4500.0,
            amountPaid = 4500.0,
            remainingBalance = 0.0,
            status = ChallanStatus.PAID,
            paymentDate = today,
            paymentMethod = "JazzCash (03707304820)",
            transactionRef = "TID-8841209341"
        )
        feeChallanDao.insertChallans(listOf(ch1, ch2, ch4))

        val payment1 = FeePayment(
            challanId = ch4.id,
            challanNumber = ch4.challanNumber,
            studentId = s4.id,
            studentName = s4.fullName,
            amount = 4500.0,
            paymentDate = today,
            paymentMethod = "JazzCash (03707304820)",
            receiptNumber = "TID-8841209341",
            receivedBy = "Online JazzCash Transfer",
            remarks = "Online QR payment verified for $currentMonth"
        )
        feePaymentDao.insertPayment(payment1)

        // 9. Announcements
        val ann1 = Announcement(
            title = "Online Fee Payment via JazzCash & EasyPaisa QR",
            content = "Parents can now pay monthly fee challans online by scanning the QR code printed on the challan via JazzCash (03707304820), EasyPaisa (03493895409), or any Banking App (Raast).",
            priority = "High",
            date = today
        )
        val ann2 = Announcement(
            title = "Friday Halaqah Schedule & Timings",
            content = "Please note that on Fridays, all morning classes will conclude by 11:30 AM to allow students and teachers to prepare for Jumu'ah prayer.",
            priority = "Normal",
            date = today
        )
        announcementDao.insertAnnouncements(listOf(ann1, ann2))

        // 10. Schedules
        val sch1 = ScheduleItem(
            classId = hifzClass1.id,
            className = hifzClass1.name,
            dayOfWeek = "Mon - Sat",
            startTime = "07:00 AM",
            endTime = "08:30 AM",
            subjectOrHalaqah = "Sabaq (New Lesson Recitation)",
            teacherName = teacher1.fullName
        )
        val sch2 = ScheduleItem(
            classId = hifzClass1.id,
            className = hifzClass1.name,
            dayOfWeek = "Mon - Sat",
            startTime = "08:45 AM",
            endTime = "10:30 AM",
            subjectOrHalaqah = "Sabqi (Recent Revision)",
            teacherName = teacher1.fullName
        )
        val sch3 = ScheduleItem(
            classId = hifzClass1.id,
            className = hifzClass1.name,
            dayOfWeek = "Mon - Sat",
            startTime = "10:45 AM",
            endTime = "12:00 PM",
            subjectOrHalaqah = "Manzil / Daur (Older Memorization)",
            teacherName = teacher1.fullName
        )
        scheduleDao.insertSchedules(listOf(sch1, sch2, sch3))
    }
}
