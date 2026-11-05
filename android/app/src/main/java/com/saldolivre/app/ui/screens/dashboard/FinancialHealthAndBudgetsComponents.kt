package com.saldolivre.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

private val BUDGETABLE_EXPENSE_CATEGORIES = listOf(
    "Alimentação",
    "Moradia",
    "Transporte",
    "Saúde",
    "Lazer & Entretenimento",
    "Compras & Estilo de Vida",
    "Assinaturas & Serviços",
    "Educação & Desenvolvimento",
    "Cuidados Pessoais & Estética",
    "Família & Pets",
    "Investimentos",
    "Financiamentos & Dívidas",
    "Impostos & Tributos",
    "Doações & Presentes",
    "Outros"
)

private enum class EnvelopeFilterMode(val label: String) {
    ALL("Todos"),
    ALERTS("Em Alerta"),
    VARIABLE("Variáveis"),
    FIXED("Fixos")
}

@Composable
fun DualLayerProgressBar(
    spentRatio: Float,
    pendingRatio: Float,
    statusColor: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 6.dp
) {
    val clampedSpent = spentRatio.coerceIn(0f, 1f)
    val clampedPending = pendingRatio.coerceIn(0f, (1f - clampedSpent).coerceAtLeast(0f))
    val remainingEmpty = (1f - (clampedSpent + clampedPending)).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (clampedSpent > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(clampedSpent)
                    .background(statusColor)
            )
        }
        if (clampedPending > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(clampedPending)
                    .background(statusColor.copy(alpha = 0.35f))
                    .border(BorderStroke(0.5.dp, statusColor.copy(alpha = 0.6f)))
            )
        }
        if (remainingEmpty > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(remainingEmpty)
                    .background(Color.Transparent)
            )
        }
    }
}

private val InvestBlue = Color(0xFF60A5FA)

private fun brl(value: Double): String = FinanceEngine.formatCurrency(value)

/**
 * Paleta semântica ÚNICA da tela de orçamentos:
 * verde = dentro do teto · âmbar = atenção · vermelho = estourou · cinza = sem teto · roxo = IA.
 */
fun getCategoryBadgeColor(badgeLabel: String): Color {
    return when (badgeLabel) {
        "Estourado", "Vai Estourar" -> Rose400
        "Quase no Limite", "No Limite", "Ritmo Rápido" -> Amber400
        "Definir Teto" -> TextMuted
        else -> Emerald400
    }
}

/** Texto curto e autoexplicativo exibido no selo (os rótulos internos do motor continuam intactos). */
fun getCategoryBadgeText(badgeLabel: String): String {
    return when (badgeLabel) {
        "Previsto no Teto", "Fixo em Dia", "Quitado / Dentro do Previsto", "Sob Controle" -> "Dentro do teto"
        "Definir Teto" -> "Sem teto"
        else -> badgeLabel
    }
}

private fun envelopeSeverityRank(item: CategoryBudgetStatus): Int = when {
    item.limit > 0.0 && (item.committedAmount - item.limit) >= 0.01 -> 0
    item.status == CategoryBudgetAlertStatus.DANGER -> 0
    item.status == CategoryBudgetAlertStatus.WARNING -> 1
    else -> 2
}

