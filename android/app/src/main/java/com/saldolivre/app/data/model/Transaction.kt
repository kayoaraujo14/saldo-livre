package com.saldolivre.app.data.model

import com.google.gson.annotations.SerializedName

enum class TransactionType(val value: String) {
    @SerializedName("expense")
    EXPENSE("expense"),

    @SerializedName("income")
    INCOME("income");

    companion object {
        fun from(value: String): TransactionType =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: EXPENSE
    }
}

enum class PaymentMethod(val value: String, val label: String) {
    @SerializedName("pix")
    PIX("pix", "Pix"),

    @SerializedName("credit_card")
    CREDIT_CARD("credit_card", "Cartão de Crédito"),

    @SerializedName("debit")
    DEBIT("debit", "Débito"),

    @SerializedName("bill")
    BILL("bill", "Boleto");

    companion object {
        fun from(value: String?): PaymentMethod =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: PIX
    }
}

enum class TransactionStatus(val value: String, val label: String) {
    @SerializedName("estimated")
    ESTIMATED("estimated", "Previsão"),

    @SerializedName("pending")
    PENDING("pending", "A Pagar"),

    @SerializedName("paid")
    PAID("paid", "Pago");

    companion object {
        fun from(value: String): TransactionStatus =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: PENDING
    }
}

enum class SplitType(val value: String, val label: String, val emoji: String) {
    @SerializedName("mine")
    MINE("mine", "100% Meu", "👤"),

    @SerializedName("split_50")
    SPLIT_50("split_50", "50/50 (Casal)", "🤝"),

    @SerializedName("partner")
    PARTNER("partner", "100% Parceiro(a)", "🔄"),

    @SerializedName("friend")
    FRIEND("friend", "Amigo da Rede", "🧑‍🤝‍🧑"),

    @SerializedName("external")
    EXTERNAL("external", "Terceiro Externo", "🏷️"),

    @SerializedName("custom")
    CUSTOM("custom", "Personalizada", "⚖️");

    companion object {
        fun from(value: String?): SplitType =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: MINE
    }
}

data class Allocation(
    val type: String = "PLATFORM_USER", // "PLATFORM_USER" | "EXTERNAL"
    val uid: String? = null,
    val name: String = "",
    val amount: Double = 0.0,
    val reimbursed: Boolean = false
)

data class Transaction(
    val id: String = "",
    val createdByUid: String = "",
    val paidByUid: String = "",
    val cardId: String? = null,
    val description: String = "",
    val category: String = "Outros",
    val subcategory: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val paymentMethod: PaymentMethod = PaymentMethod.PIX,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val estimatedAmount: Double? = null,
    val actualAmount: Double? = null,
    val totalAmount: Double = 0.0,
    val dueDate: String = "", // YYYY-MM-DD
    val competenceMonth: String = "", // YYYY-MM
    val visibleToUids: List<String> = emptyList(),
    val userImpact: Map<String, Double> = emptyMap(),
    val allocations: List<Allocation> = emptyList(),
    val recurringBillId: String? = null,
    val reserveCardId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // Compartilhamento com Amigos
    val assignedFriendUid: String? = null,
    val assignedFriendName: String? = null,
    val acceptanceStatus: String? = null, // "pending_approval", "accepted", "rejected"
    val friendSharePct: Double? = null,

    // Parcelamento
    val installmentGroupId: String? = null,
    val installmentNumber: Int? = null,
    val installmentsTotal: Int? = null,
    val splitType: SplitType? = null
) {
    val effectiveAmount: Double
        get() = actualAmount ?: estimatedAmount ?: totalAmount
}
