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
                it.type == TransactionType.DEBIT && it.timestampMillis >= startMs
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

    fun dismissMissingReceiptsBanner() {
        showMissingReceiptsBanner.value = false
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
}
