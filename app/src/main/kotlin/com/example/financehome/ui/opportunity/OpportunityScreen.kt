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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val tabTitles = listOf("Fake Portfolio", "Long-Term Projections")

    val selectedTicker by viewModel.selectedTicker.collectAsState()
    val availableAssets by viewModel.availableAssets.collectAsState()
    val phantomItems by viewModel.phantomItems.collectAsState()
    val isRefreshing by viewModel.isRefreshingPrices.collectAsState()
    val totalSpent by viewModel.totalSpentOnDiscretionary.collectAsState()
    val totalPhantomVal by viewModel.totalPhantomValue.collectAsState()
    val totalMissedGainLoss by viewModel.totalMissedGainLoss.collectAsState()

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
                title = { Text("Investment 'What If' Hub", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTabIndex == 0) {
                        IconButton(
                            onClick = { viewModel.refreshPrices() },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
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
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Asset selector bar: [SPY] [VOO] [QQQ] [NVDA] [+ Add Ticker]
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

                    // Add Custom Ticker button
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

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> RealMarketTrackingTab(
                    ticker = selectedTicker,
                    items = phantomItems,
                    totalSpent = totalSpent,
                    totalPhantomValue = totalPhantomVal,
                    totalGainLoss = totalMissedGainLoss,
                    currencyFormat = currencyFormat,
                    onRefresh = { viewModel.refreshPrices() }
                )
                1 -> LongTermProjectionsTab(
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
}

@Composable
fun RealMarketTrackingTab(
    ticker: String,
    items: List<PhantomInvestmentItem>,
    totalSpent: Double,
    totalPhantomValue: Double,
    totalGainLoss: Double,
    currencyFormat: NumberFormat,
    onRefresh: () -> Unit
) {
    val isPositive = totalGainLoss >= 0.0

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

        // Section header
        item {
            Text(
                text = "Discretionary Transactions (${items.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
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
        } else {
            items(items, key = { it.transaction.id }) { item ->
                PhantomTransactionRow(item = item, currencyFormat = currencyFormat)
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
                Column {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "$dateFormatted • Spent ${currencyFormat.format(transaction.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (hasPrice) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Worth " + currencyFormat.format(item.phantomValue),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = (if (isGain) "+" else "") +
                                    currencyFormat.format(gainLoss) +
                                    String.format(Locale.US, " (%.1f%%)", item.gainLossPct ?: 0.0),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGain) ColorIncome else ColorExpense
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Calculating...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
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
