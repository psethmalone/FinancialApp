package com.example.financehome.data.model

import androidx.room.Entity

/**
 * Cached historical market close price for a given ticker and calendar day.
 *
 * Uses a composite primary key of ([ticker], [dateEpochDay]) so there is
 * exactly one cached price per ticker per day.
 *
 * [dateEpochDay] is obtained via [java.time.LocalDate.toEpochDay] — the number
 * of days since the Unix epoch (1970-01-01).
 *
 * [fetchedAtMillis] records when this row was inserted, allowing the app to
 * expire stale cache entries via [MarketPriceDao.deleteOlderThan].
 */
@Entity(
    tableName = "market_prices",
    primaryKeys = ["ticker", "dateEpochDay"]
)
data class MarketPrice(
    val ticker: String,
    val dateEpochDay: Long,
    val closePrice: Double,
    val fetchedAtMillis: Long
)
