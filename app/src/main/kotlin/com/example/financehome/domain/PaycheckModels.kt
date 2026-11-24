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
    val anchorPayDateEpochDay: Long = LocalDate.now().toEpochDay()
) {
    // Backwards compatibility property
    val nextPayDateEpochDay: Long get() = anchorPayDateEpochDay
}

data class UpcomingBillScheduleItem(
    val bill: Bill,
    val dueDate: LocalDate,
    val isPaid: Boolean = false
)

data class PayPeriodSchedule(
    val periodIndex: Int,
    val payDate: LocalDate,
    val nextPayDate: LocalDate,
    val bills: List<UpcomingBillScheduleItem>,
    val totalBills: Double,
    val safeToSpend: Double
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
    val currentPayDate: LocalDate,
    val followingPayDate: LocalDate,
    val daysUntilPay: Long,
    val windowBillsTotal: Double,
    val billsDueInPeriod: List<UpcomingBillScheduleItem>,
    val windowVariance: Double,
    val recommendedBuffer: Double,
    val upcomingPayPeriods: List<PayPeriodSchedule>,
    val threePaycheckMonths: List<String>
) {
    // Convenience property for backwards compatibility with any existing call site
    val upcomingBillsInPeriod: List<Bill> get() = billsDueInPeriod.map { it.bill }
}
