package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an auto-categorization rule.
 *
 * When a transaction arrives with a merchant name that contains [keyword] (case-insensitive),
 * it is automatically assigned [category], flagged as discretionary ([isDiscretionary] = true for Wants,
 * false for Needs), and tagged if it represents a recurring monthly obligation like rent ([isRecurringRent]).
 */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val id: String,
    val keyword: String,
    val category: String,
    val isDiscretionary: Boolean,
    val isRecurringRent: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
