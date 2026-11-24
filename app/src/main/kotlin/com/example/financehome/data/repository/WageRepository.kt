package com.example.financehome.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.financehome.domain.WageConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("finance_wage_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOURLY_WAGE = "wage_hourly"
        private const val KEY_OT_MULTIPLIER = "wage_ot_multiplier"
        private const val KEY_DEDUCTION_PERCENT = "wage_deduction_percent"
        private const val KEY_STANDARD_HOURS = "wage_standard_weekly_hours"
        private const val KEY_WEEK1_HOURS = "wage_week1_hours"
        private const val KEY_WEEK2_HOURS = "wage_week2_hours"
        private const val KEY_IS_VERIFIED = "wage_is_verified"
        private const val KEY_VERIFIED_NET = "wage_verified_net"
        private const val KEY_LAST_VERIFIED_EPOCH = "wage_last_verified_epoch"
        private const val KEY_AUTO_SYNC = "wage_auto_sync"
    }

    private val _wageConfig = MutableStateFlow(loadWageConfig())
    val wageConfig = _wageConfig.asStateFlow()

    fun updateWageConfig(config: WageConfig) {
        prefs.edit()
            .putFloat(KEY_HOURLY_WAGE, config.hourlyWage.toFloat())
            .putFloat(KEY_OT_MULTIPLIER, config.overtimeMultiplier.toFloat())
            .putFloat(KEY_DEDUCTION_PERCENT, config.estimatedDeductionPercent.toFloat())
            .putFloat(KEY_STANDARD_HOURS, config.standardWeeklyHours.toFloat())
            .putFloat(KEY_WEEK1_HOURS, config.week1Hours.toFloat())
            .putFloat(KEY_WEEK2_HOURS, config.week2Hours.toFloat())
            .putBoolean(KEY_IS_VERIFIED, config.isPaycheckVerified)
            .putFloat(KEY_VERIFIED_NET, config.verifiedNetPay?.toFloat() ?: -1f)
            .putLong(KEY_LAST_VERIFIED_EPOCH, config.lastVerifiedEpochDay ?: 0L)
            .putBoolean(KEY_AUTO_SYNC, config.autoSyncPaycheckPlanner)
            .apply()
        _wageConfig.value = config
    }

    fun setHours(week1: Double, week2: Double) {
        val current = _wageConfig.value
        updateWageConfig(current.copy(week1Hours = week1, week2Hours = week2))
    }

    fun verifyPaycheck(actualNetPay: Double) {
        val current = _wageConfig.value
        updateWageConfig(
            current.copy(
                isPaycheckVerified = true,
                verifiedNetPay = actualNetPay,
                lastVerifiedEpochDay = LocalDate.now().toEpochDay()
            )
        )
    }

    fun resetForNextPayPeriod() {
        val current = _wageConfig.value
        updateWageConfig(
            current.copy(
                week1Hours = 40.0,
                week2Hours = 40.0,
                isPaycheckVerified = false,
                verifiedNetPay = null
            )
        )
    }

    private fun loadWageConfig(): WageConfig {
        val wage = prefs.getFloat(KEY_HOURLY_WAGE, 25f).toDouble()
        val otMult = prefs.getFloat(KEY_OT_MULTIPLIER, 1.5f).toDouble()
        val dedPercent = prefs.getFloat(KEY_DEDUCTION_PERCENT, 18f).toDouble()
        val stdHours = prefs.getFloat(KEY_STANDARD_HOURS, 40f).toDouble()
        val w1 = prefs.getFloat(KEY_WEEK1_HOURS, 40f).toDouble()
        val w2 = prefs.getFloat(KEY_WEEK2_HOURS, 40f).toDouble()
        val isVerified = prefs.getBoolean(KEY_IS_VERIFIED, false)
        val verifiedNetRaw = prefs.getFloat(KEY_VERIFIED_NET, -1f)
        val verifiedNet = if (verifiedNetRaw >= 0f) verifiedNetRaw.toDouble() else null
        val lastEpochRaw = prefs.getLong(KEY_LAST_VERIFIED_EPOCH, 0L)
        val lastEpoch = if (lastEpochRaw > 0L) lastEpochRaw else null
        val autoSync = prefs.getBoolean(KEY_AUTO_SYNC, true)

        return WageConfig(
            hourlyWage = wage,
            overtimeMultiplier = otMult,
            estimatedDeductionPercent = dedPercent,
            standardWeeklyHours = stdHours,
            week1Hours = w1,
            week2Hours = w2,
            isPaycheckVerified = isVerified,
            verifiedNetPay = verifiedNet,
            lastVerifiedEpochDay = lastEpoch,
            autoSyncPaycheckPlanner = autoSync
        )
    }
}
