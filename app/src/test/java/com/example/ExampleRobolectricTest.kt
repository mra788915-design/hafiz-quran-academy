package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AcademyDatabase
import com.example.data.models.FeeChallan
import com.example.data.models.UserRole
import com.example.data.repository.AcademyRepository
import com.example.util.ChallanPdfGenerator
import com.example.util.QrCodeGenerator
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AcademyDatabase
    private lateinit var repository: AcademyRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AcademyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AcademyRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Hafiz Quran Academy", appName)
    }

    @Test
    fun `strict single portal role isolation rejects cross role logins`() = runBlocking {
        repository.seedInitialDataIfEmpty()

        // Admin can sign into Admin Portal
        val adminOk = repository.authenticatePortalUser("admin", "Admin@123", UserRole.ADMIN)
        assertTrue(adminOk.isSuccess)

        // Admin CANNOT sign into Teacher or Parent Portal
        val adminOnTeacher = repository.authenticatePortalUser("admin", "Admin@123", UserRole.TEACHER)
        assertTrue(adminOnTeacher.isFailure)

        // Teacher can sign into Teacher Portal ONLY
        val teacherOk = repository.authenticatePortalUser("qari.abdullah", "Tchr@101", UserRole.TEACHER)
        assertTrue(teacherOk.isSuccess)

        val teacherOnAdmin = repository.authenticatePortalUser("qari.abdullah", "Tchr@101", UserRole.ADMIN)
        assertTrue(teacherOnAdmin.isFailure)

        val teacherOnParent = repository.authenticatePortalUser("qari.abdullah", "Tchr@101", UserRole.PARENT)
        assertTrue(teacherOnParent.isFailure)

        // Parent can sign into Parent Portal ONLY
        val parentOk = repository.authenticatePortalUser("p.farooq", "Prnt@101", UserRole.PARENT)
        assertTrue(parentOk.isSuccess)

        val parentOnAdmin = repository.authenticatePortalUser("p.farooq", "Prnt@101", UserRole.ADMIN)
        assertTrue(parentOnAdmin.isFailure)
    }

    @Test
    fun `admin credential vault creates and suspends teacher and parent logins`() = runBlocking {
        repository.seedInitialDataIfEmpty()

        val createdParent = repository.savePortalCredentials(
            name = "Tariq Mehmood (Parent)",
            username = "p.tariq",
            password = "Secret@789",
            role = UserRole.PARENT,
            phone = "03009998877",
            linkedStudentIds = "stud_1"
        )
        assertTrue(createdParent.isSuccess)
        val userId = createdParent.getOrThrow().id

        // Active parent can log into Parent Portal
        assertTrue(repository.authenticatePortalUser("p.tariq", "Secret@789", UserRole.PARENT).isSuccess)

        // Admin suspends account -> login blocked immediately
        repository.toggleUserAccountActive(userId, false)
        assertTrue(repository.authenticatePortalUser("p.tariq", "Secret@789", UserRole.PARENT).isFailure)
    }

    @Test
    fun `render 3 copy challan and scan official easypaisa raast qr code`() {
        val challan = FeeChallan(
            challanNumber = "HQA-202610-0101-789",
            studentId = "s1",
            admissionNumber = "HQA-26-0101",
            studentName = "Muhammad Bilal Farooq",
            classId = "c1",
            className = "Hifz Halaqah 1",
            billingMonth = "October",
            billingYear = 2026,
            issueDate = "2026-10-01",
            dueDate = "2026-10-10",
            monthlyTuitionFee = 4500.0,
            additionalCharges = 200.0,
            totalPayable = 4700.0,
            remainingBalance = 4700.0
        )

        val reader = QRCodeReader()

        // 1. Render full 3-copy landscape A4 challan sheet (842 x 595) and scan QR directly off Copy #1
        val sheetBitmap = ChallanPdfGenerator.renderThreeCopyChallanBitmap(challan, repository.settings, context)
        assertEquals(ChallanPdfGenerator.PAGE_WIDTH, sheetBitmap.width)
        assertEquals(ChallanPdfGenerator.PAGE_HEIGHT, sheetBitmap.height)

        val cropX = 30
        val cropY = 354
        val cropSize = 116
        val croppedPixels = IntArray(cropSize * cropSize)
        sheetBitmap.getPixels(croppedPixels, 0, cropSize, cropX, cropY, cropSize, cropSize)
        val printedQrDecoded = reader.decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(cropSize, cropSize, croppedPixels)))
        ).text
        assertEquals(QrCodeGenerator.OFFICIAL_EASYPAISA_RAAST_QR, printedQrDecoded)
    }

    @Test
    fun `withdrawing student permanently removes student records and unlinks parent login`() = runBlocking {
        repository.seedInitialDataIfEmpty()

        val initialStudents = database.studentDao().getAllStudents()
        val ahmadRaza = initialStudents.first { it.admissionNumber == "HQA-26-0104" }

        // Parent p.raza can initially sign in
        assertTrue(repository.authenticatePortalUser("p.raza", "Prnt@104", UserRole.PARENT).isSuccess)

        // Withdraw student -> permanently deletes student, challans, attendance, and sole-child parent login
        repository.setStudentStatus(ahmadRaza.id, com.example.data.models.StudentStatus.WITHDRAWN)

        assertNull(database.studentDao().getStudentById(ahmadRaza.id))
        assertTrue(database.feeChallanDao().getAllChallans().none { it.studentId == ahmadRaza.id })
        assertTrue(repository.authenticatePortalUser("p.raza", "Prnt@104", UserRole.PARENT).isFailure)
    }
}
