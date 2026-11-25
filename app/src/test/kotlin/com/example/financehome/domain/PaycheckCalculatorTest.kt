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

        // Period 2 (Dec 11 - Dec 25): Internet ($75) + Credit Card ($175) + Auto Loan ($300) = $550 total obligations
        val p2 = breakdown.upcomingPayPeriods[1]
        assertEquals(2, p2.periodIndex)
        assertEquals(LocalDate.of(2026, 12, 11), p2.payDate)
        assertEquals(LocalDate.of(2026, 12, 25), p2.nextPayDate)
        assertEquals(550.0, p2.totalBills, 0.01)
        assertEquals(550.0, p2.totalObligations, 0.01)
        assertEquals(1250.0, p2.safeToSpend, 0.01)
    }

    @Test
    fun testDebtsDueInPaycheckWindow() {
        val bills = listOf(
            Bill("1", "Rent", 1000.0, 1, "Housing")
        )
        val debts = listOf(
            // Due on 5th (inside Nov 27 - Dec 11 window on Dec 5)
            Debt("d1", "Credit Card", 2000.0, 2000.0, 0.20, 150.0, DebtType.CREDIT_CARD, 0L, dueDayOfMonth = 5),
            // Due on 20th (outside window)
            Debt("d2", "Student Loan", 8000.0, 8000.0, 0.06, 200.0, DebtType.LOAN, 0L, dueDayOfMonth = 20),
            // Paid off debt (balance 0, should not appear in window obligations)
            Debt("d3", "Paid Off Loan", 5000.0, 0.0, 0.05, 100.0, DebtType.LOAN, 0L, dueDayOfMonth = 5)
        )

        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 1800.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 27).toEpochDay()
        )
        val today = LocalDate.of(2026, 11, 24)
        val breakdown = PaycheckCalculator.calculateBreakdown(bills, debts, config, today)

        // Rent (Dec 1: $1000)
        assertEquals(1000.0, breakdown.windowBillsTotal, 0.01)
        // Debt d1 (Dec 5: $150)
        assertEquals(150.0, breakdown.windowDebtsTotal, 0.01)
        // Total obligations in window = $1150
        assertEquals(1150.0, breakdown.windowObligationsTotal, 0.01)

        // Check obligationsDueInPeriod list
        assertEquals(2, breakdown.obligationsDueInPeriod.size)
        val rentObligation = breakdown.obligationsDueInPeriod[0]
        assertEquals("Rent", rentObligation.name)
        assertEquals(ObligationType.BILL, rentObligation.type)
        assertEquals(LocalDate.of(2026, 12, 1), rentObligation.dueDate)

        val debtObligation = breakdown.obligationsDueInPeriod[1]
        assertEquals("Credit Card", debtObligation.name)
        assertEquals(ObligationType.DEBT, debtObligation.type)
        assertEquals(LocalDate.of(2026, 12, 5), debtObligation.dueDate)
        assertEquals(150.0, debtObligation.amount, 0.01)
    }

    @Test
    fun testDecoupledPaycheckOvertimeProjections() {
        // Bills: $500 Rent (due 1st), Debts: $200 Loan (due 15th)
        val bills = listOf(Bill("1", "Rent", 500.0, 1, "Housing"))
        val debts = listOf(Debt("d1", "Auto Loan", 5000.0, 5000.0, 0.05, 200.0, DebtType.LOAN, 0L, dueDayOfMonth = 15))

        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 1600.0, // baseline
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 27).toEpochDay()
        )
        val today = LocalDate.of(2026, 11, 24)

        // User worked 70 hours this period, giving an overtime take-home of $2400
        // But baseline is $1600 (40 hrs/wk, 0 overtime)
        val currentOvertimeTakeHome = 2400.0
        val baselineTakeHome = 1600.0

        val breakdown = PaycheckCalculator.calculateBreakdown(
            bills = bills,
            debts = debts,
            config = config,
            currentDate = today,
            currentTakeHomePay = currentOvertimeTakeHome,
            baselineTakeHomePay = baselineTakeHome
        )

        // Breakdown take-home checks
        assertEquals(2400.0, breakdown.currentTakeHomePay, 0.01)
        assertEquals(1600.0, breakdown.baselineTakeHomePay, 0.01)
        assertTrue("Current period has overtime compared to baseline", breakdown.isCurrentPeriodOvertime)

        // Equalized reserve: (500 + 200) * 12 / 26 = 323.0769
        val reserve = breakdown.perPaycheckReserve
        assertEquals(2400.0 - reserve, breakdown.currentSafeToSpend, 0.05)
        assertEquals(1600.0 - reserve, breakdown.baselineSafeToSpend, 0.05)

        // Check forward schedule: Period 1 uses currentTakeHome (2400), Period 2+ use baseline (1600)
        val upcoming = breakdown.upcomingPayPeriods
        assertTrue(upcoming.size >= 3)

        val period1 = upcoming[0]
        assertEquals(1, period1.periodIndex)
        assertTrue("Period 1 is current period", period1.isCurrentPeriod)
        assertEquals(2400.0, period1.projectedTakeHomePay, 0.01)

        val period2 = upcoming[1]
        assertEquals(2, period2.periodIndex)
        assertFalse("Period 2 is future period", period2.isCurrentPeriod)
        assertEquals(1600.0, period2.projectedTakeHomePay, 0.01)

        val period3 = upcoming[2]
        assertEquals(3, period3.periodIndex)
        assertFalse("Period 3 is future period", period3.isCurrentPeriod)
        assertEquals(1600.0, period3.projectedTakeHomePay, 0.01)
    }

    @Test
    fun testForwardSchedule_userScenario_pay3DeficitCoveredByPay2() {
        // Bi-weekly schedule starting May 2, 2026
        // Pay 1: May 2 to May 16
        // Pay 2: May 16 to May 30
        // Pay 3: May 30 to June 13
        val bills = listOf(
            Bill("b1", "Phone", 400.0, 15, "Utilities"),     // May 15 -> Pay 1 window
            Bill("b2", "Electric", 500.0, 18, "Utilities"),  // May 18 -> Pay 2 window
            Bill("b3", "Rent", 1800.0, 1, "Housing")         // June 1 -> Pay 3 window
        )

        val schedule = PaycheckCalculator.projectUpcomingPayPeriods(
            bills = bills,
            debts = emptyList(),
            firstPayDate = LocalDate.of(2026, 5, 2),
            frequency = PayFrequency.BI_WEEKLY,
            takeHomePay = 1500.0,
            periodCount = 3
        )

        assertEquals(3, schedule.size)

        val p1 = schedule[0]
        val p2 = schedule[1]
        val p3 = schedule[2]

        // Raw calculations:
        // P1: 1500 - 400 = 1100
        // P2: 1500 - 500 = 1000
        // P3: 1500 - 1800 = -300 (Deficit!)
        assertEquals(1100.0, p1.rawSafeToSpend, 0.01)
        assertEquals(1000.0, p2.rawSafeToSpend, 0.01)
        assertEquals(-300.0, p3.rawSafeToSpend, 0.01)

        // After Deficit Smoothing:
        // Pay 2 sets aside $300 for Pay 3
        assertEquals(300.0, p2.reserveHeldForFutureChecks, 0.01)
        assertEquals(700.0, p2.safeToSpend, 0.01)
        assertNotNull(p2.deficitNote)
        assertTrue("Pay 2 note references Pay #3", p2.deficitNote!!.contains("Pay #3"))

        // Pay 3 has its $300 deficit fully covered by Pay 2 -> safeToSpend is 0.00 (not -300!)
        assertEquals(300.0, p3.coveredDeficitByPriorChecks, 0.01)
        assertEquals(0.0, p3.safeToSpend, 0.01)
        assertNotNull(p3.deficitNote)
        assertTrue("Pay 3 note references Pay #2", p3.deficitNote!!.contains("Pay #2"))

        // Pay 1 is unaffected because Pay 2 had enough surplus to cover Pay 3 completely
        assertEquals(0.0, p1.reserveHeldForFutureChecks, 0.01)
        assertEquals(1100.0, p1.safeToSpend, 0.01)
        assertNull(p1.deficitNote)
    }

    @Test
    fun testForwardSchedule_cascadingDeficitCoveredByMultipleChecks() {
        // Pay 1 (May 2-16): 1500 take-home, 500 bills -> +1000 raw
        // Pay 2 (May 16-30): 1500 take-home, 1400 bills -> +100 raw
        // Pay 3 (May 30-Jun 13): 1500 take-home, 1800 bills -> -300 raw
        val bills = listOf(
            Bill("b1", "Phone", 500.0, 15, "Utilities"),
            Bill("b2", "Credit", 1400.0, 20, "Debts"),
            Bill("b3", "Rent", 1800.0, 1, "Housing")
        )

        val schedule = PaycheckCalculator.projectUpcomingPayPeriods(
            bills = bills,
            debts = emptyList(),
            firstPayDate = LocalDate.of(2026, 5, 2),
            frequency = PayFrequency.BI_WEEKLY,
            takeHomePay = 1500.0,
            periodCount = 3
        )

        val p1 = schedule[0]
        val p2 = schedule[1]
        val p3 = schedule[2]

        // Raw calculations
        assertEquals(1000.0, p1.rawSafeToSpend, 0.01)
        assertEquals(100.0, p2.rawSafeToSpend, 0.01)
        assertEquals(-300.0, p3.rawSafeToSpend, 0.01)

        // Pay 2 only has 100 surplus, so it contributes 100 (safe = 0.0)
        assertEquals(100.0, p2.reserveHeldForFutureChecks, 0.01)
        assertEquals(0.0, p2.safeToSpend, 0.01)

        // Remaining 200 cascades back to Pay 1
        assertEquals(200.0, p1.reserveHeldForFutureChecks, 0.01)
        assertEquals(800.0, p1.safeToSpend, 0.01)

        // Pay 3 is fully covered: 100 from P2 + 200 from P1 = 300
        assertEquals(300.0, p3.coveredDeficitByPriorChecks, 0.01)
        assertEquals(0.0, p3.safeToSpend, 0.01)
    }

    @Test
    fun testForwardSchedule_unavoidableDeficit() {
        // Pay 1 (May 2-16): 1000 take-home, 900 bills -> +100 raw
        // Pay 2 (May 16-30): 1000 take-home, 950 bills -> +50 raw
        // Pay 3 (May 30-Jun 13): 1000 take-home, 1500 bills -> -500 raw
        // Total surplus = 150, but needed = 500. Shortfall = -350.
        val bills = listOf(
            Bill("b1", "Bill 1", 900.0, 15, "Utilities"),
            Bill("b2", "Bill 2", 950.0, 20, "Utilities"),
            Bill("b3", "Rent", 1500.0, 1, "Housing")
        )

        val schedule = PaycheckCalculator.projectUpcomingPayPeriods(
            bills = bills,
            debts = emptyList(),
            firstPayDate = LocalDate.of(2026, 5, 2),
            frequency = PayFrequency.BI_WEEKLY,
            takeHomePay = 1000.0,
            periodCount = 3
        )

        val p1 = schedule[0]
        val p2 = schedule[1]
        val p3 = schedule[2]

        assertEquals(100.0, p1.reserveHeldForFutureChecks, 0.01)
        assertEquals(0.0, p1.safeToSpend, 0.01)

        assertEquals(50.0, p2.reserveHeldForFutureChecks, 0.01)
        assertEquals(0.0, p2.safeToSpend, 0.01)

        // P3 gets 150 covered, still has -350 deficit
        assertEquals(150.0, p3.coveredDeficitByPriorChecks, 0.01)
        assertEquals(-350.0, p3.safeToSpend, 0.01)
        assertTrue(p3.deficitNote!!.contains("remaining deficit"))
    }

    @Test
    fun testCalculateThreePaycheckCountdown() {
        val anchor = LocalDate.of(2026, 11, 13)
        val today = LocalDate.of(2026, 11, 1)
        val countdown = PaycheckCalculator.calculateThreePaycheckCountdown(
            anchorPayDate = anchor,
            frequency = PayFrequency.BI_WEEKLY,
            fromDate = today
        )

        assertNotNull(countdown)
        assertEquals(java.time.YearMonth.of(2027, 4), countdown!!.targetYearMonth)
        assertEquals("April 2027", countdown.formattedMonth)
        assertEquals(5L, countdown.monthsRemaining)
        assertFalse(countdown.isCurrentMonth)
        assertEquals(listOf(2, 16, 30), countdown.payDates)

        val insideDate = LocalDate.of(2027, 4, 5)
        val activeCountdown = PaycheckCalculator.calculateThreePaycheckCountdown(
            anchorPayDate = anchor,
            frequency = PayFrequency.BI_WEEKLY,
            fromDate = insideDate
        )
        assertNotNull(activeCountdown)
        assertTrue(activeCountdown!!.isCurrentMonth)
        assertEquals(0L, activeCountdown.monthsRemaining)
        assertEquals(25L, activeCountdown.daysRemaining)
    }

    @Test
    fun testCalculateMonthlyGlimpse() {
        val bills = listOf(
            Bill("b1", "Rent", 1000.0, 1, "Housing"),
            Bill("b2", "Electric", 150.0, 15, "Utilities")
        )
        val debts = listOf(
            Debt("d1", "Card", 2000.0, 1500.0, 0.20, 100.0, DebtType.CREDIT_CARD, 0L, dueDayOfMonth = 10)
        )
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 2000.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 11, 13).toEpochDay()
        )

        val glimpseApril = PaycheckCalculator.calculateMonthlyGlimpse(
            year = 2027,
            month = 4,
            bills = bills,
            debts = debts,
            config = config,
            anchorPayDate = LocalDate.of(2026, 11, 13)
        )

        assertEquals(3, glimpseApril.payDaysCount)
        assertEquals(6000.0, glimpseApril.estimatedIncome, 0.01)
        assertEquals(1150.0, glimpseApril.totalBills, 0.01)
        assertEquals(100.0, glimpseApril.totalDebts, 0.01)
        assertEquals(1250.0, glimpseApril.totalObligations, 0.01)
        assertEquals(4750.0, glimpseApril.netSurplus, 0.01)
    }

    @Test
    fun testSingleCycleBillSettlement() {
        val initialDue = LocalDate.of(2026, 12, 1)
        val bill = Bill(
            id = "b1",
            name = "Rent",
            amount = 1200.0,
            dueDayOfMonth = 1,
            category = "Housing",
            recurrence = com.example.financehome.data.model.BillRecurrence.MONTHLY,
            nextDueDateEpochDay = initialDue.toEpochDay()
        )

        val decStart = LocalDate.of(2026, 12, 1)
        val janStart = LocalDate.of(2027, 1, 1)
        val febStart = LocalDate.of(2027, 2, 1)
        val marStart = LocalDate.of(2027, 3, 1)

        // 1. Initial State: All cycles are unpaid
        val decUnpaidInitial = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(bill), emptyList(), decStart, janStart)
        val janUnpaidInitial = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(bill), emptyList(), janStart, febStart)
        val febUnpaidInitial = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(bill), emptyList(), febStart, marStart)
        val threeMonthTotalInitial = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(bill), emptyList(), decStart, marStart)

        assertEquals(1200.0, decUnpaidInitial, 0.01)
        assertEquals(1200.0, janUnpaidInitial, 0.01)
        assertEquals(1200.0, febUnpaidInitial, 0.01)
        assertEquals(3600.0, threeMonthTotalInitial, 0.01)

        // 2. Mark Single Cycle Paid: Dec 1 settled, nextDueDate advances to Jan 1 (+1 month)
        val currentDue = bill.getEffectiveNextDueDate(decStart)
        val nextDue = bill.advanceDueDate(currentDue)
        assertEquals(LocalDate.of(2027, 1, 1), nextDue)

        val paidBill = bill.copy(
            nextDueDateEpochDay = nextDue.toEpochDay(),
            lastSettledDateEpochDay = currentDue.toEpochDay(),
            isPaidThisMonth = true
        )

        // Verify: Dec 1 cycle removed from upcoming unpaid liabilities
        val decUnpaidAfter = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(paidBill), emptyList(), decStart, janStart)
        assertEquals(0.0, decUnpaidAfter, 0.01)

        // Verify: Month +1 (Jan) and Month +2 (Feb) remain projected as unpaid outflows
        val janUnpaidAfter = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(paidBill), emptyList(), janStart, febStart)
        val febUnpaidAfter = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(paidBill), emptyList(), febStart, marStart)
        assertEquals(1200.0, janUnpaidAfter, 0.01)
        assertEquals(1200.0, febUnpaidAfter, 0.01)

        val threeMonthTotalAfter = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(paidBill), emptyList(), decStart, marStart)
        assertEquals(2400.0, threeMonthTotalAfter, 0.01)

        // 3. Rollback / Unmark Paid: restores prior nextDueDate (Dec 1)
        val restoredDue = LocalDate.ofEpochDay(paidBill.lastSettledDateEpochDay!!)
        assertEquals(LocalDate.of(2026, 12, 1), restoredDue)

        val unmarkBill = paidBill.copy(
            nextDueDateEpochDay = restoredDue.toEpochDay(),
            lastSettledDateEpochDay = null,
            isPaidThisMonth = false
        )

        val decUnpaidRolledBack = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(unmarkBill), emptyList(), decStart, janStart)
        val threeMonthTotalRolledBack = PaycheckCalculator.calculateUpcomingUnpaidLiabilities(listOf(unmarkBill), emptyList(), decStart, marStart)
        assertEquals(1200.0, decUnpaidRolledBack, 0.01)
        assertEquals(3600.0, threeMonthTotalRolledBack, 0.01)
    }

    @Test
    fun testBiWeeklyBillSettlement() {
        val initialDue = LocalDate.of(2026, 12, 1)
        val bill = Bill(
            id = "bw1",
            name = "Gym Membership",
            amount = 50.0,
            dueDayOfMonth = 1,
            category = "Fitness",
            recurrence = com.example.financehome.data.model.BillRecurrence.BI_WEEKLY,
            nextDueDateEpochDay = initialDue.toEpochDay()
        )

        val nextDue = bill.advanceDueDate(initialDue)
        assertEquals(LocalDate.of(2026, 12, 15), nextDue)

        val paidBill = bill.copy(
            nextDueDateEpochDay = nextDue.toEpochDay(),
            lastSettledDateEpochDay = initialDue.toEpochDay()
        )

        assertTrue(paidBill.isPaidForDate(LocalDate.of(2026, 12, 1)))
        assertFalse(paidBill.isPaidForDate(LocalDate.of(2026, 12, 15)))
    }
}

