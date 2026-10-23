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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.CardInvoice
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
import com.saldolivre.app.data.engine.InvoiceStatus
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

    val visibleItems = remember(selected, uiState.itemFilter) {
        val items = selected?.items.orEmpty()
        when (uiState.itemFilter) {
            InvoiceItemFilter.ALL -> items
            InvoiceItemFilter.OPEN -> items.filter { it.status != TransactionStatus.PAID }
            InvoiceItemFilter.PAID -> items.filter { it.status == TransactionStatus.PAID }
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
                            text = "Fechamento, vencimento e pagamento",
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

            if (selected != null) {
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

                item(key = "history") {
                    HistoryChart(
                        history = uiState.history,
                        currentMonth = uiState.viewMonth,
                        barColor = cardColor(selected.card.color),
                        onSelect = viewModel::goToMonth
                    )
                }

                if (selected.byCategory.isNotEmpty()) {
                    item(key = "categories") { CategoryBreakdown(selected) }
                }

                item(key = "items_header") {
                    ItemsHeader(
                        count = selected.itemsCount,
                        filter = uiState.itemFilter,
                        onFilter = viewModel::setFilter
                    )
                }

                if (visibleItems.isEmpty()) {
                    item(key = "items_empty") {
                        Text(
                            text = "Nenhum lançamento para este filtro.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
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
    val base = cardColor(invoice.card.color)
    val paidProgress by animateFloatAsState(invoice.paidRatio, label = "paidProgress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.40f), DarkSurface)))
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Fatura ${invoice.card.cardName}", fontSize = 11.sp, color = TextSecondary)
                Text(
                    FinanceEngine.formatCurrency(invoice.total),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    InvoiceEngine.countdownLabel(invoice),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (invoice.status == InvoiceStatus.OVERDUE) Rose400 else TextPrimary
                )
            }
            StatusPill(invoice.status)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoBox("Fechamento", FinanceEngine.formatShortDate(invoice.closingDate), Modifier.weight(1f))
            InfoBox("Vencimento", FinanceEngine.formatShortDate(invoice.dueDate), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoBox("Minha parte", FinanceEngine.formatCurrency(invoice.myShare), Modifier.weight(1f))
            InfoBox("De terceiros", FinanceEngine.formatCurrency(invoice.othersShare), Modifier.weight(1f))
        }

        // Progresso de pagamento
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pago ${FinanceEngine.formatCurrency(invoice.paidAmount)}", fontSize = 11.sp, color = TextSecondary)
                Text("Em aberto ${FinanceEngine.formatCurrency(invoice.openAmount)}", fontSize = 11.sp, color = TextSecondary)
            }
            LinearProgressIndicator(
                progress = { paidProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Emerald500,
                trackColor = DarkBackground.copy(alpha = 0.8f)
            )
        }

        invoice.limit?.let { limit ->
            val usage = invoice.limitUsagePct ?: 0
            val usageColor = when {
                usage >= 90 -> Rose500
                usage >= 70 -> Amber500
                else -> base
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Uso do limite ($usage%)", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        "Disponível ${FinanceEngine.formatCurrency((limit - invoice.total).coerceAtLeast(0.0))}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                LinearProgressIndicator(
                    progress = { usage / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = usageColor,
                    trackColor = DarkBackground.copy(alpha = 0.8f)
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
                        .background(Emerald500.copy(alpha = 0.12f))
                        .border(1.dp, Emerald500.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
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
private fun InfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBackground.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 10.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(slice.category, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${FinanceEngine.formatCurrency(slice.amount)} · ${slice.pct}%", fontSize = 11.sp, color = TextMuted)
                }
                LinearProgressIndicator(
                    progress = { slice.pct / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = color,
                    trackColor = DarkBackground
                )
            }
        }
    }
}

@Composable
private fun ItemsHeader(count: Int, filter: InvoiceItemFilter, onFilter: (InvoiceItemFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Lançamentos da fatura ($count)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvoiceItemFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { onFilter(f) },
                    label = { Text(f.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DarkSurface,
                        labelColor = TextSecondary,
                        selectedContainerColor = Emerald500.copy(alpha = 0.2f),
                        selectedLabelColor = Emerald400
                    )
                )
            }
        }
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
                    Text(it, fontSize = 10.sp, color = Cyan400, fontWeight = FontWeight.SemiBold)
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
                    .background(stColor.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(tx.status.label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = stColor)
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
