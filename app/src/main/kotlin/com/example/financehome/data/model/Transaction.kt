package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class TransactionType { DEBIT, CREDIT }

enum class TransactionSource { NOTIFICATION, CSV, MANUAL }

// ---------------------------------------------------------------------------
// Entity
// ---------------------------------------------------------------------------

/**
 * Represents a single financial transaction captured from a notification,
 * imported from CSV, or entered manually by the user.
 *
 * [isDiscretionary] is used for opportunity-cost calculations — discretionary
 * spending is spending that could theoretically have been invested instead.
 *
 * [isSynced] tracks whether this record has been successfully pushed to the
 * remote backend; defaults to false on creation.
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,                    // UUID
    val amount: Double,
    val merchant: String,
    val category: String,
    val type: TransactionType,
    val source: TransactionSource,
    val timestampMillis: Long,
    val rawNotificationText: String?,
    val notes: String?,
    val isDiscretionary: Boolean,
    val isSynced: Boolean = false
)
