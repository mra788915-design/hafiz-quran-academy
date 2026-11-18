package com.example.data.remote

import com.example.data.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.security.MessageDigest
import java.util.Locale

data class CloudUserProfile(
    val userId: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.PARENT,
    val ticketHash: String = "",
    val academyId: String = FirestoreCloudMappers.ACADEMY_ID,
    val teacherId: String = "",
    val assignedClassIds: List<String> = emptyList(),
    val linkedStudentIds: List<String> = emptyList()
)

object FirestoreCloudMappers {
    const val ACADEMY_ID = "hqa_main"

    fun computeTicketHash(username: String, password: String): String {
        val canonical = "${username.trim().lowercase(Locale.US)}:${password.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sanitizeId(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.take(120).ifBlank { "id_${System.currentTimeMillis()}" }
    }

    fun parseIdList(csv: String): List<String> =
        csv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { sanitizeId(it) }
            .distinct()

    @Suppress("UNCHECKED_CAST")
    fun DocumentSnapshot.getStringList(field: String): List<String> =
        (get(field) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun DocumentSnapshot.getEpochMillis(field: String): Long =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()

    // --- Firestore DocumentSnapshot -> Local Room Domain Models ---

    fun DocumentSnapshot.toCloudUserProfile(): CloudUserProfile? {
        if (!exists()) return null
        val roleStr = getString("role") ?: return null
        val role = runCatching { UserRole.valueOf(roleStr) }.getOrNull() ?: return null
        return CloudUserProfile(
            userId = id,
            displayName = getString("displayName") ?: "",
            role = role,
            ticketHash = getString("ticketHash") ?: "",
            academyId = getString("academyId") ?: ACADEMY_ID,
            teacherId = getString("teacherId") ?: "",
            assignedClassIds = getStringList("assignedClassIds"),
            linkedStudentIds = getStringList("linkedStudentIds")
        )
    }

    fun DocumentSnapshot.toUserAccount(): UserAccount? {
        if (!exists()) return null
        val roleStr = getString("role") ?: return null
        val role = runCatching { UserRole.valueOf(roleStr) }.getOrNull() ?: return null
        return UserAccount(
            id = getString("accountId") ?: id,
            name = getString("name") ?: "",
            username = getString("username") ?: "",
            password = getString("password") ?: "",
            email = getString("email") ?: "",
            role = role,
            phone = getString("phone") ?: "",
            teacherId = getString("teacherId") ?: "",
            assignedClassIds = getString("assignedClassIds") ?: "",
            linkedStudentIds = getString("linkedStudentIds") ?: "",
            isActive = getBoolean("isActive") ?: true,
            createdAt = getEpochMillis("createdAt")
        )
    }

    fun DocumentSnapshot.toAcademyClass(): AcademyClass? {
        if (!exists()) return null
        return AcademyClass(
            id = getString("classId") ?: id,
            name = getString("name") ?: "",
            section = getString("section") ?: "Morning",
            roomNumber = getString("roomNumber") ?: "Room A",
            capacity = (getLong("capacity") ?: 20L).toInt(),
            assignedTeacherId = getString("assignedTeacherId") ?: "",
            assignedTeacherName = getString("assignedTeacherName") ?: "",
            scheduleInfo = getString("scheduleInfo") ?: "",
            isActive = getBoolean("isActive") ?: true
        )
    }

    fun DocumentSnapshot.toTeacher(): Teacher? {
        if (!exists()) return null
        return Teacher(
            id = getString("teacherId") ?: id,
            fullName = getString("fullName") ?: "",
            contactNumber = getString("contactNumber") ?: "",
            email = getString("email") ?: "",
            address = getString("address") ?: "",
            assignedClasses = getString("assignedClasses") ?: "",
            assignedClassIds = getString("assignedClassIds") ?: "",
            joiningDate = getString("joiningDate") ?: "",
            isActive = getBoolean("isActive") ?: true,
            notes = getString("notes") ?: ""
        )
    }

    fun DocumentSnapshot.toStudent(): Student? {
        if (!exists()) return null
        val statusStr = getString("status") ?: "ACTIVE"
        val status = runCatching { StudentStatus.valueOf(statusStr) }.getOrDefault(StudentStatus.ACTIVE)
        return Student(
            id = getString("studentId") ?: id,
            admissionNumber = getString("admissionNumber") ?: "",
            fullName = getString("fullName") ?: "",
            guardianName = getString("guardianName") ?: "",
            dateOfBirth = getString("dateOfBirth") ?: "",
            admissionDate = getString("admissionDate") ?: "",
            gender = getString("gender") ?: "Male",
            contactNumber = getString("contactNumber") ?: "",
            alternateContact = getString("alternateContact") ?: "",
            address = getString("address") ?: "",
            classId = getString("classId") ?: "",
            className = getString("className") ?: "",
            teacherId = getString("teacherId") ?: "",
            teacherName = getString("teacherName") ?: "",
            quranLevel = getString("quranLevel") ?: "Hifz",
            monthlyFee = getDouble("monthlyFee") ?: 0.0,
            feeCategory = getString("feeCategory") ?: "Regular",
            isFsEligible = getBoolean("isFsEligible") ?: false,
            fsStatus = getString("fsStatus") ?: "None",
            status = status,
            notes = getString("notes") ?: "",
            completedCourse = getString("completedCourse") ?: "",
            completionDate = getString("completionDate") ?: "",
            certificateNumber = getString("certificateNumber") ?: "",
            finalGrade = getString("finalGrade") ?: "",
            certificateRemarks = getString("certificateRemarks") ?: "",
            createdAt = getEpochMillis("createdAt"),
            updatedAt = getEpochMillis("updatedAt")
        )
    }

    fun DocumentSnapshot.toAttendanceRecord(): AttendanceRecord? {
        if (!exists()) return null
        val status = runCatching {
            AttendanceStatus.valueOf(getString("status") ?: "PRESENT")
        }.getOrDefault(AttendanceStatus.PRESENT)
        return AttendanceRecord(
            id = getString("attendanceId") ?: id,
            date = getString("date") ?: "",
            classId = getString("classId") ?: "",
            className = getString("className") ?: "",
            studentId = getString("studentId") ?: "",
            studentName = getString("studentName") ?: "",
            admissionNumber = getString("admissionNumber") ?: "",
            status = status,
            remarks = getString("remarks") ?: "",
            markedByTeacherId = getString("markedByTeacherId") ?: "",
            timestamp = getEpochMillis("updatedAt")
        )
    }

    fun DocumentSnapshot.toQuranProgressRecord(): QuranProgressRecord? {
        if (!exists()) return null
        val rating = runCatching {
            QuranProgressRating.valueOf(getString("sabaqRating") ?: "GOOD")
        }.getOrDefault(QuranProgressRating.GOOD)
        return QuranProgressRecord(
            id = getString("progressId") ?: id,
            date = getString("date") ?: "",
            studentId = getString("studentId") ?: "",
            studentName = getString("studentName") ?: "",
            admissionNumber = getString("admissionNumber") ?: "",
            teacherId = getString("teacherId") ?: "",
            teacherName = getString("teacherName") ?: "",
            surahName = getString("surahName") ?: "",
            surahNumber = (getLong("surahNumber") ?: 1L).toInt(),
            juzNumber = (getLong("juzNumber") ?: 1L).toInt(),
            sabaqAyatStart = (getLong("sabaqAyatStart") ?: 1L).toInt(),
            sabaqAyatEnd = (getLong("sabaqAyatEnd") ?: 10L).toInt(),
            sabaqRating = rating,
            sabqiSummary = getString("sabqiSummary") ?: "",
            manzilSummary = getString("manzilSummary") ?: "",
            mistakesCount = (getLong("mistakesCount") ?: 0L).toInt(),
            teacherRemarks = getString("teacherRemarks") ?: "",
            nextTarget = getString("nextTarget") ?: "",
            timestamp = getEpochMillis("updatedAt")
        )
    }

    fun DocumentSnapshot.toFeeChallan(): FeeChallan? {
        if (!exists()) return null
        val status = runCatching {
            ChallanStatus.valueOf(getString("status") ?: "UNPAID")
        }.getOrDefault(ChallanStatus.UNPAID)
        return FeeChallan(
            id = getString("challanId") ?: id,
            challanNumber = getString("challanNumber") ?: "",
            studentId = getString("studentId") ?: "",
            admissionNumber = getString("admissionNumber") ?: "",
            studentName = getString("studentName") ?: "",
            classId = getString("classId") ?: "",
            className = getString("className") ?: "",
            billingMonth = getString("billingMonth") ?: "",
            billingYear = (getLong("billingYear") ?: 2026L).toInt(),
            issueDate = getString("issueDate") ?: "",
            dueDate = getString("dueDate") ?: "",
            monthlyTuitionFee = getDouble("monthlyTuitionFee") ?: 0.0,
            additionalCharges = getDouble("additionalCharges") ?: 0.0,
            discountAmount = getDouble("discountAmount") ?: 0.0,
            fsAssistanceAmount = getDouble("fsAssistanceAmount") ?: 0.0,
            previousBalance = getDouble("previousBalance") ?: 0.0,
            totalPayable = getDouble("totalPayable") ?: 0.0,
            amountPaid = getDouble("amountPaid") ?: 0.0,
            remainingBalance = getDouble("remainingBalance") ?: 0.0,
            status = status,
            paymentDate = getString("paymentDate") ?: "",
            paymentMethod = getString("paymentMethod") ?: "",
            transactionRef = getString("transactionRef") ?: "",
            remarks = getString("remarks") ?: "",
            createdAt = getEpochMillis("createdAt")
        )
    }

    fun DocumentSnapshot.toFeePayment(): FeePayment? {
        if (!exists()) return null
        return FeePayment(
            id = getString("paymentId") ?: id,
            challanId = getString("challanId") ?: "",
            challanNumber = getString("challanNumber") ?: "",
            studentId = getString("studentId") ?: "",
            studentName = getString("studentName") ?: "",
            amount = getDouble("amount") ?: 0.0,
            paymentDate = getString("paymentDate") ?: "",
            paymentMethod = getString("paymentMethod") ?: "",
            receiptNumber = getString("receiptNumber") ?: "",
            receivedBy = getString("receivedBy") ?: "",
            remarks = getString("remarks") ?: "",
            timestamp = getEpochMillis("createdAt")
        )
    }

    fun DocumentSnapshot.toFsRecord(): FeeSabilillahRecord? {
        if (!exists()) return null
        val assistanceType = runCatching {
            AssistanceType.valueOf(getString("assistanceType") ?: "FULL")
        }.getOrDefault(AssistanceType.FULL)
        val approvalStatus = runCatching {
            ApprovalStatus.valueOf(getString("approvalStatus") ?: "PENDING")
        }.getOrDefault(ApprovalStatus.PENDING)
        return FeeSabilillahRecord(
            id = getString("fsId") ?: id,
            studentId = getString("studentId") ?: "",
            studentName = getString("studentName") ?: "",
            admissionNumber = getString("admissionNumber") ?: "",
            className = getString("className") ?: "",
            category = getString("category") ?: "Deserving",
            assistanceType = assistanceType,
            approvedAmount = getDouble("approvedAmount") ?: 0.0,
            effectiveStartDate = getString("effectiveStartDate") ?: "",
            effectiveEndDate = getString("effectiveEndDate") ?: "",
            approvalStatus = approvalStatus,
            approverName = getString("approverName") ?: "",
            approvalDate = getString("approvalDate") ?: "",
            confidentialRemarks = getString("confidentialRemarks") ?: "",
            createdAt = getEpochMillis("createdAt")
        )
    }

    fun DocumentSnapshot.toAnnouncement(): Announcement? {
        if (!exists()) return null
        return Announcement(
            id = getString("announcementId") ?: id,
            title = getString("title") ?: "",
            content = getString("content") ?: "",
            targetRole = getString("targetRole") ?: "All",
            date = getString("date") ?: "",
            priority = getString("priority") ?: "Normal",
            authorName = getString("authorName") ?: "Hafiz Quran Academy",
            timestamp = getEpochMillis("createdAt")
        )
    }

    // --- Domain Models -> Validated Firestore Write Payloads ---

    fun buildAuthTicketPayload(
        account: UserAccount,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Pair<String, Map<String, Any>> {
        val ticketHash = computeTicketHash(account.username, account.password)
        val map = mapOf<String, Any>(
            "ticketHash" to ticketHash,
            "accountId" to sanitizeId(account.id),
            "academyId" to ACADEMY_ID,
            "adminUid" to adminUid,
            "displayName" to account.name.take(120).ifBlank { "Portal User" },
            "role" to account.role.name,
            "teacherId" to account.teacherId.take(128),
            "assignedClassIds" to parseIdList(account.assignedClassIds).take(20),
            "linkedStudentIds" to parseIdList(account.linkedStudentIds).take(20),
            "isActive" to account.isActive,
            "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return ticketHash to map
    }

    fun buildVaultPayload(
        account: UserAccount,
        adminUid: String,
        ticketHash: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "accountId" to sanitizeId(account.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "name" to account.name.take(120).ifBlank { "Portal User" },
        "username" to account.username.trim().take(60),
        "password" to account.password.trim().take(80),
        "email" to account.email.trim().take(120),
        "phone" to account.phone.trim().take(40),
        "role" to account.role.name,
        "teacherId" to account.teacherId.trim().take(128),
        "assignedClassIds" to account.assignedClassIds.trim().take(500),
        "linkedStudentIds" to account.linkedStudentIds.trim().take(500),
        "ticketHash" to ticketHash,
        "isActive" to account.isActive,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildClassPayload(
        c: AcademyClass,
        adminUid: String,
        studentIds: List<String>,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "classId" to sanitizeId(c.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "name" to c.name.take(120).ifBlank { "Halaqah" },
        "section" to c.section.take(60).ifBlank { "Morning" },
        "roomNumber" to c.roomNumber.take(60).ifBlank { "Room A" },
        "capacity" to c.capacity.coerceIn(1, 200),
        "assignedTeacherId" to c.assignedTeacherId.take(128),
        "assignedTeacherName" to c.assignedTeacherName.take(120),
        "scheduleInfo" to c.scheduleInfo.take(200),
        "isActive" to c.isActive,
        "studentIds" to studentIds.map { sanitizeId(it) }.distinct().take(60),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildTeacherPayload(
        t: Teacher,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "teacherId" to sanitizeId(t.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "fullName" to t.fullName.take(120).ifBlank { "Teacher" },
        "contactNumber" to t.contactNumber.take(40).ifBlank { "0000000000" },
        "email" to t.email.take(120),
        "address" to t.address.take(250),
        "assignedClasses" to t.assignedClasses.take(300),
        "assignedClassIds" to t.assignedClassIds.take(300),
        "joiningDate" to t.joiningDate.take(30).ifBlank { "2025-01-01" },
        "isActive" to t.isActive,
        "notes" to t.notes.take(400),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildStudentPayload(
        s: Student,
        adminUid: String,
        recentAttIds: List<String> = emptyList(),
        recentProgIds: List<String> = emptyList(),
        recentChallanIds: List<String> = emptyList(),
        recentPaymentIds: List<String> = emptyList(),
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "studentId" to sanitizeId(s.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "admissionNumber" to s.admissionNumber.take(40).ifBlank { "HQA-0001" },
        "fullName" to s.fullName.take(120).ifBlank { "Student" },
        "guardianName" to s.guardianName.take(120).ifBlank { "Guardian" },
        "dateOfBirth" to s.dateOfBirth.take(30),
        "admissionDate" to s.admissionDate.take(30).ifBlank { "2026-01-01" },
        "gender" to s.gender.take(20).ifBlank { "Male" },
        "contactNumber" to s.contactNumber.take(40).ifBlank { "0000000000" },
        "alternateContact" to s.alternateContact.take(40),
        "address" to s.address.take(250),
        "classId" to sanitizeId(s.classId),
        "className" to s.className.take(120).ifBlank { "Halaqah" },
        "teacherId" to s.teacherId.take(128),
        "teacherName" to s.teacherName.take(120),
        "quranLevel" to s.quranLevel.take(120).ifBlank { "Hifz" },
        "monthlyFee" to s.monthlyFee.coerceIn(0.0, 1000000.0),
        "feeCategory" to s.feeCategory.take(60).ifBlank { "Regular" },
        "isFsEligible" to s.isFsEligible,
        "fsStatus" to s.fsStatus.take(40).ifBlank { "None" },
        "status" to s.status.name,
        "notes" to s.notes.take(400),
        "completedCourse" to s.completedCourse.take(120),
        "completionDate" to s.completionDate.take(30),
        "certificateNumber" to s.certificateNumber.take(60),
        "finalGrade" to s.finalGrade.take(80),
        "certificateRemarks" to s.certificateRemarks.take(350),
        "recentAttendanceIds" to recentAttIds.map { sanitizeId(it) }.distinct().take(40),
        "recentProgressIds" to recentProgIds.map { sanitizeId(it) }.distinct().take(30),
        "recentChallanIds" to recentChallanIds.map { sanitizeId(it) }.distinct().take(24),
        "recentPaymentIds" to recentPaymentIds.map { sanitizeId(it) }.distinct().take(24),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildAttendancePayload(
        a: AttendanceRecord,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "attendanceId" to sanitizeId(a.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "date" to a.date.take(20).ifBlank { "2026-01-01" },
        "classId" to sanitizeId(a.classId),
        "className" to a.className.take(120).ifBlank { "Halaqah" },
        "studentId" to sanitizeId(a.studentId),
        "studentName" to a.studentName.take(120).ifBlank { "Student" },
        "admissionNumber" to a.admissionNumber.take(40).ifBlank { "HQA-0001" },
        "status" to a.status.name,
        "remarks" to a.remarks.take(250),
        "markedByTeacherId" to a.markedByTeacherId.take(128).ifBlank { "Admin" },
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildProgressPayload(
        p: QuranProgressRecord,
        classId: String,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "progressId" to sanitizeId(p.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "date" to p.date.take(20).ifBlank { "2026-01-01" },
        "classId" to sanitizeId(classId),
        "studentId" to sanitizeId(p.studentId),
        "studentName" to p.studentName.take(120).ifBlank { "Student" },
        "admissionNumber" to p.admissionNumber.take(40).ifBlank { "HQA-0001" },
        "teacherId" to p.teacherId.take(128).ifBlank { "Teacher" },
        "teacherName" to p.teacherName.take(120).ifBlank { "Academy Qari" },
        "surahName" to p.surahName.take(80).ifBlank { "Surah Al-Fatihah" },
        "surahNumber" to p.surahNumber.coerceIn(1, 114),
        "juzNumber" to p.juzNumber.coerceIn(1, 30),
        "sabaqAyatStart" to p.sabaqAyatStart.coerceIn(1, 300),
        "sabaqAyatEnd" to p.sabaqAyatEnd.coerceIn(1, 300),
        "sabaqRating" to p.sabaqRating.name,
        "sabqiSummary" to p.sabqiSummary.take(200),
        "manzilSummary" to p.manzilSummary.take(200),
        "mistakesCount" to p.mistakesCount.coerceIn(0, 200),
        "teacherRemarks" to p.teacherRemarks.take(350),
        "nextTarget" to p.nextTarget.take(200),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildChallanPayload(
        ch: FeeChallan,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "challanId" to sanitizeId(ch.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "challanNumber" to ch.challanNumber.take(60).ifBlank { "HQA-CH-001" },
        "studentId" to sanitizeId(ch.studentId),
        "admissionNumber" to ch.admissionNumber.take(40).ifBlank { "HQA-0001" },
        "studentName" to ch.studentName.take(120).ifBlank { "Student" },
        "classId" to sanitizeId(ch.classId),
        "className" to ch.className.take(120).ifBlank { "Halaqah" },
        "billingMonth" to ch.billingMonth.take(30).ifBlank { "October" },
        "billingYear" to ch.billingYear.coerceIn(2020, 2100),
        "issueDate" to ch.issueDate.take(25).ifBlank { "2026-10-01" },
        "dueDate" to ch.dueDate.take(25).ifBlank { "2026-10-10" },
        "monthlyTuitionFee" to ch.monthlyTuitionFee.coerceIn(0.0, 1000000.0),
        "additionalCharges" to ch.additionalCharges.coerceIn(0.0, 1000000.0),
        "discountAmount" to ch.discountAmount.coerceIn(0.0, 1000000.0),
        "fsAssistanceAmount" to ch.fsAssistanceAmount.coerceIn(0.0, 1000000.0),
        "previousBalance" to ch.previousBalance.coerceIn(0.0, 5000000.0),
        "totalPayable" to ch.totalPayable.coerceIn(0.0, 5000000.0),
        "amountPaid" to ch.amountPaid.coerceIn(0.0, 5000000.0),
        "remainingBalance" to ch.remainingBalance.coerceIn(0.0, 5000000.0),
        "status" to ch.status.name,
        "paymentDate" to ch.paymentDate.take(35),
        "paymentMethod" to ch.paymentMethod.take(80),
        "transactionRef" to ch.transactionRef.take(80),
        "remarks" to ch.remarks.take(250),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildPaymentPayload(
        p: FeePayment,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "paymentId" to sanitizeId(p.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "challanId" to sanitizeId(p.challanId),
        "challanNumber" to p.challanNumber.take(60).ifBlank { "HQA-CH-001" },
        "studentId" to sanitizeId(p.studentId),
        "studentName" to p.studentName.take(120).ifBlank { "Student" },
        "amount" to p.amount.coerceIn(1.0, 5000000.0),
        "paymentDate" to p.paymentDate.take(35).ifBlank { "2026-10-01 10:00" },
        "paymentMethod" to p.paymentMethod.take(80).ifBlank { "Online QR" },
        "receiptNumber" to p.receiptNumber.take(80).ifBlank { "REC-00001" },
        "receivedBy" to p.receivedBy.take(120).ifBlank { "Academy Office" },
        "remarks" to p.remarks.take(250),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildFsPayload(
        fs: FeeSabilillahRecord,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> = mapOf(
        "fsId" to sanitizeId(fs.id),
        "academyId" to ACADEMY_ID,
        "adminUid" to adminUid,
        "studentId" to sanitizeId(fs.studentId),
        "studentName" to fs.studentName.take(120).ifBlank { "Student" },
        "admissionNumber" to fs.admissionNumber.take(40).ifBlank { "HQA-0001" },
        "className" to fs.className.take(120).ifBlank { "Halaqah" },
        "category" to fs.category.take(80).ifBlank { "Deserving" },
        "assistanceType" to fs.assistanceType.name,
        "approvedAmount" to fs.approvedAmount.coerceIn(0.0, 1000000.0),
        "effectiveStartDate" to fs.effectiveStartDate.take(25).ifBlank { "2026-01-01" },
        "effectiveEndDate" to fs.effectiveEndDate.take(25),
        "approvalStatus" to fs.approvalStatus.name,
        "approverName" to fs.approverName.take(120),
        "approvalDate" to fs.approvalDate.take(25),
        "confidentialRemarks" to fs.confidentialRemarks.take(400),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun buildAnnouncementPayload(
        a: Announcement,
        adminUid: String,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> {
        val validTarget = if (a.targetRole in listOf("All", "Teachers", "Parents")) a.targetRole else "All"
        val validPriority = if (a.priority in listOf("Normal", "High", "Urgent")) a.priority else "Normal"
        return mapOf(
            "announcementId" to sanitizeId(a.id),
            "academyId" to ACADEMY_ID,
            "adminUid" to adminUid,
            "title" to a.title.take(150).ifBlank { "Academy Notice" },
            "content" to a.content.take(1500).ifBlank { "Notice" },
            "targetRole" to validTarget,
            "date" to a.date.take(25).ifBlank { "2026-01-01" },
            "priority" to validPriority,
            "authorName" to a.authorName.take(120).ifBlank { "Hafiz Quran Academy" },
            "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
