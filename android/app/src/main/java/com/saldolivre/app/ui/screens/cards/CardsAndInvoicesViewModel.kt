package com.saldolivre.app.ui.screens.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.CardInvoice
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.engine.InvoiceStatus
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.ui.screens.invoices.InvoiceItemFilter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max

data class CardsAndInvoicesUiState(
    val selectedTabIndex: Int = 0, // 0: Faturas, 1: Meus Cartões
    val viewMonth: String = FinanceEngine.currentMonthKey(),
    val currentUser: UserProfile = UserProfile(),
    val partner: UserProfile? = null,
    // Faturas
    val invoices: List<CardInvoice> = emptyList(),
    val selectedCardId: String? = null,
    val history: List<CardInvoice> = emptyList(),
    val totalInvoicesAmount: Double = 0.0,
    val totalOpenAmount: Double = 0.0,
    val totalMineAmount: Double = 0.0,
    val totalLimitsAmount: Double = 0.0,
    val totalCommittedAmount: Double = 0.0,
    val nextDueInvoice: CardInvoice? = null,
    val itemFilter: InvoiceItemFilter = InvoiceItemFilter.ALL,
    val searchQuery: String = "",
    val showPayConfirm: Boolean = false,
    val isPaying: Boolean = false,
    val isLoading: Boolean = true,
    // Meus Cartões
    val cardsWithInvoices: List<CardWithInvoice> = emptyList(),
    val isFormOpen: Boolean = false,
    val editingCard: Card? = null,
    val cardToDelete: Card? = null,
    val selectedCardDetailsId: String? = null,
    val userMessage: String? = null
) {
    val selectedInvoice: CardInvoice? get() = invoices.find { it.card.id == selectedCardId }
}

class CardsAndInvoicesViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val viewMonth = MutableStateFlow(repository.selectedMonth.value)
    private val selectedCardId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(
        CardsAndInvoicesUiState(viewMonth = viewMonth.value)
    )
    val uiState: StateFlow<CardsAndInvoicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.getCards(),
                repository.getTransactions(),
                viewMonth,
                selectedCardId
            ) { user, cards, transactions, month, selId ->
                val partner = repository.allUsers.find { it.uid != user.uid }
                val currentDay = LocalDate.now().dayOfMonth

                // 1. Invoices computation via InvoiceEngine
                val invoices = cards.map { card ->
                    InvoiceEngine.buildInvoice(card, month, transactions, user.uid)
                }

                val resolvedId = selId?.takeIf { id -> invoices.any { it.card.id == id } }
                    ?: (invoices.firstOrNull { it.itemsCount > 0 } ?: invoices.firstOrNull())?.card?.id

                val selected = invoices.find { it.card.id == resolvedId }
                val history = selected?.let {
                    InvoiceEngine.buildHistory(it.card, month, transactions, user.uid)
                } ?: emptyList()

                val totalInvoices = FinanceEngine.round2(invoices.sumOf { it.total })
                val totalOpen = FinanceEngine.round2(invoices.sumOf { it.openAmount })
                val totalMine = FinanceEngine.round2(invoices.sumOf { it.myShare })
                val nextDue = invoices.filter { it.openAmount > 0 }.minByOrNull { it.dueDate }

                // 2. Cards with Invoice details (for Meus Cartões tab)
                val cardsWithInvoices = cards.map { card ->
                    val isOwner = card.ownerUid == user.uid
                    val isShared = card.sharedWithUsers.isNotEmpty() || card.sharedWithUids.isNotEmpty()

                    val cardTxs = transactions.filter { tx ->
                        tx.competenceMonth == month &&
                        tx.cardId == card.id &&
                        tx.visibleToUids.contains(user.uid)
                    }.sortedByDescending { it.dueDate }

                    val invoiceTotal = FinanceEngine.round2(
                        cardTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.effectiveAmount } -
                        cardTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.effectiveAmount }
                    ).coerceAtLeast(0.0)

                    val matchingInv = invoices.find { it.card.id == card.id }
                    val futureTotal = matchingInv?.futureInstallmentsTotal ?: run {
                        val futureTxs = transactions.filter { tx ->
                            tx.competenceMonth > month &&
                            tx.cardId == card.id &&
                            tx.reserveCardId == null &&
                            tx.visibleToUids.contains(user.uid)
                        }
                        val futureExpenses = futureTxs.filter { it.type == TransactionType.EXPENSE && it.status != TransactionStatus.PAID }
                        val futureCredits = futureTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.effectiveAmount }
                        FinanceEngine.round2(max(0.0, futureExpenses.sumOf { it.effectiveAmount } - futureCredits))
                    }

                    // Se a fatura atual foi quitada (PAID), ela não consome mais limite. Caso contrário, considera o saldo em aberto.
                    val currentCommitted = if (matchingInv?.status == InvoiceStatus.PAID) 0.0 else (matchingInv?.openAmount ?: invoiceTotal)
                    val totalCommitted = FinanceEngine.round2(currentCommitted + futureTotal)

                    val cardLimit = card.limit?.takeIf { it > 0.0 } ?: 5000.0
                    val available = max(0.0, FinanceEngine.round2(cardLimit - totalCommitted))
                    val usagePct = if (cardLimit > 0) ((totalCommitted / cardLimit) * 100).toInt().coerceIn(0, 100) else 0

                    val isClosed = if (month == FinanceEngine.currentMonthKey()) {
                        currentDay >= card.closingDay
                    } else month < FinanceEngine.currentMonthKey()

                    CardWithInvoice(
                        card = card,
                        isOwner = isOwner,
                        isShared = isShared,
                        invoiceAmount = invoiceTotal,
                        limit = cardLimit,
                        availableLimit = available,
                        usagePercentage = usagePct,
                        isClosed = isClosed,
                        transactions = cardTxs,
                        futureInvoicesAmount = futureTotal,
                        totalCommitted = totalCommitted
                    )
                }

                val totalLimits = FinanceEngine.round2(cardsWithInvoices.sumOf { it.limit })
                val totalCommittedAll = FinanceEngine.round2(cardsWithInvoices.sumOf { it.totalCommitted })

                _uiState.update { current ->
                    current.copy(
                        viewMonth = month,
                        currentUser = user,
                        partner = partner,
                        invoices = invoices,
                        selectedCardId = resolvedId,
                        history = history,
                        totalInvoicesAmount = totalInvoices,
                        totalOpenAmount = totalOpen,
                        totalMineAmount = totalMine,
                        totalLimitsAmount = totalLimits,
                        totalCommittedAmount = totalCommittedAll,
                        nextDueInvoice = nextDue,
                        isLoading = false,
                        cardsWithInvoices = cardsWithInvoices
                    )
                }
            }.collect()
        }
    }

    fun setTabIndex(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }

    fun selectCardAndGoToInvoices(cardId: String) {
        selectedCardId.value = cardId
        _uiState.update { it.copy(selectedTabIndex = 0, showPayConfirm = false) }
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

    fun setFilter(filter: InvoiceItemFilter) {
        _uiState.update { it.copy(itemFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun requestPay() = _uiState.update { it.copy(showPayConfirm = true) }
    fun dismissPay() = _uiState.update { it.copy(showPayConfirm = false) }

    fun confirmPay() {
        val invoice = _uiState.value.selectedInvoice ?: return
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
                    it.copy(
                        isPaying = false,
                        userMessage = "Falha ao pagar fatura: ${e.message ?: "erro desconhecido"}"
                    )
                }
            }
        }
    }

    fun openNewCard() {
        val user = _uiState.value.currentUser
        val newCard = Card(
            id = "new_${System.currentTimeMillis()}",
            ownerUid = user.uid,
            ownerName = user.displayName,
            cardName = "",
            closingDay = 5,
            dueDay = 12,
            sharedWithUsers = emptyList(),
            sharedWithUids = emptyList(),
            frequentThirdParties = listOf("Mãe", "Irmão"),
            color = "violet",
            limit = 5000.0
        )
        _uiState.update { it.copy(editingCard = newCard, isFormOpen = true) }
    }

    fun editCard(card: Card) {
        _uiState.update { it.copy(editingCard = card, isFormOpen = true) }
    }

    fun closeForm() {
        _uiState.update { it.copy(editingCard = null, isFormOpen = false) }
    }

    fun saveCard(card: Card) {
        if (card.cardName.isBlank()) return
        viewModelScope.launch {
            repository.saveCard(card)
            _uiState.update {
                it.copy(
                    editingCard = null,
                    isFormOpen = false,
                    userMessage = "Cartão salvo com sucesso!"
                )
            }
        }
    }

    fun confirmDelete(card: Card) {
        _uiState.update { it.copy(cardToDelete = card) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(cardToDelete = null) }
    }

    fun executeDelete() {
        val toDelete = _uiState.value.cardToDelete ?: return
        viewModelScope.launch {
            repository.deleteCard(toDelete.id)
            _uiState.update {
                it.copy(
                    cardToDelete = null,
                    editingCard = if (it.editingCard?.id == toDelete.id) null else it.editingCard,
                    isFormOpen = if (it.editingCard?.id == toDelete.id) false else it.isFormOpen,
                    userMessage = "Cartão excluído com sucesso."
                )
            }
        }
    }

    fun toggleCardDetails(cardId: String) {
        _uiState.update {
            it.copy(selectedCardDetailsId = if (it.selectedCardDetailsId == cardId) null else cardId)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
