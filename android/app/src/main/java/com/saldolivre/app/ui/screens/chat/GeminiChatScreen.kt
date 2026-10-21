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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.saldolivre.app.data.model.*
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

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf(uiState.apiKey) }
    var tempModel by remember { mutableStateOf(uiState.selectedModel) }

    // Launcher para gravação de áudio (Permissão do Microfone)
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording(context)
        }
    }

    // Launcher para selecionar arquivo (PDF ou Imagem)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "documento.pdf"
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
                    viewModel.onAttachmentSelected(
                        name = fileName,
                        size = fileSize,
                        mimeType = mimeType,
                        bytes = bytes,
                        textContent = null
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

    // Modal de Configuração de API Key & Modelo
    if (showApiKeyDialog) {
        var testFeedback by remember { mutableStateOf<String?>(null) }
        var isTestSuccess by remember { mutableStateOf<Boolean?>(null) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(text = "Configurações do Copiloto IA", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Informe suas chaves da API do Google Gemini (do Google AI Studio). Você pode colar múltiplas chaves separadas por vírgula para ativar o Load Balancing automático:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = {
                            tempApiKey = it
                            testFeedback = null
                        },
                        placeholder = { Text("AIzaSy... (uma ou mais chaves)", color = TextMuted, fontSize = 13.sp) },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val firstKey = tempApiKey.split(',', ';', '\n')
                                    .map { it.trim() }
                                    .firstOrNull { it.isNotBlank() } ?: ""
                                viewModel.testConnection(firstKey) { outcome ->
                                    isTestSuccess = outcome.first
                                    testFeedback = outcome.second
                                }
                            },
                            enabled = tempApiKey.isNotBlank() && !uiState.isTestingConnection,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400)
                        ) {
                            if (uiState.isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Cyan400, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(text = "Testar Conexão", fontSize = 12.sp)
                        }

                        if (isTestSuccess != null && testFeedback != null) {
                            Text(
                                text = if (isTestSuccess == true) "✓ OK" else "✕ Falhou",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTestSuccess == true) Emerald400 else Rose400
                            )
                        }
                    }

                    if (testFeedback != null) {
                        Text(
                            text = testFeedback!!,
                            fontSize = 11.sp,
                            color = if (isTestSuccess == true) Emerald400 else Rose400,
                            lineHeight = 15.sp
                        )
                    }

                    HorizontalDivider(color = DarkBorder)

                    Text(text = "Modelo de IA Padrão:", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GeminiChatViewModel.AVAILABLE_MODELS.forEach { model ->
                            val isSelected = tempModel == model
                            FilterChip(
                                selected = isSelected,
                                onClick = { tempModel = model },
                                label = { Text(model, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald500.copy(alpha = 0.2f),
                                    selectedLabelColor = Emerald400
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onApiKeyChange(tempApiKey)
                        viewModel.onModelChange(tempModel)
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                ) {
                    Text("Salvar Configurações", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
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
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Emerald500, Cyan500)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Copiloto Saldo Livre",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.connectionStatus ?: (if (uiState.apiKey.isNotBlank()) "Online • ${uiState.selectedModel}" else "Modo Local Offline"),
                                fontSize = 10.sp,
                                color = if (uiState.apiKey.isNotBlank()) Emerald400 else Amber400
                            )
                        }
                    }
                },
                actions = {
                    // Botão 1: Histórico de Conversas (Sessões)
                    IconButton(onClick = { viewModel.setHistoryOpen(true) }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Histórico de conversas",
                            tint = TextSecondary
                        )
                    }

                    // Botão 2: Regras Aprendidas (Memória Viva)
                    IconButton(onClick = { viewModel.setRulesOpen(true) }) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Memória e Regras do Agente",
                            tint = if (uiState.learnedRules.isNotEmpty()) Amber400 else TextSecondary
                        )
                    }

                    // Botão 3: Chaves e Modelos
                    IconButton(onClick = {
                        tempApiKey = uiState.apiKey
                        tempModel = uiState.selectedModel
                        showApiKeyDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Configurações de IA",
                            tint = if (uiState.apiKey.isNotBlank()) Emerald400 else TextMuted
                        )
                    }

                    // Botão 4: Limpar conversa atual
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Limpar conversa",
                            tint = TextMuted
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            // Lista de Mensagens do Chat
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Emerald400,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (uiState.isTranscribingAudio) "Transcrevendo mensagem de voz..." else "Copiloto analisando suas finanças...",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Chips de Sugestões Rápidas Dinâmicas (Scroll Horizontal + Botão de Rotação)
            if (uiState.displayedSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.onRotateSuggestions() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Girar sugestões",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.displayedSuggestions.forEach { prompt ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.sendMessage(overrideText = prompt)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.Description,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                        Text(
                            text = if (isAudio) "Áudio gravado • ${sizeKb} KB" else "Documento PDF • ${sizeKb} KB",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
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

            // Barra de Gravação de Voz Ativa OU Barra de Input Normal
            Surface(
                color = DarkSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isRecordingAudio) {
                    // BARRA DE GRAVAÇÃO DE VOZ ATIVA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                text = "Gravando áudio... $timerStr",
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
                                    .background(Emerald500)
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
                    // BARRA DE INPUT NORMAL DOCKED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão de Anexo (PDF / Imagem)
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Anexar arquivo PDF ou extrato",
                                tint = if (uiState.pendingAttachment != null) Emerald400 else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Campo de Texto
                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = { viewModel.onInputTextChange(it) },
                            placeholder = { Text(text = "Pergunte ou lance um gasto...", color = TextMuted, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Botão de Microfone (Voz)
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.startVoiceRecording(context)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Falar por áudio",
                                tint = Emerald400,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Botão de Enviar
                        val canSend = (uiState.inputText.isNotBlank() || uiState.pendingAttachment != null) && !uiState.isTyping
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = canSend,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) Emerald500
                                    else Emerald500.copy(alpha = 0.3f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = DarkBackground,
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
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isCurrent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Atual",
                                    fontSize = 10.sp,
                                    color = Emerald400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "$dateStr • ${session.messages.size} mensagens",
                            fontSize = 11.sp,
                            color = TextMuted
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
// BOLHA DE MENSAGEM DO CHAT
// -------------------------------------------------------------
@Composable
fun ChatBubble(
    message: ChatMessage,
    onConfirmAction: ((PendingAction) -> Unit)? = null,
    onCancelAction: ((PendingAction) -> Unit)? = null
) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Tag de Anexo se o usuário enviou arquivo ou áudio
        if (message.attachment != null) {
            val att = message.attachment
            val isAudio = att.type.startsWith("audio/") || att.name.endsWith(".m4a")
            Row(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, Emerald500.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.Description,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = att.name,
                    fontSize = 11.sp,
                    color = Emerald400,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Bolha de Texto Principal
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Brush.linearGradient(listOf(Emerald600, Color(0xFF047857)))
                    else Brush.linearGradient(listOf(DarkSurfaceVariant, DarkSurface))
                )
                .border(
                    1.dp,
                    if (isUser) Emerald500.copy(alpha = 0.4f) else DarkBorder,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 13.sp,
                color = if (isUser) Color.White else TextPrimary,
                lineHeight = 18.sp
            )
        }

        // Gráfico Dinâmico Renderizado
        if (message.chartPayload != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                DynamicChatChart(payload = message.chartPayload)
            }
        }

        // Card de Ação Interativo / Informativo
        if (message.actionCard != null) {
            val card = message.actionCard
            val pending = card.pendingAction

            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, if (card.isSuccess) Emerald500.copy(alpha = 0.5f) else Rose500.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (card.isSuccess) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (card.isSuccess) Emerald400 else Rose400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = card.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (card.isSuccess) Emerald400 else Rose400
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = card.description, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                if (!card.detail.isNullOrBlank()) {
                    Text(text = card.detail, fontSize = 11.sp, color = TextSecondary)
                }

                if (!card.newFreeBalance.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = card.newFreeBalance,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400
                    )
                }

                // Botões de Confirmação Interativa se a ação for pendente
                if (pending != null && pending.status == PendingActionStatus.PENDING) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onConfirmAction?.invoke(pending) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(text = "✓ Confirmar", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { onCancelAction?.invoke(pending) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400)
                        ) {
                            Text(text = "✕ Cancelar", fontSize = 11.sp)
                        }
                    }
                } else if (pending?.status == PendingActionStatus.CONFIRMED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "✓ Lançamento confirmado no Saldo Livre", fontSize = 10.sp, color = Emerald400)
                } else if (pending?.status == PendingActionStatus.CANCELLED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "✕ Ação cancelada", fontSize = 10.sp, color = Rose400)
                }
            }
        }

        // Horário da Mensagem
        Spacer(modifier = Modifier.height(3.dp))
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
        Text(
            text = timeStr,
            fontSize = 9.sp,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
