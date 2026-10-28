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
    val projections: List<FutureMonthProjection> = emptyList(),
    val averageFreeBalance: Double = 0.0,
    val totalProjectedIncome: Double = 0.0,
    val totalProjectedExpense: Double = 0.0,
    val isLoading: Boolean = false,
    val userMessage: String? = null
)

class FutureProjectionsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FutureProjectionsUiState())
    val uiState: StateFlow<FutureProjectionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getTransactions(),
                repository.getRecurringBills()
            ) { user, selectedMonth, transactions, recurringBills ->
                val curMonth = FinanceEngine.currentMonthKey()

                val projectionsList = FinanceEngine.projectFutureMonths(
                    all = transactions,
                    bills = recurringBills,
                    profile = user,
                    uid = user.uid,
                    startMonthKey = curMonth,
                    count = 6
                )

                val avgFree = if (projectionsList.isNotEmpty()) {
                    FinanceEngine.round2(projectionsList.sumOf { it.projectedFreeBalance } / projectionsList.size)
                } else 0.0

                val totIncome = FinanceEngine.round2(projectionsList.sumOf { it.projectedIncome })
                val totExpense = FinanceEngine.round2(projectionsList.sumOf { it.projectedExpense })

                FutureProjectionsUiState(
                    currentUser = user,
                    selectedMonth = selectedMonth,
                    currentMonth = curMonth,
                    projections = projectionsList,
                    averageFreeBalance = avgFree,
                    totalProjectedIncome = totIncome,
                    totalProjectedExpense = totExpense,
                    userMessage = _uiState.value.userMessage
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
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
