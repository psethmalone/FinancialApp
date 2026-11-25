package com.example.financehome.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.example.financehome.domain.SpendingAlertConfig
import com.example.financehome.ui.theme.ColorWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingThresholdDialog(
    currentConfig: SpendingAlertConfig,
    onDismiss: () -> Unit,
    onSave: (SpendingAlertConfig) -> Unit,
    onSendTestAlert: () -> Unit
) {
    val context = LocalContext.current
    val areNotificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    var isAlertsEnabled by remember { mutableStateOf(currentConfig.isAlertsEnabled) }
    var notifyOnEveryPurchase by remember { mutableStateOf(currentConfig.notifyOnEveryPurchase) }
    var lowThresholdText by remember { mutableStateOf(currentConfig.lowCushionThreshold.toInt().toString()) }
    var alertOnDeficit by remember { mutableStateOf(currentConfig.alertOnDeficit) }
    var testAlertSent by remember { mutableStateOf(false) }

    val presetAmounts = listOf(50.0, 100.0, 150.0, 200.0, 300.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Spending Alerts & Thresholds", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "FinanceHome notifies you in real-time when purchases occur, warns you when your cushion is getting low, and lets you know when to stop spending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Master Toggle: Alerts Enabled
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAlertsEnabled = !isAlertsEnabled }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Safe-to-Spend Alerts",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Enable all spending threshold notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAlertsEnabled,
                            onCheckedChange = { isAlertsEnabled = it }
                        )
                    }
                }

                if (isAlertsEnabled) {
                    // Option 1: Notify on every purchase
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { notifyOnEveryPurchase = !notifyOnEveryPurchase }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Update After Every Purchase",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Shows remaining safe-to-spend and daily pace in notification",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = notifyOnEveryPurchase,
                                onCheckedChange = { notifyOnEveryPurchase = it }
                            )
                        }
                    }

                    // Option 2: Stop Spending Deficit Warning
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alertOnDeficit = !alertOnDeficit }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Stop-Spending Alert",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                Text(
                                    text = "Urgent high-priority alert when your safe-to-spend hits \$0 or goes into deficit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = alertOnDeficit,
                                onCheckedChange = { alertOnDeficit = it }
                            )
                        }
                    }

                    // Option 3: Low Cushion Threshold
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Low Cushion Warning Threshold",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Warns you when remaining safe-to-spend drops to or below this amount:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetAmounts.forEach { amount ->
                                val isSelected = lowThresholdText == amount.toInt().toString()
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { lowThresholdText = amount.toInt().toString() },
                                    label = { Text("\$${amount.toInt()}") },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }

                        // Custom dollar input
                        OutlinedTextField(
                            value = lowThresholdText,
                            onValueChange = { lowThresholdText = it },
                            label = { Text("Threshold Amount (\$)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()

                    if (!areNotificationsEnabled) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "⚠️ Notifications are blocked in Android Settings. Spending alerts cannot be shown until notifications are allowed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Enable Notifications in Android Settings", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // Test Alert Button
                    OutlinedButton(
                        onClick = {
                            onSendTestAlert()
                            testAlertSent = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (!areNotificationsEnabled) "Test Alert (Notifications Blocked in Settings)"
                            else if (testAlertSent) "Test Alert Sent! Check Notification Tray"
                            else "Send Test Notification Now"
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val threshold = lowThresholdText.toDoubleOrNull() ?: currentConfig.lowCushionThreshold
                    onSave(
                        currentConfig.copy(
                            isAlertsEnabled = isAlertsEnabled,
                            notifyOnEveryPurchase = notifyOnEveryPurchase,
                            lowCushionThreshold = threshold.coerceAtLeast(0.0),
                            alertOnDeficit = alertOnDeficit
                        )
                    )
                }
            ) {
                Text("Save Thresholds")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
