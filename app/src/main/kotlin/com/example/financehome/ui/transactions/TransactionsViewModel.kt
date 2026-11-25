package com.example.financehome.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import android.net.Uri
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.repository.BillRepository
import com.example.financehome.data.repository.DebtRepository
import com.example.financehome.data.repository.ReceiptRepository
import com.example.financehome.service.ParsedReceipt
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val receiptRepo: ReceiptRepository,
    private val billRepo: BillRepository,
    private val debtRepo: DebtRepository
) : ViewModel() {

    val isScanningReceipt = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)

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

    fun markNoReceiptNeeded(transactionId: String, noReceipt: Boolean = true) {
        viewModelScope.launch {
            transactionRepo.markNoReceiptNeeded(transactionId, noReceipt)
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

    suspend fun scanReceiptOnly(imageUriString: String): ParsedReceipt {
        return receiptRepo.parseReceiptFromUri(imageUriString)
    }

    fun addTransactionWithReceipt(
        amount: Double,
        merchant: String,
        category: String,
        type: TransactionType,
        notes: String?,
        isDiscretionary: Boolean,
        receiptImageUri: String?,
        receiptItems: List<ReceiptItem>,
        noReceiptNeeded: Boolean = false,
        linkedBillId: String? = null,
        linkedDebtId: String? = null
    ) {
        viewModelScope.launch {
            val transactionId = UUID.randomUUID().toString()
            val transaction = Transaction(
                id = transactionId,
                amount = amount,
                merchant = merchant.trim(),
                category = category.trim().ifEmpty { "Uncategorized" },
                type = type,
                source = TransactionSource.MANUAL,
                timestampMillis = System.currentTimeMillis(),
                rawNotificationText = null,
                notes = notes?.trim()?.ifEmpty { null },
                isDiscretionary = isDiscretionary,
                isSynced = false,
                receiptImageUri = receiptImageUri,
                hasItemizedReceipt = receiptItems.isNotEmpty(),
                noReceiptNeeded = noReceiptNeeded,
                linkedBillId = linkedBillId,
                linkedDebtId = linkedDebtId
            )
            transactionRepo.insertTransaction(transaction)

            if (receiptItems.isNotEmpty()) {
                val itemsWithId = receiptItems.map {
                    it.copy(
                        id = UUID.randomUUID().toString(),
                        transactionId = transactionId
                    )
                }
                receiptRepo.insertReceiptItems(itemsWithId)
            }

            // Auto-update linked bill or debt
            if (linkedBillId != null) {
                billRepo.markBillPaid(linkedBillId, true)
            } else if (linkedDebtId != null) {
                debtRepo.payDebt(linkedDebtId, amount)
            }
        }
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
}
