package com.saldolivre.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.ui.components.DeleteInstallmentDialog
import com.saldolivre.app.ui.components.KpiCard
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.components.TransactionDetailDialog
import com.saldolivre.app.ui.components.TransactionItem
import com.saldolivre.app.ui.theme.*

private val HeroBannerShape = RoundedCornerShape(20.dp)
private val DailyLimitBannerShape = RoundedCornerShape(12.dp)
private val IncomePillShape = RoundedCornerShape(8.dp)
private val PositiveHeroBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF064E3B).copy(alpha = 0.50f),
        DarkSurface
    )
)
private val NegativeHeroBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF4C0519).copy(alpha = 0.45f),
        DarkSurface
    )
)
private val PositiveHeroBorder = Emerald500.copy(alpha = 0.4f)
private val NegativeHeroBorder = Rose500.copy(alpha = 0.4f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToReceivables: () -> Unit,
    onNavigateToInvestments: () -> Unit = {},
    onNavigateToEditTransaction: (String) -> Unit = {},
    onNavigateToRefundTransaction: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTxForDetails by remember { mutableStateOf<Transaction?>(null) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    var showUndoClosureDialog by remember { mutableStateOf(false) }
    val summary = uiState.summary

    val formattedIncome = remember(summary.income) { FinanceEngine.formatCurrency(summary.income) }
    val formattedFreeBalance = remember(summary.freeBalance) { FinanceEngine.formatCurrency(summary.freeBalance) }
    val formattedDailyLimit = remember(summary.dailyLimit) {
        summary.dailyLimit?.let { "${FinanceEngine.formatCurrency(it)} /dia" } ?: "—"
    }
    val formattedCommitted = remember(summary.committed) { FinanceEngine.formatCurrency(summary.committed) }
    val formattedCommittedEst = remember(summary.committedEstimated) {
        "Prev: ${FinanceEngine.formatCurrency(summary.committedEstimated)}"
    }
    val formattedPaid = remember(summary.paid) { FinanceEngine.formatCurrency(summary.paid) }
    val formattedReceivable = remember(summary.receivableTotal) { FinanceEngine.formatCurrency(summary.receivableTotal) }
    val formattedInvested = remember(summary.totalInvested) { FinanceEngine.formatCurrency(summary.totalInvested) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Emerald500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Saldo Livre",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Controle Pessoal & Compartilhado",
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
            // 1. Seletor de Mês
            item(key = "month_selector", contentType = "month_selector") {
                MonthSelector(
                    selectedMonth = uiState.selectedMonth,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onCurrentMonth = { viewModel.currentMonth() }
                )
            }

            // 1.1 Banner de Fechamento Pendente (se houver sobra do mês anterior aguardando decisão)
            if (summary.pendingClosureSurplus != null && summary.pendingClosureSurplus > 0.0) {
                item(key = "pending_closure_banner", contentType = "pending_closure_banner") {
                    val prevMonth = summary.pendingClosureMonth ?: FinanceEngine.shiftMonth(uiState.selectedMonth, -1)
                    PendingClosureBanner(
                        monthKey = prevMonth,
                        surplusAmount = summary.pendingClosureSurplus,
                        onOpenClosure = { viewModel.openClosureDialog() }
                    )
                }
            }

            // 2. Hero KPI Card: Saldo Livre Real & Limite Diário Seguro
            item(key = "hero_free_balance", contentType = "hero_free_balance") {
                val isNegative = summary.freeBalance < 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = HeroBannerShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isNegative) NegativeHeroBorder else PositiveHeroBorder
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isNegative) NegativeHeroBrush else PositiveHeroBrush)
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SALDO LIVRE REAL",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (isNegative) Rose400 else Emerald400,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .background(DarkSurfaceVariant, IncomePillShape)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Receitas: $formattedIncome",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = formattedFreeBalance,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isNegative) Rose400 else Emerald400,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (summary.carryoverAmount > 0.0) {
                                    Text(
                                        text = "+ ${FinanceEngine.formatCurrency(summary.carryoverAmount)} sobra",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Cyan400,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                } else if (summary.previousMonthDeficit > 0.0) {
                                    Text(
                                        text = "- ${FinanceEngine.formatCurrency(summary.previousMonthDeficit)} déficit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Rose400,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Daily Safe Limit Banner
                            val dailyLimit = summary.dailyLimit
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DailyLimitBannerShape)
                                    .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                                    .border(1.dp, DarkBorder, DailyLimitBannerShape)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = Cyan400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Disponível por Dia:",
                                            style = Typography.bodyMedium,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = formattedDailyLimit,
                                        style = Typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dailyLimit != null && dailyLimit > 0) Cyan400 else Rose400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Colchão de Proteção (se houver reserva alocada para imprevistos)
                            if (summary.cushionAmount > 0.0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DailyLimitBannerShape)
                                        .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                        .border(1.dp, Amber500.copy(alpha = 0.35f), DailyLimitBannerShape)
                                        .clickable { showUndoClosureDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Shield,
                                                contentDescription = null,
                                                tint = Amber400,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Colchão de Proteção:",
                                                style = Typography.bodyMedium,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = FinanceEngine.formatCurrency(summary.cushionAmount),
                                            style = Typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Amber400,
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

            // 3. Grid 2x2 de KPIs Essenciais
            item(key = "kpi_grid", contentType = "kpi_grid") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiCard(
                            title = "Comprometido",
                            value = formattedCommitted,
                            icon = Icons.Default.Schedule,
                            accentColor = Amber500,
                            subtitle = formattedCommittedEst,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "Já Pago",
                            value = formattedPaid,
                            icon = Icons.Default.CheckCircle,
                            accentColor = Emerald500,
                            subtitle = "Liquidado no mês",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiCard(
                            title = "A Receber",
                            value = formattedReceivable,
                            icon = Icons.Default.Group,
                            accentColor = Purple500,
                            subtitle = "Cartão / Terceiros",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToReceivables
                        )
                        KpiCard(
                            title = "Investimentos",
                            value = formattedInvested,
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = Cyan500,
                            subtitle = "Aportes do mês",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToInvestments
                        )
                    }
                }
            }

            // 4. Banner de Lançamento Automático Recente
            uiState.autoLaunchNotice?.let { notice ->
                item(key = "auto_launch_banner", contentType = "auto_launch_banner") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Amber500.copy(alpha = 0.15f))
                                    .border(1.dp, Amber500.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ Lançamento Automático",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber400
                                    )
                                    IconButton(
                                        onClick = { viewModel.dismissAutoLaunchNotice() },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fechar",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                val firstBill = notice.bills.firstOrNull()
                                val msg = if (notice.count == 1 && firstBill != null) {
                                    "A conta ${firstBill.description} (${FinanceEngine.formatCurrency(firstBill.totalAmount)}) foi lançada automaticamente no seu extrato deste mês."
                                } else {
                                    "${notice.count} contas fixas programadas foram lançadas automaticamente no seu extrato."
                                }

                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Ver no Extrato →",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber400,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.dismissAutoLaunchNotice()
                                                onNavigateToTransactions()
                                            }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Cabeçalho da Seção: Últimos Lançamentos
            item(key = "recent_header", contentType = "recent_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Últimos Lançamentos",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Ver todos",
                        style = Typography.labelLarge,
                        color = Emerald400,
                        modifier = Modifier.clickable(onClick = onNavigateToTransactions)
                    )
                }
            }

            // 5. Lista de Lançamentos Recentes
            if (uiState.recentTransactions.isEmpty()) {
                item(key = "recent_empty", contentType = "recent_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum lançamento registrado neste mês.",
                            color = TextMuted,
                            style = Typography.bodyMedium
                        )
                    }
                }
            } else {
                items(
                    items = uiState.recentTransactions,
                    key = { it.id },
                    contentType = { "recent_transaction_item" }
                ) { tx ->
                    val onItemClick = remember(tx) { { selectedTxForDetails = tx } }
                    TransactionItem(
                        transaction = tx,
                        currentUserId = uiState.currentUser.uid,
                        allTransactions = uiState.recentTransactions,
                        onClick = onItemClick,
                        onStatusClick = { viewModel.toggleTransactionStatus(tx.id) }
                    )
                }
            }

            item(key = "bottom_spacer", contentType = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Detail & Actions Dialog
    selectedTxForDetails?.let { initialTx ->
        val tx = uiState.recentTransactions.find { it.id == initialTx.id } ?: initialTx
        TransactionDetailDialog(
            transaction = tx,
            currentUserId = uiState.currentUser.uid,
            allTransactions = uiState.recentTransactions,
            onDismissRequest = { selectedTxForDetails = null },
            onToggleStatus = { viewModel.toggleTransactionStatus(tx.id) },
            onSetStatus = { status, actualAmount ->
                viewModel.setStatus(tx.id, status, actualAmount)
            },
            onNavigateToEditTransaction = { editTxId ->
                selectedTxForDetails = null
                onNavigateToEditTransaction(editTxId)
            },
            onNavigateToRefundTransaction = { refundTxId ->
                selectedTxForDetails = null
                onNavigateToRefundTransaction(refundTxId)
            },
            onDeleteRequest = {
                txToDelete = tx
                selectedTxForDetails = null
            }
        )
    }

    txToDelete?.let { tx ->
        if (tx.isInstallment && !tx.installmentGroupId.isNullOrBlank()) {
            DeleteInstallmentDialog(
                transaction = tx,
                allTransactions = uiState.allTransactions,
                onDismissRequest = { txToDelete = null },
                onDeleteSingle = { singleTx ->
                    viewModel.deleteTransaction(singleTx.id)
                    txToDelete = null
                },
                onDeleteAll = { groupId ->
                    viewModel.deleteInstallmentGroup(groupId)
                    txToDelete = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { txToDelete = null },
                containerColor = DarkSurface,
                title = {
                    Text(
                        text = "Excluir Lançamento",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Deseja realmente excluir \"${tx.description}\"?",
                            color = TextSecondary,
                            style = Typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTransaction(tx.id)
                            txToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                    ) {
                        Text(text = "Excluir", color = TextPrimary, maxLines = 1)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { txToDelete = null }) {
                        Text(text = "Cancelar", color = TextSecondary, maxLines = 1)
                    }
                }
            )
        }
    }

    // Dialog de Fechamento de Mês (comemoração e distribuição)
    if (uiState.showClosureDialog && summary.pendingClosureSurplus != null && summary.pendingClosureSurplus > 0.0) {
        val closedMonth = summary.pendingClosureMonth ?: FinanceEngine.shiftMonth(uiState.selectedMonth, -1)
        MonthClosureDialog(
            closedMonthKey = closedMonth,
            targetMonthKey = uiState.selectedMonth,
            totalSurplus = summary.pendingClosureSurplus,
            onConfirm = { carryover, invested, cushion ->
                viewModel.confirmMonthClosure(carryover, invested, cushion)
            },
            onDismiss = {
                viewModel.dismissClosureDialog()
            }
        )
    }

    // Dialog de Desfazer Fechamento de Mês (ao clicar no Colchão de Segurança)
    if (showUndoClosureDialog) {
        val prevMonth = summary.pendingClosureMonth ?: FinanceEngine.shiftMonth(uiState.selectedMonth, -1)
        val prevMonthLabel = FinanceEngine.formatMonthLabel(prevMonth)
        AlertDialog(
            onDismissRequest = { showUndoClosureDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Colchão de Proteção",
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Valor reservado: ${FinanceEngine.formatCurrency(summary.cushionAmount)}",
                        fontWeight = FontWeight.SemiBold,
                        color = Amber400
                    )
                    Text(
                        text = "Esse montante foi separado no fechamento de $prevMonthLabel para não ser consumido no dia a dia.",
                        style = Typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Deseja desfazer este fechamento? O valor voltará para o Saldo Livre Real.",
                        style = Typography.bodySmall,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.undoMonthClosure(prevMonth)
                        showUndoClosureDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text(text = "Desfazer Fechamento", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUndoClosureDialog = false }) {
                    Text(text = "Manter", color = TextSecondary)
                }
            }
        )
    }
}
