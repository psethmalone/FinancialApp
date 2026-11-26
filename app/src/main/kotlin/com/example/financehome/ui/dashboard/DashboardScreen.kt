package com.example.financehome.ui.dashboard

import androidx.compose.animation.*
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
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.domain.MultiMonthOutlookResult
import com.example.financehome.domain.OutlookStatusLevel
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
    onNavigateToCashFlow: (openTransactionsTab: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedDashboardTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }
    var showSpendingThresholdDialog by remember { mutableStateOf(false) }

    val safeToSpendStatus by viewModel.safeToSpendStatus.collectAsState()
    val monthlyOutlook by viewModel.monthlyOutlook.collectAsState()
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
                        // Unified Top Hero Card with Upgraded Monthly Financial Outlook UI
                        item {
                            UnifiedMonthlyHeroCard(
                                outlook = monthlyOutlook,
                                currencyFormat = currencyFormat,
                                onPreviousMonth = {
                                    if (monthlyOutlook.monthOffset > 0) {
                                        viewModel.selectMonthOffset(monthlyOutlook.monthOffset - 1)
                                    }
                                },
                                onNextMonth = {
                                    viewModel.selectMonthOffset(monthlyOutlook.monthOffset + 1)
                                },
                                onToggleBillPaid = { bill, date -> viewModel.toggleBillPaid(bill, date) },
                                onToggleDebtPaid = { debt, date -> viewModel.toggleDebtPaid(debt, date) },
                                onNavigateToCashFlow = { onNavigateToCashFlow(false) }
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
                                                                softWrap = false,
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

                        // Recent Activity Preview (Executive Financial Outlook)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                        Text(
                                            text = "Recent Activity",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        TextButton(onClick = { onNavigateToCashFlow(true) }) {
                                            Text("View All in Cash Flow →")
                                        }
                                    }

                                    if (recentTransactions.isEmpty()) {
                                        Text(
                                            text = "No recent transactions yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    } else {
                                        recentTransactions.take(3).forEach { transaction ->
                                            TransactionItemRow(
                                                transaction = transaction,
                                                currencyFormat = currencyFormat,
                                                modifier = Modifier.clickable { selectedTransactionForDetail = transaction }
                                            )
                                        }
                                    }
                                }
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
fun UnifiedMonthlyHeroCard(
    outlook: DashboardMonthlyOutlook,
    currencyFormat: NumberFormat,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onToggleBillPaid: (Bill, LocalDate) -> Unit,
    onToggleDebtPaid: (Debt, LocalDate) -> Unit = { _, _ -> },
    onNavigateToCashFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusBadgeColor: Color
    val statusTextColor: Color
    val statusIcon: ImageVector
    when (outlook.statusLevel) {
        OutlookStatusLevel.ON_TRACK -> {
            statusBadgeColor = ColorIncome.copy(alpha = 0.15f)
            statusTextColor = ColorIncome
            statusIcon = Icons.Default.CheckCircle
        }
        OutlookStatusLevel.TIGHT -> {
            statusBadgeColor = ColorWarning.copy(alpha = 0.18f)
            statusTextColor = ColorWarning
            statusIcon = Icons.Default.Info
        }
        OutlookStatusLevel.SHORTFALL -> {
            statusBadgeColor = ColorExpense.copy(alpha = 0.18f)
            statusTextColor = ColorExpense
            statusIcon = Icons.Default.Warning
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // ── Tier A: Top Header with Month Switcher ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Month Switcher: < MMMM yyyy >
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onPreviousMonth,
                        enabled = outlook.monthOffset > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Month",
                            tint = if (outlook.monthOffset > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val monthFormatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
                    Text(
                        text = outlook.selectedYearMonth.format(monthFormatter),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onNextMonth,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Month",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    color = statusBadgeColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = outlook.statusLevel.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (outlook.monthOffset == 0) "Projected End-of-Month Buffer" else "Projected End Buffer (${outlook.selectedYearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Primary Projected Balance with Tabular Figures
            Text(
                text = currencyFormat.format(outlook.projectedEndOfMonthBalance),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                    color = if (outlook.projectedEndOfMonthBalance >= 0) MaterialTheme.colorScheme.onSurface else ColorExpense
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Current Available / Rollover Buffer Sub-label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onNavigateToCashFlow() }
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                val bufferLabel = if (outlook.monthOffset == 0) "Current Available Buffer" else "Rollover from Prior Month"
                Text(
                    text = "$bufferLabel: ${currencyFormat.format(outlook.currentAvailableBalance)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tier B: Middle Visual (Runway Bar + 3-Column Summary) ───────────
            val bufferAmount = outlook.currentAvailableBalance.coerceAtLeast(0.0)
            val billsAmount = outlook.remainingBills.coerceAtLeast(0.0)
            val totalRunway = bufferAmount + billsAmount

            val bufferWeight = if (totalRunway > 0.0) (bufferAmount / totalRunway).toFloat().coerceIn(0.05f, 0.95f) else 0.5f
            val billsWeight = 1f - bufferWeight

            // Segmented Horizontal Runway Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(bufferWeight)
                            .fillMaxHeight()
                            .background(ColorIncome)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .weight(billsWeight)
                            .fillMaxHeight()
                            .background(
                                if (bufferAmount >= billsAmount) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                else ColorExpense
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ColorIncome)
                        )
                        Text(
                            text = "Buffer: ${currencyFormat.format(outlook.currentAvailableBalance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (bufferAmount >= billsAmount) MaterialTheme.colorScheme.outline
                                    else ColorExpense
                                )
                        )
                        Text(
                            text = "Unpaid Bills: ${currencyFormat.format(outlook.remainingBills)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Column Summary Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Col 1: Expected Inflows
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Inflows",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+${currencyFormat.format(outlook.expectedInflows)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = ColorIncome
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Col 2: Remaining Bills
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Remaining Bills",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "-${currencyFormat.format(outlook.remainingBills)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (outlook.remainingBills > 0) ColorExpense else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Col 3: Projected Net
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Projected Net",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val isNetPositive = outlook.projectedEndOfMonthBalance >= 0
                        Text(
                            text = "${if (isNetPositive) "+" else ""}${currencyFormat.format(outlook.projectedEndOfMonthBalance)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (isNetPositive) ColorIncome else ColorExpense
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Tier C: Bottom Expandable Strip ────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Spacer(modifier = Modifier.height(10.dp))

            val nextEvent = outlook.immediateNextEvent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (nextEvent != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        nextEvent.isIncome -> ColorIncome.copy(alpha = 0.15f)
                                        nextEvent.originalDebt != null -> ColorExpense.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    nextEvent.isIncome -> Icons.Default.ArrowUpward
                                    nextEvent.originalDebt != null -> Icons.Default.CreditCard
                                    else -> Icons.Default.Receipt
                                },
                                contentDescription = null,
                                tint = when {
                                    nextEvent.isIncome -> ColorIncome
                                    nextEvent.originalDebt != null -> ColorExpense
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = nextEvent.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (nextEvent.isPaid) {
                                    Surface(
                                        color = ColorIncome.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "PAID",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ColorIncome,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Due ${nextEvent.date.format(DateTimeFormatter.ofPattern("MMM d"))} • ${currencyFormat.format(nextEvent.amount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (nextEvent.originalBill != null && !nextEvent.isPaid) {
                            FilledTonalButton(
                                onClick = { onToggleBillPaid(nextEvent.originalBill, nextEvent.date) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Mark Paid", style = MaterialTheme.typography.labelSmall)
                            }
                        } else if (nextEvent.originalDebt != null && !nextEvent.isPaid) {
                            FilledTonalButton(
                                onClick = { onToggleDebtPaid(nextEvent.originalDebt, nextEvent.date) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Mark Paid", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎉", fontSize = 16.sp)
                        Text(
                            text = "No more scheduled events for this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Animated Expandable Drawer
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Remaining Schedule (${outlook.chronologicalEvents.size} events)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    outlook.chronologicalEvents.forEach { event ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            event.isIncome -> Icons.Default.ArrowUpward
                                            event.originalDebt != null -> Icons.Default.CreditCard
                                            else -> Icons.Default.Receipt
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            event.isIncome -> ColorIncome
                                            event.originalDebt != null -> ColorExpense
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${event.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} • ${currencyFormat.format(event.amount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (event.originalBill != null) {
                                    if (event.isPaid) {
                                        Surface(
                                            color = ColorIncome.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "PAID",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = ColorIncome,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onToggleBillPaid(event.originalBill, event.date) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Mark Paid", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else if (event.originalDebt != null) {
                                    if (event.isPaid) {
                                        Surface(
                                            color = ColorIncome.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "PAID",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = ColorIncome,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onToggleDebtPaid(event.originalDebt, event.date) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Mark Paid", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else if (event.isIncome) {
                                    Surface(
                                        color = ColorIncome.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "INFLOW",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ColorIncome,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
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
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (transaction.receiptImageUri != null || transaction.hasItemizedReceipt) {
                            Text(
                                text = "• 🧾 Receipt",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else if (transaction.noReceiptNeeded) {
                            Text(
                                text = "• No Receipt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = amountFormatted,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDebit) ColorExpense else ColorIncome
                    ),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End
                )
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End
                )
            }
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
