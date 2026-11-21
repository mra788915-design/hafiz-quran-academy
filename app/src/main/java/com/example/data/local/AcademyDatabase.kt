package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.models.*

@Database(
    entities = [
        UserAccount::class,
        Student::class,
        Teacher::class,
        AcademyClass::class,
        AttendanceRecord::class,
        QuranProgressRecord::class,
        NamazLogRecord::class,
        MadniQaidaLogRecord::class,
        LeaveRequest::class,
        FeeChallan::class,
        FeePayment::class,
        FeeSabilillahRecord::class,
        Announcement::class,
        ScheduleItem::class,
        AcademyExam::class,
        ExamMarkRecord::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AcademyDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun teacherDao(): TeacherDao
    abstract fun classDao(): ClassDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun quranProgressDao(): QuranProgressDao
    abstract fun namazLogDao(): NamazLogDao
    abstract fun madniQaidaLogDao(): MadniQaidaLogDao
    abstract fun leaveRequestDao(): LeaveRequestDao
    abstract fun feeChallanDao(): FeeChallanDao
    abstract fun feePaymentDao(): FeePaymentDao
    abstract fun feeSabilillahDao(): FeeSabilillahDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun examDao(): ExamDao
    abstract fun examMarkDao(): ExamMarkDao
    abstract fun userDao(): UserDao
    abstract fun syncCleanupDao(): SyncCleanupDao

    companion object {
        @Volatile
        private var INSTANCE: AcademyDatabase? = null

        fun getDatabase(context: Context): AcademyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AcademyDatabase::class.java,
                    "hafiz_quran_academy_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
