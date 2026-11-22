package com.example.financehome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financehome.data.model.MarketPrice

/**
 * Data-access object for [MarketPrice] entities.
 *
 * All operations are one-shot (`suspend`) because market price reads are
 * always explicit cache lookups — there is no need for reactive observation.
 */
@Dao
interface MarketPriceDao {

    /**
     * Inserts (or replaces) a cached market price.
     * REPLACE handles the case where a fresh fetch overwrites a stale entry
     * for the same (ticker, dateEpochDay) composite key.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mp: MarketPrice)

    /**
     * Returns the cached close price for [ticker] on the day identified by
     * [dateEpochDay] (see [java.time.LocalDate.toEpochDay]), or null if not
     * yet cached.
     */
    @Query(
        """
        SELECT * FROM market_prices
        WHERE ticker = :ticker AND dateEpochDay = :dateEpochDay
        LIMIT 1
        """
    )
    suspend fun getPrice(ticker: String, dateEpochDay: Long): MarketPrice?

    /**
     * Returns the most recently fetched price for [ticker] across all cached
     * dates, or null if no prices are cached for that ticker.
     */
    @Query(
        """
        SELECT * FROM market_prices
        WHERE ticker = :ticker
        ORDER BY dateEpochDay DESC
        LIMIT 1
        """
    )
    suspend fun getLatestPrice(ticker: String): MarketPrice?

    /**
     * Deletes all cached prices whose [MarketPrice.fetchedAtMillis] is older
     * than [thresholdMillis].  Call periodically (e.g. on app start) to keep
     * the cache from growing unbounded.
     */
    @Query("DELETE FROM market_prices WHERE fetchedAtMillis < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)
}
