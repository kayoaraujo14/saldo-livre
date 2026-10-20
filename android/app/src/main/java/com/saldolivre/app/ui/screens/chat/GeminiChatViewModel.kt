package com.saldolivre.app.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saldolivre.app.BuildConfig
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ActionCardData(
    val title: String,
    val description: String,
    val detail: String? = null,
    val newFreeBalance: String? = null,
    val isSuccess: Boolean = true
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val actionCard: ActionCardData? = null,
    val chartPayload: ChartPayload? = null,
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
    val apiKey: String = "",
    val selectedModel: String = "gemini-1.5-flash",
    val connectionStatus: String? = null
)

class GeminiChatViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val gson = Gson()
    private val appContext: Context? = runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val prefs = appContext?.getSharedPreferences("saldo_livre_gemini_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KEY_API_KEY = "saldo_livre_gemini_api_key"
        private const val PREF_KEY_MODEL = "saldo_livre_gemini_model"
        val AVAILABLE_MODELS = listOf(
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.5-flash"
        )
    }

    private val _uiState = MutableStateFlow(
        ChatUiState(
            apiKey = loadSavedApiKey(),
            selectedModel = loadSavedModel(),
            messages = listOf(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Olá! Sou o **Copiloto Saldo Livre** 🤖✨\n\n" +
                            "Estou conectado aos seus dados financeiros em tempo real. Você pode conversar comigo para:\n" +
                            "• ⚡ **Lançamentos rápidos**: contas que chegaram ou compras parceladas (ex: *\"Gastei 65 no almoço no cartão\"* ou *\"Conta de luz veio R$ 245\"*)\n" +
                            "• 📊 **Consultor ativo**: consulte seu Saldo Livre real, Limite Diário Seguro e contas a pagar\n" +
                            "• 🤝 **Divisões inteligentes**: dividir despesas com parceiro(a) ou amigos da rede\n\n" +
                            "Como posso ajudar você hoje?"
                )
            )
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private fun loadSavedApiKey(): String {
        val saved = prefs?.getString(PREF_KEY_API_KEY, null)?.trim()
        return if (!saved.isNullOrBlank()) saved else BuildConfig.GEMINI_API_KEY
    }

    private fun loadSavedModel(): String {
        return prefs?.getString(PREF_KEY_MODEL, null) ?: "gemini-1.5-flash"
    }

    fun onApiKeyChange(key: String) {
        val trimmed = key.trim()
        prefs?.edit()?.putString(PREF_KEY_API_KEY, trimmed)?.apply()
        _uiState.update { it.copy(apiKey = trimmed, connectionStatus = null) }
    }

    fun onModelChange(model: String) {
        prefs?.edit()?.putString(PREF_KEY_MODEL, model)?.apply()
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearHistory() {
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = "Histórico reiniciado. Como posso te ajudar agora com suas finanças?"
                    )
                )
            )
        }
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
            val responseResult = executeGeminiOrFallback(query)
            val assistantMsg = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = responseResult.text,
                actionCard = responseResult.actionCard,
                chartPayload = responseResult.chartPayload
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMsg,
                    isTyping = false
                )
            }
        }
    }

    private data class AgentResponse(
        val text: String,
        val actionCard: ActionCardData? = null,
        val chartPayload: ChartPayload? = null
    )

    private suspend fun executeGeminiOrFallback(query: String): AgentResponse {
        val user = repository.currentUser.value
        val summary = repository.getMonthSummary().first()
        val cards = repository.getCards().first()
        val recurringBills = repository.getRecurringBills().first()
        val transactions = repository.getTransactions().first()
        val friendships = repository.getFriendships().first()
        val selectedMonth = repository.selectedMonth.value

        val activeApiKey = _uiState.value.apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }

        if (activeApiKey.isNotBlank()) {
            try {
                val systemPrompt = buildSystemInstruction(
                    user = user,
                    summary = summary,
                    cards = cards,
                    recurringBills = recurringBills,
                    transactions = transactions,
                    friendships = friendships,
                    selectedMonth = selectedMonth
                )

                val generativeModel = GenerativeModel(
                    modelName = _uiState.value.selectedModel,
                    apiKey = activeApiKey,
                    systemInstruction = content { text(systemPrompt) }
                )

                val response = generativeModel.generateContent(query)
                val rawResponse = response.text ?: ""

                if (rawResponse.isNotBlank()) {
                    return processGeminiResponseAndExecuteActions(rawResponse, user, cards, transactions)
                }
            } catch (e: Exception) {
                // Fallback automático caso haja erro na API (sem internet, quota excedida ou chave inválida)
            }
        }

        return processLocalFallback(query, user, summary, cards, recurringBills, transactions)
    }

    private fun buildSystemInstruction(
        user: UserProfile,
        summary: MonthSummary,
        cards: List<Card>,
        recurringBills: List<RecurringBill>,
        transactions: List<Transaction>,
        friendships: List<Friendship>,
        selectedMonth: String
    ): String {
        val cardsDesc = if (cards.isEmpty()) "Nenhum cartão cadastrado"
        else cards.joinToString("; ") {
            "${it.cardName} (Titular: ${it.ownerName}, Fecha dia ${it.closingDay}, Vence dia ${it.dueDay}, ID: ${it.id})"
        }

        val friendsDesc = if (friendships.isEmpty()) "Nenhum amigo cadastrado"
        else friendships.joinToString("; ") {
            val (friendUid, friendName) = it.otherUser(user.uid)
            val friendEmail = if (it.requesterUid == user.uid) it.recipientEmail else it.requesterEmail
            "$friendName ($friendEmail, UID: $friendUid)"
        }

        val recurringDesc = if (recurringBills.isEmpty()) "Nenhuma conta recorrente cadastrada"
        else recurringBills.filter { it.active }.joinToString("; ") {
            "${it.description} (R$ ${it.defaultAmount}, Vence dia ${it.dueDay}, Categoria: ${it.category})"
        }

        val recentTxs = transactions
            .sortedByDescending { it.dueDate }
            .take(20)
            .joinToString("\n") {
                "• [ID: ${it.id}] ${it.dueDate} | ${it.description} | R$ ${FinanceEngine.round2(it.effectiveAmount)} | ${it.category} | ${it.paymentMethod.label} | ${it.status.label}"
            }

        val dailyLimitDesc = if (summary.dailyLimit != null && summary.dailyLimit > 0)
            "R$ ${FinanceEngine.round2(summary.dailyLimit)}/dia (${summary.daysRemaining} dias restantes)"
        else "R$ 0,00 (Sem saldo diário disponível)"

        val partner = repository.allUsers.find { it.uid != user.uid }

        return """
            Você é o "Copiloto Saldo Livre", assistente e conselheiro financeiro nativo no Android.
            Responda SEMPRE em português do Brasil de forma acolhedora, objetiva, precisa e direta ao ponto.
            
            DADOS FINANCEIROS EM TEMPO REAL:
            - Usuário Logado: ${user.displayName} (UID: ${user.uid})
            - Parceiro(a) Vinculado(a): ${partner?.displayName ?: "Nenhum"}
            - Hoje é: ${FinanceEngine.todayKey()}
            - Mês de Competência em Análise: ${FinanceEngine.monthLabel(selectedMonth)} ($selectedMonth)
            - Saldo Livre Real Atual: R$ ${FinanceEngine.round2(summary.freeBalance)}
            - Limite Diário Seguro: $dailyLimitDesc
            - Total de Receitas: R$ ${FinanceEngine.round2(summary.income)}
            - Total Comprometido: R$ ${FinanceEngine.round2(summary.committed)} (Já Pago: R$ ${FinanceEngine.round2(summary.paid)}, A Pagar: R$ ${FinanceEngine.round2(summary.committed - summary.paid)})
            - Total a Receber: R$ ${FinanceEngine.round2(summary.receivableTotal)} (Parceiro: R$ ${FinanceEngine.round2(summary.receivableFromPartner)}, Amigos: R$ ${FinanceEngine.round2(summary.receivableFromFriends)}, Terceiros: R$ ${FinanceEngine.round2(summary.receivableFromExternal)})
            
            RECURSOS CADASTRADOS:
            - Cartões: $cardsDesc
            - Contas Recorrentes: $recurringDesc
            - Amigos na Rede: $friendsDesc
            
            ÚLTIMOS LANÇAMENTOS DO MÊS:
            $recentTxs
            
            DIRETRIZES DE RESPOSTA E AÇÕES FINANCEIRAS:
            1. Zero Alucinação: NUNCA invente lançamentos ou números que não estejam no contexto.
            2. Competência de Cartão: Compras no cartão de crédito afetam a competência do MÊS DE VENCIMENTO DA FATURA, e não a data da compra. Se a compra for após o dia de fechamento (closingDay), a fatura fecha no ciclo seguinte.
            3. EXECUÇÃO DE AÇÕES FINANCEIRAS:
               Quando o usuário solicitar o registro de um gasto/receita, a atualização de uma conta prevista ou exclusão, forneça sua resposta em texto natural e INCLUA NO FINAL UM BLOCO JSON delimitado por ```json ... ``` com uma das seguintes ações:
               
               a) Criar Lançamento:
               ```json
               {
                 "action": "CREATE_TRANSACTION",
                 "description": "<descrição>",
                 "amount": 50.0,
                 "type": "expense",
                 "category": "Alimentação",
                 "subcategory": "Restaurante",
                 "paymentMethod": "credit_card",
                 "cardName": "<nome do cartão se houver>",
                 "splitType": "MINE",
                 "friendName": null,
                 "installments": { "current": 1, "total": 1 }
               }
               ```
               (Categorias válidas: Alimentação, Moradia, Transporte, Saúde, Lazer, Assinaturas, Compras, Educação, Financiamentos & Empréstimos, Pets, Receita, Outros).
               (Métodos válidos: credit_card, pix, debit, bill).
               (Splits válidos: MINE, SPLIT_50, PARTNER, FRIEND, EXTERNAL).
               
               b) Atualizar Conta Prevista com Valor Real:
               ```json
               {
                 "action": "UPDATE_ESTIMATED",
                 "transactionId": "<id da transação de previsão>",
                 "actualAmount": 245.50
               }
               ```
               
               c) Excluir Lançamento:
               ```json
               {
                 "action": "DELETE_TRANSACTION",
                 "transactionId": "<id da transação>",
                 "description": "<nome do item>"
               }
               ```
               
               d) Gerar Gráfico de Análise Financeira (quando solicitado pelo usuário):
               ```json
               {
                 "action": "GENERATE_CHART",
                 "chartType": "donut",
                 "title": "Distribuição de Gastos",
                 "description": "Despesas por categoria",
                 "data": [
                   { "label": "Alimentação", "value": 450.0, "colorHex": "#10B981" },
                   { "label": "Transporte", "value": 180.0, "colorHex": "#3B82F6" }
                 ]
               }
               ```
               (chartType pode ser "donut" ou "bar").
        """.trimIndent()
    }

    private suspend fun processGeminiResponseAndExecuteActions(
        rawResponse: String,
        user: UserProfile,
        cards: List<Card>,
        transactions: List<Transaction>
    ): AgentResponse {
        val jsonRegex = Regex("""```json\s*(\{[\s\S]*?\})\s*```""")
        val match = jsonRegex.find(rawResponse)

        var actionCard: ActionCardData? = null
        var chartPayload: ChartPayload? = null
        var cleanText = if (match != null) {
            rawResponse.replace(match.value, "").trim()
        } else {
            rawResponse.trim()
        }

        if (match != null) {
            try {
                val jsonString = match.groupValues[1]
                val jsonObj = gson.fromJson(jsonString, JsonObject::class.java)
                val action = jsonObj.get("action")?.asString ?: ""

                when (action) {
                    "CREATE_TRANSACTION" -> {
                        val desc = jsonObj.get("description")?.asString ?: "Lançamento via Copiloto IA"
                        val amount = jsonObj.get("amount")?.asDouble ?: 0.0
                        val typeStr = jsonObj.get("type")?.asString ?: "expense"
                        val category = jsonObj.get("category")?.asString ?: "Outros"
                        val subcategory = jsonObj.get("subcategory")?.asString
                        val payMethodStr = jsonObj.get("paymentMethod")?.asString ?: "pix"
                        val cardName = jsonObj.get("cardName")?.asString ?: ""
                        val splitTypeStr = jsonObj.get("splitType")?.asString ?: "MINE"

                        val matchedCard = if (cardName.isNotBlank()) {
                            cards.find { it.cardName.contains(cardName, ignoreCase = true) || cardName.contains(it.cardName, ignoreCase = true) }
                        } else null

                        val isCreditCard = payMethodStr == "credit_card" || matchedCard != null
                        val effectiveCard = matchedCard ?: if (isCreditCard) cards.firstOrNull() else null
                        val paymentMethod = if (isCreditCard) PaymentMethod.CREDIT_CARD else PaymentMethod.from(payMethodStr)
                        val txType = if (typeStr.equals("income", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE

                        val today = FinanceEngine.todayKey()
                        val (dueDate, competenceMonth) = if (isCreditCard && effectiveCard != null) {
                            FinanceEngine.calculateCardInvoiceDates(today, effectiveCard)
                        } else {
                            Pair(today, FinanceEngine.currentMonthKey())
                        }

                        val splitType = when (splitTypeStr.uppercase()) {
                            "SPLIT_50", "CASAL_50_50" -> SplitType.SPLIT_50
                            "PARTNER", "PARCEIRO" -> SplitType.PARTNER
                            "FRIEND", "AMIGO" -> SplitType.FRIEND
                            "EXTERNAL", "TERCEIRO" -> SplitType.EXTERNAL
                            else -> SplitType.MINE
                        }

                        val partner = repository.allUsers.find { it.uid != user.uid }
                        val splitResult = FinanceEngine.buildSplit(
                            splitType = splitType,
                            total = amount,
                            myUid = user.uid,
                            myName = user.displayName,
                            partnerUid = partner?.uid,
                            partnerName = partner?.displayName,
                            payerUid = user.uid
                        )

                        // Verifica parcelamento
                        val installmentsObj = jsonObj.getAsJsonObject("installments")
                        val totalInstallments = installmentsObj?.get("total")?.asInt ?: 1
                        val currentInstallment = installmentsObj?.get("current")?.asInt ?: 1

                        if (totalInstallments > 1) {
                            val groupId = "inst_" + UUID.randomUUID().toString().take(8)
                            val baseDesc = FinanceEngine.stripInstallmentSuffix(desc)
                            val installmentList = mutableListOf<Transaction>()

                            for (i in 1..totalInstallments) {
                                val monthOffset = i - currentInstallment
                                val targetMonth = FinanceEngine.shiftMonth(competenceMonth, monthOffset)
                                val targetDueDate = if (isCreditCard && effectiveCard != null) {
                                    FinanceEngine.dateInMonth(targetMonth, effectiveCard.dueDay)
                                } else {
                                    FinanceEngine.dateInMonth(targetMonth, 10)
                                }

                                val status = if (monthOffset < 0) TransactionStatus.PAID else TransactionStatus.PENDING

                                installmentList.add(
                                    Transaction(
                                        id = "tx_" + UUID.randomUUID().toString().take(8),
                                        createdByUid = user.uid,
                                        paidByUid = user.uid,
                                        cardId = effectiveCard?.id,
                                        description = FinanceEngine.formatInstallmentDesc(baseDesc, i, totalInstallments),
                                        category = category,
                                        subcategory = subcategory,
                                        type = txType,
                                        paymentMethod = paymentMethod,
                                        status = status,
                                        actualAmount = amount,
                                        totalAmount = amount,
                                        dueDate = targetDueDate,
                                        competenceMonth = targetMonth,
                                        visibleToUids = splitResult.visibleToUids,
                                        userImpact = splitResult.userImpact,
                                        allocations = splitResult.allocations,
                                        splitType = splitType,
                                        installmentGroupId = groupId,
                                        installmentNumber = i,
                                        installmentsTotal = totalInstallments
                                    )
                                )
                            }
                            repository.addTransactions(installmentList)
                        } else {
                            val tx = Transaction(
                                id = "tx_" + UUID.randomUUID().toString().take(8),
                                createdByUid = user.uid,
                                paidByUid = user.uid,
                                cardId = effectiveCard?.id,
                                description = desc,
                                category = category,
                                subcategory = subcategory,
                                type = txType,
                                paymentMethod = paymentMethod,
                                status = if (isCreditCard) TransactionStatus.PENDING else TransactionStatus.PAID,
                                actualAmount = amount,
                                totalAmount = amount,
                                dueDate = dueDate,
                                competenceMonth = competenceMonth,
                                visibleToUids = splitResult.visibleToUids,
                                userImpact = splitResult.userImpact,
                                allocations = splitResult.allocations,
                                splitType = splitType
                            )
                            repository.addTransaction(tx)
                        }

                        val updatedSummary = repository.getMonthSummary().first()
                        actionCard = ActionCardData(
                            title = if (totalInstallments > 1) "Parcelamento Registrado ($totalInstallments x)" else "Lançamento Registrado com Sucesso!",
                            description = "$desc • ${FinanceEngine.formatCurrency(amount)}",
                            detail = "Categoria: $category • ${paymentMethod.label}${if (effectiveCard != null) " (${effectiveCard.cardName})" else ""}",
                            newFreeBalance = "Novo Saldo Livre: ${FinanceEngine.formatCurrency(updatedSummary.freeBalance)}",
                            isSuccess = true
                        )
                    }

                    "UPDATE_ESTIMATED" -> {
                        val txId = jsonObj.get("transactionId")?.asString ?: ""
                        val actualAmount = jsonObj.get("actualAmount")?.asDouble ?: 0.0

                        if (txId.isNotBlank()) {
                            repository.updateEstimatedWithActual(txId, actualAmount)
                            val updatedSummary = repository.getMonthSummary().first()
                            val targetTx = transactions.find { it.id == txId }

                            actionCard = ActionCardData(
                                title = "Conta Prevista Atualizada!",
                                description = "${targetTx?.description ?: "Conta"} • ${FinanceEngine.formatCurrency(actualAmount)}",
                                detail = "Status alterado de Previsão para A Pagar",
                                newFreeBalance = "Novo Saldo Livre: ${FinanceEngine.formatCurrency(updatedSummary.freeBalance)}",
                                isSuccess = true
                            )
                        }
                    }

                    "DELETE_TRANSACTION" -> {
                        val txId = jsonObj.get("transactionId")?.asString ?: ""
                        val desc = jsonObj.get("description")?.asString ?: "Lançamento"

                        if (txId.isNotBlank()) {
                            repository.deleteTransaction(txId)
                            val updatedSummary = repository.getMonthSummary().first()

                            actionCard = ActionCardData(
                                title = "Lançamento Excluído",
                                description = desc,
                                detail = "Removido do controle financeiro",
                                newFreeBalance = "Novo Saldo Livre: ${FinanceEngine.formatCurrency(updatedSummary.freeBalance)}",
                                isSuccess = true
                            )
                        }
                    }

                    "GENERATE_CHART" -> {
                        val chartTypeStr = jsonObj.get("chartType")?.asString ?: "donut"
                        val title = jsonObj.get("title")?.asString ?: "Análise Financeira"
                        val description = jsonObj.get("description")?.asString
                        val totalLabel = jsonObj.get("totalLabel")?.asString
                        val dataArray = jsonObj.getAsJsonArray("data")

                        val itemsList = mutableListOf<ChartDataItem>()
                        if (dataArray != null) {
                            for (elem in dataArray) {
                                if (elem.isJsonObject) {
                                    val itemObj = elem.asJsonObject
                                    val label = itemObj.get("label")?.asString ?: "Item"
                                    val value = itemObj.get("value")?.asDouble ?: 0.0
                                    val colorHex = itemObj.get("colorHex")?.asString
                                    val sublabel = itemObj.get("sublabel")?.asString

                                    val catColor = CategoryRegistry.CATEGORIES.find { it.name.equals(label, ignoreCase = true) }?.color ?: Color(0xFF64748B)
                                    val effectiveColor = parseColorHex(colorHex, catColor)

                                    if (value > 0) {
                                        itemsList.add(ChartDataItem(label = label, value = value, color = effectiveColor, sublabel = sublabel))
                                    }
                                }
                            }
                        }

                        if (itemsList.isNotEmpty()) {
                            chartPayload = ChartPayload(
                                type = if (chartTypeStr.equals("bar", ignoreCase = true)) ChartType.BAR else ChartType.DONUT,
                                title = title,
                                description = description,
                                totalLabel = totalLabel,
                                data = itemsList
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Verifica se há bloco ```chart ... ```
        val chartBlockRegex = Regex("""```(?:chart|grafico)\s*(\{[\s\S]*?\})\s*```""")
        val chartBlockMatch = chartBlockRegex.find(rawResponse)
        if (chartBlockMatch != null) {
            cleanText = cleanText.replace(chartBlockMatch.value, "").trim()
            if (chartPayload == null) {
                try {
                    val chartJson = gson.fromJson(chartBlockMatch.groupValues[1], JsonObject::class.java)
                    val cType = chartJson.get("type")?.asString ?: "donut"
                    val cTitle = chartJson.get("title")?.asString ?: "Gráfico Financeiro"
                    val cDesc = chartJson.get("description")?.asString
                    val cTotalLabel = chartJson.get("totalLabel")?.asString
                    val cData = chartJson.getAsJsonArray("data")
                    val cItems = mutableListOf<ChartDataItem>()
                    if (cData != null) {
                        for (elem in cData) {
                            if (elem.isJsonObject) {
                                val o = elem.asJsonObject
                                val l = o.get("label")?.asString ?: "Item"
                                val v = o.get("value")?.asDouble ?: 0.0
                                val colorHex = o.get("color")?.asString ?: o.get("colorHex")?.asString
                                val catColor = CategoryRegistry.CATEGORIES.find { it.name.equals(l, ignoreCase = true) }?.color ?: Color(0xFF64748B)
                                if (v > 0) {
                                    cItems.add(ChartDataItem(label = l, value = v, color = parseColorHex(colorHex, catColor)))
                                }
                            }
                        }
                    }
                    if (cItems.isNotEmpty()) {
                        chartPayload = ChartPayload(
                            type = if (cType.equals("bar", ignoreCase = true)) ChartType.BAR else ChartType.DONUT,
                            title = cTitle,
                            description = cDesc,
                            totalLabel = cTotalLabel,
                            data = cItems
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        return AgentResponse(text = cleanText, actionCard = actionCard, chartPayload = chartPayload)
    }

    private fun parseColorHex(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return try {
            val clean = hex.removePrefix("#")
            val colorInt = clean.toLong(16)
            if (clean.length == 6) Color(0xFF000000 or colorInt)
            else Color(colorInt)
        } catch (_: Exception) {
            fallback
        }
    }

    private suspend fun processLocalFallback(
        query: String,
        user: UserProfile,
        summary: MonthSummary,
        cards: List<Card>,
        recurringBills: List<RecurringBill>,
        transactions: List<Transaction>
    ): AgentResponse {
        val lower = query.lowercase()

        // 0. Geração de Gráficos Financeiros
        if (lower.contains("gráfico") || lower.contains("grafico") || lower.contains("distribuição") || lower.contains("distribuicao") || lower.contains("pizza") || lower.contains("barras") || lower.contains("categorias")) {
            val selectedMonth = repository.selectedMonth.value
            val monthTxs = transactions.filter {
                it.type == TransactionType.EXPENSE && (it.competenceMonth == selectedMonth || it.competenceMonth.isBlank())
            }

            val byCategory = monthTxs.groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.effectiveAmount } }
                .filter { it.value > 0 }
                .toList()
                .sortedByDescending { it.second }

            if (byCategory.isNotEmpty()) {
                val chartItems = byCategory.take(6).map { (catName, sumAmount) ->
                    val catDetail = CategoryRegistry.CATEGORIES.find { it.name.equals(catName, ignoreCase = true) }
                    ChartDataItem(
                        label = catName,
                        value = sumAmount,
                        color = catDetail?.color ?: Color(0xFF64748B)
                    )
                }
                val isBar = lower.contains("barra")
                val chartPayload = ChartPayload(
                    type = if (isBar) ChartType.BAR else ChartType.DONUT,
                    title = if (isBar) "Comparativo de Despesas" else "Distribuição por Categoria",
                    description = "Gastos em ${FinanceEngine.monthLabel(selectedMonth)}",
                    totalLabel = "Total de Gastos",
                    data = chartItems
                )
                return AgentResponse(
                    text = "Aqui está a análise visual dos seus gastos no mês de **${FinanceEngine.monthLabel(selectedMonth)}**:",
                    chartPayload = chartPayload
                )
            } else {
                return AgentResponse(
                    text = "Você ainda não possui despesas registradas no mês de **${FinanceEngine.monthLabel(selectedMonth)}** para gerar um gráfico."
                )
            }
        }

        // 1. Pergunta sobre Saldo Livre
        if (lower.contains("saldo livre") || lower.contains("quanto sobra") || lower.contains("saldo real")) {
            return AgentResponse(
                text = "Seu **Saldo Livre Real** para o mês atual é de **${FinanceEngine.formatCurrency(summary.freeBalance)}**.\n\n" +
                        "• **Receitas Totais**: ${FinanceEngine.formatCurrency(summary.income)}\n" +
                        "• **Comprometido no Mês**: ${FinanceEngine.formatCurrency(summary.committed)}\n" +
                        "• **Já Pago**: ${FinanceEngine.formatCurrency(summary.paid)}\n" +
                        "• **A Pagar / Pendente**: ${FinanceEngine.formatCurrency(summary.committed - summary.paid)}"
            )
        }

        // 2. Pergunta sobre Limite Diário Seguro
        if (lower.contains("posso gastar") || lower.contains("por dia") || lower.contains("limite diário") || lower.contains("gasto seguro") || lower.contains("hoje")) {
            val limit = summary.dailyLimit
            return if (limit != null && limit > 0) {
                AgentResponse(
                    text = "Seu **Limite Diário Seguro** é de **${FinanceEngine.formatCurrency(limit)}/dia**.\n\n" +
                            "Restam **${summary.daysRemaining} dias** até o fechamento deste mês de competência."
                )
            } else {
                AgentResponse(
                    text = "⚠️ **Atenção:** Seu saldo livre está zerado ou negativo no momento (**${FinanceEngine.formatCurrency(summary.freeBalance)}**). Recomendo segurar gastos não essenciais!"
                )
            }
        }

        // 3. Pergunta sobre Contas a Pagar / Pendências
        if (lower.contains("contas a pagar") || lower.contains("a pagar") || lower.contains("pendente") || lower.contains("fatura")) {
            val pendingTxs = transactions.filter { it.status == TransactionStatus.PENDING }
            val totalPending = pendingTxs.sumOf { it.effectiveAmount }

            val listText = if (pendingTxs.isEmpty()) "Você não possui lançamentos pendentes neste mês!"
            else pendingTxs.take(5).joinToString("\n") {
                "• ${it.dueDate}: **${it.description}** (${FinanceEngine.formatCurrency(it.effectiveAmount)})"
            }

            return AgentResponse(
                text = "Você tem **${FinanceEngine.formatCurrency(totalPending)}** em contas a pagar neste mês:\n\n$listText"
            )
        }

        // 4. Pergunta sobre A Receber
        if (lower.contains("a receber") || lower.contains("me devem") || lower.contains("amigos") || lower.contains("terceiros")) {
            return AgentResponse(
                text = "Você tem **${FinanceEngine.formatCurrency(summary.receivableTotal)}** a receber no total este mês:\n\n" +
                        "• **Parceiro(a)**: ${FinanceEngine.formatCurrency(summary.receivableFromPartner)}\n" +
                        "• **Amigos da Rede**: ${FinanceEngine.formatCurrency(summary.receivableFromFriends)}\n" +
                        "• **Terceiros Externos**: ${FinanceEngine.formatCurrency(summary.receivableFromExternal)}"
            )
        }

        // 5. Pergunta sobre Contas Recorrentes e Assinaturas
        if (lower.contains("recorrente") || lower.contains("assinatura") || lower.contains("contas fixas")) {
            val activeBills = recurringBills.filter { it.active }
            val listText = if (activeBills.isEmpty()) "Você não possui contas recorrentes ativas cadastradas."
            else activeBills.joinToString("\n") {
                "• **${it.description}**: ${FinanceEngine.formatCurrency(it.defaultAmount)} (Vence dia ${it.dueDay})"
            }

            return AgentResponse(
                text = "Suas **Contas Recorrentes Ativas**:\n\n$listText"
            )
        }

        // 5. Intenção de registrar gasto
        val amountRegex = Regex("""(\d+([.,]\d{1,2})?)""")
        val match = amountRegex.find(lower)

        if ((lower.contains("gastei") || lower.contains("comprei") || lower.contains("paguei") || lower.contains("lancei") || lower.contains("adicione")) && match != null) {
            val amountVal = match.value.replace(",", ".").toDoubleOrNull() ?: 0.0
            val desc = query
                .replace(Regex("(?i)(gastei|comprei|paguei|lancei|adicione|reais|r\\$|no|na|de|para|cartão|nubank|inter|itau)"), "")
                .replace(match.value, "")
                .trim()
                .ifBlank { "Gasto rápido via IA" }
                .replaceFirstChar { it.uppercase() }

            val cat = when {
                lower.contains("almoço") || lower.contains("jantar") || lower.contains("mercado") || lower.contains("comida") || lower.contains("padaria") -> "Alimentação"
                lower.contains("uber") || lower.contains("gasolina") || lower.contains("combustível") || lower.contains("ônibus") -> "Transporte"
                lower.contains("farmácia") || lower.contains("remédio") || lower.contains("médico") -> "Saúde"
                lower.contains("cinema") || lower.contains("lazer") || lower.contains("jogo") || lower.contains("show") -> "Lazer"
                lower.contains("luz") || lower.contains("energia") || lower.contains("aluguel") || lower.contains("condomínio") -> "Moradia"
                else -> "Outros"
            }

            val isCard = lower.contains("cartão") || lower.contains("nubank") || lower.contains("inter") || lower.contains("crédito")
            val card = cards.firstOrNull { it.cardName.contains("nubank", ignoreCase = true) } ?: cards.firstOrNull()

            val today = FinanceEngine.todayKey()
            val (dueDate, compMonth) = if (isCard && card != null) {
                FinanceEngine.calculateCardInvoiceDates(today, card)
            } else {
                Pair(today, FinanceEngine.currentMonthKey())
            }

            val tx = Transaction(
                id = "tx_" + UUID.randomUUID().toString().take(8),
                createdByUid = user.uid,
                paidByUid = user.uid,
                cardId = if (isCard) card?.id else null,
                description = desc,
                category = cat,
                type = TransactionType.EXPENSE,
                paymentMethod = if (isCard) PaymentMethod.CREDIT_CARD else PaymentMethod.PIX,
                status = if (isCard) TransactionStatus.PENDING else TransactionStatus.PAID,
                actualAmount = amountVal,
                totalAmount = amountVal,
                dueDate = dueDate,
                competenceMonth = compMonth,
                visibleToUids = listOf(user.uid),
                userImpact = mapOf(user.uid to amountVal),
                splitType = SplitType.MINE
            )
            repository.addTransaction(tx)

            val updatedSummary = repository.getMonthSummary().first()

            val actionCard = ActionCardData(
                title = "Lançamento Registrado com Sucesso!",
                description = "$desc • ${FinanceEngine.formatCurrency(amountVal)}",
                detail = "Categoria: $cat • ${if (isCard) "Cartão de Crédito" else "Pix"}",
                newFreeBalance = "Novo Saldo Livre: ${FinanceEngine.formatCurrency(updatedSummary.freeBalance)}",
                isSuccess = true
            )

            return AgentResponse(
                text = "Pronto! Registrei sua despesa diretamente no seu controle:\n\n" +
                        "• **Descrição**: $desc\n" +
                        "• **Valor**: ${FinanceEngine.formatCurrency(amountVal)}\n" +
                        "• **Categoria**: $cat\n" +
                        "• **Forma de Pagamento**: ${if (isCard) "Cartão de Crédito (${card?.cardName ?: "Padrão"})" else "Pix"}\n" +
                        "• **Vencimento / Competência**: $dueDate ($compMonth)",
                actionCard = actionCard
            )
        }

        return AgentResponse(
            text = "Compreendi sua mensagem! Como seu assistente financeiro, posso calcular projeções, registrar seus gastos por texto ou tirar dúvidas sobre o seu orçamento.\n\n" +
                    "Tente perguntar:\n" +
                    "• *\"Quanto posso gastar hoje?\"*\n" +
                    "• *\"Gastei 45 no almoço no cartão\"*\n" +
                    "• *\"Quais são as minhas contas a pagar?\"*"
        )
    }
}
