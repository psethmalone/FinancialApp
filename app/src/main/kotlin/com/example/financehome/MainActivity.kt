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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "com.example.financehome.EXTRA_TRANSACTION_ID"
        const val EXTRA_OPEN_RECEIPT_CAPTURE = "com.example.financehome.EXTRA_OPEN_RECEIPT_CAPTURE"
        const val EXTRA_INITIAL_TAB = "com.example.financehome.EXTRA_INITIAL_TAB"
    }

    private val pendingTransactionId = mutableStateOf<String?>(null)
    private val pendingInitialTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingTransactionId.value = intent?.getStringExtra(EXTRA_TRANSACTION_ID)
        pendingInitialTab.value = intent?.getStringExtra(EXTRA_INITIAL_TAB)

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
}
