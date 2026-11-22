package com.example.financehome.ui.transactions

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.ui.dashboard.formatTimestamp
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import java.text.NumberFormat
import java.util.Locale

val PREDEFINED_CATEGORIES = listOf(
    "Food & Dining",
    "Groceries",
    "Entertainment",
    "Shopping",
    "Gas & Fuel",
    "Bills & Utilities",
    "Rent/Mortgage",
    "Transfers",
    "Income",
    "Uncategorized"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateToAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val transactions by viewModel.filteredTransactions.collectAsState()

    var selectedTransactionForEdit by remember { mutableStateOf<Transaction?>(null) }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToAddTransaction) {
                        Icon(Icons.Default.Add, contentDescription = "Add Transaction")
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Search by merchant or category...") },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
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

            // Transactions List
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedCategory != null)
                                "No transactions match your filter"
                            else "No transactions recorded yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionRowItem(
                            transaction = transaction,
                            currencyFormat = currencyFormat,
                            onClick = { selectedTransactionForEdit = transaction }
                        )
                    }
                }
            }
        }
    }

    // Edit Transaction Bottom Sheet
    selectedTransactionForEdit?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction,
            currencyFormat = currencyFormat,
            onDismiss = { selectedTransactionForEdit = null },
            onUpdateCategory = { newCategory ->
                viewModel.updateTransactionCategory(transaction, newCategory)
                selectedTransactionForEdit = null
            },
            onToggleDiscretionary = { isDiscretionary ->
                viewModel.toggleDiscretionary(transaction, isDiscretionary)
                selectedTransactionForEdit = null
            },
            onDelete = {
                viewModel.deleteTransaction(transaction)
                selectedTransactionForEdit = null
            }
        )
    }
}

@Composable
fun TransactionRowItem(
    transaction: Transaction,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    val isDebit = transaction.type == TransactionType.DEBIT
    val amountFormatted = (if (isDebit) "-" else "+") + currencyFormat.format(transaction.amount)
    val dateFormatted = formatTimestamp(transaction.timestampMillis)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

                Column {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (transaction.isDiscretionary && isDebit) {
                            Text(
                                text = "• Discretionary",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            text = "• $dateFormatted",
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
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: Transaction,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onUpdateCategory: (String) -> Unit,
    onToggleDiscretionary: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = currencyFormat.format(transaction.amount),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == TransactionType.DEBIT) ColorExpense else ColorIncome
                    )
                )
            }

            Divider()

            // Discretionary Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Discretionary Spending",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Qualifies for 'What if I invested' tracker (eating out, frivolous spends, non-obligations)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = transaction.isDiscretionary,
                    onCheckedChange = { onToggleDiscretionary(it) }
                )
            }

            Divider()

            // Change Category Dropdown / Picker
            Text(
                text = "Change Category",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PREDEFINED_CATEGORIES) { cat ->
                    FilterChip(
                        selected = transaction.category == cat,
                        onClick = { onUpdateCategory(cat) },
                        label = { Text(cat) }
                    )
                }
            }

            if (!transaction.notes.isNullOrBlank()) {
                Text(
                    text = "Notes: ${transaction.notes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Delete Button
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Transaction")
            }
        }
    }
}
