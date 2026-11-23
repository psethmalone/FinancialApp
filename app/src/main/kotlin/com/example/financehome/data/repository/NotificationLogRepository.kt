package com.example.financehome.data.repository

import android.content.Context
import android.util.Log
import com.example.financehome.data.local.AppDatabase
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionSource
import com.example.financehome.domain.AutoCategorizer
import com.example.financehome.service.NotificationParser
import com.example.financehome.service.ParsedTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationLogItem(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val packageName: String,
    val summaryText: String,
    val status: String,
    val isSuccess: Boolean
)

@Singleton
class NotificationLogRepository @Inject constructor() {

    private val _logs = MutableStateFlow<List<NotificationLogItem>>(emptyList())
    val logs: StateFlow<List<NotificationLogItem>> = _logs.asStateFlow()

    private val parser = NotificationParser()

    fun logEvent(packageName: String, summaryText: String, status: String, isSuccess: Boolean) {
        Log.d("NotificationLogRepo", "[$packageName] $status: $summaryText")
        val item = NotificationLogItem(
            packageName = packageName,
            summaryText = summaryText,
            status = status,
            isSuccess = isSuccess
        )
        val current = _logs.value.toMutableList()
        current.add(0, item) // newest first
        if (current.size > 30) {
            current.removeAt(current.lastIndex)
        }
        _logs.value = current
    }

    /**
     * Simulates receiving a notification. Runs exact parser, auto-categorizer,
     * and inserts the transaction into the database.
     */
    suspend fun simulateNotification(
        context: Context,
        packageName: String,
        title: String?,
        text: String?
    ): ParsedTransaction? {
        val parsed = parser.parse(packageName, title, text)
        val fullText = "$title - $text"

        if (parsed == null) {
            logEvent(packageName, fullText, "Could not parse transaction pattern", false)
            return null
        }

        val db = AppDatabase.getDatabase(context)
        val transactionDao = db.transactionDao()
        val ruleDao = db.categoryRuleDao()

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
            timestampMillis = System.currentTimeMillis(),
            rawNotificationText = parsed.rawText,
            notes = if (match.isRecurringRent) "Monthly Rent / Fixed Obligation" else null,
            isSynced = false
        )

        transactionDao.insertTransaction(transaction)
        logEvent(
            packageName,
            "${parsed.merchant}: \$${parsed.amount} (${match.category})",
            "CAPTURED",
            true
        )
        return parsed
    }
}
