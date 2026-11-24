package com.example.financehome.ui.opportunity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.model.PortfolioHolding
import com.example.financehome.data.model.PortfolioTrade
import com.example.financehome.data.model.TradeAction
import com.example.financehome.data.model.TradeSource
import com.example.financehome.data.repository.HoldingPerformance
import com.example.financehome.data.repository.MarketDataRepository
import com.example.financehome.data.repository.PortfolioRepository
import com.example.financehome.data.repository.PortfolioSummary
import com.example.financehome.data.repository.TransactionRepository
import com.example.financehome.domain.InvestmentProjector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

data class PhantomInvestmentItem(
    val transaction: Transaction,
    val ticker: String,
    val buyPrice: Double?,
    val currentPrice: Double?,
    val phantomValue: Double?,
    val gainLoss: Double?,
    val gainLossPct: Double?
)

data class ProjectionCardData(
    val years: Int,
    val lumpSumValue: Double,
    val dcaMonthlyValue: Double
)

data class AssetOption(
    val ticker: String,
    val name: String,
    val assetType: String // "Index Fund", "Tech Stock", etc.
)

@HiltViewModel
class OpportunityViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val marketDataRepo: MarketDataRepository,
    private val portfolioRepo: PortfolioRepository
) : ViewModel() {

    // ── Real Portfolio ─────────────────────────────────────────────────────────

    val rawHoldings: StateFlow<List<PortfolioHolding>> =
        portfolioRepo.holdings
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val portfolioTrades: StateFlow<List<PortfolioTrade>> =
        portfolioRepo.trades
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val isRefreshingPortfolio = MutableStateFlow(false)

    // Map of live prices: Ticker -> Price
    private val livePrices = MutableStateFlow<Map<String, Double>>(emptyMap())

    val portfolioHoldings: StateFlow<List<HoldingPerformance>> =
        combine(rawHoldings, livePrices) { holdingsList, priceMap ->
            holdingsList.filter { it.totalShares > 0.0 }.map { h ->
                val price = priceMap[h.ticker] ?: h.averageBuyPrice
                val marketVal = h.totalShares * price
                val gainLoss = marketVal - h.totalCostBasis
                val gainLossPct = if (h.totalCostBasis > 0.0) (gainLoss / h.totalCostBasis) * 100.0 else 0.0
                HoldingPerformance(
                    holding = h,
                    currentPrice = price,
                    currentMarketValue = marketVal,
                    totalGainLoss = gainLoss,
                    totalGainLossPercent = gainLossPct
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val portfolioSummary: StateFlow<PortfolioSummary> =
        portfolioHoldings.map { list ->
            val totalMarket = list.sumOf { it.currentMarketValue }
            val totalCost = list.sumOf { it.holding.totalCostBasis }
            val totalGain = totalMarket - totalCost
            val totalPct = if (totalCost > 0.0) (totalGain / totalCost) * 100.0 else 0.0
            PortfolioSummary(
                totalMarketValue = totalMarket,
                totalCostBasis = totalCost,
                totalGainLoss = totalGain,
                totalGainLossPercent = totalPct,
                holdingsCount = list.size
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PortfolioSummary(0.0, 0.0, 0.0, 0.0, 0)
        )

    // Preset list of popular index funds and stocks
    val defaultAssets = listOf(
        AssetOption("SPY", "SPDR S&P 500 ETF", "Index Fund"),
        AssetOption("VOO", "Vanguard S&P 500", "Index Fund"),
        AssetOption("QQQ", "Invesco Nasdaq 100", "Tech ETF"),
        AssetOption("VTI", "Vanguard Total Stock Market", "Index Fund"),
        AssetOption("SCHD", "Schwab US Dividend Equity", "Dividend ETF"),
        AssetOption("AAPL", "Apple Inc.", "Stock"),
        AssetOption("NVDA", "NVIDIA Corporation", "Stock"),
        AssetOption("MSFT", "Microsoft Corporation", "Stock"),
        AssetOption("TSLA", "Tesla Inc.", "Stock")
    )

    val availableAssets = MutableStateFlow(defaultAssets)

    // Currently selected ticker for the fake portfolio
    val selectedTicker = MutableStateFlow("SPY")

    // Configurable assumed rate of return (default 7.0%)
    val assumedAnnualReturn = MutableStateFlow(0.07)

    // Interactive monthly contribution for projection tab (defaults to $100 or actual spend)
    val simulatedMonthlyContribution = MutableStateFlow(100.0)

    val isRefreshingPrices = MutableStateFlow(false)

    // Real-world phantom portfolio items for all discretionary transactions
    val phantomItems = MutableStateFlow<List<PhantomInvestmentItem>>(emptyList())

    // All discretionary transactions from DB
    val discretionaryTransactions: StateFlow<List<Transaction>> =
        transactionRepo.getDiscretionaryTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Actual discretionary spending this month
    val thisMonthDiscretionarySpend = MutableStateFlow(0.0)

    // Projections based on simulatedMonthlyContribution & assumedAnnualReturn
    val projections: StateFlow<List<ProjectionCardData>> =
        combine(simulatedMonthlyContribution, assumedAnnualReturn) { monthly, rate ->
            listOf(5, 10, 20, 30).map { yrs ->
                ProjectionCardData(
                    years = yrs,
                    lumpSumValue = InvestmentProjector.futureValue(monthly * 12, rate, yrs),
                    dcaMonthlyValue = InvestmentProjector.dcaFutureValue(monthly, rate, yrs)
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalSpentOnDiscretionary: StateFlow<Double> =
        phantomItems.map { items ->
            items.sumOf { it.transaction.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val totalPhantomValue: StateFlow<Double> =
        phantomItems.map { items ->
            items.sumOf { it.phantomValue ?: it.transaction.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val totalMissedGainLoss: StateFlow<Double> =
        combine(totalSpentOnDiscretionary, totalPhantomValue) { spent, current ->
            current - spent
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    init {
        viewModelScope.launch {
            discretionaryTransactions.collect { transactions ->
                computeMonthlySpend(transactions)
                refreshPhantomPortfolio(transactions)
            }
        }
    }

    fun selectTicker(ticker: String) {
        val clean = ticker.uppercase().trim()
        selectedTicker.value = clean
        viewModelScope.launch {
            refreshPhantomPortfolio(discretionaryTransactions.value)
        }
    }

    fun addCustomTicker(ticker: String, name: String = ticker) {
        val clean = ticker.uppercase().trim()
        if (clean.isNotBlank()) {
            val exists = availableAssets.value.any { it.ticker.equals(clean, ignoreCase = true) }
            if (!exists) {
                availableAssets.value = availableAssets.value + AssetOption(clean, name, "Custom")
            }
            selectTicker(clean)
        }
    }

    fun setAssumedReturn(rate: Double) {
        assumedAnnualReturn.value = rate
    }

    fun setSimulatedMonthlyContribution(amount: Double) {
        simulatedMonthlyContribution.value = amount.coerceAtLeast(10.0)
    }

    fun useActualSpendForProjection() {
        if (thisMonthDiscretionarySpend.value > 0.0) {
            simulatedMonthlyContribution.value = thisMonthDiscretionarySpend.value
        }
    }

    private fun computeMonthlySpend(transactions: List<Transaction>) {
        val now = LocalDate.now()
        val thisMonthDebitTransactions = transactions.filter {
            it.type == TransactionType.DEBIT &&
                    run {
                        val dt = Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        dt.year == now.year && dt.monthValue == now.monthValue
                    }
        }
        val actualMonthly = thisMonthDebitTransactions.sumOf { it.amount }
        thisMonthDiscretionarySpend.value = actualMonthly
        if (actualMonthly > 0.0 && simulatedMonthlyContribution.value == 100.0) {
            simulatedMonthlyContribution.value = actualMonthly
        }
    }

    fun refreshPrices() {
        viewModelScope.launch {
            refreshPhantomPortfolio(discretionaryTransactions.value)
        }
    }

    private suspend fun refreshPhantomPortfolio(transactions: List<Transaction>) {
        val debitItems = transactions.filter { it.type == TransactionType.DEBIT }
        if (debitItems.isEmpty()) {
            phantomItems.value = emptyList()
            return
        }

        isRefreshingPrices.value = true
        val ticker = selectedTicker.value

        withContext(Dispatchers.IO) {
            val currentPrice = marketDataRepo.getCurrentPrice(ticker)

            val items = debitItems.map { t ->
                val date = Instant.ofEpochMilli(t.timestampMillis).atZone(ZoneOffset.UTC).toLocalDate()
                val epochDay = date.toEpochDay()
                val buyPrice = marketDataRepo.getClosePrice(ticker, epochDay)

                val phantomVal = if (buyPrice != null && currentPrice != null && buyPrice > 0.0) {
                    (t.amount / buyPrice) * currentPrice
                } else null

                val gainLoss = if (phantomVal != null) phantomVal - t.amount else null
                val gainLossPct = if (buyPrice != null && currentPrice != null && buyPrice > 0.0) {
                    ((currentPrice - buyPrice) / buyPrice) * 100.0
                } else null

                PhantomInvestmentItem(
                    transaction = t,
                    ticker = ticker,
                    buyPrice = buyPrice,
                    currentPrice = currentPrice,
                    phantomValue = phantomVal,
                    gainLoss = gainLoss,
                    gainLossPct = gainLossPct
                )
            }

            withContext(Dispatchers.Main) {
                phantomItems.value = items
                isRefreshingPrices.value = false
            }
        }
    }

    // ── Real Portfolio Actions ─────────────────────────────────────────────────

    fun refreshPortfolioPrices(holdingsToRefresh: List<PortfolioHolding> = rawHoldings.value) {
        if (holdingsToRefresh.isEmpty()) return
        viewModelScope.launch {
            isRefreshingPortfolio.value = true
            withContext(Dispatchers.IO) {
                val map = mutableMapOf<String, Double>()
                for (h in holdingsToRefresh) {
                    val price = marketDataRepo.getCurrentPrice(h.ticker) ?: h.averageBuyPrice
                    map[h.ticker] = price
                }
                livePrices.value = map
                isRefreshingPortfolio.value = false
            }
        }
    }

    fun addManualTrade(
        ticker: String,
        action: TradeAction,
        shares: Double,
        pricePerShare: Double,
        notes: String? = null
    ) {
        viewModelScope.launch {
            portfolioRepo.recordTrade(
                ticker = ticker,
                action = action,
                shares = shares,
                pricePerShare = pricePerShare,
                source = TradeSource.MANUAL,
                notes = notes
            )
            refreshPortfolioPrices()
        }
    }

    fun setManualHolding(ticker: String, shares: Double, averageCost: Double) {
        viewModelScope.launch {
            portfolioRepo.setHolding(ticker, shares, averageCost)
            refreshPortfolioPrices()
        }
    }

    fun deleteTrade(trade: PortfolioTrade) {
        viewModelScope.launch {
            portfolioRepo.deleteTrade(trade)
        }
    }

    fun deleteHolding(holding: PortfolioHolding) {
        viewModelScope.launch {
            portfolioRepo.deleteHolding(holding)
        }
    }
}
