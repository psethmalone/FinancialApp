package com.example.financehome.data.local

import androidx.room.*
import com.example.financehome.data.model.Bill
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills ORDER BY dueDayOfMonth ASC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillById(id: String): Bill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill)

    @Update
    suspend fun updateBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)

    @Query("UPDATE bills SET isPaidThisMonth = :isPaid, lastPaidTimestampMillis = :timestamp WHERE id = :id")
    suspend fun markPaid(id: String, isPaid: Boolean, timestamp: Long?)

    @Query("UPDATE bills SET isPaidThisMonth = 0")
    suspend fun resetMonthlyPaidStatus()

    @Query("SELECT SUM(amount) FROM bills")
    fun getTotalMonthlyBills(): Flow<Double?>
}
