package com.saldolivre.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.GeminiLoadBalancer
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
import com.saldolivre.app.data.model.FinancialHealthScore
import com.saldolivre.app.data.model.CategoryBudgetReport
import com.saldolivre.app.data.model.BudgetRebalanceResult
import com.saldolivre.app.data.model.SuggestedBudgetsResult
import com.saldolivre.app.data.model.SmartBudgetPlan
import com.saldolivre.app.data.model.FinancialHealthAiDiagnosis
import com.saldolivre.app.data.model.BudgetInsight
import com.saldolivre.app.data.model.BudgetInsightAction
import com.saldolivre.app.data.model.BudgetInsightActionType
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import com.saldolivre.app.data.model.Allocation
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.SplitType

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
    val financialHealth: FinancialHealthScore = FinancialHealthScore(),
    val categoryBudgetReport: CategoryBudgetReport = CategoryBudgetReport(),
    val budgetRebalance: BudgetRebalanceResult = BudgetRebalanceResult(),
    val suggestedBudgets: SuggestedBudgetsResult = SuggestedBudgetsResult(),
    val isLoading: Boolean = false,
    val showClosureDialog: Boolean = false,
    val closureDialogDismissed: Boolean = false,
    val autoLaunchNotice: AutoLaunchNotice? = null,
    val isGeneratingSmartBudget: Boolean = false,
    val smartBudgetPlan: SmartBudgetPlan? = null,
    val isGeneratingHealthAiDiagnosis: Boolean = false,
    val healthAiDiagnosis: FinancialHealthAiDiagnosis? = null,
    val budgetInsights: List<BudgetInsight> = emptyList(),
    val aiBudgetInsights: List<BudgetInsight>? = null,
    val aiBudgetInsightsMonth: String? = null,
    val isGeneratingBudgetInsights: Boolean = false
) {
    /** Insights do Gemini (quando gerados para o mês atual) ou, senão, os insights locais instantâneos. */
    val displayedBudgetInsights: List<BudgetInsight>
        get() = if (aiBudgetInsights != null && aiBudgetInsightsMonth == selectedMonth) aiBudgetInsights else budgetInsights
}

class DashboardViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val geminiLoadBalancer by lazy { GeminiLoadBalancer(SaldoLivreApplication.instance) }

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

                // 3. Maior Gasto (Top Categoria do Mês - Paridade 100% Web & Android)
                val nonInvestCategories = summary.byCategory.filter {
                    it.amount > 0.0 && !it.category.contains("investimento", ignoreCase = true)
                }
                val validCategories = if (nonInvestCategories.isNotEmpty()) {
                    nonInvestCategories
                } else {
                    summary.byCategory.filter { it.amount > 0.0 }
                }
                val totalExpenses = if (summary.paid + summary.committed > 0.0) {
                    summary.paid + summary.committed
                } else {
                    summary.byCategory.sumOf { it.amount }
                }
                val topCatExpense = validCategories.firstOrNull()
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

                // 4. Saúde de Investimentos (Garante alinhamento total com o envelope de Investimentos e aportes do mês)
                val investExpense = summary.byCategory.firstOrNull {
                    it.category == "Investimentos" || it.category.contains("investimento", ignoreCase = true)
                }?.amount ?: summary.totalInvested
                val invested = kotlin.math.max(summary.totalInvested, investExpense)
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

                // 5. Envelopes Orçamentários Inteligentes & Termômetro de Saúde Financeira (4 Pilares)
                val expectedMonthIncome = if (summary.income > 0.0) summary.income else user.monthlyIncome
                val monthBudgets = user.monthlyCategoryBudgets[month]?.takeIf { it.isNotEmpty() }
                    ?: if (month == FinanceEngine.currentMonthKey() || user.monthlyCategoryBudgets.isEmpty()) user.categoryBudgets else emptyMap()
                val categoryBudgetReport = FinanceEngine.computeCategoryBudgetsFromTransactions(
                    transactions = allTxs,
                    categoryBudgets = monthBudgets,
                    monthKey = month,
                    referenceDate = todayStr,
                    uid = user.uid,
                    allTransactions = allTxs,
                    expectedIncome = expectedMonthIncome,
                    carryoverAmount = summary.carryoverAmount,
                    previousMonthDeficit = summary.previousMonthDeficit,
                    recurringBills = bills
                )
                val budgetRebalance = FinanceEngine.suggestBudgetRebalance(categoryBudgetReport)
                val suggestedBudgets = FinanceEngine.suggestCategoryBudgets(
                    allTransactions = allTxs,
                    uid = user.uid,
                    referenceMonth = month,
                    monthlyIncome = expectedMonthIncome,
                    carryoverAmount = summary.carryoverAmount,
                    previousMonthDeficit = summary.previousMonthDeficit
                )

                val reserveBalance = FinanceEngine.resolveEmergencyReserveBalance(user, summary)
                val financialHealth = FinanceEngine.calculateFinancialHealthScore(
                    summary = summary,
                    emergencyReserveBalance = reserveBalance,
                    budgetReport = categoryBudgetReport
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
                    financialHealth = financialHealth,
                    categoryBudgetReport = categoryBudgetReport,
                    budgetRebalance = budgetRebalance,
                    suggestedBudgets = suggestedBudgets,
                    isLoading = false,
                    showClosureDialog = shouldShowPopup || _uiState.value.showClosureDialog,
                    closureDialogDismissed = _uiState.value.closureDialogDismissed,
                    autoLaunchNotice = _uiState.value.autoLaunchNotice,
                    isGeneratingSmartBudget = _uiState.value.isGeneratingSmartBudget,
                    smartBudgetPlan = _uiState.value.smartBudgetPlan,
                    isGeneratingHealthAiDiagnosis = _uiState.value.isGeneratingHealthAiDiagnosis,
                    healthAiDiagnosis = _uiState.value.healthAiDiagnosis,
                    budgetInsights = FinanceEngine.buildBudgetInsights(categoryBudgetReport),
                    aiBudgetInsights = _uiState.value.aiBudgetInsights,
                    aiBudgetInsightsMonth = _uiState.value.aiBudgetInsightsMonth,
                    isGeneratingBudgetInsights = _uiState.value.isGeneratingBudgetInsights
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
        _uiState.update { it.copy(showClosureDialog = true, closureDialogDismissed = false) }
    }

    fun dismissClosureDialog() {
        _uiState.update { it.copy(showClosureDialog = false, closureDialogDismissed = true) }
    }

    fun confirmMonthClosure(carryover: Double, invested: Double, subcategory: String? = null) {
        val summary = _uiState.value.summary
        val closedMonth = summary.pendingClosureMonth ?: return
        val targetMonth = _uiState.value.selectedMonth
        val surplus = summary.pendingClosureSurplus ?: (carryover + invested)
        val user = _uiState.value.currentUser
        val uid = user.uid

        val subName = subcategory?.ifBlank { "Reserva de Emergência" } ?: "Reserva de Emergência"
        val invTxId = if (invested > 0.0) "inv_cls_" + UUID.randomUUID().toString().take(8) else null

        val closure = com.saldolivre.app.data.model.MonthClosure(
            id = "cls_${closedMonth}_$uid",
            uid = uid,
            closedMonth = closedMonth,
            targetMonth = targetMonth,
            totalSurplus = surplus,
            carryoverToNextMonth = carryover,
            investedAmount = invested,
            cushionAmount = 0.0,
            investmentSubcategory = if (invested > 0.0) subName else null,
            investmentTransactionId = invTxId
        )
        viewModelScope.launch {
            try {
                repository.saveMonthClosure(closure)

                if (invested > 0.0) {
                    val currentBalances = user.investmentAssetBalances.toMutableMap()
                    val existing = currentBalances[subName] ?: 0.0
                    currentBalances[subName] = FinanceEngine.round2(existing + invested)
                    val updatedUser = user.copy(investmentAssetBalances = currentBalances)
                    repository.saveUserProfile(updatedUser)
                }

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
                val user = _uiState.value.currentUser
                repository.deleteMonthClosure(closedMonth)

                // Deleta TODAS as transações de aporte comemorativo vinculadas a esse fechamento
                val allTxs = repository.getTransactions().first()
                val relatedTxs = allTxs.filter {
                    it.competenceMonth == closedMonth &&
                    it.category == "Investimentos" &&
                    it.description.contains("Aporte Sobra", ignoreCase = true)
                }
                val currentBalances = user.investmentAssetBalances.toMutableMap()
                for (relatedTx in relatedTxs) {
                    repository.deleteTransaction(relatedTx.id)
                    val subName = relatedTx.subcategory ?: "Reserva de Emergência"
                    val existing = currentBalances[subName] ?: 0.0
                    currentBalances[subName] = max(0.0, FinanceEngine.round2(existing - relatedTx.totalAmount))
                }
                if (relatedTxs.isNotEmpty()) {
                    val updatedUser = user.copy(investmentAssetBalances = currentBalances)
                    repository.saveUserProfile(updatedUser)
                }

                // Reabre imediatamente o popup para permitir novo lançamento
                _uiState.update {
                    it.copy(
                        closureDialogDismissed = false,
                        showClosureDialog = true
                    )
                }
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

    private fun calibrateMapToIncome(budgets: Map<String, Double>): Map<String, Double> {
        val cleaned = budgets.filterValues { it > 0.0 }.mapValues { FinanceEngine.round2(it.value) }.toMutableMap()
        val summary = _uiState.value.summary
        val baseIncome = if ((summary?.income ?: 0.0) > 0.0) {
            summary!!.income
        } else {
            _uiState.value.currentUser.monthlyIncome
        }
        val carryover = summary?.carryoverAmount ?: 0.0
        val deficit = summary?.previousMonthDeficit ?: 0.0
        val effectiveIncome = FinanceEngine.round2(kotlin.math.max(0.0, baseIncome + carryover - deficit))

        // Exclui Investimentos do teto de consumo
        val expenseSum = cleaned.entries
            .filter { !it.key.contains("investimento", ignoreCase = true) && it.key != "Investimentos" }
            .sumOf { it.value }

        if (effectiveIncome > 0.0 && expenseSum > effectiveIncome) {
            val scale = effectiveIncome / expenseSum
            var running = 0.0
            var maxCat = ""
            var maxVal = -1.0
            for (cat in cleaned.keys.toList()) {
                if (cat.contains("investimento", ignoreCase = true) || cat == "Investimentos") continue
                val rawVal = cleaned[cat] ?: 0.0
                val scaled = kotlin.math.floor((rawVal * scale) / 10.0) * 10.0
                cleaned[cat] = scaled
                running = FinanceEngine.round2(running + scaled)
                if (scaled > maxVal) {
                    maxVal = scaled
                    maxCat = cat
                }
            }
            val diff = kotlin.math.floor((effectiveIncome - running) / 10.0) * 10.0
            if (maxCat.isNotEmpty() && diff >= 10.0) {
                cleaned[maxCat] = FinanceEngine.round2((cleaned[maxCat] ?: 0.0) + diff)
            }
        }
        return cleaned
    }

    fun updateCategoryBudgets(budgets: Map<String, Double>, targetMonth: String? = null) {
        val monthToSave = targetMonth ?: _uiState.value.selectedMonth
        viewModelScope.launch {
            try {
                val calibrated = calibrateMapToIncome(budgets)
                repository.updateCategoryBudgets(calibrated, monthToSave)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao atualizar envelopes orçamentários", e)
            }
        }
    }

    fun applyAiSuggestedBudgets(targetMonth: String? = null) {
        val monthToSave = targetMonth ?: _uiState.value.selectedMonth
        viewModelScope.launch {
            try {
                val suggested = _uiState.value.suggestedBudgets.suggestedBudgets
                if (suggested.isNotEmpty()) {
                    val calibrated = calibrateMapToIncome(suggested)
                    repository.updateCategoryBudgets(calibrated, monthToSave)
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao aplicar sugestão de envelopes IA", e)
            }
        }
    }

    fun generateSmartBudgetPlan() {
        val user = _uiState.value.currentUser
        val report = _uiState.value.categoryBudgetReport
        val summary = _uiState.value.summary
        val availableIncome = if (report.totalAvailableIncome > 0.0) {
            report.totalAvailableIncome
        } else if ((summary?.income ?: 0.0) > 0.0) {
            summary!!.income
        } else {
            user.monthlyIncome
        }

        val spentMap = report.items.associate { it.category to it.spentAmount }
        val pendingMap = report.items.associate { it.category to it.pendingAmount }
        val histAverages = _uiState.value.suggestedBudgets.categoryAverages
        val currentLimits = report.items.associate { it.category to it.limit }

        _uiState.update { it.copy(isGeneratingSmartBudget = true) }
        viewModelScope.launch {
            try {
                val rawPlan = geminiLoadBalancer.generateSmartBudgetPlan(
                    monthlyIncome = availableIncome,
                    spentByCategory = spentMap,
                    pendingByCategory = pendingMap,
                    history3mAverages = histAverages,
                    existingLimits = currentLimits
                )
                val validatedPlan = FinanceEngine.sanitizeAndValidateBudgetPlan(
                    plan = rawPlan,
                    availableIncome = availableIncome,
                    spentByCategory = spentMap,
                    pendingByCategory = pendingMap
                )
                _uiState.update { it.copy(isGeneratingSmartBudget = false, smartBudgetPlan = validatedPlan) }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao gerar Smart Budget Plan com Gemini", e)
                _uiState.update { it.copy(isGeneratingSmartBudget = false) }
            }
        }
    }

    fun clearSmartBudgetPlan() {
        _uiState.update { it.copy(smartBudgetPlan = null) }
    }

    fun applySmartBudgetPlan(targetMonth: String? = null) {
        val plan = _uiState.value.smartBudgetPlan ?: return
        val monthToSave = targetMonth ?: _uiState.value.selectedMonth
        val budgetMap = plan.allocations.associate { it.category to it.suggestedLimit }
        if (budgetMap.isNotEmpty()) {
            updateCategoryBudgets(budgetMap, monthToSave)
            _uiState.update { it.copy(smartBudgetPlan = null) }
        }
    }

    fun generateFinancialHealthDiagnosis() {
        val health = _uiState.value.financialHealth
        val summary = _uiState.value.summary
        val user = _uiState.value.currentUser
        val expectedIncome = if ((summary?.income ?: 0.0) > 0.0) summary!!.income else user.monthlyIncome

        _uiState.update { it.copy(isGeneratingHealthAiDiagnosis = true) }
        viewModelScope.launch {
            try {
                val diagnosis = geminiLoadBalancer.generateFinancialHealthAiDiagnosis(
                    healthScore = health,
                    monthlyIncome = expectedIncome
                )
                _uiState.update { it.copy(isGeneratingHealthAiDiagnosis = false, healthAiDiagnosis = diagnosis) }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao gerar Diagnóstico de Saúde com Gemini", e)
                _uiState.update { it.copy(isGeneratingHealthAiDiagnosis = false) }
            }
        }
    }

    /** Pede ao Gemini uma análise dos envelopes; em caso de falha mantém os insights locais. */
    fun generateBudgetAiInsights() {
        val state = _uiState.value
        if (state.isGeneratingBudgetInsights) return
        val month = state.selectedMonth
        _uiState.update { it.copy(isGeneratingBudgetInsights = true) }
        viewModelScope.launch {
            try {
                val insights = geminiLoadBalancer.generateBudgetCoachInsights(
                    report = state.categoryBudgetReport,
                    health = state.financialHealth
                )
                _uiState.update {
                    it.copy(
                        isGeneratingBudgetInsights = false,
                        aiBudgetInsights = insights ?: it.aiBudgetInsights,
                        aiBudgetInsightsMonth = if (insights != null) month else it.aiBudgetInsightsMonth
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao gerar insights de envelopes com Gemini", e)
                _uiState.update { it.copy(isGeneratingBudgetInsights = false) }
            }
        }
    }

    /**
     * Aplica uma ação sugerida por um insight (local ou Gemini).
     * Nunca deixa um teto abaixo do que já está comprometido (pago + a vencer).
     */
    fun applyBudgetInsightAction(action: BudgetInsightAction) {
        val state = _uiState.value
        val month = state.selectedMonth
        when (action.type) {
            BudgetInsightActionType.ADJUST_LIMIT -> {
                val category = action.category ?: return
                val user = state.currentUser
                val monthBudgets = user.monthlyCategoryBudgets[month]?.takeIf { it.isNotEmpty() } ?: user.categoryBudgets
                val item = state.categoryBudgetReport.items.find { it.category == category }
                val current = monthBudgets[category] ?: item?.limit ?: 0.0
                val floor = kotlin.math.ceil((item?.committedAmount ?: 0.0) / 10.0) * 10.0
                val newLimit = max(floor, current + action.amount)
                val updated = monthBudgets.toMutableMap()
                if (newLimit > 0.0) updated[category] = newLimit else updated.remove(category)
                updateCategoryBudgets(updated, month)
                _uiState.update { it.copy(aiBudgetInsights = null, aiBudgetInsightsMonth = null) }
            }
            BudgetInsightActionType.REBALANCE -> {
                applyBudgetRebalance(action.fromCategory, action.category, action.amount, month)
                _uiState.update { it.copy(aiBudgetInsights = null, aiBudgetInsightsMonth = null) }
            }
            else -> Unit // OPEN_CONFIG, APPLY_PLAN e ASK_COPILOT são tratados pela tela (navegação/diálogos)
        }
    }

    fun applyBudgetRebalance(fromCategory: String? = null, toCategory: String? = null, amount: Double? = null, targetMonth: String? = null) {
        val monthToSave = targetMonth ?: _uiState.value.selectedMonth
        viewModelScope.launch {
            try {
                val user = _uiState.value.currentUser
                val currentMonthBudgets = user.monthlyCategoryBudgets[monthToSave]?.takeIf { it.isNotEmpty() } ?: user.categoryBudgets
                val current = currentMonthBudgets.toMutableMap()
                if (!fromCategory.isNullOrBlank() && !toCategory.isNullOrBlank() && amount != null && amount > 0.0) {
                    val fromVal = current[fromCategory] ?: 0.0
                    val toVal = current[toCategory] ?: 0.0
                    current[fromCategory] = max(0.0, FinanceEngine.round2(fromVal - amount))
                    current[toCategory] = FinanceEngine.round2(toVal + amount)
                    repository.updateCategoryBudgets(current, monthToSave)
                } else {
                    val suggestions = _uiState.value.budgetRebalance.suggestions
                    if (suggestions.isNotEmpty()) {
                        for (s in suggestions) {
                            val fromVal = current[s.compensateFrom] ?: 0.0
                            val toVal = current[s.overspentCategory] ?: 0.0
                            current[s.compensateFrom] = max(0.0, FinanceEngine.round2(fromVal - s.suggestedTransfer))
                            current[s.overspentCategory] = FinanceEngine.round2(toVal + s.suggestedTransfer)
                        }
                        repository.updateCategoryBudgets(current, monthToSave)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Erro ao rebalancear envelopes", e)
            }
        }
    }
}
