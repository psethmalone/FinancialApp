package com.example.financehome.domain

import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import com.example.financehome.ui.debts.ProjectedScenarioItem
import com.example.financehome.ui.debts.ProjectedSummary
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProjectedScenarioTest {

    private fun calculateSummary(
        items: List<ProjectedScenarioItem>,
        actualBills: Double,
        actualDebts: Double,
        incomePerCheck: Double,
        frequency: PayFrequency
    ): ProjectedSummary {
        val totalActualMonthlyNeed = actualBills + actualDebts
        val enabledItems = items.filter { it.isEnabled }
        val totalProjectedMonthlyNeed = enabledItems.sumOf { it.simulatedAmount }
        val monthlyNeedDelta = totalProjectedMonthlyNeed - totalActualMonthlyNeed

        val annualProjectedNeed = totalProjectedMonthlyNeed * 12.0
        val neededPerPaycheck = if (frequency.paychecksPerYear > 0) {
            annualProjectedNeed / frequency.paychecksPerYear
        } else {
            0.0
        }

        val savingsPerPaycheck = incomePerCheck - neededPerPaycheck
        val isSurplus = savingsPerPaycheck >= 0.0
        val deficitPerPaycheck = if (isSurplus) 0.0 else -savingsPerPaycheck

        val projectedMonthlySavings = (savingsPerPaycheck * frequency.paychecksPerYear) / 12.0
        val projectedAnnualSavings = savingsPerPaycheck * frequency.paychecksPerYear

        val monthlyIncome = (incomePerCheck * frequency.paychecksPerYear) / 12.0
        val fixedNutPercentageOfIncome = if (monthlyIncome > 0.0) {
            (totalProjectedMonthlyNeed / monthlyIncome) * 100.0
        } else {
            0.0
        }

        return ProjectedSummary(
            totalProjectedMonthlyNeed = totalProjectedMonthlyNeed,
            totalActualMonthlyNeed = totalActualMonthlyNeed,
            monthlyNeedDelta = monthlyNeedDelta,
            projectedIncomePerPaycheck = incomePerCheck,
            payFrequency = frequency,
            neededPerPaycheck = neededPerPaycheck,
            savingsPerPaycheck = savingsPerPaycheck,
            projectedMonthlySavings = projectedMonthlySavings,
            projectedAnnualSavings = projectedAnnualSavings,
            isSurplus = isSurplus,
            deficitPerPaycheck = deficitPerPaycheck,
            fixedNutPercentageOfIncome = fixedNutPercentageOfIncome,
            enabledItemsCount = enabledItems.size,
            totalItemsCount = items.size,
            isCustomized = false
        )
    }

    @Test
    fun testDefaultScenarioAggregation() {
        val rent = ProjectedScenarioItem(
            id = "bill_1",
            name = "Rent",
            originalAmount = 1200.0,
            simulatedAmount = 1200.0,
            type = ObligationType.BILL,
            dueDayOfMonth = 1
        )
        val carPayment = ProjectedScenarioItem(
            id = "debt_1",
            name = "Car Loan",
            originalAmount = 350.0,
            simulatedAmount = 350.0,
            type = ObligationType.DEBT,
            dueDayOfMonth = 15
        )
        val electric = ProjectedScenarioItem(
            id = "bill_2",
            name = "Electric",
            originalAmount = 150.0,
            simulatedAmount = 150.0,
            type = ObligationType.BILL,
            dueDayOfMonth = 20
        )

        val items = listOf(rent, carPayment, electric)
        val summary = calculateSummary(
            items = items,
            actualBills = 1350.0,
            actualDebts = 350.0,
            incomePerCheck = 2000.0,
            frequency = PayFrequency.BI_WEEKLY
        )

        assertEquals(1700.0, summary.totalProjectedMonthlyNeed, 0.01)
        assertEquals(1700.0, summary.totalActualMonthlyNeed, 0.01)
        assertEquals(0.0, summary.monthlyNeedDelta, 0.01)

        // Bi-weekly: (1700 * 12) / 26 = 20400 / 26 = 784.615...
        assertEquals(784.62, summary.neededPerPaycheck, 0.01)
        assertEquals(2000.0 - 784.615, summary.savingsPerPaycheck, 0.01)
        assertTrue(summary.isSurplus)
        assertEquals(0.0, summary.deficitPerPaycheck, 0.01)
    }

    @Test
    fun testPerPaycheckReserveAcrossFrequencies() {
        val monthlyNeed = 2600.0
        val items = listOf(
            ProjectedScenarioItem(
                id = "bill_1",
                name = "Total Fixed",
                originalAmount = monthlyNeed,
                simulatedAmount = monthlyNeed,
                type = ObligationType.BILL,
                dueDayOfMonth = 1
            )
        )

        // Weekly (52 checks/year): (2600 * 12) / 52 = 600.00
        val weeklySummary = calculateSummary(items, monthlyNeed, 0.0, 1000.0, PayFrequency.WEEKLY)
        assertEquals(600.0, weeklySummary.neededPerPaycheck, 0.01)
        assertEquals(400.0, weeklySummary.savingsPerPaycheck, 0.01)

        // Bi-Weekly (26 checks/year): (2600 * 12) / 26 = 1200.00
        val biWeeklySummary = calculateSummary(items, monthlyNeed, 0.0, 2000.0, PayFrequency.BI_WEEKLY)
        assertEquals(1200.0, biWeeklySummary.neededPerPaycheck, 0.01)
        assertEquals(800.0, biWeeklySummary.savingsPerPaycheck, 0.01)

        // Semi-Monthly (24 checks/year): (2600 * 12) / 24 = 1300.00
        val semiMonthlySummary = calculateSummary(items, monthlyNeed, 0.0, 2000.0, PayFrequency.SEMI_MONTHLY)
        assertEquals(1300.0, semiMonthlySummary.neededPerPaycheck, 0.01)
        assertEquals(700.0, semiMonthlySummary.savingsPerPaycheck, 0.01)

        // Monthly (12 checks/year): 2600.00
        val monthlySummary = calculateSummary(items, monthlyNeed, 0.0, 4000.0, PayFrequency.MONTHLY)
        assertEquals(2600.0, monthlySummary.neededPerPaycheck, 0.01)
        assertEquals(1400.0, monthlySummary.savingsPerPaycheck, 0.01)
    }

    @Test
    fun testDeficitDetectionWhenObligationsExceedIncome() {
        val items = listOf(
            ProjectedScenarioItem(
                id = "rent",
                name = "Expensive Rent",
                originalAmount = 2500.0,
                simulatedAmount = 2500.0,
                type = ObligationType.BILL,
                dueDayOfMonth = 1
            )
        )

        // Bi-Weekly take-home is only $1,000, but needed per check is (2500 * 12) / 26 = $1,153.85
        val summary = calculateSummary(
            items = items,
            actualBills = 2500.0,
            actualDebts = 0.0,
            incomePerCheck = 1000.0,
            frequency = PayFrequency.BI_WEEKLY
        )

        assertFalse("Should detect deficit", summary.isSurplus)
        assertEquals(153.85, summary.deficitPerPaycheck, 0.01)
        assertTrue("Savings per paycheck should be negative", summary.savingsPerPaycheck < 0)
    }

    @Test
    fun testExcludingAndAdjustingItems() {
        val sub1 = ProjectedScenarioItem(
            id = "sub1",
            name = "Gym Membership",
            originalAmount = 80.0,
            simulatedAmount = 80.0,
            type = ObligationType.BILL,
            dueDayOfMonth = 5,
            isEnabled = true
        )
        val rent = ProjectedScenarioItem(
            id = "rent",
            name = "Rent",
            originalAmount = 1000.0,
            simulatedAmount = 1100.0, // rent increase simulated +$100
            type = ObligationType.BILL,
            dueDayOfMonth = 1,
            isEnabled = true
        )

        val initialItems = listOf(sub1, rent)
        val initialSummary = calculateSummary(initialItems, 1080.0, 0.0, 1500.0, PayFrequency.BI_WEEKLY)
        assertEquals(1180.0, initialSummary.totalProjectedMonthlyNeed, 0.01)
        assertEquals(100.0, initialSummary.monthlyNeedDelta, 0.01)

        // Now simulate cancelling the Gym Membership: isEnabled = false
        val updatedItems = listOf(sub1.copy(isEnabled = false), rent)
        val cancelledSummary = calculateSummary(updatedItems, 1080.0, 0.0, 1500.0, PayFrequency.BI_WEEKLY)

        assertEquals(1100.0, cancelledSummary.totalProjectedMonthlyNeed, 0.01)
        assertEquals(20.0, cancelledSummary.monthlyNeedDelta, 0.01)
        assertEquals(1, cancelledSummary.enabledItemsCount)
        assertEquals(2, cancelledSummary.totalItemsCount)
    }

    @Test
    fun testHypotheticalExpenseImpact() {
        val existingRent = ProjectedScenarioItem(
            id = "rent",
            name = "Rent",
            originalAmount = 1000.0,
            simulatedAmount = 1000.0,
            type = ObligationType.BILL,
            dueDayOfMonth = 1
        )
        val hypotheticalCar = ProjectedScenarioItem(
            id = "hypo_1",
            name = "New Car Loan",
            originalAmount = 0.0,
            simulatedAmount = 400.0,
            type = ObligationType.DEBT,
            dueDayOfMonth = 10,
            isHypothetical = true
        )

        val items = listOf(existingRent, hypotheticalCar)
        val summary = calculateSummary(items, 1000.0, 0.0, 2000.0, PayFrequency.BI_WEEKLY)

        assertEquals(1400.0, summary.totalProjectedMonthlyNeed, 0.01)
        assertEquals(400.0, summary.monthlyNeedDelta, 0.01)
        // Needed per paycheck: (1400 * 12) / 26 = 646.15
        assertEquals(646.15, summary.neededPerPaycheck, 0.01)
        // Savings per paycheck: 2000 - 646.15 = 1353.85
        assertEquals(1353.85, summary.savingsPerPaycheck, 0.01)
    }
}
