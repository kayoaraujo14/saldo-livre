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
import com.saldolivre.app.data.model.ActionCardLine
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

        val cards = msg.allActionCards()
        if (cards.isNotEmpty()) {
            // Formato Web (message.actions[]): todos os cards, com kind/title/lines/pendingAction
            map["actions"] = cards.map { card -> serializeActionCardForWeb(card) }

            // Formato Android legado (actionCard): mantém o primeiro card com o pendingAction completo
            val first = cards.first()
            val cardMap = mutableMapOf<String, Any?>(
                "title" to first.title,
                "description" to first.description,
                "detail" to first.detail,
                "newFreeBalance" to first.newFreeBalance,
                "isSuccess" to first.isSuccess,
                "kind" to first.kind
            )
            first.pendingAction?.let { cardMap["pendingAction"] = serializePendingAction(it) }
            map["actionCard"] = cardMap

            val pendingTxs = cards.flatMap { it.pendingTransactions.orEmpty() }
            if (pendingTxs.isNotEmpty()) {
                map["pending_transactions"] = pendingTxs
            }
        }

        return map
    }

    private fun serializeActionCardForWeb(card: ActionCardData): Map<String, Any?> {
        val linesMaps = card.resolvedLines().map { line ->
            val l = mutableMapOf<String, Any?>("label" to line.label, "value" to line.value)
            if (!line.tone.isNullOrBlank()) l["tone"] = line.tone
            l
        }
        val webMap = mutableMapOf<String, Any?>(
            "kind" to card.kind.ifBlank { if (card.isSuccess) "created" else "error" },
            "title" to card.title,
            "lines" to linesMaps
        )
        card.pendingAction?.let { pa ->
            val pMap = serializePendingAction(pa).toMutableMap()
            pMap["lines"] = linesMaps
            webMap["pendingAction"] = pMap
        }
        if (!card.pendingTransactions.isNullOrEmpty()) {
            webMap["pending_transactions"] = card.pendingTransactions
        }
        return webMap
    }

    private fun serializePendingAction(pa: PendingAction): Map<String, Any?> = mapOf(
        "id" to pa.id,
        "toolName" to pa.toolName,
        "title" to pa.title,
        "description" to pa.description,
        "detail" to pa.detail,
        "args" to pa.args,
        // Web usa 'pending' | 'confirmed' | 'cancelled' em minúsculas
        "status" to pa.status.name.lowercase()
    )

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
            val actions = parseActionCardsList(map["actionCard"], map["actions"])
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

    /**
     * Converte os cards de ação vindos do Firestore em [ActionCardData].
     * - `actions` (formato Web): lista completa de cards `{ kind, title, lines[], pendingAction?, pending_transactions? }`.
     * - `actionCard` (formato Android): card único com `pendingAction` completo e executável.
     * Quando ambos existem (mensagens gravadas pelo Android), o `actionCard` substitui o primeiro card
     * da lista para preservar o pendingAction com os argumentos nativos.
     */
    private fun parseActionCardsList(rawActionCard: Any?, rawWebActions: Any?): List<ActionCardData>? {
        val webCards: List<ActionCardData> = when (rawWebActions) {
            is List<*> -> rawWebActions.mapNotNull { parseWebActionCard(it) }
            is Map<*, *> -> listOfNotNull(parseWebActionCard(rawWebActions))
            else -> emptyList()
        }
        val androidCard = parseAndroidActionCard(rawActionCard)

        val result = when {
            androidCard != null && webCards.isNotEmpty() -> {
                val firstWeb = webCards.first()
                val merged = androidCard.copy(
                    kind = if (androidCard.pendingAction != null) "confirmation" else firstWeb.kind,
                    lines = firstWeb.lines,
                    pendingTransactions = androidCard.pendingTransactions ?: firstWeb.pendingTransactions
                )
                listOf(merged) + webCards.drop(1)
            }
            androidCard != null -> listOf(androidCard)
            else -> webCards
        }
        return result.ifEmpty { null }
    }

    private fun parsePendingStatus(raw: Any?): PendingActionStatus {
        val s = (raw as? String).orEmpty()
        return PendingActionStatus.values().firstOrNull { it.name.equals(s, ignoreCase = true) }
            ?: PendingActionStatus.PENDING
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePendingTransactions(raw: Any?): List<Map<String, Any?>>? {
        val list = raw as? List<*> ?: return null
        val parsed = list.mapNotNull { (it as? Map<*, *>)?.let { m -> m.entries.associate { e -> e.key.toString() to e.value } } }
        return parsed.ifEmpty { null }
    }

    private fun parseLines(raw: Any?): List<ActionCardLine> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { lineObj ->
            val lMap = lineObj as? Map<*, *> ?: return@mapNotNull null
            val label = lMap["label"]?.toString().orEmpty()
            val value = lMap["value"]?.toString().orEmpty()
            if (value.isBlank() && label.isBlank()) null
            else ActionCardLine(label = label, value = value, tone = lMap["tone"] as? String)
        }
    }

    private fun parsePendingActionMap(
        pendingMap: Map<*, *>,
        fallbackTitle: String,
        fallbackDesc: String,
        fallbackDetail: String?
    ): PendingAction {
        val toolName = (pendingMap["toolName"] as? String).orEmpty()
        val pTitle = (pendingMap["title"] as? String).orEmpty()
        val pDesc = (pendingMap["description"] as? String).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val pArgs = (pendingMap["args"] as? Map<String, Any?>) ?: emptyMap()
        return PendingAction(
            id = (pendingMap["id"] as? String)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            toolName = toolName.ifBlank { "CREATE_TRANSACTION" },
            title = pTitle.ifBlank { fallbackTitle },
            description = pDesc.ifBlank { fallbackDesc },
            detail = (pendingMap["detail"] as? String) ?: fallbackDetail,
            args = pArgs,
            status = parsePendingStatus(pendingMap["status"])
        )
    }

    /** Formato Android direto (campo `actionCard`). */
    private fun parseAndroidActionCard(raw: Any?): ActionCardData? {
        val directMap = raw as? Map<*, *> ?: return null
        val title = (directMap["title"] as? String).orEmpty()
        val desc = (directMap["description"] as? String).orEmpty()
        if (title.isBlank() && desc.isBlank()) return null
        val detail = directMap["detail"] as? String

        var pendingAction = (directMap["pendingAction"] as? Map<*, *>)?.let {
            parsePendingActionMap(it, title, desc, detail)
        }

        // Fallback de resiliência: card de confirmação pendente sem pendingAction serializado
        if (pendingAction == null && title.contains("Confirmar", ignoreCase = true)) {
            val parts = desc.split("—", "-")
            val descPart = parts.firstOrNull()?.trim() ?: "Lançamento"
            val amountPart = parts.getOrNull(1)?.trim() ?: ""
            val parsedAmount = Regex("""[\d.,]+""").find(amountPart)?.value
                ?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

            val detailParts = detail.orEmpty().split("•").map { it.trim() }
            val catSubcat = detailParts.firstOrNull()?.split("›")?.map { it.trim() }

            if (parsedAmount > 0.0) {
                pendingAction = PendingAction(
                    id = "act_" + (title + desc).hashCode().toString().take(8),
                    toolName = "CREATE_TRANSACTION",
                    title = title,
                    description = desc,
                    detail = detail?.ifBlank { null },
                    args = mapOf(
                        "description" to descPart,
                        "amount" to parsedAmount,
                        "category" to catSubcat?.firstOrNull(),
                        "subcategory" to catSubcat?.getOrNull(1),
                        "type" to "expense",
                        "confirmed" to true
                    ),
                    status = PendingActionStatus.PENDING
                )
            }
        }

        val isSuccess = (directMap["isSuccess"] as? Boolean) ?: true
        return ActionCardData(
            title = title.ifBlank { "Ação Executada" },
            description = desc,
            detail = detail,
            newFreeBalance = directMap["newFreeBalance"] as? String,
            isSuccess = isSuccess,
            pendingAction = pendingAction,
            kind = (directMap["kind"] as? String)?.takeIf { it.isNotBlank() }
                ?: if (pendingAction != null) "confirmation" else if (isSuccess) "created" else "error",
            pendingTransactions = parsePendingTransactions(directMap["pending_transactions"])
        )
    }

    /** Formato Web (item de `actions[]`). */
    private fun parseWebActionCard(raw: Any?): ActionCardData? {
        val action = raw as? Map<*, *> ?: return null
        val title = (action["title"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val kind = (action["kind"] as? String)?.takeIf { it.isNotBlank() } ?: "report"
        val lines = parseLines(action["lines"])

        val description = lines.firstOrNull()?.let { if (it.label.isNotBlank()) "${it.label}: ${it.value}" else it.value }.orEmpty()
        val detail = if (lines.size > 1) {
            lines.drop(1).joinToString(" • ") { if (it.label.isNotBlank()) "${it.label}: ${it.value}" else it.value }
        } else null

        val pendingMap = action["pendingAction"] as? Map<*, *>
        val pendingAction = pendingMap?.let { parsePendingActionMap(it, title, description.ifBlank { title }, detail) }

        @Suppress("UNCHECKED_CAST")
        val pendingTxs = parsePendingTransactions(action["pending_transactions"])
            ?: parsePendingTransactions(pendingMap?.get("pending_transactions"))
            ?: parsePendingTransactions((pendingMap?.get("args") as? Map<String, Any?>)?.get("pending_transactions"))

        return ActionCardData(
            title = title,
            description = description.ifBlank { title },
            detail = detail,
            isSuccess = !kind.equals("error", ignoreCase = true),
            pendingAction = pendingAction,
            kind = kind,
            lines = lines,
            pendingTransactions = pendingTxs
        )
    }

    fun extractChartFromMarkdown(content: String): Pair<String, ChartPayload?> {
        if (!content.contains("```chart")) return Pair(content, null)
        val regex = Regex("""```chart\s*(\{[\s\S]*?\})\s*```""")
        val match = regex.find(content) ?: return Pair(content, null)

        val cleanText = content.replace(match.value, "").trim()
        return try {
            val jsonObj = gson.fromJson(match.groupValues[1], JsonObject::class.java)
            val typeStr = jsonObj.get("type")?.asString ?: jsonObj.get("chartType")?.asString ?: "donut"
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
