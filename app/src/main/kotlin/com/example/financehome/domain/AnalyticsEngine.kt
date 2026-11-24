package com.example.financehome.domain

import androidx.compose.ui.graphics.Color
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AnalyticsEngine {

    val CategoryColors = mapOf(
        "Groceries" to Color(0xFF2E7D32),       // Rich Green
        "Food & Dining" to Color(0xFFFF8F00),   // Amber/Orange
        "Dining" to Color(0xFFFF8F00),
        "Coffee" to Color(0xFF8D6E63),          // Warm Brown
        "Housing" to Color(0xFF303F9F),         // Deep Indigo
        "Rent" to Color(0xFF303F9F),
        "Utilities" to Color(0xFF0097A7),       // Cyan
        "Bills" to Color(0xFF0288D1),          // Light Blue
        "Transportation" to Color(0xFF00796B),  // Teal
        "Gas" to Color(0xFF00796B),
        "Shopping" to Color(0xFFC2185B),        // Pink
        "Entertainment" to Color(0xFF7B1FA2),   // Purple
        "Healthcare" to Color(0xFFD32F2F),      // Red
        "Health" to Color(0xFFD32F2F),
        "Other" to Color(0xFF546E7A)            // Blue Grey
    )

    private val FallbackPalette = listOf(
        Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
        Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFE53935),
        Color(0xFF3949AB), Color(0xFFF4511E), Color(0xFF7CB342),
        Color(0xFFD81B60), Color(0xFF00897B), Color(0xFF6D4C41)
    )

    fun getCategoryColor(category: String, index: Int = 0): Color {
        return CategoryColors[category]
            ?: CategoryColors.entries.firstOrNull { it.key.contains(category, ignoreCase = true) }?.value
            ?: FallbackPalette[index % FallbackPalette.size]
    }

    /**
     * Generates a comprehensive analytical report from the full list of transactions.
     */
    fun generateReport(
        transactions: List<Transaction>,
        period: AnalyticsPeriod,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AnalyticsReport {
        val (startDate, endDate) = computeDateRange(period, today, transactions, zoneId)

        // Map transactions with local dates
        val transactionsWithDates = transactions.map { t ->
            val date = Instant.ofEpochMilli(t.timestampMillis).atZone(zoneId).toLocalDate()
            t to date
        }

        // Filter for period
        val periodTransactions = transactionsWithDates.filter { (_, date) ->
            !date.isBefore(startDate) && !date.isAfter(endDate)
        }.map { it.first }

        // Cash flow summary
        val cashFlow = computeCashFlow(periodTransactions)

        // Category breakdown
        val categorySlices = computeCategorySlices(periodTransactions, cashFlow.totalExpense)

        // Weekly breakdown
        val (weeklyBars, avgWeekly) = computeWeeklyBars(transactionsWithDates, period, startDate, endDate, today)

        // Monthly trends across last 6 calendar months
        val monthlyTrends = computeMonthlyTrends(transactionsWithDates, today)

        // Top merchants
        val topMerchants = computeTopMerchants(periodTransactions)

        return AnalyticsReport(
            period = period,
            startDate = startDate,
            endDate = endDate,
            cashFlow = cashFlow,
            categorySlices = categorySlices,
            weeklyBars = weeklyBars,
            averageWeeklySpend = avgWeekly,
            monthlyTrends = monthlyTrends,
            topMerchants = topMerchants
        )
    }

    private fun computeDateRange(
        period: AnalyticsPeriod,
        today: LocalDate,
        transactions: List<Transaction>,
        zoneId: ZoneId
    ): Pair<LocalDate, LocalDate> {
        return when (period) {
            AnalyticsPeriod.THIS_MONTH -> {
                today.withDayOfMonth(1) to today
            }
            AnalyticsPeriod.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                lastMonth.withDayOfMonth(1) to lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
            }
            AnalyticsPeriod.LAST_30_DAYS -> {
                today.minusDays(29) to today
            }
            AnalyticsPeriod.LAST_90_DAYS -> {
                today.minusDays(89) to today
            }
            AnalyticsPeriod.ALL_TIME -> {
                val earliest = transactions.minOfOrNull {
                    Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).toLocalDate()
                } ?: today.minusMonths(6)
                earliest to today
            }
        }
    }

    private fun computeCashFlow(transactions: List<Transaction>): CashFlowSummary {
        var totalIncome = 0.0
        var totalExpense = 0.0
        var needsTotal = 0.0
        var wantsTotal = 0.0

        for (t in transactions) {
            if (t.type == TransactionType.CREDIT) {
                totalIncome += t.amount
            } else {
                totalExpense += t.amount
                if (t.isDiscretionary) {
                    wantsTotal += t.amount
                } else {
                    needsTotal += t.amount
                }
            }
        }

        val netSavings = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) {
            maxOf(0.0, (netSavings / totalIncome) * 100.0)
        } else {
            0.0
        }

        val needsPercentage = if (totalExpense > 0) (needsTotal / totalExpense) * 100.0 else 0.0
        val wantsPercentage = if (totalExpense > 0) (wantsTotal / totalExpense) * 100.0 else 0.0

        return CashFlowSummary(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            savingsRate = savingsRate,
            needsTotal = needsTotal,
            wantsTotal = wantsTotal,
            needsPercentage = needsPercentage,
            wantsPercentage = wantsPercentage
        )
    }

    private fun computeCategorySlices(
        transactions: List<Transaction>,
        totalExpense: Double
    ): List<CategorySlice> {
        val debits = transactions.filter { it.type == TransactionType.DEBIT }
        if (debits.isEmpty() || totalExpense <= 0.0) return emptyList()

        return debits.groupBy { it.category }
            .mapValues { entry ->
                val amount = entry.value.sumOf { it.amount }
                val count = entry.value.size
                amount to count
            }
            .toList()
            .sortedByDescending { it.second.first }
            .mapIndexed { index, (cat, pair) ->
                val (amount, count) = pair
                val percentage = (amount / totalExpense) * 100.0
                CategorySlice(
                    category = cat,
                    amount = amount,
                    percentage = percentage,
                    color = getCategoryColor(cat, index),
                    transactionCount = count
                )
            }
    }

    private fun computeWeeklyBars(
        transactionsWithDates: List<Pair<Transaction, LocalDate>>,
        period: AnalyticsPeriod,
        startDate: LocalDate,
        endDate: LocalDate,
        today: LocalDate
    ): Pair<List<WeeklyBarData>, Double> {
        val bars = mutableListOf<WeeklyBarData>()

        if (period == AnalyticsPeriod.THIS_MONTH || period == AnalyticsPeriod.LAST_MONTH) {
            // Divide month into 4 calendar week buckets: Days 1-7, 8-14, 15-21, 22-end
            val month = startDate.month
            val year = startDate.year
            val totalDays = startDate.lengthOfMonth()

            val weekRanges = listOf(
                1 to 7,
                8 to 14,
                15 to 21,
                22 to totalDays
            )

            weekRanges.forEachIndexed { idx, (startDay, endDay) ->
                val wStart = LocalDate.of(year, month, startDay)
                val wEnd = LocalDate.of(year, month, minOf(endDay, totalDays))

                val spend = transactionsWithDates
                    .filter { (t, date) -> t.type == TransactionType.DEBIT && !date.isBefore(wStart) && !date.isAfter(wEnd) }
                    .sumOf { it.first.amount }

                val isCurrent = !today.isBefore(wStart) && !today.isAfter(wEnd)

                bars.add(
                    WeeklyBarData(
                        weekLabel = "W${idx + 1} (${wStart.dayOfMonth}-${wEnd.dayOfMonth})",
                        startDate = wStart,
                        endDate = wEnd,
                        spending = spend,
                        isCurrentWeek = isCurrent
                    )
                )
            }
        } else {
            // 4 rolling 7-day chunks ending at endDate
            var currentEnd = endDate
            val numWeeks = if (period == AnalyticsPeriod.LAST_90_DAYS) 8 else 4

            val dtf = DateTimeFormatter.ofPattern("M/d", Locale.US)
            for (i in 0 until numWeeks) {
                val currentStart = currentEnd.minusDays(6)
                val spend = transactionsWithDates
                    .filter { (t, date) -> t.type == TransactionType.DEBIT && !date.isBefore(currentStart) && !date.isAfter(currentEnd) }
                    .sumOf { it.first.amount }

                val isCurrent = !today.isBefore(currentStart) && !today.isAfter(currentEnd)

                bars.add(
                    0, // prepend so oldest is on the left
                    WeeklyBarData(
                        weekLabel = "${currentStart.format(dtf)}-${currentEnd.format(dtf)}",
                        startDate = currentStart,
                        endDate = currentEnd,
                        spending = spend,
                        isCurrentWeek = isCurrent
                    )
                )
                currentEnd = currentStart.minusDays(1)
            }
        }

        val totalSpending = bars.sumOf { it.spending }
        val avgWeekly = if (bars.isNotEmpty()) totalSpending / bars.size else 0.0

        return bars to avgWeekly
    }

    private fun computeMonthlyTrends(
        transactionsWithDates: List<Pair<Transaction, LocalDate>>,
        today: LocalDate
    ): List<MonthlyTrendData> {
        val trends = mutableListOf<MonthlyTrendData>()
        val dtf = DateTimeFormatter.ofPattern("MMM", Locale.US)

        // Past 6 calendar months
        for (i in 5 downTo 0) {
            val ym = YearMonth.from(today).minusMonths(i.toLong())
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()

            val monthTxs = transactionsWithDates.filter { (_, date) ->
                !date.isBefore(start) && !date.isAfter(end)
            }

            var income = 0.0
            var expense = 0.0
            for ((t, _) in monthTxs) {
                if (t.type == TransactionType.CREDIT) {
                    income += t.amount
                } else {
                    expense += t.amount
                }
            }

            trends.add(
                MonthlyTrendData(
                    monthLabel = start.format(dtf),
                    year = ym.year,
                    month = ym.monthValue,
                    income = income,
                    expense = expense,
                    net = income - expense
                )
            )
        }

        return trends
    }

    private fun computeTopMerchants(transactions: List<Transaction>): List<MerchantSpend> {
        return transactions.filter { it.type == TransactionType.DEBIT }
            .groupBy { it.merchant }
            .map { (merchant, txList) ->
                MerchantSpend(
                    merchant = merchant,
                    amount = txList.sumOf { it.amount },
                    count = txList.size
                )
            }
            .sortedByDescending { it.amount }
            .take(5)
    }
}
