package com.example.financehome.data.local

import androidx.room.TypeConverter
import com.example.financehome.data.model.DebtType
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.data.model.TransactionType

/**
 * Room [TypeConverter]s for all enum types used by the database entities.
 *
 * Each enum is persisted as its [name] string so that the stored values remain
 * human-readable in the SQLite file and are resilient to ordinal reordering.
 */
class Converters {

    // ── TransactionType ──────────────────────────────────────────────────────

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        TransactionType.valueOf(value)

    // ── TransactionSource ────────────────────────────────────────────────────

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource =
        TransactionSource.valueOf(value)

    // ── DebtType ─────────────────────────────────────────────────────────────

    @TypeConverter
    fun fromDebtType(value: DebtType): String = value.name

    @TypeConverter
    fun toDebtType(value: String): DebtType =
        DebtType.valueOf(value)

    // ── TradeAction & TradeSource ─────────────────────────────────────────────

    @TypeConverter
    fun fromTradeAction(value: com.example.financehome.data.model.TradeAction): String = value.name

    @TypeConverter
    fun toTradeAction(value: String): com.example.financehome.data.model.TradeAction =
        com.example.financehome.data.model.TradeAction.valueOf(value)

    @TypeConverter
    fun fromTradeSource(value: com.example.financehome.data.model.TradeSource): String = value.name

    @TypeConverter
    fun toTradeSource(value: String): com.example.financehome.data.model.TradeSource =
        com.example.financehome.data.model.TradeSource.valueOf(value)
}
