package com.example.financehome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.Transaction
import com.example.financehome.ui.cashflow.CashFlowScreen
import com.example.financehome.ui.cashflow.CashFlowScreenTab
import com.example.financehome.ui.categorize.SwipeCategorizeScreen
import com.example.financehome.ui.categorize.SwipeCategorizeViewModel
import com.example.financehome.ui.csvimport.CsvImportScreen
import com.example.financehome.ui.csvimport.CsvImportViewModel
import com.example.financehome.ui.dashboard.DashboardScreen
import com.example.financehome.ui.dashboard.DashboardViewModel
import com.example.financehome.ui.debts.DebtDetailScreen
import com.example.financehome.ui.debts.DebtsScreen
import com.example.financehome.ui.debts.DebtsViewModel
import com.example.financehome.ui.opportunity.OpportunityScreen
import com.example.financehome.ui.opportunity.OpportunityViewModel
import com.example.financehome.ui.settings.ManageRulesScreen
import com.example.financehome.ui.settings.ManageRulesViewModel
import com.example.financehome.ui.settings.SettingsScreen
import com.example.financehome.ui.settings.SettingsViewModel
import com.example.financehome.ui.transactions.AddTransactionScreen
import com.example.financehome.ui.transactions.TransactionDetailSheet
import com.example.financehome.ui.transactions.TransactionsScreen
import com.example.financehome.ui.transactions.TransactionsViewModel
import java.text.NumberFormat
import java.util.Locale

