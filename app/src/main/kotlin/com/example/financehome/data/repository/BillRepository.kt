package com.example.financehome.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.financehome.data.local.BillDao
import com.example.financehome.data.local.BillOccurrenceDao
import com.example.financehome.data.local.MerchantBillRuleDao
import com.example.financehome.data.model.Bill
import com.example.financehome.data.model.BillOccurrenceEntity
import com.example.financehome.data.model.MerchantBillRuleEntity
import com.example.financehome.domain.MerchantNormalizer
import com.example.financehome.domain.PayFrequency
import com.example.financehome.domain.PaycheckConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao,
    private val billOccurrenceDao: BillOccurrenceDao,
    private val merchantBillRuleDao: MerchantBillRuleDao,
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

    val allOccurrences: Flow<List<BillOccurrenceEntity>> = billOccurrenceDao.getAllOccurrences()

    suspend fun getOccurrencesForBill(billId: String): Flow<List<BillOccurrenceEntity>> =
        billOccurrenceDao.getOccurrencesForBill(billId)

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

    /**
     * Toggles the paid status of a single bill occurrence for [targetDate].
     * Marks ONLY that single occurrence as paid/unpaid, keeping subsequent cycles projected as unpaid.
     */
    suspend fun toggleOccurrencePaid(
        bill: Bill,
        targetDate: LocalDate,
        matchedTxId: String? = null,
        currentDate: LocalDate = LocalDate.now()
    ) {
        val existing = billOccurrenceDao.getOccurrence(bill.id, targetDate.toEpochDay())
        val willBePaid = !(existing?.isPaid ?: false)
        val occurrence = BillOccurrenceEntity(
            billId = bill.id,
            dueDateEpochDay = targetDate.toEpochDay(),
            isPaid = willBePaid,
            paidDateEpochDay = if (willBePaid) currentDate.toEpochDay() else null,
            matchedTransactionId = if (willBePaid) matchedTxId else null,
            amount = bill.amount
        )
        billOccurrenceDao.upsertOccurrence(occurrence)

        // Keep bill.isPaidThisMonth in sync if targetDate falls in current month
        if (targetDate.year == currentDate.year && targetDate.monthValue == currentDate.monthValue) {
            billDao.markPaid(bill.id, willBePaid, if (willBePaid) System.currentTimeMillis() else null)
        }
    }

    suspend fun toggleBillPaid(bill: Bill, targetDate: LocalDate? = null, currentDate: LocalDate = LocalDate.now()) {
        val dueToToggle = targetDate ?: bill.getEffectiveNextDueDate(currentDate)
        toggleOccurrencePaid(bill, dueToToggle, currentDate = currentDate)
    }

    suspend fun saveMerchantRule(normalizedMerchant: String, billId: String, isDebt: Boolean = false) {
        merchantBillRuleDao.upsertRule(
            MerchantBillRuleEntity(
                normalizedMerchant = normalizedMerchant,
                billId = billId,
                isDebt = isDebt
            )
        )
    }

    fun getAllMerchantRules(): Flow<List<MerchantBillRuleEntity>> = merchantBillRuleDao.getAllRules()

    /**
     * Matches an incoming transaction against saved merchant rules and marks the nearest
     * unpaid bill occurrence (within +-10 days) as paid.
     */
    suspend fun matchAndSettleOccurrence(
        merchant: String,
        txAmount: Double,
        txDate: LocalDate,
        txId: String
    ): Pair<Bill, BillOccurrenceEntity>? {
        val normalized = MerchantNormalizer.normalize(merchant)
        val rules = merchantBillRuleDao.getAllRulesList()
        val rule = rules.firstOrNull { it.normalizedMerchant == normalized }
            ?: rules.firstOrNull { normalized.contains(it.normalizedMerchant) || it.normalizedMerchant.contains(normalized) }
            ?: return null

        if (rule.isDebt) return null
        val bill = billDao.getBillById(rule.billId) ?: return null

        // Look for candidate due dates in previous, current, and next month
        val candidateMonths = listOf(
            YearMonth.from(txDate).minusMonths(1),
            YearMonth.from(txDate),
            YearMonth.from(txDate).plusMonths(1)
        )
        val candidateDueDates = candidateMonths.map { ym ->
            ym.atDay(minOf(bill.dueDayOfMonth, ym.lengthOfMonth()))
        }

        val nearestDueDate = candidateDueDates
            .map { date -> Pair(date, abs(ChronoUnit.DAYS.between(txDate, date))) }
            .filter { it.second <= 10L }
            .minByOrNull { it.second }
            ?.first ?: return null

        val existingOcc = billOccurrenceDao.getOccurrence(bill.id, nearestDueDate.toEpochDay())
        if (existingOcc == null || !existingOcc.isPaid) {
            val occurrence = BillOccurrenceEntity(
                billId = bill.id,
                dueDateEpochDay = nearestDueDate.toEpochDay(),
                isPaid = true,
                paidDateEpochDay = txDate.toEpochDay(),
                matchedTransactionId = txId,
                amount = txAmount
            )
            billOccurrenceDao.upsertOccurrence(occurrence)
            if (nearestDueDate.year == txDate.year && nearestDueDate.monthValue == txDate.monthValue) {
                billDao.markPaid(bill.id, true, System.currentTimeMillis())
            }
            return Pair(bill, occurrence)
        }

        return null
    }

    suspend fun markBillPaidForCycle(
        bill: Bill,
        dueToSettle: LocalDate = bill.getEffectiveNextDueDate(),
        currentDate: LocalDate = LocalDate.now()
    ) {
        val nextDue = bill.advanceDueDate(dueToSettle)
        val timestamp = System.currentTimeMillis()
        val currentMonthDue = try {
            currentDate.withDayOfMonth(minOf(bill.dueDayOfMonth, currentDate.lengthOfMonth()))
        } catch (_: Exception) {
            currentDate
        }
        val isPaidThisMonth = (dueToSettle.year == currentDate.year && dueToSettle.monthValue == currentDate.monthValue)
                || !nextDue.isBefore(currentMonthDue)
        billDao.updateSettlement(
            id = bill.id,
            nextDueDateEpochDay = nextDue.toEpochDay(),
            lastSettledDateEpochDay = dueToSettle.toEpochDay(),
            isPaid = isPaidThisMonth,
            timestamp = timestamp
        )
    }

    suspend fun unmarkBillPaid(
        bill: Bill,
        dueToUnsettle: LocalDate? = null,
        currentDate: LocalDate = LocalDate.now()
    ) {
        val currentNext = bill.getEffectiveNextDueDate(currentDate)
        val restoredDue = dueToUnsettle
            ?: bill.lastSettledDateEpochDay?.let { LocalDate.ofEpochDay(it) }
            ?: bill.rollbackDueDate(currentNext)
        val isThisCurrentMonth = restoredDue.year == currentDate.year && restoredDue.monthValue == currentDate.monthValue
        val isStillPaidThisMonth = if (isThisCurrentMonth) false else bill.isPaidThisMonth
        billDao.updateSettlement(
            id = bill.id,
            nextDueDateEpochDay = restoredDue.toEpochDay(),
            lastSettledDateEpochDay = null,
            isPaid = isStillPaidThisMonth,
            timestamp = null
        )
    }

    suspend fun markBillPaid(billId: String, isPaid: Boolean = true, targetDate: LocalDate? = null, currentDate: LocalDate = LocalDate.now()) {
        val bill = billDao.getBillById(billId) ?: return
        if (isPaid) {
            markBillPaidForCycle(bill, targetDate ?: bill.getEffectiveNextDueDate(currentDate), currentDate)
        } else {
            unmarkBillPaid(bill, targetDate, currentDate)
        }
    }

    fun updatePaycheckConfig(config: PaycheckConfig) {
        prefs.edit()
            .putString(KEY_FREQUENCY, config.frequency.name)
            .putFloat(KEY_TAKE_HOME, config.typicalTakeHomePay.toFloat())
            .putLong(KEY_NEXT_PAY_DATE, config.anchorPayDateEpochDay)
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
        val anchorPayEpoch = prefs.getLong(KEY_NEXT_PAY_DATE, LocalDate.now().toEpochDay())

        return PaycheckConfig(
            frequency = freq,
            typicalTakeHomePay = takeHome,
            anchorPayDateEpochDay = anchorPayEpoch
        )
    }
}
