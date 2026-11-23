package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import java.time.LocalDate

enum class PayFrequency(
    val paychecksPerYear: Double,
    val daysInPeriod: Int,
    val displayName: String
) {
    WEEKLY(52.0, 7, "Weekly (52x/yr)"),
    BI_WEEKLY(26.0, 14, "Bi-Weekly (26x/yr)"),
    SEMI_MONTHLY(24.0, 15, "Semi-Monthly (24x/yr)"),
    MONTHLY(12.0, 30, "Monthly (12x/yr)")
}

data class PaycheckConfig(
    val frequency: PayFrequency = PayFrequency.BI_WEEKLY,
    val typicalTakeHomePay: Double = 1800.0,
    val nextPayDateEpochDay: Long = LocalDate.now().toEpochDay()
)

data class PaycheckBreakdown(
    val totalMonthlyBills: Double,
    val totalMonthlyDebtMinimums: Double,
    val totalMonthlyFixedNut: Double,
    val annualFixedNut: Double,
    val perPaycheckReserve: Double,
    val perPaycheckSafeToSpend: Double,
    val dailySafeToSpend: Double,
    val isSurplus: Boolean,
    val deficitAmount: Double,
    val threePaycheckMonths: List<String>,
    val upcomingBillsInPeriod: List<Bill>
)
