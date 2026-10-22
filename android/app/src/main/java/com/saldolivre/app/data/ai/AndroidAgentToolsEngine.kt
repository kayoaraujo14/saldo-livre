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
        val action = jsonObj.get("action")?.asString ?: jsonObj.get("toolName")?.asString ?: ""
        return when (action) {
            "CREATE_TRANSACTION", "registrarTransacao" -> executeCreateFromJson(jsonObj)
            "BATCH_TRANSACTIONS", "registrarTransacoesLote" -> executeBatchFromJson(jsonObj)
            "EDIT_TRANSACTION", "editarTransacoes" -> executeEditFromJson(jsonObj)
            "DELETE_TRANSACTION", "excluirTransacoes" -> executeDeleteFromJson(jsonObj)
            "UPDATE_ESTIMATED", "atualizarContaPrevista" -> executeUpdateEstimatedFromJson(jsonObj)
            "QUERY_TRANSACTIONS", "consultarTransacoes" -> executeQueryFromJson(jsonObj)
            "MANAGE_CARDS", "gerenciarCartoes" -> executeManageCardsFromJson(jsonObj)
            "MANAGE_RECURRING", "gerenciarContasRecorrentes" -> executeManageRecurringFromJson(jsonObj)
            "EXPAND_INSTALLMENTS", "expandirParcelamentos" -> executeExpandInstallmentsFromJson(jsonObj)
            "GENERATE_CHART", "gerarGraficoFinanceiro" -> executeGenerateChartFromJson(jsonObj)
            "LEARN_RULE", "gerenciarRegrasEMemoria" -> {
                val op = jsonObj.get("acao")?.asString ?: jsonObj.get("operation")?.asString ?: "aprender"
                val rule = jsonObj.get("rule")?.asString ?: jsonObj.get("regra")?.asString ?: ""
                if (op.equals("esquecer", ignoreCase = true) || op.equals("delete", ignoreCase = true)) {
                    executeDeleteRule(rule)
                } else {
                    executeLearnRule(rule)
                }
            }
            "DELETE_RULE" -> {
                val rule = jsonObj.get("rule")?.asString ?: jsonObj.get("regra")?.asString ?: ""
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
        var desc = jsonObj.get("description")?.asString ?: "Lançamento via Copiloto"
        desc = neuralClassifier.extractDescription(desc).ifBlank { desc }
        val amount = jsonObj.get("amount")?.asDouble ?: 0.0
        val typeStr = jsonObj.get("type")?.asString ?: "expense"
        val category = jsonObj.get("category")?.asString
        val subcategory = jsonObj.get("subcategory")?.asString
        val payMethodStr = jsonObj.get("paymentMethod")?.asString ?: "pix"
        val cardName = jsonObj.get("cardName")?.asString
        val splitTypeStr = jsonObj.get("splitType")?.asString ?: "MINE"
        val friendName = jsonObj.get("friendName")?.asString
        val friendEmail = jsonObj.get("friendEmail")?.asString
        val thirdPartyName = jsonObj.get("thirdPartyName")?.asString
        val competenceMonth = jsonObj.get("competenceMonth")?.asString
        val dueDate = jsonObj.get("dueDate")?.asString
        val confirmed = jsonObj.get("confirmed")?.asBoolean
            ?: jsonObj.get("__confirmed")?.asBoolean
            ?: false

        val installmentsObj = jsonObj.getAsJsonObject("installments")
        val currentInstallment = installmentsObj?.get("current")?.asInt ?: 1
        val totalInstallments = installmentsObj?.get("total")?.asInt ?: 1

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
        val array = jsonObj.getAsJsonArray("transactions") ?: jsonObj.getAsJsonArray("transacoes")
        if (array == null || array.size() == 0) {
            return ToolExecutionResult(text = "⚠️ Nenhuma transação encontrada no lote para registrar.")
        }

        val confirmed = jsonObj.get("confirmed")?.asBoolean
            ?: jsonObj.get("__confirmed")?.asBoolean
            ?: false

        val user = repository.currentUser.value
        val defaultMonth = jsonObj.get("mesFaturaPadrao")?.asString
            ?: repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }
        val defaultPayMethod = jsonObj.get("metodoPagamentoPadrao")?.asString ?: "pix"
        val createdList = mutableListOf<Transaction>()
        val pendingTxMaps = mutableListOf<Map<String, Any?>>()
        var totalExpenses = 0.0
        var totalIncomes = 0.0

        for (elem in array) {
            if (!elem.isJsonObject) continue
            val item = elem.asJsonObject
            val filterStatus = item.get("categoria_filtro")?.asString ?: "valida"
            val desc = item.get("description")?.asString
                ?: item.get("descricao")?.asString
                ?: item.get("contraparte")?.asString
                ?: "Item de Lote"
            val rawAmt = item.get("amount")?.asDouble
                ?: item.get("valorTotal")?.asDouble
                ?: item.get("valor_exato")?.asDouble
                ?: item.get("valor")?.asDouble
                ?: 0.0
            val amt = kotlin.math.abs(rawAmt)
            if (amt <= 0.0) continue

            val typeStr = item.get("type")?.asString ?: item.get("tipo")?.asString ?: if (rawAmt < 0) "income" else "expense"
            val isIncome = typeStr.equals("income", ignoreCase = true) || rawAmt < 0
            val cat = item.get("category")?.asString ?: item.get("categoria")?.asString ?: if (isIncome) "Receita" else "Outros"
            val subcat = item.get("subcategory")?.asString ?: item.get("subcategoria")?.asString
            val payMethodStr = item.get("paymentMethod")?.asString ?: item.get("metodoPagamento")?.asString ?: defaultPayMethod
            val dt = item.get("date")?.asString ?: item.get("data")?.asString ?: item.get("dataVencimento")?.asString ?: FinanceEngine.todayKey()
            val comp = item.get("competenceMonth")?.asString ?: item.get("mesFatura")?.asString ?: defaultMonth

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
                    "motivoIgnorado" to item.get("motivoIgnorado")?.asString
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
        month: String? = null,
        newAmount: Double? = null,
        newDescription: String? = null,
        newCategory: String? = null,
        newSubcategory: String? = null,
        newStatus: TransactionStatus? = null,
        newPaymentMethod: PaymentMethod? = null,
        newCompetenceMonth: String? = null,
        newDueDate: String? = null,
        newSplitType: SplitType? = null
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val user = repository.currentUser.value

        var targets = if (!transactionId.isNullOrBlank()) {
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
        val changesList = mutableListOf<String>()

        for (tx in targets) {
            var updated = tx

            if (newDescription != null) {
                val clean = FinanceEngine.stripInstallmentSuffix(newDescription)
                val finalDesc = if (tx.isInstallment && tx.installmentNumber != null && tx.installmentsTotal != null) {
                    FinanceEngine.formatInstallmentDesc(clean, tx.installmentNumber, tx.installmentsTotal)
                } else clean
                updated = updated.copy(description = finalDesc)
                if ("Descrição" !in changesList) changesList.add("Descrição: $finalDesc")
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
                if ("Valor" !in changesList) changesList.add("Valor: ${FinanceEngine.formatCurrency(newAmount)}")
            }

            if (newCategory != null) {
                updated = updated.copy(category = newCategory)
                if ("Categoria" !in changesList) changesList.add("Categoria: $newCategory")
            }

            if (newSubcategory != null) {
                updated = updated.copy(subcategory = newSubcategory)
                if ("Subcategoria" !in changesList) changesList.add("Subcategoria: $newSubcategory")
            }

            if (newStatus != null) {
                updated = updated.copy(status = newStatus)
                if ("Status" !in changesList) changesList.add("Status: ${newStatus.label}")
            }

            if (newPaymentMethod != null) {
                updated = updated.copy(paymentMethod = newPaymentMethod)
                if ("Método de Pagamento" !in changesList) changesList.add("Método: ${newPaymentMethod.label}")
            }

            if (newCompetenceMonth != null && newCompetenceMonth.matches(Regex("""^\d{4}-\d{2}$"""))) {
                if (tx.isInstallment && tx.installmentNumber != null) {
                    val delta = tx.installmentNumber - 1
                    updated = updated.copy(competenceMonth = FinanceEngine.shiftMonth(newCompetenceMonth, delta))
                } else {
                    updated = updated.copy(competenceMonth = newCompetenceMonth)
                }
                if ("Mês da Fatura" !in changesList) changesList.add("Mês da Fatura: ${FinanceEngine.monthLabel(newCompetenceMonth)}")
            }

            if (newDueDate != null && newDueDate.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
                updated = updated.copy(dueDate = newDueDate)
                if ("Vencimento" !in changesList) changesList.add("Vencimento: ${FinanceEngine.formatShortDate(newDueDate)}")
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
                if ("Divisão" !in changesList) changesList.add("Divisão: ${newSplitType.label}")
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
        val firstDesc = targets.first().description

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
        val searchTerm = jsonObj.get("searchTerm")?.asString
        val transactionId = jsonObj.get("transactionId")?.asString
        val month = jsonObj.get("month")?.asString
        val newAmount = jsonObj.get("newAmount")?.asDouble
        val newDescription = jsonObj.get("newDescription")?.asString
        val newCategory = jsonObj.get("newCategory")?.asString
        val newSubcategory = jsonObj.get("newSubcategory")?.asString
        val newStatusStr = jsonObj.get("newStatus")?.asString
        val newPayMethodStr = jsonObj.get("newPaymentMethod")?.asString
        val newCompetenceMonth = jsonObj.get("newCompetenceMonth")?.asString
        val newDueDate = jsonObj.get("newDueDate")?.asString
        val newSplitTypeStr = jsonObj.get("newSplitType")?.asString

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
            month = month,
            newAmount = newAmount,
            newDescription = newDescription,
            newCategory = newCategory,
            newSubcategory = newSubcategory,
            newStatus = newStatus,
            newPaymentMethod = newPayMethod,
            newCompetenceMonth = newCompetenceMonth,
            newDueDate = newDueDate,
            newSplitType = newSplitType
        )
    }

    // -------------------------------------------------------------
    // 4. DELETE TRANSACTION (EXCLUSÃO CIRÚRGICA / PARCELAS)
    // -------------------------------------------------------------

    suspend fun executeDeleteTransaction(
        searchTerm: String? = null,
        transactionId: String? = null,
        amount: Double? = null,
        month: String? = null,
        deleteAllInstallments: Boolean = false
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val user = repository.currentUser.value

        val target = if (!transactionId.isNullOrBlank()) {
            all.find { it.id == transactionId }
        } else if (!searchTerm.isNullOrBlank() || amount != null) {
            all.filter { t ->
                if (!month.isNullOrBlank() && t.competenceMonth != month) return@filter false
                if (amount != null && kotlin.math.abs(t.effectiveAmount - amount) > 0.05) return@filter false
                if (!searchTerm.isNullOrBlank() && !matchesSearch(t.description, searchTerm)) return@filter false
                true
            }.maxByOrNull { it.createdAt }
        } else null

        if (target == null) {
            return ToolExecutionResult(
                text = "⚠️ Não encontrei nenhum lançamento com os dados informados (\"${searchTerm ?: transactionId ?: ""}\") para excluir."
            )
        }

        var deletedCount = 1
        var totalDeletedVal = target.effectiveAmount
        val desc = target.description

        if (deleteAllInstallments && !target.installmentGroupId.isNullOrBlank()) {
            deletedCount = repository.deleteInstallmentGroup(target.installmentGroupId)
            totalDeletedVal = target.totalAmount * deletedCount
        } else {
            repository.deleteTransaction(target.id)
        }

        repository.logActivity(
            ActivityLog(
                id = "act_" + UUID.randomUUID().toString().take(8),
                actionType = ActivityActionType.TRANSACTION_DELETED,
                title = "Lançamento Excluído",
                description = "Removeu: $desc (${FinanceEngine.formatCurrency(totalDeletedVal)})",
                amount = totalDeletedVal,
                competenceMonth = target.competenceMonth,
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
        val searchTerm = jsonObj.get("searchTerm")?.asString
        val transactionId = jsonObj.get("transactionId")?.asString
        val amount = jsonObj.get("amount")?.asDouble
        val month = jsonObj.get("month")?.asString
        val deleteAll = jsonObj.get("deleteAllInstallments")?.asBoolean ?: false

        return executeDeleteTransaction(
            searchTerm = searchTerm,
            transactionId = transactionId,
            amount = amount,
            month = month,
            deleteAllInstallments = deleteAll
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
        val searchTerm = jsonObj.get("searchTerm")?.asString ?: jsonObj.get("buscaDescricao")?.asString ?: ""
        val actualAmount = jsonObj.get("actualAmount")?.asDouble ?: jsonObj.get("valorReal")?.asDouble ?: 0.0
        val statusStr = jsonObj.get("status")?.asString ?: jsonObj.get("novoStatus")?.asString ?: "paid"
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

    suspend fun executeQueryTransactions(
        searchTerm: String? = null,
        category: String? = null,
        subcategory: String? = null,
        month: String? = null,
        status: TransactionStatus? = null
    ): ToolExecutionResult {
        val all = repository.getTransactions().first()
        val user = repository.currentUser.value
        val activeMonth = month ?: repository.selectedMonth.value.ifBlank { FinanceEngine.currentMonthKey() }

        var filtered = all.filter { t ->
            if (!month.isNullOrBlank() && t.competenceMonth != activeMonth) return@filter false
            if (!category.isNullOrBlank() && !t.category.equals(category, ignoreCase = true)) return@filter false
            if (!subcategory.isNullOrBlank() && !(t.subcategory?.contains(subcategory, ignoreCase = true) == true)) return@filter false
            if (status != null && t.status != status) return@filter false
            if (!searchTerm.isNullOrBlank() && !matchesSearch(t.description, searchTerm) && !matchesSearch(t.category, searchTerm)) return@filter false
            true
        }.sortedByDescending { it.dueDate }

        // Se o usuário procurou por um termo específico sem fixar mês e não encontrou no mês ativo, busca no histórico completo!
        if (filtered.isEmpty() && !searchTerm.isNullOrBlank() && month == null) {
            filtered = all.filter { t ->
                matchesSearch(t.description, searchTerm) || matchesSearch(t.category, searchTerm)
            }.sortedByDescending { it.dueDate }
        }

        if (filtered.isEmpty()) {
            return ToolExecutionResult(
                text = "🔍 Não encontrei nenhuma transação com os filtros aplicados" +
                        (if (!searchTerm.isNullOrBlank()) " (\"$searchTerm\")" else "") +
                        " em ${FinanceEngine.monthLabel(activeMonth)}.",
                actionCard = ActionCardData(
                    title = "Nenhum lançamento encontrado",
                    description = if (!searchTerm.isNullOrBlank()) "Busca: \"$searchTerm\"" else "Nenhum resultado",
                    detail = "Filtro: ${FinanceEngine.monthLabel(activeMonth)}"
                )
            )
        }

        val totalAmount = filtered.sumOf { it.totalAmount }
        val myImpactTotal = filtered.sumOf { it.userImpact[user.uid] ?: it.totalAmount }

        val lines = filtered.take(10).joinToString("\n") { t ->
            val sub = if (!t.subcategory.isNullOrBlank()) " › ${t.subcategory}" else ""
            "• **${t.description}**: ${FinanceEngine.formatCurrency(t.effectiveAmount)} | ${t.category}$sub | ${t.status.label} (${FinanceEngine.formatShortDate(t.dueDate)})"
        }

        val extraNote = if (filtered.size > 10) "\n*(e mais ${filtered.size - 10} lançamentos)*" else ""

        return ToolExecutionResult(
            text = "📋 **Encontrei ${filtered.size} lançamento(s):**\n\n" +
                    "$lines$extraNote\n\n" +
                    "• **Soma Total**: ${FinanceEngine.formatCurrency(totalAmount)}\n" +
                    "• **Seu Impacto no Saldo**: ${FinanceEngine.formatCurrency(myImpactTotal)}",
            actionCard = ActionCardData(
                title = "Consulta: ${filtered.size} lançamento(s)",
                description = "Total: ${FinanceEngine.formatCurrency(totalAmount)}",
                detail = "Impacto no Saldo: ${FinanceEngine.formatCurrency(myImpactTotal)} • Mês: ${FinanceEngine.monthLabel(activeMonth)}"
            )
        )
    }

    private suspend fun executeQueryFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val searchTerm = jsonObj.get("searchTerm")?.asString
        val category = jsonObj.get("category")?.asString
        val subcategory = jsonObj.get("subcategory")?.asString
        val month = jsonObj.get("month")?.asString
        val statusStr = jsonObj.get("status")?.asString
        val status = statusStr?.let { TransactionStatus.from(it) }

        return executeQueryTransactions(
            searchTerm = searchTerm,
            category = category,
            subcategory = subcategory,
            month = month,
            status = status
        )
    }

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
        val op = jsonObj.get("operation")?.asString ?: jsonObj.get("acao")?.asString ?: "listar"
        val name = jsonObj.get("cardName")?.asString ?: jsonObj.get("nome")?.asString
        val closing = jsonObj.get("closingDay")?.asInt ?: jsonObj.get("fechamento")?.asInt
        val due = jsonObj.get("dueDay")?.asInt ?: jsonObj.get("vencimento")?.asInt
        val id = jsonObj.get("cardId")?.asString ?: jsonObj.get("id")?.asString

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
                    "• **${it.description}**: ${FinanceEngine.formatCurrency(it.defaultAmount)} (Vence dia ${it.dueDay}) | ${it.category} [$status]"
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
        val op = jsonObj.get("operation")?.asString ?: jsonObj.get("acao")?.asString ?: "listar"
        val desc = jsonObj.get("description")?.asString ?: jsonObj.get("descricao")?.asString
        val amt = jsonObj.get("amount")?.asDouble ?: jsonObj.get("valor")?.asDouble
        val day = jsonObj.get("dueDay")?.asInt ?: jsonObj.get("diaVencimento")?.asInt
        val cat = jsonObj.get("category")?.asString ?: jsonObj.get("categoria")?.asString
        val sub = jsonObj.get("subcategory")?.asString ?: jsonObj.get("subcategoria")?.asString
        val id = jsonObj.get("billId")?.asString ?: jsonObj.get("id")?.asString

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
        val month = jsonObj.get("month")?.asString ?: jsonObj.get("mes")?.asString
        return executeExpandInstallments(month)
    }

    // -------------------------------------------------------------
    // 10. GENERATE CHART (GRÁFICO FINANCEIRO)
    // -------------------------------------------------------------

    private fun executeGenerateChartFromJson(jsonObj: JsonObject): ToolExecutionResult {
        val chartTypeStr = jsonObj.get("chartType")?.asString ?: "donut"
        val title = jsonObj.get("title")?.asString ?: "Análise Financeira"
        val description = jsonObj.get("description")?.asString
        val dataArray = jsonObj.getAsJsonArray("data")

        val items = mutableListOf<ChartDataItem>()
        if (dataArray != null) {
            for (elem in dataArray) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val label = obj.get("label")?.asString ?: "Outros"
                val value = obj.get("value")?.asDouble ?: 0.0
                val colorHex = obj.get("colorHex")?.asString ?: "#10B981"
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
                val newAmt = (args["newAmount"] as? Number)?.toDouble() ?: (args["novoValor"] as? Number)?.toDouble()
                val newDesc = args["newDescription"] as? String ?: args["novaDescricao"] as? String
                val newCat = args["newCategory"] as? String ?: args["novaCategoria"] as? String
                val newSub = args["newSubcategory"] as? String ?: args["novaSubcategoria"] as? String

                executeEditTransaction(
                    searchTerm = term,
                    transactionId = id,
                    newAmount = newAmt,
                    newDescription = newDesc,
                    newCategory = newCat,
                    newSubcategory = newSub
                )
            }
            "DELETE_TRANSACTION", "excluirTransacoes" -> {
                val id = args["transactionId"] as? String
                val term = args["searchTerm"] as? String ?: args["termoBusca"] as? String
                val amt = (args["amount"] as? Number)?.toDouble() ?: (args["valor"] as? Number)?.toDouble()
                val deleteAll = (args["deleteAllInstallments"] as? Boolean) ?: false

                executeDeleteTransaction(
                    searchTerm = term,
                    transactionId = id,
                    amount = amt,
                    deleteAllInstallments = deleteAll
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
                deleteAllInstallments = deleteAll
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
                    executeEditTransaction(searchTerm = term, newAmount = amtChange)
                } else {
                    val catGuess = neuralClassifier.guessCategory(targetChange)
                    if (catGuess != "Outros") {
                        executeEditTransaction(searchTerm = term, newCategory = catGuess)
                    } else {
                        executeEditTransaction(searchTerm = term, newDescription = targetChange)
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
