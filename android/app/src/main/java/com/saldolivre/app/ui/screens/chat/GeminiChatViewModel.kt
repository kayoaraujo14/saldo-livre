package com.saldolivre.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saldolivre.app.BuildConfig
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val inputText: String = "",
    val apiKey: String = BuildConfig.GEMINI_API_KEY
)

class GeminiChatViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val gson = Gson()

    private val _uiState = MutableStateFlow(
        ChatUiState(
            apiKey = BuildConfig.GEMINI_API_KEY,
            messages = listOf(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Olá! Sou o seu **Copiloto Financeiro Saldo Livre** 🤖✨.\n\nEstou conectado em tempo real ao seu Firestore e Gemini AI!\n\nVocê pode me pedir para:\n• Registrar gastos (ex: *\"Gastei 50 no almoço no cartão Nubank\"*)\n• Consultar seu Saldo Livre e Limite Diário Seguro\n• Saber quanto tem a receber de terceiros\n\nComo posso te ajudar agora?"
                )
            )
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onApiKeyChange(key: String) {
        _uiState.update { it.copy(apiKey = key) }
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val query = _uiState.value.inputText.trim()
        if (query.isBlank()) return

        val userMsg = ChatMessage(sender = MessageSender.USER, text = query)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                isTyping = true
            )
        }

        viewModelScope.launch {
            val responseText = executeGeminiOrFallback(query)
            val assistantMsg = ChatMessage(sender = MessageSender.ASSISTANT, text = responseText)
            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMsg,
                    isTyping = false
                )
            }
        }
    }

    private suspend fun executeGeminiOrFallback(query: String): String {
        val user = repository.currentUser.value
        val summary = repository.getMonthSummary().first()
        val cards = repository.getCards().first()

        val activeApiKey = _uiState.value.apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }

        val cardsInfo = if (cards.isEmpty()) "Nenhum cartão cadastrado"
        else cards.joinToString("; ") { "${it.cardName} (ID: ${it.id})" }

        val systemPromptText = """
            Você é o Copiloto IA do Saldo Livre, assistente de finanças pessoais e de casal.
            
            Contexto do Usuário:
            - Nome: ${user.displayName}
            - Saldo Livre Real do Mês: ${FinanceEngine.formatCurrency(summary.freeBalance)}
            - Limite Diário Seguro: ${if (summary.dailyLimit != null) FinanceEngine.formatCurrency(summary.dailyLimit) else "N/A"}/dia (${summary.daysRemaining} dias restantes no mês)
            - Receitas: ${FinanceEngine.formatCurrency(summary.income)}
            - Comprometido: ${FinanceEngine.formatCurrency(summary.committed)}
            - Já Pago: ${FinanceEngine.formatCurrency(summary.paid)}
            - A Receber Total: ${FinanceEngine.formatCurrency(summary.receivableTotal)}
            - Cartões Disponíveis: $cardsInfo

            Instruções de Resposta:
            1. Seja claro, direto, empático e amigável.
            2. Ao responder sobre saldos e metas, utilize os dados do contexto.
            3. REGISTRO DE DESPESAS: Se o usuário pedir para registrar, adicionar ou disser que gastou/comprou algo (ex: "Gastei 50 no almoço no cartão Nubank"), você DEVE obrigatoriamente incluir no FINAL da sua resposta um bloco JSON delimitado por ```json ... ``` com a estrutura exata:
            ```json
            {
              "action": "CREATE_TRANSACTION",
              "description": "<descrição limpa>",
              "amount": 50.0,
              "category": "<Alimentação|Transporte|Moradia|Saúde|Lazer|Assinaturas|Compras|Educação|Pets|Outros>",
              "cardName": "<nome do cartão se citado, ex: Nubank>",
              "paymentMethod": "<credit_card|pix|debit|bill>"
            }
            ```
        """.trimIndent()

        if (activeApiKey.isNotBlank()) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = activeApiKey,
                    systemInstruction = content { text(systemPromptText) }
                )

                val response = generativeModel.generateContent(query)
                val rawResponse = response.text ?: ""

                if (rawResponse.isNotBlank()) {
                    val processedMessage = processGeminiResponseAndCreateTransaction(rawResponse, user, cards)
                    return processedMessage
                }
            } catch (_: Exception) {
                // Se houver falha na API do Gemini (ex: sem internet ou chave inválida), utiliza o motor de fallback
            }
        }

        // Motor de regras locais inteligentes
        return processLocalFallback(query, user, summary, cards)
    }

    private suspend fun processGeminiResponseAndCreateTransaction(
        rawResponse: String,
        user: UserProfile,
        cards: List<Card>
    ): String {
        val jsonRegex = Regex("""```json\s*(\{[\s\S]*?\})\s*```""")
        val match = jsonRegex.find(rawResponse)

        if (match != null) {
            val jsonString = match.groupValues[1]
            try {
                val jsonObj = gson.fromJson(jsonString, JsonObject::class.java)
                val action = jsonObj.get("action")?.asString ?: ""

                if (action == "CREATE_TRANSACTION") {
                    val desc = jsonObj.get("description")?.asString ?: "Gasto via Copiloto IA"
                    val amount = jsonObj.get("amount")?.asDouble ?: 0.0
                    val category = jsonObj.get("category")?.asString ?: "Outros"
                    val cardName = jsonObj.get("cardName")?.asString ?: ""
                    val payMethodStr = jsonObj.get("paymentMethod")?.asString ?: "pix"

                    val matchedCard = cards.find {
                        it.cardName.contains(cardName, ignoreCase = true) || cardName.contains(it.cardName, ignoreCase = true)
                    }

                    val paymentMethod = PaymentMethod.from(payMethodStr)
                    val isCard = matchedCard != null || paymentMethod == PaymentMethod.CREDIT_CARD

                    val tx = Transaction(
                        id = "tx_" + UUID.randomUUID().toString().take(8),
                        createdByUid = user.uid,
                        paidByUid = user.uid,
                        cardId = matchedCard?.id ?: if (isCard) cards.firstOrNull()?.id else null,
                        description = desc,
                        category = category,
                        type = TransactionType.EXPENSE,
                        paymentMethod = if (isCard) PaymentMethod.CREDIT_CARD else paymentMethod,
                        status = TransactionStatus.PENDING,
                        actualAmount = amount,
                        totalAmount = amount,
                        dueDate = FinanceEngine.todayKey(),
                        competenceMonth = FinanceEngine.currentMonthKey(),
                        visibleToUids = listOf(user.uid),
                        userImpact = mapOf(user.uid to amount),
                        splitType = SplitType.MINE
                    )

                    repository.addTransaction(tx)

                    val cleanText = rawResponse.replace(match.value, "").trim()
                    val newSummary = repository.getMonthSummary().first()

                    return "$cleanText\n\n" +
                            "✅ **Lançamento gravado no Firestore!**\n" +
                            "• **Item**: $desc (${FinanceEngine.formatCurrency(amount)})\n" +
                            "• **Novo Saldo Livre**: ${FinanceEngine.formatCurrency(newSummary.freeBalance)}"
                }
            } catch (_: Exception) {
            }
        }

        return rawResponse.replace(Regex("""```json[\s\S]*?```"""), "").trim()
    }

    private suspend fun processLocalFallback(
        query: String,
        user: UserProfile,
        summary: MonthSummary,
        cards: List<Card>
    ): String {
        val lower = query.lowercase()

        // 1. Pergunta sobre Saldo Livre
        if (lower.contains("saldo livre") || lower.contains("quanto sobra") || lower.contains("saldo real")) {
            return "Seu **Saldo Livre Real** para o mês atual é de **${FinanceEngine.formatCurrency(summary.freeBalance)}**.\n\n" +
                    "• Receitas: ${FinanceEngine.formatCurrency(summary.income)}\n" +
                    "• Total Comprometido: ${FinanceEngine.formatCurrency(summary.committed)}\n" +
                    "• Já Pago: ${FinanceEngine.formatCurrency(summary.paid)}"
        }

        // 2. Pergunta sobre Limite Diário
        if (lower.contains("posso gastar") || lower.contains("por dia") || lower.contains("limite diário") || lower.contains("hoje")) {
            val limit = summary.dailyLimit
            return if (limit != null && limit > 0) {
                "Seu **Limite Diário Seguro** é de **${FinanceEngine.formatCurrency(limit)}/dia**.\n\nRestam **${summary.daysRemaining} dias** até o final deste mês de competência."
            } else {
                "Atenção: Seu saldo livre está zerado ou negativo no momento (**${FinanceEngine.formatCurrency(summary.freeBalance)}**). Recomendo segurar despesas não essenciais!"
            }
        }

        // 3. Pergunta sobre A Receber
        if (lower.contains("a receber") || lower.contains("me devem") || lower.contains("terceiros")) {
            return "Você tem **${FinanceEngine.formatCurrency(summary.receivableTotal)}** a receber no total este mês:\n\n" +
                    "• Parceiro(a): ${FinanceEngine.formatCurrency(summary.receivableFromPartner)}\n" +
                    "• Amigos da Rede: ${FinanceEngine.formatCurrency(summary.receivableFromFriends)}\n" +
                    "• Terceiros Externos: ${FinanceEngine.formatCurrency(summary.receivableFromExternal)}"
        }

        // 4. Intenção de registrar gasto
        val amountRegex = Regex("""(\d+([.,]\d{1,2})?)""")
        val match = amountRegex.find(lower)

        if ((lower.contains("gastei") || lower.contains("comprei") || lower.contains("adicione") || lower.contains("paguei")) && match != null) {
            val amountVal = match.value.replace(",", ".").toDoubleOrNull() ?: 0.0
            val desc = query
                .replace(Regex("(?i)(gastei|comprei|adicione|paguei|reais|r\\$|no|na|de|para|cartão|nubank|inter)"), "")
                .replace(match.value, "")
                .trim()
                .ifBlank { "Gasto rápido via IA" }
                .replaceFirstChar { it.uppercase() }

            val cat = when {
                lower.contains("almoço") || lower.contains("jantar") || lower.contains("mercado") || lower.contains("comida") -> "Alimentação"
                lower.contains("uber") || lower.contains("gasolina") || lower.contains("combustível") -> "Transporte"
                lower.contains("remédio") || lower.contains("farmácia") -> "Saúde"
                lower.contains("cinema") || lower.contains("show") || lower.contains("jogo") -> "Lazer"
                else -> "Outros"
            }

            val isCard = lower.contains("cartão") || lower.contains("nubank") || lower.contains("inter")
            val cardId = cards.firstOrNull { it.cardName.lowercase().contains("nubank") }?.id ?: cards.firstOrNull()?.id

            val tx = Transaction(
                id = "tx_" + UUID.randomUUID().toString().take(8),
                createdByUid = user.uid,
                paidByUid = user.uid,
                cardId = if (isCard) cardId else null,
                description = desc,
                category = cat,
                type = TransactionType.EXPENSE,
                paymentMethod = if (isCard) PaymentMethod.CREDIT_CARD else PaymentMethod.PIX,
                status = TransactionStatus.PENDING,
                actualAmount = amountVal,
                totalAmount = amountVal,
                dueDate = FinanceEngine.todayKey(),
                competenceMonth = FinanceEngine.currentMonthKey(),
                visibleToUids = listOf(user.uid),
                userImpact = mapOf(user.uid to amountVal),
                splitType = SplitType.MINE
            )
            repository.addTransaction(tx)

            val updatedSummary = repository.getMonthSummary().first()

            return "Pronto! Registrei a despesa diretamente no seu Firestore:\n\n" +
                    "• **Descrição**: $desc\n" +
                    "• **Valor**: ${FinanceEngine.formatCurrency(amountVal)}\n" +
                    "• **Categoria**: $cat\n" +
                    "• **Forma**: ${if (isCard) "Cartão de Crédito" else "Pix"}\n\n" +
                    "Seu **Novo Saldo Livre** é de **${FinanceEngine.formatCurrency(updatedSummary.freeBalance)}**."
        }

        return "Compreendi seu pedido. Como o seu assistente de finanças, posso calcular projeções, registrar gastos por voz/texto ou te dar dicas de economia. Tente perguntar: *\"Quanto posso gastar hoje?\"* ou *\"Gastei 55 reais na padaria\"*."
    }
}
