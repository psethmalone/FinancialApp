package com.example.financehome.ui.opportunity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.data.model.PortfolioHolding
import com.example.financehome.data.model.PortfolioTrade
import com.example.financehome.data.model.TradeAction
import com.example.financehome.data.model.TradeSource
import com.example.financehome.data.repository.HoldingPerformance
import com.example.financehome.data.repository.PortfolioSummary
import com.example.financehome.ui.dashboard.formatTimestamp
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunityScreen(
    viewModel: OpportunityViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Real Portfolio", "Fake Portfolio ('What If')", "Projections")

    // Real Portfolio state
    val portfolioHoldings by viewModel.portfolioHoldings.collectAsState()
    val portfolioSummary by viewModel.portfolioSummary.collectAsState()
    val portfolioTrades by viewModel.portfolioTrades.collectAsState()
    val isRefreshingPortfolio by viewModel.isRefreshingPortfolio.collectAsState()
    var showAddTradeDialog by remember { mutableStateOf(false) }
    var showSetHoldingDialog by remember { mutableStateOf(false) }

    // Fake Portfolio / What If state
    val selectedTicker by viewModel.selectedTicker.collectAsState()
    val availableAssets by viewModel.availableAssets.collectAsState()
    val phantomItems by viewModel.phantomItems.collectAsState()
    val isRefreshingPrices by viewModel.isRefreshingPrices.collectAsState()
    val totalSpent by viewModel.totalSpentOnDiscretionary.collectAsState()
    val totalPhantomVal by viewModel.totalPhantomValue.collectAsState()
    val totalMissedGainLoss by viewModel.totalMissedGainLoss.collectAsState()
    val monthlyCohorts by viewModel.monthlyCohorts.collectAsState()

    // Projections state
    val assumedReturn by viewModel.assumedAnnualReturn.collectAsState()
    val simulatedMonthly by viewModel.simulatedMonthlyContribution.collectAsState()
    val actualMonthlySpend by viewModel.thisMonthDiscretionarySpend.collectAsState()
    val projections by viewModel.projections.collectAsState()

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    var showAddTickerDialog by remember { mutableStateOf(false) }
    var newTickerText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Investments & Portfolio", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTabIndex == 0) {
                        IconButton(
                            onClick = { viewModel.refreshPortfolioPrices() },
                            enabled = !isRefreshingPortfolio
                        ) {
                            if (isRefreshingPortfolio) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Portfolio Prices")
                            }
                        }
                    } else if (selectedTabIndex == 1) {
                        IconButton(
                            onClick = { viewModel.refreshPrices() },
                            enabled = !isRefreshingPrices
                        ) {
                            if (isRefreshingPrices) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Market Prices")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddTradeDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Log Trade")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    )
                }
            }

            // Asset selector bar for Fake Portfolio / What If tab
            if (selectedTabIndex == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tracked Asset / Fake Portfolio:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedTicker,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableAssets.forEach { asset ->
                            val isSelected = asset.ticker == selectedTicker
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectTicker(asset.ticker) },
                                label = { Text(asset.ticker, fontWeight = FontWeight.Bold) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }

                        AssistChip(
                            onClick = {
                                newTickerText = ""
                                showAddTickerDialog = true
                            },
                            label = { Text("+ Ticker") },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            when (selectedTabIndex) {
                0 -> RealPortfolioTab(
                    summary = portfolioSummary,
                    holdings = portfolioHoldings,
                    trades = portfolioTrades,
                    isRefreshing = isRefreshingPortfolio,
                    currencyFormat = currencyFormat,
                    onRefresh = { viewModel.refreshPortfolioPrices() },
                    onAddTrade = { showAddTradeDialog = true },
                    onSetHolding = { showSetHoldingDialog = true },
                    onDeleteHolding = { viewModel.deleteHolding(it) },
                    onDeleteTrade = { viewModel.deleteTrade(it) }
                )
                1 -> RealMarketTrackingTab(
                    ticker = selectedTicker,
                    items = phantomItems,
                    monthlyCohorts = monthlyCohorts,
                    totalSpent = totalSpent,
                    totalPhantomValue = totalPhantomVal,
                    totalGainLoss = totalMissedGainLoss,
                    currencyFormat = currencyFormat,
                    onRefresh = { viewModel.refreshPrices() }
                )
                2 -> LongTermProjectionsTab(
                    ticker = selectedTicker,
                    simulatedMonthly = simulatedMonthly,
                    actualMonthlySpend = actualMonthlySpend,
                    assumedReturn = assumedReturn,
                    projections = projections,
                    currencyFormat = currencyFormat,
                    onMonthlyChange = { viewModel.setSimulatedMonthlyContribution(it) },
                    onReturnChange = { viewModel.setAssumedReturn(it) },
                    onUseActualSpend = { viewModel.useActualSpendForProjection() }
                )
            }
        }
    }

    if (showAddTickerDialog) {
        AlertDialog(
            onDismissRequest = { showAddTickerDialog = false },
            title = { Text("Add Stock or Index Fund") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter ticker symbol (e.g. VOO, QQQ, AAPL, AMZN, GOOGL, AMD, SCHD):")
                    OutlinedTextField(
                        value = newTickerText,
                        onValueChange = { newTickerText = it.uppercase() },
                        label = { Text("Ticker Symbol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTickerText.isNotBlank()) {
                            viewModel.addCustomTicker(newTickerText.trim())
                        }
                        showAddTickerDialog = false
                    }
                ) {
                    Text("Add & Track")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddTradeDialog) {
        AddTradeDialog(
            currencyFormat = currencyFormat,
            onDismiss = { showAddTradeDialog = false },
            onConfirm = { ticker, action, shares, price, notes ->
                viewModel.addManualTrade(ticker, action, shares, price, notes)
                showAddTradeDialog = false
            }
        )
    }

    if (showSetHoldingDialog) {
        SetHoldingDialog(
            currencyFormat = currencyFormat,
            onDismiss = { showSetHoldingDialog = false },
            onConfirm = { ticker, shares, avgCost ->
                viewModel.setManualHolding(ticker, shares, avgCost)
                showSetHoldingDialog = false
            }
        )
    }
}

@Composable
fun RealPortfolioTab(
    summary: PortfolioSummary,
    holdings: List<HoldingPerformance>,
    trades: List<PortfolioTrade>,
    isRefreshing: Boolean,
    currencyFormat: NumberFormat,
    onRefresh: () -> Unit,
    onAddTrade: () -> Unit,
    onSetHolding: () -> Unit,
    onDeleteHolding: (PortfolioHolding) -> Unit,
    onDeleteTrade: (PortfolioTrade) -> Unit
) {
    val isPositive = summary.totalGainLoss >= 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Fidelity Integration Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fidelity Auto-Sync Active",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Stock purchases & sales received via Fidelity notifications are automatically parsed and recorded into your portfolio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Summary Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPositive)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Portfolio Value",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${summary.holdingsCount} ${if (summary.holdingsCount == 1) "Holding" else "Holdings"}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = currencyFormat.format(summary.totalMarketValue),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Cost Basis (Invested)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormat.format(summary.totalCostBasis),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Total Return / P&L",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val sign = if (isPositive) "+" else ""
                            Text(
                                text = "$sign${currencyFormat.format(summary.totalGainLoss)} (${String.format(Locale.US, "%+.2f", summary.totalGainLossPercent)}%)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) ColorIncome else ColorExpense
                                )
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddTrade,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Log Trade")
                        }

                        OutlinedButton(
                            onClick = onSetHolding,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Set Position")
                        }
                    }
                }
            }
        }

        // Holdings Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Holdings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (holdings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No stock positions tracked yet",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "When Fidelity sends an order execution notification (e.g. Bought 2 VOO @ \$485), it will be added here automatically.\n\nYou can also tap 'Log Trade' or 'Set Position' above to enter positions manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(holdings, key = { it.holding.ticker }) { item ->
                PortfolioHoldingCard(
                    perf = item,
                    currencyFormat = currencyFormat,
                    onDelete = { onDeleteHolding(item.holding) }
                )
            }
        }

        // Recent Trades Section
        if (trades.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Trades & Ingestion History",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(trades.take(20), key = { it.id }) { trade ->
                PortfolioTradeItem(
                    trade = trade,
                    currencyFormat = currencyFormat,
                    onDelete = { onDeleteTrade(trade) }
                )
            }
        }
    }
}

