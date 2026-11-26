package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
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
        currentDate: LocalDate = LocalDate.now(),
        currentTakeHomePay: Double = config.typicalTakeHomePay,
        baselineTakeHomePay: Double = config.typicalTakeHomePay,
        isCurrentPeriodOvertime: Boolean = false,
        currentPeriodOvertimeHours: Double = 0.0
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

        // Baseline safe to spend (standard recurring cushion)
        val baselineSafeToSpend = baselineTakeHomePay - perPaycheckReserve
        val effectiveIsCurrentPeriodOvertime = isCurrentPeriodOvertime || (currentTakeHomePay > baselineTakeHomePay + 0.01)

        // Anchor payday & upcoming pay dates
        val anchorPayDate = LocalDate.ofEpochDay(config.anchorPayDateEpochDay)
        val currentPayDate = calculateNextPayDate(anchorPayDate, config.frequency, currentDate)
        val followingPayDate = calculateFollowingPayDate(currentPayDate, config.frequency)
        val daysUntilPay = ChronoUnit.DAYS.between(currentDate, currentPayDate).coerceAtLeast(0)

        // Obligations (bills + debts) due in this exact pay window [currentPayDate, followingPayDate)
        val obligationsDueInPeriod = getScheduledObligationsInWindow(bills, debts, currentPayDate, followingPayDate)
        val windowBillsTotal = obligationsDueInPeriod.filter { it.type == ObligationType.BILL }.sumOf { it.amount }
        val windowDebtsTotal = obligationsDueInPeriod.filter { it.type == ObligationType.DEBT }.sumOf { it.amount }
        val windowObligationsTotal = windowBillsTotal + windowDebtsTotal

        val unpaidWindowBillsTotal = obligationsDueInPeriod.filter { it.type == ObligationType.BILL && !it.isPaid }.sumOf { it.amount }
        val unpaidWindowDebtsTotal = obligationsDueInPeriod.filter { it.type == ObligationType.DEBT && !it.isPaid }.sumOf { it.amount }
        val unpaidWindowObligationsTotal = unpaidWindowBillsTotal + unpaidWindowDebtsTotal

        // Variance between steady deposit and cash needed in this window
        val windowVariance = perPaycheckReserve - windowObligationsTotal

        // Recommended buffer in bills checking account
        val maxSingleObligation = maxOf(
            bills.maxOfOrNull { it.amount } ?: 0.0,
            debts.maxOfOrNull { it.minimumPayment } ?: 0.0
        )
        val recommendedBuffer = maxOf(maxSingleObligation, (windowObligationsTotal - perPaycheckReserve).coerceAtLeast(0.0))

        // Multi-pay period forward projection:
        // Period 1 uses currentTakeHomePay (actual hours logged).
        // Periods 2+ strictly use baselineTakeHomePay (standard 40 hrs/wk baseline).
        val upcomingPayPeriods = projectUpcomingPayPeriods(
            bills = bills,
            debts = debts,
            firstPayDate = currentPayDate,
            frequency = config.frequency,
            currentTakeHomePay = currentTakeHomePay,
            baselineTakeHomePay = baselineTakeHomePay,
            periodCount = 3
        )

        // Current pay period safe to spend (incorporates current hours/overtime/verified pay)
        val currentSafeToSpend = currentTakeHomePay - perPaycheckReserve
        val safeToSpend = currentSafeToSpend
        val isSurplus = safeToSpend >= 0.0
        val deficitAmount = if (!isSurplus) -safeToSpend else 0.0

        val daysInPeriod = config.frequency.daysInPeriod
        val dailySafeToSpend = if (daysInPeriod > 0) {
            maxOf(0.0, safeToSpend / daysInPeriod)
        } else {
            0.0
        }

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
            windowDebtsTotal = windowDebtsTotal,
            windowObligationsTotal = windowObligationsTotal,
            obligationsDueInPeriod = obligationsDueInPeriod,
            windowVariance = windowVariance,
            recommendedBuffer = recommendedBuffer,
            upcomingPayPeriods = upcomingPayPeriods,
            threePaycheckMonths = threePayMonths,
            currentTakeHomePay = currentTakeHomePay,
            baselineTakeHomePay = baselineTakeHomePay,
            currentSafeToSpend = currentSafeToSpend,
            baselineSafeToSpend = baselineSafeToSpend,
            isCurrentPeriodOvertime = effectiveIsCurrentPeriodOvertime,
            currentPeriodOvertimeHours = currentPeriodOvertimeHours,
            currentPeriodReserveHeldForFutureChecks = upcomingPayPeriods.firstOrNull()?.reserveHeldForFutureChecks ?: 0.0,
            unpaidWindowBillsTotal = unpaidWindowBillsTotal,
            unpaidWindowDebtsTotal = unpaidWindowDebtsTotal,
            unpaidWindowObligationsTotal = unpaidWindowObligationsTotal
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
     * Calculates the pay date preceding `payDate`.
     */
    fun calculatePreviousPayDate(payDate: LocalDate, frequency: PayFrequency): LocalDate {
        return when (frequency) {
            PayFrequency.BI_WEEKLY -> payDate.minusDays(14)
            PayFrequency.WEEKLY -> payDate.minusDays(7)
            PayFrequency.SEMI_MONTHLY -> {
                val day = payDate.dayOfMonth
                if (day > 15) {
                    val first = (day - 15).coerceAtLeast(1)
                    payDate.withDayOfMonth(first)
                } else {
                    val prevMonth = payDate.minusMonths(1)
                    val second = (day + 15).coerceAtMost(prevMonth.lengthOfMonth())
                    prevMonth.withDayOfMonth(second)
                }
            }
            PayFrequency.MONTHLY -> {
                val prevMonth = payDate.minusMonths(1)
                prevMonth.withDayOfMonth(minOf(payDate.dayOfMonth, prevMonth.lengthOfMonth()))
            }
        }
    }

    /**
     * Calculates the start date of the pay cycle that includes `currentDate`.
     * If today IS payday, the new cycle starts today.
     * Otherwise, the current cycle started on the payday immediately preceding the upcoming payday.
     */
    fun calculateCurrentCycleStartDate(
        anchorPayDate: LocalDate,
        frequency: PayFrequency,
        currentDate: LocalDate = LocalDate.now()
    ): LocalDate {
        val nextPayDate = calculateNextPayDate(anchorPayDate, frequency, currentDate)
        return if (nextPayDate == currentDate) {
            currentDate
        } else {
            calculatePreviousPayDate(nextPayDate, frequency)
        }
    }

    /**
     * Calculates the real-time Safe-to-Spend status for the current pay cycle,
     * deducting the actual spending incurred since the start of the cycle.
     */
    fun calculateSafeToSpendStatus(
        breakdown: PaycheckBreakdown,
        config: PaycheckConfig,
        spentThisCycle: Double,
        alertConfig: SpendingAlertConfig,
        currentDate: LocalDate = LocalDate.now()
    ): SafeToSpendStatus {
        val initialCushion = breakdown.currentSafeToSpend
        val remaining = initialCushion - spentThisCycle
        val isDeficit = remaining < 0.0
        val isLowWarning = !isDeficit && remaining <= alertConfig.lowCushionThreshold
        val alertLevel = when {
            isDeficit -> SafeToSpendAlertLevel.DEFICIT_STOP
            isLowWarning -> SafeToSpendAlertLevel.LOW_WARNING
            else -> SafeToSpendAlertLevel.HEALTHY
        }

        val spentPercentage = if (initialCushion > 0.0) {
            (spentThisCycle / initialCushion).toFloat().coerceIn(0f, 2f)
        } else {
            if (spentThisCycle > 0.0) 1f else 0f
        }

        val daysUntilPay = breakdown.daysUntilPay
        val dailyPaceRemaining = if (daysUntilPay > 0) {
            maxOf(0.0, remaining / daysUntilPay)
        } else {
            0.0
        }

        val anchorPayDate = LocalDate.ofEpochDay(config.anchorPayDateEpochDay)
        val cycleStartDate = calculateCurrentCycleStartDate(anchorPayDate, config.frequency, currentDate)
        val nextPayDate = calculateNextPayDate(anchorPayDate, config.frequency, currentDate)

        return SafeToSpendStatus(
            initialCushion = initialCushion,
            spentThisCycle = spentThisCycle,
            remainingSafeToSpend = remaining,
            spentPercentage = spentPercentage,
            daysUntilPay = daysUntilPay,
            dailyPaceRemaining = dailyPaceRemaining,
            cycleStartDate = cycleStartDate,
            nextPayDate = nextPayDate,
            isDeficit = isDeficit,
            isLowWarning = isLowWarning,
            warningThreshold = alertConfig.lowCushionThreshold,
            alertLevel = alertLevel
        )
    }

    /**
     * Returns the list of effective LocalDate paydays in the given year and month,
     * adjusting for US Federal Reserve bank holidays and weekends (shifting backward to preceding banking business day).
     */
    fun getEffectivePayDatesForMonth(
        year: Int,
        month: Int,
        anchorPayDate: LocalDate,
        frequency: PayFrequency
    ): List<LocalDate> {
        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        val effectivePayDates = mutableListOf<LocalDate>()

        when (frequency) {
            PayFrequency.WEEKLY, PayFrequency.BI_WEEKLY -> {
                val step = if (frequency == PayFrequency.WEEKLY) 7L else 14L
                val searchStart = monthStart.minusDays(14)
                val searchEnd = monthEnd.plusDays(14)

                val diff = searchStart.toEpochDay() - anchorPayDate.toEpochDay()
                val rem = Math.floorMod(diff, step)
                val firstEpoch = if (rem == 0L) searchStart.toEpochDay() else searchStart.toEpochDay() + (step - rem)
                var cursor = LocalDate.ofEpochDay(firstEpoch)

                while (!cursor.isAfter(searchEnd)) {
                    val effective = BankHolidayUtil.adjustPayDateForBankHoliday(cursor)
                    if (effective.year == year && effective.monthValue == month) {
                        effectivePayDates.add(effective)
                    }
                    cursor = cursor.plusDays(step)
                }
            }
            PayFrequency.SEMI_MONTHLY -> {
                val searchMonths = listOf(
                    YearMonth.of(year, month).minusMonths(1),
                    YearMonth.of(year, month),
                    YearMonth.of(year, month).plusMonths(1)
                )
                val anchorDay = anchorPayDate.dayOfMonth
                val d1Day = if (anchorDay <= 15) anchorDay else (anchorDay - 15).coerceAtLeast(1)
                val d2Day = d1Day + 15

                for (ym in searchMonths) {
                    val len = ym.lengthOfMonth()
                    val candidate1 = ym.atDay(minOf(d1Day, len))
                    val candidate2 = ym.atDay(minOf(d2Day, len))

                    for (cand in listOf(candidate1, candidate2)) {
                        val effective = BankHolidayUtil.adjustPayDateForBankHoliday(cand)
                        if (effective.year == year && effective.monthValue == month) {
                            effectivePayDates.add(effective)
                        }
                    }
                }
            }
            PayFrequency.MONTHLY -> {
                val searchMonths = listOf(
                    YearMonth.of(year, month).minusMonths(1),
                    YearMonth.of(year, month),
                    YearMonth.of(year, month).plusMonths(1)
                )
                for (ym in searchMonths) {
                    val len = ym.lengthOfMonth()
                    val cand = ym.atDay(minOf(anchorPayDate.dayOfMonth, len))
                    val effective = BankHolidayUtil.adjustPayDateForBankHoliday(cand)
                    if (effective.year == year && effective.monthValue == month) {
                        effectivePayDates.add(effective)
                    }
                }
            }
        }

        return effectivePayDates.distinct().sorted()
    }

    /**
     * Returns the set of day-of-month numbers (1..31) that are paydays in the given year and month.
     */
    fun getPayDatesForMonth(
        year: Int,
        month: Int,
        anchorPayDate: LocalDate,
        frequency: PayFrequency
    ): Set<Int> {
        return getEffectivePayDatesForMonth(year, month, anchorPayDate, frequency)
            .map { it.dayOfMonth }
            .toSet()
    }

    /**
     * Finds which calendar months in `year` will have 3 bi-weekly pay dates from `anchorPayDate`.
     * Uses airtight modular arithmetic grounded in effective banking day adjustments.
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
            val months = mutableListOf<String>()
            for (m in 1..12) {
                val payDates = getEffectivePayDatesForMonth(targetYear, m, anchorPayDate, PayFrequency.BI_WEEKLY)
                if (payDates.size >= 3) {
                    months.add(LocalDate.of(targetYear, m, 1).format(formatter))
                }
            }
            return months
        }

        result.addAll(getThreePayMonthsForYear(year))

        // If later in the year (after August), also include next year's 3-paycheck months for forward visibility
        if (includeNextYearIfLate && currentDate.monthValue >= 9) {
            result.addAll(getThreePayMonthsForYear(year + 1))
        }

        return result
    }

    /**
     * Filters both bills and debts due between startDate and endDate (inclusive of startDate, exclusive of endDate)
     * and maps each to a concrete `UpcomingObligationItem` with its exact due date.
     */
    fun getScheduledObligationsInWindow(
        bills: List<Bill>,
        debts: List<Debt>,
        startDate: LocalDate,
        endDate: LocalDate,
        currentDate: LocalDate = LocalDate.now()
    ): List<UpcomingObligationItem> {
        val scheduled = mutableListOf<UpcomingObligationItem>()

        // 1. Process Bills
        for (bill in bills) {
            var curr = startDate
            while (curr.isBefore(endDate)) {
                val effectiveDueDay = minOf(bill.dueDayOfMonth, curr.lengthOfMonth())
                if (curr.dayOfMonth == effectiveDueDay) {
                    scheduled.add(
                        UpcomingObligationItem(
                            id = bill.id,
                            name = bill.name,
                            amount = bill.amount,
                            dueDayOfMonth = bill.dueDayOfMonth,
                            dueDate = curr,
                            type = ObligationType.BILL,
                            isPaid = bill.isPaidForDate(curr, currentDate),
                            subtitle = bill.category,
                            originalBill = bill
                        )
                    )
                }
                curr = curr.plusDays(1)
            }
        }

        // 2. Process Debts (only accounts with an active balance and minimum payment)
        for (debt in debts) {
            if (debt.currentBalance <= 0.0 || debt.minimumPayment <= 0.0) continue
            var curr = startDate
            while (curr.isBefore(endDate)) {
                val effectiveDueDay = minOf(debt.dueDayOfMonth, curr.lengthOfMonth())
                if (curr.dayOfMonth == effectiveDueDay) {
                    val debtTypeName = debt.type.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() }
                    scheduled.add(
                        UpcomingObligationItem(
                            id = debt.id,
                            name = debt.name,
                            amount = debt.minimumPayment,
                            dueDayOfMonth = debt.dueDayOfMonth,
                            dueDate = curr,
                            type = ObligationType.DEBT,
                            isPaid = debt.isPaidForDate(curr, currentDate),
                            subtitle = "$debtTypeName • Min Payment",
                            originalDebt = debt
                        )
                    )
                }
                curr = curr.plusDays(1)
            }
        }

        return scheduled.sortedBy { it.dueDate }
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
        return getScheduledObligationsInWindow(bills, emptyList(), startDate, endDate)
            .filter { it.type == ObligationType.BILL && it.originalBill != null }
            .map { UpcomingBillScheduleItem(it.originalBill!!, it.dueDate, it.isPaid) }
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
     * Projects forward multi-paycheck schedules (e.g. next 3 pay periods) with bills and debts mapped to each check.
     * Period 1 uses currentTakeHomePay (reflecting logged hours/overtime/verified pay).
     * Periods 2+ strictly use baselineTakeHomePay (reflecting standard 40 hrs/wk baseline).
     *
     * Deficit Smoothing:
     * If any future paycheck (e.g. Pay #3) has a deficit (obligations > take-home),
     * preceding paychecks (closest preceding check first) reserve funds to pre-fund that deficit.
     * The future check's safe-to-spend is brought up to $0.00 (covered), and the prior check's safe-to-spend
     * is reduced by the reserved amount so the user cannot overspend and be left stranded.
     */
    fun projectUpcomingPayPeriods(
        bills: List<Bill>,
        debts: List<Debt> = emptyList(),
        firstPayDate: LocalDate,
        frequency: PayFrequency,
        currentTakeHomePay: Double,
        baselineTakeHomePay: Double,
        periodCount: Int = 3
    ): List<PayPeriodSchedule> {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

        // Phase 1: Compute window obligations and raw safe-to-spend for each period
        data class RawPeriod(
            val periodIndex: Int,
            val payDate: LocalDate,
            val nextPayDate: LocalDate,
            val obligations: List<UpcomingObligationItem>,
            val totalObligations: Double,
            val takeHome: Double,
            val rawSafe: Double,
            val isCurrent: Boolean
        )

        val rawPeriods = mutableListOf<RawPeriod>()
        var start = firstPayDate

        for (i in 0 until periodCount) {
            val isCurrent = (i == 0)
            val takeHome = if (isCurrent) currentTakeHomePay else baselineTakeHomePay
            val next = calculateFollowingPayDate(start, frequency)
            val windowObligations = getScheduledObligationsInWindow(bills, debts, start, next)
            val totalObligations = windowObligations.sumOf { it.amount }
            val unpaidTotalObligations = windowObligations.filter { !it.isPaid }.sumOf { it.amount }
            val rawSafe = takeHome - unpaidTotalObligations

            rawPeriods.add(
                RawPeriod(
                    periodIndex = i + 1,
                    payDate = start,
                    nextPayDate = next,
                    obligations = windowObligations,
                    totalObligations = totalObligations,
                    takeHome = takeHome,
                    rawSafe = rawSafe,
                    isCurrent = isCurrent
                )
            )
            start = next
        }

        // Phase 2: Backward Deficit Smoothing
        // If a future paycheck (e.g. Pay #3) has rawSafe < 0, earlier paychecks (closest preceding check first)
        // reserve funds to pre-fund that deficit so the user does not overspend and be left with unpaid bills.
        class DeficitRequirement(val targetPeriodIndex: Int, var amountNeeded: Double)

        val pendingDeficits = mutableListOf<DeficitRequirement>()
        val reserveHeldForFuture = DoubleArray(periodCount) { 0.0 }
        val coveredDeficitByPrior = DoubleArray(periodCount) { 0.0 }
        val fundingBreakdown = Array(periodCount) { mutableListOf<Pair<Int, Double>>() }
        val fundedByBreakdown = Array(periodCount) { mutableListOf<Pair<Int, Double>>() }

        // Find deficits in chronological order from future to present
        for (i in periodCount - 1 downTo 0) {
            val raw = rawPeriods[i].rawSafe
            if (raw < 0.0) {
                pendingDeficits.add(DeficitRequirement(rawPeriods[i].periodIndex, -raw))
            }
        }

        // Iterate backwards from period N-1 down to 0, absorbing pending deficits with available surplus
        for (i in periodCount - 1 downTo 0) {
            val raw = rawPeriods[i].rawSafe
            if (raw > 0.0) {
                var availableSurplus = raw
                // Fund pending deficits for periods strictly AFTER period i (earliest future period first)
                val relevantDeficits = pendingDeficits
                    .filter { it.targetPeriodIndex > rawPeriods[i].periodIndex && it.amountNeeded > 0.001 }
                    .sortedBy { it.targetPeriodIndex }

                for (def in relevantDeficits) {
                    if (availableSurplus <= 0.001) break
                    val allocation = minOf(availableSurplus, def.amountNeeded)
                    availableSurplus -= allocation
                    def.amountNeeded -= allocation
                    reserveHeldForFuture[i] += allocation
                    val targetIdx = def.targetPeriodIndex - 1
                    coveredDeficitByPrior[targetIdx] += allocation
                    fundingBreakdown[i].add(Pair(def.targetPeriodIndex, allocation))
                    fundedByBreakdown[targetIdx].add(Pair(rawPeriods[i].periodIndex, allocation))
                }
            }
        }

        // Phase 3: Construct adjusted PayPeriodSchedules with notes
        val result = mutableListOf<PayPeriodSchedule>()
        for (i in 0 until periodCount) {
            val p = rawPeriods[i]
            val reserveHeld = reserveHeldForFuture[i]
            val coveredDeficit = coveredDeficitByPrior[i]

            val adjustedSafe = if (p.rawSafe >= 0.0) {
                p.rawSafe - reserveHeld
            } else {
                p.rawSafe + coveredDeficit
            }

            // Generate clear, user-friendly deficit notes
            val note = when {
                reserveHeld > 0.001 -> {
                    val targets = fundingBreakdown[i]
                    if (targets.size == 1) {
                        "(-${currencyFormat.format(reserveHeld)} held for Pay #${targets[0].first})"
                    } else {
                        "(-${currencyFormat.format(reserveHeld)} held for future bills)"
                    }
                }
                coveredDeficit > 0.001 -> {
                    val sources = fundedByBreakdown[i]
                    if (adjustedSafe >= -0.001) {
                        if (sources.size == 1) {
                            "(+${currencyFormat.format(coveredDeficit)} from Pay #${sources[0].first})"
                        } else {
                            "(+${currencyFormat.format(coveredDeficit)} from prior checks)"
                        }
                    } else {
                        "(${currencyFormat.format(adjustedSafe)} remaining deficit)"
                    }
                }
                adjustedSafe < -0.001 -> {
                    "(Unfunded deficit ${currencyFormat.format(adjustedSafe)})"
                }
                else -> null
            }

            result.add(
                PayPeriodSchedule(
                    periodIndex = p.periodIndex,
                    payDate = p.payDate,
                    nextPayDate = p.nextPayDate,
                    obligations = p.obligations,
                    totalObligations = p.totalObligations,
                    safeToSpend = adjustedSafe,
                    projectedTakeHomePay = p.takeHome,
                    isCurrentPeriod = p.isCurrent,
                    rawSafeToSpend = p.rawSafe,
                    reserveHeldForFutureChecks = reserveHeld,
                    coveredDeficitByPriorChecks = coveredDeficit,
                    deficitNote = note
                )
            )
        }

        return result
    }

    /**
     * Backwards-compatible overload using single take-home pay.
     */
    fun projectUpcomingPayPeriods(
        bills: List<Bill>,
        debts: List<Debt> = emptyList(),
        firstPayDate: LocalDate,
        frequency: PayFrequency,
        takeHomePay: Double,
        periodCount: Int = 3
    ): List<PayPeriodSchedule> = projectUpcomingPayPeriods(
        bills = bills,
        debts = debts,
        firstPayDate = firstPayDate,
        frequency = frequency,
        currentTakeHomePay = takeHomePay,
        baselineTakeHomePay = takeHomePay,
        periodCount = periodCount
    )

    /**
     * Finds the next 3-paycheck month (or 5-paycheck month for weekly) starting from [fromDate].
     * If the current month is an active 3-paycheck month and [fromDate] has not passed its final payday,
     * returns countdown with isCurrentMonth = true.
     */
    fun calculateThreePaycheckCountdown(
        anchorPayDate: LocalDate,
        frequency: PayFrequency,
        fromDate: LocalDate = LocalDate.now()
    ): ThreePaycheckMonthCountdown? {
        if (frequency != PayFrequency.BI_WEEKLY && frequency != PayFrequency.WEEKLY) {
            return null
        }
        val targetThreshold = if (frequency == PayFrequency.BI_WEEKLY) 3 else 5
        val startYearMonth = YearMonth.from(fromDate)
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

        for (i in 0..24) {
            val ym = startYearMonth.plusMonths(i.toLong())
            val payDays = getPayDatesForMonth(ym.year, ym.monthValue, anchorPayDate, frequency).sorted()
            if (payDays.size >= targetThreshold) {
                // If checking the current month
                if (i == 0) {
                    val lastPayDate = ym.atDay(payDays.last())
                    if (!fromDate.isAfter(lastPayDate)) {
                        val daysRemaining = ChronoUnit.DAYS.between(fromDate, lastPayDate).coerceAtLeast(0)
                        return ThreePaycheckMonthCountdown(
                            targetYearMonth = ym,
                            formattedMonth = ym.format(formatter),
                            monthsRemaining = 0L,
                            daysRemaining = daysRemaining,
                            isCurrentMonth = true,
                            payDates = payDays
                        )
                    }
                    continue
                }

                val monthsRemaining = ChronoUnit.MONTHS.between(startYearMonth, ym)
                val daysRemaining = ChronoUnit.DAYS.between(fromDate, ym.atDay(payDays.first())).coerceAtLeast(0)
                return ThreePaycheckMonthCountdown(
                    targetYearMonth = ym,
                    formattedMonth = ym.format(formatter),
                    monthsRemaining = monthsRemaining,
                    daysRemaining = daysRemaining,
                    isCurrentMonth = false,
                    payDates = payDays
                )
            }
        }
        return null
    }

    /**
     * Calculates the total upcoming unpaid liabilities (unsettled bills and active debt minimums)
     * between [startDate] and [endDate] (inclusive of startDate, exclusive of endDate).
     */
    fun calculateUpcomingUnpaidLiabilities(
        bills: List<Bill>,
        debts: List<Debt> = emptyList(),
        startDate: LocalDate,
        endDate: LocalDate
    ): Double {
        val obligations = getScheduledObligationsInWindow(bills, debts, startDate, endDate)
        return obligations.filter { !it.isPaid }.sumOf { it.amount }
    }

    /**
     * Calculates monthly quick glimpse for a specified [year] and [month].
     * Includes estimated income based on scheduled paychecks count,
     * total bills, active debt minimums, and projected net surplus.
     */
    fun calculateMonthlyGlimpse(
        year: Int,
        month: Int,
        bills: List<Bill>,
        debts: List<Debt>,
        config: PaycheckConfig,
        anchorPayDate: LocalDate,
        currentTakeHomePay: Double = config.typicalTakeHomePay
    ): MonthlyGlimpse {
        val payDays = getPayDatesForMonth(year, month, anchorPayDate, config.frequency)
        val isCurrentMonth = LocalDate.now().year == year && LocalDate.now().monthValue == month
        val estimatedIncome = if (isCurrentMonth) {
            if (payDays.isNotEmpty()) {
                currentTakeHomePay + (payDays.size - 1) * config.typicalTakeHomePay
            } else {
                0.0
            }
        } else {
            payDays.size * config.typicalTakeHomePay
        }

        val totalBills = bills.sumOf { it.amount }
        val totalDebts = debts.filter { it.currentBalance > 0.0 }.sumOf { it.minimumPayment }
        val totalObligations = totalBills + totalDebts
        val netSurplus = estimatedIncome - totalObligations

        val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
        val unpaidBills = bills.filter {
            val dueDay = minOf(it.dueDayOfMonth, daysInMonth)
            val dueDate = LocalDate.of(year, month, dueDay)
            !it.isPaidForDate(dueDate)
        }.sumOf { it.amount }

        val unpaidDebts = debts.filter {
            val dueDay = minOf(it.dueDayOfMonth, daysInMonth)
            val dueDate = LocalDate.of(year, month, dueDay)
            it.currentBalance > 0.0 && !it.isPaidForDate(dueDate)
        }.sumOf { it.minimumPayment }
        val unpaidObligations = unpaidBills + unpaidDebts

        return MonthlyGlimpse(
            year = year,
            month = month,
            payDaysCount = payDays.size,
            estimatedIncome = estimatedIncome,
            totalBills = totalBills,
            totalDebts = totalDebts,
            totalObligations = totalObligations,
            netSurplus = netSurplus,
            unpaidBills = unpaidBills,
            unpaidDebts = unpaidDebts,
            unpaidObligations = unpaidObligations
        )
    }

    /**
     * Calculates multi-month rollover outlook:
     * - Month 1 (Current Month):
     *     projectedEndOfMonthBuffer = currentBalance + remainingPaychecksThisMonth - remainingUnpaidBillsThisMonth
     * - Month 2+ (Future Months):
     *     projectedEndOfMonthBuffer = priorMonthProjectedBuffer + scheduledPaychecksInMonth - scheduledBillsInMonth
     *
     * "Projected Income" strictly sums unreceived paychecks scheduled between today and the end of the selected month
     * (with US Federal Reserve bank holiday shifts).
     */
    fun calculateMultiMonthOutlook(
        currentBalance: Double,
        bills: List<Bill>,
        debts: List<Debt>,
        config: PaycheckConfig,
        takeHomePay: Double,
        targetMonthOffset: Int,
        currentDate: LocalDate = LocalDate.now(),
        paidOccurrenceEpochDays: Set<Long> = emptySet()
    ): MultiMonthOutlookResult {
        var runningBuffer = currentBalance
        val startYm = YearMonth.from(currentDate)

        var result: MultiMonthOutlookResult? = null

        for (offset in 0..maxOf(0, targetMonthOffset)) {
            val ym = startYm.plusMonths(offset.toLong())
            val year = ym.year
            val month = ym.monthValue
            val daysInMonth = ym.lengthOfMonth()
            val isCurrentMonth = (offset == 0)

            // 1. Paychecks for this month with bank holiday adjustment
            val anchorPayDate = LocalDate.ofEpochDay(config.anchorPayDateEpochDay)
            val allMonthPayDates = getEffectivePayDatesForMonth(year, month, anchorPayDate, config.frequency)
            val payDatesInScope = if (isCurrentMonth) {
                allMonthPayDates.filter { !it.isBefore(currentDate) }
            } else {
                allMonthPayDates
            }

            val payEvents = payDatesInScope.map { date ->
                MonthlyEventItem(
                    id = "payday_${date}",
                    title = "Paycheck",
                    subtitle = "Scheduled Income",
                    date = date,
                    amount = takeHomePay,
                    isIncome = true,
                    isPaid = false,
                    originalBill = null,
                    originalDebt = null
                )
            }
            val expectedInflows = payEvents.sumOf { it.amount }

            // 2. Bills & Debts in this month
            val obligationEvents = mutableListOf<MonthlyEventItem>()
            for (bill in bills) {
                val dueDay = minOf(bill.dueDayOfMonth, daysInMonth)
                val dueDate = LocalDate.of(year, month, dueDay)
                val isPaid = if (paidOccurrenceEpochDays.isNotEmpty()) {
                    paidOccurrenceEpochDays.contains(dueDate.toEpochDay())
                } else {
                    bill.isPaidForDate(dueDate, currentDate)
                }
                val isConsideredPaid = if (isCurrentMonth) isPaid else false
                obligationEvents.add(
                    MonthlyEventItem(
                        id = "bill_${bill.id}_${dueDate}",
                        title = bill.name,
                        subtitle = bill.category,
                        date = dueDate,
                        amount = bill.amount,
                        isIncome = false,
                        isPaid = isConsideredPaid,
                        originalBill = bill,
                        originalDebt = null
                    )
                )
            }

            for (debt in debts) {
                if (debt.currentBalance <= 0.0 || debt.minimumPayment <= 0.0) continue
                val dueDay = minOf(debt.dueDayOfMonth, daysInMonth)
                val dueDate = LocalDate.of(year, month, dueDay)
                val isPaid = debt.isPaidForDate(dueDate, currentDate)
                val isConsideredPaid = if (isCurrentMonth) isPaid else false
                val debtTypeName = debt.type.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() }
                obligationEvents.add(
                    MonthlyEventItem(
                        id = "debt_${debt.id}_${dueDate}",
                        title = debt.name,
                        subtitle = "$debtTypeName • Min Payment",
                        date = dueDate,
                        amount = debt.minimumPayment,
                        isIncome = false,
                        isPaid = isConsideredPaid,
                        originalBill = null,
                        originalDebt = debt
                    )
                )
            }

            val remainingBills = if (isCurrentMonth) {
                obligationEvents.filter { !it.isPaid }.sumOf { it.amount }
            } else {
                bills.sumOf { it.amount } +
                        debts.filter { it.currentBalance > 0.0 }.sumOf { it.minimumPayment }
            }

            val startingBalanceForMonth = if (isCurrentMonth) currentBalance else runningBuffer
            val endOfMonthBuffer = startingBalanceForMonth + expectedInflows - remainingBills

            if (offset == targetMonthOffset) {
                val statusLevel = when {
                    endOfMonthBuffer < 0.0 -> OutlookStatusLevel.SHORTFALL
                    endOfMonthBuffer <= 250.0 -> OutlookStatusLevel.TIGHT
                    else -> OutlookStatusLevel.ON_TRACK
                }

                val bufferCoverageRatio = if (remainingBills > 0.0) {
                    (startingBalanceForMonth / remainingBills).coerceIn(0.0, 2.0).toFloat()
                } else {
                    1.0f
                }

                val allEvents = (payEvents + obligationEvents).sortedBy { it.date }
                val immediateNext = allEvents.firstOrNull { !it.isPaid && !it.date.isBefore(currentDate) }
                    ?: allEvents.firstOrNull { !it.isPaid }
                    ?: allEvents.firstOrNull()

                result = MultiMonthOutlookResult(
                    targetYearMonth = ym,
                    monthOffset = offset,
                    startingBalance = startingBalanceForMonth,
                    expectedInflows = expectedInflows,
                    remainingBills = remainingBills,
                    projectedEndOfMonthBuffer = endOfMonthBuffer,
                    statusLevel = statusLevel,
                    bufferCoverageRatio = bufferCoverageRatio,
                    chronologicalEvents = allEvents,
                    immediateNextEvent = immediateNext
                )
            }

            runningBuffer = endOfMonthBuffer
        }

        return result ?: MultiMonthOutlookResult(
            targetYearMonth = startYm,
            monthOffset = 0,
            startingBalance = currentBalance,
            expectedInflows = 0.0,
            remainingBills = 0.0,
            projectedEndOfMonthBuffer = currentBalance,
            statusLevel = OutlookStatusLevel.ON_TRACK,
            bufferCoverageRatio = 1.0f,
            chronologicalEvents = emptyList(),
            immediateNextEvent = null
        )
    }
}
