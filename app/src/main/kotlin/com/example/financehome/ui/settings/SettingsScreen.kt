package com.example.financehome.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.financehome.BuildConfig
import com.example.financehome.data.model.UpdateCheckResult
import com.example.financehome.ui.theme.ColorExpense
import com.example.financehome.ui.theme.ColorIncome
import com.example.financehome.ui.update.UpdateDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToCsvImport: () -> Unit,
    onNavigateToManageRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val updateRepo by viewModel.updateRepo.collectAsState()
    val isAutoCheckUpdatesEnabled by viewModel.isAutoCheckUpdatesEnabled.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()

    val notificationLogs by viewModel.notificationLogs.collectAsState()
    val notificationFilterConfig by viewModel.notificationFilterConfig.collectAsState()
    val simulationFeedback by viewModel.simulationFeedback.collectAsState()

    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showEditRepoDialog by remember { mutableStateOf(false) }
    var showCustomSimulatorDialog by remember { mutableStateOf(false) }
    var showAppFilterDialog by remember { mutableStateOf(false) }
    var editingRepoText by remember { mutableStateOf("") }
    var customSimulatorText by remember { mutableStateOf("") }
    var showLogsExpanded by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadSettings(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Real-Time Notification Detection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasNotificationPermission) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Real-Time Transaction Detection",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (hasNotificationPermission) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Icon(
                                imageVector = if (hasNotificationPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasNotificationPermission) ColorIncome else ColorExpense
                            )
                        }

                        Text(
                            text = if (hasNotificationPermission)
                                "Active. FinanceHome is automatically detecting push notifications from Cash App, Ally, and other banks and recording transactions."
                            else
                                "Notification Access is not enabled. To automatically log transactions from Cash App and Ally notifications in real-time, please grant notification access in Android Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasNotificationPermission) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )

                        FilledTonalButton(
                            onClick = { showAppFilterDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Monitored Apps & SMS Rules (${notificationFilterConfig.monitoredPackages.size} apps)")
                        }

                        if (!hasNotificationPermission) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "💡 Android 13/14+ Tip: If the Notification Access toggle is grayed out in phone settings: Go to phone Settings > Apps > FinanceHome > tap the 3 vertical dots (⋮) in the top-right corner > tap 'Allow restricted settings'. Then return here to enable access.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant Notification Access")
                            }
                        }
                    }
                }
            }

            // Test Transaction Simulator & Diagnostic Logs Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Test Transaction Capture",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "Tap to simulate incoming bank notifications or text messages to immediately verify parser, auto-categorizer, and swipe queue without waiting for a bank purchase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (simulationFeedback != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = simulationFeedback ?: "",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { viewModel.simulateCashApp(context) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Cash App", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.simulateAllyBank(context) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Ally Bank", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.simulateBankSms(context) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Bank SMS", fontSize = 11.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                customSimulatorText = ""
                                showCustomSimulatorDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Custom Notification Text...")
                        }

                        // Recent Logs toggle
                        if (notificationLogs.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLogsExpanded = !showLogsExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Notifications (${notificationLogs.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Icon(
                                    imageVector = if (showLogsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }

                            if (showLogsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    notificationLogs.take(5).forEach { log ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = log.packageName.substringAfterLast('.'),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = log.status,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (log.isSuccess) ColorIncome else ColorExpense
                                                        )
                                                    )
                                                }
                                                Text(
                                                    text = log.summaryText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cloud Sync Section (Local First vs Cloud Mirror)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Sync (Firebase Firestore)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Sync data to cloud mirror so you can access/view your financial data on a computer or backup device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isCloudSyncEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        showCloudSyncDialog = true
                                    } else {
                                        viewModel.setCloudSyncEnabled(context, false)
                                    }
                                }
                            )
                        }

                        if (isCloudSyncEnabled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Cloud sync is active. Room DB remains your fast local source of truth.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Auto-Categorization & Rent Tagging Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Auto-Categorization & Rent",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Configure merchant keyword rules and tag specific spends (like landlord payments) as recurring monthly rent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToManageRules,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manage Category Rules & Rent")
                        }
                    }
                }
            }

            // Data Management Section (CSV Import / Export)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Data Management",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        // Import CSV Button
                        OutlinedButton(
                            onClick = onNavigateToCsvImport,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Bank Statement (CSV)")
                        }

                        // Export CSV Button
                        Button(
                            onClick = {
                                viewModel.exportTransactionsAsCsv(context) { file ->
                                    shareCsvFile(context, file)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Transactions to CSV")
                        }

                        if (exportState != null) {
                            Text(
                                text = exportState ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // App Updates Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                            Text(
                                text = "App Updates",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Version info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Installed Version",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Auto-check toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Check on Startup",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Silently look for newer versions when opening the app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = isAutoCheckUpdatesEnabled,
                                onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
                            )
                        }

                        // Update Source / GitHub Repo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Update Source",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = updateRepo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingRepoText = updateRepo
                                    showEditRepoDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Update Source",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Check status / Button
                        when (val result = updateCheckResult) {
                            is UpdateCheckResult.Checking -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Checking for updates...")
                                }
                            }
                            is UpdateCheckResult.UpdateAvailable -> {
                                Button(
                                    onClick = { viewModel.showUpdateDialog.value = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ColorIncome
                                    )
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Update Available (v${result.updateInfo.versionName}) — Install")
                                }
                            }
                            is UpdateCheckResult.UpToDate -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.checkForUpdates() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Check for Updates")
                                    }
                                    Text(
                                        text = "✓ You are up to date (v${result.currentVersion})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ColorIncome
                                    )
                                }
                            }
                            is UpdateCheckResult.Error -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.checkForUpdates() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Check for Updates")
                                    }
                                    Text(
                                        text = "Error: ${result.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            is UpdateCheckResult.Idle -> {
                                OutlinedButton(
                                    onClick = { viewModel.checkForUpdates() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Check for Updates")
                                }
                            }
                        }
                    }
                }
            }

            // About Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "FinanceHome v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Privacy-first personal finance with real-time detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showEditRepoDialog) {
        AlertDialog(
            onDismissRequest = { showEditRepoDialog = false },
            title = { Text("Update Source Repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter GitHub repository (owner/repo) or a custom JSON manifest URL:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = editingRepoText,
                        onValueChange = { editingRepoText = it },
                        label = { Text("Repository / URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingRepoText.isNotBlank()) {
                            viewModel.setUpdateRepo(editingRepoText)
                        }
                        showEditRepoDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUpdateDialog && updateCheckResult is UpdateCheckResult.UpdateAvailable) {
        val info = (updateCheckResult as UpdateCheckResult.UpdateAvailable).updateInfo
        UpdateDialog(
            updateInfo = info,
            downloadState = downloadState,
            onStartDownload = { viewModel.startDownload(info.downloadUrl) },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    if (showCloudSyncDialog) {
        AlertDialog(
            onDismissRequest = { showCloudSyncDialog = false },
            title = { Text("Enable Cloud Sync?") },
            text = {
                Text("When enabled, your transactions and debts will be encrypted and mirrored to your private Firebase Firestore cloud store. This lets you access your data from a browser or another device in the future.\n\nAll data is kept fully offline on your device as well.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCloudSyncEnabled(context, true)
                        showCloudSyncDialog = false
                    }
                ) {
                    Text("Enable Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudSyncDialog = false }) {
                    Text("Keep Local Only")
                }
            }
        )
    }

    if (showCustomSimulatorDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSimulatorDialog = false },
            title = { Text("Simulate Bank Notification") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste or type any bank notification text to test:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = customSimulatorText,
                        onValueChange = { customSimulatorText = it },
                        label = { Text("Notification text") },
                        placeholder = { Text("e.g. Card charged $24.99 at Target") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customSimulatorText.isNotBlank()) {
                            viewModel.simulateCustom(context, customSimulatorText)
                        }
                        showCustomSimulatorDialog = false
                    }
                ) {
                    Text("Simulate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSimulatorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAppFilterDialog) {
        MonitoredAppsDialog(
            config = notificationFilterConfig,
            onDismiss = { showAppFilterDialog = false },
            onTogglePackage = { viewModel.toggleMonitoredPackage(it) },
            onToggleSmsEnabled = { viewModel.setSmsEnabled(it) },
            onToggleRequireDollar = { viewModel.setRequireDollarSignForSms(it) },
            onAddKeyword = { viewModel.addSmsKeyword(it) },
            onRemoveKeyword = { viewModel.removeSmsKeyword(it) },
            onResetKeywords = { viewModel.resetSmsKeywords() },
            onAddCustomPackage = { viewModel.setPackageMonitored(it, true) }
        )
    }
}

fun shareCsvFile(context: android.content.Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Transactions"))
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_TEXT, file.readText())
        }
        context.startActivity(Intent.createChooser(intent, "Export Transactions"))
    }
}
