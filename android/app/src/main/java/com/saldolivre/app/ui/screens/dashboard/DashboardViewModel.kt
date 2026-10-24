package com.saldolivre.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val currentUser: UserProfile = UserProfile(),
    val allUsers: List<UserProfile> = emptyList(),
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val summary: MonthSummary = MonthSummary(),
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val showClosureDialog: Boolean = false,
    val closureDialogDismissed: Boolean = false
)

class DashboardViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getMonthSummary(),
                repository.getTransactions()
            ) { user, month, summary, allTxs ->
                val recent = allTxs
                    .filter { it.competenceMonth == month && it.visibleToUids.contains(user.uid) }
                    .sortedByDescending { it.createdAt }
                    .take(6)

                val shouldShowPopup = summary.pendingClosureSurplus != null && 
                    summary.pendingClosureSurplus > 0.0 && 
                    !_uiState.value.closureDialogDismissed

                DashboardUiState(
                    currentUser = user,
                    allUsers = repository.allUsers,
                    selectedMonth = month,
                    summary = summary,
                    recentTransactions = recent,
                    isLoading = false,
                    showClosureDialog = shouldShowPopup || _uiState.value.showClosureDialog,
                    closureDialogDismissed = _uiState.value.closureDialogDismissed
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun openClosureDialog() {
        _uiState.update { it.copy(showClosureDialog = true) }
    }

    fun dismissClosureDialog() {
        _uiState.update { it.copy(showClosureDialog = false, closureDialogDismissed = true) }
    }

    fun confirmMonthClosure(carryover: Double, invested: Double, cushion: Double) {
        val summary = _uiState.value.summary
        val closedMonth = summary.pendingClosureMonth ?: return
        val targetMonth = _uiState.value.selectedMonth
        val surplus = summary.pendingClosureSurplus ?: (carryover + invested + cushion)
        val uid = _uiState.value.currentUser.uid

        val closure = com.saldolivre.app.data.model.MonthClosure(
            uid = uid,
            closedMonth = closedMonth,
            targetMonth = targetMonth,
            totalSurplus = surplus,
            carryoverToNextMonth = carryover,
            investedAmount = invested,
            cushionAmount = cushion
        )
        viewModelScope.launch {
            repository.saveMonthClosure(closure)
            _uiState.update { it.copy(showClosureDialog = false) }
        }
    }

    fun switchUser(uid: String) {
        repository.switchUser(uid)
    }

    fun previousMonth() {
        val prev = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, -1)
        repository.setSelectedMonth(prev)
    }

    fun nextMonth() {
        val next = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, 1)
        repository.setSelectedMonth(next)
    }

    fun currentMonth() {
        repository.setSelectedMonth(FinanceEngine.currentMonthKey())
    }

    fun markTransactionPaid(id: String) {
        viewModelScope.launch {
            repository.markTransactionPaid(id)
        }
    }

    fun toggleTransactionStatus(id: String) {
        viewModelScope.launch {
            repository.toggleTransactionStatus(id)
        }
    }

    fun setStatus(id: String, status: TransactionStatus, actualAmount: Double? = null) {
        viewModelScope.launch {
            repository.setTransactionStatus(id, status, actualAmount)
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun deleteInstallmentGroup(groupId: String) {
        viewModelScope.launch {
            repository.deleteInstallmentGroup(groupId)
        }
    }

    fun generateBills() {
        viewModelScope.launch {
            repository.generateBillsForMonth(_uiState.value.selectedMonth)
        }
    }
}
