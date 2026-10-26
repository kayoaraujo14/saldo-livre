package com.saldolivre.app.ui.screens.projections

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.FutureMonthProjection
import com.saldolivre.app.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutureProjectionsScreen(
    viewModel: FutureProjectionsViewModel,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Cyan500.copy(alpha = 0.2f))
                                .border(1.dp, Cyan500.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Projeção de Fluxo de Caixa",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Próximos 6 meses simulados",
                                style = Typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp,
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
            // Consolidated Summary Card
            item(key = "projections_consolidated_banner", contentType = "projections_consolidated_banner") {
                ProjectionsConsolidatedBanner(
                    averageFreeBalance = uiState.averageFreeBalance,
                    totalIncome = uiState.totalProjectedIncome,
                    totalExpense = uiState.totalProjectedExpense
                )
            }

            // Month Projections List
            items(
                items = uiState.projections,
                key = { it.monthKey },
                contentType = { "month_projection_card" }
            ) { projection ->
                val isSelected = projection.monthKey == uiState.selectedMonth
                val isCurrent = projection.monthKey == uiState.currentMonth

                MonthProjectionCard(
                    projection = projection,
                    isSelected = isSelected,
                    isCurrent = isCurrent,
                    onSelectMonth = {
                        viewModel.selectMonth(projection.monthKey)
                        onNavigateBack()
                    }
                )
            }

            item(key = "projections_disclaimer", contentType = "projections_disclaimer") {
                Surface(
                    color = DarkSurface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "* As previsões são calculadas a partir do salário cadastrado, contas fixas ativas e parcelas de cartões com vencimento futuro já agendadas.",
                        style = Typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            item(key = "projections_bottom_spacer", contentType = "projections_bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProjectionsConsolidatedBanner(
    averageFreeBalance: Double,
    totalIncome: Double,
    totalExpense: Double
) {
    val isPositive = averageFreeBalance >= 0
    val formattedAvg = String.format(Locale("pt", "BR"), "R$ %,.2f", averageFreeBalance)
    val formattedIncome = String.format(Locale("pt", "BR"), "R$ %,.2f", totalIncome)
    val formattedExpense = String.format(Locale("pt", "BR"), "R$ %,.2f", totalExpense)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isPositive) Emerald500.copy(alpha = 0.35f) else Rose500.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MÉDIA PROJETADA (6 MESES)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Surface(
                    color = if (isPositive) Emerald500.copy(alpha = 0.15f) else Rose500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isPositive) "Superávit Saudável" else "Atenção ao Limite",
                        color = if (isPositive) Emerald400 else Rose400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Saldo Livre Médio / Mês",
                        style = Typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedAvg,
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPositive) Emerald400 else Rose400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = DarkBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Entradas (6m)",
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "+$formattedIncome",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Total Saídas (6m)",
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "-$formattedExpense",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
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
    isSelected: Boolean,
    isCurrent: Boolean,
    onSelectMonth: () -> Unit
) {
    val isPositive = projection.projectedFreeBalance >= 0
    val formattedIncome = remember(projection.projectedIncome) { FinanceEngine.formatCurrency(projection.projectedIncome) }
    val formattedExpense = remember(projection.projectedExpense) { FinanceEngine.formatCurrency(projection.projectedExpense) }
    val formattedFreeBalance = remember(projection.projectedFreeBalance) { FinanceEngine.formatCurrency(projection.projectedFreeBalance) }
    val formattedDailyLimit = remember(projection.projectedDailyLimit) {
        projection.projectedDailyLimit?.let { FinanceEngine.formatCurrency(it) }
    }

    val retentionPct = if (projection.projectedIncome > 0) {
        ((projection.projectedFreeBalance / projection.projectedIncome) * 100).toInt().coerceIn(0, 100)
    } else 0

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Emerald400.copy(alpha = 0.6f) else DarkBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Month Label + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = projection.label,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isCurrent) {
                        Surface(
                            color = Emerald500.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Mês Atual",
                                color = Emerald400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isSelected && !isCurrent) {
                        Surface(
                            color = Cyan500.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Ativo",
                                color = Cyan400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (projection.isGenerated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                        Text(text = "Gerados", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Emerald400, maxLines = 1)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Amber400, modifier = Modifier.size(14.dp))
                        Text(text = "Simulado", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                    }
                }
            }

            // Grid: Entradas Previstas vs Saídas Previstas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Incomes
                Surface(
                    color = DarkBackground.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Entradas Previstas",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "+$formattedIncome",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "${projection.incomeCount} fonte(s)",
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Expenses
                Surface(
                    color = DarkBackground.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = Rose400, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Saídas Previstas",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "-$formattedExpense",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "${projection.expenseCount} conta(s)",
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Projected Free Balance & Daily Safe Limit
            Surface(
                color = DarkBackground.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Saldo Livre Estimado:",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formattedFreeBalance,
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isPositive) Emerald400 else Rose400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Gasto Seguro:",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formattedDailyLimit?.let { "$it/dia" } ?: "—",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan400,
                            maxLines = 1
                        )
                    }
                }
            }

            // Retention Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Retenção de Saldo Livre",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$retentionPct% livre",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }

                LinearProgressIndicator(
                    progress = { (retentionPct / 100f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isPositive) Emerald400 else Rose400,
                    trackColor = DarkSurfaceVariant
                )
            }

            HorizontalDivider(color = DarkBorder)

            // Card Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Cyan400, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (projection.isGenerated) "Lançamentos integrados" else "Lançamento automático",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onSelectMonth,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = TextPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Text(
                        text = "Abrir Mês",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}
