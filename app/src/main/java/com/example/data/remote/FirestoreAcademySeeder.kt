package com.example.data.remote

import com.example.data.models.*
import com.example.data.remote.FirestoreCloudMappers.ACADEMY_ID
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FirestoreAcademySeeder {

    suspend fun bootstrapAcademyWithInitialData(
        db: FirebaseFirestore,
        adminUid: String
    ) {
        val c1 = AcademyClass(
            id = "cls_hifz_1",
            name = "Hifz Halaqah 1 (Advanced)",
            section = "Morning",
            roomNumber = "Halaqah Room A",
            capacity = 15,
            assignedTeacherId = "tchr_qari_abdullah",
            assignedTeacherName = "Qari Muhammad Abdullah",
            scheduleInfo = "Mon-Sat: 07:00 AM - 12:00 PM"
        )
        val c2 = AcademyClass(
            id = "cls_hifz_2",
            name = "Hifz Halaqah 2 (Intermediate)",
            section = "Morning",
            roomNumber = "Halaqah Room B",
            capacity = 15,
            assignedTeacherId = "tchr_qari_abdullah",
            assignedTeacherName = "Qari Muhammad Abdullah",
            scheduleInfo = "Mon-Sat: 07:00 AM - 12:00 PM"
        )
        val c3 = AcademyClass(
            id = "cls_nazra_1",
            name = "Nazra & Tajweed Foundation",
            section = "Afternoon",
            roomNumber = "Class 101",
            capacity = 25,
            assignedTeacherId = "tchr_hafiz_usman",
            assignedTeacherName = "Hafiz Usman Tariq",
            scheduleInfo = "Mon-Fri: 02:30 PM - 05:00 PM"
        )

        val t1 = Teacher(
            id = "tchr_qari_abdullah",
            fullName = "Qari Muhammad Abdullah",
            contactNumber = "+92 301 2345678",
            email = "abdullah@hafizquranacademy.edu",
            address = "Lahore Cantt",
            assignedClasses = "${c1.name}, ${c2.name}",
            assignedClassIds = "${c1.id},${c2.id}",
            joiningDate = "2023-01-15",
            notes = "Lead Senior Qari, 10 Qira'at certified"
        )
        val t2 = Teacher(
            id = "tchr_hafiz_usman",
            fullName = "Hafiz Usman Tariq",
            contactNumber = "+92 302 3456789",
            email = "usman@hafizquranacademy.edu",
            address = "Johar Town, Lahore",
            assignedClasses = c3.name,
            assignedClassIds = c3.id,
            joiningDate = "2024-03-01",
            notes = "Specialist in Tajweed rules & Makharij"
        )

        val s1 = Student(
            id = "std_bilal_0101",
            admissionNumber = "HQA-26-0101",
            fullName = "Muhammad Bilal Farooq",
            guardianName = "Farooq Ahmed",
            dateOfBirth = "2013-05-14",
            admissionDate = "2025-01-10",
            gender = "Male",
            contactNumber = "03004455667",
            address = "Model Town Block C, Lahore",
            classId = c1.id,
            className = c1.name,
            teacherId = t1.id,
            teacherName = t1.fullName,
            quranLevel = "Hifz (15 Juz Completed)",
            monthlyFee = 4500.0,
            feeCategory = "Regular",
            notes = "Preparing for annual Huffaz graduation"
        )
        val s2 = Student(
            id = "std_zayd_0102",
            admissionNumber = "HQA-26-0102",
            fullName = "Zayd Bin Asad",
            guardianName = "Asadullah Khan",
            dateOfBirth = "2014-08-20",
            admissionDate = "2025-02-01",
            gender = "Male",
            contactNumber = "03128899001",
            address = "Gulberg III, Lahore",
            classId = c2.id,
            className = c2.name,
            teacherId = t1.id,
            teacherName = t1.fullName,
            quranLevel = "Hifz (8 Juz Completed)",
            monthlyFee = 4500.0,
            feeCategory = "Need-Based",
            isFsEligible = true,
            fsStatus = "Approved",
            notes = "Fee Sabilillah approved beneficiary"
        )
        val s3 = Student(
            id = "std_hamza_0103",
            admissionNumber = "HQA-26-0103",
            fullName = "Hamza Noor",
            guardianName = "Farooq Ahmed",
            dateOfBirth = "2015-11-12",
            admissionDate = "2025-03-15",
            gender = "Male",
            contactNumber = "03004455667",
            address = "Model Town Block C, Lahore",
            classId = c3.id,
            className = c3.name,
            teacherId = t2.id,
            teacherName = t2.fullName,
            quranLevel = "Nazra with Tajweed",
            monthlyFee = 3500.0,
            feeCategory = "Sibling Discount",
            notes = "Brother of Bilal in Hifz section"
        )
        val s4 = Student(
            id = "std_ahmad_0104",
            admissionNumber = "HQA-26-0104",
            fullName = "Ahmad Raza",
            guardianName = "Raza Ullah",
            dateOfBirth = "2012-04-05",
            admissionDate = "2024-09-01",
            gender = "Male",
            contactNumber = "03217788990",
            address = "Township, Lahore",
            classId = c1.id,
            className = c1.name,
            teacherId = t1.id,
            teacherName = t1.fullName,
            quranLevel = "Hifz (28 Juz Completed)",
            monthlyFee = 4500.0,
            feeCategory = "Regular",
            notes = "Completing Holy Quran memorization next month"
        )

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val dueDate = SimpleDateFormat("yyyy-MM-10", Locale.US).format(Date())

        val att1 = AttendanceRecord(
            id = "att_${s1.id}_$today",
            date = today,
            classId = c1.id,
            className = c1.name,
            studentId = s1.id,
            studentName = s1.fullName,
            admissionNumber = s1.admissionNumber,
            status = AttendanceStatus.PRESENT,
            markedByTeacherId = t1.id
        )
        val att2 = AttendanceRecord(
            id = "att_${s4.id}_$today",
            date = today,
            classId = c1.id,
            className = c1.name,
            studentId = s4.id,
            studentName = s4.fullName,
            admissionNumber = s4.admissionNumber,
            status = AttendanceStatus.PRESENT,
            markedByTeacherId = t1.id
        )
        val att3 = AttendanceRecord(
            id = "att_${s2.id}_$today",
            date = today,
            classId = c2.id,
            className = c2.name,
            studentId = s2.id,
            studentName = s2.fullName,
            admissionNumber = s2.admissionNumber,
            status = AttendanceStatus.PRESENT,
            markedByTeacherId = t1.id
        )

        val p1 = QuranProgressRecord(
            id = "prg_${s1.id}_$today",
            date = today,
            studentId = s1.id,
            studentName = s1.fullName,
            admissionNumber = s1.admissionNumber,
            teacherId = t1.id,
            teacherName = t1.fullName,
            surahName = "Surah Maryam",
            surahNumber = 19,
            juzNumber = 16,
            sabaqAyatStart = 1,
            sabaqAyatEnd = 25,
            sabaqRating = QuranProgressRating.EXCELLENT,
            sabqiSummary = "Surah Al-Kahf (Ayat 75-110)",
            manzilSummary = "Juz 14 Complete",
            mistakesCount = 1,
            teacherRemarks = "MashaAllah melodious recitation with proper Tajweed.",
            nextTarget = "Surah Maryam Ayat 26-50"
        )
        val p2 = QuranProgressRecord(
            id = "prg_${s2.id}_$today",
            date = today,
            studentId = s2.id,
            studentName = s2.fullName,
            admissionNumber = s2.admissionNumber,
            teacherId = t1.id,
            teacherName = t1.fullName,
            surahName = "Surah Al-An'am",
            surahNumber = 6,
            juzNumber = 8,
            sabaqAyatStart = 40,
            sabaqAyatEnd = 58,
            sabaqRating = QuranProgressRating.GOOD,
            sabqiSummary = "Surah Al-An'am (Ayat 1-39)",
            manzilSummary = "Juz 7 Quarter 2",
            mistakesCount = 3,
            teacherRemarks = "Good effort. Focus on Ikhfaa pronunciation.",
            nextTarget = "Surah Al-An'am Ayat 59-73"
        )

        val ch1 = FeeChallan(
            id = "ch_${s1.id}_$currentYear",
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
            totalPayable = 4700.0,
            remainingBalance = 4700.0,
            status = ChallanStatus.UNPAID
        )
        val ch3 = FeeChallan(
            id = "ch_${s3.id}_$currentYear",
            challanNumber = "HQA-$currentYear-10-0103-412",
            studentId = s3.id,
            admissionNumber = s3.admissionNumber,
            studentName = s3.fullName,
            classId = s3.classId,
            className = s3.className,
            billingMonth = currentMonth,
            billingYear = currentYear,
            issueDate = today,
            dueDate = dueDate,
            monthlyTuitionFee = s3.monthlyFee,
            discountAmount = 500.0,
            totalPayable = 3000.0,
            remainingBalance = 3000.0,
            status = ChallanStatus.UNPAID
        )

        val fs1 = FeeSabilillahRecord(
            id = "fs_${s2.id}",
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
            confidentialRemarks = "100% Fee waived under Academy Waqf."
        )

        val ann1 = Announcement(
            id = "ann_welcome_1",
            title = "Monthly Quran Sabaq & Tajweed Assessment",
            content = "Assalamu Alaikum! Monthly Hifz & Nazra Sabaq review will take place on Saturday.",
            targetRole = "All",
            date = today,
            priority = "High",
            authorName = "Nazim-e-Aala"
        )

        val accounts = listOf(
            UserAccount(
                id = "acc_admin_main",
                name = "Nazim-e-Aala (Admin)",
                username = "admin",
                password = "Admin@123",
                email = "admin@hafizquranacademy.edu",
                role = UserRole.ADMIN,
                phone = "03707304820"
            ),
            UserAccount(
                id = "acc_tchr_abdullah",
                name = t1.fullName,
                username = "qari.abdullah",
                password = "Tchr@101",
                email = t1.email,
                role = UserRole.TEACHER,
                phone = t1.contactNumber,
                teacherId = t1.id,
                assignedClassIds = "${c1.id},${c2.id}"
            ),
            UserAccount(
                id = "acc_tchr_usman",
                name = t2.fullName,
                username = "hafiz.usman",
                password = "Tchr@102",
                email = t2.email,
                role = UserRole.TEACHER,
                phone = t2.contactNumber,
                teacherId = t2.id,
                assignedClassIds = c3.id
            ),
            UserAccount(
                id = "acc_prnt_farooq",
                name = "Farooq Ahmed (Parent)",
                username = "p.farooq",
                password = "Prnt@101",
                role = UserRole.PARENT,
                phone = s1.contactNumber,
                linkedStudentIds = "${s1.id},${s3.id}"
            ),
            UserAccount(
                id = "acc_prnt_asad",
                name = "Asadullah Khan (Parent)",
                username = "p.asad",
                password = "Prnt@102",
                role = UserRole.PARENT,
                phone = s2.contactNumber,
                linkedStudentIds = s2.id
            ),
            UserAccount(
                id = "acc_prnt_raza",
                name = "Raza Ullah (Parent)",
                username = "p.raza",
                password = "Prnt@104",
                role = UserRole.PARENT,
                phone = s4.contactNumber,
                linkedStudentIds = s4.id
            )
        )

        // 1. Create classes first (since students reference classes via exists())
        db.collection("classes").document(c1.id)
            .set(FirestoreCloudMappers.buildClassPayload(c1, adminUid, listOf(s1.id, s4.id))).await()
        db.collection("classes").document(c2.id)
            .set(FirestoreCloudMappers.buildClassPayload(c2, adminUid, listOf(s2.id))).await()
        db.collection("classes").document(c3.id)
            .set(FirestoreCloudMappers.buildClassPayload(c3, adminUid, listOf(s3.id))).await()

        // 2. Create teachers
        db.collection("teachers").document(t1.id)
            .set(FirestoreCloudMappers.buildTeacherPayload(t1, adminUid)).await()
        db.collection("teachers").document(t2.id)
            .set(FirestoreCloudMappers.buildTeacherPayload(t2, adminUid)).await()

        // 3. Create students (before attendance, progress, challans which verify exists(students/...))
        db.collection("students").document(s1.id).set(
            FirestoreCloudMappers.buildStudentPayload(
                s1, adminUid,
                recentAttIds = listOf(att1.id),
                recentProgIds = listOf(p1.id),
                recentChallanIds = listOf(ch1.id)
            )
        ).await()
        db.collection("students").document(s2.id).set(
            FirestoreCloudMappers.buildStudentPayload(
                s2, adminUid,
                recentAttIds = listOf(att3.id),
                recentProgIds = listOf(p2.id)
            )
        ).await()
        db.collection("students").document(s3.id).set(
            FirestoreCloudMappers.buildStudentPayload(
                s3, adminUid,
                recentChallanIds = listOf(ch3.id)
            )
        ).await()
        db.collection("students").document(s4.id).set(
            FirestoreCloudMappers.buildStudentPayload(
                s4, adminUid,
                recentAttIds = listOf(att2.id)
            )
        ).await()

        // 4. Create attendance, progress, challans, fs_records, announcements, tickets & vault
        val batch = db.batch()
        batch.set(db.collection("attendance").document(att1.id), FirestoreCloudMappers.buildAttendancePayload(att1, adminUid))
        batch.set(db.collection("attendance").document(att2.id), FirestoreCloudMappers.buildAttendancePayload(att2, adminUid))
        batch.set(db.collection("attendance").document(att3.id), FirestoreCloudMappers.buildAttendancePayload(att3, adminUid))

        batch.set(db.collection("quran_progress").document(p1.id), FirestoreCloudMappers.buildProgressPayload(p1, c1.id, adminUid))
        batch.set(db.collection("quran_progress").document(p2.id), FirestoreCloudMappers.buildProgressPayload(p2, c2.id, adminUid))

        batch.set(db.collection("fee_challans").document(ch1.id), FirestoreCloudMappers.buildChallanPayload(ch1, adminUid))
        batch.set(db.collection("fee_challans").document(ch3.id), FirestoreCloudMappers.buildChallanPayload(ch3, adminUid))

        batch.set(db.collection("fs_records").document(fs1.id), FirestoreCloudMappers.buildFsPayload(fs1, adminUid))
        batch.set(db.collection("announcements").document(ann1.id), FirestoreCloudMappers.buildAnnouncementPayload(ann1, adminUid))

        for (acc in accounts) {
            val (ticketHash, ticketPayload) = FirestoreCloudMappers.buildAuthTicketPayload(acc, adminUid)
            val vaultPayload = FirestoreCloudMappers.buildVaultPayload(acc, adminUid, ticketHash)
            batch.set(db.collection("auth_tickets").document(ticketHash), ticketPayload)
            batch.set(db.collection("admin_vault").document(acc.id), vaultPayload)
        }

        val academyPayload = mapOf<String, Any>(
            "academyId" to ACADEMY_ID,
            "adminUid" to adminUid,
            "academyName" to "Hafiz Quran Academy",
            "easyPaisaNumber" to "03493895409",
            "jazzCashNumber" to "03707304820",
            "onlineAccountTitle" to "MUHAMMAD RIZWAN",
            "easyPaisaIban" to "PK23TMFB0000000095651623",
            "classIds" to listOf(c1.id, c2.id, c3.id),
            "teacherIds" to listOf(t1.id, t2.id),
            "studentIds" to listOf(s1.id, s2.id, s3.id, s4.id),
            "announcementIds" to listOf(ann1.id),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        batch.set(db.collection("academies").document(ACADEMY_ID), academyPayload)
        batch.commit().await()
    }
}
