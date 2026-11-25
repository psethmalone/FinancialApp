package com.example.financehome.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationFilterConfig(
    val monitoredPackages: Set<String>,
    val isSmsEnabled: Boolean,
    val smsKeywords: Set<String>,
    val requireDollarSignForSms: Boolean
)

data class InstalledAppOption(
    val packageName: String,
    val appName: String,
    val isSmsApp: Boolean = false,
    val isPredefined: Boolean = false
)

@Singleton
class NotificationFilterRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("finance_notification_filter_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MONITORED_PACKAGES = "monitored_packages"
        private const val KEY_SMS_ENABLED = "sms_enabled"
        private const val KEY_SMS_KEYWORDS = "sms_keywords"
        private const val KEY_REQUIRE_DOLLAR_SMS = "require_dollar_sms"

        val DEFAULT_MONITORED_PACKAGES = setOf(
            "com.squareup.cash",
            "com.ally.mobile",
            "com.chase.sig.android",
            "com.fidelity.android",
            "com.konylabs.capitalone",
            "com.wf.wellsfargomobile",
            "com.infonow.bofa",
            "com.americanexpress.android.acctsvcs.us",
            "com.discoverfinancial.mobile",
            "com.citi.citimobile",
            "com.chime.android",
            "com.pnc.ecommerce.mobile",
            "com.tdbank",
            "com.huntington.m",
            "com.usaa.mobile.android.usaa",
            "com.venmo",
            "com.paypal.android.p2pmobile",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.verizon.messaging"
        )

        val DEFAULT_SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.verizon.messaging",
            "com.whatsapp",
            "org.thoughtcrime.securesms"
        )

        val DEFAULT_SMS_KEYWORDS = setOf(
            "chase",
            "wells fargo",
            "citi",
            "citibank",
            "amex",
            "american express",
            "discover",
            "capital one",
            "bank of america",
            "bofa",
            "usaa",
            "navy federal",
            "pnc",
            "td bank",
            "huntington",
            "fidelity",
            "charles schwab",
            "credit union",
            "chime",
            "bank",
            "card",
            "debit",
            "credit",
            "purchase",
            "charged",
            "spent",
            "paid",
            "authorized",
            "alert",
            "fraud",
            "transaction"
        )

        val POPULAR_APPS = listOf(
            InstalledAppOption("com.google.android.apps.messaging", "Google Messages (SMS)", isSmsApp = true, isPredefined = true),
            InstalledAppOption("com.samsung.android.messaging", "Samsung Messages (SMS)", isSmsApp = true, isPredefined = true),
            InstalledAppOption("com.android.mms", "Default Text Messages (SMS)", isSmsApp = true, isPredefined = true),
            InstalledAppOption("com.squareup.cash", "Cash App", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.ally.mobile", "Ally Bank", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.chase.sig.android", "Chase Mobile", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.fidelity.android", "Fidelity Investments", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.konylabs.capitalone", "Capital One", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.wf.wellsfargomobile", "Wells Fargo", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.infonow.bofa", "Bank of America", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.americanexpress.android.acctsvcs.us", "American Express", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.discoverfinancial.mobile", "Discover Mobile", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.citi.citimobile", "Citi Mobile", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.chime.android", "Chime Banking", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.pnc.ecommerce.mobile", "PNC Bank", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.tdbank", "TD Bank", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.huntington.m", "Huntington Mobile", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.usaa.mobile.android.usaa", "USAA Mobile", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.venmo", "Venmo", isSmsApp = false, isPredefined = true),
            InstalledAppOption("com.paypal.android.p2pmobile", "PayPal", isSmsApp = false, isPredefined = true)
        )
    }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<NotificationFilterConfig> = _config.asStateFlow()

    private fun loadConfig(): NotificationFilterConfig {
        val packages = prefs.getStringSet(KEY_MONITORED_PACKAGES, null) ?: DEFAULT_MONITORED_PACKAGES
        val smsEnabled = prefs.getBoolean(KEY_SMS_ENABLED, true)
        val keywords = prefs.getStringSet(KEY_SMS_KEYWORDS, null) ?: DEFAULT_SMS_KEYWORDS
        val reqDollar = prefs.getBoolean(KEY_REQUIRE_DOLLAR_SMS, true)

        return NotificationFilterConfig(
            monitoredPackages = packages.toSet(),
            isSmsEnabled = smsEnabled,
            smsKeywords = keywords.toSet(),
            requireDollarSignForSms = reqDollar
        )
    }

    fun isPackageMonitored(packageName: String): Boolean {
        val current = _config.value
        if (current.monitoredPackages.contains(packageName)) return true
        if (current.isSmsEnabled && isSmsApp(packageName)) return true
        if (packageName.contains("cash", ignoreCase = true) ||
            packageName.contains("ally", ignoreCase = true) ||
            packageName.contains("chase", ignoreCase = true) ||
            packageName.contains("fidelity", ignoreCase = true) ||
            packageName.contains("capitalone", ignoreCase = true) ||
            packageName.contains("wellsfargo", ignoreCase = true) ||
            packageName.contains("bofa", ignoreCase = true) ||
            packageName.contains("discover", ignoreCase = true) ||
            packageName.contains("chime", ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    fun isSmsApp(packageName: String): Boolean {
        return DEFAULT_SMS_PACKAGES.contains(packageName) ||
                packageName.contains("messaging", ignoreCase = true) ||
                packageName.contains("sms", ignoreCase = true) ||
                packageName.contains("mms", ignoreCase = true)
    }

    fun togglePackageMonitored(packageName: String) {
        val current = _config.value.monitoredPackages.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        savePackages(current)
    }

    fun setPackageMonitored(packageName: String, enabled: Boolean) {
        val current = _config.value.monitoredPackages.toMutableSet()
        if (enabled) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        savePackages(current)
    }

    fun setSmsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_ENABLED, enabled).apply()
        _config.value = _config.value.copy(isSmsEnabled = enabled)
    }

    fun setRequireDollarSignForSms(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_DOLLAR_SMS, enabled).apply()
        _config.value = _config.value.copy(requireDollarSignForSms = enabled)
    }

    fun addSmsKeyword(keyword: String) {
        val trimmed = keyword.trim().lowercase()
        if (trimmed.isEmpty()) return
        val current = _config.value.smsKeywords.toMutableSet()
        current.add(trimmed)
        saveKeywords(current)
    }

    fun removeSmsKeyword(keyword: String) {
        val trimmed = keyword.trim().lowercase()
        val current = _config.value.smsKeywords.toMutableSet()
        current.remove(trimmed)
        saveKeywords(current)
    }

    fun resetKeywordsToDefault() {
        saveKeywords(DEFAULT_SMS_KEYWORDS)
    }

    private fun savePackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_MONITORED_PACKAGES, packages).apply()
        _config.value = _config.value.copy(monitoredPackages = packages)
    }

    private fun saveKeywords(keywords: Set<String>) {
        prefs.edit().putStringSet(KEY_SMS_KEYWORDS, keywords).apply()
        _config.value = _config.value.copy(smsKeywords = keywords)
    }
}
