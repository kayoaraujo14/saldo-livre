package com.saldolivre.app.ui.screens.projections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.FutureMonthProjection
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FutureProjectionsUiState(
    val currentUser: UserProfile = UserProfile(),
    val selectedMonth: String = "",
    val currentMonth: String = "",
    val horizonCount: Int = 12,
    val projections: List<FutureMonthProjection> = emptyList(),
    val averageFreeBalance: Double = 0.0,
    val totalProjectedIncome: Double = 0.0,
    val totalProjectedExpense: Double = 0.0,
    val totalInvoicesExpense: Double = 0.0,
    val totalFixedBillsExpense: Double = 0.0,
    val isLoading: Boolean = false,
    val userMessage: String? = null
)

class FutureProjectionsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _horizonCount = MutableStateFlow(12)
    val horizonCount: StateFlow<Int> = _horizonCount.asStateFlow()

    private val _uiState = MutableStateFlow(FutureProjectionsUiState())
    val uiState: StateFlow<FutureProjectionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                combine(repository.currentUser, repository.selectedMonth, _horizonCount) { u, m, h ->
                    Triple(u, m, h)
                },
                repository.getTransactions(),
                repository.getRecurringBills(),
                repository.getMonthClosures(),
                repository.getCards()
            ) { userMonthHorizon, transactions, recurringBills, closures, cards ->
                val (user, selectedMonth, horizon) = userMonthHorizon
                val curMonth = FinanceEngine.currentMonthKey()

                val projectionsList = FinanceEngine.projectFutureMonths(
                    all = transactions,
                    bills = recurringBills,
                    profile = user,
                    uid = user.uid,
                    startMonthKey = curMonth,
                    count = horizon,
                    cards = cards,
                    closures = closures
                )

                val avgFree = if (projectionsList.isNotEmpty()) {
                    FinanceEngine.round2(projectionsList.sumOf { it.projectedFreeBalance } / projectionsList.size)
                } else 0.0

                val totIncome = FinanceEngine.round2(projectionsList.sumOf { it.projectedIncome + it.carryoverAmount })
                val totExpense = FinanceEngine.round2(projectionsList.sumOf { it.projectedExpense })
                val totInvoices = FinanceEngine.round2(projectionsList.sumOf { it.invoiceExpense })
                val totFixed = FinanceEngine.round2(projectionsList.sumOf { it.fixedBillsExpense })

                FutureProjectionsUiState(
                    currentUser = user,
                    selectedMonth = selectedMonth,
                    currentMonth = curMonth,
                    horizonCount = horizon,
                    projections = projectionsList,
                    averageFreeBalance = avgFree,
                    totalProjectedIncome = totIncome,
                    totalProjectedExpense = totExpense,
                    totalInvoicesExpense = totInvoices,
                    totalFixedBillsExpense = totFixed,
                    userMessage = _uiState.value.userMessage
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setHorizonCount(count: Int) {
        if (count != 6 && count != 12) return
        _horizonCount.value = count
    }

    fun selectMonth(monthKey: String) {
        repository.setSelectedMonth(monthKey)
        _uiState.update {
            it.copy(userMessage = "Mês ativo alterado para ${FinanceEngine.formatMonthLabel(monthKey)}")
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
