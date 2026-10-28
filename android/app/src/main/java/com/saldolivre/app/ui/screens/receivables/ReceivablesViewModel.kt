package com.saldolivre.app.ui.screens.receivables

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.ReceivableItem
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SettlementsTab {
    RECEIVABLES,
    PAYABLES
}

@Immutable
data class PayableItem(
    val transactionId: String,
    val description: String,
    val dueDate: String,
    val creditorName: String,
    val amount: Double,
    val cardName: String? = null,
    val myUid: String
)

data class ReceivablesUiState(
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val activeTab: SettlementsTab = SettlementsTab.RECEIVABLES,
    val summary: MonthSummary = MonthSummary(),
    val items: List<ReceivableItem> = emptyList(),
    val payables: List<PayableItem> = emptyList(),
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

class ReceivablesViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _activeTab = MutableStateFlow(SettlementsTab.RECEIVABLES)
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(ReceivablesUiState(isLoading = true))
    val uiState: StateFlow<ReceivablesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getMonthSummary(),
                repository.getTransactions(),
                repository.getCards()
            ) { user, month, summary, transactions, cards ->
                val cardMap = cards.associateBy { it.id }
                val userMap = repository.allUsers.associateBy { it.uid }

                val monthTxs = transactions.filter {
                    it.competenceMonth == month && it.visibleToUids.contains(user.uid)
                }

                val payablesList = mutableListOf<PayableItem>()
                for (tx in monthTxs) {
                    if (tx.paidByUid != user.uid) {
                        val card = tx.cardId?.let { cardMap[it] }
                        val creditor = userMap[tx.paidByUid]?.displayName
                            ?: card?.ownerName?.takeIf { it.isNotBlank() }
                            ?: "Parceiro(a) / Amigo"

                        for (a in tx.allocations) {
                            if (a.type == "PLATFORM_USER" && a.uid == user.uid && !a.reimbursed && a.amount > 0.0) {
                                payablesList.add(
                                    PayableItem(
                                        transactionId = tx.id,
                                        description = tx.description,
                                        dueDate = tx.dueDate,
                                        creditorName = creditor,
                                        amount = a.amount,
                                        cardName = card?.cardName,
                                        myUid = user.uid
                                    )
                                )
                            }
                        }
                    }
                }

                Triple(month, summary, payablesList.sortedBy { it.dueDate })
            }.combine(
                combine(_activeTab, _feedbackMessage) { tab, msg -> tab to msg }
            ) { (month, summary, payablesList), (tab, msg) ->
                ReceivablesUiState(
                    selectedMonth = month,
                    activeTab = tab,
                    summary = summary,
                    items = summary.receivables,
                    payables = payablesList,
                    isLoading = false,
                    feedbackMessage = msg
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectTab(tab: SettlementsTab) {
        _activeTab.value = tab
    }

    fun previousMonth() {
        val prev = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, -1)
        repository.setSelectedMonth(prev)
    }

    fun nextMonth() {
        val next = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, 1)
        repository.setSelectedMonth(next)
    }

    fun goToCurrentMonth() {
        repository.setSelectedMonth(FinanceEngine.currentMonthKey())
    }

    fun markAsReimbursed(item: ReceivableItem) {
        viewModelScope.launch {
            repository.markAllocationReimbursed(item.transactionId, item.name)
            _feedbackMessage.value = "Reembolso de ${item.name} (${FinanceEngine.formatCurrency(item.amount)}) confirmado!"
        }
    }

    fun markPayableAsSettled(item: PayableItem) {
        viewModelScope.launch {
            repository.markAllocationReimbursed(item.transactionId, item.myUid)
            _feedbackMessage.value = "Acerto com ${item.creditorName} (${FinanceEngine.formatCurrency(item.amount)}) baixado!"
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }
}
