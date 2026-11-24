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

class FinanceNotificationListenerService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        fun logRepository(): NotificationLogRepository
        fun portfolioRepository(): com.example.financehome.data.repository.PortfolioRepository
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val parser = NotificationParser()
    private val deduplicationWindowMs = 30_000L   // 30 seconds

    companion object {
        private const val TAG = "FinanceNotification"
    }

    override fun onDestroy() {
        super.onDestroy()
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

        val logRepo = try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ServiceEntryPoint::class.java
            )
            entryPoint.logRepository()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get logRepository", e)
            null
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
            showReceiptPromptNotification(transaction)
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
