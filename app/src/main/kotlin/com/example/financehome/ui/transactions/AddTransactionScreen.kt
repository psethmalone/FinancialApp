package com.example.financehome.ui.transactions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.financehome.data.model.ReceiptItem
import com.example.financehome.data.model.ReceiptItemCategory
import com.example.financehome.data.model.TransactionType
import com.example.financehome.service.ParsedReceipt
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

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
        isDiscretionary: Boolean,
        receiptImageUri: String?,
        receiptItems: List<ReceiptItem>
    ) -> Unit,
    onCreateCaptureUri: (() -> Pair<File, Uri>)? = null,
    onSavePickedUri: (suspend (Uri) -> String)? = null,
    onScanReceipt: (suspend (String) -> ParsedReceipt)? = null,
    modifier: Modifier = Modifier
) {
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.DEBIT) }
    var selectedCategory by remember { mutableStateOf(PREDEFINED_CATEGORIES.first()) }
    var notesText by remember { mutableStateOf("") }
    var isDiscretionary by remember { mutableStateOf(true) }

    // Receipt capture states
    var pendingReceiptUri by remember { mutableStateOf<String?>(null) }
    var pendingReceiptItems by remember { mutableStateOf<List<ReceiptItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var showZoomImage by remember { mutableStateOf(false) }
    var tempCameraFileUri by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    fun processReceipt(uriStr: String) {
        if (onScanReceipt == null) return
        coroutineScope.launch {
            isScanning = true
            try {
                val parsed = onScanReceipt(uriStr)
                pendingReceiptUri = uriStr

                // Auto-fill merchant if recognized and field was empty or unedited
                if (!parsed.merchant.isNullOrBlank()) {
                    merchantText = parsed.merchant
                }

                // Auto-fill amount if detected
                if (parsed.totalAmount != null && parsed.totalAmount > 0.0) {
                    amountText = String.format(Locale.US, "%.2f", parsed.totalAmount)
                }

                // Auto-categorize
                if (parsed.fuelInfo != null) {
                    selectedCategory = "Gas & Fuel"
                    isDiscretionary = false
                } else if (parsed.items.any { it.category == ReceiptItemCategory.GROCERY }) {
                    selectedCategory = "Groceries"
                    isDiscretionary = false
                }

                // Map items
                pendingReceiptItems = parsed.items.map { item ->
                    ReceiptItem(
                        id = UUID.randomUUID().toString(),
                        transactionId = "",
                        name = item.name,
                        price = item.price,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        category = item.category,
                        notes = item.rawLine
                    )
                }
            } catch (_: Exception) {
            } finally {
                isScanning = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFileUri != null) {
            processReceipt(tempCameraFileUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && onSavePickedUri != null) {
            coroutineScope.launch {
                val localUri = onSavePickedUri(uri)
                processReceipt(localUri)
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

    val isValid = amountText.toDoubleOrNull() != null &&
            (amountText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            merchantText.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                isDiscretionary,
                                pendingReceiptUri,
                                pendingReceiptItems
                            )
                            onDismiss()
                        },
                        enabled = isValid && !isScanning
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                        pendingReceiptUri = null
                        pendingReceiptItems = emptyList()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Deposit (Income)")
                }
            }

            // Receipt Capture / OCR Section (for Expenses)
            if (selectedType == TransactionType.DEBIT && onScanReceipt != null) {
                if (isScanning) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Scanning receipt with ML Kit...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Text(
                                text = "Extracting merchant, total, and itemizing groceries/fuel...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else if (pendingReceiptUri != null) {
                    // Receipt attached preview
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
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
                                    model = pendingReceiptUri,
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Receipt Attached",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (pendingReceiptItems.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "${pendingReceiptItems.size} items",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Amounts & merchant pre-filled below",
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
                                        onClick = {
                                            pendingReceiptUri = null
                                            pendingReceiptItems = emptyList()
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Remove", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Card offering receipt scan or upload
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🧾", fontSize = 18.sp)
                                Text(
                                    text = "Scan or Upload Receipt",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Snap a picture to automatically fill merchant, amount, and itemize groceries or fuel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { launchCamera() },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Take Photo", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { launchGallery() },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gallery", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
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
                placeholder = { Text("e.g. McDonald's, Starbucks, Kroger, Shell") },
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
                        isDiscretionary,
                        pendingReceiptUri,
                        pendingReceiptItems
                    )
                    onDismiss()
                },
                enabled = isValid && !isScanning,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (pendingReceiptUri != null) "Save Transaction & Receipt" else "Save Transaction",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    // Zoom Dialog for Receipt Image Preview
    if (showZoomImage && pendingReceiptUri != null) {
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
                        model = pendingReceiptUri,
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
}
