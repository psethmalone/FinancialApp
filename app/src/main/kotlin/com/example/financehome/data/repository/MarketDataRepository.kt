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
        private const val ONE_DAY_SECONDS = 86_400L
    }

    suspend fun getClosePrice(ticker: String, dateEpochDay: Long): Double? {
        val symbol = ticker.uppercase()

        val cached = marketPriceDao.getPrice(symbol, dateEpochDay)
        if (cached != null) {
            Log.d(TAG, "Cache hit: $symbol @ epoch-day $dateEpochDay = ${cached.closePrice}")
            return cached.closePrice
        }

        return try {
            val date = LocalDate.ofEpochDay(dateEpochDay)
            val period1 = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            val period2 = period1 + ONE_DAY_SECONDS

            val response = api.getHistoricalData(
                symbol = symbol,
                interval = "1d",
                periodStart = period1,
                periodEnd = period2,
            )

            val closePrice = response.chart.result
                ?.firstOrNull()
                ?.indicators
                ?.quote
                ?.firstOrNull()
                ?.close
                ?.firstOrNull { it != null }

            if (closePrice != null) {
                marketPriceDao.insert(
                    MarketPrice(
                        ticker = symbol,
                        dateEpochDay = dateEpochDay,
                        closePrice = closePrice,
                        fetchedAtMillis = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Fetched & cached: $symbol @ epoch-day $dateEpochDay = $closePrice")
            }

            closePrice
        } catch (e: Exception) {
            Log.e(TAG, "getClosePrice failed for $symbol on epoch-day $dateEpochDay", e)
            null
        }
    }

    suspend fun getCurrentPrice(ticker: String): Double? {
        val symbol = ticker.uppercase()
        val today = LocalDate.now(ZoneOffset.UTC)

        for (daysBack in 0..7) {
            val candidate = today.minusDays(daysBack.toLong())
            val epochDay = candidate.toEpochDay()

            val cached = marketPriceDao.getPrice(symbol, epochDay)
            if (cached != null) {
                return cached.closePrice
            }

            val price = fetchClosePriceFromNetwork(symbol, candidate)
            if (price != null) {
                marketPriceDao.insert(
                    MarketPrice(
                        ticker = symbol,
                        dateEpochDay = epochDay,
                        closePrice = price,
                        fetchedAtMillis = System.currentTimeMillis()
                    )
                )
                return price
            }
        }

        return null
    }

    suspend fun getPhantomPnL(
        amount: Double,
        ticker: String,
        spendDateEpochDay: Long,
    ): Pair<Double, Double>? {
        if (amount <= 0.0) return null

        val symbol = ticker.uppercase()

        val buyPrice = getClosePrice(symbol, spendDateEpochDay) ?: return null
        if (buyPrice <= 0.0) return null

        val currentPrice = getCurrentPrice(symbol) ?: return null

        val shares = amount / buyPrice
        val currentValue = shares * currentPrice
        val gainLoss = currentValue - amount

        return Pair(currentValue, gainLoss)
    }

    private suspend fun fetchClosePriceFromNetwork(symbol: String, date: LocalDate): Double? {
        return try {
            val period1 = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            val period2 = period1 + ONE_DAY_SECONDS

            val response = api.getHistoricalData(
                symbol = symbol,
                interval = "1d",
                periodStart = period1,
                periodEnd = period2,
            )

            response.chart.result
                ?.firstOrNull()
                ?.indicators
                ?.quote
                ?.firstOrNull()
                ?.close
                ?.firstOrNull { it != null }
        } catch (e: Exception) {
            Log.e(TAG, "fetchClosePriceFromNetwork failed for $symbol on $date", e)
            null
        }
    }
}
