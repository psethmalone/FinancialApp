package com.example.financehome.ui.opportunity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.MarketDataRepository
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
    private val marketDataRepo: MarketDataRepository
) : ViewModel() {

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
}
