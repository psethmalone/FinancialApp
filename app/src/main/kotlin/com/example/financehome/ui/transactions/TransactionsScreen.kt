package com.example.financehome.ui.transactions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.ReceiptItemCategory
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.domain.ObligationType
import com.example.financehome.ui.dashboard.formatTimestamp
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

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
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()

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
        val receiptItems by viewModel.getReceiptItems(transaction.id).collectAsState(initial = emptyList<ReceiptItem>())
        val isScanning by viewModel.isScanningReceipt.collectAsState()

        TransactionDetailSheet(
            transaction = transaction,
            receiptItems = receiptItems,
            isScanning = isScanning,
            currencyFormat = currencyFormat,
            bills = bills,
            debts = debts,
            onDismiss = { selectedTransactionForEdit = null },
            onUpdateCategory = { newCategory ->
                viewModel.updateTransactionCategory(transaction, newCategory)
                selectedTransactionForEdit = null
            },
            onToggleDiscretionary = { isDiscretionary ->
                viewModel.toggleDiscretionary(transaction, isDiscretionary)
                selectedTransactionForEdit = selectedTransactionForEdit?.copy(isDiscretionary = isDiscretionary)
            },
            onDelete = {
                viewModel.deleteTransaction(transaction)
                selectedTransactionForEdit = null
            },
            onCreateCaptureUri = { viewModel.createReceiptCaptureFile() },
            onSavePickedUri = { uri -> viewModel.savePickedImageToInternalStorage(uri) },
            onAttachReceipt = { uriStr -> viewModel.attachReceipt(transaction.id, uriStr) },
            onAddItem = { item -> viewModel.addReceiptItem(item) },
            onUpdateItem = { item -> viewModel.updateReceiptItem(item) },
            onDeleteItem = { item -> viewModel.deleteReceiptItem(item) },
            onRemoveReceipt = { viewModel.removeReceipt(transaction.id) },
            onToggleNoReceipt = {
                val newNoReceipt = !transaction.noReceiptNeeded
                viewModel.markNoReceiptNeeded(transaction.id, newNoReceipt)
                selectedTransactionForEdit = transaction.copy(noReceiptNeeded = newNoReceipt)
            },
            onLinkToBill = { billId ->
                viewModel.linkTransactionToBill(transaction.id, billId)
                selectedTransactionForEdit = transaction.copy(
                    linkedBillId = billId,
                    linkedDebtId = null,
                    isDiscretionary = false,
                    noReceiptNeeded = true
                )
            },
            onLinkToDebt = { debtId, amount ->
                viewModel.linkTransactionToDebt(transaction.id, debtId, amount)
                selectedTransactionForEdit = transaction.copy(
                    linkedDebtId = debtId,
                    linkedBillId = null,
                    isDiscretionary = false,
                    noReceiptNeeded = true
                )
            },
            onUnlinkObligation = {
                viewModel.unlinkTransactionObligation(transaction.id)
                selectedTransactionForEdit = transaction.copy(
                    linkedBillId = null,
                    linkedDebtId = null
                )
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
    val hasReceipt = transaction.receiptImageUri != null || transaction.hasItemizedReceipt

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
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasReceipt) {
                            Text(
                                text = "• 🧾 Receipt",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else if (transaction.noReceiptNeeded) {
                            Text(
                                text = "• No Receipt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (transaction.linkedBillId != null) {
                            Text(
                                text = "• 📄 Bill",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (transaction.linkedDebtId != null) {
                            Text(
                                text = "• 💳 Debt",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
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
                ),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp)
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
    onDelete: () -> Unit,
    receiptItems: List<ReceiptItem> = emptyList(),
    isScanning: Boolean = false,
    bills: List<Bill> = emptyList(),
    debts: List<Debt> = emptyList(),
    onCreateCaptureUri: (() -> Pair<File, Uri>)? = null,
    onSavePickedUri: (suspend (Uri) -> String)? = null,
    onAttachReceipt: ((String) -> Unit)? = null,
    onAddItem: ((ReceiptItem) -> Unit)? = null,
    onUpdateItem: ((ReceiptItem) -> Unit)? = null,
    onDeleteItem: ((ReceiptItem) -> Unit)? = null,
    onRemoveReceipt: (() -> Unit)? = null,
    onToggleNoReceipt: (() -> Unit)? = null,
    onLinkToBill: ((String) -> Unit)? = null,
    onLinkToDebt: ((String, Double) -> Unit)? = null,
    onUnlinkObligation: (() -> Unit)? = null
) {
    var showZoomImage by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ReceiptItem?>(null) }
    var tempCameraFileUri by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFileUri != null) {
            onAttachReceipt?.invoke(tempCameraFileUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && onSavePickedUri != null) {
            coroutineScope.launch {
                val localUri = onSavePickedUri(uri)
                onAttachReceipt?.invoke(localUri)
            }
        }
    }

    fun launchCamera() {
        if (onCreateCaptureUri != null) {
            val (file, contentUri) = onCreateCaptureUri()
            tempCameraFileUri = Uri.fromFile(file).toString()
            cameraLauncher.launch(contentUri)
        }
    }

    fun launchGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Receipt category calculations
    val groceryTotal = receiptItems.filter { it.category == ReceiptItemCategory.GROCERY }.sumOf { it.price }
    val fuelItems = receiptItems.filter { it.category == ReceiptItemCategory.FUEL }
    val fuelTotal = fuelItems.sumOf { it.price }
    val householdTotal = receiptItems.filter { it.category == ReceiptItemCategory.HOUSEHOLD }.sumOf { it.price }
    val taxTotal = receiptItems.filter { it.category == ReceiptItemCategory.TAX }.sumOf { it.price }
    val otherTotal = receiptItems.filter {
        it.category != ReceiptItemCategory.GROCERY &&
        it.category != ReceiptItemCategory.FUEL &&
        it.category != ReceiptItemCategory.HOUSEHOLD &&
        it.category != ReceiptItemCategory.TAX
    }.sumOf { it.price }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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

            HorizontalDivider()

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

            HorizontalDivider()

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

            HorizontalDivider()

            // Obligation Link Section
            Text(
                text = "Bill / Debt Obligation Link",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )

            if (transaction.linkedBillId != null) {
                val linkedBill = bills.find { it.id == transaction.linkedBillId }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Linked to Bill",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = linkedBill?.name ?: "Bill (${transaction.linkedBillId})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        if (onUnlinkObligation != null) {
                            TextButton(onClick = onUnlinkObligation) {
                                Text("Unlink", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else if (transaction.linkedDebtId != null) {
                val linkedDebt = debts.find { it.id == transaction.linkedDebtId }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Linked to Debt",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = linkedDebt?.name ?: "Debt (${transaction.linkedDebtId})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        if (onUnlinkObligation != null) {
                            TextButton(onClick = onUnlinkObligation) {
                                Text("Unlink", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showLinkDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tag to Bill or Debt", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ---------------------------------------------------------------
            // RECEIPT & ITEMIZATION SECTION
            // ---------------------------------------------------------------
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Receipt & Items",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (transaction.hasItemizedReceipt || receiptItems.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${receiptItems.size} items",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Camera & Gallery actions
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { launchCamera() },
                        enabled = !isScanning && onCreateCaptureUri != null
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Take Photo")
                    }
                    IconButton(
                        onClick = { launchGallery() },
                        enabled = !isScanning && onSavePickedUri != null
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick Photo")
                    }
                }
            }

            // Scanning indicator card
            if (isScanning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Scanning receipt with ML Kit...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Text(
                            text = "Extracting line items, fuel details, and calculating category breakdowns...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Receipt image thumbnail & controls (if image attached)
            if (transaction.receiptImageUri != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showZoomImage = true }
                        ) {
                            AsyncImage(
                                model = transaction.receiptImageUri,
                                contentDescription = "Receipt photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = "Zoom",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp).padding(2.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Receipt Attached",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Tap thumbnail to inspect full receipt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { launchCamera() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("Retake", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = { onRemoveReceipt?.invoke() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            } else if (!isScanning && receiptItems.isEmpty()) {
                // Empty banner prompting to attach or indicating no receipt needed
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = if (transaction.noReceiptNeeded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (transaction.noReceiptNeeded) "No receipt needed" else "No receipt attached",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (transaction.noReceiptNeeded)
                                    "This transaction is excluded from missing receipt reminders."
                                else
                                    "Snap a picture or choose from gallery to itemize groceries, fuel, and expenses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (onToggleNoReceipt != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = onToggleNoReceipt,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (transaction.noReceiptNeeded) Icons.Default.Check else Icons.Default.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (transaction.noReceiptNeeded) "Require Receipt Again" else "Mark as No Receipt Needed",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Category Breakdown Badges (Groceries, Fuel, Household, etc.)
            if (receiptItems.isNotEmpty()) {
                Text(
                    text = "Category Summary",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (groceryTotal > 0) {
                        item {
                            CategoryBreakdownChip(
                                emoji = "🛒",
                                label = "Groceries",
                                amount = currencyFormat.format(groceryTotal),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (fuelTotal > 0) {
                        val fuelSummaryDetail = fuelItems.firstOrNull { it.quantity > 1.0 && it.unitPrice != null }
                        val detailText = if (fuelSummaryDetail != null) {
                            "${fuelSummaryDetail.quantity} gal @ $${String.format(Locale.US, "%.3f", fuelSummaryDetail.unitPrice)}/gal"
                        } else null

                        item {
                            CategoryBreakdownChip(
                                emoji = "⛽",
                                label = "Fuel",
                                amount = currencyFormat.format(fuelTotal),
                                detail = detailText,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                textColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (householdTotal > 0) {
                        item {
                            CategoryBreakdownChip(
                                emoji = "🧻",
                                label = "Household",
                                amount = currencyFormat.format(householdTotal),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                textColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (taxTotal > 0) {
                        item {
                            CategoryBreakdownChip(
                                emoji = "🧾",
                                label = "Tax",
                                amount = currencyFormat.format(taxTotal),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (otherTotal > 0) {
                        item {
                            CategoryBreakdownChip(
                                emoji = "📦",
                                label = "Other",
                                amount = currencyFormat.format(otherTotal),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Itemized Line Items List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Itemized Items (${receiptItems.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    TextButton(
                        onClick = { showAddItemDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", style = MaterialTheme.typography.labelSmall)
                    }
                }

                receiptItems.forEach { item ->
                    ReceiptItemRow(
                        item = item,
                        currencyFormat = currencyFormat,
                        onClick = { editingItem = item }
                    )
                }
            } else if (transaction.receiptImageUri != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Itemized Items",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    TextButton(
                        onClick = { showAddItemDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = "No line items detected automatically. You can add items manually using the button above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

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

    // Fullscreen Zoom Dialog for Receipt Image
    if (showZoomImage && transaction.receiptImageUri != null) {
        Dialog(
            onDismissRequest = { showZoomImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = transaction.receiptImageUri,
                        contentDescription = "Zoomed Receipt",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { showZoomImage = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }

    // Add Item Dialog
    if (showAddItemDialog) {
        AddReceiptItemDialog(
            transactionId = transaction.id,
            onDismiss = { showAddItemDialog = false },
            onAdd = { newItem ->
                onAddItem?.invoke(newItem)
                showAddItemDialog = false
            }
        )
    }

    // Edit Item Dialog
    editingItem?.let { item ->
        EditReceiptItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                onUpdateItem?.invoke(updated)
                editingItem = null
            },
            onDelete = {
                onDeleteItem?.invoke(item)
                editingItem = null
            }
        )
    }

    // Link Obligation Dialog
    if (showLinkDialog) {
        var selectedType by remember { mutableStateOf(ObligationType.BILL) }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = {
                Text("Tag to Bill or Debt", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Link this transaction to an existing Bill or Debt to mark it paid and update balances automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == ObligationType.BILL,
                            onClick = { selectedType = ObligationType.BILL },
                            label = { Text("Bill", maxLines = 1, softWrap = false) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == ObligationType.DEBT,
                            onClick = { selectedType = ObligationType.DEBT },
                            label = { Text("Debt", maxLines = 1, softWrap = false) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedType == ObligationType.BILL) {
                        if (bills.isEmpty()) {
                            Text(
                                "No bills found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                bills.forEach { bill ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onLinkToBill?.invoke(bill.id)
                                                showLinkDialog = false
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                bill.name,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                currencyFormat.format(bill.amount),
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (debts.isEmpty()) {
                            Text(
                                "No debts found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                debts.forEach { debt ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onLinkToDebt?.invoke(debt.id, transaction.amount)
                                                showLinkDialog = false
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                debt.name,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                currencyFormat.format(debt.currentBalance),
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CategoryBreakdownChip(
    emoji: String,
    label: String,
    amount: String,
    detail: String? = null,
    color: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color,
        contentColor = textColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(emoji, fontSize = 14.sp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun ReceiptItemRow(
    item: ReceiptItem,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.category.emoji,
                    fontSize = 20.sp
                )
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.category.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (item.quantity > 1.0 && item.unitPrice != null) {
                            Text(
                                text = "${item.quantity} @ ${currencyFormat.format(item.unitPrice)}/ea",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text(
                text = currencyFormat.format(item.price),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AddReceiptItemDialog(
    transactionId: String,
    onDismiss: () -> Unit,
    onAdd: (ReceiptItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1.0") }
    var selectedCategory by remember { mutableStateOf(ReceiptItemCategory.GROCERY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Receipt Item", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Total Price ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity (e.g. 1.0 or gallons)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ReceiptItemCategory.values()) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.emoji} ${cat.displayName}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull() ?: 0.0
                    val qty = quantityText.toDoubleOrNull() ?: 1.0
                    val unitPrice = if (qty > 1.0) price / qty else null
                    onAdd(
                        ReceiptItem(
                            id = UUID.randomUUID().toString(),
                            transactionId = transactionId,
                            name = name.trim().ifEmpty { "Item" },
                            price = price,
                            quantity = qty,
                            unitPrice = unitPrice,
                            category = selectedCategory
                        )
                    )
                },
                enabled = name.isNotBlank() && (priceText.toDoubleOrNull() != null)
            ) {
                Text("Add")
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
fun EditReceiptItemDialog(
    item: ReceiptItem,
    onDismiss: () -> Unit,
    onSave: (ReceiptItem) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var priceText by remember { mutableStateOf(item.price.toString()) }
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    var selectedCategory by remember { mutableStateOf(item.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Receipt Item", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Total Price ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ReceiptItemCategory.values()) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.emoji} ${cat.displayName}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                Button(
                    onClick = {
                        val price = priceText.toDoubleOrNull() ?: item.price
                        val qty = quantityText.toDoubleOrNull() ?: item.quantity
                        val unitPrice = if (qty > 1.0) price / qty else null
                        onSave(
                            item.copy(
                                name = name.trim().ifEmpty { item.name },
                                price = price,
                                quantity = qty,
                                unitPrice = unitPrice,
                                category = selectedCategory
                            )
                        )
                    },
                    enabled = name.isNotBlank() && (priceText.toDoubleOrNull() != null)
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
