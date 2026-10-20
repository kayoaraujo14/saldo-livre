package com.saldolivre.app.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class ActivityActionType(val label: String) {
    @SerializedName("transaction_created")
    TRANSACTION_CREATED("Lançamento Criado"),

    @SerializedName("transaction_updated")
    TRANSACTION_UPDATED("Lançamento Editado"),

    @SerializedName("transaction_deleted")
    TRANSACTION_DELETED("Lançamento Excluído"),

    @SerializedName("transaction_paid")
    TRANSACTION_PAID("Pagamento Confirmado"),

    @SerializedName("estimated_updated")
    ESTIMATED_UPDATED("Previsão Atualizada"),

    @SerializedName("bills_generated")
    BILLS_GENERATED("Contas do Mês Geradas"),

    @SerializedName("card_saved")
    CARD_SAVED("Cartão Salvo"),

    @SerializedName("recurring_saved")
    RECURRING_SAVED("Assinatura Salva"),

    @SerializedName("friend_action")
    FRIEND_ACTION("Rede de Amizades")
}

data class ActivityLog(
    val id: String = UUID.randomUUID().toString(),
    val actionType: ActivityActionType = ActivityActionType.TRANSACTION_CREATED,
    val title: String = "",
    val description: String = "",
    val amount: Double? = null,
    val category: String? = null,
    val competenceMonth: String? = null,
    val userUid: String = "",
    val userName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
