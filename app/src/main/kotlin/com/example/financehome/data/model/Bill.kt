package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class BillRecurrence(val displayName: String) {
    MONTHLY("Monthly"),
    BI_WEEKLY("Bi-Weekly"),
    WEEKLY("Weekly"),
    YEARLY("Yearly")
}

/**
 * Represents a recurring fixed bill (e.g. Rent, Electricity, Car Insurance, Internet, Subscriptions).
 *
 * Unlike [Debt], a bill does not carry an APR or a running balance; it is a recurring overhead
 * payment due on a specific recurrence schedule.
 *
 * [dueDayOfMonth] ranges from 1 to 31.
 */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey val id: String,
    val name: String,
    val amount: Double,
    val dueDayOfMonth: Int,
    val category: String,
    val isAutoPay: Boolean = false,
    val isPaidThisMonth: Boolean = false,
    val lastPaidTimestampMillis: Long? = null,
    val notes: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val recurrence: BillRecurrence = BillRecurrence.MONTHLY,
    val nextDueDateEpochDay: Long = 0L,
    val lastSettledDateEpochDay: Long? = null
) {
    /**
     * Resolves the upcoming concrete due date for this bill.
     * If [nextDueDateEpochDay] is stored (> 0), converts it to [LocalDate].
     * Otherwise, calculates the upcoming chronological occurrence based on [dueDayOfMonth] and [currentDate].
     */
    fun getEffectiveNextDueDate(currentDate: LocalDate = LocalDate.now()): LocalDate {
        if (nextDueDateEpochDay > 0L) {
            return LocalDate.ofEpochDay(nextDueDateEpochDay)
        }
        val thisMonthDue = try {
            currentDate.withDayOfMonth(minOf(dueDayOfMonth, currentDate.lengthOfMonth()))
        } catch (_: Exception) {
            currentDate
        }
        return if (currentDate.isAfter(thisMonthDue)) {
            val nextMonth = currentDate.plusMonths(1)
            nextMonth.withDayOfMonth(minOf(dueDayOfMonth, nextMonth.lengthOfMonth()))
        } else {
            thisMonthDue
        }
    }

    /**
     * Determines whether a specific scheduled date occurrence has been settled.
     * An occurrence is settled if it falls strictly before the effective [nextDueDate],
     * OR if it explicitly matches [lastSettledDateEpochDay].
     */
    fun isPaidForDate(date: LocalDate, currentDate: LocalDate = LocalDate.now()): Boolean {
        if (lastSettledDateEpochDay != null && date.toEpochDay() == lastSettledDateEpochDay) {
            return true
        }
        if (nextDueDateEpochDay > 0L) {
            return date.isBefore(LocalDate.ofEpochDay(nextDueDateEpochDay))
        }
        return false
    }

    /**
     * Advances a concrete due date by one recurrence interval.
     */
    fun advanceDueDate(currentDue: LocalDate): LocalDate {
        return when (recurrence) {
            BillRecurrence.MONTHLY -> {
                val nextMonth = currentDue.plusMonths(1)
                nextMonth.withDayOfMonth(minOf(dueDayOfMonth, nextMonth.lengthOfMonth()))
            }
            BillRecurrence.BI_WEEKLY -> currentDue.plusWeeks(2)
            BillRecurrence.WEEKLY -> currentDue.plusWeeks(1)
            BillRecurrence.YEARLY -> {
                val nextYear = currentDue.plusYears(1)
                nextYear.withDayOfMonth(minOf(dueDayOfMonth, nextYear.lengthOfMonth()))
            }
        }
    }

    /**
     * Steps back a concrete due date by one recurrence interval to roll back / undo a settlement.
     */
    fun rollbackDueDate(currentDue: LocalDate): LocalDate {
        return when (recurrence) {
            BillRecurrence.MONTHLY -> {
                val prevMonth = currentDue.minusMonths(1)
                prevMonth.withDayOfMonth(minOf(dueDayOfMonth, prevMonth.lengthOfMonth()))
            }
            BillRecurrence.BI_WEEKLY -> currentDue.minusWeeks(2)
            BillRecurrence.WEEKLY -> currentDue.minusWeeks(1)
            BillRecurrence.YEARLY -> {
                val prevYear = currentDue.minusYears(1)
                prevYear.withDayOfMonth(minOf(dueDayOfMonth, prevYear.lengthOfMonth()))
            }
        }
    }
}
