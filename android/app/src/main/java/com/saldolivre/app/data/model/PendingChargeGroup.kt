package com.saldolivre.app.data.model

/**
 * Agrupa despesas pendentes atribuídas a um amigo para aceite ou rejeição
 * (tanto para compras unitárias quanto para compras parceladas vinculadas por `installmentGroupId`).
 */
data class PendingChargeGroup(
    val id: String = "",
    val isInstallment: Boolean = false,
    val installmentGroupId: String? = null,
    val installmentsCount: Int = 1,
    val baseDescription: String = "",
    val cardId: String? = null,
    val senderUid: String = "",
    val senderName: String = "",
    val totalAmount: Double = 0.0,
    val friendShareAmount: Double = 0.0,
    val friendSharePct: Double = 100.0,
    val firstDueDate: String = "",
    val lastDueDate: String = "",
    val perInstallmentAmount: Double = 0.0,
    val perInstallmentFriendShare: Double = 0.0,
    val items: List<Transaction> = emptyList(),
    val primaryTx: Transaction = Transaction()
)
