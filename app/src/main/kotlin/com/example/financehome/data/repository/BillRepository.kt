package com.example.financehome.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.financehome.data.local.BillDao
import com.example.financehome.data.model.Bill
import com.example.financehome.domain.PayFrequency
import com.example.financehome.domain.PaycheckConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao,
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("finance_paycheck_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FREQUENCY = "pay_frequency"
        private const val KEY_TAKE_HOME = "pay_take_home"
        private const val KEY_NEXT_PAY_DATE = "pay_next_date"
    }

    private val _paycheckConfig = MutableStateFlow(loadPaycheckConfig())
    val paycheckConfig = _paycheckConfig.asStateFlow()

    fun getAllBills(): Flow<List<Bill>> = billDao.getAllBills()

    suspend fun addBill(
        name: String,
        amount: Double,
        dueDayOfMonth: Int,
        category: String,
        isAutoPay: Boolean = false,
        notes: String? = null
    ) {
        val bill = Bill(
            id = UUID.randomUUID().toString(),
            name = name,
            amount = amount,
            dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31),
            category = category,
            isAutoPay = isAutoPay,
            isPaidThisMonth = false,
            notes = notes,
            createdAtMillis = System.currentTimeMillis()
        )
        billDao.insertBill(bill)
    }

    suspend fun updateBill(bill: Bill) = billDao.updateBill(bill)

    suspend fun deleteBill(bill: Bill) = billDao.deleteBill(bill)

    suspend fun toggleBillPaid(bill: Bill) {
        val newPaid = !bill.isPaidThisMonth
        val timestamp = if (newPaid) System.currentTimeMillis() else null
        billDao.markPaid(bill.id, newPaid, timestamp)
    }

    fun updatePaycheckConfig(config: PaycheckConfig) {
        prefs.edit()
            .putString(KEY_FREQUENCY, config.frequency.name)
            .putFloat(KEY_TAKE_HOME, config.typicalTakeHomePay.toFloat())
            .putLong(KEY_NEXT_PAY_DATE, config.nextPayDateEpochDay)
            .apply()
        _paycheckConfig.value = config
    }

    private fun loadPaycheckConfig(): PaycheckConfig {
        val freqStr = prefs.getString(KEY_FREQUENCY, PayFrequency.BI_WEEKLY.name)
        val freq = try {
            PayFrequency.valueOf(freqStr ?: PayFrequency.BI_WEEKLY.name)
        } catch (_: Exception) {
            PayFrequency.BI_WEEKLY
        }
        val takeHome = prefs.getFloat(KEY_TAKE_HOME, 1800f).toDouble()
        val nextPayEpoch = prefs.getLong(KEY_NEXT_PAY_DATE, LocalDate.now().toEpochDay())

        return PaycheckConfig(
            frequency = freq,
            typicalTakeHomePay = takeHome,
            nextPayDateEpochDay = nextPayEpoch
        )
    }
}
