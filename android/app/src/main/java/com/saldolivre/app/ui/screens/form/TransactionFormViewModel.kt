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
    val isEditing: Boolean = false,
    val editingTx: Transaction? = null,
    val editScope: String = "all", // "all" | "single"
    val type: TransactionType = TransactionType.EXPENSE,
    val description: String = "",
    val amountText: String = "",
    val category: String = "Alimentação",
    val subcategory: String? = "Supermercado",
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
    private val repository: FinanceRepository,
    private val editingTxId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionFormState())
    val state: StateFlow<TransactionFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getCards().collect { cards ->
                _state.update { current ->
                    current.copy(
                        availableCards = cards,
                        selectedCardId = current.selectedCardId ?: cards.firstOrNull()?.id
                    )
                }
            }
        }

        if (!editingTxId.isNullOrBlank()) {
            viewModelScope.launch {
                val tx = repository.getTransactions().first().find { it.id == editingTxId }
                if (tx != null) {
                    val meUid = repository.currentUser.value.uid
                    val baseDesc = if (tx.isInstallment) {
                        FinanceEngine.stripInstallmentSuffix(tx.description)
                    } else {
                        tx.description
                    }

                    val extAlloc = tx.allocations.find { it.type == "EXTERNAL" }
                    val inferredSplit = tx.splitType ?: when {
                        extAlloc != null -> SplitType.EXTERNAL
                        !tx.assignedFriendUid.isNullOrBlank() -> SplitType.FRIEND
                        tx.allocations.size > 1 -> SplitType.SPLIT_50
                        tx.allocations.firstOrNull()?.uid != null && tx.allocations.first().uid != meUid -> SplitType.PARTNER
                        else -> SplitType.MINE
                    }

                    val defaultSubcat = tx.subcategory
                        ?: CategoryRegistry.get(tx.category).subcategories.firstOrNull()

                    _state.update { current ->
                        current.copy(
                            isEditing = true,
                            editingTx = tx,
                            editScope = "all",
                            type = tx.type,
                            description = baseDesc,
                            amountText = tx.effectiveAmount.toString(),
                            category = tx.category,
                            subcategory = defaultSubcat,
                            paymentMethod = tx.paymentMethod,
                            selectedCardId = tx.cardId ?: current.selectedCardId,
                            splitType = inferredSplit,
                            externalName = extAlloc?.name ?: "",
                            dueDateText = tx.dueDate,
                            status = tx.status,
                            installmentsCount = tx.installmentsTotal ?: 1
                        )
                    }
                }
            }
        }
    }

    fun onEditScopeChange(scope: String) {
        _state.update { it.copy(editScope = scope) }
    }

    fun onTypeChange(type: TransactionType) {
        val defaultCat = if (type == TransactionType.INCOME) "Receita" else "Alimentação"
        val defaultSubcat = CategoryRegistry.get(defaultCat).subcategories.firstOrNull()
        _state.update {
            it.copy(
                type = type,
                category = defaultCat,
                subcategory = defaultSubcat,
                installmentsCount = if (type == TransactionType.INCOME) 1 else it.installmentsCount
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
        val resolvedSubcat = subcat ?: CategoryRegistry.get(cat).subcategories.firstOrNull()
        _state.update { it.copy(category = cat, subcategory = resolvedSubcat) }
    }

    fun onSubcategoryChange(subcat: String) {
        _state.update { it.copy(subcategory = subcat) }
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

            if (current.isEditing && current.editingTx != null) {
                val origTx = current.editingTx
                val groupId = origTx.installmentGroupId

                if (!groupId.isNullOrBlank() && current.editScope == "all") {
                    repository.updateInstallmentGroup(
                        groupId = groupId,
                        input = InstallmentGroupUpdateInput(
                            description = current.description.trim(),
                            amount = total,
                            category = current.category,
                            subcategory = current.subcategory,
                            paymentMethod = current.paymentMethod,
                            cardId = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) card?.id else null,
                            splitType = current.splitType,
                            externalName = if (current.splitType == SplitType.EXTERNAL) current.externalName.trim() else null,
                            friend = if (!origTx.assignedFriendUid.isNullOrBlank() && !origTx.assignedFriendName.isNullOrBlank()) {
                                FriendSplitInput(
                                    uid = origTx.assignedFriendUid,
                                    name = origTx.assignedFriendName,
                                    pct = origTx.friendSharePct ?: 100.0
                                )
                            } else null,
                            date = if (current.dueDateText != origTx.dueDate) current.dueDateText else null,
                            status = current.status
                        )
                    )

                    repository.logActivity(
                        ActivityLog(
                            actionType = ActivityActionType.TRANSACTION_UPDATED,
                            title = current.description.trim(),
                            description = "Série parcelada atualizada (${origTx.installmentsTotal ?: 1}x) • ${current.category}",
                            amount = total,
                            category = current.category,
                            competenceMonth = origTx.competenceMonth,
                            userUid = user.uid,
                            userName = user.displayName
                        )
                    )
                } else {
                    val dateChanged = current.dueDateText != origTx.dueDate ||
                            current.paymentMethod != origTx.paymentMethod ||
                            (current.paymentMethod == PaymentMethod.CREDIT_CARD && card?.id != origTx.cardId)

                    val (dueDate, competenceMonth) = if (dateChanged) {
                        if (current.paymentMethod == PaymentMethod.CREDIT_CARD) {
                            FinanceEngine.calculateCardInvoiceDates(current.dueDateText, card)
                        } else {
                            val cleanDate = current.dueDateText.trim()
                            val month = if (cleanDate.length >= 7) cleanDate.take(7) else origTx.competenceMonth
                            Pair(cleanDate, month)
                        }
                    } else {
                        Pair(origTx.dueDate, origTx.competenceMonth)
                    }

                    val payerUid = if (current.paymentMethod == PaymentMethod.CREDIT_CARD && card != null) {
                        card.ownerUid
                    } else {
                        origTx.paidByUid.ifBlank { user.uid }
                    }

                    val split = FinanceEngine.buildSplit(
                        splitType = current.splitType,
                        total = total,
                        myUid = user.uid,
                        myName = user.displayName,
                        partnerUid = partner?.uid,
                        partnerName = partner?.displayName,
                        payerUid = payerUid,
                        externalName = current.externalName.trim(),
                        assignedFriendUid = origTx.assignedFriendUid,
                        assignedFriendName = origTx.assignedFriendName,
                        friendSharePct = origTx.friendSharePct
                    )

                    val formattedDesc = if (origTx.installmentNumber != null && origTx.installmentsTotal != null) {
                        FinanceEngine.formatInstallmentDesc(
                            current.description.trim(),
                            origTx.installmentNumber,
                            origTx.installmentsTotal
                        )
                    } else {
                        current.description.trim()
                    }

                    val updatedTx = origTx.copy(
                        paidByUid = payerUid,
                        cardId = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) card?.id else null,
                        description = formattedDesc,
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

                    repository.updateTransaction(updatedTx)

                    repository.logActivity(
                        ActivityLog(
                            actionType = ActivityActionType.TRANSACTION_UPDATED,
                            title = updatedTx.description,
                            description = "${updatedTx.category}${updatedTx.subcategory?.let { " › $it" } ?: ""}",
                            amount = total,
                            category = updatedTx.category,
                            competenceMonth = updatedTx.competenceMonth,
                            userUid = user.uid,
                            userName = user.displayName
                        )
                    )
                }

                _state.update { it.copy(isSaved = true) }
                return@launch
            }

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
                repository.logActivity(
                    ActivityLog(
                        actionType = ActivityActionType.TRANSACTION_CREATED,
                        title = current.description.trim(),
                        description = "Compra parcelada criada (${current.installmentsCount}x) • ${current.category}",
                        amount = total,
                        category = current.category,
                        competenceMonth = repository.selectedMonth.value,
                        userUid = user.uid,
                        userName = user.displayName
                    )
                )
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
                repository.logActivity(
                    ActivityLog(
                        actionType = ActivityActionType.TRANSACTION_CREATED,
                        title = tx.description,
                        description = "${tx.category}${tx.subcategory?.let { " › $it" } ?: ""}",
                        amount = total,
                        category = tx.category,
                        competenceMonth = tx.competenceMonth,
                        userUid = user.uid,
                        userName = user.displayName
                    )
                )
            }

            _state.update { it.copy(isSaved = true) }
        }
    }
}
