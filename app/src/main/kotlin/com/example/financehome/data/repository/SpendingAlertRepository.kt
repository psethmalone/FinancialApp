package com.example.financehome.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.financehome.domain.SpendingAlertConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpendingAlertRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("finance_spending_alert_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_NOTIFY_EVERY_PURCHASE = "notify_every_purchase"
        private const val KEY_LOW_CUSHION_THRESHOLD = "low_cushion_threshold"
        private const val KEY_ALERT_ON_DEFICIT = "alert_on_deficit"
        private const val KEY_DAILY_PACE_WARNING = "daily_pace_warning"

        const val DEFAULT_LOW_THRESHOLD = 100.0
    }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<SpendingAlertConfig> = _config.asStateFlow()

    fun loadConfig(): SpendingAlertConfig {
        val enabled = prefs.getBoolean(KEY_ALERTS_ENABLED, true)
        val notifyEvery = prefs.getBoolean(KEY_NOTIFY_EVERY_PURCHASE, true)
        val lowThreshold = prefs.getFloat(KEY_LOW_CUSHION_THRESHOLD, DEFAULT_LOW_THRESHOLD.toFloat()).toDouble()
        val alertDeficit = prefs.getBoolean(KEY_ALERT_ON_DEFICIT, true)
        val dailyPace = prefs.getBoolean(KEY_DAILY_PACE_WARNING, true)

        return SpendingAlertConfig(
            isAlertsEnabled = enabled,
            notifyOnEveryPurchase = notifyEvery,
            lowCushionThreshold = lowThreshold,
            alertOnDeficit = alertDeficit,
            dailyPaceWarningEnabled = dailyPace
        )
    }

    fun updateConfig(newConfig: SpendingAlertConfig) {
        prefs.edit()
            .putBoolean(KEY_ALERTS_ENABLED, newConfig.isAlertsEnabled)
            .putBoolean(KEY_NOTIFY_EVERY_PURCHASE, newConfig.notifyOnEveryPurchase)
            .putFloat(KEY_LOW_CUSHION_THRESHOLD, newConfig.lowCushionThreshold.toFloat())
            .putBoolean(KEY_ALERT_ON_DEFICIT, newConfig.alertOnDeficit)
            .putBoolean(KEY_DAILY_PACE_WARNING, newConfig.dailyPaceWarningEnabled)
            .apply()
        _config.value = newConfig
    }

    fun setAlertsEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isAlertsEnabled = enabled))
    }

    fun setNotifyOnEveryPurchase(enabled: Boolean) {
        updateConfig(_config.value.copy(notifyOnEveryPurchase = enabled))
    }

    fun setLowCushionThreshold(threshold: Double) {
        updateConfig(_config.value.copy(lowCushionThreshold = threshold.coerceAtLeast(0.0)))
    }

    fun setAlertOnDeficit(enabled: Boolean) {
        updateConfig(_config.value.copy(alertOnDeficit = enabled))
    }

    fun setDailyPaceWarningEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(dailyPaceWarningEnabled = enabled))
    }
}
