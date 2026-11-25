package com.example.financehome.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.financehome.MainActivity
import com.example.financehome.R
import com.example.financehome.data.local.BillDao
import com.example.financehome.data.local.DebtDao
import com.example.financehome.data.local.TransactionDao
import com.example.financehome.data.model.Transaction
import com.example.financehome.data.model.TransactionType
import com.example.financehome.data.repository.BillRepository
import com.example.financehome.data.repository.SpendingAlertRepository
import com.example.financehome.data.repository.WageRepository
import com.example.financehome.domain.PaycheckCalculator
import com.example.financehome.domain.SafeToSpendAlertLevel
import com.example.financehome.domain.SafeToSpendStatus
import com.example.financehome.domain.WageCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpendingAlertManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val spendingAlertRepo: SpendingAlertRepository,
    private val billRepo: BillRepository,
    private val billDao: BillDao,
    private val debtDao: DebtDao,
    private val transactionDao: TransactionDao,
    private val wageRepo: WageRepository
) {
    companion object {
        const val CHANNEL_ID = "spending_alerts"
        const val NOTIFICATION_ID_BASE = 4000
    }

    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Safe-to-Spend & Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warns when your paycheck cushion is low or depleted, and tracks safe-to-spend balance"
                enableVibration(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Checks user-defined spending thresholds after a new expense transaction is recorded,
     * and posts an actionable alert notification if conditions are met.
     */
    suspend fun checkAndPostAlert(transaction: Transaction) {
        if (transaction.type != TransactionType.DEBIT) return

        val config = spendingAlertRepo.config.value
        if (!config.isAlertsEnabled) return

        val status = computeCurrentSafeToSpendStatus()

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        val amountStr = currencyFormat.format(transaction.amount)
        val remainingStr = currencyFormat.format(status.remainingSafeToSpend)
        val dailyStr = currencyFormat.format(status.dailyPaceRemaining)

        val title: String
        val body: String

        when {
            // Case 1: Deficit / Overspend
            status.isDeficit && config.alertOnDeficit -> {
                val deficitStr = currencyFormat.format(-status.remainingSafeToSpend)
                title = "🚨 STOP SPENDING: Paycheck Cushion Depleted!"
                body = "Spent $amountStr at ${transaction.merchant}. You are $deficitStr over your safe-to-spend cushion!"
            }
            // Case 2: Low Cushion Threshold Reached
            status.isLowWarning -> {
                title = "⚠️ Low Safe-to-Spend: $remainingStr Remaining"
                body = "Spent $amountStr at ${transaction.merchant}. Only $remainingStr left for ${status.daysUntilPay} days until payday ($dailyStr/day pace)."
            }
            // Case 3: Every Purchase Balance Update
            config.notifyOnEveryPurchase -> {
                title = "Card Expense: $amountStr at ${transaction.merchant}"
                body = "$remainingStr Safe-to-Spend remaining ($dailyStr/day for ${status.daysUntilPay} days)"
            }
            else -> return
        }

        postSpendingNotification(
            notificationId = NOTIFICATION_ID_BASE + (transaction.id.hashCode() % 1000),
            title = title,
            body = body,
            transactionId = transaction.id
        )
    }

    /**
     * Computes the real-time SafeToSpendStatus incorporating current pay cycle spending.
     */
    suspend fun computeCurrentSafeToSpendStatus(currentDate: LocalDate = LocalDate.now()): SafeToSpendStatus {
        val paycheckConfig = billRepo.paycheckConfig.value
        val alertConfig = spendingAlertRepo.config.value
        val bills = billDao.getAllBills().first()
        val debts = debtDao.getAllDebts().first()
        val wageConfig = wageRepo.wageConfig.value

        val wageEstimate = WageCalculator.calculateEstimate(wageConfig, paycheckConfig.frequency)
        val baselineWageEstimate = WageCalculator.calculateBaselineEstimate(wageConfig, paycheckConfig.frequency)

        val currentTakeHome = if (wageConfig.autoSyncPaycheckPlanner) {
            wageEstimate.effectiveTakeHomePay
        } else {
            paycheckConfig.typicalTakeHomePay
        }
        val baselineTakeHome = if (wageConfig.autoSyncPaycheckPlanner) {
            baselineWageEstimate.effectiveTakeHomePay
        } else {
            paycheckConfig.typicalTakeHomePay
        }

        val breakdown = PaycheckCalculator.calculateBreakdown(
            bills = bills,
            debts = debts,
            config = paycheckConfig,
            currentDate = currentDate,
            currentTakeHomePay = currentTakeHome,
            baselineTakeHomePay = baselineTakeHome,
            isCurrentPeriodOvertime = wageEstimate.overtimeHours > 0.0,
            currentPeriodOvertimeHours = wageEstimate.overtimeHours
        )

        val anchorPayDate = LocalDate.ofEpochDay(paycheckConfig.anchorPayDateEpochDay)
        val cycleStart = PaycheckCalculator.calculateCurrentCycleStartDate(
            anchorPayDate = anchorPayDate,
            frequency = paycheckConfig.frequency,
            currentDate = currentDate
        )

        val startMs = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val spentThisCycle = transactionDao.getSpendingBetweenSync(startMs, nowMs)

        return PaycheckCalculator.calculateSafeToSpendStatus(
            breakdown = breakdown,
            config = paycheckConfig,
            spentThisCycle = spentThisCycle,
            alertConfig = alertConfig,
            currentDate = currentDate
        )
    }

    /**
     * Posts a notification with deep links to Cash Flow and Receipt Capture.
     */
    private fun postSpendingNotification(
        notificationId: Int,
        title: String,
        body: String,
        transactionId: String? = null
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w("SpendingAlertManager", "Cannot post notification: App notifications are disabled in Android Settings!")
            return
        }

        // Main tap intent -> opens Cash Flow tab
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_INITIAL_TAB, "CASH_FLOW")
            if (transactionId != null) {
                putExtra(MainActivity.EXTRA_TRANSACTION_ID, transactionId)
            }
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)

        // Action 1: Add Receipt (if transaction exists)
        if (transactionId != null) {
            val receiptIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_TRANSACTION_ID, transactionId)
                putExtra(MainActivity.EXTRA_OPEN_RECEIPT_CAPTURE, true)
            }
            val receiptPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 1,
                receiptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_camera,
                "📷 Add Receipt",
                receiptPendingIntent
            )
        }

        // Action 2: View Cash Flow
        val cashFlowIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_INITIAL_TAB, "CASH_FLOW")
        }
        val cashFlowPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 2,
            cashFlowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_dialog_info,
            "📊 View Cash Flow",
            cashFlowPendingIntent
        )

        notificationManager?.notify(notificationId, builder.build())
    }

    /**
     * Sends a test alert for instant user verification in Settings.
     */
    fun sendTestAlert(level: SafeToSpendAlertLevel = SafeToSpendAlertLevel.LOW_WARNING) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        val title: String
        val body: String

        when (level) {
            SafeToSpendAlertLevel.DEFICIT_STOP -> {
                title = "🚨 STOP SPENDING: Paycheck Cushion Depleted!"
                body = "Spent $48.50 at Target. You are ${currencyFormat.format(23.50)} over your safe-to-spend cushion for this paycheck!"
            }
            SafeToSpendAlertLevel.LOW_WARNING -> {
                val threshold = spendingAlertRepo.config.value.lowCushionThreshold
                title = "⚠️ Low Safe-to-Spend: ${currencyFormat.format(threshold * 0.65)} Remaining"
                body = "Spent $24.00 at Grocery Mart. Only ${currencyFormat.format(threshold * 0.65)} left for 5 days until payday (${currencyFormat.format(13.00)}/day pace)."
            }
            SafeToSpendAlertLevel.HEALTHY -> {
                title = "Card Expense: $32.50 at Shell Oil"
                body = "${currencyFormat.format(485.00)} Safe-to-Spend remaining (${currencyFormat.format(48.50)}/day for 10 days)"
            }
        }

        postSpendingNotification(
            notificationId = NOTIFICATION_ID_BASE + 999,
            title = title,
            body = body
        )
    }
}
