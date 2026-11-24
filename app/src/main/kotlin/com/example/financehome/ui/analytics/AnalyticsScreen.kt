package com.example.financehome.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.domain.AnalyticsPeriod
import com.example.financehome.domain.AnalyticsReport
import com.example.financehome.ui.analytics.components.DonutPieChart
import com.example.financehome.ui.analytics.components.MonthlyTrendChart
import com.example.financehome.ui.analytics.components.WeeklyBarChart
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AnalyticsScreen(
    report: AnalyticsReport,
    onSelectPeriod: (AnalyticsPeriod) -> Unit,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val dtf = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val dateRangeSubtitle = "${report.startDate.format(dtf)} – ${report.endDate.format(dtf)}"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Period Filter Chips
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsPeriod.values().forEach { period ->
                    FilterChip(
                        selected = report.period == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(period.displayName) },
                        leadingIcon = if (report.period == period) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }

        // Cash Flow KPI Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cash Flow Summary",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = dateRangeSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Savings Rate Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (report.cashFlow.netSavings >= 0) ColorIncome.copy(alpha = 0.15f) else ColorExpense.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (report.cashFlow.netSavings >= 0) {
                                    String.format(Locale.US, "%.1f%% Saved", report.cashFlow.savingsRate)
                                } else {
                                    "Deficit"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (report.cashFlow.netSavings >= 0) ColorIncome else ColorExpense,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Inflow
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = ColorIncome,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Inflow",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currencyFormat.format(report.cashFlow.totalIncome),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorIncome
                                )
                            )
                        }

                        // Outflow
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = ColorExpense,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Outflow",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currencyFormat.format(report.cashFlow.totalExpense),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorExpense
                                )
                            )
                        }

                        // Net
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Net Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currencyFormat.format(report.cashFlow.netSavings),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.cashFlow.netSavings >= 0) ColorIncome else ColorExpense
                                )
                            )
                        }
                    }
                }
            }
        }

        // Needs vs Wants (50/30/20 Rule) Card
        if (report.cashFlow.totalExpense > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Needs vs. Wants Split",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Total Outflow: ${currencyFormat.format(report.cashFlow.totalExpense)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Dual progress bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                        ) {
                            val needsFraction = (report.cashFlow.needsPercentage / 100f).toFloat().coerceIn(0f, 1f)
                            val wantsFraction = (report.cashFlow.wantsPercentage / 100f).toFloat().coerceIn(0f, 1f)

                            if (needsFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(needsFraction)
                                        .fillMaxHeight()
                                        .background(Color(0xFF3F51B5))
                                )
                            }
                            if (wantsFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(wantsFraction)
                                        .fillMaxHeight()
                                        .background(Color(0xFFFF9800))
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3F51B5))
                                )
                                Text(
                                    text = "Needs: ${currencyFormat.format(report.cashFlow.needsTotal)} (${String.format(Locale.US, "%.0f%%", report.cashFlow.needsPercentage)})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF9800))
                                )
                                Text(
                                    text = "Wants: ${currencyFormat.format(report.cashFlow.wantsTotal)} (${String.format(Locale.US, "%.0f%%", report.cashFlow.wantsPercentage)})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Spending by Category Donut Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Spending by Category",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    DonutPieChart(
                        slices = report.categorySlices,
                        totalAmount = report.cashFlow.totalExpense,
                        currencyFormat = currencyFormat
                    )
                }
            }
        }

        // Week-over-Week Comparative Bar Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    WeeklyBarChart(
                        weeklyBars = report.weeklyBars,
                        averageWeeklySpend = report.averageWeeklySpend,
                        currencyFormat = currencyFormat
                    )
                }
            }
        }

        // 6-Month Cash Flow Trajectory Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    MonthlyTrendChart(
                        monthlyTrends = report.monthlyTrends,
                        currencyFormat = currencyFormat
                    )
                }
            }
        }

        // Top Merchants Card
        if (report.topMerchants.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Top Spending Merchants",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        report.topMerchants.forEachIndexed { idx, merchant ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = merchant.merchant,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${merchant.count} ${if (merchant.count == 1) "transaction" else "transactions"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Text(
                                    text = currencyFormat.format(merchant.amount),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ColorExpense
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
