package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.models.AcademySettings
import com.example.data.models.FeeChallan
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

object ChallanPdfGenerator {

    const val PAGE_WIDTH = 842
    const val PAGE_HEIGHT = 595

    fun generateThreeCopyChallanPdf(
        context: Context,
        challan: FeeChallan,
        settings: AcademySettings = AcademySettings()
    ): File {
        // Standard A4 in landscape at 72dpi: 842 x 595 points
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        renderThreeCopyChallanCanvas(page.canvas, challan, settings, context)
        document.finishPage(page)

        val outputDir = File(context.cacheDir, "challans").apply { if (!exists()) mkdirs() }
        val outputFile = File(outputDir, "Challan_${challan.challanNumber}.pdf")
        FileOutputStream(outputFile).use { fos ->
            document.writeTo(fos)
            fos.flush()
        }
        document.close()

        return outputFile
    }

    fun renderThreeCopyChallanBitmap(
        challan: FeeChallan,
        settings: AcademySettings = AcademySettings(),
        context: Context? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderThreeCopyChallanCanvas(canvas, challan, settings, context)
        return bitmap
    }

    fun renderThreeCopyChallanCanvas(
        canvas: Canvas,
        challan: FeeChallan,
        settings: AcademySettings = AcademySettings(),
        @Suppress("UNUSED_PARAMETER") context: Context? = null
    ) {
        canvas.drawColor(Color.WHITE)

        // Official SBP Raast / EasyPaisa QR payload (03493895409 - MUHAMMAD RIZWAN)
        val officialQrPayload = QrCodeGenerator.buildEasyPaisaPayload(challan, settings)

        val copyTitles = listOf("STUDENT COPY", "OFFICE COPY", "ACADEMY COPY")
        val copyWidth = 260
        val gap = 16
        val startX = 20

        val copyPaints = SetupPaints()

        for (i in 0 until 3) {
            val left = startX + i * (copyWidth + gap)
            drawChallanColumn(
                canvas = canvas,
                left = left.toFloat(),
                width = copyWidth.toFloat(),
                copyTitle = copyTitles[i],
                challan = challan,
                settings = settings,
                officialQrPayload = officialQrPayload,
                paints = copyPaints
            )

            // Draw vertical dashed cut line between copies
            if (i < 2) {
                val cutX = left + copyWidth + (gap / 2f)
                canvas.drawLine(cutX, 20f, cutX, 575f, copyPaints.cutLinePaint)
                canvas.drawText("✂ cut", cutX - 10f, 290f, copyPaints.smallItalicPaint)
            }
        }
    }

