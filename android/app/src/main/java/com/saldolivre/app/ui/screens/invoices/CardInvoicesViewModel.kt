package com.saldolivre.app.ui.screens.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.CardInvoice
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.engine.InvoiceStatus
import com.saldolivre.app.data.engine.PredictiveInvoiceAlert
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InvoiceItemFilter(val label: String) {
    ALL("Todos"),
    MINE("100% Meu"),
    THIRD_PARTY("Terceiros"),
    OPEN("Em aberto"),
    PAID("Pagos"),
    INSTALLMENTS("Parcelados")
}

data class CardInvoicesUiState(
    val viewMonth: String = FinanceEngine.currentMonthKey(),
    val currentUser: com.saldolivre.app.data.model.UserProfile = com.saldolivre.app.data.model.UserProfile(),
    val invoices: List<CardInvoice> = emptyList(),
    val selectedCardId: String? = null,
    val history: List<CardInvoice> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalOpen: Double = 0.0,
    val totalMine: Double = 0.0,
    val nextDue: CardInvoice? = null,
    val itemFilter: InvoiceItemFilter = InvoiceItemFilter.ALL,
    val selectedCategory: String? = null,
    val predictiveAlert: PredictiveInvoiceAlert? = null,
    val selectedParticipantName: String? = null,
    val searchQuery: String = "",
    val showPayConfirm: Boolean = false,
    val isPaying: Boolean = false,
    val isLoading: Boolean = true,
    val userMessage: String? = null
) {
    val selected: CardInvoice? get() = invoices.find { it.card.id == selectedCardId }
}

class CardInvoicesViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private var initialMonthRouted = false
    private val viewMonth = MutableStateFlow(repository.selectedMonth.value)
    private val selectedCardId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(CardInvoicesUiState(viewMonth = viewMonth.value))
    val uiState: StateFlow<CardInvoicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.getCards(),
                repository.getTransactions(),
                viewMonth,
                selectedCardId
            ) { user, cards, transactions, month, selId ->
                // Roteamento inteligente na inicialização
                if (!initialMonthRouted && cards.isNotEmpty()) {
                    initialMonthRouted = true
                    val currentMonthKey = FinanceEngine.currentMonthKey()
                    val primaryCard = cards.firstOrNull { it.ownerUid == user.uid } ?: cards.firstOrNull()
                    if (primaryCard != null) {
                        val currentMonthInvoice = InvoiceEngine.buildInvoice(primaryCard, currentMonthKey, transactions, user.uid)
                        if (currentMonthInvoice.status == InvoiceStatus.PAID) {
                            var checkMonth = FinanceEngine.shiftMonth(currentMonthKey, 1)
                            var targetOpenMonth: String? = null
                            for (step in 1..12) {
                                val candidateInv = InvoiceEngine.buildInvoice(primaryCard, checkMonth, transactions, user.uid)
                                if (candidateInv.status != InvoiceStatus.PAID && candidateInv.openAmount > 0) {
                                    targetOpenMonth = checkMonth
                                    break
                                }
                                checkMonth = FinanceEngine.shiftMonth(checkMonth, 1)
                            }
                            if (targetOpenMonth != null && targetOpenMonth != month) {
                                viewMonth.value = targetOpenMonth
                                return@combine _uiState.value
                            }
                        }
                    }
                }

                val rawInvoices = cards.map { InvoiceEngine.buildInvoice(it, month, transactions, user.uid) }

                val resolvedId = selId?.takeIf { id -> rawInvoices.any { it.card.id == id } }
                    ?: (rawInvoices.firstOrNull { it.itemsCount > 0 } ?: rawInvoices.firstOrNull())?.card?.id

                val selected = rawInvoices.find { it.card.id == resolvedId }
                val history = selected?.let {
                    InvoiceEngine.buildHistory(it.card, month, transactions, user.uid)
                } ?: emptyList()

                val predictiveAlert = selected?.let {
                    InvoiceEngine.computePredictiveAlert(it, transactions, history)
                }

                val invoices = rawInvoices.map { inv ->
                    if (inv.card.id == resolvedId && predictiveAlert != null) {
                        inv.copy(predictiveAlert = predictiveAlert)
                    } else inv
                }

                _uiState.value.copy(
                    viewMonth = month,
                    currentUser = user,
                    invoices = invoices,
                    selectedCardId = resolvedId,
                    history = history,
                    predictiveAlert = predictiveAlert,
                    totalAmount = FinanceEngine.round2(invoices.sumOf { it.total }),
                    totalOpen = FinanceEngine.round2(invoices.sumOf { it.openAmount }),
                    totalMine = FinanceEngine.round2(invoices.sumOf { it.myShare }),
                    nextDue = invoices.filter { it.openAmount > 0 }.minByOrNull { it.dueDate },
                    isLoading = false
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun previousMonth() = changeMonth(-1)
    fun nextMonth() = changeMonth(1)

    fun goToMonth(monthKey: String) {
        viewMonth.value = monthKey
        _uiState.update { it.copy(showPayConfirm = false, selectedParticipantName = null, selectedCategory = null) }
    }

    private fun changeMonth(delta: Int) = goToMonth(FinanceEngine.shiftMonth(viewMonth.value, delta))

    fun selectCard(cardId: String) {
        selectedCardId.value = cardId
        _uiState.update { it.copy(showPayConfirm = false, selectedParticipantName = null, selectedCategory = null) }
    }

    fun toggleCategoryFilter(category: String) {
        _uiState.update { current ->
            val next = if (current.selectedCategory.equals(category, ignoreCase = true)) null else category
            current.copy(selectedCategory = next)
        }
    }

    fun clearCategoryFilter() {
        _uiState.update { it.copy(selectedCategory = null) }
    }

    fun toggleParticipantFilter(name: String) {
        _uiState.update { current ->
            val next = if (current.selectedParticipantName == name) null else name
            current.copy(selectedParticipantName = next)
        }
    }

    fun clearParticipantFilter() {
        _uiState.update { it.copy(selectedParticipantName = null) }
    }

    fun setFilter(filter: InvoiceItemFilter) = _uiState.update { it.copy(itemFilter = filter) }
    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun requestPay() = _uiState.update { it.copy(showPayConfirm = true) }
    fun dismissPay() = _uiState.update { it.copy(showPayConfirm = false) }

    /** Quita a fatura marcando todos os lançamentos de despesa em aberto como pagos. */
    fun confirmPay() {
        val invoice = _uiState.value.selected ?: return
        val unpaid = invoice.unpaidExpenses
        if (unpaid.isEmpty()) return
        _uiState.update { it.copy(isPaying = true) }
        viewModelScope.launch {
            try {
                unpaid.forEach { repository.setTransactionStatus(it.id, TransactionStatus.PAID) }
                _uiState.update {
                    it.copy(
                        isPaying = false,
                        showPayConfirm = false,
                        userMessage = "Fatura ${invoice.card.cardName} paga: ${FinanceEngine.formatCurrency(invoice.openAmount)}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isPaying = false, userMessage = "Falha ao pagar fatura: ${e.message ?: "erro desconhecido"}")
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(userMessage = null) }
}
