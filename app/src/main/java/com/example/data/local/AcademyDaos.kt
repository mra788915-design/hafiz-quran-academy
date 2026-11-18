package com.example.data.local

import androidx.room.*
import com.example.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY admissionNumber ASC")
    fun getAllStudentsFlow(): Flow<List<Student>>

    @Query("SELECT * FROM students ORDER BY admissionNumber ASC")
    suspend fun getAllStudents(): List<Student>

    @Query("SELECT * FROM students WHERE status = 'ACTIVE' ORDER BY fullName ASC")
    fun getActiveStudentsFlow(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE status = 'ACTIVE' ORDER BY fullName ASC")
    suspend fun getActiveStudents(): List<Student>

    @Query("SELECT * FROM students WHERE classId = :classId AND status = 'ACTIVE' ORDER BY fullName ASC")
    fun getStudentsByClassFlow(classId: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE classId = :classId AND status = 'ACTIVE' ORDER BY fullName ASC")
    suspend fun getStudentsByClass(classId: String): List<Student>

    @Query("SELECT * FROM students WHERE teacherId = :teacherId AND status = 'ACTIVE' ORDER BY fullName ASC")
    fun getStudentsByTeacherFlow(teacherId: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): Student?

    @Query("SELECT * FROM students WHERE admissionNumber = :admNo LIMIT 1")
    suspend fun getStudentByAdmissionNo(admNo: String): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("SELECT * FROM students WHERE status = 'WITHDRAWN'")
    suspend fun getWithdrawnStudents(): List<Student>

    @Query("DELETE FROM students WHERE id = :studentId")
    suspend fun deleteStudentById(studentId: String)
}

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY fullName ASC")
    fun getAllTeachersFlow(): Flow<List<Teacher>>

    @Query("SELECT * FROM teachers ORDER BY fullName ASC")
    suspend fun getAllTeachers(): List<Teacher>

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun getTeacherById(id: String): Teacher?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacher(teacher: Teacher)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeachers(teachers: List<Teacher>)

    @Update
    suspend fun updateTeacher(teacher: Teacher)

    @Delete
    suspend fun deleteTeacher(teacher: Teacher)

    @Query("DELETE FROM teachers WHERE id = :id")
    suspend fun deleteTeacherById(id: String)
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY name ASC")
    fun getAllClassesFlow(): Flow<List<AcademyClass>>

    @Query("SELECT * FROM classes ORDER BY name ASC")
    suspend fun getAllClasses(): List<AcademyClass>

    @Query("SELECT * FROM classes WHERE id = :id LIMIT 1")
    suspend fun getClassById(id: String): AcademyClass?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(academyClass: AcademyClass)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<AcademyClass>)

    @Update
    suspend fun updateClass(academyClass: AcademyClass)

    @Delete
    suspend fun deleteClass(academyClass: AcademyClass)

    @Query("DELETE FROM classes WHERE id = :id")
    suspend fun deleteClassById(id: String)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE date = :date AND classId = :classId")
    fun getAttendanceByClassAndDateFlow(classId: String, date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE date = :date AND classId = :classId")
    suspend fun getAttendanceByClassAndDate(classId: String, date: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance WHERE date = :date")
    suspend fun getAttendanceByDate(date: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun getAttendanceByStudentFlow(studentId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    suspend fun getAttendanceByStudent(studentId: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance WHERE date = :date AND studentId = :studentId LIMIT 1")
    suspend fun getStudentAttendanceForDate(studentId: String, date: String): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>)

    @Query("SELECT * FROM attendance ORDER BY date DESC LIMIT 200")
    fun getAllAttendanceFlow(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE id = :id LIMIT 1")
    suspend fun getAttendanceById(id: String): AttendanceRecord?

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteAttendanceById(id: String)

    @Query("DELETE FROM attendance WHERE classId = :classId AND date = :date")
    suspend fun deleteAttendanceByClassAndDate(classId: String, date: String)

    @Query("DELETE FROM attendance WHERE studentId = :studentId")
    suspend fun deleteAttendanceByStudentId(studentId: String)
}

@Dao
interface QuranProgressDao {
    @Query("SELECT * FROM quran_progress WHERE studentId = :studentId ORDER BY date DESC")
    fun getProgressByStudentFlow(studentId: String): Flow<List<QuranProgressRecord>>

    @Query("SELECT * FROM quran_progress WHERE date = :date ORDER BY studentName ASC")
    fun getProgressByDateFlow(date: String): Flow<List<QuranProgressRecord>>

    @Query("SELECT * FROM quran_progress ORDER BY date DESC LIMIT 200")
    fun getAllProgressFlow(): Flow<List<QuranProgressRecord>>

    @Query("SELECT * FROM quran_progress WHERE id = :id LIMIT 1")
    suspend fun getProgressById(id: String): QuranProgressRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(record: QuranProgressRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressList(records: List<QuranProgressRecord>)

    @Update
    suspend fun updateProgress(record: QuranProgressRecord)

    @Delete
    suspend fun deleteProgress(record: QuranProgressRecord)

    @Query("DELETE FROM quran_progress WHERE id = :id")
    suspend fun deleteProgressById(id: String)

    @Query("DELETE FROM quran_progress WHERE studentId = :studentId")
    suspend fun deleteProgressByStudentId(studentId: String)
}

@Dao
interface FeeChallanDao {
    @Query("SELECT * FROM fee_challans ORDER BY createdAt DESC")
    fun getAllChallansFlow(): Flow<List<FeeChallan>>

    @Query("SELECT * FROM fee_challans ORDER BY createdAt DESC")
    suspend fun getAllChallans(): List<FeeChallan>

    @Query("SELECT * FROM fee_challans WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun getChallansByStudentFlow(studentId: String): Flow<List<FeeChallan>>

    @Query("SELECT * FROM fee_challans WHERE id = :id LIMIT 1")
    suspend fun getChallanById(id: String): FeeChallan?

    @Query("SELECT * FROM fee_challans WHERE studentId = :studentId AND billingMonth = :month AND billingYear = :year LIMIT 1")
    suspend fun getChallanByStudentAndPeriod(studentId: String, month: String, year: Int): FeeChallan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallan(challan: FeeChallan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallans(challans: List<FeeChallan>)

    @Update
    suspend fun updateChallan(challan: FeeChallan)

    @Delete
    suspend fun deleteChallan(challan: FeeChallan)

    @Query("DELETE FROM fee_challans WHERE id = :id")
    suspend fun deleteChallanById(id: String)

    @Query("DELETE FROM fee_challans WHERE studentId = :studentId")
    suspend fun deleteChallansByStudentId(studentId: String)
}

@Dao
interface FeePaymentDao {
    @Query("SELECT * FROM fee_payments ORDER BY timestamp DESC")
    fun getAllPaymentsFlow(): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE challanId = :challanId ORDER BY timestamp DESC")
    fun getPaymentsByChallanFlow(challanId: String): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE challanId = :challanId ORDER BY timestamp DESC")
    suspend fun getPaymentsByChallan(challanId: String): List<FeePayment>

    @Query("SELECT * FROM fee_payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: String): FeePayment?

    @Query("SELECT * FROM fee_payments WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun getPaymentsByStudentFlow(studentId: String): Flow<List<FeePayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: FeePayment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<FeePayment>)

    @Update
    suspend fun updatePayment(payment: FeePayment)

    @Delete
    suspend fun deletePayment(payment: FeePayment)

    @Query("DELETE FROM fee_payments WHERE id = :id")
    suspend fun deletePaymentById(id: String)

    @Query("DELETE FROM fee_payments WHERE challanId = :challanId")
    suspend fun deletePaymentsByChallanId(challanId: String)

    @Query("DELETE FROM fee_payments WHERE studentId = :studentId")
    suspend fun deletePaymentsByStudentId(studentId: String)
}

@Dao
interface FeeSabilillahDao {
    @Query("SELECT * FROM fs_records ORDER BY createdAt DESC")
    fun getAllFsRecordsFlow(): Flow<List<FeeSabilillahRecord>>

    @Query("SELECT * FROM fs_records WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun getFsByStudentFlow(studentId: String): Flow<List<FeeSabilillahRecord>>

    @Query("SELECT * FROM fs_records WHERE id = :id LIMIT 1")
    suspend fun getFsById(id: String): FeeSabilillahRecord?

    @Query("SELECT * FROM fs_records WHERE studentId = :studentId AND approvalStatus = 'APPROVED' LIMIT 1")
    suspend fun getApprovedFsForStudent(studentId: String): FeeSabilillahRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFsRecord(record: FeeSabilillahRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFsRecords(records: List<FeeSabilillahRecord>)

    @Update
    suspend fun updateFsRecord(record: FeeSabilillahRecord)

    @Delete
    suspend fun deleteFsRecord(record: FeeSabilillahRecord)

    @Query("DELETE FROM fs_records WHERE id = :id")
    suspend fun deleteFsById(id: String)

    @Query("DELETE FROM fs_records WHERE studentId = :studentId")
    suspend fun deleteFsByStudentId(studentId: String)
}

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY timestamp DESC")
    fun getAllAnnouncementsFlow(): Flow<List<Announcement>>

    @Query("SELECT * FROM announcements WHERE id = :id LIMIT 1")
    suspend fun getAnnouncementById(id: String): Announcement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: Announcement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncements(announcements: List<Announcement>)

    @Update
    suspend fun updateAnnouncement(announcement: Announcement)

    @Delete
    suspend fun deleteAnnouncement(announcement: Announcement)

    @Query("DELETE FROM announcements WHERE id = :id")
    suspend fun deleteAnnouncementById(id: String)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE classId = :classId ORDER BY dayOfWeek ASC")
    fun getScheduleByClassFlow(classId: String): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedules ORDER BY dayOfWeek ASC")
    fun getAllSchedulesFlow(): Flow<List<ScheduleItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleItem>)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY role ASC, name ASC")
    fun getAllUsersFlow(): Flow<List<UserAccount>>

    @Query("SELECT * FROM users ORDER BY role ASC, name ASC")
    suspend fun getAllUsers(): List<UserAccount>

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:loginId) OR (email != '' AND LOWER(email) = LOWER(:loginId)) LIMIT 1")
    suspend fun getUserByLoginId(loginId: String): UserAccount?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserAccount?

    @Query("SELECT * FROM users WHERE teacherId = :teacherId AND role = 'TEACHER' LIMIT 1")
    suspend fun getUserByTeacherId(teacherId: String): UserAccount?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserAccount>)

    @Update
    suspend fun updateUser(user: UserAccount)

    @Delete
    suspend fun deleteUser(user: UserAccount)

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()
}

@Dao
interface SyncCleanupDao {
    @Query("DELETE FROM students")
    suspend fun clearAllStudents()

    @Query("DELETE FROM teachers")
    suspend fun clearAllTeachers()

    @Query("DELETE FROM classes")
    suspend fun clearAllClasses()

    @Query("DELETE FROM attendance")
    suspend fun clearAllAttendance()

    @Query("DELETE FROM quran_progress")
    suspend fun clearAllProgress()

    @Query("DELETE FROM fee_challans")
    suspend fun clearAllChallans()

    @Query("DELETE FROM fee_payments")
    suspend fun clearAllPayments()

    @Query("DELETE FROM fs_records")
    suspend fun clearAllFsRecords()

    @Query("DELETE FROM announcements")
    suspend fun clearAllAnnouncements()
}

