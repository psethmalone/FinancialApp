package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PaycheckCalculator {

    /**
     * Calculates the equalized paycheck allocation for fixed obligations.
     *
     * Equalized Reserve Formula:
     *   Annual Fixed Nut = (Monthly Bills + Monthly Minimum Debt Payments) * 12
     *   Per-Paycheck Reserve = Annual Fixed Nut / Paychecks Per Year
     *   Safe-To-Spend = Take-Home Pay - Per-Paycheck Reserve
     */
    fun calculateBreakdown(
        bills: List<Bill>,
        debts: List<Debt>,
        config: PaycheckConfig,
        currentDate: LocalDate = LocalDate.now()
    ): PaycheckBreakdown {
        val totalMonthlyBills = bills.sumOf { it.amount }
        val totalMonthlyDebtMinimums = debts.sumOf { it.minimumPayment }
        val totalMonthlyFixedNut = totalMonthlyBills + totalMonthlyDebtMinimums
        val annualFixedNut = totalMonthlyFixedNut * 12.0

        val perPaycheckReserve = if (config.frequency.paychecksPerYear > 0) {
            annualFixedNut / config.frequency.paychecksPerYear
        } else {
            0.0
        }

        val safeToSpend = config.typicalTakeHomePay - perPaycheckReserve
        val isSurplus = safeToSpend >= 0.0
        val deficitAmount = if (!isSurplus) -safeToSpend else 0.0

        val daysInPeriod = config.frequency.daysInPeriod
        val dailySafeToSpend = if (daysInPeriod > 0) {
            maxOf(0.0, safeToSpend / daysInPeriod)
        } else {
            0.0
        }

        val nextPayDate = LocalDate.ofEpochDay(config.nextPayDateEpochDay)
        val endOfPayPeriod = nextPayDate.plusDays(daysInPeriod.toLong())

        val upcomingBills = getUpcomingBills(bills, nextPayDate, endOfPayPeriod)
        val threePayMonths = if (config.frequency == PayFrequency.BI_WEEKLY) {
            findThreePaycheckMonths(nextPayDate.year, nextPayDate)
        } else {
            emptyList()
        }

        return PaycheckBreakdown(
            totalMonthlyBills = totalMonthlyBills,
            totalMonthlyDebtMinimums = totalMonthlyDebtMinimums,
            totalMonthlyFixedNut = totalMonthlyFixedNut,
            annualFixedNut = annualFixedNut,
            perPaycheckReserve = perPaycheckReserve,
            perPaycheckSafeToSpend = safeToSpend,
            dailySafeToSpend = dailySafeToSpend,
            isSurplus = isSurplus,
            deficitAmount = deficitAmount,
            threePaycheckMonths = threePayMonths,
            upcomingBillsInPeriod = upcomingBills
        )
    }

    /**
     * Filters bills due between startDate and endDate (inclusive of startDate, exclusive of endDate).
     */
    fun getUpcomingBills(
        bills: List<Bill>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Bill> {
        val daysInWindow = mutableSetOf<Int>()
        var curr = startDate
        while (curr.isBefore(endDate)) {
            daysInWindow.add(curr.dayOfMonth)
            curr = curr.plusDays(1)
        }
        return bills.filter { it.dueDayOfMonth in daysInWindow }
            .sortedBy { it.dueDayOfMonth }
    }

    /**
     * Determines which calendar months in a year will have 3 bi-weekly pay dates.
     */
    fun findThreePaycheckMonths(
        year: Int,
        anchorPayDate: LocalDate
    ): List<String> {
        val payDates = mutableListOf<LocalDate>()
        var d = anchorPayDate

        // Rewind to before start of year
        while (d.year >= year) {
            d = d.minusDays(14)
        }
        d = d.plusDays(14)

        // Advance through the target year
        while (d.year <= year) {
            if (d.year == year) {
                payDates.add(d)
            }
            d = d.plusDays(14)
        }

        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
        return payDates.groupBy { it.month }
            .filter { it.value.size >= 3 }
            .map { entry ->
                LocalDate.of(year, entry.key, 1).format(formatter)
            }
    }
}
