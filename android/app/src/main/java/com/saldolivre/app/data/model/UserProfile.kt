package com.saldolivre.app.data.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String? = null,
    val monthlyIncome: Double = 0.0,
    val resolvedRecurring: Map<String, List<String>> = emptyMap()
)
