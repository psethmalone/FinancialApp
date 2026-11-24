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
import com.example.financehome.domain.PayFrequency
import com.example.financehome.domain.PaycheckBreakdown
import com.example.financehome.domain.PaycheckConfig
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

    var selectedTab by remember { mutableStateOf(ObligationTab.BILLS) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var showAddBillDialog by remember { mutableStateOf(false) }
    var showEditPaycheckDialog by remember { mutableStateOf(false) }

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

            // Paycheck-to-Bill Cushion Allocator Card
            item {
                PaycheckPlannerCard(
                    breakdown = paycheckBreakdown,
                    config = paycheckConfig,
                    currencyFormat = currencyFormat,
                    onEditPaycheck = { showEditPaycheckDialog = true },
                    onToggleBillPaid = { viewModel.toggleBillPaid(it) }
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
            onAddDebt = { name, balance, apr, minPayment, type ->
                viewModel.addDebt(name, balance, apr, minPayment, type)
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
    onToggleBillPaid: (Bill) -> Unit = {}
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
                            text = currencyFormat.format(breakdown.windowBillsTotal),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Bills due before $nextPayDateStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                            text = "Steady Bills Reserve",
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
                // Peak rent/bill cycle
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
                            text = "Peak Bill Cycle: ${currencyFormat.format(breakdown.windowBillsTotal)} in bills due before next check. If your Bills Account has a ${currencyFormat.format(breakdown.recommendedBuffer)} cushion, your steady ${currencyFormat.format(breakdown.perPaycheckReserve)} is safe. Otherwise, set aside ${currencyFormat.format(breakdown.windowBillsTotal)} so nothing bounces!",
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
                            text = "Light Bill Cycle: Only ${currencyFormat.format(breakdown.windowBillsTotal)} in bills due. Transferring your steady ${currencyFormat.format(breakdown.perPaycheckReserve)} builds your cushion for peak rent weeks!",
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

            // Upcoming Bills in this Pay Cycle
            if (breakdown.billsDueInPeriod.isNotEmpty()) {
                Text(
                    text = "Bills Due This Pay Cycle (${breakdown.billsDueInPeriod.size}):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    breakdown.billsDueInPeriod.forEach { item ->
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = item.isPaid,
                                        onCheckedChange = { onToggleBillPaid(item.bill) },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = item.bill.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                textDecoration = if (item.isPaid) TextDecoration.LineThrough else null
                                            )
                                        )
                                        Text(
                                            text = "Due ${item.dueDate.format(dtf)} • ${item.bill.category}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = currencyFormat.format(item.bill.amount),
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
                                    text = "${currencyFormat.format(period.totalBills)} bills",
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
                    Text(
                        text = debt.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
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
    onAddDebt: (name: String, balance: Double, apr: Double, minPayment: Double, type: DebtType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var aprText by remember { mutableStateOf("") }
    var minPaymentText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DebtType.CREDIT_CARD) }

    val isValid = name.isNotBlank() &&
            (balanceText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (aprText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (minPaymentText.toDoubleOrNull() ?: 0.0) > 0.0

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
                    onAddDebt(name, balance, apr, minPayment, selectedType)
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
