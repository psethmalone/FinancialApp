package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ReceiptItemCategory(val displayName: String, val emoji: String) {
    GROCERY("Groceries", "🛒"),
    FUEL("Fuel / Gas", "⛽"),
    HOUSEHOLD("Household", "🧻"),
    ELECTRONICS("Electronics", "🔌"),
    CLOTHING("Clothing", "👕"),
    TAX("Tax", "🧾"),
    OTHER("Other", "📦")
}

@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"])]
)
data class ReceiptItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val name: String,
    val price: Double,
    val quantity: Double = 1.0,
    val unitPrice: Double? = null,
    val category: ReceiptItemCategory = ReceiptItemCategory.GROCERY,
    val notes: String? = null
)
