package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable

enum class FinancialHealthClassification {
    CRITICAL,
    WARNING,
    HEALTHY,
    EXCELLENT
}

enum class CategoryBudgetPaceStatus {
    NORMAL,
    FAST,
    CRITICAL;

    companion object {
        val ON_TRACK = NORMAL
    }
}

enum class CategoryBudgetAlertStatus {
    OK,
    WARNING,
    DANGER;

    companion object {
        val SAFE = OK
    }
}

typealias BudgetEnvelopeStatus = CategoryBudgetAlertStatus
typealias BudgetBurnRateStatus = CategoryBudgetPaceStatus

@Immutable
data class CategoryBudgetStatus(
    val category: String,
    val limit: Double,
    val rawLimit: Double = limit,
    val spent: Double,
    val spentAmount: Double = spent,
    val pendingAmount: Double = 0.0,
    val committedAmount: Double = spent,
    val remaining: Double,
    val realRemaining: Double = remaining,
    val percentage: Double,
    val pendingPercentage: Double = 0.0,
    val totalPercentage: Double = percentage,
    val status: CategoryBudgetAlertStatus = CategoryBudgetAlertStatus.OK,
    val badgeLabel: String = "Sob Controle",
    val dailyPace: Double = 0.0,
    val projectedSpent: Double = 0.0,
    val projectedPercentage: Double = 0.0,
    val recommendedDailyRemaining: Double = 0.0,
    val burnRateStatus: CategoryBudgetPaceStatus = CategoryBudgetPaceStatus.NORMAL,
    val daysRemaining: Int = 0,
    val currentDay: Int = 1,
    val totalDays: Int = 30,
    val isFixedCategory: Boolean = false
)

@Immutable
data class CategoryBudgetReport(
    val items: List<CategoryBudgetStatus> = emptyList(),
    val totalBudgeted: Double = 0.0,
    val rawTotalBudgeted: Double = totalBudgeted,
    val expectedIncome: Double = 0.0,
    val carryoverAmount: Double = 0.0,
    val previousMonthDeficit: Double = 0.0,
    val isCappedToIncome: Boolean = false,
    val incomeUsagePercentage: Double = 0.0,
    val unallocatedIncome: Double = 0.0,
    val totalSpent: Double = 0.0,
    val totalPending: Double = 0.0,
    val totalCommitted: Double = totalSpent,
    val totalRemaining: Double = 0.0,
    val totalRealRemaining: Double = totalRemaining,
    val overallPercentage: Double = 0.0,
    val status: CategoryBudgetAlertStatus = CategoryBudgetAlertStatus.OK,
    val criticalCount: Int = 0,
    val warningCount: Int = 0,
    val topOverspentCategory: CategoryBudgetStatus? = null,
    val unbudgetedCategories: List<CategoryExpense> = emptyList(),
    val investmentTarget: Double = 0.0,
    val investmentSpent: Double = 0.0,
    val investmentPercentage: Double = 0.0
) {
    val totalAvailableIncome: Double get() = kotlin.math.max(0.0, expectedIncome + carryoverAmount - previousMonthDeficit)
    val categories: List<CategoryBudgetStatus> get() = items
    val totalSpentInBudgeted: Double get() = totalSpent
    val overspentCount: Int get() = items.count { it.limit > 0.0 && ((it.committedAmount - it.limit) >= 0.01) }
    val safeCount: Int get() = items.count { it.limit > 0.0 && it.status == CategoryBudgetAlertStatus.OK }
}

@Immutable
data class BudgetRebalanceSuggestion(
    val overspentCategory: String,
    val overspentAmount: Double,
    val compensateFrom: String,
    val availableSurplus: Double,
    val suggestedTransfer: Double,
    val newFromLimit: Double = 0.0,
    val newToLimit: Double = 0.0
) {
    val fromCategory: String get() = compensateFrom
    val toCategory: String get() = overspentCategory
    val amount: Double get() = suggestedTransfer
}

@Immutable
data class SuggestedBudgetsResult(
    val suggestedBudgets: Map<String, Double> = emptyMap(),
    val totalSuggested: Double = 0.0,
    val incomeCoveragePct: Double = 0.0,
    val expectedIncome: Double = 0.0,
    val isCappedToIncome: Boolean = false,
    val categoryAverages: Map<String, Double> = emptyMap()
)

@Immutable
data class BudgetRebalanceResult(
    val canRebalance: Boolean = false,
    val deficitTotal: Double = 0.0,
    val surplusTotal: Double = 0.0,
    val suggestions: List<BudgetRebalanceSuggestion> = emptyList(),
    val summaryText: String = ""
) {
    val canCoverFullDeficit: Boolean get() = canRebalance && surplusTotal >= deficitTotal && deficitTotal > 0.0
    val totalDeficit: Double get() = deficitTotal
    val totalCovered: Double get() = suggestions.sumOf { it.suggestedTransfer }
    val transfers: List<BudgetRebalanceSuggestion> get() = suggestions
}

@Immutable
data class FinancialHealthScore(
    val score: Int = 0,
    val classification: FinancialHealthClassification = FinancialHealthClassification.CRITICAL,
    val label: String = "Crítico",
    val description: String = "",
    val savingsScore: Int = 0,
    val spendingLimitScore: Int = 0,
    val emergencyReserveScore: Int = 0,
    val envelopeScore: Int = 0,
    val savingsRatePercentage: Int = 0,
    val spendingRatioPercentage: Int = 0,
    val emergencyCoverageMonths: Double = 0.0,
    val budgetAdherenceScore: Int = 0,
    val budgetConfiguredCount: Int = 0,
    val budgetOverspentCount: Int = 0,
    val monthlyLivingCost: Double = 0.0,
    val potentialSavingsRatePercentage: Int = 0,
    val actionableTips: List<String> = emptyList()
)

@Immutable
data class SmartBudgetAllocation(
    val category: String,
    val suggestedLimit: Double,
    val reason: String = ""
)

@Immutable
data class SmartBudgetPlan(
    val summaryInsight: String = "",
    val allocations: List<SmartBudgetAllocation> = emptyList()
)

@Immutable
data class FinancialHealthAiDiagnosis(
    val overallAssessment: String = "",
    val actionPlan: List<String> = emptyList()
)
