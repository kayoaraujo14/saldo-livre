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

    fun confirmPendingAction(pendingAction: PendingAction) {
        val currentMsgs = _uiState.value.messages.map { msg ->
            val card = msg.actions?.firstOrNull() ?: msg.actionCard
            val matchById = card?.pendingAction?.id == pendingAction.id
            val matchByContent = card?.title == pendingAction.title && card.description == pendingAction.description
            if (card != null && (matchById || matchByContent)) {
                msg.copy(
                    actions = listOf(card.copy(
                        pendingAction = pendingAction.copy(status = PendingActionStatus.CONFIRMED)
                    )
                )
            } else msg
        }
        updateActiveSessionMessages(currentMsgs)

        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true) }
            try {
                val toolResult = toolsEngine.executeConfirmedAction(pendingAction)
                val successMsg = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = toolResult.text.ifBlank { "✅ **Ação confirmada e executada com sucesso!**\n\nConcluí a operação **\"${pendingAction.title}\"** e atualizei seus lançamentos e o cálculo do seu Saldo Livre." },
                    actions = toolResult.actions ?: toolResult.actionCard?.let { listOf(it) }
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
            val card = msg.actions?.firstOrNull() ?: msg.actionCard
            val matchById = card?.pendingAction?.id == pendingAction.id
            val matchByContent = card?.title == pendingAction.title && card.description == pendingAction.description
            if (card != null && (matchById || matchByContent)) {
                msg.copy(
                    actions = listOf(card.copy(
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
        var activePending = (lastAssistant?.actions?.firstOrNull() ?: lastAssistant?.actionCard)?.pendingAction
        if (activePending == null && (lastAssistant?.actions?.firstOrNull() ?: lastAssistant?.actionCard)?.title?.contains("Confirmar", ignoreCase = true) == true) {
            val card = lastAssistant.actions?.firstOrNull() ?: lastAssistant.actionCard
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
            val responseResult = executeGeminiOrFallback(promptText, attachment)
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
                    it.copy(connectionStatus = "Online")
                }

                if (result.responseText.isNotBlank()) {
                    return processGeminiResponseAndExecuteActions(result.responseText, query)
                }
            } catch (e: Exception) {
                android.util.Log.e("GeminiChat", "executeGeminiOrFallback falhou, acionando fallback local: ${e.message}", e)
            }
        }

        return processLocalFallback(query)
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
            val sorted = transactions.sortedByDescending { it.dueDate }.take(30)
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
- Usuário logado: ${user.displayName}.
- Parceiro(a) vinculado(a): ${partner?.displayName ?: "nenhum"}.
- Hoje é: ${FinanceEngine.todayKey()}.
- Mês em análise na tela: ${FinanceEngine.monthLabel(selectedMonth)} (${selectedMonth}).
- Cartões cadastrados: ${cardsDesc}.
- Amigos na plataforma: ${friendsDesc}.

${summaryDesc}

${recentTxsDesc}

${pendingTxsDesc}


EXECUÇÃO DE OPERAÇÕES NO BANCO DE DADOS (CRUD COMPLETO):
Sempre que você precisar realizar uma ação ou chamar uma função descrita acima (como registrarTransacao, consultarTransacoes, etc), você DEVE explicar brevemente ao usuário e OBRIGATORIAMENTE INCLUIR NO FINAL UM BLOCO JSON delimitado por ```json ... ``` com a ação correspondente!

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
    { "description": "iFood", "amount": 45.0, "type": "expense", "category": "Alimentação", "paymentMethod": "credit_card" },
    { "description": "Salário", "amount": 5000.0, "type": "income", "category": "Receita", "paymentMethod": "pix" }
  ]
}
```

3. Editar / Corrigir Lançamento Existente:
```json
{
  "action": "EDIT_TRANSACTION",
  "searchTerm": "Padaria",
  "newAmount": 25.0,
  "newCategory": "Alimentação",
  "newSubcategory": "Padaria & Café"
}
```

4. Excluir Lançamento:
```json
{
  "action": "DELETE_TRANSACTION",
  "searchTerm": "Burger King",
  "amount": 50.0
}
```

5. Consultar e Filtrar Lançamentos:
```json
{
  "action": "QUERY_TRANSACTIONS",
  "searchTerm": "Uber",
  "category": null,
  "month": "2026-10"
}
```

6. Expandir Parcelamentos do Mês:
```json
{
  "action": "EXPAND_INSTALLMENTS",
  "month": "2026-11"
}
```

7. Gerar Gráfico de Análise:
```json
{
  "action": "GENERATE_CHART",
  "chartType": "donut",
  "title": "Distribuição de Gastos",
  "data": [
    { "label": "Alimentação", "value": 450.0, "colorHex": "#10B981" },
    { "label": "Transporte", "value": 180.0, "colorHex": "#3B82F6" }
  ]
}
```

8. Memorizar Regra:
```json
{
  "action": "LEARN_RULE",
  "rule": "Todo gasto no Zé Delivery é Lazer e 100% meu"
}
```


REGRAS E PREFERÊNCIAS APRENDIDAS DO USUÁRIO (MEMÓRIA VIVA):
${learnedRulesDesc}

ATENÇÃO: Sempre que uma despesa ou comando do usuário corresponder a uma dessas regras aprendidas, APLIQUE-A IMEDIATAMENTE sem hesitar!
Se o usuário pedir para você aprender uma nova regra, memorizar um comportamento ou esquecer uma regra antiga, chame OBRIGATORIAMENTE a ferramenta "gerenciarRegrasEMemoria"!

REGRAS DE FUNCIONAMENTO:

1. CARTÕES DE CRÉDITO, FATURA E MÊS DE COMPETÊNCIA:
- Compras no cartão de crédito comprometem a renda do MÊS DE VENCIMENTO DA FATURA (competenceMonth), e NÃO a data em que a compra foi feita!
- Se o usuário disser que os gastos virão para a fatura com vencimento em determinado dia/mês (ex: "vence 14/11" ou "fatura de novembro"):
  Defina obrigatoriamente: dataVencimento = "2026-11-14" e mesFatura = "2026-11"! O orçamento que deve ser comprometido é novembro, e não outubro!
- Se a compra foi feita após o dia de fechamento (closingDay) do cartão, a fatura fecha no ciclo seguinte e compromete o mês de vencimento (dueDay).

2. AMIGOS NA REDE E DIVISÃO DE CONTAS (PORCENTAGEM, VALORES ESPECÍFICOS E MULTI-PESSOAS):
- Se o usuário disser que um gasto é de um amigo na plataforma (ex: "KAYO", "kayoaraujo2002@gmail.com"):
  * Identifique o amigo (emailAmigo = "kayoaraujo2002@gmail.com", nomeAmigo = "Kayo").
  * Se for 100% do amigo: use tipoDivisao = "AMIGO" (ou porcentagemAmigo = 100).
  * Se o usuário disser que dividiu com o amigo em porcentagem ou valor (ex: "almoço R$ 150 onde Kayo paga 40% e eu 60%", "Kayo paga R$ 50", "dividi com Kayo meio a meio"):
    Preencha porcentagemAmigo: 40 (ou valorAmigo: 50). O sistema calcula os valores exatos, aloca a cota correta de cada um, gera a cobrança com aceite mútuo para o amigo e desconta do Saldo Livre do usuário apenas a parte dele!
  * Se a despesa for dividida entre mais de 2 pessoas (ex: "Eu, Kayo e minha Mãe"):
    Preencha divisoesPersonalizadas com cada participante (nome, email se amigo, porcentagem ou valor).
- Se for pessoa sem conta na plataforma (ex: "Mãe", "Pai", "Irmão"):
  Use tipoDivisao = "TERCEIRO" com nomeTerceiro = "Mãe".

3. ESTORNOS E REEMBOLSOS (VALORES NEGATIVOS):
- Se o gasto for negativo (ex: "99FOOD -R$ 8,40" ou "UBER -R$ 4,09"), defina obrigatoriamente categoria = "Estorno" e envie o valor negativo (ex: -8.40). Trata-se de estorno/crédito na fatura que alivia o Saldo Livre.

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
- Ao registrar ou editar lançamentos (individuais ou lote), preencha SEMPRE "categoria" e "subcategoria" de acordo com o estabelecimento (ex: "iFood" -> categoria: "Alimentação", subcategoria: "Delivery & Lanches"; "Pão de Açúcar" -> categoria: "Alimentação", subcategoria: "Supermercado"; "Uber" -> categoria: "Transporte", subcategoria: "Aplicativos (Uber/99)"; "Financiamento Habitacional / Caixa" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Financiamento Imobiliário"; "Financiamento Carro" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Financiamento Veicular"; "Empréstimo Pessoal Nubank" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Empréstimo Pessoal"; "Amortização de Parcela" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Amortização de Financiamento").
- Se o usuário pedir relatórios analíticos de um grupo ou subgrupo (ex: "quanto gastei com restaurante?", "quanto foi mercado vs delivery?"), chame "consultarTransacoes" com subcategoria ou utilize o relatório do mês, gerando respostas comparativas detalhadas com clareza.

5. COMPRAS PARCELADAS:
- Notações como "9/12", "2/6" ou "10/12" indicam compras parceladas. Preencha parcelaAtual e totalParcelas (ex: parcelaAtual: 9, totalParcelas: 12).
- O sistema aloca AUTOMATICAMENTE toda a série de parcelas no banco de dados pelos meses correspondentes (passadas como pagas, atual e futuras como pendentes).
- AO EDITAR CATEGORIA OU SUBCATEGORIA DE COMPRA PARCELADA: passe APENAS termoBusca, novaCategoria e novaSubcategoria. NUNCA passe novoMesFatura nem novaDataVencimento, pois cada parcela tem sua própria data e mês cronológico individual!
- IMPORTANTE: Confirme a operação de forma simples e direta no mês atual. NÃO fique gerando previsões ou projeções detalhadas de meses futuros ao final da resposta a menos que o usuário peça especificamente.
- Se o usuário pedir para gerar, expandir ou sincronizar as parcelas passadas e futuras de compras já existentes:
  Chame a ferramenta "expandirParcelamentos" com mes: "2026-11"!

5. REGISTRO EM LOTE:
- Quando o usuário enviar uma lista com vários gastos de uma vez, use SEMPRE a ferramenta "registrarTransacoesLote". Isso processa todas as despesas em uma única chamada rápida!

6. EDIÇÃO E EXCLUSÃO CIRÚRGICA DE TRANSAÇÕES (NUNCA EXCLUA EM LOTE SEM PEDIDO EXPLÍCITO):
- Para alterar categoria ou subcategoria de uma despesa (avulsa ou parcelada):
  Chame "editarTransacoes" passando termoBusca, novaCategoria e novaSubcategoria (NUNCA passe novoMesFatura nem novaDataVencimento a menos que o usuário peça expressamente para mudar o vencimento!).
- Para excluir um lançamento específico que o usuário pediu (ex: "exclua o almoço de R$ 30", "apague o Supermercado", "delete a compra X"):
  * Consulte os "LANÇAMENTOS REAIS REGISTRADOS NESTE MÊS" listados acima no contexto.
  * Localize a transação correspondente e passe SEMPRE o ID específico em ids: ["id_da_transacao"]!
  * Se não tiver o ID, passe o termoBusca COM O NOME EXATO DO ESTABELECIMENTO e o valor: XX.XX em reais para desambiguação sem erros!
  * Defina SEMPRE limiteQuantidade: 1 para exclusão de item individual.
  * NUNCA use palavras genéricas como "compra", "despesa" ou "gasto" em termoBusca!
  * NUNCA defina apenasRecentes: true ou tudoDoMes: true a menos que o usuário peça expressamente com palavras como: "desfazer último lote", "limpar todos os recentes" ou "apagar tudo do mês"!
- Se o usuário pedir para mover despesas avulsas cadastradas por engano para outro mês/fatura:
  Chame "editarTransacoes" com mesOrigem, novoMesFatura e novaDataVencimento!
- Se o usuário perguntar sobre compras anteriores ou pesquisar despesas específicas:
  Chame "consultarTransacoes" para obter a lista exata!

7. DIRETRIZES DE ASSERTIVIDADE, VERACIDADE E ZERO ALUCINAÇÃO (GUARDRAILS COMPORTAMENTAIS):
- REGRA CRÍTICA DE INTEGRIDADE FINANCEIRA: Você está estritamente proibido de inventar, estimar ou deduzir valores de transações para fazer a soma bater com um total informado anteriormente. Toda listagem deve refletir caractere por caractere o documento original. Se os itens individuais não estiverem disponíveis no seu contexto atual, releia a ferramenta/documento original antes de responder.
- NUNCA invente, deduza ou presuma números, parâmetros faltantes, lançamentos, dívidas, faturas ou saldos que não constem nos dados reais informados acima.
- SE FALTAR UM PARÂMETRO OBRIGATÓRIO (como valor, data de competência ou divisão), PERGUNTE AO USUÁRIO em vez de preencher valores prováveis ou inventados. O fluxo deve parar e aguardar a resposta dele.
- CITE OS IDs DE REFERÊNCIA, categorias e números de comprovantes (se houver) sempre que confirmar a preparação de uma tarefa de CRUD/Lançamento.
- APLIQUE RIGOROSAMENTE a formatação da moeda corrente nas respostas visuais (ex: R$ 45,00 em vez de 45 ou $45).
- NÃO faça cálculos matemáticos soltos por conta própria para dar saldos. Você deve sempre consultar os dados fornecidos no CONTEXTO ATUAL acima (soma no backend) ou chamar "obterRelatorioEProjecaoMensal".
- NUNCA afirme que o usuário comprou algo ou gastou determinado valor a menos que conste nos LANÇAMENTOS REAIS acima ou seja retornado pela ferramenta "consultarTransacoes".
- Se o usuário perguntar algo sobre seu extrato, compras passadas ou se um pagamento foi feito, consulte os LANÇAMENTOS REAIS acima ou chame "consultarTransacoes". Se não encontrar, diga com sinceridade e exatidão: "Não encontrei nenhum lançamento de [X] registrado no mês de ${FinanceEngine.monthLabel(selectedMonth)}."
- Seja extremamente DIRETO, OBJETIVO e SUCINTO. Elimine rodeios, introduções prolixas, encerramentos repetitivos e sermões não solicitados.

8. MENSAGENS DE ÁUDIO E COMANDOS POR VOZ (MULTIMODAL):
- Quando o usuário gravar áudio ou enviar mensagem de voz (ou se houver áudio anexado):
  1. Transcreva com fidelidade logo no início da sua resposta:
     🎙️ **Áudio:** *"[texto exato falado pelo usuário]"*
  2. Em seguida, processe e execute imediatamente as transações, contas divididas, consultas ou despesas solicitadas na mensagem de áudio!
- Ao registrar, editar ou excluir despesas, confirme a operação em NO MÁXIMO 2 a 3 linhas objetivas.
- NÃO dê sermões ou lições financeiras a menos que o usuário explicitamente peça conselhos.

8. IMPORTAÇÃO DE FATURAS E EXTRATOS BANCÁRIOS EM PDF (ONBOARDING & GASTOS ANTERIORES):
- Se o usuário anexar um arquivo PDF (fatura de cartão ou extrato de conta bancária) ou colar dados de lançamentos:
  1. EXTRAÇÃO ANTES DA AGREGAÇÃO (CHAIN-OF-THOUGHT):
     - O cálculo de totais nunca deve ser feito pela LLM de cabeça. A LLM deve apenas extrair as linhas literais do documento, e o código da ferramenta "registrarTransacoesLote" deve somar os valores a partir da lista extraída.
     - Extraia rigorosamente cada linha com: data, descricao/contraparte, valorTotal, tipo ("income" ou "expense"), metodoPagamento e categoria.
     - IGNORE movimentações internas de reserva (Aplicações RDB, Resgates RDB, Guardar Dinheiro) e pagamentos de fatura de cartão já existentes, marcando-as como categoria_filtro: "ignorada" para que não distorçam os totais de receitas nem despesas.

  2. IDENTIFIQUE COM PRECISÃO O TIPO DE DOCUMENTO:
     A) SE FOR EXTRATO DE CONTA BANCÁRIA (CONTA CORRENTE / POUPANÇA / NUCONTA / INTER CONTA / ITAÚ EXTRATO / BRADESCO):
        - Defina obrigatoriamente: origemDocumento = "extrato_conta" e metodoPagamentoPadrao = "pix" ou "debit"!
        - TODAS as ENTRADAS / CRÉDITOS / VALORES POSITIVOS (identificados com +, C, Crédito, Pix Recebido, Salário, Depósito, TED Recebida, Transferência Recebida, Rendimento):
          * tipo: "income"
          * categoria: "Receita"
          * metodoPagamento: "pix" (ou "debit")
          * status: "paid"
          * NUNCA marque como despesa de cartão de crédito! Entradas AUMENTAM o Saldo Livre!
        - TODAS as SAÍDAS / DÉBITOS / GASTOS DE CONTA (identificados com -, D, Débito, Pix Enviado, Transferência Enviada, Compra Débito, Pagamento de Boleto):
          * tipo: "expense"
          * metodoPagamento: "pix" (para Pix), "debit" (para débito) ou "bill" (para boletos)
          * status: "paid"
          * NUNCA marque como cartão de crédito (credit_card) a menos que a linha do extrato diga expressamente "Compra Cartão de Crédito"!
        - mesFaturaPadrao: mês em que as transações ocorreram (ex: "2026-10").
        - dataVencimentoPadrao: a data de cada movimentação.

     B) SE FOR FATURA DE CARTÃO DE CRÉDITO (FATURA NUBANK, FATURA ITAÚ CARTÕES, C6, SANTANDER, BRADESCO, ETC.):
        - Defina obrigatoriamente: origemDocumento = "fatura_cartao" e metodoPagamentoPadrao = "credit_card"!
        - As compras no cartão: tipo = "expense", metodoPagamento = "credit_card", status = "pending".
        - Estornos/créditos de devolução: valorTotal negativo (ex: -15.20) e categoria = "Estorno".
        - ATENÇÃO: IGNORE linhas de "Pagamento de fatura recebido", "Pagamento efetuado" ou "Saldo anterior" para NÃO duplicar lançamentos nem somar como novo gasto de cartão!
        - mesFaturaPadrao: mês de vencimento da fatura (ex: "2026-11").

  3. TRANSPARÊNCIA IMEDIATA NA CONFIRMAÇÃO (PRIMEIRA RESPOSTA AO USUÁRIO):
     - Sempre que pedir para o usuário clicar em **🟢 Confirmar e Executar**, JÁ EXIBA DE IMEDIATO na sua mensagem de resposta a tabela completa e formatada em Markdown com TODAS as transações válidas que compõem aquele total (Data | Tipo | Descrição / Contraparte | Valor), evitando que o usuário aprove uma importação às cegas ou precise pedir a listagem em uma segunda mensagem!
     - Exemplo de formato obrigatório na resposta:
       | Data | Tipo | Descrição / Contraparte | Valor |
       | :--- | :--- | :--- | :--- |
       | 05/10/2026 | 🟢 Entrada | Salário Empresa XPTO | R$ 5.200,00 |
       | 06/10/2026 | 🔴 Saída | Pix enviado Brisanet | R$ 99,90 |
     - Destaque com clareza o total exato de receitas (entradas) e de despesas (saídas) calculados pela ferramenta.

  4. EXECUÇÃO EM LOTE:
     - Chame SEMPRE a ferramenta "registrarTransacoesLote" preenchendo origemDocumento, metodoPagamentoPadrao e a lista completa de transações com cada campo tipo, metodoPagamento, categoria, contraparte e categoria_filtro!

10. CONFIRMAÇÃO PRÉVIA OBRIGATÓRIA ANTES DE QUALQUER EXECUÇÃO NO SISTEMA (HUMAN-IN-THE-LOOP):
- NUNCA assuma que você executa as ações no banco diretamente. Antes de efetivar alterações no banco de dados, o backend (Firestore) é bloqueado e as ferramentas SEMPRE retornam "aguardandoConfirmacao: true" em um estado de DRAFT (rascunho).
- QUANDO A FERRAMENTA RETORNAR "aguardandoConfirmacao: true":
  * O seu papel é apenas preparar a ação e exibi-la para o usuário.
  * Demonstre com total clareza que você ENTENDEU a solicitação e cite os parâmetros montados.
  * Resuma o que você entendeu de forma direta:
    - Exemplo de despesa: "Entendi que você deseja registrar uma despesa de **R$ 45,00** na **Padaria** no débito para o mês de **Outubro 2026**."
  * Convide o usuário a revisar os detalhes no cartão visual e confirmar clicando no botão **🟢 Confirmar e Executar** (a execução real no banco ocorrerá apenas após esse clique explícito do usuário).
  * NUNCA afirme que a ação já foi salva ou gravada! Diga sempre que a transação foi "preparada/criada em modo rascunho" e está "aguardando a aprovação e confirmação explícita".

11. CRIAÇÃO DE GRÁFICOS FINANCEIROS INTERATIVOS (DATA VISUALIZATION):
Sempre que o usuário pedir uma análise visual, comparativo, projeção gráfica ou perguntar "faça um gráfico de...", "analise com gráfico", "mostre a evolução", "distribuição de gastos", "como estão meus investimentos", ou você achar relevante enriquecer a resposta com visualização, inclua em sua resposta um bloco de código markdown no formato:

\
""".trimIndent()
    }

    private suspend fun processGeminiResponseAndExecuteActions(
        rawResponse: String,
        userQuery: String
    ): AgentResponse {
        val jsonRegex = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        val rawJsonRegex = Regex("""(\{\s*"action"\s*:\s*"[A-Z_]+"[^}]*?\})""")

        val allMatches = jsonRegex.findAll(rawResponse).toList().ifEmpty { rawJsonRegex.findAll(rawResponse).toList() }

        if (allMatches.isNotEmpty()) {
            try {
                val allActions = mutableListOf<ActionCardData>()
                var cleanText = rawResponse
                var lastChartPayload: ChartPayload? = null
                var fallbackText = ""

                for (match in allMatches) {
                    val jsonString = match.groupValues[1]
                    val jsonObj = gson.fromJson(jsonString, JsonObject::class.java)
                    val toolResult = toolsEngine.executeFromJson(jsonObj)

                    cleanText = cleanText.replace(match.value, "").trim()
                    if (toolResult.actions != null) {
                        allActions.addAll(toolResult.actions)
                    } else if (toolResult.actionCard != null) {
                        allActions.add(toolResult.actionCard)
                    }
                    if (toolResult.chartPayload != null) {
                        lastChartPayload = toolResult.chartPayload
                    }
                    if (fallbackText.isBlank()) fallbackText = toolResult.text
                }

                val isPending = allActions.any { it.pendingAction != null }
                val claimsSaved = Regex("""cadastrad[oa] com sucesso|registrad[oa] com sucesso|salv[oa] com sucesso""", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)

                val finalText = if (isPending) {
                    if (cleanText.isNotBlank() && !claimsSaved) cleanText else fallbackText
                } else if (cleanText.isNotBlank()) {
                    cleanText
                } else {
                    fallbackText
                }

                return AgentResponse(
                    text = finalText,
                    actions = allActions.ifEmpty { null },
                    chartPayload = lastChartPayload
                )
            } catch (_: Exception) {
                // Ignore
            }
        }

        // Se o Gemini não gerou bloco JSON mas o usuário solicitou uma ação de CRUD,
        // o motor de ferramentas local processa para garantir a execução no banco de dados!
        val offlineResult = toolsEngine.processNaturalLanguageOffline(userQuery)
        val isDefaultFallback = offlineResult.text.startsWith("Estou pronto para ajudar! Você pode me pedir para:")

        if (!isDefaultFallback || offlineResult.actions != null || offlineResult.actionCard != null || offlineResult.chartPayload != null) {
            val finalText = if ((offlineResult.actions?.firstOrNull() ?: offlineResult.actionCard)?.pendingAction != null) {
                offlineResult.text
            } else if (!isDefaultFallback) {
                offlineResult.text
            } else {
                "${rawResponse.trim()}\n\n${offlineResult.text}".trim()
            }
            return AgentResponse(
                text = finalText,
                actions = offlineResult.actions,
                actionCard = offlineResult.actions?.firstOrNull(),
                chartPayload = offlineResult.chartPayload
            )
        }

        return AgentResponse(text = rawResponse.trim())
    }

    private suspend fun processLocalFallback(query: String): AgentResponse {
        val toolResult = toolsEngine.processNaturalLanguageOffline(query)
        return AgentResponse(
            text = toolResult.text,
            actions = toolResult.actions ?: toolResult.actionCard?.let { listOf(it) },
            chartPayload = toolResult.chartPayload
        )
    }
}
