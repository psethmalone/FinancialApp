package com.example.financehome.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import com.example.financehome.domain.DebtPayoffMilestone
import com.example.financehome.domain.MultiDebtComparison
import com.example.financehome.domain.ObligationType
import com.example.financehome.domain.PayFrequency
import com.example.financehome.domain.PaycheckBreakdown
import com.example.financehome.domain.PaycheckCalculator
import com.example.financehome.domain.PaycheckConfig
import com.example.financehome.domain.PayoffStrategy
import com.example.financehome.domain.StrategyPayoffResult
import com.example.financehome.domain.UpcomingObligationItem
import com.example.financehome.domain.WageConfig
import com.example.financehome.domain.WageEstimate
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import com.example.financehome.ui.theme.ColorWarning
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ObligationTab { BILLS, DEBTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(
    viewModel: DebtsViewModel,
    onDebtSelected: (Debt) -> Unit,
    modifier: Modifier = Modifier
) {
    val debts by viewModel.debts.collectAsState()
    val totalDebt by viewModel.totalDebt.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val totalMonthlyBills by viewModel.totalMonthlyBills.collectAsState()
    val totalMonthlyDebtMinimums by viewModel.totalMonthlyDebtMinimums.collectAsState()
    val totalMonthlyFixedNut by viewModel.totalMonthlyFixedNut.collectAsState()
    val paycheckBreakdown by viewModel.paycheckBreakdown.collectAsState()
    val paycheckConfig by viewModel.paycheckConfig.collectAsState()
    val wageConfig by viewModel.wageConfig.collectAsState()
    val wageEstimate by viewModel.wageEstimate.collectAsState()
    val multiDebtComparison by viewModel.multiDebtComparison.collectAsState()
    val extraMonthlyPayoffBudget by viewModel.extraMonthlyPayoffBudget.collectAsState()

    var selectedTab by remember { mutableStateOf(ObligationTab.BILLS) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var showAddBillDialog by remember { mutableStateOf(false) }
    var showEditPaycheckDialog by remember { mutableStateOf(false) }
    var showEditWageDialog by remember { mutableStateOf(false) }
    var showVerifyPaydayDialog by remember { mutableStateOf(false) }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills & Obligations", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedTab == ObligationTab.BILLS) {
                                showAddBillDialog = true
                            } else {
                                showAddDebtDialog = true
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = if (selectedTab == ObligationTab.BILLS) "Add Bill" else "Add Debt"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == ObligationTab.BILLS) {
                        showAddBillDialog = true
                    } else {
                        showAddDebtDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (selectedTab == ObligationTab.BILLS) "Add Bill" else "Add Debt"
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Combined Monthly Fixed Nut Hero Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Monthly Fixed Obligations",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currencyFormat.format(totalMonthlyFixedNut) + "/mo",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bills: ${currencyFormat.format(totalMonthlyBills)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "Debt Minimums: ${currencyFormat.format(totalMonthlyDebtMinimums)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Total Outstanding Debt: ${currencyFormat.format(totalDebt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // Interactive Monthly Due Dates & Paydays Calendar
            item {
                DueDatesCalendarCard(
                    bills = bills,
                    debts = debts,
                    paycheckConfig = paycheckConfig,
                    currencyFormat = currencyFormat,
                    onToggleBillPaid = { viewModel.toggleBillPaid(it) },
                    onToggleDebtPaid = { viewModel.toggleDebtPaid(it) }
                )
            }

            // Paycheck-to-Obligations Cushion Allocator Card
            item {
                PaycheckPlannerCard(
                    breakdown = paycheckBreakdown,
                    config = paycheckConfig,
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

            // Weekly Wage & Hours Worked Estimator Card
            item {
                WageHoursEstimatorCard(
                    wageConfig = wageConfig,
                    wageEstimate = wageEstimate,
                    paycheckConfig = paycheckConfig,
                    currencyFormat = currencyFormat,
                    onEditWage = { showEditWageDialog = true },
                    onAdjustW1 = { viewModel.adjustWeek1Hours(it) },
                    onAdjustW2 = { viewModel.adjustWeek2Hours(it) },
                    onVerifyPayday = { showVerifyPaydayDialog = true },
                    onResetPeriod = { viewModel.resetPayPeriodHours() }
                )
            }

            // Segmented Tab Switcher (Bills vs Debts)
            item {
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == ObligationTab.BILLS,
                        onClick = { selectedTab = ObligationTab.BILLS },
                        text = {
                            Text(
                                text = "Recurring Bills (${bills.size})",
                                fontWeight = if (selectedTab == ObligationTab.BILLS) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == ObligationTab.DEBTS,
                        onClick = { selectedTab = ObligationTab.DEBTS },
                        text = {
                            Text(
                                text = "Debts & Loans (${debts.size})",
                                fontWeight = if (selectedTab == ObligationTab.DEBTS) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab Content
            if (selectedTab == ObligationTab.BILLS) {
                if (bills.isEmpty()) {
                    item {
                        EmptyBillsCard(onAddBill = { showAddBillDialog = true })
                    }
                } else {
                    items(bills, key = { it.id }) { bill ->
                        BillCardItem(
                            bill = bill,
                            currencyFormat = currencyFormat,
                            onTogglePaid = { viewModel.toggleBillPaid(bill) },
                            onDelete = { viewModel.deleteBill(bill) }
                        )
                    }
                }
            } else {
                if (debts.isEmpty()) {
                    item {
                        EmptyDebtsCard(onAddDebt = { showAddDebtDialog = true })
                    }
                } else {
                    item {
                        DebtPayoffOptimizerCard(
                            comparison = multiDebtComparison,
                            currentExtra = extraMonthlyPayoffBudget,
                            currencyFormat = currencyFormat,
                            onExtraChanged = { viewModel.setExtraMonthlyPayoffBudget(it) }
                        )
                    }

                    items(debts, key = { it.id }) { debt ->
                        DebtCardItem(
                            debt = debt,
                            currencyFormat = currencyFormat,
                            onClick = { onDebtSelected(debt) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showAddBillDialog) {
        AddBillDialog(
            onDismiss = { showAddBillDialog = false },
            onAddBill = { name, amount, dueDay, category, isAutoPay, notes ->
                viewModel.addBill(name, amount, dueDay, category, isAutoPay, notes)
                showAddBillDialog = false
            }
        )
    }

    if (showAddDebtDialog) {
        AddDebtDialog(
            onDismiss = { showAddDebtDialog = false },
            onAddDebt = { name, balance, apr, minPayment, type, dueDay ->
                viewModel.addDebt(name, balance, apr, minPayment, type, dueDay)
                showAddDebtDialog = false
            }
        )
    }

    if (showEditPaycheckDialog) {
        EditPaycheckDialog(
            currentConfig = paycheckConfig,
            onDismiss = { showEditPaycheckDialog = false },
            onSave = { newConfig ->
                viewModel.updatePaycheckConfig(newConfig)
                showEditPaycheckDialog = false
            }
        )
    }

    if (showEditWageDialog) {
        EditWageDialog(
            currentConfig = wageConfig,
            frequency = paycheckConfig.frequency,
            onDismiss = { showEditWageDialog = false },
            onSave = { updated ->
                viewModel.updateWageConfig(updated)
                showEditWageDialog = false
            }
        )
    }

    if (showVerifyPaydayDialog) {
        VerifyPaydayDialog(
            estimatedAmount = wageEstimate.estimatedNetPay,
            currentVerified = wageConfig.verifiedNetPay,
            currencyFormat = currencyFormat,
            onDismiss = { showVerifyPaydayDialog = false },
            onConfirm = { actualAmount ->
                viewModel.verifyPaycheck(actualAmount)
                showVerifyPaydayDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paycheck Planner Component
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PaycheckPlannerCard(
    breakdown: PaycheckBreakdown,
    config: PaycheckConfig,
    currencyFormat: NumberFormat,
    onEditPaycheck: () -> Unit,
    onToggleObligationPaid: (UpcomingObligationItem) -> Unit = {}
) {
    val dtf = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val dayOfWeekFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
    val payDateStr = breakdown.currentPayDate.format(dayOfWeekFmt)
    val nextPayDateStr = breakdown.followingPayDate.format(dtf)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Next Payday info + Edit button
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Next Payday: $payDateStr",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (breakdown.daysUntilPay == 0L) "Today is Payday! • ${config.frequency.displayName}"
                                   else "In ${breakdown.daysUntilPay} days • ${config.frequency.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEditPaycheck) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Paycheck Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Specifically What to Save: Cash Needed vs Steady Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cash Needed this cycle
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
                        Text(
                            text = "Due before $nextPayDateStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Steady Equalized Reserve Target
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
                            text = "Stash every payday",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Window Insight & Advice
            if (breakdown.windowVariance < 0) {
                // Peak obligation cycle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ColorWarning.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ColorWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Peak Cycle: ${currencyFormat.format(breakdown.windowObligationsTotal)} in bills & debts due before next check. If your Bills Account has a ${currencyFormat.format(breakdown.recommendedBuffer)} cushion, your steady ${currencyFormat.format(breakdown.perPaycheckReserve)} is safe. Otherwise, set aside ${currencyFormat.format(breakdown.windowObligationsTotal)} so nothing bounces!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ColorIncome.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ColorIncome,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Light Cycle: Only ${currencyFormat.format(breakdown.windowObligationsTotal)} in bills & debts due. Transferring your steady ${currencyFormat.format(breakdown.perPaycheckReserve)} builds your cushion for peak rent weeks!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Safe to Spend Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (breakdown.isSurplus) ColorIncome.copy(alpha = 0.15f) else ColorExpense.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (breakdown.isSurplus) "Safe-to-Spend Remaining" else "Paycheck Deficit",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (breakdown.isSurplus) ColorIncome else ColorExpense
                        )
                        Text(
                            text = currencyFormat.format(if (breakdown.isSurplus) breakdown.perPaycheckSafeToSpend else breakdown.deficitAmount),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (breakdown.isSurplus) ColorIncome else ColorExpense
                        )
                    }
                    Text(
                        text = if (breakdown.isSurplus) "${currencyFormat.format(breakdown.dailySafeToSpend)} / day for lifestyle" else "Bills exceed pay",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (breakdown.isSurplus) ColorIncome.copy(alpha = 0.85f) else ColorExpense.copy(alpha = 0.85f)
                    )
                }
            }

            // Upcoming Obligations in this Pay Cycle
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
                                                color = if (item.type == ObligationType.BILL) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                                }
                                            ) {
                                                Text(
                                                    text = if (item.type == ObligationType.BILL) "BILL" else "DEBT",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    ),
                                                    color = if (item.type == ObligationType.BILL) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.secondary
                                                    },
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
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Multi-Paycheck Schedule Projection (Next 3 Paychecks)
            if (breakdown.upcomingPayPeriods.size > 1) {
                Text(
                    text = "Forward Paycheck Schedule:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    breakdown.upcomingPayPeriods.forEach { period ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Pay #${period.periodIndex}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = period.payDate.format(dtf),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${currencyFormat.format(period.totalObligations)} due",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ColorExpense
                                )
                                Text(
                                    text = "${currencyFormat.format(period.safeToSpend)} safe",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorIncome
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
                                text = "In these months, you get 3 paychecks. The 3rd check is 100% bonus cash with $0 bills to pay!",
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
// Bill Card Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BillCardItem(
    bill: Bill,
    currencyFormat: NumberFormat,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (bill.isPaidThisMonth) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (bill.isPaidThisMonth) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Checkbox to mark paid
            IconButton(onClick = onTogglePaid) {
                Icon(
                    imageVector = if (bill.isPaidThisMonth) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (bill.isPaidThisMonth) "Mark Unpaid" else "Mark Paid",
                    tint = if (bill.isPaidThisMonth) ColorIncome else MaterialTheme.colorScheme.outline
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bill.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (bill.isPaidThisMonth) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (bill.isPaidThisMonth) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                    if (bill.isAutoPay) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Auto",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Due Day ${bill.dueDayOfMonth}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = bill.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = currencyFormat.format(bill.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (bill.isPaidThisMonth) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Bill",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Debt Card Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DebtCardItem(
    debt: Debt,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    val aprPercent = debt.apr * 100.0
    val aprColor = when {
        aprPercent >= 20.0 -> ColorExpense
        aprPercent >= 10.0 -> ColorWarning
        else -> ColorIncome
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = debt.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Due Day ${debt.dueDayOfMonth}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = debt.type.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = currencyFormat.format(debt.currentBalance),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = ColorExpense
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "APR:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f%%", aprPercent),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = aprColor
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Min Payment:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormat.format(debt.minimumPayment) + "/mo",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EmptyBillsCard(onAddBill: () -> Unit) {
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No recurring bills added yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add Rent, Utilities, Car Insurance, Wi-Fi, or Subscriptions to calculate your equalized paycheck cushion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddBill) {
                Text("Add Your First Bill")
            }
        }
    }
}

@Composable
fun EmptyDebtsCard(onAddDebt: () -> Unit) {
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CreditCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No debts added yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add credit cards, auto loans, mortgages, or student loans to project payoff timelines and test extra principal payments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddDebt) {
                Text("Add Your First Debt")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillDialog(
    onDismiss: () -> Unit,
    onAddBill: (name: String, amount: Double, dueDay: Int, category: String, isAutoPay: Boolean, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDayText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Housing") }
    var isAutoPay by remember { mutableStateOf(false) }

    val categories = listOf("Housing", "Utilities", "Insurance", "Phone/Internet", "Subscriptions", "Other")

    val dueDayInt = dueDayText.toIntOrNull() ?: 1
    val isValid = name.isNotBlank() &&
            (amountText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            dueDayInt in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recurring Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill Name") },
                    placeholder = { Text("e.g. Rent, Duke Energy, Geico") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount ($)") },
                        placeholder = { Text("1200.00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = dueDayText,
                        onValueChange = { dueDayText = it },
                        label = { Text("Due Day (1-31)") },
                        placeholder = { Text("1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(text = "Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Pay Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isAutoPay, onCheckedChange = { isAutoPay = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    onAddBill(name, amount, dueDayInt, selectedCategory, isAutoPay, null)
                },
                enabled = isValid
            ) {
                Text("Add Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    onDismiss: () -> Unit,
    onAddDebt: (name: String, balance: Double, apr: Double, minPayment: Double, type: DebtType, dueDay: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var aprText by remember { mutableStateOf("") }
    var minPaymentText by remember { mutableStateOf("") }
    var dueDayText by remember { mutableStateOf("15") }
    var selectedType by remember { mutableStateOf(DebtType.CREDIT_CARD) }

    val dueDayInt = dueDayText.toIntOrNull() ?: 15
    val isValid = name.isNotBlank() &&
            (balanceText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (aprText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (minPaymentText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            dueDayInt in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Debt Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Debt Name") },
                    placeholder = { Text("e.g. Chase Freedom, Auto Loan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Current Balance ($)") },
                    placeholder = { Text("5000.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = aprText,
                        onValueChange = { aprText = it },
                        label = { Text("APR %") },
                        placeholder = { Text("21.99") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = minPaymentText,
                        onValueChange = { minPaymentText = it },
                        label = { Text("Min Pay ($)") },
                        placeholder = { Text("150.00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it },
                    label = { Text("Payment Due Day of Month (1-31)") },
                    placeholder = { Text("15") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DebtType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name.take(6), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balance = balanceText.toDoubleOrNull() ?: return@Button
                    val apr = aprText.toDoubleOrNull() ?: return@Button
                    val minPayment = minPaymentText.toDoubleOrNull() ?: return@Button
                    onAddDebt(name, balance, apr, minPayment, selectedType, dueDayInt)
                },
                enabled = isValid
            ) {
                Text("Add Debt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPaycheckDialog(
    currentConfig: PaycheckConfig,
    onDismiss: () -> Unit,
    onSave: (PaycheckConfig) -> Unit
) {
    var selectedFreq by remember { mutableStateOf(currentConfig.frequency) }
    var takeHomeText by remember { mutableStateOf(currentConfig.typicalTakeHomePay.toString()) }
    var anchorDate by remember {
        mutableStateOf(LocalDate.ofEpochDay(currentConfig.anchorPayDateEpochDay))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val isValid = (takeHomeText.toDoubleOrNull() ?: 0.0) > 0.0
    val dtf = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Paycheck Cushion") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "FinanceHome calculates specifically what to set aside from each paycheck so every bill is paid on time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(text = "Pay Frequency", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PayFrequency.values().forEach { freq ->
                        FilterChip(
                            selected = selectedFreq == freq,
                            onClick = { selectedFreq = freq },
                            label = { Text(freq.displayName) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = takeHomeText,
                    onValueChange = { takeHomeText = it },
                    label = { Text("Typical Take-Home Pay ($)") },
                    placeholder = { Text("1800.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Next (or Anchor) Payday",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Used to pinpoint which bills fall in each pay cycle and calculate 3-paycheck bonus months.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Selected Payday",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = anchorDate.format(dtf),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Pick Date",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Quick shortcuts
                val today = LocalDate.now()
                val nextFriday = today.plusDays(((5 - today.dayOfWeek.value + 7) % 7).toLong())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SuggestionChip(
                        onClick = { anchorDate = today },
                        label = { Text("Today") }
                    )
                    SuggestionChip(
                        onClick = { anchorDate = if (today.dayOfWeek.value == 5) today else nextFriday },
                        label = { Text("This Friday") }
                    )
                    SuggestionChip(
                        onClick = { anchorDate = nextFriday.plusDays(7) },
                        label = { Text("Next Friday") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pay = takeHomeText.toDoubleOrNull() ?: return@Button
                    onSave(
                        currentConfig.copy(
                            frequency = selectedFreq,
                            typicalTakeHomePay = pay,
                            anchorPayDateEpochDay = anchorDate.toEpochDay()
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = anchorDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            anchorDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wage & Hours Worked Estimator Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WageHoursEstimatorCard(
    wageConfig: WageConfig,
    wageEstimate: WageEstimate,
    paycheckConfig: PaycheckConfig,
    currencyFormat: NumberFormat,
    onEditWage: () -> Unit,
    onAdjustW1: (Double) -> Unit,
    onAdjustW2: (Double) -> Unit,
    onVerifyPayday: () -> Unit,
    onResetPeriod: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
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
                                text = "Paycheck planner synced with verified pay.",
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
                        Column {
                            Text(
                                text = "Estimated Take-Home Pay",
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
                                text = "Gross: ${currencyFormat.format(wageEstimate.grossPay)} • Est Deductions: -${currencyFormat.format(wageEstimate.estimatedDeductions)}",
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
            HoursRow(
                label = "Week 1",
                hours = wageConfig.week1Hours,
                onDelta = onAdjustW1
            )

            // If not weekly, show Week 2 Hours
            if (paycheckConfig.frequency != PayFrequency.WEEKLY) {
                HoursRow(
                    label = "Week 2",
                    hours = wageConfig.week2Hours,
                    onDelta = onAdjustW2
                )
            }
        }
    }
}

@Composable
fun HoursRow(
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
                    text = if (hours > 40.0) "40h regular @ 1x + ${String.format(Locale.US, "%.1f", hours - 40.0)}h @ 1.5x" else "Regular time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onDelta(-1.0) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("-1", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = { onDelta(1.0) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+1", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit Wage Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWageDialog(
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
    var week1Text by remember { mutableStateOf(currentConfig.week1Hours.toString()) }
    var week2Text by remember { mutableStateOf(currentConfig.week2Hours.toString()) }
    var autoSync by remember { mutableStateOf(currentConfig.autoSyncPaycheckPlanner) }

    val isValid = (hourlyWageText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (otMultiplierText.toDoubleOrNull() ?: -1.0) >= 1.0 &&
            (deductionsPercentText.toDoubleOrNull() ?: -1.0) in 0.0..100.0 &&
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
                    text = "Configure your hourly pay and tax withholding to automatically estimate your upcoming paycheck deposit.",
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

                Text(
                    text = "Logged Hours This Period:",
                    style = MaterialTheme.typography.labelMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = week1Text,
                        onValueChange = { week1Text = it },
                        label = { Text("Week 1 Hours") },
                        placeholder = { Text("40.0") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    if (frequency != PayFrequency.WEEKLY) {
                        OutlinedTextField(
                            value = week2Text,
                            onValueChange = { week2Text = it },
                            label = { Text("Week 2 Hours") },
                            placeholder = { Text("40.0") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-sync to Paycheck Planner",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Dynamically updates the paycheck cushion with estimated/verified wages.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wage = hourlyWageText.toDoubleOrNull() ?: return@Button
                    val ot = otMultiplierText.toDoubleOrNull() ?: 1.5
                    val ded = deductionsPercentText.toDoubleOrNull() ?: 18.0
                    val w1 = week1Text.toDoubleOrNull() ?: 40.0
                    val w2 = week2Text.toDoubleOrNull() ?: 40.0
                    onSave(
                        currentConfig.copy(
                            hourlyWage = wage,
                            overtimeMultiplier = ot,
                            estimatedDeductionPercent = ded,
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Verify Payday Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyPaydayDialog(
    estimatedAmount: Double,
    currentVerified: Double?,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val initialVal = currentVerified ?: estimatedAmount
    var actualAmountText by remember {
        mutableStateOf(if (initialVal > 0.0) String.format(Locale.US, "%.2f", initialVal) else "")
    }

    val actualAmount = actualAmountText.toDoubleOrNull() ?: 0.0
    val isValid = actualAmount > 0.0
    val diff = actualAmount - estimatedAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify Paycheck Deposit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Did your direct deposit land? Enter the exact net pay deposited into your bank account. This locks in your real numbers and updates your safe-to-spend budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Net Pay:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = currencyFormat.format(estimatedAmount),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = actualAmountText,
                    onValueChange = { actualAmountText = it },
                    label = { Text("Actual Net Pay Received ($)") },
                    placeholder = { Text("1650.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isValid && estimatedAmount > 0.0) {
                    val isHigher = diff >= 0
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isHigher) ColorIncome.copy(alpha = 0.12f) else ColorWarning.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isHigher) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (isHigher) ColorIncome else ColorWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (diff == 0.0) "Exact match with your estimate!"
                                else if (isHigher) "+${currencyFormat.format(diff)} higher than estimated (+bonus/lower tax)"
                                else "${currencyFormat.format(diff)} difference from estimate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(actualAmount) },
                enabled = isValid
            ) {
                Text("Confirm Deposit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DueDatesCalendarCard(
    bills: List<Bill>,
    debts: List<Debt>,
    paycheckConfig: PaycheckConfig,
    currencyFormat: NumberFormat,
    onToggleBillPaid: (Bill) -> Unit,
    onToggleDebtPaid: (Debt) -> Unit,
    modifier: Modifier = Modifier
) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val today = remember { LocalDate.now() }
    val displayedMonth = remember(monthOffset) { today.plusMonths(monthOffset.toLong()) }
    val year = displayedMonth.year
    val month = displayedMonth.monthValue
    val daysInMonth = displayedMonth.lengthOfMonth()

    // 0 = Sunday, 1 = Monday, ... 6 = Saturday
    val firstDayOfWeek = displayedMonth.withDayOfMonth(1).dayOfWeek.value % 7

    val anchorPayDate = remember(paycheckConfig.anchorPayDateEpochDay) {
        LocalDate.ofEpochDay(paycheckConfig.anchorPayDateEpochDay)
    }
    val payDays = remember(year, month, paycheckConfig) {
        PaycheckCalculator.getPayDatesForMonth(year, month, anchorPayDate, paycheckConfig.frequency)
    }

    val billDays = remember(bills) { bills.groupBy { it.dueDayOfMonth } }
    val debtDays = remember(debts) { debts.groupBy { it.dueDayOfMonth } }

    var selectedDay by remember { mutableStateOf<Int?>(today.dayOfMonth) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Month Navigation & Today Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { monthOffset-- },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                    }
                    Text(
                        text = displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = { monthOffset++ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                    }
                }

                if (monthOffset != 0) {
                    TextButton(
                        onClick = {
                            monthOffset = 0
                            selectedDay = today.dayOfMonth
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Today", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ColorIncome))
                    Text("Payday", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ColorWarning))
                    Text("Bill Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ColorExpense))
                    Text("Debt Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Day of Week Headers
            val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                dayHeaders.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Calendar Grid
            val totalCells = firstDayOfWeek + daysInMonth
            val totalRows = (totalCells + 6) / 7

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (row in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val day = cellIndex - firstDayOfWeek + 1
                            if (day in 1..daysInMonth) {
                                val isToday = (year == today.year && month == today.monthValue && day == today.dayOfMonth)
                                val isSelected = (selectedDay == day)
                                val isPayday = payDays.contains(day)
                                val hasBills = billDays.containsKey(day)
                                val hasDebts = debtDays.containsKey(day)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            selectedDay = if (selectedDay == day) null else day
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )

                                        // Indicator dots
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.height(4.dp)
                                        ) {
                                            if (isPayday) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ColorIncome)
                                                )
                                            }
                                            if (hasBills) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ColorWarning)
                                                )
                                            }
                                            if (hasDebts) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else ColorExpense)
                                                )
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
            }

            // Selected Day Details
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                        val dateFormatted = displayedMonth.withDayOfMonth(day)
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
                                Icon(
                                    Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = ColorIncome,
                                    modifier = Modifier.size(20.dp)
                                )
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
                                        text = "Debt Min • ${(debt.apr * 100).toInt()}% APR",
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
            } else {
                Text(
                    text = "Tap any date to inspect due obligations or payday schedules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun DebtPayoffOptimizerCard(
    comparison: MultiDebtComparison,
    currentExtra: Double,
    currencyFormat: NumberFormat,
    onExtraChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStrategy by remember { mutableStateOf(PayoffStrategy.AVALANCHE) }
    val activeResult = if (selectedStrategy == PayoffStrategy.AVALANCHE) {
        comparison.avalancheResult
    } else {
        comparison.snowballResult
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Debt Payoff Strategy Optimizer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Rollover momentum: paid debts roll into next target",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Extra Monthly Budget Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Extra Monthly Payoff Budget: ${currencyFormat.format(currentExtra)}/mo",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.0, 50.0, 100.0, 250.0, 500.0).forEach { amount ->
                        val isSelected = currentExtra == amount
                        FilterChip(
                            selected = isSelected,
                            onClick = { onExtraChanged(amount) },
                            label = {
                                Text(
                                    if (amount == 0.0) "$0" else "+$${amount.toInt()}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Strategy Tab Switcher (Avalanche vs Snowball)
            TabRow(
                selectedTabIndex = selectedStrategy.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedStrategy == PayoffStrategy.AVALANCHE,
                    onClick = { selectedStrategy = PayoffStrategy.AVALANCHE },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Debt Avalanche", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("Highest APR First", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                )
                Tab(
                    selected = selectedStrategy == PayoffStrategy.SNOWBALL,
                    onClick = { selectedStrategy = PayoffStrategy.SNOWBALL },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Debt Snowball", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("Lowest Balance First", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                )
            }

            // Outcome Hero Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
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
                        Column {
                            Text(
                                text = "DEBT-FREE DATE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.outline
                            )
                            val freeDateStr = if (activeResult.totalMonths > 0) {
                                formatPayoffDate(activeResult.debtFreeDateMillis)
                            } else "Never"
                            Text(
                                text = freeDateStr,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ColorIncome
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TOTAL TIME",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.outline
                            )
                            val timeStr = if (activeResult.totalMonths > 0) {
                                val yrs = activeResult.totalMonths / 12
                                val mos = activeResult.totalMonths % 12
                                if (yrs > 0) "${yrs}y ${mos}m" else "${mos} months"
                            } else "N/A"
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Total Interest Paid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormat.format(activeResult.totalInterestPaid),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Interest Saved vs Minimums",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+${currencyFormat.format(activeResult.totalInterestSavedVsMinimum)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorIncome
                                )
                            )
                        }
                    }
                }
            }

            // Payoff Order Milestones
            if (activeResult.milestones.isNotEmpty()) {
                Text(
                    text = "Payoff Sequence & Milestones:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeResult.milestones.forEachIndexed { idx, milestone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column {
                                    Text(
                                        text = milestone.debtName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Month ${milestone.payoffMonth} (${formatPayoffDate(milestone.payoffDateMillis)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Text(
                                text = "Interest: ${currencyFormat.format(milestone.totalInterestPaid)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Benchmark vs Minimums
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (comparison.minimumOnlyTotalMonths > 0) {
                        "Minimums only: ${comparison.minimumOnlyTotalMonths} months to finish, paying ${currencyFormat.format(comparison.minimumOnlyTotalInterest)} in interest."
                    } else {
                        "Minimums only: Payments don't cover interest on some debts (never debt-free without extra)."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
