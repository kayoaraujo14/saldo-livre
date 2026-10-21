package com.saldolivre.app.ui.screens.chat

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.AndroidSpeechManager
import com.saldolivre.app.data.ai.ChatSessionManager
import com.saldolivre.app.data.ai.GeminiExecutionResult
import com.saldolivre.app.data.ai.GeminiLoadBalancer
import com.saldolivre.app.data.ai.LearnedRulesManager
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.parser.ParsedStatementItem
import com.saldolivre.app.data.parser.StatementParser
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.ui.theme.Emerald500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val inputText: String = "",
    val apiKey: String = "",
    val selectedModel: String = "gemini-3-flash",
    val connectionStatus: String? = null,
    val isTestingConnection: Boolean = false,
    // Sessions / Histórico
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = "",
    val isHistoryOpen: Boolean = false,
    // Regras Aprendidas
    val learnedRules: List<UserLearnedRule> = emptyList(),
    val isRulesOpen: Boolean = false,
    val newRuleInput: String = "",
    // Mensagem por Áudio / Microfone
    val isRecordingAudio: Boolean = false,
    val recordingSeconds: Int = 0,
    val isTranscribingAudio: Boolean = false,
    // Anexos & Importação de Extrato (PDF / OFX / CSV / TXT)
    val pendingAttachment: ChatAttachment? = null,
    val parsedStatementItems: List<ParsedStatementItem> = emptyList(),
    val isStatementReviewOpen: Boolean = false,
    val statementFileName: String = "",
    val availableCards: List<Card> = emptyList(),
    val selectedImportCardId: String? = null,
    val hideIgnoredStatementItems: Boolean = true,
    val isImportingStatement: Boolean = false,
    val showPasteStatementDialog: Boolean = false,
    val pasteStatementText: String = "",
    // Sugestões Dinâmicas
    val displayedSuggestions: List<String> = emptyList(),
    val suggestionOffset: Int = 0
)

class GeminiChatViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val gson = Gson()
    private val appContext: Context? = runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val loadBalancer = GeminiLoadBalancer(appContext)
    private val neuralClassifier = NeuralFinanceClassifier(appContext)
    private val sessionManager = ChatSessionManager(appContext)
    private val rulesManager = LearnedRulesManager(appContext)
    private var speechManager: AndroidSpeechManager? = null
    private var sessionsListener: ListenerRegistration? = null
    private var rulesListener: ListenerRegistration? = null

    companion object {
        val AVAILABLE_MODELS = GeminiLoadBalancer.DEFAULT_MODELS_POOL
    }

    private val _uiState = MutableStateFlow(
        ChatUiState(
            apiKey = loadBalancer.getApiKeys().joinToString(", "),
            selectedModel = loadBalancer.getSelectedModel()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Inicializa sessões e regras do usuário
        val userUid = repository.currentUser.value.uid
        val initialSessions = sessionManager.getLocalSessions(userUid)
        val initialRules = rulesManager.getLocalRules(userUid)

        val activeSession = if (initialSessions.isNotEmpty()) {
            initialSessions.first()
        } else {
            val fresh = sessionManager.createInitialSession()
            sessionManager.saveSession(userUid, fresh, syncToCloud = false)
            fresh
        }

        _uiState.update {
            it.copy(
                sessions = if (initialSessions.isNotEmpty()) initialSessions else listOf(activeSession),
                currentSessionId = activeSession.id,
                messages = activeSession.messages,
                learnedRules = initialRules
            )
        }

        // Observa alterações do usuário logado para carregar sessões e regras vinculadas
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                if (user.uid.isNotBlank()) {
                    loadUserSessionsAndRules(user.uid)
                }
            }
        }

        // Calcula sugestões dinâmicas baseadas no resumo financeiro em tempo real
        viewModelScope.launch {
            combine(
                repository.getMonthSummary(),
                repository.getTransactions(),
                repository.getCards(),
                repository.selectedMonth
            ) { summary, txs, cards, month ->
                _uiState.update { state ->
                    state.copy(
                        availableCards = cards,
                        selectedImportCardId = state.selectedImportCardId ?: cards.firstOrNull()?.id
                    )
                }
                computeDynamicSuggestions(summary, txs, cards, month)
            }.collect { suggestions ->
                updateDisplayedSuggestions(suggestions, _uiState.value.suggestionOffset)
            }
        }
    }

    private fun loadUserSessionsAndRules(uid: String) {
        sessionsListener?.remove()
        rulesListener?.remove()

        val loadedSessions = sessionManager.getLocalSessions(uid)
        val loadedRules = rulesManager.getLocalRules(uid)

        val currentSession = if (loadedSessions.isNotEmpty()) {
            loadedSessions.first()
        } else {
            val fresh = sessionManager.createInitialSession()
            sessionManager.saveSession(uid, fresh, syncToCloud = false)
            fresh
        }

        _uiState.update {
            it.copy(
                sessions = if (loadedSessions.isNotEmpty()) loadedSessions else listOf(currentSession),
                currentSessionId = currentSession.id,
                messages = currentSession.messages,
                learnedRules = loadedRules
            )
        }

        // Inscreve escutas em tempo real do Firestore se logado (mesma coleção e estrutura da Web)
        sessionsListener = sessionManager.subscribeCloudSessions(uid) { cloudSessions ->
            if (cloudSessions.isNotEmpty()) {
                _uiState.update { state ->
                    val currentLocal = state.sessions.find { it.id == state.currentSessionId }
                    val isCurrentBlank = currentLocal == null || sessionManager.isBlankInitialSession(currentLocal)
                    // Se a sessão atual for apenas uma tela inicial em branco, chaveia para a mais recente da nuvem (igual à Web)
                    val targetActive = if (isCurrentBlank) {
                        cloudSessions.first()
                    } else {
                        cloudSessions.find { it.id == state.currentSessionId } ?: cloudSessions.first()
                    }
                    state.copy(
                        sessions = cloudSessions,
                        currentSessionId = targetActive.id,
                        messages = targetActive.messages
                    )
                }
            }
        }

        rulesListener = rulesManager.subscribeCloudRules(uid) { cloudRules ->
            neuralClassifier.syncLearnedRules(cloudRules.filter { it.active }.map { it.rule })
            _uiState.update { it.copy(learnedRules = cloudRules) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionsListener?.remove()
        rulesListener?.remove()
        speechManager?.cancelListening()
    }

    // -------------------------------------------------------------
    // GESTÃO DE SESSÕES (HISTÓRICO DE CONVERSAS)
    // -------------------------------------------------------------

    fun setHistoryOpen(open: Boolean) {
        _uiState.update { it.copy(isHistoryOpen = open) }
    }

    fun onSelectSession(sessionId: String) {
        val target = _uiState.value.sessions.find { it.id == sessionId } ?: return
        _uiState.update {
            it.copy(
                currentSessionId = sessionId,
                messages = target.messages,
                isHistoryOpen = false
            )
        }
    }

    fun onNewSession() {
        val userUid = repository.currentUser.value.uid
        val fresh = sessionManager.createInitialSession()
        sessionManager.saveSession(userUid, fresh, syncToCloud = false)

        _uiState.update {
            it.copy(
                sessions = listOf(fresh) + it.sessions.filter { s -> s.id != fresh.id },
                currentSessionId = fresh.id,
                messages = fresh.messages,
                isHistoryOpen = false
            )
        }
    }

    fun onDeleteSession(sessionId: String) {
        val userUid = repository.currentUser.value.uid
        sessionManager.deleteSession(userUid, sessionId)

        _uiState.update { state ->
            val updatedSessions = state.sessions.filter { it.id != sessionId }
            if (updatedSessions.isEmpty()) {
                val fresh = sessionManager.createInitialSession()
                sessionManager.saveSession(userUid, fresh, syncToCloud = false)
                state.copy(
                    sessions = listOf(fresh),
                    currentSessionId = fresh.id,
                    messages = fresh.messages
                )
            } else {
                val nextActive = if (state.currentSessionId == sessionId) updatedSessions.first() else state.sessions.find { it.id == state.currentSessionId } ?: updatedSessions.first()
                state.copy(
                    sessions = updatedSessions,
                    currentSessionId = nextActive.id,
                    messages = nextActive.messages
                )
            }
        }
    }

    fun clearHistory() {
        val currentId = _uiState.value.currentSessionId
        val freshMsg = sessionManager.createInitialSession().messages
        val userUid = repository.currentUser.value.uid

        // Se a sessão limpa existia na nuvem, remove-a do Firestore para refletir na Web
        if (currentId.isNotBlank()) {
            sessionManager.deleteSession(userUid, currentId)
        }

        _uiState.update { state ->
            val updated = state.sessions.map { s ->
                if (s.id == currentId) s.copy(title = "Nova Conversa", messages = freshMsg, updatedAt = System.currentTimeMillis()) else s
            }
            sessionManager.saveLocalSessions(userUid, updated)
            state.copy(messages = freshMsg, sessions = updated)
        }
    }

    private fun updateActiveSessionMessages(newMessages: List<ChatMessage>, userPromptForTitle: String? = null) {
        val currentId = _uiState.value.currentSessionId
        val userUid = repository.currentUser.value.uid

        _uiState.update { state ->
            val updatedSessions = state.sessions.map { s ->
                if (s.id == currentId) {
                    var title = s.title
                    if ((title == "Nova Conversa" || title == "Conversa") && !userPromptForTitle.isNullOrBlank()) {
                        val clean = userPromptForTitle.trim()
                        title = if (clean.length > 32) clean.take(30) + "..." else clean
                    }
                    val updatedSession = s.copy(
                        title = title,
                        updatedAt = System.currentTimeMillis(),
                        messages = newMessages
                    )
                    sessionManager.saveSession(userUid, updatedSession)
                    updatedSession
                } else s
            }
            state.copy(messages = newMessages, sessions = updatedSessions)
        }
    }

    // -------------------------------------------------------------
    // GESTÃO DE REGRAS APRENDIDAS (MEMÓRIA VIVA)
    // -------------------------------------------------------------

    fun setRulesOpen(open: Boolean) {
        _uiState.update { it.copy(isRulesOpen = open) }
    }

    fun onNewRuleInputChange(text: String) {
        _uiState.update { it.copy(newRuleInput = text) }
    }

    fun onAddRule(ruleText: String? = null) {
        val text = (ruleText ?: _uiState.value.newRuleInput).trim()
        if (text.isBlank()) return

        val userUid = repository.currentUser.value.uid
        val created = rulesManager.addRule(userUid, text)
        neuralClassifier.saveLearnedRule(text)

        _uiState.update {
            it.copy(
                learnedRules = listOf(created) + it.learnedRules.filter { r -> r.id != created.id },
                newRuleInput = ""
            )
        }
    }

    fun onDeleteRule(ruleIdOrText: String) {
        val userUid = repository.currentUser.value.uid
        rulesManager.deleteRule(userUid, ruleIdOrText)
        neuralClassifier.deleteLearnedRule(ruleIdOrText)

        _uiState.update { state ->
            state.copy(learnedRules = state.learnedRules.filter { it.id != ruleIdOrText && !it.rule.contains(ruleIdOrText, ignoreCase = true) })
        }
    }

    // -------------------------------------------------------------
    // GESTÃO DE ÁUDIO / MICROFONE
    // -------------------------------------------------------------

    fun startVoiceRecording(context: Context) {
        if (_uiState.value.isRecordingAudio) {
            stopVoiceRecording(sendImmediately = true)
            return
        }

        if (speechManager == null) {
            speechManager = AndroidSpeechManager(context)
            speechManager?.onPartialTranscript = { partialText ->
                _uiState.update { it.copy(inputText = partialText) }
            }
            speechManager?.onRecordingSeconds = { seconds ->
                _uiState.update { it.copy(recordingSeconds = seconds) }
            }
            speechManager?.onFinalTranscript = { finalText, audioBytes ->
                processFinalVoiceTranscript(finalText, audioBytes)
            }
        }

        _uiState.update {
            it.copy(
                isRecordingAudio = true,
                recordingSeconds = 0
            )
        }
        speechManager?.startListening()
    }

    fun stopVoiceRecording(sendImmediately: Boolean) {
        val currentRecognized = _uiState.value.inputText
        _uiState.update { it.copy(isRecordingAudio = false) }
        speechManager?.stopListening(sendImmediately, currentRecognized)
    }

    fun cancelVoiceRecording() {
        _uiState.update {
            it.copy(
                isRecordingAudio = false,
                recordingSeconds = 0,
                inputText = ""
            )
        }
        speechManager?.cancelListening()
    }

    private fun processFinalVoiceTranscript(text: String, audioBytes: ByteArray?) {
        val trimmed = text.trim()

        if (trimmed.isNotBlank()) {
            _uiState.update { it.copy(inputText = trimmed) }
            sendMessage(overrideText = trimmed)
            return
        }

        // Se o reconhecimento nativo não obteve texto mas gravou o áudio, transcreve com o Gemini
        if (audioBytes != null && audioBytes.isNotEmpty() && loadBalancer.getApiKeys().isNotEmpty()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isTranscribingAudio = true) }
                try {
                    val aiTranscription = loadBalancer.transcribeAudio(audioBytes)
                    if (aiTranscription.isNotBlank()) {
                        _uiState.update { it.copy(inputText = aiTranscription) }
                        sendMessage(overrideText = aiTranscription)
                    }
                } catch (e: Exception) {
                    // Fallback para envio do prompt padrão com anexo de áudio
                    val audioAttachment = ChatAttachment(
                        name = "audio_${System.currentTimeMillis()}.m4a",
                        size = audioBytes.size.toLong(),
                        type = "audio/mp4",
                        base64 = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
                    )
                    sendMessage(overrideText = "🎙️ [Áudio]: Por favor, processe as despesas ou solicitações faladas neste áudio.", overrideAttachment = audioAttachment)
                } finally {
                    _uiState.update { it.copy(isTranscribingAudio = false) }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // GESTÃO DE ANEXOS (PDF / IMAGEM / EXTRATO OFX / CSV / TXT)
    // -------------------------------------------------------------

    fun onAttachmentSelected(name: String, size: Long, mimeType: String, bytes: ByteArray, textContent: String? = null) {
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val attachment = ChatAttachment(
            name = name,
            size = size,
            type = mimeType,
            base64 = base64,
            extractedText = textContent
        )
        _uiState.update { it.copy(pendingAttachment = attachment) }

        // Tenta extrair lançamentos nativamente via StatementParser (PDF, OFX, CSV, TXT)
        val isImage = mimeType.startsWith("image/") || name.lowercase().let {
            it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".webp")
        }
        if (!isImage) {
            viewModelScope.launch {
                val defaultMonth = repository.selectedMonth.value
                val rawItems = withContext(Dispatchers.Default) {
                    runCatching {
                        if (!textContent.isNullOrBlank()) {
                            StatementParser.parseFromRawText(textContent, defaultMonth)
                        } else {
                            StatementParser.parseFromBytes(bytes, name, mimeType, defaultMonth)
                        }
                    }.getOrDefault(emptyList())
                }
                val enriched = enrichStatementItemsWithRules(rawItems)
                if (enriched.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            parsedStatementItems = enriched,
                            statementFileName = name,
                            isStatementReviewOpen = true
                        )
                    }
                }
            }
        }
    }

    fun onRemoveAttachment() {
        _uiState.update {
            it.copy(
                pendingAttachment = null,
                parsedStatementItems = emptyList(),
                isStatementReviewOpen = false,
                statementFileName = ""
            )
        }
    }

    private fun enrichStatementItemsWithRules(items: List<ParsedStatementItem>): List<ParsedStatementItem> {
        val rules = _uiState.value.learnedRules
        return items.map { item ->
            var cat = item.category
            var sub = item.subcategory

            // Se ainda for "Outros", consulta o classificador neural
            if (cat == "Outros" && item.type == TransactionType.EXPENSE) {
                val guess = neuralClassifier.guessCategoryAndSubcategory(
                    "${item.description} ${item.counterparty}",
                    item.amount
                )
                if (guess.category != "Outros") {
                    cat = guess.category
                    sub = guess.subcategory ?: sub
                }
            }

            // Aplica regras aprendidas do usuário (ex: "Padaria X é Alimentação")
            val combinedText = "${item.description} ${item.counterparty}".lowercase()
            val knownCategories = listOf(
                "Alimentação", "Moradia", "Transporte", "Saúde", "Lazer",
                "Assinaturas", "Compras", "Educação", "Pets", "Investimentos", "Estorno", "Outros"
            )
            for (ruleObj in rules) {
                val ruleLower = ruleObj.rule.lowercase()
                for (knownCat in knownCategories) {
                    val knownCatLower = knownCat.lowercase()
                    if (ruleLower.contains(knownCatLower)) {
                        val keywords = ruleLower
                            .replace(knownCatLower, "")
                            .split(Regex("""[\s,;.=:\->]+"""))
                            .filter { kw -> kw.length >= 4 && kw !in listOf("sempre", "categoria", "coloque", "classifique", "como", "para", "gasto", "despesa", "lancamento", "lançamento") }
                        if (keywords.any { kw -> combinedText.contains(kw) }) {
                            cat = knownCat
                            break
                        }
                    }
                }
            }

            item.copy(category = cat, subcategory = sub)
        }
    }

    fun setStatementReviewOpen(open: Boolean) {
        _uiState.update { it.copy(isStatementReviewOpen = open) }
    }

    fun setShowPasteStatementDialog(show: Boolean) {
        _uiState.update { it.copy(showPasteStatementDialog = show) }
    }

    fun onPasteStatementTextChange(text: String) {
        _uiState.update { it.copy(pasteStatementText = text) }
    }

    fun onParseRawStatementText(rawText: String? = null) {
        val textToParse = (rawText ?: _uiState.value.pasteStatementText).trim()
        if (textToParse.isBlank()) return

        viewModelScope.launch {
            val defaultMonth = repository.selectedMonth.value
            val parsed = withContext(Dispatchers.Default) {
                runCatching {
                    StatementParser.parseFromRawText(textToParse, defaultMonth)
                }.getOrDefault(emptyList())
            }
            val enriched = enrichStatementItemsWithRules(parsed)
            if (enriched.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        parsedStatementItems = enriched,
                        statementFileName = "Extrato Colado (${enriched.size} itens)",
                        isStatementReviewOpen = true,
                        showPasteStatementDialog = false,
                        pasteStatementText = ""
                    )
                }
            } else {
                // Caso não seja um extrato tabular estruturado, envia como mensagem para o Copiloto analisar
                _uiState.update {
                    it.copy(
                        showPasteStatementDialog = false,
                        pasteStatementText = ""
                    )
                }
                sendMessage(overrideText = "Analise e extraia os lançamentos deste extrato:\n$textToParse")
            }
        }
    }

    fun toggleStatementItemSelection(itemId: String) {
        _uiState.update { state ->
            state.copy(
                parsedStatementItems = state.parsedStatementItems.map {
                    if (it.id == itemId) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleSelectAllStatementItems(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                parsedStatementItems = state.parsedStatementItems.map {
                    if (state.hideIgnoredStatementItems && it.isIgnored) it else it.copy(isSelected = selectAll)
                }
            )
        }
    }

    fun setSelectedImportCard(cardId: String?) {
        _uiState.update { it.copy(selectedImportCardId = cardId) }
    }

    fun setHideIgnoredStatementItems(hide: Boolean) {
        _uiState.update { it.copy(hideIgnoredStatementItems = hide) }
    }

    fun confirmStatementImport() {
        val state = _uiState.value
        val selectedItems = state.parsedStatementItems.filter { it.isSelected }
        if (selectedItems.isEmpty() || state.isImportingStatement) return

        _uiState.update { it.copy(isImportingStatement = true) }

        viewModelScope.launch {
            try {
                val currentUser = repository.currentUser.value
                val targetCard = state.availableCards.find { it.id == state.selectedImportCardId }
                val defaultMonth = repository.selectedMonth.value

                val transactions = selectedItems.map { item ->
                    val isCard = item.paymentMethod == PaymentMethod.CREDIT_CARD && targetCard != null
                    val (dueDate, compMonth) = if (isCard && targetCard != null) {
                        FinanceEngine.calculateCardInvoiceDates(
                            purchaseDate = item.date,
                            card = targetCard
                        )
                    } else {
                        val m = if (item.date.length >= 7) item.date.substring(0, 7) else defaultMonth
                        Pair(item.date, m)
                    }

                    val descWithInstallment = if (item.installmentNumber != null && item.installmentsTotal != null) {
                        if (!item.description.contains("${item.installmentNumber}/${item.installmentsTotal}")) {
                            "${item.description} (${item.installmentNumber}/${item.installmentsTotal})"
                        } else item.description
                    } else item.description

                    Transaction(
                        id = "imp_" + UUID.randomUUID().toString().take(8),
                        createdByUid = currentUser.uid,
                        paidByUid = currentUser.uid,
                        cardId = if (isCard) targetCard?.id else null,
                        description = descWithInstallment,
                        category = item.category,
                        subcategory = item.subcategory,
                        type = item.type,
                        paymentMethod = item.paymentMethod,
                        status = if (item.type == TransactionType.INCOME || item.paymentMethod == PaymentMethod.PIX || item.paymentMethod == PaymentMethod.DEBIT) {
                            TransactionStatus.PAID
                        } else {
                            TransactionStatus.PENDING
                        },
                        actualAmount = item.amount,
                        totalAmount = item.amount,
                        dueDate = dueDate,
                        competenceMonth = compMonth,
                        installmentNumber = item.installmentNumber ?: 1,
                        installmentsTotal = item.installmentsTotal ?: 1,
                        visibleToUids = listOf(currentUser.uid),
                        userImpact = mapOf(currentUser.uid to item.amount),
                        allocations = listOf(
                            Allocation(
                                type = "PLATFORM_USER",
                                uid = currentUser.uid,
                                name = currentUser.displayName,
                                amount = item.amount,
                                reimbursed = true
                            )
                        )
                    )
                }

                repository.addTransactions(transactions)

                val totalAmount = selectedItems.sumOf { it.amount }
                val sourceLabel = state.statementFileName.ifBlank { "Extrato Bancário" }

                repository.logActivity(
                    ActivityLog(
                        id = "act_" + UUID.randomUUID().toString().take(8),
                        actionType = ActivityActionType.TRANSACTION_CREATED,
                        title = "Importação de Extrato ($sourceLabel)",
                        description = "Importou ${transactions.size} lançamentos via Copiloto IA",
                        amount = totalAmount,
                        competenceMonth = transactions.firstOrNull()?.competenceMonth ?: defaultMonth,
                        userUid = currentUser.uid,
                        userName = currentUser.displayName
                    )
                )

                val userImportMsg = ChatMessage(
                    sender = MessageSender.USER,
                    text = "📄 Importar extrato: **$sourceLabel** (${transactions.size} itens selecionados)"
                )

                val targetDestinationLabel = if (targetCard != null) {
                    "no cartão **${targetCard.cardName}**"
                } else {
                    "na sua **Conta Corrente**"
                }

                val assistantConfirmMsg = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = buildString {
                        appendLine("✅ **Importação concluída com sucesso!**")
                        appendLine()
                        appendLine("Foram adicionados **${transactions.size} lançamentos** $targetDestinationLabel, somando **${FinanceEngine.formatCurrency(totalAmount)}**.")
                        appendLine()
                        val byCat = selectedItems.groupBy { it.category }
                            .mapValues { entry -> entry.value.sumOf { it.amount } }
                            .entries.sortedByDescending { it.value }
                            .take(4)
                        if (byCat.isNotEmpty()) {
                            appendLine("📊 **Resumo por categoria importada:**")
                            byCat.forEach { (cat, sum) ->
                                appendLine("• **$cat**: ${FinanceEngine.formatCurrency(sum)}")
                            }
                            appendLine()
                        }
                        append("O seu **Extrato** e o **Saldo Livre** já foram atualizados em tempo real!")
                    }
                )

                val updatedMessages = _uiState.value.messages + userImportMsg + assistantConfirmMsg
                updateActiveSessionMessages(updatedMessages, userPromptForTitle = "Importação $sourceLabel")

                _uiState.update {
                    it.copy(
                        isImportingStatement = false,
                        isStatementReviewOpen = false,
                        parsedStatementItems = emptyList(),
                        pendingAttachment = null,
                        statementFileName = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImportingStatement = false) }
            }
        }
    }

    // -------------------------------------------------------------
    // SUGESTÕES DINÂMICAS & ROTAÇÃO
    // -------------------------------------------------------------

    fun onRotateSuggestions() {
        _uiState.update { state ->
            val nextOffset = state.suggestionOffset + 1
            state.copy(suggestionOffset = nextOffset)
        }
        viewModelScope.launch {
            val currentSummary = repository.getMonthSummary().first()
            val currentTxs = repository.getTransactions().first()
            val currentCards = repository.getCards().first()
            val month = repository.selectedMonth.value
            val list = computeDynamicSuggestions(currentSummary, currentTxs, currentCards, month)
            updateDisplayedSuggestions(list, _uiState.value.suggestionOffset)
        }
    }

    private fun computeDynamicSuggestions(
        summary: MonthSummary,
        txs: List<Transaction>,
        cards: List<Card>,
        month: String
    ): List<String> {
        val list = mutableListOf<String>()
        val monthLabel = FinanceEngine.monthLabel(month)

        // 1. Gráficos & Visualizações
        list.add("📊 Gerar gráfico de gastos em $monthLabel")
        if (summary.totalInvested > 0 || summary.byInvestmentSubcategory.isNotEmpty()) {
            list.add("📈 Criar gráfico da minha carteira de investimentos")
        } else {
            list.add("📊 Criar comparativo das categorias onde mais gastei")
        }

        // 2. Análise Estratégica 50/30/20
        list.add("⚖️ Avaliar meus gastos segundo a Regra 50/30/20")

        // 3. Limite Diário e Fim de Semana
        if (summary.dailyLimit != null && summary.dailyLimit > 0) {
            list.add("💡 Qual é o meu limite diário seguro até o fim do mês?")
            list.add("🎉 Quanto posso gastar no final de semana sem estourar o mês?")
        }

        // 4. Saldo Livre Real
        if (summary.freeBalance < 0) {
            list.add("⚠️ Meu Saldo Livre está negativo em R$ ${FinanceEngine.round2(kotlin.math.abs(summary.freeBalance))}. Onde cortar?")
        } else if (summary.freeBalance > 0 && summary.income > 0) {
            list.add("✨ Tenho R$ ${FinanceEngine.round2(summary.freeBalance)} livre. Quanto aportar?")
        }

        // 5. Contas Fixas e Faturas
        val pendingBills = txs.filter { it.type == TransactionType.EXPENSE && it.status != TransactionStatus.PAID }
        if (pendingBills.isNotEmpty()) {
            list.add("⚡ Quanto falta pagar das minhas contas de $monthLabel?")
        }

        // 6. Recebíveis e Amigos
        if (summary.receivableTotal > 0) {
            list.add("📬 Detalhar valores que tenho a receber (R$ ${FinanceEngine.round2(summary.receivableTotal)})")
        }

        // 7. Cartões de Crédito
        if (cards.isNotEmpty()) {
            val card = cards.first()
            list.add("💳 Previsão da fatura do cartão ${card.cardName}")
        }

        // 8. Projeção Futura
        list.add("🔮 Projetar meu fluxo de caixa para os próximos 3 meses")

        return list.distinct()
    }

    private fun updateDisplayedSuggestions(all: List<String>, offset: Int) {
        if (all.isEmpty()) return
        val count = 5
        val start = (offset * count) % all.size
        val slice = all.drop(start).take(count)
        val result = if (slice.size < count) {
            slice + all.take(count - slice.size)
        } else slice
        _uiState.update { it.copy(displayedSuggestions = result) }
    }

    // -------------------------------------------------------------
    // CONFIGURAÇÕES DE API KEY & MODELO
    // -------------------------------------------------------------

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

    // -------------------------------------------------------------
    // AÇÕES INTERATIVAS PENDENTES (CONFIRMAR / CANCELAR)
    // -------------------------------------------------------------

    fun confirmPendingAction(pendingAction: PendingAction) {
        val currentMsgs = _uiState.value.messages.map { msg ->
            if (msg.actionCard?.pendingAction?.id == pendingAction.id) {
                msg.copy(
                    actionCard = msg.actionCard.copy(
                        pendingAction = pendingAction.copy(status = PendingActionStatus.CONFIRMED)
                    )
                )
            } else msg
        }
        updateActiveSessionMessages(currentMsgs)

        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true) }
            try {
                executeConfirmedAction(pendingAction)
                val successMsg = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "✅ **Ação confirmada e executada com sucesso!**\n\nConcluí a operação **\"${pendingAction.title}\"** e atualizei seus lançamentos e o cálculo do seu Saldo Livre."
                )
                updateActiveSessionMessages(_uiState.value.messages + successMsg)
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "⚠️ Ocorreu um erro ao executar a ação: ${e.message}"
                )
                updateActiveSessionMessages(_uiState.value.messages + errorMsg)
            } finally {
                _uiState.update { it.copy(isTyping = false) }
            }
        }
    }

    fun cancelPendingAction(pendingAction: PendingAction) {
        val currentMsgs = _uiState.value.messages.map { msg ->
            if (msg.actionCard?.pendingAction?.id == pendingAction.id) {
                msg.copy(
                    actionCard = msg.actionCard.copy(
                        pendingAction = pendingAction.copy(status = PendingActionStatus.CANCELLED)
                    )
                )
            } else msg
        }
        val cancelMsg = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "❌ **Ação cancelada.** Nada foi alterado no seu Saldo Livre. Se quiser ajustar algum detalhe, basta me avisar!"
        )
        updateActiveSessionMessages(currentMsgs + cancelMsg)
    }

    private suspend fun executeConfirmedAction(action: PendingAction) {
        val user = repository.currentUser.value
        val args = action.args

        when (action.toolName) {
            "CREATE_TRANSACTION" -> {
                val desc = args["description"] as? String ?: "Lançamento via Copiloto"
                val amount = (args["amount"] as? Number)?.toDouble() ?: 0.0
                val category = args["category"] as? String ?: "Outros"
                val subcategory = args["subcategory"] as? String
                val payMethodStr = args["paymentMethod"] as? String ?: "pix"
                val isCredit = payMethodStr == "credit_card"

                val tx = Transaction(
                    id = "tx_" + UUID.randomUUID().toString().take(8),
                    createdByUid = user.uid,
                    paidByUid = user.uid,
                    cardId = args["cardId"] as? String,
                    description = desc,
                    category = category,
                    subcategory = subcategory,
                    type = if ((args["type"] as? String)?.equals("income", ignoreCase = true) == true) TransactionType.INCOME else TransactionType.EXPENSE,
                    paymentMethod = if (isCredit) PaymentMethod.CREDIT_CARD else PaymentMethod.from(payMethodStr),
                    status = if (isCredit) TransactionStatus.PENDING else TransactionStatus.PAID,
                    actualAmount = amount,
                    totalAmount = amount,
                    dueDate = args["dueDate"] as? String ?: FinanceEngine.todayKey(),
                    competenceMonth = args["competenceMonth"] as? String ?: FinanceEngine.currentMonthKey(),
                    visibleToUids = listOf(user.uid),
                    userImpact = mapOf(user.uid to amount),
                    allocations = emptyList()
                )
                repository.addTransaction(tx)
            }
            "UPDATE_ESTIMATED" -> {
                val txId = args["transactionId"] as? String ?: ""
                val actual = (args["actualAmount"] as? Number)?.toDouble() ?: 0.0
                if (txId.isNotBlank()) {
                    repository.updateEstimatedWithActual(txId, actual)
                }
            }
            "DELETE_TRANSACTION" -> {
                val txId = args["transactionId"] as? String ?: ""
                if (txId.isNotBlank()) {
                    repository.deleteTransaction(txId)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // ENVIO DE MENSAGENS E ORQUESTRAÇÃO DO AGENTE
    // -------------------------------------------------------------

    fun sendMessage(overrideText: String? = null, overrideAttachment: ChatAttachment? = null) {
        val rawInput = (overrideText ?: _uiState.value.inputText).trim()
        val attachment = overrideAttachment ?: _uiState.value.pendingAttachment

        if (rawInput.isBlank() && attachment == null) return
        if (_uiState.value.isTyping) return

        // 1. Detecção mágica de chave do Google AI Studio
        if (rawInput.startsWith("AIzaSy") && rawInput.length in 35..45 && !rawInput.contains(" ")) {
            _uiState.update { it.copy(inputText = "") }
            val userKeyMsg = ChatMessage(sender = MessageSender.USER, text = "🔑 *[Chave de API do Gemini inserida]*")
            updateActiveSessionMessages(_uiState.value.messages + userKeyMsg, userPromptForTitle = "Configuração de Chave")

            viewModelScope.launch {
                _uiState.update { it.copy(isTyping = true) }
                val test = loadBalancer.testConnection(rawInput)
                if (test.first) {
                    loadBalancer.saveApiKeys(rawInput)
                    _uiState.update { it.copy(apiKey = rawInput) }
                    val botKeyMsg = ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = "🎉 **Chave da API do Google Gemini validada e conectada com sucesso!**\n\nAgora estou conectado aos seus lançamentos com inteligência artificial avançada e Function Calling. Você pode me pedir para lançar despesas, registrar compras divididas ou anexar extratos bancários em PDF!"
                    )
                    updateActiveSessionMessages(_uiState.value.messages + botKeyMsg)
                } else {
                    val errKeyMsg = ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = "⚠️ Não consegui validar a chave inserida no Google AI Studio:\n*${test.second}*\n\nVerifique se copiou a chave completa ou configure pelo ícone 🔑 acima."
                    )
                    updateActiveSessionMessages(_uiState.value.messages + errKeyMsg)
                }
                _uiState.update { it.copy(isTyping = false) }
            }
            return
        }

        // 2. Verifica se há uma ação pendente de confirmação no último card
        val lastAssistant = _uiState.value.messages.lastOrNull { it.sender == MessageSender.ASSISTANT }
        val activePending = lastAssistant?.actionCard?.pendingAction
        if (activePending != null && activePending.status == PendingActionStatus.PENDING && rawInput.isNotBlank() && attachment == null) {
            val isAffirmative = Regex("""^(sim|confirmo|confirmar|pode|pode lançar|pode lancar|pode salvar|ok|beleza|positivo|isso|correto|executar|pode sim|claro|com certeza)\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawInput)
            val isNegative = Regex("""^(não|nao|cancela|cancelar|esquece|esqueça|deixa pra lá|deixa pra la|abortar|errado|não quero|nao quero)\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawInput)

            if (isAffirmative) {
                _uiState.update { it.copy(inputText = "") }
                val userAnsMsg = ChatMessage(sender = MessageSender.USER, text = rawInput)
                updateActiveSessionMessages(_uiState.value.messages + userAnsMsg)
                confirmPendingAction(activePending)
                return
            } else if (isNegative) {
                _uiState.update { it.copy(inputText = "") }
                val userAnsMsg = ChatMessage(sender = MessageSender.USER, text = rawInput)
                updateActiveSessionMessages(_uiState.value.messages + userAnsMsg)
                cancelPendingAction(activePending)
                return
            }
        }

        // 3. Monta o texto final da mensagem
        var promptText = rawInput
        if (promptText.isBlank() && attachment != null) {
            val isStatement = attachment.name.contains("extrato", ignoreCase = true) || attachment.name.contains("conta", ignoreCase = true)
            promptText = if (isStatement) {
                "Por favor, analise o extrato bancário em PDF anexado (${attachment.name}). Extraia linha a linha os lançamentos com valores reais, separando receitas de despesas sem inventar dados, e prepare os lançamentos para conferência."
            } else {
                "Por favor, analise o documento anexado (${attachment.name}) e identifique as despesas e informações financeiras para cadastro."
            }
        }

        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = promptText,
            attachment = attachment
        )

        val nextMessages = _uiState.value.messages + userMsg
        updateActiveSessionMessages(nextMessages, userPromptForTitle = promptText)

        _uiState.update {
            it.copy(
                inputText = "",
                pendingAttachment = null,
                isTyping = true
            )
        }

        viewModelScope.launch {
            val responseResult = executeGeminiOrFallback(promptText, attachment)
            val assistantMsg = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = responseResult.text,
                actionCard = responseResult.actionCard,
                chartPayload = responseResult.chartPayload
            )
            updateActiveSessionMessages(_uiState.value.messages + assistantMsg)
            _uiState.update { it.copy(isTyping = false) }
        }
    }

    private data class AgentResponse(
        val text: String,
        val actionCard: ActionCardData? = null,
        val chartPayload: ChartPayload? = null
    )

    private suspend fun executeGeminiOrFallback(query: String, attachment: ChatAttachment? = null): AgentResponse {
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

                var attachBytes: ByteArray? = null
                var attachMime: String? = null
                if (attachment?.base64 != null) {
                    attachBytes = android.util.Base64.decode(attachment.base64, android.util.Base64.DEFAULT)
                    attachMime = attachment.type
                }

                val result: GeminiExecutionResult = loadBalancer.executeWithResilience(
                    userQuery = query,
                    systemInstructionText = systemPrompt,
                    attachmentBytes = attachBytes,
                    mimeType = attachMime,
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
                // Em caso de erro de rede ou quota, cai no fallback inteligente
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
        val activeRules = _uiState.value.learnedRules.filter { it.active }
        val learnedRulesDesc = if (activeRules.isNotEmpty()) {
            activeRules.mapIndexed { idx: Int, r: UserLearnedRule -> "${idx + 1}. ${r.rule}" }.joinToString("\n")
        } else {
            "Nenhuma regra memorizada ainda. Você pode aprender novas regras sempre que o usuário pedir (\"Aprenda que...\", \"Lembre-se que...\")."
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

            DIRETRIZES DE ASSERTIVIDADE E ZERO ALUCINAÇÃO:
            - NUNCA invente ou presuma números, lançamentos, dívidas ou saldos que não constem nos dados reais informados acima.
            - Se faltar parâmetro obrigatório, pergunte diretamente ao usuário.
            - Cite valores formatados em moeda brasileira (ex: R$ 45,00).
            - Seja DIRETO, OBJETIVO e SUCINTO.

            EXECUÇÃO DE AÇÕES FINANCEIRAS:
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
        val cleanText = if (match != null) {
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

                        val summaryNow = repository.getMonthSummary().first()
                        val cardNote = if (isCreditCard && effectiveCard != null) " • Cartão ${effectiveCard.cardName}" else ""
                        val instNote = if (totalInstallments > 1) " • Parcelado em $totalInstallments vezes" else ""

                        actionCard = ActionCardData(
                            title = if (txType == TransactionType.INCOME) "Receita Registrada" else "Despesa Registrada",
                            description = "$desc — R$ ${FinanceEngine.round2(amount)}",
                            detail = "$category${if (!subcategory.isNullOrBlank()) " › $subcategory" else ""}$cardNote$instNote",
                            newFreeBalance = "Saldo Livre Atual: R$ ${FinanceEngine.round2(summaryNow.freeBalance)}"
                        )
                    }

                    "UPDATE_ESTIMATED" -> {
                        val txId = jsonObj.get("transactionId")?.asString ?: ""
                        val actualAmount = jsonObj.get("actualAmount")?.asDouble ?: 0.0
                        if (txId.isNotBlank()) {
                            repository.updateEstimatedWithActual(txId, actualAmount)
                            val summaryNow = repository.getMonthSummary().first()
                            actionCard = ActionCardData(
                                title = "Conta Prevista Atualizada",
                                description = "Valor real fixado em R$ ${FinanceEngine.round2(actualAmount)}",
                                newFreeBalance = "Saldo Livre Atual: R$ ${FinanceEngine.round2(summaryNow.freeBalance)}"
                            )
                        }
                    }

                    "DELETE_TRANSACTION" -> {
                        val txId = jsonObj.get("transactionId")?.asString ?: ""
                        val itemDesc = jsonObj.get("description")?.asString ?: "Lançamento"
                        if (txId.isNotBlank()) {
                            repository.deleteTransaction(txId)
                            val summaryNow = repository.getMonthSummary().first()
                            actionCard = ActionCardData(
                                title = "Lançamento Excluído",
                                description = "$itemDesc removido do histórico",
                                newFreeBalance = "Saldo Livre Atual: R$ ${FinanceEngine.round2(summaryNow.freeBalance)}"
                            )
                        }
                    }

                    "GENERATE_CHART" -> {
                        val chartTypeStr = jsonObj.get("chartType")?.asString ?: "donut"
                        val title = jsonObj.get("title")?.asString ?: "Análise Financeira"
                        val description = jsonObj.get("description")?.asString
                        val dataArray = jsonObj.getAsJsonArray("data")

                        val items = mutableListOf<ChartDataItem>()
                        if (dataArray != null) {
                            for (elem in dataArray) {
                                val obj = elem.asJsonObject
                                val label = obj.get("label")?.asString ?: "Outros"
                                val value = obj.get("value")?.asDouble ?: 0.0
                                val colorHex = obj.get("colorHex")?.asString ?: "#10B981"
                                val color = try {
                                    Color(android.graphics.Color.parseColor(colorHex))
                                } catch (e: Exception) {
                                    Emerald500
                                }
                                items.add(ChartDataItem(label = label, value = value, color = color))
                            }
                        }

                        if (items.isNotEmpty()) {
                            chartPayload = ChartPayload(
                                type = if (chartTypeStr.equals("bar", ignoreCase = true)) ChartType.BAR else ChartType.DONUT,
                                title = title,
                                description = description,
                                data = items
                            )
                        }
                    }

                    "LEARN_RULE" -> {
                        val rule = jsonObj.get("rule")?.asString ?: ""
                        if (rule.isNotBlank()) {
                            onAddRule(rule)
                            actionCard = ActionCardData(
                                title = "Nova Preferência Aprendida 🧠",
                                description = rule,
                                detail = "Memorizado no Copiloto Saldo Livre"
                            )
                        }
                    }

                    "DELETE_RULE" -> {
                        val rule = jsonObj.get("rule")?.asString ?: ""
                        if (rule.isNotBlank()) {
                            onDeleteRule(rule)
                            actionCard = ActionCardData(
                                title = "Preferência Removida da Memória",
                                description = rule
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors and keep natural language text
            }
        }

        return AgentResponse(text = cleanText, actionCard = actionCard, chartPayload = chartPayload)
    }

    private suspend fun processLocalFallback(
        query: String,
        user: UserProfile,
        summary: MonthSummary,
        cards: List<Card>,
        recurringBills: List<RecurringBill>,
        transactions: List<Transaction>
    ): AgentResponse {
        val lower = query.lowercase().trim()

        if (lower.contains("saldo livre") || lower.contains("saldo")) {
            val free = FinanceEngine.round2(summary.freeBalance)
            return AgentResponse(
                text = "Seu **Saldo Livre real** neste momento é de **R$ $free**.\n\n" +
                        "• **Total de Receitas**: R$ ${FinanceEngine.round2(summary.income)}\n" +
                        "• **Total Comprometido**: R$ ${FinanceEngine.round2(summary.committed)}\n" +
                        (if (summary.dailyLimit != null && summary.dailyLimit > 0) "• **Limite Diário Seguro**: R$ ${FinanceEngine.round2(summary.dailyLimit)}/dia\n" else "")
            )
        }

        if (lower.contains("limite diário") || lower.contains("limite diario") || lower.contains("posso gastar hoje")) {
            val daily = summary.dailyLimit
            return if (daily != null && daily > 0) {
                AgentResponse(
                    text = "Seu **Limite Diário Seguro** é de **R$ ${FinanceEngine.round2(daily)}/dia**.\n\n" +
                            "Restam **${summary.daysRemaining} dias** até o final do mês. Gastando dentro desse valor por dia, você encerra o mês com Saldo Livre positivo!"
                )
            } else {
                AgentResponse(
                    text = "Atenção: Seu Saldo Livre está comprometido ou encerrado para o mês atual. Recomendamos evitar gastos não essenciais."
                )
            }
        }

        // Tenta classificar intenção com a Rede Neural local
        val intent = neuralClassifier.parseNaturalIntent(query)
        if (intent != null && intent.amount > 0) {
            val isCredit = intent.isCreditCard
            val effectiveCard = if (isCredit) {
                cards.find { it.cardName.contains(intent.cardNameHint ?: "", ignoreCase = true) } ?: cards.firstOrNull()
            } else null

            val today = FinanceEngine.todayKey()
            val (dueDate, competenceMonth) = if (isCredit && effectiveCard != null) {
                FinanceEngine.calculateCardInvoiceDates(today, effectiveCard)
            } else {
                Pair(today, FinanceEngine.currentMonthKey())
            }

            val partner = repository.allUsers.find { it.uid != user.uid }
            val splitResult = FinanceEngine.buildSplit(
                splitType = intent.splitType,
                total = intent.amount,
                myUid = user.uid,
                myName = user.displayName,
                partnerUid = partner?.uid,
                partnerName = partner?.displayName,
                payerUid = user.uid
            )

            val tx = Transaction(
                id = "tx_" + UUID.randomUUID().toString().take(8),
                createdByUid = user.uid,
                paidByUid = user.uid,
                cardId = effectiveCard?.id,
                description = intent.description,
                category = intent.category,
                subcategory = intent.subcategory,
                type = intent.type,
                paymentMethod = if (isCredit) PaymentMethod.CREDIT_CARD else PaymentMethod.PIX,
                status = if (isCredit) TransactionStatus.PENDING else TransactionStatus.PAID,
                actualAmount = intent.amount,
                totalAmount = intent.amount,
                dueDate = dueDate,
                competenceMonth = competenceMonth,
                visibleToUids = splitResult.visibleToUids,
                userImpact = splitResult.userImpact,
                allocations = splitResult.allocations,
                splitType = intent.splitType
            )
            repository.addTransaction(tx)

            val summaryNow = repository.getMonthSummary().first()
            return AgentResponse(
                text = "Cadastrei **${intent.description}** no valor de **R$ ${FinanceEngine.round2(intent.amount)}** na categoria **${intent.category}${if (intent.subcategory != null) " › ${intent.subcategory}" else ""}**.",
                actionCard = ActionCardData(
                    title = "Lançamento Registrado",
                    description = "${intent.description} — R$ ${FinanceEngine.round2(intent.amount)}",
                    detail = intent.category + (if (effectiveCard != null) " • Cartão ${effectiveCard.cardName}" else ""),
                    newFreeBalance = "Saldo Livre Atual: R$ ${FinanceEngine.round2(summaryNow.freeBalance)}"
                )
            )
        }

        return AgentResponse(
            text = "Estou conectado em modo local. Você pode me pedir para lançar despesas (ex: *\"Gastei 55 no mercado\"*), consultar seu Saldo Livre ou conectar sua chave do Google Gemini no ícone 🔑 para inteligência generativa completa!"
        )
    }
}
