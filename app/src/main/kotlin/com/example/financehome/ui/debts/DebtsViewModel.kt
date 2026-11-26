package com.example.financehome.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import com.example.financehome.data.repository.BillRepository
import com.example.financehome.data.repository.DebtRepository
import com.example.financehome.data.repository.WageRepository
import com.example.financehome.domain.AmortizationRow
import com.example.financehome.domain.DebtCalculator
import com.example.financehome.domain.MultiDebtComparison
import com.example.financehome.domain.PaycheckBreakdown
import com.example.financehome.domain.PaycheckCalculator
import com.example.financehome.domain.PaycheckConfig
import com.example.financehome.domain.WageCalculator
import com.example.financehome.domain.WageConfig
import com.example.financehome.domain.WageEstimate
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.TransactionRepository
import com.example.financehome.data.repository.SpendingAlertRepository
import com.example.financehome.domain.ObligationType
import com.example.financehome.domain.PayFrequency
import com.example.financehome.domain.SafeToSpendStatus
import com.example.financehome.domain.SpendingAlertConfig
import com.example.financehome.service.SpendingAlertManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class PayoffSimulation(
    val monthsToPayoff: Int,
    val payoffDateMillis: Long,
    val totalInterest: Double,
    val monthsSaved: Int,
    val interestSaved: Double,
    val amortizationRows: List<AmortizationRow>
)

data class ProjectedScenarioItem(
    val id: String,
    val name: String,
    val originalAmount: Double,
    val simulatedAmount: Double,
    val type: ObligationType,
    val dueDayOfMonth: Int,
    val isEnabled: Boolean = true,
    val isHypothetical: Boolean = false
) {
    val delta: Double get() = simulatedAmount - originalAmount
}

data class ProjectedSummary(
    val totalProjectedMonthlyNeed: Double,
    val totalActualMonthlyNeed: Double,
    val monthlyNeedDelta: Double,
    val projectedIncomePerPaycheck: Double,
    val payFrequency: PayFrequency,
    val neededPerPaycheck: Double,
    val savingsPerPaycheck: Double,
    val projectedMonthlySavings: Double,
    val projectedAnnualSavings: Double,
    val isSurplus: Boolean,
    val deficitPerPaycheck: Double,
    val fixedNutPercentageOfIncome: Double,
    val enabledItemsCount: Int,
    val totalItemsCount: Int,
    val isCustomized: Boolean
)

