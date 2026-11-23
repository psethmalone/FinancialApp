package com.example.financehome.ui.categorize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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

data class CategoryOption(
    val name: String,
    val icon: String,
    val defaultIsDiscretionary: Boolean,
    val isRent: Boolean = false
)

val CATEGORY_OPTIONS = listOf(
    CategoryOption("Groceries", "🛒", defaultIsDiscretionary = false),
    CategoryOption("Food & Dining", "🍔", defaultIsDiscretionary = true),
    CategoryOption("Coffee & Drinks", "☕", defaultIsDiscretionary = true),
    CategoryOption("Rent / Housing", "🏠", defaultIsDiscretionary = false, isRent = true),
    CategoryOption("Utilities & Bills", "💡", defaultIsDiscretionary = false),
    CategoryOption("Gas & Transit", "🚗", defaultIsDiscretionary = false),
    CategoryOption("Entertainment", "🎬", defaultIsDiscretionary = true),
    CategoryOption("Shopping", "🛍️", defaultIsDiscretionary = true),
    CategoryOption("Health & Care", "💊", defaultIsDiscretionary = false),
    CategoryOption("Personal & Other", "📦", defaultIsDiscretionary = true)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeCategorizeScreen(
    viewModel: SwipeCategorizeViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uncategorized by viewModel.uncategorizedTransactions.collectAsState()
    val batchMessage by viewModel.batchMessage.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Categorize Spending", fontWeight = FontWeight.Bold)
                        if (uncategorized.isNotEmpty()) {
                            Text(
                                "${uncategorized.size} transactions to classify",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Batch notification toast
            AnimatedVisibility(
                visible = batchMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = batchMessage ?: "",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (uncategorized.isEmpty()) {
                AllCaughtUpView(onDone = onDone)
            } else {
                val currentTransaction = uncategorized.first()
                val sameMerchantCount = uncategorized.count {
                    it.merchant.equals(currentTransaction.merchant, ignoreCase = true)
                }

                CategoryFirstSwipeCard(
                    transaction = currentTransaction,
                    sameMerchantCount = sameMerchantCount,
                    currencyFormat = currencyFormat,
                    onClassified = { category, isDiscretionary, isRent ->
                        viewModel.categorizeTransaction(
                            transaction = currentTransaction,
                            category = category,
                            isDiscretionary = isDiscretionary,
                            isRecurringRent = isRent,
                            rememberRule = true
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryFirstSwipeCard(
    transaction: Transaction,
    sameMerchantCount: Int,
    currencyFormat: NumberFormat,
    onClassified: (category: String, isDiscretionary: Boolean, isRent: Boolean) -> Unit
) {
    // Default selected category
    var selectedCategory by remember(transaction.id) {
        mutableStateOf(CATEGORY_OPTIONS.first { it.name == "Food & Dining" })
    }

    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember(transaction.id) { Animatable(0f) }
    val dragLimit = 400f

    // When card is dragged
    val dragProgress = (offsetX.value / dragLimit).coerceIn(-1f, 1f)
    val cardColor = when {
        dragProgress > 0.15f -> ColorIncome.copy(alpha = 0.12f * (dragProgress / 1f))
        dragProgress < -0.15f -> ColorExpense.copy(alpha = 0.12f * (-dragProgress / 1f))
        else -> MaterialTheme.colorScheme.surface
    }

    fun triggerCommit(isNeed: Boolean) {
        coroutineScope.launch {
            val targetX = if (isNeed) 1200f else -1200f
            offsetX.animateTo(targetX, animationSpec = tween(250))
            onClassified(
                selectedCategory.name,
                !isNeed, // if Need -> isDiscretionary = false; if Want -> isDiscretionary = true
                selectedCategory.isRent
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top instruction
        Text(
            text = "1. Click a category  →  2. Swipe or tap Need / Want",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )

        // The interactive card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    rotationZ = (offsetX.value / dragLimit) * 10f
                }
                .pointerInput(transaction.id) {
                    detectDragGestures(
                        onDragEnd = {
                            if (offsetX.value > 180f) {
                                triggerCommit(isNeed = true)
                            } else if (offsetX.value < -180f) {
                                triggerCommit(isNeed = false)
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
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header with drag badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dragProgress < -0.1f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorExpense,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                "← WANT (INVEST IT)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (dragProgress > 0.1f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorIncome,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                "NEED (ESSENTIAL) →",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Transaction summary
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currencyFormat.format(transaction.amount),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = formatTimestamp(transaction.timestampMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (sameMerchantCount > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "⚡ $sameMerchantCount ${transaction.merchant} transactions will be auto-tagged",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Category selector grid
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Category: ${selectedCategory.icon} ${selectedCategory.name}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(CATEGORY_OPTIONS) { cat ->
                            val isSelected = cat.name == selectedCategory.name
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategory = cat
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat.icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Hint
                Text(
                    text = "Swipe Right for Need • Swipe Left for Want",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Two prominent action buttons below the card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { triggerCommit(isNeed = false) },
                colors = ButtonDefaults.buttonColors(containerColor = ColorExpense),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("WANT (Discretionary)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Button(
                onClick = { triggerCommit(isNeed = true) },
                colors = ButtonDefaults.buttonColors(containerColor = ColorIncome),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("NEED (Essential)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
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
                Icons.Filled.CheckCircle,
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
            text = "Every transaction has been categorized into Needs and Wants. Your financial tracking and fake portfolio calculations are up to date.",
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
