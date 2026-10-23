package com.saldolivre.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.parser.ParsedStatementItem
import com.saldolivre.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiChatScreen(
    viewModel: GeminiChatViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Launcher para gravação de áudio (Permissão do Microfone)
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording(context)
        }
    }

    // Launcher para selecionar arquivo (PDF, OFX, CSV, TXT ou Imagem)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "extrato.pdf"
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }
                val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (bytes.isNotEmpty()) {
                    val lowerName = fileName.lowercase()
                    val isTextFile = lowerName.endsWith(".ofx") ||
                        lowerName.endsWith(".qfx") ||
                        lowerName.endsWith(".csv") ||
                        lowerName.endsWith(".txt") ||
                        mimeType.startsWith("text/")
                    val textContent = if (isTextFile) {
                        runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
                    } else null

                    viewModel.onAttachmentSelected(
                        name = fileName,
                        size = if (fileSize > 0L) fileSize else bytes.size.toLong(),
                        mimeType = mimeType,
                        bytes = bytes,
                        textContent = textContent
                    )
                }
            } catch (e: Exception) {
                // Ignore picker errors
            }
        }
    }

    var isInitialScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            if (!isInitialScrollDone) {
                listState.scrollToItem(uiState.messages.size - 1)
                isInitialScrollDone = true
            } else {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // Modal Sheet de Histórico de Conversas (Sessões)
    if (uiState.isHistoryOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setHistoryOpen(false) },
            containerColor = DarkSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = DarkBorder) }
        ) {
            ChatHistorySheetContent(
                sessions = uiState.sessions,
                currentSessionId = uiState.currentSessionId,
                onSelectSession = { viewModel.onSelectSession(it) },
                onNewSession = { viewModel.onNewSession() },
                onDeleteSession = { viewModel.onDeleteSession(it) },
                onClose = { viewModel.setHistoryOpen(false) }
            )
        }
    }

    // Modal Sheet de Regras Aprendidas (Memória Viva)
    if (uiState.isRulesOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setRulesOpen(false) },
            containerColor = DarkSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = DarkBorder) }
        ) {
            LearnedRulesSheetContent(
                rules = uiState.learnedRules,
                newRuleInput = uiState.newRuleInput,
                onInputChange = { viewModel.onNewRuleInputChange(it) },
                onAddRule = { viewModel.onAddRule() },
                onDeleteRule = { viewModel.onDeleteRule(it) },
                onClose = { viewModel.setRulesOpen(false) }
            )
        }
    }

    // Modal Sheet de Revisão de Extrato / Fatura (PDF / OFX / CSV / TXT)
    if (uiState.isStatementReviewOpen && uiState.parsedStatementItems.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setStatementReviewOpen(false) },
            containerColor = DarkSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = DarkBorder) }
        ) {
            StatementReviewSheetContent(
                fileName = uiState.statementFileName,
                items = uiState.parsedStatementItems,
                availableCards = uiState.availableCards,
                selectedCardId = uiState.selectedImportCardId,
                hideIgnoredItems = uiState.hideIgnoredStatementItems,
                isImporting = uiState.isImportingStatement,
                onSelectCard = { viewModel.setSelectedImportCard(it) },
                onToggleHideIgnored = { viewModel.setHideIgnoredStatementItems(it) },
                onToggleSelectAll = { viewModel.toggleSelectAllStatementItems(it) },
                onToggleItem = { viewModel.toggleStatementItemSelection(it) },
                onConfirmImport = { viewModel.confirmStatementImport() },
                onSendToAiChat = {
                    viewModel.setStatementReviewOpen(false)
                    viewModel.sendMessage()
                }
            )
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF131D31), DarkBackground)
                        )
                    )
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary
                    ),
                    navigationIcon = {
                        Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                            ChatTopBarButton(
                                onClick = onNavigateBack,
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextPrimary
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Copiloto Saldo Livre",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                        )
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            // 1. Botão Criar Novo Chat
                            ChatTopBarButton(
                                onClick = { viewModel.onNewSession() },
                                icon = Icons.Rounded.Add,
                                contentDescription = "Criar novo chat",
                                tint = TextPrimary
                            )

                            // 2. Botão Histórico de Conversas (Sessões)
                            ChatTopBarButton(
                                onClick = { viewModel.setHistoryOpen(true) },
                                icon = Icons.Rounded.History,
                                contentDescription = "Histórico de conversas",
                                tint = TextSecondary
                            )

                            // 3. Botão Dicas / Memória (Regras Aprendidas)
                            val hasRules = uiState.learnedRules.isNotEmpty()
                            ChatTopBarButton(
                                onClick = { viewModel.setRulesOpen(true) },
                                icon = Icons.Rounded.Lightbulb,
                                contentDescription = "Dicas e regras do Copiloto",
                                tint = TextSecondary,
                                hasBadge = hasRules
                            )
                        }
                    }
                )
                // Linha sutil luminosa separando o cabeçalho smart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Emerald500.copy(alpha = 0.35f),
                                    Cyan500.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, Color(0xFF0B1120), DarkBackground)
                    )
                )
        ) {
            // Lista de Mensagens do Chat
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = uiState.messages,
                    key = { _, msg -> msg.id },
                    contentType = { _, _ -> "chat_message" }
                ) { index, msg ->
                    ChatBubble(
                        message = msg,
                        isActionInProgress = uiState.isTyping,
                        onConfirmAction = { viewModel.confirmPendingAction(it) },
                        onCancelAction = { viewModel.cancelPendingAction(it) }
                    )

                    // Prompt Starters (Onboarding) logo abaixo da primeira mensagem do assistente
                    if (index == 0 && msg.sender == MessageSender.ASSISTANT) {
                        Spacer(modifier = Modifier.height(10.dp))
                        PromptStartersSection(
                            onQuickExpense = { viewModel.onInputTextChange("Lançar gasto de R$ ") },
                            onImportPdf = { filePickerLauncher.launch("*/*") },
                            onViewCharts = { viewModel.sendMessage(overrideText = "Mostrar gráficos dos meus gastos do mês") },
                            onDailyLimit = { viewModel.sendMessage(overrideText = "Qual meu limite diário para hoje?") }
                        )
                    }
                }

                if (uiState.isTyping) {
                    item {
                        val thinkingShape = RoundedCornerShape(
                            topStart = 6.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .shadow(6.dp, thinkingShape, spotColor = Cyan500.copy(alpha = 0.25f))
                                .clip(thinkingShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF152238), DarkSurfaceVariant)
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(Emerald500.copy(alpha = 0.45f), Cyan500.copy(alpha = 0.3f))
                                    ),
                                    thinkingShape
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Emerald400,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (uiState.isTranscribingAudio) "Transcrevendo mensagem de voz..."
                                        else uiState.connectionStatus?.takeIf { it != "Online" } ?: "Copiloto processando análise...",
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Sincronizado com seu Saldo Livre em tempo real",
                                    fontSize = 10.sp,
                                    color = Cyan400
                                )
                            }
                        }
                    }
                }
            }

            // Preview de Anexo Pendente (Pill flutuante acima do input)
            if (uiState.pendingAttachment != null) {
                val att = uiState.pendingAttachment!!
                val isAudio = att.type.startsWith("audio/") || att.name.endsWith(".m4a")
                val extractedCount = uiState.parsedStatementItems.count { it.isSelected }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF132530), DarkSurfaceVariant)
                            )
                        )
                        .border(1.dp, Emerald500.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.Description,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = att.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeKb = (att.size / 1024L).coerceAtLeast(1L)
                        val subtitle = when {
                            isAudio -> "Áudio gravado • ${sizeKb} KB"
                            uiState.parsedStatementItems.isNotEmpty() -> "${uiState.parsedStatementItems.size} lançamentos detectados • ${sizeKb} KB"
                            else -> "Arquivo anexado • ${sizeKb} KB"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            color = if (uiState.parsedStatementItems.isNotEmpty()) Emerald400 else TextMuted
                        )
                    }
                    if (uiState.parsedStatementItems.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.setStatementReviewOpen(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Revisar ($extractedCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.onRemoveAttachment() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remover anexo",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Área de Entrada da Mensagem (Layout unificado com dimensões idênticas entre Digitação e Gravação)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Container da Pílula Principal (mantém exatamente 56.dp de altura base em ambos os estados)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isRecordingAudio) {
                        // Pílula escura de gravação com 56.dp de altura (idêntica ao TextField)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1A253A), DarkSurface)
                                    )
                                )
                                .border(1.dp, Rose500.copy(alpha = 0.5f), CircleShape)
                                .padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Rose500)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val sec = uiState.recordingSeconds
                            val timerStr = String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60)
                            Text(
                                text = "Ouvindo comando de voz... $timerStr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Rose400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Botão de cancelar (X) como IconButton de tamanho fixo (40.dp)
                            IconButton(
                                onClick = { viewModel.cancelVoiceRecording() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurfaceVariant.copy(alpha = 0.85f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cancelar gravação",
                                        tint = Rose400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // TextField em formato de Pílula (CircleShape) com 56.dp de altura base
                        TextField(
                            value = uiState.inputText,
                            onValueChange = { viewModel.onInputTextChange(it) },
                            placeholder = {
                                Text(
                                    text = "Mensagem ou lançamento...",
                                    color = TextMuted,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                IconButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AttachFile,
                                        contentDescription = "Anexar extrato PDF, OFX, CSV ou TXT",
                                        tint = if (uiState.pendingAttachment != null) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (uiState.inputText.isNotBlank()) Emerald500.copy(alpha = 0.5f) else DarkBorder,
                                    shape = CircleShape
                                ),
                            shape = CircleShape,
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.85f),
                                unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.65f),
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = Emerald400
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botão de Ação Circular Dedicado (48.dp fixo: Microfone ↔ Enviar ↔ Confirmar Áudio)
                val actionButtonState = when {
                    uiState.isRecordingAudio -> "RECORDING"
                    uiState.inputText.isNotBlank() || uiState.pendingAttachment != null -> "SEND"
                    else -> "MIC"
                }
                AnimatedContent(
                    targetState = actionButtonState,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.8f, animationSpec = tween(180)))
                            .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                    },
                    label = "ActionFabTransition"
                ) { state ->
                    when (state) {
                        "RECORDING" -> {
                            IconButton(
                                onClick = { viewModel.stopVoiceRecording(sendImmediately = true) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(Emerald400, Cyan500))
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Concluir áudio",
                                    tint = DarkBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        "SEND" -> {
                            val isEnabled = !uiState.isTyping
                            IconButton(
                                onClick = { if (isEnabled) viewModel.sendMessage() },
                                enabled = isEnabled,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Emerald400, Cyan500)))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Enviar",
                                    tint = DarkBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.startVoiceRecording(context)
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Emerald500)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Falar por áudio",
                                    tint = DarkBackground,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// BOTÃO REUTILIZÁVEL DO CABEÇALHO / SHEETS DO CHAT
// -------------------------------------------------------------
@Composable
private fun ChatTopBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hasBadge: Boolean = false
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            if (hasBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Amber400)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// CHIPS DE SUGESTÃO / ONBOARDING (PROMPT STARTERS)
// -------------------------------------------------------------
@Composable
private fun PromptStartersSection(
    onQuickExpense: () -> Unit,
    onImportPdf: () -> Unit,
    onViewCharts: () -> Unit,
    onDailyLimit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PromptStarterCard(
                icon = Icons.Rounded.FlashOn,
                label = "Lançar gasto rápido",
                onClick = onQuickExpense,
                modifier = Modifier.weight(1f)
            )
            PromptStarterCard(
                icon = Icons.Rounded.PictureAsPdf,
                label = "Importar fatura PDF",
                onClick = onImportPdf,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PromptStarterCard(
                icon = Icons.Rounded.BarChart,
                label = "Ver gráficos",
                onClick = onViewCharts,
                modifier = Modifier.weight(1f)
            )
            PromptStarterCard(
                icon = Icons.Rounded.Lightbulb,
                label = "Meu limite diário",
                onClick = onDailyLimit,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PromptStarterCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DarkSurfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.28f)),
        modifier = modifier.heightIn(min = 42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Emerald500.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// -------------------------------------------------------------
// SHEET DE HISTÓRICO DE SESSÕES
// -------------------------------------------------------------
@Composable
private fun ChatHistorySheetContent(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Histórico de Conversas",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${sessions.size} conversas salvas no histórico",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            ChatTopBarButton(
                onClick = onClose,
                icon = Icons.Rounded.Close,
                contentDescription = "Fechar histórico",
                tint = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Botão "+ Nova Conversa"
        Button(
            onClick = onNewSession,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = DarkBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Iniciar Nova Conversa", color = DarkBackground, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de Sessões Anteriores
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sessions) { session ->
                val isCurrent = session.id == currentSessionId
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.updatedAt))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) DarkSurfaceVariant else DarkSurface)
                        .border(1.dp, if (isCurrent) Emerald500.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelectSession(session.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = session.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) Emerald400 else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isCurrent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Atual",
                                    fontSize = 10.sp,
                                    color = Emerald400,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$dateStr • ${session.messages.size} mensagens",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onDeleteSession(session.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir conversa",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SHEET DE REGRAS APRENDIDAS (MEMÓRIA VIVA)
// -------------------------------------------------------------
@Composable
private fun LearnedRulesSheetContent(
    rules: List<UserLearnedRule>,
    newRuleInput: String,
    onInputChange: (String) -> Unit,
    onAddRule: () -> Unit,
    onDeleteRule: (String) -> Unit,
    onClose: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Memória do Copiloto (Regras)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "O Copiloto memoriza preferências financeiras para categorizar e lançar gastos automaticamente do seu jeito.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            ChatTopBarButton(
                onClick = onClose,
                icon = Icons.Rounded.Close,
                contentDescription = "Fechar regras",
                tint = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Adicionar nova regra manualmente
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newRuleInput,
                onValueChange = onInputChange,
                placeholder = { Text("Ex: Almoço no RU é R$ 12 no débito...", color = TextMuted, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAddRule,
                enabled = newRuleInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text("+ Adicionar", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Nenhuma regra personalizada ainda.", fontSize = 13.sp, color = TextMuted)
                    Text(text = "Diga no chat: \"Aprenda que almoço no RU é Alimentação...\"", fontSize = 11.sp, color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules) { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = rule.rule,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = { onDeleteRule(rule.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remover regra",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CONVERSÃO INTELIGENTE DE TABELAS MARKDOWN PARA LISTAS MOBILE
// -------------------------------------------------------------
private fun convertMarkdownTables(rawText: String): String {
    if (!rawText.contains("|")) return rawText

    val lines = rawText.lines()
    val result = mutableListOf<String>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (isTableRow(trimmed) && i + 1 < lines.size && isTableSeparator(lines[i + 1].trim())) {
            val headerCells = splitTableRow(trimmed)
            i += 2 // pula cabeçalho e separador

            val dataRows = mutableListOf<List<String>>()
            while (i < lines.size && isTableRow(lines[i].trim())) {
                val rowTrimmed = lines[i].trim()
                if (!isTableSeparator(rowTrimmed)) {
                    val rowCells = splitTableRow(rowTrimmed)
                    if (rowCells.isNotEmpty()) {
                        dataRows.add(rowCells)
                    }
                }
                i++
            }

            val formattedTable = formatTableForMobile(headerCells, dataRows)
            if (formattedTable.isNotBlank()) {
                result.add(formattedTable)
            }
        } else {
            result.add(line)
            i++
        }
    }

    return result.joinToString("\n")
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("|")) return false
    if (trimmed.startsWith("|") && trimmed.endsWith("|")) return true
    return trimmed.count { it == '|' } >= 2
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("|") || !trimmed.contains("-")) return false
    val cells = splitTableRow(trimmed)
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && c.all { it == '-' || it == ':' || it == ' ' } && c.contains('-')
    }
}

private fun splitTableRow(line: String): List<String> {
    val trimmed = line.trim()
    val stripped = trimmed.removePrefix("|").removeSuffix("|")
    return stripped.split("|").map { it.trim() }
}

private fun formatTableForMobile(headers: List<String>, rows: List<List<String>>): String {
    if (rows.isEmpty()) return ""

    val headersLower = headers.map { it.lowercase().trim() }

    val titleIdx = headersLower.indexOfFirst {
        it.contains("descri") || it.contains("nome") || it.contains("estabelecimento") ||
        it.contains("contraparte") || it.contains("item") || it.contains("serviço") ||
        it.contains("servico") || it.contains("conta")
    }

    val totalValIdx = headersLower.indexOfFirst {
        it.contains("valor total") || it.contains("total")
    }

    val valIdx = if (totalValIdx != -1) totalValIdx else headersLower.indexOfFirst {
        it.contains("valor") || it.contains("preço") || it.contains("preco") || it.contains("quantia")
    }

    val impactIdx = headersLower.indexOfFirst {
        it.contains("impacto") || it.contains("cota") || it.contains("sua parte") || it.contains("seu impacto")
    }

    val dateIdx = headersLower.indexOfFirst {
        it.contains("data") || it.contains("vencimento") || it.contains("dia")
    }

    val catIdx = headersLower.indexOfFirst {
        it.contains("subcategoria") || it.contains("categoria")
    }

    val statusIdx = headersLower.indexOfFirst {
        it.contains("status") || it.contains("situação") || it.contains("situacao") ||
        it.contains("tipo") || it.contains("método") || it.contains("metodo") || it.contains("pagamento")
    }

    // Tabela simples Chave / Valor de 2 colunas
    if (headers.size == 2 && (titleIdx == -1 || valIdx == -1)) {
        return rows.joinToString("\n") { row ->
            val key = row.getOrNull(0)?.removePrefix("**")?.removeSuffix("**")?.trim().orEmpty()
            val value = row.getOrNull(1)?.trim().orEmpty()
            if (key.isNotBlank()) "• **$key**: $value" else value
        }
    }

    val formattedRows = rows.mapNotNull { row ->
        val resolvedTitleIdx = if (titleIdx != -1) titleIdx else {
            if (dateIdx == 0 && row.size > 1) 1 else 0
        }

        val rawTitle = row.getOrNull(resolvedTitleIdx)?.trim().orEmpty()
        if (rawTitle.isBlank() || rawTitle == "-") return@mapNotNull null

        val cleanTitle = rawTitle.removePrefix("**").removeSuffix("**").trim()
        val totalVal = if (valIdx != -1 && valIdx != resolvedTitleIdx) row.getOrNull(valIdx)?.trim() else null
        val impactVal = if (impactIdx != -1 && impactIdx != resolvedTitleIdx && impactIdx != valIdx) row.getOrNull(impactIdx)?.trim() else null
        val dateVal = if (dateIdx != -1 && dateIdx != resolvedTitleIdx) row.getOrNull(dateIdx)?.trim() else null
        val catVal = if (catIdx != -1 && catIdx != resolvedTitleIdx) row.getOrNull(catIdx)?.trim() else null
        val statusVal = if (statusIdx != -1 && statusIdx != resolvedTitleIdx) row.getOrNull(statusIdx)?.trim() else null

        val headerBuilder = StringBuilder("• **$cleanTitle**")
        if (!totalVal.isNullOrBlank() && totalVal != "-") {
            headerBuilder.append(": $totalVal")
        }

        if (!impactVal.isNullOrBlank() && impactVal != "-" && impactVal != totalVal) {
            val impactClean = if (impactVal.lowercase().contains("parte") || impactVal.lowercase().contains("cota") || impactVal.lowercase().contains("impacto")) {
                impactVal
            } else {
                "Sua parte: $impactVal"
            }
            headerBuilder.append(" ($impactClean)")
        }

        val details = mutableListOf<String>()
        if (!dateVal.isNullOrBlank() && dateVal != "-") {
            details.add("📅 $dateVal")
        }
        if (!catVal.isNullOrBlank() && catVal != "-") {
            details.add(catVal)
        }
        if (!statusVal.isNullOrBlank() && statusVal != "-") {
            details.add(statusVal)
        }

        for (colIdx in row.indices) {
            if (colIdx != resolvedTitleIdx && colIdx != valIdx && colIdx != impactIdx &&
                colIdx != dateIdx && colIdx != catIdx && colIdx != statusIdx) {
                val hName = headers.getOrNull(colIdx)?.trim()
                val cellVal = row.getOrNull(colIdx)?.trim()
                if (!hName.isNullOrBlank() && !cellVal.isNullOrBlank() && cellVal != "-") {
                    details.add("$hName: $cellVal")
                }
            }
        }

        if (details.isNotEmpty()) {
            "${headerBuilder}\n  ${details.joinToString(" • ")}"
        } else {
            headerBuilder.toString()
        }
    }

    return formattedRows.joinToString("\n\n")
}

// -------------------------------------------------------------
// FORMATAÇÃO INTELIGENTE DE MARKDOWN (NEGRITO, ITÁLICO, CÓDIGO E LISTAS)
// -------------------------------------------------------------
private fun parseSmartMarkdownText(rawText: String, isUser: Boolean): AnnotatedString {
    val boldColor = if (isUser) Color.White else Emerald400
    val italicColor = if (isUser) Color.White.copy(alpha = 0.92f) else TextPrimary
    val codeColor = if (isUser) Color.White else Cyan400
    val codeBg = DarkSurfaceVariant.copy(alpha = 0.65f)

    // Pré-processamento: converte tabelas Markdown em listas mobile amigáveis
    val textWithoutTables = convertMarkdownTables(rawText)

    // Pré-processamento de linhas: cabeçalhos e marcadores de lista
    val preprocessed = textWithoutTables.lines().joinToString("\n") { line ->
        var l = line
        val trimmed = l.trimStart()
        if (trimmed.startsWith("### ")) {
            val indent = l.substring(0, l.length - trimmed.length)
            l = "$indent**${trimmed.substring(4)}**"
        } else if (trimmed.startsWith("## ")) {
            val indent = l.substring(0, l.length - trimmed.length)
            l = "$indent**${trimmed.substring(3)}**"
        } else if (trimmed.startsWith("# ")) {
            val indent = l.substring(0, l.length - trimmed.length)
            l = "$indent**${trimmed.substring(2)}**"
        }
        val t2 = l.trimStart()
        if (t2.startsWith("* ") || t2.startsWith("- ")) {
            val indent = l.substring(0, l.length - t2.length)
            "$indent• " + t2.substring(2)
        } else l
    }

    // Expressão regular com grupos ordenados por especificidade
    val pattern = Regex(
        """(\*\*\*(.+?)\*\*\*)|(\*\*(.+?)\*\*)|(\*([^\*\n]+?)\*)|(_([^_\n]+?)_)|(`([^`\n]+?)`)"""
    )

    return buildAnnotatedString {
        var lastIndex = 0
        for (match in pattern.findAll(preprocessed)) {
            val range = match.range
            if (range.first > lastIndex) {
                append(preprocessed.substring(lastIndex, range.first))
            }
            when {
                // ***negrito e itálico***
                match.groups[2] != null -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = boldColor
                        )
                    ) {
                        append(match.groups[2]!!.value)
                    }
                }
                // **negrito**
                match.groups[4] != null -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = boldColor
                        )
                    ) {
                        append(match.groups[4]!!.value)
                    }
                }
                // *itálico*
                match.groups[6] != null -> {
                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = italicColor
                        )
                    ) {
                        append(match.groups[6]!!.value)
                    }
                }
                // _itálico_
                match.groups[8] != null -> {
                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = italicColor
                        )
                    ) {
                        append(match.groups[8]!!.value)
                    }
                }
                // `código inline`
                match.groups[10] != null -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            color = codeColor,
                            fontSize = 12.sp
                        )
                    ) {
                        append(" ${match.groups[10]!!.value} ")
                    }
                }
            }
            lastIndex = range.last + 1
        }
        if (lastIndex < preprocessed.length) {
            append(preprocessed.substring(lastIndex))
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isActionInProgress: Boolean = false,
    onConfirmAction: ((PendingAction) -> Unit)? = null,
    onCancelAction: ((PendingAction) -> Unit)? = null
) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxBubbleWidth = (screenWidthDp * if (isUser) 0.82f else 0.90f).dp
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }
    val formattedText = remember(message.text, isUser) {
        parseSmartMarkdownText(message.text, isUser)
    }

    val bubbleShape = remember(isUser) {
        RoundedCornerShape(
            topStart = if (isUser) 22.dp else 6.dp,
            topEnd = if (isUser) 6.dp else 22.dp,
            bottomStart = 22.dp,
            bottomEnd = 22.dp
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Cabeçalho Smart para Respostas da IA
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Emerald400, Cyan500))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Copiloto Saldo Livre",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Emerald400,
                    letterSpacing = 0.2.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• $timeStr",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }

        // Tag de Anexo se o usuário enviou arquivo ou áudio
        if (message.attachment != null) {
            val att = message.attachment
            val isAudio = att.type.startsWith("audio/") || att.name.endsWith(".m4a")
            Row(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, Emerald500.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.Description,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = att.name,
                    fontSize = 11.sp,
                    color = Emerald400,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bolha de Texto Principal com Gradiente, Sombra Sutil e Bordas Dinâmicas
        Box(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .shadow(
                    elevation = if (isUser) 4.dp else 8.dp,
                    shape = bubbleShape,
                    spotColor = if (isUser) Emerald500.copy(alpha = 0.3f) else Cyan500.copy(alpha = 0.22f)
                )
                .clip(bubbleShape)
                .background(
                    if (isUser) {
                        Brush.linearGradient(
                            listOf(Emerald500, Color(0xFF059669), Color(0xFF047857))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xFF17253B), Color(0xFF111B2D))
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    brush = if (isUser) {
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.28f), Emerald400.copy(alpha = 0.4f))
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Emerald500.copy(alpha = 0.35f), Cyan500.copy(alpha = 0.22f), DarkBorder)
                        )
                    },
                    shape = bubbleShape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = formattedText,
                fontSize = 13.5.sp,
                color = if (isUser) Color.White else TextPrimary,
                lineHeight = 20.sp,
                letterSpacing = 0.15.sp
            )
        }

        // Gráfico Dinâmico Renderizado
        if (message.chartPayload != null) {
            val chartShape = RoundedCornerShape(20.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .shadow(6.dp, chartShape, spotColor = Cyan500.copy(alpha = 0.2f))
                    .clip(chartShape)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF152236), DarkSurface))
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Cyan500.copy(alpha = 0.35f), DarkBorder)),
                        chartShape
                    )
                    .padding(14.dp)
            ) {
                DynamicChatChart(payload = message.chartPayload)
            }
        }

        // Cards de Ação Interativos / Confirmação de Lançamento Smart (suporta múltiplos cards igual à Web)
        val actionCards = message.allActionCards()
        for (card in actionCards) {
            val pending = card.pendingAction ?: if (card.title.contains("Confirmar", ignoreCase = true)) {
                val parts = card.description.split("—", "-")
                val descPart = parts.firstOrNull()?.trim() ?: "Lançamento"
                val amountPart = parts.getOrNull(1)?.trim() ?: ""
                val parsedAmount = Regex("""[\d.,]+""").find(amountPart)?.value
                    ?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                if (parsedAmount > 0.0) {
                    val detailParts = card.detail?.split("•")?.map { it.trim() }
                    val catSubcat = detailParts?.firstOrNull()?.split("›")?.map { it.trim() }
                    PendingAction(
                        id = "act_" + (card.title + card.description).hashCode().toString().take(8),
                        toolName = "CREATE_TRANSACTION",
                        title = card.title,
                        description = card.description,
                        detail = card.detail,
                        args = mapOf(
                            "description" to descPart,
                            "amount" to parsedAmount,
                            "category" to catSubcat?.firstOrNull(),
                            "subcategory" to catSubcat?.getOrNull(1),
                            "type" to "expense",
                            "confirmed" to true
                        ),
                        status = PendingActionStatus.PENDING
                    )
                } else null
            } else null
            val isPendingAction = pending != null && pending.status == PendingActionStatus.PENDING
            val actionShape = RoundedCornerShape(18.dp)

            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .shadow(
                        elevation = if (isPendingAction) 8.dp else 4.dp,
                        shape = actionShape,
                        spotColor = if (isPendingAction) Emerald400.copy(alpha = 0.35f)
                            else if (card.isSuccess) Emerald500.copy(alpha = 0.2f)
                            else Rose500.copy(alpha = 0.2f)
                    )
                    .clip(actionShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF162436), DarkSurface)
                        )
                    )
                    .border(
                        1.dp,
                        if (isPendingAction) {
                            Brush.linearGradient(
                                listOf(Emerald400.copy(alpha = 0.7f), Cyan500.copy(alpha = 0.45f))
                            )
                        } else if (card.isSuccess) {
                            Brush.linearGradient(listOf(Emerald400.copy(alpha = 0.45f), Cyan500.copy(alpha = 0.2f)))
                        } else {
                            Brush.linearGradient(listOf(Rose400.copy(alpha = 0.55f), DarkBorder))
                        },
                        actionShape
                    )
                    .padding(14.dp)
            ) {
                // Header com Título e Badge de Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isPendingAction) Emerald500.copy(alpha = 0.2f)
                                else if (card.isSuccess) Emerald500.copy(alpha = 0.15f)
                                else Rose500.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPendingAction) Icons.AutoMirrored.Filled.ReceiptLong
                                else if (card.isSuccess) Icons.Default.CheckCircle
                                else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (card.isSuccess || isPendingAction) Emerald400 else Rose400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = card.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (card.isSuccess || isPendingAction) Emerald400 else Rose400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isPendingAction) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Amber500.copy(alpha = 0.16f))
                                .border(1.dp, Amber500.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Aguardando",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber400
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                @Suppress("USELESS_ELVIS")
                val structuredLines = (card.lines ?: emptyList()).filter { it.value.isNotBlank() }
                if (structuredLines.isNotEmpty() && card.newFreeBalance.isNullOrBlank()) {
                    // Renderização de linhas estruturadas vindas da Web (label / value / tone)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (line in structuredLines) {
                            val valueColor = when (line.tone) {
                                "positive" -> Emerald400
                                "negative" -> Rose400
                                else -> TextPrimary
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                if (line.label.isNotBlank()) {
                                    Text(
                                        text = "${line.label}:",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    text = line.value,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = valueColor,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }
                } else {
                    // Descrição principal destacada
                    if (card.description.isNotBlank()) {
                        Text(
                            text = card.description,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    // Detalhes da transação (Categoria, Método, Divisão)
                    if (!card.detail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = card.detail,
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Impacto no Saldo Livre
                if (!card.newFreeBalance.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, Emerald500.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = card.newFreeBalance,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Emerald400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Botões Explícitos de Confirmação Interativa
                if (isPendingAction && pending != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão Cancelar
                        OutlinedButton(
                            onClick = { onCancelAction?.invoke(pending) },
                            enabled = !isActionInProgress,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, Rose500.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Rose400,
                                disabledContentColor = Rose400.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Rose400
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cancelar",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Botão Confirmar
                        Button(
                            onClick = { onConfirmAction?.invoke(pending) },
                            enabled = !isActionInProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald500,
                                disabledContainerColor = Emerald500.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            if (isActionInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = DarkBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = DarkBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Confirmar",
                                    color = DarkBackground,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else if (pending?.status == PendingActionStatus.CONFIRMED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Emerald500.copy(alpha = 0.12f))
                            .border(1.dp, Emerald500.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lançamento confirmado no Saldo Livre",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Emerald400
                        )
                    }
                } else if (pending?.status == PendingActionStatus.CANCELLED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Rose500.copy(alpha = 0.1f))
                            .border(1.dp, Rose500.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Rose400,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ação cancelada pelo usuário",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Rose400
                        )
                    }
                }
            }
        }

        // Horário da Mensagem do Usuário (para IA já consta no cabeçalho smart)
        if (isUser) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeStr,
                fontSize = 10.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// SHEET DE REVISÃO E IMPORTAÇÃO DE EXTRATO / FATURA (PDF / OFX / CSV)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementReviewSheetContent(
    fileName: String,
    items: List<ParsedStatementItem>,
    availableCards: List<Card>,
    selectedCardId: String?,
    hideIgnoredItems: Boolean,
    isImporting: Boolean,
    onSelectCard: (String?) -> Unit,
    onToggleHideIgnored: (Boolean) -> Unit,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleItem: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onSendToAiChat: () -> Unit
) {
    val displayedItems = remember(items, hideIgnoredItems) {
        if (hideIgnoredItems) items.filter { !it.isIgnored } else items
    }
    val selectedItems = remember(items) { items.filter { it.isSelected } }
    val selectedTotal = remember(selectedItems) { selectedItems.sumOf { it.amount } }
    val allDisplayedSelected = remember(displayedItems) {
        displayedItems.isNotEmpty() && displayedItems.all { it.isSelected }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp)
    ) {
        // Cabeçalho
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Emerald500.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Revisão de Extrato / Fatura",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$fileName • ${items.size} lançamentos identificados",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TextButton(onClick = onSendToAiChat) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pedir Análise IA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cyan400
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Seletor de Destino (Cartão de Crédito ou Conta Bancária)
        Text(
            text = "DESTINO DOS LANÇAMENTOS:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCardId == null,
                onClick = { onSelectCard(null) },
                label = {
                    Text(
                        text = "Conta Corrente / Pix",
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Emerald500.copy(alpha = 0.2f),
                    selectedLabelColor = Emerald400,
                    selectedLeadingIconColor = Emerald400
                )
            )

            availableCards.forEach { card ->
                val isSelected = selectedCardId == card.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCard(card.id) },
                    label = {
                        Text(
                            text = card.cardName,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Emerald500.copy(alpha = 0.2f),
                        selectedLabelColor = Emerald400,
                        selectedLeadingIconColor = Emerald400
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Controles de Seleção e Filtro de Ignorados
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = { onToggleSelectAll(!allDisplayedSelected) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (allDisplayedSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (allDisplayedSelected) "Desmarcar Todos" else "Marcar Todos",
                    fontSize = 12.sp,
                    color = Emerald400,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Ocultar RDB/Pagto Fatura",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = hideIgnoredItems,
                    onCheckedChange = onToggleHideIgnored,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = Emerald500
                    )
                )
            }
        }

        HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 6.dp))

        // Lista de Lançamentos Extraídos
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(
                items = displayedItems,
                key = { it.id },
                contentType = { "parsed_statement_item" }
            ) { item ->
                StatementItemRow(
                    item = item,
                    onToggle = { onToggleItem(item.id) }
                )
            }
        }

        HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

        // Rodapé com Total Selecionado e Botão de Confirmação
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${selectedItems.size} selecionados",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = FinanceEngine.formatCurrency(selectedTotal),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Emerald400
                )
            }

            Button(
                onClick = onConfirmImport,
                enabled = selectedItems.isNotEmpty() && !isImporting,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = DarkBackground,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "Confirmar Importação (${selectedItems.size} itens)",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun StatementItemRow(
    item: ParsedStatementItem,
    onToggle: () -> Unit
) {
    val isIncome = item.type == TransactionType.INCOME
    val amountFormatted = remember(item.amount) { FinanceEngine.formatCurrency(item.amount) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (item.isSelected) DarkSurfaceVariant else DarkBackground.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = if (item.isSelected) Emerald500.copy(alpha = 0.35f) else DarkBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Emerald500,
                uncheckedColor = TextMuted,
                checkmarkColor = DarkBackground
            ),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.counterparty.ifBlank { item.description },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (item.isSelected) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = FinanceEngine.formatFullDate(item.date),
                    fontSize = 10.sp,
                    color = TextMuted
                )

                val catText = if (!item.subcategory.isNullOrBlank()) {
                    "${item.category} › ${item.subcategory}"
                } else {
                    item.category
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Cyan500.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = catText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Cyan400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.installmentNumber != null && item.installmentsTotal != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Amber500.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${item.installmentNumber}/${item.installmentsTotal}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber400
                        )
                    }
                }

                if (item.isIgnored && !item.ignoredReason.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Rose500.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.ignoredReason,
                            fontSize = 9.sp,
                            color = Rose400
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = (if (isIncome) "+ " else "- ") + amountFormatted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                !item.isSelected -> TextMuted
                isIncome -> Emerald400
                else -> TextPrimary
            }
        )
    }
}
