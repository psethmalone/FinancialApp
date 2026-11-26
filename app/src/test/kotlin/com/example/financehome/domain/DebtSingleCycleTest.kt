package com.example.financehome.domain

import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DebtSingleCycleTest {

    @Test
    fun testSingleCycleSettlementTracking() {
        val nov15 = LocalDate.of(2026, 11, 15)
        val dec15 = LocalDate.of(2026, 12, 15)
        val jan15 = LocalDate.of(2027, 1, 15)

        val creditCard = Debt(
            id = "cc_debt_1",
            name = "Chase Sapphire",
            originalBalance = 5000.0,
            currentBalance = 3200.0,
            apr = 0.24,
            minimumPayment = 120.0,
            type = DebtType.CREDIT_CARD,
            createdAtMillis = 0L,
            dueDayOfMonth = 15,
            isPaidThisMonth = false,
            nextDueDateEpochDay = 0L,
            lastSettledDateEpochDay = null
        )

        // Initial state
        assertEquals(nov15, creditCard.getEffectiveNextDueDate(LocalDate.of(2026, 11, 1)))
        assertFalse(creditCard.isPaidForDate(nov15, LocalDate.of(2026, 11, 1)))
        assertFalse(creditCard.isPaidForDate(dec15, LocalDate.of(2026, 11, 1)))

        // Settle Nov 15 cycle
        val nextDue = creditCard.advanceDueDate(nov15)
        assertEquals("Next due date should advance by one month to Dec 15", dec15, nextDue)

        val paidDebt = creditCard.copy(
            nextDueDateEpochDay = nextDue.toEpochDay(),
            lastSettledDateEpochDay = nov15.toEpochDay(),
            isPaidThisMonth = true
        )

        // Verify cycle isolation
        assertTrue("November 15 cycle MUST be marked as paid", paidDebt.isPaidForDate(nov15, nov15))
        assertFalse("December 15 cycle MUST remain unpaid", paidDebt.isPaidForDate(dec15, nov15))
        assertFalse("January 15 cycle MUST remain unpaid", paidDebt.isPaidForDate(jan15, nov15))
        assertEquals(dec15, paidDebt.getEffectiveNextDueDate(nov15))
    }

    @Test
    fun testUnmarkDebtPaymentRollback() {
        val nov15 = LocalDate.of(2026, 11, 15)
        val dec15 = LocalDate.of(2026, 12, 15)

        val settledDebt = Debt(
            id = "loan_1",
            name = "Auto Loan",
            originalBalance = 15000.0,
            currentBalance = 10000.0,
            apr = 0.06,
            minimumPayment = 350.0,
            type = DebtType.LOAN,
            createdAtMillis = 0L,
            dueDayOfMonth = 15,
            isPaidThisMonth = true,
            nextDueDateEpochDay = dec15.toEpochDay(),
            lastSettledDateEpochDay = nov15.toEpochDay()
        )

        assertTrue(settledDebt.isPaidForDate(nov15, nov15))
        assertFalse(settledDebt.isPaidForDate(dec15, nov15))

        // Rollback / Unmark payment
        val rolledBack = settledDebt.copy(
            nextDueDateEpochDay = nov15.toEpochDay(),
            lastSettledDateEpochDay = null,
            isPaidThisMonth = false
        )

        assertFalse("Rolled-back cycle for Nov 15 should now be unpaid", rolledBack.isPaidForDate(nov15, nov15))
        assertFalse("Future cycles remain unpaid", rolledBack.isPaidForDate(dec15, nov15))
        assertEquals(nov15, rolledBack.getEffectiveNextDueDate(nov15))
    }

    @Test
    fun testForwardPaycheckScheduleCycleIsolation() {
        val today = LocalDate.of(2026, 11, 10)
        val nov15 = LocalDate.of(2026, 11, 15)
        val dec15 = LocalDate.of(2026, 12, 15)

        // Debt where Nov 15 is settled, next due is Dec 15
        val debt = Debt(
            id = "cc_1",
            name = "Amex",
            originalBalance = 2000.0,
            currentBalance = 1200.0,
            apr = 0.20,
            minimumPayment = 85.0,
            type = DebtType.CREDIT_CARD,
            createdAtMillis = 0L,
            dueDayOfMonth = 15,
            isPaidThisMonth = true,
            nextDueDateEpochDay = dec15.toEpochDay(),
            lastSettledDateEpochDay = nov15.toEpochDay()
        )

        // Window 1: covering Nov 15 (e.g., Paycheck 1: Nov 10 to Nov 23)
        val obligationsPeriod1 = PaycheckCalculator.getScheduledObligationsInWindow(
            bills = emptyList(),
            debts = listOf(debt),
            startDate = LocalDate.of(2026, 11, 10),
            endDate = LocalDate.of(2026, 11, 24),
            currentDate = today
        )

        assertEquals(1, obligationsPeriod1.size)
        val itemPeriod1 = obligationsPeriod1.first()
        assertEquals(nov15, itemPeriod1.dueDate)
        assertTrue("Debt in period 1 (Nov 15) must be marked as paid", itemPeriod1.isPaid)

        // Window 2: covering Dec 15 (e.g., Paycheck 3: Dec 8 to Dec 21)
        val obligationsPeriod2 = PaycheckCalculator.getScheduledObligationsInWindow(
            bills = emptyList(),
            debts = listOf(debt),
            startDate = LocalDate.of(2026, 12, 8),
            endDate = LocalDate.of(2026, 12, 22),
            currentDate = today
        )

        assertEquals(1, obligationsPeriod2.size)
        val itemPeriod2 = obligationsPeriod2.first()
        assertEquals(dec15, itemPeriod2.dueDate)
        assertFalse("Debt in future period 2 (Dec 15) MUST NOT be marked as paid", itemPeriod2.isPaid)
    }

    @Test
    fun testMultiMonthOutlookFutureDebtsUnpaid() {
        val today = LocalDate.of(2026, 11, 5)
        val nov20 = LocalDate.of(2026, 11, 20)
        val dec20 = LocalDate.of(2026, 12, 20)

        val debt = Debt(
            id = "student_loan",
            name = "Student Loan",
            originalBalance = 20000.0,
            currentBalance = 15000.0,
            apr = 0.05,
            minimumPayment = 250.0,
            type = DebtType.LOAN,
            createdAtMillis = 0L,
            dueDayOfMonth = 20,
            isPaidThisMonth = true,
            nextDueDateEpochDay = dec20.toEpochDay(),
            lastSettledDateEpochDay = nov20.toEpochDay()
        )

        val config = PaycheckConfig(
            frequency = PayFrequency.MONTHLY,
            typicalTakeHomePay = 4000.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay()
        )

        // Month 0 (November): Debt is paid, so remaining unpaid liabilities should be 0.0
        val month0 = PaycheckCalculator.calculateMultiMonthOutlook(
            currentBalance = 2000.0,
            bills = emptyList(),
            debts = listOf(debt),
            config = config,
            takeHomePay = 4000.0,
            targetMonthOffset = 0,
            currentDate = today
        )

        assertEquals("November remaining bills/debts should be 0.0 since Nov 20 is paid", 0.0, month0.remainingBills, 0.01)
        val month0DebtEvent = month0.chronologicalEvents.firstOrNull { it.originalDebt?.id == debt.id }
        assertNotNull(month0DebtEvent)
        assertTrue("Month 0 event must be marked paid", month0DebtEvent!!.isPaid)

        // Month 1 (December): Debt is NOT paid, remaining unpaid liabilities should be 250.0
        val month1 = PaycheckCalculator.calculateMultiMonthOutlook(
            currentBalance = 2000.0,
            bills = emptyList(),
            debts = listOf(debt),
            config = config,
            takeHomePay = 4000.0,
            targetMonthOffset = 1,
            currentDate = today
        )

        assertEquals("December remaining bills/debts MUST be 250.0 since Dec 20 is unpaid", 250.0, month1.remainingBills, 0.01)
        val month1DebtEvent = month1.chronologicalEvents.firstOrNull { it.originalDebt?.id == debt.id }
        assertNotNull(month1DebtEvent)
        assertFalse("Month 1 event MUST remain unpaid", month1DebtEvent!!.isPaid)
    }

    @Test
    fun testMonthLengthCappingForAdvancingDueDate() {
        val debt = Debt(
            id = "card_31",
            name = "Card 31",
            originalBalance = 1000.0,
            currentBalance = 500.0,
            apr = 0.15,
            minimumPayment = 50.0,
            type = DebtType.CREDIT_CARD,
            createdAtMillis = 0L,
            dueDayOfMonth = 31
        )

        // Advancing from Jan 31 -> Feb 28 in non-leap year (2026)
        val febDue = debt.advanceDueDate(LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), febDue)

        // Advancing from March 31 -> April 30
        val aprDue = debt.advanceDueDate(LocalDate.of(2026, 3, 31))
        assertEquals(LocalDate.of(2026, 4, 30), aprDue)
    }

    @Test
    fun testMonthlyGlimpseFutureMonthsDoNotMarkRestitutionPaid() {
        // Cars debt: 362.0 unpaid
        val carDebt = Debt(
            id = "car_loans",
            name = "Cars",
            originalBalance = 15000.0,
            currentBalance = 12000.0,
            apr = 0.05,
            minimumPayment = 362.0,
            type = DebtType.LOAN,
            createdAtMillis = 0L,
            dueDayOfMonth = 10,
            isPaidThisMonth = false,
            nextDueDateEpochDay = 0L
        )

        // Restitution debt: 150.0 minimum payment, legacy isPaidThisMonth = true
        val restitutionDebt = Debt(
            id = "restitution_debt",
            name = "Restitution",
            originalBalance = 5000.0,
            currentBalance = 3500.0,
            apr = 0.0,
            minimumPayment = 150.0,
            type = DebtType.OTHER,
            createdAtMillis = 0L,
            dueDayOfMonth = 15,
            isPaidThisMonth = true,
            nextDueDateEpochDay = 0L // Legacy or just settled for this month
        )

        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 2000.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 6).toEpochDay()
        )

        // Current month (November 2026):
        // Restitution is marked as paid for this month, car debt is unpaid (362.0)
        val novGlimpse = PaycheckCalculator.calculateMonthlyGlimpse(
            year = 2026,
            month = 11,
            bills = emptyList(),
            debts = listOf(carDebt, restitutionDebt),
            config = config,
            anchorPayDate = LocalDate.of(2026, 11, 6),
            currentTakeHomePay = 2000.0
        )

        assertEquals("Total debts should include both cars and restitution (362 + 150 = 512)", 512.0, novGlimpse.totalDebts, 0.01)
        assertEquals("November unpaid debts should only be 362.0 because restitution was paid this month", 362.0, novGlimpse.unpaidDebts, 0.01)

        // Future month (December 2026):
        // Restitution MUST NOT be marked paid for December! Unpaid debts MUST be 512.0 (both cars + restitution)!
        val decGlimpse = PaycheckCalculator.calculateMonthlyGlimpse(
            year = 2026,
            month = 12,
            bills = emptyList(),
            debts = listOf(carDebt, restitutionDebt),
            config = config,
            anchorPayDate = LocalDate.of(2026, 11, 6),
            currentTakeHomePay = 2000.0
        )

        assertEquals("December total debts should be 512.0", 512.0, decGlimpse.totalDebts, 0.01)
        assertEquals("December unpaid debts MUST be 512.0 (NOT 362.0). Restitution is NOT paid for future months!", 512.0, decGlimpse.unpaidDebts, 0.01)

        // Future month (January 2027):
        val janGlimpse = PaycheckCalculator.calculateMonthlyGlimpse(
            year = 2027,
            month = 1,
            bills = emptyList(),
            debts = listOf(carDebt, restitutionDebt),
            config = config,
            anchorPayDate = LocalDate.of(2026, 11, 6),
            currentTakeHomePay = 2000.0
        )

        assertEquals("January unpaid debts MUST be 512.0", 512.0, janGlimpse.unpaidDebts, 0.01)
    }
}
