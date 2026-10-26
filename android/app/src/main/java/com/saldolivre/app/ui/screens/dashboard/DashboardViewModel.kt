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

data class AutoLaunchNotice(
    val count: Int,
    val bills: List<Transaction>
)

data class DashboardUiState(
    val currentUser: UserProfile = UserProfile(),
    val allUsers: List<UserProfile> = emptyList(),
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val summary: MonthSummary = MonthSummary(),
    val recentTransactions: List<Transaction> = emptyList(),
    val allTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val showClosureDialog: Boolean = false,
    val closureDialogDismissed: Boolean = false,
    val autoLaunchNotice: AutoLaunchNotice? = null
)

class DashboardViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var lastCheckedRunKey = ""

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
                    allTransactions = allTxs.filter { it.visibleToUids.contains(user.uid) },
                    isLoading = false,
                    showClosureDialog = shouldShowPopup || _uiState.value.showClosureDialog,
                    closureDialogDismissed = _uiState.value.closureDialogDismissed,
                    autoLaunchNotice = _uiState.value.autoLaunchNotice
                )
            }.collect { newState ->
                _uiState.value = newState
                checkAndAutoLaunchBills(newState.selectedMonth)
            }
        }
    }

    private fun checkAndAutoLaunchBills(month: String) {
        val todayStr = java.time.LocalDate.now().toString()
        val runKey = "${month}_$todayStr"
        if (lastCheckedRunKey == runKey) return
        lastCheckedRunKey = runKey

        viewModelScope.launch {
            try {
                val res = repository.autoLaunchDueBillsForMonth(month, todayStr)
                if (res.created.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            autoLaunchNotice = AutoLaunchNotice(
                                count = res.created.size,
                                bills = res.created
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao executar lançamento automático", e)
            }
        }
    }

    fun dismissAutoLaunchNotice() {
        _uiState.update { it.copy(autoLaunchNotice = null) }
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
            try {
                repository.saveMonthClosure(closure)
                _uiState.update { it.copy(showClosureDialog = false) }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao salvar fechamento de mês", e)
                _uiState.update { it.copy(showClosureDialog = false) }
            }
        }
    }

    fun undoMonthClosure(closedMonth: String) {
        viewModelScope.launch {
            try {
                repository.deleteMonthClosure(closedMonth)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao desfazer fechamento de mês", e)
            }
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
