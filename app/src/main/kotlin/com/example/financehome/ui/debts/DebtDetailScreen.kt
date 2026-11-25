package com.example.financehome.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financehome.data.model.Debt
import com.example.financehome.domain.AmortizationRow
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    debt: Debt,
    viewModel: DebtsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var extraMonthlyPayment by remember { mutableStateOf(50.0) }
    var showAllAmortization by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }

    val simulation = remember(debt, extraMonthlyPayment) {
        viewModel.simulatePayoff(debt, extraMonthlyPayment)
    }

    val baseSimulation = remember(debt) {
        viewModel.simulatePayoff(debt, 0.0)
    }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(debt.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Debt")
                    }
                    IconButton(onClick = {
                        viewModel.deleteDebt(debt)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Debt", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
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
                // Debt Overview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Balance", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                text = currencyFormat.format(debt.currentBalance),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorExpense
                                ),
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Annual APR", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                text = String.format(Locale.US, "%.2f%%", debt.apr * 100.0),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Minimum Monthly Payment", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                text = currencyFormat.format(debt.minimumPayment),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Account")
                            }
                            Button(
                                onClick = { showPayDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pay Debt")
                            }
                        }
                    }
                }
            }

            // Timeline & Extra Principal Calculator
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Extra Principal Payment Simulator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "Additional monthly payment: +${currencyFormat.format(extraMonthlyPayment)}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Slider(
                            value = extraMonthlyPayment.toFloat(),
                            onValueChange = { extraMonthlyPayment = (it / 10).roundToInt() * 10.0 },
                            valueRange = 0f..500f,
                            steps = 49
                        )

                        Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                        // Payoff Timeline Comparison
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Payoff Date",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (simulation.monthsToPayoff < 0) "Never (Payment < Interest)"
                                    else formatPayoffDate(simulation.payoffDateMillis),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (simulation.monthsSaved > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = ColorIncome,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "${simulation.monthsSaved} months sooner!",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = ColorIncome
                                            )
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Interest",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = currencyFormat.format(simulation.totalInterest),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (simulation.interestSaved > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Savings,
                                            contentDescription = null,
                                            tint = ColorIncome,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Save ${currencyFormat.format(simulation.interestSaved)}",
                                            style = MaterialTheme.typography.bodySmall.copy(
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

            // Amortization Schedule Table Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Amortization Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = { showAllAmortization = !showAllAmortization }) {
                        Text(if (showAllAmortization) "Show Less" else "Show All (${simulation.amortizationRows.size} mo)")
                    }
                }
            }

            // Table Columns Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mo", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.7f))
                    Text("Payment", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.3f))
                    Text("Principal", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.3f))
                    Text("Interest", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.3f))
                    Text("Balance", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.4f))
                }
            }

            val displayedRows = if (showAllAmortization) simulation.amortizationRows
            else simulation.amortizationRows.take(12)

            items(displayedRows) { row ->
                AmortizationTableRow(row = row, currencyFormat = currencyFormat)
            }

        }
    }

    if (showEditDialog) {
        EditDebtDialog(
            debt = debt,
            onDismiss = { showEditDialog = false },
            onSave = { name, curBal, origBal, apr, minPay, dueDay, type ->
                viewModel.editDebt(debt.id, name, curBal, origBal, apr, minPay, dueDay, type)
                showEditDialog = false
            }
        )
    }

    if (showPayDialog) {
        PayDebtDialog(
            debt = debt,
            currencyFormat = currencyFormat,
            onDismiss = { showPayDialog = false },
            onConfirmPayment = { amount ->
                viewModel.payDebt(debt, amount)
                showPayDialog = false
            }
        )
    }
}

@Composable
fun AmortizationTableRow(
    row: AmortizationRow,
    currencyFormat: NumberFormat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${row.month}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            text = currencyFormat.format(row.payment),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = currencyFormat.format(row.principal),
            style = MaterialTheme.typography.bodySmall.copy(color = ColorIncome),
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = currencyFormat.format(row.interest),
            style = MaterialTheme.typography.bodySmall.copy(color = ColorExpense),
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = currencyFormat.format(row.remainingBalance),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1.4f)
        )
    }
}

fun formatPayoffDate(millis: Long): String {
    if (millis == Long.MAX_VALUE) return "Never"
    val instant = Instant.ofEpochMilli(millis)
    val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return dateTime.format(DateTimeFormatter.ofPattern("MMM yyyy"))
}
