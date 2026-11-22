package com.example.financehome.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.financehome.data.local.AppDatabase
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.domain.AutoCategorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class FinanceNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val parser = NotificationParser()
    private val deduplicationWindowMs = 30_000L   // 30 seconds

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getString("android.title")
        val text = extras.getString("android.text")

        val parsed = parser.parse(packageName, title, text) ?: return

        serviceScope.launch {
            persistIfNotDuplicate(parsed)
        }
    }

    private suspend fun persistIfNotDuplicate(parsed: ParsedTransaction) {
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
    }
}
