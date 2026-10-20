package com.saldolivre.app.data.model

/**
 * Projeção orçamentária para meses futuros, espelhando FutureMonthProjection de web/src/types/finance.ts.
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
    val expenseCount: Int = 0
)
