package com.example.financehome.data.repository

import com.example.financehome.data.local.DebtDao
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [Debt] data.
 *
 * All reactive reads return [Flow] backed by Room so that the UI
 * recomposes automatically on any database change. Write helpers are
 * plain `suspend` functions safe to call from any coroutine context.
 */
@Singleton
class DebtRepository @Inject constructor(
    private val debtDao: DebtDao,
) {

    // ── Reactive reads ────────────────────────────────────────────────────────

    /** Emits the full list of debts, ordered by current balance descending. */
    fun getAllDebts(): Flow<List<Debt>> = debtDao.getAllDebts()

    // ── One-shot reads ────────────────────────────────────────────────────────

    suspend fun getDebtById(id: String): Debt? = debtDao.getDebtById(id)

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Creates and persists a new [Debt].
     *
     * Note: [apr] should be passed as a **fraction** (e.g. 0.2199 for 21.99 %).
     * The UI layer is responsible for converting from percentage input.
     */
    suspend fun addDebt(
        name: String,
        balance: Double,
        apr: Double,
        minimumPayment: Double,
        type: DebtType,
        dueDayOfMonth: Int = 15
    ) {
        val debt = Debt(
            id = UUID.randomUUID().toString(),
            name = name,
            originalBalance = balance,
            currentBalance = balance,
            apr = apr,
            minimumPayment = minimumPayment,
            type = type,
            createdAtMillis = System.currentTimeMillis(),
            isSynced = false,
            dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31),
            isPaidThisMonth = false
        )
        debtDao.insertDebt(debt)
    }

    suspend fun updateDebt(debt: Debt) = debtDao.updateDebt(debt)

    suspend fun deleteDebt(debt: Debt) = debtDao.deleteDebt(debt)

    suspend fun toggleDebtPaid(debt: Debt) {
        val newPaid = !debt.isPaidThisMonth
        val timestamp = if (newPaid) System.currentTimeMillis() else null
        debtDao.markPaid(debt.id, newPaid, timestamp)
    }
}
