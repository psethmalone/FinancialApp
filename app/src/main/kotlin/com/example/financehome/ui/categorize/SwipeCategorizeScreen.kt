package com.example.financehome.ui.categorize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.data.model.Transaction
import com.example.financehome.ui.dashboard.formatTimestamp
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import com.example.financehome.ui.theme.ColorWarning
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeCategorizeScreen(
    viewModel: SwipeCategorizeViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uncategorized by viewModel.uncategorizedTransactions.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorize Spending", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uncategorized.isEmpty()) {
                AllCaughtUpView(onDone = onDone)
            } else {
                val currentTransaction = uncategorized.first()
                val totalCount = uncategorized.size

                SwipeCardDeck(
                    transaction = currentTransaction,
                    remainingCount = totalCount,
                    currencyFormat = currencyFormat,
                    onCategorized = { category, isDiscretionary, isRent, remember ->
                        viewModel.categorizeTransaction(
                            transaction = currentTransaction,
                            category = category,
                            isDiscretionary = isDiscretionary,
                            isRecurringRent = isRent,
                            rememberRule = remember
                        )
                    }
                )
            }
        }
    }
}

enum class SwipeStage {
    NEEDS_VS_WANTS,
    SUB_CATEGORY
}

@Composable
fun SwipeCardDeck(
    transaction: Transaction,
    remainingCount: Int,
    currencyFormat: NumberFormat,
    onCategorized: (category: String, isDiscretionary: Boolean, isRecurringRent: Boolean, rememberRule: Boolean) -> Unit
) {
    var stage by remember(transaction.id) { mutableStateOf(SwipeStage.NEEDS_VS_WANTS) }
    var selectedIsDiscretionary by remember(transaction.id) { mutableStateOf(false) }
    var rememberRule by remember(transaction.id) { mutableStateOf(true) }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Counter Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$remainingCount remaining to review",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            AssistChip(
                onClick = {},
                label = { Text(if (stage == SwipeStage.NEEDS_VS_WANTS) "Step 1: Need vs Want" else "Step 2: Sub-category") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Swipeable Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    rotationZ = (offsetX.value / 40f).coerceIn(-15f, 15f)
                }
                .pointerInput(transaction.id, stage) {
                    if (stage == SwipeStage.NEEDS_VS_WANTS) {
                        detectDragGestures(
                            onDragEnd = {
                                if (offsetX.value < -250f) {
                                    // Swiped Left: WANT
                                    selectedIsDiscretionary = true
                                    stage = SwipeStage.SUB_CATEGORY
                                    coroutineScope.launch { offsetX.snapTo(0f) }
                                } else if (offsetX.value > 250f) {
                                    // Swiped Right: NEED
                                    selectedIsDiscretionary = false
                                    stage = SwipeStage.SUB_CATEGORY
                                    coroutineScope.launch { offsetX.snapTo(0f) }
                                } else {
                                    coroutineScope.launch {
                                        offsetX.animateTo(0f, animationSpec = tween(200))
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                }
                            }
                        )
                    }
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Tag Indicator on Drag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (offsetX.value < -40f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorWarning.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "← WANT (Discretionary)",
                                color = ColorWarning,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (offsetX.value > 40f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorIncome.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "NEED (Essential) →",
                                color = ColorIncome,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }

                // Transaction Details
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                    Text(
                        text = currencyFormat.format(transaction.amount),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = formatTimestamp(transaction.timestampMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bottom section depending on stage
                if (stage == SwipeStage.NEEDS_VS_WANTS) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Swipe left for Wants, right for Needs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedIsDiscretionary = true
                                    stage = SwipeStage.SUB_CATEGORY
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("← Want")
                            }

                            Button(
                                onClick = {
                                    selectedIsDiscretionary = false
                                    stage = SwipeStage.SUB_CATEGORY
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Need →")
                            }
                        }
                    }
                } else {
                    // Sub-category Selection
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (selectedIsDiscretionary) "Select Want Category:" else "Select Need Category:",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        if (!selectedIsDiscretionary) {
                            // Needs subcategories
                            SubCategoryButton(
                                title = "Rent / Mortgage",
                                subtitle = "Marks as fixed monthly obligation",
                                isHighlight = true,
                                onClick = {
                                    onCategorized("Rent/Mortgage", false, true, rememberRule)
                                }
                            )
                            SubCategoryButton(
                                title = "Groceries",
                                subtitle = "Supermarkets, food essentials",
                                onClick = {
                                    onCategorized("Groceries", false, false, rememberRule)
                                }
                            )
                            SubCategoryButton(
                                title = "Bills & Utilities",
                                subtitle = "Electric, water, phone, internet",
                                onClick = {
                                    onCategorized("Bills & Utilities", false, false, rememberRule)
                                }
                            )
                            SubCategoryButton(
                                title = "Gas & Fuel",
                                subtitle = "Transportation fuel",
                                onClick = {
                                    onCategorized("Gas & Fuel", false, false, rememberRule)
                                }
                            )
                            SubCategoryButton(
                                title = "Debt Payment",
                                subtitle = "Credit card or loan payment",
                                onClick = {
                                    onCategorized("Debt Payment", false, false, rememberRule)
                                }
                            )
                        } else {
                            // Wants subcategories
                            SubCategoryButton(
                                title = "Food & Dining",
                                subtitle = "Eating out, coffee, fast food",
                                onClick = {
                                    onCategorized("Food & Dining", true, false, rememberRule)
                                }
                            )
                            SubCategoryButton(
                                title = "Entertainment",
                                subtitle = "Movies, games, streaming, subscriptions",
                                onClick = {
                                    onCategorized("Entertainment", true, false, rememberRule)
                                }
                            )
                            SubCategoryButton(
                                title = "Shopping",
                                subtitle = "Clothes, electronics, hobbies",
                                onClick = {
                                    onCategorized("Shopping", true, false, rememberRule)
                                }
                            )
                            SubCategoryButton(
                                title = "Personal Care / Other",
                                subtitle = "Salon, fitness, non-essential",
                                onClick = {
                                    onCategorized("Personal Care", true, false, rememberRule)
                                }
                            )
                        }

                        // Remember Rule Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Auto-tag all future ${transaction.merchant} spends",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = rememberRule,
                                onCheckedChange = { rememberRule = it }
                            )
                        }

                        TextButton(
                            onClick = { stage = SwipeStage.NEEDS_VS_WANTS },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("← Back to Need vs Want")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubCategoryButton(
    title: String,
    subtitle: String,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isHighlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isHighlight) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (isHighlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun AllCaughtUpView(onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ColorIncome.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = ColorIncome,
                modifier = Modifier.size(44.dp)
            )
        }

        Text(
            text = "All Caught Up!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Every transaction has been categorized into Needs and Wants. Your financial tracking and index fund opportunity calculations are up to date.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onDone,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back to Dashboard")
        }
    }
}