@Composable
fun PortfolioHoldingCard(
    perf: HoldingPerformance,
    currencyFormat: NumberFormat,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isGain = perf.totalGainLoss >= 0.0
    val sharesStr = if (perf.holding.totalShares % 1.0 == 0.0) {
        perf.holding.totalShares.toLong().toString()
    } else {
        String.format(Locale.US, "%.4f", perf.holding.totalShares).trimEnd('0').trimEnd('.')
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = perf.holding.ticker,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "$sharesStr shs",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = currencyFormat.format(perf.currentMarketValue),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        val sign = if (isGain) "+" else ""
                        Text(
                            text = "$sign${currencyFormat.format(perf.totalGainLoss)} (${String.format(Locale.US, "%+.2f", perf.totalGainLossPercent)}%)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGain) ColorIncome else ColorExpense
                            )
                        )
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Holding",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current Price",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormat.format(perf.currentPrice),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Avg Cost Basis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormat.format(perf.holding.averageBuyPrice),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Invested",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormat.format(perf.holding.totalCostBasis),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${perf.holding.ticker}?") },
            text = { Text("Are you sure you want to remove this position? Trade history will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PortfolioTradeItem(
    trade: PortfolioTrade,
    currencyFormat: NumberFormat,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isBuy = trade.action == TradeAction.BUY
    val sharesStr = if (trade.shares % 1.0 == 0.0) {
        trade.shares.toLong().toString()
    } else {
        String.format(Locale.US, "%.4f", trade.shares).trimEnd('0').trimEnd('.')
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isBuy) ColorIncome.copy(alpha = 0.15f) else ColorExpense.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = trade.action.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isBuy) ColorIncome else ColorExpense
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = trade.ticker,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$sharesStr @ ${currencyFormat.format(trade.pricePerShare)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatTimestamp(trade.timestampMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (trade.source == TradeSource.NOTIFICATION) {
                            Text(
                                text = "• Fidelity Sync",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = currencyFormat.format(trade.totalAmount),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete Trade",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Trade Record?") },
            text = { Text("Are you sure you want to delete this ${trade.action.name} trade for ${trade.ticker}?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddTradeDialog(
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: (ticker: String, action: TradeAction, shares: Double, price: Double, notes: String?) -> Unit
) {
    var ticker by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(TradeAction.BUY) }
    var sharesText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    val shares = sharesText.toDoubleOrNull()
    val price = priceText.toDoubleOrNull()
    val total = if (shares != null && price != null) shares * price else 0.0
    val isValid = ticker.isNotBlank() && shares != null && shares > 0.0 && price != null && price >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Investment Trade") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = action == TradeAction.BUY,
                        onClick = { action = TradeAction.BUY },
                        label = { Text("BUY") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorIncome.copy(alpha = 0.2f),
                            selectedLabelColor = ColorIncome
                        )
                    )
                    FilterChip(
                        selected = action == TradeAction.SELL,
                        onClick = { action = TradeAction.SELL },
                        label = { Text("SELL") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorExpense.copy(alpha = 0.2f),
                            selectedLabelColor = ColorExpense
                        )
                    )
                }

                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase().trim() },
                    label = { Text("Ticker (e.g. VOO, AAPL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sharesText,
                    onValueChange = { sharesText = it },
                    label = { Text("Number of Shares") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price Per Share ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (total > 0.0) {
                    Text(
                        text = "Total Order Amount: ${currencyFormat.format(total)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid && shares != null && price != null) {
                        onConfirm(ticker, action, shares, price, notesText.ifBlank { null })
                    }
                },
                enabled = isValid
            ) {
                Text("Save Trade")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetHoldingDialog(
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: (ticker: String, totalShares: Double, averageCost: Double) -> Unit
) {
    var ticker by remember { mutableStateOf("") }
    var sharesText by remember { mutableStateOf("") }
    var avgCostText by remember { mutableStateOf("") }

    val shares = sharesText.toDoubleOrNull()
    val avgCost = avgCostText.toDoubleOrNull()
    val totalInvested = if (shares != null && avgCost != null) shares * avgCost else 0.0
    val isValid = ticker.isNotBlank() && shares != null && shares > 0.0 && avgCost != null && avgCost >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Direct Position") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quickly define or overwrite your current holding balance and cost basis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase().trim() },
                    label = { Text("Ticker (e.g. VOO, AAPL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sharesText,
                    onValueChange = { sharesText = it },
                    label = { Text("Total Shares Owned") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = avgCostText,
                    onValueChange = { avgCostText = it },
                    label = { Text("Average Buy Price Per Share ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (totalInvested > 0.0) {
                    Text(
                        text = "Total Cost Basis: ${currencyFormat.format(totalInvested)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid && shares != null && avgCost != null) {
                        onConfirm(ticker, shares, avgCost)
                    }
                },
                enabled = isValid
            ) {
                Text("Set Position")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RealMarketTrackingTab(
    ticker: String,
    items: List<PhantomInvestmentItem>,
    monthlyCohorts: List<MonthlyDiscretionaryCohort>,
    totalSpent: Double,
    totalPhantomValue: Double,
    totalGainLoss: Double,
    currencyFormat: NumberFormat,
    onRefresh: () -> Unit
) {
    val isPositive = totalGainLoss >= 0.0
    var showMonthlyView by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPositive) ColorIncome.copy(alpha = 0.12f)
                    else ColorExpense.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fake Portfolio ($ticker)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPositive) ColorIncome else ColorExpense
                        ) {
                            Text(
                                text = (if (isPositive) "+" else "") +
                                        currencyFormat.format(totalGainLoss) +
                                        if (totalSpent > 0.0) {
                                            String.format(Locale.US, " (%.1f%%)", (totalGainLoss / totalSpent) * 100.0)
                                        } else "",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = currencyFormat.format(totalPhantomValue),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Discretionary Spent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormat.format(totalSpent),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Opportunity P&L",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = (if (isPositive) "+" else "") + currencyFormat.format(totalGainLoss),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) ColorIncome else ColorExpense
                                )
                            )
                        }
                    }

                    Text(
                        text = "Had you put your non-essential spending into $ticker on the day of each spend, this is what that money would be worth today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Section header with view toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discretionary Retrospective",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = showMonthlyView,
                        onClick = { showMonthlyView = true },
                        label = { Text("Monthly", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = !showMonthlyView,
                        onClick = { showMonthlyView = false },
                        label = { Text("All (${items.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        if (items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No discretionary spending logged yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "When you tag dining, coffee, or frivolous spending as a 'Want' in the categorization queue, it will appear here showing real-time growth in $ticker!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (showMonthlyView) {
            items(monthlyCohorts, key = { it.yearMonth.toString() }) { cohort ->
                MonthlyCohortCard(
                    cohort = cohort,
                    ticker = ticker,
                    currencyFormat = currencyFormat
                )
            }
        } else {
            items(items, key = { it.transaction.id }) { item ->
                PhantomTransactionRow(item = item, currencyFormat = currencyFormat)
            }
        }
    }
}

@Composable
fun MonthlyCohortCard(
    cohort: MonthlyDiscretionaryCohort,
    ticker: String,
    currencyFormat: NumberFormat
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isPositive = cohort.gainLoss >= 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Month Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = cohort.monthTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (cohort.isCompletedMonth) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = if (cohort.isCompletedMonth) "Completed" else "In Progress",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = if (cohort.isCompletedMonth) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPositive) ColorIncome.copy(alpha = 0.15f) else ColorExpense.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = (if (isPositive) "+" else "") +
                                currencyFormat.format(cohort.gainLoss) +
                                String.format(Locale.US, " (%.1f%%)", cohort.gainLossPct),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) ColorIncome else ColorExpense
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Spent vs Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Non-Essential Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormat.format(cohort.totalSpent),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Current Value in $ticker",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormat.format(cohort.totalPhantomValue),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Future projections banner for this month's cohort
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🚀", fontSize = 14.sp)
                        Text(
                            text = "Future value:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Text(
                        text = "10Y: ${currencyFormat.format(cohort.projected10Y)} • 20Y: ${currencyFormat.format(cohort.projected20Y)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Expand / Collapse line items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${cohort.items.size} purchase${if (cohort.items.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide purchases" else "View purchases",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    cohort.items.forEach { item ->
                        PhantomTransactionRow(item = item, currencyFormat = currencyFormat)
                    }
                }
            }
        }
    }
}

@Composable
fun PhantomTransactionRow(
    item: PhantomInvestmentItem,
    currencyFormat: NumberFormat
) {
    val transaction = item.transaction
    val gainLoss = item.gainLoss ?: 0.0
    val isGain = gainLoss >= 0.0
    val hasPrice = item.phantomValue != null
    val dateFormatted = formatTimestamp(transaction.timestampMillis)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateFormatted • Spent ${currencyFormat.format(transaction.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (hasPrice) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Worth " + currencyFormat.format(item.phantomValue),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = (if (isGain) "+" else "") +
                                    currencyFormat.format(gainLoss) +
                                    String.format(Locale.US, " (%.1f%%)", item.gainLossPct ?: 0.0),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGain) ColorIncome else ColorExpense
                            ),
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.End
                        )
                    }
                } else {
                    Text(
                        text = "Calculating...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (item.buyPrice != null && item.currentPrice != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.ticker} on spend: ${currencyFormat.format(item.buyPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${item.ticker} now: ${currencyFormat.format(item.currentPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LongTermProjectionsTab(
    ticker: String,
    simulatedMonthly: Double,
    actualMonthlySpend: Double,
    assumedReturn: Double,
    projections: List<ProjectionCardData>,
    currencyFormat: NumberFormat,
    onMonthlyChange: (Double) -> Unit,
    onReturnChange: (Double) -> Unit,
    onUseActualSpend: () -> Unit
) {
    val returnPercent = (assumedReturn * 100.0).roundToInt()
    val monthlyInt = simulatedMonthly.roundToInt()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Monthly Investment Simulator Control Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly Investment Amount",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = currencyFormat.format(simulatedMonthly) + "/mo",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = simulatedMonthly.toFloat(),
                        onValueChange = { onMonthlyChange(it.toDouble()) },
                        valueRange = 10f..1000f,
                        steps = 39,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(50.0, 100.0, 200.0, 500.0).forEach { amt ->
                            OutlinedButton(
                                onClick = { onMonthlyChange(amt) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("$${amt.toInt()}", fontSize = 12.sp)
                            }
                        }
                    }

                    if (actualMonthlySpend > 0.0) {
                        TextButton(
                            onClick = onUseActualSpend,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Use My Actual Spend (${currencyFormat.format(actualMonthlySpend)}/mo)")
                        }
                    }
                }
            }
        }

        // Return slider
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assumed Annual Return",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$returnPercent% per year",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = returnPercent.toFloat(),
                        onValueChange = { onReturnChange(it.toDouble() / 100.0) },
                        valueRange = 3f..15f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Historical S&P 500 average is ~10% nominal (7% adjusted for inflation).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Projections header
        item {
            Text(
                text = "Compound Growth Projections",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Projection cards for 5, 10, 20, 30 years
        items(projections) { proj ->
            val totalContributions = simulatedMonthly * 12 * proj.years
            val interestEarned = (proj.dcaMonthlyValue - totalContributions).coerceAtLeast(0.0)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${proj.years} YEARS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = currencyFormat.format(proj.dcaMonthlyValue),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = ColorIncome
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Your Contributions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormat.format(totalContributions),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Compound Interest Made",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+" + currencyFormat.format(interestEarned),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorIncome
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
