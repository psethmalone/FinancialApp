package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PaycheckCalculatorTest {

    private val sampleBills = listOf(
        Bill("1", "Rent", 1200.0, 1, "Housing"),
        Bill("2", "Electric", 150.0, 10, "Utilities"),
        Bill("3", "Internet", 75.0, 20, "Utilities")
    )

    private val sampleDebts = listOf(
        Debt("d1", "Credit Card", 5000.0, 5000.0, 0.22, 175.0, DebtType.CREDIT_CARD, 0L),
        Debt("d2", "Auto Loan", 12000.0, 12000.0, 0.05, 300.0, DebtType.LOAN, 0L)
    )

    @Test
    fun testBiWeeklyAllocationMath() {
        // Total bills = 1200 + 150 + 75 = 1425
        // Total debt min = 175 + 300 = 475
        // Total fixed nut = 1900/mo
        // Annual fixed nut = 1900 * 12 = 22,800
        // Bi-weekly paychecks = 26
        // Expected per-paycheck reserve = 22,800 / 26 = 876.923...
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 1800.0,
            nextPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
        )

        val breakdown = PaycheckCalculator.calculateBreakdown(sampleBills, sampleDebts, config)

        assertEquals(1425.0, breakdown.totalMonthlyBills, 0.01)
        assertEquals(475.0, breakdown.totalMonthlyDebtMinimums, 0.01)
        assertEquals(1900.0, breakdown.totalMonthlyFixedNut, 0.01)
        assertEquals(22800.0, breakdown.annualFixedNut, 0.01)
        assertEquals(876.92, breakdown.perPaycheckReserve, 0.05)
        assertEquals(1800.0 - 876.923, breakdown.perPaycheckSafeToSpend, 0.05)
        assertTrue(breakdown.isSurplus)
        assertEquals(0.0, breakdown.deficitAmount, 0.01)
        assertEquals((1800.0 - 876.923) / 14.0, breakdown.dailySafeToSpend, 0.05)
    }

    @Test
    fun testDeficitDetection() {
        // If take-home ($700) is less than per-paycheck reserve (~$876.92)
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 700.0,
            nextPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
        )

        val breakdown = PaycheckCalculator.calculateBreakdown(sampleBills, sampleDebts, config)

        assertFalse(breakdown.isSurplus)
        assertTrue("Deficit should be positive", breakdown.deficitAmount > 0.0)
        assertEquals(0.0, breakdown.dailySafeToSpend, 0.01)
    }

    @Test
    fun testSemiMonthlyAllocationMath() {
        // 24 paychecks per year
        // 22,800 / 24 = 950.00
        val config = PaycheckConfig(
            frequency = PayFrequency.SEMI_MONTHLY,
            typicalTakeHomePay = 2000.0,
            nextPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
        )

        val breakdown = PaycheckCalculator.calculateBreakdown(sampleBills, sampleDebts, config)

        assertEquals(950.0, breakdown.perPaycheckReserve, 0.01)
        assertEquals(1050.0, breakdown.perPaycheckSafeToSpend, 0.01)
    }

    @Test
    fun testUpcomingBillsInWindow() {
        // Window: May 1 to May 15 (inclusive 1, exclusive 15)
        // Rent is due on 1st, Electric is due on 10th, Internet is due on 20th.
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 15)

        val upcoming = PaycheckCalculator.getUpcomingBills(sampleBills, start, end)

        assertEquals(2, upcoming.size)
        assertEquals("Rent", upcoming[0].name)
        assertEquals("Electric", upcoming[1].name)
    }

    @Test
    fun testThreePaycheckMonthsDetection() {
        // Any 26-paycheck schedule in a normal 365-day year has exactly 2 three-paycheck months
        val anchor = LocalDate.of(2026, 1, 2)
        val threePayMonths = PaycheckCalculator.findThreePaycheckMonths(2026, anchor)

        assertEquals("Should have exactly 2 three-paycheck months in a calendar year", 2, threePayMonths.size)
    }
}
