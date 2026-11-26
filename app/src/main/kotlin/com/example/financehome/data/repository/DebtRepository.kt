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

    suspend fun toggleDebtPaid(
        debt: Debt,
        targetDate: java.time.LocalDate? = null,
        currentDate: java.time.LocalDate = java.time.LocalDate.now()
    ) {
        val thisMonthDue = try {
            currentDate.withDayOfMonth(minOf(debt.dueDayOfMonth, currentDate.lengthOfMonth()))
        } catch (_: Exception) {
            currentDate
        }
        val dueToSettle = targetDate ?: thisMonthDue
        val currentlyPaid = debt.isPaidForDate(dueToSettle, currentDate)
        if (currentlyPaid) {
            unmarkDebtPaid(debt, targetDate = dueToSettle, currentDate = currentDate)
        } else {
            markDebtPaidForCycle(debt, targetDate = dueToSettle, currentDate = currentDate)
        }
    }

    suspend fun markDebtPaidForCycle(
        debt: Debt,
        targetDate: java.time.LocalDate? = null,
        currentDate: java.time.LocalDate = java.time.LocalDate.now()
    ) {
        val dueToSettle = targetDate ?: debt.getEffectiveNextDueDate(currentDate)
        val advancedDue = debt.advanceDueDate(dueToSettle)
        val isPaidThisMonth = (dueToSettle.year == currentDate.year && dueToSettle.monthValue == currentDate.monthValue)
        val timestamp = System.currentTimeMillis()

        debtDao.updateDebtSettlement(
            id = debt.id,
            nextDueDateEpochDay = advancedDue.toEpochDay(),
            lastSettledDateEpochDay = dueToSettle.toEpochDay(),
            isPaid = isPaidThisMonth,
            timestamp = timestamp
        )
    }

    suspend fun unmarkDebtPaid(
        debt: Debt,
        targetDate: java.time.LocalDate? = null,
        currentDate: java.time.LocalDate = java.time.LocalDate.now()
    ) {
        val thisMonthDue = try {
            currentDate.withDayOfMonth(minOf(debt.dueDayOfMonth, currentDate.lengthOfMonth()))
        } catch (_: Exception) {
            currentDate
        }
        val target = targetDate ?: thisMonthDue
        val isThisCurrentMonth = (target.year == currentDate.year && target.monthValue == currentDate.monthValue)
        val isStillPaidThisMonth = if (isThisCurrentMonth) false else debt.isPaidThisMonth
        val restoredNextDue = target.toEpochDay()

        debtDao.updateDebtSettlement(
            id = debt.id,
            nextDueDateEpochDay = restoredNextDue,
            lastSettledDateEpochDay = null,
            isPaid = isStillPaidThisMonth,
            timestamp = null
        )
    }

    suspend fun payDebt(
        debtId: String,
        amount: Double,
        targetDate: java.time.LocalDate? = null,
        currentDate: java.time.LocalDate = java.time.LocalDate.now()
    ) {
        val debt = debtDao.getDebtById(debtId) ?: return
        val newBalance = maxOf(0.0, debt.currentBalance - amount)
        val dueToSettle = targetDate ?: debt.getEffectiveNextDueDate(currentDate)
        val advancedDue = debt.advanceDueDate(dueToSettle)
        val isPaidThisMonth = (dueToSettle.year == currentDate.year && dueToSettle.monthValue == currentDate.monthValue)
        val timestamp = System.currentTimeMillis()

        debtDao.updateDebt(
            debt.copy(
                currentBalance = newBalance,
                nextDueDateEpochDay = advancedDue.toEpochDay(),
                lastSettledDateEpochDay = dueToSettle.toEpochDay(),
                isPaidThisMonth = isPaidThisMonth,
                lastPaidTimestampMillis = timestamp
            )
        )
    }
}
