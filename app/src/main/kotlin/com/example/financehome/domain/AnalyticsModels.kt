package com.example.financehome.domain

import androidx.compose.ui.graphics.Color
import java.time.LocalDate

enum class AnalyticsPeriod(val displayName: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_90_DAYS("Last 90 Days"),
    ALL_TIME("All Time")
}

data class CategorySlice(
    val category: String,
    val amount: Double,
    val percentage: Double, // 0.0 to 100.0
    val color: Color,
    val transactionCount: Int
)

data class CashFlowSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val netSavings: Double,
    val savingsRate: Double, // percentage e.g. 25.5
    val needsTotal: Double,
    val wantsTotal: Double,
    val needsPercentage: Double,
    val wantsPercentage: Double
)

data class WeeklyBarData(
    val weekLabel: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val spending: Double,
    val isCurrentWeek: Boolean
)

data class MonthlyTrendData(
    val monthLabel: String,
    val year: Int,
    val month: Int,
    val income: Double,
    val expense: Double,
    val net: Double
)

data class MerchantSpend(
    val merchant: String,
    val amount: Double,
    val count: Int
)

data class AnalyticsReport(
    val period: AnalyticsPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val cashFlow: CashFlowSummary,
    val categorySlices: List<CategorySlice>,
    val weeklyBars: List<WeeklyBarData>,
    val averageWeeklySpend: Double,
    val monthlyTrends: List<MonthlyTrendData>,
    val topMerchants: List<MerchantSpend>
)
