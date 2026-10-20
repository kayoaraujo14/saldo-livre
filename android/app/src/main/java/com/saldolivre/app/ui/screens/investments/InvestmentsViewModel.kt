package com.saldolivre.app.ui.screens.investments

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

enum class InvestmentTab {
    OVERVIEW,
    CALCULATOR,
    RULE_50_30_20,
    HISTORY
}

enum class InvestmentBucketType(val title: String, val subtitle: String) {
    SAFE_RESERVE("Caixinhas & Reservas Seguras", "Renda Fixa, Tesouro Direto e Reserva de Emergência"),
    RISK_VARIABLE("Investimentos de Risco & Variável", "Ações B3, FIIs, Criptomoedas e Ativos Digitais")
}

data class InvestmentAsset(
    val subcategory: String,
    val bucketType: InvestmentBucketType,
    val totalInvestedAllTime: Double,
    val totalInvestedThisMonth: Double,
    val currentBalance: Double,
    val color: Color
) {
    val profitOrLoss: Double
        get() = FinanceEngine.round2(currentBalance - totalInvestedAllTime)

    val profitPercentage: Double
        get() = if (totalInvestedAllTime > 0.0) FinanceEngine.round2((profitOrLoss / totalInvestedAllTime) * 100.0) else 0.0
}

data class Rule503020Data(
    val income: Double = 0.0,
    val targetNeeds: Double = 0.0,
    val targetWants: Double = 0.0,
    val targetInvest: Double = 0.0,
    val realNeeds: Double = 0.0,
    val realWants: Double = 0.0,
    val realInvest: Double = 0.0,
    val needsPct: Double = 0.0,
    val wantsPct: Double = 0.0,
    val investPct: Double = 0.0
)

data class YearlySimulationData(
    val year: Int,
    val invested: Double,
    val total: Double,
    val interest: Double
)

data class CompoundSimulationResult(
    val futureValue: Double = 0.0,
    val totalInvested: Double = 0.0,
    val totalInterest: Double = 0.0,
    val yearlyData: List<YearlySimulationData> = emptyList()
)

data class InvestmentsUiState(
    val currentUser: UserProfile = UserProfile(),
    val selectedMonth: String = "",
    val allInvestments: List<Transaction> = emptyList(),
    val monthInvestments: List<Transaction> = emptyList(),
    val totalInvestedThisMonth: Double = 0.0,
    val totalInvestedAllTime: Double = 0.0,
    val totalCurrentBalance: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyTarget: Double = 1000.0,
    val isEditingTarget: Boolean = false,
    val targetInput: String = "1000",
    val assets: List<InvestmentAsset> = emptyList(),
    val activeTab: InvestmentTab = InvestmentTab.OVERVIEW,
    val historyFilter: String = "all",
    val rule503020: Rule503020Data = Rule503020Data(),
    val isAddAporteOpen: Boolean = false,
    val isResgateOpen: Boolean = false,
    val isEditBalanceOpen: Boolean = false,
    val selectedAssetForAction: InvestmentAsset? = null,
    val feedbackMessage: String? = null
)

class InvestmentsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val appContext: Context? = runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val prefs = appContext?.getSharedPreferences("saldo_livre_investments_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(InvestmentsUiState())
    val uiState: StateFlow<InvestmentsUiState> = _uiState.asStateFlow()

    companion object {
        val ASSET_DEFINITIONS = listOf(
            Triple("Reserva de Emergência", InvestmentBucketType.SAFE_RESERVE, Color(0xFF06B6D4)),
            Triple("Renda Fixa (CDB / Tesouro / LCI / LCA)", InvestmentBucketType.SAFE_RESERVE, Color(0xFF10B981)),
            Triple("Previdência Privada", InvestmentBucketType.SAFE_RESERVE, Color(0xFFEC4899)),
            Triple("Ações & Fundos Imobiliários (FIIs)", InvestmentBucketType.RISK_VARIABLE, Color(0xFF8B5CF6)),
            Triple("Criptomoedas & Ativos Digitais", InvestmentBucketType.RISK_VARIABLE, Color(0xFFF59E0B)),
            Triple("Fundos de Investimento", InvestmentBucketType.RISK_VARIABLE, Color(0xFF3B82F6)),
            Triple("Outros Investimentos", InvestmentBucketType.RISK_VARIABLE, Color(0xFF94A3B8))
        )
    }

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getTransactions()
            ) { user, month, transactions ->
                Triple(user, month, transactions)
            }.collect { (user, month, transactions) ->
                val myTxs = transactions.filter { it.visibleToUids.contains(user.uid) }
                val allInvTxs = myTxs.filter { it.category == "Investimentos" }
                    .sortedByDescending { it.dueDate }
                val monthInvTxs = allInvTxs.filter { it.competenceMonth == month }

                val monthInvested = FinanceEngine.round2(
                    monthInvTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.effectiveAmount }
                )
                val allInvested = FinanceEngine.round2(
                    allInvTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.effectiveAmount }
                )

                val savedTarget = prefs?.getFloat("target_${user.uid}", 1000f)?.toDouble() ?: 1000.0

                // Build assets list
                val assets = ASSET_DEFINITIONS.map { (subName, bucketType, color) ->
                    val allSubTxs = allInvTxs.filter { (it.subcategory ?: "Outros Investimentos") == subName }
                    val monthSubTxs = monthInvTxs.filter { (it.subcategory ?: "Outros Investimentos") == subName }

                    val subInvestedAllTime = FinanceEngine.round2(
                        allSubTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.effectiveAmount } -
                        allSubTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.effectiveAmount }
                    ).coerceAtLeast(0.0)

                    val subInvestedMonth = FinanceEngine.round2(
                        monthSubTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.effectiveAmount }
                    )

                    // Stored manual balance if edited, otherwise defaults to invested amount
                    val storedBalance = prefs?.getString("balance_${user.uid}_$subName", null)?.toDoubleOrNull()
                    val curBalance = storedBalance ?: subInvestedAllTime

                    InvestmentAsset(
                        subcategory = subName,
                        bucketType = bucketType,
                        totalInvestedAllTime = subInvestedAllTime,
                        totalInvestedThisMonth = subInvestedMonth,
                        currentBalance = curBalance,
                        color = color
                    )
                }

                val totalCurrentBalance = FinanceEngine.round2(assets.sumOf { it.currentBalance })

                // Rule 50/30/20 calculation
                val monthAllTxs = myTxs.filter { it.competenceMonth == month }
                val income = monthAllTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.effectiveAmount }
                    .takeIf { it > 0.0 } ?: user.monthlyIncome

                val needsCats = setOf(
                    "Moradia", "Alimentação", "Saúde", "Transporte", "Educação", "Financiamentos & Empréstimos"
                )
                var realNeeds = 0.0
                var realWants = 0.0

                for (t in monthAllTxs) {
                    if (t.type == TransactionType.INCOME || t.category == "Investimentos") continue
                    if (needsCats.contains(t.category)) {
                        realNeeds += t.effectiveAmount
                    } else {
                        realWants += t.effectiveAmount
                    }
                }

                val targetNeeds = FinanceEngine.round2(income * 0.5)
                val targetWants = FinanceEngine.round2(income * 0.3)
                val targetInvest = FinanceEngine.round2(income * 0.2)

                val rule503020 = Rule503020Data(
                    income = FinanceEngine.round2(income),
                    targetNeeds = targetNeeds,
                    targetWants = targetWants,
                    targetInvest = targetInvest,
                    realNeeds = FinanceEngine.round2(realNeeds),
                    realWants = FinanceEngine.round2(realWants),
                    realInvest = monthInvested,
                    needsPct = if (income > 0) FinanceEngine.round2((realNeeds / income) * 100) else 0.0,
                    wantsPct = if (income > 0) FinanceEngine.round2((realWants / income) * 100) else 0.0,
                    investPct = if (income > 0) FinanceEngine.round2((monthInvested / income) * 100) else 0.0
                )

                _uiState.update { current ->
                    current.copy(
                        currentUser = user,
                        selectedMonth = month,
                        allInvestments = allInvTxs,
                        monthInvestments = monthInvTxs,
                        totalInvestedThisMonth = monthInvested,
                        totalInvestedAllTime = allInvested,
                        totalCurrentBalance = totalCurrentBalance,
                        monthlyIncome = income,
                        monthlyTarget = savedTarget,
                        targetInput = if (!current.isEditingTarget) savedTarget.toInt().toString() else current.targetInput,
                        assets = assets,
                        rule503020 = rule503020
                    )
                }
            }
        }
    }

    fun setTab(tab: InvestmentTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setHistoryFilter(filter: String) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun startEditingTarget() {
        _uiState.update { it.copy(isEditingTarget = true, targetInput = it.monthlyTarget.toInt().toString()) }
    }

    fun cancelEditingTarget() {
        _uiState.update { it.copy(isEditingTarget = false) }
    }

    fun setTargetInput(v: String) {
        _uiState.update { it.copy(targetInput = v) }
    }

    fun saveTarget() {
        val num = _uiState.value.targetInput.replace(',', '.').toDoubleOrNull() ?: 1000.0
        val target = max(0.0, num)
        val uid = _uiState.value.currentUser.uid
        prefs?.edit()?.putFloat("target_$uid", target.toFloat())?.apply()
        _uiState.update { it.copy(monthlyTarget = target, isEditingTarget = false, feedbackMessage = "Meta mensal atualizada!") }
    }

    fun openAddAporte(asset: InvestmentAsset? = null) {
        _uiState.update { it.copy(isAddAporteOpen = true, selectedAssetForAction = asset) }
    }

    fun closeAddAporte() {
        _uiState.update { it.copy(isAddAporteOpen = false, selectedAssetForAction = null) }
    }

    fun submitAporte(subcat: String, amount: Double, desc: String, date: String) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val user = _uiState.value.currentUser
            val month = if (date.length >= 7) date.substring(0, 7) else _uiState.value.selectedMonth

            val tx = Transaction(
                id = "inv_tx_" + UUID.randomUUID().toString().take(8),
                createdByUid = user.uid,
                paidByUid = user.uid,
                description = if (desc.isNotBlank()) desc else "Aporte - $subcat",
                category = "Investimentos",
                subcategory = subcat,
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                status = TransactionStatus.PAID,
                totalAmount = amount,
                actualAmount = amount,
                dueDate = date,
                competenceMonth = month,
                visibleToUids = listOf(user.uid),
                allocations = listOf(
                    Allocation(type = "PLATFORM_USER", uid = user.uid, name = user.displayName, amount = amount, reimbursed = true)
                ),
                userImpact = mapOf(user.uid to amount),
                splitType = SplitType.MINE
            )
            repository.addTransaction(tx)

            // Update current balance by adding the contribution
            val currentStored = prefs?.getString("balance_${user.uid}_$subcat", null)?.toDoubleOrNull()
            val existing = _uiState.value.assets.find { it.subcategory == subcat }?.currentBalance ?: 0.0
            val newBal = (currentStored ?: existing) + amount
            prefs?.edit()?.putString("balance_${user.uid}_$subcat", newBal.toString())?.apply()

            closeAddAporte()
            _uiState.update { it.copy(feedbackMessage = "Aporte de ${FinanceEngine.formatCurrency(amount)} registrado com sucesso!") }
        }
    }

    fun openResgate(asset: InvestmentAsset? = null) {
        _uiState.update { it.copy(isResgateOpen = true, selectedAssetForAction = asset) }
    }

    fun closeResgate() {
        _uiState.update { it.copy(isResgateOpen = false, selectedAssetForAction = null) }
    }

    fun submitResgate(subcat: String, amount: Double, desc: String, date: String) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val user = _uiState.value.currentUser
            val month = if (date.length >= 7) date.substring(0, 7) else _uiState.value.selectedMonth

            val tx = Transaction(
                id = "res_tx_" + UUID.randomUUID().toString().take(8),
                createdByUid = user.uid,
                paidByUid = user.uid,
                description = if (desc.isNotBlank()) desc else "Resgate - $subcat",
                category = "Investimentos",
                subcategory = subcat,
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.PIX,
                status = TransactionStatus.PAID,
                totalAmount = amount,
                actualAmount = amount,
                dueDate = date,
                competenceMonth = month,
                visibleToUids = listOf(user.uid),
                allocations = listOf(
                    Allocation(type = "PLATFORM_USER", uid = user.uid, name = user.displayName, amount = amount, reimbursed = true)
                ),
                userImpact = mapOf(user.uid to amount),
                splitType = SplitType.MINE
            )
            repository.addTransaction(tx)

            // Deduct from current balance
            val existing = _uiState.value.assets.find { it.subcategory == subcat }?.currentBalance ?: 0.0
            val newBal = max(0.0, existing - amount)
            prefs?.edit()?.putString("balance_${user.uid}_$subcat", newBal.toString())?.apply()

            closeResgate()
            _uiState.update { it.copy(feedbackMessage = "Resgate de ${FinanceEngine.formatCurrency(amount)} concluído!") }
        }
    }

    fun openEditBalance(asset: InvestmentAsset) {
        _uiState.update { it.copy(isEditBalanceOpen = true, selectedAssetForAction = asset) }
    }

    fun closeEditBalance() {
        _uiState.update { it.copy(isEditBalanceOpen = false, selectedAssetForAction = null) }
    }

    fun submitEditBalance(subcat: String, newBalance: Double) {
        val user = _uiState.value.currentUser
        val valid = max(0.0, newBalance)
        prefs?.edit()?.putString("balance_${user.uid}_$subcat", valid.toString())?.apply()

        _uiState.update { current ->
            val updatedAssets = current.assets.map {
                if (it.subcategory == subcat) it.copy(currentBalance = valid) else it
            }
            current.copy(
                assets = updatedAssets,
                totalCurrentBalance = FinanceEngine.round2(updatedAssets.sumOf { it.currentBalance }),
                isEditBalanceOpen = false,
                selectedAssetForAction = null,
                feedbackMessage = "Saldo de $subcat atualizado para ${FinanceEngine.formatCurrency(valid)}!"
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun simulateCompoundInterest(
        initialAmount: Double,
        monthlyContribution: Double,
        annualRate: Double,
        years: Int
    ): CompoundSimulationResult {
        val rMonthly = (1.0 + annualRate / 100.0).pow(1.0 / 12.0) - 1.0
        val totalMonths = years * 12
        var futureValue = initialAmount
        var totalInvestedBolso = initialAmount
        val yearlyData = mutableListOf<YearlySimulationData>()

        for (m in 1..totalMonths) {
            futureValue = futureValue * (1.0 + rMonthly) + monthlyContribution
            totalInvestedBolso += monthlyContribution

            if (m % 12 == 0) {
                val y = m / 12
                val invRound = totalInvestedBolso.roundToLong().toDouble()
                val totalRound = futureValue.roundToLong().toDouble()
                val interestRound = max(0.0, totalRound - invRound)
                yearlyData.add(
                    YearlySimulationData(
                        year = y,
                        invested = invRound,
                        total = totalRound,
                        interest = interestRound
                    )
                )
            }
        }

        val totalInterest = max(0.0, futureValue - totalInvestedBolso)
        return CompoundSimulationResult(
            futureValue = FinanceEngine.round2(futureValue),
            totalInvested = FinanceEngine.round2(totalInvestedBolso),
            totalInterest = FinanceEngine.round2(totalInterest),
            yearlyData = yearlyData
        )
    }
}
