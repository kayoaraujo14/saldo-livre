package com.saldolivre.app.ui.screens.projections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.saldolivre.app.ui.util.safeNavigationBarBottomPadding
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.FutureMonthProjection
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.ProjectedCategoryItem
import com.saldolivre.app.data.model.ProjectedMonthItem
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*
import kotlin.math.max
import kotlin.math.min

private val DarkCyanHeroTint = Color(0xFF164E63)
private val DarkRoseHeroTint = Color(0xFF4C0519)

private val PositiveProjectionsBrush = Brush.verticalGradient(
    colors = listOf(DarkCyanHeroTint.copy(alpha = 0.5f), DarkSurface)
)

private val NegativeProjectionsBrush = Brush.verticalGradient(
    colors = listOf(DarkRoseHeroTint.copy(alpha = 0.5f), DarkSurface)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureProjectionsScreen(
    viewModel: FutureProjectionsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Cyan500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .border(1.dp, Cyan500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Projeção de Fluxo (${uiState.horizonCount}M)",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Simulação de Renda, Cartão, Fixas e Categorias",
                                style = Typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = safeNavigationBarBottomPadding(extraDp = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Seletor de Horizonte (6 Meses vs 12 Meses)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizonSelectorButton(
                        label = "6 Meses",
                        selected = uiState.horizonCount == 6,
                        onClick = { viewModel.setHorizonCount(6) },
                        modifier = Modifier.weight(1f)
                    )
                    HorizonSelectorButton(
                        label = "12 Meses",
                        selected = uiState.horizonCount == 12,
                        onClick = { viewModel.setHorizonCount(12) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Painel Executivo Acumulado (Hero Card + 4 Métricas Chave)
            item {
                ProjectionsHeroBanner(
                    horizonCount = uiState.horizonCount,
                    averageFreeBalance = uiState.averageFreeBalance,
                    totalIncome = uiState.totalProjectedIncome,
                    totalExpense = uiState.totalProjectedExpense,
                    totalInvoices = uiState.totalInvoicesExpense,
                    totalFixed = uiState.totalFixedBillsExpense
                )
            }

            item {
                Text(
                    text = "CRONOGRAMA MENSAL PROJETADO (${uiState.projections.size} MESES)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            items(uiState.projections, key = { it.monthKey }) { proj ->
                MonthProjectionCard(
                    projection = proj,
                    isCurrentMonth = proj.monthKey == uiState.currentMonth,
                    isSelectedMonth = proj.monthKey == uiState.selectedMonth,
                    onSelectMonth = {
                        viewModel.selectMonth(proj.monthKey)
                        onNavigateBack()
                    }
                )
            }

            // Nota informativa de rodapé
            item {
                Text(
                    text = "* As previsões somam automaticamente faturas de cartão, parcelas futuras, contas fixas e salário para até 12 meses.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HorizonSelectorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (selected) Cyan500.copy(alpha = 0.2f) else Color.Transparent
    val border = if (selected) Cyan500.copy(alpha = 0.4f) else Color.Transparent
    val textColor = if (selected) Cyan400 else TextMuted

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun ProjectionsHeroBanner(
    horizonCount: Int,
    averageFreeBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    totalInvoices: Double,
    totalFixed: Double
) {
    val isPositive = averageFreeBalance >= 0.0
    val accentColor = if (isPositive) Cyan400 else Rose400
    val borderColor = if (isPositive) Cyan500.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f)
    val brush = if (isPositive) PositiveProjectionsBrush else NegativeProjectionsBrush

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = brush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MÉDIA LIVRE (${horizonCount}M)",
                    style = Typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.16f), CircleShape)
                        .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Automático",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }

            Text(
                text = "${FinanceEngine.formatCurrency(averageFreeBalance)}/mês",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )

            // Grid 2x2 de métricas acumuladas (Entradas, Saídas Totais, Faturas Cartão, Contas Fixas)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "ENTRADAS (${horizonCount}M)",
                        value = "+${FinanceEngine.formatCurrency(totalIncome)}",
                        valueColor = Emerald400,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "SAÍDAS (${horizonCount}M)",
                        value = "−${FinanceEngine.formatCurrency(totalExpense)}",
                        valueColor = Rose400,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "FATURAS CARTÃO (${horizonCount}M)",
                        value = "−${FinanceEngine.formatCurrency(totalInvoices)}",
                        valueColor = Purple400,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "CONTAS FIXAS (${horizonCount}M)",
                        value = "−${FinanceEngine.formatCurrency(totalFixed)}",
                        valueColor = Amber400,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MonthProjectionCard(
    projection: FutureMonthProjection,
    isCurrentMonth: Boolean,
    isSelectedMonth: Boolean,
    onSelectMonth: () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var detailTab by rememberSaveable { mutableStateOf("items") } // "items" or "categories"

    val isPositive = projection.projectedFreeBalance >= 0.0
    val balanceColor = if (isPositive) Emerald400 else Rose400

    val totalAvailable = projection.projectedIncome + projection.carryoverAmount
    val retentionRatio = if (totalAvailable > 0) {
        min(1f, max(0f, (projection.projectedFreeBalance / totalAvailable).toFloat()))
    } else 0f

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(
            1.dp,
            when {
                isSelectedMonth -> Emerald500.copy(alpha = 0.6f)
                isCurrentMonth -> Cyan400.copy(alpha = 0.5f)
                else -> DarkBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Mês + Badges + Status de Geração
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = projection.label,
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )

                        if (isCurrentMonth) {
                            Box(
                                modifier = Modifier
                                    .background(Emerald500.copy(alpha = 0.16f), CircleShape)
                                    .border(1.dp, Emerald500.copy(alpha = 0.4f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Mês Atual",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald400,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }

                        if (isSelectedMonth && !isCurrentMonth) {
                            Box(
                                modifier = Modifier
                                    .background(Cyan500.copy(alpha = 0.16f), CircleShape)
                                    .border(1.dp, Cyan500.copy(alpha = 0.4f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Em Exibição",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan400,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${projection.daysInMonth} dias",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // Tag de Lançamentos Gerados vs Simulado
                if (projection.isGenerated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(Emerald500.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Lançamentos Gerados",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Emerald400
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(Amber500.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Simulado",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Amber400
                        )
                    }
                }
            }

            // Caixas: Entradas vs Saídas com quantidade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkBackground.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Entradas (${projection.incomeCount})",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "+${FinanceEngine.formatCurrency(projection.projectedIncome)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkBackground.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Saídas (${projection.expenseCount})",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "−${FinanceEngine.formatCurrency(projection.projectedExpense)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Rose400
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = Rose400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Indicador de Sobra Remanescente do Mês Anterior ou Déficit Herdado
            if (projection.carryoverAmount > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Emerald500.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, Emerald500.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sobra do mês anterior injetada",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Emerald400
                        )
                    }
                    Text(
                        text = "+${FinanceEngine.formatCurrency(projection.carryoverAmount)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400
                    )
                }
            } else if (projection.previousMonthDeficit > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Rose500.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, Rose500.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = Rose400,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Déficit herdado do mês anterior",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Rose400
                        )
                    }
                    Text(
                        text = "−${FinanceEngine.formatCurrency(projection.previousMonthDeficit)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Rose400
                    )
                }
            }

            // Decomposição por Origem: Faturas vs Fixas vs Débito/Pix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OriginBox(
                    label = "Faturas",
                    amount = projection.invoiceExpense,
                    icon = Icons.Default.CreditCard,
                    accentColor = Purple400,
                    containerColor = Purple500.copy(alpha = 0.12f),
                    borderColor = Purple500.copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f)
                )
                OriginBox(
                    label = "Fixas",
                    amount = projection.fixedBillsExpense,
                    icon = Icons.Default.Receipt,
                    accentColor = Amber400,
                    containerColor = Amber500.copy(alpha = 0.12f),
                    borderColor = Amber500.copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f)
                )
                OriginBox(
                    label = "Pix/Débito",
                    amount = projection.variableAccountExpense,
                    icon = Icons.Default.Bolt,
                    accentColor = Cyan400,
                    containerColor = Cyan500.copy(alpha = 0.12f),
                    borderColor = Cyan500.copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f)
                )
            }

            // Saldo Livre Estimado & Gasto Seguro
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saldo Livre Estimado:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = FinanceEngine.formatCurrency(projection.projectedFreeBalance),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = balanceColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Gasto Seguro:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = projection.projectedDailyLimit?.let { "${FinanceEngine.formatCurrency(it)}/dia" } ?: "—",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Cyan400
                    )
                }
            }

            // Barra de Retenção de Saldo
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Retenção de Saldo",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${(retentionRatio * 100).toInt()}% livre",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )
                }
                LinearProgressIndicator(
                    progress = { retentionRatio },
                    color = balanceColor,
                    trackColor = DarkBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                )
            }

            // Raio-X Expandido: Itens & Categorias do Mês
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Raio-X de ${projection.label}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cyan400
                            )
                        }

                        // Seletor Itens vs Categorias
                        Row(
                            modifier = Modifier
                                .background(DarkBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            DetailTabButton(
                                label = "Itens (${projection.items.size})",
                                selected = detailTab == "items",
                                onClick = { detailTab = "items" }
                            )
                            DetailTabButton(
                                label = "Categorias (${projection.byCategory.size})",
                                selected = detailTab == "categories",
                                onClick = { detailTab = "categories" }
                            )
                        }
                    }

                    if (detailTab == "items") {
                        if (projection.items.isEmpty()) {
                            Text(
                                text = "Nenhum item previsto para este mês.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                projection.items.forEach { item ->
                                    ProjectedItemRow(item = item)
                                }
                            }
                        }
                    } else {
                        if (projection.byCategory.isEmpty()) {
                            Text(
                                text = "Nenhuma categoria de despesa neste mês.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                projection.byCategory.forEach { cat ->
                                    ProjectedCategoryRow(cat = cat)
                                }
                            }
                        }
                    }
                }
            }

            // Ações: Botão de Expandir Raio-X + Botão Abrir Mês
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Cyan400),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isExpanded) "Ocultar Raio-X" else "Ver Itens & Categorias",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = onSelectMonth,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Cyan500.copy(alpha = 0.18f),
                        contentColor = Cyan400
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isSelectedMonth) "Mês Ativo" else "Abrir Mês",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OriginBox(
    label: String,
    amount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = FinanceEngine.formatCurrency(amount),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) DarkSurfaceVariant else Color.Transparent
    val textCol = if (selected) TextPrimary else TextMuted
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textCol
        )
    }
}

