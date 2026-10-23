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
        val analysisRegex = Regex(
            """(analis(e|ar)|relat(ório|orios)|auditori|diagn[óo]stico|dre|balan[çc]o|resumo detalhado|compara[çc][ãa]o|proje[çc][ãa]o)""",
            RegexOption.IGNORE_CASE
        )

        return if (analysisRegex.containsMatchIn(userQuery)) {
            "gemini-pro-latest"
        } else {
            preferredModel
        }
    }

    /**
     * Executa a requisição ao Gemini com resiliência total e tentativas contínuas:
     * - Rotação de múltiplas chaves de API (Round-Robin com detecção de 429)
     * - Cascata de Fallback de modelos (Flash -> Pro -> Lite)
     * - Retries persistentes com backoff em sobrecargas temporárias (503 / 429 / rede)
     * - Temperatura e parâmetros ajustados para máxima precisão determinística (0.1f)
     */
    suspend fun executeWithResilience(
        userQuery: String,
        systemInstructionText: String,
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

                            val contentPrompt = content {
                                text(userQuery)
                                if (attachmentBytes != null && !mimeType.isNullOrBlank()) {
                                    blob(mimeType, attachmentBytes)
                                }
                            }

                            val response: GenerateContentResponse = generativeModel.generateContent(contentPrompt)
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
}
