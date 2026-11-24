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
    val lastPaidTimestampMillis: Long? = null
)
