package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String? = null,
    val monthlyIncome: Double = 0.0,
    val emergencyReserveBalance: Double = 0.0,
    val resolvedRecurring: Map<String, List<String>> = emptyMap(),
    val investmentMonthlyTarget: Double = 1000.0,
    val investmentAssetBalances: Map<String, Double> = emptyMap(),
    val categoryBudgets: Map<String, Double> = emptyMap(),
    val monthlyCategoryBudgets: Map<String, Map<String, Double>> = emptyMap()
)
