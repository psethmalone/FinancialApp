package com.example.financehome.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.domain.WeeklyBarData
import java.text.NumberFormat

@Composable
fun WeeklyBarChart(
    weeklyBars: List<WeeklyBarData>,
    averageWeeklySpend: Double,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    if (weeklyBars.isEmpty()) {
        Text(
            text = "No weekly data available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxSpend = weeklyBars.maxOfOrNull { it.spending } ?: 1.0
    val ceiling = maxOf(maxSpend, averageWeeklySpend * 1.25, 10.0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Average benchmark pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Week-by-Week Spending",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Avg: ${currencyFormat.format(averageWeeklySpend)}/wk",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Bars Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(top = 16.dp, bottom = 4.dp)
        ) {
            // Dotted Average Line
            if (averageWeeklySpend > 0) {
                val avgFraction = (averageWeeklySpend / ceiling).toFloat().coerceIn(0.05f, 0.95f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(bottom = (avgFraction * 120).dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }

            // Columns for each week
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyBars.forEach { bar ->
                    val fraction = (bar.spending / ceiling).toFloat().coerceIn(0.02f, 1f)
                    val barHeight = (fraction * 120).dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Amount above bar
                        Text(
                            text = if (bar.spending > 0) String.format("$%.0f", bar.spending) else "$0",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (bar.isCurrentWeek) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (bar.isCurrentWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Vertical bar
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (bar.isCurrentWeek) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Week label below
                        Text(
                            text = bar.weekLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = if (bar.isCurrentWeek) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (bar.isCurrentWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
