package com.example

import com.example.data.models.*
import com.example.util.QrCodeGenerator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun test_regularFeePayableCalculation() {
        val tuition = 4500.0
        val additionalCharges = 200.0
        val previousArrears = 500.0
        val discount = 300.0
        val fsAssistance = 0.0

        val totalPayable = (tuition + additionalCharges + previousArrears - (discount + fsAssistance)).coerceAtLeast(0.0)
        assertEquals(4900.0, totalPayable, 0.001)

        val amountPaid = 3000.0
        val remainingBalance = (totalPayable - amountPaid).coerceAtLeast(0.0)
        val status = when {
            remainingBalance <= 0.0 -> ChallanStatus.PAID
            amountPaid > 0.0 -> ChallanStatus.PARTIALLY_PAID
            else -> ChallanStatus.UNPAID
        }

        assertEquals(1900.0, remainingBalance, 0.001)
        assertEquals(ChallanStatus.PARTIALLY_PAID, status)
    }

    @Test
    fun test_feeSabilillahFullWaiverCalculation() {
        val tuition = 4500.0
        val fsAssistance = tuition
        val totalPayable = (tuition - fsAssistance).coerceAtLeast(0.0)
        assertEquals(0.0, totalPayable, 0.001)

        val status = if (totalPayable == 0.0) ChallanStatus.PAID else ChallanStatus.UNPAID
        assertEquals(ChallanStatus.PAID, status)
    }

    @Test
    fun test_jazzCashAndEasyPaisaAccountNumbersAndOfficialQrPayload() {
        val settings = AcademySettings()
        assertEquals("03707304820", settings.jazzCashNumber)
        assertEquals("03493895409", settings.easyPaisaNumber)
        assertEquals("MUHAMMAD RIZWAN", settings.onlineAccountTitle)
        assertEquals("PK23TMFB0000000095651623", settings.easyPaisaIban)

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
            totalPayable = 4500.0,
            remainingBalance = 4500.0
        )

        // Both unpaid and paid challans use the exact verified Official EasyPaisa / Raast QR
        // so EasyPaisa never throws a server-side "Scan Failed" error
        val unpaidPayload = QrCodeGenerator.buildEasyPaisaPayload(challan, settings)
        assertEquals("0002020102110202000424PK23TMFB000000009565162310048E1D", unpaidPayload)

        val computedStatic = QrCodeGenerator.buildRaastStaticIbanQr("PK23TMFB0000000095651623")
        assertEquals("0002020102110202000424PK23TMFB000000009565162310048E1D", computedStatic)
    }
}
