package com.saldolivre.app.ui.screens.recurring

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class RecurringTab {
    ALL,
    INCOME,
    EXPENSE
}

enum class BillMonthlyState {
    NOT_LAUNCHED,
    IN_STATEMENT_PENDING,
    IN_CARD_INVOICE,
    PAID_OR_RECEIVED
}

@Immutable
data class BillMonthStatusUi(
    val state: BillMonthlyState,
    val matchedTxId: String? = null,
    val estimatedAmount: Double = 0.0,
    val estimationBasis: String = "Valor fixo"
)

@Immutable
data class RecurringExecutiveStats(
    val totalFixedIncome: Double = 0.0,
    val fixedCashExpenses: Double = 0.0,
    val fixedCardSubscriptions: Double = 0.0,
    val totalFixedCommitment: Double = 0.0,
    val commitmentRatioPct: Double = 0.0,
    val freeBaseBalance: Double = 0.0
)

data class RecurringBillsUiState(
    val currentUser: UserProfile = UserProfile(),
    val partner: UserProfile? = null,
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val bills: List<RecurringBill> = emptyList(),
    val cards: List<Card> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val billStatuses: Map<String, BillMonthStatusUi> = emptyMap(),
    val executiveStats: RecurringExecutiveStats = RecurringExecutiveStats(),
    val isLoading: Boolean = false,
    val activeTab: RecurringTab = RecurringTab.ALL,
    val editingIncome: Boolean = false,
    val incomeInput: String = "",
    val editingBill: RecurringBill? = null,
    val isFormOpen: Boolean = false,
    val billToDelete: RecurringBill? = null,
    val userMessage: String? = null
)

class RecurringBillsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringBillsUiState())
    val uiState: StateFlow<RecurringBillsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getRecurringBills(),
                repository.getCards(),
                repository.getTransactions()
            ) { user, month, bills, cards, transactions ->
                val partner = repository.allUsers.find { it.uid != user.uid }
                val myBills = bills.filter { it.ownerUid == user.uid || it.ownerUid.isBlank() }

                val statuses = mutableMapOf<String, BillMonthStatusUi>()
                var extraFixedIncome = 0.0
                var cashFixed = 0.0
                var cardSubs = 0.0

                for (bill in myBills) {
                    val est = FinanceEngine.estimateRecurring(bill, transactions, month)
                    val matchedTx = transactions.firstOrNull {
                        it.competenceMonth == month && it.recurringBillId == bill.id
                    } ?: FinanceEngine.findMatchingManualBill(transactions, month, bill)

                    val isCard = bill.paymentMethod == PaymentMethod.CREDIT_CARD || !bill.cardId.isNullOrBlank()
                    val monthState = when {
                        matchedTx == null -> BillMonthlyState.NOT_LAUNCHED
                        matchedTx.status == TransactionStatus.PAID -> BillMonthlyState.PAID_OR_RECEIVED
                        isCard -> BillMonthlyState.IN_CARD_INVOICE
                        else -> BillMonthlyState.IN_STATEMENT_PENDING
                    }

                    statuses[bill.id] = BillMonthStatusUi(
                        state = monthState,
                        matchedTxId = matchedTx?.id,
                        estimatedAmount = est.amount,
                        estimationBasis = est.basis
                    )

                    if (bill.active) {
                        val inRange = (bill.startMonth.isNullOrBlank() || month >= bill.startMonth) &&
                                (bill.endMonth.isNullOrBlank() || month <= bill.endMonth)
                        if (inRange) {
                            if (bill.type == TransactionType.INCOME) {
                                extraFixedIncome += est.amount
                            } else {
                                val myShare = if (bill.splitType == SplitType.SPLIT_50) est.amount * 0.5 else est.amount
                                if (isCard) {
                                    cardSubs += myShare
                                } else {
                                    cashFixed += myShare
                                }
                            }
                        }
                    }
                }

                val totIncome = FinanceEngine.round2(user.monthlyIncome + extraFixedIncome)
                val totCash = FinanceEngine.round2(cashFixed)
                val totCard = FinanceEngine.round2(cardSubs)
                val totCommit = FinanceEngine.round2(totCash + totCard)
                val ratio = if (totIncome > 0.0) FinanceEngine.round2((totCommit / totIncome) * 100.0) else 0.0
                val freeBase = FinanceEngine.round2(totIncome - totCommit)

                val exec = RecurringExecutiveStats(
                    totalFixedIncome = totIncome,
                    fixedCashExpenses = totCash,
                    fixedCardSubscriptions = totCard,
                    totalFixedCommitment = totCommit,
                    commitmentRatioPct = ratio,
                    freeBaseBalance = freeBase
                )

                _uiState.value.copy(
                    currentUser = user,
                    partner = partner,
                    selectedMonth = month,
                    bills = myBills,
                    cards = cards,
                    transactions = transactions,
                    billStatuses = statuses,
                    executiveStats = exec,
                    incomeInput = if (!_uiState.value.editingIncome) user.monthlyIncome.toString() else _uiState.value.incomeInput
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setTab(tab: RecurringTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun startEditingIncome() {
        val currentIncome = _uiState.value.currentUser.monthlyIncome
        _uiState.update {
            it.copy(
                editingIncome = true,
                incomeInput = if (currentIncome > 0) currentIncome.toString() else ""
            )
        }
    }

    fun cancelEditingIncome() {
        _uiState.update { it.copy(editingIncome = false) }
    }

    fun setIncomeInput(input: String) {
        _uiState.update { it.copy(incomeInput = input) }
    }

    fun saveMonthlyIncome() {
        val raw = _uiState.value.incomeInput.replace(',', '.').trim()
        val num = raw.toDoubleOrNull()
        if (num != null && num >= 0.0) {
            viewModelScope.launch {
                repository.updateMonthlyIncome(num)
                _uiState.update { it.copy(editingIncome = false, userMessage = "Renda base atualizada!") }
            }
        }
    }

    fun quickEffectivateBillInMonth(bill: RecurringBill) {
        viewModelScope.launch {
            val state = _uiState.value
            val statusUi = state.billStatuses[bill.id]
            val monthKey = state.selectedMonth
            val user = state.currentUser
            val partner = state.partner

            // Se já existe como pendente/estimado no extrato, confirma como PAGO
            if (statusUi?.state == BillMonthlyState.IN_STATEMENT_PENDING && !statusUi.matchedTxId.isNullOrBlank()) {
                repository.markTransactionPaid(statusUi.matchedTxId)
                repository.markRecurringResolved(monthKey, bill.id)
                _uiState.update {
                    it.copy(userMessage = "\"${bill.description}\" confirmado como pago em ${FinanceEngine.formatMonthLabel(monthKey)}!")
                }
                return@launch
            }

            // Caso ainda não tenha sido lançado no mês, cria e efetiva agora
            val amount = statusUi?.estimatedAmount ?: bill.defaultAmount
            val isIncome = bill.type == TransactionType.INCOME
            val isCard = !isIncome && (bill.paymentMethod == PaymentMethod.CREDIT_CARD || !bill.cardId.isNullOrBlank())
            val pm = bill.paymentMethod ?: if (isIncome) PaymentMethod.PIX else if (isCard) PaymentMethod.CREDIT_CARD else PaymentMethod.BILL

            val split = FinanceEngine.buildSplit(
                splitType = if (isIncome) SplitType.MINE else (bill.splitType ?: SplitType.MINE),
                total = amount,
                myUid = user.uid,
                myName = user.displayName,
                partnerUid = partner?.uid,
                partnerName = partner?.displayName,
                payerUid = user.uid
            )

            val tx = Transaction(
                id = "tx_rec_${UUID.randomUUID().toString().take(8)}",
                createdByUid = user.uid,
                paidByUid = user.uid,
                cardId = if (isCard) bill.cardId else null,
                description = bill.description,
                category = if (isIncome) "Receita" else bill.category,
                subcategory = bill.subcategory,
                type = bill.type,
                paymentMethod = pm,
                status = if (isCard) TransactionStatus.PENDING else TransactionStatus.PAID,
                estimatedAmount = amount,
                actualAmount = amount,
                totalAmount = amount,
                dueDate = FinanceEngine.dateInMonth(monthKey, bill.dueDay),
                competenceMonth = monthKey,
                recurringBillId = bill.id,
                allocations = split.allocations,
                userImpact = split.userImpact,
                visibleToUids = split.visibleToUids,
                splitType = if (isIncome) SplitType.MINE else (bill.splitType ?: SplitType.MINE),
                createdAt = System.currentTimeMillis()
            )

            repository.addTransaction(tx)
            repository.markRecurringResolved(monthKey, bill.id)
            _uiState.update {
                it.copy(
                    userMessage = if (isCard) {
                        "\"${bill.description}\" lançado na fatura de ${FinanceEngine.formatMonthLabel(monthKey)}!"
                    } else {
                        "\"${bill.description}\" efetivado em ${FinanceEngine.formatMonthLabel(monthKey)}!"
                    }
                )
            }
        }
    }

    fun openNewBill() {
        val user = _uiState.value.currentUser
        val initialCategory = "Moradia"
        val initialSubcategory = CategoryRegistry.get(initialCategory).subcategories.firstOrNull()
        val newBill = RecurringBill(
            id = "new_${System.currentTimeMillis()}",
            ownerUid = user.uid,
            description = "",
            category = initialCategory,
            subcategory = initialSubcategory,
            dueDay = 10,
            defaultAmount = 200.0,
            estimationRule = EstimationRule.FIXED,
            active = true,
            splitType = SplitType.MINE,
            paymentMethod = PaymentMethod.BILL,
            type = TransactionType.EXPENSE,
            startMonth = FinanceEngine.currentMonthKey(),
            installmentsCount = 3,
            launchTrigger = LaunchTrigger.DAY_BEFORE
        )
        _uiState.update { it.copy(editingBill = newBill, isFormOpen = true) }
    }

    fun editBill(bill: RecurringBill) {
        _uiState.update { it.copy(editingBill = bill, isFormOpen = true) }
    }

    fun closeForm() {
        _uiState.update { it.copy(editingBill = null, isFormOpen = false) }
    }

    fun saveBill(bill: RecurringBill) {
        if (bill.description.isBlank()) return
        viewModelScope.launch {
            repository.saveRecurringBill(bill)
            _uiState.update {
                it.copy(
                    editingBill = null,
                    isFormOpen = false,
                    userMessage = "Conta fixa salva com sucesso!"
                )
            }
        }
    }

    fun toggleBillActive(bill: RecurringBill) {
        viewModelScope.launch {
            val updated = bill.copy(active = !bill.active)
            repository.saveRecurringBill(updated)
        }
    }

    fun confirmDelete(bill: RecurringBill) {
        _uiState.update { it.copy(billToDelete = bill) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(billToDelete = null) }
    }

    fun executeDelete() {
        val toDelete = _uiState.value.billToDelete ?: return
        viewModelScope.launch {
            repository.deleteRecurringBill(toDelete.id)
            _uiState.update {
                it.copy(
                    billToDelete = null,
                    editingBill = if (it.editingBill?.id == toDelete.id) null else it.editingBill,
                    isFormOpen = if (it.editingBill?.id == toDelete.id) false else it.isFormOpen,
                    userMessage = "Conta fixa excluída com sucesso."
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
