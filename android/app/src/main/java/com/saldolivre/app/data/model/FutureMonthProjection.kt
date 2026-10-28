package com.saldolivre.app.data.model

data class ProjectedCategoryItem(
    val category: String = "",
    val amount: Double = 0.0,
    val invoiceAmount: Double = 0.0,
    val accountAmount: Double = 0.0,
    val fixedBillAmount: Double = 0.0,
    val count: Int = 0
)

data class ProjectedMonthItem(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val myImpact: Double = 0.0,
    val category: String = "",
    val subcategory: String? = null,
    val dueDate: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val paymentMethod: PaymentMethod = PaymentMethod.BILL,
    val cardId: String? = null,
    val cardName: String? = null,
    val origin: String = "fixed_bill", // "invoice" | "fixed_bill" | "debit_pix" | "income"
    val status: String = "estimated", // "paid" | "pending" | "estimated" | "projected_recurring"
    val isRecurringProjection: Boolean = false,
    val installmentInfo: String? = null
)

/**
 * Projeção orçamentária para meses futuros, espelhando FutureMonthProjection de web/src/types/index.ts.
 */
data class FutureMonthProjection(
    val monthKey: String = "",
    val label: String = "",
    val daysInMonth: Int = 30,
    val projectedIncome: Double = 0.0,
    val projectedExpense: Double = 0.0,
    val projectedFreeBalance: Double = 0.0,
    val projectedDailyLimit: Double? = null,
    val isGenerated: Boolean = false,
    val incomeCount: Int = 0,
    val expenseCount: Int = 0,
    val invoiceExpense: Double = 0.0,
    val fixedBillsExpense: Double = 0.0,
    val variableAccountExpense: Double = 0.0,
    val byCategory: List<ProjectedCategoryItem> = emptyList(),
    val items: List<ProjectedMonthItem> = emptyList()
)
