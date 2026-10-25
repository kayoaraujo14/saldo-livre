package com.saldolivre.app.ui.screens.invoices

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.saldolivre.app.data.engine.CardInvoice
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.engine.InvoiceStatus
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

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

private fun statusColor(status: InvoiceStatus): Color = when (status) {
    InvoiceStatus.EMPTY -> TextMuted
    InvoiceStatus.OPEN -> Cyan400
    InvoiceStatus.CLOSED -> Amber400
    InvoiceStatus.OVERDUE -> Rose400
    InvoiceStatus.PAID -> Emerald400
}

private fun itemStatusColor(status: TransactionStatus): Color = when (status) {
    TransactionStatus.ESTIMATED -> Amber400
    TransactionStatus.PENDING -> Rose400
    TransactionStatus.PAID -> Emerald400
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardInvoicesScreen(
    viewModel: CardInvoicesViewModel,
    onNavigateBack: () -> Unit,
    onEditTransaction: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val selected = uiState.selected

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val visibleItems = remember(selected, uiState.itemFilter, uiState.searchQuery) {
        val items = selected?.items.orEmpty()
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

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    navigationIconContentColor = TextPrimary,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Faturas dos Cartões",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Fechamento, vencimento, rateio e inteligência",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "month") {
                MonthNavigator(
                    monthKey = uiState.viewMonth,
                    onPrev = viewModel::previousMonth,
                    onNext = viewModel::nextMonth
                )
            }

            if (!uiState.isLoading && uiState.invoices.isEmpty()) {
                item(key = "empty") { EmptyCards() }
                return@LazyColumn
            }

            // Oculta KPIs redundantes se houver apenas 1 cartão ou se o valor for idêntico ao do cartão selecionado
            val hideKpis = selected != null && (
                uiState.invoices.size <= 1 ||
                (kotlin.math.abs(uiState.totalOpen - selected.openAmount) < 0.01 && kotlin.math.abs(uiState.totalAmount - selected.total) < 0.01)
            )

            if (!hideKpis) {
                item(key = "kpis") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile("Total das faturas", FinanceEngine.formatCurrency(uiState.totalAmount), TextPrimary, Modifier.weight(1f))
                            KpiTile("Em aberto", FinanceEngine.formatCurrency(uiState.totalOpen), Amber400, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiTile("Minha parte", FinanceEngine.formatCurrency(uiState.totalMine), Emerald400, Modifier.weight(1f))
                            KpiTile(
                                label = "Próximo vencimento",
                                value = uiState.nextDue?.let { FinanceEngine.formatShortDate(it.dueDate) } ?: "—",
                                color = Rose400,
                                modifier = Modifier.weight(1f),
                                hint = uiState.nextDue?.card?.cardName ?: "Tudo em dia"
                            )
                        }
                    }
                }
            }

            if (uiState.invoices.size > 1) {
                item(key = "card_selector") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.invoices, key = { it.card.id }) { inv ->
                            CardChip(
                                invoice = inv,
                                selected = inv.card.id == uiState.selectedCardId,
                                onClick = { viewModel.selectCard(inv.card.id) }
                            )
                        }
                    }
                }
            }

            if (selected != null) {
                // 1. Cartão Virtual Moderno
                item(key = "summary_${selected.card.id}") {
                    InvoiceSummaryCard(
                        invoice = selected,
                        showConfirm = uiState.showPayConfirm,
                        isPaying = uiState.isPaying,
                        onPay = viewModel::requestPay,
                        onConfirm = viewModel::confirmPay,
                        onDismiss = viewModel::dismissPay
                    )
                }

                // 2. Card Inteligente: Melhor Época de Compra e Ciclo
                item(key = "insights_${selected.card.id}") {
                    SmartInsightsCard(invoice = selected)
                }

                // 3. Divisão da Fatura (Quem divide comigo)
                if (selected.splitBreakdown.isNotEmpty()) {
                    item(key = "split_${selected.card.id}") {
                        SplitBreakdownCard(invoice = selected)
                    }
                }

                // 4. Histórico e Evolução
                item(key = "history") {
                    HistoryChart(
                        history = uiState.history,
                        currentMonth = uiState.viewMonth,
                        barColor = cardColor(selected.card.color),
                        onSelect = viewModel::goToMonth
                    )
                }

                // 5. Categorias
                if (selected.byCategory.isNotEmpty()) {
                    item(key = "categories") { CategoryBreakdown(selected) }
                }

                // 6. Cabeçalho de Lançamentos + Busca + Filtros
                item(key = "items_header") {
                    ItemsHeader(
                        count = selected.itemsCount,
                        filter = uiState.itemFilter,
                        searchQuery = uiState.searchQuery,
                        onFilter = viewModel::setFilter,
                        onSearchChange = viewModel::setSearchQuery
                    )
                }

                if (visibleItems.isEmpty()) {
                    item(key = "items_empty") {
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
                    items(visibleItems, key = { "tx_${it.id}" }) { tx ->
                        InvoiceItemRow(tx = tx, onClick = { onEditTransaction(tx.id) })
                    }
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

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
private fun KpiTile(label: String, value: String, color: Color, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        if (hint != null) {
            Text(hint, fontSize = 10.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusPill(status: InvoiceStatus) {
    val c = statusColor(status)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Text(status.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c)
    }
}

@Composable
private fun CardChip(invoice: CardInvoice, selected: Boolean, onClick: () -> Unit) {
    val base = cardColor(invoice.card.color)
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.55f), DarkBackground)))
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
                invoice.card.cardName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusPill(invoice.status)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            FinanceEngine.formatCurrency(invoice.total),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text(
            "Vence ${FinanceEngine.formatShortDate(invoice.dueDate)} · ${invoice.itemsCount} itens",
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
    onDismiss: () -> Unit
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
        // Top row: Titular + Chip metálico simulado + Status
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

            // Chip simulado
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                StatusPill(invoice.status)
            }
        }

        // Fechamento e Vencimento
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoBox("Fechamento", FinanceEngine.formatShortDate(invoice.closingDate), Modifier.weight(1f))
            InfoBox("Vencimento", FinanceEngine.formatShortDate(invoice.dueDate), Modifier.weight(1f))
        }

        // Progresso de pagamento
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pago: ${FinanceEngine.formatCurrency(invoice.paidAmount)}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                Text("Em aberto: ${FinanceEngine.formatCurrency(invoice.openAmount)}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            }
            LinearProgressIndicator(
                progress = { paidProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Emerald400,
                trackColor = Emerald400.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
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
                    Text(
                        "Disponível: ${FinanceEngine.formatCurrency((limit - invoice.total).coerceAtLeast(0.0))}",
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
                    trackColor = usageColor.copy(alpha = 0.15f),
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
                    Text("${invoice.futureInstallmentsCount} parcelas nos prox. meses", fontSize = 10.sp, color = TextMuted, maxLines = 1)
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
private fun SplitBreakdownCard(invoice: CardInvoice) {
    SectionCard("Divisão da Fatura (Quem Divide Comigo)", Icons.Default.People, Emerald400) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            invoice.splitBreakdown.forEach { part ->
                val itemColor = if (part.isMe) Emerald400 else Cyan400
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBackground.copy(alpha = 0.6f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                part.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (part.isMe) Emerald400 else TextPrimary
                            )
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
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color, content: @Composable ColumnScope.() -> Unit) {
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
private fun CategoryBreakdown(invoice: CardInvoice) {
    val palette = listOf(Emerald500, Cyan500, Purple500, Amber500, Rose500, CardInterOrange)
    SectionCard("Para onde foi o dinheiro", Icons.Default.PieChart, Purple400) {
        invoice.byCategory.take(6).forEachIndexed { index, slice ->
            val color = palette[index % palette.size]
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(slice.category, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${FinanceEngine.formatCurrency(slice.amount)} · ${slice.pct}%", fontSize = 11.sp, color = TextMuted)
                }
                LinearProgressIndicator(
                    progress = { slice.pct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = color,
                    trackColor = color.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round
                )
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

        // Filtros por chip (Formato pílula CircleShape)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(InvoiceItemFilter.entries) { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { onFilter(f) },
                    label = { Text(f.label, fontSize = 12.sp, fontWeight = if (f == filter) FontWeight.Bold else FontWeight.Medium) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DarkSurface,
                        labelColor = TextSecondary,
                        selectedContainerColor = Emerald500.copy(alpha = 0.2f),
                        selectedLabelColor = Emerald400
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = f == filter,
                        borderColor = DarkBorder,
                        selectedBorderColor = Emerald500.copy(alpha = 0.4f),
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    )
                )
            }
        }

        // Campo de busca (Formato pílula CircleShape)
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
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = Cyan400,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
    }
}

@Composable
private fun InvoiceItemRow(tx: Transaction, onClick: () -> Unit) {
    val isCredit = tx.type == TransactionType.INCOME
    val stColor = itemStatusColor(tx.status)
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
                            .background(Cyan400.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                    ) {
                        Text(it, fontSize = 9.sp, color = Cyan400, fontWeight = FontWeight.SemiBold)
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
                (if (isCredit) "− " else "") + FinanceEngine.formatCurrency(tx.effectiveAmount),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) Cyan400 else TextPrimary
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(stColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tx.status.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = stColor
                )
            }
        }
    }
}

@Composable
private fun EmptyCards() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.CreditCard, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
        Text("Nenhum cartão cadastrado", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(
            "Cadastre um cartão em \"Cartões de Crédito\" para acompanhar as faturas.",
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}
