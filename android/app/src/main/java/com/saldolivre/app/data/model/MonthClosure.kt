package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class MonthClosure(
    val id: String = "",
    val uid: String = "",
    val closedMonth: String = "", // Mês que encerrou (ex: "2026-09")
    val targetMonth: String = "", // Mês seguinte que recebe o impacto (ex: "2026-10")
    val totalSurplus: Double = 0.0,
    val carryoverToNextMonth: Double = 0.0, // Montante adicionado ao Saldo Livre do mês seguinte
    val investedAmount: Double = 0.0, // Montante convertido em investimento
    val cushionAmount: Double = 0.0, // Montante retido como Colchão de Imprevistos
    val investmentTransactionId: String? = null,
    val closedAt: Long = System.currentTimeMillis()
)