@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debtRepo: DebtRepository,
    private val billRepo: BillRepository,
    private val wageRepo: WageRepository,
    private val transactionRepo: TransactionRepository,
    private val spendingAlertRepo: SpendingAlertRepository,
    private val spendingAlertManager: SpendingAlertManager
) : ViewModel() {

    // ── Debts ──────────────────────────────────────────────────────────────────

    val debts: StateFlow<List<Debt>> =
        debtRepo.getAllDebts()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val totalDebt: StateFlow<Double> =
        debts.map { list -> list.sumOf { it.currentBalance } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

    // ── Multi-Debt Payoff Optimizer ──────────────────────────────────────────

    val extraMonthlyPayoffBudget = MutableStateFlow(100.0)

    val multiDebtComparison: StateFlow<MultiDebtComparison> =
        combine(debts, extraMonthlyPayoffBudget) { debtList, extra ->
            DebtCalculator.compareMultiDebtPayoff(debtList, extra)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DebtCalculator.compareMultiDebtPayoff(emptyList(), 100.0)
        )

    fun setExtraMonthlyPayoffBudget(amount: Double) {
        extraMonthlyPayoffBudget.value = maxOf(0.0, amount)
    }

    // ── Bills ──────────────────────────────────────────────────────────────────

    val bills: StateFlow<List<Bill>> =
        billRepo.getAllBills()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val occurrences: StateFlow<List<com.example.financehome.data.model.BillOccurrenceEntity>> =
        billRepo.allOccurrences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val totalMonthlyBills: StateFlow<Double> =
        bills.map { list -> list.sumOf { it.amount } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

    val totalMonthlyDebtMinimums: StateFlow<Double> =
        debts.map { list -> list.sumOf { it.minimumPayment } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

    val totalMonthlyFixedNut: StateFlow<Double> =
        combine(totalMonthlyBills, totalMonthlyDebtMinimums) { billsTotal, debtsMinTotal ->
            billsTotal + debtsMinTotal
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // ── Wage & Hours Worked Tracker ──────────────────────────────────────────

    val wageConfig: StateFlow<WageConfig> =
        wageRepo.wageConfig

    val paycheckConfig: StateFlow<PaycheckConfig> =
        billRepo.paycheckConfig

    val wageEstimate: StateFlow<WageEstimate> =
        combine(wageConfig, paycheckConfig) { wage, payConfig ->
            WageCalculator.calculateEstimate(wage, payConfig.frequency)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WageCalculator.calculateEstimate(WageConfig())
        )

    val baselineWageEstimate: StateFlow<WageEstimate> =
        combine(wageConfig, paycheckConfig) { wage, payConfig ->
            WageCalculator.calculateBaselineEstimate(wage, payConfig.frequency)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WageCalculator.calculateBaselineEstimate(WageConfig())
        )

    // ── Paycheck Cushion & Allocator ───────────────────────────────────────────

    val paycheckBreakdown: StateFlow<PaycheckBreakdown> =
        combine(bills, debts, paycheckConfig, wageEstimate, baselineWageEstimate, wageConfig) { args ->
            @Suppress("UNCHECKED_CAST")
            val billList = args[0] as List<Bill>
            @Suppress("UNCHECKED_CAST")
            val debtList = args[1] as List<Debt>
            val config = args[2] as PaycheckConfig
            val wageEst = args[3] as WageEstimate
            val baseWageEst = args[4] as WageEstimate
            val wageCfg = args[5] as WageConfig

            val currentTakeHome = if (wageCfg.autoSyncPaycheckPlanner) {
                wageEst.effectiveTakeHomePay
            } else {
                config.typicalTakeHomePay
            }
            val baselineTakeHome = if (wageCfg.autoSyncPaycheckPlanner) {
                baseWageEst.effectiveTakeHomePay
            } else {
                config.typicalTakeHomePay
            }
            PaycheckCalculator.calculateBreakdown(
                bills = billList,
                debts = debtList,
                config = config,
                currentTakeHomePay = currentTakeHome,
                baselineTakeHomePay = baselineTakeHome,
                isCurrentPeriodOvertime = wageEst.overtimeHours > 0.0,
                currentPeriodOvertimeHours = wageEst.overtimeHours
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PaycheckCalculator.calculateBreakdown(emptyList(), emptyList(), PaycheckConfig())
        )

    // ── Debt Actions ───────────────────────────────────────────────────────────

    fun addDebt(
        name: String,
        balance: Double,
        aprPercent: Double,
        minimumPayment: Double,
        type: DebtType,
        dueDayOfMonth: Int = 15
    ) {
        viewModelScope.launch {
            val aprFraction = aprPercent / 100.0
            debtRepo.addDebt(
                name = name,
                balance = balance,
                apr = aprFraction,
                minimumPayment = minimumPayment,
                type = type,
                dueDayOfMonth = dueDayOfMonth
            )
        }
    }

    fun updateDebt(debt: Debt) {
        viewModelScope.launch {
            debtRepo.updateDebt(debt)
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            debtRepo.deleteDebt(debt)
        }
    }

    fun toggleDebtPaid(debt: Debt, targetDate: LocalDate? = null) {
        viewModelScope.launch {
            debtRepo.toggleDebtPaid(debt, targetDate)
        }
    }

    fun editDebt(
        id: String,
        name: String,
        currentBalance: Double,
        originalBalance: Double,
        aprPercent: Double,
        minimumPayment: Double,
        dueDayOfMonth: Int,
        type: DebtType
    ) {
        viewModelScope.launch {
            val existing = debts.value.find { it.id == id }
            val createdAt = existing?.createdAtMillis ?: System.currentTimeMillis()
            val isPaid = existing?.isPaidThisMonth ?: false
            val lastPaid = existing?.lastPaidTimestampMillis
            val updated = Debt(
                id = id,
                name = name.trim(),
                originalBalance = originalBalance,
                currentBalance = currentBalance,
                apr = aprPercent / 100.0,
                minimumPayment = minimumPayment,
                type = type,
                createdAtMillis = createdAt,
                dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31),
                isPaidThisMonth = isPaid,
                lastPaidTimestampMillis = lastPaid,
                nextDueDateEpochDay = existing?.nextDueDateEpochDay ?: 0L,
                lastSettledDateEpochDay = existing?.lastSettledDateEpochDay,
                isSynced = false
            )
            debtRepo.updateDebt(updated)
        }
    }

    fun payDebt(debt: Debt, paymentAmount: Double) {
        viewModelScope.launch {
            debtRepo.payDebt(debt.id, paymentAmount)
            val isExtra = paymentAmount > debt.minimumPayment
            val extraFormatted = String.format(Locale.US, "%.2f", paymentAmount - debt.minimumPayment)
            val note = if (isExtra) "Debt Payment ($${extraFormatted} extra)" else "Debt Minimum Payment"
            val tx = Transaction(
                id = UUID.randomUUID().toString(),
                amount = paymentAmount,
                merchant = "${debt.name} Payment",
                category = "Debts",
                type = TransactionType.DEBIT,
                source = TransactionSource.MANUAL,
                timestampMillis = System.currentTimeMillis(),
                rawNotificationText = null,
                notes = note,
                isDiscretionary = false,
                isSynced = false,
                noReceiptNeeded = true,
                linkedDebtId = debt.id
            )
            transactionRepo.insertTransaction(tx)
        }
    }

    fun simulatePayoff(debt: Debt, extraMonthlyPayment: Double): PayoffSimulation {
        val effectivePayment = debt.minimumPayment + extraMonthlyPayment
        val months = DebtCalculator.monthsToPayoff(debt.currentBalance, debt.apr, effectivePayment)
        val payoffDate = DebtCalculator.payoffDateMillis(months)
        val totalInterest = DebtCalculator.totalInterestPaid(debt.currentBalance, debt.apr, effectivePayment)

        val monthsSaved = if (extraMonthlyPayment > 0.0) {
            DebtCalculator.monthsSavedWithExtraPayment(debt.currentBalance, debt.apr, debt.minimumPayment, extraMonthlyPayment)
        } else 0

        val interestSaved = if (extraMonthlyPayment > 0.0) {
            DebtCalculator.interestSavedWithExtraPayment(debt.currentBalance, debt.apr, debt.minimumPayment, extraMonthlyPayment)
        } else 0.0

        val schedule = DebtCalculator.amortizationSchedule(debt.currentBalance, debt.apr, effectivePayment)

        return PayoffSimulation(
            monthsToPayoff = if (months == Double.MAX_VALUE) -1 else months.roundToInt(),
            payoffDateMillis = payoffDate,
            totalInterest = if (totalInterest == Double.MAX_VALUE) 0.0 else totalInterest,
            monthsSaved = monthsSaved,
            interestSaved = interestSaved,
            amortizationRows = schedule
        )
    }

    // ── Bill Actions ───────────────────────────────────────────────────────────

    fun addBill(
        name: String,
        amount: Double,
        dueDayOfMonth: Int,
        category: String,
        isAutoPay: Boolean = false,
        notes: String? = null
    ) {
        viewModelScope.launch {
            billRepo.addBill(name, amount, dueDayOfMonth, category, isAutoPay, notes)
        }
    }

    fun updateBill(bill: Bill) {
        viewModelScope.launch {
            billRepo.updateBill(bill)
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            billRepo.deleteBill(bill)
        }
    }

    fun toggleBillPaid(bill: Bill, targetDate: LocalDate? = null) {
        viewModelScope.launch {
            billRepo.toggleBillPaid(bill, targetDate)
        }
    }

    fun payBill(bill: Bill, targetDate: LocalDate? = null) {
        viewModelScope.launch {
            billRepo.markBillPaidForCycle(bill, dueToSettle = targetDate ?: bill.getEffectiveNextDueDate())
            val tx = Transaction(
                id = UUID.randomUUID().toString(),
                amount = bill.amount,
                merchant = bill.name,
                category = bill.category.ifEmpty { "Bills" },
                type = TransactionType.DEBIT,
                source = TransactionSource.MANUAL,
                timestampMillis = System.currentTimeMillis(),
                rawNotificationText = null,
                notes = "Bill Payment: ${bill.name}",
                isDiscretionary = false,
                isSynced = false,
                noReceiptNeeded = true,
                linkedBillId = bill.id
            )
            transactionRepo.insertTransaction(tx)
        }
    }

    fun updatePaycheckConfig(config: PaycheckConfig) {
        billRepo.updatePaycheckConfig(config)
    }

    // ── Wage & Hours Actions ──────────────────────────────────────────────────

    fun updateWageConfig(config: WageConfig) {
        wageRepo.updateWageConfig(config)
    }

    fun setHours(week1: Double, week2: Double) {
        wageRepo.setHours(week1, week2)
    }

    fun adjustWeek1Hours(delta: Double) {
        val current = wageConfig.value
        val newH = maxOf(0.0, current.week1Hours + delta)
        wageRepo.setHours(newH, current.week2Hours)
    }

    fun adjustWeek2Hours(delta: Double) {
        val current = wageConfig.value
        val newH = maxOf(0.0, current.week2Hours + delta)
        wageRepo.setHours(current.week1Hours, newH)
    }

    fun verifyPaycheck(actualAmount: Double) {
        wageRepo.verifyPaycheck(actualAmount)
    }

    fun resetPayPeriodHours() {
        wageRepo.resetForNextPayPeriod()
    }

    // ── Safe-to-Spend Real-Time Burn & Alerts ────────────────────────────────────

    val spendingAlertConfig: StateFlow<SpendingAlertConfig> = spendingAlertRepo.config

    val safeToSpendStatus: StateFlow<SafeToSpendStatus> =
        combine(
            paycheckBreakdown,
            paycheckConfig,
            transactionRepo.getAllTransactions(),
            spendingAlertConfig
        ) { breakdown, config, allTxs, alertConfig ->
            val currentDate = LocalDate.now()
            val anchorPayDate = LocalDate.ofEpochDay(config.anchorPayDateEpochDay)
            val cycleStart = PaycheckCalculator.calculateCurrentCycleStartDate(
                anchorPayDate = anchorPayDate,
                frequency = config.frequency,
                currentDate = currentDate
            )
            val startMs = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val spentThisCycle = allTxs.filter {
                it.type == TransactionType.DEBIT &&
                it.timestampMillis >= startMs &&
                it.linkedBillId == null &&
                it.linkedDebtId == null
            }.sumOf { it.amount }

            PaycheckCalculator.calculateSafeToSpendStatus(
                breakdown = breakdown,
                config = config,
                spentThisCycle = spentThisCycle,
                alertConfig = alertConfig,
                currentDate = currentDate
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SafeToSpendStatus.empty()
        )

    fun updateSpendingAlertConfig(config: SpendingAlertConfig) {
        spendingAlertRepo.updateConfig(config)
    }

    fun sendTestSpendingAlert() {
        spendingAlertManager.sendTestAlert()
    }

    // ── Projection & What-If Scenario Sandbox ───────────────────────────────────

    private val customScenarioItems = MutableStateFlow<List<ProjectedScenarioItem>?>(null)
    private val customProjectedIncome = MutableStateFlow<Double?>(null)
    private val customProjectedFrequency = MutableStateFlow<PayFrequency?>(null)

    val projectedScenarioItems: StateFlow<List<ProjectedScenarioItem>> =
        combine(bills, debts, customScenarioItems) { billList, debtList, customItems ->
            customItems ?: buildDefaultScenarioItems(billList, debtList)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val projectedSummary: StateFlow<ProjectedSummary> =
        combine(
            projectedScenarioItems,
            paycheckConfig,
            wageEstimate,
            wageConfig,
            customProjectedIncome,
            customProjectedFrequency
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val items = args[0] as List<ProjectedScenarioItem>
            val config = args[1] as PaycheckConfig
            val wageEst = args[2] as WageEstimate
            val wageCfg = args[3] as WageConfig
            val customIncome = args[4] as? Double
            val customFreq = args[5] as? PayFrequency

            val defaultIncome = if (wageCfg.autoSyncPaycheckPlanner) {
                wageEst.effectiveTakeHomePay
            } else {
                config.typicalTakeHomePay
            }
            val activeIncome = customIncome ?: defaultIncome
            val activeFreq = customFreq ?: config.frequency

            val totalActualMonthlyNeed = bills.value.sumOf { it.amount } + debts.value.sumOf { it.minimumPayment }
            val enabledItems = items.filter { it.isEnabled }
            val totalProjectedMonthlyNeed = enabledItems.sumOf { it.simulatedAmount }
            val monthlyNeedDelta = totalProjectedMonthlyNeed - totalActualMonthlyNeed

            val annualProjectedNeed = totalProjectedMonthlyNeed * 12.0
            val neededPerPaycheck = if (activeFreq.paychecksPerYear > 0) {
                annualProjectedNeed / activeFreq.paychecksPerYear
            } else {
                0.0
            }

            val savingsPerPaycheck = activeIncome - neededPerPaycheck
            val isSurplus = savingsPerPaycheck >= 0.0
            val deficitPerPaycheck = if (isSurplus) 0.0 else -savingsPerPaycheck

            val projectedMonthlySavings = (savingsPerPaycheck * activeFreq.paychecksPerYear) / 12.0
            val projectedAnnualSavings = savingsPerPaycheck * activeFreq.paychecksPerYear

            val monthlyIncome = (activeIncome * activeFreq.paychecksPerYear) / 12.0
            val fixedNutPercentageOfIncome = if (monthlyIncome > 0.0) {
                (totalProjectedMonthlyNeed / monthlyIncome) * 100.0
            } else {
                0.0
            }

            val isCustomized = customScenarioItems.value != null ||
                    customProjectedIncome.value != null ||
                    customProjectedFrequency.value != null

            ProjectedSummary(
                totalProjectedMonthlyNeed = totalProjectedMonthlyNeed,
                totalActualMonthlyNeed = totalActualMonthlyNeed,
                monthlyNeedDelta = monthlyNeedDelta,
                projectedIncomePerPaycheck = activeIncome,
                payFrequency = activeFreq,
                neededPerPaycheck = neededPerPaycheck,
                savingsPerPaycheck = savingsPerPaycheck,
                projectedMonthlySavings = projectedMonthlySavings,
                projectedAnnualSavings = projectedAnnualSavings,
                isSurplus = isSurplus,
                deficitPerPaycheck = deficitPerPaycheck,
                fixedNutPercentageOfIncome = fixedNutPercentageOfIncome,
                enabledItemsCount = enabledItems.size,
                totalItemsCount = items.size,
                isCustomized = isCustomized
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProjectedSummary(
                totalProjectedMonthlyNeed = 0.0,
                totalActualMonthlyNeed = 0.0,
                monthlyNeedDelta = 0.0,
                projectedIncomePerPaycheck = 0.0,
                payFrequency = PayFrequency.BI_WEEKLY,
                neededPerPaycheck = 0.0,
                savingsPerPaycheck = 0.0,
                projectedMonthlySavings = 0.0,
                projectedAnnualSavings = 0.0,
                isSurplus = true,
                deficitPerPaycheck = 0.0,
                fixedNutPercentageOfIncome = 0.0,
                enabledItemsCount = 0,
                totalItemsCount = 0,
                isCustomized = false
            )
        )

    private fun buildDefaultScenarioItems(
        billList: List<Bill>,
        debtList: List<Debt>
    ): List<ProjectedScenarioItem> {
        val billItems = billList.map { bill ->
            ProjectedScenarioItem(
                id = "bill_${bill.id}",
                name = bill.name,
                originalAmount = bill.amount,
                simulatedAmount = bill.amount,
                type = ObligationType.BILL,
                dueDayOfMonth = bill.dueDayOfMonth,
                isEnabled = true,
                isHypothetical = false
            )
        }
        val debtItems = debtList.map { debt ->
            ProjectedScenarioItem(
                id = "debt_${debt.id}",
                name = debt.name,
                originalAmount = debt.minimumPayment,
                simulatedAmount = debt.minimumPayment,
                type = ObligationType.DEBT,
                dueDayOfMonth = debt.dueDayOfMonth,
                isEnabled = true,
                isHypothetical = false
            )
        }
        return (billItems + debtItems).sortedWith(compareBy({ it.dueDayOfMonth }, { it.name }))
    }

    private fun ensureCustomScenarioItems(): MutableList<ProjectedScenarioItem> {
        val current = customScenarioItems.value
        return if (current != null) {
            current.toMutableList()
        } else {
            buildDefaultScenarioItems(bills.value, debts.value).toMutableList()
        }
    }

    fun updateProjectedIncome(newIncome: Double) {
        customProjectedIncome.value = maxOf(0.0, newIncome)
    }

    fun updateProjectedFrequency(freq: PayFrequency) {
        customProjectedFrequency.value = freq
    }

    fun updateScenarioItemAmount(id: String, newAmount: Double) {
        val list = ensureCustomScenarioItems()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(simulatedAmount = maxOf(0.0, newAmount))
            customScenarioItems.value = list
        }
    }

    fun toggleScenarioItemEnabled(id: String) {
        val list = ensureCustomScenarioItems()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(isEnabled = !list[index].isEnabled)
            customScenarioItems.value = list
        }
    }

    fun addHypotheticalItem(name: String, amount: Double, type: ObligationType, dueDay: Int = 1) {
        val list = ensureCustomScenarioItems()
        list.add(
            ProjectedScenarioItem(
                id = "hypo_${UUID.randomUUID()}",
                name = name.ifBlank { "Hypothetical Expense" },
                originalAmount = 0.0,
                simulatedAmount = maxOf(0.0, amount),
                type = type,
                dueDayOfMonth = dueDay.coerceIn(1, 31),
                isEnabled = true,
                isHypothetical = true
            )
        )
        customScenarioItems.value = list
    }

    fun removeHypotheticalItem(id: String) {
        val list = ensureCustomScenarioItems()
        val removed = list.removeAll { it.id == id }
        if (removed) {
            customScenarioItems.value = list
        }
    }

    fun resetScenarioToActuals() {
        customScenarioItems.value = null
        customProjectedIncome.value = null
        customProjectedFrequency.value = null
    }
}

