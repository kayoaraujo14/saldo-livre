package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable
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
    ESTIMATED("estimated", "Estimado"),

    @SerializedName("pending")
    PENDING("pending", "Pendente"),

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
        fun from(value: String?): SplitType? {
            if (value.isNullOrBlank()) return null
            return when (value.lowercase().trim()) {
                "mine", "100% meu" -> MINE
                "split_50", "shared_50_50", "50/50", "casal" -> SPLIT_50
                "partner", "parceiro" -> PARTNER
                "friend", "amigo" -> FRIEND
                "external", "third_party", "terceiro" -> EXTERNAL
                "custom", "personalizada" -> CUSTOM
                else -> entries.find { it.value.equals(value.trim(), ignoreCase = true) }
            }
        }
    }
}

enum class AcceptanceStatus(val value: String, val label: String) {
    @SerializedName("pending_approval")
    PENDING_APPROVAL("pending_approval", "Aguardando Aprovação"),

    @SerializedName("accepted")
    ACCEPTED("accepted", "Aceito"),

    @SerializedName("rejected")
    REJECTED("rejected", "Recusado");

    companion object {
        fun from(value: String?): AcceptanceStatus? =
            entries.find { it.value.equals(value, ignoreCase = true) }
    }
}

@Immutable
data class Allocation(
    val type: String = "PLATFORM_USER", // "PLATFORM_USER" | "EXTERNAL"
    val uid: String? = null,
    val name: String = "",
    val amount: Double = 0.0,
    val reimbursed: Boolean = false
)

@Immutable
data class CustomParticipantRow(
    val id: String,
    val type: String, // "me" | "partner" | "friend" | "external"
    val uid: String? = null,
    val name: String = "",
    val amount: Double = 0.0,
    val percentage: Double = 0.0
)

@Immutable
data class FriendSplitInput(
    val uid: String = "",
    val name: String = "",
    val pct: Double? = null
)

@Immutable
data class InstallmentGroupUpdateInput(
    val description: String? = null,
    val amount: Double? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val cardId: String? = null,
    val splitType: SplitType? = null,
    val externalName: String? = null,
    val friend: FriendSplitInput? = null,
    val customAllocations: List<Allocation>? = null,
    val date: String? = null,
    val status: TransactionStatus? = null
)

@Immutable
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

    // Compartilhamento com Amigos & Aceite Prévio
    val assignedFriendUid: String? = null,
    val assignedFriendName: String? = null,
    val acceptanceStatus: String? = null, // "pending_approval", "accepted", "rejected"
    val friendSharePct: Double? = null,
    val respondedAt: Long? = null,

    // Parcelamento & Grupo
    val installmentGroupId: String? = null,
    val installmentNumber: Int? = null,
    val installmentsTotal: Int? = null,
    val splitType: SplitType? = null,
    val externalName: String? = null
) {
    val effectiveAmount: Double
        get() = actualAmount ?: estimatedAmount ?: totalAmount

    val isInstallment: Boolean
        get() = !installmentGroupId.isNullOrBlank() || (installmentNumber != null && installmentsTotal != null)

    val installmentTag: String?
        get() = if (installmentNumber != null && installmentsTotal != null) "$installmentNumber/$installmentsTotal" else null

    val isPendingApproval: Boolean
        get() = acceptanceStatus == AcceptanceStatus.PENDING_APPROVAL.value

    val isAccepted: Boolean
        get() = acceptanceStatus == AcceptanceStatus.ACCEPTED.value

    val isRejected: Boolean
        get() = acceptanceStatus == AcceptanceStatus.REJECTED.value

    val isRefund: Boolean
        get() = category.equals("Estorno", ignoreCase = true) ||
                subcategory?.contains("Estorno", ignoreCase = true) == true ||
                subcategory?.contains("Crédito na Fatura", ignoreCase = true) == true ||
                effectiveAmount < 0.0 ||
                totalAmount < 0.0
}
