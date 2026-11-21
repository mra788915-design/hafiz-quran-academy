package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class UserRole {
    ADMIN,
    TEACHER,
    PARENT
}

enum class StudentStatus {
    ACTIVE,
    COMPLETED,
    INACTIVE,
    WITHDRAWN
}

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    LEAVE
}

enum class ChallanStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED
}

enum class AssistanceType {
    FULL,
    PARTIAL
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class QuranProgressRating {
    EXCELLENT,
    VERY_GOOD,
    GOOD,
    AVERAGE,
    NEEDS_REVISION
}

enum class NamazProgressRating {
    EXCELLENT,
    MEMORIZED,
    PRACTICED_GOOD,
    NEEDS_CORRECTION,
    MISSED
}

enum class ExamType(val displayLabel: String, val shortLabel: String) {
    TEST("Test / Monthly Quiz", "Test"),
    MID_EXAM("Mid-Term Exam", "Mid Exam"),
    FINAL_EXAM("Final-Term Exam", "Final Exam")
}

object ExamGradeHelper {
    fun computePercentage(obtainedMarks: Double, totalMarks: Double): Double {
        if (totalMarks <= 0.0) return 0.0
        return ((obtainedMarks / totalMarks) * 100.0).coerceIn(0.0, 100.0)
    }

    fun computeGrade(
        obtainedMarks: Double,
        totalMarks: Double,
        passingMarks: Double,
        isAbsent: Boolean
    ): String {
        if (isAbsent) return "AB (Absent)"
        val pct = computePercentage(obtainedMarks, totalMarks)
        val passed = obtainedMarks >= passingMarks
        if (!passed) return "F (Fail)"
        return when {
            pct >= 90.0 -> "A+ (Mumtaz)"
            pct >= 80.0 -> "A (Jayyid Jiddan)"
            pct >= 70.0 -> "B (Jayyid)"
            pct >= 60.0 -> "C (Maqbool)"
            else -> "D (Pass)"
        }
    }
}

@Entity(tableName = "users")
data class UserAccount(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String, // Unique login ID managed exclusively by Admin
    val password: String, // Password issued & controlled exclusively by Admin
    val email: String = "",
    val role: UserRole,
    val phone: String = "",
    val teacherId: String = "", // Linked Teacher ID for TEACHER accounts
    val assignedClassIds: String = "", // Comma-separated class IDs for teachers
    val linkedStudentIds: String = "", // Comma-separated student IDs for parents
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "students")
data class Student(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val admissionNumber: String,
    val fullName: String,
    val guardianName: String,
    val dateOfBirth: String = "",
    val admissionDate: String,
    val gender: String = "Male",
    val contactNumber: String,
    val alternateContact: String = "",
    val address: String,
    val classId: String,
    val className: String,
    val teacherId: String = "",
    val teacherName: String = "",
    val quranLevel: String = "Hifz", // Nazra, Qaida, Hifz, Tajweed, Tarteel
    val monthlyFee: Double,
    val feeCategory: String = "Regular", // Regular, Need-Based, Sibling, Staff
    val isFsEligible: Boolean = false,
    val fsStatus: String = "None", // None, Pending, Approved
    val status: StudentStatus = StudentStatus.ACTIVE,
    val notes: String = "",
    val completedCourse: String = "",
    val completionDate: String = "",
    val certificateNumber: String = "",
    val finalGrade: String = "",
    val certificateRemarks: String = "",
    val photoUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "teachers")
data class Teacher(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val contactNumber: String,
    val email: String = "",
    val address: String = "",
    val assignedClasses: String = "", // Comma-separated class names
    val assignedClassIds: String = "", // Comma-separated IDs
    val joiningDate: String,
    val isActive: Boolean = true,
    val notes: String = ""
)

@Entity(tableName = "classes")
data class AcademyClass(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "Hifz Halaqah 1"
    val section: String = "Morning",
    val roomNumber: String = "Hall A",
    val capacity: Int = 20,
    val assignedTeacherId: String = "",
    val assignedTeacherName: String = "",
    val scheduleInfo: String = "Mon-Sat: 07:30 AM - 12:30 PM",
    val isActive: Boolean = true
)

@Entity(tableName = "attendance")
data class AttendanceRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val classId: String,
    val className: String,
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val status: AttendanceStatus,
    val remarks: String = "",
    val markedByTeacherId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "quran_progress")
data class QuranProgressRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val teacherId: String = "",
    val teacherName: String = "",
    val surahName: String,
    val surahNumber: Int = 1,
    val juzNumber: Int = 1, // Para 1 to 30
    val sabaqAyatStart: Int = 1,
    val sabaqAyatEnd: Int = 10,
    val sabaqRating: QuranProgressRating = QuranProgressRating.GOOD,
    val sabqiSummary: String = "", // Recent revision, e.g. "Para 1 Ruku 1-4"
    val manzilSummary: String = "", // Daur / Older revision, e.g. "Para 29 Half"
    val mistakesCount: Int = 0,
    val teacherRemarks: String = "",
    val nextTarget: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "namaz_logs")
data class NamazLogRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val teacherId: String = "",
    val teacherName: String = "",
    val prayerName: String, // e.g., "Fajr", "Zuhr", "Asr", "Maghrib", "Isha", "5 Prayers Check"
    val duaOrSurahTopic: String = "", // e.g., "Sana & Tashahhud", "Dua-e-Qunoot"
    val rating: NamazProgressRating = NamazProgressRating.PRACTICED_GOOD,
    val wuduAndPostureNotes: String = "Proper Wudu, Ruku & Sajdah",
    val teacherRemarks: String = "",
    val nextTarget: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "madni_qaida_logs")
