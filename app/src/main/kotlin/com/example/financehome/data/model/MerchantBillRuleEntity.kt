package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_bill_rules")
data class MerchantBillRuleEntity(
    @PrimaryKey val normalizedMerchant: String,
    val billId: String,
    val isDebt: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
