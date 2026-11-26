package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Enum
// ---------------------------------------------------------------------------

enum class DebtType { CREDIT_CARD, LOAN, MORTGAGE, OTHER }

// ---------------------------------------------------------------------------
// Entity
// ---------------------------------------------------------------------------

/**
 * Represents a single debt account tracked by the user (credit card, loan,
 * mortgage, or other).
 *
 * [apr] is stored as a decimal fraction, e.g. 0.2199 represents 21.99% APR.
 *
 * [isSynced] tracks whether this record has been successfully pushed to the
 * remote backend; defaults to false on creation.
 */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey val id: String,
    val name: String,
    val originalBalance: Double,
    val currentBalance: Double,
    val apr: Double,
    val minimumPayment: Double,
    val type: DebtType,
    val createdAtMillis: Long,
    val isSynced: Boolean = false,
    val dueDayOfMonth: Int = 15,
    val isPaidThisMonth: Boolean = false,
    val lastPaidTimestampMillis: Long? = null,
    val nextDueDateEpochDay: Long = 0L,
    val lastSettledDateEpochDay: Long? = null
) {
    /**
     * Resolves the upcoming concrete due date for this debt.
     * If [nextDueDateEpochDay] is stored (> 0), converts it to [java.time.LocalDate].
     * Otherwise, calculates the upcoming chronological occurrence based on [dueDayOfMonth] and [currentDate].
     */
    fun getEffectiveNextDueDate(currentDate: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate {
        if (nextDueDateEpochDay > 0L) {
            return java.time.LocalDate.ofEpochDay(nextDueDateEpochDay)
        }
        val thisMonthDue = try {
            currentDate.withDayOfMonth(minOf(dueDayOfMonth, currentDate.lengthOfMonth()))
        } catch (_: Exception) {
            currentDate
        }
        return if (isPaidThisMonth) {
            advanceDueDate(thisMonthDue)
        } else {
            thisMonthDue
        }
    }

    /**
     * Advances a concrete due date by one month.
     */
    fun advanceDueDate(currentDue: java.time.LocalDate): java.time.LocalDate {
        val nextMonth = currentDue.plusMonths(1)
        val targetDay = minOf(dueDayOfMonth, nextMonth.lengthOfMonth())
        return nextMonth.withDayOfMonth(targetDay)
    }

    /**
     * Determines whether a specific scheduled date occurrence has been settled.
     * An occurrence is settled if it explicitly matches [lastSettledDateEpochDay],
     * OR if it falls strictly before the effective [nextDueDate].
     * In fallback mode (when single-cycle tracking is not initialized), it strictly
     * checks only the current month and returns false for any other month.
     */
    fun isPaidForDate(date: java.time.LocalDate, currentDate: java.time.LocalDate = java.time.LocalDate.now()): Boolean {
        if (lastSettledDateEpochDay != null && date.toEpochDay() == lastSettledDateEpochDay) {
            return true
        }
        if (nextDueDateEpochDay > 0L) {
            return date.isBefore(java.time.LocalDate.ofEpochDay(nextDueDateEpochDay))
        }
        // Fallback for debts where single-cycle tracking is not yet initialized:
        // ONLY valid for the active current month. NEVER marks future or past months as paid!
        if (date.year == currentDate.year && date.monthValue == currentDate.monthValue) {
            return isPaidThisMonth
        }
        return false
    }
}
