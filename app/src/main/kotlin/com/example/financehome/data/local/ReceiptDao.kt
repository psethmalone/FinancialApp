package com.example.financehome.data.local

import androidx.room.*
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.ReceiptItemCategory
import kotlinx.coroutines.flow.Flow

data class CategorySpendTotal(
    val category: ReceiptItemCategory,
    val totalAmount: Double,
    val itemCount: Int
)

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipt_items WHERE transactionId = :transactionId ORDER BY price DESC")
    fun getReceiptItemsForTransaction(transactionId: String): Flow<List<ReceiptItem>>

    @Query("SELECT * FROM receipt_items WHERE transactionId = :transactionId ORDER BY price DESC")
    suspend fun getReceiptItemsForTransactionSync(transactionId: String): List<ReceiptItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItems(items: List<ReceiptItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItem(item: ReceiptItem)

    @Update
    suspend fun updateReceiptItem(item: ReceiptItem)

    @Delete
    suspend fun deleteReceiptItem(item: ReceiptItem)

    @Query("DELETE FROM receipt_items WHERE transactionId = :transactionId")
    suspend fun deleteReceiptItemsForTransaction(transactionId: String)

    @Query("SELECT category, SUM(price) as totalAmount, COUNT(*) as itemCount FROM receipt_items GROUP BY category")
    fun getAllCategorySpends(): Flow<List<CategorySpendTotal>>
}
