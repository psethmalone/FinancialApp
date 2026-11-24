package com.example.financehome.data.repository

import com.example.financehome.data.local.PortfolioDao
import com.example.financehome.data.model.PortfolioHolding
import com.example.financehome.data.model.PortfolioTrade
import com.example.financehome.data.model.TradeAction
import com.example.financehome.data.model.TradeSource
import com.example.financehome.service.ParsedBrokerageTrade
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class HoldingPerformance(
    val holding: PortfolioHolding,
    val currentPrice: Double,
    val currentMarketValue: Double,
    val totalGainLoss: Double,
    val totalGainLossPercent: Double
)

data class PortfolioSummary(
    val totalMarketValue: Double,
    val totalCostBasis: Double,
    val totalGainLoss: Double,
    val totalGainLossPercent: Double,
    val holdingsCount: Int
)

@Singleton
class PortfolioRepository @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val marketDataRepo: MarketDataRepository
) {
    val holdings: Flow<List<PortfolioHolding>> = portfolioDao.getAllHoldings()
    val trades: Flow<List<PortfolioTrade>> = portfolioDao.getAllTrades()

    suspend fun recordTrade(
        ticker: String,
        action: TradeAction,
        shares: Double,
        pricePerShare: Double,
        source: TradeSource = TradeSource.MANUAL,
        rawText: String? = null,
        notes: String? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): PortfolioHolding {
        val symbol = ticker.uppercase().trim()
        val totalAmount = shares * pricePerShare
        val trade = PortfolioTrade(
            id = UUID.randomUUID().toString(),
            ticker = symbol,
            action = action,
            shares = shares,
            pricePerShare = pricePerShare,
            totalAmount = totalAmount,
            timestampMillis = timestampMillis,
            source = source,
            rawNotificationText = rawText,
            notes = notes
        )
        portfolioDao.insertTrade(trade)

        val existing = portfolioDao.getHoldingByTicker(symbol)
        val updatedHolding = if (existing == null) {
            if (action == TradeAction.BUY) {
                PortfolioHolding(
                    ticker = symbol,
                    totalShares = shares,
                    totalCostBasis = totalAmount,
                    averageBuyPrice = pricePerShare,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            } else {
                PortfolioHolding(
                    ticker = symbol,
                    totalShares = 0.0,
                    totalCostBasis = 0.0,
                    averageBuyPrice = 0.0,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            }
        } else {
            if (action == TradeAction.BUY) {
                val newShares = existing.totalShares + shares
                val newCost = existing.totalCostBasis + totalAmount
                val newAvg = if (newShares > 0) newCost / newShares else 0.0
                existing.copy(
                    totalShares = newShares,
                    totalCostBasis = newCost,
                    averageBuyPrice = newAvg,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            } else {
                // SELL: reduce shares, reduce cost basis proportionally
                val newShares = maxOf(0.0, existing.totalShares - shares)
                val newCost = if (existing.totalShares > 0) {
                    existing.totalCostBasis * (newShares / existing.totalShares)
                } else 0.0
                val newAvg = if (newShares > 0) newCost / newShares else 0.0
                existing.copy(
                    totalShares = newShares,
                    totalCostBasis = newCost,
                    averageBuyPrice = newAvg,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            }
        }

        portfolioDao.insertOrUpdateHolding(updatedHolding)
        return updatedHolding
    }

    suspend fun recordBrokerageNotificationTrade(parsed: ParsedBrokerageTrade): PortfolioHolding {
        var price = parsed.pricePerShare
        var shares = parsed.shares
        if (price <= 0.0 && parsed.totalAmount > 0.0) {
            val live = marketDataRepo.getCurrentPrice(parsed.ticker) ?: 100.0
            price = live
            shares = parsed.totalAmount / price
        } else if (shares <= 0.0 && parsed.totalAmount > 0.0 && price > 0.0) {
            shares = parsed.totalAmount / price
        }
        return recordTrade(
            ticker = parsed.ticker,
            action = parsed.action,
            shares = shares,
            pricePerShare = price,
            source = TradeSource.NOTIFICATION,
            rawText = parsed.rawText,
            timestampMillis = System.currentTimeMillis()
        )
    }

    suspend fun deleteTrade(trade: PortfolioTrade) {
        portfolioDao.deleteTrade(trade)
    }

    suspend fun deleteHolding(holding: PortfolioHolding) {
        portfolioDao.deleteHolding(holding)
    }

    suspend fun setHolding(ticker: String, shares: Double, averageCost: Double): PortfolioHolding {
        val symbol = ticker.uppercase().trim()
        val totalCost = shares * averageCost
        val holding = PortfolioHolding(
            ticker = symbol,
            totalShares = shares,
            totalCostBasis = totalCost,
            averageBuyPrice = averageCost,
            lastUpdatedMillis = System.currentTimeMillis()
        )
        portfolioDao.insertOrUpdateHolding(holding)
        return holding
    }
}
