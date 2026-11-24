package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import java.text.NumberFormat
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
        // Current pay period safe to spend (incorporates current hours/overtime/verified pay)
        val currentSafeToSpend = currentTakeHomePay - perPaycheckReserve
        val effectiveIsCurrentPeriodOvertime = isCurrentPeriodOvertime || (currentTakeHomePay > baselineTakeHomePay + 0.01)

        val safeToSpend = currentSafeToSpend
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

        // Obligations (bills + debts) due in this exact pay window [currentPayDate, followingPayDate)
        val obligationsDueInPeriod = getScheduledObligationsInWindow(bills, debts, currentPayDate, followingPayDate)
        val windowBillsTotal = obligationsDueInPeriod.filter { it.type == ObligationType.BILL }.sumOf { it.amount }
        val windowDebtsTotal = obligationsDueInPeriod.filter { it.type == ObligationType.DEBT }.sumOf { it.amount }
        val windowObligationsTotal = windowBillsTotal + windowDebtsTotal

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
            currentPeriodReserveHeldForFutureChecks = upcomingPayPeriods.firstOrNull()?.reserveHeldForFutureChecks ?: 0.0
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
     * Returns the set of day-of-month numbers (1..31) that are paydays in the given year and month.
     */
    fun getPayDatesForMonth(
        year: Int,
        month: Int,
        anchorPayDate: LocalDate,
        frequency: PayFrequency
    ): Set<Int> {
        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        val payDays = mutableSetOf<Int>()

        when (frequency) {
            PayFrequency.WEEKLY, PayFrequency.BI_WEEKLY -> {
                val step = if (frequency == PayFrequency.WEEKLY) 7L else 14L
                val diff = monthStart.toEpochDay() - anchorPayDate.toEpochDay()
                val rem = Math.floorMod(diff, step)
                val firstEpoch = if (rem == 0L) monthStart.toEpochDay() else monthStart.toEpochDay() + (step - rem)
                var cursor = LocalDate.ofEpochDay(firstEpoch)
                while (!cursor.isAfter(monthEnd)) {
                    if (cursor.monthValue == month && cursor.year == year) {
                        payDays.add(cursor.dayOfMonth)
                    }
                    cursor = cursor.plusDays(step)
                }
            }
            PayFrequency.SEMI_MONTHLY -> {
                val anchorDay = anchorPayDate.dayOfMonth
                val d1 = if (anchorDay <= 15) anchorDay else (anchorDay - 15).coerceAtLeast(1)
                val d2 = (d1 + 15).coerceAtMost(monthEnd.dayOfMonth)
                payDays.add(minOf(d1, monthEnd.dayOfMonth))
                payDays.add(minOf(d2, monthEnd.dayOfMonth))
            }
            PayFrequency.MONTHLY -> {
                payDays.add(minOf(anchorPayDate.dayOfMonth, monthEnd.dayOfMonth))
            }
        }
        return payDays
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
     * Filters both bills and debts due between startDate and endDate (inclusive of startDate, exclusive of endDate)
     * and maps each to a concrete `UpcomingObligationItem` with its exact due date.
     */
    fun getScheduledObligationsInWindow(
        bills: List<Bill>,
        debts: List<Debt>,
        startDate: LocalDate,
        endDate: LocalDate
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
                            isPaid = bill.isPaidThisMonth,
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
                            isPaid = debt.isPaidThisMonth,
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
            val rawSafe = takeHome - totalObligations

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
}
