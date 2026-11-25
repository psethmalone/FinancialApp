package com.example.financehome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.financehome.sync.DailySyncWorker
import com.example.financehome.ui.theme.FinanceHomeTheme
import dagger.hilt.android.AndroidEntryPoint

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.financehome.service.FinanceNotificationListenerService

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "com.example.financehome.EXTRA_TRANSACTION_ID"
        const val EXTRA_OPEN_RECEIPT_CAPTURE = "com.example.financehome.EXTRA_OPEN_RECEIPT_CAPTURE"
        const val EXTRA_INITIAL_TAB = "com.example.financehome.EXTRA_INITIAL_TAB"
    }

    private val pendingTransactionId = mutableStateOf<String?>(null)
    private val pendingInitialTab = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingTransactionId.value = intent?.getStringExtra(EXTRA_TRANSACTION_ID)
        pendingInitialTab.value = intent?.getStringExtra(EXTRA_INITIAL_TAB)

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+) so spending alerts & receipts can post
        requestNotificationPermissionIfNeeded()

        // Ensure notification listener service connection is refreshed
        FinanceNotificationListenerService.rebindService(applicationContext)

        // Schedule periodic background refresh of SPY price cache and cloud sync
        DailySyncWorker.schedule(applicationContext)

        setContent {
            FinanceHomeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        pendingTransactionId = pendingTransactionId.value,
                        onClearPendingTransaction = { pendingTransactionId.value = null },
                        initialTabName = pendingInitialTab.value,
                        onClearInitialTab = { pendingInitialTab.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val txId = intent.getStringExtra(EXTRA_TRANSACTION_ID)
        if (!txId.isNullOrBlank()) {
            pendingTransactionId.value = txId
        }
        val tab = intent.getStringExtra(EXTRA_INITIAL_TAB)
        if (!tab.isNullOrBlank()) {
            pendingInitialTab.value = tab
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh listener connection when app returns to foreground
        FinanceNotificationListenerService.rebindService(applicationContext)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }
}
