package com.saldolivre.app.data.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saldolivre.app.data.model.ChatMessage
import com.saldolivre.app.data.model.ChatSession
import com.saldolivre.app.data.model.MessageSender

class ChatSessionManager(
    private val context: Context?,
    private val firestore: FirebaseFirestore = runCatching { FirebaseFirestore.getInstance() }.getOrElse { null } ?: FirebaseFirestore.getInstance()
) {
    companion object {
        private const val PREFS_SESSIONS = "saldo_livre_chat_sessions_prefs"
        private const val KEY_SESSIONS_PREFIX = "chat_sessions_"
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

    fun saveSession(uid: String, session: ChatSession) {
        val current = getLocalSessions(uid).toMutableList()
        val index = current.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            current[index] = session
        } else {
            current.add(0, session)
        }
        saveLocalSessions(uid, current)

        // Sincroniza com o Firestore /users/{uid}/chat_sessions/{sessionId}
        if (uid.isNotBlank() && uid != "guest") {
            try {
                val sanitizedSession = session.copy(
                    messages = session.messages.map { msg ->
                        // Remove base64 pesado para respeitar limites do documento Firestore (1MB)
                        if (msg.attachment?.base64 != null) {
                            msg.copy(attachment = msg.attachment.copy(base64 = null))
                        } else msg
                    }
                )
                val map = mapOf(
                    "id" to sanitizedSession.id,
                    "title" to sanitizedSession.title,
                    "createdAt" to sanitizedSession.createdAt,
                    "updatedAt" to sanitizedSession.updatedAt,
                    "messagesJson" to gson.toJson(sanitizedSession.messages)
                )
                firestore.collection("users").document(uid)
                    .collection("chat_sessions").document(session.id)
                    .set(map)
            } catch (e: Exception) {
                // Ignore sync errors gracefully
            }
        }
    }

    fun deleteSession(uid: String, sessionId: String) {
        val current = getLocalSessions(uid).filter { it.id != sessionId }
        saveLocalSessions(uid, current)

        if (uid.isNotBlank() && uid != "guest") {
            try {
                firestore.collection("users").document(uid)
                    .collection("chat_sessions").document(sessionId)
                    .delete()
            } catch (e: Exception) {
                // Ignore delete errors
            }
        }
    }

    fun subscribeCloudSessions(uid: String, onUpdate: (List<ChatSession>) -> Unit): ListenerRegistration? {
        if (uid.isBlank() || uid == "guest") return null
        return try {
            firestore.collection("users").document(uid)
                .collection("chat_sessions")
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val cloudList = mutableListOf<ChatSession>()
                        for (doc in snapshot.documents) {
                            val id = doc.getString("id") ?: doc.id
                            val title = doc.getString("title") ?: "Conversa"
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            val messagesJson = doc.getString("messagesJson")
                            val messages: List<ChatMessage> = try {
                                if (!messagesJson.isNullOrBlank()) {
                                    val type = object : TypeToken<List<ChatMessage>>() {}.type
                                    gson.fromJson(messagesJson, type) ?: emptyList()
                                } else emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                            cloudList.add(
                                ChatSession(
                                    id = id,
                                    title = title,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    messages = messages
                                )
                            )
                        }
                        if (cloudList.isNotEmpty()) {
                            saveLocalSessions(uid, cloudList)
                            onUpdate(cloudList)
                        }
                    }
                }
        } catch (e: Exception) {
            null
        }
    }
}
