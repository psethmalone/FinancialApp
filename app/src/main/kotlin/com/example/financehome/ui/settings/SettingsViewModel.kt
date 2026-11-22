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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val appUpdateRepo: AppUpdateRepository
) : ViewModel() {

    val isCloudSyncEnabled = MutableStateFlow(false)
    val hasNotificationPermission = MutableStateFlow(false)
    val exportState = MutableStateFlow<String?>(null)

    val updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val updateRepo = MutableStateFlow(appUpdateRepo.getUpdateRepo())
    val isAutoCheckUpdatesEnabled = MutableStateFlow(appUpdateRepo.isAutoCheckEnabled())
    val showUpdateDialog = MutableStateFlow(false)

    fun checkForUpdates() {
        viewModelScope.launch {
            updateCheckResult.value = UpdateCheckResult.Checking
            val result = appUpdateRepo.checkForUpdate()
            result.onSuccess { info ->
                if (info != null && info.isNewer) {
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
