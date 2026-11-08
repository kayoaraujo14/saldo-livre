package com.saldolivre.app.data.ai

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.ui.screens.chat.ChartDataItem
import com.saldolivre.app.ui.screens.chat.ChartPayload
import com.saldolivre.app.ui.screens.chat.ChartType
import com.saldolivre.app.ui.theme.Emerald500
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

data class ToolExecutionResult(
    val text: String,
    val actionCard: ActionCardData? = null,
    val actions: List<ActionCardData>? = actionCard?.let { listOf(it) },
    val chartPayload: ChartPayload? = null
)

/**
 * Motor completo de ferramentas inteligentes do Agente Financeiro para Android.
 * Executa operações completas de CRUD no banco de dados (Cloud Firestore / Repositório Local):
 * - CREATE: Registro individual e em lote com parcelamento contínuo, divisões inteligentes e cálculo de faturas.
 * - READ: Consultas filtradas com sumarização de valores e impacto no Saldo Livre.
 * - UPDATE: Edição precisa de descrições, valores, categorias, meses de fatura, vencimentos e contas previstas.
 * - DELETE: Exclusão cirúrgica por busca inteligente, valor, mês ou grupo completo de parcelas.
 * - GESTÃO: Cartões de crédito, contas recorrentes, regras aprendidas e gráficos.
 * - OFFLINE NLP: Processamento inteligente de linguagem natural com paridade total à versão Web.
 */
