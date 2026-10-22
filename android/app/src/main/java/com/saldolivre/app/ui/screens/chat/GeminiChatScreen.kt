package com.saldolivre.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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

    var showClearConfirmDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
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
                onDeleteSession = { viewModel.onDeleteSession(it) }
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
                onDeleteRule = { viewModel.onDeleteRule(it) }
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

    // Modal para Colar Texto de Extrato / Fatura Diretamente no Chat
    if (uiState.showPasteStatementDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowPasteStatementDialog(false) },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Colar Fatura ou Extrato",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cole abaixo as linhas da sua fatura (Nubank, Itaú, Inter, C6, CSV ou OFX). O Copiloto irá identificar e categorizar todos os lançamentos para sua revisão.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = uiState.pasteStatementText,
                        onValueChange = { viewModel.onPasteStatementTextChange(it) },
                        placeholder = {
                            Text(
                                text = "Ex:\n10 MAR Ifood *Restaurante R$ 48,90\n12 MAR Uber *Trip R$ 22,50\n15 MAR Supermercado Extra 1/2 R$ 180,00",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onParseRawStatementText() },
                    enabled = uiState.pasteStatementText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                ) {
                    Text(
                        text = "Analisar Lançamentos",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowPasteStatementDialog(false) }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Modal de Confirmação para Limpar Chat Atual
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = DarkSurface,
            title = { Text(text = "Limpar Conversa?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Deseja reiniciar a conversa atual? Seus dados financeiros e lançamentos gravados não serão afetados.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Limpar", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
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
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Emerald400)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Emerald400, Cyan500)
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.25f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = DarkBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Copiloto Smart",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        letterSpacing = 0.2.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Emerald500.copy(alpha = 0.16f))
                                            .border(1.dp, Emerald500.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "IA",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Emerald400
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.apiKey.isNotBlank()) Emerald400 else Amber400)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = uiState.connectionStatus ?: (if (uiState.apiKey.isNotBlank()) "Ativo • ${uiState.selectedModel}" else "Motor Neural Local"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (uiState.apiKey.isNotBlank()) Emerald400 else Amber400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // Botão 1: Histórico de Conversas (Sessões)
                        IconButton(
                            onClick = { viewModel.setHistoryOpen(true) },
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Histórico de conversas",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Botão 2: Regras Aprendidas (Memória Viva)
                        IconButton(
                            onClick = { viewModel.setRulesOpen(true) },
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (uiState.learnedRules.isNotEmpty()) Amber500.copy(alpha = 0.14f)
                                    else DarkSurfaceVariant.copy(alpha = 0.6f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "Memória e Regras do Agente",
                                tint = if (uiState.learnedRules.isNotEmpty()) Amber400 else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Botão 3: Limpar conversa atual
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier
                                .padding(start = 2.dp, end = 6.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Limpar conversa",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
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
                items(uiState.messages) { msg ->
                    ChatBubble(
                        message = msg,
                        onConfirmAction = { viewModel.confirmPendingAction(it) },
                        onCancelAction = { viewModel.cancelPendingAction(it) }
                    )
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
                                    text = if (uiState.isTranscribingAudio) "Transcrevendo mensagem de voz..." else "Copiloto processando análise...",
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

            // Chips de Sugestões Rápidas Dinâmicas (Smart Prompts)
            if (uiState.displayedSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.onRotateSuggestions() },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant.copy(alpha = 0.8f))
                            .border(1.dp, DarkBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Girar sugestões",
                            tint = Emerald400,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.displayedSuggestions.forEach { prompt ->
                            val chipShape = RoundedCornerShape(20.dp)
                            Box(
                                modifier = Modifier
                                    .clip(chipShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF162235), DarkSurfaceVariant)
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Brush.horizontalGradient(
                                            listOf(Emerald500.copy(alpha = 0.3f), Cyan500.copy(alpha = 0.2f))
                                        ),
                                        chipShape
                                    )
                                    .clickable {
                                        viewModel.sendMessage(overrideText = prompt)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.1.sp
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

            // Dock Flutuante Smart (Gravação de Voz Ativa OU Barra de Comando Inteligente)
            val dockShape = RoundedCornerShape(28.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(10.dp, dockShape, spotColor = Emerald500.copy(alpha = 0.25f))
                    .clip(dockShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A253A), DarkSurface)
                        )
                    )
                    .border(
                        1.dp,
                        if (uiState.inputText.isNotBlank() || uiState.isRecordingAudio) {
                            Brush.horizontalGradient(listOf(Emerald500.copy(alpha = 0.6f), Cyan500.copy(alpha = 0.5f)))
                        } else {
                            Brush.horizontalGradient(listOf(DarkBorder, DarkBorder))
                        },
                        dockShape
                    )
            ) {
                if (uiState.isRecordingAudio) {
                    // BARRA DE GRAVAÇÃO DE VOZ ATIVA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
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
                                color = Rose400
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Cancelar
                            IconButton(
                                onClick = { viewModel.cancelVoiceRecording() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancelar gravação",
                                    tint = Rose400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Concluir e Enviar
                            IconButton(
                                onClick = { viewModel.stopVoiceRecording(sendImmediately = true) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(Emerald400, Cyan500))
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Concluir áudio",
                                    tint = DarkBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    // BARRA DE COMANDO SMART
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão de Anexo (PDF / OFX / CSV / TXT / Imagem)
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Anexar extrato PDF, OFX, CSV ou TXT",
                                tint = if (uiState.pendingAttachment != null) Emerald400 else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Botão para Colar Texto de Extrato / Fatura
                        IconButton(
                            onClick = { viewModel.setShowPasteStatementDialog(true) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Colar texto de fatura ou extrato",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Campo de Texto Integrado e Limpo
                        TextField(
                            value = uiState.inputText,
                            onValueChange = { viewModel.onInputTextChange(it) },
                            placeholder = {
                                Text(
                                    text = "Pergunte à IA ou digite um gasto...",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = Emerald400
                            )
                        )

                        // Botão de Microfone (Voz)
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.startVoiceRecording(context)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Falar por áudio",
                                tint = Emerald400,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Botão de Enviar Smart
                        val canSend = (uiState.inputText.isNotBlank() || uiState.pendingAttachment != null) && !uiState.isTyping
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = canSend,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) Brush.linearGradient(listOf(Emerald400, Cyan500))
                                    else Brush.linearGradient(listOf(DarkSurfaceVariant, DarkSurfaceVariant))
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if (canSend) DarkBackground else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
    onDeleteSession: (String) -> Unit
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
            Column {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            text = "$dateStr • ${session.messages.size} mensagens",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
    onDeleteRule: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
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
                shape = RoundedCornerShape(12.dp)
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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = rule.rule, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
                        }
                        IconButton(
                            onClick = { onDeleteRule(rule.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remover regra", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// BOLHA DE MENSAGEM SMART DO CHAT
// -------------------------------------------------------------
private fun parseSmartMarkdownText(text: String, isUser: Boolean): AnnotatedString {
    val boldColor = if (isUser) Color.White else Emerald400
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            val startBold = text.indexOf("**", idx)
            if (startBold == -1) {
                append(text.substring(idx))
                break
            }
            val endBold = text.indexOf("**", startBold + 2)
            if (endBold == -1) {
                append(text.substring(idx))
                break
            }
            if (startBold > idx) {
                append(text.substring(idx, startBold))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                append(text.substring(startBold + 2, endBold))
            }
            idx = endBold + 2
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
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
                    text = "Copiloto Smart",
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

        // Card de Ação Interativo / Informativo Smart
        if (message.actionCard != null) {
            val card = message.actionCard
            val pending = card.pendingAction
            val actionShape = RoundedCornerShape(18.dp)

            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .shadow(6.dp, actionShape, spotColor = if (card.isSuccess) Emerald500.copy(alpha = 0.25f) else Rose500.copy(alpha = 0.25f))
                    .clip(actionShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF162436), DarkSurface)
                        )
                    )
                    .border(
                        1.dp,
                        if (card.isSuccess) {
                            Brush.linearGradient(listOf(Emerald400.copy(alpha = 0.55f), Cyan500.copy(alpha = 0.3f)))
                        } else {
                            Brush.linearGradient(listOf(Rose400.copy(alpha = 0.55f), DarkBorder))
                        },
                        actionShape
                    )
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (card.isSuccess) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (card.isSuccess) Emerald400 else Rose400,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = card.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (card.isSuccess) Emerald400 else Rose400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(text = card.description, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                if (!card.detail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = card.detail, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
                }

                if (!card.newFreeBalance.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.newFreeBalance,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Botões de Confirmação Interativa se a ação for pendente
                if (pending != null && pending.status == PendingActionStatus.PENDING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onConfirmAction?.invoke(pending) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(
                                text = "✓ Confirmar",
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { onCancelAction?.invoke(pending) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400)
                        ) {
                            Text(
                                text = "✕ Cancelar",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (pending?.status == PendingActionStatus.CONFIRMED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "✓ Lançamento confirmado no Saldo Livre", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Emerald400)
                } else if (pending?.status == PendingActionStatus.CANCELLED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "✕ Ação cancelada", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Rose400)
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
