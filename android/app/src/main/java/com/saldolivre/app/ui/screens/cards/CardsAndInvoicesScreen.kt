package com.saldolivre.app.ui.screens.cards

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.CardInvoice
import com.saldolivre.app.data.engine.InvoiceCategorySlice
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.engine.InvoiceStatus
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.ui.components.InvoiceDateGroup
import com.saldolivre.app.ui.components.InvoiceDateGroupHeader
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.screens.invoices.InvoiceItemFilter
import com.saldolivre.app.ui.theme.*
import java.util.Locale

private fun cardColor(name: String?): Color = when (name) {
    "orange" -> CardInterOrange
    "slate" -> CardXpSlate
    "emerald" -> Emerald500
    "cyan" -> Cyan500
    "rose" -> Rose500
    else -> CardNubankViolet
}

private fun cardDarkGradient(name: String?): List<Color> = when (name) {
    "orange" -> listOf(Color(0xFF7C2D12), Color(0xFF451A03), DarkSurface)
    "slate" -> listOf(Color(0xFF334155), Color(0xFF1E293B), DarkSurface)
    "emerald" -> listOf(Color(0xFF064E3B), Color(0xFF022C22), DarkSurface)
    "cyan" -> listOf(Color(0xFF164E63), Color(0xFF083344), DarkSurface)
    "rose" -> listOf(Color(0xFF881337), Color(0xFF4C0519), DarkSurface)
    else -> listOf(Color(0xFF581C87), Color(0xFF3B0764), DarkSurface)
}

private fun cardGradientColors(colorName: String?): List<Color> = when (colorName) {
    "violet" -> listOf(Color(0xFF5B21B6), Color(0xFF7C3AED))
    "orange" -> listOf(Color(0xFFC2410C), Color(0xFFEA580C))
    "slate" -> listOf(Color(0xFF1E293B), Color(0xFF334155))
    "emerald" -> listOf(Color(0xFF065F46), Color(0xFF059669))
    "cyan" -> listOf(Color(0xFF155E75), Color(0xFF0891B2))
    "rose" -> listOf(Color(0xFF9F1239), Color(0xFFE11D48))
    else -> listOf(Color(0xFF1E293B), Color(0xFF334155))
}

private fun statusColor(status: InvoiceStatus): Color = when (status) {
    InvoiceStatus.EMPTY -> TextMuted
    InvoiceStatus.OPEN -> Cyan400
    InvoiceStatus.CLOSED -> Amber400
    InvoiceStatus.OVERDUE -> Rose400
    InvoiceStatus.PAID -> Emerald400
}

