package com.example.financehome.ui.categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.CategoryRule
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SwipeCategorizeViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    val uncategorizedTransactions: StateFlow<List<Transaction>> =
        transactionRepo.getUncategorizedTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _batchMessage = MutableStateFlow<String?>(null)
    val batchMessage: StateFlow<String?> = _batchMessage.asStateFlow()

    fun categorizeTransaction(
        transaction: Transaction,
        category: String,
        isDiscretionary: Boolean,
        isRecurringRent: Boolean = false,
        rememberRule: Boolean = true
    ) {
        viewModelScope.launch {
            val updatedNotes = if (isRecurringRent) {
                "Monthly Rent / Fixed Obligation"
            } else transaction.notes

            // 1. Batch update all matching transactions with the same merchant in Room
            val updatedCount = transactionRepo.batchCategorizeByMerchant(
                merchant = transaction.merchant,
                category = category,
                isDiscretionary = isDiscretionary,
                notes = updatedNotes
            )

            // Fallback: also ensure this exact transaction is updated if merchant was blank
            if (updatedCount == 0) {
                transactionRepo.updateTransaction(
                    transaction.copy(
                        category = category,
                        isDiscretionary = isDiscretionary,
                        notes = updatedNotes
                    )
                )
            }

            // 2. Save rule so future spends from this merchant are automatically categorized
            if (rememberRule && transaction.merchant.isNotBlank()) {
                val rule = CategoryRule(
                    id = UUID.randomUUID().toString(),
                    keyword = transaction.merchant.trim(),
                    category = category,
                    isDiscretionary = isDiscretionary,
                    isRecurringRent = isRecurringRent
                )
                transactionRepo.insertRule(rule)
            }

            // 3. Show batch feedback
            val msg = if (updatedCount > 1) {
                "Auto-tagged $updatedCount '${transaction.merchant}' transactions as $category"
            } else {
                "Tagged '${transaction.merchant}' as $category"
            }
            _batchMessage.value = msg
            delay(3000)
            if (_batchMessage.value == msg) {
                _batchMessage.value = null
            }
        }
    }
}
