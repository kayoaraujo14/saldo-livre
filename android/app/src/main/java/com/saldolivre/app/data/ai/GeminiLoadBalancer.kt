package com.saldolivre.app.data.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.saldolivre.app.BuildConfig
import com.saldolivre.app.data.model.ChatMessage
import com.saldolivre.app.data.model.BudgetInsight
import com.saldolivre.app.data.model.CategoryBudgetReport
import com.saldolivre.app.data.model.MessageSender
import com.saldolivre.app.data.model.SmartBudgetAllocation
import com.saldolivre.app.data.model.SmartBudgetPlan
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.FinancialHealthAiDiagnosis
import com.saldolivre.app.data.model.FinancialHealthScore
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class GeminiExecutionResult(
    val responseText: String,
    val modelUsed: String,
    val keyUsedMasked: String
)

class GeminiLoadBalancer(
    private val context: Context?
) {
    companion object {
        const val PREFS_NAME = "saldo_livre_gemini_prefs"
        const val PREF_KEY_API_KEYS = "saldo_livre_gemini_api_keys"
        const val PREF_KEY_MODEL = "saldo_livre_gemini_model"

        val DEFAULT_MODELS_POOL = listOf(
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-3.1-flash-lite",
            "gemini-pro-latest"
        )
    }

    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyIndex = AtomicInteger(0)
    private val rateLimitedKeysCooldown = ConcurrentHashMap<String, Long>()

    /**
     * Retorna a lista de chaves cadastradas (limpas e não-vazias).
     * Suporta múltiplas chaves separadas por vírgula, ponto e vírgula ou quebra de linha.
     */
    fun getApiKeys(): List<String> {
        val raw = prefs?.getString(PREF_KEY_API_KEYS, null)?.trim()
        val combined = if (!raw.isNullOrBlank()) {
            raw
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        return combined
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun saveApiKeys(rawInput: String) {
        prefs?.edit()?.putString(PREF_KEY_API_KEYS, rawInput.trim())?.apply()
    }

    fun getSelectedModel(): String {
        val saved = prefs?.getString(PREF_KEY_MODEL, null)
        if (saved.isNullOrBlank() || saved == "gemini-1.5-flash" || saved == "gemini-2.0-flash" || saved == "gemini-3-flash" || saved == "gemini-2.5-flash" || saved == "gemini-2.5-pro") {
            return "gemini-3.5-flash"
        }
        return saved
    }

    fun setSelectedModel(model: String) {
        prefs?.edit()?.putString(PREF_KEY_MODEL, model.trim())?.apply()
    }

    /**
     * Roteador de Modelos (Router Pattern):
     * Analisa a intenção da mensagem do usuário e eleva para o modelo Pro se for análise complexa,
     * ou mantém o modelo Flash para operações e cadastros rápidos.
     */
    fun resolveTargetModel(userQuery: String, preferredModel: String): String {
        val normalized = java.text.Normalizer.normalize(userQuery, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()

        val analysisRegex = Regex(
            """(analis(e|ar)|relatorio|auditori|diagnostico|dre|balanco|resumo detalhado|comparacao|projecao)"""
        )

        return if (analysisRegex.containsMatchIn(normalized)) {
            "gemini-pro-latest"
        } else {
            preferredModel
        }
    }

    /**
     * Executa a requisição ao Gemini com resiliência total e tentativas contínuas:
     * - Histórico conversacional para continuidade de contexto (multi-turn chat)
     * - Rotação de múltiplas chaves de API (Round-Robin com detecção de 429)
     * - Cascata de Fallback de modelos (Flash -> Pro -> Lite)
     * - Retries persistentes com backoff em sobrecargas temporárias (503 / 429 / rede)
     * - Temperatura e parâmetros ajustados para máxima precisão determinística (0.1f)
     */
    suspend fun executeWithResilience(
        userQuery: String,
        systemInstructionText: String,
        history: List<ChatMessage> = emptyList(),
        attachmentBytes: ByteArray? = null,
        mimeType: String? = null,
        onModelSwitched: ((newModel: String) -> Unit)? = null,
        onStatusUpdate: ((status: String) -> Unit)? = null,
        maxRounds: Int = 3
    ): GeminiExecutionResult {
        val keys = getApiKeys()
        if (keys.isEmpty()) {
            throw IllegalStateException("Nenhuma chave de API do Google Gemini configurada.")
        }

        val initialModel = resolveTargetModel(userQuery, getSelectedModel())
        val modelCandidates = listOf(initialModel) + DEFAULT_MODELS_POOL.filter { it != initialModel }

        var lastException: Exception? = null
        val numKeys = keys.size

        // Executa múltiplos rounds persistentes antes de desistir
        for (round in 0 until maxRounds) {
            val startKeyIdx = (keyIndex.getAndIncrement() and Int.MAX_VALUE) % numKeys

            for (kStep in 0 until numKeys) {
                val currentKeyIdx = (startKeyIdx + kStep) % numKeys
                val candidateKey = keys[currentKeyIdx]

                // Verifica cooldown recente (429) apenas se houver mais de uma chave disponível
                val cooldownUntil = rateLimitedKeysCooldown[candidateKey]
                val now = System.currentTimeMillis()
                if (numKeys > 1 && cooldownUntil != null && now < cooldownUntil) {
                    continue
                }

                // Tenta a cascata de modelos para esta chave
                for (candidateModel in modelCandidates) {
                    for (attempt in 0..1) {
                        try {
                            val modelLabel = candidateModel.replace("gemini-", "").replace("-latest", "")
                            onStatusUpdate?.invoke(
                                if (round == 0) "Consultando Gemini $modelLabel..."
                                else "Tentando Gemini $modelLabel (tentativa ${round + 1})..."
                            )
                            android.util.Log.d("GeminiLB", "[Round $round Attempt $attempt] Chamando $candidateModel com chave ${candidateKey.take(6)}...")

                            val generativeModel = GenerativeModel(
                                modelName = candidateModel,
                                apiKey = candidateKey,
                                generationConfig = generationConfig {
                                    temperature = 0.1f
                                    topP = 0.95f
                                    topK = 40
                                },
                                systemInstruction = content { text(systemInstructionText) }
                            )

                            val allContents = buildChatContents(history, userQuery, attachmentBytes, mimeType)

                            val response: GenerateContentResponse = generativeModel.generateContent(*allContents.toTypedArray())
                            val text = response.text

                            if (!text.isNullOrBlank()) {
                                android.util.Log.i("GeminiLB", "Sucesso com modelo $candidateModel! Resposta tamanho=${text.length}")
                                if (candidateModel != initialModel) {
                                    onModelSwitched?.invoke(candidateModel)
                                }
                                val maskedKey = if (candidateKey.length > 8) {
                                    candidateKey.take(4) + "..." + candidateKey.takeLast(4)
                                } else "Chave Ativa"

                                return GeminiExecutionResult(
                                    responseText = text,
                                    modelUsed = candidateModel,
                                    keyUsedMasked = maskedKey
                                )
                            } else {
                                throw IllegalStateException("Resposta vazia retornada pelo modelo $candidateModel.")
                            }
                        } catch (e: Exception) {
                            lastException = e
                            android.util.Log.e("GeminiLB", "Tentativa com modelo $candidateModel falhou: ${e.message}")
                            val errLower = (e.message ?: "").lowercase()

                            val isRateLimit = errLower.contains("429") ||
                                    errLower.contains("quota") ||
                                    errLower.contains("resource_exhausted")

                            val isTransient = errLower.contains("503") ||
                                    errLower.contains("unavailable") ||
                                    errLower.contains("high demand") ||
                                    errLower.contains("spikes in demand") ||
                                    errLower.contains("timeout") ||
                                    errLower.contains("socket") ||
                                    errLower.contains("connect")

                            if (isRateLimit) {
                                if (numKeys > 1) {
                                    rateLimitedKeysCooldown[candidateKey] = System.currentTimeMillis() + 45_000L
                                    break // Pula para a próxima chave
                                } else {
                                    // Com 1 chave só, aguarda 2s antes de tentar outro modelo
                                    delay(2000L)
                                    break
                                }
                            }

                            if (attempt == 0 && isTransient) {
                                delay(1500L)
                                continue
                            }

                            // Avança para o próximo modelo candidato
                            break
                        }
                    }
                }
            }

            // Se ainda não teve sucesso e faltam rounds, aguarda backoff progressivo antes da próxima rodada
            if (round < maxRounds - 1) {
                val backoffMs = (round + 1) * 2000L
                onStatusUpdate?.invoke("Aguardando servidores do Gemini (${round + 1}/$maxRounds)...")
                delay(backoffMs)
            }
        }

        throw lastException ?: IllegalStateException("Não foi possível obter resposta dos servidores do Google Gemini após várias tentativas.")
    }

    /**
     * Constrói o histórico multi-turn de mensagens garantindo as regras rígidas da API do Gemini:
     * 1. O primeiro turno DEVE ser do papel 'user' (descarta mensagem de boas-vindas do assistente).
     * 2. Os turnos devem alternar rigorosamente entre 'user' e 'model'.
     * 3. A mensagem atual do usuário é incluída como o turno final de 'user'.
     */
    private fun buildChatContents(
        history: List<ChatMessage>,
        currentQuery: String,
        attachmentBytes: ByteArray? = null,
        mimeType: String? = null
    ): List<Content> {
        data class SimpleTurn(val role: String, var text: String)

        val turns = mutableListOf<SimpleTurn>()

        val filtered = history.filter {
            it.text.isNotBlank() &&
            !it.text.contains("Olá! Sou o **Copiloto Saldo Livre**") &&
            !it.text.startsWith("🔑 *[Chave de API")
        }

        for (msg in filtered.takeLast(20)) {
            val role = if (msg.sender == MessageSender.USER) "user" else "model"
            if (turns.isEmpty() && role != "user") continue

            val actionDetails = if (!msg.allActionCards().isNullOrEmpty()) {
                "\n" + msg.allActionCards().joinToString("\n") { card ->
                    val linesDesc = card.resolvedLines().joinToString(", ") { "${it.label}: ${it.value}" }
                    "[Ação executada: ${card.title} - $linesDesc]"
                }
            } else ""

            val fullText = (msg.text + actionDetails).trim()
            if (fullText.isBlank()) continue

            val last = turns.lastOrNull()
            if (last != null && last.role == role) {
                last.text = "${last.text}\n$fullText"
            } else {
                turns.add(SimpleTurn(role, fullText))
            }
        }

        val last = turns.lastOrNull()
        if (last != null && last.role == "user") {
            last.text = "${last.text}\n$currentQuery"
        } else {
            turns.add(SimpleTurn("user", currentQuery))
        }

        return turns.mapIndexed { index, turn ->
            val isLast = index == turns.size - 1
            content(turn.role) {
                text(turn.text)
                if (isLast && attachmentBytes != null && !mimeType.isNullOrBlank()) {
                    blob(mimeType, attachmentBytes)
                }
            }
        }
    }

    /**
     * Valida uma chave de teste com checagem leve
     */
    suspend fun testConnection(testKey: String): Pair<Boolean, String> {
        val trimmed = testKey.trim()
        if (trimmed.isBlank()) {
            return Pair(false, "Chave da API não fornecida.")
        }

        val pool = listOf(getSelectedModel(), "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-latest", "gemini-flash-lite-latest", "gemini-pro-latest")
        for (model in pool) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = model,
                    apiKey = trimmed,
                    generationConfig = generationConfig {
                        temperature = 0.1f
                    }
                )
                val res = generativeModel.generateContent("Olá! Responda apenas OK para teste de conexão.")
                if (!res.text.isNullOrBlank()) {
                    return Pair(true, "Conectado com sucesso ao Google Gemini ($model)!")
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("API key not valid", ignoreCase = true) || msg.contains("INVALID_ARGUMENT", ignoreCase = true)) {
                    return Pair(false, "Chave de API inválida. Verifique sua chave no Google AI Studio.")
                }
            }
        }

        return Pair(false, "Falha ao conectar aos servidores do Gemini. Verifique sua chave ou conexão.")
    }

    /**
     * Transcreve áudio com o Gemini Multimodal Audio
     */
    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String = "audio/mp4"): String {
        val prompt = "Por favor, transcreva o áudio falado em português do Brasil com máxima precisão. Retorne APENAS o texto da fala transcrita, sem pontuações artificiais ou comentários."
        val result = executeWithResilience(
            userQuery = prompt,
            systemInstructionText = "Você é um transcritor de voz em português altamente especializado. Retorne apenas o texto que a pessoa falou.",
            attachmentBytes = audioBytes,
            mimeType = mimeType
        )
        return result.responseText.trim()
    }

    suspend fun generateSmartBudgetPlan(
        monthlyIncome: Double,
        spentByCategory: Map<String, Double>,
        pendingByCategory: Map<String, Double>,
        history3mAverages: Map<String, Double>,
        existingLimits: Map<String, Double>
    ): SmartBudgetPlan {
        val income = kotlin.math.max(0.0, monthlyIncome)

        // 1. Canonicaliza todas as categorias de entrada para evitar duplicação ou divergência entre aliases
        val canonSpent = mutableMapOf<String, Double>()
        for ((k, v) in spentByCategory) {
            if (v > 0.0) {
                val c = FinanceEngine.normalizeBudgetCategory(k)
                canonSpent[c] = FinanceEngine.round2((canonSpent[c] ?: 0.0) + v)
            }
        }
        val canonPending = mutableMapOf<String, Double>()
        for ((k, v) in pendingByCategory) {
            if (v > 0.0) {
                val c = FinanceEngine.normalizeBudgetCategory(k)
                canonPending[c] = FinanceEngine.round2((canonPending[c] ?: 0.0) + v)
            }
        }
        val canonHistory = mutableMapOf<String, Double>()
        for ((k, v) in history3mAverages) {
            if (v > 0.0) {
                val c = FinanceEngine.normalizeBudgetCategory(k)
                canonHistory[c] = FinanceEngine.round2(kotlin.math.max(canonHistory[c] ?: 0.0, v))
            }
        }
        val canonLimits = mutableMapOf<String, Double>()
        for ((k, v) in existingLimits) {
            if (v > 0.0) {
                val c = FinanceEngine.normalizeBudgetCategory(k)
                canonLimits[c] = FinanceEngine.round2((canonLimits[c] ?: 0.0) + v)
            }
        }

        val categoriesSet = linkedSetOf<String>().apply {
            addAll(canonSpent.keys)
            addAll(canonPending.keys)
            addAll(canonHistory.keys)
            addAll(canonLimits.keys)
        }

        val categoryContext = categoriesSet
            .filter { it.isNotBlank() && !it.contains("rendimento", ignoreCase = true) && !it.contains("salário", ignoreCase = true) && !it.contains("salario", ignoreCase = true) }
            .joinToString("\n") { cat ->
                val spent = canonSpent[cat] ?: 0.0
                val pending = canonPending[cat] ?: 0.0
                val committed = spent + pending
                val histAvg = canonHistory[cat] ?: 0.0
                val curLim = canonLimits[cat] ?: 0.0
                "- $cat: pago = R$ ${String.format(Locale.US, "%.2f", spent)}, a vencer = R$ ${String.format(Locale.US, "%.2f", pending)} (comprometido total = R$ ${String.format(Locale.US, "%.2f", committed)}), média 3 meses = R$ ${String.format(Locale.US, "%.2f", histAvg)}, teto atual = R$ ${String.format(Locale.US, "%.2f", curLim)}"
            }

        val prompt = """
            Você é um planejador financeiro pessoal de elite e especialista na Regra 50/30/20.
            Sua missão é sugerir um plano de orçamento mensal (envelopes) realista, disciplinado e seguro para o usuário.

            DADOS DO MÊS:
            - Receita líquida prevista: R$ ${String.format(Locale.US, "%.2f", income)}
            - Gastos e compromissos por categoria:
            ${categoryContext.ifBlank { "Nenhum dado prévio." }}

            REGRAS OBRIGATÓRIAS DE PLANEJAMENTO:
            1. PISO INEGOCIÁVEL: Se uma categoria tiver gastos ou contas a vencer (comprometido total > 0), o "suggestedLimit" NUNCA pode ser menor do que o total comprometido (já pago + contas a vencer). Além disso, a justificativa ("reason") JAMAIS deve dizer que a categoria está sem compromissos ou zerada quando houver comprometimento! Só mencione "sem compromissos" se o comprometido for estritamente R$ 0,00.
            2. A soma de todos os tetos de despesas sugeridos NÃO DEVE ultrapassar a receita prevista do mês (R$ ${String.format(Locale.US, "%.2f", income)}). Se a receita for maior que os gastos, reserve pelo menos 15-20% para "Investimentos" (Futuro/Aportes).
            3. "Investimentos" NÃO é despesa de consumo; é meta de aporte da regra 50/30/20.
            4. ARREDONDAMENTO OBRIGATÓRIO: NUNCA sugira valores com centavos quebrados (como R$ 499,08 ou R$ 338,38). Todos os valores de "suggestedLimit" DEVEM ser inteiros múltiplos de R$ 10,00 ou R$ 50,00 (ex: 150, 400, 750, 1200).
            5. Cada alocação deve ter uma justificativa curta ("reason") de 1 frase explicando a escolha com base nos dados reais de comprometimento e média.
            6. Retorne estritamente um JSON no seguinte formato:
            {
              "summaryInsight": "Resumo executivo curto de 1-2 frases explicando a estratégia adotada (ex: Regra 50/30/20 adaptada protegendo R$ 1.280 em contas a vencer com folga real garantida).",
              "allocations": [
                {
                  "category": "Alimentação",
                  "suggestedLimit": 700,
                  "reason": "Baseado na sua média de R$ 620 com margem de segurança de 10% cobrindo o que já foi gasto."
                }
              ]
            }
        """.trimIndent()

        val keys = getApiKeys()
        if (keys.isNotEmpty()) {
            for (key in keys) {
                for (model in listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-latest")) {
                    try {
                        val generativeModel = GenerativeModel(
                            modelName = model,
                            apiKey = key,
                            generationConfig = generationConfig {
                                temperature = 0.2f
                            }
                        )
                        val res = generativeModel.generateContent(prompt)
                        val text = res.text?.trim()
                        if (!text.isNullOrBlank()) {
                            val cleanJson = text.replace("```json", "").replace("```", "").trim()
                            val json = org.json.JSONObject(cleanJson)
                            val summary = json.optString("summaryInsight", "")
                            val allocArray = json.optJSONArray("allocations")
                            if (allocArray != null && allocArray.length() > 0) {
                                val list = mutableListOf<SmartBudgetAllocation>()
                                for (i in 0 until allocArray.length()) {
                                    val obj = allocArray.getJSONObject(i)
                                    val rawCat = obj.getString("category")
                                    val cat = FinanceEngine.normalizeBudgetCategory(rawCat)
                                    val spent = canonSpent[cat] ?: 0.0
                                    val pending = canonPending[cat] ?: 0.0
                                    val committed = spent + pending
                                    val minFloor = (kotlin.math.ceil(committed / 10.0) * 10.0)
                                    val rawLimit = obj.optDouble("suggestedLimit", minFloor)
                                    val lim = kotlin.math.max(minFloor, kotlin.math.max(50.0, kotlin.math.ceil(rawLimit / 10.0) * 10.0))
                                    var reason = obj.optString("reason", "").trim()
                                    if (committed > 0.0) {
                                        val isHallucinated = rawLimit < committed ||
                                            Regex("""(?:sem|nenhum|zerad|não h[aá]|nao h[aá]|não possui|nao possui)\s*(?:compromisso|gasto|despesa|conta|pendência|pendencia|saída|saida|movimentação|movimentacao)?""", RegexOption.IGNORE_CASE).containsMatchIn(reason) ||
                                            reason.isBlank()
                                        if (isHallucinated) {
                                            reason = if (pending > 0.0) {
                                                "Garante os ${FinanceEngine.formatCurrency(committed)} já comprometidos no mês (${FinanceEngine.formatCurrency(pending)} a vencer)."
                                            } else {
                                                "Garante os ${FinanceEngine.formatCurrency(committed)} já pagos no mês com margem de segurança."
                                            }
                                        }
                                    } else if (reason.isBlank()) {
                                        reason = "Sem compromissos no momento, alocação preventiva de segurança."
                                    }
                                    list.add(SmartBudgetAllocation(category = cat, suggestedLimit = lim, reason = reason))
                                }
                                return SmartBudgetPlan(
                                    summaryInsight = summary.ifBlank { "Plano de envelopes gerado pelo Gemini com base no seu padrão de gastos e contas a vencer." },
                                    allocations = list
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("GeminiLB", "Falha Smart Budget com modelo $model: ${e.message}")
                    }
                }
            }
        }

        // Fallback determinístico inteligente
        val fallbackAllocations = mutableListOf<SmartBudgetAllocation>()
        for (cat in categoriesSet) {
            if (cat.isBlank() || cat.contains("rendimento", ignoreCase = true) || cat.contains("salário", ignoreCase = true) || cat.contains("salario", ignoreCase = true)) continue
            val spent = canonSpent[cat] ?: 0.0
            val pending = canonPending[cat] ?: 0.0
            val committed = spent + pending
            val histAvg = canonHistory[cat] ?: 0.0
            val minFloor = (kotlin.math.ceil(committed / 10.0) * 10.0)
            val isFixed = cat.contains("moradia", ignoreCase = true) || cat.contains("financiamento", ignoreCase = true) || cat.contains("assinatura", ignoreCase = true)
            val baseTarget = if (isFixed) kotlin.math.max(histAvg, committed) else kotlin.math.max(histAvg * 1.1, committed)
            val limit = kotlin.math.max(50.0, kotlin.math.max(minFloor, kotlin.math.ceil(baseTarget / 10.0) * 10.0))

            val reason = if (pending > 0.0) {
                "Protegendo R$ ${String.format(Locale.US, "%.2f", pending)} em contas a vencer e cobrindo média de R$ ${String.format(Locale.US, "%.2f", histAvg)}."
            } else if (committed > 0.0) {
                "Garante os ${FinanceEngine.formatCurrency(committed)} já pagos no mês com margem de segurança."
            } else {
                "Margem de segurança calculada sobre sua média histórica de R$ ${String.format(Locale.US, "%.2f", histAvg)}."
            }
            fallbackAllocations.add(SmartBudgetAllocation(category = cat, suggestedLimit = limit, reason = reason))
        }

        val totalPending = canonPending.values.sum()
        return SmartBudgetPlan(
            summaryInsight = "Plano inteligente calculado com Regra 50/30/20 protegendo R$ ${String.format(Locale.US, "%.2f", totalPending)} em contas a vencer com tetos arredondados.",
            allocations = fallbackAllocations
        )
    }

    /**
     * Coach de envelopes: o Gemini analisa o estado real dos tetos e devolve insights acionáveis.
     * Retorna null quando não há chave/resposta válida (o chamador mantém os insights locais).
     */
    suspend fun generateBudgetCoachInsights(
        report: CategoryBudgetReport,
        health: FinancialHealthScore
    ): List<BudgetInsight>? {
        val keys = getApiKeys()
        if (keys.isEmpty() || report.items.isEmpty()) return null

        fun brl(v: Double) = String.format(Locale.US, "%.2f", v)
        val envelopes = report.items.joinToString("\n") {
            "- ${it.category}: teto R$ ${brl(it.limit)}, pago R$ ${brl(it.spentAmount)}, a vencer R$ ${brl(it.pendingAmount)}, " +
                "uso ${it.totalPercentage.toInt()}%, ${if (it.isFixedCategory) "FIXO" else "VARIAVEL"}, status ${it.badgeLabel}, dias restantes ${it.daysRemaining}"
        }
        val unbudgeted = report.unbudgetedCategories.joinToString("; ") { "${it.category} R$ ${brl(it.amount)}" }
            .ifBlank { "nenhum" }

        val prompt = """
            Você é o coach de orçamento de um app de finanças pessoais brasileiro. Fale em português, tom direto e amigável, sem jargão.
            Analise os envelopes do mês e devolva de 2 a 4 insights curtos e ACIONÁVEIS, priorizando o que mais impacta o usuário.

            DADOS:
            - Renda prevista: R$ ${brl(report.expectedIncome)}
            - Ainda pode gastar dentro dos tetos: R$ ${brl(report.totalRealRemaining)}
            - Meta de aporte (20% da renda): R$ ${brl(report.investmentGoal)} | já investido: R$ ${brl(report.investmentSpent)}
            - Score de saúde: ${health.score}/100 (poupança ${health.savingsScore}/30, custo de vida ${health.spendingLimitScore}/30, reserva ${health.emergencyReserveScore}/25, envelopes ${health.envelopeScore}/15)
            - Gastos sem teto: $unbudgeted
            - Envelopes:
            $envelopes

            REGRAS:
            1. Use apenas nomes de categoria EXATAMENTE como listados acima (ou dos gastos sem teto).
            2. Nunca sugira reduzir um teto abaixo de (pago + a vencer). Valores em múltiplos de 10, sem centavos.
            3. Cite valores reais em R$ e, quando houver, os dias restantes. Não invente números.
            4. Categorias FIXAS não têm "ritmo"; não as trate como gasto acelerado.
            5. Se estiver tudo bem, devolva 1 insight positivo e 1 oportunidade (ex: aporte).
            6. "severity": DANGER (estouro), ATTENTION (risco), INFO (oportunidade/elogio).
            7. "action.type": ADJUST_LIMIT (amount em R$ a somar ao teto; negativo reduz), ASK_COPILOT (com "prompt" para o chat) ou NONE.

            Responda APENAS com JSON:
            {"insights":[{"severity":"ATTENTION","title":"até 60 caracteres","message":"1 a 2 frases","action":{"type":"ADJUST_LIMIT","category":"Lazer & Entretenimento","amount":50,"label":"Subir teto em R$ 50"}}]}
        """.trimIndent()

        val validCategories = report.items.map { it.category } + report.unbudgetedCategories.map { it.category }
        for (key in keys) {
            for (model in listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-latest")) {
                try {
                    val generativeModel = GenerativeModel(
                        modelName = model,
                        apiKey = key,
                        generationConfig = generationConfig { temperature = 0.3f }
                    )
                    val text = generativeModel.generateContent(prompt).text?.trim()
                    if (!text.isNullOrBlank()) {
                        val parsed = BudgetInsightParser.parse(text, validCategories)
                        if (parsed.isNotEmpty()) return parsed
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GeminiLB", "Falha Budget Coach com modelo $model: ${e.message}")
                }
            }
        }
        return null
    }

    suspend fun generateFinancialHealthAiDiagnosis(
        healthScore: FinancialHealthScore,
        monthlyIncome: Double
    ): FinancialHealthAiDiagnosis {
        val prompt = """
            Você é um consultor financeiro pessoal sênior de fintech.
            Analise os 4 pilares do Score de Saúde Financeira do usuário (0 a 100 pontos):
            - Score Total: ${healthScore.score}/100 (${healthScore.label.uppercase()})
            - Pilar 1: Poupança/Investimentos = ${healthScore.savingsScore}/30 pts (Taxa: ${healthScore.savingsRatePercentage}% da renda)
            - Pilar 2: Custo de Vida / Comprometimento = ${healthScore.spendingLimitScore}/30 pts (${healthScore.spendingRatioPercentage}% da renda, Custo: R$ ${String.format(Locale.US, "%.2f", healthScore.monthlyLivingCost)})
            - Pilar 3: Reserva de Emergência = ${healthScore.emergencyReserveScore}/25 pts (Cobre ${String.format(Locale.US, "%.1f", healthScore.emergencyCoverageMonths)} meses de custo)
            - Pilar 4: Controle de Envelopes = ${healthScore.envelopeScore}/15 pts

            Gere um diagnóstico ultra objetivo e um plano de ação estritamente no formato JSON:
            {
              "overallAssessment": "1 a 2 frases avaliando a saúde financeira atual de forma direta e motivadora.",
              "actionPlan": [
                "Dica prática 1 com emoji, métrica exata em R$ e ganho estimado de pontos",
                "Dica prática 2 com emoji, métrica exata em R$ e ganho estimado de pontos",
                "Dica prática 3 com emoji, métrica exata em R$ e ganho estimado de pontos"
              ]
            }
        """.trimIndent()

        val keys = getApiKeys()
        if (keys.isNotEmpty()) {
            for (key in keys) {
                for (model in listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-latest")) {
                    try {
                        val generativeModel = GenerativeModel(
                            modelName = model,
                            apiKey = key,
                            generationConfig = generationConfig {
                                temperature = 0.2f
                            }
                        )
                        val res = generativeModel.generateContent(prompt)
                        val text = res.text?.trim()
                        if (!text.isNullOrBlank()) {
                            val cleanJson = text.replace("```json", "").replace("```", "").trim()
                            val json = org.json.JSONObject(cleanJson)
                            val overall = json.optString("overallAssessment", "")
                            val planArray = json.optJSONArray("actionPlan")
                            val plan = mutableListOf<String>()
                            if (planArray != null) {
                                for (i in 0 until planArray.length()) {
                                    plan.add(planArray.getString(i))
                                }
                            }
                            if (overall.isNotBlank() && plan.isNotEmpty()) {
                                return FinancialHealthAiDiagnosis(
                                    overallAssessment = overall,
                                    actionPlan = plan
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("GeminiLB", "Falha Health Diagnosis com modelo $model: ${e.message}")
                    }
                }
            }
        }

        // Fallback determinístico
        val tips = mutableListOf<String>()
        if (healthScore.savingsScore < 20) {
            tips.add("💡 Guarde pelo menos 15% a 20% da sua renda antes de iniciar gastos supérfluos (+10 pts no score).")
        }
        if (healthScore.spendingLimitScore < 20) {
            tips.add("✂️ Seus custos fixos consom mais de 70% da renda. Renegocie assinaturas e despesas não essenciais (+10 pts).")
        }
        if (healthScore.emergencyReserveScore < 15) {
            tips.add("🛡️ Aporte R$ 300/mês na reserva até atingir pelo menos 3 meses do seu custo de vida (+10 pts).")
        }
        if (healthScore.envelopeScore < 10) {
            tips.add("🎯 Monitore envelopes com 'Quase no Limite' para não estourar os gastos variáveis da semana (+5 pts).")
        }
        if (tips.isEmpty()) {
            tips.add("⭐ Parabéns! Continue mantendo seus gastos sob controle e reinvestindo os dividendos.")
        }

        return FinancialHealthAiDiagnosis(
            overallAssessment = "Diagnóstico estruturado: seu score é ${healthScore.score}/100 com classificação ${healthScore.label}.",
            actionPlan = tips.take(3)
        )
    }
}
