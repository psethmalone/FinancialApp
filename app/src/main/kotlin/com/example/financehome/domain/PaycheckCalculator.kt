package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object PaycheckCalculator {

    /**
     * Calculates the equalized and window-matched paycheck allocation for fixed obligations.
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

        // Anchor payday & upcoming pay dates
        val anchorPayDate = LocalDate.ofEpochDay(config.anchorPayDateEpochDay)
        val currentPayDate = calculateNextPayDate(anchorPayDate, config.frequency, currentDate)
        val followingPayDate = calculateFollowingPayDate(currentPayDate, config.frequency)
        val daysUntilPay = ChronoUnit.DAYS.between(currentDate, currentPayDate).coerceAtLeast(0)

        // Bills due in this exact pay window [currentPayDate, followingPayDate)
        val billsDueInPeriod = getScheduledBillsInWindow(bills, currentPayDate, followingPayDate)
        val windowBillsTotal = billsDueInPeriod.sumOf { it.bill.amount }

        // Variance between steady deposit and cash needed in this window
        val windowVariance = perPaycheckReserve - windowBillsTotal

        // Recommended buffer in bills checking account
        val maxSingleBill = bills.maxOfOrNull { it.amount } ?: 0.0
        val recommendedBuffer = maxOf(maxSingleBill, (windowBillsTotal - perPaycheckReserve).coerceAtLeast(0.0))

        // Multi-pay period forward projection (next 3 pay periods)
        val upcomingPayPeriods = projectUpcomingPayPeriods(
            bills = bills,
            firstPayDate = currentPayDate,
            frequency = config.frequency,
            takeHomePay = config.typicalTakeHomePay,
            periodCount = 3
        )

        // 3-Paycheck Bonus Months (for bi-weekly schedules)
        val threePayMonths = if (config.frequency == PayFrequency.BI_WEEKLY) {
            findThreePaycheckMonths(currentDate.year, anchorPayDate, includeNextYearIfLate = true, currentDate = currentDate)
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
            currentPayDate = currentPayDate,
            followingPayDate = followingPayDate,
            daysUntilPay = daysUntilPay,
            windowBillsTotal = windowBillsTotal,
            billsDueInPeriod = billsDueInPeriod,
            windowVariance = windowVariance,
            recommendedBuffer = recommendedBuffer,
            upcomingPayPeriods = upcomingPayPeriods,
            threePaycheckMonths = threePayMonths
        )
    }

    /**
     * Calculates the next upcoming payday on or after `currentDate` based on an anchor payday.
     * Automatically advances past anchor dates to today's current cycle.
     */
    fun calculateNextPayDate(
        anchorPayDate: LocalDate,
        frequency: PayFrequency,
        currentDate: LocalDate = LocalDate.now()
    ): LocalDate {
        val daysDiff = currentDate.toEpochDay() - anchorPayDate.toEpochDay()

        return when (frequency) {
            PayFrequency.BI_WEEKLY -> {
                val rem = Math.floorMod(daysDiff, 14L)
                if (rem == 0L) currentDate else currentDate.plusDays(14L - rem)
            }
            PayFrequency.WEEKLY -> {
                val rem = Math.floorMod(daysDiff, 7L)
                if (rem == 0L) currentDate else currentDate.plusDays(7L - rem)
            }
            PayFrequency.SEMI_MONTHLY -> {
                // Usually 1st & 15th or 15th & Last Day. Anchor gives the baseline days of month.
                val anchorDay = anchorPayDate.dayOfMonth
                val secondPayDay = if (anchorDay <= 15) {
                    (anchorDay + 15).coerceAtMost(30)
                } else {
                    (anchorDay - 15).coerceAtLeast(1)
                }
                val d1 = minOf(anchorDay, secondPayDay)
                val d2 = maxOf(anchorDay, secondPayDay)

                val cDay = currentDate.dayOfMonth
                when {
                    cDay <= d1 -> currentDate.withDayOfMonth(minOf(d1, currentDate.lengthOfMonth()))
                    cDay <= d2 -> currentDate.withDayOfMonth(minOf(d2, currentDate.lengthOfMonth()))
                    else -> {
                        val nextMonth = currentDate.plusMonths(1)
                        nextMonth.withDayOfMonth(minOf(d1, nextMonth.lengthOfMonth()))
                    }
                }
            }
            PayFrequency.MONTHLY -> {
                val anchorDay = anchorPayDate.dayOfMonth
                if (currentDate.dayOfMonth <= anchorDay) {
                    currentDate.withDayOfMonth(minOf(anchorDay, currentDate.lengthOfMonth()))
                } else {
                    val nextMonth = currentDate.plusMonths(1)
                    nextMonth.withDayOfMonth(minOf(anchorDay, nextMonth.lengthOfMonth()))
                }
            }
        }
    }

    /**
     * Calculates the pay date following `payDate`.
     */
    fun calculateFollowingPayDate(payDate: LocalDate, frequency: PayFrequency): LocalDate {
        return when (frequency) {
            PayFrequency.BI_WEEKLY -> payDate.plusDays(14)
            PayFrequency.WEEKLY -> payDate.plusDays(7)
            PayFrequency.SEMI_MONTHLY -> {
                val anchorDay = payDate.dayOfMonth
                if (anchorDay <= 15) {
                    val second = (anchorDay + 15).coerceAtMost(payDate.lengthOfMonth())
                    payDate.withDayOfMonth(second)
                } else {
                    val nextMonth = payDate.plusMonths(1)
                    val first = (anchorDay - 15).coerceAtLeast(1)
                    nextMonth.withDayOfMonth(minOf(first, nextMonth.lengthOfMonth()))
                }
            }
            PayFrequency.MONTHLY -> {
                val nextMonth = payDate.plusMonths(1)
                nextMonth.withDayOfMonth(minOf(payDate.dayOfMonth, nextMonth.lengthOfMonth()))
            }
        }
    }

    /**
     * Finds which calendar months in `year` will have 3 bi-weekly pay dates from `anchorPayDate`.
     * Uses airtight modular arithmetic: finds the first pay date in `year` and collects all pay dates.
     */
    fun findThreePaycheckMonths(
        year: Int,
        anchorPayDate: LocalDate,
        includeNextYearIfLate: Boolean = false,
        currentDate: LocalDate = LocalDate.now()
    ): List<String> {
        val result = mutableListOf<String>()
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

        fun getThreePayMonthsForYear(targetYear: Int): List<String> {
            val jan1Epoch = LocalDate.of(targetYear, 1, 1).toEpochDay()
            val daysDiff = jan1Epoch - anchorPayDate.toEpochDay()
            val rem = Math.floorMod(daysDiff, 14L)
            val firstPayEpoch = if (rem == 0L) jan1Epoch else jan1Epoch + (14L - rem)

            var pay = LocalDate.ofEpochDay(firstPayEpoch)
            val payDates = mutableListOf<LocalDate>()
            while (pay.year == targetYear) {
                payDates.add(pay)
                pay = pay.plusDays(14)
            }

            return payDates.groupBy { it.month }
                .filter { it.value.size >= 3 }
                .map { entry ->
                    LocalDate.of(targetYear, entry.key, 1).format(formatter)
                }
        }

        result.addAll(getThreePayMonthsForYear(year))

        // If later in the year (after August), also include next year's 3-paycheck months for forward visibility
        if (includeNextYearIfLate && currentDate.monthValue >= 9) {
            result.addAll(getThreePayMonthsForYear(year + 1))
        }

        return result
    }

    /**
     * Filters bills due between startDate and endDate (inclusive of startDate, exclusive of endDate)
     * and maps each to a concrete `UpcomingBillScheduleItem` with its exact due date.
     */
    fun getScheduledBillsInWindow(
        bills: List<Bill>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<UpcomingBillScheduleItem> {
        val scheduled = mutableListOf<UpcomingBillScheduleItem>()

        for (bill in bills) {
            var curr = startDate
            while (curr.isBefore(endDate)) {
                val effectiveDueDay = minOf(bill.dueDayOfMonth, curr.lengthOfMonth())
                if (curr.dayOfMonth == effectiveDueDay) {
                    scheduled.add(
                        UpcomingBillScheduleItem(
                            bill = bill,
                            dueDate = curr,
                            isPaid = bill.isPaidThisMonth
                        )
                    )
                }
                curr = curr.plusDays(1)
            }
        }

        return scheduled.sortedBy { it.dueDate }
    }

    /**
     * Backwards-compatible overload returning List<Bill>.
     */
    fun getUpcomingBills(
        bills: List<Bill>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Bill> {
        return getScheduledBillsInWindow(bills, startDate, endDate).map { it.bill }
    }

    /**
     * Projects forward multi-paycheck schedules (e.g. next 3 pay periods) with bills mapped to each check.
     */
    fun projectUpcomingPayPeriods(
        bills: List<Bill>,
        firstPayDate: LocalDate,
        frequency: PayFrequency,
        takeHomePay: Double,
        periodCount: Int = 3
    ): List<PayPeriodSchedule> {
        val list = mutableListOf<PayPeriodSchedule>()
        var start = firstPayDate

        for (i in 0 until periodCount) {
            val next = calculateFollowingPayDate(start, frequency)
            val windowBills = getScheduledBillsInWindow(bills, start, next)
            val totalBills = windowBills.sumOf { it.bill.amount }
            val safeToSpend = takeHomePay - totalBills

            list.add(
                PayPeriodSchedule(
                    periodIndex = i + 1,
                    payDate = start,
                    nextPayDate = next,
                    bills = windowBills,
                    totalBills = totalBills,
                    safeToSpend = safeToSpend
                )
            )
            start = next
        }

        return list
    }
}
