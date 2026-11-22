package com.example.financehome.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.financehome.data.model.Debt
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [Debt] entities.
 *
 * Reactive reads return [Flow]; sync helpers are plain `suspend` functions.
 */
@Dao
interface DebtDao {

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Inserts a debt record.  REPLACE strategy lets callers upsert by ID
     * without needing a separate update path.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(d: Debt)

    @Update
    suspend fun updateDebt(d: Debt)

    @Delete
    suspend fun deleteDebt(d: Debt)

    // ── Reads (reactive) ──────────────────────────────────────────────────────

    /** Emits all debts ordered by current balance descending (highest first). */
    @Query("SELECT * FROM debts ORDER BY currentBalance DESC")
    fun getAllDebts(): Flow<List<Debt>>

    // ── Reads (one-shot) ──────────────────────────────────────────────────────

    /** Returns the debt with [id], or null if not found. */
    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtById(id: String): Debt?

    /** Returns all debts not yet pushed to the remote backend. */
    @Query("SELECT * FROM debts WHERE isSynced = 0")
    suspend fun getUnsyncedDebts(): List<Debt>

    /** Marks the debt with [id] as successfully synced. */
    @Query("UPDATE debts SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
