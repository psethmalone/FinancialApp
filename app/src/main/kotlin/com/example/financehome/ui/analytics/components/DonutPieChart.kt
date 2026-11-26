package com.example.financehome.ui.analytics.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.domain.CategorySlice
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.atan2

@Composable
fun DonutPieChart(
    slices: List<CategorySlice>,
    totalAmount: Double,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    var selectedSlice by remember { mutableStateOf<CategorySlice?>(null) }

    // Pre-aggregate slices under 3% into "Other"
    val displaySlices = remember(slices) {
        if (slices.isEmpty()) emptyList()
        else {
            val major = mutableListOf<CategorySlice>()
            var otherAmt = 0.0
            var otherCount = 0
            var otherPct = 0.0

            slices.forEach { slice ->
                if (slice.percentage < 3.0) {
                    otherAmt += slice.amount
                    otherCount += slice.transactionCount
                    otherPct += slice.percentage
                } else {
                    major.add(slice)
                }
            }

            if (otherAmt > 0.0) {
                major.add(
                    CategorySlice(
                        category = "Other",
                        amount = otherAmt,
                        percentage = otherPct,
                        color = Color(0xFF9E9E9E),
                        transactionCount = otherCount
                    )
                )
            }
            major
        }
    }

    // Reset selection if slices change
    LaunchedEffect(displaySlices) {
        selectedSlice = null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (displaySlices.isEmpty() || totalAmount <= 0.0) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No spending recorded in this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Interactive Donut Chart Canvas constrained in a 180dp aspect-ratio box
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(displaySlices) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                val radius = size.width / 2f
                                val strokeW = size.width * 0.18f
                                val innerRadius = radius - strokeW

                                if (dist in innerRadius..radius) {
                                    // Angle in degrees from 0 to 360 starting from -90 (top)
                                    var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    touchAngle = (touchAngle + 90f + 360f) % 360f

                                    var currentAngle = 0f
                                    var found: CategorySlice? = null
                                    for (slice in displaySlices) {
                                        val sweep = (slice.percentage.toFloat() / 100f) * 360f
                                        if (touchAngle >= currentAngle && touchAngle <= currentAngle + sweep) {
                                            found = slice
                                            break
                                        }
                                        currentAngle += sweep
                                    }
                                    selectedSlice = if (selectedSlice == found) null else found
                                } else {
                                    selectedSlice = null
                                }
                            }
                        }
                ) {
                    val strokeWidth = size.width * 0.18f
                    val chartRadius = (size.width - strokeWidth) / 2f
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(chartRadius * 2f, chartRadius * 2f)

                    var startAngle = -90f
                    val gapAngle = if (displaySlices.size > 1) 2f else 0f

                    for (slice in displaySlices) {
                        val totalSweep = (slice.percentage.toFloat() / 100f) * 360f
                        val actualSweep = maxOf(0.5f, totalSweep - gapAngle)

                        val isHighlighted = selectedSlice == null || selectedSlice == slice
                        val sliceColor = if (isHighlighted) slice.color else slice.color.copy(alpha = 0.35f)
                        val actualStroke = if (selectedSlice == slice) strokeWidth * 1.15f else strokeWidth

                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = actualSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = actualStroke, cap = StrokeCap.Round)
                        )

                        startAngle += totalSweep
                    }
                }

                // Center Content (Total or selected category details)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    val currentDisplaySlice = selectedSlice
                    if (currentDisplaySlice != null) {
                        Text(
                            text = currentDisplaySlice.category,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = currentDisplaySlice.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currencyFormat.format(currentDisplaySlice.amount),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f%%", currentDisplaySlice.percentage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Total Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currencyFormat.format(totalAmount),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${displaySlices.size} categories",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Legend List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displaySlices.forEach { slice ->
                    val isSelected = selectedSlice == slice
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSlice = if (isSelected) null else slice }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .padding(top = 1.dp)
                                    ) {
                                        Surface(
                                            color = slice.color,
                                            modifier = Modifier.fillMaxSize()
                                        ) {}
                                    }
                                    Text(
                                        text = slice.category,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "(${slice.transactionCount})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currencyFormat.format(slice.amount),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", slice.percentage),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Colored percentage fill bar
                            LinearProgressIndicator(
                                progress = { (slice.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = slice.color,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
