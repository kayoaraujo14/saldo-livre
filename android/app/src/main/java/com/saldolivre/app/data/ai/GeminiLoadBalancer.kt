package com.saldolivre.app.data.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.saldolivre.app.BuildConfig
import kotlinx.coroutines.delay
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
            "gemini-3-flash",
            "gemini-2.5-pro",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
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
        if (saved.isNullOrBlank() || saved == "gemini-1.5-flash" || saved == "gemini-2.0-flash") {
            return "gemini-3-flash"
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
        val analysisRegex = Regex(
            """(analis(e|ar)|relat(ório|orios)|auditori|diagn[óo]stico|dre|balan[çc]o|resumo detalhado|compara[çc][ãa]o|proje[çc][ãa]o)""",
            RegexOption.IGNORE_CASE
        )

        return if (analysisRegex.containsMatchIn(userQuery)) {
            "gemini-2.5-pro"
        } else if (preferredModel == "gemini-2.5-pro") {
            "gemini-3-flash"
        } else {
            preferredModel
        }
    }

    /**
     * Executa a requisição ao Gemini com resiliência total:
     * - Rotação de múltiplas chaves de API (Round-Robin com detecção de 429)
     * - Cascata de Fallback de modelos (Flash -> Pro -> Lite)
     * - Retries com backoff em sobrecargas temporárias (503 / 429)
     * - Temperatura e parâmetros ajustados para máxima precisão determinística (0.1f)
     */
    suspend fun executeWithResilience(
        userQuery: String,
        systemInstructionText: String,
        onModelSwitched: ((newModel: String) -> Unit)? = null
    ): GeminiExecutionResult {
        val keys = getApiKeys()
        if (keys.isEmpty()) {
            throw IllegalStateException("Nenhuma chave de API do Google Gemini configurada.")
        }

        val initialModel = resolveTargetModel(userQuery, getSelectedModel())
        val modelCandidates = listOf(initialModel) + DEFAULT_MODELS_POOL.filter { it != initialModel }

        var lastException: Exception? = null

        // Itera sobre as chaves disponíveis (Round-Robin / Load Balancing)
        val numKeys = keys.size
        val startKeyIdx = (keyIndex.getAndIncrement() and Int.MAX_VALUE) % numKeys

        for (kStep in 0 until numKeys) {
            val currentKeyIdx = (startKeyIdx + kStep) % numKeys
            val candidateKey = keys[currentKeyIdx]

            // Verifica se a chave está em período de cooldown por limite de quota recente (429)
            val cooldownUntil = rateLimitedKeysCooldown[candidateKey]
            val now = System.currentTimeMillis()
            if (cooldownUntil != null && now < cooldownUntil) {
                continue
            }

            // Tenta a cascata de modelos para esta chave
            for (candidateModel in modelCandidates) {
                for (attempt in 0..1) {
                    try {
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

                        val response: GenerateContentResponse = generativeModel.generateContent(userQuery)
                        val text = response.text

                        if (!text.isNullOrBlank()) {
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
                        val errLower = (e.message ?: "").lowercase()

                        val isRateLimit = errLower.contains("429") ||
                                errLower.contains("quota") ||
                                errLower.contains("resource_exhausted")

                        val isTransient = errLower.contains("503") ||
                                errLower.contains("unavailable") ||
                                errLower.contains("high demand") ||
                                errLower.contains("spikes in demand")

                        if (isRateLimit) {
                            // Marca chave em cooldown de 60 segundos e avança para a próxima chave
                            rateLimitedKeysCooldown[candidateKey] = System.currentTimeMillis() + 60_000L
                            break // Sai do loop de modelos e pula para a próxima chave
                        }

                        if (attempt == 0 && isTransient) {
                            // Pausa curta de 1.2s para pico transitório do Google
                            delay(1200L)
                            continue
                        }

                        // Se falhou na 2ª tentativa ou erro do modelo, tenta o próximo modelo do pool
                        break
                    }
                }
            }
        }

        throw lastException ?: IllegalStateException("Não foi possível obter resposta dos servidores do Google Gemini.")
    }

    /**
     * Valida uma chave de teste com checagem leve
     */
    suspend fun testConnection(testKey: String): Pair<Boolean, String> {
        val trimmed = testKey.trim()
        if (trimmed.isBlank()) {
            return Pair(false, "Chave da API não fornecida.")
        }

        val pool = listOf(getSelectedModel(), "gemini-3-flash", "gemini-2.5-pro", "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-1.5-flash")
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
}
