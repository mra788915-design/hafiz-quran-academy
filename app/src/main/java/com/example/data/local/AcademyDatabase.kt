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
        FeeChallan::class,
        FeePayment::class,
        FeeSabilillahRecord::class,
        Announcement::class,
        ScheduleItem::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AcademyDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun teacherDao(): TeacherDao
    abstract fun classDao(): ClassDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun quranProgressDao(): QuranProgressDao
    abstract fun feeChallanDao(): FeeChallanDao
    abstract fun feePaymentDao(): FeePaymentDao
    abstract fun feeSabilillahDao(): FeeSabilillahDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun scheduleDao(): ScheduleDao
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
