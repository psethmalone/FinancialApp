package com.example.financehome.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)

    val transactions: StateFlow<List<Transaction>> =
        transactionRepo.getAllTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val filteredTransactions: StateFlow<List<Transaction>> =
        combine(transactions, searchQuery, selectedCategory) { list, query, category ->
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

    fun addManualTransaction(
        amount: Double,
        merchant: String,
        category: String,
        type: TransactionType,
        notes: String?,
        isDiscretionary: Boolean
    ) {
        viewModelScope.launch {
            val transaction = Transaction(
                id = UUID.randomUUID().toString(),
                amount = amount,
                merchant = merchant.trim(),
                category = category.trim().ifEmpty { "Uncategorized" },
                type = type,
                source = TransactionSource.MANUAL,
                timestampMillis = System.currentTimeMillis(),
                rawNotificationText = null,
                notes = notes?.trim()?.ifEmpty { null },
                isDiscretionary = isDiscretionary,
                isSynced = false
            )
            transactionRepo.insertTransaction(transaction)
        }
    }
}
