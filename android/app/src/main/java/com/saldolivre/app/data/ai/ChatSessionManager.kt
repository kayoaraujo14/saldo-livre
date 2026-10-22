package com.saldolivre.app.data.ai

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.saldolivre.app.data.model.ActionCardData
import com.saldolivre.app.data.model.ChatAttachment
import com.saldolivre.app.data.model.ChatMessage
import com.saldolivre.app.data.model.ChatSession
import com.saldolivre.app.data.model.MessageSender
import com.saldolivre.app.data.model.PendingAction
import com.saldolivre.app.data.model.PendingActionStatus
import com.saldolivre.app.ui.screens.chat.ChartDataItem
import com.saldolivre.app.ui.screens.chat.ChartPayload
import com.saldolivre.app.ui.screens.chat.ChartType
import com.saldolivre.app.ui.theme.Emerald500
import java.util.UUID

class ChatSessionManager(
    private val context: Context?,
    private val firestore: FirebaseFirestore = runCatching { FirebaseFirestore.getInstance() }.getOrElse { null } ?: FirebaseFirestore.getInstance()
) {
    companion object {
        private const val PREFS_SESSIONS = "saldo_livre_chat_sessions_prefs"
        private const val KEY_SESSIONS_PREFIX = "chat_sessions_"
        private const val COLLECTION_USERS = "users"
        private const val SUBCOLLECTION_CHAT_SESSIONS = "chat_sessions"
        const val WELCOME_MESSAGE_ID = "msg_welcome"

        private val CHART_Colors_HEX = listOf(
            "#10B981", "#3B82F6", "#8B5CF6", "#F59E0B",
            "#EC4899", "#06B6D4", "#6366F1", "#14B8A6"
        )
    }

    private val gson = Gson()
    private val prefs = context?.getSharedPreferences(PREFS_SESSIONS, Context.MODE_PRIVATE)

    fun createInitialSession(): ChatSession {
        return ChatSession(
            id = "sess_${System.currentTimeMillis()}",
            title = "Nova Conversa",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = listOf(
                ChatMessage(
                    id = WELCOME_MESSAGE_ID,
                    sender = MessageSender.ASSISTANT,
                    text = "Olá! Sou o **Copiloto Saldo Livre** 🤖✨\n\n" +
                            "Estou conectado aos seus dados com **IA Generativa Avançada (Google Gemini)** e **Rede Neural Heurística**. Você pode conversar comigo para:\n" +
                            "• ⚡ **Lançamentos rápidos**: contas que chegaram ou compras parceladas (ex: *\"Gastei 65 no almoço no cartão\"* ou *\"Conta de luz veio R$ 245\"*)\n" +
                            "• 📄 **Extratos & Faturas em PDF**: anexe faturas ou extratos para importar em lote automaticamente\n" +
                            "• 📊 **Gráficos e visualizações**: gere gráficos de pizza ou comparativos em barra para ver seus maiores gastos\n" +
                            "• 🎙️ **Comandos de voz**: toque no microfone e fale naturalmente\n" +
                            "• 🧠 **Aprendizado contínuo**: ensine regras financeiras personalizadas\n" +
                            "• 💡 **Consultor ativo**: pergunte seu limite diário e previsões de gastos\n\n" +
                            "Como posso ajudar você hoje?"
                )
            )
        )
    }

    fun isBlankInitialSession(session: ChatSession): Boolean {
        val msgs = session.messages
        if (msgs.isEmpty()) return true
        if (msgs.size == 1 && msgs.first().sender == MessageSender.ASSISTANT &&
            (msgs.first().id == WELCOME_MESSAGE_ID || msgs.first().text.contains("Copiloto Saldo Livre"))
        ) {
            return true
        }
        return false
    }

    fun getLocalSessions(uid: String): List<ChatSession> {
        val key = "$KEY_SESSIONS_PREFIX${if (uid.isBlank()) "guest" else uid}"
        val raw = prefs?.getString(key, null)
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<ChatSession>>() {}.type
            gson.fromJson<List<ChatSession>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLocalSessions(uid: String, sessions: List<ChatSession>) {
        val key = "$KEY_SESSIONS_PREFIX${if (uid.isBlank()) "guest" else uid}"
        val json = gson.toJson(sessions)
        prefs?.edit()?.putString(key, json)?.apply()
    }

    fun saveSession(uid: String, session: ChatSession, syncToCloud: Boolean = true) {
        val current = getLocalSessions(uid).toMutableList()
        val index = current.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            current[index] = session
        } else {
            current.add(0, session)
        }
        saveLocalSessions(uid, current)

        // Só persiste no Firestore se a sessão tiver atividade real do usuário (alinhado com a Web)
        val hasUserActivity = !isBlankInitialSession(session)
        if (syncToCloud && hasUserActivity && uid.isNotBlank() && uid != "guest") {
            try {
                val serializedMessages = session.messages.map { msg ->
                    serializeMessageForFirestore(msg)
                }

                val payload = mutableMapOf<String, Any?>(
                    "id" to session.id,
                    "title" to session.title,
                    "createdAt" to session.createdAt,
                    "updatedAt" to session.updatedAt,
                    "ownerUid" to uid,
                    "messages" to serializedMessages
                )
                if (session.pendingTransactions.isNotEmpty()) {
                    payload["pending_transactions"] = session.pendingTransactions
                }

                firestore.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(SUBCOLLECTION_CHAT_SESSIONS)
                    .document(session.id)
                    .set(payload, SetOptions.merge())
            } catch (e: Exception) {
                // Ignore sync errors gracefully
            }
        }
    }

    private fun serializeMessageForFirestore(msg: ChatMessage): Map<String, Any?> {
        val role = if (msg.sender == MessageSender.USER) "user" else "assistant"

        // Se houver gráfico nativo no Android, inclui bloco ```chart no content para renderização na Web
        val contentForWeb = if (msg.chartPayload != null && !msg.text.contains("```chart")) {
            val chartMap = mapOf(
                "type" to if (msg.chartPayload.type == ChartType.BAR) "bar" else "donut",
                "title" to msg.chartPayload.title,
                "description" to (msg.chartPayload.description ?: ""),
                "data" to msg.chartPayload.data.mapIndexed { idx, item ->
                    mapOf(
                        "label" to item.label,
                        "value" to item.value,
                        "color" to CHART_Colors_HEX[idx % CHART_Colors_HEX.size]
                    )
                }
            )
            "${msg.text.trim()}\n\n```chart\n${gson.toJson(chartMap)}\n```"
        } else {
            msg.text
        }

        val map = mutableMapOf<String, Any?>(
            "id" to msg.id,
            "role" to role,
            "content" to contentForWeb,
            "sender" to msg.sender.name,
            "text" to msg.text,
            "timestamp" to msg.timestamp
        )

        if (msg.attachment != null) {
            // Remove base64 pesado para respeitar limites do documento Firestore (1MB), igual à Web
            val attMap = mutableMapOf<String, Any?>(
                "name" to msg.attachment.name,
                "size" to msg.attachment.size,
                "type" to msg.attachment.type
            )
            if (!msg.attachment.extractedText.isNullOrBlank()) {
                attMap["extractedText"] = msg.attachment.extractedText
            }
            map["attachment"] = attMap
        }

        if (msg.actionCard != null) {
            val lines = mutableListOf<Map<String, String>>()
            if (msg.actionCard.description.isNotBlank()) {
                lines.add(mapOf("label" to "Resumo", "value" to msg.actionCard.description))
            }
            if (!msg.actionCard.detail.isNullOrBlank()) {
                lines.add(mapOf("label" to "Detalhes", "value" to msg.actionCard.detail))
            }
            if (!msg.actionCard.newFreeBalance.isNullOrBlank()) {
                lines.add(mapOf("label" to "Saldo", "value" to msg.actionCard.newFreeBalance, "tone" to "positive"))
            }
            map["actions"] = listOf(
                mapOf(
                    "kind" to if (msg.actionCard.isSuccess) "created" else "error",
                    "title" to msg.actionCard.title,
                    "lines" to lines
                )
            )
            val pendingMap = msg.actionCard.pendingAction?.let { pa ->
                mapOf(
                    "id" to pa.id,
                    "toolName" to pa.toolName,
                    "title" to pa.title,
                    "description" to pa.description,
                    "detail" to pa.detail,
                    "args" to pa.args,
                    "status" to pa.status.name
                )
            }
            val cardMap = mutableMapOf<String, Any?>(
                "title" to msg.actionCard.title,
                "description" to msg.actionCard.description,
                "detail" to msg.actionCard.detail,
                "newFreeBalance" to msg.actionCard.newFreeBalance,
                "isSuccess" to msg.actionCard.isSuccess
            )
            if (pendingMap != null) {
                cardMap["pendingAction"] = pendingMap
            }
            map["actionCard"] = cardMap
        }

        return map
    }

    fun deleteSession(uid: String, sessionId: String) {
        val current = getLocalSessions(uid).filter { it.id != sessionId }
        saveLocalSessions(uid, current)

        if (uid.isNotBlank() && uid != "guest") {
            try {
                firestore.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(SUBCOLLECTION_CHAT_SESSIONS)
                    .document(sessionId)
                    .delete()
            } catch (e: Exception) {
                // Ignore delete errors
            }
        }
    }

    fun subscribeCloudSessions(uid: String, onUpdate: (List<ChatSession>) -> Unit): ListenerRegistration? {
        if (uid.isBlank() || uid == "guest") return null
        return try {
            firestore.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUBCOLLECTION_CHAT_SESSIONS)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val cloudList = mutableListOf<ChatSession>()
                        for (doc in snapshot.documents) {
                            val id = doc.getString("id") ?: doc.id
                            val title = doc.getString("title") ?: "Nova Conversa"
                            val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
                            val updatedAt = (doc.get("updatedAt") as? Number)?.toLong() ?: createdAt

                            val rawMessagesField = doc.get("messages")
                            val parsedFromArray = parseMessagesFromFirestoreField(rawMessagesField)

                            val messages: List<ChatMessage> = if (parsedFromArray.isNotEmpty()) {
                                parsedFromArray
                            } else {
                                // Fallback para sessões legadas que tinham apenas messagesJson
                                val messagesJson = doc.getString("messagesJson")
                                try {
                                    if (!messagesJson.isNullOrBlank()) {
                                        val type = object : TypeToken<List<ChatMessage>>() {}.type
                                        val legacy = gson.fromJson<List<ChatMessage>>(messagesJson, type) ?: emptyList()
                                        // Migra automaticamente o documento legado para o formato unificado (messages array)
                                        if (legacy.isNotEmpty()) {
                                            val migrated = legacy.map { serializeMessageForFirestore(it) }
                                            doc.reference.set(mapOf("messages" to migrated, "ownerUid" to uid), SetOptions.merge())
                                        }
                                        legacy
                                    } else emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }

                            @Suppress("UNCHECKED_CAST")
                            val pendingTxs = (doc.get("pending_transactions") as? List<Map<String, Any?>>) ?: emptyList()

                            if (messages.isNotEmpty()) {
                                cloudList.add(
                                    ChatSession(
                                        id = id,
                                        title = title,
                                        createdAt = createdAt,
                                        updatedAt = updatedAt,
                                        messages = messages,
                                        pendingTransactions = pendingTxs
                                    )
                                )
                            }
                        }

                        val sorted = cloudList.sortedByDescending { it.updatedAt }
                        if (sorted.isNotEmpty()) {
                            saveLocalSessions(uid, sorted)
                        }
                        onUpdate(sorted)
                    }
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMessagesFromFirestoreField(rawField: Any?): List<ChatMessage> {
        val rawList: List<*> = when (rawField) {
            is List<*> -> rawField
            is Map<*, *> -> rawField.values.toList()
            else -> return emptyList()
        }

        val result = mutableListOf<ChatMessage>()
        for (item in rawList) {
            val map = item as? Map<*, *> ?: continue
            val id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

            val roleStr = (map["role"] as? String) ?: (map["sender"] as? String) ?: "assistant"
            val sender = if (roleStr.equals("user", ignoreCase = true)) {
                MessageSender.USER
            } else {
                MessageSender.ASSISTANT
            }

            val rawContent = (map["content"] as? String) ?: (map["text"] as? String) ?: ""
            val (cleanText, extractedChart) = extractChartFromMarkdown(rawContent)

            val attachment = parseAttachment(map["attachment"])
            val actions = parseActionCardsList(map["actions"] ?: map["actionCard"])
            val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

            result.add(
                ChatMessage(
                    id = id,
                    sender = sender,
                    text = cleanText.ifBlank { rawContent },
                    actions = actions,
                    actionCard = actions?.firstOrNull(),
                    chartPayload = extractedChart,
                    attachment = attachment,
                    timestamp = timestamp
                )
            )
        }
        return result
    }

    private fun parseAttachment(rawAtt: Any?): ChatAttachment? {
        val map = rawAtt as? Map<*, *> ?: return null
        val name = (map["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val size = (map["size"] as? Number)?.toLong() ?: 0L
        val type = (map["type"] as? String) ?: "application/pdf"
        val extractedText = map["extractedText"] as? String
        return ChatAttachment(
            name = name,
            size = size,
            type = type,
            base64 = null,
            extractedText = extractedText
        )
    }

    private fun parseActionCard(rawActionCard: Any?, rawWebActions: Any?): ActionCardData? {
        // 1. Tenta ler formato direto Android (actionCard)
        val directMap = rawActionCard as? Map<*, *>
        if (directMap != null) {
            val title = (directMap["title"] as? String).orEmpty()
            val desc = (directMap["description"] as? String).orEmpty()
            if (title.isNotBlank() || desc.isNotBlank()) {
                val pendingMap = directMap["pendingAction"] as? Map<*, *>
                var pendingAction = if (pendingMap != null) {
                    val pId = (pendingMap["id"] as? String) ?: UUID.randomUUID().toString()
                    val toolName = (pendingMap["toolName"] as? String).orEmpty()
                    val pTitle = (pendingMap["title"] as? String).orEmpty()
                    val pDesc = (pendingMap["description"] as? String).orEmpty()
                    val pDetail = pendingMap["detail"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val pArgs = (pendingMap["args"] as? Map<String, Any?>) ?: emptyMap()
                    val statusStr = (pendingMap["status"] as? String).orEmpty()
                    val pStatus = try {
                        PendingActionStatus.valueOf(statusStr)
                    } catch (_: Exception) {
                        PendingActionStatus.PENDING
                    }
                    PendingAction(
                        id = pId,
                        toolName = if (toolName.isNotBlank()) toolName else "CREATE_TRANSACTION",
                        title = if (pTitle.isNotBlank()) pTitle else title,
                        description = if (pDesc.isNotBlank()) pDesc else desc,
                        detail = pDetail,
                        args = pArgs,
                        status = pStatus
                    )
                } else null

                // Fallback de resiliência: se não tinha pendingAction serializado mas é um card de confirmação pendente:
                if (pendingAction == null && title.contains("Confirmar", ignoreCase = true)) {
                    val parts = desc.split("—", "-")
                    val descPart = parts.firstOrNull()?.trim() ?: "Lançamento"
                    val amountPart = parts.getOrNull(1)?.trim() ?: ""
                    val parsedAmount = Regex("""[\d.,]+""").find(amountPart)?.value
                        ?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

                    val detailStr = (directMap["detail"] as? String).orEmpty()
                    val detailParts = detailStr.split("•").map { it.trim() }
                    val catSubcat = detailParts.firstOrNull()?.split("›")?.map { it.trim() }
                    val category = catSubcat?.firstOrNull()
                    val subcategory = catSubcat?.getOrNull(1)

                    if (parsedAmount > 0.0) {
                        pendingAction = PendingAction(
                            id = "act_" + (title + desc).hashCode().toString().take(8),
                            toolName = "CREATE_TRANSACTION",
                            title = title,
                            description = desc,
                            detail = detailStr.ifBlank { null },
                            args = mapOf(
                                "description" to descPart,
                                "amount" to parsedAmount,
                                "category" to category,
                                "subcategory" to subcategory,
                                "type" to "expense",
                                "confirmed" to true
                            ),
                            status = PendingActionStatus.PENDING
                        )
                    }
                }

                return ActionCardData(
                    title = title.ifBlank { "Ação Executada" },
                    description = desc,
                    detail = directMap["detail"] as? String,
                    newFreeBalance = directMap["newFreeBalance"] as? String,
                    isSuccess = (directMap["isSuccess"] as? Boolean) ?: true,
                    pendingAction = pendingAction
                )
            }
        }

        // 2. Converte formato da Web (actions: [{ kind, title, lines: [{ label, value }] }])
        val actionsList = rawWebActions as? List<*> ?: return null
        val firstAction = actionsList.firstOrNull() as? Map<*, *> ?: return null
        val title = (firstAction["title"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val kind = (firstAction["kind"] as? String).orEmpty()
        val linesList = firstAction["lines"] as? List<*> ?: emptyList<Any>()

        val parsedLines = linesList.mapNotNull { lineObj ->
            val lMap = lineObj as? Map<*, *> ?: return@mapNotNull null
            val label = (lMap["label"] as? String).orEmpty()
            val value = (lMap["value"] as? String).orEmpty()
            if (label.isNotBlank() && value.isNotBlank()) "$label: $value" else value.ifBlank { null }
        }

        val description = parsedLines.firstOrNull().orEmpty()
        val detail = if (parsedLines.size > 1) parsedLines.drop(1).joinToString(" • ") else null

        return ActionCardData(
            title = title,
            description = description.ifBlank { title },
            detail = detail,
            isSuccess = !kind.equals("error", ignoreCase = true)
        )
    }

    private fun extractChartFromMarkdown(content: String): Pair<String, ChartPayload?> {
        if (!content.contains("```chart")) return Pair(content, null)
        val regex = Regex("""```chart\s*(\{[\s\S]*?\})\s*```""")
        val match = regex.find(content) ?: return Pair(content, null)

        val cleanText = content.replace(match.value, "").trim()
        return try {
            val jsonObj = gson.fromJson(match.groupValues[1], JsonObject::class.java)
            val typeStr = jsonObj.get("type")?.asString ?: "donut"
            val title = jsonObj.get("title")?.asString ?: "Análise Financeira"
            val description = jsonObj.get("description")?.asString
            val dataArray = jsonObj.getAsJsonArray("data")

            val items = mutableListOf<ChartDataItem>()
            if (dataArray != null) {
                var idx = 0
                for (elem in dataArray) {
                    val obj = elem.asJsonObject
                    val label = obj.get("label")?.asString ?: "Outros"
                    val value = obj.get("value")?.asDouble ?: 0.0
                    val colorHex = obj.get("color")?.asString
                        ?: obj.get("colorHex")?.asString
                        ?: CHART_Colors_HEX[idx % CHART_Colors_HEX.size]
                    val color = try {
                        Color(android.graphics.Color.parseColor(colorHex))
                    } catch (e: Exception) {
                        Emerald500
                    }
                    items.add(ChartDataItem(label = label, value = value, color = color))
                    idx++
                }
            }

            if (items.isNotEmpty()) {
                val payload = ChartPayload(
                    type = if (typeStr.equals("bar", ignoreCase = true)) ChartType.BAR else ChartType.DONUT,
                    title = title,
                    description = description,
                    data = items
                )
                Pair(cleanText, payload)
            } else {
                Pair(content, null)
            }
        } catch (e: Exception) {
            Pair(content, null)
        }
    }
}
