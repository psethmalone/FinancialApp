package com.example.financehome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financehome.data.model.BillOccurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillOccurrenceDao {

    @Query("SELECT * FROM bill_occurrences WHERE billId = :billId AND dueDateEpochDay = :dueDateEpochDay")
    suspend fun getOccurrence(billId: String, dueDateEpochDay: Long): BillOccurrenceEntity?

    @Query("SELECT * FROM bill_occurrences WHERE billId = :billId")
    fun getOccurrencesForBill(billId: String): Flow<List<BillOccurrenceEntity>>

    @Query("SELECT * FROM bill_occurrences WHERE dueDateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    fun getOccurrencesInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BillOccurrenceEntity>>

    @Query("SELECT * FROM bill_occurrences")
    fun getAllOccurrences(): Flow<List<BillOccurrenceEntity>>

    @Query("SELECT * FROM bill_occurrences")
    suspend fun getAllOccurrencesList(): List<BillOccurrenceEntity>

    @Query("SELECT * FROM bill_occurrences WHERE billId = :billId AND isPaid = 0")
    suspend fun getUnpaidOccurrencesForBill(billId: String): List<BillOccurrenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOccurrence(occurrence: BillOccurrenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOccurrences(occurrences: List<BillOccurrenceEntity>)

    @Query("DELETE FROM bill_occurrences WHERE billId = :billId AND dueDateEpochDay = :dueDateEpochDay")
    suspend fun deleteOccurrence(billId: String, dueDateEpochDay: Long)

    @Query("UPDATE bill_occurrences SET isPaid = :isPaid, paidDateEpochDay = :paidDateEpochDay, matchedTransactionId = :matchedTransactionId WHERE billId = :billId AND dueDateEpochDay = :dueDateEpochDay")
    suspend fun updatePaidStatus(
        billId: String,
        dueDateEpochDay: Long,
        isPaid: Boolean,
        paidDateEpochDay: Long?,
        matchedTransactionId: String?
    )
}
