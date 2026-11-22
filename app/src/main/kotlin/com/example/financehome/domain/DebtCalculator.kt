package com.example.financehome.domain

import java.util.Calendar
import kotlin.math.ln
import kotlin.math.roundToInt

data class AmortizationRow(
    val month: Int,
    val payment: Double,
    val principal: Double,
    val interest: Double,
    val remainingBalance: Double
)

object DebtCalculator {

    /**
     * Calculates the number of months required to pay off a debt with fixed monthly payment.
     * Formula: -ln(1 - (r * P) / A) / ln(1 + r)
     * where r = monthlyRate (annualApr / 12), P = balance, A = monthlyPayment.
     */
    fun monthsToPayoff(balance: Double, annualApr: Double, monthlyPayment: Double): Double {
        if (balance <= 0.0) return 0.0
        if (monthlyPayment <= 0.0) return Double.MAX_VALUE

        val monthlyRate = annualApr / 12.0
        if (monthlyRate <= 0.0) {
            return balance / monthlyPayment
        }

        val monthlyInterest = balance * monthlyRate
        if (monthlyPayment <= monthlyInterest) {
            // Payment doesn't even cover interest; balance will never be paid off
            return Double.MAX_VALUE
        }

        val numerator = -ln(1.0 - (monthlyRate * balance) / monthlyPayment)
        val denominator = ln(1.0 + monthlyRate)
        return numerator / denominator
    }

    /**
     * Calculates total interest paid over the life of the debt.
     */
    fun totalInterestPaid(balance: Double, annualApr: Double, monthlyPayment: Double): Double {
        val months = monthsToPayoff(balance, annualApr, monthlyPayment)
        if (months == Double.MAX_VALUE || months.isInfinite() || months.isNaN()) {
            return Double.MAX_VALUE
        }
        val totalPaid = months * monthlyPayment
        return (totalPaid - balance).coerceAtLeast(0.0)
    }

    /**
     * Calculates how many months earlier the debt will be paid off with an extra payment.
     */
    fun monthsSavedWithExtraPayment(
        balance: Double,
        annualApr: Double,
        minPayment: Double,
        extraPayment: Double
    ): Int {
        val baseMonths = monthsToPayoff(balance, annualApr, minPayment)
        if (baseMonths == Double.MAX_VALUE) return 0

        val acceleratedMonths = monthsToPayoff(balance, annualApr, minPayment + extraPayment)
        val saved = baseMonths.roundToInt() - acceleratedMonths.roundToInt()
        return saved.coerceAtLeast(0)
    }

    /**
     * Calculates interest saved by adding extra monthly principal payments.
     */
    fun interestSavedWithExtraPayment(
        balance: Double,
        annualApr: Double,
        minPayment: Double,
        extraPayment: Double
    ): Double {
        val baseInterest = totalInterestPaid(balance, annualApr, minPayment)
        if (baseInterest == Double.MAX_VALUE) return 0.0

        val acceleratedInterest = totalInterestPaid(balance, annualApr, minPayment + extraPayment)
        val saved = baseInterest - acceleratedInterest
        return saved.coerceAtLeast(0.0)
    }

    /**
     * Generates a month-by-month amortization schedule up to 360 months (30 years).
     */
    fun amortizationSchedule(
        balance: Double,
        annualApr: Double,
        monthlyPayment: Double
    ): List<AmortizationRow> {
        val schedule = mutableListOf<AmortizationRow>()
        if (balance <= 0.0 || monthlyPayment <= 0.0) return schedule

        val monthlyRate = annualApr / 12.0
        var remaining = balance
        var month = 1
        val maxMonths = 360

        while (remaining > 0.001 && month <= maxMonths) {
            val interest = remaining * monthlyRate
            val actualPayment = if (remaining + interest < monthlyPayment) remaining + interest else monthlyPayment
            val principal = (actualPayment - interest).coerceAtLeast(0.0)
            remaining = (remaining - principal).coerceAtLeast(0.0)

            schedule.add(
                AmortizationRow(
                    month = month,
                    payment = actualPayment,
                    principal = principal,
                    interest = interest,
                    remainingBalance = remaining
                )
            )

            if (principal <= 0.0) {
                // Payment doesn't cover interest
                break
            }
            month++
        }

        return schedule
    }

    /**
     * Estimates payoff timestamp in epoch milliseconds from current time.
     */
    fun payoffDateMillis(monthsToPayoff: Double): Long {
        if (monthsToPayoff == Double.MAX_VALUE || monthsToPayoff.isInfinite() || monthsToPayoff.isNaN()) {
            return Long.MAX_VALUE
        }
        val cal = Calendar.getInstance()
        val wholeMonths = monthsToPayoff.toInt()
        val remainingFraction = monthsToPayoff - wholeMonths
        val extraDays = (remainingFraction * 30.0).roundToInt()

        cal.add(Calendar.MONTH, wholeMonths)
        cal.add(Calendar.DAY_OF_MONTH, extraDays)
        return cal.timeInMillis
    }
}