@Composable
private fun ProjectedItemRow(item: ProjectedMonthItem) {
    val isIncome = item.type == TransactionType.INCOME

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .border(1.dp, DarkBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Badges de Origem
                when (item.origin) {
                    "invoice" -> {
                        OriginBadge(
                            text = "💳 ${item.cardName ?: "Cartão"}",
                            textColor = Purple400,
                            bgColor = Purple500.copy(alpha = 0.16f),
                            borderColor = Purple500.copy(alpha = 0.35f)
                        )
                    }
                    "fixed_bill" -> {
                        OriginBadge(
                            text = "🏠 Fixa",
                            textColor = Amber400,
                            bgColor = Amber500.copy(alpha = 0.16f),
                            borderColor = Amber500.copy(alpha = 0.35f)
                        )
                    }
                    "debit_pix" -> {
                        val methodLabel = if (item.paymentMethod == PaymentMethod.DEBIT) "Débito" else "Pix/Boleto"
                        OriginBadge(
                            text = "⚡ $methodLabel",
                            textColor = Cyan400,
                            bgColor = Cyan500.copy(alpha = 0.16f),
                            borderColor = Cyan500.copy(alpha = 0.35f)
                        )
                    }
                    "income" -> {
                        OriginBadge(
                            text = "💰 Receita",
                            textColor = Emerald400,
                            bgColor = Emerald500.copy(alpha = 0.16f),
                            borderColor = Emerald500.copy(alpha = 0.35f)
                        )
                    }
                }

                item.installmentInfo?.let { inst ->
                    OriginBadge(
                        text = inst,
                        textColor = TextSecondary,
                        bgColor = DarkSurfaceVariant,
                        borderColor = DarkBorder
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = buildString {
                    append(item.category)
                    item.subcategory?.let { append(" • $it") }
                },
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = (if (isIncome) "+" else "−") + FinanceEngine.formatCurrency(item.myImpact),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) Emerald400 else TextPrimary
        )
    }
}

@Composable
private fun ProjectedCategoryRow(cat: ProjectedCategoryItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .border(1.dp, DarkBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = cat.category,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val hasSub = cat.invoiceAmount > 0.0 || (cat.fixedBillAmount + cat.accountAmount) > 0.0
            if (hasSub) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cat.invoiceAmount > 0.0) {
                        Text(
                            text = "💳 Fatura: ${FinanceEngine.formatCurrency(cat.invoiceAmount)}",
                            fontSize = 10.sp,
                            color = Purple400
                        )
                    }
                    if ((cat.fixedBillAmount + cat.accountAmount) > 0.0) {
                        Text(
                            text = "🏦 Fixas/Extrato: ${FinanceEngine.formatCurrency(cat.fixedBillAmount + cat.accountAmount)}",
                            fontSize = 10.sp,
                            color = Amber400
                        )
                    }
                }
            }
        }

        Text(
            text = FinanceEngine.formatCurrency(cat.amount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun OriginBadge(
    text: String,
    textColor: Color,
    bgColor: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}