    private fun drawChallanColumn(
        canvas: Canvas,
        left: Float,
        width: Float,
        copyTitle: String,
        challan: FeeChallan,
        settings: AcademySettings,
        officialQrPayload: String,
        paints: SetupPaints
    ) {
        val right = left + width
        val padding = 8f

        // Outer Border
        val borderRect = RectF(left, 16f, right, 580f)
        canvas.drawRoundRect(borderRect, 6f, 6f, paints.borderPaint)

        // Header Background (16..64)
        val headerRect = RectF(left, 16f, right, 64f)
        canvas.drawRoundRect(headerRect, 6f, 6f, paints.headerBgPaint)
        canvas.drawRect(left, 48f, right, 64f, paints.headerBgPaint)

        // Academy Title
        canvas.drawText(settings.academyName.uppercase(Locale.US), left + width / 2f, 32f, paints.titlePaint)
        canvas.drawText("حفظ قرآن اکیڈمی • " + settings.tagline, left + width / 2f, 44f, paints.subtitlePaint)
        canvas.drawText("EasyPaisa: ${settings.easyPaisaNumber} • JazzCash: ${settings.jazzCashNumber}", left + width / 2f, 56f, paints.contactPaint)

        // Copy Title Banner (68..84)
        val bannerRect = RectF(left + 16f, 68f, right - 16f, 84f)
        canvas.drawRoundRect(bannerRect, 4f, 4f, paints.copyBannerPaint)
        canvas.drawText(copyTitle, left + width / 2f, 79f, paints.copyTitlePaint)

        // Challan Metadata (compact 12pt spacing)
        var y = 97f
        canvas.drawText("Challan No:", left + padding, y, paints.labelBoldPaint)
        canvas.drawText(challan.challanNumber, right - padding, y, paints.valueBoldRightPaint)

        y += 12f
        canvas.drawText("Billing Month:", left + padding, y, paints.labelPaint)
        canvas.drawText("${challan.billingMonth} ${challan.billingYear} (Issued: ${challan.issueDate})", right - padding, y, paints.valueRightPaint)

        y += 12f
        canvas.drawText("Due Date:", left + padding, y, paints.labelBoldPaint)
        canvas.drawText(challan.dueDate, right - padding, y, paints.dueDatePaint)

        // Separator
        y += 6f
        canvas.drawLine(left + padding, y, right - padding, y, paints.dividerPaint)

        // Student Details
        y += 12f
        canvas.drawText("Student Name:", left + padding, y, paints.labelPaint)
        val shortStudent = if (challan.studentName.length > 24) challan.studentName.take(22) + ".." else challan.studentName
        canvas.drawText(shortStudent, right - padding, y, paints.valueBoldRightPaint)

        y += 12f
        canvas.drawText("Admission No:", left + padding, y, paints.labelPaint)
        canvas.drawText(challan.admissionNumber, right - padding, y, paints.valueRightPaint)

        y += 12f
        canvas.drawText("Class / Halaqah:", left + padding, y, paints.labelPaint)
        val shortClass = if (challan.className.length > 22) challan.className.take(20) + ".." else challan.className
        canvas.drawText(shortClass, right - padding, y, paints.valueRightPaint)

        // Fee Table Header
        y += 10f
        val tableHeaderRect = RectF(left + padding, y, right - padding, y + 14f)
        canvas.drawRect(tableHeaderRect, paints.tableHeaderPaint)
        canvas.drawText("PARTICULARS", left + padding + 4f, y + 10f, paints.tableHeaderText)
        canvas.drawText("AMOUNT (PKR)", right - padding - 4f, y + 10f, paints.tableHeaderTextRight)

        val formatter = NumberFormat.getNumberInstance(Locale.US)

        y += 24f
        drawRow(canvas, "Monthly Tuition Fee", "Rs. ${formatter.format(challan.monthlyTuitionFee)}", left, right, padding, y, paints)

        if (challan.additionalCharges > 0) {
            y += 12f
            drawRow(canvas, "Additional Charges", "Rs. ${formatter.format(challan.additionalCharges)}", left, right, padding, y, paints)
        }

        if (challan.fsAssistanceAmount > 0) {
            y += 12f
            drawRow(canvas, "Fee Sabilillah (FS) Waiver", "- Rs. ${formatter.format(challan.fsAssistanceAmount)}", left, right, padding, y, paints, isNegative = true)
        }

        if (challan.discountAmount > 0) {
            y += 12f
            drawRow(canvas, "Approved Discount", "- Rs. ${formatter.format(challan.discountAmount)}", left, right, padding, y, paints, isNegative = true)
        }

        if (challan.previousBalance > 0) {
            y += 12f
            drawRow(canvas, "Previous Arrears", "Rs. ${formatter.format(challan.previousBalance)}", left, right, padding, y, paints)
        }

        // Total Payable Highlight Box
        y += 8f
        val payableBox = RectF(left + padding, y, right - padding, y + 20f)
        canvas.drawRoundRect(payableBox, 4f, 4f, paints.payableBoxPaint)
        canvas.drawText("TOTAL PAYABLE:", left + padding + 6f, y + 13.5f, paints.payableLabel)
        canvas.drawText("Rs. ${formatter.format(challan.totalPayable)}", right - padding - 6f, y + 13.5f, paints.payableValue)

        y += 30f
        drawRow(canvas, "Amount Paid:", "Rs. ${formatter.format(challan.amountPaid)}", left, right, padding, y, paints)

        y += 12f
        drawRow(canvas, "Remaining Due (${challan.status.name}):", "Rs. ${formatter.format(challan.remainingBalance)}", left, right, padding, y, paints, isBold = true)

        // --- OFFICIAL EASYPAISA / RAAST QR & ACCOUNT DETAILS SECTION ---
        val qrBoxTop = maxOf(y + 10f, 326f)
        val qrBoxBottom = 532f
        val qrBoxRect = RectF(left + padding, qrBoxTop, right - padding, qrBoxBottom)
        canvas.drawRoundRect(qrBoxRect, 5f, 5f, paints.qrSectionBgPaint)
        canvas.drawRoundRect(qrBoxRect, 5f, 5f, paints.qrSectionBorderPaint)

        // QR Box Header Strip
        val qrHeaderRect = RectF(left + padding, qrBoxTop, right - padding, qrBoxTop + 15f)
        canvas.drawRoundRect(qrHeaderRect, 5f, 5f, paints.headerBgPaint)
        canvas.drawRect(left + padding, qrBoxTop + 8f, right - padding, qrBoxTop + 15f, paints.headerBgPaint)
        canvas.drawText("ONLINE PAYMENT • SCAN OFFICIAL QR CODE", left + width / 2f, qrBoxTop + 10.5f, paints.qrBannerTitlePaint)

        // Sub-caption
        canvas.drawText(
            "Scan via EasyPaisa, JazzCash (Raast), or Any Banking App",
            left + width / 2f,
            qrBoxTop + 25f,
            paints.qrSubtitlePaint
        )

        // Left Half: Large Official EasyPaisa / Raast QR Code (104 x 104 pt)
        val qrSize = 104f
        val qrLeft = left + padding + 6f
        val qrTop = qrBoxTop + 32f
        val qrCenterX = qrLeft + qrSize / 2f

        val qrRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
        QrCodeGenerator.drawQrVector(canvas, officialQrPayload, qrRect)
        canvas.drawRect(qrRect, paints.qrBorderPaint)

        canvas.drawText(
            settings.onlineAccountTitle,
            qrCenterX,
            qrTop + qrSize + 9.5f,
            paints.qrCaptionPaint
        )
        val qrSubLabel = if (challan.remainingBalance > 0.0) {
            "Scan & Enter: Rs. ${formatter.format(challan.remainingBalance)}"
        } else {
            "EasyPaisa / Raast QR"
        }
        canvas.drawText(
            qrSubLabel,
            qrCenterX,
            qrTop + qrSize + 18f,
            paints.payableQrSubCaptionPaint
        )

        // Right Half: EasyPaisa & JazzCash Account Numbers
        val detailsLeft = qrLeft + qrSize + 8f
        val detailsRight = right - padding - 5f
        val detailsCenterX = (detailsLeft + detailsRight) / 2f

        // 1. EasyPaisa Account Block
        val epPillTop = qrTop + 2f
        val epPill = RectF(detailsLeft + 4f, epPillTop, detailsRight - 4f, epPillTop + 13f)
        canvas.drawRoundRect(epPill, 3f, 3f, paints.easyPaisaBadgePaint)
        canvas.drawText("EASYPAISA (QR)", detailsCenterX, epPillTop + 9.5f, paints.walletBadgeTextPaint)

        canvas.drawText(settings.easyPaisaNumber, detailsCenterX, epPillTop + 24f, paints.accountNumberPaint)
        canvas.drawText("Title: ${settings.onlineAccountTitle}", detailsCenterX, epPillTop + 34f, paints.qrSmallSubCaptionPaint)

        // Divider between EasyPaisa and JazzCash
        val midDivY = epPillTop + 42f
        canvas.drawLine(detailsLeft + 4f, midDivY, detailsRight - 4f, midDivY, paints.dividerPaint)

        // 2. JazzCash Account Block
        val jcPillTop = midDivY + 7f
        val jcPill = RectF(detailsLeft + 4f, jcPillTop, detailsRight - 4f, jcPillTop + 13f)
        canvas.drawRoundRect(jcPill, 3f, 3f, paints.jazzCashBadgePaint)
        canvas.drawText("JAZZCASH A/C", detailsCenterX, jcPillTop + 9.5f, paints.walletBadgeTextPaint)

        canvas.drawText(settings.jazzCashNumber, detailsCenterX, jcPillTop + 24f, paints.accountNumberPaint)
        canvas.drawText("Send Money / Raast ID", detailsCenterX, jcPillTop + 34f, paints.qrSmallSubCaptionPaint)

        // Amount Reminder
        canvas.drawText(
            "Pay: Rs. ${formatter.format(challan.remainingBalance)}",
            detailsCenterX,
            jcPillTop + 49f,
            paints.payableRightMiniPaint
        )

        val footerNoteY = qrBoxBottom - 6.5f
        canvas.drawText(
            "Keep TID after transfer • Challan #${challan.challanNumber}",
            left + width / 2f,
            footerNoteY,
            paints.smallCenteredPaint
        )

        // Signature Lines (556f)
        val sigY = 556f
        canvas.drawLine(left + padding + 8f, sigY, left + padding + 82f, sigY, paints.signatureLinePaint)
        canvas.drawText("Depositor / TID", left + padding + 45f, sigY + 10f, paints.signLabel)

        canvas.drawLine(right - padding - 88f, sigY, right - padding - 8f, sigY, paints.signatureLinePaint)
        canvas.drawText(settings.authorizedSignatory, right - padding - 48f, sigY + 10f, paints.signLabel)
    }

