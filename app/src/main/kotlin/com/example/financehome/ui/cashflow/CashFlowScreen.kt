package com.example.financehome.ui.cashflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.Transaction
import com.example.financehome.domain.*
import com.example.financehome.ui.debts.DebtsViewModel
import com.example.financehome.ui.settings.SpendingThresholdDialog
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import com.example.financehome.ui.theme.ColorWarning
import com.example.financehome.ui.transactions.TransactionDetailSheetHost
import com.example.financehome.ui.transactions.TransactionsContent
import com.example.financehome.ui.transactions.TransactionsViewModel
import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CashFlowScreenTab(val label: String, val icon: ImageVector) {
    CASH_FLOW("Cash Flow", Icons.Default.CalendarMonth),
    TRANSACTIONS("Transactions", Icons.Default.ReceiptLong)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    viewModel: DebtsViewModel,
    transactionsViewModel: TransactionsViewModel = viewModel(),
    onNavigateToAddTransaction: () -> Unit = {},
    initialTab: CashFlowScreenTab = CashFlowScreenTab.CASH_FLOW,
    modifier: Modifier = Modifier
) {
    var selectedScreenTab by remember { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) {
        selectedScreenTab = initialTab
    }
    var selectedTransactionForEdit by remember { mutableStateOf<Transaction?>(null) }

    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val paycheckBreakdown by viewModel.paycheckBreakdown.collectAsState()
    val paycheckConfig by viewModel.paycheckConfig.collectAsState()
    val wageConfig by viewModel.wageConfig.collectAsState()
    val wageEstimate by viewModel.wageEstimate.collectAsState()
    val baselineWageEstimate by viewModel.baselineWageEstimate.collectAsState()
    val safeToSpendStatus by viewModel.safeToSpendStatus.collectAsState()
    val spendingAlertConfig by viewModel.spendingAlertConfig.collectAsState()

    var showEditPaycheckDialog by remember { mutableStateOf(false) }
    var showEditWageDialog by remember { mutableStateOf(false) }
    var showVerifyPaydayDialog by remember { mutableStateOf(false) }
    var showSpendingThresholdDialog by remember { mutableStateOf(false) }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedScreenTab == CashFlowScreenTab.CASH_FLOW) "Cash Flow & Payday" else "Transactions",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (selectedScreenTab == CashFlowScreenTab.CASH_FLOW) {
                        IconButton(onClick = { showSpendingThresholdDialog = true }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Spending Alert Thresholds")
                        }
                        IconButton(onClick = { showEditWageDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Wage & Payday Settings")
                        }
                    } else {
                        IconButton(onClick = onNavigateToAddTransaction) {
                            Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                        }
                    }
                }
            )
        },
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
            // Segmented Tab Switcher: Cash Flow vs Transactions
            TabRow(
                selectedTabIndex = selectedScreenTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                CashFlowScreenTab.values().forEach { tab ->
                    Tab(
                        selected = selectedScreenTab == tab,
                        onClick = { selectedScreenTab = tab },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = tab.label,
                                    fontWeight = if (selectedScreenTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }
            }

            when (selectedScreenTab) {
                CashFlowScreenTab.CASH_FLOW -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Next Payday Countdown Hero Banner
                        item {
                            NextPaydayHeroCard(
                                breakdown = paycheckBreakdown,
                                config = paycheckConfig,
                                wageEstimate = wageEstimate,
                                baselineEstimate = baselineWageEstimate,
                                safeToSpendStatus = safeToSpendStatus,
                                currencyFormat = currencyFormat,
                                onEditPaycheck = { showEditPaycheckDialog = true },
                                onConfigureThreshold = { showSpendingThresholdDialog = true }
                            )
                        }

                        // 2. Interactive Paydays & Due Dates Calendar (2nd item in Cash Flow, with 3-paycheck countdown, monthly glimpse, and in-calendar forward schedule)
                        item {
                            CashFlowCalendarCard(
                                bills = bills,
                                debts = debts,
                                paycheckConfig = paycheckConfig,
                                breakdown = paycheckBreakdown,
                                safeToSpendStatus = safeToSpendStatus,
                                wageEstimate = wageEstimate,
                                currencyFormat = currencyFormat,
                                onToggleBillPaid = { viewModel.toggleBillPaid(it) },
                                onToggleDebtPaid = { viewModel.toggleDebtPaid(it) },
                                onToggleObligationPaid = { item ->
                                    if (item.type == ObligationType.BILL && item.originalBill != null) {
                                        viewModel.toggleBillPaid(item.originalBill)
                                    } else if (item.type == ObligationType.DEBT && item.originalDebt != null) {
                                        viewModel.toggleDebtPaid(item.originalDebt)
                                    }
                                }
                            )
                        }

                        // 3. Paycheck Allocation Card (Cash Needed This Check vs Steady Fixed Reserve & Due This Pay Cycle checklist)
                        item {
                            CashFlowPaycheckPlannerCard(
                                breakdown = paycheckBreakdown,
                                config = paycheckConfig,
                                safeToSpendStatus = safeToSpendStatus,
                                currencyFormat = currencyFormat,
                                onEditPaycheck = { showEditPaycheckDialog = true },
                                onToggleObligationPaid = { item ->
                                    if (item.type == ObligationType.BILL && item.originalBill != null) {
                                        viewModel.toggleBillPaid(item.originalBill)
                                    } else if (item.type == ObligationType.DEBT && item.originalDebt != null) {
                                        viewModel.toggleDebtPaid(item.originalDebt)
                                    }
                                }
                            )
                        }

                        // 4. Weekly Wage & Hours Worked Tracker Card
                        item {
                            CashFlowWageHoursCard(
                                wageConfig = wageConfig,
                                wageEstimate = wageEstimate,
                                baselineEstimate = baselineWageEstimate,
                                paycheckConfig = paycheckConfig,
                                currencyFormat = currencyFormat,
                                onEditWage = { showEditWageDialog = true },
                                onAdjustW1 = { viewModel.adjustWeek1Hours(it) },
                                onAdjustW2 = { viewModel.adjustWeek2Hours(it) },
                                onVerifyPayday = { showVerifyPaydayDialog = true },
                                onResetPeriod = { viewModel.resetPayPeriodHours() }
                            )
                        }
                    }
                }
                CashFlowScreenTab.TRANSACTIONS -> {
                    TransactionsContent(
                        viewModel = transactionsViewModel,
                        onTransactionClick = { selectedTransactionForEdit = it }
                    )
                }
            }
        }
    }

    TransactionDetailSheetHost(
        transaction = selectedTransactionForEdit,
        viewModel = transactionsViewModel,
        onDismiss = { selectedTransactionForEdit = null },
        onTransactionUpdated = { selectedTransactionForEdit = it }
    )

    if (showEditPaycheckDialog) {
        CashFlowEditPaycheckDialog(
            currentConfig = paycheckConfig,
            onDismiss = { showEditPaycheckDialog = false },
            onSave = { newConfig ->
                viewModel.updatePaycheckConfig(newConfig)
                showEditPaycheckDialog = false
            }
        )
    }

    if (showEditWageDialog) {
        CashFlowEditWageDialog(
            currentConfig = wageConfig,
            frequency = paycheckConfig.frequency,
            onDismiss = { showEditWageDialog = false },
            onSave = { newConfig ->
                viewModel.updateWageConfig(newConfig)
                showEditWageDialog = false
            }
        )
    }

    if (showVerifyPaydayDialog) {
        CashFlowVerifyPaydayDialog(
            estimatedNet = wageEstimate.effectiveTakeHomePay,
            currencyFormat = currencyFormat,
            onDismiss = { showVerifyPaydayDialog = false },
            onConfirm = { actualNet ->
                viewModel.verifyPaycheck(actualNet)
                showVerifyPaydayDialog = false
            }
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

// ─────────────────────────────────────────────────────────────────────────────
// 1. Next Payday Hero Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NextPaydayHeroCard(
    breakdown: PaycheckBreakdown,
    config: PaycheckConfig,
    wageEstimate: WageEstimate,
    baselineEstimate: WageEstimate,
    safeToSpendStatus: SafeToSpendStatus,
    currencyFormat: NumberFormat,
    onEditPaycheck: () -> Unit,
    onConfigureThreshold: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dtf = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    val nextPayDateStr = breakdown.currentPayDate.format(dtf)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Next Payday: $nextPayDateStr",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = if (breakdown.daysUntilPay == 0L) "TODAY! 💰" else "${breakdown.daysUntilPay} days",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Income Estimate for this check vs Baseline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (wageEstimate.overtimeHours > 0.0) "Expected Deposit (with OT)" else "Expected Deposit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = currencyFormat.format(breakdown.currentTakeHomePay),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Baseline (Standard 40h)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = currencyFormat.format(breakdown.baselineTakeHomePay),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

            // Real-Time Safe-to-Spend Live Burn & Balance
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Remaining Safe-to-Spend:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = onConfigureThreshold,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Alert Thresholds",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (safeToSpendStatus.alertLevel) {
                            SafeToSpendAlertLevel.DEFICIT_STOP -> MaterialTheme.colorScheme.error
                            SafeToSpendAlertLevel.LOW_WARNING -> ColorWarning
                            SafeToSpendAlertLevel.HEALTHY -> ColorIncome
                        }
                    ) {
                        Text(
                            text = when (safeToSpendStatus.alertLevel) {
                                SafeToSpendAlertLevel.DEFICIT_STOP -> "DEFICIT STOP"
                                SafeToSpendAlertLevel.LOW_WARNING -> "LOW CUSHION"
                                SafeToSpendAlertLevel.HEALTHY -> "HEALTHY"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = currencyFormat.format(safeToSpendStatus.remainingSafeToSpend),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (safeToSpendStatus.alertLevel) {
                                SafeToSpendAlertLevel.DEFICIT_STOP -> MaterialTheme.colorScheme.error
                                SafeToSpendAlertLevel.LOW_WARNING -> ColorWarning
                                SafeToSpendAlertLevel.HEALTHY -> ColorIncome
                            }
                        )
                    )

                    if (safeToSpendStatus.daysRemainingInCycle > 0 && safeToSpendStatus.remainingSafeToSpend > 0) {
                        Text(
                            text = "${currencyFormat.format(safeToSpendStatus.dailyPace)}/day remaining",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = "Initial Cushion: ${currencyFormat.format(safeToSpendStatus.initialSafeToSpend)} • Spent this cycle: ${currencyFormat.format(safeToSpendStatus.spentThisCycle)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Wage & Hours Worked Tracker Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CashFlowWageHoursCard(
    wageConfig: WageConfig,
    wageEstimate: WageEstimate,
    baselineEstimate: WageEstimate,
    paycheckConfig: PaycheckConfig,
    currencyFormat: NumberFormat,
    onEditWage: () -> Unit,
    onAdjustW1: (Double) -> Unit,
    onAdjustW2: (Double) -> Unit,
    onVerifyPayday: () -> Unit,
    onResetPeriod: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Wage info + Edit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Work,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Hours & Paycheck Estimator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${currencyFormat.format(wageConfig.hourlyWage)}/hr • ${String.format(Locale.US, "%.1f", wageConfig.overtimeMultiplier)}x OT (>40h)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEditWage) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Wage Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Verified Status Banner vs Estimated Banner
            if (wageConfig.isPaycheckVerified) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ColorIncome.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ColorIncome,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Payday Verified",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ColorIncome
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Actual Deposit: ${currencyFormat.format(wageConfig.verifiedNetPay ?: wageEstimate.estimatedNetPay)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Current pay cycle verified.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onResetPeriod,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Next Cycle", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (wageEstimate.overtimeHours > 0.0) "This Check (with ${String.format(Locale.US, "%.1f", wageEstimate.overtimeHours)}h OT)" else "Estimated Take-Home Pay",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currencyFormat.format(wageEstimate.estimatedNetPay),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Gross: ${currencyFormat.format(wageEstimate.grossPay)} • Baseline: ${currencyFormat.format(baselineEstimate.estimatedNetPay)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Button(
                            onClick = onVerifyPayday,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Verify Payday", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Hours Logged Steppers
            Text(
                text = "Hours Worked This Cycle (${String.format(Locale.US, "%.1f", wageEstimate.totalHours)}h total):",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            // Week 1 Hours
            CashFlowHoursRow(
                label = "Week 1",
                hours = wageConfig.week1Hours,
                onDelta = onAdjustW1
            )

            // If not weekly, show Week 2 Hours
            if (paycheckConfig.frequency != PayFrequency.WEEKLY) {
                CashFlowHoursRow(
                    label = "Week 2",
                    hours = wageConfig.week2Hours,
                    onDelta = onAdjustW2
                )
            }

            // Helpful note about baseline decoupling
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Future pay periods on your schedule use your baseline 40h/wk (${currencyFormat.format(baselineEstimate.estimatedNetPay)}) so overtime worked this cycle doesn't inflate future projections.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun CashFlowHoursRow(
    label: String,
    hours: Double,
    onDelta: (Double) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$label: ${String.format(Locale.US, "%.1f", hours)} hrs",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (hours > 40.0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ColorIncome.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "+${String.format(Locale.US, "%.1f", hours - 40.0)}h OT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = ColorIncome,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (hours > 40.0) "40.0 regular + ${String.format(Locale.US, "%.1f", hours - 40.0)} overtime"
                    else "${String.format(Locale.US, "%.1f", hours)} regular",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onDelta(-1.0) },
                    enabled = hours >= 1.0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease 1 hour", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { onDelta(1.0) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase 1 hour", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { onDelta(5.0) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = "+5h",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Interactive Due Dates & Paydays Calendar Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CashFlowCalendarCard(
    bills: List<Bill>,
    debts: List<Debt>,
    paycheckConfig: PaycheckConfig,
    breakdown: PaycheckBreakdown,
    safeToSpendStatus: SafeToSpendStatus,
    wageEstimate: WageEstimate?,
    currencyFormat: NumberFormat,
    onToggleBillPaid: (Bill) -> Unit,
    onToggleDebtPaid: (Debt) -> Unit,
    onToggleObligationPaid: (UpcomingObligationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<Int?>(LocalDate.now().dayOfMonth) }

    val anchorDate = LocalDate.ofEpochDay(paycheckConfig.anchorPayDateEpochDay)
    val payDays = remember(displayedMonth, anchorDate, paycheckConfig.frequency) {
        PaycheckCalculator.getPayDatesForMonth(
            year = displayedMonth.year,
            month = displayedMonth.monthValue,
            anchorPayDate = anchorDate,
            frequency = paycheckConfig.frequency
        )
    }

    val billDays = remember(bills, displayedMonth) {
        bills.groupBy { it.dueDayOfMonth }
    }
    val debtDays = remember(debts, displayedMonth) {
        debts.filter { it.currentBalance > 0.0 && it.minimumPayment > 0.0 }.groupBy { it.dueDayOfMonth }
    }

    val threeCheckCountdown = remember(anchorDate, paycheckConfig.frequency) {
        PaycheckCalculator.calculateThreePaycheckCountdown(anchorDate, paycheckConfig.frequency)
    }

    val monthlyGlimpse = remember(displayedMonth, bills, debts, paycheckConfig, anchorDate, wageEstimate) {
        PaycheckCalculator.calculateMonthlyGlimpse(
            year = displayedMonth.year,
            month = displayedMonth.monthValue,
            bills = bills,
            debts = debts,
            config = paycheckConfig,
            anchorPayDate = anchorDate,
            currentTakeHomePay = wageEstimate?.effectiveTakeHomePay ?: paycheckConfig.typicalTakeHomePay
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Month switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }
                Text(
                    text = displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
            }

            // 3-Paycheck Month Countdown under the month
            if (threeCheckCountdown != null) {
                val isViewingTarget = displayedMonth == threeCheckCountdown.targetYearMonth
                val bannerBg = if (threeCheckCountdown.isCurrentMonth || isViewingTarget) {
                    ColorIncome.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                }
                val contentColor = if (threeCheckCountdown.isCurrentMonth || isViewingTarget) {
                    ColorIncome
                } else {
                    MaterialTheme.colorScheme.primary
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bannerBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (threeCheckCountdown.isCurrentMonth || isViewingTarget) Icons.Default.Celebration else Icons.Default.Event,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            if (threeCheckCountdown.isCurrentMonth) {
                                Text(
                                    text = "3-Paycheck Month Active! (3 Paydays Scheduled)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor
                                )
                                Text(
                                    text = "Paydays: ${threeCheckCountdown.payDates.joinToString(", ")} • 1 extra paycheck available for savings or debt!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (isViewingTarget) {
                                Text(
                                    text = "Viewing 3-Paycheck Month (${threeCheckCountdown.formattedMonth})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor
                                )
                                Text(
                                    text = "In ${threeCheckCountdown.monthsRemaining} month(s) • ${threeCheckCountdown.daysRemaining} days • Paydays: ${threeCheckCountdown.payDates.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Next 3-Paycheck Month: ${threeCheckCountdown.formattedMonth}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor
                                )
                                Text(
                                    text = "In ${threeCheckCountdown.monthsRemaining} month(s) (${threeCheckCountdown.daysRemaining} days) • Paydays: ${threeCheckCountdown.payDates.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Monthly Financial Outlook Quick Glimpse
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${displayedMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale.US))} Financial Outlook",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (monthlyGlimpse.netSurplus >= 0) ColorIncome.copy(alpha = 0.15f) else ColorExpense.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${if (monthlyGlimpse.netSurplus >= 0) "+" else ""}${currencyFormat.format(monthlyGlimpse.netSurplus)} Net",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (monthlyGlimpse.netSurplus >= 0) ColorIncome else ColorExpense,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Est Income
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Est. Income",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormat.format(monthlyGlimpse.estimatedIncome),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = ColorIncome,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "${monthlyGlimpse.payDaysCount} check${if (monthlyGlimpse.payDaysCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // Bills
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Total Bills",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormat.format(monthlyGlimpse.totalBills),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "${bills.size} bill${if (bills.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // Debts
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Total Debts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormat.format(monthlyGlimpse.totalDebts),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "Min payments",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ColorIncome))
                    Text("Payday", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ColorWarning))
                    Text("Bills", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ColorExpense))
                    Text("Debts", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Weekday Headers
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Calendar Grid
            val firstDayOfWeek = displayedMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
            val daysInMonth = displayedMonth.lengthOfMonth()
            val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

            for (week in 0 until (totalCells / 7)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    for (dayOfWeek in 0..6) {
                        val cellIndex = week * 7 + dayOfWeek
                        val dayNumber = cellIndex - firstDayOfWeek + 1

                        if (dayNumber in 1..daysInMonth) {
                            val isPayday = payDays.contains(dayNumber)
                            val hasBills = billDays.containsKey(dayNumber)
                            val hasDebts = debtDays.containsKey(dayNumber)
                            val isSelected = selectedDay == dayNumber
                            val isToday = displayedMonth == YearMonth.now() && dayNumber == LocalDate.now().dayOfMonth

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        }
                                    )
                                    .clickable { selectedDay = dayNumber },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayNumber",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected || isToday || isPayday) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    )
                                    // Dots row
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (isPayday) {
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ColorIncome))
                                        }
                                        if (hasBills) {
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ColorWarning))
                                        }
                                        if (hasDebts) {
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ColorExpense))
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }

            // Selected Day Details
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (selectedDay != null) {
                val day = selectedDay!!
                val isPayday = payDays.contains(day)
                val dayBills = billDays[day] ?: emptyList()
                val dayDebts = debtDays[day] ?: emptyList()
                val totalDue = dayBills.sumOf { it.amount } + dayDebts.sumOf { it.minimumPayment }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dateFormatted = displayedMonth.atDay(day)
                            .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        if (totalDue > 0.0) {
                            Text(
                                text = "Total Due: ${currencyFormat.format(totalDue)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ColorExpense
                            )
                        }
                    }

                    if (isPayday) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorIncome.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = ColorIncome, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        text = "Payday Estimated Deposit",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = ColorIncome
                                    )
                                    Text(
                                        text = "${currencyFormat.format(paycheckConfig.typicalTakeHomePay)} (${paycheckConfig.frequency.displayName})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (dayBills.isEmpty() && dayDebts.isEmpty() && !isPayday) {
                        Text(
                            text = "No bills or debt payments scheduled for this date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    dayBills.forEach { bill ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onToggleBillPaid(bill) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (bill.isPaidThisMonth) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (bill.isPaidThisMonth) ColorIncome else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = bill.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = if (bill.isPaidThisMonth) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    )
                                    Text(
                                        text = "Bill • ${bill.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Text(
                                text = currencyFormat.format(bill.amount),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    dayDebts.forEach { debt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onToggleDebtPaid(debt) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (debt.isPaidThisMonth) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (debt.isPaidThisMonth) ColorIncome else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = debt.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = if (debt.isPaidThisMonth) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    )
                                    Text(
                                        text = "Debt Min • ${debt.type.name.replace('_', ' ').lowercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Text(
                                text = currencyFormat.format(debt.minimumPayment),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Forward Paycheck Schedule embedded inside the Calendar card
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ForwardPaycheckScheduleSection(
                breakdown = breakdown,
                safeToSpendStatus = safeToSpendStatus,
                currencyFormat = currencyFormat,
                onToggleObligationPaid = onToggleObligationPaid
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3b. Forward Paycheck Schedule Projection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ForwardPaycheckScheduleSection(
    breakdown: PaycheckBreakdown,
    safeToSpendStatus: SafeToSpendStatus,
    currencyFormat: NumberFormat,
    onToggleObligationPaid: (UpcomingObligationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val dtf = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    var selectedPeriodIndex by remember { mutableStateOf<Int?>(null) }

    if (breakdown.upcomingPayPeriods.size <= 1) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Forward Paycheck Schedule (Next 3 Paydays):",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "Tap any paycheck box to see the exact bills and debts it covers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            breakdown.upcomingPayPeriods.forEach { period ->
                val isSelected = selectedPeriodIndex == period.periodIndex
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (period.isCurrentPeriod) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedPeriodIndex = if (selectedPeriodIndex == period.periodIndex) null else period.periodIndex
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (period.isCurrentPeriod) "Pay #1 (Upcoming)" else "Pay #${period.periodIndex}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (period.isCurrentPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = period.payDate.format(dtf),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val displaySafe = if (period.isCurrentPeriod) safeToSpendStatus.remainingSafeToSpend else period.safeToSpend
                        val safeColor = when {
                            displaySafe > 0.001 -> ColorIncome
                            displaySafe >= -0.001 -> MaterialTheme.colorScheme.primary
                            else -> ColorExpense
                        }
                        Text(
                            text = "Pay: ${currencyFormat.format(period.projectedTakeHomePay)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (period.isCurrentPeriod) "(Current Hours)" else "(Baseline 40h)",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currencyFormat.format(period.totalObligations)} due",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ColorExpense,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${currencyFormat.format(displaySafe)} safe",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = safeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (!period.deficitNote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = period.deficitNote,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (period.reserveHeldForFutureChecks > 0.001) ColorWarning else if (displaySafe >= -0.001) ColorIncome else ColorExpense,
                                maxLines = 2,
                                softWrap = true
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isSelected) "Hide bills" else "${period.obligations.size} bill${if (period.obligations.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                imageVector = if (isSelected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // Expandable drop-down of the bills covered by the selected paycheck
        val activePeriod = breakdown.upcomingPayPeriods.find { it.periodIndex == selectedPeriodIndex }
        AnimatedVisibility(visible = activePeriod != null) {
            if (activePeriod != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (activePeriod.isCurrentPeriod) "Pay #1 (Upcoming) Obligations" else "Pay #${activePeriod.periodIndex} Obligations",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Due window: ${activePeriod.payDate.format(dtf)} to ${activePeriod.nextPayDate.format(dtf)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${currencyFormat.format(activePeriod.totalObligations)} total",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = ColorExpense,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        if (activePeriod.obligations.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text("🎉", fontSize = 16.sp)
                                Text(
                                    text = "No bills or debts due in this pay window! All take-home pay is safe to spend.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activePeriod.obligations.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (activePeriod.isCurrentPeriod) {
                                                    onToggleObligationPaid(item)
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (item.type == ObligationType.BILL) "📄" else "💳",
                                                fontSize = 16.sp
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            textDecoration = if (item.isPaid) TextDecoration.LineThrough else null
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (item.type == ObligationType.BILL) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                                    ) {
                                                        Text(
                                                            text = if (item.type == ObligationType.BILL) "BILL" else "DEBT",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 9.sp
                                                            ),
                                                            color = if (item.type == ObligationType.BILL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Due ${item.dueDate.format(dtf)}" + if (item.subtitle.isNotBlank()) " • ${item.subtitle}" else "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (item.isPaid) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = ColorIncome.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "PAID",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        ),
                                                        color = ColorIncome,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = currencyFormat.format(item.amount),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (item.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                softWrap = false,
                                                textAlign = TextAlign.End
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
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Paycheck Allocation Card (Steady vs Window Reserve)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CashFlowPaycheckPlannerCard(
    breakdown: PaycheckBreakdown,
    config: PaycheckConfig,
    safeToSpendStatus: SafeToSpendStatus,
    currencyFormat: NumberFormat,
    onEditPaycheck: () -> Unit,
    onToggleObligationPaid: (UpcomingObligationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val dtf = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    val nextPayDateStr = breakdown.followingPayDate.format(dtf)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Paycheck Allocation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onEditPaycheck) {
                    Icon(Icons.Default.Settings, contentDescription = "Edit Schedule", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Cash Needed vs Steady Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Cash Needed This Check",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currencyFormat.format(breakdown.windowObligationsTotal),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Bills: ${currencyFormat.format(breakdown.windowBillsTotal)} • Debts: ${currencyFormat.format(breakdown.windowDebtsTotal)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Steady Fixed Reserve",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currencyFormat.format(breakdown.perPaycheckReserve),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Equalized savings per check",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Due this pay cycle checklist
            if (breakdown.obligationsDueInPeriod.isNotEmpty()) {
                Text(
                    text = "Due This Pay Cycle (${breakdown.obligationsDueInPeriod.size}):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    breakdown.obligationsDueInPeriod.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Checkbox(
                                        checked = item.isPaid,
                                        onCheckedChange = { onToggleObligationPaid(item) },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    textDecoration = if (item.isPaid) TextDecoration.LineThrough else null
                                                )
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (item.type == ObligationType.BILL) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = if (item.type == ObligationType.BILL) "BILL" else "DEBT",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                                    color = if (item.type == ObligationType.BILL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Due ${item.dueDate.format(dtf)} • ${item.subtitle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = currencyFormat.format(item.amount),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // 3-Paycheck Bonus Months Callout (for bi-weekly schedules)
            if (breakdown.threePaycheckMonths.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "3-Paycheck Bonus Months: ${breakdown.threePaycheckMonths.joinToString(", ")}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "On these months you receive an extra paycheck that has zero fixed obligations assigned to it! Pure surplus for savings or debt payoff.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs: Edit Paycheck, Edit Wage, Verify Payday
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CashFlowEditPaycheckDialog(
    currentConfig: PaycheckConfig,
    onDismiss: () -> Unit,
    onSave: (PaycheckConfig) -> Unit
) {
    var selectedFrequency by remember { mutableStateOf(currentConfig.frequency) }
    var takeHomeText by remember { mutableStateOf(currentConfig.typicalTakeHomePay.toString()) }
    var anchorPayDate by remember { mutableStateOf(LocalDate.ofEpochDay(currentConfig.anchorPayDateEpochDay)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay Schedule Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure how often you get paid and an anchor pay date to calculate upcoming paydays and bill cycles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Pay Frequency:", style = MaterialTheme.typography.labelMedium)
                PayFrequency.values().forEach { freq ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFrequency = freq }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFrequency == freq,
                            onClick = { selectedFrequency = freq }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(freq.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                OutlinedTextField(
                    value = takeHomeText,
                    onValueChange = { takeHomeText = it },
                    label = { Text("Baseline Take-Home Pay ($)") },
                    placeholder = { Text("1800.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Anchor Payday: ${anchorDateDisplay(anchorPayDate)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { anchorPayDate = anchorPayDate.minusDays(if (selectedFrequency == PayFrequency.WEEKLY) 7 else 14) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-1 Cycle")
                    }
                    OutlinedButton(
                        onClick = { anchorPayDate = anchorPayDate.plusDays(if (selectedFrequency == PayFrequency.WEEKLY) 7 else 14) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+1 Cycle")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pay = takeHomeText.toDoubleOrNull() ?: currentConfig.typicalTakeHomePay
                    onSave(
                        currentConfig.copy(
                            frequency = selectedFrequency,
                            typicalTakeHomePay = pay,
                            anchorPayDateEpochDay = anchorPayDate.toEpochDay()
                        )
                    )
                }
            ) {
                Text("Save")
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
fun CashFlowEditWageDialog(
    currentConfig: WageConfig,
    frequency: PayFrequency,
    onDismiss: () -> Unit,
    onSave: (WageConfig) -> Unit
) {
    var hourlyWageText by remember { mutableStateOf(currentConfig.hourlyWage.toString()) }
    var otMultiplierText by remember { mutableStateOf(currentConfig.overtimeMultiplier.toString()) }
    var deductionsPercentText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentConfig.estimatedDeductionPercent))
    }
    var standardHoursText by remember { mutableStateOf(currentConfig.standardWeeklyHours.toString()) }
    var week1Text by remember { mutableStateOf(currentConfig.week1Hours.toString()) }
    var week2Text by remember { mutableStateOf(currentConfig.week2Hours.toString()) }
    var autoSync by remember { mutableStateOf(currentConfig.autoSyncPaycheckPlanner) }

    val isValid = (hourlyWageText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (otMultiplierText.toDoubleOrNull() ?: -1.0) >= 1.0 &&
            (deductionsPercentText.toDoubleOrNull() ?: -1.0) in 0.0..100.0 &&
            (standardHoursText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (week1Text.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (week2Text.toDoubleOrNull() ?: -1.0) >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wage & Hours Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure your hourly pay and baseline hours to estimate current and future paychecks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hourlyWageText,
                        onValueChange = { hourlyWageText = it },
                        label = { Text("Hourly Wage ($)") },
                        placeholder = { Text("25.00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = otMultiplierText,
                        onValueChange = { otMultiplierText = it },
                        label = { Text("OT Multiplier") },
                        placeholder = { Text("1.5") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = deductionsPercentText,
                    onValueChange = { deductionsPercentText = it },
                    label = { Text("Est. Deductions & Taxes (%)") },
                    placeholder = { Text("18.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = standardHoursText,
                    onValueChange = { standardHoursText = it },
                    label = { Text("Standard Baseline Hours/Week") },
                    placeholder = { Text("40.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Baseline hours are used for all future pay periods so single-week overtime does not inflate future forecasts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    text = "Logged Hours This Period:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = week1Text,
                        onValueChange = { week1Text = it },
                        label = { Text("Week 1 Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    if (frequency != PayFrequency.WEEKLY) {
                        OutlinedTextField(
                            value = week2Text,
                            onValueChange = { week2Text = it },
                            label = { Text("Week 2 Hours") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = autoSync, onCheckedChange = { autoSync = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sync hours estimate with Paycheck Planner",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wage = hourlyWageText.toDoubleOrNull() ?: currentConfig.hourlyWage
                    val ot = otMultiplierText.toDoubleOrNull() ?: currentConfig.overtimeMultiplier
                    val ded = deductionsPercentText.toDoubleOrNull() ?: currentConfig.estimatedDeductionPercent
                    val std = standardHoursText.toDoubleOrNull() ?: currentConfig.standardWeeklyHours
                    val w1 = week1Text.toDoubleOrNull() ?: currentConfig.week1Hours
                    val w2 = week2Text.toDoubleOrNull() ?: currentConfig.week2Hours

                    onSave(
                        currentConfig.copy(
                            hourlyWage = wage,
                            overtimeMultiplier = ot,
                            estimatedDeductionPercent = ded,
                            standardWeeklyHours = std,
                            week1Hours = w1,
                            week2Hours = w2,
                            autoSyncPaycheckPlanner = autoSync
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("Save")
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
fun CashFlowVerifyPaydayDialog(
    estimatedNet: Double,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var actualPayText by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", estimatedNet))
    }

    val isValid = (actualPayText.toDoubleOrNull() ?: -1.0) > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify Actual Payday Deposit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Did your paycheck post to your bank? Confirm the exact net take-home deposit to lock it into your cash flow planner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Estimated Net: ${currencyFormat.format(estimatedNet)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                OutlinedTextField(
                    value = actualPayText,
                    onValueChange = { actualPayText = it },
                    label = { Text("Actual Net Deposit ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val actual = actualPayText.toDoubleOrNull() ?: estimatedNet
                    onConfirm(actual)
                },
                enabled = isValid
            ) {
                Text("Verify & Lock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun anchorDateDisplay(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))
}
