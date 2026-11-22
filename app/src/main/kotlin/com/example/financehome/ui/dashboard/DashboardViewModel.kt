package com.example.financehome.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.local.CategoryTotal
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.DebtRepository
import com.example.financehome.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val debtRepo: DebtRepository
) : ViewModel() {

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
