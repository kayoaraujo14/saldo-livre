package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable
import com.saldolivre.app.data.engine.FinanceEngine
import java.util.UUID

/**
 * Esboço de despesa detectada automaticamente via notificação bancária do Android.
 * Pode ser aprovado diretamente pela notificação ou revisado/ajustado no Chat IA.
 */
@Immutable
data class ExpenseDraft(
    val id: String = "draft_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(4)}",
    val amount: Double = 0.0,
    val description: String = "",
    val category: String = "Outros",
    val subcategory: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CREDIT_CARD,
    val cardId: String? = null,
    val cardName: String? = null,
    val date: String = FinanceEngine.todayKey(),
    val rawNotificationText: String = "",
    val packageName: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun paymentMethodShortLabel(): String = when (paymentMethod) {
        PaymentMethod.CREDIT_CARD -> "Crédito"
        PaymentMethod.DEBIT -> "Débito"
        PaymentMethod.PIX -> "Pix"
        PaymentMethod.BILL -> "Boleto"
    }

    fun paymentMethodChatLabel(resolvedCardName: String? = cardName): String = when (paymentMethod) {
        PaymentMethod.CREDIT_CARD -> {
            val cardLabel = resolvedCardName?.takeIf { it.isNotBlank() }
            if (cardLabel != null) "Crédito (Cartão $cardLabel)" else "Crédito"
        }
        PaymentMethod.DEBIT -> "Débito"
        PaymentMethod.PIX -> "Pix"
        PaymentMethod.BILL -> "Boleto"
    }
}