class AndroidAgentToolsEngine(
    private val repository: FinanceRepository,
    private val neuralClassifier: NeuralFinanceClassifier,
    private val rulesManager: LearnedRulesManager,
    private val context: Context?
) {
    private val gson = Gson()

    companion object {
        private val SYNONYMS = mapOf(
            "luz" to listOf("luz", "energia", "enel", "cemig", "light", "copel", "cpfl", "eletropaulo"),
            "agua" to listOf("agua", "água", "sabesp", "saneamento", "sanepar", "copasa"),
            "carro" to listOf("financiamento", "carro", "veiculo", "veículo", "moto", "parcela do carro"),
            "internet" to listOf("internet", "fibra", "wifi", "wi-fi", "claro", "vivo", "oi", "tim"),
            "aluguel" to listOf("aluguel", "condominio", "condomínio", "quintoandar"),
            "mercado" to listOf("mercado", "supermercado", "compras", "hortifruti", "atacadão", "assai", "carrefour", "pão de açúcar")
        )

        private val STOP_WORDS = setOf(
            "compra", "compras", "despesa", "despesas", "gasto", "gastos",
            "lancamento", "lancamentos", "lançamento", "lançamentos",
            "valor", "reais", "de", "do", "da", "no", "na", "em", "para",
            "com", "o", "a", "os", "as", "pago", "pix", "fatura", "cartao", "cartão"
        )
    }

    // -------------------------------------------------------------
    // AUXILIARES DE BUSCA & NORMALIZAÇÃO
    // -------------------------------------------------------------

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return Regex("""\p{M}""").replace(nfd, "").lowercase(Locale.ROOT).trim()
    }

    fun matchesSearch(targetText: String, searchTerm: String): Boolean {
        val targetNorm = normalize(targetText)
        val searchNorm = normalize(searchTerm)
        if (targetNorm.contains(searchNorm)) return true

        for ((key, list) in SYNONYMS) {
            val queryMatchesKey = searchNorm.contains(key) || list.any { searchNorm.contains(it) }
            val targetMatchesKey = targetNorm.contains(key) || list.any { targetNorm.contains(it) }
            if (queryMatchesKey && targetMatchesKey) return true
        }

        val words = searchNorm.split(Regex("""\s+""")).filter { it.length >= 3 && it !in STOP_WORDS }
        if (words.isNotEmpty()) {
            return words.all { targetNorm.contains(it) }
        }
        return false
    }

    fun shiftDateMonth(dateStr: String, delta: Int): String {
        return FinanceEngine.shiftDateMonth(dateStr, delta)
    }

    // -------------------------------------------------------------
    // EXECUÇÃO A PARTIR DE JSON (GEMINI OU RESPOSTA ESTRUTURADA)
    // -------------------------------------------------------------

    suspend fun executeFromJson(jsonObj: JsonObject): ToolExecutionResult = try {
        val action = jsonObj.getStringOrNull("action") ?: jsonObj.getStringOrNull("toolName") ?: ""
        when (action) {
            "CREATE_TRANSACTION", "registrarTransacao" -> executeCreateFromJson(jsonObj)
            "BATCH_TRANSACTIONS", "registrarTransacoesLote" -> executeBatchFromJson(jsonObj)
            "EDIT_TRANSACTION", "editarTransacoes", "EDIT_TRANSACTIONS", "BATCH_EDIT", "batchEdit", "editarTransacao" -> executeEditFromJson(jsonObj)
            "DELETE_TRANSACTION", "excluirTransacoes", "DELETE_TRANSACTIONS", "excluirTransacao" -> executeDeleteFromJson(jsonObj)
            "UPDATE_ESTIMATED", "atualizarContaPrevista" -> executeUpdateEstimatedFromJson(jsonObj)
            "SEARCH_TRANSACTIONS", "buscarTransacoes", "QUERY_TRANSACTIONS", "consultarTransacoes" -> executeSearchFromJson(jsonObj)
            "REPORT_PROJECTION", "PROJECT_MONTH", "obterRelatorioEProjecaoMensal" -> executeReportProjectionFromJson(jsonObj)
            "MANAGE_CARDS", "gerenciarCartoes" -> executeManageCardsFromJson(jsonObj)
            "MANAGE_RECURRING", "gerenciarContasRecorrentes" -> executeManageRecurringFromJson(jsonObj)
            "EXPAND_INSTALLMENTS", "expandirParcelamentos" -> executeExpandInstallmentsFromJson(jsonObj)
            "GENERATE_CHART", "gerarGraficoFinanceiro" -> executeGenerateChartFromJson(jsonObj)
            "MANAGE_BUDGETS", "gerenciarEnvelopesOrcamentarios" -> executeManageBudgetsFromJson(jsonObj)
            "HEALTH_DIAGNOSIS", "diagnosticoSaudeFinanceira" -> executeManageBudgets(acao = "diagnostico_saude_financeira")
            "LEARN_RULE", "gerenciarRegrasEMemoria" -> {
                val op = jsonObj.getStringOrNull("acao") ?: jsonObj.getStringOrNull("operation") ?: "aprender"
                val rule = jsonObj.getStringOrNull("rule") ?: jsonObj.getStringOrNull("regra") ?: ""
                val cleanRule = AgentSecuritySanitizer.sanitizeText(rule, 500)
                if (op.equals("esquecer", ignoreCase = true) || op.equals("delete", ignoreCase = true)) {
                    executeDeleteRule(cleanRule)
                } else {
                    executeLearnRule(cleanRule)
                }
            }
            "DELETE_RULE" -> {
                val rule = jsonObj.getStringOrNull("rule") ?: jsonObj.getStringOrNull("regra") ?: ""
                executeDeleteRule(AgentSecuritySanitizer.sanitizeText(rule, 500))
            }
            else -> ToolExecutionResult(text = "Ação desconhecida solicitada: $action")
        }
    } catch (e: Exception) {
        ToolExecutionResult(text = "⚠️ Não foi possível executar a ação com segurança: ${e.message ?: "parâmetros corrompidos ou inválidos"}")
    }

    // -------------------------------------------------------------
    // 1. CREATE TRANSACTION (INDIVIDUAL & PARCELADA)
    // -------------------------------------------------------------

    suspend fun executeCreateTransaction(
        description: String,
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        category: String? = null,
        subcategory: String? = null,
        paymentMethod: PaymentMethod = PaymentMethod.PIX,
        cardName: String? = null,
        splitType: SplitType = SplitType.MINE,
        friendName: String? = null,
        friendEmail: String? = null,
        friendSharePct: Double? = null,
        thirdPartyName: String? = null,
        currentInstallment: Int = 1,
        totalInstallments: Int = 1,
        customCompetenceMonth: String? = null,
        customDueDate: String? = null,
        confirmed: Boolean = false
    ): ToolExecutionResult {
        val isEstorno = type == TransactionType.EXPENSE && (
            category?.equals("Estorno", ignoreCase = true) == true ||
            description.startsWith("estorno", ignoreCase = true) ||
            description.contains("crédito na fatura", ignoreCase = true) ||
            description.contains("credito na fatura", ignoreCase = true)
        )

        val cleanDescription = AgentSecuritySanitizer.sanitizeText(description, 300)
        val validAmount = AgentSecuritySanitizer.sanitizeAmount(amount, allowNegative = true)
        if (validAmount == null || Math.abs(validAmount) <= 0.0) {
            return ToolExecutionResult(text = "⚠️ O valor da transação deve ser um número finito maior que zero.")
        }
        val absAmount = Math.abs(validAmount)
        val totalSigned = if (isEstorno) -absAmount else absAmount

        val user = repository.currentUser.value
        val cards = repository.getCards().first()

        val requestedCreditCard = paymentMethod == PaymentMethod.CREDIT_CARD || !cardName.isNullOrBlank()
        val matchedCard = if (!cardName.isNullOrBlank()) {
            val found = cards.find { it.cardName.contains(cardName, ignoreCase = true) || cardName.contains(it.cardName, ignoreCase = true) }
            if (found == null && cards.isNotEmpty()) {
                return ToolExecutionResult(text = "⚠️ Não encontrei o cartão \"$cardName\". Seus cartões cadastrados são: ${cards.joinToString { it.cardName }}. Em qual deles foi realizada a compra?")
            }
            found
        } else if (requestedCreditCard) {
            if (cards.size > 1) {
                return ToolExecutionResult(text = "⚠️ Você possui múltiplos cartões cadastrados (${cards.joinToString { it.cardName }}). Em qual deles foi realizada a compra de R$ ${FinanceEngine.round2(absAmount)} em $description?")
            }
            cards.firstOrNull()
        } else null

        val isCreditCard = requestedCreditCard || matchedCard != null
        val effectiveMethod = if (isCreditCard) PaymentMethod.CREDIT_CARD else paymentMethod

        val today = FinanceEngine.todayKey()
        val baseDate = customDueDate?.takeIf { it.isNotBlank() } ?: today
        val (calcDueDate, calcCompetenceMonth) = if (isCreditCard && matchedCard != null) {
            FinanceEngine.calculateCardInvoiceDates(baseDate, matchedCard)
        } else {
            val fallbackCompMonth = if (baseDate.length >= 7) baseDate.substring(0, 7) else repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
            Pair(baseDate, customCompetenceMonth?.takeIf { it.isNotBlank() } ?: fallbackCompMonth)
        }

        val finalCompetenceMonth = customCompetenceMonth?.takeIf { it.isNotBlank() } ?: calcCompetenceMonth
        // Para cartão de crédito, a data do lançamento é a data em que a compra foi realizada (baseDate)
        val finalDueDate = if (isCreditCard) baseDate else (customDueDate?.takeIf { it.isNotBlank() } ?: calcDueDate)

        // Categorização estrita e resolução canônica (paridade total com Web)
        val (finalCat, finalSubcat) = if (isEstorno && (category.isNullOrBlank() || category == "Outros")) {
            Pair("Estorno", "Crédito na Fatura")
        } else {
            CategoryRegistry.resolveStrictCategory(
                rawCategory = category,
                rawSubcategory = subcategory,
                type = type,
                description = description,
                guessFallback = { desc, txType ->
                    val guess = neuralClassifier.guessCategoryAndSubcategory(desc, absAmount)
                    Pair(guess.category, guess.subcategory)
                }
            )
        }

        val partner = FinanceEngine.resolvePartner(user.uid, cards, repository.allUsers)
        val friendships = repository.getFriendships().first()
        val matchedFriend = if (!friendEmail.isNullOrBlank()) {
            friendships.find {
                it.status == FriendshipStatus.ACCEPTED &&
                (it.recipientEmail.equals(friendEmail, true) || it.requesterEmail.equals(friendEmail, true))
            }
        } else if (!friendName.isNullOrBlank()) {
            friendships.find {
                it.status == FriendshipStatus.ACCEPTED &&
                (it.recipientName.contains(friendName, true) || it.requesterName.contains(friendName, true))
            }
        } else null

        val assignedFriendUid = matchedFriend?.let {
            if (it.requesterUid == user.uid) it.recipientUid else it.requesterUid
        }
        val assignedFriendName = matchedFriend?.let {
            if (it.requesterUid == user.uid) it.recipientName else it.requesterName
        } ?: friendName ?: friendEmail

        val effectiveSplitType = if (assignedFriendUid != null || !friendName.isNullOrBlank()) SplitType.FRIEND
            else if (!thirdPartyName.isNullOrBlank()) SplitType.EXTERNAL
            else splitType

        if (splitType == SplitType.FRIEND && assignedFriendUid == null && friendName.isNullOrBlank()) {
            return ToolExecutionResult(text = "⚠️ Com quem você gostaria de dividir essa despesa de R$ ${FinanceEngine.round2(absAmount)} e qual a porcentagem ou valor de cada um?")
        }
        if (splitType == SplitType.EXTERNAL && thirdPartyName.isNullOrBlank()) {
            return ToolExecutionResult(text = "⚠️ Qual o nome da pessoa com quem você dividiu essa despesa de R$ ${FinanceEngine.round2(absAmount)}?")
        }

        val friendPct = friendSharePct
            ?: if (effectiveSplitType == SplitType.FRIEND) {
                if (splitType == SplitType.SPLIT_50) 50.0 else 100.0
            } else null
        val acceptanceStatus = if (assignedFriendUid != null) "pending_approval" else null

        val payerUid = if (isCreditCard && matchedCard != null) {
            matchedCard.ownerUid.ifBlank { user.uid }
        } else {
            user.uid
        }

        val splitResult = FinanceEngine.buildSplit(
            splitType = effectiveSplitType,
            total = totalSigned,
            myUid = user.uid,
            myName = user.displayName,
            partnerUid = partner?.uid,
            partnerName = partner?.displayName,
            payerUid = payerUid,
            externalName = thirdPartyName,
            assignedFriendUid = assignedFriendUid,
            assignedFriendName = assignedFriendName,
            friendSharePct = friendPct
        )

        val cleanDesc = FinanceEngine.stripInstallmentSuffix(description)
        val isInstallment = totalInstallments > 1
        val cardText = if (matchedCard != null) " • Cartão ${matchedCard.cardName}" else ""
        val myImpact = splitResult.userImpact[user.uid] ?: totalSigned

        // SE AINDA NÃO CONFIRMADO PELO USUÁRIO: Gera rascunho com pedido de confirmação interativo
        if (!confirmed) {
            val before = repository.getMonthSummary().first()
            val netDiff = if (type == TransactionType.INCOME) absAmount else -myImpact
            val estimatedAfter = FinanceEngine.round2(before.freeBalance + netDiff)
            val actionTitle = if (isEstorno) "Estorno / Reembolso: $cleanDesc" else "${if (type == TransactionType.INCOME) "Receita" else "Despesa"}: $cleanDesc"

            val confirmMessage = if (isEstorno) {
                val dateDetail = if (isCreditCard) " para a fatura de **${FinanceEngine.monthLabel(finalCompetenceMonth)}**" else " para **${FinanceEngine.monthLabel(finalCompetenceMonth)}**"
                "Entendi que você deseja cadastrar um **estorno rateado**: **\"$cleanDesc\"** no valor de **${FinanceEngine.formatCurrency(absAmount)}**${if (matchedCard != null) " no cartão **${matchedCard.cardName}**" else ""}$dateDetail.\n\n" +
                        "• **Categoria**: $finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}\n" +
                        "• **Método**: ${effectiveMethod.label}\n" +
                        "• **Divisão**: ${splitType.label} (Seu alívio: +${FinanceEngine.formatCurrency(Math.abs(myImpact))})\n" +
                        "• **Alívio no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" +
                        "Por favor, confirme se os dados estão corretos para abater este estorno no Saldo Livre."
            } else if (isInstallment) {
                "Entendi que você deseja cadastrar uma despesa parcelada: **\"$cleanDesc\"** em **${totalInstallments}x de ${FinanceEngine.formatCurrency(amount)}** (total de ${FinanceEngine.formatCurrency(amount * totalInstallments)})${if (matchedCard != null) " no cartão **${matchedCard.cardName}**" else ""} a partir de **${FinanceEngine.monthLabel(finalCompetenceMonth)}**.\n\n" +
                        "• **Categoria**: $finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}\n" +
                        "• **Divisão**: ${splitType.label} (Seu impacto: ${FinanceEngine.formatCurrency(myImpact)}/mês)\n" +
                        "• **Impacto no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" +
                        "Por favor, confirme se os dados estão corretos para registrar no Saldo Livre."
            } else {
                val typeName = if (type == TransactionType.INCOME) "receita" else "despesa"
                val dateDetail = if (isCreditCard) " realizada em **${FinanceEngine.formatShortDate(finalDueDate)}** para a fatura de **${FinanceEngine.monthLabel(finalCompetenceMonth)}**" else " para **${FinanceEngine.monthLabel(finalCompetenceMonth)}** (data: ${FinanceEngine.formatShortDate(finalDueDate)})"
                "Entendi que você deseja cadastrar uma $typeName: **\"$cleanDesc\"** no valor de **${FinanceEngine.formatCurrency(amount)}**${if (matchedCard != null) " no cartão **${matchedCard.cardName}**" else ""}$dateDetail.\n\n" +
                        "• **Categoria**: $finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}\n" +
                        "• **Método**: ${effectiveMethod.label}\n" +
                        "• **Divisão**: ${splitType.label} (Seu impacto: ${FinanceEngine.formatCurrency(myImpact)})\n" +
                        "• **Impacto no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" +
                        "Por favor, confirme se os dados estão corretos para registrar no Saldo Livre."
            }

            val pendingAction = PendingAction(
                id = "act_" + UUID.randomUUID().toString().take(8),
                toolName = "CREATE_TRANSACTION",
                title = "Confirmar $actionTitle",
                description = if (isInstallment) {
                    "$cleanDesc — ${totalInstallments}x de ${FinanceEngine.formatCurrency(amount)}"
                } else {
                    "$cleanDesc — ${FinanceEngine.formatCurrency(amount)}"
                },
                detail = "$finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}$cardText • ${splitType.label}",
                args = mapOf(
                    "description" to cleanDesc,
                    "amount" to amount,
                    "type" to if (type == TransactionType.INCOME) "income" else "expense",
                    "category" to finalCat,
                    "subcategory" to finalSubcat,
                    "paymentMethod" to effectiveMethod.name,
                    "cardName" to matchedCard?.cardName,
                    "splitType" to splitType.name,
                    "friendName" to friendName,
                    "friendEmail" to friendEmail,
                    "friendSharePct" to friendPct,
                    "thirdPartyName" to thirdPartyName,
                    "currentInstallment" to currentInstallment,
                    "totalInstallments" to totalInstallments,
                    "competenceMonth" to finalCompetenceMonth,
                    "dueDate" to finalDueDate,
                    "confirmed" to true
                ),
                status = PendingActionStatus.PENDING
            )

            return ToolExecutionResult(
                text = confirmMessage,
                actionCard = ActionCardData(
                    title = "Confirmar $actionTitle",
                    description = if (isInstallment) {
                        "$cleanDesc — ${totalInstallments}x de ${FinanceEngine.formatCurrency(amount)}"
                    } else {
                        "$cleanDesc — ${FinanceEngine.formatCurrency(amount)}"
                    },
                    detail = "$finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}$cardText • ${splitType.label}",
                    newFreeBalance = "Saldo Livre previsto: ${FinanceEngine.formatCurrency(before.freeBalance)} → ${FinanceEngine.formatCurrency(estimatedAfter)}",
                    pendingAction = pendingAction
                )
            )
        }

        if (totalInstallments > 1) {
            val groupId = "inst_" + UUID.randomUUID().toString().take(8)
            val installmentList = mutableListOf<Transaction>()

            for (i in 1..totalInstallments) {
                val delta = i - currentInstallment
                val targetMonth = FinanceEngine.shiftMonth(finalCompetenceMonth, delta)
                val targetDueDate = shiftDateMonth(finalDueDate, delta)

                val isPast = i < currentInstallment
                val status = if (isPast) TransactionStatus.PAID else TransactionStatus.PENDING

                installmentList.add(
                    Transaction(
                        id = "tx_" + UUID.randomUUID().toString().take(8),
                        createdByUid = user.uid,
                        paidByUid = payerUid,
                        cardId = matchedCard?.id,
                        description = FinanceEngine.formatInstallmentDesc(cleanDesc, i, totalInstallments),
                        category = finalCat,
                        subcategory = finalSubcat,
                        type = type,
                        paymentMethod = effectiveMethod,
                        status = status,
                        actualAmount = amount,
                        totalAmount = amount,
                        dueDate = targetDueDate,
                        competenceMonth = targetMonth,
                        visibleToUids = splitResult.visibleToUids,
                        userImpact = splitResult.userImpact,
                        allocations = if (isPast) splitResult.allocations.map { it.copy(reimbursed = true) } else splitResult.allocations,
                        splitType = effectiveSplitType,
                        installmentGroupId = groupId,
                        installmentNumber = i,
                        installmentsTotal = totalInstallments,
                        assignedFriendUid = assignedFriendUid,
                        assignedFriendName = assignedFriendName,
                        acceptanceStatus = acceptanceStatus,
                        friendSharePct = friendPct,
                        externalName = thirdPartyName
                    )
                )
            }

            repository.addTransactions(installmentList)

            repository.logActivity(
                ActivityLog(
                    id = "act_" + UUID.randomUUID().toString().take(8),
                    actionType = ActivityActionType.TRANSACTION_CREATED,
                    title = "Compra Parcelada (${totalInstallments}x)",
                    description = "$cleanDesc — ${totalInstallments}x de ${FinanceEngine.formatCurrency(amount)}",
                    amount = amount * totalInstallments,
                    competenceMonth = finalCompetenceMonth,
                    userUid = user.uid,
                    userName = user.displayName
                )
            )

            val summaryNow = repository.getMonthSummary().first()

            return ToolExecutionResult(
                text = "✅ **Compra parcelada cadastrada com sucesso!**\n\n" +
                        "• **Item**: $cleanDesc\n" +
                        "• **Valor da parcela**: ${FinanceEngine.formatCurrency(amount)} (${totalInstallments} parcelas totalizando ${FinanceEngine.formatCurrency(amount * totalInstallments)})\n" +
                        "• **Mês de início da fatura**: ${FinanceEngine.monthLabel(finalCompetenceMonth)}$cardText\n" +
                        "• **Divisão**: ${splitType.label}\n\n" +
                        "As parcelas foram alocadas cronologicamente mês a mês no seu Saldo Livre.",
                actionCard = ActionCardData(
                    title = "Compra Parcelada Registrada (${totalInstallments}x)",
                    description = "$cleanDesc — $totalInstallments x ${FinanceEngine.formatCurrency(amount)}",
                    detail = "$finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}$cardText • ${splitType.label}",
                    newFreeBalance = "Saldo Livre em ${FinanceEngine.monthLabel(finalCompetenceMonth)}: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
                )
            )
        } else {
            val tx = Transaction(
                id = "tx_" + UUID.randomUUID().toString().take(8),
                createdByUid = user.uid,
                paidByUid = payerUid,
                cardId = matchedCard?.id,
                description = cleanDesc,
                category = finalCat,
                subcategory = finalSubcat,
                type = type,
                paymentMethod = effectiveMethod,
                status = if (type == TransactionType.INCOME || !isCreditCard) TransactionStatus.PAID else TransactionStatus.PENDING,
                actualAmount = totalSigned,
                totalAmount = totalSigned,
                dueDate = finalDueDate,
                competenceMonth = finalCompetenceMonth,
                visibleToUids = splitResult.visibleToUids,
                userImpact = splitResult.userImpact,
                allocations = splitResult.allocations,
                splitType = effectiveSplitType,
                assignedFriendUid = assignedFriendUid,
                assignedFriendName = assignedFriendName,
                acceptanceStatus = acceptanceStatus,
                friendSharePct = friendPct,
                externalName = thirdPartyName
            )

            repository.addTransaction(tx)

            val actTitle = if (isEstorno) "Estorno Rateado Registrado" else if (type == TransactionType.INCOME) "Receita Registrada" else "Despesa Registrada"
            val actDesc = if (isEstorno) "$cleanDesc — Estorno de ${FinanceEngine.formatCurrency(absAmount)}" else "$cleanDesc — ${FinanceEngine.formatCurrency(amount)}"

            repository.logActivity(
                ActivityLog(
                    id = "act_" + UUID.randomUUID().toString().take(8),
                    actionType = ActivityActionType.TRANSACTION_CREATED,
                    title = actTitle,
                    description = actDesc,
                    amount = totalSigned,
                    competenceMonth = finalCompetenceMonth,
                    userUid = user.uid,
                    userName = user.displayName
                )
            )

            val summaryNow = repository.getMonthSummary().first()

            val typeLabel = if (isEstorno) "Estorno" else if (type == TransactionType.INCOME) "Receita" else "Despesa"

            val dateLabel = if (isCreditCard) "Data da Compra" else "Vencimento"
            val splitDetail = when {
                isEstorno -> "(Seu alívio: +${FinanceEngine.formatCurrency(Math.abs(myImpact))})"
                type == TransactionType.INCOME -> "(Entrada: +${FinanceEngine.formatCurrency(absAmount)})"
                else -> "(Seu impacto: ${FinanceEngine.formatCurrency(myImpact)})"
            }

            return ToolExecutionResult(
                text = "✅ **$typeLabel ${if (isEstorno) "registrado" else "registrada"} com sucesso!**\n\n" +
                        "• **Descrição**: $cleanDesc\n" +
                        "• **Valor**: ${FinanceEngine.formatCurrency(absAmount)}\n" +
                        "• **Categoria**: $finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}\n" +
                        "• **Competência**: ${FinanceEngine.monthLabel(finalCompetenceMonth)} ($dateLabel: ${FinanceEngine.formatShortDate(finalDueDate)})$cardText\n" +
                        "• **Divisão**: ${splitType.label} $splitDetail\n\n" +
                        "Seu **Saldo Livre** foi atualizado em tempo real!",
                actionCard = ActionCardData(
                    title = actTitle,
                    description = "$cleanDesc — ${FinanceEngine.formatCurrency(absAmount)}",
                    detail = "$finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}$cardText • ${splitType.label}",
                    newFreeBalance = "Saldo Livre em ${FinanceEngine.monthLabel(finalCompetenceMonth)}: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
                )
            )
        }
    }

    private suspend fun executeCreateFromJson(jsonObj: JsonObject): ToolExecutionResult {
        var rawDesc = jsonObj.getStringOrNull("description") ?: jsonObj.getStringOrNull("descricao") ?: ""
        rawDesc = neuralClassifier.extractDescription(rawDesc).ifBlank { rawDesc }
        val desc = AgentSecuritySanitizer.sanitizeText(rawDesc, 300)

        val rawAmount = jsonObj.getDoubleOrNull("amount") ?: jsonObj.getDoubleOrNull("valor")
        val amount = AgentSecuritySanitizer.sanitizeAmount(rawAmount)
        if (amount == null || amount <= 0.0) {
            return ToolExecutionResult(text = "⚠️ Valor de transação inválido ou ausente. Por favor, informe um valor numérico válido maior que zero.")
        }
        val typeStr = jsonObj.getStringOrNull("type") ?: jsonObj.getStringOrNull("tipo") ?: "expense"
        val category = jsonObj.getStringOrNull("category") ?: jsonObj.getStringOrNull("categoria")
        val subcategory = jsonObj.getStringOrNull("subcategory") ?: jsonObj.getStringOrNull("subcategoria")
        val rawPayMethod = jsonObj.getStringOrNull("paymentMethod") ?: jsonObj.getStringOrNull("metodoPagamento")
        val cardName = jsonObj.getStringOrNull("cardName") ?: jsonObj.getStringOrNull("cartao")
        val splitTypeStr = jsonObj.getStringOrNull("splitType") ?: "MINE"
        val friendName = jsonObj.getStringOrNull("friendName")
        val friendEmail = jsonObj.getStringOrNull("friendEmail")
        val friendSharePct = jsonObj.getDoubleOrNull("friendSharePct")
            ?: jsonObj.getDoubleOrNull("friendPct")
            ?: jsonObj.getDoubleOrNull("percentualAmigo")
            ?: jsonObj.getDoubleOrNull("porcentagemAmigo")
            ?: jsonObj.getDoubleOrNull("pctAmigo")
            ?: jsonObj.getDoubleOrNull("pct")
        val thirdPartyName = jsonObj.getStringOrNull("thirdPartyName")
        val competenceMonth = jsonObj.getStringOrNull("competenceMonth")
        val dueDate = jsonObj.getStringOrNull("date")
            ?: jsonObj.getStringOrNull("data")
            ?: jsonObj.getStringOrNull("dataCompra")
            ?: jsonObj.getStringOrNull("dueDate")
        val confirmed = jsonObj.getBooleanOrNull("confirmed")
            ?: jsonObj.getBooleanOrNull("__confirmed")
            ?: false

        val installmentsObj = jsonObj.getAsJsonObjectOrNull("installments")
        val rawCurrent = installmentsObj?.getIntOrNull("current")
            ?: installmentsObj?.getIntOrNull("currentInstallment")
            ?: jsonObj.getIntOrNull("currentInstallment")
            ?: jsonObj.getIntOrNull("parcelaAtual")
            ?: jsonObj.getIntOrNull("parcela")
            ?: 1

        val rawTotal = installmentsObj?.getIntOrNull("total")
            ?: installmentsObj?.getIntOrNull("totalInstallments")
            ?: jsonObj.getIntOrNull("totalInstallments")
            ?: jsonObj.getIntOrNull("totalParcelas")
            ?: jsonObj.getIntOrNull("parcelas")
            ?: (jsonObj.get("installments")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asInt else null })
            ?: 1

        val detectedFromDesc = if (rawTotal <= 1) neuralClassifier.detectInstallments(desc) else null
        val currentInstallment = if (rawTotal > 1) rawCurrent else (detectedFromDesc?.first ?: rawCurrent)
        val totalInstallments = if (rawTotal > 1) rawTotal else (detectedFromDesc?.second ?: rawTotal)

        val explicitInstallmentAmt = jsonObj.getDoubleOrNull("valorParcela")
            ?: jsonObj.getDoubleOrNull("installmentAmount")
            ?: jsonObj.getDoubleOrNull("amountPerInstallment")

        val explicitTotalAmt = jsonObj.getDoubleOrNull("valorTotal")
            ?: jsonObj.getDoubleOrNull("totalAmount")

        val finalAmount = when {
            explicitInstallmentAmt != null && explicitInstallmentAmt > 0 -> explicitInstallmentAmt
            explicitTotalAmt != null && explicitTotalAmt > 0 && totalInstallments > 1 -> FinanceEngine.round2(explicitTotalAmt / totalInstallments)
            totalInstallments > 1 && (jsonObj.getBooleanOrNull("isTotalAmount") == true || jsonObj.getBooleanOrNull("ehValorTotal") == true) ->
                FinanceEngine.round2(amount / totalInstallments)
            else -> amount
        }

        val txType = when {
            typeStr.equals("income", ignoreCase = true) || typeStr.equals("receita", ignoreCase = true) || typeStr.equals("entrada", ignoreCase = true) -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        if (finalAmount <= 0.0) {
            return ToolExecutionResult(text = "⚠️ Qual foi o valor dessa transação?")
        }
        if (desc.isBlank() || desc.equals("Lançamento via Copiloto", ignoreCase = true) || desc.equals("Lançamento", ignoreCase = true) || desc.equals("Despesa", ignoreCase = true) || desc.equals("Gasto", ignoreCase = true)) {
            return ToolExecutionResult(text = "⚠️ Qual foi o estabelecimento ou a descrição dessa transação?")
        }

        val inferredPayMethod = when {
            !rawPayMethod.isNullOrBlank() -> PaymentMethod.from(rawPayMethod)
            Regex("""\bpix\b""", RegexOption.IGNORE_CASE).containsMatchIn(desc) -> PaymentMethod.PIX
            Regex("""\bd[eé]bito\b""", RegexOption.IGNORE_CASE).containsMatchIn(desc) -> PaymentMethod.DEBIT
            Regex("""\b(boleto|darf)\b""", RegexOption.IGNORE_CASE).containsMatchIn(desc) -> PaymentMethod.BILL
            Regex("""\b(cart[aã]o|cr[eé]dito|fatura)\b""", RegexOption.IGNORE_CASE).containsMatchIn(desc) || !cardName.isNullOrBlank() -> PaymentMethod.CREDIT_CARD
            txType == TransactionType.INCOME -> PaymentMethod.PIX
            else -> null
        }

        if (inferredPayMethod == null) {
            return ToolExecutionResult(text = "⚠️ Qual foi a forma de pagamento do lançamento de R$ ${FinanceEngine.round2(finalAmount)} em $desc? (Pix, Débito, Cartão de Crédito ou Boleto?)")
        }
        val payMethod = inferredPayMethod
        val splitType = when (splitTypeStr.uppercase()) {
            "SPLIT_50", "CASAL_50_50" -> SplitType.SPLIT_50
            "PARTNER", "PARCEIRO" -> SplitType.PARTNER
            "FRIEND", "AMIGO" -> SplitType.FRIEND
            "EXTERNAL", "TERCEIRO" -> SplitType.EXTERNAL
            "CUSTOM", "PERSONALIZADA" -> SplitType.CUSTOM
            else -> SplitType.MINE
        }

        return executeCreateTransaction(
            description = desc,
            amount = finalAmount,
            type = txType,
            category = category,
            subcategory = subcategory,
            paymentMethod = payMethod,
            cardName = cardName,
            splitType = splitType,
            friendName = friendName,
            friendEmail = friendEmail,
            friendSharePct = friendSharePct,
            thirdPartyName = thirdPartyName,
            currentInstallment = currentInstallment,
            totalInstallments = totalInstallments,
            customCompetenceMonth = competenceMonth,
            customDueDate = dueDate,
            confirmed = confirmed
        )
    }

    // -------------------------------------------------------------
    // 2. BATCH TRANSACTIONS (CRIAÇÃO EM LOTE)
    // -------------------------------------------------------------

    private suspend fun executeBatchFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val array = jsonObj.getAsJsonArrayOrNull("transactions") ?: jsonObj.getAsJsonArrayOrNull("transacoes")
        if (array == null || array.size() == 0) {
            return ToolExecutionResult(text = "⚠️ Nenhuma transação encontrada no lote para registrar.")
        }

        val confirmed = jsonObj.getBooleanOrNull("confirmed")
            ?: jsonObj.getBooleanOrNull("__confirmed")
            ?: false

        val user = repository.currentUser.value
        val defaultMonth = jsonObj.getStringOrNull("mesFaturaPadrao")
            ?: repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
        val defaultPayMethod = jsonObj.getStringOrNull("metodoPagamentoPadrao") ?: "pix"
        val cardParam = jsonObj.getStringOrNull("cartao")
            ?: jsonObj.getStringOrNull("cartaoPadrao")
            ?: jsonObj.getStringOrNull("cardName")
            ?: jsonObj.getStringOrNull("cardId")
        val origemDoc = jsonObj.getStringOrNull("origemDocumento") ?: jsonObj.getStringOrNull("documentOrigin")
        val isFaturaDoc = origemDoc?.contains("fatura", ignoreCase = true) == true ||
            defaultPayMethod.equals("credit_card", ignoreCase = true) ||
            defaultPayMethod.equals("cartao_credito", ignoreCase = true)

        val allCards = repository.getCards().first()
        val hasBatchCardItems = isFaturaDoc || array.any { elem ->
            if (!elem.isJsonObject) return@any false
            val obj = elem.asJsonObject
            val pm = obj.getStringOrNull("paymentMethod") ?: obj.getStringOrNull("metodoPagamento")
            pm?.equals("credit_card", ignoreCase = true) == true
        }
        val hasBatchExplicitCard = !cardParam.isNullOrBlank() || array.any { elem ->
            if (!elem.isJsonObject) return@any false
            val obj = elem.asJsonObject
            !obj.getStringOrNull("cartao").isNullOrBlank() || !obj.getStringOrNull("cardName").isNullOrBlank() || !obj.getStringOrNull("cardId").isNullOrBlank()
        }
        if (hasBatchCardItems && !hasBatchExplicitCard && allCards.size > 1) {
            return ToolExecutionResult(text = "⚠️ Identifiquei compras de cartão de crédito no lote, mas você possui múltiplos cartões cadastrados (${allCards.joinToString { it.cardName }}). Em qual deles essas compras foram realizadas?")
        }

        val defaultCard: Card? = if (!cardParam.isNullOrBlank()) {
            allCards.find { it.id == cardParam || it.cardName.contains(cardParam, ignoreCase = true) || cardParam.contains(it.cardName, ignoreCase = true) }
                ?: allCards.firstOrNull { it.ownerUid == user.uid }
                ?: allCards.firstOrNull()
        } else if (isFaturaDoc) {
            allCards.firstOrNull { it.ownerUid == user.uid } ?: allCards.firstOrNull()
        } else {
            allCards.firstOrNull { it.ownerUid == user.uid } ?: allCards.firstOrNull()
        }

        val friendships = repository.getFriendships().first()
        val partner = FinanceEngine.resolvePartner(user.uid, allCards, repository.allUsers)

        val createdList = mutableListOf<Transaction>()
        val pendingTxMaps = mutableListOf<Map<String, Any?>>()
        var totalExpenses = 0.0
        var totalIncomes = 0.0

        for (elem in array) {
            if (!elem.isJsonObject) continue
            val item = elem.asJsonObject
            val filterStatus = item.getStringOrNull("categoria_filtro") ?: "valida"
            var desc = item.getStringOrNull("description")
                ?: item.getStringOrNull("descricao")
                ?: item.getStringOrNull("contraparte")
                ?: "Item de Lote"
            val rawAmt = item.getDoubleOrNull("amount")
                ?: item.getDoubleOrNull("valorTotal")
                ?: item.getDoubleOrNull("valor_exato")
                ?: item.getDoubleOrNull("valor")
                ?: 0.0
            val amt = kotlin.math.abs(rawAmt)
            if (amt <= 0.0) continue

            val typeStr = item.getStringOrNull("type") ?: item.getStringOrNull("tipo") ?: if (rawAmt < 0) "income" else "expense"
            val isIncome = typeStr.equals("income", ignoreCase = true) || typeStr.equals("receita", ignoreCase = true) || rawAmt < 0
            val rawCat = item.getStringOrNull("category") ?: item.getStringOrNull("categoria")
            val rawSubcat = item.getStringOrNull("subcategory") ?: item.getStringOrNull("subcategoria")
            val (cat, subcat) = CategoryRegistry.resolveStrictCategory(
                rawCategory = rawCat,
                rawSubcategory = rawSubcat,
                type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                description = desc,
                guessFallback = { d, _ ->
                    val guess = neuralClassifier.guessCategoryAndSubcategory(d, amt)
                    Pair(guess.category, guess.subcategory)
                }
            )
            val payMethodStr = item.getStringOrNull("paymentMethod") ?: item.getStringOrNull("metodoPagamento") ?: defaultPayMethod
            val dt = item.getStringOrNull("date") ?: item.getStringOrNull("data") ?: item.getStringOrNull("dataVencimento") ?: FinanceEngine.todayKey()
            val comp = item.getStringOrNull("competenceMonth") ?: item.getStringOrNull("mesFatura") ?: defaultMonth

            val itemInstObj = item.getAsJsonObjectOrNull("installments")
            val itemCur = itemInstObj?.getIntOrNull("current")
                ?: item.getIntOrNull("currentInstallment")
                ?: item.getIntOrNull("parcelaAtual")
                ?: 1
            val itemTot = itemInstObj?.getIntOrNull("total")
                ?: item.getIntOrNull("totalInstallments")
                ?: item.getIntOrNull("totalParcelas")
                ?: (item.get("installments")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asInt else null })
                ?: 1

            val detectedBatchInst = if (itemTot <= 1) neuralClassifier.detectInstallments(desc) else null
            val finalCur = if (itemTot > 1) itemCur else (detectedBatchInst?.first ?: itemCur)
            val finalTot = if (itemTot > 1) itemTot else (detectedBatchInst?.second ?: itemTot)

            val itemCardParam = item.getStringOrNull("cartao") ?: item.getStringOrNull("cardId") ?: item.getStringOrNull("cardName")
            val itemCard = if (!itemCardParam.isNullOrBlank()) {
                allCards.find { it.id == itemCardParam || it.cardName.contains(itemCardParam, ignoreCase = true) || itemCardParam.contains(it.cardName, ignoreCase = true) }
            } else defaultCard

            val isCardPayment = (itemCard != null && !isIncome) ||
                payMethodStr.equals("credit_card", ignoreCase = true) ||
                payMethodStr.equals("cartao_credito", ignoreCase = true) ||
                (isFaturaDoc && !isIncome)

            val resolvedPayMethod = if (isIncome) {
                PaymentMethod.PIX
            } else if (isCardPayment) {
                PaymentMethod.CREDIT_CARD
            } else {
                PaymentMethod.from(payMethodStr)
            }

            val targetCard = if (resolvedPayMethod == PaymentMethod.CREDIT_CARD) {
                itemCard ?: defaultCard ?: allCards.firstOrNull { it.ownerUid == user.uid } ?: allCards.firstOrNull()
            } else null
            val targetCardId = targetCard?.id

            val (resolvedDueDate, resolvedComp) = if (targetCard != null) {
                val dates = FinanceEngine.calculateCardInvoiceDates(dt, targetCard)
                val compMonth = item.getStringOrNull("competenceMonth")
                    ?: item.getStringOrNull("mesFatura")
                    ?: dates.second
                // Para cartão de crédito, a data da transação é a data da compra (dt).
                // O mês de competência da fatura do cartão é compMonth.
                Pair(dt, compMonth)
            } else {
                Pair(dt, comp)
            }

            // Split / Rateio detection and resolution
            val itemSplitTypeStr = item.getStringOrNull("splitType")
                ?: item.getStringOrNull("tipoDivisao")
                ?: item.getStringOrNull("tipo_divisao")
            var itemFriendName = item.getStringOrNull("friendName")
                ?: item.getStringOrNull("amigo")
                ?: item.getStringOrNull("nomeAmigo")
            val itemFriendEmail = item.getStringOrNull("friendEmail")
                ?: item.getStringOrNull("emailAmigo")
            var itemFriendPct = item.getDoubleOrNull("friendSharePct")
                ?: item.getDoubleOrNull("friendPct")
                ?: item.getDoubleOrNull("percentualAmigo")
                ?: item.getDoubleOrNull("porcentagemAmigo")
                ?: item.getDoubleOrNull("pctAmigo")
                ?: item.getDoubleOrNull("pct")
            var itemThirdParty = item.getStringOrNull("thirdPartyName")
                ?: item.getStringOrNull("externalName")
                ?: item.getStringOrNull("terceiro")
                ?: item.getStringOrNull("nomeTerceiro")

            // Detecção via regex se a descrição contiver indicação de rateio (ex: "50% para Kayo Araujo" ou "divido com Fulano")
            var cleanDesc = desc
            if (itemSplitTypeStr.isNullOrBlank() && itemFriendName.isNullOrBlank() && itemThirdParty.isNullOrBlank()) {
                val pctSplitMatch = Regex("""[;,]?\s*(\d+(?:[.,]\d+)?)\s*%\s*(?:para|com|de)\s+([A-Za-zÀ-ÖØ-öø-ÿ\s]+?)(?:[;,\.]|$)""", RegexOption.IGNORE_CASE).find(desc)
                if (pctSplitMatch != null) {
                    val pctVal = pctSplitMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 50.0
                    val targetName = pctSplitMatch.groupValues[2].trim()
                    itemFriendPct = pctVal
                    itemFriendName = targetName
                    cleanDesc = desc.replace(pctSplitMatch.value, "").trim().removeSuffix(";").removeSuffix(",").trim()
                } else {
                    val dividedMatch = Regex("""[;,]?\s*(?:dividid[oa]|divis[ãa]o|dividi|meio\s+a\s+meio|50/50|50-50)\s*(?:com|para)\s+([A-Za-zÀ-ÖØ-öø-ÿ\s]+?)(?:[;,\.]|$)""", RegexOption.IGNORE_CASE).find(desc)
                    if (dividedMatch != null) {
                        val targetName = dividedMatch.groupValues[1].trim()
                        itemFriendPct = 50.0
                        itemFriendName = targetName
                        cleanDesc = desc.replace(dividedMatch.value, "").trim().removeSuffix(";").removeSuffix(",").trim()
                    }
                }
            }
            cleanDesc = cleanDesc.replace(Regex("""[;,]?\s*R\$\s*\d+(?:[.,]\d+)?""", RegexOption.IGNORE_CASE), "").trim().removeSuffix(";").removeSuffix(",").trim()

            val matchedFriend = if (!itemFriendEmail.isNullOrBlank()) {
                friendships.find {
                    it.status == FriendshipStatus.ACCEPTED &&
                    (it.recipientEmail.equals(itemFriendEmail, true) || it.requesterEmail.equals(itemFriendEmail, true))
                }
            } else if (!itemFriendName.isNullOrBlank()) {
                friendships.find {
                    it.status == FriendshipStatus.ACCEPTED &&
                    (it.recipientName.contains(itemFriendName, true) || it.requesterName.contains(itemFriendName, true))
                }
            } else null

            val assignedFriendUid = matchedFriend?.let {
                if (it.requesterUid == user.uid) it.recipientUid else it.requesterUid
            }
            val assignedFriendName = matchedFriend?.let {
                if (it.requesterUid == user.uid) it.recipientName else it.requesterName
            } ?: itemFriendName ?: itemFriendEmail

            val parsedSplitType = when (itemSplitTypeStr?.uppercase()) {
                "SPLIT_50", "CASAL_50_50", "50/50" -> SplitType.SPLIT_50
                "PARTNER", "PARCEIRO" -> SplitType.PARTNER
                "FRIEND", "AMIGO" -> SplitType.FRIEND
                "EXTERNAL", "TERCEIRO" -> SplitType.EXTERNAL
                "CUSTOM", "PERSONALIZADA" -> SplitType.CUSTOM
                else -> null
            }

            val effectiveSplitType = when {
                parsedSplitType != null -> parsedSplitType
                assignedFriendUid != null || !itemFriendName.isNullOrBlank() -> SplitType.FRIEND
                !itemThirdParty.isNullOrBlank() -> SplitType.EXTERNAL
                else -> SplitType.MINE
            }

            val effectiveFriendPct = itemFriendPct
                ?: if (effectiveSplitType == SplitType.FRIEND) {
                    if (parsedSplitType == SplitType.SPLIT_50) 50.0 else 50.0
                } else null

            val payerUid = if (resolvedPayMethod == PaymentMethod.CREDIT_CARD && targetCard != null) {
                targetCard.ownerUid.ifBlank { user.uid }
            } else {
                user.uid
            }

            val splitResult = FinanceEngine.buildSplit(
                splitType = effectiveSplitType,
                total = amt,
                myUid = user.uid,
                myName = user.displayName,
                partnerUid = partner?.uid,
                partnerName = partner?.displayName,
                payerUid = payerUid,
                externalName = itemThirdParty,
                assignedFriendUid = assignedFriendUid,
                assignedFriendName = assignedFriendName,
                friendSharePct = effectiveFriendPct
            )

            val myImpact = splitResult.userImpact[user.uid] ?: amt
            val acceptanceStatus = if (assignedFriendUid != null) "pending_approval" else null

            pendingTxMaps.add(
                mapOf(
                    "data" to dt,
                    "tipo" to if (isIncome) "income" else "expense",
                    "valor_exato" to amt,
                    "contraparte" to cleanDesc,
                    "categoria_filtro" to filterStatus,
                    "categoria" to cat,
                    "subcategoria" to subcat,
                    "metodoPagamento" to payMethodStr,
                    "cartao" to (targetCard?.cardName ?: itemCardParam ?: cardParam),
                    "cardId" to targetCardId,
                    "splitType" to effectiveSplitType.name,
                    "friendName" to assignedFriendName,
                    "friendSharePct" to effectiveFriendPct,
                    "motivoIgnorado" to item.getStringOrNull("motivoIgnorado")
                )
            )
            if (filterStatus.equals("ignorada", ignoreCase = true)) continue

            if (finalTot > 1 && !isIncome) {
                val cleanInstDesc = FinanceEngine.stripInstallmentSuffix(cleanDesc)
                val groupId = "inst_" + UUID.randomUUID().toString().take(8)
                for (i in 1..finalTot) {
                    val delta = i - finalCur
                    val targetMonth = FinanceEngine.shiftMonth(resolvedComp, delta)
                    val targetDueDate = shiftDateMonth(resolvedDueDate, delta)
                    val isPast = i < finalCur
                    val tx = Transaction(
                        id = "tx_" + UUID.randomUUID().toString().take(8),
                        createdByUid = user.uid,
                        paidByUid = payerUid,
                        description = FinanceEngine.formatInstallmentDesc(cleanInstDesc, i, finalTot),
                        category = cat,
                        subcategory = subcat,
                        type = TransactionType.EXPENSE,
                        paymentMethod = resolvedPayMethod,
                        cardId = targetCardId,
                        status = if (isPast) TransactionStatus.PAID else TransactionStatus.PENDING,
                        actualAmount = amt,
                        totalAmount = amt,
                        dueDate = targetDueDate,
                        competenceMonth = targetMonth,
                        visibleToUids = splitResult.visibleToUids,
                        userImpact = splitResult.userImpact,
                        allocations = if (isPast) splitResult.allocations.map { it.copy(reimbursed = true) } else splitResult.allocations,
                        splitType = effectiveSplitType,
                        assignedFriendUid = assignedFriendUid,
                        assignedFriendName = assignedFriendName,
                        friendSharePct = effectiveFriendPct,
                        externalName = if (effectiveSplitType == SplitType.EXTERNAL) itemThirdParty else null,
                        acceptanceStatus = acceptanceStatus,
                        installmentGroupId = groupId,
                        installmentNumber = i,
                        installmentsTotal = finalTot
                    )
                    createdList.add(tx)
                }
                totalExpenses += myImpact
            } else {
                val tx = Transaction(
                    id = "tx_" + UUID.randomUUID().toString().take(8),
                    createdByUid = user.uid,
                    paidByUid = payerUid,
                    description = cleanDesc,
                    category = cat,
                    subcategory = subcat,
                    type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                    paymentMethod = resolvedPayMethod,
                    cardId = targetCardId,
                    status = if (isIncome) TransactionStatus.PAID else if (resolvedPayMethod == PaymentMethod.CREDIT_CARD) TransactionStatus.PENDING else TransactionStatus.PAID,
                    actualAmount = amt,
                    totalAmount = amt,
                    dueDate = resolvedDueDate,
                    competenceMonth = resolvedComp,
                    visibleToUids = splitResult.visibleToUids,
                    userImpact = splitResult.userImpact,
                    allocations = splitResult.allocations,
                    splitType = effectiveSplitType,
                    assignedFriendUid = assignedFriendUid,
                    assignedFriendName = assignedFriendName,
                    friendSharePct = effectiveFriendPct,
                    externalName = if (effectiveSplitType == SplitType.EXTERNAL) itemThirdParty else null,
                    acceptanceStatus = acceptanceStatus
                )
                createdList.add(tx)
                if (isIncome) totalIncomes += amt else totalExpenses += myImpact
            }
        }

        if (createdList.isEmpty()) {
            return ToolExecutionResult(text = "⚠️ Não foi possível processar as transações do lote.")
        }

        if (!confirmed) {
            val before = repository.getMonthSummary().first()
            val netDiff = totalIncomes - totalExpenses
            val estimatedAfter = FinanceEngine.round2(before.freeBalance + netDiff)

            val pendingAction = PendingAction(
                id = "act_" + UUID.randomUUID().toString().take(8),
                toolName = "BATCH_TRANSACTIONS",
                title = "Confirmar Lote (${createdList.size} itens)",
                description = "${createdList.size} transações prontas para lançamento",
                detail = "Receitas: ${FinanceEngine.formatCurrency(totalIncomes)} | Despesas: ${FinanceEngine.formatCurrency(totalExpenses)}",
                args = mapOf(
                    "transactionsJson" to array.toString(),
                    "pending_transactions" to pendingTxMaps,
                    "cartaoPadrao" to (cardParam ?: defaultCard?.cardName),
                    "cartao" to (cardParam ?: defaultCard?.cardName),
                    "cardName" to (cardParam ?: defaultCard?.cardName),
                    "cardId" to defaultCard?.id,
                    "metodoPagamentoPadrao" to defaultPayMethod,
                    "origemDocumento" to (origemDoc ?: if (isFaturaDoc) "fatura_cartao" else null),
                    "mesFaturaPadrao" to defaultMonth,
                    "confirmed" to true
                ),
                status = PendingActionStatus.PENDING
            )

            return ToolExecutionResult(
                text = "Identifiquei **${createdList.size} lançamentos** para cadastro em lote:\n\n" +
                        (if (totalIncomes > 0) "• 🟢 **Receitas**: ${FinanceEngine.formatCurrency(totalIncomes)}\n" else "") +
                        (if (totalExpenses > 0) "• 🔴 **Despesas**: ${FinanceEngine.formatCurrency(totalExpenses)}\n" else "") +
                        "• **Saldo Livre previsto**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" +
                        "Por favor, confirme se deseja registrar todos esses lançamentos no seu Saldo Livre.",
                actionCard = ActionCardData(
                    title = "Confirmar Lote (${createdList.size} lançamentos)",
                    description = "${createdList.size} itens preparados",
                    detail = "Receitas: ${FinanceEngine.formatCurrency(totalIncomes)} | Despesas: ${FinanceEngine.formatCurrency(totalExpenses)}",
                    newFreeBalance = "Saldo Livre previsto: ${FinanceEngine.formatCurrency(before.freeBalance)} → ${FinanceEngine.formatCurrency(estimatedAfter)}",
                    pendingAction = pendingAction,
                    pendingTransactions = pendingTxMaps
                )
            )
        }

        repository.addTransactions(createdList)
        val summaryNow = repository.getMonthSummary().first()

        return ToolExecutionResult(
            text = "✅ **Lote de ${createdList.size} lançamentos registrado com sucesso!**\n\n" +
                    (if (totalIncomes > 0) "• 🟢 **Receitas**: ${FinanceEngine.formatCurrency(totalIncomes)}\n" else "") +
                    (if (totalExpenses > 0) "• 🔴 **Despesas**: ${FinanceEngine.formatCurrency(totalExpenses)}\n" else "") +
                    "• **Saldo Livre Atual**: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}",
            actionCard = ActionCardData(
                title = "Lote de Lançamentos Registrado",
                description = "${createdList.size} itens adicionados ao Saldo Livre",
                detail = "Receitas: ${FinanceEngine.formatCurrency(totalIncomes)} | Despesas: ${FinanceEngine.formatCurrency(totalExpenses)}",
                newFreeBalance = "Saldo Livre: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
            )
        )
    }

    // -------------------------------------------------------------
    // 3. EDIT TRANSACTION (ATUALIZAR / CORRIGIR NO BANCO)
    // -------------------------------------------------------------

    suspend fun executeEditTransaction(
        searchTerm: String? = null,
        searchTerms: List<String>? = null,
        transactionId: String? = null,
        targetIds: List<String>? = null,
        month: String? = null,
        originDate: String? = null,
        filterAmount: Double? = null,
        editAllMatches: Boolean = false,
        newAmount: Double? = null,
        newDescription: String? = null,
        newCategory: String? = null,
        newSubcategory: String? = null,
        newStatus: TransactionStatus? = null,
        newPaymentMethod: PaymentMethod? = null,
        newCompetenceMonth: String? = null,
        newDueDate: String? = null,
        newSplitType: SplitType? = null,
        confirmed: Boolean = false
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val user = repository.currentUser.value

        val effectiveTerms = searchTerms?.filter { it.isNotBlank() }
            ?: when {
                !searchTerm.isNullOrBlank() && (searchTerm.contains(",") || searchTerm.contains(";") || searchTerm.contains(" e ")) -> {
                    searchTerm.split(Regex(""",|;|\s+e\s+""")).map { it.trim() }.filter { it.isNotBlank() }
                }
                !searchTerm.isNullOrBlank() -> listOf(searchTerm)
                else -> emptyList()
            }

        var targets = if (!targetIds.isNullOrEmpty()) {
            all.filter { it.id in targetIds }
        } else if (!transactionId.isNullOrBlank()) {
            all.filter { it.id == transactionId }
        } else if (effectiveTerms.isNotEmpty()) {
            val selected = mutableListOf<Transaction>()
            val selectedIds = mutableSetOf<String>()

            for (term in effectiveTerms) {
                val candidates = all.filter { t ->
                    if (t.id in selectedIds) return@filter false
                    if (!month.isNullOrBlank() && t.competenceMonth != month) return@filter false
                    if (!originDate.isNullOrBlank() && t.dueDate != originDate) return@filter false
                    if (filterAmount != null && kotlin.math.abs(kotlin.math.abs(t.effectiveAmount) - kotlin.math.abs(filterAmount)) > 0.05) return@filter false
                    matchesSearch(t.description, term) || matchesSearch(t.category, term) || (t.subcategory != null && matchesSearch(t.subcategory, term))
                }

                if (editAllMatches) {
                    selected.addAll(candidates)
                    selectedIds.addAll(candidates.map { it.id })
                } else {
                    // Seleção cirúrgica: escolhe apenas o item mais recente correspondente a este termo específico
                    val best = candidates.maxByOrNull { it.createdAt }
                    if (best != null) {
                        selected.add(best)
                        selectedIds.add(best.id)
                    }
                }
            }
            selected
        } else emptyList()

        if (targets.isEmpty()) {
            val termsDesc = searchTerm ?: effectiveTerms.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: transactionId ?: ""
            return ToolExecutionResult(
                text = "⚠️ Nenhuma transação foi encontrada com o termo \"$termsDesc\" para edição."
            )
        }

        // Se pertencer a parcelamento e for edição de categoria ou descrição, inclui irmãos do mesmo grupo
        val groupIds = targets.mapNotNull { it.installmentGroupId }.toSet()
        if (groupIds.isNotEmpty() && (newCategory != null || newSubcategory != null || newDescription != null)) {
            val siblings = all.filter { it.installmentGroupId in groupIds && !targets.contains(it) }
            targets = targets + siblings
        }

        val updatedCount = targets.size
        val firstTarget = targets.first()
        val firstDesc = firstTarget.description
        val resolvedEditPair = if (newCategory != null || newSubcategory != null) {
            CategoryRegistry.resolveStrictCategory(
                rawCategory = newCategory ?: firstTarget.category,
                rawSubcategory = newSubcategory ?: firstTarget.subcategory,
                type = firstTarget.type,
                description = newDescription ?: firstTarget.description
            )
        } else null
        val resolvedEditCat = resolvedEditPair?.first
        val resolvedEditSubcat = resolvedEditPair?.second

        val changesList = mutableListOf<String>()

        if (newDescription != null) {
            changesList.add("Descrição: \"$newDescription\"")
        }
        if (newAmount != null && newAmount > 0.0) {
            changesList.add("Valor: ${FinanceEngine.formatCurrency(firstTarget.effectiveAmount)} → ${FinanceEngine.formatCurrency(newAmount)}")
        }
        if (newCategory != null || (resolvedEditCat != null && resolvedEditCat != firstTarget.category)) {
            changesList.add("Categoria: $resolvedEditCat")
        }
        if (newSubcategory != null || (resolvedEditSubcat != null && resolvedEditSubcat != firstTarget.subcategory)) {
            changesList.add("Subcategoria: $resolvedEditSubcat")
        }
        if (newStatus != null) {
            changesList.add("Status: ${newStatus.label}")
        }
        if (newPaymentMethod != null) {
            changesList.add("Método: ${newPaymentMethod.label}")
        }
        if (newCompetenceMonth != null && newCompetenceMonth.matches(Regex("""^\d{4}-\d{2}$"""))) {
            changesList.add("Mês da Fatura: ${FinanceEngine.monthLabel(newCompetenceMonth)}")
        }
        if (newDueDate != null && newDueDate.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            changesList.add("Data / Vencimento: ${FinanceEngine.formatShortDate(newDueDate)}")
        }
        if (newSplitType != null) {
            changesList.add("Divisão: ${newSplitType.label}")
        }

        if (changesList.isEmpty()) {
            return ToolExecutionResult(
                text = "⚠️ Nenhuma alteração foi informada para o lançamento \"$firstDesc\"."
            )
        }

        val isSameInstallmentGroup = groupIds.size == 1 && targets.all { it.installmentGroupId != null }
        val itemsSummary = if (updatedCount > 1) {
            if (isSameInstallmentGroup) {
                "$firstDesc (+${updatedCount - 1} parcelas)"
            } else {
                targets.take(3).joinToString(", ") { it.description } + (if (targets.size > 3) " (+${targets.size - 3})" else "")
            }
        } else {
            "$firstDesc (${FinanceEngine.formatCurrency(newAmount ?: firstTarget.effectiveAmount)})"
        }

        // SE AINDA NÃO CONFIRMADO PELO USUÁRIO: Gera rascunho com pedido de confirmação interativo
        if (!confirmed) {
            val before = repository.getMonthSummary().first()
            var estimatedAfter = before.freeBalance

            if (newAmount != null && newAmount > 0.0) {
                val oldImpact = firstTarget.userImpact[user.uid] ?: firstTarget.effectiveAmount
                val partner = repository.allUsers.find { it.uid != user.uid }
                val newSplit = FinanceEngine.buildSplit(
                    splitType = newSplitType ?: firstTarget.splitType ?: SplitType.MINE,
                    total = newAmount,
                    myUid = user.uid,
                    myName = user.displayName,
                    partnerUid = partner?.uid,
                    partnerName = partner?.displayName,
                    payerUid = firstTarget.paidByUid
                )
                val newImpact = newSplit.userImpact[user.uid] ?: newAmount
                val diff = if (firstTarget.type == TransactionType.INCOME) (newAmount - firstTarget.effectiveAmount) else -(newImpact - oldImpact)
                estimatedAfter = FinanceEngine.round2(before.freeBalance + diff)
            }

            val confirmMessage = if (updatedCount > 1) {
                "Entendi que você deseja alterar **$updatedCount lançamentos** ($itemsSummary):\n\n" +
                        changesList.joinToString("\n") { "• **$it**" } + "\n\n" +
                        (if (newAmount != null) "• **Impacto no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" else "") +
                        "Por favor, confirme se os dados estão corretos para salvar as edições."
            } else {
                "Entendi que você deseja alterar o lançamento **\"$firstDesc\"** (${FinanceEngine.formatCurrency(firstTarget.effectiveAmount)}):\n\n" +
                        changesList.joinToString("\n") { "• **$it**" } + "\n\n" +
                        (if (newAmount != null) "• **Impacto no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" else "") +
                        "Por favor, confirme se os dados estão corretos para salvar as edições."
            }

            val actionTitle = if (updatedCount > 1) "Confirmar Edição ($updatedCount itens)" else "Confirmar Edição: $firstDesc"
            val actionDesc = itemsSummary

            val pendingAction = PendingAction(
                id = "act_" + UUID.randomUUID().toString().take(8),
                toolName = "EDIT_TRANSACTION",
                title = actionTitle,
                description = actionDesc,
                detail = changesList.joinToString(" • "),
                args = mapOf(
                    "transactionId" to (transactionId ?: firstTarget.id),
                    "targetIds" to targets.map { it.id },
                    "searchTerm" to searchTerm,
                    "searchTerms" to effectiveTerms,
                    "month" to month,
                    "originDate" to originDate,
                    "filterAmount" to filterAmount,
                    "editAllMatches" to editAllMatches,
                    "newAmount" to newAmount,
                    "newDescription" to newDescription,
                    "newCategory" to (resolvedEditCat ?: newCategory),
                    "newSubcategory" to (resolvedEditSubcat ?: newSubcategory),
                    "newStatus" to newStatus?.name,
                    "newPaymentMethod" to newPaymentMethod?.name,
                    "newCompetenceMonth" to newCompetenceMonth,
                    "newDueDate" to newDueDate,
                    "newSplitType" to newSplitType?.name,
                    "confirmed" to true
                ),
                status = PendingActionStatus.PENDING
            )

            return ToolExecutionResult(
                text = confirmMessage,
                actionCard = ActionCardData(
                    title = actionTitle,
                    description = actionDesc,
                    detail = changesList.joinToString(" • "),
                    newFreeBalance = if (newAmount != null) "Saldo Livre previsto: ${FinanceEngine.formatCurrency(before.freeBalance)} → ${FinanceEngine.formatCurrency(estimatedAfter)}" else null,
                    pendingAction = pendingAction
                )
            )
        }

        val allCards = repository.getCards().first()
        for (tx in targets) {
            var updated = tx

            if (newDescription != null) {
                val clean = FinanceEngine.stripInstallmentSuffix(newDescription)
                val finalDesc = if (tx.isInstallment && tx.installmentNumber != null && tx.installmentsTotal != null) {
                    FinanceEngine.formatInstallmentDesc(clean, tx.installmentNumber, tx.installmentsTotal)
                } else clean
                updated = updated.copy(description = finalDesc)
            }

            if (newAmount != null && newAmount > 0.0) {
                val userSplit = tx.splitType ?: SplitType.MINE
                val partner = repository.allUsers.find { it.uid != user.uid }
                val newSplit = FinanceEngine.buildSplit(
                    splitType = userSplit,
                    total = newAmount,
                    myUid = user.uid,
                    myName = user.displayName,
                    partnerUid = partner?.uid,
                    partnerName = partner?.displayName,
                    payerUid = tx.paidByUid
                )
                updated = updated.copy(
                    totalAmount = newAmount,
                    actualAmount = if (tx.actualAmount != null || tx.status == TransactionStatus.PAID) newAmount else null,
                    estimatedAmount = if (tx.status == TransactionStatus.ESTIMATED) newAmount else null,
                    userImpact = newSplit.userImpact,
                    allocations = newSplit.allocations
                )
            }

            if (newCategory != null || newSubcategory != null) {
                val (targetCat, targetSub) = CategoryRegistry.resolveStrictCategory(
                    rawCategory = newCategory ?: tx.category,
                    rawSubcategory = newSubcategory ?: tx.subcategory,
                    type = tx.type,
                    description = newDescription ?: tx.description
                )
                updated = updated.copy(category = targetCat, subcategory = targetSub)
            }

            if (newStatus != null) {
                updated = updated.copy(status = newStatus)
            }

            if (newPaymentMethod != null) {
                updated = updated.copy(paymentMethod = newPaymentMethod)
            }

            if (newCompetenceMonth != null && newCompetenceMonth.matches(Regex("""^\d{4}-\d{2}$"""))) {
                if (tx.isInstallment && tx.installmentNumber != null) {
                    val delta = tx.installmentNumber - 1
                    updated = updated.copy(competenceMonth = FinanceEngine.shiftMonth(newCompetenceMonth, delta))
                } else {
                    updated = updated.copy(competenceMonth = newCompetenceMonth)
                }
            }

            if (newDueDate != null && newDueDate.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
                val oldComp = updated.competenceMonth
                val newComp = if (updated.paymentMethod == PaymentMethod.CREDIT_CARD && updated.cardId != null) {
                    val card = allCards.find { it.id == updated.cardId }
                    if (card != null) {
                        FinanceEngine.calculateCardInvoiceDates(newDueDate, card).second
                    } else oldComp
                } else {
                    newDueDate.take(7)
                }
                updated = updated.copy(
                    dueDate = newDueDate,
                    competenceMonth = if (newCompetenceMonth.isNullOrBlank()) newComp else updated.competenceMonth
                )
            }

            if (newSplitType != null) {
                val partner = repository.allUsers.find { it.uid != user.uid }
                val newSplit = FinanceEngine.buildSplit(
                    splitType = newSplitType,
                    total = updated.totalAmount,
                    myUid = user.uid,
                    myName = user.displayName,
                    partnerUid = partner?.uid,
                    partnerName = partner?.displayName,
                    payerUid = updated.paidByUid
                )
                updated = updated.copy(
                    splitType = newSplitType,
                    userImpact = newSplit.userImpact,
                    allocations = newSplit.allocations,
                    visibleToUids = newSplit.visibleToUids
                )
            }

            repository.updateTransaction(updated)
        }

        repository.logActivity(
            ActivityLog(
                id = "act_" + UUID.randomUUID().toString().take(8),
                actionType = ActivityActionType.TRANSACTION_UPDATED,
                title = "Lançamento(s) Editado(s)",
                description = "Atualizou $updatedCount item(ns): ${changesList.joinToString(", ")}",
                competenceMonth = targets.firstOrNull()?.competenceMonth,
                userUid = user.uid,
                userName = user.displayName
            )
        )

        val summaryNow = repository.getMonthSummary().first()

        return ToolExecutionResult(
            text = "✏️ **Lançamento atualizado com sucesso!**\n\n" +
                    "• **Item(ns) alterado(s)**: $itemsSummary\n" +
                    "• **Alterações realizadas**:\n" + changesList.joinToString("\n") { "  - $it" } + "\n\n" +
                    "Seu **Saldo Livre** foi recalculado no banco de dados.",
            actionCard = ActionCardData(
                title = "Lançamento Editado",
                description = itemsSummary,
                detail = changesList.joinToString(" • "),
                newFreeBalance = "Saldo Livre Atual: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
            )
        )
    }

    private suspend fun executeEditFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val transactionsArray = jsonObj.getAsJsonArrayOrNull("transactions")
            ?: jsonObj.getAsJsonArrayOrNull("itens")
            ?: jsonObj.getAsJsonArrayOrNull("items")
            ?: jsonObj.getAsJsonArrayOrNull("lancamentos")
            ?: jsonObj.getAsJsonArrayOrNull("edits")

        val searchTerm = jsonObj.getStringOrNull("searchTerm") ?: jsonObj.getStringOrNull("termoBusca")
        val transactionId = jsonObj.getStringOrNull("transactionId") ?: jsonObj.getStringOrNull("id")
        val targetIdsArray = jsonObj.getAsJsonArrayOrNull("targetIds") ?: jsonObj.getAsJsonArrayOrNull("ids")
        val targetIds = targetIdsArray?.mapNotNull { it.asString }
        val rawSearchTerms = jsonObj.getAsJsonArrayOrNull("searchTerms") ?: jsonObj.getAsJsonArrayOrNull("termosBusca")
        val searchTermsList = rawSearchTerms?.mapNotNull { it.asString }

        val month = jsonObj.getStringOrNull("month") ?: jsonObj.getStringOrNull("mes")
        val newAmount = jsonObj.getDoubleOrNull("newAmount") ?: jsonObj.getDoubleOrNull("novoValor")
        val newDescription = jsonObj.getStringOrNull("newDescription") ?: jsonObj.getStringOrNull("novaDescricao")
        val newCategory = jsonObj.getStringOrNull("newCategory") ?: jsonObj.getStringOrNull("novaCategoria")
        val newSubcategory = jsonObj.getStringOrNull("newSubcategory") ?: jsonObj.getStringOrNull("novaSubcategoria")
        val newStatusStr = jsonObj.getStringOrNull("newStatus") ?: jsonObj.getStringOrNull("novoStatus")
        val newPayMethodStr = jsonObj.getStringOrNull("newPaymentMethod") ?: jsonObj.getStringOrNull("novoMetodoPagamento")
        val newCompetenceMonth = jsonObj.getStringOrNull("newCompetenceMonth") ?: jsonObj.getStringOrNull("novoMesFatura")
        val newDueDate = jsonObj.getStringOrNull("newDueDate")
            ?: jsonObj.getStringOrNull("novaDataVencimento")
            ?: jsonObj.getStringOrNull("newDate")
            ?: jsonObj.getStringOrNull("novaData")
            ?: jsonObj.getStringOrNull("date")
            ?: jsonObj.getStringOrNull("data")
            ?: jsonObj.getStringOrNull("dueDate")
            ?: jsonObj.getStringOrNull("dataVencimento")
        val newSplitTypeStr = jsonObj.getStringOrNull("newSplitType") ?: jsonObj.getStringOrNull("novoTipoDivisao")
        val originDate = jsonObj.getStringOrNull("originDate")
            ?: jsonObj.getStringOrNull("currentDate")
            ?: jsonObj.getStringOrNull("dataOriginal")
            ?: jsonObj.getStringOrNull("dataAtual")
            ?: jsonObj.getStringOrNull("startDate")
            ?: (jsonObj.getStringOrNull("date")?.takeIf { it != newDueDate })
            ?: (jsonObj.getStringOrNull("data")?.takeIf { it != newDueDate })
        val filterAmount = jsonObj.getDoubleOrNull("amount")
            ?: jsonObj.getDoubleOrNull("valor")
            ?: jsonObj.getDoubleOrNull("filterAmount")
        val editAllMatches = jsonObj.getBooleanOrNull("editAll")
            ?: jsonObj.getBooleanOrNull("editAllMatches")
            ?: jsonObj.getBooleanOrNull("alterarTodos")
            ?: jsonObj.getBooleanOrNull("allOccurrences")
            ?: false
        val confirmed = jsonObj.getBooleanOrNull("confirmed")
            ?: jsonObj.getBooleanOrNull("__confirmed")
            ?: false

        val newStatus = newStatusStr?.let { TransactionStatus.from(it) }
        val newPayMethod = newPayMethodStr?.let { PaymentMethod.from(it) }
        val newSplitType = newSplitTypeStr?.let {
            when (it.uppercase()) {
                "SPLIT_50", "CASAL_50_50" -> SplitType.SPLIT_50
                "PARTNER", "PARCEIRO" -> SplitType.PARTNER
                "FRIEND", "AMIGO" -> SplitType.FRIEND
                "EXTERNAL", "TERCEIRO" -> SplitType.EXTERNAL
                else -> SplitType.MINE
            }
        }

        // Se houver uma lista de transações explícita dentro do JSON (batch edit):
        if (transactionsArray != null && transactionsArray.size() > 0) {
            val all = repository.getTransactions().first()
            val resolvedTargetIds = mutableListOf<String>()
            for (elem in transactionsArray) {
                if (!elem.isJsonObject) continue
                val item = elem.asJsonObject
                val itemTerm = item.getStringOrNull("searchTerm") ?: item.getStringOrNull("termoBusca")
                    ?: item.getStringOrNull("description") ?: item.getStringOrNull("descricao")
                val itemId = item.getStringOrNull("transactionId") ?: item.getStringOrNull("id")
                val matched = if (!itemId.isNullOrBlank()) {
                    all.filter { it.id == itemId }
                } else if (!itemTerm.isNullOrBlank()) {
                    val candidates = all.filter { t ->
                        if (t.id in resolvedTargetIds) return@filter false
                        if (!month.isNullOrBlank() && t.competenceMonth != month) return@filter false
                        if (!originDate.isNullOrBlank() && t.dueDate != originDate) return@filter false
                        if (filterAmount != null && kotlin.math.abs(kotlin.math.abs(t.effectiveAmount) - kotlin.math.abs(filterAmount)) > 0.05) return@filter false
                        matchesSearch(t.description, itemTerm) || matchesSearch(t.category, itemTerm) || (t.subcategory != null && matchesSearch(t.subcategory, itemTerm))
                    }
                    if (editAllMatches) {
                        candidates
                    } else {
                        listOfNotNull(candidates.maxByOrNull { it.createdAt })
                    }
                } else emptyList()
                resolvedTargetIds.addAll(matched.map { it.id })
            }
            if (resolvedTargetIds.isNotEmpty()) {
                return executeEditTransaction(
                    targetIds = resolvedTargetIds.distinct(),
                    month = month,
                    originDate = originDate,
                    filterAmount = filterAmount,
                    editAllMatches = editAllMatches,
                    newAmount = newAmount,
                    newDescription = newDescription,
                    newCategory = newCategory,
                    newSubcategory = newSubcategory,
                    newStatus = newStatus,
                    newPaymentMethod = newPayMethod,
                    newCompetenceMonth = newCompetenceMonth,
                    newDueDate = newDueDate,
                    newSplitType = newSplitType,
                    confirmed = confirmed
                )
            }
        }

        val effectiveSearchTerms = when {
            !searchTermsList.isNullOrEmpty() -> searchTermsList
            !searchTerm.isNullOrBlank() && (searchTerm.contains(",") || searchTerm.contains(";") || searchTerm.contains(" e ")) -> {
                searchTerm.split(Regex(""",|;|\s+e\s+""")).map { it.trim() }.filter { it.isNotBlank() }
            }
            !searchTerm.isNullOrBlank() -> listOf(searchTerm)
            else -> emptyList()
        }

        return executeEditTransaction(
            searchTerm = searchTerm,
            searchTerms = effectiveSearchTerms.ifEmpty { null },
            transactionId = transactionId,
            targetIds = targetIds,
            month = month,
            originDate = originDate,
            filterAmount = filterAmount,
            editAllMatches = editAllMatches,
            newAmount = newAmount,
            newDescription = newDescription,
            newCategory = newCategory,
            newSubcategory = newSubcategory,
            newStatus = newStatus,
            newPaymentMethod = newPayMethod,
            newCompetenceMonth = newCompetenceMonth,
            newDueDate = newDueDate,
            newSplitType = newSplitType,
            confirmed = confirmed
        )
    }

    // -------------------------------------------------------------
    // 4. DELETE TRANSACTION (EXCLUSÃO CIRÚRGICA / PARCELAS)
    // -------------------------------------------------------------

    suspend fun executeDeleteTransaction(
        searchTerm: String? = null,
        transactionId: String? = null,
        targetIds: List<String>? = null,
        amount: Double? = null,
        month: String? = null,
        deleteAllInstallments: Boolean = false,
        confirmed: Boolean = false
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val user = repository.currentUser.value

        val targets = if (!targetIds.isNullOrEmpty()) {
            all.filter { it.id in targetIds }
        } else if (!transactionId.isNullOrBlank()) {
            all.filter { it.id == transactionId }
        } else if (!searchTerm.isNullOrBlank() || amount != null) {
            val matched = all.filter { t ->
                if (!month.isNullOrBlank() && t.competenceMonth != month) return@filter false
                if (amount != null && kotlin.math.abs(kotlin.math.abs(t.effectiveAmount) - kotlin.math.abs(amount)) > 0.05) return@filter false
                if (!searchTerm.isNullOrBlank() && !matchesSearch(t.description, searchTerm)) return@filter false
                true
            }.maxByOrNull { it.createdAt }
            if (matched != null) listOf(matched) else emptyList()
        } else emptyList()

        if (targets.isEmpty()) {
            return ToolExecutionResult(
                text = "⚠️ Não encontrei nenhum lançamento com os dados informados (\"${searchTerm ?: transactionId ?: ""}\") para excluir."
            )
        }

        val primaryTarget = targets.first()
        val isGroupDelete = deleteAllInstallments && !primaryTarget.installmentGroupId.isNullOrBlank()
        val finalTargets = if (isGroupDelete) {
            all.filter { it.installmentGroupId == primaryTarget.installmentGroupId }
        } else {
            targets
        }

        val deletedCount = finalTargets.size
        val totalDeletedVal = finalTargets.sumOf { it.effectiveAmount }
        val desc = primaryTarget.description

        // SE AINDA NÃO CONFIRMADO PELO USUÁRIO: Gera rascunho com pedido de confirmação interativo
        if (!confirmed) {
            val before = repository.getMonthSummary().first()
            val myImpactTotal = finalTargets.sumOf { it.userImpact[user.uid] ?: it.effectiveAmount }
            val isIncome = primaryTarget.type == TransactionType.INCOME
            val netDiff = if (isIncome) -myImpactTotal else myImpactTotal
            val estimatedAfter = FinanceEngine.round2(before.freeBalance + netDiff)

            val confirmMessage = if (deletedCount > 1) {
                "Entendi que você deseja excluir o parcelamento completo de **\"$desc\"** ($deletedCount parcelas no total de **${FinanceEngine.formatCurrency(totalDeletedVal)}**).\n\n" +
                        "• **Item a remover**: $desc ($deletedCount parcelas)\n" +
                        "• **Valor total a estornar**: ${FinanceEngine.formatCurrency(totalDeletedVal)}\n" +
                        "• **Impacto no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" +
                        "⚠️ Esta ação é irreversível. Por favor, confirme no botão abaixo para excluir definitivamente."
            } else {
                "Entendi que você deseja excluir o lançamento **\"$desc\"** no valor de **${FinanceEngine.formatCurrency(totalDeletedVal)}** referente a **${FinanceEngine.monthLabel(primaryTarget.competenceMonth)}**.\n\n" +
                        "• **Item a remover**: $desc\n" +
                        "• **Valor a estornar**: ${FinanceEngine.formatCurrency(totalDeletedVal)}\n" +
                        "• **Competência**: ${FinanceEngine.monthLabel(primaryTarget.competenceMonth)}\n" +
                        "• **Impacto no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" +
                        "⚠️ Esta ação removerá o lançamento do seu histórico. Por favor, confirme no botão abaixo para excluir definitivamente."
            }

            val actionTitle = if (deletedCount > 1) "Confirmar Exclusão ($deletedCount parcelas)" else "Confirmar Exclusão: $desc"
            val actionDetail = if (deletedCount > 1) "$deletedCount parcelas serão removidas" else "Remover de ${FinanceEngine.monthLabel(primaryTarget.competenceMonth)}"

            val pendingAction = PendingAction(
                id = "act_" + UUID.randomUUID().toString().take(8),
                toolName = "DELETE_TRANSACTION",
                title = actionTitle,
                description = "$desc — ${FinanceEngine.formatCurrency(totalDeletedVal)}",
                detail = actionDetail,
                args = mapOf(
                    "transactionId" to primaryTarget.id,
                    "targetIds" to finalTargets.map { it.id },
                    "searchTerm" to searchTerm,
                    "amount" to amount,
                    "month" to month,
                    "deleteAllInstallments" to deleteAllInstallments,
                    "confirmed" to true
                ),
                status = PendingActionStatus.PENDING
            )

            return ToolExecutionResult(
                text = confirmMessage,
                actionCard = ActionCardData(
                    title = actionTitle,
                    description = "$desc — ${FinanceEngine.formatCurrency(totalDeletedVal)}",
                    detail = actionDetail,
                    newFreeBalance = "Saldo Livre previsto: ${FinanceEngine.formatCurrency(before.freeBalance)} → ${FinanceEngine.formatCurrency(estimatedAfter)}",
                    pendingAction = pendingAction
                )
            )
        }

        if (isGroupDelete) {
            repository.deleteInstallmentGroup(primaryTarget.installmentGroupId!!)
        } else {
            for (t in finalTargets) {
                repository.deleteTransaction(t.id)
            }
        }

        repository.logActivity(
            ActivityLog(
                id = "act_" + UUID.randomUUID().toString().take(8),
                actionType = ActivityActionType.TRANSACTION_DELETED,
                title = "Lançamento Excluído",
                description = "Removeu: $desc (${FinanceEngine.formatCurrency(totalDeletedVal)})",
                amount = totalDeletedVal,
                competenceMonth = primaryTarget.competenceMonth,
                userUid = user.uid,
                userName = user.displayName
            )
        )

        val summaryNow = repository.getMonthSummary().first()

        return ToolExecutionResult(
            text = "🗑️ **Lançamento excluído com sucesso!**\n\n" +
                    "• **Item removido**: $desc\n" +
                    "• **Valor estornado**: ${FinanceEngine.formatCurrency(totalDeletedVal)}" + (if (deletedCount > 1) " ($deletedCount parcelas excluídas)" else "") + "\n\n" +
                    "O seu **Saldo Livre** foi liberado e recalculado.",
            actionCard = ActionCardData(
                title = "Lançamento Excluído",
                description = "$desc — ${FinanceEngine.formatCurrency(totalDeletedVal)}",
                detail = if (deletedCount > 1) "$deletedCount parcelas removidas" else "Removido do histórico",
                newFreeBalance = "Saldo Livre Atual: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
            )
        )
    }

    private suspend fun executeDeleteFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val searchTerm = jsonObj.getStringOrNull("searchTerm") ?: jsonObj.getStringOrNull("termoBusca")
        val transactionId = jsonObj.getStringOrNull("transactionId") ?: jsonObj.getStringOrNull("id")
        val targetIdsArray = jsonObj.getAsJsonArrayOrNull("targetIds") ?: jsonObj.getAsJsonArrayOrNull("ids")
        val targetIds = targetIdsArray?.mapNotNull { it.asString }
        val amount = jsonObj.getDoubleOrNull("amount") ?: jsonObj.getDoubleOrNull("valor")
        val month = jsonObj.getStringOrNull("month") ?: jsonObj.getStringOrNull("mes")
        val deleteAll = jsonObj.getBooleanOrNull("deleteAllInstallments") ?: false
        val confirmed = jsonObj.getBooleanOrNull("confirmed")
            ?: jsonObj.getBooleanOrNull("__confirmed")
            ?: false

        return executeDeleteTransaction(
            searchTerm = searchTerm,
            transactionId = transactionId,
            targetIds = targetIds,
            amount = amount,
            month = month,
            deleteAllInstallments = deleteAll,
            confirmed = confirmed
        )
    }

    // -------------------------------------------------------------
    // 5. UPDATE ESTIMATED BILL (CONTA PREVISTA -> REAL)
    // -------------------------------------------------------------

    suspend fun executeUpdateEstimated(
        searchTerm: String,
        actualAmount: Double,
        status: TransactionStatus = TransactionStatus.PAID,
        month: String? = null
    ): ToolExecutionResult {
        if (actualAmount <= 0.0) {
            return ToolExecutionResult(text = "⚠️ Informe o valor real da conta.")
        }

        val all = repository.getTransactions().first()
        val user = repository.currentUser.value
        val activeMonth = month ?: repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }

        val target = all.filter { t ->
            t.competenceMonth == activeMonth && matchesSearch(t.description, searchTerm)
        }.minByOrNull { it.status == TransactionStatus.ESTIMATED } ?: all.find { matchesSearch(it.description, searchTerm) }

        if (target == null) {
            return ToolExecutionResult(
                text = "⚠️ Não encontrei nenhuma conta prevista com o nome \"$searchTerm\" no mês de ${FinanceEngine.monthLabel(activeMonth)}."
            )
        }

        val oldAmount = target.effectiveAmount
        repository.updateEstimatedWithActual(target.id, actualAmount)
        if (status != TransactionStatus.ESTIMATED) {
            repository.setTransactionStatus(target.id, status, actualAmount)
        }

        val diff = oldAmount - actualAmount

        repository.logActivity(
            ActivityLog(
                id = "act_" + UUID.randomUUID().toString().take(8),
                actionType = ActivityActionType.ESTIMATED_UPDATED,
                title = "Conta Prevista Atualizada",
                description = "${target.description}: ${FinanceEngine.formatCurrency(oldAmount)} → ${FinanceEngine.formatCurrency(actualAmount)} (${status.label})",
                amount = actualAmount,
                competenceMonth = target.competenceMonth,
                userUid = user.uid,
                userName = user.displayName
            )
        )

        val summaryNow = repository.getMonthSummary().first()
        val diffNote = if (diff > 0) {
            "Economia de **${FinanceEngine.formatCurrency(diff)}** em relação à previsão anterior!"
        } else if (diff < 0) {
            "Aumento de **${FinanceEngine.formatCurrency(kotlin.math.abs(diff))}** em relação à previsão anterior."
        } else {
            "Valor exatamente igual à previsão."
        }

        return ToolExecutionResult(
            text = "⚡ **Conta atualizada com sucesso!**\n\n" +
                    "• **Conta**: ${target.description}\n" +
                    "• **Valor Real**: ${FinanceEngine.formatCurrency(actualAmount)} (Previsão anterior: ${FinanceEngine.formatCurrency(oldAmount)})\n" +
                    "• **Status**: ${status.label}\n" +
                    "• $diffNote\n\n" +
                    "Seu **Saldo Livre** foi ajustado com precisão no banco de dados.",
            actionCard = ActionCardData(
                title = "Conta Prevista Atualizada",
                description = "${target.description} — ${FinanceEngine.formatCurrency(actualAmount)}",
                detail = "Status: ${status.label} • De ${FinanceEngine.formatCurrency(oldAmount)} para ${FinanceEngine.formatCurrency(actualAmount)}",
                newFreeBalance = "Saldo Livre Atual: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
            )
        )
    }

    private suspend fun executeUpdateEstimatedFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val searchTerm = jsonObj.getStringOrNull("searchTerm") ?: jsonObj.getStringOrNull("buscaDescricao") ?: ""
        val actualAmount = jsonObj.getDoubleOrNull("actualAmount") ?: jsonObj.getDoubleOrNull("valorReal") ?: 0.0
        val statusStr = jsonObj.getStringOrNull("status") ?: jsonObj.getStringOrNull("novoStatus") ?: "paid"
        val status = TransactionStatus.from(statusStr)

        return executeUpdateEstimated(
            searchTerm = searchTerm,
            actualAmount = actualAmount,
            status = status
        )
    }

    // -------------------------------------------------------------
    // 6. QUERY TRANSACTIONS (CONSULTA INTELIGENTE / FILTRO)
    // -------------------------------------------------------------

    fun parseMonthKey(raw: String?, defaultMonth: String = FinanceEngine.currentMonthKey()): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().lowercase()
        if (Regex("""^\d{4}-\d{2}$""").matches(trimmed)) return trimmed
        if (trimmed == "all" || trimmed == "todos" || trimmed == "ano" || trimmed == "tudo") return "all"

        val yearMatch = Regex("""\b(20\d{2})\b""").find(trimmed)
        val year = yearMatch?.value ?: defaultMonth.take(4)

        val monthMap = mapOf(
            "janeiro" to "01", "jan" to "01",
            "fevereiro" to "02", "fev" to "02",
            "marco" to "03", "março" to "03", "mar" to "03",
            "abril" to "04", "abr" to "04",
            "maio" to "05", "mai" to "05",
            "junho" to "06", "jun" to "06",
            "julho" to "07", "jul" to "07",
            "agosto" to "08", "ago" to "08",
            "setembro" to "09", "set" to "09",
            "outubro" to "10", "out" to "10",
            "novembro" to "11", "nov" to "11",
            "dezembro" to "12", "dez" to "12"
        )

        for ((name, mm) in monthMap) {
            if (trimmed.contains(name)) {
                return "$year-$mm"
            }
        }

        val numMatch = Regex("""^([1-9]|1[0-2])$""").matchEntire(trimmed)
        if (numMatch != null) {
            val mm = numMatch.value.padStart(2, '0')
            return "$year-$mm"
        }

        return null
    }

    suspend fun executeSearchTransactions(
        searchTerm: String? = null,
        category: String? = null,
        subcategory: String? = null,
        month: String? = null,
        status: TransactionStatus? = null,
        onlyMine: Boolean = false,
        allMyExpenses: Boolean = false,
        person: String? = null,
        onlyThirdParties: Boolean = false,
        cardFilter: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        type: TransactionType? = null,
        paymentMethodFilter: String? = null,
        includeProjectedRecurring: Boolean = false
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val user = repository.currentUser.value
        val defaultMonth = repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
        val parsedMonth = parseMonthKey(month, defaultMonth)
        val isAllMonths = parsedMonth == "all" || (!searchTerm.isNullOrBlank() && month.isNullOrBlank())
        val activeMonth = if (isAllMonths) null else (parsedMonth ?: defaultMonth)
        val activeMonthLabel = if (activeMonth.isNullOrBlank()) "Todos os meses" else FinanceEngine.monthLabel(activeMonth)
        val recurringBills = repository.getRecurringBills().first()
        val cards = repository.getCards().first()
        val cardsMap = cards.associateBy { it.id }

        // Identificação de Cartão de Crédito cadastrado se filtro fornecido
        val matchedCard = if (!cardFilter.isNullOrBlank()) {
            cards.firstOrNull {
                it.cardName.contains(cardFilter, ignoreCase = true) || it.id.equals(cardFilter, ignoreCase = true)
            }
        } else null

        // 1. Filtra prioritariamente no mês vigente / selecionado (ou intervalo de datas se informado)
        val monthTransactions = if (!activeMonth.isNullOrBlank()) {
            all.filter { it.competenceMonth == activeMonth }
        } else {
            all
        }

        var filtered = monthTransactions.filter { t ->
            // Tipo (despesa vs receita)
            if (type != null) {
                if (t.type != type) return@filter false
            } else if (onlyMine || !person.isNullOrBlank() || onlyThirdParties || !cardFilter.isNullOrBlank() || !category.isNullOrBlank()) {
                // Em análises de despesas, filtra compras/saídas a menos que categoria seja Receita
                if (t.type != TransactionType.EXPENSE && !CategoryRegistry.INCOME_CATEGORIES.any { it.name.equals(t.category, ignoreCase = true) } && !t.category.equals("Receita", ignoreCase = true)) return@filter false
            }

            // Intervalo de datas
            if (!startDate.isNullOrBlank() && t.dueDate < startDate) return@filter false
            if (!endDate.isNullOrBlank() && t.dueDate > endDate) return@filter false

            // Método de pagamento (Fatura Cartão vs Extrato/Pix/Débito/Boleto)
            if (!paymentMethodFilter.isNullOrBlank()) {
                val pmNorm = paymentMethodFilter.trim().lowercase(Locale.ROOT)
                val isTxCard = t.paymentMethod == PaymentMethod.CREDIT_CARD || !t.cardId.isNullOrBlank()
                when {
                    pmNorm == "extrato_conta" || pmNorm == "extrato" || pmNorm == "conta" -> {
                        if (isTxCard) return@filter false
                    }
                    pmNorm == "credit_card" || pmNorm == "cartao" || pmNorm == "cartão" || pmNorm == "fatura" -> {
                        if (!isTxCard) return@filter false
                    }
                    else -> {
                        val expectedMethod = PaymentMethod.from(pmNorm)
                        if (t.paymentMethod != expectedMethod) return@filter false
                    }
                }
            }

            // Cartão de crédito
            if (matchedCard != null) {
                if (t.cardId != matchedCard.id && !matchesSearch(t.description, matchedCard.cardName)) return@filter false
            } else if (!cardFilter.isNullOrBlank()) {
                if (!matchesSearch(t.description, cardFilter)) return@filter false
            }

            // Categoria e subcategoria
            if (!category.isNullOrBlank() && !t.category.equals(category, ignoreCase = true) && !matchesSearch(t.category, category)) return@filter false
            if (!subcategory.isNullOrBlank() && !(t.subcategory?.contains(subcategory, ignoreCase = true) == true)) return@filter false
            if (status != null && t.status != status) return@filter false
            if (!searchTerm.isNullOrBlank() && !matchesSearch(t.description, searchTerm) && !matchesSearch(t.category, searchTerm)) return@filter false

            // FILTRO: TODOS OS MEUS GASTOS (100% MEUS + RATEADOS COM MINHA PARTICIPAÇÃO)
            if (allMyExpenses) {
                if (!FinanceEngine.isMyExpense(t, user.uid, all)) return@filter false
            }

            // FILTRO CRÍTICO: SOMENTE 100% MEU (SEM DIVISÕES NEM TERCEIROS)
            if (onlyMine) {
                if (t.category.equals("Cartão Terceiros", ignoreCase = true)) return@filter false
                if (t.splitType != null && t.splitType != SplitType.MINE) return@filter false
                if (!t.assignedFriendUid.isNullOrBlank() || !t.assignedFriendName.isNullOrBlank()) return@filter false
                if (t.allocations.any { it.uid != user.uid && it.amount > 0 }) return@filter false
                val myImp = FinanceEngine.impactFor(t, user.uid, all)
                if (kotlin.math.abs(myImp - t.totalAmount) > 0.01) return@filter false
            }

            // FILTRO CRÍTICO: PESSOA ESPECÍFICA (TERCEIRO X OU Y / AMIGO)
            if (!person.isNullOrBlank() && !onlyThirdParties) {
                val matchesFriend = t.assignedFriendName?.contains(person, ignoreCase = true) == true
                val matchesAlloc = t.allocations.any { it.name.contains(person, ignoreCase = true) }
                val matchesDesc = matchesSearch(t.description, person)
                val isThirdCat = t.category.equals("Cartão Terceiros", ignoreCase = true) && matchesSearch(t.description, person)

                if (!matchesFriend && !matchesAlloc && !matchesDesc && !isThirdCat) {
                    return@filter false
                }
            }

            // FILTRO CRÍTICO: APENAS TERCEIROS / DIVIDIDOS (NÃO 100% MEU)
            if (onlyThirdParties) {
                val isThirdCat = t.category.equals("Cartão Terceiros", ignoreCase = true)
                val isSharedSplit = t.splitType != null && t.splitType != SplitType.MINE
                val hasFriend = !t.assignedFriendUid.isNullOrBlank() || !t.assignedFriendName.isNullOrBlank()
                val hasOtherAlloc = t.allocations.any { it.uid != user.uid && it.amount > 0 }
                val myImp = FinanceEngine.impactFor(t, user.uid, all)
                val hasThirdShare = t.totalAmount - myImp > 0.01

                if (!isThirdCat && !isSharedSplit && !hasFriend && !hasOtherAlloc && !hasThirdShare) {
                    return@filter false
                }
            }

            true
        }.sortedByDescending { it.dueDate }

        // Verifica se há molde de Conta Recorrente correspondente ao termo
        val matchingRecurring = if (!searchTerm.isNullOrBlank()) {
            recurringBills.firstOrNull { matchesSearch(it.description, searchTerm) || (!category.isNullOrBlank() && matchesSearch(it.category, category)) }
        } else null

        // 2. Se não encontrou no mês ativo e o usuário não especificou um mês fixo nem filtros específicos, busca em outros meses para orientar
        var foundInOtherMonth = false
        if (filtered.isEmpty() && !searchTerm.isNullOrBlank() && month.isNullOrBlank() && !onlyMine && person.isNullOrBlank() && !onlyThirdParties && cardFilter.isNullOrBlank()) {
            val inOtherMonths = all.filter { t ->
                matchesSearch(t.description, searchTerm) || (!category.isNullOrBlank() && matchesSearch(t.category, category))
            }.sortedByDescending { it.dueDate }
            if (inOtherMonths.isNotEmpty()) {
                filtered = inOtherMonths.take(5)
                foundInOtherMonth = true
            }
        }

        if (filtered.isEmpty()) {
            val recurringNote = if (matchingRecurring != null) {
                "\n\n⚠️ **Nota sobre Conta Recorrente:** Encontrei o molde previsto de **\"${matchingRecurring.description}\"** cadastrado com valor estimado de **${FinanceEngine.formatCurrency(matchingRecurring.defaultAmount)}** (vence dia ${matchingRecurring.dueDay}), mas **NENHUM lançamento real foi efetivado ou registrado** no mês de $activeMonthLabel."
            } else ""

            var emptyFilterMsg = "os filtros informados"
            if (onlyMine) emptyFilterMsg = "despesas 100% pessoais do usuário"
            else if (!person.isNullOrBlank()) emptyFilterMsg = "despesas vinculadas a \"$person\""
            else if (onlyThirdParties) emptyFilterMsg = "despesas de terceiros ou divididas"
            else if (matchedCard != null) emptyFilterMsg = "o cartão ${matchedCard.cardName}"
            else if (!searchTerm.isNullOrBlank()) emptyFilterMsg = "\"$searchTerm\""

            return ToolExecutionResult(
                text = "🔍 **Busca de Transações Reais ($activeMonthLabel):**\n" +
                        "Nenhuma transação real encontrada no banco de dados para $emptyFilterMsg em $activeMonthLabel.$recurringNote",
                actionCard = ActionCardData(
                    title = "Nenhum lançamento real encontrado",
                    description = "Filtro: $emptyFilterMsg",
                    detail = if (matchingRecurring != null) "Molde previsto: ${FinanceEngine.formatCurrency(matchingRecurring.defaultAmount)} (sem lançamento real)" else "Mês: $activeMonthLabel"
                )
            )
        }

        // Separação matemática rigorosa entre Despesas, Estornos e Receitas
        val isRefund: (Transaction) -> Boolean = { t ->
            t.category.equals("Estorno", ignoreCase = true) ||
            t.subcategory?.contains("Estorno", ignoreCase = true) == true ||
            t.description.contains("Estorno", ignoreCase = true)
        }

        val isExpense: (Transaction) -> Boolean = { t ->
            t.type == TransactionType.EXPENSE && !isRefund(t)
        }

        val isIncome: (Transaction) -> Boolean = { t ->
            t.type == TransactionType.INCOME && !isRefund(t)
        }

        val expenseTxs = filtered.filter(isExpense)
        val refundTxs = filtered.filter(isRefund)
        val incomeTxs = filtered.filter(isIncome)

        // 1. Total Bruto de Despesas (apenas saídas)
        val totalExpensesGross = expenseTxs.sumOf { it.effectiveAmount }
        val totalRefunds = refundTxs.sumOf { kotlin.math.abs(it.effectiveAmount) }
        val netExpenses = kotlin.math.max(0.0, totalExpensesGross - totalRefunds)

        // 2.Seu Impacto Real nas Despesas (Sua Parte)
        val myExpensesGrossImpact = expenseTxs.sumOf { FinanceEngine.impactFor(it, user.uid, all) }
        val myRefundsImpact = refundTxs.sumOf { FinanceEngine.impactFor(it, user.uid, all) }
        val myImpactTotal = kotlin.math.max(0.0, myExpensesGrossImpact - myRefundsImpact)

        // Subtotais por Canal de Pagamento (Fatura de Cartão vs Extrato/Pix/Débito/Boleto)
        val cardExpenseTxs = expenseTxs.filter { it.paymentMethod == PaymentMethod.CREDIT_CARD || !it.cardId.isNullOrBlank() }
        val accountExpenseTxs = expenseTxs.filter { it.paymentMethod != PaymentMethod.CREDIT_CARD && it.cardId.isNullOrBlank() }
        val cardImpactTotal = cardExpenseTxs.sumOf { FinanceEngine.impactFor(it, user.uid, all) }
        val accountImpactTotal = accountExpenseTxs.sumOf { FinanceEngine.impactFor(it, user.uid, all) }

        // Contas recorrentes ativas que ainda não foram lançadas no mês consultado
        val unlaunchedRecurringLines = if (!activeMonth.isNullOrBlank() && (includeProjectedRecurring || activeMonth >= FinanceEngine.currentMonthKey())) {
            val unlaunched = recurringBills.filter { bill ->
                if (!bill.active) return@filter false
                val startM = bill.startMonth ?: activeMonth
                if (activeMonth < startM) return@filter false
                if (!bill.endMonth.isNullOrBlank() && activeMonth > bill.endMonth) return@filter false
                if (bill.installmentsCount != null && bill.installmentsCount > 0 && !bill.startMonth.isNullOrBlank()) {
                    val endM = FinanceEngine.shiftMonth(bill.startMonth, bill.installmentsCount - 1)
                    if (activeMonth > endM) return@filter false
                }
                val alreadyInDb = monthTransactions.any {
                    it.recurringBillId == bill.id
                } || FinanceEngine.findMatchingManualBill(monthTransactions, activeMonth, bill) != null
                if (alreadyInDb) return@filter false
                if (type != null && bill.type != type) return@filter false
                if (!category.isNullOrBlank() && !bill.category.equals(category, ignoreCase = true) && !matchesSearch(bill.category, category)) return@filter false
                if (!searchTerm.isNullOrBlank() && !matchesSearch(bill.description, searchTerm) && !matchesSearch(bill.category, searchTerm)) return@filter false
                if (!paymentMethodFilter.isNullOrBlank()) {
                    val pmNorm = paymentMethodFilter.trim().lowercase(Locale.ROOT)
                    val isBillCard = bill.paymentMethod == PaymentMethod.CREDIT_CARD || !bill.cardId.isNullOrBlank()
                    when {
                        pmNorm == "extrato_conta" || pmNorm == "extrato" || pmNorm == "conta" -> if (isBillCard) return@filter false
                        pmNorm == "credit_card" || pmNorm == "cartao" || pmNorm == "cartão" || pmNorm == "fatura" -> if (!isBillCard) return@filter false
                        else -> if (bill.paymentMethod != PaymentMethod.from(pmNorm)) return@filter false
                    }
                }
                true
            }
            if (unlaunched.isNotEmpty()) {
                val recLines = unlaunched.joinToString("\n") { b ->
                    val est = FinanceEngine.estimateRecurring(b, all, activeMonth).amount
                    val myShare = if (b.splitType == SplitType.SPLIT_50) est * 0.5 else if (b.splitType == SplitType.PARTNER) 0.0 else est
                    val channelLabel = if (b.paymentMethod == PaymentMethod.CREDIT_CARD || !b.cardId.isNullOrBlank()) {
                        val cn = b.cardId?.let { cardsMap[it]?.cardName } ?: "Cartão"
                        "💳 Assinatura Cartão $cn"
                    } else "🏠 Conta Fixa (${b.paymentMethod?.label ?: "Boleto/Pix"})"
                    "  * **${b.description}**: ${FinanceEngine.formatCurrency(myShare)} (Vence dia ${b.dueDay}) • ${b.category} • $channelLabel [Prevista/Não lançada no banco]"
                }
                "\n\n🔄 **Contas Fixas / Recorrentes Previstas para $activeMonthLabel (${unlaunched.size} item(ns) ainda não lançados no banco):**\n$recLines"
            } else ""
        } else ""

        // 3. Cota de Terceiros (A Receber)
        val thirdPartyTotal = kotlin.math.max(0.0, totalExpensesGross - myExpensesGrossImpact)

        // 4. Entradas / Receitas do Período (separadas para nunca distorcerem a soma das despesas)
        val totalIncome = incomeTxs.sumOf { FinanceEngine.impactFor(it, user.uid, all) }

        // Agrupamento por subcategoria calculado exclusivamente sobre as despesas para totalizar fielmente
        val subcategoryGroups = expenseTxs.groupBy { it.subcategory ?: it.category.ifBlank { "Geral" } }
        val subcategoryBreakdownText = if (subcategoryGroups.size > 1) {
            val breakdownLines = subcategoryGroups.entries
                .sortedByDescending { it.value.sumOf { tx -> tx.effectiveAmount } }
                .take(6)
                .joinToString("\n") { (sub, txs) ->
                    val subTotal = txs.sumOf { it.effectiveAmount }
                    val subMyPart = txs.sumOf { FinanceEngine.impactFor(it, user.uid, all) }
                    val subPartNote = if (kotlin.math.abs(subTotal - subMyPart) > 0.01) {
                        " (Sua parte: ${FinanceEngine.formatCurrency(subMyPart)})"
                    } else ""
                    "  * **$sub**: ${FinanceEngine.formatCurrency(subTotal)}$subPartNote (${txs.size} item(ns))"
                }
            "\n\n📊 **Distribuição das Despesas por Subcategoria:**\n$breakdownLines"
        } else ""

        val lines = filtered.take(15).joinToString("\n") { t ->
            val sub = if (!t.subcategory.isNullOrBlank()) " › ${t.subcategory}" else ""
            val cName = t.cardId?.let { cid -> cardsMap[cid]?.cardName }
            val methodOrCard = if (!cName.isNullOrBlank()) "Cartão $cName" else t.paymentMethod.label
            val monthNote = if (foundInOtherMonth) " [Mês: ${FinanceEngine.monthLabel(t.competenceMonth)}]" else ""

            val myImpact = FinanceEngine.impactFor(t, user.uid, all)
            val isSplit = kotlin.math.abs(myImpact - t.effectiveAmount) > 0.01 || (t.splitType != null && t.splitType != SplitType.MINE)
            val splitNote = if (isSplit) {
                val friendName = t.assignedFriendName?.takeIf { it.isNotBlank() }
                    ?: t.allocations.firstOrNull { it.uid != user.uid && it.name.isNotBlank() }?.name
                val friendPct = t.friendSharePct?.toInt() ?: 50
                val who = when {
                    !friendName.isNullOrBlank() -> "Dividido com $friendName ($friendPct%)"
                    t.splitType == SplitType.SPLIT_50 -> "Dividido 50/50 (Casal)"
                    t.splitType == SplitType.FRIEND -> "Dividido com Amigo(a)"
                    t.splitType == SplitType.PARTNER -> "100% Parceiro(a)"
                    t.splitType == SplitType.EXTERNAL -> "Dividido com Terceiro"
                    else -> "Compra Dividida"
                }
                " (Sua parte: ${FinanceEngine.formatCurrency(myImpact)} • $who)"
            } else ""

            val typeBadge = if (t.type == TransactionType.INCOME) "🟢 Entrada" else "🔴 Despesa"
            "• [ID: ${t.id}] **${t.description}**: ${FinanceEngine.formatCurrency(t.effectiveAmount)}$splitNote • $typeBadge • ${t.category}$sub • ${t.status.label} (${FinanceEngine.formatShortDate(t.dueDate)}) • $methodOrCard$monthNote"
        }

        val extraNote = if (filtered.size > 15) "\n*(e mais ${filtered.size - 15} lançamentos)*" else ""

        val recurringDivergenceNote = if (matchingRecurring != null && !foundInOtherMonth && expenseTxs.isNotEmpty()) {
            val diff = totalExpensesGross - matchingRecurring.defaultAmount
            if (kotlin.math.abs(diff) > 0.01) {
                "\n\nℹ️ **Divergência com Molde Recorrente:** O molde de previsão cadastrado previa **${FinanceEngine.formatCurrency(matchingRecurring.defaultAmount)}**, mas o **valor real registrado no mês é ${FinanceEngine.formatCurrency(totalExpensesGross)}**."
            } else {
                "\n\nℹ️ **Conta Recorrente:** O valor real lançado confere exatamente com o molde previsto (${FinanceEngine.formatCurrency(matchingRecurring.defaultAmount)})."
            }
        } else ""

        val displayMonth = if (foundInOtherMonth) filtered.first().competenceMonth else (activeMonth ?: "Ano")
        val monthLabelText = if (displayMonth == "Ano") "Todos os meses / Ano" else FinanceEngine.monthLabel(displayMonth)

        val headerText = when {
            foundInOtherMonth ->
                "🔍 **Lançamentos reais encontrados em outros meses (nenhum em $monthLabelText):**"
            onlyMine ->
                "👤 **Despesas 100% Pessoais (Suas Despesas Exclusivas)** em $monthLabelText (${expenseTxs.size} item(ns)):"
            !person.isNullOrBlank() ->
                "👥 **Despesas Vinculadas a $person** em $monthLabelText (${filtered.size} item(ns)):"
            onlyThirdParties ->
                "👥 **Despesas de Terceiros e Divisões no Cartão** em $monthLabelText (${filtered.size} item(ns)):"
            matchedCard != null ->
                "💳 **Fatura do Cartão ${matchedCard.cardName}** (Titular: ${matchedCard.ownerName}, Fecha dia ${matchedCard.closingDay}, Vence dia ${matchedCard.dueDay}) em $monthLabelText (${filtered.size} compra(s)):"
            !category.isNullOrBlank() ->
                "🏷️ **Despesas na Categoria $category**${if (!subcategory.isNullOrBlank()) " › $subcategory" else ""} em $monthLabelText (${filtered.size} item(ns)):"
            !startDate.isNullOrBlank() || !endDate.isNullOrBlank() ->
                "📅 **Despesas no Período [${startDate ?: "início"} a ${endDate ?: "hoje"}]** (${filtered.size} item(ns)):"
            !searchTerm.isNullOrBlank() ->
                "🔍 **Lançamentos encontrados para \"$searchTerm\" em $monthLabelText (${filtered.size} item(ns)):**"
            else ->
                "📋 **Lançamentos reais encontrados em $monthLabelText (${filtered.size} item(ns)):**"
        }

        var cardTitle = "Busca: ${filtered.size} lançamento(s)"
        if (onlyMine) cardTitle = "100% Pessoais: ${expenseTxs.size} item(ns)"
        else if (!person.isNullOrBlank()) cardTitle = "Despesas de $person (${filtered.size})"
        else if (onlyThirdParties) cardTitle = "Terceiros: ${filtered.size} item(ns)"
        else if (matchedCard != null) cardTitle = "Cartão ${matchedCard.cardName} (${filtered.size})"
        else if (!searchTerm.isNullOrBlank()) cardTitle = "$searchTerm (${filtered.size})"
        else if (expenseTxs.isNotEmpty() && incomeTxs.isEmpty()) cardTitle = "Despesas: ${expenseTxs.size} item(ns)"

        val linesList = mutableListOf<ActionCardLine>()
        linesList.add(ActionCardLine("Total Encontrado", "${filtered.size} lançamento(s)"))
        searchTerm?.takeIf { it.isNotBlank() }?.let { linesList.add(ActionCardLine("Termo de Busca", it)) }

        if (expenseTxs.isNotEmpty()) {
            linesList.add(ActionCardLine("Total Bruto de Despesas", FinanceEngine.formatCurrency(totalExpensesGross)))
            linesList.add(ActionCardLine("Seu Impacto (Sua Parte)", FinanceEngine.formatCurrency(myImpactTotal)))
            if (thirdPartyTotal > 0.01) {
                linesList.add(ActionCardLine("Cota de Terceiros (A Receber)", FinanceEngine.formatCurrency(thirdPartyTotal)))
            }
            if (totalRefunds > 0.01) {
                linesList.add(ActionCardLine("Estornos Abatidos", "-${FinanceEngine.formatCurrency(totalRefunds)}"))
                linesList.add(ActionCardLine("Despesas Líquidas", FinanceEngine.formatCurrency(netExpenses)))
            }
        }
        if (incomeTxs.isNotEmpty()) {
            linesList.add(ActionCardLine("Entradas / Receitas", FinanceEngine.formatCurrency(totalIncome)))
        }
        linesList.add(ActionCardLine("Mês / Período", monthLabelText))

        val cardDesc = when {
            expenseTxs.isNotEmpty() && incomeTxs.isNotEmpty() ->
                "Despesas: ${FinanceEngine.formatCurrency(totalExpensesGross)} • Receitas: ${FinanceEngine.formatCurrency(totalIncome)}"
            expenseTxs.isNotEmpty() ->
                "Total Bruto: ${FinanceEngine.formatCurrency(totalExpensesGross)}"
            else ->
                "Total Receitas: ${FinanceEngine.formatCurrency(totalIncome)}"
        }

        val cardDetail = when {
            expenseTxs.isNotEmpty() ->
                "Sua Parte: ${FinanceEngine.formatCurrency(myImpactTotal)} • Mês: $monthLabelText"
            else ->
                "Mês: $monthLabelText"
        }

        val finalCard = ActionCardData(
            title = cardTitle,
            description = cardDesc,
            detail = cardDetail,
            lines = linesList
        )

        val summaryMetricsBuilder = StringBuilder()
        if (expenseTxs.isNotEmpty()) {
            summaryMetricsBuilder.append("• **Total Bruto de Despesas**: ${FinanceEngine.formatCurrency(totalExpensesGross)}\n")
            summaryMetricsBuilder.append("• **Seu Impacto Real nas Despesas (Sua Parte)**: ${FinanceEngine.formatCurrency(myImpactTotal)}\n")
            if (cardExpenseTxs.isNotEmpty() || accountExpenseTxs.isNotEmpty()) {
                summaryMetricsBuilder.append("  ↳ 💳 **No Cartão (Fatura)**: ${FinanceEngine.formatCurrency(cardImpactTotal)} (${cardExpenseTxs.size} item(ns)) | ⚡ **No Extrato (Pix/Débito/Boleto)**: ${FinanceEngine.formatCurrency(accountImpactTotal)} (${accountExpenseTxs.size} item(ns))\n")
            }
            if (thirdPartyTotal > 0.01) {
                summaryMetricsBuilder.append("• **Cota de Terceiros (A Receber)**: ${FinanceEngine.formatCurrency(thirdPartyTotal)}\n")
            }
            if (totalRefunds > 0.01) {
                summaryMetricsBuilder.append("• **Estornos / Devoluções**: -${FinanceEngine.formatCurrency(totalRefunds)} (Despesas Líquidas: ${FinanceEngine.formatCurrency(netExpenses)})\n")
            }
        }
        if (incomeTxs.isNotEmpty()) {
            summaryMetricsBuilder.append("• **Entradas / Receitas do Período**: ${FinanceEngine.formatCurrency(totalIncome)} (${incomeTxs.size} item(ns) - contabilizadas separadamente de despesas)\n")
        }

        return ToolExecutionResult(
            text = "$headerText\n\n" +
                    "$lines$extraNote\n\n" +
                    summaryMetricsBuilder.toString().trimEnd() +
                    subcategoryBreakdownText +
                    unlaunchedRecurringLines +
                    recurringDivergenceNote,
            actionCard = finalCard,
            actions = listOf(finalCard)
        )
    }

    suspend fun executeQueryTransactions(
        searchTerm: String? = null,
        category: String? = null,
        subcategory: String? = null,
        month: String? = null,
        status: TransactionStatus? = null,
        onlyMine: Boolean = false,
        allMyExpenses: Boolean = false,
        person: String? = null,
        onlyThirdParties: Boolean = false,
        cardFilter: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        type: TransactionType? = null,
        paymentMethodFilter: String? = null,
        includeProjectedRecurring: Boolean = false
    ): ToolExecutionResult = executeSearchTransactions(
        searchTerm = searchTerm,
        category = category,
        subcategory = subcategory,
        month = month,
        status = status,
        onlyMine = onlyMine,
        allMyExpenses = allMyExpenses,
        person = person,
        onlyThirdParties = onlyThirdParties,
        cardFilter = cardFilter,
        startDate = startDate,
        endDate = endDate,
        type = type,
        paymentMethodFilter = paymentMethodFilter,
        includeProjectedRecurring = includeProjectedRecurring
    )

    private suspend fun executeSearchFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val searchTerm = jsonObj.getStringOrNull("searchTerm")
            ?: jsonObj.getStringOrNull("termoBusca")
            ?: jsonObj.getStringOrNull("description")
            ?: jsonObj.getStringOrNull("descricao")
            ?: jsonObj.getStringOrNull("query")
            ?: jsonObj.getStringOrNull("busca")
        val category = jsonObj.getStringOrNull("category")
            ?: jsonObj.getStringOrNull("categoria")
        val subcategory = jsonObj.getStringOrNull("subcategory")
            ?: jsonObj.getStringOrNull("subcategoria")
        val month = jsonObj.getStringOrNull("month")
            ?: jsonObj.getStringOrNull("mes")
            ?: jsonObj.getStringOrNull("competenceMonth")
        val statusStr = jsonObj.getStringOrNull("status")
        val status = statusStr?.let { TransactionStatus.from(it) }

        val onlyMine = jsonObj.getBooleanOrNull("onlyMine")
            ?: jsonObj.getBooleanOrNull("somenteMinhas")
            ?: jsonObj.getBooleanOrNull("apenasMinhas")
            ?: false

        val allMyExpenses = jsonObj.getBooleanOrNull("todosMeusGastos")
            ?: jsonObj.getBooleanOrNull("meusGastos")
            ?: false

        val person = jsonObj.getStringOrNull("person")
            ?: jsonObj.getStringOrNull("pessoa")
            ?: jsonObj.getStringOrNull("terceiro")
            ?: jsonObj.getStringOrNull("friendName")

        val onlyThirdParties = jsonObj.getBooleanOrNull("onlyThirdParties")
            ?: jsonObj.getBooleanOrNull("apenasTerceiros")
            ?: (person != null && (person.equals("terceiro", ignoreCase = true) || person.equals("terceiros", ignoreCase = true) || person.equals("amigos", ignoreCase = true)))

        val cardFilter = jsonObj.getStringOrNull("cardName")
            ?: jsonObj.getStringOrNull("cartao")
            ?: jsonObj.getStringOrNull("card")

        val startDate = jsonObj.getStringOrNull("startDate")
            ?: jsonObj.getStringOrNull("dataInicio")

        val endDate = jsonObj.getStringOrNull("endDate")
            ?: jsonObj.getStringOrNull("dataFim")

        val paymentMethodFilter = jsonObj.getStringOrNull("paymentMethod")
            ?: jsonObj.getStringOrNull("metodoPagamento")

        val includeProjectedRecurring = jsonObj.getBooleanOrNull("incluirRecorrentesProjetadas")
            ?: jsonObj.getBooleanOrNull("includeProjectedRecurring")
            ?: false

        val typeStr = jsonObj.getStringOrNull("type")
            ?: jsonObj.getStringOrNull("tipo")
        val type = when {
            typeStr?.equals("income", ignoreCase = true) == true || typeStr?.equals("receita", ignoreCase = true) == true -> TransactionType.INCOME
            typeStr?.equals("expense", ignoreCase = true) == true || typeStr?.equals("despesa", ignoreCase = true) == true -> TransactionType.EXPENSE
            else -> null
        }

        return executeSearchTransactions(
            searchTerm = searchTerm,
            category = category,
            subcategory = subcategory,
            month = month,
            status = status,
            onlyMine = onlyMine,
            allMyExpenses = allMyExpenses,
            person = person,
            onlyThirdParties = onlyThirdParties,
            cardFilter = cardFilter,
            startDate = startDate,
            endDate = endDate,
            type = type,
            paymentMethodFilter = paymentMethodFilter,
            includeProjectedRecurring = includeProjectedRecurring
        )
    }

    private suspend fun executeQueryFromJson(jsonObj: JsonObject): ToolExecutionResult =
        executeSearchFromJson(jsonObj)

    // -------------------------------------------------------------
    // 7. MANAGE CARDS (CARTÕES DE CRÉDITO)
    // -------------------------------------------------------------

    suspend fun executeManageCards(
        operation: String,
        cardName: String? = null,
        closingDay: Int? = null,
        dueDay: Int? = null,
        cardId: String? = null,
        month: String? = null
    ): ToolExecutionResult {
        val cards = repository.getCards().first()
        val user = repository.currentUser.value

        when (operation.lowercase()) {
            "listar" -> {
                if (cards.isEmpty()) {
                    return ToolExecutionResult(text = "💳 Você ainda não possui nenhum cartão de crédito cadastrado.")
                }
                val list = cards.joinToString("\n") {
                    "• **${it.cardName}** (Titular: ${it.ownerName}): Fecha dia ${it.closingDay}, Vence dia ${it.dueDay}"
                }
                return ToolExecutionResult(
                    text = "💳 **Seus Cartões de Crédito cadastrados:**\n\n$list",
                    actionCard = ActionCardData(title = "Cartões Listados", description = "${cards.size} cartões encontrados")
                )
            }
            "detalhar_fatura", "fatura" -> {
                if (cards.isEmpty()) {
                    return ToolExecutionResult(text = "💳 Você ainda não possui nenhum cartão de crédito cadastrado.")
                }
                val all = repository.getTransactions().first()
                val bills = repository.getRecurringBills().first()
                val defaultMonth = repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
                val targetMonth = parseMonthKey(month, defaultMonth) ?: defaultMonth
                val targetMonthLabel = FinanceEngine.formatMonthLabel(targetMonth)

                val targetCards = if (!cardId.isNullOrBlank() || !cardName.isNullOrBlank()) {
                    cards.filter {
                        it.id == cardId || (cardName != null && (it.cardName.contains(cardName, ignoreCase = true) || cardName.contains(it.cardName, ignoreCase = true)))
                    }
                } else cards

                if (targetCards.isEmpty()) {
                    return ToolExecutionResult(text = "⚠️ Cartão \"${cardName ?: cardId}\" não encontrado.")
                }

                val sections = targetCards.map { card ->
                    val inv = InvoiceEngine.buildInvoice(card, targetMonth, all, user.uid)
                    val unlaunchedCardBills = bills.filter { b ->
                        b.active && b.type == TransactionType.EXPENSE &&
                            (b.cardId == card.id || (b.paymentMethod == PaymentMethod.CREDIT_CARD && b.cardId == null && targetCards.size == 1)) &&
                            !inv.items.any { tx -> tx.recurringBillId == b.id || FinanceEngine.findMatchingManualBill(listOf(tx), targetMonth, b) != null }
                    }
                    val unlaunchedSum = unlaunchedCardBills.sumOf { FinanceEngine.estimateRecurring(it, all, targetMonth).amount }
                    val catText = if (inv.byCategory.isNotEmpty()) {
                        "\n  • **Categorias na Fatura**: " + inv.byCategory.joinToString(", ") { "${it.category}: ${FinanceEngine.formatCurrency(it.amount)} (${it.pct}%)" }
                    } else ""
                    val itemsLines = inv.items.take(15).joinToString("\n") { tx ->
                        val myImp = FinanceEngine.impactFor(tx, user.uid, all)
                        val splitNote = if (kotlin.math.abs(myImp - tx.effectiveAmount) > 0.01) " (Sua parte: ${FinanceEngine.formatCurrency(myImp)})" else ""
                        "    - **${tx.description}**: ${FinanceEngine.formatCurrency(tx.effectiveAmount)}$splitNote • ${tx.category}${if (!tx.subcategory.isNullOrBlank()) " › ${tx.subcategory}" else ""} • ${tx.status.label}"
                    }
                    val unlaunchedLines = if (unlaunchedCardBills.isNotEmpty()) {
                        "\n  • **Assinaturas Recorrentes Previstas no Cartão (${unlaunchedCardBills.size})**:\n" +
                            unlaunchedCardBills.joinToString("\n") { b ->
                                "    - **${b.description}**: ${FinanceEngine.formatCurrency(FinanceEngine.estimateRecurring(b, all, targetMonth).amount)} • ${b.category} (Projetada)"
                            }
                    } else ""

                    "💳 **Fatura ${card.cardName} — $targetMonthLabel**\n" +
                        "  • **Status**: ${inv.status.label} (Fecha: ${FinanceEngine.formatShortDate(inv.closingDate)} | Vence: ${FinanceEngine.formatShortDate(inv.dueDate)})\n" +
                        "  • **Total Lançado na Fatura**: ${FinanceEngine.formatCurrency(inv.total)} (Sua Parte: ${FinanceEngine.formatCurrency(inv.myShare)} | Terceiros: ${FinanceEngine.formatCurrency(inv.othersShare)})" +
                        (if (unlaunchedSum > 0) "\n  • **Total Projetado c/ Assinaturas**: ${FinanceEngine.formatCurrency(inv.total + unlaunchedSum)}" else "") +
                        catText +
                        (if (itemsLines.isNotBlank()) "\n  • **Compras Lançadas (${inv.items.size})**:\n$itemsLines" else "\n  • *Nenhuma compra lançada nesta fatura ainda.*") +
                        unlaunchedLines
                }

                return ToolExecutionResult(
                    text = sections.joinToString("\n\n"),
                    actionCard = ActionCardData(
                        title = "Fatura(s) — $targetMonthLabel",
                        description = "${targetCards.size} cartão(ões) detalhado(s)"
                    )
                )
            }
            "adicionar" -> {
                val name = cardName?.trim()
                if (name.isNullOrBlank()) {
                    return ToolExecutionResult(text = "⚠️ Informe o nome do cartão a cadastrar.")
                }
                val f = closingDay ?: 1
                val v = dueDay ?: 10
                val newCard = Card(
                    id = "card_" + UUID.randomUUID().toString().take(8),
                    ownerUid = user.uid,
                    ownerName = user.displayName,
                    cardName = name,
                    closingDay = f,
                    dueDay = v
                )
                repository.saveCard(newCard)
                return ToolExecutionResult(
                    text = "💳 **Cartão de crédito cadastrado com sucesso!**\n\n" +
                            "• **Nome**: $name\n" +
                            "• **Fechamento**: Dia $f\n" +
                            "• **Vencimento**: Dia $v\n\n" +
                            "A partir de agora, compras neste cartão calcularão automaticamente a fatura correta!",
                    actionCard = ActionCardData(
                        title = "Cartão Adicionado",
                        description = "$name (Fecha dia $f, Vence dia $v)",
                        detail = "Cadastrado no seu perfil"
                    )
                )
            }
            "excluir" -> {
                val target = cards.find { it.id == cardId || (cardName != null && it.cardName.contains(cardName, ignoreCase = true)) }
                if (target == null) {
                    return ToolExecutionResult(text = "⚠️ Cartão não encontrado para exclusão.")
                }
                repository.deleteCard(target.id)
                return ToolExecutionResult(
                    text = "🗑️ **Cartão \"${target.cardName}\" removido com sucesso!**",
                    actionCard = ActionCardData(title = "Cartão Excluído", description = target.cardName)
                )
            }
            else -> return ToolExecutionResult(text = "Operação de cartão desconhecida: $operation")
        }
    }

    private suspend fun executeManageCardsFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val op = jsonObj.getStringOrNull("operation") ?: jsonObj.getStringOrNull("acao") ?: "listar"
        val name = jsonObj.getStringOrNull("cardName") ?: jsonObj.getStringOrNull("nome") ?: jsonObj.getStringOrNull("cartao")
        val closing = jsonObj.getIntOrNull("closingDay") ?: jsonObj.getIntOrNull("fechamento")
        val due = jsonObj.getIntOrNull("dueDay") ?: jsonObj.getIntOrNull("vencimento")
        val id = jsonObj.getStringOrNull("cardId") ?: jsonObj.getStringOrNull("id")
        val month = jsonObj.getStringOrNull("month") ?: jsonObj.getStringOrNull("mes")

        return executeManageCards(operation = op, cardName = name, closingDay = closing, dueDay = due, cardId = id, month = month)
    }

    // -------------------------------------------------------------
    // 8. MANAGE RECURRING (ASSINATURAS / CONTAS FIXAS)
    // -------------------------------------------------------------

    suspend fun executeManageRecurring(
        operation: String,
        description: String? = null,
        amount: Double? = null,
        dueDay: Int? = null,
        category: String? = null,
        subcategory: String? = null,
        billId: String? = null,
        type: TransactionType? = null,
        paymentMethod: PaymentMethod? = null,
        cardName: String? = null,
        monthsLimit: Int? = null
    ): ToolExecutionResult {
        val bills = repository.getRecurringBills().first()
        val cards = repository.getCards().first()
        val cardsMap = cards.associateBy { it.id }
        val user = repository.currentUser.value

        when (operation.lowercase()) {
            "listar" -> {
                if (bills.isEmpty()) {
                    return ToolExecutionResult(text = "⚡ Você não possui nenhuma conta recorrente ou renda fixa cadastrada.")
                }
                val allTxs = repository.getTransactions().first()
                val curMonth = repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
                val monthTxs = allTxs.filter { it.competenceMonth == curMonth }

                val list = bills.joinToString("\n") { b ->
                    val status = if (b.active) "🟢 Ativa" else "⏸️ Pausada"
                    val typeTag = if (b.type == TransactionType.INCOME) "💰 Renda Fixa" else "🏠 Conta Fixa"
                    val cardLabel = b.cardId?.let { cardsMap[it]?.cardName }
                    val channel = if (b.paymentMethod == PaymentMethod.CREDIT_CARD || !cardLabel.isNullOrBlank()) {
                        "💳 Cartão ${cardLabel ?: ""}".trim()
                    } else "⚡ ${b.paymentMethod?.label ?: "Boleto/Pix"}"
                    val matchedTx = monthTxs.find { it.recurringBillId == b.id } ?: FinanceEngine.findMatchingManualBill(monthTxs, curMonth, b)
                    val monthBadge = when {
                        matchedTx?.status == TransactionStatus.PAID -> "✅ Já Pago/Recebido no mês"
                        matchedTx?.status == TransactionStatus.PENDING -> "💳 Na Fatura no mês"
                        matchedTx != null -> "⏳ Previsto no Extrato"
                        else -> "📅 A lançar no mês"
                    }
                    "• **${b.description}** ($typeTag): ${FinanceEngine.formatCurrency(b.defaultAmount)} (Dia ${b.dueDay}) • ${b.category} • $channel • [$status | $monthBadge]"
                }
                return ToolExecutionResult(
                    text = "⚡ **Suas Contas Fixas, Assinaturas e Rendas Recorrentes:**\n\n$list",
                    actionCard = ActionCardData(title = "Contas Fixas & Renda", description = "${bills.size} itens cadastrados")
                )
            }
            "adicionar" -> {
                val desc = description?.trim()
                val amt = amount ?: 0.0
                if (desc.isNullOrBlank() || amt <= 0.0) {
                    return ToolExecutionResult(text = "⚠️ Informe o nome e o valor da conta recorrente.")
                }
                val guessed = neuralClassifier.guessCategoryAndSubcategory(desc, amt)
                val matchedCard = if (!cardName.isNullOrBlank()) {
                    cards.find { it.cardName.contains(cardName, ignoreCase = true) || cardName.contains(it.cardName, ignoreCase = true) }
                } else null
                val effectiveMethod = when {
                    matchedCard != null -> PaymentMethod.CREDIT_CARD
                    paymentMethod != null -> paymentMethod
                    else -> PaymentMethod.BILL
                }
                val newBill = RecurringBill(
                    id = "rec_" + UUID.randomUUID().toString().take(8),
                    ownerUid = user.uid,
                    description = desc,
                    defaultAmount = amt,
                    dueDay = dueDay ?: matchedCard?.dueDay ?: 10,
                    category = category ?: guessed.category,
                    subcategory = subcategory ?: guessed.subcategory,
                    type = type ?: TransactionType.EXPENSE,
                    paymentMethod = effectiveMethod,
                    cardId = matchedCard?.id,
                    startMonth = FinanceEngine.currentMonthKey(),
                    installmentsCount = monthsLimit,
                    active = true
                )
                repository.saveRecurringBill(newBill)
                return ToolExecutionResult(
                    text = "⚡ **Conta recorrente adicionada com sucesso!**\n\n" +
                            "• **Descrição**: $desc\n" +
                            "• **Valor Mensal**: ${FinanceEngine.formatCurrency(amt)}\n" +
                            "• **Vencimento**: Todo dia ${newBill.dueDay}\n" +
                            "• **Canal**: ${if (matchedCard != null) "Cartão ${matchedCard.cardName}" else effectiveMethod.label}\n" +
                            "• **Categoria**: ${newBill.category}\n\n" +
                            "Essa conta será projetada automaticamente nos seus próximos 12 meses!",
                    actionCard = ActionCardData(
                        title = "Recorrência Salva",
                        description = "$desc — ${FinanceEngine.formatCurrency(amt)}",
                        detail = "Todo dia ${newBill.dueDay} • ${newBill.category}"
                    )
                )
            }
            "editar" -> {
                val target = bills.find { it.id == billId || (description != null && matchesSearch(it.description, description)) }
                if (target == null) {
                    return ToolExecutionResult(text = "⚠️ Conta recorrente não encontrada para edição.")
                }
                val matchedCard = if (!cardName.isNullOrBlank()) {
                    cards.find { it.cardName.contains(cardName, ignoreCase = true) || cardName.contains(it.cardName, ignoreCase = true) }
                } else null
                val updated = target.copy(
                    defaultAmount = amount ?: target.defaultAmount,
                    dueDay = dueDay ?: target.dueDay,
                    category = category ?: target.category,
                    subcategory = subcategory ?: target.subcategory,
                    type = type ?: target.type,
                    paymentMethod = matchedCard?.let { PaymentMethod.CREDIT_CARD } ?: paymentMethod ?: target.paymentMethod,
                    cardId = if (matchedCard != null) matchedCard.id else target.cardId,
                    installmentsCount = monthsLimit ?: target.installmentsCount
                )
                repository.saveRecurringBill(updated)
                return ToolExecutionResult(
                    text = "✏️ **Conta recorrente \"${updated.description}\" atualizada com sucesso!**\n" +
                        "• **Valor**: ${FinanceEngine.formatCurrency(updated.defaultAmount)} | **Vencimento**: Dia ${updated.dueDay} | **Categoria**: ${updated.category}",
                    actionCard = ActionCardData(
                        title = "Recorrência Editada",
                        description = "${updated.description} — ${FinanceEngine.formatCurrency(updated.defaultAmount)}",
                        detail = "Dia ${updated.dueDay} • ${updated.category}"
                    )
                )
            }
            "pausar", "ativar" -> {
                val shouldActive = operation.equals("ativar", ignoreCase = true)
                val target = bills.find { it.id == billId || (description != null && it.description.contains(description, ignoreCase = true)) }
                if (target == null) {
                    return ToolExecutionResult(text = "⚠️ Conta recorrente não encontrada.")
                }
                repository.saveRecurringBill(target.copy(active = shouldActive))
                val actLabel = if (shouldActive) "reativada" else "pausada"
                return ToolExecutionResult(
                    text = "⚡ Conta **${target.description}** $actLabel com sucesso!",
                    actionCard = ActionCardData(title = "Assinatura Atualizada", description = "${target.description} ($actLabel)")
                )
            }
            "excluir" -> {
                val target = bills.find { it.id == billId || (description != null && it.description.contains(description, ignoreCase = true)) }
                if (target == null) {
                    return ToolExecutionResult(text = "⚠️ Conta recorrente não encontrada.")
                }
                repository.deleteRecurringBill(target.id)
                return ToolExecutionResult(
                    text = "🗑️ Conta recorrente **${target.description}** removida do sistema.",
                    actionCard = ActionCardData(title = "Assinatura Removida", description = target.description)
                )
            }
            else -> return ToolExecutionResult(text = "Operação desconhecida: $operation")
        }
    }

    private suspend fun executeManageRecurringFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val op = jsonObj.getStringOrNull("operation") ?: jsonObj.getStringOrNull("acao") ?: "listar"
        val desc = jsonObj.getStringOrNull("description") ?: jsonObj.getStringOrNull("descricao")
        val amt = jsonObj.getDoubleOrNull("amount") ?: jsonObj.getDoubleOrNull("valor")
        val day = jsonObj.getIntOrNull("dueDay") ?: jsonObj.getIntOrNull("diaVencimento")
        val cat = jsonObj.getStringOrNull("category") ?: jsonObj.getStringOrNull("categoria")
        val sub = jsonObj.getStringOrNull("subcategory") ?: jsonObj.getStringOrNull("subcategoria")
        val id = jsonObj.getStringOrNull("billId") ?: jsonObj.getStringOrNull("id")
        val typeStr = jsonObj.getStringOrNull("type") ?: jsonObj.getStringOrNull("tipo")
        val type = when {
            typeStr?.equals("income", ignoreCase = true) == true || typeStr?.equals("receita", ignoreCase = true) == true -> TransactionType.INCOME
            typeStr?.equals("expense", ignoreCase = true) == true || typeStr?.equals("despesa", ignoreCase = true) == true -> TransactionType.EXPENSE
            else -> null
        }
        val pmStr = jsonObj.getStringOrNull("paymentMethod") ?: jsonObj.getStringOrNull("metodoPagamento")
        val pm = pmStr?.let { PaymentMethod.from(it) }
        val cardName = jsonObj.getStringOrNull("cardName") ?: jsonObj.getStringOrNull("cartao")
        val monthsLimit = jsonObj.getIntOrNull("monthsLimit") ?: jsonObj.getIntOrNull("mesesLimite")

        return executeManageRecurring(
            operation = op,
            description = desc,
            amount = amt,
            dueDay = day,
            category = cat,
            subcategory = sub,
            billId = id,
            type = type,
            paymentMethod = pm,
            cardName = cardName,
            monthsLimit = monthsLimit
        )
    }

    // -------------------------------------------------------------
    // 9. EXPAND INSTALLMENTS (EXPANDIR PARCELAMENTOS)
    // -------------------------------------------------------------

    suspend fun executeExpandInstallments(month: String? = null): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val targetMonth = month ?: repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }

        val candidates = all.filter { t ->
            t.competenceMonth == targetMonth && t.isInstallment && t.installmentNumber != null && t.installmentsTotal != null && t.installmentsTotal > 1
        }

        if (candidates.isEmpty()) {
            return ToolExecutionResult(text = "ℹ️ Nenhuma compra parcelada necessitando expansão foi encontrada em ${FinanceEngine.monthLabel(targetMonth)}.")
        }

        val toCreate = mutableListOf<Transaction>()
        var count = 0

        for (tx in candidates) {
            val total = tx.installmentsTotal ?: 1
            val cur = tx.installmentNumber ?: 1
            val clean = FinanceEngine.stripInstallmentSuffix(tx.description)
            val groupId = tx.installmentGroupId ?: "inst_" + UUID.randomUUID().toString().take(8)

            val existingInDb = all.filter { it.installmentGroupId == groupId || (FinanceEngine.stripInstallmentSuffix(it.description) == clean && it.installmentsTotal == total) }
            val existingNums = existingInDb.mapNotNull { it.installmentNumber }.toSet()

            for (i in 1..total) {
                if (i in existingNums) continue
                val delta = i - cur
                val instMonth = FinanceEngine.shiftMonth(tx.competenceMonth, delta)
                val instDueDate = shiftDateMonth(tx.dueDate, delta)
                val isPast = i < cur

                toCreate.add(
                    tx.copy(
                        id = "tx_" + UUID.randomUUID().toString().take(8),
                        description = FinanceEngine.formatInstallmentDesc(clean, i, total),
                        status = if (isPast) TransactionStatus.PAID else TransactionStatus.PENDING,
                        competenceMonth = instMonth,
                        dueDate = instDueDate,
                        installmentGroupId = groupId,
                        installmentNumber = i,
                        installmentsTotal = total,
                        createdAt = System.currentTimeMillis() + i
                    )
                )
                count++
            }
        }

        if (toCreate.isNotEmpty()) {
            repository.addTransactions(toCreate)
        }

        val summaryNow = repository.getMonthSummary().first()

        return ToolExecutionResult(
            text = "🔄 **Parcelamentos expandidos com sucesso!**\n\n" +
                    "Foram geradas **$count parcelas** passadas e futuras para manter seu orçamento e Saldo Livre precisos mês a mês.",
            actionCard = ActionCardData(
                title = "Parcelas Expandidas",
                description = "$count parcelas alocadas no calendário",
                newFreeBalance = "Saldo Livre Atual: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
            )
        )
    }

    private suspend fun executeExpandInstallmentsFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val month = jsonObj.getStringOrNull("month") ?: jsonObj.getStringOrNull("mes")
        return executeExpandInstallments(month)
    }

    // -------------------------------------------------------------
    // 10. GENERATE CHART (GRÁFICO FINANCEIRO)
    // -------------------------------------------------------------

    private fun executeGenerateChartFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val chartTypeStr = jsonObj.getStringOrNull("chartType") ?: "donut"
        val title = jsonObj.getStringOrNull("title") ?: "Análise Financeira"
        val description = jsonObj.getStringOrNull("description")
        val dataArray = jsonObj.getAsJsonArrayOrNull("data")

        val items = mutableListOf<ChartDataItem>()
        if (dataArray != null) {
            for (elem in dataArray) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val label = obj.getStringOrNull("label") ?: "Outros"
                val value = obj.getDoubleOrNull("value") ?: 0.0
                val colorHex = obj.getStringOrNull("colorHex") ?: "#10B981"
                val color = try {
                    Color(android.graphics.Color.parseColor(colorHex))
                } catch (_: Exception) {
                    Emerald500
                }
                items.add(ChartDataItem(label = label, value = value, color = color))
            }
        }

        val payload = if (items.isNotEmpty()) {
            ChartPayload(
                type = if (chartTypeStr.equals("bar", ignoreCase = true)) ChartType.BAR else ChartType.DONUT,
                title = title,
                description = description,
                data = items
            )
        } else null

        return ToolExecutionResult(
            text = "📊 **$title**\n\n${description ?: "Gráfico gerado com base nos seus lançamentos reais."}",
            chartPayload = payload
        )
    }

    // -------------------------------------------------------------
    // 10.1. REPORT PROJECTION (RELATÓRIO E PROJEÇÃO MENSAL — ATÉ 12 MESES)
    // -------------------------------------------------------------

    private suspend fun executeReportProjectionFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val monthStr = jsonObj.getStringOrNull("mes")
            ?: jsonObj.getStringOrNull("month")
            ?: jsonObj.getStringOrNull("competenceMonth")
        val monthsCount = jsonObj.getIntOrNull("quantidadeMeses")
            ?: jsonObj.getIntOrNull("monthsCount")
            ?: jsonObj.getIntOrNull("meses")
            ?: 1
        val cardFilter = jsonObj.getStringOrNull("cartao")
            ?: jsonObj.getStringOrNull("cardName")
            ?: jsonObj.getStringOrNull("card")
        return executeReportProjection(monthStr, monthsCount, cardFilter)
    }

    suspend fun executeReportProjection(
        month: String? = null,
        monthsCount: Int = 1,
        cardFilter: String? = null
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val bills = repository.getRecurringBills().first()
        val cards = repository.getCards().first()
        val closures = repository.getMonthClosures().first()
        val user = repository.currentUser.value
        val defaultMonth = repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
        val targetMonth = parseMonthKey(month, defaultMonth) ?: defaultMonth
        val targetMonthLabel = FinanceEngine.formatMonthLabel(targetMonth)
        val isFutureMonth = targetMonth > FinanceEngine.currentMonthKey()
        val profile = repository.allUsers.find { it.uid == user.uid }
        val safeCount = monthsCount.coerceIn(1, 12)

        val summary = FinanceEngine.computeSummary(all, user.uid, targetMonth, cards, closures)
        val projections = FinanceEngine.projectFutureMonths(
            all = all,
            bills = bills,
            profile = profile,
            uid = user.uid,
            startMonthKey = targetMonth,
            count = safeCount,
            cards = cards,
            closures = closures
        )
        val proj = projections.firstOrNull()

        val effectiveIncome = proj?.projectedIncome ?: summary.income
        val effectiveExpense = proj?.projectedExpense ?: summary.committed
        val effectiveFreeBalance = proj?.projectedFreeBalance ?: summary.freeBalance
        val effectiveDailyLimit = proj?.projectedDailyLimit ?: summary.dailyLimit
        val invoiceExpense = proj?.invoiceExpense ?: 0.0
        val fixedBillsExpense = proj?.fixedBillsExpense ?: summary.committedEstimated
        val variableAccountExpense = proj?.variableAccountExpense ?: 0.0

        // 1. Detalhamento de Faturas por Cartão no mês-alvo (InvoiceEngine.buildInvoice)
        val targetCards = if (!cardFilter.isNullOrBlank()) {
            cards.filter { it.cardName.contains(cardFilter, ignoreCase = true) || cardFilter.contains(it.cardName, ignoreCase = true) }
        } else cards

        val invoiceSections = targetCards.mapNotNull { card ->
            val inv = InvoiceEngine.buildInvoice(card, targetMonth, all, user.uid)
            val unlaunchedCardBills = bills.filter { b ->
                b.active && b.type == TransactionType.EXPENSE &&
                    (b.cardId == card.id || (b.paymentMethod == PaymentMethod.CREDIT_CARD && b.cardId == null && targetCards.size == 1)) &&
                    !inv.items.any { tx -> tx.recurringBillId == b.id || FinanceEngine.findMatchingManualBill(listOf(tx), targetMonth, b) != null }
            }
            if (inv.items.isEmpty() && unlaunchedCardBills.isEmpty()) return@mapNotNull null
            val unlaunchedSum = unlaunchedCardBills.sumOf { FinanceEngine.estimateRecurring(it, all, targetMonth).amount }
            val catSummary = if (inv.byCategory.isNotEmpty()) {
                " | Categorias na fatura: " + inv.byCategory.joinToString(", ") { "${it.category} (${FinanceEngine.formatCurrency(it.amount)})" }
            } else ""
            val itemLines = inv.items.take(12).joinToString("\n") { tx ->
                val myImp = FinanceEngine.impactFor(tx, user.uid, all)
                "    - **${tx.description}**: ${FinanceEngine.formatCurrency(myImp)} • ${tx.category}${if (!tx.subcategory.isNullOrBlank()) " › ${tx.subcategory}" else ""} • ${tx.status.label}"
            }
            val recLines = unlaunchedCardBills.joinToString("\n") { b ->
                val est = FinanceEngine.estimateRecurring(b, all, targetMonth).amount
                "    - **${b.description}**: ${FinanceEngine.formatCurrency(est)} • ${b.category} • 🔄 Assinatura Projetada"
            }
            val combinedItems = listOfNotNull(itemLines.takeIf { it.isNotBlank() }, recLines.takeIf { it.isNotBlank() }).joinToString("\n")
            "  * 💳 **Cartão ${card.cardName}** (${inv.status.label} • Fecha ${FinanceEngine.formatShortDate(inv.closingDate)} • Vence ${FinanceEngine.formatShortDate(inv.dueDate)}): " +
                "Total Fatura: **${FinanceEngine.formatCurrency(inv.total + unlaunchedSum)}** (Sua Parte: ${FinanceEngine.formatCurrency(inv.myShare + unlaunchedSum)})$catSummary\n$combinedItems"
        }
        val invoicesBlock = if (invoiceSections.isNotEmpty()) {
            "\n\n💳 **Detalhamento das Faturas de Cartão ($targetMonthLabel):**\n" + invoiceSections.joinToString("\n")
        } else ""

        // 2. Categorias Consolidadas do Mês (unindo itens da fatura + contas fixas + pix/débito)
        val categoriesBlock = if (proj != null && proj.byCategory.isNotEmpty()) {
            val catLines = proj.byCategory.take(10).joinToString("\n") { c ->
                val originParts = mutableListOf<String>()
                if (c.invoiceAmount > 0.01) originParts.add("Fatura Cartão: ${FinanceEngine.formatCurrency(c.invoiceAmount)}")
                if (c.fixedBillAmount > 0.01) originParts.add("Fixas: ${FinanceEngine.formatCurrency(c.fixedBillAmount)}")
                if (c.accountAmount > 0.01) originParts.add("Pix/Débito: ${FinanceEngine.formatCurrency(c.accountAmount)}")
                val originDetail = if (originParts.isNotEmpty()) " (${originParts.joinToString(" • ")})" else ""
                "  * **${c.category}**: ${FinanceEngine.formatCurrency(c.amount)}$originDetail — ${c.count} item(ns)"
            }
            "\n\n🏷️ **Gastos por Categoria Consolidados em $targetMonthLabel (Fatura + Extrato + Fixas):**\n$catLines"
        } else ""

        // 3. Todos os Itens Previstos / Lançados do Mês-Alvo
        val itemsBlock = if (proj != null && proj.items.isNotEmpty()) {
            val itemLines = proj.items.take(25).joinToString("\n") { item ->
                val badge = when (item.origin) {
                    "invoice" -> "💳 Fatura${item.cardName?.let { " $it" } ?: ""}"
                    "fixed_bill" -> "🏠 Conta Fixa"
                    "income" -> "🟢 Receita"
                    else -> "⚡ ${item.paymentMethod.label}"
                }
                val projTag = if (item.isRecurringProjection) " [Projetado]" else ""
                val instTag = item.installmentInfo?.let { " ($it)" } ?: ""
                "  * **${item.description}**$instTag: ${FinanceEngine.formatCurrency(item.myImpact)} • ${item.category} • $badge$projTag (Dia ${item.dueDate.takeLast(2)})"
            }
            val moreItems = if (proj.items.size > 25) "\n  *(e mais ${proj.items.size - 25} itens no mês)*" else ""
            "\n\n📋 **Itens Listados de $targetMonthLabel (${proj.items.size} lançamentos/projeções):**\n$itemLines$moreItems"
        } else ""

        // 4. Horizonte Multi-Mês (quando solicitado 2 a 12 meses para frente)
        val multiMonthBlock = if (projections.size > 1) {
            val rows = projections.joinToString("\n") { p ->
                "  * **${p.label}**: Entradas ${FinanceEngine.formatCurrency(p.projectedIncome)} | " +
                    "Saídas ${FinanceEngine.formatCurrency(p.projectedExpense)} " +
                    "(💳 Faturas: ${FinanceEngine.formatCurrency(p.invoiceExpense)} • 🏠 Fixas: ${FinanceEngine.formatCurrency(p.fixedBillsExpense)} • ⚡ Pix/Déb: ${FinanceEngine.formatCurrency(p.variableAccountExpense)}) " +
                    "→ **Saldo Livre: ${FinanceEngine.formatCurrency(p.projectedFreeBalance)}**"
            }
            "\n\n🗓️ **Projeção de Fluxo para ${projections.size} Meses:**\n$rows"
        } else ""

        val text = """
📊 **${if (isFutureMonth || safeCount > 1) "Projeção Financeira e Raio-X" else "Relatório Consolidado e Raio-X"} ($targetMonthLabel):**

• **Receitas Previstas**: ${FinanceEngine.formatCurrency(effectiveIncome)}
• **Total de Despesas Comprometidas**: ${FinanceEngine.formatCurrency(effectiveExpense)}
  ↳ 💳 **Faturas de Cartão**: ${FinanceEngine.formatCurrency(invoiceExpense)}
  ↳ 🏠 **Contas Fixas (Extrato/Boleto/Pix)**: ${FinanceEngine.formatCurrency(fixedBillsExpense)}
  ↳ ⚡ **Pix / Débito Variável**: ${FinanceEngine.formatCurrency(variableAccountExpense)}
• **Despesas Já Pagas**: ${FinanceEngine.formatCurrency(summary.paid)}
• **Saldo Livre Projetado**: ${FinanceEngine.formatCurrency(effectiveFreeBalance)}
• **Limite Diário Seguro**: ${effectiveDailyLimit?.let { "${FinanceEngine.formatCurrency(it)}/dia" } ?: "Mês encerrado"}$invoicesBlock$categoriesBlock$itemsBlock$multiMonthBlock
""".trimIndent()

        return ToolExecutionResult(
            text = text,
            actionCard = ActionCardData(
                title = if (safeCount > 1) "Projeção $safeCount Meses" else if (isFutureMonth) "Projeção $targetMonthLabel" else "Raio-X $targetMonthLabel",
                description = "Saldo Livre Previsto ($targetMonthLabel): ${FinanceEngine.formatCurrency(effectiveFreeBalance)}",
                detail = "Faturas: ${FinanceEngine.formatCurrency(invoiceExpense)} • Fixas: ${FinanceEngine.formatCurrency(fixedBillsExpense)} • Pix/Déb: ${FinanceEngine.formatCurrency(variableAccountExpense)}"
            )
        )
    }

    // -------------------------------------------------------------
    // 10.2. ENVELOPES ORÇAMENTÁRIOS & DIAGNÓSTICO DE SAÚDE FINANCEIRA
    // -------------------------------------------------------------

    private suspend fun executeManageBudgetsFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val acao = jsonObj.getStringOrNull("acao")
            ?: jsonObj.getStringOrNull("operation")
            ?: jsonObj.getStringOrNull("subAction")
            ?: "consultar"
        val categoria = jsonObj.getStringOrNull("categoria")
            ?: jsonObj.getStringOrNull("category")
        val valorTeto = jsonObj.getDoubleOrNull("valorTeto")
            ?: jsonObj.getDoubleOrNull("limit")
            ?: jsonObj.getDoubleOrNull("amount")
            ?: jsonObj.getDoubleOrNull("valor")
        val categoriaOrigem = jsonObj.getStringOrNull("categoriaOrigem")
            ?: jsonObj.getStringOrNull("fromCategory")
        val categoriaDestino = jsonObj.getStringOrNull("categoriaDestino")
            ?: jsonObj.getStringOrNull("toCategory")
        val valorTransferencia = jsonObj.getDoubleOrNull("valorTransferencia")
            ?: jsonObj.getDoubleOrNull("transferAmount")
            ?: jsonObj.getDoubleOrNull("valor")
        val valorSimulacao = jsonObj.getDoubleOrNull("valorSimulacao")
            ?: jsonObj.getDoubleOrNull("simulatedAmount")
            ?: jsonObj.getDoubleOrNull("amount")
            ?: jsonObj.getDoubleOrNull("valor")
        val descricaoSimulacao = jsonObj.getStringOrNull("descricaoSimulacao")
            ?: jsonObj.getStringOrNull("description")
            ?: jsonObj.getStringOrNull("descricao")

        return executeManageBudgets(
            acao = acao,
            categoria = categoria,
            valorTeto = valorTeto,
            categoriaOrigem = categoriaOrigem,
            categoriaDestino = categoriaDestino,
            valorTransferencia = valorTransferencia,
            valorSimulacao = valorSimulacao,
            descricaoSimulacao = descricaoSimulacao
        )
    }

    suspend fun executeManageBudgets(
        acao: String = "consultar",
        categoria: String? = null,
        valorTeto: Double? = null,
        categoriaOrigem: String? = null,
        categoriaDestino: String? = null,
        valorTransferencia: Double? = null,
        valorSimulacao: Double? = null,
        descricaoSimulacao: String? = null
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val cards = repository.getCards().first()
        val closures = repository.getMonthClosures().first()
        val user = repository.currentUser.value
        val month = repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
        val summary = FinanceEngine.computeSummary(all, user.uid, month, cards, closures)
        val expectedIncome = if (summary.income > 0.0) summary.income else user.monthlyIncome
        val currentBudgets = (user.monthlyCategoryBudgets[month] ?: user.categoryBudgets).toMutableMap()
        val report = FinanceEngine.computeCategoryBudgets(
            summary = summary,
            categoryBudgets = currentBudgets,
            expectedIncome = expectedIncome
        )
        val emergencyReserve = FinanceEngine.resolveEmergencyReserveBalance(user, summary)
        val health = FinanceEngine.calculateFinancialHealthScore(summary, emergencyReserve, report)

        return when (acao.lowercase(Locale.ROOT).trim()) {
            "diagnostico_saude_financeira", "saude_financeira", "diagnostico" -> {
                val tipsBlock = if (health.actionableTips.isNotEmpty()) {
                    "\n\n💡 **Plano de Ação Personalizado (Coach IA):**\n" +
                        health.actionableTips.joinToString("\n") { "  • $it" }
                } else ""

                val budgetLine = if (health.budgetConfiguredCount > 0) {
                    "${health.budgetAdherenceScore}% de aderência (${health.budgetConfiguredCount - health.budgetOverspentCount}/${health.budgetConfiguredCount} dentro do teto)"
                } else {
                    "Nenhum envelope configurado ainda"
                }

                val text = """
🩺 **Diagnóstico Completo de Saúde Financeira (${FinanceEngine.formatMonthLabel(month)}):**

• **Score Geral**: **${health.score}/100 (${health.label})**
  *${health.description}*

📊 **Raio-X dos 4 Pilares:**
1. **Taxa de Poupança & Investimento**: **${health.savingsScore}/30 pts**
   ↳ Taxa real aportada: **${health.savingsRatePercentage}%** (${FinanceEngine.formatCurrency(summary.totalInvested)}) | Potencial com Saldo Livre: **${health.potentialSavingsRatePercentage}%** (Meta ideal: ≥ 20%)
2. **Controle de Custo de Vida**: **${health.spendingLimitScore}/30 pts**
   ↳ Custo de vida real (excluindo aportes): **${health.spendingRatioPercentage}%** da renda (${FinanceEngine.formatCurrency(health.monthlyLivingCost)}) (Meta ideal: ≤ 70%)
3. **Reserva de Emergência**: **${health.emergencyReserveScore}/25 pts**
   ↳ Cobertura atual: **${health.emergencyCoverageMonths} meses** do custo de vida (${FinanceEngine.formatCurrency(emergencyReserve)}) (Meta ideal: 6 meses)
4. **Disciplina nos Envelopes**: **${health.envelopeScore}/15 pts**
   ↳ Status: **$budgetLine**$tipsBlock
""".trimIndent()

                ToolExecutionResult(
                    text = text,
                    actionCard = ActionCardData(
                        title = "Saúde Financeira: ${health.score}/100 (${health.label})",
                        description = health.actionableTips.firstOrNull() ?: health.description,
                        detail = "Poupança ${health.savingsScore}/30 • Custo ${health.spendingLimitScore}/30 • Reserva ${health.emergencyReserveScore}/25 • Envelopes ${health.envelopeScore}/15",
                        newFreeBalance = "Saldo Livre: ${FinanceEngine.formatCurrency(summary.freeBalance)}"
                    )
                )
            }

            "definir_teto" -> {
                val catName = categoria?.trim().orEmpty()
                if (catName.isBlank()) {
                    return ToolExecutionResult(text = "⚠️ Informe a categoria para definir o teto (ex: Alimentação, Lazer, Transporte).")
                }
                if (valorTeto == null || valorTeto < 0.0) {
                    return ToolExecutionResult(text = "⚠️ Informe um valor válido para o teto de **$catName**.")
                }

                val cleanVal = FinanceEngine.round2(valorTeto)
                if (cleanVal <= 0.0) {
                    currentBudgets.remove(catName)
                } else {
                    currentBudgets[catName] = cleanVal
                }
                repository.updateCategoryBudgets(currentBudgets)

                val updatedReport = FinanceEngine.computeCategoryBudgets(
                    summary = summary,
                    categoryBudgets = currentBudgets,
                    expectedIncome = expectedIncome
                )
                val catStatus = updatedReport.categories.find { it.category.equals(catName, ignoreCase = true) }
                val spent = catStatus?.spent ?: 0.0
                val rem = cleanVal - spent

                val msg = if (cleanVal <= 0.0) {
                    "🗑️ **Teto de $catName removido!**\n\nO envelope de **$catName** foi desativado."
                } else {
                    "🎯 **Teto de $catName atualizado para ${FinanceEngine.formatCurrency(cleanVal)}!**\n\n" +
                        "• **Gasto atual no mês**: ${FinanceEngine.formatCurrency(spent)} (${catStatus?.percentage ?: 0.0}%)\n" +
                        "• **Folga no envelope**: ${if (rem >= 0) "${FinanceEngine.formatCurrency(rem)} disponíveis" else "Estourado em ${FinanceEngine.formatCurrency(kotlin.math.abs(rem))}"}\n" +
                        (if (catStatus != null && !catStatus.isFixedCategory && rem > 0) "• **Ritmo seguro até o fim do mês**: ${FinanceEngine.formatCurrency(catStatus.recommendedDailyRemaining)}/dia" else "")
                }

                ToolExecutionResult(
                    text = msg,
                    actionCard = ActionCardData(
                        title = if (cleanVal > 0) "Envelope Atualizado: $catName" else "Envelope Removido: $catName",
                        description = if (cleanVal > 0) "Teto: ${FinanceEngine.formatCurrency(cleanVal)} (Gasto: ${FinanceEngine.formatCurrency(spent)})" else "Teto desativado",
                        detail = if (cleanVal > 0) "${catStatus?.percentage ?: 0.0}% utilizado • Aderência geral: ${updatedReport.overallPercentage}%" else null
                    )
                )
            }

            "rebalancear" -> {
                val fromCat = categoriaOrigem?.trim().orEmpty()
                val toCat = categoriaDestino?.trim().orEmpty()
                val transferAmt = valorTransferencia ?: 0.0

                if (fromCat.isBlank() || toCat.isBlank() || transferAmt <= 0.0) {
                    return ToolExecutionResult(text = "⚠️ Informe a categoria de origem, a categoria de destino e o valor positivo a transferir.")
                }

                val currentFrom = currentBudgets[fromCat] ?: 0.0
                val currentTo = currentBudgets[toCat] ?: 0.0
                val newFrom = kotlin.math.max(0.0, FinanceEngine.round2(currentFrom - transferAmt))
                val newTo = FinanceEngine.round2(currentTo + transferAmt)

                currentBudgets[fromCat] = newFrom
                currentBudgets[toCat] = newTo
                repository.updateCategoryBudgets(currentBudgets)

                ToolExecutionResult(
                    text = "⚖️ **Rebalanceamento concluído com sucesso!**\n\n" +
                        "Transferi **${FinanceEngine.formatCurrency(transferAmt)}** do envelope de **$fromCat** para **$toCat**:\n" +
                        "• **$fromCat**: ${FinanceEngine.formatCurrency(currentFrom)} → **${FinanceEngine.formatCurrency(newFrom)}**\n" +
                        "• **$toCat**: ${FinanceEngine.formatCurrency(currentTo)} → **${FinanceEngine.formatCurrency(newTo)}**",
                    actionCard = ActionCardData(
                        title = "Envelopes Rebalanceados ⚖️",
                        description = "${FinanceEngine.formatCurrency(transferAmt)} de $fromCat → $toCat",
                        detail = "$fromCat: ${FinanceEngine.formatCurrency(newFrom)} | $toCat: ${FinanceEngine.formatCurrency(newTo)}"
                    )
                )
            }

            "rebalancear_automatico" -> {
                val plan = FinanceEngine.suggestBudgetRebalance(report)
                if (!plan.canCoverFullDeficit && plan.transfers.isEmpty()) {
                    return ToolExecutionResult(
                        text = if (report.overspentCount == 0) {
                            "✅ **Todos os seus envelopes estão dentro do teto!** Não há estouros para rebalancear no momento."
                        } else {
                            "⚠️ Há um estouro de **${FinanceEngine.formatCurrency(plan.totalDeficit)}**, mas não há outros envelopes com folga suficiente para cobrir automaticamente. Considere revisar seus tetos."
                        }
                    )
                }

                for (t in plan.transfers) {
                    currentBudgets[t.fromCategory] = t.newFromLimit
                    currentBudgets[t.toCategory] = t.newToLimit
                }
                repository.updateCategoryBudgets(currentBudgets)

                val lines = plan.transfers.joinToString("\n") { t ->
                    "• **${t.fromCategory} → ${t.toCategory}**: ${FinanceEngine.formatCurrency(t.amount)} (${t.toCategory} passa para ${FinanceEngine.formatCurrency(t.newToLimit)})"
                }

                ToolExecutionResult(
                    text = "✨ **Rebalanceamento Inteligente Aplicado!**\n\n${plan.summaryText}\n\n$lines",
                    actionCard = ActionCardData(
                        title = "Rebalanceamento IA Aplicado ✨",
                        description = "Coberto: ${FinanceEngine.formatCurrency(plan.totalCovered)} em ${plan.transfers.size} transferência(s)",
                        detail = plan.transfers.joinToString(" | ") { "${it.fromCategory} → ${it.toCategory}" }
                    )
                )
            }

            "sugerir_tetos" -> {
                val suggestedResult = FinanceEngine.suggestCategoryBudgets(all, user.uid, month, expectedIncome, summary.carryoverAmount, summary.previousMonthDeficit)
                val suggested = suggestedResult.suggestedBudgets
                if (suggested.isEmpty()) {
                    return ToolExecutionResult(text = "ℹ️ Ainda não há dados suficientes de renda ou despesas para sugerir tetos automaticamente.")
                }
                val totalSuggested = suggestedResult.totalSuggested
                val lines = suggested.entries.sortedByDescending { it.value }.joinToString("\n") { (cat, lim) ->
                    val currentSpent = summary.byCategory.find { it.category == cat }?.amount ?: 0.0
                    val kind = if (FinanceEngine.isFixedBudgetCategory(cat)) "📌 Fixa" else "🔄 Variável"
                    "• **$cat** ($kind): Teto sugerido **${FinanceEngine.formatCurrency(lim)}** (Gasto atual: ${FinanceEngine.formatCurrency(currentSpent)})"
                }

                ToolExecutionResult(
                    text = "🤖 **Sugestão de Envelopes Inteligentes (Calibrados à Entrada Prevista do Mês):**\n\n" +
                        "Com base na entrada esperada para ${FinanceEngine.formatMonthLabel(month)} de **${FinanceEngine.formatCurrency(expectedIncome)}** e no seu padrão real de gastos, sugiro um orçamento total de **${FinanceEngine.formatCurrency(totalSuggested)}** (${suggestedResult.incomeCoveragePct}% da entrada):\n\n" +
                        "$lines\n\n" +
                        "Deseja que eu aplique esses tetos agora? Basta dizer **\"Aplicar sugestão de tetos\"**!",
                    actionCard = ActionCardData(
                        title = "Tetos Sugeridos pela IA (Entrada do Mês)",
                        description = "${suggested.size} categorias • Total: ${FinanceEngine.formatCurrency(totalSuggested)} (${suggestedResult.incomeCoveragePct}% da entrada)",
                        detail = "Diga \"Aplicar sugestão de tetos\" para ativar com 1 clique"
                    )
                )
            }

            "aplicar_sugestao_ia" -> {
                val suggestedResult = FinanceEngine.suggestCategoryBudgets(all, user.uid, month, expectedIncome, summary.carryoverAmount, summary.previousMonthDeficit)
                val suggested = suggestedResult.suggestedBudgets
                if (suggested.isEmpty()) {
                    return ToolExecutionResult(text = "⚠️ Não foi possível gerar tetos sugeridos sem receita ou histórico no mês.")
                }
                repository.updateCategoryBudgets(suggested)
                val totalSuggested = suggestedResult.totalSuggested

                ToolExecutionResult(
                    text = "✅ **Envelopes Orçamentários IA ativados com sucesso!**\n\n" +
                        "Configurei **${suggested.size} categorias** somando **${FinanceEngine.formatCurrency(totalSuggested)}** (calibrados para respeitar a entrada prevista de ${FinanceEngine.formatCurrency(expectedIncome)} no mês).\n" +
                        "Você já pode acompanhar o ritmo de cada envelope no Dashboard ou me perguntar a qualquer momento!",
                    actionCard = ActionCardData(
                        title = "Envelopes IA Ativados ✨",
                        description = "${suggested.size} categorias configuradas (${FinanceEngine.formatCurrency(totalSuggested)})",
                        detail = "Calibrado à entrada prevista do mês (${FinanceEngine.formatCurrency(expectedIncome)})"
                    )
                )
            }

            "simular_compra" -> {
                val simAmount = valorSimulacao ?: valorTeto ?: 0.0
                if (simAmount <= 0.0) {
                    return ToolExecutionResult(text = "⚠️ Informe o valor da compra que deseja simular (ex: \"Posso gastar R$ 180 em Lazer?\").")
                }
                val targetCat = categoria?.takeIf { it.isNotBlank() }
                    ?: descricaoSimulacao?.let { neuralClassifier.guessCategory(it) }
                    ?: "Lazer"

                val catStatus = report.categories.find { it.category.equals(targetCat, ignoreCase = true) }
                val currentSpent = catStatus?.spent ?: (summary.byCategory.find { it.category.equals(targetCat, ignoreCase = true) }?.amount ?: 0.0)
                val limit = catStatus?.limit ?: 0.0
                val newFreeBalance = FinanceEngine.round2(summary.freeBalance - simAmount)
                val newDailyLimit = if (summary.daysRemaining > 0) {
                    FinanceEngine.round2(kotlin.math.max(0.0, newFreeBalance / summary.daysRemaining))
                } else null

                val envelopeAnalysis = if (limit > 0.0) {
                    val afterSpent = FinanceEngine.round2(currentSpent + simAmount)
                    val afterPct = FinanceEngine.round2((afterSpent / limit) * 100.0)
                    val afterRem = FinanceEngine.round2(limit - afterSpent)
                    if (afterRem >= 0.0) {
                        "✅ **Cabe no envelope de $targetCat!**\n" +
                            "• Uso do envelope: ${FinanceEngine.formatCurrency(currentSpent)} → **${FinanceEngine.formatCurrency(afterSpent)}** ($afterPct% do teto de ${FinanceEngine.formatCurrency(limit)})\n" +
                            "• Folga restante em $targetCat: **${FinanceEngine.formatCurrency(afterRem)}**"
                    } else {
                        val deficit = kotlin.math.abs(afterRem)
                        val donor = report.categories.filter { it.category != targetCat && it.status == CategoryBudgetAlertStatus.OK && it.remaining >= 20.0 }
                            .maxByOrNull { it.remaining }
                        "⚠️ **Atenção: Estoura o envelope de $targetCat em ${FinanceEngine.formatCurrency(deficit)}!**\n" +
                            "• Uso do envelope: ${FinanceEngine.formatCurrency(currentSpent)} → **${FinanceEngine.formatCurrency(afterSpent)}** ($afterPct% do teto de ${FinanceEngine.formatCurrency(limit)})\n" +
                            (if (donor != null) "💡 *Dica IA:* Você pode cobrir esse excesso transferindo de **${donor.category}** (que tem ${FinanceEngine.formatCurrency(donor.remaining)} livres)." else "")
                    }
                } else {
                    "ℹ️ Você ainda não definiu um teto específico para **$targetCat** (gasto atual: ${FinanceEngine.formatCurrency(currentSpent)})."
                }

                val verdict = when {
                    newFreeBalance < 0 -> "🚨 **Risco Alto**: Essa compra deixará seu Saldo Livre do mês negativo em **${FinanceEngine.formatCurrency(kotlin.math.abs(newFreeBalance))}**."
                    limit > 0.0 && (currentSpent + simAmount) > limit -> "⚠️ **Cautela**: Seu Saldo Livre comporta, mas você precisará rebalancear o envelope de **$targetCat**."
                    else -> "🟢 **Compra Segura**: Cabe tanto no seu Saldo Livre quanto no planejamento do mês!"
                }

                ToolExecutionResult(
                    text = "🧮 **Simulação de Impacto: ${descricaoSimulacao ?: targetCat} (${FinanceEngine.formatCurrency(simAmount)})**\n\n" +
                        "$verdict\n\n" +
                        "$envelopeAnalysis\n\n" +
                        "💰 **Impacto no Saldo Livre do Mês:**\n" +
                        "• Saldo Livre: ${FinanceEngine.formatCurrency(summary.freeBalance)} → **${FinanceEngine.formatCurrency(newFreeBalance)}**\n" +
                        (if (newDailyLimit != null) "• Novo Limite Diário Seguro: **${FinanceEngine.formatCurrency(newDailyLimit)}/dia**" else ""),
                    actionCard = ActionCardData(
                        title = "Simulação: ${FinanceEngine.formatCurrency(simAmount)} em $targetCat",
                        description = if (newFreeBalance >= 0) "Saldo Livre após compra: ${FinanceEngine.formatCurrency(newFreeBalance)}" else "Saldo Livre ficaria negativo: ${FinanceEngine.formatCurrency(newFreeBalance)}",
                        detail = if (limit > 0) "Envelope $targetCat: ${FinanceEngine.round2(((currentSpent + simAmount) / limit) * 100)}% do teto" else "Categoria sem teto configurado"
                    )
                )
            }

            else -> {
                // "consultar"
                if (report.categories.isEmpty()) {
                    val suggestedResult = FinanceEngine.suggestCategoryBudgets(all, user.uid, month, summary.income, summary.carryoverAmount, summary.previousMonthDeficit)
                    val suggested = suggestedResult.suggestedBudgets
                    val sugTotal = suggested.values.sum()
                    return ToolExecutionResult(
                        text = "🎯 **Você ainda não tem Envelopes Orçamentários configurados para ${FinanceEngine.formatMonthLabel(month)}.**\n\n" +
                            "Com base na sua renda de **${FinanceEngine.formatCurrency(summary.income)}**, posso criar automaticamente **${suggested.size} envelopes inteligentes** (somando ${FinanceEngine.formatCurrency(sugTotal)}) calibrados pela Regra 50/30/20.\n\n" +
                            "Deseja ativar agora? Diga **\"Aplicar sugestão de tetos\"**!",
                        actionCard = ActionCardData(
                            title = "Envelopes Orçamentários",
                            description = "Nenhum teto ativo • Sugestão IA pronta (${suggested.size} categorias)",
                            detail = "Peça \"Aplicar sugestão de tetos\" para ativar"
                        )
                    )
                }

                val lines = report.categories.joinToString("\n") { c ->
                    val icon = when (c.status) {
                        CategoryBudgetAlertStatus.DANGER -> "🔴"
                        CategoryBudgetAlertStatus.WARNING -> "🟡"
                        CategoryBudgetAlertStatus.OK -> "🟢"
                    }
                    val typeTag = if (c.isFixedCategory) "📌 Fixa" else "🔄 Variável"
                    val paceNote = if (!c.isFixedCategory && c.burnRateStatus == CategoryBudgetPaceStatus.CRITICAL && c.status != CategoryBudgetAlertStatus.DANGER) {
                        " ⚠️ *Projeção: ${FinanceEngine.formatCurrency(c.projectedSpent)}*"
                    } else if (!c.isFixedCategory && c.remaining > 0) {
                        " • *Meta: ${FinanceEngine.formatCurrency(c.recommendedDailyRemaining)}/dia*"
                    } else ""
                    "$icon **${c.category}** ($typeTag): ${FinanceEngine.formatCurrency(c.spent)} / ${FinanceEngine.formatCurrency(c.limit)} (**${c.percentage.toInt()}%**)$paceNote"
                }

                val rebalance = FinanceEngine.suggestBudgetRebalance(report)
                val rebalanceBlock = if (rebalance.transfers.isNotEmpty()) {
                    "\n\n⚖️ **Sugestão de Rebalanceamento IA:**\n${rebalance.summaryText}\n*(Diga \"Rebalancear envelopes\" para aplicar)*"
                } else ""

                ToolExecutionResult(
                    text = "🎯 **Raio-X dos Envelopes Orçamentários (${FinanceEngine.formatMonthLabel(month)}):**\n\n" +
                        "• **Orçamento Total**: ${FinanceEngine.formatCurrency(report.totalSpentInBudgeted)} de ${FinanceEngine.formatCurrency(report.totalBudgeted)} (**${report.overallPercentage}%**)\n" +
                        "• **Folga nos Envelopes**: ${FinanceEngine.formatCurrency(report.totalRemaining)}\n" +
                        "• **Status**: ${report.safeCount} no verde • ${report.warningCount} em atenção • ${report.overspentCount} estourado(s)\n\n" +
                        "$lines$rebalanceBlock",
                    actionCard = ActionCardData(
                        title = "Envelopes: ${report.overallPercentage}% utilizado",
                        description = "${FinanceEngine.formatCurrency(report.totalSpentInBudgeted)} de ${FinanceEngine.formatCurrency(report.totalBudgeted)}",
                        detail = "${report.safeCount} seguros • ${report.warningCount} atenção • ${report.overspentCount} estourados"
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------
    // 11. REGRAS APRENDIDAS (MEMÓRIA VIVA)
    // -------------------------------------------------------------

    fun executeLearnRule(rule: String): ToolExecutionResult {
        val clean = rule.trim()
        if (clean.isBlank()) return ToolExecutionResult(text = "⚠️ Regra vazia.")
        val user = repository.currentUser.value
        rulesManager.addRule(user.uid, clean)
        neuralClassifier.saveLearnedRule(clean)

        return ToolExecutionResult(
            text = "🧠 **Nova preferência aprendida!**\n\n\"$clean\"\n\nJá memorizei esse comportamento e aplicarei automaticamente em futuros lançamentos!",
            actionCard = ActionCardData(
                title = "Preferência Memorizada 🧠",
                description = clean,
                detail = "Gravado na sua Memória Viva"
            )
        )
    }

    fun executeDeleteRule(rule: String): ToolExecutionResult {
        val clean = rule.trim()
        if (clean.isBlank()) return ToolExecutionResult(text = "⚠️ Regra vazia.")
        val user = repository.currentUser.value
        rulesManager.deleteRule(user.uid, clean)
        neuralClassifier.deleteLearnedRule(clean)

        return ToolExecutionResult(
            text = "🗑️ Esqueci a regra: \"$clean\".",
            actionCard = ActionCardData(
                title = "Regra Esquecida",
                description = clean
            )
        )
    }

    // -------------------------------------------------------------
    // 12. EXECUÇÃO DE AÇÕES CONFIRMADAS INTERATIVAMENTE
    // -------------------------------------------------------------

    suspend fun executeConfirmedAction(action: PendingAction): ToolExecutionResult {
        val args = action.args
        return when (action.toolName) {
            "CREATE_TRANSACTION", "registrarTransacao" -> {
                val desc = args["description"] as? String ?: args["descricao"] as? String ?: "Lançamento"
                val amount = (args["amount"] as? Number)?.toDouble() ?: (args["amount"] as? String)?.toDoubleOrNull()
                    ?: (args["valorTotal"] as? Number)?.toDouble() ?: (args["valorTotal"] as? String)?.toDoubleOrNull() ?: 0.0
                val typeStr = (args["type"] as? String) ?: (args["tipo"] as? String)
                val type = if (typeStr?.equals("income", ignoreCase = true) == true || typeStr?.equals("receita", ignoreCase = true) == true || typeStr?.equals("entrada", ignoreCase = true) == true) TransactionType.INCOME else TransactionType.EXPENSE
                val cat = args["category"] as? String ?: args["categoria"] as? String
                val sub = args["subcategory"] as? String ?: args["subcategoria"] as? String
                val payMethod = PaymentMethod.from(args["paymentMethod"] as? String ?: args["metodoPagamento"] as? String)
                val cardName = args["cardName"] as? String ?: args["cartao"] as? String

                val splitTypeStr = args["splitType"] as? String ?: args["tipoDivisao"] as? String
                val splitType = when (splitTypeStr?.uppercase()) {
                    "SPLIT_50", "CASAL_50_50" -> SplitType.SPLIT_50
                    "PARTNER", "PARCEIRO" -> SplitType.PARTNER
                    "FRIEND", "AMIGO" -> SplitType.FRIEND
                    "EXTERNAL", "TERCEIRO" -> SplitType.EXTERNAL
                    "CUSTOM", "PERSONALIZADA" -> SplitType.CUSTOM
                    else -> SplitType.MINE
                }
                val friendName = args["friendName"] as? String ?: args["nomeAmigo"] as? String
                val friendEmail = args["friendEmail"] as? String ?: args["emailAmigo"] as? String
                val thirdPartyName = args["thirdPartyName"] as? String ?: args["nomeTerceiro"] as? String
                val currentInst = (args["currentInstallment"] as? Number)?.toInt() ?: (args["currentInstallment"] as? String)?.toIntOrNull()
                    ?: (args["parcelaAtual"] as? Number)?.toInt() ?: (args["parcelaAtual"] as? String)?.toIntOrNull() ?: 1
                val totalInst = (args["totalInstallments"] as? Number)?.toInt() ?: (args["totalInstallments"] as? String)?.toIntOrNull()
                    ?: (args["totalParcelas"] as? Number)?.toInt() ?: (args["totalParcelas"] as? String)?.toIntOrNull() ?: 1
                val compMonth = args["competenceMonth"] as? String ?: args["mesFatura"] as? String ?: args["mesCompetenciaFatura"] as? String
                val dueDate = args["dueDate"] as? String ?: args["dataVencimento"] as? String
                val friendSharePct = (args["friendSharePct"] as? Number)?.toDouble()
                    ?: (args["friendSharePct"] as? String)?.toDoubleOrNull()
                    ?: (args["friendPct"] as? Number)?.toDouble()
                    ?: (args["friendPct"] as? String)?.toDoubleOrNull()

                executeCreateTransaction(
                    description = desc,
                    amount = amount,
                    type = type,
                    category = cat,
                    subcategory = sub,
                    paymentMethod = payMethod,
                    cardName = cardName,
                    splitType = splitType,
                    friendName = friendName,
                    friendEmail = friendEmail,
                    friendSharePct = friendSharePct,
                    thirdPartyName = thirdPartyName,
                    currentInstallment = currentInst,
                    totalInstallments = totalInst,
                    customCompetenceMonth = compMonth,
                    customDueDate = dueDate,
                    confirmed = true
                )
            }
            "BATCH_TRANSACTIONS", "registrarTransacoesLote" -> {
                val jsonString = args["transactionsJson"] as? String
                val rawList = args["transacoes"] ?: args["pending_transactions"] ?: args["transactions"]
                val array: JsonArray? = when {
                    !jsonString.isNullOrBlank() -> gson.fromJson(jsonString, JsonArray::class.java)
                    rawList is List<*> -> gson.toJsonTree(rawList).asJsonArray
                    else -> null
                }
                if (array != null && array.size() > 0) {
                    val jsonObj = JsonObject().apply {
                        add("transactions", array)
                        (args["mesFaturaPadrao"] as? String)?.let { addProperty("mesFaturaPadrao", it) }
                        (args["metodoPagamentoPadrao"] as? String)?.let { addProperty("metodoPagamentoPadrao", it) }
                        (args["cartaoPadrao"] as? String ?: args["cartao"] as? String ?: args["cardName"] as? String)?.let {
                            addProperty("cartaoPadrao", it)
                            addProperty("cartao", it)
                            addProperty("cardName", it)
                        }
                        (args["cardId"] as? String)?.let { addProperty("cardId", it) }
                        (args["origemDocumento"] as? String)?.let { addProperty("origemDocumento", it) }
                        addProperty("confirmed", true)
                    }
                    executeBatchFromJson(jsonObj)
                } else {
                    ToolExecutionResult(text = "⚠️ Não foi possível recuperar as transações do lote.")
                }
            }
            "EDIT_TRANSACTION", "editarTransacoes", "EDIT_TRANSACTIONS", "BATCH_EDIT", "batchEdit", "editarTransacao" -> {
                val term = args["searchTerm"] as? String ?: args["termoBusca"] as? String
                val terms = (args["searchTerms"] as? List<*>)?.mapNotNull { it?.toString() }
                val id = args["transactionId"] as? String
                val targetIds = (args["targetIds"] as? List<*>)?.mapNotNull { it?.toString() }
                val month = args["month"] as? String ?: args["mes"] as? String
                val newAmt = (args["newAmount"] as? Number)?.toDouble() ?: (args["newAmount"] as? String)?.toDoubleOrNull()
                    ?: (args["novoValor"] as? Number)?.toDouble() ?: (args["novoValor"] as? String)?.toDoubleOrNull()
                val newDesc = args["newDescription"] as? String ?: args["novaDescricao"] as? String
                val newCat = args["newCategory"] as? String ?: args["novaCategoria"] as? String
                val newSub = args["newSubcategory"] as? String ?: args["novaSubcategoria"] as? String
                val newStatus = (args["newStatus"] as? String ?: args["novoStatus"] as? String)?.let { TransactionStatus.from(it) }
                val newPayMethod = (args["newPaymentMethod"] as? String ?: args["novoMetodoPagamento"] as? String)?.let { PaymentMethod.from(it) }
                val newCompMonth = args["newCompetenceMonth"] as? String ?: args["novoMesFatura"] as? String
                val newDueDate = args["newDueDate"] as? String
                    ?: args["novaDataVencimento"] as? String
                    ?: args["newDate"] as? String
                    ?: args["novaData"] as? String
                    ?: args["date"] as? String
                    ?: args["data"] as? String
                    ?: args["dueDate"] as? String
                    ?: args["dataVencimento"] as? String
                val newSplitTypeStr = args["newSplitType"] as? String ?: args["novoTipoDivisao"] as? String
                val newSplitType = when (newSplitTypeStr?.uppercase()) {
                    "SPLIT_50", "CASAL_50_50" -> SplitType.SPLIT_50
                    "PARTNER", "PARCEIRO" -> SplitType.PARTNER
                    "FRIEND", "AMIGO" -> SplitType.FRIEND
                    "EXTERNAL", "TERCEIRO" -> SplitType.EXTERNAL
                    else -> if (newSplitTypeStr != null) SplitType.MINE else null
                }

                val originDate = args["originDate"] as? String
                    ?: args["currentDate"] as? String
                    ?: args["dataOriginal"] as? String
                    ?: (args["date"] as? String)?.takeIf { it != newDueDate }
                    ?: (args["data"] as? String)?.takeIf { it != newDueDate }
                val filterAmount = (args["filterAmount"] as? Number)?.toDouble() ?: (args["filterAmount"] as? String)?.toDoubleOrNull()
                    ?: (args["amount"] as? Number)?.toDouble() ?: (args["amount"] as? String)?.toDoubleOrNull()
                val editAllMatches = (args["editAllMatches"] as? Boolean) ?: (args["editAll"] as? Boolean) ?: false

                executeEditTransaction(
                    searchTerm = term,
                    searchTerms = terms,
                    transactionId = id,
                    targetIds = targetIds,
                    month = month,
                    originDate = originDate,
                    filterAmount = filterAmount,
                    editAllMatches = editAllMatches,
                    newAmount = newAmt,
                    newDescription = newDesc,
                    newCategory = newCat,
                    newSubcategory = newSub,
                    newStatus = newStatus,
                    newPaymentMethod = newPayMethod,
                    newCompetenceMonth = newCompMonth,
                    newDueDate = newDueDate,
                    newSplitType = newSplitType,
                    confirmed = true
                )
            }
            "DELETE_TRANSACTION", "excluirTransacoes" -> {
                val id = args["transactionId"] as? String
                val targetIds = (args["targetIds"] as? List<*>)?.mapNotNull { it?.toString() }
                val term = args["searchTerm"] as? String ?: args["termoBusca"] as? String
                val amt = (args["amount"] as? Number)?.toDouble() ?: (args["amount"] as? String)?.toDoubleOrNull()
                    ?: (args["valor"] as? Number)?.toDouble() ?: (args["valor"] as? String)?.toDoubleOrNull()
                val month = args["month"] as? String ?: args["mes"] as? String
                val deleteAll = (args["deleteAllInstallments"] as? Boolean) ?: false

                executeDeleteTransaction(
                    searchTerm = term,
                    transactionId = id,
                    targetIds = targetIds,
                    amount = amt,
                    month = month,
                    deleteAllInstallments = deleteAll,
                    confirmed = true
                )
            }
            "UPDATE_ESTIMATED", "atualizarContaPrevista" -> {
                val term = args["searchTerm"] as? String ?: args["buscaDescricao"] as? String ?: ""
                val amt = (args["actualAmount"] as? Number)?.toDouble() ?: (args["valorReal"] as? Number)?.toDouble() ?: 0.0
                val statusStr = args["status"] as? String ?: args["novoStatus"] as? String ?: "paid"
                val status = TransactionStatus.from(statusStr)

                executeUpdateEstimated(
                    searchTerm = term,
                    actualAmount = amt,
                    status = status
                )
            }
            "EXPAND_INSTALLMENTS", "expandirParcelamentos" -> {
                val month = args["month"] as? String ?: args["mes"] as? String
                executeExpandInstallments(month)
            }
            else -> ToolExecutionResult(text = "Ação confirmada desconhecida: ${action.toolName}")
        }
    }

    // -------------------------------------------------------------
    // 13. PROCESSAMENTO DE LINGUAGEM NATURAL LOCAL (OFFLINE NLP)
    // -------------------------------------------------------------

    suspend fun processNaturalLanguageOffline(query: String): ToolExecutionResult {
        val trimmed = query.trim()
        val lower = normalize(trimmed)
        val summary = repository.getMonthSummary().first()

        // 1. Aprender Regra
        val learnMatch = Regex("""^(?:aprenda que|lembre-se que|memorize que|salve que)\s+(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (learnMatch != null) {
            return executeLearnRule(learnMatch.groupValues[1])
        }

        // 2. Esquecer Regra
        val deleteRuleMatch = Regex("""^(?:esque[çc]a|delete a regra|remova a regra)\s+(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (deleteRuleMatch != null) {
            return executeDeleteRule(deleteRuleMatch.groupValues[1])
        }

        // 2.1. Diagnóstico de Saúde Financeira (4 Pilares)
        if (
            lower.contains("saude financeira") ||
            lower.contains("meu score") ||
            lower.contains("score financeiro") ||
            lower.contains("diagnostico financeiro") ||
            lower.contains("termometro financeiro") ||
            lower.contains("4 pilares")
        ) {
            return executeManageBudgets(acao = "diagnostico_saude_financeira")
        }

        // 2.2. Envelopes Orçamentários: Definir Teto ("definir teto de Lazer para 500", "teto de Alimentação 1200")
        val setCapRegex = Regex("""(?:definir|colocar|ajustar|mudar|alterar)?\s*(?:o\s+)?teto\s+(?:de|para|em)\s+([a-zA-ZÀ-ÿ\s]+?)\s+(?:para|em|de)\s*r?\$?\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
        val setCapMatch = setCapRegex.find(trimmed)
        if (setCapMatch != null) {
            val catRaw = setCapMatch.groupValues[1].trim()
            val amt = neuralClassifier.parseAmount(setCapMatch.groupValues[2])
            if (amt != null) {
                val matchedCat = CategoryRegistry.EXPENSE_CATEGORIES.find { normalize(it.name) == normalize(catRaw) }?.name
                    ?: catRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                return executeManageBudgets(acao = "definir_teto", categoria = matchedCat, valorTeto = amt)
            }
        }

        // 2.3. Envelopes Orçamentários: Aplicar Sugestão IA
        if (
            lower.contains("aplicar sugestao") ||
            lower.contains("ativar tetos") ||
            lower.contains("aplicar tetos") ||
            lower.contains("ativar sugestao")
        ) {
            return executeManageBudgets(acao = "aplicar_sugestao_ia")
        }

        // 2.4. Envelopes Orçamentários: Sugerir Tetos / Regra 50/30/20
        if (
            lower.contains("sugerir tetos") ||
            lower.contains("sugira tetos") ||
            lower.contains("sugerir envelopes") ||
            lower.contains("50/30/20") ||
            lower.contains("50 30 20")
        ) {
            return executeManageBudgets(acao = "sugerir_tetos")
        }

        // 2.5. Envelopes Orçamentários: Rebalanceamento Automático
        if (
            lower.contains("rebalancear") ||
            lower.contains("rebalanceamento") ||
            lower.contains("cobrir estouro")
        ) {
            return executeManageBudgets(acao = "rebalancear_automatico")
        }

        // 2.6. Envelopes Orçamentários: Simular Compra ("posso gastar 200 no jantar", "posso comprar 350 em lazer")
        val simRegex = Regex("""^(?:posso\s+(?:gastar|comprar)|simul(?:ar|e)\s+(?:compra|gasto)\s*(?:de)?)\s*r?\$?\s*([\d.,]+)\s*(?:em|no|na|com|de)?\s*(.*)$""", RegexOption.IGNORE_CASE)
        val simMatch = simRegex.find(trimmed)
        if (simMatch != null) {
            val amt = neuralClassifier.parseAmount(simMatch.groupValues[1])
            val desc = simMatch.groupValues[2].replace("?", "").trim()
            if (amt != null && amt > 0.0) {
                val catGuess = if (desc.isNotBlank()) neuralClassifier.guessCategory(desc) else "Lazer"
                return executeManageBudgets(
                    acao = "simular_compra",
                    categoria = catGuess,
                    valorSimulacao = amt,
                    descricaoSimulacao = desc.ifBlank { catGuess }
                )
            }
        }

        // 2.7. Envelopes Orçamentários: Consultar Envelopes
        if (
            lower.contains("envelope") ||
            lower.contains("tetos de gastos") ||
            lower.contains("orcamento por categoria") ||
            lower.contains("meus tetos")
        ) {
            return executeManageBudgets(acao = "consultar")
        }

        // 3. Consulta de Saldo Livre e Limite Diário
        if (lower.contains("saldo livre") || lower.contains("quanto tenho livre") || lower == "saldo") {
            val free = FinanceEngine.round2(summary.freeBalance)
            return ToolExecutionResult(
                text = "💰 Seu **Saldo Livre real** neste momento é de **${FinanceEngine.formatCurrency(free)}**.\n\n" +
                        "• **Total de Receitas**: ${FinanceEngine.formatCurrency(summary.income)}\n" +
                        "• **Total Comprometido**: ${FinanceEngine.formatCurrency(summary.committed)}\n" +
                        (if (summary.dailyLimit != null && summary.dailyLimit > 0) "• **Limite Diário Seguro**: ${FinanceEngine.formatCurrency(summary.dailyLimit)}/dia\n" else ""),
                actionCard = ActionCardData(
                    title = "Saldo Livre Real",
                    description = FinanceEngine.formatCurrency(free),
                    detail = "Comprometido: ${FinanceEngine.formatCurrency(summary.committed)} | Receitas: ${FinanceEngine.formatCurrency(summary.income)}"
                )
            )
        }

        if (lower.contains("limite diario") || lower.contains("posso gastar hoje") || lower.contains("limite por dia")) {
            val daily = summary.dailyLimit
            return if (daily != null && daily > 0) {
                ToolExecutionResult(
                    text = "💡 Seu **Limite Diário Seguro** é de **${FinanceEngine.formatCurrency(daily)}/dia**.\n\n" +
                            "Restam **${summary.daysRemaining} dias** até o encerramento do mês. Gastando até esse valor por dia, você encerra com Saldo Livre positivo!"
                )
            } else {
                ToolExecutionResult(
                    text = "⚠️ Seu Saldo Livre está insuficiente ou encerrado neste mês. Evite gastos supérfluos."
                )
            }
        }

        // 4. Atualização de Conta Prevista (ex: "a conta de luz veio 180", "paguei a enel de 150")
        val billRegex = Regex("""(?:conta de|boleto d[eo]|fatura d[eo])?\s*(luz|energia|enel|água|agua|sabesp|internet|condomínio|condominio|aluguel|gás|gas|financiamento)\s*(?:veio|chegou|deu|paguei|ficou em|foi)\s*r?\$?\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
        val billMatch = billRegex.find(trimmed)
        if (billMatch != null) {
            val billName = billMatch.groupValues[1]
            val amt = neuralClassifier.parseAmount(billMatch.groupValues[2])
            if (amt != null && amt > 0.0) {
                val isPaid = lower.contains("paguei") || lower.contains("quitei")
                return executeUpdateEstimated(
                    searchTerm = billName,
                    actualAmount = amt,
                    status = if (isPaid) TransactionStatus.PAID else TransactionStatus.PENDING
                )
            }
        }

        // 5. Exclusão de Transação (ex: "apague a compra do Burger King", "exclua o gasto de 50 reais")
        val deleteRegex = Regex("""^(?:apague|apagar|exclua|excluir|delete|deletar|remover|remova)\s+(?:a compra|o gasto|a despesa|o lançamento|o lancamento|a|o)?\s*(.+)$""", RegexOption.IGNORE_CASE)
        val deleteMatch = deleteRegex.find(trimmed)
        if (deleteMatch != null) {
            val targetPart = deleteMatch.groupValues[1].trim()
            val parsedAmt = neuralClassifier.parseAmount(targetPart)
            val cleanTerm = targetPart.replace(Regex("""r?\$?\s*[\d.,]+"""), "").trim()
            val deleteAll = lower.contains("todas as parcelas") || lower.contains("parcelamento")

            return executeDeleteTransaction(
                searchTerm = cleanTerm.ifBlank { null },
                amount = parsedAmt,
                deleteAllInstallments = deleteAll,
                confirmed = false
            )
        }

        // 6. Edição de Transação (ex: "mude a compra da padaria para 25", "altere o uber para transporte")
        val editRegex = Regex("""^(?:mude|mudar|altere|alterar|corrija|corrigir|atualize|atualizar|troque|trocar)\s+(?:a compra|o gasto|a despesa|o lançamento|o lancamento|a|o)?\s*(.+)$""", RegexOption.IGNORE_CASE)
        val editMatch = editRegex.find(trimmed)
        if (editMatch != null) {
            val rest = editMatch.groupValues[1].trim()
            val forMatch = Regex("""(.+?)\s+para\s+(.+)""", RegexOption.IGNORE_CASE).find(rest)
            if (forMatch != null) {
                val term = forMatch.groupValues[1].trim()
                val targetChange = forMatch.groupValues[2].trim()
                val amtChange = neuralClassifier.parseAmount(targetChange)

                return if (amtChange != null) {
                    executeEditTransaction(searchTerm = term, newAmount = amtChange, confirmed = false)
                } else {
                    val (strictCat, strictSub) = CategoryRegistry.resolveStrictCategory(targetChange)
                    if (strictCat != "Outros") {
                        executeEditTransaction(searchTerm = term, newCategory = strictCat, newSubcategory = strictSub, confirmed = false)
                    } else {
                        val catGuess = neuralClassifier.guessCategory(targetChange)
                        if (catGuess != "Outros") {
                            executeEditTransaction(searchTerm = term, newCategory = catGuess, confirmed = false)
                        } else {
                            executeEditTransaction(searchTerm = term, newDescription = targetChange, confirmed = false)
                        }
                    }
                }
            }
        }

        // 7. Consulta de Gastos e Localização de Despesas
        val queryRegex = Regex(
            """^(?:quanto gastei|quais(?: foram)?(?: as| os)? (?:compras|gastos|despesas|lançamentos)|mostrar(?: os)? (?:gastos|despesas|lançamentos)|listar(?: os)? (?:gastos|despesas|lançamentos)|localiz(?:ar|e|a)(?: uma| um| a| o)?(?: despesa| gasto| compra| lan[çc]amento)?(?: espec[íi]fic[ao])?|busc(?:ar|a|que)(?: uma| um| a| o)?(?: despesa| gasto| compra| lan[çc]amento)?(?: espec[íi]fic[ao])?|procur(?:ar|e|a)(?: uma| um| a| o)?(?: despesa| gasto| compra| lan[çc]amento)?(?: espec[íi]fic[ao])?|encontr(?:ar|e|a)(?: uma| um| a| o)?(?: despesa| gasto| compra| lan[çc]amento)?(?: espec[íi]fic[ao])?|pesquis(?:ar|e|a)(?: uma| um| a| o)?(?: despesa| gasto| compra| lan[çc]amento)?(?: espec[íi]fic[ao])?|consult(?:ar|e|a)(?: uma| um| a| o)?(?: despesa| gasto| compra| lan[çc]amento)?(?: espec[íi]fic[ao])?|gastos(?: de| do| da| no| na| em)?|compras(?: de| do| da| no| na| em)?|despesas(?: de| do| da| no| na| em)?|onde gastei)\b""",
            RegexOption.IGNORE_CASE
        )
        if (queryRegex.containsMatchIn(trimmed)) {
            val searchPart = trimmed.replace(queryRegex, "")
                .replace(Regex("""["'“”‘’]"""), "")
                .replace(Regex("""^\s*(?:de|por|em|no|na|nos|nas|com|para|chamad[ao]|com o nome|do|da|dos|das)\s+""", RegexOption.IGNORE_CASE), "")
                .trim()
            return executeQueryTransactions(searchTerm = searchPart.ifBlank { null })
        }

        // 8. Expandir Parcelamentos
        if (lower.contains("expandir parcelamento") || lower.contains("gerar parcelas")) {
            return executeExpandInstallments()
        }

        // 9. Cadastro Inteligente de Nova Transação (Expense / Income)
        val intent = neuralClassifier.parseNaturalIntent(trimmed)
        if (intent != null && intent.amount > 0.0) {
            val cards = repository.getCards().first()
            val card = if (intent.isCreditCard) {
                cards.find { it.cardName.contains(intent.cardNameHint ?: "", ignoreCase = true) } ?: cards.firstOrNull()
            } else null

            return executeCreateTransaction(
                description = intent.description,
                amount = intent.amount,
                type = intent.type,
                category = intent.category,
                subcategory = intent.subcategory,
                paymentMethod = if (intent.isCreditCard) PaymentMethod.CREDIT_CARD else intent.paymentMethod,
                cardName = card?.cardName,
                splitType = intent.splitType,
                thirdPartyName = intent.thirdPartyName,
                currentInstallment = intent.currentInstallment,
                totalInstallments = intent.totalInstallments,
                customCompetenceMonth = intent.competenceMonth,
                customDueDate = intent.dueDate
            )
        }

        return ToolExecutionResult(
            text = "Estou pronto para ajudar! Você pode me pedir para:\n\n" +
                    "• **Registrar**: *\"Gastei 45 no mercado\"* ou *\"Comprei TV de 1800 em 10x no cartão\"*\n" +
                    "• **Editar**: *\"Mude o valor da padaria para R$ 25\"* ou *\"Altere o Uber para Transporte\"*\n" +
                    "• **Excluir**: *\"Apague a compra do Burger King\"* ou *\"Exclua a despesa de R$ 50\"*\n" +
                    "• **Atualizar Conta**: *\"A conta de luz veio R$ 180\"*\n" +
                    "• **Consultar**: *\"Quanto gastei no mercado?\"* ou *\"Qual meu Saldo Livre?\"*"
        )
    }
}
