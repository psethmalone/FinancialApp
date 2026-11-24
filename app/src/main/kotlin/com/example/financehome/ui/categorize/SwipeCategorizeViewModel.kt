package com.example.financehome.ui.categorize

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.CategoryRule
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.repository.ReceiptRepository
import com.example.financehome.data.repository.TransactionRepository
import com.example.financehome.service.ParsedReceipt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SwipeCategorizeViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val receiptRepo: ReceiptRepository
) : ViewModel() {

    val isScanningReceipt = MutableStateFlow(false)

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
