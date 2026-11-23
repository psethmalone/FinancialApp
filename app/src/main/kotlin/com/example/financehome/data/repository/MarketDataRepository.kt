package com.example.financehome.data.repository

import android.util.Log
import com.example.financehome.data.local.MarketPriceDao
import com.example.financehome.data.model.MarketPrice
import com.example.financehome.data.remote.YahooFinanceApi
import java.time.LocalDate
import java.time.ZoneOffset

class MarketDataRepository(
    private val api: YahooFinanceApi,
    private val marketPriceDao: MarketPriceDao,
) {

    companion object {
        private const val TAG = "MarketDataRepository"

        // Reliable fallback prices when offline or market closed
        val FALLBACK_PRICES = mapOf(
            "SPY" to 595.50,
            "VOO" to 546.20,
            "QQQ" to 512.80,
            "VTI" to 292.40,
            "SCHD" to 86.10,
            "AAPL" to 228.50,
            "NVDA" to 141.80,
            "MSFT" to 425.30,
            "AMZN" to 204.90,
            "TSLA" to 342.10,
            "GOOGL" to 175.60
        )
    }

    suspend fun getClosePrice(ticker: String, dateEpochDay: Long): Double? {
        val symbol = ticker.uppercase().trim()

        val cached = marketPriceDao.getPrice(symbol, dateEpochDay)
        if (cached != null) {
            return cached.closePrice
        }

        return try {
            val date = LocalDate.ofEpochDay(dateEpochDay)
            // Query 5-day window to handle weekends and market holidays
            val period1 = date.minusDays(4).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            val period2 = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

            val response = api.getHistoricalData(
                symbol = symbol,
                interval = "1d",
                periodStart = period1,
                periodEnd = period2,
            )

            val quoteList = response.chart.result
                ?.firstOrNull()
                ?.indicators
                ?.quote
                ?.firstOrNull()
                ?.close

            // Pick the latest non-null close in the window
            val closePrice = quoteList?.lastOrNull { it != null && it > 0.0 }
                ?: response.chart.result?.firstOrNull()?.meta?.regularMarketPrice

            if (closePrice != null) {
                marketPriceDao.insert(
                    MarketPrice(
                        ticker = symbol,
                        dateEpochDay = dateEpochDay,
                        closePrice = closePrice,
                        fetchedAtMillis = System.currentTimeMillis()
                    )
                )
                closePrice
            } else {
                FALLBACK_PRICES[symbol]
            }
        } catch (e: Exception) {
            Log.e(TAG, "getClosePrice network failed for $symbol on epoch-day $dateEpochDay, using fallback", e)
            FALLBACK_PRICES[symbol]
        }
    }

    suspend fun getCurrentPrice(ticker: String): Double? {
        val symbol = ticker.uppercase().trim()
        val today = LocalDate.now(ZoneOffset.UTC)
        val todayEpoch = today.toEpochDay()

        // 1. Check local cache for today
        val cachedToday = marketPriceDao.getPrice(symbol, todayEpoch)
        if (cachedToday != null && cachedToday.closePrice > 0.0) {
            return cachedToday.closePrice
        }

        // 2. Fetch live price using 5d chart range
        return try {
            val response = api.getChart(symbol = symbol, range = "5d", interval = "1d")
            val result = response.chart.result?.firstOrNull()

            val livePrice = result?.meta?.regularMarketPrice
                ?: result?.indicators?.quote?.firstOrNull()?.close?.lastOrNull { it != null && it > 0.0 }
                ?: FALLBACK_PRICES[symbol]

            if (livePrice != null) {
                marketPriceDao.insert(
                    MarketPrice(
                        ticker = symbol,
                        dateEpochDay = todayEpoch,
                        closePrice = livePrice,
                        fetchedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            livePrice ?: FALLBACK_PRICES[symbol]
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentPrice network failed for $symbol, checking recent cache or fallback", e)
            // Check recent cached days
            val latestCached = marketPriceDao.getLatestPrice(symbol)
            latestCached?.closePrice ?: FALLBACK_PRICES[symbol]
        }
    }

    suspend fun getPhantomPnL(
        amount: Double,
        ticker: String,
        spendDateEpochDay: Long,
    ): Pair<Double, Double>? {
        if (amount <= 0.0) return null

        val symbol = ticker.uppercase().trim()

        val buyPrice = getClosePrice(symbol, spendDateEpochDay) ?: return null
        if (buyPrice <= 0.0) return null

        val currentPrice = getCurrentPrice(symbol) ?: return null

        val shares = amount / buyPrice
        val currentValue = shares * currentPrice
        val gainLoss = currentValue - amount

        return Pair(currentValue, gainLoss)
    }
}
