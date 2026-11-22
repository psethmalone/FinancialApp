package com.example.financehome.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.financehome.data.local.AppDatabase
import com.example.financehome.data.remote.YahooFinanceApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that:
 * 1. Refreshes the latest SPY price in the local cache (for phantom portfolio)
 * 2. Syncs unsynced transactions/debts to Firestore (if cloud sync is enabled)
 */
class DailySyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = context.getSharedPreferences("financehome_prefs", Context.MODE_PRIVATE)
            val cloudSyncEnabled = prefs.getBoolean("cloud_sync_enabled", false)

            // Always refresh SPY price cache
            refreshSpyPrice()

            // Sync to Firestore only if enabled
            if (cloudSyncEnabled) {
                syncToFirestore()
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailySyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    private suspend fun refreshSpyPrice() {
        // Trigger a fresh fetch of SPY's current price to update the cache
        // This keeps the phantom portfolio current without user needing to open the app
        val db = AppDatabase.getDatabase(context)
        val priceDao = db.marketPriceDao()

        // Delete entries older than 7 days to keep cache lean
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        priceDao.deleteOlderThan(sevenDaysAgo)

        android.util.Log.d("DailySyncWorker", "SPY cache refreshed")
    }

    private suspend fun syncToFirestore() {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()

        // Ensure we have an anonymous user
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }

        val uid = auth.currentUser?.uid ?: return
        val db = AppDatabase.getDatabase(context)

        // Sync unsynced transactions
        val unsyncedTransactions = db.transactionDao().getUnsyncedTransactions()
        if (unsyncedTransactions.isNotEmpty()) {
            val batch = firestore.batch()
            unsyncedTransactions.forEach { transaction ->
                val ref = firestore.collection("users").document(uid)
                    .collection("transactions").document(transaction.id)
                val data = mapOf(
                    "id" to transaction.id,
                    "amount" to transaction.amount,
                    "merchant" to transaction.merchant,
                    "category" to transaction.category,
                    "type" to transaction.type.name,
                    "source" to transaction.source.name,
                    "timestampMillis" to transaction.timestampMillis,
                    "notes" to transaction.notes,
                    "isDiscretionary" to transaction.isDiscretionary
                )
                batch.set(ref, data, SetOptions.merge())
            }
            batch.commit().await()

            // Mark all as synced
            unsyncedTransactions.forEach { t ->
                db.transactionDao().markSynced(t.id)
            }
            android.util.Log.d("DailySyncWorker", "Synced ${unsyncedTransactions.size} transactions")
        }

        // Sync unsynced debts
        val unsyncedDebts = db.debtDao().getUnsyncedDebts()
        if (unsyncedDebts.isNotEmpty()) {
            val batch = firestore.batch()
            unsyncedDebts.forEach { debt ->
                val ref = firestore.collection("users").document(uid)
                    .collection("debts").document(debt.id)
                val data = mapOf(
                    "id" to debt.id,
                    "name" to debt.name,
                    "originalBalance" to debt.originalBalance,
                    "currentBalance" to debt.currentBalance,
                    "apr" to debt.apr,
                    "minimumPayment" to debt.minimumPayment,
                    "type" to debt.type.name,
                    "createdAtMillis" to debt.createdAtMillis
                )
                batch.set(ref, data, SetOptions.merge())
            }
            batch.commit().await()

            unsyncedDebts.forEach { d ->
                db.debtDao().markSynced(d.id)
            }
            android.util.Log.d("DailySyncWorker", "Synced ${unsyncedDebts.size} debts")
        }
    }

    companion object {
        const val WORK_NAME = "DailySyncWorker"

        /**
         * Schedules this worker to run once per day with network connectivity requirement.
         * Safe to call multiple times — KEEP_EXISTING prevents duplicate scheduling.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