private fun insightColor(insight: BudgetInsight): Color = when (insight.severity) {
    BudgetInsightSeverity.DANGER -> Rose400
    BudgetInsightSeverity.ATTENTION -> Amber400
    BudgetInsightSeverity.INFO -> if (insight.id == "all_good") Emerald400 else TextSecondary
}

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
                        text = if (fromAi) "Gemini" else "Dicas rápidas",
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
                        isGenerating -> "Analisando…"
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
            it.badgeLabel in listOf("Estourado", "Vai Estourar", "Quase no Limite", "Ritmo Rápido")
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
        CategoryBudgetAlertStatus.DANGER -> "Atenção aos tetos"
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
            // ── Cabeçalho: título + ajuda + ações ─────────────────────────────
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
                            text = "Envelopes Orçamentários",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = if (report.totalBudgeted > 0.0) {
                                "Um teto de gasto para cada categoria do mês"
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
                        Text("• Envelope é o teto de gasto de uma categoria neste mês.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
                        Text("• Barra cheia = já pago. Barra clara = contas e faturas que ainda vão vencer.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
                        Text("• “Ainda pode gastar” = tetos − pago − a vencer.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Emerald400, CircleShape)); Text("ok", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Amber400, CircleShape)); Text("atenção", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Rose400, CircleShape)); Text("estourou", fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(Purple400, CircleShape)); Text("IA", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        Text("• Toque em uma categoria para ajustar o teto ou perguntar à IA.", fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
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

            // ── Resumo: quanto ainda pode gastar ───────────────────────────────
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
                                text = "dentro dos tetos, até o fim do mês",
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

                    // Legenda única da barra (as categorias abaixo não repetem esse texto)
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
                                        text = "Meta de aporte · 20% da renda",
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
                                    text = "de ${brl(goal)} neste mês",
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

            // ── Assistente IA: insights acionáveis ─────────────────────────────
            if (insights.isNotEmpty()) {
                BudgetAiCoachCard(
                    insights = insights,
                    isGenerating = isGeneratingInsights,
                    onGenerate = onGenerateAiInsights,
                    onAction = onInsightAction
                )
            }

            // ── Lista de envelopes ─────────────────────────────────────────────
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
                            EnvelopeFilterMode.VARIABLE -> "Variáveis"
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
                                item.pendingAmount > 0.0 && item.spentAmount <= 0.0 -> "Nada pago ainda · ${brl(item.pendingAmount)} a vencer"
                                item.pendingAmount > 0.0 -> "Pago ${brl(item.spentAmount)} · A vencer ${brl(item.pendingAmount)}"
                                item.spentAmount > 0.0 -> "Pago ${brl(item.spentAmount)}"
                                else -> "Sem gastos neste mês"
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
                                        text = if (item.limit > 0.0) "Teto atual ${brl(item.limit)} · ajuste rápido:" else "Ainda sem teto · crie um:",
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
                                                        "Analise meu envelope de ${item.category}: teto ${brl(item.limit)}, pago ${brl(item.spentAmount)}, a vencer ${brl(item.pendingAmount)}. O que posso fazer para fechar o mês dentro do orçamento?"
                                                    )
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Purple400, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Perguntar à IA", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Purple400)
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

@Composable
fun CompactFinancialHealthCard(
    health: FinancialHealthScore,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColor = when (health.classification) {
        FinancialHealthClassification.EXCELLENT -> Emerald400
        FinancialHealthClassification.HEALTHY -> Cyan400
        FinancialHealthClassification.WARNING -> Amber400
        FinancialHealthClassification.CRITICAL -> Rose400
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { (health.score / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = healthColor,
                        trackColor = Color.White.copy(alpha = 0.10f),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "${health.score}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SAÚDE FINANCEIRA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .background(healthColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = health.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = healthColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = health.actionableTips.firstOrNull() ?: health.description,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = TextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Raio-X",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Emerald400
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun HealthPillarColumn(
    title: String,
    scoreText: String,
    ratio: Float,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val barColor = when {
        clampedRatio >= 0.70f -> Emerald400
        clampedRatio >= 0.40f -> Amber400
        else -> Rose400
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
        Text(
            text = scoreText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clampedRatio)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
        Text(
            text = subtitle,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun FullFinancialHealthCard(
    health: FinancialHealthScore,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColor = when (health.classification) {
        FinancialHealthClassification.EXCELLENT -> Emerald400
        FinancialHealthClassification.HEALTHY -> Cyan400
        FinancialHealthClassification.WARNING -> Amber400
        FinancialHealthClassification.CRITICAL -> Rose400
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { (health.score / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            color = healthColor,
                            trackColor = Color.White.copy(alpha = 0.10f),
                            strokeWidth = 3.5.dp
                        )
                        Text(
                            text = "${health.score}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SAÚDE FINANCEIRA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = TextSecondary
                            )
                            Box(
                                modifier = Modifier
                                    .background(healthColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = health.label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = healthColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = health.actionableTips.firstOrNull() ?: health.description,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = TextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "Raio-X →",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Emerald400
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HealthPillarColumn(
                    title = "POUPANÇA",
                    scoreText = "${health.savingsScore}/30 pts",
                    ratio = health.savingsScore / 30f,
                    subtitle = "${health.savingsRatePercentage}% investido",
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                HealthPillarColumn(
                    title = "CUSTO VIDA",
                    scoreText = "${health.spendingLimitScore}/30 pts",
                    ratio = health.spendingLimitScore / 30f,
                    subtitle = "${health.spendingRatioPercentage}% renda",
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                HealthPillarColumn(
                    title = "RESERVA",
                    scoreText = "${health.emergencyReserveScore}/25 pts",
                    ratio = health.emergencyReserveScore / 25f,
                    subtitle = "${health.emergencyCoverageMonths}m reserva",
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                HealthPillarColumn(
                    title = "ENVELOPES",
                    scoreText = "${health.envelopeScore}/15 pts",
                    ratio = health.envelopeScore / 15f,
                    subtitle = if (health.budgetConfiguredCount > 0) "${health.budgetAdherenceScore}% no teto" else "Sem teto",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun FinancialHealthDialog(
    health: FinancialHealthScore,
    summary: MonthSummary,
    monthKey: String,
    diagnosis: FinancialHealthAiDiagnosis? = null,
    isGeneratingDiagnosis: Boolean = false,
    onGenerateDiagnosis: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onOpenInvestments: () -> Unit,
    onOpenBudgets: () -> Unit,
    onAskCopilot: (String) -> Unit
) {
    val healthColor = when (health.classification) {
        FinancialHealthClassification.EXCELLENT -> Emerald400
        FinancialHealthClassification.HEALTHY -> Cyan400
        FinancialHealthClassification.WARNING -> Amber400
        FinancialHealthClassification.CRITICAL -> Rose400
    }
    val livingCost = if (health.monthlyLivingCost > 0.0) health.monthlyLivingCost else (summary.paid + summary.committed)
    val target6m = livingCost * 6.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Emerald500.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Diagnóstico de Saúde Financeira",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Avaliação integrada dos 4 pilares em ${FinanceEngine.formatMonthLabel(monthKey)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextSecondary)
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Score Summary Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, healthColor.copy(alpha = 0.30f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { (health.score / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxSize(),
                                        color = healthColor,
                                        trackColor = Color.White.copy(alpha = 0.10f),
                                        strokeWidth = 5.dp
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${health.score}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "/100",
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Status: ${health.label} (${health.score} pts)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = healthColor
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = health.description,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onAskCopilot("Faça um diagnóstico completo da minha Saúde Financeira (4 pilares) e dos meus Envelopes Orçamentários deste mês, indicando um plano prático para subir meu Score.")
                                    },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Analisar no Copiloto IA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenBudgets()
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = Emerald400, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Envelopes", fontSize = 11.sp, color = TextPrimary)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenInvestments()
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Cyan400, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Carteira & Reserva", fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    // Resumo Executivo do Mês
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text("Entrada Prevista", fontSize = 9.sp, color = TextMuted)
                                Text(
                                    text = FinanceEngine.formatCurrency(summary.income),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text("Custo de Vida Líquido", fontSize = 9.sp, color = TextMuted)
                                Text(
                                    text = FinanceEngine.formatCurrency(livingCost),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan400
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text("Investido no Mês", fontSize = 9.sp, color = TextMuted)
                                Text(
                                    text = FinanceEngine.formatCurrency(summary.totalInvested),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald400
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text("Saldo Livre Real", fontSize = 9.sp, color = TextMuted)
                                Text(
                                    text = FinanceEngine.formatCurrency(summary.freeBalance),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.freeBalance >= 0.0) Emerald400 else Rose400
                                )
                            }
                        }
                    }

                    // Plano de Ação Gemini AI
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, Purple400.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Purple400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Plano de Ação Gemini AI ✨",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                if (onGenerateDiagnosis != null) {
                                    OutlinedButton(
                                        onClick = onGenerateDiagnosis,
                                        enabled = !isGeneratingDiagnosis,
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Purple400.copy(alpha = 0.5f))
                                    ) {
                                        if (isGeneratingDiagnosis) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                color = Purple400,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Gerando...", fontSize = 10.sp, color = Purple400)
                                        } else {
                                            Text(
                                                if (diagnosis != null) "Atualizar" else "Diagnosticar",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Purple400
                                            )
                                        }
                                    }
                                }
                            }

                            if (isGeneratingDiagnosis) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Purple400,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Gerando recomendações práticas com o Gemini...", fontSize = 11.sp, color = TextSecondary)
                                }
                            } else if (diagnosis != null) {
                                Text(
                                    text = diagnosis.overallAssessment,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = TextPrimary
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (item in diagnosis.actionPlan) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DarkBackground.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(text = item, fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Toque em 'Diagnosticar' para obter um plano de ação estratégico gerado pelo Gemini com foco em elevar seu Score.",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    // 4 Pillars Breakdown (Total = 100 pts)
                    PillarRowCard(
                        title = "1. Taxa de Poupança & Investimento",
                        scoreText = "${health.savingsScore}/30 pts",
                        progress = (health.savingsScore / 30f).coerceIn(0f, 1f),
                        color = Emerald400,
                        line1 = "Investido no mês: ${FinanceEngine.formatCurrency(summary.totalInvested)} (${health.savingsRatePercentage}%)",
                        line2 = "Meta saudável: >= 20% da renda"
                    )

                    PillarRowCard(
                        title = "2. Custo de Vida / Comprometimento",
                        scoreText = "${health.spendingLimitScore}/30 pts",
                        progress = (health.spendingLimitScore / 30f).coerceIn(0f, 1f),
                        color = Cyan400,
                        line1 = "Custo líquido (sem aportes): ${FinanceEngine.formatCurrency(livingCost)} (${health.spendingRatioPercentage}%)",
                        line2 = "Teto saudável recomendado: Até 70% da renda"
                    )

                    PillarRowCard(
                        title = "3. Reserva de Emergência",
                        scoreText = "${health.emergencyReserveScore}/25 pts",
                        progress = (health.emergencyReserveScore / 25f).coerceIn(0f, 1f),
                        color = Amber400,
                        line1 = "Cobertura atual: ${health.emergencyCoverageMonths} meses do custo de vida",
                        line2 = "Meta de blindagem (6 meses): ${FinanceEngine.formatCurrency(target6m)}"
                    )

                    PillarRowCard(
                        title = "4. Controle de Envelopes",
                        scoreText = "${health.envelopeScore}/15 pts",
                        progress = (health.envelopeScore / 15f).coerceIn(0f, 1f),
                        color = if (health.budgetOverspentCount > 0) Rose400 else Purple400,
                        line1 = "Aderência dos envelopes variáveis",
                        line2 = if (health.budgetConfiguredCount == 0) {
                            "Configure tetos para pontuar até 15 pts"
                        } else if (health.budgetOverspentCount == 0) {
                            "Todos os envelopes sob controle (+15 pts)!"
                        } else {
                            "${health.budgetOverspentCount} envelope(s) com estouro no mês"
                        }
                    )

                    // Actionable Coach Tips
                    if (health.actionableTips.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "ALAVANCAS PRÁTICAS PARA SUBIR SEU SCORE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald400
                                    )
                                }

                                for (tip in health.actionableTips) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = tip,
                                            fontSize = 11.sp,
                                            color = TextPrimary
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
}

@Composable
private fun PillarRowCard(
    title: String,
    scoreText: String,
    progress: Float,
    color: Color,
    line1: String,
    line2: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = scoreText,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Text(text = line1, fontSize = 11.sp, color = TextSecondary)
        Text(text = line2, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
fun ManageBudgetsDialog(
    initialTab: Int = 0,
    currentBudgets: Map<String, Double>,
    report: CategoryBudgetReport,
    suggestedBudgets: SuggestedBudgetsResult,
    rebalance: BudgetRebalanceResult,
    smartBudgetPlan: SmartBudgetPlan? = null,
    isGeneratingSmartBudget: Boolean = false,
    onGenerateSmartBudget: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaveBudgets: (Map<String, Double>) -> Unit,
    onApplyRebalance: (String?, String?, Double?) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(initialTab) }
    val budgetInputs = remember(currentBudgets) {
        mutableStateMapOf<String, String>().apply {
            val normalizedCurrent = mutableMapOf<String, Double>()
            for ((rawCat, valAmt) in currentBudgets) {
                if (valAmt > 0.0) {
                    val norm = FinanceEngine.normalizeBudgetCategory(rawCat)
                    normalizedCurrent[norm] = FinanceEngine.round2((normalizedCurrent[norm] ?: 0.0) + valAmt)
                }
            }
            for (cat in BUDGETABLE_EXPENSE_CATEGORIES) {
                val norm = FinanceEngine.normalizeBudgetCategory(cat)
                val v = currentBudgets[cat] ?: normalizedCurrent[norm] ?: 0.0
                if (v > 0.0) {
                    put(cat, if (v % 1.0 == 0.0) v.toInt().toString() else v.toString())
                }
            }
        }
    }

    val expectedIncome = if (report.totalAvailableIncome > 0.0) report.totalAvailableIncome else if (report.expectedIncome > 0.0) report.expectedIncome else suggestedBudgets.expectedIncome
    val formTotal = BUDGETABLE_EXPENSE_CATEGORIES.sumOf { cat ->
        val clean = (budgetInputs[cat] ?: "").replace(",", ".").trim()
        val v = clean.toDoubleOrNull() ?: 0.0
        if (v > 0.0) v else 0.0
    }
    val isOverIncome = expectedIncome > 0.0 && formTotal > expectedIncome
    val incomeUsagePct = if (expectedIncome > 0.0) ((formTotal / expectedIncome) * 100.0).roundToInt() else 0
    val unallocatedIncome = if (expectedIncome > 0.0) FinanceEngine.round2(expectedIncome - formTotal) else 0.0

    val calibrateInputsToIncome = {
        if (expectedIncome > 0.0 && formTotal > expectedIncome) {
            val parsedVals = mutableMapOf<String, Double>()
            for (cat in BUDGETABLE_EXPENSE_CATEGORIES) {
                val raw = (budgetInputs[cat] ?: "").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                if (raw > 0.0) parsedVals[cat] = raw
            }

            // Piso inegociável por categoria: o que já foi gasto no mês ou custo fixo contratual
            val floorVals = mutableMapOf<String, Double>()
            for ((cat, raw) in parsedVals) {
                val spentItem = report.items.find {
                    FinanceEngine.normalizeBudgetCategory(it.category) == FinanceEngine.normalizeBudgetCategory(cat)
                }
                val spentSoFar = spentItem?.spent ?: 0.0
                val isFixed = FinanceEngine.isFixedBudgetCategory(cat)
                floorVals[cat] = if (isFixed) raw else spentSoFar
            }

            val fixedCategories = parsedVals.keys.filter { FinanceEngine.isFixedBudgetCategory(it) }
            val flexCategories = parsedVals.keys.filter { !FinanceEngine.isFixedBudgetCategory(it) }
            val fixedSum = fixedCategories.sumOf { parsedVals[it] ?: 0.0 }
            val availableForFlex = expectedIncome - fixedSum

            if (availableForFlex > 0.0 && flexCategories.isNotEmpty()) {
                val flexFloorsSum = flexCategories.sumOf { floorVals[it] ?: 0.0 }
                if (availableForFlex >= flexFloorsSum) {
                    val discretionaryPool = availableForFlex - flexFloorsSum
                    val currentDiscretionarySum = flexCategories.sumOf {
                        kotlin.math.max(0.0, (parsedVals[it] ?: 0.0) - (floorVals[it] ?: 0.0))
                    }
                    for (cat in flexCategories) {
                        val floor = floorVals[cat] ?: 0.0
                        val excess = kotlin.math.max(0.0, (parsedVals[cat] ?: 0.0) - floor)
                        val share = if (currentDiscretionarySum > 0.0) {
                            (excess / currentDiscretionarySum) * discretionaryPool
                        } else {
                            discretionaryPool / flexCategories.size
                        }
                        val calibrated = kotlin.math.floor((floor + share) / 5.0) * 5.0
                        budgetInputs[cat] = if (calibrated % 1.0 == 0.0) calibrated.toInt().toString() else FinanceEngine.round2(calibrated).toString()
                    }
                } else {
                    // Entrada não cobre nem os pisos já gastos das categorias flexíveis; fixa no piso gasto
                    for (cat in flexCategories) {
                        val floor = floorVals[cat] ?: 0.0
                        val calibrated = kotlin.math.max(10.0, kotlin.math.floor(floor / 5.0) * 5.0)
                        budgetInputs[cat] = if (calibrated % 1.0 == 0.0) calibrated.toInt().toString() else FinanceEngine.round2(calibrated).toString()
                    }
                }
            } else {
                // Se as despesas fixas sozinhas já consomem a renda prevista, mantém os fixos e ajusta flexíveis para o mínimo
                for (cat in flexCategories) {
                    val floor = floorVals[cat] ?: 0.0
                    val minVal = if (floor > 0.0) floor else 10.0
                    budgetInputs[cat] = if (minVal % 1.0 == 0.0) minVal.toInt().toString() else FinanceEngine.round2(minVal).toString()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Envelopes Orçamentários Inteligentes",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Defina tetos mensais por categoria ou reequilibre com IA",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextSecondary)
                    }
                }

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { activeTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 0) Emerald600 else DarkSurfaceVariant,
                            contentColor = if (activeTab == 0) Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Definir Tetos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { activeTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 1) Purple500 else DarkSurfaceVariant,
                            contentColor = if (activeTab == 1) Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (rebalance.suggestions.isNotEmpty()) {
                                "Reequilibrar IA (${rebalance.suggestions.size})"
                            } else {
                                "Reequilibrar IA"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Termômetro ao Vivo de Alocação da Entrada Prevista do Mês
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isOverIncome) Rose500.copy(alpha = 0.12f) else DarkSurface,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isOverIncome) Rose400.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (report.carryoverAmount > 0.0) "ORÇAMENTO TOTAL VS DISPONÍVEL NO MÊS" else "ORÇAMENTO TOTAL VS ENTRADA DO MÊS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "${FinanceEngine.formatCurrency(formTotal)} / ${if (expectedIncome > 0.0) FinanceEngine.formatCurrency(expectedIncome) else "—"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isOverIncome) Rose400 else Color.White
                                    )
                                    if (report.carryoverAmount > 0.0) {
                                        Text(
                                            text = "Entrada: ${FinanceEngine.formatCurrency(report.expectedIncome)} • Sobra: +${FinanceEngine.formatCurrency(report.carryoverAmount)}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Cyan400
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isOverIncome) {
                                        Button(
                                            onClick = { calibrateInputsToIncome() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Calibrar à Entrada", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (formTotal > 0.0) {
                                        OutlinedButton(
                                            onClick = { budgetInputs.clear() },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Limpar", fontSize = 10.sp, color = TextSecondary)
                                        }
                                    }
                                }
                            }

                            if (expectedIncome > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (incomeUsagePct / 100f).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                when {
                                                    isOverIncome -> Rose400
                                                    incomeUsagePct > 85 -> Amber400
                                                    else -> Emerald400
                                                }
                                            )
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isOverIncome) {
                                            "Teto total excede a entrada em ${FinanceEngine.formatCurrency(abs(unallocatedIncome))}"
                                        } else {
                                            "$incomeUsagePct% da entrada alocada nos envelopes"
                                        },
                                        fontSize = 10.sp,
                                        color = if (isOverIncome) Rose400 else TextSecondary
                                    )
                                    if (!isOverIncome) {
                                        Text(
                                            text = "Livre: ${FinanceEngine.formatCurrency(unallocatedIncome)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Emerald400
                                        )
                                    }
                                }
                            }
                        }

                        // Feature: Sugerir Orçamento com Gemini AI
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Purple500.copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, Purple400.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            text = "✨ Planejador Inteligente Gemini",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    if (onGenerateSmartBudget != null) {
                                        Button(
                                            onClick = onGenerateSmartBudget,
                                            enabled = !isGeneratingSmartBudget,
                                            colors = ButtonDefaults.buttonColors(containerColor = Purple500, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            if (isGeneratingSmartBudget) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Planejando...", fontSize = 10.sp)
                                            } else {
                                                Text(if (smartBudgetPlan != null) "Repensar IA" else "Planejar com IA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (isGeneratingSmartBudget) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Purple400, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Gemini analisando contas a vencer e histórico...", fontSize = 11.sp, color = TextSecondary)
                                    }
                                } else if (smartBudgetPlan != null) {
                                    Text(
                                        text = smartBudgetPlan.summaryInsight,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = TextPrimary
                                    )
                                    Button(
                                        onClick = {
                                            for (a in smartBudgetPlan.allocations) {
                                                val v = a.suggestedLimit
                                                budgetInputs[a.category] = if (v % 1.0 == 0.0) v.toInt().toString() else FinanceEngine.round2(v).toString()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Aplicar Sugestões da IA nos Campos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else if (suggestedBudgets.suggestedBudgets.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Sugestão estatística: ${FinanceEngine.formatCurrency(suggestedBudgets.totalSuggested)}",
                                            fontSize = 10.5.sp,
                                            color = TextSecondary
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                for ((cat, lim) in suggestedBudgets.suggestedBudgets) {
                                                    budgetInputs[cat] = lim.toInt().toString()
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Usar Histórico", fontSize = 10.sp, color = Emerald400, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        for (category in BUDGETABLE_EXPENSE_CATEGORIES) {
                            val spentItem = report.items.find {
                                FinanceEngine.normalizeBudgetCategory(it.category) == FinanceEngine.normalizeBudgetCategory(category)
                            }
                            val spentAmt = spentItem?.spentAmount ?: 0.0
                            val pendingAmt = spentItem?.pendingAmount ?: 0.0
                            val minFloor = spentAmt + pendingAmt
                            val avg3m = suggestedBudgets.categoryAverages[category] ?: 0.0
                            val aiReason = smartBudgetPlan?.allocations?.find { it.category == category }?.reason

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        if (minFloor > 0.0) {
                                            Text(
                                                text = "Mínimo comprometido: ${FinanceEngine.formatCurrency(minFloor)} (${FinanceEngine.formatCurrency(spentAmt)} pagos + ${FinanceEngine.formatCurrency(pendingAmt)} a vencer)",
                                                fontSize = 10.sp,
                                                color = Amber400
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = budgetInputs[category] ?: "",
                                        onValueChange = { raw ->
                                            budgetInputs[category] = raw.filter { it.isDigit() || it == '.' || it == ',' }
                                        },
                                        placeholder = { Text("0", fontSize = 11.sp, color = TextMuted) },
                                        prefix = { Text("R$ ", fontSize = 11.sp, color = TextSecondary) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(115.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White)
                                    )
                                }

                                if (!aiReason.isNullOrBlank()) {
                                    Text(
                                        text = "✨ $aiReason",
                                        fontSize = 10.sp,
                                        color = Purple400,
                                        lineHeight = 14.sp
                                    )
                                }

                                // Quick adjustment chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (avg3m > 0.0) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val rounded = (kotlin.math.ceil(kotlin.math.max(minFloor, avg3m) / 10.0) * 10.0).toInt()
                                                    budgetInputs[category] = rounded.toString()
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Média 3M: ${FinanceEngine.formatCurrency(avg3m)}", fontSize = 9.5.sp, color = Cyan400)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                val current = (budgetInputs[category] ?: "").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                                                val next = kotlin.math.max(minFloor, current - 50.0)
                                                val rounded = (kotlin.math.ceil(next / 10.0) * 10.0).toInt()
                                                budgetInputs[category] = rounded.toString()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("-R$ 50", fontSize = 9.5.sp, color = TextSecondary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                val current = (budgetInputs[category] ?: "").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                                                val next = current + 50.0
                                                val rounded = (kotlin.math.ceil(next / 10.0) * 10.0).toInt()
                                                budgetInputs[category] = rounded.toString()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("+R$ 50", fontSize = 9.5.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    // Footer Save
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val parsed = mutableMapOf<String, Double>()
                                for ((cat, raw) in budgetInputs) {
                                    val clean = raw.replace(",", ".").trim()
                                    val v = clean.toDoubleOrNull() ?: 0.0
                                    if (v > 0.0) {
                                        parsed[cat] = FinanceEngine.round2(v)
                                    }
                                }
                                onSaveBudgets(parsed)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Salvar Envelopes",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Tab 1: Rebalanceamento IA
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (rebalance.suggestions.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface, RoundedCornerShape(14.dp))
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Orçamento Equilibrado!",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Nenhuma categoria precisa de compensação no momento ou não há sobras para realocar.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Text(
                                text = "A IA identificou envelopes com folga que podem cobrir os excessos deste mês sem alterar seu orçamento global:",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            for (s in rebalance.suggestions) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurface, RoundedCornerShape(12.dp))
                                        .border(1.dp, Purple400.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${s.compensateFrom} ➔ ${s.overspentCategory}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = FinanceEngine.formatCurrency(s.suggestedTransfer),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Purple400
                                        )
                                    }
                                    Text(
                                        text = "Retira da folga de ${FinanceEngine.formatCurrency(s.availableSurplus)} em ${s.compensateFrom} para cobrir o estouro de ${FinanceEngine.formatCurrency(s.overspentAmount)} em ${s.overspentCategory}.",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                    Button(
                                        onClick = {
                                            onApplyRebalance(s.compensateFrom, s.overspentCategory, s.suggestedTransfer)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Purple500, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.align(Alignment.End).height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text("Transferir Agora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    onApplyRebalance(null, null, null)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Aplicar Todo o Rebalanceamento IA", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
