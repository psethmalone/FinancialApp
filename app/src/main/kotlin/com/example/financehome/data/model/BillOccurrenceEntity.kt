package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

typealias RecurringBillEntity = Bill

@Entity(
    tableName = "bill_occurrences",
    primaryKeys = ["billId", "dueDateEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("billId"),
        Index("dueDateEpochDay")
    ]
)
data class BillOccurrenceEntity(
    val billId: String,
    val dueDateEpochDay: Long,
    val isPaid: Boolean = false,
    val paidDateEpochDay: Long? = null,
    val matchedTransactionId: String? = null,
    val amount: Double = 0.0
)
