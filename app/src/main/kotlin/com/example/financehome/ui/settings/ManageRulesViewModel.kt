package com.example.financehome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.CategoryRule
import com.example.financehome.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ManageRulesViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    val rules: StateFlow<List<CategoryRule>> =
        transactionRepo.getAllRules()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun addRule(
        keyword: String,
        category: String,
        isDiscretionary: Boolean,
        isRecurringRent: Boolean
    ) {
        viewModelScope.launch {
            val rule = CategoryRule(
                id = UUID.randomUUID().toString(),
                keyword = keyword.trim().lowercase(),
                category = category,
                isDiscretionary = isDiscretionary,
                isRecurringRent = isRecurringRent
            )
            transactionRepo.insertRule(rule)
        }
    }

    fun deleteRule(rule: CategoryRule) {
        viewModelScope.launch {
            transactionRepo.deleteRule(rule)
        }
    }
}
