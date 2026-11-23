package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a recurring fixed bill (e.g. Rent, Electricity, Car Insurance, Internet, Subscriptions).
 *
 * Unlike [Debt], a bill does not carry an APR or a running balance; it is a recurring monthly
 * overhead payment due on a specific day of the month.
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
    val isSynced: Boolean = false
)
