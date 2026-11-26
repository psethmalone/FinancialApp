package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.BillOccurrenceEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BillOccurrenceTest {

    @Test
    fun testSingleDateMarkPaidIsolation() {
        val nov1 = LocalDate.of(2026, 11, 1)
        val dec1 = LocalDate.of(2026, 12, 1)
        val jan1 = LocalDate.of(2027, 1, 1)

        val rentBill = Bill(
            id = "rent_bill",
            name = "Apartment Rent",
            amount = 1500.0,
            dueDayOfMonth = 1,
            category = "Housing"
        )

        // Only mark Nov 1 occurrence as paid
        val occurrenceNov = BillOccurrenceEntity(
            billId = rentBill.id,
            dueDateEpochDay = nov1.toEpochDay(),
            isPaid = true,
            paidDateEpochDay = nov1.toEpochDay(),
            matchedTransactionId = "tx_rent_11"
        )

        val occurrences = listOf(occurrenceNov)

        // Helper check matching the app's occurrence check
        fun isCyclePaid(dueDate: LocalDate): Boolean {
            return occurrences.any { it.billId == rentBill.id && it.dueDateEpochDay == dueDate.toEpochDay() && it.isPaid }
        }

        assertTrue("November 1 occurrence should be paid", isCyclePaid(nov1))
        assertFalse("December 1 occurrence MUST remain unpaid", isCyclePaid(dec1))
        assertFalse("January 1 occurrence MUST remain unpaid", isCyclePaid(jan1))
    }

    @Test
    fun testUndoSingleOccurrencePayment() {
        val dueDate = LocalDate.of(2026, 11, 15)
        var occurrence = BillOccurrenceEntity(
            billId = "electric_bill",
            dueDateEpochDay = dueDate.toEpochDay(),
            isPaid = true,
            paidDateEpochDay = dueDate.toEpochDay(),
            matchedTransactionId = "tx_elec_123"
        )

        assertTrue(occurrence.isPaid)
        assertNotNull(occurrence.matchedTransactionId)

        // Undo payment
        occurrence = occurrence.copy(
            isPaid = false,
            paidDateEpochDay = null,
            matchedTransactionId = null
        )

        assertFalse(occurrence.isPaid)
        assertNull(occurrence.paidDateEpochDay)
        assertNull(occurrence.matchedTransactionId)
    }

    @Test
    fun testMultiMonthOutlookWithSettledOccurrence() {
        val today = LocalDate.of(2026, 11, 5)
        val nov10 = LocalDate.of(2026, 11, 10)
        val dec10 = LocalDate.of(2026, 12, 10)

        val bill = Bill(
            id = "internet_bill",
            name = "Internet",
            amount = 80.0,
            dueDayOfMonth = 10,
            category = "Utilities"
        )

        // Settle only Nov 10 occurrence
        val settledNovOccurrence = BillOccurrenceEntity(
            billId = bill.id,
            dueDateEpochDay = nov10.toEpochDay(),
            isPaid = true,
            paidDateEpochDay = today.toEpochDay(),
            matchedTransactionId = "tx_net_99"
        )

        val config = PaycheckConfig(
            frequency = PayFrequency.MONTHLY,
            typicalTakeHomePay = 3000.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay()
        )

        val paidEpochDays = setOf(settledNovOccurrence.dueDateEpochDay)

        // Month 0 (November): Internet is already paid via occurrence, so remaining unpaid bills = 0.0
        val month0 = PaycheckCalculator.calculateMultiMonthOutlook(
            currentBalance = 1000.0,
            bills = listOf(bill),
            debts = emptyList(),
            config = config,
            takeHomePay = 3000.0,
            targetMonthOffset = 0,
            currentDate = today,
            paidOccurrenceEpochDays = paidEpochDays
        )

        assertEquals("Internet is paid for Nov, so remaining bills should be 0", 0.0, month0.remainingBills, 0.01)

        // Month 1 (December): Internet on Dec 10 is NOT paid, so remaining bills = 80.0
        val month1 = PaycheckCalculator.calculateMultiMonthOutlook(
            currentBalance = 1000.0,
            bills = listOf(bill),
            debts = emptyList(),
            config = config,
            takeHomePay = 3000.0,
            targetMonthOffset = 1,
            currentDate = today,
            paidOccurrenceEpochDays = paidEpochDays
        )

        assertEquals("Internet is NOT paid for Dec, so remaining bills should be 80.0", 80.0, month1.remainingBills, 0.01)
    }
}
