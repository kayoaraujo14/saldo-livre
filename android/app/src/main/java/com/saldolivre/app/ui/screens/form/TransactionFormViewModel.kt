package com.saldolivre.app.ui.screens.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class TransactionFormState(
    val type: TransactionType = TransactionType.EXPENSE,
    val description: String = "",
    val amountText: String = "",
    val category: String = "Alimentação",
    val subcategory: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.PIX,
    val selectedCardId: String? = null,
    val splitType: SplitType = SplitType.MINE,
    val externalName: String = "",
    val dueDateText: String = FinanceEngine.todayKey(),
    val status: TransactionStatus = TransactionStatus.PENDING,
    val installmentsCount: Int = 1,
    val availableCards: List<Card> = emptyList(),
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class TransactionFormViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionFormState())
    val state: StateFlow<TransactionFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getCards().collect { cards ->
                _state.update {
                    it.copy(
                        availableCards = cards,
                        selectedCardId = cards.firstOrNull()?.id
                    )
                }
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        _state.update {
            it.copy(
                type = type,
                category = if (type == TransactionType.INCOME) "Receita" else "Alimentação"
            )
        }
    }

    fun onDescriptionChange(desc: String) {
        _state.update { it.copy(description = desc, errorMessage = null) }
    }

    fun onAmountChange(amount: String) {
        val clean = amount.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", ".")
        _state.update { it.copy(amountText = clean, errorMessage = null) }
    }

    fun onCategoryChange(cat: String, subcat: String? = null) {
        _state.update { it.copy(category = cat, subcategory = subcat) }
    }

    fun onPaymentMethodChange(method: PaymentMethod) {
        _state.update { it.copy(paymentMethod = method) }
    }

    fun onCardChange(cardId: String) {
        _state.update { it.copy(selectedCardId = cardId) }
    }

    fun onSplitTypeChange(split: SplitType) {
        _state.update { it.copy(splitType = split) }
    }

    fun onExternalNameChange(name: String) {
        _state.update { it.copy(externalName = name) }
    }

    fun onDueDateChange(date: String) {
        _state.update { it.copy(dueDateText = date) }
    }

    fun onStatusChange(status: TransactionStatus) {
        _state.update { it.copy(status = status) }
    }

    fun onInstallmentsChange(count: Int) {
        _state.update { it.copy(installmentsCount = count.coerceAtLeast(1)) }
    }

    fun saveTransaction() {
        val current = _state.value
        val amount = current.amountText.toDoubleOrNull()

        if (current.description.isBlank()) {
            _state.update { it.copy(errorMessage = "Informe a descrição do lançamento.") }
            return
        }

        if (amount == null || amount <= 0) {
            _state.update { it.copy(errorMessage = "Informe um valor válido maior que zero.") }
            return
        }

        viewModelScope.launch {
            val user = repository.currentUser.value
            val allUsers = repository.allUsers
            val partner = allUsers.find { it.uid != user.uid }
            val card = current.availableCards.find { it.id == current.selectedCardId }

            val total = FinanceEngine.round2(amount)

            if (current.installmentsCount > 1 && current.type == TransactionType.EXPENSE) {
                // Gera parcelas
                val groupId = "inst_grp_" + UUID.randomUUID().toString().take(8)
                val installmentAmount = FinanceEngine.round2(total / current.installmentsCount)
                val installmentList = mutableListOf<Transaction>()

                for (i in 1..current.installmentsCount) {
                    val purchaseMonth = FinanceEngine.shiftMonth(repository.selectedMonth.value, i - 1)
                    val purchaseDate = String.format("%s-05", purchaseMonth)

                    val (dueDate, competenceMonth) = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) {
                        FinanceEngine.calculateCardInvoiceDates(purchaseDate, card)
                    } else {
                        Pair(purchaseDate, purchaseMonth)
                    }

                    val split = FinanceEngine.buildSplit(
                        splitType = current.splitType,
                        total = installmentAmount,
                        myUid = user.uid,
                        myName = user.displayName,
                        partnerUid = partner?.uid,
                        partnerName = partner?.displayName,
                        payerUid = user.uid,
                        externalName = current.externalName
                    )

                    val tx = Transaction(
                        id = "tx_" + UUID.randomUUID().toString().take(8),
                        createdByUid = user.uid,
                        paidByUid = user.uid,
                        cardId = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) card?.id else null,
                        description = FinanceEngine.formatInstallmentDesc(current.description, i, current.installmentsCount),
                        category = current.category,
                        subcategory = current.subcategory,
                        type = current.type,
                        paymentMethod = current.paymentMethod,
                        status = if (i == 1) current.status else TransactionStatus.PENDING,
                        estimatedAmount = if (current.status == TransactionStatus.ESTIMATED) installmentAmount else null,
                        actualAmount = if (current.status != TransactionStatus.ESTIMATED) installmentAmount else null,
                        totalAmount = installmentAmount,
                        dueDate = dueDate,
                        competenceMonth = competenceMonth,
                        visibleToUids = split.visibleToUids,
                        userImpact = split.userImpact,
                        allocations = split.allocations,
                        installmentGroupId = groupId,
                        installmentNumber = i,
                        installmentsTotal = current.installmentsCount,
                        splitType = current.splitType
                    )
                    installmentList.add(tx)
                }
                repository.addTransactions(installmentList)
            } else {
                // Transação individual
                val (dueDate, competenceMonth) = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) {
                    FinanceEngine.calculateCardInvoiceDates(current.dueDateText, card)
                } else {
                    Pair(current.dueDateText, current.dueDateText.take(7))
                }

                val split = FinanceEngine.buildSplit(
                    splitType = current.splitType,
                    total = total,
                    myUid = user.uid,
                    myName = user.displayName,
                    partnerUid = partner?.uid,
                    partnerName = partner?.displayName,
                    payerUid = user.uid,
                    externalName = current.externalName
                )

                val tx = Transaction(
                    id = "tx_" + UUID.randomUUID().toString().take(8),
                    createdByUid = user.uid,
                    paidByUid = user.uid,
                    cardId = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) card?.id else null,
                    description = current.description.trim(),
                    category = current.category,
                    subcategory = current.subcategory,
                    type = current.type,
                    paymentMethod = current.paymentMethod,
                    status = current.status,
                    estimatedAmount = if (current.status == TransactionStatus.ESTIMATED) total else null,
                    actualAmount = if (current.status != TransactionStatus.ESTIMATED) total else null,
                    totalAmount = total,
                    dueDate = dueDate,
                    competenceMonth = competenceMonth,
                    visibleToUids = split.visibleToUids,
                    userImpact = split.userImpact,
                    allocations = split.allocations,
                    splitType = current.splitType
                )
                repository.addTransaction(tx)
            }

            _state.update { it.copy(isSaved = true) }
        }
    }
}
