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

data class ActionCardData(
    val title: String,
    val description: String,
    val detail: String? = null,
    val newFreeBalance: String? = null,
    val isSuccess: Boolean = true,
    val pendingAction: PendingAction? = null
)

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
