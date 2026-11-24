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
            anchorPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
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
            anchorPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
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
            anchorPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
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
        assertTrue(threePayMonths.contains("January 2026"))
        assertTrue(threePayMonths.contains("July 2026"))
    }

    @Test
    fun testCohortBThreePaycheckMonths() {
        // If anchor is Friday Jan 9, 2026 (or Nov 27, 2026):
        // Pay dates fall on Jan 9, 23; Feb 6, 20; Mar 6, 20; Apr 3, 17; May 1, 15, 29 (3 in May!); Oct 2, 16, 30 (3 in Oct!)
        val anchor = LocalDate.of(2026, 11, 27)
        val threePayMonths = PaycheckCalculator.findThreePaycheckMonths(2026, anchor)

        assertEquals(2, threePayMonths.size)
        assertTrue(threePayMonths.contains("May 2026"))
        assertTrue(threePayMonths.contains("October 2026"))
    }

    @Test
    fun testPayDateAdvancementFromPastAnchor() {
        // If anchor was set to Friday Nov 13, 2026
        // Today is Tuesday Nov 24, 2026
        // Next payday must automatically advance to Friday Nov 27, 2026!
        val anchor = LocalDate.of(2026, 11, 13)
        val today = LocalDate.of(2026, 11, 24)

        val nextPay = PaycheckCalculator.calculateNextPayDate(anchor, PayFrequency.BI_WEEKLY, today)
        assertEquals(LocalDate.of(2026, 11, 27), nextPay)

        // And following payday must be Dec 11, 2026
        val followingPay = PaycheckCalculator.calculateFollowingPayDate(nextPay, PayFrequency.BI_WEEKLY)
        assertEquals(LocalDate.of(2026, 12, 11), followingPay)
    }

    @Test
    fun testScheduledBillsInWindowAndVariance() {
        // Rent (1st: 1200), Electric (10th: 150), Internet (20th: 75)
        // Paycheck window: Nov 27, 2026 to Dec 11, 2026
        // Bills inside window: Rent (Dec 1: $1200) and Electric (Dec 10: $150) -> Total $1350
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 1800.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 27).toEpochDay()
        )
        val today = LocalDate.of(2026, 11, 24)
        val breakdown = PaycheckCalculator.calculateBreakdown(sampleBills, sampleDebts, config, today)

        assertEquals(1350.0, breakdown.windowBillsTotal, 0.01)
        assertEquals(2, breakdown.billsDueInPeriod.size)
        assertEquals("Rent", breakdown.billsDueInPeriod[0].bill.name)
        assertEquals(LocalDate.of(2026, 12, 1), breakdown.billsDueInPeriod[0].dueDate)
        assertEquals("Electric", breakdown.billsDueInPeriod[1].bill.name)
        assertEquals(LocalDate.of(2026, 12, 10), breakdown.billsDueInPeriod[1].dueDate)

        // Steady reserve is $876.92. Window bills is $1350. Variance = 876.92 - 1350 = -473.08 (rent peak!)
        assertEquals(-473.08, breakdown.windowVariance, 0.05)
        assertEquals(3, breakdown.daysUntilPay)
    }

    @Test
    fun testMultiPayPeriodProjection() {
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 1800.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 27).toEpochDay()
        )
        val today = LocalDate.of(2026, 11, 24)
        val breakdown = PaycheckCalculator.calculateBreakdown(sampleBills, sampleDebts, config, today)

        assertEquals(3, breakdown.upcomingPayPeriods.size)

        // Period 1 (Nov 27 - Dec 11): Rent ($1200) + Electric ($150) = $1350
        val p1 = breakdown.upcomingPayPeriods[0]
        assertEquals(1, p1.periodIndex)
        assertEquals(LocalDate.of(2026, 11, 27), p1.payDate)
        assertEquals(LocalDate.of(2026, 12, 11), p1.nextPayDate)
        assertEquals(1350.0, p1.totalBills, 0.01)
        assertEquals(450.0, p1.safeToSpend, 0.01)

        // Period 2 (Dec 11 - Dec 25): Internet ($75) = $75
        val p2 = breakdown.upcomingPayPeriods[1]
        assertEquals(2, p2.periodIndex)
        assertEquals(LocalDate.of(2026, 12, 11), p2.payDate)
        assertEquals(LocalDate.of(2026, 12, 25), p2.nextPayDate)
        assertEquals(75.0, p2.totalBills, 0.01)
        assertEquals(1725.0, p2.safeToSpend, 0.01)
    }
}
