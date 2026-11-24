package com.example.financehome.domain

import com.example.financehome.data.model.Debt
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

enum class PayoffStrategy {
    AVALANCHE, // Highest APR first - mathematically minimizes total interest paid
    SNOWBALL   // Lowest balance first - maximizes psychological momentum
}

data class DebtPayoffMilestone(
    val debtId: String,
    val debtName: String,
    val payoffMonth: Int,
    val payoffDateMillis: Long,
    val totalInterestPaid: Double
)

data class StrategyPayoffResult(
    val strategy: PayoffStrategy,
    val totalMonths: Int,
    val debtFreeDateMillis: Long,
    val totalInterestPaid: Double,
    val totalInterestSavedVsMinimum: Double,
    val milestones: List<DebtPayoffMilestone>
)

data class MultiDebtComparison(
    val minimumOnlyTotalMonths: Int,
    val minimumOnlyDebtFreeDateMillis: Long,
    val minimumOnlyTotalInterest: Double,
    val avalancheResult: StrategyPayoffResult,
    val snowballResult: StrategyPayoffResult,
    val extraMonthlyPayment: Double
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

    /**
     * Simulates payoff across multiple debts using either Avalanche (highest APR first)
     * or Snowball (lowest balance first), rolling over eliminated minimum payments.
     */
    fun simulateStrategy(
        debts: List<Debt>,
        extraMonthlyPayment: Double,
        strategy: PayoffStrategy
    ): StrategyPayoffResult {
        val activeDebts = debts.filter { it.currentBalance > 0.01 }
        if (activeDebts.isEmpty()) {
            return StrategyPayoffResult(
                strategy = strategy,
                totalMonths = 0,
                debtFreeDateMillis = System.currentTimeMillis(),
                totalInterestPaid = 0.0,
                totalInterestSavedVsMinimum = 0.0,
                milestones = emptyList()
            )
        }

        class SimDebt(
            val debt: Debt,
            var balance: Double,
            var totalInterestPaid: Double = 0.0,
            var isPaid: Boolean = false,
            var wasPaidAtStartOfMonth: Boolean = false
        )

        val simDebts = activeDebts.map { SimDebt(it, it.currentBalance) }
        val milestones = mutableListOf<DebtPayoffMilestone>()
        var month = 0
        val maxMonths = 600

        while (simDebts.any { !it.isPaid } && month < maxMonths) {
            month++

            // 1. Accumulate monthly interest on active balances
            for (d in simDebts) {
                if (!d.isPaid) {
                    val monthlyInterest = d.balance * (d.debt.apr / 12.0)
                    d.balance += monthlyInterest
                    d.totalInterestPaid += monthlyInterest
                }
            }

            // 2. Pay required minimums on active debts
            for (d in simDebts) {
                if (!d.isPaid) {
                    val minDue = d.debt.minimumPayment
                    val payment = minOf(d.balance, minDue)
                    d.balance -= payment
                    if (d.balance <= 0.001) {
                        d.balance = 0.0
                        d.isPaid = true
                        milestones.add(
                            DebtPayoffMilestone(
                                debtId = d.debt.id,
                                debtName = d.debt.name,
                                payoffMonth = month,
                                payoffDateMillis = payoffDateMillis(month.toDouble()),
                                totalInterestPaid = d.totalInterestPaid
                            )
                        )
                    }
                }
            }

            // 3. Pool available rollover money (extra budget + minimum payments freed in prior months)
            val freedMinimums = simDebts.filter { it.wasPaidAtStartOfMonth }.sumOf { it.debt.minimumPayment }
            var availableExtra = extraMonthlyPayment + freedMinimums

            // 4. Allocate rollover money according to strategy
            while (availableExtra > 0.001 && simDebts.any { !it.isPaid }) {
                val unpaid = simDebts.filter { !it.isPaid }
                val target = when (strategy) {
                    PayoffStrategy.AVALANCHE -> unpaid.maxWithOrNull(
                        compareBy<SimDebt> { it.debt.apr }.thenByDescending { it.balance }
                    )
                    PayoffStrategy.SNOWBALL -> unpaid.minWithOrNull(
                        compareBy<SimDebt> { it.balance }.thenByDescending { it.debt.apr }
                    )
                } ?: break

                val payment = minOf(target.balance, availableExtra)
                target.balance -= payment
                availableExtra -= payment

                if (target.balance <= 0.001) {
                    target.balance = 0.0
                    target.isPaid = true
                    milestones.add(
                        DebtPayoffMilestone(
                            debtId = target.debt.id,
                            debtName = target.debt.name,
                            payoffMonth = month,
                            payoffDateMillis = payoffDateMillis(month.toDouble()),
                            totalInterestPaid = target.totalInterestPaid
                        )
                    )
                }
            }

            // Update start-of-month flag for the next cycle
            for (d in simDebts) {
                if (d.isPaid) {
                    d.wasPaidAtStartOfMonth = true
                }
            }
        }

        val totalInterest = simDebts.sumOf { it.totalInterestPaid }
        val allPaid = simDebts.all { it.isPaid }
        val finalMonths = if (allPaid) month else -1

        return StrategyPayoffResult(
            strategy = strategy,
            totalMonths = finalMonths,
            debtFreeDateMillis = if (finalMonths >= 0) payoffDateMillis(finalMonths.toDouble()) else Long.MAX_VALUE,
            totalInterestPaid = totalInterest,
            totalInterestSavedVsMinimum = 0.0,
            milestones = milestones
        )
    }

    /**
     * Simulates payoff paying ONLY the minimum payment independently on each debt (no rollover, no extra).
     */
    fun simulateMinimumsOnly(debts: List<Debt>): StrategyPayoffResult {
        val activeDebts = debts.filter { it.currentBalance > 0.01 }
        if (activeDebts.isEmpty()) {
            return StrategyPayoffResult(
                strategy = PayoffStrategy.AVALANCHE,
                totalMonths = 0,
                debtFreeDateMillis = System.currentTimeMillis(),
                totalInterestPaid = 0.0,
                totalInterestSavedVsMinimum = 0.0,
                milestones = emptyList()
            )
        }

        var maxMonths = 0
        var totalInterest = 0.0
        val milestones = mutableListOf<DebtPayoffMilestone>()

        for (d in activeDebts) {
            val months = monthsToPayoff(d.currentBalance, d.apr, d.minimumPayment)
            val interest = totalInterestPaid(d.currentBalance, d.apr, d.minimumPayment)
            if (months == Double.MAX_VALUE || months.isInfinite() || months.isNaN() || months > 600.0) {
                maxMonths = -1
                totalInterest += if (interest != Double.MAX_VALUE && !interest.isInfinite() && !interest.isNaN()) interest else d.currentBalance * 2.0
            } else {
                val mInt = months.roundToInt()
                if (maxMonths != -1 && mInt > maxMonths) {
                    maxMonths = mInt
                }
                totalInterest += interest
                milestones.add(
                    DebtPayoffMilestone(
                        debtId = d.id,
                        debtName = d.name,
                        payoffMonth = mInt,
                        payoffDateMillis = payoffDateMillis(months),
                        totalInterestPaid = interest
                    )
                )
            }
        }

        return StrategyPayoffResult(
            strategy = PayoffStrategy.AVALANCHE,
            totalMonths = maxMonths,
            debtFreeDateMillis = if (maxMonths >= 0) payoffDateMillis(maxMonths.toDouble()) else Long.MAX_VALUE,
            totalInterestPaid = totalInterest,
            totalInterestSavedVsMinimum = 0.0,
            milestones = milestones.sortedBy { it.payoffMonth }
        )
    }

    /**
     * Generates a side-by-side comparison of Minimums Only vs Avalanche vs Snowball.
     */
    fun compareMultiDebtPayoff(debts: List<Debt>, extraMonthlyPayment: Double): MultiDebtComparison {
        val minResult = simulateMinimumsOnly(debts)
        val avalancheRaw = simulateStrategy(debts, extraMonthlyPayment, PayoffStrategy.AVALANCHE)
        val snowballRaw = simulateStrategy(debts, extraMonthlyPayment, PayoffStrategy.SNOWBALL)

        val avalancheSaved = (minResult.totalInterestPaid - avalancheRaw.totalInterestPaid).coerceAtLeast(0.0)
        val snowballSaved = (minResult.totalInterestPaid - snowballRaw.totalInterestPaid).coerceAtLeast(0.0)

        val avalancheResult = avalancheRaw.copy(totalInterestSavedVsMinimum = avalancheSaved)
        val snowballResult = snowballRaw.copy(totalInterestSavedVsMinimum = snowballSaved)

        return MultiDebtComparison(
            minimumOnlyTotalMonths = minResult.totalMonths,
            minimumOnlyDebtFreeDateMillis = minResult.debtFreeDateMillis,
            minimumOnlyTotalInterest = minResult.totalInterestPaid,
            avalancheResult = avalancheResult,
            snowballResult = snowballResult,
            extraMonthlyPayment = extraMonthlyPayment
        )
    }
}
