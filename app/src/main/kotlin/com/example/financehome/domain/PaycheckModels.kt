package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import java.time.LocalDate
import java.time.YearMonth

data class ThreePaycheckMonthCountdown(
    val targetYearMonth: YearMonth,
    val formattedMonth: String,
    val monthsRemaining: Long,
    val daysRemaining: Long,
    val isCurrentMonth: Boolean,
    val payDates: List<Int>
)

data class MonthlyGlimpse(
    val year: Int,
    val month: Int,
    val payDaysCount: Int,
    val estimatedIncome: Double,
    val totalBills: Double,
    val totalDebts: Double,
    val totalObligations: Double,
    val netSurplus: Double
)

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

enum class ObligationType { BILL, DEBT }

data class UpcomingObligationItem(
    val id: String,
    val name: String,
    val amount: Double,
    val dueDayOfMonth: Int,
    val dueDate: LocalDate,
    val type: ObligationType,
    val isPaid: Boolean = false,
    val subtitle: String = "",
    val originalBill: Bill? = null,
    val originalDebt: Debt? = null
)

data class UpcomingBillScheduleItem(
    val bill: Bill,
    val dueDate: LocalDate,
    val isPaid: Boolean = false
)

data class PayPeriodSchedule(
    val periodIndex: Int,
    val payDate: LocalDate,
    val nextPayDate: LocalDate,
    val obligations: List<UpcomingObligationItem>,
    val totalObligations: Double,
    val safeToSpend: Double,
    val projectedTakeHomePay: Double = 0.0,
    val isCurrentPeriod: Boolean = false,
    val rawSafeToSpend: Double = 0.0,
    val reserveHeldForFutureChecks: Double = 0.0,
    val coveredDeficitByPriorChecks: Double = 0.0,
    val deficitNote: String? = null
) {
    // Backwards compatibility properties
    val bills: List<UpcomingBillScheduleItem>
        get() = obligations.filter { it.type == ObligationType.BILL && it.originalBill != null }
            .map { UpcomingBillScheduleItem(it.originalBill!!, it.dueDate, it.isPaid) }
    val totalBills: Double get() = totalObligations
}

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
    val windowDebtsTotal: Double,
    val windowObligationsTotal: Double,
    val obligationsDueInPeriod: List<UpcomingObligationItem>,
    val windowVariance: Double,
    val recommendedBuffer: Double,
    val upcomingPayPeriods: List<PayPeriodSchedule>,
    val threePaycheckMonths: List<String>,
    val currentTakeHomePay: Double = 0.0,
    val baselineTakeHomePay: Double = 0.0,
    val currentSafeToSpend: Double = 0.0,
    val baselineSafeToSpend: Double = 0.0,
    val isCurrentPeriodOvertime: Boolean = false,
    val currentPeriodOvertimeHours: Double = 0.0,
    val currentPeriodReserveHeldForFutureChecks: Double = 0.0
) {
    // Backwards compatibility properties
    val billsDueInPeriod: List<UpcomingBillScheduleItem>
        get() = obligationsDueInPeriod
            .filter { it.type == ObligationType.BILL && it.originalBill != null }
            .map { UpcomingBillScheduleItem(it.originalBill!!, it.dueDate, it.isPaid) }

    val upcomingBillsInPeriod: List<Bill> get() = billsDueInPeriod.map { it.bill }
}
