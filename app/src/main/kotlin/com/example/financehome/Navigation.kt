package com.example.financehome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.example.financehome.ui.transactions.TransactionsScreen
import com.example.financehome.ui.transactions.TransactionsViewModel

enum class MainTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Dashboard),
    TRANSACTIONS("Activity", Icons.Default.ReceiptLong),
    DEBTS("Bills & Debt", Icons.Default.CreditCard),
    OPPORTUNITY("Invest", Icons.Default.TrendingUp),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var showAddTransactionScreen by remember { mutableStateOf(false) }
    var selectedDebtDetail by remember { mutableStateOf<Debt?>(null) }
    var showCsvImportScreen by remember { mutableStateOf(false) }
    var showSwipeCategorizeScreen by remember { mutableStateOf(false) }
    var showManageRulesScreen by remember { mutableStateOf(false) }

    // Shared ViewModels (scoped to Activity lifecycle)
    val dashboardViewModel: DashboardViewModel = viewModel()
    val transactionsViewModel: TransactionsViewModel = viewModel()
    val debtsViewModel: DebtsViewModel = viewModel()
    val opportunityViewModel: OpportunityViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val csvImportViewModel: CsvImportViewModel = viewModel()
    val swipeCategorizeViewModel: SwipeCategorizeViewModel = viewModel()
    val manageRulesViewModel: ManageRulesViewModel = viewModel()

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
            AddTransactionScreen(
                onDismiss = { showAddTransactionScreen = false },
                onSaveTransaction = { amount, merchant, category, type, notes, isDiscretionary ->
                    transactionsViewModel.addManualTransaction(
                        amount = amount,
                        merchant = merchant,
                        category = category,
                        type = type,
                        notes = notes,
                        isDiscretionary = isDiscretionary
                    )
                }
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
                                onNavigateToTransactions = { selectedTab = MainTab.TRANSACTIONS },
                                onNavigateToDebts = { selectedTab = MainTab.DEBTS },
                                onNavigateToSwipeCategorize = { showSwipeCategorizeScreen = true }
                            )
                            MainTab.TRANSACTIONS -> TransactionsScreen(
                                viewModel = transactionsViewModel,
                                onNavigateToAddTransaction = { showAddTransactionScreen = true }
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
}
