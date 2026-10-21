package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class CategoryExpense(
    val category: String,
    val amount: Double
)

@Immutable
data class InvestmentSubcat(
    val subcategory: String,
    val amount: Double,
    val percentage: Int
)

@Immutable
data class ReceivableItem(
    val transactionId: String,
    val description: String,
    val dueDate: String,
    val name: String,
    val amount: Double,
    val type: String, // "PLATFORM_USER" | "EXTERNAL"
    val cardName: String? = null,
    val reimbursed: Boolean = false
)

@Immutable
data class MonthSummary(
    val monthKey: String = "", // YYYY-MM
    val income: Double = 0.0,
    val paid: Double = 0.0,
    val committed: Double = 0.0,
    val committedEstimated: Double = 0.0,
    val committedPending: Double = 0.0,
    val freeBalance: Double = 0.0,
    val daysRemaining: Int = 0,
    val daysInMonth: Int = 30,
    val dailyLimit: Double? = null,
    val monthState: String = "current", // "past" | "current" | "future"
    val receivableTotal: Double = 0.0,
    val receivableFromPartner: Double = 0.0,
    val receivableFromExternal: Double = 0.0,
    val receivableFromFriends: Double = 0.0,
    val payableToOthers: Double = 0.0,
    val totalInvested: Double = 0.0,
    val byCategory: List<CategoryExpense> = emptyList(),
    val byInvestmentSubcategory: List<InvestmentSubcat> = emptyList(),
    val receivables: List<ReceivableItem> = emptyList()
)
