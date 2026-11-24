package com.example.financehome.domain

import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtMultiPayoffTest {

    private val debtHighApr = Debt(
        id = "d1",
        name = "High APR Credit Card",
        originalBalance = 2000.0,
        currentBalance = 2000.0,
        apr = 0.24, // 24%
        minimumPayment = 50.0,
        type = DebtType.CREDIT_CARD,
        createdAtMillis = System.currentTimeMillis()
    )

    private val debtLowBalance = Debt(
        id = "d2",
        name = "Small Medical Bill",
        originalBalance = 500.0,
        currentBalance = 500.0,
        apr = 0.08, // 8%
        minimumPayment = 25.0,
        type = DebtType.OTHER,
        createdAtMillis = System.currentTimeMillis()
    )

    private val debtCarLoan = Debt(
        id = "d3",
        name = "Car Loan",
        originalBalance = 8000.0,
        currentBalance = 8000.0,
        apr = 0.06, // 6%
        minimumPayment = 180.0,
        type = DebtType.LOAN,
        createdAtMillis = System.currentTimeMillis()
    )

    @Test
    fun testAvalanche_targetsHighestAprFirst() {
        val debts = listOf(debtLowBalance, debtHighApr, debtCarLoan)
        val result = DebtCalculator.simulateStrategy(
            debts = debts,
            extraMonthlyPayment = 150.0,
            strategy = PayoffStrategy.AVALANCHE
        )

        assertTrue(result.totalMonths > 0)
        assertTrue(result.milestones.isNotEmpty())

        // Under Avalanche, the 24% card receives the extra $150 and should be the primary acceleration target
        val highAprMilestone = result.milestones.first { it.debtId == "d1" }
        // High APR card finishes early because extra payments are focused on it
        assertTrue(highAprMilestone.payoffMonth < 20)
    }

    @Test
    fun testSnowball_targetsLowestBalanceFirst() {
        val debts = listOf(debtLowBalance, debtHighApr, debtCarLoan)
        val result = DebtCalculator.simulateStrategy(
            debts = debts,
            extraMonthlyPayment = 150.0,
            strategy = PayoffStrategy.SNOWBALL
        )

        assertTrue(result.totalMonths > 0)
        assertTrue(result.milestones.isNotEmpty())

        // Under Snowball, smallest balance ($500) receives the extra $150 and MUST be paid off first
        val firstEliminated = result.milestones.first()
        assertEquals("d2", firstEliminated.debtId)
        assertEquals("Small Medical Bill", firstEliminated.debtName)
        // With $25 min + $150 extra = $175/mo against $500 balance, pays off in ~3 months!
        assertTrue(firstEliminated.payoffMonth <= 4)
    }

    @Test
    fun testMultiDebtComparison_calculatesSavingsVsMinimum() {
        val debts = listOf(debtLowBalance, debtHighApr)
        val comparison = DebtCalculator.compareMultiDebtPayoff(
            debts = debts,
            extraMonthlyPayment = 100.0
        )

        assertTrue(comparison.avalancheResult.totalInterestPaid < comparison.minimumOnlyTotalInterest)
        assertTrue(comparison.avalancheResult.totalInterestSavedVsMinimum > 0.0)
        assertTrue(comparison.snowballResult.totalInterestSavedVsMinimum > 0.0)

        // Avalanche mathematically saves more (or equal) interest than Snowball
        assertTrue(comparison.avalancheResult.totalInterestPaid <= comparison.snowballResult.totalInterestPaid + 0.01)
    }

    @Test
    fun testGetPayDatesForMonth_biWeekly() {
        // Bi-weekly anchor: Jan 2, 2026 (Friday)
        val anchor = LocalDate.of(2026, 1, 2)
        val payDaysInNov = PaycheckCalculator.getPayDatesForMonth(
            year = 2026,
            month = 11,
            anchorPayDate = anchor,
            frequency = PayFrequency.BI_WEEKLY
        )

        // In Nov 2026, paydays every 14 days should be detected correctly
        assertTrue(payDaysInNov.isNotEmpty())
        payDaysInNov.forEach { day ->
            val date = LocalDate.of(2026, 11, day)
            val diff = java.time.temporal.ChronoUnit.DAYS.between(anchor, date)
            assertEquals(0L, diff % 14L)
        }
    }

    @Test
    fun testGetPayDatesForMonth_semiMonthly() {
        // Semi-monthly anchor: 1st of month
        val anchor = LocalDate.of(2026, 1, 1)
        val payDays = PaycheckCalculator.getPayDatesForMonth(
            year = 2026,
            month = 5,
            anchorPayDate = anchor,
            frequency = PayFrequency.SEMI_MONTHLY
        )

        assertEquals(2, payDays.size)
        assertTrue(payDays.contains(1))
        assertTrue(payDays.contains(16))
    }

    @Test
    fun testGetPayDatesForMonth_monthly() {
        // Monthly anchor: 15th
        val anchor = LocalDate.of(2026, 1, 15)
        val payDays = PaycheckCalculator.getPayDatesForMonth(
            year = 2026,
            month = 8,
            anchorPayDate = anchor,
            frequency = PayFrequency.MONTHLY
        )

        assertEquals(1, payDays.size)
        assertTrue(payDays.contains(15))
    }
}