private fun itemStatusColor(status: TransactionStatus): Color = when (status) {
    TransactionStatus.ESTIMATED -> Amber400
    TransactionStatus.PENDING -> CardInterOrange
    TransactionStatus.PAID -> Emerald400
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsAndInvoicesScreen(
    viewModel: CardsAndInvoicesViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onEditTransaction: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedInvoice = uiState.selectedInvoice

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val visibleInvoiceItems = remember(
        selectedInvoice,
        uiState.itemFilter,
        uiState.searchQuery,
        uiState.selectedParticipantName,
        uiState.currentUser.uid
    ) {
        var items = selectedInvoice?.items.orEmpty()
        val participantName = uiState.selectedParticipantName
        if (participantName != null) {
            val part = selectedInvoice?.splitBreakdown?.find { it.name == participantName }
            if (part != null) {
                items = items.filter { tx ->
                    InvoiceEngine.participantAmountIn(tx, part, uiState.currentUser.uid) > 0.0
                }
            }
        }
        val filtered = when (uiState.itemFilter) {
            InvoiceItemFilter.ALL -> items
            InvoiceItemFilter.THIRD_PARTY -> items.filter { tx ->
                tx.splitType == SplitType.EXTERNAL ||
                !tx.assignedFriendUid.isNullOrBlank() ||
                !tx.assignedFriendName.isNullOrBlank() ||
                tx.allocations.any { it.type == "EXTERNAL" } ||
                tx.category.contains("Terceiro", ignoreCase = true)
            }
            InvoiceItemFilter.OPEN -> items.filter { it.status != TransactionStatus.PAID }
            InvoiceItemFilter.PAID -> items.filter { it.status == TransactionStatus.PAID }
            InvoiceItemFilter.INSTALLMENTS -> items.filter { (it.installmentsTotal ?: 1) > 1 }
        }
        if (uiState.searchQuery.isBlank()) filtered
        else filtered.filter {
            it.description.contains(uiState.searchQuery, ignoreCase = true) ||
                it.category.contains(uiState.searchQuery, ignoreCase = true) ||
                it.subcategory?.contains(uiState.searchQuery, ignoreCase = true) == true
        }
    }

    val activeCategorySlices = remember(
        selectedInvoice,
        uiState.selectedParticipantName,
        uiState.currentUser.uid
    ) {
        val participantName = uiState.selectedParticipantName
        val part = if (participantName != null) selectedInvoice?.splitBreakdown?.find { it.name == participantName } else null
        if (part != null && selectedInvoice != null) {
            val expenses = selectedInvoice.items.filter { it.type == TransactionType.EXPENSE }
            InvoiceEngine.calculateCategoryBreakdownForParticipant(expenses, part, uiState.currentUser.uid)
        } else {
            selectedInvoice?.byCategory.orEmpty()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Cartões & Faturas",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (uiState.selectedTabIndex == 0)
                                "Faturas, fechamento e controle de gastos"
                            else
                                "Gerenciamento de cartões e limites",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                            maxLines = 1
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
        ) {
            // Segmented Tab Selector
            TabRow(
                selectedTabIndex = uiState.selectedTabIndex,
                containerColor = DarkBackground,
                contentColor = Emerald400,
                indicator = { tabPositions ->
                    if (uiState.selectedTabIndex < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[uiState.selectedTabIndex])
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(Emerald400)
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.7f))
                }
            ) {
                Tab(
                    selected = uiState.selectedTabIndex == 0,
                    onClick = { viewModel.setTabIndex(0) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Faturas do Mês",
                                fontWeight = if (uiState.selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    },
                    selectedContentColor = Emerald400,
                    unselectedContentColor = TextSecondary
                )
                Tab(
                    selected = uiState.selectedTabIndex == 1,
                    onClick = { viewModel.setTabIndex(1) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Meus Cartões (${uiState.cardsWithInvoices.size})",
                                fontWeight = if (uiState.selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    },
                    selectedContentColor = Emerald400,
                    unselectedContentColor = TextSecondary
                )
            }

            // Tab Content
            when (uiState.selectedTabIndex) {
                0 -> {
                    InvoicesTabContent(
                        uiState = uiState,
                        selectedInvoice = selectedInvoice,
                        visibleItems = visibleInvoiceItems,
                        categorySlices = activeCategorySlices,
                        viewModel = viewModel,
                        onEditTransaction = onEditTransaction
                    )
                }
                1 -> {
                    CardsManagementTabContent(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
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
                    text = "Deseja realmente remover o cartão '${uiState.cardToDelete?.cardName}'? As faturas e despesas vinculadas permanecerão salvas no histórico.",
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

// =========================================================================
// ABA 1: FATURAS DETALHADAS
// =========================================================================
@Composable
private fun InvoicesTabContent(
    uiState: CardsAndInvoicesUiState,
    selectedInvoice: CardInvoice?,
    visibleItems: List<Transaction>,
    categorySlices: List<InvoiceCategorySlice>,
    viewModel: CardsAndInvoicesViewModel,
    onEditTransaction: (String) -> Unit
) {
    val participant = if (uiState.selectedParticipantName != null) {
        selectedInvoice?.splitBreakdown?.find { it.name == uiState.selectedParticipantName }
    } else null

    val groupedByDate = remember(visibleItems, uiState.selectedParticipantName, uiState.currentUser.uid) {
        val map = LinkedHashMap<String, MutableList<Transaction>>()
        for (tx in visibleItems) {
            val key = tx.dueDate.ifBlank { "Sem data" }
            map.getOrPut(key) { mutableListOf() }.add(tx)
        }
        map.map { (dateKey, items) ->
            val dayTotal = items.sumOf { tx ->
                val amt = if (participant != null) {
                    InvoiceEngine.participantAmountIn(tx, participant, uiState.currentUser.uid)
                } else tx.effectiveAmount
                if (tx.type == TransactionType.INCOME) -amt else amt
            }
            InvoiceDateGroup(dateKey, items, dayTotal)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Navegador de Mês
        item(key = "inv_month_nav") {
            MonthNavigator(
                monthKey = uiState.viewMonth,
                onPrev = viewModel::previousMonth,
                onNext = viewModel::nextMonth
            )
        }

        if (!uiState.isLoading && uiState.invoices.isEmpty()) {
            item(key = "inv_empty_state") {
                EmptyCardsPlaceholder(onAddNew = { viewModel.openNewCard() })
            }
            return@LazyColumn
        }

        // 2. Carrossel de Cartões (exibido se houver múltiplos cartões para alternar faturas)
        if (uiState.invoices.size > 1) {
            item(key = "inv_card_selector") {
                val listState = rememberLazyListState()
                LaunchedEffect(uiState.selectedCardId) {
                    val targetIndex = uiState.invoices.indexOfFirst { it.card.id == uiState.selectedCardId }
                    if (targetIndex >= 0) {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.invoices, key = { it.card.id }) { inv ->
                        CardChip(
                            invoice = inv,
                            selected = inv.card.id == uiState.selectedCardId,
                            onClick = { viewModel.selectCard(inv.card.id) }
                        )
                    }

                    // Chip de adicionar cartão rápido
                    item(key = "add_new_card_chip") {
                        Surface(
                            modifier = Modifier
                                .width(130.dp)
                                .height(98.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { viewModel.openNewCard() },
                            color = DarkSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Novo Cartão",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Emerald400
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Detalhamento do Cartão Selecionado
        if (selectedInvoice != null) {
            // 4.1 Card Virtual Moderno (com resumo e botão de pagar fatura)
            item(key = "inv_summary_${selectedInvoice.card.id}") {
                InvoiceSummaryCard(
                    invoice = selectedInvoice,
                    showConfirm = uiState.showPayConfirm,
                    isPaying = uiState.isPaying,
                    onPay = viewModel::requestPay,
                    onConfirm = viewModel::confirmPay,
                    onDismiss = viewModel::dismissPay,
                    onEditCard = { viewModel.editCard(selectedInvoice.card) }
                )
            }

            // 4.2 Inteligência da Fatura (Melhor época, ciclo, futuro comprometido)
            item(key = "inv_insights_${selectedInvoice.card.id}") {
                SmartInsightsCard(invoice = selectedInvoice)
            }

            // 4.3 Divisão da Fatura (Rateio com parceiro e terceiros)
            if (selectedInvoice.splitBreakdown.isNotEmpty()) {
                item(key = "inv_split_${selectedInvoice.card.id}") {
                    SplitBreakdownCard(
                        invoice = selectedInvoice,
                        selectedParticipantName = uiState.selectedParticipantName,
                        onToggleParticipant = viewModel::toggleParticipantFilter
                    )
                }
            }

            // 4.4 Histórico e Evolução das Faturas
            item(key = "inv_history_${selectedInvoice.card.id}") {
                HistoryChart(
                    history = uiState.history,
                    currentMonth = uiState.viewMonth,
                    barColor = cardColor(selectedInvoice.card.color),
                    onSelect = viewModel::goToMonth
                )
            }

            // 4.5 Distribuição por Categorias
            if (categorySlices.isNotEmpty()) {
                item(key = "inv_categories_${selectedInvoice.card.id}") {
                    CategoryBreakdown(
                        categories = categorySlices,
                        selectedParticipantName = uiState.selectedParticipantName,
                        onClearParticipant = viewModel::clearParticipantFilter
                    )
                }
            }

            // 4.6 Cabeçalho de Lançamentos + Busca + Filtros
            item(key = "inv_items_header") {
                ItemsHeader(
                    count = visibleItems.size,
                    filter = uiState.itemFilter,
                    searchQuery = uiState.searchQuery,
                    onFilter = viewModel::setFilter,
                    onSearchChange = viewModel::setSearchQuery
                )
            }

            // 4.7 Lista de Lançamentos
            if (visibleItems.isEmpty()) {
                item(key = "inv_items_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Nenhum lançamento para este filtro.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        if (uiState.searchQuery.isNotBlank()) {
                            TextButton(onClick = { viewModel.setSearchQuery("") }) {
                                Text("Limpar busca", fontSize = 11.sp, color = Cyan400)
                            }
                        }
                    }
                }
            } else {
                groupedByDate.forEach { group ->
                    item(key = "inv_date_header_${group.dateKey}") {
                        InvoiceDateGroupHeader(
                            dateKey = group.dateKey,
                            totalAmount = group.totalAmount
                        )
                    }
                    items(group.items, key = { "inv_tx_${it.id}" }) { tx ->
                        val share = if (participant != null) {
                            InvoiceEngine.participantAmountIn(tx, participant, uiState.currentUser.uid)
                        } else null
                        InvoiceItemRow(
                            tx = tx,
                            participantShare = share,
                            onClick = { onEditTransaction(tx.id) }
                        )
                    }
                }
            }
        }

        item(key = "inv_bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

// =========================================================================
// ABA 2: GERENCIAR MEUS CARTÕES
// =========================================================================
@Composable
private fun CardsManagementTabContent(
    uiState: CardsAndInvoicesUiState,
    viewModel: CardsAndInvoicesViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner Resumo de Limites e Uso (exibido apenas se houver mais de 1 cartão para evitar redundância de valor)
        if (uiState.cardsWithInvoices.size > 1) {
            item(key = "cards_summary_banner") {
                CardsSummaryBanner(
                    selectedMonth = uiState.viewMonth,
                    totalInvoices = uiState.totalCommittedAmount.takeIf { it > 0 } ?: uiState.totalInvoicesAmount,
                    totalLimits = uiState.totalLimitsAmount
                )
            }
        }

        // Título da Seção
        item(key = "cards_section_title") {
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

        // Lista de Cartões
        if (uiState.cardsWithInvoices.isEmpty()) {
            item(key = "empty_cards_placeholder") {
                EmptyCardsPlaceholder(onAddNew = { viewModel.openNewCard() })
            }
        } else {
            items(
                items = uiState.cardsWithInvoices,
                key = { it.card.id }
            ) { item ->
                val isExpanded = uiState.selectedCardDetailsId == item.card.id

                CardItemView(
                    item = item,
                    isExpanded = isExpanded,
                    onToggleDetails = { viewModel.toggleCardDetails(item.card.id) },
                    onGoToInvoice = { viewModel.selectCardAndGoToInvoices(item.card.id) },
                    onEdit = { viewModel.editCard(item.card) },
                    onDelete = { viewModel.confirmDelete(item.card) }
                )
            }
        }

        item(key = "cards_bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =========================================================================
// COMPONENTES AUXILIARES
// =========================================================================

@Composable
private fun MonthNavigator(monthKey: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Fatura anterior", tint = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FATURAS COM VENCIMENTO EM", fontSize = 9.sp, letterSpacing = 0.8.sp, color = TextMuted)
            Text(
                FinanceEngine.formatMonthLabel(monthKey),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Próxima fatura", tint = TextSecondary)
        }
    }
}

@Composable
private fun StatusPill(status: InvoiceStatus) {
    val c = statusColor(status)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(c.copy(alpha = 0.14f))
            .border(1.dp, c.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Text(status.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c)
    }
}

@Composable
private fun CardChip(invoice: CardInvoice, selected: Boolean, onClick: () -> Unit) {
    val base = cardColor(invoice.card.color)
    val formattedName = remember(invoice.card.cardName) {
        invoice.card.cardName.trim().let { name ->
            if (name.isNotEmpty()) name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } else "Cartão"
        }
    }
    Column(
        modifier = Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(base.copy(alpha = if (selected) 0.65f else 0.35f), DarkBackground)))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) base else DarkBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formattedName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusPill(invoice.status)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            FinanceEngine.formatCurrency(invoice.total),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        val subtitle = if (invoice.itemsCount == 0) {
            "Sem compras no ciclo"
        } else {
            "Vence ${FinanceEngine.formatShortDate(invoice.dueDate)} · ${invoice.itemsCount} ${if (invoice.itemsCount == 1) "item" else "itens"}"
        }
        Text(
            subtitle,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun InvoiceSummaryCard(
    invoice: CardInvoice,
    showConfirm: Boolean,
    isPaying: Boolean,
    onPay: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onEditCard: () -> Unit
) {
    val gradientColors = cardDarkGradient(invoice.card.color)
    val paidProgress by animateFloatAsState(invoice.paidRatio, label = "paidProgress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = cardColor(invoice.card.color).copy(alpha = 0.45f),
                ambientColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top row: Titular + Chip metálico simulado + Status + Botão Editar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        invoice.card.cardName.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            invoice.card.ownerName.ifBlank { "Titular" },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    FinanceEngine.formatCurrency(invoice.total),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    InvoiceEngine.countdownLabel(invoice),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (invoice.status == InvoiceStatus.OVERDUE) Rose400 else Color.White.copy(alpha = 0.9f)
                )
            }

            // Ações & Chip
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEditCard,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Cartão",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFD97706))))
                            .border(1.dp, Color(0xFFB45309), RoundedCornerShape(6.dp))
                            .padding(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF92400E).copy(alpha = 0.7f)))
                            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF92400E).copy(alpha = 0.7f)))
                        }
                    }
                }
                StatusPill(invoice.status)
            }
        }

        // Fechamento e Vencimento
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoBox("Fechamento", FinanceEngine.formatShortDate(invoice.closingDate), Modifier.weight(1f))
            InfoBox("Vencimento", FinanceEngine.formatShortDate(invoice.dueDate), Modifier.weight(1f))
        }

        // Progresso de pagamento (exibido quando há pagamentos parciais para evitar repetição redundante)
        if (invoice.paidAmount > 0.0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pago: ${FinanceEngine.formatCurrency(invoice.paidAmount)}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                    Text("Restante em aberto: ${FinanceEngine.formatCurrency(invoice.openAmount)}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                }
                LinearProgressIndicator(
                    progress = { paidProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = Emerald400,
                    trackColor = Emerald400.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        // Limite do Cartão
        invoice.limit?.let { limit ->
            val usage = invoice.limitUsagePct ?: 0
            val usageColor = when {
                usage >= 90 -> Rose400
                usage >= 70 -> Amber400
                else -> Cyan400
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Uso do limite ($usage%)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    val avail = invoice.availableLimit ?: (limit - invoice.total).coerceAtLeast(0.0)
                    Text(
                        "Disponível: ${FinanceEngine.formatCurrency(avail)}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                LinearProgressIndicator(
                    progress = { usage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = usageColor,
                    trackColor = usageColor.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        if (invoice.credits > 0) {
            Text(
                "Inclui ${FinanceEngine.formatCurrency(invoice.credits)} em créditos/estornos.",
                fontSize = 11.sp,
                color = Cyan400
            )
        }

        if (invoice.openAmount > 0) {
            if (showConfirm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Emerald500.copy(alpha = 0.15f))
                        .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Marcar ${invoice.unpaidExpenses.size} lançamento(s) como pagos, totalizando " +
                            "${FinanceEngine.formatCurrency(invoice.openAmount)}?",
                        fontSize = 12.sp,
                        color = Emerald400
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = onDismiss, enabled = !isPaying, shape = RoundedCornerShape(12.dp)) {
                            Text("Cancelar", fontSize = 12.sp, color = TextSecondary)
                        }
                        Button(
                            onClick = onConfirm,
                            enabled = !isPaying,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Color.Black)
                        ) {
                            if (isPaying) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (isPaying) "Pagando..." else "Confirmar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onPay,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Pagar fatura (${FinanceEngine.formatCurrency(invoice.openAmount)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartInsightsCard(invoice: CardInvoice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Amber400, modifier = Modifier.size(18.dp))
                Text("Inteligência da Fatura", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            if (invoice.isBestPurchaseTime) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Amber400.copy(alpha = 0.15f))
                        .border(1.dp, Amber400.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Melhor Época Ativa!", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Amber400)
                }
            }
        }

        Text(invoice.bestPurchaseHint, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)

        // Barra de progresso do ciclo
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Progresso do ciclo da fatura", fontSize = 10.sp, color = TextMuted)
                Text("${invoice.cycleProgressPct}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            LinearProgressIndicator(
                progress = { invoice.cycleProgressPct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Cyan400,
                trackColor = Cyan400.copy(alpha = 0.14f),
                strokeCap = StrokeCap.Round
            )
        }

        // Blocos de futuro comprometido e maior compra
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBackground.copy(alpha = 0.6f))
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text("FUTURO COMPROMETIDO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                    Spacer(Modifier.height(2.dp))
                    Text(FinanceEngine.formatCurrency(invoice.futureInstallmentsTotal), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("${invoice.futureInstallmentsCount} parcelas nos próx. meses", fontSize = 10.sp, color = TextMuted, maxLines = 1)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBackground.copy(alpha = 0.6f))
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text("MAIOR COMPRA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Rose400)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        invoice.topExpense?.let { FinanceEngine.formatCurrency(it.effectiveAmount) } ?: "—",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        invoice.topExpense?.description ?: "Sem despesas",
                        fontSize = 10.sp,
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
private fun SplitBreakdownCard(
    invoice: CardInvoice,
    selectedParticipantName: String? = null,
    onToggleParticipant: ((String) -> Unit)? = null
) {
    SectionCard("Divisão da Fatura (Quem Divide Comigo)", Icons.Default.People, Emerald400) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            invoice.splitBreakdown.forEach { part ->
                val isSelected = selectedParticipantName == part.name
                val itemColor = if (part.isMe) Emerald400 else Cyan400
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) itemColor.copy(alpha = 0.12f) else DarkBackground.copy(alpha = 0.6f))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) itemColor else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = onToggleParticipant != null) {
                            onToggleParticipant?.invoke(part.name)
                        }
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    part.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (part.isMe) Emerald400 else TextPrimary
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(itemColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text("Filtrando", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = itemColor)
                                    }
                                }
                            }
                            Text(
                                "${FinanceEngine.formatCurrency(part.amount)} (${part.pct}%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { part.pct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = itemColor,
                            trackColor = itemColor.copy(alpha = 0.14f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
            if (onToggleParticipant != null) {
                Text(
                    text = if (selectedParticipantName != null) "Toque no participante novamente para remover o filtro." else "Toque em um participante para filtrar o extrato e categorias por ele.",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun HistoryChart(
    history: List<CardInvoice>,
    currentMonth: String,
    barColor: Color,
    onSelect: (String) -> Unit
) {
    val max = (history.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1.0)
    SectionCard("Evolução das faturas", Icons.Default.BarChart, Cyan400) {
        Row(
            modifier = Modifier.fillMaxWidth().height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            history.forEach { h ->
                val isCurrent = h.monthKey == currentMonth
                val isFuture = h.monthKey > currentMonth
                val fraction = ((h.total / max).toFloat()).coerceIn(if (h.total > 0) 0.04f else 0.01f, 1f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(h.monthKey) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    when {
                                        isCurrent -> barColor
                                        isFuture -> DarkBorder.copy(alpha = 0.5f)
                                        else -> TextMuted.copy(alpha = 0.6f)
                                    }
                                )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        FinanceEngine.formatMonthLabel(h.monthKey).take(3),
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) TextPrimary else TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
        Text("Barras claras à direita = faturas futuras já comprometidas (parcelas).", fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun CategoryBreakdown(
    categories: List<InvoiceCategorySlice>,
    selectedParticipantName: String? = null,
    onClearParticipant: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val displayCategories = if (expanded) categories else categories.take(6)
    val title = if (selectedParticipantName != null) {
        "Para onde foi o dinheiro ($selectedParticipantName)"
    } else {
        "Para onde foi o dinheiro"
    }

    SectionCard(title, Icons.Default.PieChart, Purple400) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedParticipantName != null && onClearParticipant != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Exibindo apenas gastos de $selectedParticipantName",
                        fontSize = 11.sp,
                        color = Cyan400
                    )
                    TextButton(
                        onClick = onClearParticipant,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Ver geral", fontSize = 11.sp, color = Emerald400)
                    }
                }
            }

            displayCategories.forEach { slice ->
                val catDetail = CategoryRegistry.get(slice.category)
                val color = catDetail.color
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    catDetail.icon,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                slice.category,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "${FinanceEngine.formatCurrency(slice.amount)} · ${slice.pct}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { slice.pct / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = color,
                        trackColor = color.copy(alpha = 0.14f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            if (categories.size > 6) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        if (expanded) "Mostrar menos" else "Ver mais (${categories.size - 6} categorias)",
                        fontSize = 11.sp,
                        color = Cyan400
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemsHeader(
    count: Int,
    filter: InvoiceItemFilter,
    searchQuery: String,
    onFilter: (InvoiceItemFilter) -> Unit,
    onSearchChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Lançamentos da fatura ($count)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // Filtros por chip (Formato pílula CircleShape com fundos translúcidos suaves)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(InvoiceItemFilter.entries) { f ->
                val isSelected = f == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilter(f) },
                    label = {
                        Text(
                            text = f.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        labelColor = TextSecondary,
                        selectedContainerColor = Emerald500.copy(alpha = 0.2f),
                        selectedLabelColor = Emerald400
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.White.copy(alpha = 0.08f),
                        selectedBorderColor = Emerald500.copy(alpha = 0.4f),
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    )
                )
            }
        }

        // Campo de busca (Formato pílula CircleShape com fundo translúcido suave)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Buscar lançamentos...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedBorderColor = Emerald400.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
    }
}

@Composable
private fun InvoiceItemRow(
    tx: Transaction,
    participantShare: Double? = null,
    onClick: () -> Unit
) {
    val isCredit = tx.type == TransactionType.INCOME
    val stColor = itemStatusColor(tx.status)
    val catDetail = remember(tx.category) { CategoryRegistry.get(tx.category) }
    val thirdParty = remember(tx) {
        tx.allocations.firstOrNull { it.type == "EXTERNAL" }?.name
            ?: tx.assignedFriendName
    }
    val displayAmount = participantShare ?: tx.effectiveAmount
    val isSharedAndDifferent = participantShare != null && Math.abs(participantShare - tx.effectiveAmount) > 0.009

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(catDetail.color.copy(alpha = 0.14f))
                .border(1.dp, catDetail.color.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = catDetail.icon,
                contentDescription = tx.category,
                tint = catDetail.color,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tx.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                tx.installmentTag?.let {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Cyan400.copy(alpha = 0.14f))
                            .border(1.dp, Cyan400.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(it, fontSize = 9.sp, color = Cyan400, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!thirdParty.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Amber500.copy(alpha = 0.14f))
                            .border(1.dp, Amber500.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "🏷️ $thirdParty",
                            fontSize = 9.sp,
                            color = Amber400,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                listOfNotNull(tx.category, tx.subcategory).joinToString(" › "),
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (isCredit) "− " else "") + FinanceEngine.formatCurrency(displayAmount),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) Cyan400 else TextPrimary
            )
            if (isSharedAndDifferent) {
                Text(
                    "de " + FinanceEngine.formatCurrency(tx.effectiveAmount),
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(stColor.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(tx.status.label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = stColor)
            }
        }
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
    onGoToInvoice: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val card = item.card
    val gradientColors = remember(card.color) { cardGradientColors(card.color) }

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
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ir para a fatura detalhada (Pílula translúcida suave)
                        Surface(
                            onClick = onGoToInvoice,
                            color = Cyan500.copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Ver Fatura",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Cyan400
                                )
                            }
                        }

                        // Toggle Details (Pílula suave)
                        Surface(
                            onClick = onToggleDetails,
                            color = Color.White.copy(alpha = 0.06f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) "Ocultar" else "Lançamentos (${item.transactions.size})",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
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
                            val groupedCardTxs = remember(item.transactions) {
                                val map = LinkedHashMap<String, MutableList<Transaction>>()
                                for (tx in item.transactions) {
                                    val key = tx.dueDate.ifBlank { "Sem data" }
                                    map.getOrPut(key) { mutableListOf() }.add(tx)
                                }
                                map.map { (dateKey, txs) ->
                                    val total = txs.sumOf { tx ->
                                        if (tx.type == TransactionType.INCOME) -tx.effectiveAmount else tx.effectiveAmount
                                    }
                                    InvoiceDateGroup(dateKey, txs, total)
                                }
                            }
                            groupedCardTxs.forEach { grp ->
                                InvoiceDateGroupHeader(
                                    dateKey = grp.dateKey,
                                    totalAmount = grp.totalAmount,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                grp.items.forEach { tx ->
                                    CardTransactionRow(tx = tx)
                                }
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
    val isCredit = tx.type == TransactionType.INCOME
    val formattedAmount = remember(tx.effectiveAmount) { FinanceEngine.formatCurrency(tx.effectiveAmount) }
    val catDetail = remember(tx.category) { CategoryRegistry.get(tx.category) }
    val thirdParty = remember(tx) {
        tx.allocations.firstOrNull { it.type == "EXTERNAL" }?.name
            ?: tx.assignedFriendName
    }

    Surface(
        color = DarkBackground.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(catDetail.color.copy(alpha = 0.14f))
                    .border(1.dp, catDetail.color.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = catDetail.icon,
                    contentDescription = tx.category,
                    tint = catDetail.color,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = listOfNotNull(tx.category, tx.subcategory).joinToString(" › "),
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    tx.installmentTag?.let { tag ->
                        Surface(
                            color = Cyan400.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                color = Cyan400,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (!thirdParty.isNullOrBlank()) {
                        Surface(
                            color = Amber500.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "🏷️ $thirdParty",
                                fontSize = 9.sp,
                                color = Amber400,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = (if (isCredit) "− " else "") + formattedAmount,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCredit) Cyan400 else TextPrimary
            )
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

@Composable
private fun InfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, iconTint: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        content()
    }
}
