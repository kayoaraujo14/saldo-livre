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
    val formattedFreeBalance = remember(summary.freeBalance) { FinanceEngine.formatCurrency(summary.freeBalance) }
    val formattedDailyLimit = remember(summary.dailyLimit) {
        summary.dailyLimit?.let { "${FinanceEngine.formatCurrency(it)} /dia" } ?: "—"
    }

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
                                        .background(Color.White.copy(alpha = 0.05f), IncomePillShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), IncomePillShape)
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

                            // Daily Safe Limit Banner (integrated in card footer)
                            val dailyLimit = summary.dailyLimit
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DailyLimitBannerShape)
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), DailyLimitBannerShape)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Cyan500.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Shield,
                                                contentDescription = null,
                                                tint = Cyan400,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Disponível por Dia",
                                                style = Typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (summary.monthState == "past") "Mês encerrado" else "Restam ${summary.daysRemaining} dias no ritmo",
                                                fontSize = 11.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
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
                                        .background(Amber500.copy(alpha = 0.08f))
                                        .border(1.dp, Amber500.copy(alpha = 0.20f), DailyLimitBannerShape)
                                        .clickable { showUndoClosureDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
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

            // 3. Grid 2x2 de Indicadores Dinâmicos
            item(key = "kpi_grid", contentType = "kpi_grid") {
                val consumption = uiState.incomeConsumption
                val radar = uiState.radar7Days
                val dailyGauge = uiState.dailyGauge
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // KPI 1: Consumo da Renda
                        KpiCard(
                            title = "Consumo da Renda",
                            value = "${consumption.percentage}% Consumido",
                            icon = Icons.Default.PieChart,
                            accentColor = consumptionColor,
                            subtitle = "${FinanceEngine.formatCurrency(consumption.paid)} de ${FinanceEngine.formatCurrency(consumption.income)}",
                            modifier = Modifier.weight(1f)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
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
                            icon = Icons.Default.AccessTime,
                            accentColor = radarAccent,
                            badgeText = radar.urgencyBadge,
                            badgeColor = radarAccent,
                            subtitle = radar.subtitle,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToTransactions
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // KPI 3: Termômetro Diário
                        val gaugeAccent = if (dailyGauge.isEconomy) Emerald400 else Amber400
                        KpiCard(
                            title = "Termômetro Diário",
                            value = dailyGauge.performanceText,
                            icon = Icons.Default.Speed,
                            accentColor = gaugeAccent,
                            badgeText = if (dailyGauge.dailyLimit != null && dailyGauge.dailyLimit > 0.0) {
                                if (dailyGauge.isEconomy) "Econômico" else "Acima da cota"
                            } else null,
                            badgeColor = gaugeAccent,
                            subtitle = if (dailyGauge.dailyLimit != null && dailyGauge.dailyLimit > 0.0) {
                                "Gasto hoje: ${FinanceEngine.formatCurrency(dailyGauge.spentToday)}"
                            } else if (summary.monthState == "past") {
                                "Mês consolidado"
                            } else {
                                "Sem cota diária"
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // KPI 4: Saúde de Investimentos
                        val investAccent = if (investment.isTargetReached) Emerald400 else Cyan400
                        KpiCard(
                            title = "Investimentos",
                            value = "${investment.incomePercentage}% da Renda",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = investAccent,
                            badgeText = if (investment.isTargetReached) "Meta OK" else "Meta: 20%",
                            badgeColor = investAccent,
                            subtitle = "${FinanceEngine.formatCurrency(investment.totalInvested)} aportado",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToInvestments
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { investment.progressRatio },
                                    modifier = Modifier.size(16.dp),
                                    color = investAccent,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "Regra 50/30/20 (Meta 20%)",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
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
