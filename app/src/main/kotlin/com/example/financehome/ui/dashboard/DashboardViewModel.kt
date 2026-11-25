package com.example.financehome.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.local.CategoryTotal
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.model.AppUpdateInfo
import com.example.financehome.data.model.DownloadState
import com.example.financehome.data.repository.AppUpdateRepository
import com.example.financehome.data.repository.DebtRepository
import com.example.financehome.data.repository.TransactionRepository
import com.example.financehome.domain.AnalyticsEngine
import com.example.financehome.domain.AnalyticsPeriod
import com.example.financehome.domain.AnalyticsReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import android.net.Uri
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.repository.ReceiptRepository
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.repository.BillRepository
import com.example.financehome.data.repository.SpendingAlertRepository
import com.example.financehome.data.repository.WageRepository
import com.example.financehome.domain.*
import com.example.financehome.service.SpendingAlertManager
import com.example.financehome.service.ParsedReceipt
import java.time.ZoneId
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val debtRepo: DebtRepository,
    private val appUpdateRepo: AppUpdateRepository,
    private val receiptRepo: ReceiptRepository,
    private val billRepo: BillRepository,
    private val wageRepo: WageRepository,
    private val spendingAlertRepo: SpendingAlertRepository,
    private val spendingAlertManager: SpendingAlertManager
) : ViewModel() {

    val isScanningReceipt = MutableStateFlow(false)
    val availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val showUpdateDialog = MutableStateFlow(false)

    val spendingAlertConfig = spendingAlertRepo.config

    val bills: StateFlow<List<Bill>> =
        billRepo.getAllBills()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val debts: StateFlow<List<Debt>> =
        debtRepo.getAllDebts()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val safeToSpendStatus: StateFlow<SafeToSpendStatus> =
        combine(
            billRepo.paycheckConfig,
            billRepo.getAllBills(),
            debtRepo.getAllDebts(),
            wageRepo.wageConfig,
            spendingAlertRepo.config,
            transactionRepo.getAllTransactions()
        ) { args ->
            val paycheckConfig = args[0] as PaycheckConfig
            @Suppress("UNCHECKED_CAST")
            val bills = args[1] as List<Bill>
            @Suppress("UNCHECKED_CAST")
            val debts = args[2] as List<Debt>
            val wageConfig = args[3] as WageConfig
            val alertConfig = args[4] as SpendingAlertConfig
            @Suppress("UNCHECKED_CAST")
            val allTxs = args[5] as List<Transaction>

            val currentDate = LocalDate.now()
            val wageEst = WageCalculator.calculateEstimate(wageConfig, paycheckConfig.frequency)
            val baseWageEst = WageCalculator.calculateBaselineEstimate(wageConfig, paycheckConfig.frequency)

            val currentTakeHome = if (wageConfig.autoSyncPaycheckPlanner) {
                wageEst.effectiveTakeHomePay
            } else {
                paycheckConfig.typicalTakeHomePay
            }
            val baselineTakeHome = if (wageConfig.autoSyncPaycheckPlanner) {
                baseWageEst.effectiveTakeHomePay
            } else {
                paycheckConfig.typicalTakeHomePay
            }

            val breakdown = PaycheckCalculator.calculateBreakdown(
                bills = bills,
                debts = debts,
                config = paycheckConfig,
                currentDate = currentDate,
                currentTakeHomePay = currentTakeHome,
                baselineTakeHomePay = baselineTakeHome,
                isCurrentPeriodOvertime = wageEst.overtimeHours > 0.0,
                currentPeriodOvertimeHours = wageEst.overtimeHours
            )

            val anchorPayDate = LocalDate.ofEpochDay(paycheckConfig.anchorPayDateEpochDay)
            val cycleStart = PaycheckCalculator.calculateCurrentCycleStartDate(
                anchorPayDate = anchorPayDate,
                frequency = paycheckConfig.frequency,
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
                config = paycheckConfig,
                spentThisCycle = spentThisCycle,
                alertConfig = alertConfig,
                currentDate = currentDate
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SafeToSpendStatus.empty()
        )

    val monthlyOutlook: StateFlow<DashboardMonthlyOutlook> =
        combine(
            safeToSpendStatus,
            bills,
            debts,
            billRepo.paycheckConfig,
            wageRepo.wageConfig
        ) { status, billsList, debtsList, pConfig, wConfig ->
            val today = LocalDate.now()
            val year = today.year
            val month = today.monthValue
            val daysInMonth = today.lengthOfMonth()

            // 1. Current available liquidity/cushion
            val currentAvailable = status.remainingSafeToSpend

            // 2. Paychecks remaining in this month (on or after today)
            val anchorPayDate = LocalDate.ofEpochDay(pConfig.anchorPayDateEpochDay)
            val allMonthPayDays = PaycheckCalculator.getPayDatesForMonth(year, month, anchorPayDate, pConfig.frequency)
            val remainingPayDays = allMonthPayDays.filter { it >= today.dayOfMonth }.sorted()

            val wageEst = WageCalculator.calculateEstimate(wConfig, pConfig.frequency)
            val effectiveTakeHome = if (wConfig.autoSyncPaycheckPlanner) wageEst.effectiveTakeHomePay else pConfig.typicalTakeHomePay

            val payEvents = remainingPayDays.map { day ->
                MonthlyEventItem(
                    id = "payday_${day}",
                    title = "Paycheck",
                    subtitle = "Scheduled Income",
                    date = LocalDate.of(year, month, day),
                    amount = effectiveTakeHome,
                    isIncome = true,
                    isPaid = false,
                    originalBill = null
                )
            }
            val expectedInflows = payEvents.sumOf { it.amount }

            // 3. Bills in this month
            val billEvents = billsList.map { bill ->
                val dueDay = minOf(bill.dueDayOfMonth, daysInMonth)
                val dueDate = LocalDate.of(year, month, dueDay)
                val isPaid = bill.isPaidForDate(dueDate, today)
                MonthlyEventItem(
                    id = "bill_${bill.id}_${dueDate}",
                    title = bill.name,
                    subtitle = bill.category,
                    date = dueDate,
                    amount = bill.amount,
                    isIncome = false,
                    isPaid = isPaid,
                    originalBill = bill
                )
            }

            // Remaining bills to be paid (only unpaid bills in this month)
            val remainingBills = billEvents.filter { !it.isPaid }.sumOf { it.amount }

            // 4. Projected End-of-Month Balance
            val projectedBalance = currentAvailable + expectedInflows - remainingBills

            // 5. Status Level
            val statusLevel = when {
                projectedBalance < 0.0 -> OutlookStatusLevel.SHORTFALL
                projectedBalance <= 250.0 -> OutlookStatusLevel.TIGHT
                else -> OutlookStatusLevel.ON_TRACK
            }

            // 6. Buffer coverage ratio (Runway bar)
            val bufferCoverageRatio = if (remainingBills > 0.0) {
                (currentAvailable / remainingBills).coerceIn(0.0, 2.0).toFloat()
            } else {
                1.0f
            }

            // 7. Chronological Events for the month
            val futureEvents = (payEvents + billEvents.filter { !it.isPaid || it.date >= today }).sortedBy { it.date }
            val immediateNext = futureEvents.firstOrNull { !it.isPaid } ?: futureEvents.firstOrNull()

            DashboardMonthlyOutlook(
                currentAvailableBalance = currentAvailable,
                expectedInflows = expectedInflows,
                remainingBills = remainingBills,
                projectedEndOfMonthBalance = projectedBalance,
                statusLevel = statusLevel,
                bufferCoverageRatio = bufferCoverageRatio,
                chronologicalEvents = futureEvents,
                immediateNextEvent = immediateNext
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardMonthlyOutlook(
                currentAvailableBalance = 0.0,
                expectedInflows = 0.0,
                remainingBills = 0.0,
                projectedEndOfMonthBalance = 0.0,
                statusLevel = OutlookStatusLevel.ON_TRACK,
                bufferCoverageRatio = 1.0f,
                chronologicalEvents = emptyList(),
                immediateNextEvent = null
            )
        )

    fun toggleBillPaid(bill: Bill) {
        viewModelScope.launch {
            billRepo.toggleBillPaid(bill)
        }
    }

    fun updateSpendingAlertConfig(config: SpendingAlertConfig) {
        spendingAlertRepo.updateConfig(config)
    }

    fun sendTestSpendingAlert() {
        spendingAlertManager.sendTestAlert()
    }

    // Analytics state
    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<AnalyticsPeriod> = _selectedPeriod.asStateFlow()

    fun setAnalyticsPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
    }

    val analyticsReport: StateFlow<AnalyticsReport> =
        combine(transactionRepo.getAllTransactions(), _selectedPeriod) { transactions, period ->
            AnalyticsEngine.generateReport(transactions, period)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsEngine.generateReport(emptyList(), AnalyticsPeriod.THIS_MONTH)
        )

    init {
        checkUpdatesOnStartup()
    }

    fun checkUpdatesOnStartup() {
        if (!appUpdateRepo.isAutoCheckEnabled()) return
        viewModelScope.launch {
            val result = appUpdateRepo.checkForUpdate()
            result.onSuccess { info ->
                if (info != null && info.isNewer) {
                    availableUpdate.value = info
                }
            }
        }
    }

    fun openUpdateDialog() {
        val update = availableUpdate.value
        val cached = appUpdateRepo.getCachedApk(update?.versionName)
        if (cached != null) {
            downloadState.value = DownloadState.Success(cached)
        }
        showUpdateDialog.value = true
    }

    fun dismissUpdateDialog() {
        showUpdateDialog.value = false
    }

    fun dismissUpdateBanner() {
        availableUpdate.value = null
    }

    fun startDownload(downloadUrl: String) {
        viewModelScope.launch {
            appUpdateRepo.downloadApk(downloadUrl).collect { state ->
                downloadState.value = state
            }
        }
    }

    val uncategorizedCount: StateFlow<Int> =
        transactionRepo.getUncategorizedTransactions()
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

    // Transaction Search & Filter state
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)

    val allTransactions: StateFlow<List<Transaction>> =
        transactionRepo.getAllTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val filteredTransactions: StateFlow<List<Transaction>> =
        combine(allTransactions, searchQuery, selectedCategory) { list, query, category ->
            list.filter { item ->
                val matchesQuery = query.isBlank() ||
                        item.merchant.contains(query, ignoreCase = true) ||
                        item.category.contains(query, ignoreCase = true) ||
                        (item.notes != null && item.notes.contains(query, ignoreCase = true))

                val matchesCategory = category == null || item.category.equals(category, ignoreCase = true)

                matchesQuery && matchesCategory
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onCategorySelect(category: String?) {
        selectedCategory.value = if (selectedCategory.value == category) null else category
    }

    fun updateTransactionCategory(transaction: Transaction, newCategory: String) {
        viewModelScope.launch {
            transactionRepo.updateTransaction(transaction.copy(category = newCategory))
        }
    }

    fun toggleDiscretionary(transaction: Transaction, isDiscretionary: Boolean) {
        viewModelScope.launch {
            transactionRepo.updateTransaction(transaction.copy(isDiscretionary = isDiscretionary))
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepo.deleteTransaction(transaction)
        }
    }

    val recentTransactions: StateFlow<List<Transaction>> =
        transactionRepo.getAllTransactions()
            .map { list -> list.take(5) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val categoryTotals: StateFlow<List<CategoryTotal>> =
        transactionRepo.getSpendingTotalByCategory()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val now = LocalDate.now()

    val monthlySpendTotal: StateFlow<Double> =
        transactionRepo.getMonthlySpendingTotal(now.year, now.monthValue)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

    val totalDebt: StateFlow<Double> =
        debtRepo.getAllDebts()
            .map { debts -> debts.sumOf { it.currentBalance } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

    val showMissingReceiptsBanner = MutableStateFlow(true)

    val transactionsMissingReceipts: StateFlow<List<Transaction>> =
        transactionRepo.getTransactionsMissingReceipts(limit = 6)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun dismissMissingReceiptsBanner(markAllAsNoReceipt: Boolean = false) {
        showMissingReceiptsBanner.value = false
        if (markAllAsNoReceipt) {
            viewModelScope.launch {
                transactionRepo.markAllMissingAsNoReceipt()
            }
        }
    }

    fun markNoReceiptNeeded(transactionId: String, noReceipt: Boolean = true) {
        viewModelScope.launch {
            transactionRepo.markNoReceiptNeeded(transactionId, noReceipt)
        }
    }

    val netPosition: StateFlow<Double> =
        combine(transactionRepo.getAllTransactions(), totalDebt) { transactions, debt ->
            val totalIncome = transactions.filter { it.type == TransactionType.CREDIT }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }
            (totalIncome - totalExpense) - debt
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    fun getReceiptItems(transactionId: String): Flow<List<ReceiptItem>> =
        receiptRepo.getReceiptItems(transactionId)

    fun createReceiptCaptureFile(): Pair<File, Uri> =
        receiptRepo.createReceiptCaptureFile()

    suspend fun savePickedImageToInternalStorage(uri: Uri): String =
        receiptRepo.savePickedImageToInternalStorage(uri)

    fun attachReceipt(transactionId: String, imageUriString: String, onDone: ((Result<ParsedReceipt>) -> Unit)? = null) {
        viewModelScope.launch {
            isScanningReceipt.value = true
            try {
                val parsed = receiptRepo.processAndAttachReceipt(imageUriString, transactionId)
                onDone?.invoke(Result.success(parsed))
            } catch (e: Exception) {
                onDone?.invoke(Result.failure(e))
            } finally {
                isScanningReceipt.value = false
            }
        }
    }

    fun addReceiptItem(item: ReceiptItem) {
        viewModelScope.launch { receiptRepo.addReceiptItem(item) }
    }

    fun updateReceiptItem(item: ReceiptItem) {
        viewModelScope.launch { receiptRepo.updateReceiptItem(item) }
    }

    fun deleteReceiptItem(item: ReceiptItem) {
        viewModelScope.launch { receiptRepo.deleteReceiptItem(item) }
    }

    fun removeReceipt(transactionId: String) {
        viewModelScope.launch { receiptRepo.removeReceipt(transactionId) }
    }

    fun linkTransactionToBill(transactionId: String, billId: String) {
        viewModelScope.launch {
            transactionRepo.updateTransactionLinks(
                id = transactionId,
                billId = billId,
                debtId = null,
                isDiscretionary = false,
                noReceiptNeeded = true
            )
            billRepo.markBillPaid(billId, true)
        }
    }

    fun linkTransactionToDebt(transactionId: String, debtId: String, amount: Double) {
        viewModelScope.launch {
            transactionRepo.updateTransactionLinks(
                id = transactionId,
                billId = null,
                debtId = debtId,
                isDiscretionary = false,
                noReceiptNeeded = true
            )
            debtRepo.payDebt(debtId, amount)
        }
    }

    fun unlinkTransactionObligation(transactionId: String) {
        viewModelScope.launch {
            transactionRepo.updateTransactionLinks(
                id = transactionId,
                billId = null,
                debtId = null,
                isDiscretionary = true,
                noReceiptNeeded = false
            )
        }
    }

    fun toggleBillPaid(bill: Bill, targetDate: LocalDate? = null) {
        viewModelScope.launch {
            billRepo.toggleBillPaid(bill, targetDate)
        }
    }
}

data class DashboardMonthlyOutlook(
    val currentAvailableBalance: Double,
    val expectedInflows: Double,
    val remainingBills: Double,
    val projectedEndOfMonthBalance: Double,
    val statusLevel: OutlookStatusLevel,
    val bufferCoverageRatio: Float,
    val chronologicalEvents: List<MonthlyEventItem>,
    val immediateNextEvent: MonthlyEventItem?
)

enum class OutlookStatusLevel(val label: String) {
    ON_TRACK("On Track"),
    TIGHT("Tight"),
    SHORTFALL("Shortfall")
}

data class MonthlyEventItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val date: java.time.LocalDate,
    val amount: Double,
    val isIncome: Boolean,
    val isPaid: Boolean,
    val originalBill: com.example.financehome.data.model.Bill? = null
)
