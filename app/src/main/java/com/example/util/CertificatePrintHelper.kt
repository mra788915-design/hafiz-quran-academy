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
import com.example.data.models.AcademySettings
import com.example.data.models.Student
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

object CertificatePrintHelper {

    // Standard ISO A4 Landscape in PostScript points (72 dpi): 842 x 595
    const val PAGE_WIDTH = 842
    const val PAGE_HEIGHT = 595

    private const val FILE_PROVIDER_AUTHORITY = "com.aistudio.hafizquran.acdm.fileprovider"

    fun buildDefaultCertificateSerial(student: Student): String {
        if (student.certificateNumber.isNotBlank()) return student.certificateNumber
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val suffix = student.admissionNumber
            .replace("[^A-Za-z0-9]".toRegex(), "")
            .takeLast(5)
            .ifBlank { "1001" }
        return "HQA-SANAD-$year-$suffix"
    }

    fun generateCertificatePdf(
        context: Context,
        student: Student,
        settings: AcademySettings = AcademySettings()
    ): File {
        val outputDir = File(context.cacheDir, "certificates").apply { if (!exists()) mkdirs() }
        val safeAdm = student.admissionNumber.replace("[^A-Za-z0-9_-]".toRegex(), "_")
        val outputFile = File(outputDir, "Sanad_${safeAdm}.pdf")

        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            renderCertificateCanvas(page.canvas, student, settings)
            document.finishPage(page)
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
            document.close()
        } catch (e: IllegalStateException) {
            runCatching { document.close() }
            val bitmap = renderCertificateBitmap(student, settings, scale = 2f)
            writeBitmapAsPdf14(outputFile, bitmap)
        }
        return outputFile
    }

    private fun writeBitmapAsPdf14(outputFile: File, bitmap: Bitmap) {
        val imgStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, imgStream)
        val jpegBytes = imgStream.toByteArray()

        FileOutputStream(outputFile).use { fos ->
            var offset = 0
            val offsets = IntArray(6)
            fun writeAscii(text: String) {
                val bytes = text.toByteArray(Charsets.US_ASCII)
                fos.write(bytes)
                offset += bytes.size
            }

            writeAscii("%PDF-1.4\n")
            offsets[1] = offset
            writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            offsets[2] = offset
            writeAscii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
            val contentStream = "q $PAGE_WIDTH 0 0 $PAGE_HEIGHT 0 0 cm /Im0 Do Q\n"
            offsets[3] = offset
            writeAscii(
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                    "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
            )
            offsets[4] = offset
            writeAscii(
                "4 0 obj\n<< /Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
            )
            fos.write(jpegBytes)
            offset += jpegBytes.size
            writeAscii("\nendstream\nendobj\n")
            offsets[5] = offset
            writeAscii("5 0 obj\n<< /Length ${contentStream.length} >>\nstream\n$contentStream\nendstream\nendobj\n")
            val xrefOffset = offset
            writeAscii("xref\n0 6\n0000000000 65535 f \n")
            for (i in 1..5) {
                writeAscii(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
            }
            writeAscii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
            fos.flush()
        }
    }

    fun renderCertificateBitmap(
        student: Student,
        settings: AcademySettings = AcademySettings(),
        scale: Float = 2f
    ): Bitmap {
        val widthPx = (PAGE_WIDTH * scale).toInt().coerceAtLeast(PAGE_WIDTH)
        val heightPx = (PAGE_HEIGHT * scale).toInt().coerceAtLeast(PAGE_HEIGHT)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(scale, scale)
        renderCertificateCanvas(canvas, student, settings)
        canvas.restore()
        return bitmap
    }

    fun printCertificate(
        context: Context,
        student: Student,
        settings: AcademySettings = AcademySettings()
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            shareCertificatePdf(context, student, settings)
            return
        }

        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service unavailable. Opening PDF instead...", Toast.LENGTH_SHORT).show()
            shareCertificatePdf(context, student, settings)
            return
        }

        val safeName = student.fullName.replace("[^A-Za-z0-9 ]".toRegex(), "").trim().replace("\\s+".toRegex(), "_")
        val jobName = "HQA_Sanad_${safeName.ifBlank { student.admissionNumber }}"

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

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
                    .setPageCount(1)
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
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    renderCertificateCanvas(page.canvas, student, settings)
                    pdfDoc.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                        out.flush()
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Failed to write certificate PDF.")
                } finally {
                    pdfDoc.close()
                }
            }
        }

        printManager.print(jobName, adapter, printAttributes)
    }

    fun shareCertificatePdf(
        context: Context,
        student: Student,
        settings: AcademySettings = AcademySettings()
    ) {
        try {
            val file = generateCertificatePdf(context, student, settings)
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Course Completion Certificate (Sanad-e-Takmeel) - ${student.fullName}"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "MashaAllah! Official Course Completion Certificate (Sanad-e-Takmeel) for ${student.fullName} (${student.admissionNumber}) from ${settings.academyName}."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share / Save Certificate PDF").apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun renderCertificateCanvas(
        canvas: Canvas,
        student: Student,
        settings: AcademySettings = AcademySettings()
    ) {
        val w = PAGE_WIDTH.toFloat()
        val h = PAGE_HEIGHT.toFloat()
        val centerX = w / 2f

        val emeraldDeep = Color.parseColor("#0B4F32")
        val emeraldMid = Color.parseColor("#136F48")
        val goldRoyal = Color.parseColor("#C59B27")
        val goldSoft = Color.parseColor("#E6C86E")
        val ivoryBg = Color.parseColor("#FFFCF5")
        val parchmentTint = Color.parseColor("#FAF3E0")
        val inkDark = Color.parseColor("#1C2520")
        val inkMuted = Color.parseColor("#4B5563")

        // 1. Warm Ivory Parchment Background
        canvas.drawColor(ivoryBg)

        // Subtle inner parchment panel
        val innerPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parchmentTint
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(28f, 28f, w - 28f, h - 28f), 12f, 12f, innerPanelPaint)

        // Soft watermark geometric medallion in center
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(14, 197, 155, 39)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        drawEightPointStar(canvas, centerX, h / 2f + 10f, 130f, watermarkPaint)
        canvas.drawCircle(centerX, h / 2f + 10f, 95f, watermarkPaint)

        // 2. Ornate Islamic Emerald & Royal Gold Borders
        val outerEmeraldBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRect(18f, 18f, w - 18f, h - 18f, outerEmeraldBorder)

        val goldOuterStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldRoyal
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
        }
        canvas.drawRect(27f, 27f, w - 27f, h - 27f, goldOuterStroke)

        val emeraldFineStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldMid
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRect(33f, 33f, w - 33f, h - 33f, emeraldFineStroke)

        // Corner 8-pointed Islamic Star Medallions
        val cornerFillGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldRoyal
            style = Paint.Style.FILL
        }
        val cornerFillEmerald = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            style = Paint.Style.FILL
        }
        val corners = listOf(
            33f to 33f,
            (w - 33f) to 33f,
            33f to (h - 33f),
            (w - 33f) to (h - 33f)
        )
        corners.forEach { (cx, cy) ->
            drawEightPointStar(canvas, cx, cy, 16f, cornerFillGold)
            drawEightPointStar(canvas, cx, cy, 10f, cornerFillEmerald)
            canvas.drawCircle(cx, cy, 3.5f, cornerFillGold)
        }

        // Top Emerald Header Arch Strip
        val topHeaderRect = RectF(46f, 44f, w - 46f, 128f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(topHeaderRect, 10f, 10f, headerPaint)
        val headerGoldBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldRoyal
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(topHeaderRect, 10f, 10f, headerGoldBorder)

        // Bismillah & Academy Title inside Emerald Header
        val bismillahPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldSoft
            textSize = 18f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", centerX, 68f, bismillahPaint)

        val academyTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(settings.academyName.uppercase(Locale.US), centerX, 96f, academyTitlePaint)

        val urduSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldSoft
            textSize = 13f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("حفظ قرآن اکیڈمی • ${settings.tagline}", centerX, 116f, urduSubtitlePaint)

        // Serial No (Left) & Admission No (Right) row below header
        val serialNo = buildDefaultCertificateSerial(student)
        val metaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        metaLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("SANAD SERIAL NO: $serialNo", 54f, 152f, metaLabelPaint)

        metaLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ADMISSION REG NO: ${student.admissionNumber}", w - 54f, 152f, metaLabelPaint)

        // Gold Ribbon Title: SANAD-E-TAKMEEL • CERTIFICATE OF COMPLETION
        val ribbonRect = RectF(centerX - 245f, 164f, centerX + 245f, 198f)
        val ribbonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldRoyal
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(ribbonRect, 17f, 17f, ribbonBgPaint)

        val ribbonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("SANAD-E-TAKMEEL  •  CERTIFICATE OF COURSE COMPLETION", centerX, 186f, ribbonTextPaint)

        // Formal Citation Intro
        val citationItalicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 14f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText(
            "Alhamdulillah! By the Infinite Grace of Allah Almighty, this is to proudly certify that",
            centerX,
            224f,
            citationItalicPaint
        )

        // Student Name (Prominent Royal Typography)
        val studentNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            textSize = 31f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(student.fullName.uppercase(Locale.US), centerX, 262f, studentNamePaint)

        // Decorative Gold Underline under Student Name
        canvas.drawLine(centerX - 190f, 271f, centerX + 190f, 271f, goldOuterStroke)

        // Parentage Line (S/o or D/o)
        val relationPrefix = if (student.gender.equals("Female", ignoreCase = true)) {
            "Daughter of (Bint-e)"
        } else {
            "Son of (Ibn-e)"
        }
        val parentagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 15.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("$relationPrefix  ${student.guardianName}", centerX, 294f, parentagePaint)

        canvas.drawText(
            "has successfully completed the blessed Islamic & Quranic curriculum of",
            centerX,
            318f,
            citationItalicPaint
        )

        // Course Completed Highlight Box
        val courseTitle = student.completedCourse.ifBlank {
            when {
                student.quranLevel.contains("Hifz", ignoreCase = true) -> "Hifz-ul-Quran Al-Kareem (Complete 30 Juz)"
                student.quranLevel.contains("Nazra", ignoreCase = true) -> "Nazra Quran Majeed with Tajweed"
                student.quranLevel.contains("Qaida", ignoreCase = true) -> "Noorani Qaida & Makharij Foundation"
                else -> "${student.quranLevel} Course"
            }
        }
        val courseBoxRect = RectF(centerX - 280f, 330f, centerX + 280f, 372f)
        val courseBoxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(courseBoxRect, 10f, 10f, courseBoxFill)
        canvas.drawRoundRect(courseBoxRect, 10f, 10f, goldOuterStroke)

        val courseTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            textSize = 20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(courseTitle, centerX, 358f, courseTitlePaint)

        // Grade, Completion Date & Class/Ustadh Metadata Strip
        val completionDate = student.completionDate.ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
        val finalGrade = student.finalGrade.ifBlank { "Mumtaz (Excellent Distinction)" }
        val ustadhName = student.teacherName.ifBlank { "Academy Shura & Asatizah" }

        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 13.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            "Grade / Performance: $finalGrade    •    Date of Completion: $completionDate",
            centerX,
            398f,
            detailPaint
        )

        val subDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 12.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(
            "Halaqah / Department: ${student.className}    •    Supervised by Ustadh: $ustadhName",
            centerX,
            418f,
            subDetailPaint
        )

        // Remarks or Dua / Hadith Quote
        val remarksOrDua = student.certificateRemarks.trim().ifBlank {
            "\"The best among you are those who learn the Noble Quran and teach it to others.\" — Sahih Al-Bukhari"
        }
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldMid
            textSize = 12f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText(remarksOrDua.take(115), centerX, 442f, quotePaint)

        // 3. Bottom Signatures & Concentric Official Gold Seal (y = 460 .. 548)
        val sigLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            strokeWidth = 1.4f
            style = Paint.Style.STROKE
        }
        val sigTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            textSize = 12f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val sigSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 10.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Left Signature: Class Teacher / Ustadh-e-Mohtaram
        val leftSigCenter = 185f
        canvas.drawText(ustadhName, leftSigCenter, 504f, quotePaint)
        canvas.drawLine(leftSigCenter - 95f, 512f, leftSigCenter + 95f, 512f, sigLinePaint)
        canvas.drawText("Ustadh / Halaqah In-Charge", leftSigCenter, 528f, sigTitlePaint)
        canvas.drawText("Department of Tajweed & Hifz", leftSigCenter, 542f, sigSubPaint)

        // Center Official Academy Gold Seal Stamp
        val sealY = 502f
        val sealRadius = 38f
        val sealFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF8DC")
            style = Paint.Style.FILL
        }
        drawEightPointStar(canvas, centerX, sealY, sealRadius + 5f, cornerFillGold)
        canvas.drawCircle(centerX, sealY, sealRadius, sealFillPaint)
        canvas.drawCircle(centerX, sealY, sealRadius, goldOuterStroke)
        canvas.drawCircle(centerX, sealY, sealRadius - 5f, emeraldFineStroke)

        val sealTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDeep
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("OFFICIAL", centerX, sealY - 9f, sealTextPaint)
        canvas.drawText("SANAD SEAL", centerX, sealY + 2f, sealTextPaint)
        val sealSmallGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldRoyal
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("VERIFIED • HQA", centerX, sealY + 13f, sealSmallGold)

        // Right Signature: Principal / Nazim-e-Aala
        val rightSigCenter = w - 185f
        canvas.drawText(settings.onlineAccountTitle, rightSigCenter, 504f, quotePaint)
        canvas.drawLine(rightSigCenter - 95f, 512f, rightSigCenter + 95f, 512f, sigLinePaint)
        canvas.drawText(settings.authorizedSignatory, rightSigCenter, 528f, sigTitlePaint)
        canvas.drawText(settings.academyName, rightSigCenter, 542f, sigSubPaint)

        // Footer Campus Address & Contact Bar
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(
            "${settings.address}  •  Contact: ${settings.phone}  •  Email: ${settings.email}",
            centerX,
            564f,
            footerPaint
        )
    }

    private fun drawEightPointStar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        paint: Paint
    ) {
        val path = Path()
        val innerRadius = radius * 0.54f
        val totalPoints = 16
        for (i in 0 until totalPoints) {
            val r = if (i % 2 == 0) radius else innerRadius
            val angle = Math.PI * 2.0 * i / totalPoints - Math.PI / 2.0
            val px = (cx + r * cos(angle)).toFloat()
            val py = (cy + r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
