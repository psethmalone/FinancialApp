package com.example.financehome.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.financehome.data.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onDismiss: () -> Unit,
    onSaveTransaction: (
        amount: Double,
        merchant: String,
        category: String,
        type: TransactionType,
        notes: String?,
        isDiscretionary: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.DEBIT) }
    var selectedCategory by remember { mutableStateOf(PREDEFINED_CATEGORIES.first()) }
    var notesText by remember { mutableStateOf("") }
    var isDiscretionary by remember { mutableStateOf(true) }

    val isValid = amountText.toDoubleOrNull() != null &&
            (amountText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            merchantText.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: return@IconButton
                            onSaveTransaction(
                                amount,
                                merchantText,
                                selectedCategory,
                                selectedType,
                                notesText,
                                isDiscretionary
                            )
                            onDismiss()
                        },
                        enabled = isValid
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Transaction Type Toggle (Debit vs Credit)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedType == TransactionType.DEBIT,
                    onClick = {
                        selectedType = TransactionType.DEBIT
                        isDiscretionary = true
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Expense (Debit)")
                }
                SegmentedButton(
                    selected = selectedType == TransactionType.CREDIT,
                    onClick = {
                        selectedType = TransactionType.CREDIT
                        isDiscretionary = false
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Deposit (Income)")
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amountText = input
                    }
                },
                label = { Text("Amount ($)") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Merchant / Description
            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { Text("Merchant / Source") },
                placeholder = { Text("e.g. McDonald's, Starbucks, Ally Interest") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PREDEFINED_CATEGORIES) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // Discretionary toggle (only for Debit)
            if (selectedType == TransactionType.DEBIT) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Discretionary Spending?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Mark this if it's optional spending (coffee, eating out, entertainment) that you could have invested into an index fund instead.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isDiscretionary,
                            onCheckedChange = { isDiscretionary = it }
                        )
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Notes (Optional)") },
                placeholder = { Text("Add any extra details...") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Save Button
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    onSaveTransaction(
                        amount,
                        merchantText,
                        selectedCategory,
                        selectedType,
                        notesText,
                        isDiscretionary
                    )
                    onDismiss()
                },
                enabled = isValid,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Transaction", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
