package com.example.financehome.ui.categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.CategoryRule
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun categorizeTransaction(
        transaction: Transaction,
        category: String,
        isDiscretionary: Boolean,
        isRecurringRent: Boolean,
        rememberRule: Boolean
    ) {
        viewModelScope.launch {
            val updatedNotes = if (isRecurringRent) {
                "Monthly Rent / Fixed Obligation"
            } else transaction.notes

            // 1. Update the transaction in database
            transactionRepo.updateTransaction(
                transaction.copy(
                    category = category,
                    isDiscretionary = isDiscretionary,
                    notes = updatedNotes
                )
            )

            // 2. Save the rule for future automated tagging
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
        }
    }
}
