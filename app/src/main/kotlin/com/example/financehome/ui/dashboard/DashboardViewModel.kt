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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val debtRepo: DebtRepository,
    private val appUpdateRepo: AppUpdateRepository
) : ViewModel() {

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
}
