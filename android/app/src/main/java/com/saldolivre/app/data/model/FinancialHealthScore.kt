package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable

enum class FinancialHealthClassification {
    CRITICAL,
    WARNING,
    HEALTHY,
    EXCELLENT
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
    val savingsRatePercentage: Int = 0,
    val spendingRatioPercentage: Int = 0,
    val emergencyCoverageMonths: Double = 0.0
)