data class MadniQaidaLogRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val teacherId: String = "",
    val teacherName: String = "",
    val lessonNumber: Int = 1, // Lesson 1 to 26
    val lessonTitle: String, // e.g., "Lesson 1: Huroof-e-Mufridat"
    val pageNumber: Int = 1,
    val linesCovered: String = "Lines 1-6",
    val rating: QuranProgressRating = QuranProgressRating.GOOD,
    val revisionSummary: String = "", // Previous lesson / Dohrai
    val mistakesCount: Int = 0,
    val teacherRemarks: String = "",
    val nextTarget: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "leave_requests")
data class LeaveRequest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val classId: String,
    val className: String,
    val teacherId: String = "",
    val parentUid: String = "",
    val parentName: String = "",
    val leaveType: String = "Sick / Medical",
    val fromDate: String, // YYYY-MM-DD
    val toDate: String, // YYYY-MM-DD
    val reason: String,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val reviewerName: String = "",
    val reviewerRole: String = "",
    val reviewedDate: String = "",
    val reviewerRemarks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "fee_challans")
data class FeeChallan(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val challanNumber: String, // e.g. HQA-2026-10-001
    val studentId: String,
    val admissionNumber: String,
    val studentName: String,
    val classId: String,
    val className: String,
    val billingMonth: String, // e.g. "October"
    val billingYear: Int, // e.g. 2026
    val issueDate: String,
    val dueDate: String,
    val monthlyTuitionFee: Double,
    val additionalCharges: Double = 0.0,
    val discountAmount: Double = 0.0,
    val fsAssistanceAmount: Double = 0.0,
    val previousBalance: Double = 0.0,
    val totalPayable: Double,
    val amountPaid: Double = 0.0,
    val remainingBalance: Double,
    val status: ChallanStatus = ChallanStatus.UNPAID,
    val paymentDate: String = "",
    val paymentMethod: String = "",
    val transactionRef: String = "",
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "fee_payments")
data class FeePayment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val challanId: String,
    val challanNumber: String,
    val studentId: String,
    val studentName: String,
    val amount: Double,
    val paymentDate: String,
    val paymentMethod: String, // JazzCash, EasyPaisa, Bank Transfer, Cash
    val receiptNumber: String,
    val receivedBy: String = "Academy Office",
    val remarks: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "fs_records")
data class FeeSabilillahRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val className: String,
    val category: String = "Orphan / Deserving",
    val assistanceType: AssistanceType = AssistanceType.FULL,
    val approvedAmount: Double, // If partial: fixed discount or fee cap; if full: 100% of fee
    val effectiveStartDate: String,
    val effectiveEndDate: String = "",
    val approvalStatus: ApprovalStatus = ApprovalStatus.PENDING,
    val approverName: String = "",
    val approvalDate: String = "",
    val confidentialRemarks: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "announcements")
data class Announcement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val targetRole: String = "All", // All, Teachers, Parents
    val date: String,
    val priority: String = "Normal", // Normal, High, Urgent
    val authorName: String = "Hafiz Quran Academy Administration",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedules")
data class ScheduleItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val classId: String,
    val className: String,
    val dayOfWeek: String, // Monday, Tuesday, etc.
    val startTime: String, // 07:30 AM
    val endTime: String, // 09:30 AM
    val subjectOrHalaqah: String, // Sabaq (New Lesson), Sabqi (Revision), Tajweed
    val teacherName: String
)

@Entity(tableName = "exams")
data class AcademyExam(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val examType: ExamType = ExamType.TEST,
    val subjectName: String = "Hifz / Nazra & Tajweed",
    val classId: String,
    val className: String,
    val academicTerm: String = "2026-2027",
    val examDate: String, // YYYY-MM-DD
    val totalMarks: Double = 100.0,
    val passingMarks: Double = 40.0,
    val instructions: String = "",
    val createdByAdminName: String = "Administration",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "exam_marks")
data class ExamMarkRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val examId: String,
    val examTitle: String,
    val examType: ExamType = ExamType.TEST,
    val subjectName: String = "",
    val examDate: String,
    val academicTerm: String = "2026-2027",
    val classId: String,
    val className: String,
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val obtainedMarks: Double = 0.0,
    val totalMarks: Double = 100.0,
    val passingMarks: Double = 40.0,
    val grade: String = "",
    val isAbsent: Boolean = false,
    val remarks: String = "",
    val gradedByTeacherId: String = "",
    val gradedByTeacherName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class AcademySettings(
    val academyName: String = "Hafiz Quran Academy",
    val tagline: String = "Excellence in Quran Memorization, Tajweed & Tarbiyah",
    val phone: String = "0370-7304820 / 0349-3895409",
    val email: String = "admin@hafizquranacademy.edu",
    val address: String = "Ali Pur Sharqi Post, Gujrat",
    val currencyCode: String = "PKR",
    val jazzCashNumber: String = "03707304820",
    val easyPaisaNumber: String = "03493895409",
    val easyPaisaIban: String = "PK23TMFB0000000095651623",
    val easyPaisaRaastQrPayload: String = "0002020102110202000424PK23TMFB000000009565162310048E1D",
    val onlineAccountTitle: String = "MUHAMMAD RIZWAN",
    val bankAccountTitle: String = "MUHAMMAD RIZWAN (Hafiz Quran Academy)",
    val bankName: String = "EasyPaisa (Raast QR) & JazzCash",
    val bankAccountNumber: String = "EP: 03493895409 | JC: 03707304820",
    val paymentInstructions: String = "Scan Official EasyPaisa/Raast QR Code (03493895409 - MUHAMMAD RIZWAN) via EasyPaisa, JazzCash, or any Banking App, or transfer manually to JazzCash 03707304820 / EasyPaisa 03493895409.",
    val authorizedSignatory: String = "Principal / Nazim-e-Aala"
)
