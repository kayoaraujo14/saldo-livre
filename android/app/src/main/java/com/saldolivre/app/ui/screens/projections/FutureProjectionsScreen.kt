package com.saldolivre.app.ui.screens.projections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.FutureMonthProjection
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
                                imageVector = Icons.Default.QueryStats,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Projeção de Fluxo",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Horizonte preditivo de 6 meses",
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Hero Card Consolidado (6 Meses)
            item {
                ProjectionsHeroBanner(
                    averageFreeBalance = uiState.averageFreeBalance,
                    totalIncome = uiState.totalProjectedIncome,
                    totalExpense = uiState.totalProjectedExpense
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

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ProjectionsHeroBanner(
    averageFreeBalance: Double,
    totalIncome: Double,
    totalExpense: Double
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
                    text = "SALDO LIVRE MÉDIO PROJETADO (6M)",
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
                text = FinanceEngine.formatCurrency(averageFreeBalance),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "ENTRADAS (6 MESES)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = FinanceEngine.formatCurrency(totalIncome),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Emerald400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "SAÍDAS (6 MESES)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = FinanceEngine.formatCurrency(totalExpense),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Rose400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthProjectionCard(
    projection: FutureMonthProjection,
    isCurrentMonth: Boolean,
    isSelectedMonth: Boolean,
    onSelectMonth: () -> Unit
) {
    val isPositive = projection.projectedFreeBalance >= 0.0
    val balanceColor = if (isPositive) Emerald400 else Rose400

    val retentionRatio = if (projection.projectedIncome > 0) {
        min(1f, max(0f, (projection.projectedFreeBalance / projection.projectedIncome).toFloat()))
    } else 0f

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(
            1.dp,
            when {
                isSelectedMonth -> Cyan400.copy(alpha = 0.6f)
                isCurrentMonth -> Emerald500.copy(alpha = 0.45f)
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
            // Header: Mês + Badges + Saldo Livre Projetado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = buildString {
                            append("${projection.daysInMonth} dias • ")
                            projection.projectedDailyLimit?.let {
                                append("Teto diário: ${FinanceEngine.formatCurrency(it)}/dia")
                            }
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FinanceEngine.formatCurrency(projection.projectedFreeBalance),
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = balanceColor
                    )
                    Text(
                        text = "Saldo Livre Previsto",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            // Caixas: Receitas vs Comprometido
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
                            text = "Receitas (${projection.incomeCount})",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Text(
                            text = FinanceEngine.formatCurrency(projection.projectedIncome),
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
                            text = FinanceEngine.formatCurrency(projection.projectedExpense),
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

            // Barra de Retenção + Botão Abrir Mês
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Margem Livre da Renda",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${(retentionRatio * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
