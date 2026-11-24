package com.example.financehome.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TradeAction {
    BUY,
    SELL
}

enum class TradeSource {
    NOTIFICATION,
    MANUAL
}

@Entity(tableName = "portfolio_holdings")
data class PortfolioHolding(
    @PrimaryKey val ticker: String,
    val totalShares: Double,
    val totalCostBasis: Double,
    val averageBuyPrice: Double,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolio_trades")
data class PortfolioTrade(
    @PrimaryKey val id: String,
    val ticker: String,
    val action: TradeAction,
    val shares: Double,
    val pricePerShare: Double,
    val totalAmount: Double,
    val timestampMillis: Long,
    val source: TradeSource = TradeSource.MANUAL,
    val rawNotificationText: String? = null,
    val notes: String? = null
)
