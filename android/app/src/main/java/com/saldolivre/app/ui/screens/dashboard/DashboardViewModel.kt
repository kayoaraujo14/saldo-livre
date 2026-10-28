package com.saldolivre.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.RecurringBill
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
    val recurringBills: List<RecurringBill> = emptyList(),
    val pendingRecurringLaunches: List<FinanceEngine.PendingRecurringCandidate> = emptyList(),
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

    init {
        viewModelScope.launch {
            combine(
                combine(repository.currentUser, repository.selectedMonth) { u, m -> Pair(u, m) },
                repository.getMonthSummary(),
                repository.getTransactions(),
                repository.getRecurringBills(),
                repository.getCards()
            ) { userMonth, summary, allTxs, bills, cards ->
                val user = userMonth.first
                val month = userMonth.second
                val monthTxs = allTxs
                    .filter { it.competenceMonth == month && it.visibleToUids.contains(user.uid) }
                    .sortedByDescending { it.createdAt }

                val consolidated = InvoiceEngine.consolidateMonthTransactions(
                    monthTxs,
                    allTxs,
                    cards,
                    user.uid,
                    month,
                    true
                )
                val recent = consolidated.take(6)

                val shouldShowPopup = summary.pendingClosureSurplus != null && 
                    summary.pendingClosureSurplus > 0.0 && 
                    !_uiState.value.closureDialogDismissed

                val todayStr = java.time.LocalDate.now().toString()
                val monthResolved = user.resolvedRecurring[month] ?: emptyList()

                val pending = if (month == FinanceEngine.currentMonthKey()) {
                    FinanceEngine.getPendingRecurringLaunches(
                        bills = bills,
                        all = allTxs,
                        monthKey = month,
                        todayStr = todayStr,
                        me = user,
                        resolvedIds = monthResolved
                    )
                } else emptyList()

                DashboardUiState(
                    currentUser = user,
                    allUsers = repository.allUsers,
                    selectedMonth = month,
                    summary = summary,
                    recentTransactions = recent,
                    allTransactions = allTxs.filter { it.visibleToUids.contains(user.uid) },
                    recurringBills = bills,
                    pendingRecurringLaunches = pending,
                    isLoading = false,
                    showClosureDialog = shouldShowPopup || _uiState.value.showClosureDialog,
                    closureDialogDismissed = _uiState.value.closureDialogDismissed,
                    autoLaunchNotice = _uiState.value.autoLaunchNotice
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun confirmLaunchCandidate(candidate: FinanceEngine.PendingRecurringCandidate, asPaid: Boolean = false) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser
            val month = _uiState.value.selectedMonth
            val isCardBill = candidate.paymentMethod == com.saldolivre.app.data.model.PaymentMethod.CREDIT_CARD || !candidate.cardId.isNullOrBlank()
            val txStatus = when {
                isCardBill -> com.saldolivre.app.data.model.TransactionStatus.PENDING
                asPaid -> com.saldolivre.app.data.model.TransactionStatus.PAID
                else -> com.saldolivre.app.data.model.TransactionStatus.ESTIMATED
            }
            val split = FinanceEngine.buildSplit(
                splitType = candidate.bill?.splitType ?: com.saldolivre.app.data.model.SplitType.MINE,
                total = candidate.amount,
                myUid = user.uid,
                myName = user.displayName,
                partnerUid = null,
                partnerName = null,
                payerUid = user.uid
            )
            val tx = Transaction(
                id = "tx_gen_${java.util.UUID.randomUUID().toString().take(8)}",
                createdByUid = user.uid,
                paidByUid = user.uid,
                cardId = candidate.cardId,
                description = candidate.description,
                category = candidate.category,
                subcategory = candidate.subcategory,
                type = candidate.type,
                paymentMethod = if (isCardBill) com.saldolivre.app.data.model.PaymentMethod.CREDIT_CARD else candidate.paymentMethod,
                status = txStatus,
                estimatedAmount = candidate.amount,
                actualAmount = if (isCardBill || asPaid) candidate.amount else null,
                totalAmount = candidate.amount,
                dueDate = candidate.dueDate,
                competenceMonth = month,
                recurringBillId = candidate.id,
                allocations = split.allocations,
                userImpact = split.userImpact,
                visibleToUids = split.visibleToUids,
                createdAt = System.currentTimeMillis()
            )
            repository.addTransaction(tx)
            repository.markRecurringResolved(month, candidate.id)
        }
    }

    fun confirmAllCandidates(asPaid: Boolean = false) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser
            val month = _uiState.value.selectedMonth
            val candidates = _uiState.value.pendingRecurringLaunches.filter { it.matchedManualTx == null }
            val txs = candidates.map { candidate ->
                val isCardBill = candidate.paymentMethod == com.saldolivre.app.data.model.PaymentMethod.CREDIT_CARD || !candidate.cardId.isNullOrBlank()
                val txStatus = when {
                    isCardBill -> com.saldolivre.app.data.model.TransactionStatus.PENDING
                    asPaid -> com.saldolivre.app.data.model.TransactionStatus.PAID
                    else -> com.saldolivre.app.data.model.TransactionStatus.ESTIMATED
                }
                val split = FinanceEngine.buildSplit(
                    splitType = candidate.bill?.splitType ?: com.saldolivre.app.data.model.SplitType.MINE,
                    total = candidate.amount,
                    myUid = user.uid,
                    myName = user.displayName,
                    partnerUid = null,
                    partnerName = null,
                    payerUid = user.uid
                )
                Transaction(
                    id = "tx_gen_${java.util.UUID.randomUUID().toString().take(8)}",
                    createdByUid = user.uid,
                    paidByUid = user.uid,
                    cardId = candidate.cardId,
                    description = candidate.description,
                    category = candidate.category,
                    subcategory = candidate.subcategory,
                    type = candidate.type,
                    paymentMethod = if (isCardBill) com.saldolivre.app.data.model.PaymentMethod.CREDIT_CARD else candidate.paymentMethod,
                    status = txStatus,
                    estimatedAmount = candidate.amount,
                    actualAmount = if (isCardBill || asPaid) candidate.amount else null,
                    totalAmount = candidate.amount,
                    dueDate = candidate.dueDate,
                    competenceMonth = month,
                    recurringBillId = candidate.id,
                    allocations = split.allocations,
                    userImpact = split.userImpact,
                    visibleToUids = split.visibleToUids,
                    createdAt = System.currentTimeMillis()
                )
            }
            if (txs.isNotEmpty()) {
                repository.addTransactions(txs)
                candidates.forEach { repository.markRecurringResolved(month, it.id) }
            }
        }
    }

    fun linkManualTransaction(candidateId: String, manualTx: Transaction) {
        viewModelScope.launch {
            val month = _uiState.value.selectedMonth
            repository.updateTransaction(manualTx.copy(recurringBillId = candidateId))
            repository.markRecurringResolved(month, candidateId)
        }
    }

    fun skipRecurring(candidateId: String) {
        viewModelScope.launch {
            val month = _uiState.value.selectedMonth
            repository.markRecurringResolved(month, candidateId)
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

    fun createRefund(origTx: Transaction, refundAmount: Double) {
        viewModelScope.launch {
            try {
                val refundTx = FinanceEngine.createRefundTransaction(origTx, refundAmount, _uiState.value.currentUser)
                repository.addTransaction(refundTx)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao registrar estorno", e)
            }
        }
    }
}
