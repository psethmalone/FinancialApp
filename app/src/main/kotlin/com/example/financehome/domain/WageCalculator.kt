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

    private fun splitRegularAndOvertime(hours: Double): Pair<Double, Double> {
        val clampedHours = maxOf(0.0, hours)
        return if (clampedHours > 40.0) {
            Pair(40.0, clampedHours - 40.0)
        } else {
            Pair(clampedHours, 0.0)
        }
    }
}
