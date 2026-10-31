package com.saldolivre.app.ui.screens.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    viewModel: CardsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
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
                    Text(
                        text = "Cartões & Faturas",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    FilledTonalButton(
                        onClick = { viewModel.openNewCard() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Emerald500.copy(alpha = 0.2f),
                            contentColor = Emerald400
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Novo Cartão",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Summary Banner (exibido apenas se houver mais de 1 cartão para evitar redundância de valor)
            if (uiState.cardsWithInvoices.size > 1) {
                item(key = "cards_summary_banner", contentType = "cards_summary_banner") {
                    CardsSummaryBanner(
                        selectedMonth = uiState.selectedMonth,
                        totalInvoices = uiState.totalCommittedAmount.takeIf { it > 0 } ?: uiState.totalInvoicesAmount,
                        totalLimits = uiState.totalLimitsAmount
                    )
                }
            }

            // Cards Section Title
            item(key = "cards_section_title", contentType = "cards_section_title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seus Cartões Cadastrados",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${uiState.cardsWithInvoices.size} cartões",
                        style = Typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Cards List
            if (uiState.cardsWithInvoices.isEmpty()) {
                item(key = "empty_cards_placeholder", contentType = "empty_cards_placeholder") {
                    EmptyCardsPlaceholder(onAddNew = { viewModel.openNewCard() })
                }
            } else {
                items(
                    items = uiState.cardsWithInvoices,
                    key = { it.card.id },
                    contentType = { "card_with_invoice_item" }
                ) { item ->
                    val isExpanded = uiState.selectedCardDetailsId == item.card.id

                    CardItemView(
                        item = item,
                        isExpanded = isExpanded,
                        onToggleDetails = { viewModel.toggleCardDetails(item.card.id) },
                        onEdit = { viewModel.editCard(item.card) },
                        onDelete = { viewModel.confirmDelete(item.card) }
                    )
                }
            }

            item(key = "cards_bottom_spacer", contentType = "cards_bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal: Edit/Create Card
    if (uiState.isFormOpen && uiState.editingCard != null) {
        CardFormDialog(
            card = uiState.editingCard!!,
            partner = uiState.partner,
            onDismiss = { viewModel.closeForm() },
            onSave = { updatedCard -> viewModel.saveCard(updatedCard) }
        )
    }

    // Modal: Confirm Delete
    if (uiState.cardToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Excluir Cartão",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Deseja realmente remover o cartão '${uiState.cardToDelete?.cardName}'? As faturas e despesas vinculadas a ele permanecerão gravadas no histórico.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CardsSummaryBanner(
    selectedMonth: String,
    totalInvoices: Double,
    totalLimits: Double
) {
    val formattedInvoices = String.format(Locale("pt", "BR"), "R$ %,.2f", totalInvoices)
    val formattedLimits = String.format(Locale("pt", "BR"), "R$ %,.2f", totalLimits)
    val totalAvailable = (totalLimits - totalInvoices).coerceAtLeast(0.0)
    val formattedAvailable = String.format(Locale("pt", "BR"), "R$ %,.2f", totalAvailable)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Faturas do Mês (${FinanceEngine.formatMonthLabel(selectedMonth)})",
                        style = Typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = Cyan400.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Consolidado",
                        color = Cyan400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Total Comprometido",
                        style = Typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedInvoices,
                        style = Typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Rose400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Limite Disponível",
                        style = Typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedAvailable,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val overallUsage = if (totalLimits > 0) ((totalInvoices / totalLimits) * 100).toInt().coerceIn(0, 100) else 0
            val progressColor = if (overallUsage > 80) Rose400 else Emerald400
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { (overallUsage / 100f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$overallUsage% do limite total em uso",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "Limite total: $formattedLimits",
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CardItemView(
    item: CardWithInvoice,
    isExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val card = item.card
    val gradientColors = remember(card.color) {
        when (card.color) {
            "violet" -> listOf(Color(0xFF5B21B6), Color(0xFF7C3AED))
            "orange" -> listOf(Color(0xFFC2410C), Color(0xFFEA580C))
            "slate" -> listOf(Color(0xFF1E293B), Color(0xFF334155))
            "emerald" -> listOf(Color(0xFF065F46), Color(0xFF059669))
            "cyan" -> listOf(Color(0xFF155E75), Color(0xFF0891B2))
            "rose" -> listOf(Color(0xFF9F1239), Color(0xFFE11D48))
            else -> listOf(Color(0xFF1E293B), Color(0xFF334155))
        }
    }

    val formattedInvoice = remember(item.invoiceAmount) { FinanceEngine.formatCurrency(item.invoiceAmount) }
    val formattedLimit = remember(item.limit) { FinanceEngine.formatCurrency(item.limit) }
    val formattedAvailable = remember(item.availableLimit) { FinanceEngine.formatCurrency(item.availableLimit) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Floating Visual Credit Card (sem contêiner externo / elimina box-in-box)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 176.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = gradientColors.first().copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(gradientColors))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            // Marca d'água sutil ao fundo
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 24.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Card Name, Chip, Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.95f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = card.cardName,
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Invoice Status Badge (Formato pílula translúcido sem bordas pesadas)
                    Surface(
                        color = if (item.isClosed) Amber400.copy(alpha = 0.15f) else Emerald400.copy(alpha = 0.15f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (item.isClosed) "Fatura Fechada" else "Fatura Aberta",
                            color = if (item.isClosed) Amber400 else Emerald400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Middle: Dates & Ownership
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                text = "FECHA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Dia ${card.closingDay}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "VENCE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Dia ${card.dueDay}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    if (item.isShared) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.25f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Compartilhado",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Bottom: Invoice Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "FATURA ATUAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                        Text(
                            text = formattedInvoice,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "Titular: ${card.ownerName}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                }
            }
        }

        // Lower Section: Limits, Progress and Actions
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Limit Progress
                val usageColor = if (item.usagePercentage > 85) Rose400 else if (item.usagePercentage > 50) Amber400 else Emerald400
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Limite: $formattedLimit",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "Disponível: $formattedAvailable",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.availableLimit <= 0) Rose400 else Emerald400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (item.usagePercentage / 100f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = usageColor,
                        trackColor = usageColor.copy(alpha = 0.14f),
                        strokeCap = StrokeCap.Round
                    )

                    if (item.futureInvoicesAmount > 0) {
                        Text(
                            text = "Inclui ${FinanceEngine.formatCurrency(item.futureInvoicesAmount)} em faturas futuras",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Frequent Third Parties Tags
                if (card.frequentThirdParties.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Terceiros: ${card.frequentThirdParties.joinToString(", ")}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggle Details
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(CircleShape)
                            .clickable { onToggleDetails() }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Ocultar Lançamentos" else "Ver Lançamentos (${item.transactions.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Cyan400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Edit & Delete Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (item.isOwner) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir",
                                    tint = Rose400.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Expandable Details (Transactions linked to card)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.transactions.isEmpty()) {
                            Text(
                                text = "Nenhuma transação vinculada a este cartão neste mês.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            item.transactions.forEach { tx ->
                                CardTransactionRow(tx = tx)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTransactionRow(tx: Transaction) {
    val isExpense = tx.type == TransactionType.EXPENSE
    val formattedAmount = remember(tx.effectiveAmount) { FinanceEngine.formatCurrency(tx.effectiveAmount) }
    val statusColor = when (tx.status) {
        com.saldolivre.app.data.model.TransactionStatus.PAID -> Emerald400
        com.saldolivre.app.data.model.TransactionStatus.PENDING -> Rose400
        com.saldolivre.app.data.model.TransactionStatus.ESTIMATED -> Amber400
    }

    Surface(
        color = DarkBackground.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = tx.description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FinanceEngine.formatFullDate(tx.dueDate),
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )

                    tx.installmentTag?.let { tag ->
                        Surface(
                            color = Cyan400.copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                color = Cyan400,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }

                    val thirdParty = tx.allocations.firstOrNull { it.type == "EXTERNAL" }?.name
                        ?: tx.assignedFriendName
                    if (!thirdParty.isNullOrBlank()) {
                        Surface(
                            color = Amber500.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = thirdParty,
                                fontSize = 9.sp,
                                color = Amber400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isExpense) "-" else "+"}$formattedAmount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpense) Rose400 else Emerald400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = tx.status.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCardsPlaceholder(onAddNew: () -> Unit) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "Nenhum cartão cadastrado",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Cadastre seus cartões de crédito para acompanhar faturas abertas, datas de fechamento e limites disponíveis.",
                style = Typography.bodySmall,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onAddNew,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Adicionar Cartão", fontWeight = FontWeight.Bold)
            }
        }
    }
}
