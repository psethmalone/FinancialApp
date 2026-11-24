package com.example.financehome.data.repository

import com.example.financehome.data.local.MarketPriceDao
import com.example.financehome.data.local.PortfolioDao
import com.example.financehome.data.model.MarketPrice
import com.example.financehome.data.model.PortfolioHolding
import com.example.financehome.data.model.PortfolioTrade
import com.example.financehome.data.model.TradeAction
import com.example.financehome.data.remote.YahooFinanceApi
import com.example.financehome.data.remote.YahooFinanceResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class PortfolioRepositoryTest {

    private class FakePortfolioDao : PortfolioDao {
        val holdingsMap = mutableMapOf<String, PortfolioHolding>()
        val tradesList = mutableListOf<PortfolioTrade>()

        override fun getAllHoldings(): Flow<List<PortfolioHolding>> = flowOf(holdingsMap.values.toList())

        override suspend fun getHoldingByTicker(ticker: String): PortfolioHolding? = holdingsMap[ticker]

        override suspend fun insertOrUpdateHolding(holding: PortfolioHolding) {
            holdingsMap[holding.ticker] = holding
        }

        override suspend fun deleteHolding(holding: PortfolioHolding) {
            holdingsMap.remove(holding.ticker)
        }

        override suspend fun deleteHoldingByTicker(ticker: String) {
            holdingsMap.remove(ticker)
        }

        override fun getAllTrades(): Flow<List<PortfolioTrade>> = flowOf(tradesList)

        override fun getTradesForTicker(ticker: String): Flow<List<PortfolioTrade>> =
            flowOf(tradesList.filter { it.ticker == ticker })

        override suspend fun insertTrade(trade: PortfolioTrade) {
            tradesList.add(trade)
        }

        override suspend fun deleteTrade(trade: PortfolioTrade) {
            tradesList.remove(trade)
        }
    }

    private class FakeMarketPriceDao : MarketPriceDao {
        override suspend fun insert(price: MarketPrice) {}
        override suspend fun getPrice(ticker: String, dateEpochDay: Long): MarketPrice? = null
        override suspend fun getLatestPrice(ticker: String): MarketPrice? = null
        override suspend fun deleteOlderThan(thresholdMillis: Long) {}
    }

    private class FakeYahooApi : YahooFinanceApi {
        override suspend fun getHistoricalData(symbol: String, interval: String, periodStart: Long, periodEnd: Long): YahooFinanceResponse {
            throw UnsupportedOperationException("Not needed in test")
        }

        override suspend fun getChart(symbol: String, range: String, interval: String): YahooFinanceResponse {
            throw UnsupportedOperationException("Not needed in test")
        }
    }

    private lateinit var fakeDao: FakePortfolioDao
    private lateinit var repo: PortfolioRepository

    @Before
    fun setup() {
        fakeDao = FakePortfolioDao()
        val marketRepo = MarketDataRepository(FakeYahooApi(), FakeMarketPriceDao())
        repo = PortfolioRepository(fakeDao, marketRepo)
    }

    @Test
    fun testInitialBuyOrder_createsPosition() = runBlocking {
        val holding = repo.recordTrade(
            ticker = "VOO",
            action = TradeAction.BUY,
            shares = 10.0,
            pricePerShare = 500.0
        )

        assertEquals("VOO", holding.ticker)
        assertEquals(10.0, holding.totalShares, 0.001)
        assertEquals(5000.0, holding.totalCostBasis, 0.001)
        assertEquals(500.0, holding.averageBuyPrice, 0.001)
    }

    @Test
    fun testSubsequentBuy_updatesWeightedAverageCostBasis() = runBlocking {
        // Buy 10 shares @ $500 -> $5,000 cost
        repo.recordTrade("VOO", TradeAction.BUY, 10.0, 500.0)

        // Buy 10 shares @ $600 -> $6,000 cost
        // Total shares = 20, Total cost = $11,000 -> Avg buy price = $550
        val updated = repo.recordTrade("VOO", TradeAction.BUY, 10.0, 600.0)

        assertEquals(20.0, updated.totalShares, 0.001)
        assertEquals(11000.0, updated.totalCostBasis, 0.001)
        assertEquals(550.0, updated.averageBuyPrice, 0.001)
    }

    @Test
    fun testPartialSell_scalesCostBasisProportionallyWithoutChangingAvgCost() = runBlocking {
        // Buy 20 shares @ $550 = $11,000 total cost
        repo.setHolding("VOO", 20.0, 550.0)

        // Sell 5 shares @ $620
        // Remaining shares = 15
        // Remaining cost basis = 15 * 550 = $8,250
        // Avg buy price remains $550
        val afterSell = repo.recordTrade("VOO", TradeAction.SELL, 5.0, 620.0)

        assertEquals(15.0, afterSell.totalShares, 0.001)
        assertEquals(8250.0, afterSell.totalCostBasis, 0.001)
        assertEquals(550.0, afterSell.averageBuyPrice, 0.001)
    }

    @Test
    fun testDirectPositionOverride() = runBlocking {
        repo.setHolding("AAPL", 50.0, 220.0)
        val holding = fakeDao.getHoldingByTicker("AAPL")

        assertNotNull(holding)
        assertEquals(50.0, holding!!.totalShares, 0.001)
        assertEquals(11000.0, holding.totalCostBasis, 0.001)
        assertEquals(220.0, holding.averageBuyPrice, 0.001)
    }
}
