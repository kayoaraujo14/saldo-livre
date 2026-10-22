package com.saldolivre.app.data.model

import com.saldolivre.app.ui.screens.chat.ChartPayload
import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT
}

enum class PendingActionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}

data class PendingAction(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val title: String,
    val description: String,
    val detail: String? = null,
    val args: Map<String, Any?> = emptyMap(),
    var status: PendingActionStatus = PendingActionStatus.PENDING
)

data class ActionCardLine(
    val label: String = "",
    val value: String = "",
    val tone: String? = null
)

/**
 * Card de ação exibido no chat. Compatível com o formato da Web
 * (`{ kind, title, lines[], pendingAction?, pending_transactions? }`) e com o formato
 * legado do Android (`title`, `description`, `detail`, `newFreeBalance`).
 *
 * Todos os parâmetros têm valor padrão para que o Gson consiga usar o construtor vazio
 * e preserve os defaults ao desserializar sessões antigas salvas localmente.
 */
data class ActionCardData(
    val title: String = "",
    val description: String = "",
    val detail: String? = null,
    val newFreeBalance: String? = null,
    val isSuccess: Boolean = true,
    val pendingAction: PendingAction? = null,
    /** 'created' | 'updated' | 'report' | 'error' | 'confirmation' (mesmo enum da Web) */
    val kind: String = when {
        pendingAction != null -> "confirmation"
        !isSuccess -> "error"
        else -> "created"
    },
    /** Linhas estruturadas (label/value/tone) — idênticas às exibidas na Web. */
    val lines: List<ActionCardLine> = emptyList(),
    val pendingTransactions: List<Map<String, Any?>>? = null
) {
    /**
     * Linhas a exibir/sincronizar. Se o card veio da Web, usa `lines`; caso contrário,
     * deriva a partir dos campos legados do Android.
     */
    fun resolvedLines(): List<ActionCardLine> {
        // Gson pode deixar campos não-nulos como null ao ler JSON antigo; por isso a defesa extra.
        @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
        val explicit = (lines ?: emptyList()).filter { it != null && !it.value.isNullOrBlank() }
        if (explicit.isNotEmpty()) return explicit
        @Suppress("USELESS_ELVIS")
        val desc = description ?: ""
        return listOfNotNull(
            desc.takeIf { it.isNotBlank() }?.let { ActionCardLine("Resumo", it) },
            detail?.takeIf { it.isNotBlank() }?.let { ActionCardLine("Detalhes", it) },
            newFreeBalance?.takeIf { it.isNotBlank() }?.let { ActionCardLine("Saldo", it, "positive") }
        )
    }
}

data class ChatAttachment(
    val name: String,
    val size: Long = 0L,
    val type: String = "application/pdf",
    val base64: String? = null,
    val extractedText: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val actions: List<ActionCardData>? = null,
    val actionCard: ActionCardData? = null,
    val chartPayload: ChartPayload? = null,
    val attachment: ChatAttachment? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Todos os cards de ação da mensagem (paridade com `message.actions[]` da Web).
     * Mantém compatibilidade com mensagens antigas que só possuem `actionCard`.
     */
    fun allActionCards(): List<ActionCardData> {
        val list = actions?.filterNotNull().orEmpty()
        return if (list.isNotEmpty()) list else listOfNotNull(actionCard)
    }
}

data class ChatSession(
    val id: String = "sess_${System.currentTimeMillis()}",
    val title: String = "Nova Conversa",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val pendingTransactions: List<Map<String, Any?>> = emptyList()
)

data class UserLearnedRule(
    val id: String = "rule_${UUID.randomUUID().toString().take(8)}",
    val rule: String,
    val category: String = "Geral",
    val createdAt: Long = System.currentTimeMillis(),
    val active: Boolean = true
)
