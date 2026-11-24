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
import com.example.financehome.service.ParsedReceipt
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val debtRepo: DebtRepository,
    private val appUpdateRepo: AppUpdateRepository,
    private val receiptRepo: ReceiptRepository
) : ViewModel() {

    val isScanningReceipt = MutableStateFlow(false)
    val availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val showUpdateDialog = MutableStateFlow(false)

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
