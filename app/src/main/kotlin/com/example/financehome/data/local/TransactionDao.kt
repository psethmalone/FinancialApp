package com.example.financehome.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.financehome.data.model.Transaction
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(
    val category: String,
    val total: Double
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(t: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: Transaction) = insertTransaction(t)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(list: List<Transaction>)

    @Update
    suspend fun updateTransaction(t: Transaction)

    @Delete
    suspend fun deleteTransaction(t: Transaction)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): Transaction?

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE receiptImageUri IS NULL
          AND hasItemizedReceipt = 0
          AND noReceiptNeeded = 0
          AND type = 'DEBIT'
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    fun getTransactionsMissingReceipts(limit: Int = 10): Flow<List<Transaction>>

    @Query("UPDATE transactions SET noReceiptNeeded = :noReceipt WHERE id = :id")
    suspend fun markNoReceiptNeeded(id: String, noReceipt: Boolean = true)

    @Query("UPDATE transactions SET noReceiptNeeded = 1 WHERE receiptImageUri IS NULL AND hasItemizedReceipt = 0 AND noReceiptNeeded = 0 AND type = 'DEBIT'")
    suspend fun markAllMissingAsNoReceipt()

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestampMillis >= :startMs AND timestampMillis <= :endMs
        ORDER BY timestampMillis DESC
        """
    )
    fun getTransactionsByDateRange(startMs: Long, endMs: Long): Flow<List<Transaction>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE type = 'DEBIT'
          AND timestampMillis >= :startMs AND timestampMillis <= :endMs
        """
    )
    fun getSpendingBetween(startMs: Long, endMs: Long): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE type = 'DEBIT'
          AND timestampMillis >= :startMs AND timestampMillis <= :endMs
        """
    )
    suspend fun getSpendingBetweenSync(startMs: Long, endMs: Long): Double

    @Query(
        """
        SELECT * FROM transactions
        WHERE category = :category
        ORDER BY timestampMillis DESC
        """
    )
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE isDiscretionary = 1
        ORDER BY timestampMillis DESC
        """
    )
    fun getDiscretionaryTransactions(): Flow<List<Transaction>>

    @Query(
        """
        SELECT category, SUM(amount) AS total
        FROM transactions
        WHERE type = 'DEBIT'
        GROUP BY category
        ORDER BY total DESC
        """
    )
    fun getSpendingTotalByCategory(): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM transactions
            WHERE amount = :amount
              AND merchant = :merchant
              AND timestampMillis >= :windowStart
        )
        """
    )
    suspend fun existsRecentTransaction(amount: Double, merchant: String, windowStart: Long): Boolean

    @Query("SELECT * FROM transactions WHERE isSynced = 0")
    suspend fun getUnsyncedTransactions(): List<Transaction>

    @Query("UPDATE transactions SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE transactions SET category = :category, isDiscretionary = :isDiscretionary, notes = :notes WHERE LOWER(merchant) = LOWER(:merchant)")
    suspend fun updateCategoryForMerchant(merchant: String, category: String, isDiscretionary: Boolean, notes: String?): Int

    @Query("UPDATE transactions SET linkedBillId = :billId, linkedDebtId = :debtId, isDiscretionary = :isDiscretionary, noReceiptNeeded = :noReceiptNeeded WHERE id = :id")
    suspend fun updateTransactionLinks(id: String, billId: String?, debtId: String?, isDiscretionary: Boolean, noReceiptNeeded: Boolean): Int
}
