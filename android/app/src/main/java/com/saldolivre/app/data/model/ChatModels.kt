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
    val label: String,
    val value: String,
    val tone: String? = null
)

data class ActionCardData(
    val kind: String = "confirmation",
    val title: String,
    val description: String? = null,
    val detail: String? = null,
    val newFreeBalance: String? = null,
    val isSuccess: Boolean = true,
    val lines: List<ActionCardLine> = emptyList(),
    val pendingAction: PendingAction? = null,
    val pendingTransactions: List<Map<String, Any?>>? = null
) {
    constructor(
        title: String,
        description: String? = null,
        detail: String? = null,
        newFreeBalance: String? = null,
        isSuccess: Boolean = true,
        pendingAction: PendingAction? = null
    ) : this(
        kind = if (pendingAction != null) "confirmation" else "report",
        title = title,
        description = description,
        detail = detail,
        newFreeBalance = newFreeBalance,
        isSuccess = isSuccess,
        lines = listOfNotNull(
            description?.takeIf { it.isNotBlank() }?.let { ActionCardLine("Detalhe", it) },
            detail?.takeIf { it.isNotBlank() }?.let { ActionCardLine("Info", it) }
        ),
        pendingAction = pendingAction,
        pendingTransactions = null
    )
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
)

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
