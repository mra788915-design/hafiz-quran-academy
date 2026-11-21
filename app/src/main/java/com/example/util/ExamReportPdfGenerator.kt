package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.models.AcademyExam
import com.example.data.models.AcademySettings
import com.example.data.models.ExamGradeHelper
import com.example.data.models.ExamMarkRecord
import com.example.data.models.Student
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExamReportPdfGenerator {

    // Standard ISO A4 Portrait in PostScript points (72 dpi): 595 x 842
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    private const val FILE_PROVIDER_AUTHORITY = "com.aistudio.hafizquran.acdm.fileprovider"

    private val emeraldDeep = Color.parseColor("#0B4F32")
    private val emeraldMid = Color.parseColor("#146C43")
    private val emeraldTint = Color.parseColor("#ECFDF5")
    private val goldRoyal = Color.parseColor("#B8860B")
    private val goldLight = Color.parseColor("#FEF9E7")
    private val slateBg = Color.parseColor("#F8FAFC")
    private val borderGray = Color.parseColor("#CBD5E1")
    private val inkDark = Color.parseColor("#0F172A")
    private val inkMuted = Color.parseColor("#475569")
    private val passGreen = Color.parseColor("#15803D")
    private val failRed = Color.parseColor("#B91C1C")
    private val absentAmber = Color.parseColor("#B45309")

    // =========================================================================
    // 1. STUDENT OFFICIAL RESULT CARD PDF (Portrait A4)
    // =========================================================================

    fun generateStudentResultCardPdf(
        context: Context,
        student: Student,
        marks: List<ExamMarkRecord>,
        filterTitle: String = "All Tests, Mid & Final Exams",
        settings: AcademySettings = AcademySettings()
    ): File {
        val outputDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val safeAdm = student.admissionNumber.replace("[^A-Za-z0-9_-]".toRegex(), "_")
        val outputFile = File(outputDir, "ResultCard_${safeAdm}.pdf")

        val document = PdfDocument()
        try {
            buildStudentResultCardPdfDocument(document, context, student, marks, filterTitle, settings)
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
            document.close()
        } catch (e: IllegalStateException) {
            runCatching { document.close() }
            val bitmaps = renderStudentResultCardBitmaps(context, student, marks, filterTitle, settings, scale = 2f)
            writeBitmapsAsPdf14(outputFile, bitmaps)
        }
        return outputFile
    }

    fun printStudentResultCard(
        context: Context,
        student: Student,
        marks: List<ExamMarkRecord>,
        filterTitle: String = "All Tests, Mid & Final Exams",
        settings: AcademySettings = AcademySettings()
    ) {
        val activity = context.findActivity()
        val printManager = activity?.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            shareStudentResultCardPdf(context, student, marks, filterTitle, settings)
            return
        }

        val safeName = student.fullName.replace("[^A-Za-z0-9 ]".toRegex(), "").trim().replace("\\s+".toRegex(), "_")
        val jobName = "HQA_ResultCard_${safeName.ifBlank { student.admissionNumber }}"

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val sortedMarks = marks.sortedWith(compareBy<ExamMarkRecord> { it.examDate }.thenBy { it.examType.ordinal })
        val totalPages = computeStudentResultCardPageCount(sortedMarks.size)

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(totalPages)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (destination == null) {
                    callback.onWriteFailed("Invalid print destination.")
                    return
                }
                val pdfDoc = PdfDocument()
                try {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    buildStudentResultCardPdfDocument(pdfDoc, context, student, sortedMarks, filterTitle, settings)
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                        out.flush()
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Failed to generate Result Card PDF.")
                } finally {
                    pdfDoc.close()
                }
            }
        }
        printManager.print(jobName, adapter, printAttributes)
    }

    fun shareStudentResultCardPdf(
        context: Context,
        student: Student,
        marks: List<ExamMarkRecord>,
        filterTitle: String = "All Tests, Mid & Final Exams",
        settings: AcademySettings = AcademySettings()
    ) {
        try {
            val file = generateStudentResultCardPdf(context, student, marks, filterTitle, settings)
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Official Result Card - ${student.fullName} (${student.admissionNumber})")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Assalam-o-Alaikum! Attached is the Official Exam Result Card for ${student.fullName} (${student.admissionNumber}) from ${settings.academyName}."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share / Save Result Card PDF").apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share Result Card PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun computeStudentResultCardPageCount(rowCount: Int): Int {
        if (rowCount <= 16) return 1
        val remaining = rowCount - 16
        return 1 + ((remaining + 25) / 26)
    }

    private fun buildStudentResultCardPdfDocument(
        document: PdfDocument,
        context: Context,
        student: Student,
        marks: List<ExamMarkRecord>,
        filterTitle: String,
        settings: AcademySettings
    ) {
        val sortedMarks = marks.sortedWith(compareBy<ExamMarkRecord> { it.examDate }.thenBy { it.examType.ordinal })
        val appearedMarks = sortedMarks.filter { !it.isAbsent }
        val totalObtained = appearedMarks.sumOf { it.obtainedMarks }
        val totalMax = sortedMarks.filter { !it.isAbsent }.sumOf { it.totalMarks }
        val totalPossibleAll = sortedMarks.sumOf { it.totalMarks }
        val overallPct = ExamGradeHelper.computePercentage(totalObtained, totalPossibleAll)
        val passedCount = sortedMarks.count { !it.isAbsent && it.obtainedMarks >= it.passingMarks }
        val failedCount = sortedMarks.count { !it.isAbsent && it.obtainedMarks < it.passingMarks }
        val absentCount = sortedMarks.count { it.isAbsent }
        val overallGrade = if (sortedMarks.isEmpty()) {
            "N/A"
        } else {
            ExamGradeHelper.computeGrade(
                obtainedMarks = overallPct,
                totalMarks = 100.0,
                passingMarks = 40.0,
                isAbsent = appearedMarks.isEmpty() && absentCount > 0
            )
        }

        val chunks = mutableListOf<List<ExamMarkRecord>>()
        if (sortedMarks.isEmpty()) {
            chunks.add(emptyList())
        } else {
            val firstPage = sortedMarks.take(16)
            chunks.add(firstPage)
            var idx = 16
            while (idx < sortedMarks.size) {
                chunks.add(sortedMarks.subList(idx, (idx + 26).coerceAtMost(sortedMarks.size)))
                idx += 26
            }
        }

        val totalPages = chunks.size
        val issueDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        chunks.forEachIndexed { pageIndex, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            drawStudentResultCardPage(
                canvas = page.canvas,
                context = context,
                student = student,
                pageRows = pageRows,
                pageIndex = pageIndex,
                totalPages = totalPages,
                filterTitle = filterTitle,
                issueDate = issueDate,
                sortedMarksSize = sortedMarks.size,
                passedCount = passedCount,
                failedCount = failedCount,
                absentCount = absentCount,
                totalObtained = totalObtained,
                totalPossible = if (totalPossibleAll > 0) totalPossibleAll else totalMax,
                overallPct = overallPct,
                overallGrade = overallGrade,
                settings = settings
            )
            document.finishPage(page)
        }
    }

    private fun renderStudentResultCardBitmaps(
        context: Context,
        student: Student,
        marks: List<ExamMarkRecord>,
        filterTitle: String,
        settings: AcademySettings,
        scale: Float = 2f
    ): List<Bitmap> {
        val sortedMarks = marks.sortedWith(compareBy<ExamMarkRecord> { it.examDate }.thenBy { it.examType.ordinal })
        val appearedMarks = sortedMarks.filter { !it.isAbsent }
        val totalObtained = appearedMarks.sumOf { it.obtainedMarks }
        val totalMax = appearedMarks.sumOf { it.totalMarks }
        val totalPossibleAll = sortedMarks.sumOf { it.totalMarks }
        val overallPct = ExamGradeHelper.computePercentage(totalObtained, totalPossibleAll)
        val passedCount = sortedMarks.count { !it.isAbsent && it.obtainedMarks >= it.passingMarks }
        val failedCount = sortedMarks.count { !it.isAbsent && it.obtainedMarks < it.passingMarks }
        val absentCount = sortedMarks.count { it.isAbsent }
        val overallGrade = if (sortedMarks.isEmpty()) "N/A" else ExamGradeHelper.computeGrade(
            obtainedMarks = overallPct,
            totalMarks = 100.0,
            passingMarks = 40.0,
            isAbsent = appearedMarks.isEmpty() && absentCount > 0
        )
        val chunks = mutableListOf<List<ExamMarkRecord>>()
        if (sortedMarks.isEmpty()) {
            chunks.add(emptyList())
        } else {
            chunks.add(sortedMarks.take(16))
            var idx = 16
            while (idx < sortedMarks.size) {
                chunks.add(sortedMarks.subList(idx, (idx + 26).coerceAtMost(sortedMarks.size)))
                idx += 26
            }
        }
        val issueDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val widthPx = (PAGE_WIDTH * scale).toInt().coerceAtLeast(PAGE_WIDTH)
        val heightPx = (PAGE_HEIGHT * scale).toInt().coerceAtLeast(PAGE_HEIGHT)
        return chunks.mapIndexed { pageIndex, pageRows ->
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.save()
            canvas.scale(scale, scale)
            drawStudentResultCardPage(
                canvas = canvas,
                context = context,
                student = student,
                pageRows = pageRows,
                pageIndex = pageIndex,
                totalPages = chunks.size,
                filterTitle = filterTitle,
                issueDate = issueDate,
                sortedMarksSize = sortedMarks.size,
                passedCount = passedCount,
                failedCount = failedCount,
                absentCount = absentCount,
                totalObtained = totalObtained,
                totalPossible = if (totalPossibleAll > 0) totalPossibleAll else totalMax,
                overallPct = overallPct,
                overallGrade = overallGrade,
                settings = settings
            )
            canvas.restore()
            bitmap
        }
    }

    private fun drawStudentResultCardPage(
        canvas: Canvas,
        context: Context,
        student: Student,
        pageRows: List<ExamMarkRecord>,
        pageIndex: Int,
        totalPages: Int,
        filterTitle: String,
        issueDate: String,
        sortedMarksSize: Int,
        passedCount: Int,
        failedCount: Int,
        absentCount: Int,
        totalObtained: Double,
        totalPossible: Double,
        overallPct: Double,
        overallGrade: String,
        settings: AcademySettings
    ) {
        drawPageFrame(canvas)
        var y = drawAcademyHeader(
            canvas = canvas,
            title = "OFFICIAL ACADEMIC RESULT CARD",
            subtitle = "Monthly Tests • Mid-Term • Final-Term Assessment Transcript",
            settings = settings,
            pageNumber = pageIndex + 1,
            totalPages = totalPages
        )
        if (pageIndex == 0) {
            y = drawStudentProfileBox(canvas, student, filterTitle, issueDate, y + 8f)
            y = drawStudentSummaryStrip(
                canvas = canvas,
                totalExams = sortedMarksSize,
                passedCount = passedCount,
                failedCount = failedCount,
                absentCount = absentCount,
                totalObtained = totalObtained,
                totalPossible = totalPossible,
                overallPct = overallPct,
                overallGrade = overallGrade,
                topY = y + 8f
            )
        } else {
            val contPaint = TextPaintHelper.bold(11f, emeraldDeep)
            canvas.drawText(
                "Student: ${student.fullName} (${student.admissionNumber}) • Continued Exam Marks",
                36f,
                y + 18f,
                contPaint
            )
            y += 28f
        }
        drawStudentMarksTable(canvas, pageRows, topY = y + 10f)
        if (pageIndex == totalPages - 1) {
            drawStampAndSignaturesFooter(
                canvas = canvas,
                context = context,
                leftLabel = "Class Ustadh / Examiner",
                leftSub = student.teacherName.ifBlank { "Academy Faculty" },
                rightLabel = settings.authorizedSignatory,
                rightSub = settings.academyName,
                settings = settings
            )
        } else {
            drawContinuationFooter(canvas, settings, pageIndex + 1, totalPages)
        }
    }

    // =========================================================================
    // 2. CLASS / EXAM CONSOLIDATED REPORT PDF (Portrait A4)
    // =========================================================================

    fun generateExamMarksReportPdf(
        context: Context,
        exam: AcademyExam,
        marks: List<ExamMarkRecord>,
        enrolledStudents: List<Student>,
        settings: AcademySettings = AcademySettings()
    ): File {
        val outputDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val safeExamId = exam.title.replace("[^A-Za-z0-9_-]".toRegex(), "_").take(28)
        val outputFile = File(outputDir, "ExamReport_${safeExamId}.pdf")

        val document = PdfDocument()
        try {
            buildExamMarksReportPdfDocument(document, context, exam, marks, enrolledStudents, settings)
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
            document.close()
        } catch (e: IllegalStateException) {
            runCatching { document.close() }
            val bitmaps = renderExamMarksReportBitmaps(context, exam, marks, enrolledStudents, settings, scale = 2f)
            writeBitmapsAsPdf14(outputFile, bitmaps)
        }
        return outputFile
    }

    fun printExamMarksReport(
        context: Context,
        exam: AcademyExam,
        marks: List<ExamMarkRecord>,
        enrolledStudents: List<Student>,
        settings: AcademySettings = AcademySettings()
    ) {
        val activity = context.findActivity()
        val printManager = activity?.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            shareExamMarksReportPdf(context, exam, marks, enrolledStudents, settings)
            return
        }

        val safeTitle = exam.title.replace("[^A-Za-z0-9 ]".toRegex(), "").trim().replace("\\s+".toRegex(), "_")
        val jobName = "HQA_ExamReport_${safeTitle.ifBlank { exam.id }}"

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val rows = buildConsolidatedExamRows(exam, marks, enrolledStudents)
        val totalPages = computeExamReportPageCount(rows.size)

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(totalPages)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (destination == null) {
                    callback.onWriteFailed("Invalid print destination.")
                    return
                }
                val pdfDoc = PdfDocument()
                try {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    buildExamMarksReportPdfDocument(pdfDoc, context, exam, marks, enrolledStudents, settings)
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                        out.flush()
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Failed to write Exam Report PDF.")
                } finally {
                    pdfDoc.close()
                }
            }
        }
        printManager.print(jobName, adapter, printAttributes)
    }

    fun shareExamMarksReportPdf(
        context: Context,
        exam: AcademyExam,
        marks: List<ExamMarkRecord>,
        enrolledStudents: List<Student>,
        settings: AcademySettings = AcademySettings()
    ) {
        try {
            val file = generateExamMarksReportPdf(context, exam, marks, enrolledStudents, settings)
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Exam Marks Sheet - ${exam.title} (${exam.className})")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Exam Assessment Report: ${exam.title} (${exam.examType.shortLabel}) for ${exam.className} conducted on ${exam.examDate}."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share / Save Exam Report PDF").apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share Exam Report PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private data class ExamReportRow(
        val rankLabel: String,
        val admissionNumber: String,
        val studentName: String,
        val obtainedText: String,
        val percentageText: String,
        val grade: String,
        val statusText: String,
        val statusColor: Int,
        val remarks: String
    )

    private fun buildConsolidatedExamRows(
        exam: AcademyExam,
        marks: List<ExamMarkRecord>,
        enrolledStudents: List<Student>
    ): List<ExamReportRow> {
        val markByStudent = marks.associateBy { it.studentId }
        val rankedMarks = marks.sortedWith(
            compareByDescending<ExamMarkRecord> { !it.isAbsent }
                .thenByDescending { it.obtainedMarks }
                .thenBy { it.studentName }
        )

        val rows = mutableListOf<ExamReportRow>()
        var rankCounter = 1
        rankedMarks.forEach { m ->
            val pct = if (m.isAbsent) 0.0 else ExamGradeHelper.computePercentage(m.obtainedMarks, m.totalMarks)
            val passed = !m.isAbsent && m.obtainedMarks >= m.passingMarks
            val (statusTxt, statusClr) = when {
                m.isAbsent -> "ABSENT" to absentAmber
                passed -> "PASS" to passGreen
                else -> "FAIL" to failRed
            }
            rows.add(
                ExamReportRow(
                    rankLabel = if (m.isAbsent) "-" else "#${rankCounter++}",
                    admissionNumber = m.admissionNumber,
                    studentName = m.studentName,
                    obtainedText = if (m.isAbsent) "AB" else "${formatScore(m.obtainedMarks)} / ${formatScore(m.totalMarks)}",
                    percentageText = if (m.isAbsent) "-" else String.format(Locale.US, "%.1f%%", pct),
                    grade = m.grade.ifBlank {
                        ExamGradeHelper.computeGrade(m.obtainedMarks, m.totalMarks, m.passingMarks, m.isAbsent)
                    },
                    statusText = statusTxt,
                    statusColor = statusClr,
                    remarks = m.remarks.ifBlank { "-" }
                )
            )
        }

        // Also list any active enrolled class students whose marks are pending
        enrolledStudents
            .filter { it.id !in markByStudent.keys }
            .sortedBy { it.fullName }
            .forEach { stud ->
                rows.add(
                    ExamReportRow(
                        rankLabel = "-",
                        admissionNumber = stud.admissionNumber,
                        studentName = stud.fullName,
                        obtainedText = "Pending",
                        percentageText = "-",
                        grade = "Pending",
                        statusText = "PENDING",
                        statusColor = inkMuted,
                        remarks = "Marks not uploaded yet"
                    )
                )
            }
        return rows
    }

    private fun computeExamReportPageCount(rowCount: Int): Int {
        if (rowCount <= 18) return 1
        val remaining = rowCount - 18
        return 1 + ((remaining + 26) / 27)
    }

    private fun buildExamMarksReportPdfDocument(
        document: PdfDocument,
        context: Context,
        exam: AcademyExam,
        marks: List<ExamMarkRecord>,
        enrolledStudents: List<Student>,
        settings: AcademySettings
    ) {
        val rows = buildConsolidatedExamRows(exam, marks, enrolledStudents)
        val appeared = marks.filter { !it.isAbsent }
        val passedCount = appeared.count { it.obtainedMarks >= it.passingMarks }
        val failedCount = appeared.count { it.obtainedMarks < it.passingMarks }
        val absentCount = marks.count { it.isAbsent }
        val avgScore = if (appeared.isNotEmpty()) appeared.map { it.obtainedMarks }.average() else 0.0
        val avgPct = ExamGradeHelper.computePercentage(avgScore, exam.totalMarks)
        val highest = appeared.maxOfOrNull { it.obtainedMarks } ?: 0.0

        val chunks = mutableListOf<List<ExamReportRow>>()
        if (rows.isEmpty()) {
            chunks.add(emptyList())
        } else {
            chunks.add(rows.take(18))
            var idx = 18
            while (idx < rows.size) {
                chunks.add(rows.subList(idx, (idx + 27).coerceAtMost(rows.size)))
                idx += 27
            }
        }

        val totalPages = chunks.size
        chunks.forEachIndexed { pageIndex, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            drawExamReportPage(
                canvas = page.canvas,
                context = context,
                exam = exam,
                marks = marks,
                pageRows = pageRows,
                pageIndex = pageIndex,
                totalPages = totalPages,
                totalStudents = maxOf(enrolledStudents.size, marks.size),
                gradedCount = marks.size,
                passedCount = passedCount,
                failedCount = failedCount,
                absentCount = absentCount,
                avgPct = avgPct,
                highest = highest,
                settings = settings
            )
            document.finishPage(page)
        }
    }

    private fun renderExamMarksReportBitmaps(
        context: Context,
        exam: AcademyExam,
        marks: List<ExamMarkRecord>,
        enrolledStudents: List<Student>,
        settings: AcademySettings,
        scale: Float = 2f
    ): List<Bitmap> {
        val rows = buildConsolidatedExamRows(exam, marks, enrolledStudents)
        val appeared = marks.filter { !it.isAbsent }
        val passedCount = appeared.count { it.obtainedMarks >= it.passingMarks }
        val failedCount = appeared.count { it.obtainedMarks < it.passingMarks }
        val absentCount = marks.count { it.isAbsent }
        val avgScore = if (appeared.isNotEmpty()) appeared.map { it.obtainedMarks }.average() else 0.0
        val avgPct = ExamGradeHelper.computePercentage(avgScore, exam.totalMarks)
        val highest = appeared.maxOfOrNull { it.obtainedMarks } ?: 0.0

        val chunks = mutableListOf<List<ExamReportRow>>()
        if (rows.isEmpty()) {
            chunks.add(emptyList())
        } else {
            chunks.add(rows.take(18))
            var idx = 18
            while (idx < rows.size) {
                chunks.add(rows.subList(idx, (idx + 27).coerceAtMost(rows.size)))
                idx += 27
            }
        }
        val widthPx = (PAGE_WIDTH * scale).toInt().coerceAtLeast(PAGE_WIDTH)
        val heightPx = (PAGE_HEIGHT * scale).toInt().coerceAtLeast(PAGE_HEIGHT)
        return chunks.mapIndexed { pageIndex, pageRows ->
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.save()
            canvas.scale(scale, scale)
            drawExamReportPage(
                canvas = canvas,
                context = context,
                exam = exam,
                marks = marks,
                pageRows = pageRows,
                pageIndex = pageIndex,
                totalPages = chunks.size,
                totalStudents = maxOf(enrolledStudents.size, marks.size),
                gradedCount = marks.size,
                passedCount = passedCount,
                failedCount = failedCount,
                absentCount = absentCount,
                avgPct = avgPct,
                highest = highest,
                settings = settings
            )
            canvas.restore()
            bitmap
        }
    }

    private fun drawExamReportPage(
        canvas: Canvas,
        context: Context,
        exam: AcademyExam,
        marks: List<ExamMarkRecord>,
        pageRows: List<ExamReportRow>,
        pageIndex: Int,
        totalPages: Int,
        totalStudents: Int,
        gradedCount: Int,
        passedCount: Int,
        failedCount: Int,
        absentCount: Int,
        avgPct: Double,
        highest: Double,
        settings: AcademySettings
    ) {
        drawPageFrame(canvas)
        var y = drawAcademyHeader(
            canvas = canvas,
            title = "EXAMINATION AWARD LIST & MARKS REPORT",
            subtitle = "${exam.examType.displayLabel} • ${exam.className} • Term ${exam.academicTerm}",
            settings = settings,
            pageNumber = pageIndex + 1,
            totalPages = totalPages
        )
        if (pageIndex == 0) {
            y = drawExamInfoAndStatsBox(
                canvas = canvas,
                exam = exam,
                totalStudents = totalStudents,
                gradedCount = gradedCount,
                passedCount = passedCount,
                failedCount = failedCount,
                absentCount = absentCount,
                avgPct = avgPct,
                highest = highest,
                topY = y + 8f
            )
        } else {
            val contPaint = TextPaintHelper.bold(11f, emeraldDeep)
            canvas.drawText(
                "Exam: ${exam.title} (${exam.className}) • Continued Marks Sheet",
                36f,
                y + 18f,
                contPaint
            )
            y += 28f
        }
        drawExamReportTable(canvas, pageRows, topY = y + 10f)
        if (pageIndex == totalPages - 1) {
            val gradedTeacher = marks.firstOrNull { it.gradedByTeacherName.isNotBlank() }?.gradedByTeacherName
                ?: "Subject Ustadh"
            drawStampAndSignaturesFooter(
                canvas = canvas,
                context = context,
                leftLabel = "Examiner / Class Teacher",
                leftSub = gradedTeacher,
                rightLabel = settings.authorizedSignatory,
                rightSub = "Controller of Examinations",
                settings = settings
            )
        } else {
            drawContinuationFooter(canvas, settings, pageIndex + 1, totalPages)
        }
    }

    private fun writeBitmapsAsPdf14(outputFile: File, bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        FileOutputStream(outputFile).use { fos ->
            var offset = 0
            val pageCount = bitmaps.size
            // Objects: 1=Catalog, 2=Pages, per page i: pageObj, imageObj, contentObj (3 per page)
            val totalObjs = 2 + pageCount * 3
            val offsets = IntArray(totalObjs + 1)

            fun writeAscii(text: String) {
                val bytes = text.toByteArray(Charsets.US_ASCII)
                fos.write(bytes)
                offset += bytes.size
            }

            writeAscii("%PDF-1.4\n")
            offsets[1] = offset
            writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

            val kidRefs = (0 until pageCount).joinToString(" ") { "${3 + it * 3} 0 R" }
            offsets[2] = offset
            writeAscii("2 0 obj\n<< /Type /Pages /Kids [$kidRefs] /Count $pageCount >>\nendobj\n")

            bitmaps.forEachIndexed { idx, bitmap ->
                val pageObj = 3 + idx * 3
                val imgObj = pageObj + 1
                val contentObj = pageObj + 2

                val imgStream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, imgStream)
                val jpegBytes = imgStream.toByteArray()
                val contentStream = "q $PAGE_WIDTH 0 0 $PAGE_HEIGHT 0 0 cm /Im0 Do Q\n"

                offsets[pageObj] = offset
                writeAscii(
                    "$pageObj 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                        "/Resources << /XObject << /Im0 $imgObj 0 R >> >> /Contents $contentObj 0 R >>\nendobj\n"
                )
                offsets[imgObj] = offset
                writeAscii(
                    "$imgObj 0 obj\n<< /Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
                )
                fos.write(jpegBytes)
                offset += jpegBytes.size
                writeAscii("\nendstream\nendobj\n")

                offsets[contentObj] = offset
                writeAscii("$contentObj 0 obj\n<< /Length ${contentStream.length} >>\nstream\n$contentStream\nendstream\nendobj\n")
            }

            val xrefOffset = offset
            writeAscii("xref\n0 ${totalObjs + 1}\n0000000000 65535 f \n")
            for (i in 1..totalObjs) {
                writeAscii(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
            }
            writeAscii("trailer\n<< /Size ${totalObjs + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
            fos.flush()
        }
    }

    // =========================================================================
    // SHARED CANVAS DRAWING PRIMITIVES
    // =========================================================================

    private fun drawPageFrame(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val innerGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldRoyal
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(18f, 18f, PAGE_WIDTH - 18f, PAGE_HEIGHT - 18f, outerPaint)
        canvas.drawRect(23f, 23f, PAGE_WIDTH - 23f, PAGE_HEIGHT - 23f, innerGoldPaint)
    }

    private fun drawAcademyHeader(
        canvas: Canvas,
        title: String,
        subtitle: String,
        settings: AcademySettings,
        pageNumber: Int,
        totalPages: Int
    ): Float {
        val headerRect = RectF(32f, 32f, PAGE_WIDTH - 32f, 114f)
        val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(headerRect, 8f, 8f, headerFill)

        val centerX = PAGE_WIDTH / 2f
        val arabicPaint = TextPaintHelper.regular(11f, Color.parseColor("#FDE68A"), Paint.Align.CENTER)
        canvas.drawText("حفظ قرآن اکیڈمی  •  بِسْمِ اللّٰهِ الرَّحْمٰنِ الرَّحِيْمِ", centerX, 49f, arabicPaint)

        val academyPaint = TextPaintHelper.bold(17f, Color.WHITE, Paint.Align.CENTER)
        canvas.drawText(settings.academyName.uppercase(Locale.US), centerX, 68f, academyPaint)

        val titlePaint = TextPaintHelper.bold(11.5f, Color.parseColor("#FEF08A"), Paint.Align.CENTER)
        canvas.drawText(title, centerX, 85f, titlePaint)

        val subPaint = TextPaintHelper.regular(9f, Color.parseColor("#E2E8F0"), Paint.Align.CENTER)
        canvas.drawText("$subtitle   •   Page $pageNumber of $totalPages", centerX, 100f, subPaint)

        return 118f
    }

    private fun drawStudentProfileBox(
        canvas: Canvas,
        student: Student,
        filterTitle: String,
        issueDate: String,
        topY: Float
    ): Float {
        val boxBottom = topY + 72f
        val rect = RectF(32f, topY, PAGE_WIDTH - 32f, boxBottom)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slateBg; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderGray; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRoundRect(rect, 6f, 6f, fill)
        canvas.drawRoundRect(rect, 6f, 6f, stroke)

        val lbl = TextPaintHelper.regular(8.5f, inkMuted)
        val valBold = TextPaintHelper.bold(10f, inkDark)
        val emeraldVal = TextPaintHelper.bold(10f, emeraldDeep)

        canvas.drawText("STUDENT NAME", 44f, topY + 16f, lbl)
        canvas.drawText(student.fullName.take(30), 44f, topY + 29f, emeraldVal)

        canvas.drawText("FATHER / GUARDIAN", 225f, topY + 16f, lbl)
        canvas.drawText(student.guardianName.take(28), 225f, topY + 29f, valBold)

        canvas.drawText("ADMISSION REG NO.", 415f, topY + 16f, lbl)
        canvas.drawText(student.admissionNumber, 415f, topY + 29f, valBold)

        canvas.drawText("CLASS / HALAQAH", 44f, topY + 49f, lbl)
        canvas.drawText(student.className.take(28), 44f, topY + 62f, valBold)

        canvas.drawText("REPORT SCOPE", 225f, topY + 49f, lbl)
        canvas.drawText(filterTitle.take(30), 225f, topY + 62f, valBold)

        canvas.drawText("ISSUED ON", 415f, topY + 49f, lbl)
        canvas.drawText(issueDate, 415f, topY + 62f, valBold)

        return boxBottom
    }

    private fun drawStudentSummaryStrip(
        canvas: Canvas,
        totalExams: Int,
        passedCount: Int,
        failedCount: Int,
        absentCount: Int,
        totalObtained: Double,
        totalPossible: Double,
        overallPct: Double,
        overallGrade: String,
        topY: Float
    ): Float {
        val boxBottom = topY + 52f
        val rect = RectF(32f, topY, PAGE_WIDTH - 32f, boxBottom)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = emeraldTint; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = emeraldMid; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRoundRect(rect, 6f, 6f, fill)
        canvas.drawRoundRect(rect, 6f, 6f, stroke)

        val lbl = TextPaintHelper.regular(8.5f, emeraldDeep)
        val bigVal = TextPaintHelper.bold(12f, emeraldDeep)
        val normalVal = TextPaintHelper.bold(10.5f, inkDark)

        canvas.drawText("EXAMS TAKEN", 44f, topY + 18f, lbl)
        canvas.drawText("$totalExams ($passedCount Pass / $failedCount Fail / $absentCount AB)", 44f, topY + 37f, normalVal)

        canvas.drawText("AGGREGATE MARKS", 240f, topY + 18f, lbl)
        canvas.drawText("${formatScore(totalObtained)} / ${formatScore(totalPossible)}", 240f, topY + 37f, bigVal)

        canvas.drawText("PERCENTAGE", 365f, topY + 18f, lbl)
        canvas.drawText(String.format(Locale.US, "%.1f%%", overallPct), 365f, topY + 37f, bigVal)

        canvas.drawText("OVERALL GRADE", 455f, topY + 18f, lbl)
        canvas.drawText(overallGrade.take(18), 455f, topY + 37f, bigVal)

        return boxBottom
    }

    private fun drawStudentMarksTable(
        canvas: Canvas,
        rows: List<ExamMarkRecord>,
        topY: Float
    ): Float {
        val tableLeft = 32f
        val tableRight = PAGE_WIDTH - 32f
        val rowHeight = 23f

        // Header Bar
        val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldRoyal; style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(tableLeft, topY, tableRight, topY + 22f), 4f, 4f, headerBg)

        val hPaint = TextPaintHelper.bold(8.5f, Color.WHITE)
        canvas.drawText("DATE", 38f, topY + 14f, hPaint)
        canvas.drawText("EXAM TITLE & SUBJECT", 96f, topY + 14f, hPaint)
        canvas.drawText("TYPE", 252f, topY + 14f, hPaint)
        canvas.drawText("SCORE", 312f, topY + 14f, hPaint)
        canvas.drawText("%", 374f, topY + 14f, hPaint)
        canvas.drawText("GRADE", 412f, topY + 14f, hPaint)
        canvas.drawText("STATUS", 502f, topY + 14f, hPaint)

        var currentY = topY + 22f
        if (rows.isEmpty()) {
            val emptyPaint = TextPaintHelper.regular(11f, inkMuted, Paint.Align.CENTER)
            canvas.drawText("No exam marks uploaded yet for the selected filter.", PAGE_WIDTH / 2f, currentY + 36f, emptyPaint)
            return currentY + 60f
        }

        val altRowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 0.8f }
        val cellRegular = TextPaintHelper.regular(8.5f, inkDark)
        val cellBold = TextPaintHelper.bold(8.5f, inkDark)
        val cellSub = TextPaintHelper.regular(7.5f, inkMuted)

        rows.forEachIndexed { idx, mark ->
            val rBottom = currentY + rowHeight
            if (idx % 2 == 1) {
                canvas.drawRect(tableLeft, currentY, tableRight, rBottom, altRowBg)
            }
            val pct = if (mark.isAbsent) 0.0 else ExamGradeHelper.computePercentage(mark.obtainedMarks, mark.totalMarks)
            val passed = !mark.isAbsent && mark.obtainedMarks >= mark.passingMarks
            val (statusStr, statusColor) = when {
                mark.isAbsent -> "ABSENT" to absentAmber
                passed -> "PASS" to passGreen
                else -> "FAIL" to failRed
            }

            canvas.drawText(mark.examDate.take(10), 38f, currentY + 14f, cellRegular)
            canvas.drawText(mark.examTitle.take(26), 96f, currentY + 11f, cellBold)
            if (mark.subjectName.isNotBlank()) {
                canvas.drawText(mark.subjectName.take(28), 96f, currentY + 20f, cellSub)
            }
            canvas.drawText(mark.examType.shortLabel.take(10), 252f, currentY + 14f, cellRegular)
            val scoreStr = if (mark.isAbsent) "AB / ${formatScore(mark.totalMarks)}" else "${formatScore(mark.obtainedMarks)} / ${formatScore(mark.totalMarks)}"
            canvas.drawText(scoreStr, 312f, currentY + 14f, cellBold)
            canvas.drawText(if (mark.isAbsent) "-" else String.format(Locale.US, "%.0f%%", pct), 374f, currentY + 14f, cellRegular)
            canvas.drawText(mark.grade.take(16), 412f, currentY + 14f, cellRegular)
            canvas.drawText(statusStr, 502f, currentY + 14f, TextPaintHelper.bold(8.5f, statusColor))

            canvas.drawLine(tableLeft, rBottom, tableRight, rBottom, linePaint)
            currentY = rBottom
        }
        return currentY
    }

    private fun drawExamInfoAndStatsBox(
        canvas: Canvas,
        exam: AcademyExam,
        totalStudents: Int,
        gradedCount: Int,
        passedCount: Int,
        failedCount: Int,
        absentCount: Int,
        avgPct: Double,
        highest: Double,
        topY: Float
    ): Float {
        val boxBottom = topY + 84f
        val rect = RectF(32f, topY, PAGE_WIDTH - 32f, boxBottom)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slateBg; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderGray; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRoundRect(rect, 6f, 6f, fill)
        canvas.drawRoundRect(rect, 6f, 6f, stroke)

        val lbl = TextPaintHelper.regular(8f, inkMuted)
        val valBold = TextPaintHelper.bold(9.5f, inkDark)
        val emVal = TextPaintHelper.bold(9.5f, emeraldDeep)

        canvas.drawText("EXAM TITLE", 44f, topY + 14f, lbl)
        canvas.drawText(exam.title.take(32), 44f, topY + 26f, emVal)

        canvas.drawText("SUBJECT & CLASS", 240f, topY + 14f, lbl)
        canvas.drawText("${exam.subjectName.take(18)} • ${exam.className.take(16)}", 240f, topY + 26f, valBold)

        canvas.drawText("DATE / MAX MARKS", 435f, topY + 14f, lbl)
        canvas.drawText("${exam.examDate} (${formatScore(exam.totalMarks)})", 435f, topY + 26f, valBold)

        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderGray; strokeWidth = 0.8f }
        canvas.drawLine(40f, topY + 36f, PAGE_WIDTH - 40f, topY + 36f, divPaint)

        canvas.drawText("TOTAL / GRADED", 44f, topY + 52f, lbl)
        canvas.drawText("$gradedCount / $totalStudents Students", 44f, topY + 66f, valBold)

        canvas.drawText("PASS / FAIL / ABSENT", 175f, topY + 52f, lbl)
        canvas.drawText("$passedCount Pass  •  $failedCount Fail  •  $absentCount AB", 175f, topY + 66f, valBold)

        canvas.drawText("CLASS AVERAGE", 345f, topY + 52f, lbl)
        canvas.drawText(String.format(Locale.US, "%.1f%%", avgPct), 345f, topY + 66f, emVal)

        canvas.drawText("TOP SCORE (PASS: ${formatScore(exam.passingMarks)})", 435f, topY + 52f, lbl)
        canvas.drawText("${formatScore(highest)} / ${formatScore(exam.totalMarks)}", 435f, topY + 66f, emVal)

        return boxBottom
    }

    private fun drawExamReportTable(
        canvas: Canvas,
        rows: List<ExamReportRow>,
        topY: Float
    ): Float {
        val tableLeft = 32f
        val tableRight = PAGE_WIDTH - 32f
        val rowHeight = 20f

        val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldRoyal; style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(tableLeft, topY, tableRight, topY + 20f), 4f, 4f, headerBg)

        val hPaint = TextPaintHelper.bold(8f, Color.WHITE)
        canvas.drawText("POS", 38f, topY + 13f, hPaint)
        canvas.drawText("REG NO.", 68f, topY + 13f, hPaint)
        canvas.drawText("STUDENT NAME", 136f, topY + 13f, hPaint)
        canvas.drawText("MARKS", 278f, topY + 13f, hPaint)
        canvas.drawText("%", 346f, topY + 13f, hPaint)
        canvas.drawText("GRADE", 382f, topY + 13f, hPaint)
        canvas.drawText("STATUS", 464f, topY + 13f, hPaint)
        canvas.drawText("REMARKS", 510f, topY + 13f, hPaint)

        var currentY = topY + 20f
        if (rows.isEmpty()) {
            val emptyPaint = TextPaintHelper.regular(11f, inkMuted, Paint.Align.CENTER)
            canvas.drawText("No student marks recorded for this examination yet.", PAGE_WIDTH / 2f, currentY + 36f, emptyPaint)
            return currentY + 60f
        }

        val altRowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slateBg; style = Paint.Style.FILL }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 0.8f }
        val cellRegular = TextPaintHelper.regular(8.2f, inkDark)
        val cellBold = TextPaintHelper.bold(8.2f, inkDark)

        rows.forEachIndexed { idx, row ->
            val rBottom = currentY + rowHeight
            if (idx % 2 == 1) {
                canvas.drawRect(tableLeft, currentY, tableRight, rBottom, altRowBg)
            }
            canvas.drawText(row.rankLabel, 38f, currentY + 13f, cellBold)
            canvas.drawText(row.admissionNumber.take(12), 68f, currentY + 13f, cellRegular)
            canvas.drawText(row.studentName.take(24), 136f, currentY + 13f, cellBold)
            canvas.drawText(row.obtainedText.take(12), 278f, currentY + 13f, cellBold)
            canvas.drawText(row.percentageText, 346f, currentY + 13f, cellRegular)
            canvas.drawText(row.grade.take(15), 382f, currentY + 13f, cellRegular)
            canvas.drawText(row.statusText, 464f, currentY + 13f, TextPaintHelper.bold(8f, row.statusColor))
            canvas.drawText(row.remarks.take(11), 510f, currentY + 13f, TextPaintHelper.regular(7.5f, inkMuted))

            canvas.drawLine(tableLeft, rBottom, tableRight, rBottom, linePaint)
            currentY = rBottom
        }
        return currentY
    }

    private fun drawStampAndSignaturesFooter(
        canvas: Canvas,
        context: Context,
        leftLabel: String,
        leftSub: String,
        rightLabel: String,
        rightSub: String,
        settings: AcademySettings
    ) {
        val sigY = 768f
        val sigLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            strokeWidth = 1.1f
            style = Paint.Style.STROKE
        }
        val sigTitlePaint = TextPaintHelper.bold(9.5f, emeraldDeep, Paint.Align.CENTER)
        val sigSubPaint = TextPaintHelper.regular(8f, inkMuted, Paint.Align.CENTER)
        val sigNamePaint = TextPaintHelper.italic(9f, emeraldMid, Paint.Align.CENTER)

        // Left Signature
        val leftX = 128f
        canvas.drawText(leftSub.take(26), leftX, sigY - 6f, sigNamePaint)
        canvas.drawLine(leftX - 75f, sigY, leftX + 75f, sigY, sigLinePaint)
        canvas.drawText(leftLabel, leftX, sigY + 13f, sigTitlePaint)

        // Center Official Stamp
        val centerX = PAGE_WIDTH / 2f
        val stampY = 746f
        val stampHalf = 36f
        val stampBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.academy_official_stamp)
        }.getOrNull()
        if (stampBitmap != null) {
            val destRect = RectF(centerX - stampHalf, stampY - stampHalf, centerX + stampHalf, stampY + stampHalf)
            val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(stampBitmap, null, destRect, stampPaint)
        } else {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = goldRoyal
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawCircle(centerX, stampY, 30f, ringPaint)
            canvas.drawText("HQA SEAL", centerX, stampY + 3f, sigTitlePaint)
        }

        // Right Signature
        val rightX = PAGE_WIDTH - 128f
        canvas.drawText(settings.onlineAccountTitle, rightX, sigY - 6f, sigNamePaint)
        canvas.drawLine(rightX - 75f, sigY, rightX + 75f, sigY, sigLinePaint)
        canvas.drawText(rightLabel, rightX, sigY + 13f, sigTitlePaint)
        canvas.drawText(rightSub.take(28), rightX, sigY + 24f, sigSubPaint)

        // Bottom Address Strip strictly above inner bottom border (y = 819)
        val addrPaint = TextPaintHelper.regular(8f, inkMuted, Paint.Align.CENTER)
        canvas.drawText(
            "${settings.address}  •  Tel: ${settings.phone}  •  Email: ${settings.email}",
            centerX,
            811f,
            addrPaint
        )
    }

    private fun drawContinuationFooter(
        canvas: Canvas,
        settings: AcademySettings,
        pageNumber: Int,
        totalPages: Int
    ) {
        val addrPaint = TextPaintHelper.regular(8f, inkMuted, Paint.Align.CENTER)
        canvas.drawText(
            "${settings.academyName} (${settings.address})  •  Page $pageNumber of $totalPages — Continued on next page",
            PAGE_WIDTH / 2f,
            811f,
            addrPaint
        )
    }

    private fun formatScore(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }

    private object TextPaintHelper {
        fun bold(size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.textSize = size
                this.textAlign = align
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

        fun regular(size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.textSize = size
                this.textAlign = align
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

        fun italic(size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.textSize = size
                this.textAlign = align
                this.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
