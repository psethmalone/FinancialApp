package com.example.financehome.data.local

import androidx.room.*
import com.example.financehome.data.model.PortfolioHolding
import com.example.financehome.data.model.PortfolioTrade
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {

    // ── Holdings ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM portfolio_holdings ORDER BY ticker ASC")
    fun getAllHoldings(): Flow<List<PortfolioHolding>>

    @Query("SELECT * FROM portfolio_holdings WHERE ticker = :ticker LIMIT 1")
    suspend fun getHoldingByTicker(ticker: String): PortfolioHolding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHolding(holding: PortfolioHolding)

    @Delete
    suspend fun deleteHolding(holding: PortfolioHolding)

    @Query("DELETE FROM portfolio_holdings WHERE ticker = :ticker")
    suspend fun deleteHoldingByTicker(ticker: String)

    // ── Trades ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM portfolio_trades ORDER BY timestampMillis DESC")
    fun getAllTrades(): Flow<List<PortfolioTrade>>

    @Query("SELECT * FROM portfolio_trades WHERE ticker = :ticker ORDER BY timestampMillis DESC")
    fun getTradesForTicker(ticker: String): Flow<List<PortfolioTrade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: PortfolioTrade)

    @Delete
    suspend fun deleteTrade(trade: PortfolioTrade)
}
