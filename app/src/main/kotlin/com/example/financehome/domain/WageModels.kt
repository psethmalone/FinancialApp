package com.example.financehome.domain

data class WageConfig(
    val hourlyWage: Double = 25.0,
    val overtimeMultiplier: Double = 1.5,
    val estimatedDeductionPercent: Double = 18.0,
    val standardWeeklyHours: Double = 40.0,
    val week1Hours: Double = 40.0,
    val week2Hours: Double = 40.0,
    val isPaycheckVerified: Boolean = false,
    val verifiedNetPay: Double? = null,
    val lastVerifiedEpochDay: Long? = null,
    val autoSyncPaycheckPlanner: Boolean = true
)

data class WageEstimate(
    val totalHours: Double,
    val regularHours: Double,
    val overtimeHours: Double,
    val grossPay: Double,
    val estimatedDeductions: Double,
    val estimatedNetPay: Double,
    val isVerified: Boolean,
    val verifiedNetPay: Double?,
    val effectiveTakeHomePay: Double
)