    private fun drawRow(
        canvas: Canvas,
        label: String,
        value: String,
        left: Float,
        right: Float,
        padding: Float,
        y: Float,
        paints: SetupPaints,
        isNegative: Boolean = false,
        isBold: Boolean = false
    ) {
        val labelP = if (isBold) paints.labelBoldPaint else paints.labelPaint
        val valueP = when {
            isNegative -> paints.negativeValueRightPaint
            isBold -> paints.valueBoldRightPaint
            else -> paints.valueRightPaint
        }
        canvas.drawText(label, left + padding, y, labelP)
        canvas.drawText(value, right - padding, y, valueP)
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.hafizquran.acdm.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Challan PDF"))
    }

    fun printOrOpenPdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.hafizquran.acdm.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            sharePdf(context, file)
        }
    }

    private class SetupPaints {
        val borderPaint = Paint().apply {
            color = Color.parseColor("#0F6841")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#0F6841")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#FFDF9B")
            textSize = 6.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val contactPaint = Paint().apply {
            color = Color.parseColor("#E0E4DF")
            textSize = 6.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val copyBannerPaint = Paint().apply {
            color = Color.parseColor("#916B00")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val copyTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 7.2f
            isAntiAlias = true
        }
        val labelBoldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val valueRightPaint = Paint().apply {
            color = Color.parseColor("#222222")
            textSize = 7.2f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val valueBoldRightPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val negativeValueRightPaint = Paint().apply {
            color = Color.parseColor("#0F6841")
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val dueDatePaint = Paint().apply {
            color = Color.parseColor("#C62828")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val tableHeaderPaint = Paint().apply {
            color = Color.parseColor("#EAEAEA")
            style = Paint.Style.FILL
        }
        val tableHeaderText = Paint().apply {
            color = Color.parseColor("#444444")
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val tableHeaderTextRight = Paint().apply {
            color = Color.parseColor("#444444")
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val payableBoxPaint = Paint().apply {
            color = Color.parseColor("#DFF3E8")
            style = Paint.Style.FILL
        }
        val payableLabel = Paint().apply {
            color = Color.parseColor("#0F6841")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val payableValue = Paint().apply {
            color = Color.parseColor("#0F6841")
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val qrSectionBgPaint = Paint().apply {
            color = Color.parseColor("#F5FBF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val qrSectionBorderPaint = Paint().apply {
            color = Color.parseColor("#0F6841")
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            isAntiAlias = true
        }
        val qrBorderPaint = Paint().apply {
            color = Color.parseColor("#15803D")
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        val qrBannerTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val qrSubtitlePaint = Paint().apply {
            color = Color.parseColor("#1E3A2F")
            textSize = 5.9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val jazzCashBadgePaint = Paint().apply {
            color = Color.parseColor("#B91C1C")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val easyPaisaBadgePaint = Paint().apply {
            color = Color.parseColor("#15803D")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val walletBadgeTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 6.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val accountNumberPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.4f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val payableRightMiniPaint = Paint().apply {
            color = Color.parseColor("#0F6841")
            textSize = 7.0f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val whiteFillPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val qrBitmapPaint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        val qrCaptionPaint = Paint().apply {
            color = Color.BLACK
            textSize = 6.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val qrSmallSubCaptionPaint = Paint().apply {
            color = Color.parseColor("#444444")
            textSize = 5.6f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val payableQrSubCaptionPaint = Paint().apply {
            color = Color.parseColor("#0F6841")
            textSize = 5.9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val cutLinePaint = Paint().apply {
            color = Color.parseColor("#888888")
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }
        val smallItalicPaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }
        val smallCenteredPaint = Paint().apply {
            color = Color.parseColor("#555555")
            textSize = 5.6f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val signatureLinePaint = Paint().apply {
            color = Color.parseColor("#444444")
            strokeWidth = 0.8f
        }
        val signLabel = Paint().apply {
            color = Color.parseColor("#444444")
            textSize = 6.2f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
}
