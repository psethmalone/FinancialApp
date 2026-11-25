package com.example.financehome.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.financehome.MainActivity
import com.example.financehome.R
import com.example.financehome.data.local.AppDatabase
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.NotificationLogRepository
import com.example.financehome.domain.AutoCategorizer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FinanceNotificationListenerService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        fun logRepository(): NotificationLogRepository
        fun portfolioRepository(): com.example.financehome.data.repository.PortfolioRepository
        fun filterRepository(): com.example.financehome.data.repository.NotificationFilterRepository
        fun spendingAlertManager(): SpendingAlertManager
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val parser = NotificationParser()
    private val deduplicationWindowMs = 30_000L   // 30 seconds

    companion object {
        private const val TAG = "FinanceNotification"
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /**
         * Rebinds the notification listener service if the OS disconnected it.
         */
        fun rebindService(context: Context) {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) {
                try {
                    val componentName = ComponentName(context, FinanceNotificationListenerService::class.java)
                    val pm = context.packageManager
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.i(TAG, "Toggled listener component to trigger system rebind")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle listener component", e)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        Log.i(TAG, "Notification listener connected successfully!")
        getEntryPoint()?.logRepository()?.logEvent(
            packageName = "system",
            summaryText = "Notification Listener Connected",
            status = "CONNECTED",
            isSuccess = true
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
        Log.w(TAG, "Notification listener disconnected! Attempting rebind...")
        getEntryPoint()?.logRepository()?.logEvent(
            packageName = "system",
            summaryText = "Notification Listener Disconnected",
            status = "DISCONNECTED",
            isSuccess = false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, FinanceNotificationListenerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "requestRebind failed", e)
            }
        }
    }

    private fun getEntryPoint(): ServiceEntryPoint? {
        return try {
            EntryPointAccessors.fromApplication(
                applicationContext,
                ServiceEntryPoint::class.java
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ServiceEntryPoint", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        // Extract using CharSequence to prevent nulls from styled SpannableStrings
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val entryPoint = getEntryPoint()

        val logRepo = entryPoint?.logRepository()
        val filterRepo = entryPoint?.filterRepository()

        // 0. Filter check: Ensure the app is in user's monitored list
        if (filterRepo != null) {
            val config = filterRepo.config.value
            val isSms = filterRepo.isSmsApp(packageName)

            val isMonitored = filterRepo.isPackageMonitored(packageName)

            if (!isMonitored) {
                // If it contains financial signs, log why it was skipped so user has visibility in diagnostics!
                val sampleText = listOfNotNull(title, text, bigText).joinToString(" ")
                if (sampleText.contains("$") ||
                    packageName.contains("bank", ignoreCase = true) ||
                    packageName.contains("pay", ignoreCase = true) ||
                    packageName.contains("card", ignoreCase = true) ||
                    packageName.contains("credit", ignoreCase = true) ||
                    packageName.contains("citi", ignoreCase = true) ||
                    packageName.contains("capitalone", ignoreCase = true)
                ) {
                    logRepo?.logEvent(
                        packageName = packageName,
                        summaryText = sampleText.take(120),
                        status = "Skipped: App not monitored (Enable in Settings)",
                        isSuccess = false
                    )
                }
                return
            }

            // If it's an SMS/text messaging app, apply smart bank text filtering
            if (isSms) {
                if (!config.isSmsEnabled) return

                val combinedText = listOfNotNull(title, text, bigText).joinToString(" ")

                if (config.requireDollarSignForSms && !combinedText.contains("$")) {
                    return
                }

                // Must match at least one bank/financial keyword to avoid personal text messages
                val hasBankKeyword = config.smsKeywords.any { keyword ->
                    combinedText.contains(keyword, ignoreCase = true)
                }

                if (!hasBankKeyword) {
                    return
                }
            }
        }

        // 1. Check for Fidelity / Brokerage trade execution alerts
        val brokerageTrade = parser.parseBrokerageTrade(packageName, title, text, bigText, subText)
        if (brokerageTrade != null) {
            logRepo?.logEvent(
                packageName = packageName,
                summaryText = "Trade: ${brokerageTrade.action} ${brokerageTrade.shares} ${brokerageTrade.ticker} @ $${brokerageTrade.pricePerShare}",
                status = "Parsed Fidelity/Brokerage Trade",
                isSuccess = true
            )
            serviceScope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        applicationContext,
                        ServiceEntryPoint::class.java
                    )
                    entryPoint.portfolioRepository().recordBrokerageNotificationTrade(brokerageTrade)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist brokerage trade", e)
                }
            }
            return
        }

        // 2. Standard banking transaction parsing
        val parsed = parser.parse(packageName, title, text, bigText, subText)
        val combinedSummary = listOfNotNull(title, text, bigText).filter { it.isNotBlank() }.joinToString(" - ")

        if (parsed == null) {
            // Only log if it might be a financial, brokerage, or banking app
            if (packageName.contains("cash", ignoreCase = true) ||
                packageName.contains("ally", ignoreCase = true) ||
                packageName.contains("bank", ignoreCase = true) ||
                packageName.contains("pay", ignoreCase = true) ||
                packageName.contains("chase", ignoreCase = true) ||
                packageName.contains("fidelity", ignoreCase = true) ||
                combinedSummary.contains("$")
            ) {
                logRepo?.logEvent(
                    packageName = packageName,
                    summaryText = combinedSummary.take(120),
                    status = "Ignored (non-transaction alert)",
                    isSuccess = false
                )
            }
            return
        }

        serviceScope.launch {
            persistIfNotDuplicate(parsed, packageName, logRepo)
        }
    }

    private suspend fun persistIfNotDuplicate(
        parsed: ParsedTransaction,
        packageName: String,
        logRepo: NotificationLogRepository?
    ) {
        val db = AppDatabase.getDatabase(applicationContext)
        val transactionDao = db.transactionDao()
        val ruleDao = db.categoryRuleDao()

        val nowMs = System.currentTimeMillis()
        val windowStart = nowMs - deduplicationWindowMs

        val isDuplicate = transactionDao.existsRecentTransaction(
            amount = parsed.amount,
            merchant = parsed.merchant,
            windowStart = windowStart
        )

        if (isDuplicate) {
            logRepo?.logEvent(
                packageName = packageName,
                summaryText = "${parsed.merchant}: \$${parsed.amount}",
                status = "Skipped (duplicate within 30s)",
                isSuccess = false
            )
            return
        }

        // Auto-categorize based on user rules and built-in dictionary
        val userRules = ruleDao.getAllRulesList()
        val match = AutoCategorizer.categorize(parsed.merchant, userRules)

        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            amount = parsed.amount,
            merchant = parsed.merchant,
            type = parsed.type,
            category = match.category,
            source = TransactionSource.NOTIFICATION,
            isDiscretionary = match.isDiscretionary,
            timestampMillis = nowMs,
            rawNotificationText = parsed.rawText,
            notes = if (match.isRecurringRent) "Monthly Rent / Fixed Obligation" else null,
            isSynced = false
        )

        transactionDao.insertTransaction(transaction)
        if (transaction.type == TransactionType.DEBIT) {
            val alertManager = getEntryPoint()?.spendingAlertManager()
            if (alertManager != null) {
                alertManager.checkAndPostAlert(transaction)
            } else {
                showReceiptPromptNotification(transaction)
            }
        }

        logRepo?.logEvent(
            packageName = packageName,
            summaryText = "${parsed.merchant}: \$${parsed.amount} (${match.category})",
            status = "CAPTURED & LOGGED",
            isSuccess = true
        )
        Log.i(TAG, "Successfully captured transaction: ${parsed.merchant} \$${parsed.amount}")
    }

    private fun showReceiptPromptNotification(transaction: Transaction) {
        val channelId = "receipt_prompts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Receipt Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to scan receipts for new bank transactions"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TRANSACTION_ID, transaction.id)
            putExtra(MainActivity.EXTRA_OPEN_RECEIPT_CAPTURE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            transaction.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = String.format(java.util.Locale.US, "$%.2f", transaction.amount)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Expense: $formattedAmount at ${transaction.merchant}")
            .setContentText("Tap to scan or attach receipt to itemize groceries & fuel")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(transaction.id.hashCode(), notification)
    }
}
