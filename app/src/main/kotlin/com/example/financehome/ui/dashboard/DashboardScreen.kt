package com.example.financehome.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.data.local.CategoryTotal
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.domain.SafeToSpendAlertLevel
import com.example.financehome.domain.SafeToSpendStatus
import com.example.financehome.ui.analytics.AnalyticsScreen
import com.example.financehome.ui.settings.SpendingThresholdDialog
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import com.example.financehome.ui.theme.ColorWarning
import com.example.financehome.ui.transactions.PREDEFINED_CATEGORIES
import com.example.financehome.ui.transactions.TransactionDetailSheet
import com.example.financehome.ui.update.UpdateDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DashboardTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    ANALYTICS("Charts & Trends", Icons.Default.PieChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToSwipeCategorize: () -> Unit,
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToCashFlow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedDashboardTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var showSpendingThresholdDialog by remember { mutableStateOf(false) }

    val safeToSpendStatus by viewModel.safeToSpendStatus.collectAsState()
    val spendingAlertConfig by viewModel.spendingAlertConfig.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()

    val uncategorizedCount by viewModel.uncategorizedCount.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val monthlySpend by viewModel.monthlySpendTotal.collectAsState()
    val categoryTotals by viewModel.categoryTotals.collectAsState()
    val totalDebt by viewModel.totalDebt.collectAsState()
    val netPosition by viewModel.netPosition.collectAsState()
    val analyticsReport by viewModel.analyticsReport.collectAsState()

    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val missingReceipts by viewModel.transactionsMissingReceipts.collectAsState()
    val showMissingReceiptsBanner by viewModel.showMissingReceiptsBanner.collectAsState()

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))

    val context = LocalContext.current
    var dismissedNotificationWarning by remember { mutableStateOf(false) }
    val hasNotificationAccess = remember(dismissedNotificationWarning) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val hasPostNotifications = remember(dismissedNotificationWarning) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pinned top section: App Header, Update Banner, and Segmented Tabs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                // App Header
                Column {
                    Text(
                        text = "FinanceHome",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = todayFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // App Update Available Banner
                if (availableUpdate != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        onClick = { viewModel.openUpdateDialog() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Update Available (v${availableUpdate?.versionName})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Tap to view changelog and install →",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.dismissUpdateBanner() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Notification Permission Warning Banner
                if ((!hasNotificationAccess || !hasPostNotifications) && !dismissedNotificationWarning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        onClick = {
                            val intent = if (!hasNotificationAccess) {
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            } else {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = if (!hasNotificationAccess) "Bank Notification Access Disabled" else "App Push Alerts Blocked",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = if (!hasNotificationAccess)
                                            "Tap to enable in Settings to auto-track bank transactions"
                                        else
                                            "Tap to enable in Settings to receive spending alerts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            IconButton(onClick = { dismissedNotificationWarning = true }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Tab Switcher: Overview vs Charts & Trends
                TabRow(
                    selectedTabIndex = selectedDashboardTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    DashboardTab.values().forEach { tab ->
                        Tab(
                            selected = selectedDashboardTab == tab,
                            onClick = { selectedDashboardTab = tab },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = tab.label,
                                        fontWeight = if (selectedDashboardTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tab Content
            when (selectedDashboardTab) {
                DashboardTab.OVERVIEW -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Real-Time Safe-to-Spend Live Burn & Pace Card
                        item {
                            SafeToSpendGaugeCard(
                                status = safeToSpendStatus,
                                currencyFormat = currencyFormat,
                                onNavigateToCashFlow = onNavigateToCashFlow,
                                onConfigureThreshold = { showSpendingThresholdDialog = true }
                            )
                        }

                        // Swipe to Categorize Card (shown if there are uncategorized transactions)
                        if (uncategorizedCount > 0) {
                            item {
                                Card(
                                    onClick = onNavigateToSwipeCategorize,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.tertiary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onTertiary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "$uncategorizedCount to categorize",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                                Text(
                                                    text = "Swipe into Needs vs. Wants in seconds →",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Missing Receipts Card (prompt to attach receipts to recent spends)
                        if (showMissingReceiptsBanner && missingReceipts.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
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
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("🧾", fontSize = 20.sp)
                                                Column {
                                                    Text(
                                                        text = "Attach Receipts (${missingReceipts.size} recent)",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    Text(
                                                        text = "Auto-track grocery & fuel costs for recent card spends",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(
                                                    onClick = { viewModel.dismissMissingReceiptsBanner(markAllAsNoReceipt = true) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Dismiss All", style = MaterialTheme.typography.labelSmall)
                                                }
                                                IconButton(onClick = { viewModel.dismissMissingReceiptsBanner(markAllAsNoReceipt = true) }) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Dismiss",
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(vertical = 4.dp)
                                        ) {
                                            items(missingReceipts, key = { it.id }) { tx ->
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.clickable { selectedTransactionForDetail = tx }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Column(modifier = Modifier.widthIn(max = 140.dp)) {
                                                            Text(
                                                                text = tx.merchant,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "${currencyFormat.format(tx.amount)} • ${formatTimestamp(tx.timestampMillis)}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            FilledTonalButton(
                                                                onClick = { selectedTransactionForDetail = tx },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.height(32.dp)
                                                            ) {
                                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Scan", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                            OutlinedButton(
                                                                onClick = { viewModel.markNoReceiptNeeded(tx.id) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.height(32.dp)
                                                            ) {
                                                                Text("No Receipt", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Net Position Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = "Net Cash & Debt Position",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = currencyFormat.format(netPosition),
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (netPosition >= 0) ColorIncome else ColorExpense
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (netPosition >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            tint = if (netPosition >= 0) ColorIncome else ColorExpense,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (netPosition >= 0) "Cash deposits exceed tracked debt" else "Outstanding debts exceed cash",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Stats Row: Spending vs Debt
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Monthly Spending Card (tapping switches to Charts & Trends)
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    onClick = { selectedDashboardTab = DashboardTab.ANALYTICS }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "This Month",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                Icons.Default.PieChart,
                                                contentDescription = "View charts",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currencyFormat.format(monthlySpend),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            text = "Tap for charts →",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Total Debt Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    onClick = onNavigateToDebts
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Total Debt",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                Icons.Default.CreditCard,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currencyFormat.format(totalDebt),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (totalDebt > 0) ColorExpense else ColorIncome
                                            )
                                        )
                                        Text(
                                            text = "Active balances",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Top Spending Categories
                        if (categoryTotals.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Top Spending Categories",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    TextButton(onClick = { selectedDashboardTab = DashboardTab.ANALYTICS }) {
                                        Text("See Charts")
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(categoryTotals.take(5)) { cat ->
                                        CategoryChip(
                                            categoryTotal = cat,
                                            currencyFormat = currencyFormat,
                                            isSelected = selectedCategory == cat.category,
                                            onClick = { viewModel.onCategorySelect(cat.category) }
                                        )
                                    }
                                }
                            }
                        }

                        // Full Searchable Transactions & Activity Section
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Transactions & Activity",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (filteredTransactions.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "${filteredTransactions.size}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (selectedCategory != null || searchQuery.isNotEmpty()) {
                                    TextButton(onClick = {
                                        viewModel.onSearchQueryChange("")
                                        viewModel.onCategorySelect(null)
                                    }) {
                                        Text("Clear Filters")
                                    }
                                }
                            }
                        }

                        // Search Input
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                placeholder = { Text("Search by merchant, category, notes...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Category Filter Chips
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategory == null,
                                        onClick = { viewModel.onCategorySelect(null) },
                                        label = { Text("All") }
                                    )
                                }
                                items(PREDEFINED_CATEGORIES) { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = { viewModel.onCategorySelect(cat) },
                                        label = { Text(cat) }
                                    )
                                }
                            }
                        }

                        // Filtered Transactions List
                        if (filteredTransactions.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (searchQuery.isNotBlank() || selectedCategory != null)
                                                "No matching transactions found"
                                            else "No transactions detected yet",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (searchQuery.isNotBlank() || selectedCategory != null)
                                                "Try adjusting your search terms or category filter."
                                            else "Transactions will appear here automatically when notifications arrive or when you add them manually.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredTransactions, key = { it.id }) { transaction ->
                                TransactionItemRow(
                                    transaction = transaction,
                                    currencyFormat = currencyFormat,
                                    modifier = Modifier.clickable { selectedTransactionForDetail = transaction }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
                DashboardTab.ANALYTICS -> {
                    AnalyticsScreen(
                        report = analyticsReport,
                        onSelectPeriod = { viewModel.setAnalyticsPeriod(it) },
                        currencyFormat = currencyFormat,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (selectedTransactionForDetail != null) {
        val currentTx = selectedTransactionForDetail!!
        val receiptItems by viewModel.getReceiptItems(currentTx.id).collectAsState(initial = emptyList<ReceiptItem>())
        val isScanning by viewModel.isScanningReceipt.collectAsState()
        val bills by viewModel.bills.collectAsState()
        val debts by viewModel.debts.collectAsState()

        TransactionDetailSheet(
            transaction = currentTx,
            receiptItems = receiptItems,
            isScanning = isScanning,
            currencyFormat = currencyFormat,
            bills = bills,
            debts = debts,
            onDismiss = { selectedTransactionForDetail = null },
            onUpdateCategory = { newCategory ->
                viewModel.updateTransactionCategory(currentTx, newCategory)
                selectedTransactionForDetail = null
            },
            onToggleDiscretionary = { isDisc ->
                viewModel.toggleDiscretionary(currentTx, isDisc)
                selectedTransactionForDetail = selectedTransactionForDetail?.copy(isDiscretionary = isDisc)
            },
            onDelete = {
                viewModel.deleteTransaction(currentTx)
                selectedTransactionForDetail = null
            },
            onCreateCaptureUri = { viewModel.createReceiptCaptureFile() },
            onSavePickedUri = { uri -> viewModel.savePickedImageToInternalStorage(uri) },
            onAttachReceipt = { uriStr -> viewModel.attachReceipt(currentTx.id, uriStr) },
            onAddItem = { item -> viewModel.addReceiptItem(item) },
            onUpdateItem = { item -> viewModel.updateReceiptItem(item) },
            onDeleteItem = { item -> viewModel.deleteReceiptItem(item) },
            onRemoveReceipt = { viewModel.removeReceipt(currentTx.id) },
            onToggleNoReceipt = {
                val newNoReceipt = !currentTx.noReceiptNeeded
                viewModel.markNoReceiptNeeded(currentTx.id, newNoReceipt)
                selectedTransactionForDetail = currentTx.copy(noReceiptNeeded = newNoReceipt)
            },
            onLinkToBill = { billId ->
                viewModel.linkTransactionToBill(currentTx.id, billId)
                selectedTransactionForDetail = currentTx.copy(
                    linkedBillId = billId,
                    linkedDebtId = null,
                    isDiscretionary = false,
                    noReceiptNeeded = true
                )
            },
            onLinkToDebt = { debtId, amount ->
                viewModel.linkTransactionToDebt(currentTx.id, debtId, amount)
                selectedTransactionForDetail = currentTx.copy(
                    linkedDebtId = debtId,
                    linkedBillId = null,
                    isDiscretionary = false,
                    noReceiptNeeded = true
                )
            },
            onUnlinkObligation = {
                viewModel.unlinkTransactionObligation(currentTx.id)
                selectedTransactionForDetail = currentTx.copy(
                    linkedBillId = null,
                    linkedDebtId = null
                )
            }
        )
    }

    if (showUpdateDialog && availableUpdate != null) {
        val info = availableUpdate!!
        UpdateDialog(
            updateInfo = info,
            downloadState = downloadState,
            onStartDownload = { viewModel.startDownload(info.downloadUrl) },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    if (showSpendingThresholdDialog) {
        SpendingThresholdDialog(
            currentConfig = spendingAlertConfig,
            onDismiss = { showSpendingThresholdDialog = false },
            onSave = { updated ->
                viewModel.updateSpendingAlertConfig(updated)
                showSpendingThresholdDialog = false
            },
            onSendTestAlert = {
                viewModel.sendTestSpendingAlert()
            }
        )
    }
}

@Composable
fun SafeToSpendGaugeCard(
    status: SafeToSpendStatus,
    currencyFormat: NumberFormat,
    onNavigateToCashFlow: () -> Unit,
    onConfigureThreshold: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusLabel: String
    val badgeColor: Color
    val badgeTextColor: Color
    val badgeIcon: ImageVector
    when (status.alertLevel) {
        SafeToSpendAlertLevel.DEFICIT_STOP -> {
            statusLabel = "DEFICIT: STOP SPENDING"
            badgeColor = MaterialTheme.colorScheme.errorContainer
            badgeTextColor = MaterialTheme.colorScheme.onErrorContainer
            badgeIcon = Icons.Default.Warning
        }
        SafeToSpendAlertLevel.LOW_WARNING -> {
            statusLabel = "LOW CUSHION WARNING"
            badgeColor = MaterialTheme.colorScheme.tertiaryContainer
            badgeTextColor = MaterialTheme.colorScheme.onTertiaryContainer
            badgeIcon = Icons.Default.Info
        }
        SafeToSpendAlertLevel.HEALTHY -> {
            statusLabel = "SAFE-TO-SPEND HEALTHY"
            badgeColor = MaterialTheme.colorScheme.secondaryContainer
            badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer
            badgeIcon = Icons.Default.CheckCircle
        }
    }

    val progress = if (status.initialSafeToSpend > 0) {
        (status.spentThisCycle / status.initialSafeToSpend).coerceIn(0.0, 1.0).toFloat()
    } else {
        if (status.spentThisCycle > 0) 1.0f else 0.0f
    }

    val progressColor = when (status.alertLevel) {
        SafeToSpendAlertLevel.DEFICIT_STOP -> ColorExpense
        SafeToSpendAlertLevel.LOW_WARNING -> ColorWarning
        SafeToSpendAlertLevel.HEALTHY -> ColorIncome
    }

    Card(
        onClick = onNavigateToCashFlow,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            badgeIcon,
                            contentDescription = null,
                            tint = badgeTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeTextColor
                        )
                    }
                }

                IconButton(
                    onClick = onConfigureThreshold,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Alert Thresholds",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Safe-to-Spend Left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = currencyFormat.format(status.remainingSafeToSpend),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = when (status.alertLevel) {
                            SafeToSpendAlertLevel.DEFICIT_STOP -> ColorExpense
                            SafeToSpendAlertLevel.LOW_WARNING -> ColorWarning
                            SafeToSpendAlertLevel.HEALTHY -> MaterialTheme.colorScheme.primary
                        }
                    )
                )

                if (status.daysRemainingInCycle > 0 && status.remainingSafeToSpend > 0) {
                    Text(
                        text = "${currencyFormat.format(status.dailyPace)}/day",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spent: ${currencyFormat.format(status.spentThisCycle)} of ${currencyFormat.format(status.initialSafeToSpend)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${status.daysRemainingInCycle}d to payday (${status.nextPayDate.format(DateTimeFormatter.ofPattern("MMM d"))})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    categoryTotal: CategoryTotal,
    currencyFormat: NumberFormat,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = categoryTotal.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = currencyFormat.format(categoryTotal.total),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun TransactionItemRow(
    transaction: Transaction,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val isDebit = transaction.type == TransactionType.DEBIT
    val amountFormatted = (if (isDebit) "-" else "+") + currencyFormat.format(transaction.amount)
    val dateFormatted = formatTimestamp(transaction.timestampMillis)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDebit) ColorExpense.copy(alpha = 0.12f)
                            else ColorIncome.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDebit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isDebit) ColorExpense else ColorIncome,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (transaction.receiptImageUri != null || transaction.hasItemizedReceipt) {
                            Text(
                                text = "• 🧾 Receipt",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else if (transaction.noReceiptNeeded) {
                            Text(
                                text = "• No Receipt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = amountFormatted,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isDebit) ColorExpense else ColorIncome
                ),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

fun formatTimestamp(millis: Long): String {
    val instant = Instant.ofEpochMilli(millis)
    val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = LocalDate.now()

    return if (dateTime.toLocalDate() == today) {
        dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    } else {
        dateTime.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
