package com.example.financehome.domain

import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AnalyticsEngineTest {

    private val zoneId = ZoneOffset.UTC
    private val testToday = LocalDate.of(2026, 5, 20)

    private fun createTx(
        id: String,
        amount: Double,
        merchant: String,
        category: String,
        type: TransactionType,
        date: LocalDate,
        isDiscretionary: Boolean = false
    ): Transaction {
        return Transaction(
            id = id,
            amount = amount,
            merchant = merchant,
            category = category,
            type = type,
            source = TransactionSource.MANUAL,
            timestampMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            rawNotificationText = null,
            notes = null,
            isDiscretionary = isDiscretionary
        )
    }

    @Test
    fun testCashFlowSummary_calculatesIncomeExpenseAndSavingsRate() {
        val transactions = listOf(
            createTx("1", 3000.0, "Employer", "Income", TransactionType.CREDIT, LocalDate.of(2026, 5, 1)),
            createTx("2", 1200.0, "Rent", "Housing", TransactionType.DEBIT, LocalDate.of(2026, 5, 2), isDiscretionary = false),
            createTx("3", 300.0, "Walmart", "Groceries", TransactionType.DEBIT, LocalDate.of(2026, 5, 5), isDiscretionary = false),
            createTx("4", 150.0, "McDonald's", "Dining", TransactionType.DEBIT, LocalDate.of(2026, 5, 10), isDiscretionary = true)
        )

        val report = AnalyticsEngine.generateReport(transactions, AnalyticsPeriod.THIS_MONTH, testToday, zoneId)

        assertEquals(3000.0, report.cashFlow.totalIncome, 0.01)
        assertEquals(1650.0, report.cashFlow.totalExpense, 0.01)
        assertEquals(1350.0, report.cashFlow.netSavings, 0.01)
        // Savings rate = (1350 / 3000) * 100 = 45.0%
        assertEquals(45.0, report.cashFlow.savingsRate, 0.01)
        assertEquals(1500.0, report.cashFlow.needsTotal, 0.01)
        assertEquals(150.0, report.cashFlow.wantsTotal, 0.01)
        assertEquals((1500.0 / 1650.0) * 100.0, report.cashFlow.needsPercentage, 0.05)
    }

    @Test
    fun testCategorySlices_calculatesPercentagesAndSorts() {
        val transactions = listOf(
            createTx("1", 1000.0, "Landlord", "Housing", TransactionType.DEBIT, LocalDate.of(2026, 5, 1)),
            createTx("2", 500.0, "Kroger", "Groceries", TransactionType.DEBIT, LocalDate.of(2026, 5, 5)),
            createTx("3", 500.0, "Target", "Groceries", TransactionType.DEBIT, LocalDate.of(2026, 5, 12))
        )

        val report = AnalyticsEngine.generateReport(transactions, AnalyticsPeriod.THIS_MONTH, testToday, zoneId)

        assertEquals(2, report.categorySlices.size)
        // Total = 2000. Both Housing (1000) and Groceries (1000) are 50%
        assertEquals(1000.0, report.categorySlices[0].amount, 0.01)
        assertEquals(50.0, report.categorySlices[0].percentage, 0.01)
        assertEquals(1000.0, report.categorySlices[1].amount, 0.01)
        assertEquals(50.0, report.categorySlices[1].percentage, 0.01)
        // Groceries count = 2
        val groceriesSlice = report.categorySlices.first { it.category == "Groceries" }
        assertEquals(2, groceriesSlice.transactionCount)
    }

    @Test
    fun testWeeklyBars_computesBucketsAndAverage() {
        val transactions = listOf(
            createTx("1", 200.0, "Target", "Shopping", TransactionType.DEBIT, LocalDate.of(2026, 5, 3)), // W1
            createTx("2", 400.0, "Costco", "Groceries", TransactionType.DEBIT, LocalDate.of(2026, 5, 10)), // W2
            createTx("3", 600.0, "Apple", "Shopping", TransactionType.DEBIT, LocalDate.of(2026, 5, 18)) // W3
        )

        val report = AnalyticsEngine.generateReport(transactions, AnalyticsPeriod.THIS_MONTH, testToday, zoneId)

        assertEquals(4, report.weeklyBars.size)
        assertEquals(200.0, report.weeklyBars[0].spending, 0.01) // W1
        assertEquals(400.0, report.weeklyBars[1].spending, 0.01) // W2
        assertEquals(600.0, report.weeklyBars[2].spending, 0.01) // W3
        assertEquals(0.0, report.weeklyBars[3].spending, 0.01)   // W4
        // Avg = (200 + 400 + 600 + 0) / 4 = 300.0
        assertEquals(300.0, report.averageWeeklySpend, 0.01)
        // Today is May 20 (W3: 15-21)
        assertTrue(report.weeklyBars[2].isCurrentWeek)
        assertFalse(report.weeklyBars[0].isCurrentWeek)
    }

    @Test
    fun testMonthlyTrends_computesLast6Months() {
        val transactions = listOf(
            createTx("1", 100.0, "Gas", "Gas", TransactionType.DEBIT, LocalDate.of(2026, 1, 15)),
            createTx("2", 200.0, "Gas", "Gas", TransactionType.DEBIT, LocalDate.of(2026, 2, 15)),
            createTx("3", 300.0, "Gas", "Gas", TransactionType.DEBIT, LocalDate.of(2026, 3, 15)),
            createTx("4", 400.0, "Gas", "Gas", TransactionType.DEBIT, LocalDate.of(2026, 4, 15)),
            createTx("5", 500.0, "Gas", "Gas", TransactionType.DEBIT, LocalDate.of(2026, 5, 15))
        )

        val report = AnalyticsEngine.generateReport(transactions, AnalyticsPeriod.THIS_MONTH, testToday, zoneId)

        assertEquals(6, report.monthlyTrends.size)
        val mayTrend = report.monthlyTrends.last()
        assertEquals("May", mayTrend.monthLabel)
        assertEquals(500.0, mayTrend.expense, 0.01)
    }

    @Test
    fun testTopMerchants_sortsByHighestSpend() {
        val transactions = listOf(
            createTx("1", 80.0, "Shell", "Gas", TransactionType.DEBIT, LocalDate.of(2026, 5, 2)),
            createTx("2", 450.0, "Costco", "Groceries", TransactionType.DEBIT, LocalDate.of(2026, 5, 5)),
            createTx("3", 120.0, "Shell", "Gas", TransactionType.DEBIT, LocalDate.of(2026, 5, 12))
        )

        val report = AnalyticsEngine.generateReport(transactions, AnalyticsPeriod.THIS_MONTH, testToday, zoneId)

        assertEquals(2, report.topMerchants.size)
        // Costco = 450.0, Shell = 200.0
        assertEquals("Costco", report.topMerchants[0].merchant)
        assertEquals(450.0, report.topMerchants[0].amount, 0.01)
        assertEquals(1, report.topMerchants[0].count)

        assertEquals("Shell", report.topMerchants[1].merchant)
        assertEquals(200.0, report.topMerchants[1].amount, 0.01)
        assertEquals(2, report.topMerchants[1].count)
    }
}
