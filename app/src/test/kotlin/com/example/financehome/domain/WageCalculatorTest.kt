package com.example.financehome.domain

import org.junit.Assert.*
import org.junit.Test

class WageCalculatorTest {

    @Test
    fun testStandardFortyHourBiWeekly() {
        val config = WageConfig(
            hourlyWage = 25.0,
            overtimeMultiplier = 1.5,
            estimatedDeductionPercent = 20.0,
            week1Hours = 40.0,
            week2Hours = 40.0
        )

        val estimate = WageCalculator.calculateEstimate(config, PayFrequency.BI_WEEKLY)

        assertEquals(80.0, estimate.totalHours, 0.01)
        assertEquals(80.0, estimate.regularHours, 0.01)
        assertEquals(0.0, estimate.overtimeHours, 0.01)
        // 80 * 25 = 2000 gross
        assertEquals(2000.0, estimate.grossPay, 0.01)
        // 20% of 2000 = 400
        assertEquals(400.0, estimate.estimatedDeductions, 0.01)
        // 2000 - 400 = 1600 net
        assertEquals(1600.0, estimate.estimatedNetPay, 0.01)
        assertFalse(estimate.isVerified)
        assertEquals(1600.0, estimate.effectiveTakeHomePay, 0.01)
    }

    @Test
    fun testOvertimeCalculationPerWeek() {
        // Week 1: 46 hours (40 regular @ 25, 6 OT @ 37.5) -> 1000 + 225 = 1225
        // Week 2: 38 hours (38 regular @ 25) -> 950
        // Total Gross: 1225 + 950 = 2175
        val config = WageConfig(
            hourlyWage = 25.0,
            overtimeMultiplier = 1.5,
            estimatedDeductionPercent = 15.0,
            week1Hours = 46.0,
            week2Hours = 38.0
        )

        val estimate = WageCalculator.calculateEstimate(config, PayFrequency.BI_WEEKLY)

        assertEquals(84.0, estimate.totalHours, 0.01)
        assertEquals(78.0, estimate.regularHours, 0.01)
        assertEquals(6.0, estimate.overtimeHours, 0.01)
        assertEquals(2175.0, estimate.grossPay, 0.01)
        // 15% of 2175 = 326.25
        assertEquals(326.25, estimate.estimatedDeductions, 0.01)
        assertEquals(1848.75, estimate.estimatedNetPay, 0.01)
        assertEquals(1848.75, estimate.effectiveTakeHomePay, 0.01)
    }

    @Test
    fun testVerifiedNetPayOverridesEstimate() {
        val config = WageConfig(
            hourlyWage = 25.0,
            overtimeMultiplier = 1.5,
            estimatedDeductionPercent = 20.0,
            week1Hours = 40.0,
            week2Hours = 40.0,
            isPaycheckVerified = true,
            verifiedNetPay = 1642.50
        )

        val estimate = WageCalculator.calculateEstimate(config, PayFrequency.BI_WEEKLY)

        assertEquals(1600.0, estimate.estimatedNetPay, 0.01)
        assertTrue(estimate.isVerified)
        assertEquals(1642.50, estimate.verifiedNetPay)
        // effectiveTakeHomePay should use verifiedNetPay
        assertEquals(1642.50, estimate.effectiveTakeHomePay, 0.01)
    }

    @Test
    fun testWeeklyFrequencyIgnoresWeek2() {
        val config = WageConfig(
            hourlyWage = 30.0,
            overtimeMultiplier = 1.5,
            estimatedDeductionPercent = 10.0,
            week1Hours = 42.0,
            week2Hours = 40.0
        )

        val estimate = WageCalculator.calculateEstimate(config, PayFrequency.WEEKLY)

        assertEquals(42.0, estimate.totalHours, 0.01)
        assertEquals(40.0, estimate.regularHours, 0.01)
        assertEquals(2.0, estimate.overtimeHours, 0.01)
        // 40 * 30 + 2 * 45 = 1200 + 90 = 1290
        assertEquals(1290.0, estimate.grossPay, 0.01)
        assertEquals(129.0, estimate.estimatedDeductions, 0.01)
        assertEquals(1161.0, estimate.estimatedNetPay, 0.01)
    }
}
