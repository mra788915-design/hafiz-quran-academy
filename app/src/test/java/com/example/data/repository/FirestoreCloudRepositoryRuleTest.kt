package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.AcademyDatabase
import com.example.data.models.Announcement
import com.example.data.models.ApprovalStatus
import com.example.data.models.AttendanceRecord
import com.example.data.models.AttendanceStatus
import com.example.data.models.ChallanStatus
import com.example.data.models.FeeChallan
import com.example.data.models.NamazProgressRating
import com.example.data.models.QuranProgressRating
import com.example.data.models.QuranProgressRecord
import com.example.data.models.Student
import com.example.data.models.StudentStatus
import com.example.data.models.UserRole
import com.example.data.remote.FirestoreCloudMappers
import com.example.data.remote.FirestoreCloudRepository
import com.example.util.CertificatePrintHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FirestoreCloudRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun createInMemoryDb(): AcademyDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AcademyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun ensureAdminBootstrapped(): Pair<String, FirestoreCloudRepository> {
        val adminUid = signInTestUser(ADMIN_EMAIL)
        val repo = FirestoreCloudRepository(firestore)
        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.authenticateAndClaimSession(
                uid = adminUid,
                email = ADMIN_EMAIL,
                usernameInput = "admin",
                passwordInput = "Admin@123",
                requiredRole = UserRole.ADMIN
            )
        }
        assertTrue("Admin bootstrap/login failed: ${res.exceptionOrNull()}", res.isSuccess)
        return adminUid to repo
    }

    @Test
    fun adminBootstrap_andRealtimeStudentsFlow_emitsSeededAndAdmittedStudents(): Unit = runBlocking {
        val (adminUid, repo) = ensureAdminBootstrapped()
        val resolvedOwnerUid = withTimeout(DEFAULT_TIMEOUT_MS) { repo.resolveAcademyAdminUid(adminUid) }

        val dynamicStudentId = "std_rule_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val student = Student(
            id = dynamicStudentId,
            admissionNumber = "HQA-26-0999",
            fullName = "Hafiz Salman Test",
            guardianName = "Tariq Mahmood",
            admissionDate = "2026-10-24",
            contactNumber = "03009998887",
            address = "Model Town Lahore",
            classId = "cls_hifz_1",
            className = "Hifz Halaqah 1 (Advanced)",
            teacherId = "tchr_qari_abdullah",
            teacherName = "Qari Muhammad Abdullah",
            monthlyFee = 4500.0
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createOrUpdateStudentCloud(student, resolvedOwnerUid)
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAdminStudents(resolvedOwnerUid).first { list ->
                list.any { it.id == dynamicStudentId }
            }
        }
        assertTrue(emitted.any { it.id == dynamicStudentId })

        // Verify permanent removal of withdrawn student deletes document from cloud
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.withdrawStudentPermanentlyCloud(dynamicStudentId)
        }
        val afterDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getStudentById(dynamicStudentId)
        }
        assertTrue(afterDelete.isFailure)
    }

    @Test
    fun adminAddsTeacherAndAssignsClass_thenTeacherPortalSyncsAllAssignedClassStudents(): Unit = runBlocking {
        val (adminUid, adminCloudRepo) = ensureAdminBootstrapped()
        val adminLocalDb = createInMemoryDb()
        val teacherLocalDb = createInMemoryDb()
        try {
            val adminAcademyRepo = AcademyRepository(adminLocalDb, adminCloudRepo).apply {
                activeCloudUid = adminUid
            }

            // Start Admin realtime sync briefly so adminLocalDb has seeded classes & students
            val adminProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.authenticateAndClaimSession(
                    uid = adminUid,
                    email = ADMIN_EMAIL,
                    usernameInput = "admin",
                    passwordInput = "Admin@123",
                    requiredRole = UserRole.ADMIN
                ).getOrThrow()
            }
            val adminSyncJob = launch {
                adminCloudRepo.syncRoleDataRealtime(
                    uid = adminUid,
                    profile = adminProfile,
                    localDb = adminLocalDb,
                    onSessionRevoked = {},
                    onProfileUpdated = {}
                )
            }
            withTimeout(FLOW_TIMEOUT_MS) {
                adminAcademyRepo.allClasses.first { it.isNotEmpty() }
                adminAcademyRepo.allStudents.first { it.isNotEmpty() }
            }

            // Admin creates a brand-new Class, admits a Student into it, and adds a brand-new Teacher assigned to that Class
            val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
            val newClass = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminAcademyRepo.addClass(
                    name = "Tajweed Special $suffix",
                    section = "Evening",
                    roomNumber = "Hall $suffix",
                    capacity = 25,
                    teacherId = "",
                    teacherName = "",
                    scheduleInfo = "Asr to Maghrib"
                ).getOrThrow()
            }

            val newStudent = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminAcademyRepo.admitStudent(
                    fullName = "Hamza Tariq $suffix",
                    guardianName = "Tariq Aziz",
                    dateOfBirth = "2014-08-10",
                    admissionDate = "2026-10-25",
                    gender = "Male",
                    contactNumber = "03017778899",
                    alternateContact = "",
                    address = "Gulberg Lahore",
                    classId = newClass.id,
                    className = newClass.name,
                    teacherId = "",
                    teacherName = "",
                    quranLevel = "Tajweed",
                    monthlyFee = 4000.0,
                    feeCategory = "Regular",
                    isFsEligible = false,
                    notes = ""
                ).getOrThrow()
            }

            val teacherUsername = "qari.new.$suffix"
            val teacherPassword = "Tchr@$suffix"
            val addedTeacher = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminAcademyRepo.addTeacher(
                    fullName = "Qari Zubair $suffix",
                    contactNumber = "03219998877",
                    email = "zubair.$suffix@hqa.edu",
                    address = "Lahore",
                    assignedClasses = newClass.name,
                    assignedClassIds = newClass.id,
                    joiningDate = "2026-10-25",
                    notes = "Assigned during addTeacher",
                    teacherUsername = teacherUsername,
                    teacherPassword = teacherPassword
                ).getOrThrow()
            }
            assertEquals(newClass.id, addedTeacher.assignedClassIds)
            adminSyncJob.cancelAndJoin()

            // Now sign in on a separate Teacher device/session and verify students appear in Teacher Portal!
            val newTeacherUid = signInTestUser("zubair_$suffix@hqa.edu")
            val teacherCloudRepo = FirestoreCloudRepository(firestore)
            val teacherClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherCloudRepo.authenticateAndClaimSession(
                    uid = newTeacherUid,
                    email = "zubair_$suffix@hqa.edu",
                    usernameInput = teacherUsername,
                    passwordInput = teacherPassword,
                    requiredRole = UserRole.TEACHER
                ).getOrThrow()
            }
            assertTrue("Teacher profile should include assigned classId", newClass.id in teacherClaim.assignedClassIds)

            val teacherSyncJob = launch {
                teacherCloudRepo.syncRoleDataRealtime(
                    uid = newTeacherUid,
                    profile = teacherClaim,
                    localDb = teacherLocalDb,
                    onSessionRevoked = {},
                    onProfileUpdated = {}
                )
            }

            val syncedClasses = withTimeout(FLOW_TIMEOUT_MS) {
                teacherLocalDb.classDao().getAllClassesFlow().first { list -> list.any { it.id == newClass.id } }
            }
            val syncedStudents = withTimeout(FLOW_TIMEOUT_MS) {
                teacherLocalDb.studentDao().getAllStudentsFlow().first { list -> list.any { it.id == newStudent.id } }
            }
            assertTrue(syncedClasses.any { it.id == newClass.id })
            assertTrue(syncedStudents.any { it.id == newStudent.id && it.fullName == newStudent.fullName })

            teacherSyncJob.cancelAndJoin()

            // Sign back in as Admin and test Edit & Delete of Teacher, Class, Quran Progress, and Announcement
            signInTestUser(ADMIN_EMAIL)
            val updatedTeacher = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminAcademyRepo.updateTeacherDetails(
                    teacher = addedTeacher.copy(fullName = "Qari Zubair Updated"),
                    assignedClassIdsCsv = newClass.id,
                    teacherUsername = teacherUsername,
                    teacherPassword = teacherPassword
                ).getOrThrow()
            }
            assertEquals("Qari Zubair Updated", updatedTeacher.fullName)

            val progId = "prog_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            val prog = QuranProgressRecord(
                id = progId,
                date = "2026-10-25",
                studentId = newStudent.id,
                studentName = newStudent.fullName,
                admissionNumber = newStudent.admissionNumber,
                teacherId = updatedTeacher.id,
                teacherName = updatedTeacher.fullName,
                surahName = "Yaseen",
                surahNumber = 36,
                juzNumber = 22,
                sabaqAyatStart = 1,
                sabaqAyatEnd = 12,
                sabaqRating = QuranProgressRating.GOOD,
                mistakesCount = 1
            )
            withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.upsertQuranProgressCloud(prog, newClass.id, adminUid)
                adminCloudRepo.upsertQuranProgressCloud(prog.copy(sabaqAyatEnd = 20, sabaqRating = QuranProgressRating.EXCELLENT), newClass.id, adminUid)
                adminCloudRepo.deleteQuranProgressCloud(progId, newStudent.id)
            }

            val annId = "ann_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            val ann = Announcement(
                id = annId,
                title = "Exam Schedule",
                content = "Monthly Hifz test on Saturday",
                targetRole = "All",
                date = "2026-10-25",
                priority = "High",
                authorName = "Nazim-e-Aala"
            )
            withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.upsertAnnouncementCloud(ann, adminUid)
                adminCloudRepo.upsertAnnouncementCloud(ann.copy(title = "Updated Exam Schedule"), adminUid)
                adminCloudRepo.deleteAnnouncementCloud(annId)
            }

            assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { adminAcademyRepo.deleteTeacher(updatedTeacher.id).isSuccess })
            assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { adminAcademyRepo.deleteClass(newClass.id).isSuccess })
            // Student should remain safe and reassigned to unassigned_class, then clean up student
            val reassignedStudent = adminLocalDb.studentDao().getStudentById(newStudent.id)
            assertNotNull(reassignedStudent)
            assertEquals("unassigned_class", reassignedStudent?.classId)
            assertFalse(reassignedStudent?.teacherId?.isNotBlank() == true)
            val deleteStudentRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminAcademyRepo.withdrawAndRemoveStudentPermanently(newStudent.id)
            }
            assertTrue(deleteStudentRes.isSuccess)
        } finally {
            adminLocalDb.close()
            teacherLocalDb.close()
        }
    }

    @Test
    fun teacherSession_marksUpdatesAndDeletesAttendance_andSyncsToCloud(): Unit = runBlocking {
        ensureAdminBootstrapped()

        val teacherUid = signInTestUser(TEACHER_EMAIL)
        val teacherRepo = FirestoreCloudRepository(firestore)
        val claim = withTimeout(DEFAULT_TIMEOUT_MS) {
            teacherRepo.authenticateAndClaimSession(
                uid = teacherUid,
                email = TEACHER_EMAIL,
                usernameInput = "qari.abdullah",
                passwordInput = "Tchr@101",
                requiredRole = UserRole.TEACHER
            )
        }
        assertTrue("Teacher claim failed: ${claim.exceptionOrNull()}", claim.isSuccess)
        assertEquals(UserRole.TEACHER, claim.getOrThrow().role)

        val date = "2026-10-25"
        val attId = "att_std_bilal_0101_$date"
        val record = AttendanceRecord(
            id = attId,
            date = date,
            classId = "cls_hifz_1",
            className = "Hifz Halaqah 1 (Advanced)",
            studentId = "std_bilal_0101",
            studentName = "Muhammad Bilal Farooq",
            admissionNumber = "HQA-26-0101",
            status = AttendanceStatus.PRESENT,
            remarks = "Present on time",
            markedByTeacherId = "tchr_qari_abdullah"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            teacherRepo.saveAttendanceCloud(listOf(record), teacherUid)
            // Update existing attendance record
            teacherRepo.saveAttendanceCloud(listOf(record.copy(status = AttendanceStatus.LATE, remarks = "5 min late")), teacherUid)
            // Delete attendance record
            teacherRepo.deleteAttendanceRecordCloud(attId, "std_bilal_0101")
        }

        // Teacher must NOT be able to read Admin Vault credentials
        val vaultRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            teacherRepo.getAdminVaultAccount("acc_admin_main")
        }
        assertTrue(vaultRead.isFailure)
        val fsEx = vaultRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun parentSession_paysChallanOnlineWithTid_adminCanEditAndDeletePaymentAndChallan(): Unit = runBlocking {
        val (adminUid, adminRepo) = ensureAdminBootstrapped()
        val resolvedOwnerUid = withTimeout(DEFAULT_TIMEOUT_MS) { adminRepo.resolveAcademyAdminUid(adminUid) }

        val dynamicChallanId = "ch_test_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val challan = FeeChallan(
            id = dynamicChallanId,
            challanNumber = "HQA-2026-10-991",
            studentId = "std_bilal_0101",
            admissionNumber = "HQA-26-0101",
            studentName = "Muhammad Bilal Farooq",
            classId = "cls_hifz_1",
            className = "Hifz Halaqah 1 (Advanced)",
            billingMonth = "November",
            billingYear = 2026,
            issueDate = "2026-11-01",
            dueDate = "2026-11-10",
            monthlyTuitionFee = 4500.0,
            totalPayable = 4500.0,
            remainingBalance = 4500.0,
            status = ChallanStatus.UNPAID
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createChallanCloud(challan, resolvedOwnerUid)
        }

        // Sign in as Bilal's Parent (p.farooq)
        val parentUid = signInTestUser(PARENT_1_EMAIL)
        val parentRepo = FirestoreCloudRepository(firestore)
        val parentClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
            parentRepo.authenticateAndClaimSession(
                uid = parentUid,
                email = PARENT_1_EMAIL,
                usernameInput = "p.farooq",
                passwordInput = "Prnt@101",
                requiredRole = UserRole.PARENT
            )
        }
        assertTrue("Parent claim failed: ${parentClaim.exceptionOrNull()}", parentClaim.isSuccess)

        val payRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            parentRepo.recordPaymentCloud(
                challanId = dynamicChallanId,
                amount = 4500.0,
                paymentMethod = "EasyPaisa (03493895409)",
                receivedBy = "Online QR Portal (Farooq Ahmed)",
                remarks = "Paid via EasyPaisa QR",
                transactionId = "8899001122",
                callerUid = parentUid
            )
        }
        assertTrue("Parent payment failed: ${payRes.exceptionOrNull()}", payRes.isSuccess)
        val createdPayment = payRes.getOrThrow()

        val updatedChallan = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeChallanById(dynamicChallanId).first { it?.status == ChallanStatus.PAID }
        }
        assertEquals(ChallanStatus.PAID, updatedChallan?.status)

        // Cross-Parent isolation: p.asad (linked only to std_zayd_0102) cannot read std_bilal_0101
        val otherParentUid = signInTestUser(PARENT_2_EMAIL)
        val otherParentRepo = FirestoreCloudRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            otherParentRepo.authenticateAndClaimSession(
                uid = otherParentUid,
                email = PARENT_2_EMAIL,
                usernameInput = "p.asad",
                passwordInput = "Prnt@102",
                requiredRole = UserRole.PARENT
            ).getOrThrow()
        }

        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            otherParentRepo.getStudentById("std_bilal_0101")
        }
        assertTrue(crossRead.isFailure)
        val crossEx = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(crossEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossEx?.code)

        // Sign back in as Admin and verify Admin can update & delete Payment and Challan
        signInTestUser(ADMIN_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updatePaymentAndSyncChallanCloud(
                payment = createdPayment.copy(remarks = "Verified TID by Admin"),
                updatedChallan = updatedChallan?.copy(remarks = "Verified TID by Admin"),
                adminUid = resolvedOwnerUid
            )
            adminRepo.deletePaymentAndSyncChallanCloud(
                paymentId = createdPayment.id,
                studentId = "std_bilal_0101",
                updatedChallan = challan,
                adminUid = resolvedOwnerUid
            )
            adminRepo.deleteChallanCloud(
                challanId = dynamicChallanId,
                studentId = "std_bilal_0101",
                paymentIds = emptyList()
            )
        }
    }

    @Test
    fun unauthenticatedCaller_observeAdminStudents_failsWithPermissionDenied(): Unit = runBlocking {
        auth.signOut()
        val repo = FirestoreCloudRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeAdminStudents("unauth_uid").first()
            }
            fail("Expected exception for unauthenticated caller")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    @Test
    fun adminChangesOwnCredentials_ownerRecoversFromLockout_andIssuesPrintableCourseCertificate(): Unit = runBlocking {
        val (adminUid, adminCloudRepo) = ensureAdminBootstrapped()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localDb = createInMemoryDb()
        try {
            val academyRepo = AcademyRepository(localDb, adminCloudRepo).apply {
                activeCloudUid = adminUid
            }

            // 1. Change Admin's own username & password from default (admin / Admin@123) to custom
            val customUsername = "nazim.rizwan"
            val customPassword = "Custom@786"
            val changeRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                academyRepo.changeAdminOwnCredentials(
                    newUsername = customUsername,
                    newPassword = customPassword,
                    displayName = "Nazim-e-Aala Rizwan"
                )
            }
            assertTrue("changeAdminOwnCredentials failed: ${changeRes.exceptionOrNull()}", changeRes.isSuccess)

            // Verify old default credentials now fail, and new custom credentials succeed!
            val oldLoginAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.authenticateAndClaimSession(
                    uid = adminUid,
                    email = ADMIN_EMAIL,
                    usernameInput = "admin",
                    passwordInput = "Admin@123",
                    requiredRole = UserRole.ADMIN
                )
            }
            assertTrue("Old default password should be invalidated after Admin changed it", oldLoginAttempt.isFailure)

            val newLoginAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.authenticateAndClaimSession(
                    uid = adminUid,
                    email = ADMIN_EMAIL,
                    usernameInput = customUsername,
                    passwordInput = customPassword,
                    requiredRole = UserRole.ADMIN
                )
            }
            assertTrue("New custom Admin login failed: ${newLoginAttempt.exceptionOrNull()}", newLoginAttempt.isSuccess)

            // 2. Simulate user lockout (e.g. forgotten custom password or missing ticket) -> Owner resets back to admin / Admin@123
            val resetRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.recoverOrResetAdminCredentials(
                    uid = adminUid,
                    email = ADMIN_EMAIL,
                    newUsername = "admin",
                    newPassword = "Admin@123"
                )
            }
            assertTrue("Owner Admin credential reset failed: ${resetRes.exceptionOrNull()}", resetRes.isSuccess)
            assertEquals(
                FirestoreCloudMappers.computeTicketHash("admin", "Admin@123"),
                resetRes.getOrThrow().ticketHash
            )

            // 3. Admit a student, complete their course, verify Firestore persistence & A4 Landscape Certificate PDF generation
            val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
            val admitted = withTimeout(DEFAULT_TIMEOUT_MS) {
                academyRepo.admitStudent(
                    fullName = "Hafiz Huzaifa $suffix",
                    guardianName = "Abdul Rehman",
                    dateOfBirth = "2012-04-15",
                    admissionDate = "2024-01-10",
                    gender = "Male",
                    contactNumber = "03005556677",
                    alternateContact = "",
                    address = "Model Town Lahore",
                    classId = "cls_hifz_1",
                    className = "Hifz Halaqah 1 (Advanced)",
                    teacherId = "tchr_qari_abdullah",
                    teacherName = "Qari Muhammad Abdullah",
                    quranLevel = "Hifz",
                    monthlyFee = 4500.0,
                    feeCategory = "Regular",
                    isFsEligible = false,
                    notes = ""
                ).getOrThrow()
            }

            val completed = withTimeout(DEFAULT_TIMEOUT_MS) {
                academyRepo.completeStudentCourseAndIssueCertificate(
                    studentId = admitted.id,
                    completedCourse = "Hifz-ul-Quran Al-Kareem (Complete 30 Juz)",
                    completionDate = "2026-11-01",
                    certificateNumber = "HQA-SANAD-2026-$suffix",
                    finalGrade = "Mumtaz (A+ Distinction)",
                    teacherNameOverride = "Qari Muhammad Abdullah",
                    certificateRemarks = "Completed full 30 Juz with Tajweed"
                ).getOrThrow()
            }
            assertEquals(StudentStatus.COMPLETED, completed.status)
            assertEquals("HQA-SANAD-2026-$suffix", completed.certificateNumber)

            val cloudFetched = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.getStudentById(admitted.id).getOrThrow()
            }
            assertEquals(StudentStatus.COMPLETED, cloudFetched.status)
            assertEquals("Hifz-ul-Quran Al-Kareem (Complete 30 Juz)", cloudFetched.completedCourse)
            assertEquals("HQA-SANAD-2026-$suffix", cloudFetched.certificateNumber)

            val previewBitmap = CertificatePrintHelper.renderCertificateBitmap(cloudFetched, scale = 1f, context = context)
            assertEquals(842, previewBitmap.width)
            assertEquals(595, previewBitmap.height)

            val pdfFile = CertificatePrintHelper.generateCertificatePdf(context, cloudFetched)
            assertTrue("Generated Sanad PDF should exist and start with %PDF", pdfFile.exists() && pdfFile.length() > 300L)

            // Clean up test student
            withTimeout(DEFAULT_TIMEOUT_MS) {
                academyRepo.withdrawAndRemoveStudentPermanently(admitted.id)
            }
        } finally {
            localDb.close()
        }
    }

    @Test
    fun teacherLogsNamazAndMadniQaida_andParentSubmitsLeave_whichTeacherAndAdminApproveIntoAttendance(): Unit = runBlocking {
        ensureAdminBootstrapped()

        val teacherLocalDb = createInMemoryDb()
        val parentLocalDb = createInMemoryDb()
        val adminLocalDb = createInMemoryDb()
        try {
            // 1. Sign in as Qari Abdullah (Teacher of std_bilal_0101 in cls_hifz_1)
            val teacherUid = signInTestUser(TEACHER_EMAIL)
            val teacherCloudRepo = FirestoreCloudRepository(firestore)
            val teacherClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherCloudRepo.authenticateAndClaimSession(
                    uid = teacherUid,
                    email = TEACHER_EMAIL,
                    usernameInput = "qari.abdullah",
                    passwordInput = "Tchr@101",
                    requiredRole = UserRole.TEACHER
                ).getOrThrow()
            }
            val teacherSyncJob = launch {
                teacherCloudRepo.syncRoleDataRealtime(
                    uid = teacherUid,
                    profile = teacherClaim,
                    localDb = teacherLocalDb,
                    onSessionRevoked = {},
                    onProfileUpdated = {}
                )
            }
            withTimeout(FLOW_TIMEOUT_MS) {
                teacherLocalDb.studentDao().getAllStudentsFlow().first { list -> list.any { it.id == "std_bilal_0101" } }
            }

            val teacherAcademyRepo = AcademyRepository(teacherLocalDb, teacherCloudRepo).apply {
                activeCloudUid = teacherUid
            }

            // Record, Update, and Delete Namaz Log & Madni Qaida Log
            val namazRec = withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherAcademyRepo.recordNamazLog(
                    studentId = "std_bilal_0101",
                    teacherId = "tchr_qari_abdullah",
                    teacherName = "Qari Muhammad Abdullah",
                    date = "2026-11-18",
                    prayerName = "Fajr",
                    duaOrSurahTopic = "Dua-e-Qunoot & Sana",
                    rating = NamazProgressRating.EXCELLENT,
                    wuduAndPostureNotes = "Complete Sunnah Wudu & calm Ruku/Sajdah",
                    teacherRemarks = "MashaAllah very punctual",
                    nextTarget = "Masnoon Dua after Adhan"
                ).getOrThrow()
            }
            val updatedNamaz = withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherAcademyRepo.updateNamazLog(namazRec.copy(teacherRemarks = "Updated Fajr remarks")).getOrThrow()
            }
            assertEquals("Updated Fajr remarks", updatedNamaz.teacherRemarks)

            val qaidaRec = withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherAcademyRepo.recordMadniQaidaLog(
                    studentId = "std_bilal_0101",
                    teacherId = "tchr_qari_abdullah",
                    teacherName = "Qari Muhammad Abdullah",
                    date = "2026-11-18",
                    lessonNumber = 12,
                    lessonTitle = "Lesson 12: Tashdeed (Shaddah)",
                    pageNumber = 28,
                    linesCovered = "Lines 1-6",
                    rating = QuranProgressRating.VERY_GOOD,
                    revisionSummary = "Lesson 11 passed",
                    mistakesCount = 1,
                    teacherRemarks = "Strong Makharij",
                    nextTarget = "Lesson 13 Page 30"
                ).getOrThrow()
            }
            val updatedQaida = withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherAcademyRepo.updateMadniQaidaLog(qaidaRec.copy(mistakesCount = 0)).getOrThrow()
            }
            assertEquals(0, updatedQaida.mistakesCount)

            teacherSyncJob.cancelAndJoin()

            // 2. Sign in as Bilal's Parent (p.farooq), verify synced Namaz & Qaida logs, and submit 2 leave requests
            val parentUid = signInTestUser(PARENT_1_EMAIL)
            val parentCloudRepo = FirestoreCloudRepository(firestore)
            val parentClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
                parentCloudRepo.authenticateAndClaimSession(
                    uid = parentUid,
                    email = PARENT_1_EMAIL,
                    usernameInput = "p.farooq",
                    passwordInput = "Prnt@101",
                    requiredRole = UserRole.PARENT
                ).getOrThrow()
            }
            val parentSyncJob = launch {
                parentCloudRepo.syncRoleDataRealtime(
                    uid = parentUid,
                    profile = parentClaim,
                    localDb = parentLocalDb,
                    onSessionRevoked = {},
                    onProfileUpdated = {}
                )
            }
            withTimeout(FLOW_TIMEOUT_MS) {
                parentLocalDb.namazLogDao().getNamazLogsByStudentFlow("std_bilal_0101").first { list ->
                    list.any { it.id == namazRec.id }
                }
                parentLocalDb.madniQaidaLogDao().getQaidaLogsByStudentFlow("std_bilal_0101").first { list ->
                    list.any { it.id == qaidaRec.id }
                }
            }

            val parentAcademyRepo = AcademyRepository(parentLocalDb, parentCloudRepo).apply {
                activeCloudUid = parentUid
            }
            val leaveForTeacherApprove = withTimeout(DEFAULT_TIMEOUT_MS) {
                parentAcademyRepo.submitLeaveRequest(
                    studentId = "std_bilal_0101",
                    parentUid = parentUid,
                    parentName = "Farooq Ahmed",
                    leaveType = "Sick / Medical",
                    fromDate = "2026-11-20",
                    toDate = "2026-11-21",
                    reason = "High fever, rest advised"
                ).getOrThrow()
            }
            assertEquals(ApprovalStatus.PENDING, leaveForTeacherApprove.status)

            val leaveForAdminReject = withTimeout(DEFAULT_TIMEOUT_MS) {
                parentAcademyRepo.submitLeaveRequest(
                    studentId = "std_bilal_0101",
                    parentUid = parentUid,
                    parentName = "Farooq Ahmed",
                    leaveType = "Out of Station",
                    fromDate = "2026-11-25",
                    toDate = "2026-11-25",
                    reason = "Family travel"
                ).getOrThrow()
            }

            // Parent MUST NOT be able to approve their own leave request
            val selfApproveAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
                runCatching {
                    parentCloudRepo.reviewLeaveRequestCloud(
                        leaveId = leaveForTeacherApprove.id,
                        approved = true,
                        reviewerName = "Farooq",
                        reviewerRole = "PARENT",
                        reviewedDate = "2026-11-18",
                        reviewerRemarks = "Self approve hack"
                    )
                }
            }
            assertTrue("Parent self-approve should be rejected", selfApproveAttempt.isFailure)
            parentSyncJob.cancelAndJoin()

            // 3. Sign back in as Teacher -> Approve first leave -> verify APPROVED + Attendance LEAVE created
            signInTestUser(TEACHER_EMAIL)
            val teacherSyncJob2 = launch {
                teacherCloudRepo.syncRoleDataRealtime(
                    uid = teacherUid,
                    profile = teacherClaim,
                    localDb = teacherLocalDb,
                    onSessionRevoked = {},
                    onProfileUpdated = {}
                )
            }
            withTimeout(FLOW_TIMEOUT_MS) {
                teacherLocalDb.leaveRequestDao().getAllLeaveRequestsFlow().first { list ->
                    list.any { it.id == leaveForTeacherApprove.id }
                }
            }
            val approvedLeave = withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherAcademyRepo.reviewLeaveRequest(
                    leaveId = leaveForTeacherApprove.id,
                    approved = true,
                    reviewerName = "Qari Muhammad Abdullah",
                    reviewerRole = "TEACHER",
                    reviewerRemarks = "Get well soon"
                ).getOrThrow()
            }
            assertEquals(ApprovalStatus.APPROVED, approvedLeave.status)

            val attDay1 = teacherLocalDb.attendanceDao().getAllAttendanceFlow().first()
                .firstOrNull { it.studentId == "std_bilal_0101" && it.date == "2026-11-20" }
            val attDay2 = teacherLocalDb.attendanceDao().getAllAttendanceFlow().first()
                .firstOrNull { it.studentId == "std_bilal_0101" && it.date == "2026-11-21" }
            assertEquals(AttendanceStatus.LEAVE, attDay1?.status)
            assertEquals(AttendanceStatus.LEAVE, attDay2?.status)

            // Clean up Namaz & Qaida logs and auto-attendance
            withTimeout(DEFAULT_TIMEOUT_MS) {
                teacherAcademyRepo.deleteNamazLog(namazRec.id)
                teacherAcademyRepo.deleteMadniQaidaLog(qaidaRec.id)
                attDay1?.let { teacherAcademyRepo.deleteAttendanceRecord(it.id) }
                attDay2?.let { teacherAcademyRepo.deleteAttendanceRecord(it.id) }
            }
            teacherSyncJob2.cancelAndJoin()

            // 4. Sign in as Admin -> Reject second leave & clean up both leave requests
            val (adminUid, adminCloudRepo) = ensureAdminBootstrapped()
            val adminClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminCloudRepo.authenticateAndClaimSession(
                    uid = adminUid,
                    email = ADMIN_EMAIL,
                    usernameInput = "admin",
                    passwordInput = "Admin@123",
                    requiredRole = UserRole.ADMIN
                ).getOrThrow()
            }
            val adminSyncJob = launch {
                adminCloudRepo.syncRoleDataRealtime(
                    uid = adminUid,
                    profile = adminClaim,
                    localDb = adminLocalDb,
                    onSessionRevoked = {},
                    onProfileUpdated = {}
                )
            }
            withTimeout(FLOW_TIMEOUT_MS) {
                adminLocalDb.leaveRequestDao().getAllLeaveRequestsFlow().first { list ->
                    list.any { it.id == leaveForAdminReject.id }
                }
            }
            val adminAcademyRepo = AcademyRepository(adminLocalDb, adminCloudRepo).apply {
                activeCloudUid = adminUid
            }
            val rejectedLeave = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminAcademyRepo.reviewLeaveRequest(
                    leaveId = leaveForAdminReject.id,
                    approved = false,
                    reviewerName = "Muhammad Rizwan",
                    reviewerRole = "ADMIN",
                    reviewerRemarks = "Please attend oral assessment first"
                ).getOrThrow()
            }
            assertEquals(ApprovalStatus.REJECTED, rejectedLeave.status)

            withTimeout(DEFAULT_TIMEOUT_MS) {
                adminAcademyRepo.deleteLeaveRequest(leaveForTeacherApprove.id)
                adminAcademyRepo.deleteLeaveRequest(leaveForAdminReject.id)
            }
            adminSyncJob.cancelAndJoin()
        } finally {
            teacherLocalDb.close()
            parentLocalDb.close()
            adminLocalDb.close()
        }
    }

    private companion object {
        const val ADMIN_EMAIL = "admin_test@hqa.edu"
        const val TEACHER_EMAIL = "qari_test@hqa.edu"
        const val PARENT_1_EMAIL = "farooq_test@hqa.edu"
        const val PARENT_2_EMAIL = "asad_test@hqa.edu"
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
