package com.saldolivre.app.ui.screens.dashboard.budgets

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt
@Composable
private fun BudgetAiCoachCard(
    insights: List<BudgetInsight>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onAction: (BudgetInsightAction) -> Unit
) {
    val fromAi = insights.any { it.fromAi }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Purple500.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, Purple400.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Purple400,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Assistente IA",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .background(Purple500.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = if (fromAi) "Gemini" else "Dicas rÃ¡pidas",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple400,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isGenerating) { onGenerate() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Purple400
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Purple400,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = when {
                        isGenerating -> "Analisandoâ€¦"
                        fromAi -> "Atualizar"
                        else -> "Analisar com Gemini"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Purple400,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        insights.forEach { insight ->
            val color = insightColor(insight)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(color, RoundedCornerShape(2.dp))
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = insight.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = insight.message,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = TextSecondary
                    )
                    insight.action?.let { action ->
                        Box(
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Purple500.copy(alpha = 0.16f))
                                .border(1.dp, Purple400.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable { onAction(action) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = action.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Purple400
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBudgetsSectionCard(
    report: CategoryBudgetReport,
    rebalance: BudgetRebalanceResult,
    onOpenConfig: () -> Unit,
    onOpenRebalance: () -> Unit,
    onQuickApplyAiBudgets: () -> Unit,
    onAdjustBudget: ((category: String, delta: Double) -> Unit)? = null,
    insights: List<BudgetInsight> = emptyList(),
    isGeneratingInsights: Boolean = false,
    onGenerateAiInsights: () -> Unit = {},
    onInsightAction: (BudgetInsightAction) -> Unit = {},
    onAskAi: (String) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(EnvelopeFilterMode.ALL) }
    var expandedCategoryKey by remember { mutableStateOf<String?>(null) }

    val sortedItems = remember(report.items) {
        report.items.sortedWith(
            compareBy<CategoryBudgetStatus>({ envelopeSeverityRank(it) }, { -it.totalPercentage })
        )
    }
    val allCount = sortedItems.size
    val isAlert: (CategoryBudgetStatus) -> Boolean = {
        it.status != CategoryBudgetAlertStatus.OK ||
            it.burnRateStatus != CategoryBudgetPaceStatus.NORMAL ||
            it.badgeLabel in listOf("Estourado", "Vai Estourar", "Quase no Limite", "Ritmo RÃ¡pido")
    }
    val alertsCount = remember(sortedItems) { sortedItems.count(isAlert) }

    val filteredItems = remember(sortedItems, filterMode) {
        when (filterMode) {
            EnvelopeFilterMode.ALL -> sortedItems
            EnvelopeFilterMode.ALERTS -> sortedItems.filter(isAlert)
            EnvelopeFilterMode.VARIABLE -> sortedItems.filter { !it.isFixedCategory && !it.category.contains("investimento", ignoreCase = true) }
            EnvelopeFilterMode.FIXED -> sortedItems.filter { it.isFixedCategory }
        }
    }
    val visibleItems = if (isExpanded) filteredItems else filteredItems.take(5)

    val statusColor = when (report.status) {
        CategoryBudgetAlertStatus.DANGER -> Rose400
        CategoryBudgetAlertStatus.WARNING -> Amber400
        CategoryBudgetAlertStatus.OK -> Emerald400
    }
    val statusText = when (report.status) {
        CategoryBudgetAlertStatus.DANGER -> "AtenÃ§Ã£o aos tetos"
        CategoryBudgetAlertStatus.WARNING -> "Ritmo acelerado"
        CategoryBudgetAlertStatus.OK -> "Dentro da meta"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // â”€â”€ CabeÃ§alho: tÃ­tulo + ajuda + aÃ§Ãµes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Envelopes OrÃ§amentÃ¡rios",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = if (report.totalBudgeted > 0.0) {
                                "Um teto de gasto para cada categoria do mÃªs"
                            } else {
                                "Defina tetos por categoria e acompanhe quanto ainda pode gastar"
                            },
                            fontSize = 11.5.sp,
                            color = TextMuted,
                            maxLines = 2,
                            lineHeight = 15.sp
                        )
                    }

                    if (report.totalBudgeted > 0.0) {
                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Como funciona",
                        tint = if (showHelp) TextPrimary else TextMuted,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { showHelp = !showHelp }
                            .padding(2.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showHelp,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Como ler esta tela", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("â€¢ Envelope Ã© o teto de gasto de uma categoria neste mÃªs.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
                        Text("â€¢ Barra cheia = jÃ¡ pago. Barra clara = contas e faturas que ainda vÃ£o vencer.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
                        Text("â€¢ â€œAinda pode gastarâ€ = tetos âˆ’ pago âˆ’ a vencer.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Emerald400, CircleShape)); Text("ok", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Amber400, CircleShape)); Text("atenÃ§Ã£o", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Rose400, CircleShape)); Text("estourou", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Purple400, CircleShape)); Text("IA", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        Text("â€¢ Toque em uma categoria para ajustar o teto ou perguntar Ã  IA.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onQuickApplyAiBudgets,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Purple500.copy(alpha = 0.85f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (report.totalBudgeted > 0.0) "Planejar com IA" else "Criar com IA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenConfig,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (report.totalBudgeted > 0.0) "Ajustar tetos" else "Definir tetos",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // â”€â”€ Resumo: quanto ainda pode gastar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (report.totalBudgeted > 0.0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "AINDA PODE GASTAR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = brl(report.totalRealRemaining),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (report.totalRealRemaining > 0.0) Emerald400 else Rose400
                            )
                            Text(
                                text = "dentro dos tetos, atÃ© o fim do mÃªs",
                                fontSize = 10.5.sp,
                                color = TextMuted
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TETO TOTAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = brl(report.totalBudgeted),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    val globalSpentRatio = (report.totalSpent / report.totalBudgeted).toFloat()
                    val globalPendingRatio = (report.totalPending / report.totalBudgeted).toFloat()
                    DualLayerProgressBar(
                        spentRatio = globalSpentRatio,
                        pendingRatio = globalPendingRatio,
                        statusColor = statusColor,
                        height = 10.dp
                    )

                    // Legenda Ãºnica da barra (as categorias abaixo nÃ£o repetem esse texto)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                            Text(
                                text = "Pago ${brl(report.totalSpent)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColor.copy(alpha = 0.35f), CircleShape)
                                    .border(0.5.dp, statusColor, CircleShape)
                            )
                            Text(
                                text = "A vencer ${brl(report.totalPending)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }

                    // Meta de aporte (regra 50/30/20) isolada do consumo
                    val goal = if (report.investmentGoal > 0.0) report.investmentGoal else report.investmentTarget
                    if (goal > 0.0 || report.investmentSpent > 0.0) {
                        val ratio = if (goal > 0.0) (report.investmentSpent / goal).toFloat().coerceIn(0f, 1f) else 0f
                        val pct = if (goal > 0.0) (report.investmentSpent / goal * 100.0).roundToInt() else 0
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .border(1.dp, InvestBlue.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = InvestBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Meta de aporte Â· 20% da renda",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InvestBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "$pct%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InvestBlue,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = brl(report.investmentSpent),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "de ${brl(goal)} neste mÃªs",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                            }
                            DualLayerProgressBar(
                                spentRatio = ratio,
                                pendingRatio = 0f,
                                statusColor = InvestBlue,
                                height = 4.dp
                            )
                        }
                    }
                }
            }

            // â”€â”€ Assistente IA: insights acionÃ¡veis â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (insights.isNotEmpty()) {
                BudgetAiCoachCard(
                    insights = insights,
                    isGenerating = isGeneratingInsights,
                    onGenerate = onGenerateAiInsights,
                    onAction = onInsightAction
                )
            }

            // â”€â”€ Lista de envelopes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (report.items.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (mode in EnvelopeFilterMode.entries) {
                        val isSelected = filterMode == mode
                        val labelText = when (mode) {
                            EnvelopeFilterMode.ALL -> "Todos ($allCount)"
                            EnvelopeFilterMode.ALERTS -> "Em alerta ($alertsCount)"
                            EnvelopeFilterMode.VARIABLE -> "VariÃ¡veis"
                            EnvelopeFilterMode.FIXED -> "Fixos"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    if (isSelected) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { filterMode = mode }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = labelText,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visibleItems.isEmpty()) {
                        Text(
                            text = "Nenhuma categoria neste filtro.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    for (item in visibleItems) {
                        val badgeColor = getCategoryBadgeColor(item.badgeLabel)
                        val isThisCardExpanded = expandedCategoryKey == item.category
                        val isOver = item.limit > 0.0 && (item.committedAmount - item.limit) >= 0.01

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (isThisCardExpanded) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    expandedCategoryKey = if (isThisCardExpanded) null else item.category
                                }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Linha 1: nome + selo de status + seta
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.category,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = getCategoryBadgeText(item.badgeLabel),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Icon(
                                    imageVector = if (isThisCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isThisCardExpanded) "Recolher" else "Ajustar teto",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Linha 2: comprometido de teto  |  sobra / acima
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = brl(item.committedAmount),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (item.limit > 0.0) "de ${brl(item.limit)}" else "(sem teto)",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(bottom = 1.dp)
                                    )
                                }
                                val rightText = when {
                                    item.limit <= 0.0 -> ""
                                    isOver -> "${brl(item.committedAmount - item.limit)} acima"
                                    else -> "sobra ${brl(item.realRemaining)}"
                                }
                                if (rightText.isNotEmpty()) {
                                    Text(
                                        text = rightText,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            val itemSpentRatio = if (item.limit > 0.0) (item.spentAmount / item.limit).toFloat() else 0f
                            val itemPendingRatio = if (item.limit > 0.0) (item.pendingAmount / item.limit).toFloat() else 0f
                            DualLayerProgressBar(
                                spentRatio = itemSpentRatio,
                                pendingRatio = itemPendingRatio,
                                statusColor = badgeColor,
                                height = 6.dp
                            )

                            val detail = when {
                                item.pendingAmount > 0.0 && item.spentAmount <= 0.0 -> "Nada pago ainda Â· ${brl(item.pendingAmount)} a vencer"
                                item.pendingAmount > 0.0 -> "Pago ${brl(item.spentAmount)} Â· A vencer ${brl(item.pendingAmount)}"
                                item.spentAmount > 0.0 -> "Pago ${brl(item.spentAmount)}"
                                else -> "Sem gastos neste mÃªs"
                            }
                            Text(
                                text = detail,
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            AnimatedVisibility(
                                visible = isThisCardExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (item.limit > 0.0) "Teto atual ${brl(item.limit)} Â· ajuste rÃ¡pido:" else "Ainda sem teto Â· crie um:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(-100.0, -50.0, 50.0, 100.0).forEach { delta ->
                                            val label = if (delta > 0) "+${delta.toInt()}" else "${delta.toInt()}"
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                                                    .clickable { onAdjustBudget?.invoke(item.category, delta) }
                                                    .padding(vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                .clickable { onOpenConfig() }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Editar valor", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Purple500.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                                .border(1.dp, Purple400.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onAskAi(
                                                        "Analise meu envelope de ${item.category}: teto ${brl(item.limit)}, pago ${brl(item.spentAmount)}, a vencer ${brl(item.pendingAmount)}. O que posso fazer para fechar o mÃªs dentro do orÃ§amento?"
                                                    )
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Purple400, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Perguntar Ã  IA", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Purple400)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredItems.size > 5) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isExpanded) "Mostrar menos" else "Ver mais ${filteredItems.size - 5} categorias",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
