package com.example.financehome.data.repository

import com.example.financehome.data.local.CategoryRuleDao
import com.example.financehome.data.local.CategoryTotal
import com.example.financehome.data.local.TransactionDao
import com.example.financehome.data.model.CategoryRule
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.domain.AutoCategorizer
import com.example.financehome.domain.CategorizationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryRuleDao: CategoryRuleDao
) {

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions()

    fun getDiscretionaryTransactions(): Flow<List<Transaction>> =
        transactionDao.getDiscretionaryTransactions()

    fun getUncategorizedTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().map { list ->
            list.filter { it.category.equals("Uncategorized", ignoreCase = true) }
        }

    fun getSpendingTotalByCategory(): Flow<List<CategoryTotal>> =
        transactionDao.getSpendingTotalByCategory()

    fun getTransactionsByDateRange(startMs: Long, endMs: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(startMs, endMs)

    fun getMonthlySpendingTotal(year: Int, month: Int): Flow<Double> {
        val yearMonth = YearMonth.of(year, month)
        val startMs = yearMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMs = yearMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli()

        return transactionDao.getTransactionsByDateRange(startMs, endMs).map { transactions ->
            transactions.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }
        }
    }

    suspend fun insertTransaction(t: Transaction) = transactionDao.insertTransaction(t)

    suspend fun insertTransactions(list: List<Transaction>) = transactionDao.insertTransactions(list)

    suspend fun updateTransaction(t: Transaction) = transactionDao.updateTransaction(t)

    suspend fun deleteTransaction(t: Transaction) = transactionDao.deleteTransaction(t)

    // Category Rules operations
    fun getAllRules(): Flow<List<CategoryRule>> = categoryRuleDao.getAllRules()

    suspend fun insertRule(rule: CategoryRule) = categoryRuleDao.insertRule(rule)

    suspend fun deleteRule(rule: CategoryRule) = categoryRuleDao.deleteRule(rule)

    suspend fun deleteRuleById(id: String) = categoryRuleDao.deleteRuleById(id)

    suspend fun categorizeMerchant(merchant: String): CategorizationResult {
        val userRules = categoryRuleDao.getAllRulesList()
        return AutoCategorizer.categorize(merchant, userRules)
    }

    suspend fun batchCategorizeByMerchant(
        merchant: String,
        category: String,
        isDiscretionary: Boolean,
        notes: String?
    ): Int {
        return transactionDao.updateCategoryForMerchant(
            merchant = merchant.trim(),
            category = category,
            isDiscretionary = isDiscretionary,
            notes = notes
        )
    }
}
