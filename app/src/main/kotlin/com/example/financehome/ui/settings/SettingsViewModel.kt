package com.example.financehome.ui.settings

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.AppUpdateInfo
import com.example.financehome.data.model.DownloadState
import com.example.financehome.data.model.UpdateCheckResult
import com.example.financehome.data.repository.AppUpdateRepository
import com.example.financehome.data.repository.TransactionRepository
import com.example.financehome.sync.DailySyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

import com.example.financehome.data.repository.NotificationFilterRepository
import com.example.financehome.data.repository.NotificationLogRepository
import com.example.financehome.data.repository.SpendingAlertRepository
import com.example.financehome.domain.SafeToSpendAlertLevel
import com.example.financehome.domain.SpendingAlertConfig
import com.example.financehome.service.FinanceNotificationListenerService
import com.example.financehome.service.NotificationParser
import com.example.financehome.service.SpendingAlertManager

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val appUpdateRepo: AppUpdateRepository,
    private val notificationLogRepo: NotificationLogRepository,
    private val notificationFilterRepo: NotificationFilterRepository,
    private val spendingAlertRepo: SpendingAlertRepository,
    private val spendingAlertManager: SpendingAlertManager
) : ViewModel() {

    val isCloudSyncEnabled = MutableStateFlow(false)
    val hasNotificationPermission = MutableStateFlow(false)
    val isPostNotificationsGranted = MutableStateFlow(false)
    val isListenerConnected = FinanceNotificationListenerService.isConnected
    val exportState = MutableStateFlow<String?>(null)

    val notificationLogs = notificationLogRepo.logs
    val notificationFilterConfig = notificationFilterRepo.config
    val spendingAlertConfig = spendingAlertRepo.config
    val simulationFeedback = MutableStateFlow<String?>(null)

    fun updateSpendingAlertConfig(config: SpendingAlertConfig) {
        spendingAlertRepo.updateConfig(config)
    }

    fun sendTestSpendingAlert(level: SafeToSpendAlertLevel = SafeToSpendAlertLevel.LOW_WARNING) {
        spendingAlertManager.sendTestAlert(level)
    }

    val updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val updateRepo = MutableStateFlow(appUpdateRepo.getUpdateRepo())
    val isAutoCheckUpdatesEnabled = MutableStateFlow(appUpdateRepo.isAutoCheckEnabled())
    val showUpdateDialog = MutableStateFlow(false)

    fun toggleMonitoredPackage(packageName: String) {
        notificationFilterRepo.togglePackageMonitored(packageName)
    }

    fun setPackageMonitored(packageName: String, enabled: Boolean) {
        notificationFilterRepo.setPackageMonitored(packageName, enabled)
    }

    fun setSmsEnabled(enabled: Boolean) {
        notificationFilterRepo.setSmsEnabled(enabled)
    }

    fun setRequireDollarSignForSms(enabled: Boolean) {
        notificationFilterRepo.setRequireDollarSignForSms(enabled)
    }

    fun addSmsKeyword(keyword: String) {
        notificationFilterRepo.addSmsKeyword(keyword)
    }

    fun removeSmsKeyword(keyword: String) {
        notificationFilterRepo.removeSmsKeyword(keyword)
    }

    fun resetSmsKeywords() {
        notificationFilterRepo.resetKeywordsToDefault()
    }

    fun simulateCashApp(context: Context) {
        viewModelScope.launch {
            val res = notificationLogRepo.simulateNotification(
                context,
                NotificationParser.PACKAGE_CASH_APP,
                "Cash App",
                "You paid $12.50 to McDonald's"
            )
            simulationFeedback.value = if (res != null) "✓ Simulated Cash App: \$12.50 to McDonald's captured!" else "Simulation failed"
        }
    }

    fun simulateAllyBank(context: Context) {
        viewModelScope.launch {
            val res = notificationLogRepo.simulateNotification(
                context,
                NotificationParser.PACKAGE_ALLY_BANK,
                "Ally Bank Debit Card",
                "A $45.00 transaction was made using your Ally Bank Debit Card at WALMART"
            )
            simulationFeedback.value = if (res != null) "✓ Simulated Ally Bank: \$45.00 at WALMART captured!" else "Simulation failed"
        }
    }

    fun simulateBankSms(context: Context) {
        viewModelScope.launch {
            val res = notificationLogRepo.simulateNotification(
                context,
                "com.google.android.apps.messaging",
                "Chase",
                "Chase: You made a $24.50 purchase at SHELL OIL on Nov 24 using card 1234."
            )
            simulationFeedback.value = if (res != null) "✓ Simulated Bank SMS: ${res.merchant} \$${res.amount} captured!" else "Simulation failed"
        }
    }

    fun simulateCustom(context: Context, text: String) {
        viewModelScope.launch {
            val res = notificationLogRepo.simulateNotification(
                context,
                "com.generic.bank",
                "Bank Alert",
                text
            )
            simulationFeedback.value = if (res != null) "✓ Captured: ${res.merchant} \$${res.amount}!" else "❌ Could not parse transaction from this text"
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateCheckResult.value = UpdateCheckResult.Checking
            val result = appUpdateRepo.checkForUpdate()
            result.onSuccess { info ->
                if (info != null && info.isNewer) {
                    val cached = appUpdateRepo.getCachedApk(info.versionName)
                    if (cached != null) {
                        downloadState.value = DownloadState.Success(cached)
                    } else {
                        downloadState.value = DownloadState.Idle
                    }
                    updateCheckResult.value = UpdateCheckResult.UpdateAvailable(info)
                    showUpdateDialog.value = true
                } else if (info != null) {
                    updateCheckResult.value = UpdateCheckResult.UpToDate(info.versionName)
                } else {
                    updateCheckResult.value = UpdateCheckResult.UpToDate(com.example.financehome.BuildConfig.VERSION_NAME)
                }
            }.onFailure { error ->
                updateCheckResult.value = UpdateCheckResult.Error(error.localizedMessage ?: "Check failed")
            }
        }
    }

    fun startDownload(downloadUrl: String) {
        viewModelScope.launch {
            appUpdateRepo.downloadApk(downloadUrl).collect { state ->
                downloadState.value = state
            }
        }
    }

    fun dismissUpdateDialog() {
        showUpdateDialog.value = false
    }

    fun setUpdateRepo(repo: String) {
        appUpdateRepo.setUpdateRepo(repo)
        updateRepo.value = appUpdateRepo.getUpdateRepo()
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        appUpdateRepo.setAutoCheckEnabled(enabled)
        isAutoCheckUpdatesEnabled.value = enabled
    }

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences("financehome_prefs", Context.MODE_PRIVATE)
        isCloudSyncEnabled.value = prefs.getBoolean("cloud_sync_enabled", false)
        checkNotificationPermission(context)
    }

    fun checkNotificationPermission(context: Context) {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        hasNotificationPermission.value = enabledPackages.contains(context.packageName)
        isPostNotificationsGranted.value = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun rebindListenerService(context: Context) {
        FinanceNotificationListenerService.rebindService(context)
        checkNotificationPermission(context)
    }

    fun setCloudSyncEnabled(context: Context, enabled: Boolean) {
        isCloudSyncEnabled.value = enabled
        val prefs = context.getSharedPreferences("financehome_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("cloud_sync_enabled", enabled).apply()

        if (enabled) {
            DailySyncWorker.schedule(context)
        }
    }

    fun exportTransactionsAsCsv(context: Context, onExportReady: (File) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val transactions = transactionRepo.getAllTransactions().first()
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val file = File(context.cacheDir, "financehome_transactions_$dateStr.csv")

                    FileOutputStream(file).use { out ->
                        val writer = out.bufferedWriter()
                        writer.write("id,date,merchant,category,type,amount,isDiscretionary,source,notes\n")
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        transactions.forEach { t ->
                            val date = dateFormat.format(Date(t.timestampMillis))
                            val cleanMerchant = t.merchant.replace("\"", "\"\"")
                            val cleanNotes = (t.notes ?: "").replace("\"", "\"\"")
                            writer.write("\"${t.id}\",\"$date\",\"$cleanMerchant\",\"${t.category}\",\"${t.type}\",${t.amount},${t.isDiscretionary},\"${t.source}\",\"$cleanNotes\"\n")
                        }
                        writer.flush()
                    }

                    withContext(Dispatchers.Main) {
                        exportState.value = "Exported ${transactions.size} transactions"
                        onExportReady(file)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        exportState.value = "Export failed: ${e.message}"
                    }
                }
            }
        }
    }
}
