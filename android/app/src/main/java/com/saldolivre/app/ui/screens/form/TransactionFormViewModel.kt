package com.saldolivre.app.ui.screens.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.ui.screens.friends.FriendItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

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
    val dueDateText: String = FinanceEngine.todayFormatted(),
    val status: TransactionStatus = TransactionStatus.PENDING,
    val isInstallment: Boolean = false,
    val installmentMode: String = "total", // "total" | "per_installment"
    val installmentsCount: Int = 3,
    val availableCards: List<Card> = emptyList(),
    val friends: List<FriendItem> = emptyList(),
    val partner: UserProfile? = null,
    val assignedFriendUid: String? = null,
    val assignedFriendName: String? = null,
    val friendSharePct: Double? = null,
    val customParticipants: List<CustomParticipantRow> = emptyList(),
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
            combine(
                repository.currentUser,
                repository.getCards(),
                repository.getFriendships()
            ) { user, cards, friendships ->
                val activeFriends = friendships.filter { it.status == FriendshipStatus.ACCEPTED }.map { f ->
                    val isRequester = f.requesterUid == user.uid
                    FriendItem(
                        friendshipId = f.id,
                        friendUid = if (isRequester) f.recipientUid else f.requesterUid,
                        friendName = if (isRequester) f.recipientName else f.requesterName,
                        friendEmail = if (isRequester) f.recipientEmail else f.requesterEmail
                    )
                }
                val partnerUser = FinanceEngine.resolvePartner(user.uid, cards, repository.allUsers)
                _state.update { current ->
                    current.copy(
                        availableCards = cards,
                        selectedCardId = current.selectedCardId ?: cards.firstOrNull()?.id,
                        friends = activeFriends,
                        partner = partnerUser
                    )
                }
            }.collect()
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
                    val total = tx.effectiveAmount

                    val customRows = if (tx.splitType == SplitType.CUSTOM || tx.allocations.size > 2) {
                        tx.allocations.mapIndexed { i, a ->
                            val isMe = a.uid == meUid
                            val pct = if (total > 0) FinanceEngine.round2((a.amount / total) * 100.0) else 0.0
                            CustomParticipantRow(
                                id = "p_${i}_${a.uid ?: a.name}",
                                type = if (isMe) "me" else if (a.type == "EXTERNAL") "external" else "friend",
                                uid = a.uid,
                                name = a.name,
                                amount = a.amount,
                                percentage = pct
                            )
                        }
                    } else if (!tx.assignedFriendUid.isNullOrBlank()) {
                        val pct = tx.friendSharePct ?: 50.0
                        val friendAmt = FinanceEngine.round2((total * pct) / 100.0)
                        val myAmt = FinanceEngine.round2(total - friendAmt)
                        listOf(
                            CustomParticipantRow(
                                id = "me",
                                type = "me",
                                uid = meUid,
                                name = repository.currentUser.value.displayName.ifBlank { "Você" },
                                amount = myAmt,
                                percentage = 100.0 - pct
                            ),
                            CustomParticipantRow(
                                id = "fr_${tx.assignedFriendUid}",
                                type = "friend",
                                uid = tx.assignedFriendUid,
                                name = tx.assignedFriendName ?: "Amigo",
                                amount = friendAmt,
                                percentage = pct
                            )
                        )
                    } else emptyList()

                    val inferredSplit = tx.splitType ?: when {
                        customRows.isNotEmpty() -> SplitType.CUSTOM
                        extAlloc != null -> SplitType.EXTERNAL
                        !tx.assignedFriendUid.isNullOrBlank() -> SplitType.CUSTOM
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
                            externalName = tx.externalName ?: extAlloc?.name ?: "",
                            assignedFriendUid = tx.assignedFriendUid,
                            assignedFriendName = tx.assignedFriendName,
                            friendSharePct = tx.friendSharePct,
                            customParticipants = customRows,
                            dueDateText = FinanceEngine.formatFullDate(tx.dueDate),
                            status = tx.status,
                            isInstallment = !tx.installmentGroupId.isNullOrBlank() || (tx.installmentsTotal != null && tx.installmentsTotal > 1),
                            installmentMode = "per_installment",
                            installmentsCount = tx.installmentsTotal ?: 1
                        )
                    }
                }
            }
        }
    }

    private fun getInitialCustomParticipants(
        total: Double,
        meUid: String,
        meName: String,
        friends: List<FriendItem>,
        partner: UserProfile?
    ): List<CustomParticipantRow> {
        val curTot = if (total > 0.0) total else 0.0
        val half = FinanceEngine.round2(curTot / 2.0)
        val secondHalf = FinanceEngine.round2(curTot - half)

        val list = mutableListOf<CustomParticipantRow>()
        list.add(
            CustomParticipantRow(
                id = "me",
                type = "me",
                uid = meUid,
                name = meName.ifBlank { "Você" },
                amount = half,
                percentage = 50.0
            )
        )

        if (friends.isNotEmpty()) {
            val firstFriend = friends[0]
            list.add(
                CustomParticipantRow(
                    id = "fr_${firstFriend.friendUid}",
                    type = "friend",
                    uid = firstFriend.friendUid,
                    name = firstFriend.friendName,
                    amount = secondHalf,
                    percentage = 50.0
                )
            )
        } else if (partner != null) {
            list.add(
                CustomParticipantRow(
                    id = "partner",
                    type = "partner",
                    uid = partner.uid,
                    name = partner.displayName,
                    amount = secondHalf,
                    percentage = 50.0
                )
            )
        } else {
            list.add(
                CustomParticipantRow(
                    id = "ext_terceiro",
                    type = "external",
                    name = "Terceiro",
                    amount = secondHalf,
                    percentage = 50.0
                )
            )
        }

        return list
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
                isInstallment = if (type == TransactionType.INCOME) false else it.isInstallment,
                installmentsCount = if (type == TransactionType.INCOME) 1 else it.installmentsCount
            )
        }
    }

    fun onDescriptionChange(desc: String) {
        _state.update { it.copy(description = desc, errorMessage = null) }
    }

    fun onAmountChange(amount: String) {
        val clean = amount.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", ".")
        val num = clean.toDoubleOrNull() ?: 0.0
        _state.update { current ->
            val updatedParticipants = if (num > 0.0 && current.customParticipants.isNotEmpty()) {
                current.customParticipants.map { p ->
                    p.copy(amount = FinanceEngine.round2((num * p.percentage) / 100.0))
                }
            } else {
                current.customParticipants
            }
            current.copy(
                amountText = clean,
                customParticipants = updatedParticipants,
                errorMessage = null
            )
        }
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
        _state.update { current ->
            val currentAmount = current.amountText.toDoubleOrNull() ?: 0.0
            val user = repository.currentUser.value
            val participants = if (split == SplitType.CUSTOM && current.customParticipants.isEmpty()) {
                getInitialCustomParticipants(
                    total = currentAmount,
                    meUid = user.uid,
                    meName = user.displayName,
                    friends = current.friends,
                    partner = current.partner
                )
            } else {
                current.customParticipants
            }
            current.copy(
                splitType = split,
                customParticipants = participants
            )
        }
    }

    fun onExternalNameChange(name: String) {
        _state.update { it.copy(externalName = name) }
    }

    fun onParticipantPercentageChange(id: String, newPctRaw: Double) {
        val total = _state.value.amountText.toDoubleOrNull() ?: 0.0
        val pct = newPctRaw.coerceIn(0.0, 100.0)
        _state.update { current ->
            current.copy(
                customParticipants = current.customParticipants.map { p ->
                    if (p.id != id) p
                    else p.copy(
                        percentage = pct,
                        amount = FinanceEngine.round2((total * pct) / 100.0)
                    )
                }
            )
        }
    }

    fun onParticipantAmountChange(id: String, newAmtRaw: Double) {
        val total = _state.value.amountText.toDoubleOrNull() ?: 0.0
        val amt = max(0.0, newAmtRaw)
        _state.update { current ->
            current.copy(
                customParticipants = current.customParticipants.map { p ->
                    if (p.id != id) p
                    else {
                        val pct = if (total > 0.0) FinanceEngine.round2((amt / total) * 100.0) else p.percentage
                        p.copy(
                            amount = FinanceEngine.round2(amt),
                            percentage = pct
                        )
                    }
                }
            )
        }
    }

    fun onSetParticipantPreset(id: String, presetPct: Double) {
        onParticipantPercentageChange(id, presetPct)
    }

    fun onAddParticipant(type: String, friendUid: String? = null) {
        val total = _state.value.amountText.toDoubleOrNull() ?: 0.0
        _state.update { current ->
            val newRow = when (type) {
                "partner" -> current.partner?.let {
                    CustomParticipantRow(
                        id = "partner_${System.currentTimeMillis()}",
                        type = "partner",
                        uid = it.uid,
                        name = it.displayName,
                        amount = 0.0,
                        percentage = 0.0
                    )
                }
                "friend" -> current.friends.find { it.friendUid == friendUid }?.let {
                    CustomParticipantRow(
                        id = "fr_${it.friendUid}_${System.currentTimeMillis()}",
                        type = "friend",
                        uid = it.friendUid,
                        name = it.friendName,
                        amount = 0.0,
                        percentage = 0.0
                    )
                }
                else -> CustomParticipantRow(
                    id = "ext_${System.currentTimeMillis()}",
                    type = "external",
                    name = "Terceiro",
                    amount = 0.0,
                    percentage = 0.0
                )
            }
            if (newRow == null) return@update current

            val updated = current.customParticipants + newRow
            val count = updated.size
            val eachPct = FinanceEngine.round2(100.0 / count)
            val distributed = updated.mapIndexed { idx, p ->
                val pct = if (idx == count - 1) FinanceEngine.round2(100.0 - (eachPct * (count - 1))) else eachPct
                p.copy(
                    percentage = pct,
                    amount = FinanceEngine.round2((total * pct) / 100.0)
                )
            }
            current.copy(customParticipants = distributed)
        }
    }

    fun onRemoveParticipant(id: String) {
        val total = _state.value.amountText.toDoubleOrNull() ?: 0.0
        _state.update { current ->
            val filtered = current.customParticipants.filter { it.id != id }
            if (filtered.isEmpty()) return@update current
            val count = filtered.size
            val eachPct = FinanceEngine.round2(100.0 / count)
            val distributed = filtered.mapIndexed { idx, p ->
                val pct = if (idx == count - 1) FinanceEngine.round2(100.0 - (eachPct * (count - 1))) else eachPct
                p.copy(
                    percentage = pct,
                    amount = FinanceEngine.round2((total * pct) / 100.0)
                )
            }
            current.copy(customParticipants = distributed)
        }
    }

    fun onDistributeEqually() {
        val total = _state.value.amountText.toDoubleOrNull() ?: 0.0
        _state.update { current ->
            val count = current.customParticipants.size
            if (count == 0) return@update current
            val eachPct = FinanceEngine.round2(100.0 / count)
            val distributed = current.customParticipants.mapIndexed { idx, p ->
                val pct = if (idx == count - 1) FinanceEngine.round2(100.0 - (eachPct * (count - 1))) else eachPct
                p.copy(
                    percentage = pct,
                    amount = FinanceEngine.round2((total * pct) / 100.0)
                )
            }
            current.copy(customParticipants = distributed)
        }
    }

    fun onAdjustDifferenceToMe() {
        val total = _state.value.amountText.toDoubleOrNull() ?: 0.0
        _state.update { current ->
            val othersTotal = current.customParticipants.filter { it.type != "me" }.sumOf { it.amount }
            val myAmount = FinanceEngine.round2(max(0.0, total - othersTotal))
            val myPct = if (total > 0.0) FinanceEngine.round2((myAmount / total) * 100.0) else 0.0
            val updated = current.customParticipants.map { p ->
                if (p.type == "me") p.copy(amount = myAmount, percentage = myPct) else p
            }
            current.copy(customParticipants = updated)
        }
    }

    fun onQuickFriendPreset(friendUid: String, pct: Double) {
        val total = _state.value.amountText.toDoubleOrNull() ?: 0.0
        val friendPct = pct.coerceIn(1.0, 100.0)
        val myPct = FinanceEngine.round2(100.0 - friendPct)
        _state.update { current ->
            val user = repository.currentUser.value
            val fr = current.friends.find { it.friendUid == friendUid }
            val frName = fr?.friendName ?: "Amigo"
            val rows = listOf(
                CustomParticipantRow(
                    id = "me",
                    type = "me",
                    uid = user.uid,
                    name = user.displayName.ifBlank { "Você" },
                    amount = FinanceEngine.round2((total * myPct) / 100.0),
                    percentage = myPct
                ),
                CustomParticipantRow(
                    id = "fr_$friendUid",
                    type = "friend",
                    uid = friendUid,
                    name = frName,
                    amount = FinanceEngine.round2((total * friendPct) / 100.0),
                    percentage = friendPct
                )
            )
            current.copy(customParticipants = rows)
        }
    }

    fun onUpdateParticipantExternalName(id: String, newName: String) {
        _state.update { current ->
            current.copy(
                customParticipants = current.customParticipants.map {
                    if (it.id == id) it.copy(name = newName) else it
                }
            )
        }
    }

    fun onDueDateChange(date: String) {
        _state.update { it.copy(dueDateText = date) }
    }

    fun onStatusChange(status: TransactionStatus) {
        _state.update { it.copy(status = status) }
    }

    fun onIsInstallmentChange(enabled: Boolean) {
        _state.update {
            it.copy(
                isInstallment = enabled,
                installmentsCount = if (enabled && it.installmentsCount <= 1) 3 else it.installmentsCount
            )
        }
    }

    fun onInstallmentModeChange(mode: String) {
        _state.update { it.copy(installmentMode = mode) }
    }

    fun onInstallmentsChange(count: Int) {
        val safeCount = count.coerceIn(1, 48)
        _state.update {
            it.copy(
                installmentsCount = safeCount,
                isInstallment = safeCount > 1
            )
        }
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
            val card = current.availableCards.find { it.id == current.selectedCardId }
            val partner = current.partner ?: repository.allUsers.find { it.uid != user.uid }
            val total = FinanceEngine.round2(amount)
            val isoInputDate = FinanceEngine.parseInputDateToIso(current.dueDateText)

            val isCustom = current.splitType == SplitType.CUSTOM
            val customAllocations = if (isCustom && current.customParticipants.isNotEmpty()) {
                current.customParticipants.map { p ->
                    Allocation(
                        type = if (p.type == "external") "EXTERNAL" else "PLATFORM_USER",
                        uid = if (p.type != "external") p.uid else null,
                        name = p.name.ifBlank { "Participante" },
                        amount = FinanceEngine.round2(p.amount),
                        reimbursed = (p.uid == user.uid)
                    )
                }
            } else null

            val friendPart = if (isCustom) {
                current.customParticipants.find {
                    it.type == "friend" || (it.uid != null && it.uid != user.uid && (partner == null || it.uid != partner.uid))
                }
            } else null

            val assignedFriendUid = friendPart?.uid ?: (if (current.splitType == SplitType.FRIEND) current.assignedFriendUid else null)
            val assignedFriendName = friendPart?.name ?: (if (current.splitType == SplitType.FRIEND) current.assignedFriendName else null)
            val friendSharePct = if (friendPart != null) {
                if (total > 0.0) FinanceEngine.round2((friendPart.amount / total) * 100.0) else friendPart.percentage
            } else current.friendSharePct
            val acceptanceStatus = if (!assignedFriendUid.isNullOrBlank()) "pending_approval" else null
            val externalName = if (current.splitType == SplitType.EXTERNAL) current.externalName.trim() else null

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
                            externalName = externalName,
                            friend = if (!assignedFriendUid.isNullOrBlank() && !assignedFriendName.isNullOrBlank()) {
                                FriendSplitInput(
                                    uid = assignedFriendUid,
                                    name = assignedFriendName,
                                    pct = friendSharePct ?: 100.0
                                )
                            } else null,
                            customAllocations = customAllocations,
                            date = if (isoInputDate != origTx.dueDate) isoInputDate else null,
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
                    val dateChanged = isoInputDate != origTx.dueDate ||
                            current.paymentMethod != origTx.paymentMethod ||
                            (current.paymentMethod == PaymentMethod.CREDIT_CARD && card?.id != origTx.cardId)

                    val (dueDate, competenceMonth) = if (dateChanged) {
                        if (current.paymentMethod == PaymentMethod.CREDIT_CARD) {
                            val cardDates = FinanceEngine.calculateCardInvoiceDates(isoInputDate, card)
                            Pair(isoInputDate, cardDates.second)
                        } else {
                            val month = if (isoInputDate.length >= 7) isoInputDate.take(7) else origTx.competenceMonth
                            Pair(isoInputDate, month)
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
                        externalName = externalName,
                        assignedFriendUid = assignedFriendUid,
                        assignedFriendName = assignedFriendName,
                        friendSharePct = friendSharePct,
                        customAllocations = customAllocations
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
                        splitType = current.splitType,
                        externalName = externalName,
                        assignedFriendUid = assignedFriendUid,
                        assignedFriendName = assignedFriendName,
                        acceptanceStatus = acceptanceStatus ?: origTx.acceptanceStatus,
                        friendSharePct = friendSharePct
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

            val payerUid = if (current.paymentMethod == PaymentMethod.CREDIT_CARD && card != null) {
                card.ownerUid
            } else {
                user.uid
            }

            val isInstallmentActive = current.type == TransactionType.EXPENSE && current.isInstallment && current.installmentsCount > 1

            if (isInstallmentActive) {
                // Gera parcelas
                val groupId = "inst_grp_" + UUID.randomUUID().toString().take(8)
                val (installmentAmount, totalPurchase) = if (current.installmentMode == "per_installment") {
                    Pair(total, FinanceEngine.round2(total * current.installmentsCount))
                } else {
                    Pair(FinanceEngine.round2(total / current.installmentsCount), total)
                }
                val installmentList = mutableListOf<Transaction>()

                val instCustomAllocs = customAllocations?.map { a ->
                    if (current.installmentMode == "per_installment") {
                        a
                    } else {
                        a.copy(amount = FinanceEngine.round2(a.amount / current.installmentsCount))
                    }
                }

                for (i in 1..current.installmentsCount) {
                    val purchaseDate = FinanceEngine.shiftDateMonth(isoInputDate, i - 1)

                    val (dueDate, competenceMonth) = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) {
                        val cardDates = FinanceEngine.calculateCardInvoiceDates(purchaseDate, card)
                        Pair(purchaseDate, cardDates.second)
                    } else {
                        Pair(purchaseDate, purchaseDate.take(7))
                    }

                    val split = FinanceEngine.buildSplit(
                        splitType = current.splitType,
                        total = installmentAmount,
                        myUid = user.uid,
                        myName = user.displayName,
                        partnerUid = partner?.uid,
                        partnerName = partner?.displayName,
                        payerUid = payerUid,
                        externalName = externalName,
                        assignedFriendUid = assignedFriendUid,
                        assignedFriendName = assignedFriendName,
                        friendSharePct = friendSharePct,
                        customAllocations = instCustomAllocs
                    )

                    val tx = Transaction(
                        id = "tx_" + UUID.randomUUID().toString().take(8),
                        createdByUid = user.uid,
                        paidByUid = payerUid,
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
                        splitType = current.splitType,
                        externalName = externalName,
                        assignedFriendUid = assignedFriendUid,
                        assignedFriendName = assignedFriendName,
                        acceptanceStatus = acceptanceStatus,
                        friendSharePct = friendSharePct
                    )
                    installmentList.add(tx)
                }
                repository.addTransactions(installmentList)
                repository.logActivity(
                    ActivityLog(
                        actionType = ActivityActionType.TRANSACTION_CREATED,
                        title = current.description.trim(),
                        description = "Série parcelada criada (${current.installmentsCount}x de ${FinanceEngine.formatCurrency(installmentAmount)}) • ${current.category}",
                        amount = totalPurchase,
                        category = current.category,
                        competenceMonth = installmentList.firstOrNull()?.competenceMonth ?: repository.selectedMonth.value,
                        userUid = user.uid,
                        userName = user.displayName
                    )
                )
            } else {
                // Transação individual
                val (dueDate, competenceMonth) = if (current.paymentMethod == PaymentMethod.CREDIT_CARD) {
                    val cardDates = FinanceEngine.calculateCardInvoiceDates(isoInputDate, card)
                    Pair(isoInputDate, cardDates.second)
                } else {
                    Pair(isoInputDate, isoInputDate.take(7))
                }

                val split = FinanceEngine.buildSplit(
                    splitType = current.splitType,
                    total = total,
                    myUid = user.uid,
                    myName = user.displayName,
                    partnerUid = partner?.uid,
                    partnerName = partner?.displayName,
                    payerUid = payerUid,
                    externalName = externalName,
                    assignedFriendUid = assignedFriendUid,
                    assignedFriendName = assignedFriendName,
                    friendSharePct = friendSharePct,
                    customAllocations = customAllocations
                )

                val tx = Transaction(
                    id = "tx_" + UUID.randomUUID().toString().take(8),
                    createdByUid = user.uid,
                    paidByUid = payerUid,
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
                    splitType = current.splitType,
                    externalName = externalName,
                    assignedFriendUid = assignedFriendUid,
                    assignedFriendName = assignedFriendName,
                    acceptanceStatus = acceptanceStatus,
                    friendSharePct = friendSharePct
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
