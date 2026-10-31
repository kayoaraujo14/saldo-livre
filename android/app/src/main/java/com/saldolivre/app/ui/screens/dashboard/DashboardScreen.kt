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
import com.saldolivre.app.ui.components.DashboardSkeleton
import com.saldolivre.app.ui.components.DeleteInstallmentDialog
import com.saldolivre.app.ui.components.EmptyState
import com.saldolivre.app.ui.components.KpiCard
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.components.NumberTickerText
import com.saldolivre.app.ui.components.TransactionDetailDialog
import com.saldolivre.app.ui.components.TransactionItem
import com.saldolivre.app.ui.theme.*
import kotlin.math.roundToInt

private val HeroBannerShape = RoundedCornerShape(20.dp)
private val IncomePillShape = RoundedCornerShape(8.dp)
private val PositiveHeroBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF042F2E).copy(alpha = 0.55f),
        Color(0xFF064E3B).copy(alpha = 0.20f),
        DarkSurface
    )
)
private val NegativeHeroBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF4C0519).copy(alpha = 0.45f),
        Color(0xFF2E020D).copy(alpha = 0.20f),
        DarkSurface
    )
)
private val PositiveHeroBorder = Emerald500.copy(alpha = 0.25f)
private val NegativeHeroBorder = Rose500.copy(alpha = 0.25f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToReceivables: () -> Unit,
    onNavigateToInvestments: () -> Unit = {},
    onNavigateToEditTransaction: (String) -> Unit = {},
    onNavigateToRefundTransaction: (String) -> Unit = {},
    onNavigateToCards: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTxForDetails by remember { mutableStateOf<Transaction?>(null) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    var showUndoClosureDialog by remember { mutableStateOf(false) }
    val summary = uiState.summary

    val formattedIncome = remember(summary.income) { FinanceEngine.formatCurrency(summary.income) }

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
        if (uiState.isLoading) {
            DashboardSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
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
                val incomeRemainingPct = if (summary.income > 0.0) {
                    ((summary.freeBalance / summary.income) * 100.0).roundToInt()
                } else 0
                val pctSign = if (incomeRemainingPct > 0) "+$incomeRemainingPct%" else "$incomeRemainingPct%"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = HeroBannerShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
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
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Top Row: Ícone + Título e Badge de % Livre
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
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                (if (isNegative) Rose500 else Emerald500).copy(alpha = 0.15f),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = if (isNegative) Rose400 else Emerald400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SALDO LIVRE REAL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(
                                            (if (isNegative) Rose500 else Emerald500).copy(alpha = 0.15f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "$pctSign livre",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isNegative) Rose400 else Emerald400
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Saldo em destaque com Number Ticker
                            NumberTickerText(
                                amount = summary.freeBalance,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isNegative) Rose400 else Emerald400
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Contexto de Receitas e Despesas (e sobra/déficit)
                            val totalExpenses = summary.paid + summary.committed
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Receitas: $formattedIncome • Despesas: ${FinanceEngine.formatCurrency(totalExpenses)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (summary.carryoverAmount > 0.0) {
                                    Text(
                                        text = "• + ${FinanceEngine.formatCurrency(summary.carryoverAmount)} sobra",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Cyan400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (summary.previousMonthDeficit > 0.0) {
                                    Text(
                                        text = "• - ${FinanceEngine.formatCurrency(summary.previousMonthDeficit)} déficit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Rose400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Colchão de Proteção (se houver)
                            if (summary.cushionAmount > 0.0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Amber500.copy(alpha = 0.10f))
                                        .border(1.dp, Amber500.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .clickable { showUndoClosureDialog = true }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = Amber400,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Colchão: ${FinanceEngine.formatCurrency(summary.cushionAmount)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Amber400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Grid 2x2 de Indicadores Dinâmicos
            item(key = "kpi_grid", contentType = "kpi_grid") {
                val consumption = uiState.incomeConsumption
                val radar = uiState.radar7Days
                val topCategory = uiState.topCategory
                val investment = uiState.investmentHealth

                val consumptionColor = when {
                    consumption.percentage > 100 -> Rose400
                    consumption.percentage > 80 -> Amber400
                    else -> Emerald400
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // KPI 1: Consumo da Renda
                        KpiCard(
                            title = "Consumo da Renda",
                            value = "${consumption.percentage}% Consumido",
                            icon = Icons.Default.PieChart,
                            accentColor = consumptionColor,
                            badgeText = consumption.statusBadge,
                            badgeColor = consumptionColor,
                            subtitle = "${FinanceEngine.formatCurrency(consumption.paid)} pagos no mês",
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(consumptionColor.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = (consumption.percentage / 100f).coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(consumptionColor)
                                )
                            }
                        }

                        // KPI 2: Radar 7 Dias
                        val radarAccent = when {
                            radar.isUrgent -> Rose400
                            radar.totalAmount > 0.0 -> Amber400
                            else -> Emerald400
                        }
                        KpiCard(
                            title = "Radar 7 Dias",
                            value = FinanceEngine.formatCurrency(radar.totalAmount),
                            numericValue = radar.totalAmount,
                            icon = Icons.Default.AccessTime,
                            accentColor = radarAccent,
                            badgeText = radar.urgencyBadge,
                            badgeColor = radarAccent,
                            subtitle = if (radar.count == 0) "Até ${radar.formattedEndDate} sem contas" else "Vencendo até ${radar.formattedEndDate}",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onNavigateToTransactions
                        ) {
                            Text(
                                text = if (radar.count == 0) "Próx. vencimento livre" else "${radar.count} conta${if (radar.count > 1) "s" else ""} a vencer",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = radarAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // KPI 3: Maior Gasto (Top Categoria)
                        KpiCard(
                            title = "Maior Gasto",
                            value = FinanceEngine.formatCurrency(topCategory.amount),
                            numericValue = topCategory.amount,
                            icon = Icons.Default.ShoppingBag,
                            accentColor = Purple400,
                            badgeText = topCategory.badgeText,
                            badgeColor = Purple400,
                            subtitle = topCategory.subtitle,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Text(
                                text = topCategory.categoryName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Purple400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // KPI 4: Saúde de Investimentos
                        val investAccent = if (investment.isTargetReached) Emerald400 else Cyan400
                        KpiCard(
                            title = "Investimentos",
                            value = "${investment.incomePercentage}% da Renda",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = investAccent,
                            badgeText = investment.badgeText,
                            badgeColor = investAccent,
                            subtitle = "${FinanceEngine.formatCurrency(investment.totalInvested)} aportados no mês",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onNavigateToInvestments
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { investment.progressRatio },
                                    modifier = Modifier.size(16.dp),
                                    color = investAccent,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "Regra 50/30/20",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 4. Central de Lançamentos Recorrentes Programados (Confirmação inteligente)
            if (uiState.pendingRecurringLaunches.isNotEmpty()) {
                item(key = "pending_launches_banner", contentType = "pending_launches_banner") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Amber500.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Amber400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Contas Fixas & Renda Programadas",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${uiState.pendingRecurringLaunches.size} pronta(s) para conferência",
                                            fontSize = 10.sp,
                                            color = Amber400
                                        )
                                    }
                                }

                                val safeCount = uiState.pendingRecurringLaunches.count { it.matchedManualTx == null }
                                if (safeCount > 1) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = { viewModel.confirmAllCandidates(asPaid = true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text(
                                                text = "Confirmar Tudo Pago",
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.confirmAllCandidates(asPaid = false) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp),
                                            border = BorderStroke(1.dp, Amber500.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "Previsto",
                                                fontSize = 10.sp,
                                                color = Amber400,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.pendingRecurringLaunches.forEach { item ->
                                    val hasMatch = item.matchedManualTx != null
                                    Surface(
                                        color = if (hasMatch) Amber500.copy(alpha = 0.08f) else DarkBackground,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (hasMatch) Amber500.copy(alpha = 0.35f) else DarkBorder
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = item.description,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White
                                                        )
                                                        Text(
                                                            text = FinanceEngine.formatCurrency(item.amount),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (item.type == com.saldolivre.app.data.model.TransactionType.INCOME) Emerald500 else TextSecondary
                                                        )
                                                    }
                                                    Text(
                                                        text = "Vence dia ${item.dueDay} • ${item.category}",
                                                        fontSize = 10.sp,
                                                        color = TextMuted
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (hasMatch && item.matchedManualTx != null) {
                                                        Button(
                                                            onClick = { viewModel.linkManualTransaction(item.id, item.matchedManualTx) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(26.dp)
                                                        ) {
                                                            Text(
                                                                text = "Vincular",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.Black
                                                            )
                                                        }
                                                        OutlinedButton(
                                                            onClick = { viewModel.skipRecurring(item.id) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(26.dp),
                                                            border = BorderStroke(1.dp, DarkBorder)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Pular",
                                                                tint = TextMuted,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    } else {
                                                        val isCardBill = item.paymentMethod == com.saldolivre.app.data.model.PaymentMethod.CREDIT_CARD || !item.cardId.isNullOrBlank()
                                                        val isIncome = item.type == com.saldolivre.app.data.model.TransactionType.INCOME
                                                        Button(
                                                            onClick = { viewModel.confirmLaunchCandidate(item, asPaid = true) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(26.dp)
                                                        ) {
                                                            Text(
                                                                text = when {
                                                                    isCardBill -> "Na Fatura"
                                                                    isIncome -> "Já Recebi"
                                                                    else -> "Já Paguei"
                                                                },
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                        if (!isCardBill) {
                                                            OutlinedButton(
                                                                onClick = { viewModel.confirmLaunchCandidate(item, asPaid = false) },
                                                                shape = RoundedCornerShape(8.dp),
                                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(26.dp),
                                                                border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f))
                                                            ) {
                                                                Text(
                                                                    text = "Previsto",
                                                                    fontSize = 10.sp,
                                                                    color = Emerald400
                                                                )
                                                            }
                                                        }
                                                        OutlinedButton(
                                                            onClick = { viewModel.skipRecurring(item.id) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(26.dp),
                                                            border = BorderStroke(1.dp, DarkBorder)
                                                        ) {
                                                            Text(
                                                                text = "Pular",
                                                                fontSize = 10.sp,
                                                                color = TextMuted
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (hasMatch && item.matchedManualTx != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "💡 Encontramos manual: \"${item.matchedManualTx.description}\" (${FinanceEngine.formatCurrency(item.matchedManualTx.totalAmount)}) em ${item.matchedManualTx.dueDate}",
                                                    fontSize = 10.sp,
                                                    color = Amber400
                                                )
                                            }
                                        }
                                    }
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
                    EmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = "Nenhum lançamento no mês",
                        description = "Nenhum gasto ou receita registrado neste mês. Cadastre uma despesa para acompanhar seu saldo livre.",
                        buttonText = "Ver Transações",
                        buttonIcon = Icons.Default.ArrowForward,
                        onButtonClick = onNavigateToTransactions
                    )
                }
            } else {
                items(
                    items = uiState.recentTransactions,
                    key = { it.id },
                    contentType = { "recent_transaction_item" }
                ) { tx ->
                    val onItemClick = remember(tx) {
                        {
                            if (tx.id.startsWith("invoice_")) {
                                onNavigateToCards()
                            } else {
                                selectedTxForDetails = tx
                            }
                        }
                    }
                    TransactionItem(
                        transaction = tx,
                        currentUserId = uiState.currentUser.uid,
                        allTransactions = uiState.recentTransactions,
                        showDate = true,
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
    }

    // Detail & Actions Dialog
    selectedTxForDetails?.let { initialTx ->
        val tx = uiState.recentTransactions.find { it.id == initialTx.id } ?: initialTx
        TransactionDetailDialog(
            transaction = tx,
            currentUserId = uiState.currentUser.uid,
            currentUser = uiState.currentUser,
            allTransactions = uiState.allTransactions,
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
            onConfirmRefund = { origTx, refundAmount ->
                viewModel.createRefund(origTx, refundAmount)
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
