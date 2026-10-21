package com.saldolivre.app.ui.screens.import_statement

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.parser.ParsedStatementItem
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementScreen(
    viewModel: ImportStatementViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFileSelected(context, uri)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (!error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
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
                                .background(
                                    Brush.linearGradient(listOf(Cyan500.copy(alpha = 0.3f), Purple400.copy(alpha = 0.2f))),
                                    CircleShape
                                )
                                .border(1.dp, Cyan400, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Importar Extrato",
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Cyan500.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "OFX • CSV • PDF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Cyan400,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = "Leitura inteligente de lançamentos bancários",
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setShowPasteDialog(true) }) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Colar Texto",
                            tint = Cyan400
                        )
                    }
                }
            )
        },
        bottomBar = {
            val selectedCount = uiState.items.count { it.isSelected }
            val selectedTotal = uiState.items.filter { it.isSelected }.sumOf { it.amount }

            if (uiState.items.isNotEmpty()) {
                Surface(
                    color = DarkSurface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "$selectedCount selecionados",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = FinanceEngine.formatCurrency(selectedTotal),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.confirmImport { _ ->
                                    onNavigateBack()
                                }
                            },
                            enabled = selectedCount > 0 && !uiState.isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cyan500,
                                disabledContainerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            if (uiState.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Confirmar Importação",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Upload & Destination Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Upload button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, Cyan500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable {
                                filePickerLauncher.launch("*/*")
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Cyan500.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (uiState.items.isEmpty()) "Toque para escolher o arquivo" else "Trocar arquivo selecionado",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Aceita extratos em PDF, OFX, CSV ou TXT",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // Seletor de Cartão de Crédito de Destino
                    if (uiState.availableCards.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Vincular Compras no Cartão a:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.availableCards) { card ->
                                val isSelected = uiState.selectedCardId == card.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Cyan500.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Cyan400 else DarkBorder
                                    ),
                                    modifier = Modifier.clickable {
                                        viewModel.setSelectedCard(card.id)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CreditCard,
                                            contentDescription = null,
                                            tint = if (isSelected) Cyan400 else TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = card.cardName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Cyan400 else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Barra de filtros e estatísticas de itens
            if (uiState.items.isNotEmpty()) {
                val visibleItems = if (uiState.hideIgnored) {
                    uiState.items.filter { !it.isIgnored }
                } else {
                    uiState.items
                }
                val ignoredCount = uiState.items.count { it.isIgnored }
                val allSelected = visibleItems.isNotEmpty() && visibleItems.all { it.isSelected }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { viewModel.toggleSelectAll(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Cyan500,
                                uncheckedColor = TextMuted
                            )
                        )
                        Text(
                            text = if (allSelected) "Desmarcar Todos" else "Marcar Todos",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { viewModel.toggleSelectAll(!allSelected) }
                        )
                    }

                    if (ignoredCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                viewModel.setHideIgnored(!uiState.hideIgnored)
                            }
                        ) {
                            Text(
                                text = if (uiState.hideIgnored) "Ver $ignoredCount ocultos" else "Ocultar ignorados",
                                fontSize = 11.sp,
                                color = Amber400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (uiState.hideIgnored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Lista de Itens extraídos
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleItems, key = { it.id }) { item ->
                        ParsedItemCard(
                            item = item,
                            onToggleSelection = { viewModel.toggleItemSelection(item.id) }
                        )
                    }
                }
            } else if (!uiState.isProcessing) {
                // Estado Vazio
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhum arquivo importado ainda",
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Faça o upload do seu extrato para identificar automaticamente valores, datas, parcelas e categorias.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Modal para Colar Texto Manualmente
    if (uiState.showPasteDialog) {
        var pastedText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.setShowPasteDialog(false) },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Colar Texto do Extrato",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Copie e cole o extrato ou texto da fatura do seu banco aqui:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 200.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Cyan400,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        ),
                        placeholder = {
                            Text(
                                text = "Exemplo:\n04/10 Supermercado Pão de Açúcar R$ 152,30\n05/10 Posto Ipiranga 2/3 - R$ 100,00",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onParseRawText(pastedText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                    enabled = pastedText.isNotBlank()
                ) {
                    Text("Processar", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowPasteDialog(false) }) {
                    Text("Cancelar", color = TextMuted, maxLines = 1)
                }
            }
        )
    }
}

@Composable
fun ParsedItemCard(
    item: ParsedStatementItem,
    onToggleSelection: () -> Unit
) {
    val categoryDetail = CategoryRegistry.CATEGORIES.find { it.name == item.category }
    val categoryColor = categoryDetail?.color ?: TextSecondary
    val isExpense = item.type == TransactionType.EXPENSE

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (item.isSelected) DarkSurface else DarkSurface.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (item.isSelected) Cyan500.copy(alpha = 0.4f) else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelection() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Cyan500,
                    uncheckedColor = TextMuted
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Data e status ignorado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.date,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        maxLines = 1
                    )

                    if (item.isIgnored) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Amber500.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = item.ignoredReason ?: "Ignorado",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Descrição
                Text(
                    text = item.description,
                    style = Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isSelected) TextPrimary else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Tags de Categoria, Parcela e Método
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tag Categoria
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .background(categoryColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.category + if (!item.subcategory.isNullOrBlank()) " • ${item.subcategory}" else "",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = categoryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Tag Parcela
                    if (item.installmentNumber != null && item.installmentsTotal != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Purple400.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${item.installmentNumber}/${item.installmentsTotal}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Purple400,
                                maxLines = 1
                            )
                        }
                    }

                    // Método de Pagamento
                    Text(
                        text = item.paymentMethod.label,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Valor
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isExpense) "-" else "+") + FinanceEngine.formatCurrency(item.amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!item.isSelected) TextMuted else if (isExpense) Rose400 else Emerald400,
                    maxLines = 1
                )
            }
        }
    }
}
