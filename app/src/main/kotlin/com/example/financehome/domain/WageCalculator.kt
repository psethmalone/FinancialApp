package com.example.financehome.domain

object WageCalculator {

    fun calculateEstimate(
        config: WageConfig,
        frequency: PayFrequency = PayFrequency.BI_WEEKLY
    ): WageEstimate {
        val (regW1, otW1) = splitRegularAndOvertime(config.week1Hours)
        val (regW2, otW2) = if (frequency == PayFrequency.WEEKLY) {
            Pair(0.0, 0.0)
        } else {
            splitRegularAndOvertime(config.week2Hours)
        }

        val totalRegular = regW1 + regW2
        val totalOvertime = otW1 + otW2
        val totalHours = if (frequency == PayFrequency.WEEKLY) config.week1Hours else (config.week1Hours + config.week2Hours)

        val regularPay = totalRegular * config.hourlyWage
        val overtimePay = totalOvertime * (config.hourlyWage * config.overtimeMultiplier)
        val grossPay = regularPay + overtimePay

        val deductionFraction = (config.estimatedDeductionPercent / 100.0).coerceIn(0.0, 1.0)
        val estimatedDeductions = grossPay * deductionFraction
        val estimatedNetPay = maxOf(0.0, grossPay - estimatedDeductions)

        val effectiveTakeHome = if (config.isPaycheckVerified && config.verifiedNetPay != null && config.verifiedNetPay > 0.0) {
            config.verifiedNetPay
        } else {
            estimatedNetPay
        }

        return WageEstimate(
            totalHours = totalHours,
            regularHours = totalRegular,
            overtimeHours = totalOvertime,
            grossPay = grossPay,
            estimatedDeductions = estimatedDeductions,
            estimatedNetPay = estimatedNetPay,
            isVerified = config.isPaycheckVerified,
            verifiedNetPay = config.verifiedNetPay,
            effectiveTakeHomePay = effectiveTakeHome
        )
    }

    /**
     * Calculates the baseline/standard paycheck take-home pay based on standard regular hours
     * (e.g. 40 hrs/wk, 80 hrs bi-weekly), completely free of temporary overtime.
     * This is used for all future pay periods and equalized annual reserve targets.
     */
    fun calculateBaselineEstimate(
        config: WageConfig,
        frequency: PayFrequency = PayFrequency.BI_WEEKLY
    ): WageEstimate {
        val stdWeekly = maxOf(0.0, config.standardWeeklyHours)
        val baselineHours = when (frequency) {
            PayFrequency.WEEKLY -> stdWeekly
            PayFrequency.BI_WEEKLY -> stdWeekly * 2.0
            PayFrequency.SEMI_MONTHLY -> (stdWeekly * 52.0) / 24.0
            PayFrequency.MONTHLY -> (stdWeekly * 52.0) / 12.0
        }

        val grossPay = baselineHours * config.hourlyWage
        val deductionFraction = (config.estimatedDeductionPercent / 100.0).coerceIn(0.0, 1.0)
        val estimatedDeductions = grossPay * deductionFraction
        val estimatedNetPay = maxOf(0.0, grossPay - estimatedDeductions)

        return WageEstimate(
            totalHours = baselineHours,
            regularHours = baselineHours,
            overtimeHours = 0.0,
            grossPay = grossPay,
            estimatedDeductions = estimatedDeductions,
            estimatedNetPay = estimatedNetPay,
            isVerified = false,
            verifiedNetPay = null,
            effectiveTakeHomePay = estimatedNetPay
        )
    }

    private fun splitRegularAndOvertime(hours: Double): Pair<Double, Double> {
        val clampedHours = maxOf(0.0, hours)
        return if (clampedHours > 40.0) {
            Pair(40.0, clampedHours - 40.0)
        } else {
            Pair(clampedHours, 0.0)
        }
    }
}
