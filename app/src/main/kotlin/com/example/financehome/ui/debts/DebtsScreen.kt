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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import com.example.financehome.domain.DebtPayoffMilestone
import com.example.financehome.domain.MultiDebtComparison
import com.example.financehome.domain.PayoffStrategy
import com.example.financehome.domain.StrategyPayoffResult
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import com.example.financehome.ui.theme.ColorWarning
import java.text.NumberFormat
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
    val multiDebtComparison by viewModel.multiDebtComparison.collectAsState()
    val extraMonthlyPayoffBudget by viewModel.extraMonthlyPayoffBudget.collectAsState()

    var selectedTab by remember { mutableStateOf(ObligationTab.BILLS) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var showAddBillDialog by remember { mutableStateOf(false) }
    var debtToEdit by remember { mutableStateOf<Debt?>(null) }
    var debtToPay by remember { mutableStateOf<Debt?>(null) }
    var billToPay by remember { mutableStateOf<Bill?>(null) }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills & Debt", fontWeight = FontWeight.Bold) },
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
                            onTogglePaid = {
                                if (bill.isPaidThisMonth) {
                                    viewModel.toggleBillPaid(bill)
                                } else {
                                    billToPay = bill
                                }
                            },
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
                            onClick = { onDebtSelected(debt) },
                            onEdit = { debtToEdit = debt },
                            onPay = { debtToPay = debt }
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

    if (billToPay != null) {
        ConfirmPayBillDialog(
            bill = billToPay!!,
            currencyFormat = currencyFormat,
            onDismiss = { billToPay = null },
            onConfirm = {
                viewModel.payBill(billToPay!!)
                billToPay = null
            }
        )
    }

    if (debtToEdit != null) {
        EditDebtDialog(
            debt = debtToEdit!!,
            onDismiss = { debtToEdit = null },
            onSave = { name, curBal, origBal, apr, minPay, dueDay, type ->
                viewModel.editDebt(debtToEdit!!.id, name, curBal, origBal, apr, minPay, dueDay, type)
                debtToEdit = null
            }
        )
    }

    if (debtToPay != null) {
        PayDebtDialog(
            debt = debtToPay!!,
            currencyFormat = currencyFormat,
            onDismiss = { debtToPay = null },
            onConfirmPayment = { amount ->
                viewModel.payDebt(debtToPay!!, amount)
                debtToPay = null
            }
        )
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
                        color = if (bill.isPaidThisMonth) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = currencyFormat.format(bill.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (bill.isPaidThisMonth) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End
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
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPay: () -> Unit
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = debt.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = debt.type.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (debt.isPaidThisMonth) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ColorIncome.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Paid this cycle",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = ColorIncome,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = currencyFormat.format(debt.currentBalance),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (debt.currentBalance == 0.0) ColorIncome else ColorExpense
                    ),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp)
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
                        ),
                        maxLines = 1,
                        softWrap = false
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
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onPay,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pay Debt", style = MaterialTheme.typography.labelSmall)
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
                categories.chunked(2).forEach { rowCats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCats.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
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

                Text(text = "Debt Type", style = MaterialTheme.typography.labelMedium)
                listOf(
                    listOf(DebtType.CREDIT_CARD to "Credit Card", DebtType.LOAN to "Loan"),
                    listOf(DebtType.MORTGAGE to "Mortgage", DebtType.OTHER to "Other")
                ).forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTypes.forEach { (type, label) ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
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

// ─────────────────────────────────────────────────────────────────────────────
// Bill & Debt Payment & Edit Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConfirmPayBillDialog(
    bill: Bill,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay ${bill.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Marking this bill as paid will record a ${currencyFormat.format(bill.amount)} expense transaction and deduct it from your current cycle's available funds."
                )
                Text(
                    text = "Category: ${bill.category} • Due Day ${bill.dueDayOfMonth}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Pay ${currencyFormat.format(bill.amount)}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDebtDialog(
    debt: Debt,
    onDismiss: () -> Unit,
    onSave: (name: String, currentBalance: Double, originalBalance: Double, aprPercent: Double, minPayment: Double, dueDay: Int, type: DebtType) -> Unit
) {
    var name by remember { mutableStateOf(debt.name) }
    var currentBalanceText by remember { mutableStateOf(String.format(Locale.US, "%.2f", debt.currentBalance)) }
    var originalBalanceText by remember { mutableStateOf(String.format(Locale.US, "%.2f", debt.originalBalance)) }
    var aprText by remember { mutableStateOf(String.format(Locale.US, "%.2f", debt.apr * 100.0)) }
    var minPaymentText by remember { mutableStateOf(String.format(Locale.US, "%.2f", debt.minimumPayment)) }
    var dueDayText by remember { mutableStateOf(debt.dueDayOfMonth.toString()) }
    var selectedType by remember { mutableStateOf(debt.type) }

    val dueDayInt = dueDayText.toIntOrNull() ?: debt.dueDayOfMonth
    val isValid = name.isNotBlank() &&
            (currentBalanceText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (originalBalanceText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (aprText.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (minPaymentText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            dueDayInt in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Debt Account") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Debt Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentBalanceText,
                        onValueChange = { currentBalanceText = it },
                        label = { Text("Current Bal ($)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = originalBalanceText,
                        onValueChange = { originalBalanceText = it },
                        label = { Text("Orig Bal ($)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = aprText,
                        onValueChange = { aprText = it },
                        label = { Text("APR %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = minPaymentText,
                        onValueChange = { minPaymentText = it },
                        label = { Text("Min Pay ($)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it },
                    label = { Text("Due Day of Month (1-31)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Debt Type", style = MaterialTheme.typography.labelMedium)
                listOf(
                    listOf(DebtType.CREDIT_CARD to "Credit Card", DebtType.LOAN to "Loan"),
                    listOf(DebtType.MORTGAGE to "Mortgage", DebtType.OTHER to "Other")
                ).forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTypes.forEach { (type, label) ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val curBal = currentBalanceText.toDoubleOrNull() ?: return@Button
                    val origBal = originalBalanceText.toDoubleOrNull() ?: curBal
                    val apr = aprText.toDoubleOrNull() ?: return@Button
                    val minPayment = minPaymentText.toDoubleOrNull() ?: return@Button
                    onSave(name, curBal, origBal, apr, minPayment, dueDayInt, selectedType)
                },
                enabled = isValid
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PayDebtDialog(
    debt: Debt,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirmPayment: (paymentAmount: Double) -> Unit
) {
    var paymentAmountText by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", debt.minimumPayment))
    }

    val paymentAmount = paymentAmountText.toDoubleOrNull() ?: 0.0
    val newBalance = maxOf(0.0, debt.currentBalance - paymentAmount)
    val isValid = paymentAmount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Payment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Pay ${debt.name}")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Balance:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                currencyFormat.format(debt.currentBalance),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Monthly Minimum:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                currencyFormat.format(debt.minimumPayment),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Text(
                    text = "Quick Payment Options",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                // Quick Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = paymentAmount == debt.minimumPayment,
                            onClick = {
                                paymentAmountText = String.format(Locale.US, "%.2f", debt.minimumPayment)
                            },
                            label = {
                                Text(
                                    text = "Minimum (${currencyFormat.format(debt.minimumPayment)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentAmount == debt.minimumPayment + 25.0,
                            onClick = {
                                paymentAmountText = String.format(Locale.US, "%.2f", debt.minimumPayment + 25.0)
                            },
                            label = {
                                Text(
                                    text = "+$25 Extra",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = paymentAmount == debt.minimumPayment + 50.0,
                            onClick = {
                                paymentAmountText = String.format(Locale.US, "%.2f", debt.minimumPayment + 50.0)
                            },
                            label = {
                                Text(
                                    text = "+$50 Extra",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentAmount == debt.minimumPayment + 100.0,
                            onClick = {
                                paymentAmountText = String.format(Locale.US, "%.2f", debt.minimumPayment + 100.0)
                            },
                            label = {
                                Text(
                                    text = "+$100 Extra",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = paymentAmount == debt.currentBalance,
                            onClick = {
                                paymentAmountText = String.format(Locale.US, "%.2f", debt.currentBalance)
                            },
                            label = {
                                Text(
                                    text = "Full Balance (${currencyFormat.format(debt.currentBalance)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Custom Amount Input
                OutlinedTextField(
                    value = paymentAmountText,
                    onValueChange = { paymentAmountText = it },
                    label = { Text("Payment Amount ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Remaining Balance Preview
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New Remaining Balance:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = currencyFormat.format(newBalance),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (newBalance == 0.0) ColorIncome else MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Text(
                    text = "Deducts this payment from your cash flow & records a transaction.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onConfirmPayment(paymentAmount)
                    }
                },
                enabled = isValid
            ) {
                Text("Confirm & Pay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
