package com.saldolivre.app.ui.screens.chat

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.GeminiExecutionResult
import com.saldolivre.app.data.ai.GeminiLoadBalancer
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
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
    val connectionStatus: String? = null,
    val isTestingConnection: Boolean = false
)

class GeminiChatViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val gson = Gson()
    private val appContext: Context? = runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val loadBalancer = GeminiLoadBalancer(appContext)
    private val neuralClassifier = NeuralFinanceClassifier(appContext)

    companion object {
        val AVAILABLE_MODELS = GeminiLoadBalancer.DEFAULT_MODELS_POOL
    }

    private val _uiState = MutableStateFlow(
        ChatUiState(
            apiKey = loadBalancer.getApiKeys().joinToString(", "),
            selectedModel = loadBalancer.getSelectedModel(),
            messages = listOf(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Olá! Sou o **Copiloto Saldo Livre** 🤖✨\n\n" +
                            "Estou conectado aos seus dados com **IA Generativa Avançada (Google Gemini 3 Flash & 2.5 Pro)** e **Rede Neural Heurística com Memória Viva**. Você pode conversar comigo para:\n" +
                            "• ⚡ **Lançamentos rápidos**: lance contas, faturas ou compras parceladas em linguagem natural (ex: *\"Gastei 65 no almoço no cartão\"* ou *\"Conta de luz veio R$ 245\"*)\n" +
                            "• 📊 **Gráficos e visualizações**: gere gráficos de pizza ou comparativos em barra para ver seus maiores gastos\n" +
                            "• 🧠 **Aprendizado contínuo**: ensine preferências financeiras (ex: *\"Aprenda que almoço no RU custa 12 e é Alimentação\"*)\n" +
                            "• 💡 **Consultor ativo**: consulte seu Saldo Livre real, Limite Diário Seguro e contas a pagar\n" +
                            "• 🤝 **Divisões inteligentes**: dividir despesas com parceiro(a), amigos ou terceiros (mãe, pai)\n\n" +
                            "Como posso ajudar você hoje?"
                )
            )
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onApiKeyChange(keyInput: String) {
        val trimmed = keyInput.trim()
        loadBalancer.saveApiKeys(trimmed)
        _uiState.update { it.copy(apiKey = trimmed, connectionStatus = null) }
    }

    fun onModelChange(model: String) {
        loadBalancer.setSelectedModel(model)
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

    fun testConnection(testKey: String, onResult: (Pair<Boolean, String>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            val outcome = loadBalancer.testConnection(testKey)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionStatus = outcome.second
                )
            }
            onResult(outcome)
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

        val keys = loadBalancer.getApiKeys()

        if (keys.isNotEmpty()) {
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

                val result: GeminiExecutionResult = loadBalancer.executeWithResilience(
                    userQuery = query,
                    systemInstructionText = systemPrompt,
                    onModelSwitched = { newModel ->
                        _uiState.update { it.copy(selectedModel = newModel) }
                    }
                )

                _uiState.update {
                    it.copy(connectionStatus = "Ativo • ${result.modelUsed} (${result.keyUsedMasked})")
                }

                if (result.responseText.isNotBlank()) {
                    return processGeminiResponseAndExecuteActions(result.responseText, user, cards, transactions)
                }
            } catch (e: Exception) {
                // Se o load balancer esgotar tentativas ou encontrar rate limits, utiliza o fallback local
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
        val cardsDesc = if (cards.isEmpty()) "nenhum"
        else cards.joinToString("; ") {
            "${it.cardName} (titular: ${it.ownerName}, fecha dia ${it.closingDay}, vence dia ${it.dueDay}, id: ${it.id})"
        }

        val friendsDesc = if (friendships.isEmpty()) "nenhum"
        else friendships.joinToString("; ") {
            val (friendUid, friendName) = it.otherUser(user.uid)
            val friendEmail = if (it.requesterUid == user.uid) it.recipientEmail else it.requesterEmail
            "$friendName (email: $friendEmail, uid: $friendUid)"
        }

        val recurringDesc = if (recurringBills.isEmpty()) "nenhuma conta recorrente ativa"
        else recurringBills.filter { it.active }.joinToString("; ") {
            "${it.description} (R$ ${it.defaultAmount}, vence dia ${it.dueDay}, categoria: ${it.category})"
        }

        val recentTxsDesc = if (transactions.isEmpty()) {
            "LANÇAMENTOS REGISTRADOS: Nenhum lançamento registrado no mês atual até o momento."
        } else {
            val sorted = transactions.sortedByDescending { it.dueDate }.take(25)
            "LANÇAMENTOS REAIS REGISTRADOS NESTE MÊS (${sorted.size} itens):\n" +
                    sorted.joinToString("\n") {
                        val subText = if (!it.subcategory.isNullOrBlank()) "${it.category} › ${it.subcategory}" else it.category
                        "• [ID: ${it.id}] ${it.dueDate} | ${it.description} | R$ ${FinanceEngine.round2(it.effectiveAmount)} | $subText | ${it.status.label} | ${it.paymentMethod.label}"
                    }
        }

        val byCategoryDesc = if (summary.byCategory.isNotEmpty()) {
            summary.byCategory.joinToString("\n  ") { "* ${it.category}: R$ ${FinanceEngine.round2(it.amount)}" }
        } else {
            "* Nenhuma despesa computada ainda"
        }

        val byInvestmentDesc = if (summary.byInvestmentSubcategory.isNotEmpty()) {
            "\n- Alocação de Investimentos por Subcategoria:\n  " +
                    summary.byInvestmentSubcategory.joinToString("\n  ") { "* ${it.subcategory}: R$ ${FinanceEngine.round2(it.amount)} (${it.percentage}%)" }
        } else ""

        val dailyLimitDesc = if (summary.dailyLimit != null && summary.dailyLimit > 0) {
            "R$ ${FinanceEngine.round2(summary.dailyLimit)}/dia (${summary.daysRemaining} dias restantes)"
        } else {
            "R$ 0,00 (Mês encerrado ou saldo insuficiente)"
        }

        val partner = repository.allUsers.find { it.uid != user.uid }
        val learnedRules = neuralClassifier.getLearnedRules()
        val learnedRulesDesc = if (learnedRules.isNotEmpty()) {
            learnedRules.mapIndexed { idx: Int, r: String -> "${idx + 1}. $r" }.joinToString("\n")
        } else {
            "Nenhuma regra personalizada memorizada ainda. Você pode aprender novas regras sempre que o usuário pedir (\"Aprenda que...\", \"Lembre-se que...\", \"Sempre que eu falar X coloque em Y\")."
        }

        return """
            Você é o "Copiloto Saldo Livre", um agente financeiro pessoal brasileiro, ultra-preciso, direto e acolhedor. Responda SEMPRE em português do Brasil.

            CONTEXTO ATUAL:
            - Usuário logado: ${user.displayName} (UID: ${user.uid}).
            - Parceiro(a) vinculado(a): ${partner?.displayName ?: "nenhum"}.
            - Hoje é: ${FinanceEngine.todayKey()}.
            - Mês em análise na tela: ${FinanceEngine.monthLabel(selectedMonth)} ($selectedMonth).
            - Cartões cadastrados: $cardsDesc.
            - Amigos na plataforma: $friendsDesc.
            - Contas recorrentes ativas: $recurringDesc.

            DADOS FINANCEIROS REAIS DO USUÁRIO EM ${FinanceEngine.monthLabel(selectedMonth).uppercase()} ($selectedMonth):
            - Saldo Livre Real: R$ ${FinanceEngine.round2(summary.freeBalance)}
            - Limite Diário Seguro: $dailyLimitDesc
            - Total de Receitas: R$ ${FinanceEngine.round2(summary.income)}
            - Total Comprometido: R$ ${FinanceEngine.round2(summary.committed)} (Já Pago: R$ ${FinanceEngine.round2(summary.paid)}, A Pagar/Pendente: R$ ${FinanceEngine.round2(summary.committed - summary.paid)})
            - Valores a Receber: R$ ${FinanceEngine.round2(summary.receivableTotal)} (Parceiro: R$ ${FinanceEngine.round2(summary.receivableFromPartner)}, Amigos: R$ ${FinanceEngine.round2(summary.receivableFromFriends)}, Terceiros: R$ ${FinanceEngine.round2(summary.receivableFromExternal)})
            - Gastos por Categoria neste mês:
              $byCategoryDesc
            - Total Investido no Mês: R$ ${FinanceEngine.round2(summary.totalInvested)}$byInvestmentDesc

            $recentTxsDesc

            REGRAS E PREFERÊNCIAS APRENDIDAS DO USUÁRIO (MEMÓRIA VIVA):
            $learnedRulesDesc

            ATENÇÃO: Sempre que uma despesa ou comando do usuário corresponder a uma dessas regras aprendidas, APLIQUE-A IMEDIATAMENTE sem hesitar!
            Se o usuário pedir para você aprender uma nova regra, memorizar um comportamento ou esquecer uma regra antiga, inclua no final o bloco JSON com "LEARN_RULE" ou "DELETE_RULE"!

            DIRETRIZES DE ASSERTIVIDADE, VERACIDADE E ZERO ALUCINAÇÃO (GUARDRAILS COMPORTAMENTAIS):
            - NUNCA invente, deduza ou presuma números, parâmetros faltantes, lançamentos, dívidas, faturas ou saldos que não constem nos dados reais informados acima.
            - SE FALTAR UM PARÂMETRO OBRIGATÓRIO (como valor, data de competência ou divisão), PERGUNTE AO USUÁRIO em vez de preencher valores prováveis ou inventados.
            - CITE OS IDs DE REFERÊNCIA, categorias e números de comprovantes (se houver) sempre que confirmar a preparação de um lançamento.
            - APLIQUE RIGOROSAMENTE a formatação da moeda corrente nas respostas visuais (ex: R$ 45,00 em vez de 45 ou $45).
            - NÃO faça cálculos matemáticos soltos por conta própria para dar saldos. Você deve sempre consultar os dados fornecidos no CONTEXTO ATUAL acima (soma determinística).
            - Seja extremamente DIRETO, OBJETIVO e SUCINTO. Elimine rodeios, introduções prolixas e sermões moralistas.

            REGRAS DE FUNCIONAMENTO:

            1. CARTÕES DE CRÉDITO, FATURA E MÊS DE COMPETÊNCIA:
            - Compras no cartão de crédito comprometem a renda do MÊS DE VENCIMENTO DA FATURA (competenceMonth), e NÃO a data em que a compra foi feita!
            - Se a compra foi feita após o dia de fechamento (closingDay) do cartão, a fatura fecha no ciclo seguinte e compromete o mês de vencimento (dueDay).

            2. AMIGOS NA REDE E DIVISÃO DE CONTAS:
            - Se o gasto for de um amigo na plataforma (cadastrado em Amigos):
              Preencha friendName e splitType = "FRIEND". O sistema aloca a cota correta e gera a cobrança com aceite mútuo.
            - Se for pessoa sem conta na plataforma (ex: "Mãe", "Pai", "Irmão"):
              Use splitType = "EXTERNAL".

            3. ESTORNOS E REEMBOLSOS (VALORES NEGATIVOS):
            - Se o gasto for estorno/crédito na fatura, use categoria = "Estorno" e valor negativo (ex: -15.00), aliviando o Saldo Livre.

            4. TAXONOMIA RICA E SUBGRUPOS ESPECÍFICOS:
            - Alimentação: Supermercado, Restaurante, Delivery & Lanches, Padaria & Café, Bares & Bebidas.
            - Moradia: Aluguel & Condomínio, Energia Elétrica, Água & Saneamento, Gás, Internet & TV a Cabo.
            - Transporte: Combustível, Aplicativos (Uber/99), Transporte Público, Estacionamento & Pedágio.
            - Saúde: Farmácia & Remédios, Consultas & Exames, Academia & Treinos.
            - Lazer: Cinema, Teatro & Shows, Viagens & Hospedagem.
            - Assinaturas: Streaming de Vídeo, Streaming de Música, Softwares, IA & Cloud.
            - Compras: Roupas & Acessórios, Eletrônicos & Informática, Casa & Decoração, Presentes.
            - Receita: Salário Principal, Pix Recebido, Rendimentos.
            - Estorno: Crédito na Fatura, Reembolso.

            5. COMPRAS PARCELADAS:
            - Notações como "2/6", "10/12" indicam compras parceladas. Preencha installments = { "current": 2, "total": 6 }. O sistema aloca todas as parcelas cronologicamente.

            6. EXECUÇÃO DE AÇÕES FINANCEIRAS:
            Quando o usuário solicitar o registro de um gasto/receita, atualização de conta, geração de gráfico ou memorização de regra, forneça sua resposta em texto natural e INCLUA NO FINAL UM BLOCO JSON delimitado por ```json ... ``` com uma das seguintes ações:

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

            b) Atualizar Conta Prevista:
            ```json
            {
              "action": "UPDATE_ESTIMATED",
              "transactionId": "<id da transação>",
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

            d) Gerar Gráfico de Análise Financeira:
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

            e) Aprender Nova Regra (Memória Viva):
            ```json
            {
              "action": "LEARN_RULE",
              "rule": "<regra aprendida>"
            }
            ```

            f) Esquecer Regra da Memória:
            ```json
            {
              "action": "DELETE_RULE",
              "rule": "<regra a esquecer>"
            }
            ```
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

                        // Parcelamento
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

                    "LEARN_RULE" -> {
                        val rule = jsonObj.get("rule")?.asString ?: ""
                        if (rule.isNotBlank()) {
                            neuralClassifier.saveLearnedRule(rule)
                            actionCard = ActionCardData(
                                title = "Regra Memorizada pela IA 🧠✨",
                                description = rule,
                                detail = "Salva na memória neural viva do app",
                                isSuccess = true
                            )
                        }
                    }

                    "DELETE_RULE" -> {
                        val rule = jsonObj.get("rule")?.asString ?: ""
                        if (rule.isNotBlank()) {
                            neuralClassifier.deleteLearnedRule(rule)
                            actionCard = ActionCardData(
                                title = "Regra Removida da Memória",
                                description = rule,
                                detail = "O copiloto não aplicará mais esta regra",
                                isSuccess = true
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Bloco ```chart ... ```
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

        // 6. Aprendizado de Regras e Memória Viva ("Aprenda que...", "Lembre-se que...", "Sempre que...")
        val isLearn = lower.startsWith("aprenda que") || lower.startsWith("lembre-se que") || lower.startsWith("memorize que") || lower.startsWith("sempre que")
        if (isLearn) {
            val ruleText = query.replace(Regex("^(aprenda que|lembre-se que|memorize que|grave que)", RegexOption.IGNORE_CASE), "").trim()
            if (ruleText.isNotBlank()) {
                neuralClassifier.saveLearnedRule(ruleText)
                return AgentResponse(
                    text = "Entendido! Memorizei essa regra na minha **Rede Neural / Memória Viva**:\n\n*\"$ruleText\"*\n\nPassarei a aplicar essa regra automaticamente em todos os seus lançamentos e análises financeiras.",
                    actionCard = ActionCardData(
                        title = "Regra Aprendida com Sucesso! 🧠✨",
                        description = ruleText,
                        detail = "Salva na memória neural viva do app",
                        isSuccess = true
                    )
                )
            }
        }

        // 7. Intenção de registrar gasto / receita / parcelamento via Rede Neural Heurística
        val extracted = neuralClassifier.parseNaturalIntent(query)
        if (extracted != null) {
            val amountVal = extracted.amount
            val desc = extracted.description
            val cat = extracted.category
            val subcat = extracted.subcategory
            val isCard = extracted.isCreditCard
            val hint = extracted.cardNameHint
            val card = if (!hint.isNullOrBlank()) {
                cards.find { it.cardName.contains(hint, ignoreCase = true) } ?: cards.firstOrNull()
            } else if (isCard) {
                cards.firstOrNull()
            } else null

            val today = FinanceEngine.todayKey()
            val (dueDate, compMonth) = if (isCard && card != null) {
                FinanceEngine.calculateCardInvoiceDates(today, card)
            } else {
                Pair(today, FinanceEngine.currentMonthKey())
            }

            val partner = repository.allUsers.find { it.uid != user.uid }
            val splitResult = FinanceEngine.buildSplit(
                splitType = extracted.splitType,
                total = amountVal,
                myUid = user.uid,
                myName = user.displayName,
                partnerUid = partner?.uid,
                partnerName = partner?.displayName,
                payerUid = user.uid
            )

            val totalInstallments = extracted.totalInstallments
            val currentInstallment = extracted.currentInstallment

            if (totalInstallments > 1) {
                val groupId = "inst_" + UUID.randomUUID().toString().take(8)
                val baseDesc = FinanceEngine.stripInstallmentSuffix(desc)
                val installmentList = mutableListOf<Transaction>()

                for (i in 1..totalInstallments) {
                    val monthOffset = i - currentInstallment
                    val targetMonth = FinanceEngine.shiftMonth(compMonth, monthOffset)
                    val targetDueDate = if (isCard && card != null) {
                        FinanceEngine.dateInMonth(targetMonth, card.dueDay)
                    } else {
                        FinanceEngine.dateInMonth(targetMonth, 10)
                    }

                    val status = if (monthOffset < 0) TransactionStatus.PAID else TransactionStatus.PENDING

                    installmentList.add(
                        Transaction(
                            id = "tx_" + UUID.randomUUID().toString().take(8),
                            createdByUid = user.uid,
                            paidByUid = user.uid,
                            cardId = card?.id,
                            description = FinanceEngine.formatInstallmentDesc(baseDesc, i, totalInstallments),
                            category = cat,
                            subcategory = subcat,
                            type = extracted.type,
                            paymentMethod = if (isCard) PaymentMethod.CREDIT_CARD else PaymentMethod.PIX,
                            status = status,
                            actualAmount = amountVal,
                            totalAmount = amountVal,
                            dueDate = targetDueDate,
                            competenceMonth = targetMonth,
                            visibleToUids = splitResult.visibleToUids,
                            userImpact = splitResult.userImpact,
                            allocations = splitResult.allocations,
                            splitType = extracted.splitType,
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
                    cardId = card?.id,
                    description = desc,
                    category = cat,
                    subcategory = subcat,
                    type = extracted.type,
                    paymentMethod = if (isCard) PaymentMethod.CREDIT_CARD else PaymentMethod.PIX,
                    status = if (isCard) TransactionStatus.PENDING else TransactionStatus.PAID,
                    actualAmount = amountVal,
                    totalAmount = amountVal,
                    dueDate = dueDate,
                    competenceMonth = compMonth,
                    visibleToUids = splitResult.visibleToUids,
                    userImpact = splitResult.userImpact,
                    allocations = splitResult.allocations,
                    splitType = extracted.splitType
                )
                repository.addTransaction(tx)
            }

            val updatedSummary = repository.getMonthSummary().first()

            val subDesc = if (!subcat.isNullOrBlank()) " › $subcat" else ""
            val actionCard = ActionCardData(
                title = if (totalInstallments > 1) "Parcelamento Registrado ($totalInstallments x)" else "Lançamento Registrado com Sucesso!",
                description = "$desc • ${FinanceEngine.formatCurrency(amountVal)}",
                detail = "Categoria: $cat$subDesc • ${if (isCard) "Cartão de Crédito (${card?.cardName ?: "Padrão"})" else "Pix"}",
                newFreeBalance = "Novo Saldo Livre: ${FinanceEngine.formatCurrency(updatedSummary.freeBalance)}",
                isSuccess = true
            )

            return AgentResponse(
                text = "Pronto! Registrei sua despesa com classificação neural:\n\n" +
                        "• **Descrição**: $desc\n" +
                        "• **Valor**: ${FinanceEngine.formatCurrency(amountVal)}\n" +
                        "• **Categoria**: $cat${if (!subcat.isNullOrBlank()) " › $subcat" else ""}\n" +
                        "• **Tipo**: ${if (extracted.type == TransactionType.INCOME) "Receita (Entrada)" else "Despesa (Saída)"}\n" +
                        "• **Forma de Pagamento**: ${if (isCard) "Cartão de Crédito (${card?.cardName ?: "Padrão"})" else "Pix"}\n" +
                        "• **Vencimento / Competência**: $dueDate ($compMonth)",
                actionCard = actionCard
            )
        }

        return AgentResponse(
            text = "Compreendi sua mensagem! Como seu copiloto financeiro com IA e Rede Neural, posso calcular projeções, registrar seus gastos por texto ou gerar gráficos do seu orçamento.\n\n" +
                    "Tente perguntar:\n" +
                    "• *\"Aprenda que almoço no RU custa 12 e é Alimentação\"*\n" +
                    "• *\"Gerar gráfico de despesas\"*\n" +
                    "• *\"Quanto posso gastar hoje?\"*\n" +
                    "• *\"Gastei 45 no almoço no cartão\"*\n" +
                    "• *\"Quais são as minhas contas a pagar?\"*"
        )
    }
}
