package com.saldolivre.app.ui.screens.chat

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.AndroidAgentToolsEngine
import com.saldolivre.app.data.ai.AndroidSpeechManager
import com.saldolivre.app.data.ai.ChatSessionManager
import com.saldolivre.app.data.ai.GeminiExecutionResult
import com.saldolivre.app.data.ai.GeminiLoadBalancer
import com.saldolivre.app.data.ai.LearnedRulesManager
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import com.saldolivre.app.data.ai.ToolExecutionResult
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
    private val toolsEngine = AndroidAgentToolsEngine(
        repository = repository,
        neuralClassifier = neuralClassifier,
        rulesManager = rulesManager,
        context = appContext
    )
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

                    // Preserva pendingActions locais que ainda estão aguardando confirmação
                    val mergedMessages = targetActive.messages.map { cloudMsg ->
                        val localMatch = state.messages.find { it.id == cloudMsg.id }
                        if (localMatch?.actionCard?.pendingAction != null) {
                            cloudMsg.copy(actionCard = localMatch.actionCard, actions = localMatch.actions)
                        } else {
                            cloudMsg
                        }
                    }

                    state.copy(
                        sessions = cloudSessions,
                        currentSessionId = targetActive.id,
                        messages = mergedMessages
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
                inputText = "",
                pendingAttachment = null,
                isHistoryOpen = false,
                isRulesOpen = false
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
                    val latestPendingTxs = newMessages.lastOrNull { msg ->
                        msg.allActionCards().any { !it.pendingTransactions.isNullOrEmpty() }
                    }?.allActionCards()?.flatMap { it.pendingTransactions.orEmpty() }.orEmpty()
                    val updatedSession = s.copy(
                        title = title,
                        updatedAt = System.currentTimeMillis(),
                        messages = newMessages,
                        pendingTransactions = if (latestPendingTxs.isNotEmpty()) latestPendingTxs else s.pendingTransactions
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
                _uiState.update { it.copy(isRecordingAudio = false, recordingSeconds = 0) }
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

    /**
     * Atualiza o status do pendingAction no card correspondente (em qualquer posição de `actions`),
     * mantendo `actionCard` sincronizado para compatibilidade com mensagens legadas.
     */
    private fun markPendingActionStatus(
        messages: List<ChatMessage>,
        pendingAction: PendingAction,
        newStatus: PendingActionStatus
    ): List<ChatMessage> = messages.map { msg ->
        val cards = msg.allActionCards()
        var changed = false
        val updatedCards = cards.map { card ->
            val matchById = card.pendingAction?.id == pendingAction.id
            val matchByContent = card.title == pendingAction.title && card.description == pendingAction.description
            if (matchById || matchByContent) {
                changed = true
                card.copy(
                    pendingAction = (card.pendingAction ?: pendingAction).copy(status = newStatus)
                )
            } else card
        }
        if (changed) {
            msg.copy(actions = updatedCards, actionCard = updatedCards.firstOrNull())
        } else msg
    }

    fun confirmPendingAction(pendingAction: PendingAction) {
        val currentMsgs = markPendingActionStatus(_uiState.value.messages, pendingAction, PendingActionStatus.CONFIRMED)
        updateActiveSessionMessages(currentMsgs)

        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true) }
            try {
                val toolResult = toolsEngine.executeConfirmedAction(pendingAction)
                val resultCards = toolResult.actions ?: toolResult.actionCard?.let { listOf(it) }
                val successMsg = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = toolResult.text.ifBlank { "✅ **Ação confirmada e executada com sucesso!**\n\nConcluí a operação **\"${pendingAction.title}\"** e atualizei seus lançamentos e o cálculo do seu Saldo Livre." },
                    actions = resultCards,
                    actionCard = resultCards?.firstOrNull(),
                    chartPayload = toolResult.chartPayload
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
        val currentMsgs = markPendingActionStatus(_uiState.value.messages, pendingAction, PendingActionStatus.CANCELLED)
        val cancelMsg = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "❌ **Ação cancelada.** Nada foi alterado no seu Saldo Livre. Se quiser ajustar algum detalhe, basta me avisar!"
        )
        updateActiveSessionMessages(currentMsgs + cancelMsg)
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
        val lastCards = lastAssistant?.allActionCards().orEmpty()
        var activePending = lastCards.firstNotNullOfOrNull { c ->
            c.pendingAction?.takeIf { it.status == PendingActionStatus.PENDING }
        } ?: lastCards.firstOrNull()?.pendingAction
        val lastCard = lastCards.firstOrNull()
        if (activePending == null && lastCard != null && lastCard.title.contains("Confirmar", ignoreCase = true)) {
            val card = lastCard
            val desc = card.description
            val parts = desc.split("—", "-")
            val descPart = parts.firstOrNull()?.trim() ?: "Lançamento"
            val amountPart = parts.getOrNull(1)?.trim() ?: ""
            val parsedAmount = Regex("""[\d.,]+""").find(amountPart)?.value
                ?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            if (parsedAmount > 0.0) {
                activePending = PendingAction(
                    id = "act_" + (card.title + card.description).hashCode().toString().take(8),
                    toolName = "CREATE_TRANSACTION",
                    title = card.title,
                    description = card.description,
                    detail = card.detail,
                    args = mapOf(
                        "description" to descPart,
                        "amount" to parsedAmount,
                        "category" to card.detail?.split("•")?.firstOrNull()?.trim(),
                        "confirmed" to true
                    ),
                    status = PendingActionStatus.PENDING
                )
            }
        }
        if (activePending != null && activePending.status == PendingActionStatus.PENDING && rawInput.isNotBlank() && attachment == null) {
            val isAffirmative = Regex("""^(sim|confirmo|confirma|confirmar|pode|pode lançar|pode lancar|pode salvar|pode cadastrar|pode registrar|ok|beleza|positivo|isso|correto|executar|pode sim|claro|com certeza|salvar|registra|lança|lanca)\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawInput)
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
            val responseResult = executeGemini(promptText, attachment)
            val assistantMsg = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = responseResult.text,
                actions = responseResult.actions, actionCard = responseResult.actions?.firstOrNull(),
                chartPayload = responseResult.chartPayload
            )
            updateActiveSessionMessages(_uiState.value.messages + assistantMsg)
            _uiState.update { it.copy(isTyping = false) }
        }
    }

    private data class AgentResponse(
        val text: String,
        val actions: List<ActionCardData>? = null,
        val chartPayload: ChartPayload? = null
    )

    private suspend fun executeGemini(query: String, attachment: ChatAttachment? = null): AgentResponse {
        val user = repository.currentUser.value
        val summary = repository.getMonthSummary().first()
        val cards = repository.getCards().first()
        val recurringBills = repository.getRecurringBills().first()
        val transactions = repository.getTransactions().first()
        val friendships = repository.getFriendships().first()
        val selectedMonth = repository.selectedMonth.value

        val keys = loadBalancer.getApiKeys()

        if (keys.isEmpty()) {
            return AgentResponse(
                text = "🔑 **Chave da API do Google Gemini não encontrada**\n\n" +
                        "Para conversar com o Copiloto com inteligência artificial, configure sua chave de API no ícone 🔑 acima ou cole sua chave diretamente nesta conversa."
            )
        }

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
                },
                onStatusUpdate = { status ->
                    _uiState.update { it.copy(connectionStatus = status) }
                }
            )

            _uiState.update {
                it.copy(connectionStatus = "Online")
            }

            if (result.responseText.isNotBlank()) {
                return processGeminiResponseAndExecuteActions(
                    rawResponse = result.responseText,
                    originalQuery = query,
                    systemPrompt = systemPrompt
                )
            } else {
                throw IllegalStateException("Resposta vazia retornada pelo modelo Gemini.")
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiChat", "executeGemini falhou após todas as tentativas: ${e.message}", e)
            _uiState.update { it.copy(connectionStatus = "Erro nos Servidores do Gemini") }
            val errorMsg = e.message ?: "Instabilidade nos servidores do Google"
            return AgentResponse(
                text = "⚠️ **Não foi possível obter resposta dos servidores do Google Gemini.**\n\n" +
                        "*Detalhe:* $errorMsg\n\n" +
                        "Por favor, verifique sua conexão com a internet ou sua chave de API no ícone 🔑 acima e tente enviar novamente."
            )
        }
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
        val cardsMap = cards.associateBy { it.id }
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

        val recurringDesc = if (recurringBills.isEmpty()) "nenhum molde de conta recorrente cadastrado"
        else recurringBills.filter { it.active }.joinToString("; ") {
            "${it.description} [MOLDE / PREVISÃO TEÓRICA: R$ ${FinanceEngine.round2(it.defaultAmount)}, vence dia ${it.dueDay}, categoria: ${it.category}]"
        }

        val currentMonthTxs = transactions.filter { it.competenceMonth == selectedMonth }
        val recentTxsDesc = if (currentMonthTxs.isEmpty()) {
            "LANÇAMENTOS REAIS REGISTRADOS NO MÊS VIGENTE (${FinanceEngine.monthLabel(selectedMonth).uppercase()} / $selectedMonth): Nenhum lançamento real registrado neste mês até o momento."
        } else {
            val sorted = currentMonthTxs.sortedByDescending { it.dueDate }.take(40)
            "LANÇAMENTOS REAIS REGISTRADOS NO MÊS VIGENTE (${FinanceEngine.monthLabel(selectedMonth).uppercase()} / $selectedMonth - ${sorted.size} de ${currentMonthTxs.size} itens):\n" +
                    sorted.joinToString("\n") {
                        val subText = if (!it.subcategory.isNullOrBlank()) "${it.category} › ${it.subcategory}" else it.category
                        val cName = it.cardId?.let { cid -> cardsMap[cid]?.cardName }
                        val methodOrCard = if (!cName.isNullOrBlank()) "Cartão $cName" else it.paymentMethod.label
                        val myImpact = it.userImpact[user.uid] ?: it.effectiveAmount
                        val isSplit = kotlin.math.abs(myImpact - it.effectiveAmount) > 0.01 || (it.splitType != null && it.splitType != SplitType.MINE)
                        val splitNote = if (isSplit) {
                            val friendName = it.assignedFriendName?.takeIf { f -> f.isNotBlank() }
                                ?: it.allocations.firstOrNull { a -> a.uid != user.uid && a.name.isNotBlank() }?.name
                            val who = if (!friendName.isNullOrBlank()) "com $friendName" else it.splitType?.label ?: "dividido"
                            " [DIVISÃO: Sua cota: R$ ${FinanceEngine.round2(myImpact)} de R$ ${FinanceEngine.round2(it.effectiveAmount)} ($who)]"
                        } else ""
                        "• [ID: ${it.id}] ${it.dueDate} — **${it.description}**: R$ ${FinanceEngine.round2(it.effectiveAmount)}$splitNote • $subText • ${it.status.label} • $methodOrCard"
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



        // Bloco de dados financeiros (paridade com `summaryDesc` da Web)
        val summaryDescText = """
DADOS FINANCEIROS REAIS DO USUÁRIO EM ${FinanceEngine.monthLabel(selectedMonth).uppercase()} ($selectedMonth):
- Saldo Livre Real: R$ ${FinanceEngine.round2(summary.freeBalance)}
- Limite Diário Seguro: $dailyLimitDesc
- Total de Receitas: R$ ${FinanceEngine.round2(summary.income)}
- Total Comprometido: R$ ${FinanceEngine.round2(summary.committed)} (Já Pago: R$ ${FinanceEngine.round2(summary.paid)}, A Pagar/Pendente: R$ ${FinanceEngine.round2(summary.committed - summary.paid)})
- Valores a Receber: R$ ${FinanceEngine.round2(summary.receivableTotal)} (Parceiro: R$ ${FinanceEngine.round2(summary.receivableFromPartner)}, Amigos: R$ ${FinanceEngine.round2(summary.receivableFromFriends)}, Terceiros: R$ ${FinanceEngine.round2(summary.receivableFromExternal)})
- Gastos por Categoria neste mês:
  $byCategoryDesc
- Total Investido no Mês: R$ ${FinanceEngine.round2(summary.totalInvested)}$byInvestmentDesc
- Moldes de Contas Recorrentes Cadastradas [ATENÇÃO: MOLDES / PREVISÕES TEÓRICAS - NÃO SÃO TRANSAÇÕES EFETIVADAS]:
  $recurringDesc
""".trim()

        // Lote pendente de confirmação (memória estruturada de estado — paridade com `pendingTxsDesc` da Web)
        val currentSession = _uiState.value.sessions.find { it.id == _uiState.value.currentSessionId }
        val pendingTxs: List<Map<String, Any?>> = currentSession?.pendingTransactions?.takeIf { it.isNotEmpty() }
            ?: _uiState.value.messages
                .lastOrNull { msg -> msg.allActionCards().any { !it.pendingTransactions.isNullOrEmpty() } }
                ?.allActionCards()
                ?.flatMap { it.pendingTransactions.orEmpty() }
                .orEmpty()

        fun pendingStr(m: Map<String, Any?>, vararg keys: String): String =
            keys.firstNotNullOfOrNull { k -> m[k]?.toString()?.takeIf { it.isNotBlank() } }.orEmpty()

        fun pendingAmount(m: Map<String, Any?>): Double =
            listOf("valor_exato", "valorTotal", "amount", "valor").firstNotNullOfOrNull { k ->
                when (val v = m[k]) {
                    is Number -> v.toDouble()
                    is String -> v.replace(",", ".").toDoubleOrNull()
                    else -> null
                }
            } ?: 0.0

        val pendingTxsDescText = if (pendingTxs.isNotEmpty()) {
            val valid = pendingTxs.filter { pendingStr(it, "categoria_filtro").ifBlank { "valida" } == "valida" }
            val isIncome: (Map<String, Any?>) -> Boolean = { pendingStr(it, "tipo", "type").equals("income", ignoreCase = true) }
            val totalIncome = valid.filter(isIncome).sumOf { pendingAmount(it) }
            val totalExpense = valid.filterNot(isIncome).sumOf { pendingAmount(it) }
            val itemsDesc = pendingTxs.mapIndexed { idx, t ->
                val tipoLabel = if (isIncome(t)) "🟢 ENTRADA" else "🔴 SAÍDA"
                val motivo = pendingStr(t, "motivoIgnorado").let { if (it.isNotBlank()) " ($it)" else "" }
                "${idx + 1}. [${pendingStr(t, "data", "date")}] $tipoLabel — **${pendingStr(t, "contraparte", "description", "descricao")}**: " +
                    "R$ ${FinanceEngine.round2(pendingAmount(t))} • ${pendingStr(t, "categoria", "category").ifBlank { "Geral" }}$motivo"
            }.joinToString("\n")
            """
TRANSAÇÕES PENDENTES DE CONFIRMAÇÃO DO EXTRATO/LOTE (MEMÓRIA ESTRUTURADA DE ESTADO):
Existe atualmente salvo no estado da sessão um lote de ${pendingTxs.size} transações extraídas do extrato/fatura:
$itemsDesc

TOTAIS VALIDADOS CALCULADOS PELO SISTEMA (DETERMINÍSTICO):
- Receitas/Entradas válidas: ${valid.count(isIncome)} itens somando R$ ${FinanceEngine.round2(totalIncome)}
- Despesas/Saídas válidas: ${valid.count { !isIncome(it) }} itens somando R$ ${FinanceEngine.round2(totalExpense)}

SE O USUÁRIO PERGUNTAR "quais foram as transações?", "enumere os itens" ou qualquer pergunta sobre as movimentações do extrato, responda EXCLUSIVAMENTE a partir da lista estruturada acima! É expressamente proibido inventar valores ou contrapartes!
""".trim()
        } else {
            "TRANSAÇÕES PENDENTES DE CONFIRMAÇÃO DO EXTRATO/LOTE: Nenhum lote pendente de confirmação no momento."
        }

        val partner = repository.allUsers.find { it.uid != user.uid }
        val activeRules = _uiState.value.learnedRules.filter { it.active }
        val learnedRulesDesc = if (activeRules.isNotEmpty()) {
            activeRules.mapIndexed { idx: Int, r: UserLearnedRule -> "${idx + 1}. ${r.rule}" }.joinToString("\n")
        } else {
            "Nenhuma regra memorizada ainda. Você pode aprender novas regras sempre que o usuário pedir (\"Aprenda que...\", \"Lembre-se que...\", \"Sempre que eu falar X faça Y\")."
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

$summaryDescText

$recentTxsDesc

$pendingTxsDescText

REGRAS E PREFERÊNCIAS APRENDIDAS DO USUÁRIO (MEMÓRIA VIVA):
$learnedRulesDesc

ATENÇÃO: Sempre que uma despesa ou comando do usuário corresponder a uma dessas regras aprendidas, APLIQUE-A IMEDIATAMENTE sem hesitar!
Se o usuário pedir para você aprender uma nova regra, memorizar um comportamento ou esquecer uma regra antiga, use OBRIGATORIAMENTE a ação LEARN_RULE ou DELETE_RULE.

======================================================================
REGRA DE OURO INVIOLÁVEL: DISTINÇÃO RIGOROSA ENTRE CONTAS RECORRENTES (CONFIGURAÇÕES E PREVISÕES) E TRANSAÇÕES REAIS (LANÇAMENTOS DO MÊS)
======================================================================
1. DEFINIÇÃO DE CONTAS RECORRENTES (CONFIGURAÇÕES E PREVISÕES):
   - "Contas Recorrentes" são APENAS configurações e previsões orçamentárias teóricas (ex: estimativa padrão configurada de R$ 150,00 para supermercado, R$ 100,00 para conta de luz, etc.).
   - O valor pré-cadastrado na conta recorrente é uma mera configuração de previsão inicial.
   - CONTAS RECORRENTES NÃO SÃO TRANSAÇÕES EFETIVADAS NEM GASTOS REAIS PAGOS NO MÊS!

2. PROIBIÇÃO TERMINANTE (ANTI-ALUCINAÇÃO):
   - Se o usuário perguntar sobre lançamentos, faturas, gastos ou despesas reais (ex: "quanto paguei no Forte Alencar?", "busque a conta de luz", "qual foi o valor do mercado?", "qual a despesa real de X no mês?"):
     * A IA É TERMINANTEMENTE PROIBIDA de usar o valor da Conta Recorrente como resposta final e DEVE OBRIGATORIAMENTE consultar as transações reais!
     * A IA NUNCA pode usar ou afirmar que o valor configurado na conta recorrente foi pago ou faturado.

3. OBRIGATORIEDADE DA CONSULTA A TRANSAÇÕES REAIS (TOOL SEARCH_TRANSACTIONS):
   - Sempre que o usuário perguntar sobre lançamentos, faturas, gastos ou despesas reais, a IA DEVE OBRIGATORIAMENTE emitir a tool call `SEARCH_TRANSACTIONS`:
     ```json
     {
       "action": "SEARCH_TRANSACTIONS",
       "searchTerm": "Forte Alencar",
       "category": null,
       "month": "$selectedMonth"
     }
     ```
   - O motor nativo (FinanceRepository / FinanceEngine) executará essa busca localmente ou no Firestore e devolverá para a IA os valores exatos lançados no mês vigente, superando a limitação de ler apenas a amostra estática do prompt.

4. PREVALÊNCIA ABSOLUTA DO VALOR REAL:
   - O valor da transação real lançada no banco de dados é o ÚNICO que deve ser informado como valor pago/efetivado.
   - Havendo divergência entre o valor da transação real e o valor configurado na Conta Recorrente (ex: previsão configurada de R$ 150,00 vs lançamento real de R$ 185,50), o valor REAL registrado (R$ 185,50) é o único correto que você deve informar.
   - Se nenhuma transação real for encontrada no mês após a busca no banco de dados, informe com total sinceridade que existe apenas a configuração/previsão cadastrada de R$ X, mas que NENHUM lançamento real foi registrado, faturado ou pago no mês de ${FinanceEngine.monthLabel(selectedMonth)} até o momento.
======================================================================

EXECUÇÃO DE OPERAÇÕES NO BANCO DE DADOS (CRUD COMPLETO):
Sempre que você precisar realizar uma ação, explique brevemente ao usuário e OBRIGATORIAMENTE INCLUA NO FINAL UM BLOCO JSON delimitado por ```json ... ``` com a ação correspondente! Você pode incluir MAIS DE UM bloco JSON na mesma resposta quando o usuário solicitar múltiplas ações.

1. Cadastrar Transação (Despesa, Receita ou Parcelamento):
```json
{
  "action": "CREATE_TRANSACTION",
  "description": "Supermercado Pão de Açúcar",
  "amount": 150.0,
  "type": "expense",
  "category": "Alimentação",
  "subcategory": "Supermercado",
  "paymentMethod": "credit_card",
  "cardName": "Nubank",
  "splitType": "MINE",
  "friendName": null,
  "friendEmail": null,
  "thirdPartyName": null,
  "installments": { "current": 1, "total": 1 },
  "competenceMonth": "2026-11",
  "dueDate": "2026-11-10"
}
```

2. Cadastrar em Lote (Múltiplos Lançamentos):
```json
{
  "action": "BATCH_TRANSACTIONS",
  "transactions": [
    { "description": "iFood", "amount": 45.0, "type": "expense", "category": "Alimentação", "subcategory": "Delivery & Lanches", "paymentMethod": "credit_card", "date": "2026-10-05", "competenceMonth": "2026-11" },
    { "description": "Salário", "amount": 5000.0, "type": "income", "category": "Receita", "subcategory": "Salário Principal", "paymentMethod": "pix", "date": "2026-10-05", "competenceMonth": "2026-10" }
  ]
}
```

3. Editar / Corrigir Lançamento Existente:
```json
{
  "action": "EDIT_TRANSACTION",
  "transactionId": "id_da_transacao_se_conhecido",
  "searchTerm": "Padaria",
  "month": "2026-10",
  "newAmount": 25.0,
  "newDescription": "Padaria Real",
  "newCategory": "Alimentação",
  "newSubcategory": "Padaria & Café",
  "newStatus": "paid",
  "newPaymentMethod": "debit",
  "newSplitType": "MINE",
  "newCompetenceMonth": "2026-11",
  "newDueDate": "2026-11-15"
}
```

4. Excluir Lançamento:
```json
{
  "action": "DELETE_TRANSACTION",
  "transactionId": "id_da_transacao_se_conhecido",
  "searchTerm": "Burger King",
  "amount": 50.0,
  "month": "2026-10",
  "deleteAllInstallments": false
}
```

5. Atualizar Conta Prevista / Estimada (Luz, Água, Internet, etc.):
```json
{
  "action": "UPDATE_ESTIMATED",
  "searchTerm": "Luz",
  "actualAmount": 180.0,
  "status": "paid"
}
```

6. Buscar Transações Reais no Banco de Dados (SEARCH_TRANSACTIONS):
```json
{
  "action": "SEARCH_TRANSACTIONS",
  "searchTerm": "Forte Alencar",
  "category": "Alimentação",
  "subcategory": "Supermercado",
  "month": "2026-10",
  "status": "paid"
}
```
*(OBRIGATÓRIO: Use SEMPRE que o usuário perguntar sobre despesas reais, valores pagos, compras de estabelecimentos específicos como "Forte Alencar", contas do mês ou pesquisar se algo já foi lançado. O motor nativo busca no banco de dados local/Firestore e devolve os valores exatos lançados no mês vigente. Campos category, subcategory, month ou status são opcionais e podem ser omitidos)*

7. Gerenciar Cartões de Crédito (operation: "listar" | "adicionar" | "excluir"):
```json
{
  "action": "MANAGE_CARDS",
  "operation": "adicionar",
  "cardName": "Inter Black",
  "closingDay": 5,
  "dueDay": 12
}
```

8. Gerenciar Contas Recorrentes / Assinaturas (operation: "listar" | "adicionar" | "pausar" | "ativar" | "excluir"):
```json
{
  "action": "MANAGE_RECURRING",
  "operation": "adicionar",
  "description": "Netflix",
  "amount": 55.90,
  "dueDay": 15,
  "category": "Assinaturas",
  "subcategory": "Streaming de Vídeo (Netflix/Max/Disney)"
}
```

9. Expandir Parcelamentos do Mês:
```json
{
  "action": "EXPAND_INSTALLMENTS",
  "month": "2026-11"
}
```

10. Gerar Gráfico de Análise (chartType: "donut" | "bar"):
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

11. Memorizar Nova Regra (Memória Viva):
```json
{
  "action": "LEARN_RULE",
  "rule": "Todo gasto no Zé Delivery é Lazer e 100% meu"
}
```

12. Esquecer Regra da Memória:
```json
{
  "action": "DELETE_RULE",
  "rule": "<regra a esquecer>"
}
```

REGRAS DE FUNCIONAMENTO:

1. CARTÕES DE CRÉDITO, FATURA E MÊS DE COMPETÊNCIA:
- Compras no cartão de crédito comprometem a renda do MÊS DE VENCIMENTO DA FATURA (competenceMonth), e NÃO a data em que a compra foi feita!
- Se o usuário disser que os gastos virão para a fatura com vencimento em determinado dia/mês (ex: "vence 14/11" ou "fatura de novembro"):
  Defina obrigatoriamente: dueDate = "2026-11-14" e competenceMonth = "2026-11"! O orçamento que deve ser comprometido é novembro, e não outubro!
- Se a compra foi feita após o dia de fechamento (closingDay) do cartão, a fatura fecha no ciclo seguinte e compromete o mês de vencimento (dueDay).

2. AMIGOS NA REDE E DIVISÃO DE CONTAS:
- Se o usuário disser que um gasto é de um amigo na plataforma (consulte "Amigos na plataforma" acima):
  * Identifique o amigo e preencha friendName e friendEmail.
  * Se for 100% do amigo: use splitType = "FRIEND".
- Se for dividido meio a meio com o(a) parceiro(a): splitType = "SPLIT_50". Se for 100% do parceiro: splitType = "PARTNER".
- Se for pessoa sem conta na plataforma (ex: "Mãe", "Pai", "Irmão"):
  Use splitType = "EXTERNAL" com thirdPartyName = "Mãe".
- EXIBIÇÃO DE TRANSAÇÕES DIVIDIDAS (SPLIT):
  Ao listar, detalhar ou responder sobre transações que foram divididas (com amigos, parceiro ou terceiros):
  * NUNCA afirme que o usuário gastou ou pagou o valor TOTAL de uma compra dividida sem esclarecer a divisão!
  * Informe SEMPRE o valor total da compra E a parte correspondente ao usuário (ex: "Oficina Della Pizza: R$ 135,45 no total da compra, sendo R$ 67,73 a sua parte (dividida 50/50 com seu amigo)").
  * Deixe claro que o impacto no Saldo Livre reflete apenas a cota do usuário (R$ 67,73), e não o valor total da compra.

3. ESTORNOS E REEMBOLSOS:
- Se o gasto for um estorno/crédito (ex: "99FOOD -R$ 8,40" ou "UBER -R$ 4,09"), registre com category = "Estorno", type = "income" e amount POSITIVO (ex: 8.40). Trata-se de crédito na fatura que alivia o Saldo Livre.

4. TAXONOMIA RICA E SUBGRUPOS ESPECÍFICOS (CATEGORIAS E SUBCATEGORIAS):
- O Saldo Livre conta com inteligência para categorização granular em grupos e subgrupos analíticos:
  * Alimentação: Supermercado, Restaurante, Delivery & Lanches, Padaria & Café, Feira & Hortifruti, Açougue & Peixaria, Bares & Bebidas.
  * Moradia: Aluguel & Condomínio, Energia Elétrica, Água & Saneamento, Gás, Internet & TV a Cabo, Manutenção & Reparos, Móveis & Eletrodomésticos, IPTU & Taxas.
  * Transporte: Combustível, Aplicativos (Uber/99), Transporte Público (Metrô/Ônibus), Estacionamento & Pedágio, Manutenção & Revisão, Seguro & IPVA, Lavagem & Estética Automotiva.
  * Saúde: Farmácia & Remédios, Consultas & Exames, Plano de Saúde & Dental, Academia & Treinos, Terapia & Psicologia, Cuidados Pessoais & Estética.
  * Lazer: Cinema, Teatro & Shows, Viagens & Hospedagem, Passeios & Experiências, Jogos & Games, Festas, Baladas & Eventos, Hobbies & Atividades.
  * Assinaturas: Streaming de Vídeo (Netflix/Max/Disney), Streaming de Música (Spotify/Apple), Softwares, IA & Cloud (iCloud/ChatGPT), Telefonia Celular, Clubes & Assinaturas Recorrentes.
  * Educação: Cursos Online & Certificações, Faculdade & Pós-Graduação, Livros & E-books, Material Escolar & Papelaria, Idiomas.
  * Compras: Roupas & Acessórios, Calçados, Eletrônicos & Informática, Casa & Decoração, Presentes.
  * Financiamentos & Empréstimos: Financiamento Imobiliário, Financiamento Veicular, Empréstimo Pessoal, Empréstimo Consignado, Amortização de Financiamento, Crédito Estudantil (FIES/Pravaler), Renegociação de Dívidas, Juros & Tarifas de Empréstimo, Outros Empréstimos.
  * Pets: Ração & Petiscos, Veterinário & Medicamentos, Banho, Tosa & Cuidados, Acessórios & Brinquedos.
  * Receita: Salário Principal, Adiantamento / 13º Salário, Freelance & Prestação de Serviços, Rendimentos & Investimentos, Reembolsos & Devoluções, Pix Recebido, Outras Receitas.
  * Cartão Terceiros: Compras de Amigos, Compras de Familiares, Outros Terceiros.
  * Estorno: Crédito na Fatura, Devolução / Cancelamento, Cashback Recebido.
  * Outros: Tarifas Bancárias & Juros, Impostos & Taxas Governamentais, Doações & Caridade, Diversos.
- Ao registrar ou editar lançamentos (individuais ou lote), preencha SEMPRE "category" e "subcategory" de acordo com o estabelecimento (ex: "iFood" -> Alimentação › Delivery & Lanches; "Pão de Açúcar" -> Alimentação › Supermercado; "Uber" -> Transporte › Aplicativos (Uber/99); "Financiamento Caixa" -> Financiamentos & Empréstimos › Financiamento Imobiliário).
- Se o usuário pedir relatórios analíticos de um grupo ou subgrupo (ex: "quanto gastei com restaurante?", "quanto foi mercado vs delivery?"), use QUERY_TRANSACTIONS com subcategory ou os dados do mês acima, gerando respostas comparativas detalhadas com clareza.

5. COMPRAS PARCELADAS:
- Notações como "9/12", "2/6" ou "10/12" indicam compras parceladas. Preencha installments.current e installments.total (ex: { "current": 9, "total": 12 }).
- O sistema aloca AUTOMATICAMENTE toda a série de parcelas pelos meses correspondentes (passadas como pagas, atual e futuras como pendentes).
- AO EDITAR CATEGORIA OU SUBCATEGORIA DE COMPRA PARCELADA: passe APENAS searchTerm, newCategory e newSubcategory. NUNCA passe newCompetenceMonth nem newDueDate, pois cada parcela tem sua própria data!
- Confirme a operação de forma simples e direta no mês atual. NÃO gere projeções de meses futuros a menos que o usuário peça.
- Se o usuário pedir para gerar, expandir ou sincronizar as parcelas de compras já existentes: use EXPAND_INSTALLMENTS.

6. REGISTRO EM LOTE:
- Quando o usuário enviar uma lista com vários gastos de uma vez, use SEMPRE BATCH_TRANSACTIONS (uma única ação com todos os itens).

7. EDIÇÃO E EXCLUSÃO CIRÚRGICA DE TRANSAÇÕES (NUNCA EXCLUA EM LOTE SEM PEDIDO EXPLÍCITO):
- Para excluir/editar um lançamento específico: consulte os "LANÇAMENTOS REAIS REGISTRADOS NESTE MÊS" acima, localize a transação e passe SEMPRE o ID em transactionId!
- Se não tiver o ID, passe searchTerm COM O NOME EXATO DO ESTABELECIMENTO e o amount em reais para desambiguação.
- NUNCA use palavras genéricas como "compra", "despesa" ou "gasto" em searchTerm!
- Se o usuário perguntar sobre compras anteriores ou pesquisar despesas específicas: use SEMPRE SEARCH_TRANSACTIONS.

8. DIRETRIZES DE ASSERTIVIDADE, VERACIDADE E ZERO ALUCINAÇÃO:
- REGRA CRÍTICA DE INTEGRIDADE FINANCEIRA: é estritamente proibido inventar, estimar ou deduzir valores de transações para fazer a soma bater com um total. Toda listagem deve refletir fielmente o documento original.
- NUNCA invente, deduza ou presuma números, parâmetros faltantes, lançamentos, dívidas, faturas ou saldos que não constem nos dados reais acima.
- SE FALTAR UM PARÂMETRO OBRIGATÓRIO (como valor, data de competência ou divisão), PERGUNTE AO USUÁRIO em vez de preencher valores inventados.
- Na descrição das transações ("description"), extraia ESTRITAMENTE o nome do produto, serviço ou estabelecimento (ex: "Two Brothers", "Subway", "Uber"). NUNCA inclua frases faladas como "eu comprei", "almoço no", "gastei", valores ou datas.
- CORREÇÃO FONÉTICA INTELIGENTE: corrija termos falados ou erros de transcrição para os nomes corretos de marcas (ex: "two brothers", "ifood", "starbucks", "mercado livre").
- Calcule datas relativas com precisão: se hoje é ${FinanceEngine.todayKey()}, "ontem" refere-se ao dia anterior.
- APLIQUE a formatação da moeda brasileira (ex: R$ 45,00).
- NÃO faça cálculos soltos para dar saldos: use os dados do CONTEXTO ATUAL acima.
- Se não encontrar um lançamento, diga com sinceridade: "Não encontrei nenhum lançamento de [X] registrado no mês de ${FinanceEngine.monthLabel(selectedMonth)}."
- Seja extremamente DIRETO, OBJETIVO e SUCINTO. Sem sermões não solicitados.
- PROIBIÇÃO ABSOLUTA DE TABELAS MARKDOWN (FORMATAÇÃO MOBILE):
  NUNCA utilize tabelas Markdown com barras verticais (| Coluna 1 | Coluna 2 |). Dispositivos móveis quebram tabelas tornando-as totalmente desalinhadas e ilegíveis na tela. Apresente SEMPRE listas limpas com marcadores (bullet points •) e detalhes organizados.

9. MENSAGENS DE ÁUDIO E COMANDOS POR VOZ (MULTIMODAL):
- Quando houver áudio anexado:
  1. Transcreva com fidelidade logo no início da resposta:
     🎙️ **Áudio:** *"[texto exato falado pelo usuário]"*
  2. Em seguida, processe as transações, divisões, consultas ou despesas solicitadas no áudio!
- Ao registrar, editar ou excluir despesas, confirme em NO MÁXIMO 2 a 3 linhas objetivas.

10. IMPORTAÇÃO DE FATURAS E EXTRATOS BANCÁRIOS EM PDF:
- Se o usuário anexar um PDF (fatura de cartão ou extrato bancário) ou colar dados de lançamentos:
  1. EXTRAÇÃO ANTES DA AGREGAÇÃO: extraia rigorosamente cada linha (data, descrição/contraparte, valor, tipo, método de pagamento e categoria). Os totais são calculados pelo sistema.
     - IGNORE movimentações internas de reserva (Aplicações RDB, Resgates RDB, Guardar Dinheiro) e pagamentos de fatura de cartão já existentes.
  2. IDENTIFIQUE O TIPO DE DOCUMENTO:
     A) EXTRATO DE CONTA BANCÁRIA (Conta Corrente / Poupança / NuConta / Inter / Itaú / Bradesco):
        - ENTRADAS / CRÉDITOS (+, C, Pix Recebido, Salário, Depósito, TED Recebida, Rendimento): type "income", category "Receita", paymentMethod "pix" ou "debit". Entradas AUMENTAM o Saldo Livre!
        - SAÍDAS / DÉBITOS (-, D, Pix Enviado, Compra Débito, Pagamento de Boleto): type "expense", paymentMethod "pix", "debit" ou "bill". NUNCA marque como credit_card a menos que a linha diga "Compra Cartão de Crédito"!
        - competenceMonth: mês em que as transações ocorreram; date: a data de cada movimentação.
     B) FATURA DE CARTÃO DE CRÉDITO (Nubank, Itaú, C6, Santander, Bradesco, etc.):
        - Compras: type "expense", paymentMethod "credit_card".
        - Estornos/créditos: category "Estorno", type "income", amount positivo.
        - IGNORE "Pagamento de fatura recebido", "Pagamento efetuado" ou "Saldo anterior"!
        - competenceMonth: mês de vencimento da fatura.
  3. TRANSPARÊNCIA IMEDIATA: ao pedir confirmação, liste todas as transações válidas em lista limpa com marcadores (•):
       • 05/10/2026: **Salário Empresa XPTO** — R$ 5.200,00 (🟢 Entrada)
       • 06/10/2026: **Pix enviado Brisanet** — R$ 99,90 (🔴 Saída)
     (NUNCA utilize tabelas Markdown com barras verticais | Coluna | pois telas mobile quebram tabelas).
  4. EXECUÇÃO EM LOTE: use SEMPRE BATCH_TRANSACTIONS com a lista completa.

11. CONFIRMAÇÃO PRÉVIA OBRIGATÓRIA (HUMAN-IN-THE-LOOP):
- Criações, edições e exclusões são preparadas em modo RASCUNHO. O sistema exibe automaticamente o card com os botões "✓ Confirmar" e "✕ Cancelar"; a gravação real só ocorre após o clique explícito do usuário (ou se ele responder "sim"/"pode lançar").
- Demonstre que ENTENDEU a solicitação e cite os parâmetros montados (ex: "Preparei o lançamento de **R$ 45,00** em **Padaria** no débito para **Outubro de 2026**. Confira os dados abaixo e confirme para salvar.").
- NUNCA afirme que a ação já foi salva ou gravada!

12. CRIAÇÃO DE GRÁFICOS FINANCEIROS INTERATIVOS (DATA VISUALIZATION):
- Sempre que o usuário pedir uma análise visual, comparativo ou distribuição ("faça um gráfico de...", "distribuição de gastos", "como estão meus investimentos"), ou quando enriquecer a resposta, use a ação GENERATE_CHART (ou um bloco ```chart { "type": "donut", "title": "...", "data": [{ "label": "...", "value": 100.0, "color": "#10b981" }] } ```) com dados numéricos REAIS do contexto (NUNCA invente números).
""".trimIndent()
    }

    private data class ExtractedJsonBlock(
        val fullMatchedText: String,
        val jsonContent: String
    )

    private fun extractActionJsonBlocks(content: String): List<ExtractedJsonBlock> {
        val results = mutableListOf<ExtractedJsonBlock>()

        // 1. Procura blocos cercados por ```json ... ``` ou ``` ... ```
        val fenceRegex = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        for (match in fenceRegex.findAll(content)) {
            val jsonCandidate = match.groupValues[1].trim()
            if (jsonCandidate.contains("\"action\"") || jsonCandidate.contains("\"toolName\"")) {
                results.add(ExtractedJsonBlock(match.value, jsonCandidate))
            }
        }

        // 2. Se não encontrou blocos com fences, procura blocos JSON crus contendo "action" ou "toolName"
        if (results.isEmpty()) {
            val actionPattern = Regex("""\{\s*"(?:action|toolName)"\s*:""")
            val startIndices = actionPattern.findAll(content).map { it.range.first }.toList()

            for (startIndex in startIndices) {
                var depth = 0
                var inString = false
                var escape = false
                var endIndex = -1

                for (i in startIndex until content.length) {
                    val c = content[i]
                    if (escape) {
                        escape = false
                        continue
                    }
                    if (c == '\\') {
                        escape = true
                        continue
                    }
                    if (c == '"') {
                        inString = !inString
                        continue
                    }
                    if (!inString) {
                        if (c == '{') depth++
                        else if (c == '}') {
                            depth--
                            if (depth == 0) {
                                endIndex = i + 1
                                break
                            }
                        }
                    }
                }

                if (endIndex != -1) {
                    val fullJson = content.substring(startIndex, endIndex)
                    results.add(ExtractedJsonBlock(fullJson, fullJson))
                }
            }
        }

        return results
    }

    private suspend fun processGeminiResponseAndExecuteActions(
        rawResponse: String,
        originalQuery: String? = null,
        systemPrompt: String? = null
    ): AgentResponse {
        val (textWithoutChartFence, fenceChart) = sessionManager.extractChartFromMarkdown(rawResponse)
        val extractedBlocks = extractActionJsonBlocks(textWithoutChartFence)

        var cleanText = textWithoutChartFence
        // Limpa SEMPRE todos os blocos de ação JSON do texto visível ao usuário
        for (block in extractedBlocks) {
            cleanText = cleanText.replace(block.fullMatchedText, "")
        }
        // Limpeza adicional defensiva de qualquer bloco de código json residual com action/toolName
        cleanText = cleanText.replace(
            Regex("""```(?:json)?\s*\{[\s\S]*?"(?:action|toolName)"[\s\S]*?\}\s*```""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        if (extractedBlocks.isNotEmpty()) {
            val allActions = mutableListOf<ActionCardData>()
            var lastChartPayload: ChartPayload? = fenceChart
            var fallbackText = ""

            for (block in extractedBlocks) {
                try {
                    val jsonObj = gson.fromJson(block.jsonContent, JsonObject::class.java)
                    if (jsonObj != null && (jsonObj.has("action") || jsonObj.has("toolName"))) {
                        val toolResult = toolsEngine.executeFromJson(jsonObj)
                        if (toolResult.actions != null) {
                            allActions.addAll(toolResult.actions)
                        } else if (toolResult.actionCard != null) {
                            allActions.add(toolResult.actionCard)
                        }
                        if (toolResult.chartPayload != null) {
                            lastChartPayload = toolResult.chartPayload
                        }
                        if (fallbackText.isBlank() && toolResult.text.isNotBlank()) {
                            fallbackText = toolResult.text
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeminiChat", "Erro ao executar ação a partir do JSON: ${block.jsonContent}", e)
                }
            }

            val isPending = allActions.any { it.pendingAction != null }
            val claimsSaved = Regex("""cadastrad[oa] com sucesso|registrad[oa] com sucesso|salv[oa] com sucesso""", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)

            val isSearchOrQuery = extractedBlocks.any { block ->
                val upper = block.jsonContent.uppercase()
                upper.contains("SEARCH_TRANSACTIONS") || upper.contains("QUERY_TRANSACTIONS") ||
                upper.contains("BUSCARTRANSACOES") || upper.contains("CONSULTARTRANSACOES") ||
                upper.contains("\"OPERACAO\": \"LISTAR\"") || upper.contains("\"OPERATION\": \"LISTAR\"")
            }

            // Se a IA solicitou uma busca de transações reais, devolvemos os valores exatos lançados
            // no mês vigente (FinanceRepository / FinanceEngine) de volta para a IA formular a resposta final,
            // superando a limitação de ler apenas a amostra estática do prompt!
            if (isSearchOrQuery && !originalQuery.isNullOrBlank() && !systemPrompt.isNullOrBlank() && fallbackText.isNotBlank()) {
                val followUpPrompt = """
Pergunta original do usuário: "$originalQuery"

Você solicitou a consulta ao banco de dados via ferramenta SEARCH_TRANSACTIONS.
O motor nativo (FinanceRepository / FinanceEngine) executou a busca no banco de transações reais e retornou os seguintes dados oficiais do mês vigente:

$fallbackText

INSTRUÇÃO PARA SUA RESPOSTA FINAL AO USUÁRIO:
- Responda de forma direta e acolhedora em linguagem natural.
- Baseie-se EXCLUSIVAMENTE nos dados reais retornados acima pelo motor nativo.
- FORMATAÇÃO VISUAL MOBILE (RIGOROSO):
  É ESTRITAMENTE PROIBIDO usar tabelas Markdown com barras verticais (| Coluna | Coluna |). O aplicativo mobile NÃO renderiza tabelas e elas ficam totalmente quebradas e ilegíveis na tela do smartphone.
  Formate SEMPRE como uma lista limpa com marcadores (bullet points •) e linhas bem organizadas, por exemplo:
  • **Oficina Della Pizza**: R$ 135,45 no total (Sua parte: **R$ 67,73** • 50% com Amigo)
    📅 19/10/2026 • Alimentação › Restaurante • Pendente (Cartão)
- ATENÇÃO RIGOROSA A COMPRAS DIVIDIDAS (SPLIT / AMIGOS / CASAL):
  Se alguma transação contiver indicação de divisão ou cota do usuário (ex: "(Sua parte: R$ 67,73 • Dividido com...)"), VOCÊ DEVE OBRIGATORIAMENTE DESTACAR O VALOR DA PARTE DO USUÁRIO e o valor total (ex: "Oficina Della Pizza: R$ 135,45 no total da compra, sendo R$ 67,73 a sua parte de 50% dividida com sua amizade"). NUNCA afirme que o usuário gastou ou pagou sozinho o valor integral da compra dividida!
- Se o valor real faturado/pago for diferente da previsão/configuração da Conta Recorrente, explique a diferença ao usuário com clareza.
- Se o banco de dados informou que não há lançamentos reais neste mês, informe com total clareza que existe apenas a previsão/configuração cadastrada, mas nenhum lançamento real foi registrado ou pago ainda.
- NÃO gere novos blocos JSON nesta resposta final.
""".trimIndent()

                try {
                    _uiState.update { it.copy(connectionStatus = "Consultando dados oficiais...") }
                    val followUpResult = loadBalancer.executeWithResilience(
                        userQuery = followUpPrompt,
                        systemInstructionText = systemPrompt,
                        onModelSwitched = { newModel -> _uiState.update { it.copy(selectedModel = newModel) } }
                    )
                    _uiState.update { it.copy(connectionStatus = "Online") }
                    if (followUpResult.responseText.isNotBlank()) {
                        val (finalCleanText, finalChart) = sessionManager.extractChartFromMarkdown(followUpResult.responseText)
                        val cleaned = finalCleanText.replace(
                            Regex("""```(?:json)?\s*\{[\s\S]*?"(?:action|toolName)"[\s\S]*?\}\s*```""", RegexOption.IGNORE_CASE),
                            ""
                        ).trim()
                        if (cleaned.isNotBlank()) {
                            return AgentResponse(
                                text = cleaned,
                                actions = allActions.ifEmpty { null },
                                chartPayload = finalChart ?: lastChartPayload
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GeminiChat", "Erro no follow-up da tool SEARCH_TRANSACTIONS: ${e.message}")
                }
            }

            val finalText = if (isPending) {
                if (cleanText.isNotBlank() && !claimsSaved) cleanText else fallbackText
            } else if (isSearchOrQuery && fallbackText.isNotBlank()) {
                if (cleanText.isNotBlank() && !cleanText.contains("R$")) {
                    "$cleanText\n\n$fallbackText"
                } else {
                    fallbackText
                }
            } else if (cleanText.isNotBlank()) {
                cleanText
            } else if (fallbackText.isNotBlank()) {
                fallbackText
            } else {
                "✅ Ação concluída com sucesso."
            }

            return AgentResponse(
                text = finalText,
                actions = allActions.ifEmpty { null },
                chartPayload = lastChartPayload
            )
        }

        if (fenceChart != null) {
            return AgentResponse(
                text = cleanText.ifBlank { "📊 **Análise Gráfica Gerada**" },
                chartPayload = fenceChart
            )
        }

        return AgentResponse(text = cleanText)
    }

    /**
     * Executa busca de transações reais diretamente pelo repositório/motor nativo.
     */
    suspend fun searchTransactions(
        searchTerm: String? = null,
        category: String? = null,
        month: String? = null
    ): ToolExecutionResult = toolsEngine.executeSearchTransactions(
        searchTerm = searchTerm,
        category = category,
        month = month
    )
}
