package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SpendingAlertTest {

    private val sampleBills = listOf(
        Bill("1", "Rent", 1000.0, 1, "Housing"),
        Bill("2", "Electric", 100.0, 15, "Utilities")
    )
    private val sampleDebts = listOf(
        Debt("d1", "Card", 2000.0, 2000.0, 0.15, 100.0, DebtType.CREDIT_CARD, 0L)
    )

    @Test
    fun testCalculateCurrentCycleStartDate_biWeekly() {
        // Anchor: 2026-05-01 (Friday). Next is 2026-05-15.
        val anchor = LocalDate.of(2026, 5, 1)
        val testDate = LocalDate.of(2026, 5, 8) // Middle of cycle

        val cycleStart = PaycheckCalculator.calculateCurrentCycleStartDate(
            anchorPayDate = anchor,
            frequency = PayFrequency.BI_WEEKLY,
            currentDate = testDate
        )
        assertEquals(LocalDate.of(2026, 5, 1), cycleStart)

        // Exact payday: cycle start is the payday itself
        val onPayday = PaycheckCalculator.calculateCurrentCycleStartDate(
            anchorPayDate = anchor,
            frequency = PayFrequency.BI_WEEKLY,
            currentDate = LocalDate.of(2026, 5, 15)
        )
        assertEquals(LocalDate.of(2026, 5, 15), onPayday)
    }

    @Test
    fun testCalculateCurrentCycleStartDate_weekly() {
        val anchor = LocalDate.of(2026, 5, 1) // Friday
        val testDate = LocalDate.of(2026, 5, 5) // Tuesday

        val cycleStart = PaycheckCalculator.calculateCurrentCycleStartDate(
            anchorPayDate = anchor,
            frequency = PayFrequency.WEEKLY,
            currentDate = testDate
        )
        assertEquals(LocalDate.of(2026, 5, 1), cycleStart)
    }

    @Test
    fun testCalculateCurrentCycleStartDate_monthly() {
        val anchor = LocalDate.of(2026, 5, 1)
        val testDate = LocalDate.of(2026, 5, 20)

        val cycleStart = PaycheckCalculator.calculateCurrentCycleStartDate(
            anchorPayDate = anchor,
            frequency = PayFrequency.MONTHLY,
            currentDate = testDate
        )
        assertEquals(LocalDate.of(2026, 5, 1), cycleStart)
    }

    @Test
    fun testCalculatePreviousPayDate() {
        val nextPayDate = LocalDate.of(2026, 5, 15)
        val prevBiWeekly = PaycheckCalculator.calculatePreviousPayDate(nextPayDate, PayFrequency.BI_WEEKLY)
        assertEquals(LocalDate.of(2026, 5, 1), prevBiWeekly)

        val prevWeekly = PaycheckCalculator.calculatePreviousPayDate(nextPayDate, PayFrequency.WEEKLY)
        assertEquals(LocalDate.of(2026, 5, 8), prevWeekly)

        val prevMonthly = PaycheckCalculator.calculatePreviousPayDate(nextPayDate, PayFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 4, 15), prevMonthly)
    }

    @Test
    fun testSafeToSpendStatus_healthy() {
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 2000.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
        )
        val breakdown = PaycheckCalculator.calculateBreakdown(
            bills = sampleBills,
            debts = sampleDebts,
            config = config,
            currentDate = LocalDate.of(2026, 5, 5)
        )
        val alertConfig = SpendingAlertConfig(
            isAlertsEnabled = true,
            lowCushionThreshold = 100.0,
            alertOnDeficit = true
        )

        // Initial cushion is ~1446.15. Spent: $200 -> Remaining: ~1246.15 (Healthy)
        val status = PaycheckCalculator.calculateSafeToSpendStatus(
            breakdown = breakdown,
            config = config,
            spentThisCycle = 200.0,
            alertConfig = alertConfig,
            currentDate = LocalDate.of(2026, 5, 5)
        )

        assertEquals(SafeToSpendAlertLevel.HEALTHY, status.alertLevel)
        assertEquals(breakdown.perPaycheckSafeToSpend, status.initialSafeToSpend, 0.01)
        assertEquals(200.0, status.spentThisCycle, 0.01)
        assertEquals(breakdown.perPaycheckSafeToSpend - 200.0, status.remainingSafeToSpend, 0.01)
        assertTrue(status.dailyPace > 0.0)
    }

    @Test
    fun testSafeToSpendStatus_lowWarning() {
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 1000.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
        )
        val breakdown = PaycheckCalculator.calculateBreakdown(
            bills = sampleBills,
            debts = sampleDebts,
            config = config,
            currentDate = LocalDate.of(2026, 5, 5)
        )
        val alertConfig = SpendingAlertConfig(
            isAlertsEnabled = true,
            lowCushionThreshold = 100.0,
            alertOnDeficit = true
        )

        // Suppose initial cushion is breakdown.perPaycheckSafeToSpend (~446.15)
        // Spent: 400.0 -> Remaining: ~46.15 (below 100.0 threshold) -> LOW_WARNING
        val spent = breakdown.perPaycheckSafeToSpend - 46.15
        val status = PaycheckCalculator.calculateSafeToSpendStatus(
            breakdown = breakdown,
            config = config,
            spentThisCycle = spent,
            alertConfig = alertConfig,
            currentDate = LocalDate.of(2026, 5, 5)
        )

        assertEquals(SafeToSpendAlertLevel.LOW_WARNING, status.alertLevel)
        assertEquals(46.15, status.remainingSafeToSpend, 0.05)
    }

    @Test
    fun testSafeToSpendStatus_deficitStop() {
        val config = PaycheckConfig(
            frequency = PayFrequency.BI_WEEKLY,
            typicalTakeHomePay = 1000.0,
            anchorPayDateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay()
        )
        val breakdown = PaycheckCalculator.calculateBreakdown(
            bills = sampleBills,
            debts = sampleDebts,
            config = config,
            currentDate = LocalDate.of(2026, 5, 5)
        )
        val alertConfig = SpendingAlertConfig(
            isAlertsEnabled = true,
            lowCushionThreshold = 100.0,
            alertOnDeficit = true
        )

        // Spent more than initial cushion -> DEFICIT_STOP
        val spent = breakdown.perPaycheckSafeToSpend + 50.0
        val status = PaycheckCalculator.calculateSafeToSpendStatus(
            breakdown = breakdown,
            config = config,
            spentThisCycle = spent,
            alertConfig = alertConfig,
            currentDate = LocalDate.of(2026, 5, 5)
        )

        assertEquals(SafeToSpendAlertLevel.DEFICIT_STOP, status.alertLevel)
        assertEquals(-50.0, status.remainingSafeToSpend, 0.01)
        assertEquals(0.0, status.dailyPace, 0.01)
    }

    @Test
    fun testSpendingAlertConfig_defaults() {
        val cfg = SpendingAlertConfig()
        assertTrue(cfg.isAlertsEnabled)
        assertTrue(cfg.notifyOnEveryPurchase)
        assertEquals(100.0, cfg.lowCushionThreshold, 0.01)
        assertTrue(cfg.alertOnDeficit)
        assertTrue(cfg.dailyPaceWarningEnabled)
    }
}
