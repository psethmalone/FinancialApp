package com.example.financehome.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class DebtCalculatorTest {

    @Test
    fun testZeroBalance_returnsZeroMonths() {
        val months = DebtCalculator.monthsToPayoff(0.0, 0.20, 100.0)
        assertEquals(0.0, months, 0.001)
    }

    @Test
    fun testPaymentLessThanInterest_returnsInfinity() {
        // Balance 10,000, 24% APR (2% per month = $200 interest)
        // Payment of $150 cannot even cover interest
        val months = DebtCalculator.monthsToPayoff(10000.0, 0.24, 150.0)
        assertEquals(Double.MAX_VALUE, months, 0.001)
    }

    @Test
    fun testStandardPayoffCalculation() {
        // $5,000 balance at 18% APR with $150 monthly payment
        // Standard amortization formula: 46.55 -> 47 months
        val months = DebtCalculator.monthsToPayoff(5000.0, 0.18, 150.0)
        assertEquals(47, months.roundToInt())
    }

    @Test
    fun testExtraPayment_savesMonthsAndInterest() {
        val balance = 5000.0
        val apr = 0.18
        val minPayment = 150.0
        val extra = 50.0 // total $200/mo

        val monthsSaved = DebtCalculator.monthsSavedWithExtraPayment(balance, apr, minPayment, extra)
        val interestSaved = DebtCalculator.interestSavedWithExtraPayment(balance, apr, minPayment, extra)

        assertTrue("Months saved should be greater than zero", monthsSaved > 0)
        assertTrue("Interest saved should be positive", interestSaved > 0.0)
    }

    @Test
    fun testAmortizationSchedule_terminatesAtZero() {
        val schedule = DebtCalculator.amortizationSchedule(1000.0, 0.12, 100.0)
        assertTrue(schedule.isNotEmpty())
        val finalRow = schedule.last()
        assertEquals(0.0, finalRow.remainingBalance, 0.01)
    }
}