enum class MainTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Dashboard),
    CASH_FLOW("Cash Flow", Icons.Default.CalendarMonth),
    DEBTS("Bills & Debt", Icons.Default.CreditCard),
    OPPORTUNITY("Invest", Icons.Default.TrendingUp),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    pendingTransactionId: String? = null,
    onClearPendingTransaction: () -> Unit = {},
    initialTabName: String? = null,
    onClearInitialTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var showAddTransactionScreen by remember { mutableStateOf(false) }
    var selectedDebtDetail by remember { mutableStateOf<Debt?>(null) }
    var showCsvImportScreen by remember { mutableStateOf(false) }
    var showSwipeCategorizeScreen by remember { mutableStateOf(false) }
    var showManageRulesScreen by remember { mutableStateOf(false) }
    var initialCashFlowTab by remember { mutableStateOf(CashFlowScreenTab.CASH_FLOW) }

    // Shared ViewModels (scoped to Activity lifecycle)
    val dashboardViewModel: DashboardViewModel = viewModel()
    val transactionsViewModel: TransactionsViewModel = viewModel()
    val debtsViewModel: DebtsViewModel = viewModel()
    val opportunityViewModel: OpportunityViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val csvImportViewModel: CsvImportViewModel = viewModel()
    val swipeCategorizeViewModel: SwipeCategorizeViewModel = viewModel()
    val manageRulesViewModel: ManageRulesViewModel = viewModel()

    val allTransactions by transactionsViewModel.transactions.collectAsState()
    var deepLinkedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Handle initial tab routing from notifications
    LaunchedEffect(initialTabName) {
        if (!initialTabName.isNullOrBlank()) {
            val target = MainTab.values().firstOrNull { it.name.equals(initialTabName, ignoreCase = true) }
            if (target != null) {
                selectedTab = target
                onClearInitialTab()
            }
        }
    }

    // Handle deep link from notification
    LaunchedEffect(pendingTransactionId, allTransactions) {
        if (!pendingTransactionId.isNullOrBlank()) {
            val match = allTransactions.firstOrNull { it.id == pendingTransactionId }
            if (match != null) {
                deepLinkedTransaction = match
                onClearPendingTransaction()
            }
        }
    }

    // Overlay full-screen sub-screens if active
    when {
        showSwipeCategorizeScreen -> {
            SwipeCategorizeScreen(
                viewModel = swipeCategorizeViewModel,
                onDone = { showSwipeCategorizeScreen = false }
            )
        }
        showManageRulesScreen -> {
            ManageRulesScreen(
                viewModel = manageRulesViewModel,
                onBack = { showManageRulesScreen = false }
            )
        }
        showAddTransactionScreen -> {
            val bills by transactionsViewModel.bills.collectAsState()
            val debts by transactionsViewModel.debts.collectAsState()
            AddTransactionScreen(
                onDismiss = { showAddTransactionScreen = false },
                onSaveTransaction = { amount, merchant, category, type, notes, isDiscretionary, receiptUri, receiptItems, noReceiptNeeded, linkedBillId, linkedDebtId ->
                    transactionsViewModel.addTransactionWithReceipt(
                        amount = amount,
                        merchant = merchant,
                        category = category,
                        type = type,
                        notes = notes,
                        isDiscretionary = isDiscretionary,
                        receiptImageUri = receiptUri,
                        receiptItems = receiptItems,
                        noReceiptNeeded = noReceiptNeeded,
                        linkedBillId = linkedBillId,
                        linkedDebtId = linkedDebtId
                    )
                },
                bills = bills,
                debts = debts,
                onCreateCaptureUri = { transactionsViewModel.createReceiptCaptureFile() },
                onSavePickedUri = { uri -> transactionsViewModel.savePickedImageToInternalStorage(uri) },
                onScanReceipt = { uriStr -> transactionsViewModel.scanReceiptOnly(uriStr) }
            )
        }
        selectedDebtDetail != null -> {
            DebtDetailScreen(
                debt = selectedDebtDetail!!,
                viewModel = debtsViewModel,
                onBack = { selectedDebtDetail = null }
            )
        }
        showCsvImportScreen -> {
            CsvImportScreen(
                viewModel = csvImportViewModel,
                onBack = { showCsvImportScreen = false }
            )
        }
        else -> {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        MainTab.values().forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                },
                modifier = modifier
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "MainTabsAnimation"
                    ) { targetTab ->
                        when (targetTab) {
                            MainTab.DASHBOARD -> DashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToAddTransaction = { showAddTransactionScreen = true },
                                onNavigateToDebts = { selectedTab = MainTab.DEBTS },
                                onNavigateToSwipeCategorize = { showSwipeCategorizeScreen = true },
                                onNavigateToCashFlow = { openTransactionsTab ->
                                    initialCashFlowTab = if (openTransactionsTab) CashFlowScreenTab.TRANSACTIONS else CashFlowScreenTab.CASH_FLOW
                                    selectedTab = MainTab.CASH_FLOW
                                }
                            )
                            MainTab.CASH_FLOW -> CashFlowScreen(
                                viewModel = debtsViewModel,
                                transactionsViewModel = transactionsViewModel,
                                onNavigateToAddTransaction = { showAddTransactionScreen = true },
                                initialTab = initialCashFlowTab
                            )
                            MainTab.DEBTS -> DebtsScreen(
                                viewModel = debtsViewModel,
                                onDebtSelected = { debt -> selectedDebtDetail = debt }
                            )
                            MainTab.OPPORTUNITY -> OpportunityScreen(
                                viewModel = opportunityViewModel
                            )
                            MainTab.SETTINGS -> SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateToCsvImport = { showCsvImportScreen = true },
                                onNavigateToManageRules = { showManageRulesScreen = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deepLinkedTransaction != null) {
        val currentTx = deepLinkedTransaction!!
        val receiptItems by transactionsViewModel.getReceiptItems(currentTx.id).collectAsState(initial = emptyList<ReceiptItem>())
        val isScanning by transactionsViewModel.isScanningReceipt.collectAsState()
        val bills by transactionsViewModel.bills.collectAsState()
        val debts by transactionsViewModel.debts.collectAsState()

        TransactionDetailSheet(
            transaction = currentTx,
            receiptItems = receiptItems,
            isScanning = isScanning,
            currencyFormat = NumberFormat.getCurrencyInstance(Locale.US),
            bills = bills,
            debts = debts,
            onDismiss = { deepLinkedTransaction = null },
            onUpdateCategory = { newCategory ->
                transactionsViewModel.updateTransactionCategory(currentTx, newCategory)
                deepLinkedTransaction = null
            },
            onToggleDiscretionary = { isDisc ->
                transactionsViewModel.toggleDiscretionary(currentTx, isDisc)
                deepLinkedTransaction = deepLinkedTransaction?.copy(isDiscretionary = isDisc)
            },
            onDelete = {
                transactionsViewModel.deleteTransaction(currentTx)
                deepLinkedTransaction = null
            },
            onCreateCaptureUri = { transactionsViewModel.createReceiptCaptureFile() },
            onSavePickedUri = { uri -> transactionsViewModel.savePickedImageToInternalStorage(uri) },
            onAttachReceipt = { uriStr -> transactionsViewModel.attachReceipt(currentTx.id, uriStr) },
            onAddItem = { item -> transactionsViewModel.addReceiptItem(item) },
            onUpdateItem = { item -> transactionsViewModel.updateReceiptItem(item) },
            onDeleteItem = { item -> transactionsViewModel.deleteReceiptItem(item) },
            onRemoveReceipt = { transactionsViewModel.removeReceipt(currentTx.id) },
            onToggleNoReceipt = {
                val newNoReceipt = !currentTx.noReceiptNeeded
                transactionsViewModel.markNoReceiptNeeded(currentTx.id, newNoReceipt)
                deepLinkedTransaction = currentTx.copy(noReceiptNeeded = newNoReceipt)
            },
            onLinkToBill = { billId ->
                transactionsViewModel.linkTransactionToBill(currentTx.id, billId)
                deepLinkedTransaction = currentTx.copy(
                    linkedBillId = billId,
                    linkedDebtId = null,
                    isDiscretionary = false,
                    noReceiptNeeded = true
                )
            },
            onLinkToDebt = { debtId, amount ->
                transactionsViewModel.linkTransactionToDebt(currentTx.id, debtId, amount)
                deepLinkedTransaction = currentTx.copy(
                    linkedDebtId = debtId,
                    linkedBillId = null,
                    isDiscretionary = false,
                    noReceiptNeeded = true
                )
            },
            onUnlinkObligation = {
                transactionsViewModel.unlinkTransactionObligation(currentTx.id)
                deepLinkedTransaction = currentTx.copy(
                    linkedBillId = null,
                    linkedDebtId = null
                )
            }
        )
    }
}
