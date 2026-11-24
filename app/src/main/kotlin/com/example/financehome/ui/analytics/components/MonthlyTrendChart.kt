package com.example.financehome.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.domain.MonthlyTrendData
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import java.text.NumberFormat

@Composable
fun MonthlyTrendChart(
    monthlyTrends: List<MonthlyTrendData>,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    if (monthlyTrends.isEmpty()) return

    val maxVal = monthlyTrends.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0
    val ceiling = maxOf(maxVal, 100.0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "6-Month Cash Flow Trajectory",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ColorIncome)
                    )
                    Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ColorExpense)
                    )
                    Text("Outflow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Grouped Bar Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(top = 10.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyTrends.forEach { trend ->
                    val incomeFraction = (trend.income / ceiling).toFloat().coerceIn(0.01f, 1f)
                    val expenseFraction = (trend.expense / ceiling).toFloat().coerceIn(0.01f, 1f)

                    val incomeHeight = (incomeFraction * 120).dp
                    val expenseHeight = (expenseFraction * 120).dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Dual bars side-by-side
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Income bar
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(incomeHeight)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(ColorIncome)
                            )
                            // Expense bar
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(expenseHeight)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(ColorExpense)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Month label
                        Text(
                            text = trend.monthLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
