package com.example.financehome.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehome.data.model.Debt
import com.example.financehome.data.model.DebtType
import com.example.financehome.data.repository.DebtRepository
import com.example.financehome.domain.AmortizationRow
import com.example.financehome.domain.DebtCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

data class PayoffSimulation(
    val monthsToPayoff: Int,
    val payoffDateMillis: Long,
    val totalInterest: Double,
    val monthsSaved: Int,
    val interestSaved: Double,
    val amortizationRows: List<AmortizationRow>
)

@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debtRepo: DebtRepository
) : ViewModel() {

    val debts: StateFlow<List<Debt>> =
        debtRepo.getAllDebts()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val totalDebt: StateFlow<Double> =
        debts.map { list -> list.sumOf { it.currentBalance } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0.0
            )

    fun addDebt(
        name: String,
        balance: Double,
        aprPercent: Double,
        minimumPayment: Double,
        type: DebtType
    ) {
        viewModelScope.launch {
            // apr in Debt entity is stored as a fraction (e.g. 0.2199 for 21.99%)
            val aprFraction = aprPercent / 100.0
            debtRepo.addDebt(
                name = name,
                balance = balance,
                apr = aprFraction,
                minimumPayment = minimumPayment,
                type = type
            )
        }
    }

    fun updateDebt(debt: Debt) {
        viewModelScope.launch {
            debtRepo.updateDebt(debt)
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            debtRepo.deleteDebt(debt)
        }
    }

    fun simulatePayoff(debt: Debt, extraMonthlyPayment: Double): PayoffSimulation {
        val effectivePayment = debt.minimumPayment + extraMonthlyPayment
        val months = DebtCalculator.monthsToPayoff(debt.currentBalance, debt.apr, effectivePayment)
        val payoffDate = DebtCalculator.payoffDateMillis(months)
        val totalInterest = DebtCalculator.totalInterestPaid(debt.currentBalance, debt.apr, effectivePayment)

        val monthsSaved = if (extraMonthlyPayment > 0.0) {
            DebtCalculator.monthsSavedWithExtraPayment(debt.currentBalance, debt.apr, debt.minimumPayment, extraMonthlyPayment)
        } else 0

        val interestSaved = if (extraMonthlyPayment > 0.0) {
            DebtCalculator.interestSavedWithExtraPayment(debt.currentBalance, debt.apr, debt.minimumPayment, extraMonthlyPayment)
        } else 0.0

        val schedule = DebtCalculator.amortizationSchedule(debt.currentBalance, debt.apr, effectivePayment)

        return PayoffSimulation(
            monthsToPayoff = if (months == Double.MAX_VALUE) -1 else months.roundToInt(),
            payoffDateMillis = payoffDate,
            totalInterest = if (totalInterest == Double.MAX_VALUE) 0.0 else totalInterest,
            monthsSaved = monthsSaved,
            interestSaved = interestSaved,
            amortizationRows = schedule
        )
    }
}
