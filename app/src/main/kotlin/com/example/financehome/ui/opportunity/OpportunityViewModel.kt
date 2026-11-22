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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

data class PhantomInvestmentItem(
    val transaction: Transaction,
    val ticker: String = "SPY",
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

@HiltViewModel
class OpportunityViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val marketDataRepo: MarketDataRepository
) : ViewModel() {

    // Configurable assumed rate of return (default 7.0%)
    val assumedAnnualReturn = MutableStateFlow(0.07)

    // Ticker symbol for tracking (defaults to S&P 500 ETF 'SPY')
    val trackerTicker = MutableStateFlow("SPY")

    val isRefreshingPrices = MutableStateFlow(false)

    // Real-world phantom portfolio items for all discretionary transactions
    val phantomItems = MutableStateFlow<List<PhantomInvestmentItem>>(emptyList())

    // All discretionary transactions from DB
    private val discretionaryTransactions: StateFlow<List<Transaction>> =
        transactionRepo.getDiscretionaryTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Total discretionary spending this month
    val thisMonthDiscretionarySpend = MutableStateFlow(0.0)

    // 10, 20, 30 year projections based on monthly discretionary spend
    val projections: StateFlow<List<ProjectionCardData>> =
        combine(thisMonthDiscretionarySpend, assumedAnnualReturn) { spend, rate ->
            listOf(10, 20, 30).map { yrs ->
                ProjectionCardData(
                    years = yrs,
                    lumpSumValue = InvestmentProjector.futureValue(spend, rate, yrs),
                    dcaMonthlyValue = InvestmentProjector.dcaFutureValue(spend, rate, yrs)
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalSpentOnDiscretionary: StateFlow<Double> =
        combine(phantomItems) { items ->
            items.first().sumOf { it.transaction.amount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val totalPhantomValue: StateFlow<Double> =
        combine(phantomItems) { items ->
            items.first().sumOf { it.phantomValue ?: it.transaction.amount }
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
        // Observe transactions and recompute prices
        viewModelScope.launch {
            discretionaryTransactions.collect { transactions ->
                computeMonthlySpend(transactions)
                refreshPhantomPortfolio(transactions)
            }
        }
    }

    fun setAssumedReturn(rate: Double) {
        assumedAnnualReturn.value = rate
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
        thisMonthDiscretionarySpend.value = thisMonthDebitTransactions.sumOf { it.amount }
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
        val ticker = trackerTicker.value

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
