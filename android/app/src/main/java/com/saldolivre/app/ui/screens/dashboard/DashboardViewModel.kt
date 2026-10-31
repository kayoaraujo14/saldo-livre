package com.saldolivre.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.engine.InvoiceStatus
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.RecurringBill
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class AutoLaunchNotice(
    val count: Int,
    val bills: List<Transaction>
)

data class IncomeConsumptionState(
    val percentage: Int = 0,
    val paid: Double = 0.0,
    val income: Double = 0.0,
    val statusBadge: String = "No Limite"
)

data class Radar7DaysState(
    val totalAmount: Double = 0.0,
    val count: Int = 0,
    val urgencyBadge: String = "Sem pendências",
    val formattedEndDate: String = "",
    val isUrgent: Boolean = false
)

data class TopCategoryState(
    val categoryName: String = "Sem despesas",
    val amount: Double = 0.0,
    val percentageOfTotal: Int = 0,
    val badgeText: String = "Sem gastos",
    val subtitle: String = "Nenhum gasto registrado"
)

data class InvestmentHealthState(
    val totalInvested: Double = 0.0,
    val incomePercentage: Int = 0,
    val targetPercentage: Int = 20,
    val isTargetReached: Boolean = false,
    val progressRatio: Float = 0.0f,
    val badgeText: String = "Pendente"
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
    val incomeConsumption: IncomeConsumptionState = IncomeConsumptionState(),
    val radar7Days: Radar7DaysState = Radar7DaysState(),
    val topCategory: TopCategoryState = TopCategoryState(),
    val investmentHealth: InvestmentHealthState = InvestmentHealthState(),
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

                // 1. Consumo da Renda (Sem repetição do valor da renda já presente no Hero Card)
                val consumptionPct = if (summary.income > 0.0) {
                    ((summary.paid / summary.income) * 100.0).roundToInt()
                } else 0
                val consumptionBadge = when {
                    consumptionPct > 100 -> "Alerta"
                    consumptionPct > 80 -> "Atenção"
                    else -> "No Limite"
                }
                val incomeConsumption = IncomeConsumptionState(
                    percentage = consumptionPct,
                    paid = summary.paid,
                    income = summary.income,
                    statusBadge = consumptionBadge
                )

                // 2. Radar 7 Dias (Sem repetições triplas de 'sem contas')
                val today = java.time.LocalDate.now()
                val nextWeekDate = today.plusDays(7)
                val nextWeekStr = String.format(Locale.US, "%04d-%02d-%02d", nextWeekDate.year, nextWeekDate.monthValue, nextWeekDate.dayOfMonth)
                val formattedEndDate = String.format(Locale.US, "%02d/%02d", nextWeekDate.dayOfMonth, nextWeekDate.monthValue)

                val upcomingNonCardTxs = allTxs.filter { tx ->
                    tx.type == TransactionType.EXPENSE &&
                    !tx.isRefund &&
                    tx.status != TransactionStatus.PAID &&
                    (tx.cardId.isNullOrBlank() || tx.reserveCardId != null) &&
                    tx.dueDate >= todayStr && tx.dueDate <= nextWeekStr &&
                    tx.visibleToUids.contains(user.uid)
                }
                var radarTotal = upcomingNonCardTxs.sumOf { tx ->
                    FinanceEngine.impactFor(tx, user.uid, allTxs)
                }
                var radarCount = upcomingNonCardTxs.size
                var minDaysToDue: Long? = upcomingNonCardTxs.mapNotNull { tx ->
                    try {
                        val d = java.time.LocalDate.parse(tx.dueDate)
                        java.time.temporal.ChronoUnit.DAYS.between(today, d)
                    } catch (_: Exception) { null }
                }.minOrNull()

                val candidateMonths = listOf(month, FinanceEngine.shiftMonth(month, 1)).distinct()
                val userCards = cards.filter { it.ownerUid == user.uid || it.sharedWithUids.contains(user.uid) }
                for (m in candidateMonths) {
                    for (card in userCards) {
                        val (_, invDueDate) = InvoiceEngine.invoiceDates(card, m)
                        if (invDueDate in todayStr..nextWeekStr) {
                            val inv = InvoiceEngine.buildInvoice(card, m, allTxs, user.uid)
                            if (inv.status != InvoiceStatus.PAID && inv.status != InvoiceStatus.EMPTY && inv.openAmount > 0.0) {
                                val amountOwed = if (card.ownerUid == user.uid) inv.openAmount else inv.myShare
                                if (amountOwed > 0.0) {
                                    radarTotal += amountOwed
                                    radarCount += 1
                                    try {
                                        val d = java.time.LocalDate.parse(invDueDate)
                                        val diffDays = java.time.temporal.ChronoUnit.DAYS.between(today, d)
                                        minDaysToDue = minOf(minDaysToDue ?: diffDays, diffDays)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
                radarTotal = FinanceEngine.round2(radarTotal)

                val urgencyBadge = when {
                    radarCount == 0 -> "Sem pendências"
                    minDaysToDue != null && minDaysToDue <= 1L -> "Vence em breve!"
                    else -> "$radarCount a vencer"
                }
                val isUrgent = radarCount > 0 && minDaysToDue != null && minDaysToDue <= 1L
                val radar7Days = Radar7DaysState(
                    totalAmount = radarTotal,
                    count = radarCount,
                    urgencyBadge = urgencyBadge,
                    formattedEndDate = formattedEndDate,
                    isUrgent = isUrgent
                )

                // 3. Maior Gasto (Top Categoria do Mês - Substitui o Termômetro Diário)
                val totalExpenses = summary.byCategory.sumOf { it.amount }
                val topCatExpense = summary.byCategory.firstOrNull { it.amount > 0.0 }
                val topCategory = if (topCatExpense != null && totalExpenses > 0.0) {
                    val catPct = ((topCatExpense.amount / totalExpenses) * 100.0).roundToInt()
                    TopCategoryState(
                        categoryName = topCatExpense.category,
                        amount = topCatExpense.amount,
                        percentageOfTotal = catPct,
                        badgeText = "$catPct% do total",
                        subtitle = "Principal despesa do mês"
                    )
                } else {
                    TopCategoryState(
                        categoryName = "Sem despesas",
                        amount = 0.0,
                        percentageOfTotal = 0,
                        badgeText = "Sem gastos",
                        subtitle = "Nenhum gasto registrado"
                    )
                }

                // 4. Saúde de Investimentos (Sem repetições de Meta: 20% vs 0% vs R$ 0)
                val invested = summary.totalInvested
                val investIncomePct = if (summary.income > 0.0) {
                    ((invested / summary.income) * 100.0).roundToInt()
                } else 0
                val isTargetReached = investIncomePct >= 20
                val progressRatio = (investIncomePct / 20.0f).coerceIn(0.0f, 1.0f)
                val investBadge = when {
                    invested <= 0.0 -> "Pendente"
                    isTargetReached -> "Meta OK"
                    else -> "$investIncomePct% / 20%"
                }
                val investmentHealth = InvestmentHealthState(
                    totalInvested = invested,
                    incomePercentage = investIncomePct,
                    targetPercentage = 20,
                    isTargetReached = isTargetReached,
                    progressRatio = progressRatio,
                    badgeText = investBadge
                )

                DashboardUiState(
                    currentUser = user,
                    allUsers = repository.allUsers,
                    selectedMonth = month,
                    summary = summary,
                    recentTransactions = recent,
                    allTransactions = allTxs.filter { it.visibleToUids.contains(user.uid) },
                    recurringBills = bills,
                    pendingRecurringLaunches = pending,
                    incomeConsumption = incomeConsumption,
                    radar7Days = radar7Days,
                    topCategory = topCategory,
                    investmentHealth = investmentHealth,
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
