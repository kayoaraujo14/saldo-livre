package com.saldolivre.app.ui.screens.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max

data class CardWithInvoice(
    val card: Card,
    val isOwner: Boolean,
    val isShared: Boolean,
    val invoiceAmount: Double,
    val limit: Double,
    val availableLimit: Double,
    val usagePercentage: Int,
    val isClosed: Boolean,
    val transactions: List<Transaction>
)

data class CardsUiState(
    val currentUser: UserProfile = UserProfile(),
    val partner: UserProfile? = null,
    val selectedMonth: String = "",
    val cardsWithInvoices: List<CardWithInvoice> = emptyList(),
    val totalInvoicesAmount: Double = 0.0,
    val totalLimitsAmount: Double = 0.0,
    val isFormOpen: Boolean = false,
    val editingCard: Card? = null,
    val cardToDelete: Card? = null,
    val selectedCardDetailsId: String? = null,
    val userMessage: String? = null
)

class CardsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardsUiState())
    val uiState: StateFlow<CardsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getCards(),
                repository.getTransactions()
            ) { user, month, cards, transactions ->
                val partner = repository.allUsers.find { it.uid != user.uid }
                val currentDay = LocalDate.now().dayOfMonth

                val cardsInvoices = cards.map { card ->
                    val isOwner = card.ownerUid == user.uid
                    val isShared = card.sharedWithUsers.isNotEmpty() || card.sharedWithUids.isNotEmpty()

                    // Transactions on this card for selected competence month
                    val cardTxs = transactions.filter { tx ->
                        tx.competenceMonth == month &&
                        tx.cardId == card.id &&
                        tx.visibleToUids.contains(user.uid)
                    }.sortedByDescending { it.dueDate }

                    val invoiceTotal = FinanceEngine.round2(
                        cardTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.effectiveAmount } -
                        cardTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.effectiveAmount }
                    ).coerceAtLeast(0.0)

                    val cardLimit = card.limit?.takeIf { it > 0.0 } ?: 5000.0
                    val available = max(0.0, cardLimit - invoiceTotal)
                    val usagePct = if (cardLimit > 0) ((invoiceTotal / cardLimit) * 100).toInt().coerceIn(0, 100) else 0

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
                        transactions = cardTxs
                    )
                }

                val totalInvoices = FinanceEngine.round2(cardsInvoices.sumOf { it.invoiceAmount })
                val totalLimits = FinanceEngine.round2(cardsInvoices.sumOf { it.limit })

                CardsUiState(
                    currentUser = user,
                    partner = partner,
                    selectedMonth = month,
                    cardsWithInvoices = cardsInvoices,
                    totalInvoicesAmount = totalInvoices,
                    totalLimitsAmount = totalLimits,
                    isFormOpen = _uiState.value.isFormOpen,
                    editingCard = _uiState.value.editingCard,
                    cardToDelete = _uiState.value.cardToDelete,
                    selectedCardDetailsId = _uiState.value.selectedCardDetailsId,
                    userMessage = _uiState.value.userMessage
                )
            }.collect { newState ->
                _uiState.value = newState
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
