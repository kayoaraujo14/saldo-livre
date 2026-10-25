package com.saldolivre.app.ui.screens.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.CardInvoice
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
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
    THIRD_PARTY("Terceiros"),
    OPEN("Em aberto"),
    PAID("Pagos"),
    INSTALLMENTS("Parcelados")
}

data class CardInvoicesUiState(
    val viewMonth: String = FinanceEngine.currentMonthKey(),
    val invoices: List<CardInvoice> = emptyList(),
    val selectedCardId: String? = null,
    val history: List<CardInvoice> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalOpen: Double = 0.0,
    val totalMine: Double = 0.0,
    val nextDue: CardInvoice? = null,
    val itemFilter: InvoiceItemFilter = InvoiceItemFilter.ALL,
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
                val invoices = cards.map { InvoiceEngine.buildInvoice(it, month, transactions, user.uid) }

                val resolvedId = selId?.takeIf { id -> invoices.any { it.card.id == id } }
                    ?: (invoices.firstOrNull { it.itemsCount > 0 } ?: invoices.firstOrNull())?.card?.id

                val selected = invoices.find { it.card.id == resolvedId }
                val history = selected?.let {
                    InvoiceEngine.buildHistory(it.card, month, transactions, user.uid)
                } ?: emptyList()

                _uiState.value.copy(
                    viewMonth = month,
                    invoices = invoices,
                    selectedCardId = resolvedId,
                    history = history,
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
        _uiState.update { it.copy(showPayConfirm = false) }
    }

    private fun changeMonth(delta: Int) = goToMonth(FinanceEngine.shiftMonth(viewMonth.value, delta))

    fun selectCard(cardId: String) {
        selectedCardId.value = cardId
        _uiState.update { it.copy(showPayConfirm = false) }
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
