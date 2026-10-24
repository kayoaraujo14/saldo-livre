package com.saldolivre.app.data.ai

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.saldolivre.app.data.engine.FinanceEngine
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
        return try {
            val parts = dateStr.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].take(2).toInt()
            val ym = java.time.YearMonth.of(year, month).plusMonths(delta.toLong())
            val clampedDay = kotlin.math.min(day, ym.lengthOfMonth())
            String.format(java.util.Locale.US, "%04d-%02d-%02d", ym.year, ym.monthValue, clampedDay)
        } catch (_: Exception) {
            dateStr
        }
    }

    // -------------------------------------------------------------
    // EXECUÇÃO A PARTIR DE JSON (GEMINI OU RESPOSTA ESTRUTURADA)
    // -------------------------------------------------------------

    suspend fun executeFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val action = jsonObj.getStringOrNull("action") ?: jsonObj.getStringOrNull("toolName") ?: ""
        return when (action) {
            "CREATE_TRANSACTION", "registrarTransacao" -> executeCreateFromJson(jsonObj)
            "BATCH_TRANSACTIONS", "registrarTransacoesLote" -> executeBatchFromJson(jsonObj)
            "EDIT_TRANSACTION", "editarTransacoes" -> executeEditFromJson(jsonObj)
            "DELETE_TRANSACTION", "excluirTransacoes" -> executeDeleteFromJson(jsonObj)
            "UPDATE_ESTIMATED", "atualizarContaPrevista" -> executeUpdateEstimatedFromJson(jsonObj)
            "SEARCH_TRANSACTIONS", "buscarTransacoes", "QUERY_TRANSACTIONS", "consultarTransacoes" -> executeSearchFromJson(jsonObj)
            "MANAGE_CARDS", "gerenciarCartoes" -> executeManageCardsFromJson(jsonObj)
            "MANAGE_RECURRING", "gerenciarContasRecorrentes" -> executeManageRecurringFromJson(jsonObj)
            "EXPAND_INSTALLMENTS", "expandirParcelamentos" -> executeExpandInstallmentsFromJson(jsonObj)
            "GENERATE_CHART", "gerarGraficoFinanceiro" -> executeGenerateChartFromJson(jsonObj)
            "LEARN_RULE", "gerenciarRegrasEMemoria" -> {
                val op = jsonObj.getStringOrNull("acao") ?: jsonObj.getStringOrNull("operation") ?: "aprender"
                val rule = jsonObj.getStringOrNull("rule") ?: jsonObj.getStringOrNull("regra") ?: ""
                if (op.equals("esquecer", ignoreCase = true) || op.equals("delete", ignoreCase = true)) {
                    executeDeleteRule(rule)
                } else {
                    executeLearnRule(rule)
                }
            }
            "DELETE_RULE" -> {
                val rule = jsonObj.getStringOrNull("rule") ?: jsonObj.getStringOrNull("regra") ?: ""
                executeDeleteRule(rule)
            }
            else -> ToolExecutionResult(text = "Ação desconhecida solicitada: $action")
        }
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
        thirdPartyName: String? = null,
        currentInstallment: Int = 1,
        totalInstallments: Int = 1,
        customCompetenceMonth: String? = null,
        customDueDate: String? = null,
        confirmed: Boolean = false
    ): ToolExecutionResult {
        if (amount <= 0.0) {
            return ToolExecutionResult(text = "⚠️ O valor da transação deve ser maior que zero.")
        }

        val user = repository.currentUser.value
        val cards = repository.getCards().first()

        val matchedCard = if (!cardName.isNullOrBlank()) {
            cards.find { it.cardName.contains(cardName, ignoreCase = true) || cardName.contains(it.cardName, ignoreCase = true) }
        } else if (paymentMethod == PaymentMethod.CREDIT_CARD) {
            cards.firstOrNull()
        } else null

        val isCreditCard = paymentMethod == PaymentMethod.CREDIT_CARD || matchedCard != null
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
        val finalDueDate = customDueDate?.takeIf { it.isNotBlank() } ?: calcDueDate

        // Categorização inteligente se não informada
        var finalCat = category?.takeIf { it.isNotBlank() && it != "Outros" }
        var finalSubcat = subcategory?.takeIf { it.isNotBlank() }

        if (finalCat == null) {
            val guess = neuralClassifier.guessCategoryAndSubcategory(description, amount)
            finalCat = if (type == TransactionType.INCOME) "Receita" else guess.category
            finalSubcat = guess.subcategory ?: finalSubcat
        }

        val partner = repository.allUsers.find { it.uid != user.uid }
        val splitResult = FinanceEngine.buildSplit(
            splitType = splitType,
            total = amount,
            myUid = user.uid,
            myName = user.displayName,
            partnerUid = partner?.uid,
            partnerName = partner?.displayName,
            payerUid = user.uid,
            externalName = thirdPartyName,
            assignedFriendName = friendName ?: friendEmail
        )

        val cleanDesc = FinanceEngine.stripInstallmentSuffix(description)
        val isInstallment = totalInstallments > 1
        val cardText = if (matchedCard != null) " • Cartão ${matchedCard.cardName}" else ""
        val myImpact = splitResult.userImpact[user.uid] ?: amount

        // SE AINDA NÃO CONFIRMADO PELO USUÁRIO: Gera rascunho com pedido de confirmação interativo
        if (!confirmed) {
            val before = repository.getMonthSummary().first()
            val netDiff = if (type == TransactionType.INCOME) amount else -myImpact
            val estimatedAfter = FinanceEngine.round2(before.freeBalance + netDiff)
            val actionTitle = "${if (type == TransactionType.INCOME) "Receita" else "Despesa"}: $cleanDesc"

            val confirmMessage = if (isInstallment) {
                "Entendi que você deseja cadastrar uma despesa parcelada: **\"$cleanDesc\"** em **${totalInstallments}x de ${FinanceEngine.formatCurrency(amount)}** (total de ${FinanceEngine.formatCurrency(amount * totalInstallments)})${if (matchedCard != null) " no cartão **${matchedCard.cardName}**" else ""} a partir de **${FinanceEngine.monthLabel(finalCompetenceMonth)}**.\n\n" +
                        "• **Categoria**: $finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}\n" +
                        "• **Divisão**: ${splitType.label} (Seu impacto: ${FinanceEngine.formatCurrency(myImpact)}/mês)\n" +
                        "• **Impacto no Saldo Livre**: ${FinanceEngine.formatCurrency(before.freeBalance)} → **${FinanceEngine.formatCurrency(estimatedAfter)}**\n\n" +
                        "Por favor, confirme se os dados estão corretos para registrar no Saldo Livre."
            } else {
                val typeName = if (type == TransactionType.INCOME) "receita" else "despesa"
                "Entendi que você deseja cadastrar uma $typeName: **\"$cleanDesc\"** no valor de **${FinanceEngine.formatCurrency(amount)}**${if (matchedCard != null) " no cartão **${matchedCard.cardName}**" else ""} para **${FinanceEngine.monthLabel(finalCompetenceMonth)}** (vencimento: ${FinanceEngine.formatShortDate(finalDueDate)}).\n\n" +
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
                val targetDueDate = if (isCreditCard && matchedCard != null) {
                    FinanceEngine.dateInMonth(targetMonth, matchedCard.dueDay)
                } else {
                    shiftDateMonth(finalDueDate, delta)
                }

                val isPast = i < currentInstallment
                val status = if (isPast) TransactionStatus.PAID else TransactionStatus.PENDING

                installmentList.add(
                    Transaction(
                        id = "tx_" + UUID.randomUUID().toString().take(8),
                        createdByUid = user.uid,
                        paidByUid = user.uid,
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
                        splitType = splitType,
                        installmentGroupId = groupId,
                        installmentNumber = i,
                        installmentsTotal = totalInstallments
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
                paidByUid = user.uid,
                cardId = matchedCard?.id,
                description = cleanDesc,
                category = finalCat,
                subcategory = finalSubcat,
                type = type,
                paymentMethod = effectiveMethod,
                status = if (type == TransactionType.INCOME || !isCreditCard) TransactionStatus.PAID else TransactionStatus.PENDING,
                actualAmount = amount,
                totalAmount = amount,
                dueDate = finalDueDate,
                competenceMonth = finalCompetenceMonth,
                visibleToUids = splitResult.visibleToUids,
                userImpact = splitResult.userImpact,
                allocations = splitResult.allocations,
                splitType = splitType
            )

            repository.addTransaction(tx)

            repository.logActivity(
                ActivityLog(
                    id = "act_" + UUID.randomUUID().toString().take(8),
                    actionType = ActivityActionType.TRANSACTION_CREATED,
                    title = if (type == TransactionType.INCOME) "Receita Registrada" else "Despesa Registrada",
                    description = "$cleanDesc — ${FinanceEngine.formatCurrency(amount)}",
                    amount = amount,
                    competenceMonth = finalCompetenceMonth,
                    userUid = user.uid,
                    userName = user.displayName
                )
            )

            val summaryNow = repository.getMonthSummary().first()

            val typeLabel = if (type == TransactionType.INCOME) "Receita" else "Despesa"

            return ToolExecutionResult(
                text = "✅ **$typeLabel registrada com sucesso!**\n\n" +
                        "• **Descrição**: $cleanDesc\n" +
                        "• **Valor**: ${FinanceEngine.formatCurrency(amount)}\n" +
                        "• **Categoria**: $finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}\n" +
                        "• **Competência**: ${FinanceEngine.monthLabel(finalCompetenceMonth)} (Vencimento: ${FinanceEngine.formatShortDate(finalDueDate)})$cardText\n" +
                        "• **Divisão**: ${splitType.label} (Seu impacto: ${FinanceEngine.formatCurrency(myImpact)})\n\n" +
                        "Seu **Saldo Livre** foi atualizado em tempo real!",
                actionCard = ActionCardData(
                    title = if (type == TransactionType.INCOME) "Receita Registrada" else "Despesa Registrada",
                    description = "$cleanDesc — ${FinanceEngine.formatCurrency(amount)}",
                    detail = "$finalCat${if (!finalSubcat.isNullOrBlank()) " › $finalSubcat" else ""}$cardText • ${splitType.label}",
                    newFreeBalance = "Saldo Livre em ${FinanceEngine.monthLabel(finalCompetenceMonth)}: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
                )
            )
        }
    }

    private suspend fun executeCreateFromJson(jsonObj: JsonObject): ToolExecutionResult {
        var desc = jsonObj.getStringOrNull("description") ?: "Lançamento via Copiloto"
        desc = neuralClassifier.extractDescription(desc).ifBlank { desc }
        val amount = jsonObj.getDoubleOrNull("amount") ?: 0.0
        val typeStr = jsonObj.getStringOrNull("type") ?: "expense"
        val category = jsonObj.getStringOrNull("category")
        val subcategory = jsonObj.getStringOrNull("subcategory")
        val payMethodStr = jsonObj.getStringOrNull("paymentMethod") ?: "pix"
        val cardName = jsonObj.getStringOrNull("cardName")
        val splitTypeStr = jsonObj.getStringOrNull("splitType") ?: "MINE"
        val friendName = jsonObj.getStringOrNull("friendName")
        val friendEmail = jsonObj.getStringOrNull("friendEmail")
        val thirdPartyName = jsonObj.getStringOrNull("thirdPartyName")
        val competenceMonth = jsonObj.getStringOrNull("competenceMonth")
        val dueDate = jsonObj.getStringOrNull("dueDate")
        val confirmed = jsonObj.getBooleanOrNull("confirmed")
            ?: jsonObj.getBooleanOrNull("__confirmed")
            ?: false

        val installmentsObj = jsonObj.getAsJsonObjectOrNull("installments")
        val currentInstallment = installmentsObj.getIntOrNull("current") ?: 1
        val totalInstallments = installmentsObj.getIntOrNull("total") ?: 1

        val txType = if (typeStr.equals("income", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE
        val payMethod = PaymentMethod.from(payMethodStr)
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
            amount = amount,
            type = txType,
            category = category,
            subcategory = subcategory,
            paymentMethod = payMethod,
            cardName = cardName,
            splitType = splitType,
            friendName = friendName,
            friendEmail = friendEmail,
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
        val createdList = mutableListOf<Transaction>()
        val pendingTxMaps = mutableListOf<Map<String, Any?>>()
        var totalExpenses = 0.0
        var totalIncomes = 0.0

        for (elem in array) {
            if (!elem.isJsonObject) continue
            val item = elem.asJsonObject
            val filterStatus = item.getStringOrNull("categoria_filtro") ?: "valida"
            val desc = item.getStringOrNull("description")
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
            val isIncome = typeStr.equals("income", ignoreCase = true) || rawAmt < 0
            val cat = item.getStringOrNull("category") ?: item.getStringOrNull("categoria") ?: if (isIncome) "Receita" else "Outros"
            val subcat = item.getStringOrNull("subcategory") ?: item.getStringOrNull("subcategoria")
            val payMethodStr = item.getStringOrNull("paymentMethod") ?: item.getStringOrNull("metodoPagamento") ?: defaultPayMethod
            val dt = item.getStringOrNull("date") ?: item.getStringOrNull("data") ?: item.getStringOrNull("dataVencimento") ?: FinanceEngine.todayKey()
            val comp = item.getStringOrNull("competenceMonth") ?: item.getStringOrNull("mesFatura") ?: defaultMonth

            pendingTxMaps.add(
                mapOf(
                    "data" to dt,
                    "tipo" to if (isIncome) "income" else "expense",
                    "valor_exato" to amt,
                    "contraparte" to desc,
                    "categoria_filtro" to filterStatus,
                    "categoria" to cat,
                    "subcategoria" to subcat,
                    "metodoPagamento" to payMethodStr,
                    "motivoIgnorado" to item.getStringOrNull("motivoIgnorado")
                )
            )
            if (filterStatus.equals("ignorada", ignoreCase = true)) continue

            val tx = Transaction(
                id = "tx_" + UUID.randomUUID().toString().take(8),
                createdByUid = user.uid,
                paidByUid = user.uid,
                description = desc,
                category = cat,
                subcategory = subcat,
                type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.from(payMethodStr),
                status = if (isIncome) TransactionStatus.PAID else TransactionStatus.PENDING,
                actualAmount = amt,
                totalAmount = amt,
                dueDate = dt,
                competenceMonth = comp,
                visibleToUids = listOf(user.uid),
                userImpact = mapOf(user.uid to amt),
                allocations = listOf(Allocation(type = "PLATFORM_USER", uid = user.uid, name = user.displayName, amount = amt, reimbursed = true))
            )
            createdList.add(tx)
            if (isIncome) totalIncomes += amt else totalExpenses += amt
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
        transactionId: String? = null,
        targetIds: List<String>? = null,
        month: String? = null,
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

        var targets = if (!targetIds.isNullOrEmpty()) {
            all.filter { it.id in targetIds }
        } else if (!transactionId.isNullOrBlank()) {
            all.filter { it.id == transactionId }
        } else if (!searchTerm.isNullOrBlank()) {
            all.filter { t ->
                if (!month.isNullOrBlank() && t.competenceMonth != month) return@filter false
                matchesSearch(t.description, searchTerm) || matchesSearch(t.category, searchTerm) || (t.subcategory != null && matchesSearch(t.subcategory, searchTerm))
            }
        } else emptyList()

        if (targets.isEmpty()) {
            return ToolExecutionResult(
                text = "⚠️ Nenhuma transação foi encontrada com o termo \"${searchTerm ?: transactionId}\" para edição."
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
        val changesList = mutableListOf<String>()

        if (newDescription != null) {
            changesList.add("Descrição: \"$newDescription\"")
        }
        if (newAmount != null && newAmount > 0.0) {
            changesList.add("Valor: ${FinanceEngine.formatCurrency(firstTarget.effectiveAmount)} → ${FinanceEngine.formatCurrency(newAmount)}")
        }
        if (newCategory != null) {
            changesList.add("Categoria: $newCategory")
        }
        if (newSubcategory != null) {
            changesList.add("Subcategoria: $newSubcategory")
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
            changesList.add("Vencimento: ${FinanceEngine.formatShortDate(newDueDate)}")
        }
        if (newSplitType != null) {
            changesList.add("Divisão: ${newSplitType.label}")
        }

        if (changesList.isEmpty()) {
            return ToolExecutionResult(
                text = "⚠️ Nenhuma alteração foi informada para o lançamento \"$firstDesc\"."
            )
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
                "Entendi que você deseja alterar **$updatedCount lançamentos** vinculados a **\"$firstDesc\"**:\n\n" +
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
            val actionDesc = if (updatedCount > 1) "$firstDesc (+${updatedCount - 1} parcelas)" else "$firstDesc (${FinanceEngine.formatCurrency(newAmount ?: firstTarget.effectiveAmount)})"

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
                    "month" to month,
                    "newAmount" to newAmount,
                    "newDescription" to newDescription,
                    "newCategory" to newCategory,
                    "newSubcategory" to newSubcategory,
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

            if (newCategory != null) {
                updated = updated.copy(category = newCategory)
            }

            if (newSubcategory != null) {
                updated = updated.copy(subcategory = newSubcategory)
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
                updated = updated.copy(dueDate = newDueDate)
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
                    "• **Item(ns) alterado(s)**: $firstDesc" + (if (updatedCount > 1) " (+${updatedCount - 1} parcelas sincronizadas)" else "") + "\n" +
                    "• **Alterações realizadas**:\n" + changesList.joinToString("\n") { "  - $it" } + "\n\n" +
                    "Seu **Saldo Livre** foi recalculado no banco de dados.",
            actionCard = ActionCardData(
                title = "Lançamento Editado",
                description = firstDesc + (if (updatedCount > 1) " ($updatedCount parcelas)" else ""),
                detail = changesList.joinToString(" • "),
                newFreeBalance = "Saldo Livre Atual: ${FinanceEngine.formatCurrency(summaryNow.freeBalance)}"
            )
        )
    }

    private suspend fun executeEditFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val searchTerm = jsonObj.getStringOrNull("searchTerm") ?: jsonObj.getStringOrNull("termoBusca")
        val transactionId = jsonObj.getStringOrNull("transactionId") ?: jsonObj.getStringOrNull("id")
        val targetIdsArray = jsonObj.getAsJsonArrayOrNull("targetIds") ?: jsonObj.getAsJsonArrayOrNull("ids")
        val targetIds = targetIdsArray?.mapNotNull { it.asString }
        val month = jsonObj.getStringOrNull("month") ?: jsonObj.getStringOrNull("mes")
        val newAmount = jsonObj.getDoubleOrNull("newAmount") ?: jsonObj.getDoubleOrNull("novoValor")
        val newDescription = jsonObj.getStringOrNull("newDescription") ?: jsonObj.getStringOrNull("novaDescricao")
        val newCategory = jsonObj.getStringOrNull("newCategory") ?: jsonObj.getStringOrNull("novaCategoria")
        val newSubcategory = jsonObj.getStringOrNull("newSubcategory") ?: jsonObj.getStringOrNull("novaSubcategoria")
        val newStatusStr = jsonObj.getStringOrNull("newStatus") ?: jsonObj.getStringOrNull("novoStatus")
        val newPayMethodStr = jsonObj.getStringOrNull("newPaymentMethod") ?: jsonObj.getStringOrNull("novoMetodoPagamento")
        val newCompetenceMonth = jsonObj.getStringOrNull("newCompetenceMonth") ?: jsonObj.getStringOrNull("novoMesFatura")
        val newDueDate = jsonObj.getStringOrNull("newDueDate") ?: jsonObj.getStringOrNull("novaDataVencimento")
        val newSplitTypeStr = jsonObj.getStringOrNull("newSplitType") ?: jsonObj.getStringOrNull("novoTipoDivisao")
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

        return executeEditTransaction(
            searchTerm = searchTerm,
            transactionId = transactionId,
            targetIds = targetIds,
            month = month,
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
                if (amount != null && kotlin.math.abs(t.effectiveAmount - amount) > 0.05) return@filter false
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
        person: String? = null,
        onlyThirdParties: Boolean = false,
        cardFilter: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        type: TransactionType? = null
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
                if (t.type != TransactionType.EXPENSE && !t.category.equals("Receita", ignoreCase = true)) return@filter false
            }

            // Intervalo de datas
            if (!startDate.isNullOrBlank() && t.dueDate < startDate) return@filter false
            if (!endDate.isNullOrBlank() && t.dueDate > endDate) return@filter false

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

            // FILTRO CRÍTICO: SOMENTE 100% MEU (SEM DIVISÕES NEM TERCEIROS)
            if (onlyMine) {
                if (t.category.equals("Cartão Terceiros", ignoreCase = true)) return@filter false
                if (t.splitType != null && t.splitType != SplitType.MINE) return@filter false
                if (!t.assignedFriendUid.isNullOrBlank() || !t.assignedFriendName.isNullOrBlank()) return@filter false
                if (t.allocations.any { it.uid != user.uid && it.amount > 0 }) return@filter false
                val myImp = t.userImpact[user.uid] ?: t.effectiveAmount
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
                val myImp = t.userImpact[user.uid] ?: t.effectiveAmount
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

        // 2. Seu Impacto Real nas Despesas (Sua Parte)
        val myExpensesGrossImpact = expenseTxs.sumOf { it.userImpact[user.uid] ?: it.effectiveAmount }
        val myRefundsImpact = refundTxs.sumOf { it.userImpact[user.uid] ?: it.effectiveAmount }
        val myImpactTotal = kotlin.math.max(0.0, myExpensesGrossImpact - myRefundsImpact)

        // 3. Cota de Terceiros (A Receber)
        val thirdPartyTotal = kotlin.math.max(0.0, totalExpensesGross - myExpensesGrossImpact)

        // 4. Entradas / Receitas do Período (separadas para nunca distorcerem a soma das despesas)
        val totalIncome = incomeTxs.sumOf { it.effectiveAmount }

        // Agrupamento por subcategoria calculado exclusivamente sobre as despesas para totalizar fielmente
        val subcategoryGroups = expenseTxs.groupBy { it.subcategory ?: it.category.ifBlank { "Geral" } }
        val subcategoryBreakdownText = if (subcategoryGroups.size > 1) {
            val breakdownLines = subcategoryGroups.entries
                .sortedByDescending { it.value.sumOf { tx -> tx.effectiveAmount } }
                .take(6)
                .joinToString("\n") { (sub, txs) ->
                    val subTotal = txs.sumOf { it.effectiveAmount }
                    val subMyPart = txs.sumOf { it.userImpact[user.uid] ?: it.effectiveAmount }
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

            val myImpact = t.userImpact[user.uid] ?: t.effectiveAmount
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
            "• **${t.description}**: ${FinanceEngine.formatCurrency(t.effectiveAmount)}$splitNote • $typeBadge • ${t.category}$sub • ${t.status.label} (${FinanceEngine.formatShortDate(t.dueDate)}) • $methodOrCard$monthNote"
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
        person: String? = null,
        onlyThirdParties: Boolean = false,
        cardFilter: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        type: TransactionType? = null
    ): ToolExecutionResult = executeSearchTransactions(
        searchTerm = searchTerm,
        category = category,
        subcategory = subcategory,
        month = month,
        status = status,
        onlyMine = onlyMine,
        person = person,
        onlyThirdParties = onlyThirdParties,
        cardFilter = cardFilter,
        startDate = startDate,
        endDate = endDate,
        type = type
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
            person = person,
            onlyThirdParties = onlyThirdParties,
            cardFilter = cardFilter,
            startDate = startDate,
            endDate = endDate,
            type = type
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
        cardId: String? = null
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
        val name = jsonObj.getStringOrNull("cardName") ?: jsonObj.getStringOrNull("nome")
        val closing = jsonObj.getIntOrNull("closingDay") ?: jsonObj.getIntOrNull("fechamento")
        val due = jsonObj.getIntOrNull("dueDay") ?: jsonObj.getIntOrNull("vencimento")
        val id = jsonObj.getStringOrNull("cardId") ?: jsonObj.getStringOrNull("id")

        return executeManageCards(operation = op, cardName = name, closingDay = closing, dueDay = due, cardId = id)
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
        billId: String? = null
    ): ToolExecutionResult {
        val bills = repository.getRecurringBills().first()
        val user = repository.currentUser.value

        when (operation.lowercase()) {
            "listar" -> {
                if (bills.isEmpty()) {
                    return ToolExecutionResult(text = "⚡ Você não possui nenhuma conta recorrente cadastrada.")
                }
                val list = bills.joinToString("\n") {
                    val status = if (it.active) "🟢 Ativa" else "⏸️ Pausada"
                    "• **${it.description}**: ${FinanceEngine.formatCurrency(it.defaultAmount)} (Vence dia ${it.dueDay}) • ${it.category} • [$status]"
                }
                return ToolExecutionResult(
                    text = "⚡ **Suas Contas Fixas e Assinaturas Recorrentes:**\n\n$list",
                    actionCard = ActionCardData(title = "Contas Recorrentes", description = "${bills.size} contas cadastradas")
                )
            }
            "adicionar" -> {
                val desc = description?.trim()
                val amt = amount ?: 0.0
                if (desc.isNullOrBlank() || amt <= 0.0) {
                    return ToolExecutionResult(text = "⚠️ Informe o nome e o valor da conta recorrente.")
                }
                val guessed = neuralClassifier.guessCategoryAndSubcategory(desc, amt)
                val newBill = RecurringBill(
                    id = "rec_" + UUID.randomUUID().toString().take(8),
                    ownerUid = user.uid,
                    description = desc,
                    defaultAmount = amt,
                    dueDay = dueDay ?: 10,
                    category = category ?: guessed.category,
                    subcategory = subcategory ?: guessed.subcategory,
                    active = true
                )
                repository.saveRecurringBill(newBill)
                return ToolExecutionResult(
                    text = "⚡ **Conta recorrente adicionada com sucesso!**\n\n" +
                            "• **Assinatura**: $desc\n" +
                            "• **Valor Mensal**: ${FinanceEngine.formatCurrency(amt)}\n" +
                            "• **Vencimento**: Todo dia ${newBill.dueDay}\n" +
                            "• **Categoria**: ${newBill.category}\n\n" +
                            "Essa conta será projetada automaticamente nos seus próximos meses!",
                    actionCard = ActionCardData(
                        title = "Assinatura Salva",
                        description = "$desc — ${FinanceEngine.formatCurrency(amt)}",
                        detail = "Todo dia ${newBill.dueDay} • ${newBill.category}"
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

        return executeManageRecurring(operation = op, description = desc, amount = amt, dueDay = day, category = cat, subcategory = sub, billId = id)
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
                val type = if ((args["type"] as? String)?.equals("income", ignoreCase = true) == true) TransactionType.INCOME else TransactionType.EXPENSE
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
                        addProperty("confirmed", true)
                    }
                    executeBatchFromJson(jsonObj)
                } else {
                    ToolExecutionResult(text = "⚠️ Não foi possível recuperar as transações do lote.")
                }
            }
            "EDIT_TRANSACTION", "editarTransacoes" -> {
                val term = args["searchTerm"] as? String ?: args["termoBusca"] as? String
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
                val newDueDate = args["newDueDate"] as? String ?: args["novaDataVencimento"] as? String
                val newSplitTypeStr = args["newSplitType"] as? String ?: args["novoTipoDivisao"] as? String
                val newSplitType = when (newSplitTypeStr?.uppercase()) {
                    "SPLIT_50", "CASAL_50_50" -> SplitType.SPLIT_50
                    "PARTNER", "PARCEIRO" -> SplitType.PARTNER
                    "FRIEND", "AMIGO" -> SplitType.FRIEND
                    "EXTERNAL", "TERCEIRO" -> SplitType.EXTERNAL
                    else -> if (newSplitTypeStr != null) SplitType.MINE else null
                }

                executeEditTransaction(
                    searchTerm = term,
                    transactionId = id,
                    targetIds = targetIds,
                    month = month,
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
                    val catGuess = neuralClassifier.guessCategory(targetChange)
                    if (catGuess != "Outros") {
                        executeEditTransaction(searchTerm = term, newCategory = catGuess, confirmed = false)
                    } else {
                        executeEditTransaction(searchTerm = term, newDescription = targetChange, confirmed = false)
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
