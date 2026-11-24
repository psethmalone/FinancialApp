package com.example.financehome.domain

import java.time.LocalDate

data class SpendingAlertConfig(
    val isAlertsEnabled: Boolean = true,
    val notifyOnEveryPurchase: Boolean = true,
    val lowCushionThreshold: Double = 100.0,
    val alertOnDeficit: Boolean = true,
    val dailyPaceWarningEnabled: Boolean = true
)

enum class SafeToSpendAlertLevel {
    HEALTHY,
    LOW_WARNING,
    DEFICIT_STOP
}

data class SafeToSpendStatus(
    val initialCushion: Double,
    val spentThisCycle: Double,
    val remainingSafeToSpend: Double,
    val spentPercentage: Float,
    val daysUntilPay: Long,
    val dailyPaceRemaining: Double,
    val cycleStartDate: LocalDate,
    val nextPayDate: LocalDate,
    val isDeficit: Boolean,
    val isLowWarning: Boolean,
    val warningThreshold: Double,
    val alertLevel: SafeToSpendAlertLevel
) {
    val initialSafeToSpend: Double get() = initialCushion
    val daysRemainingInCycle: Int get() = daysUntilPay.toInt()
    val dailyPace: Double get() = dailyPaceRemaining
    val lowThreshold: Double get() = warningThreshold

    companion object {
        fun empty(): SafeToSpendStatus = SafeToSpendStatus(
            initialCushion = 0.0,
            spentThisCycle = 0.0,
            remainingSafeToSpend = 0.0,
            spentPercentage = 0f,
            daysUntilPay = 0,
            dailyPaceRemaining = 0.0,
            cycleStartDate = LocalDate.now(),
            nextPayDate = LocalDate.now(),
            isDeficit = false,
            isLowWarning = false,
            warningThreshold = 100.0,
            alertLevel = SafeToSpendAlertLevel.HEALTHY
        )
    }
}
