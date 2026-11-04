package com.saldolivre.app.ui.screens.dashboard

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
    "Família & Dependentes",
    "Pets",
    "Financiamentos & Empréstimos",
    "Impostos & Tributos",
    "Outros"
)

@Composable
fun CategoryBudgetsSectionCard(
    report: CategoryBudgetReport,
    rebalance: BudgetRebalanceResult,
    onOpenConfig: () -> Unit,
    onOpenRebalance: () -> Unit,
    onQuickApplyAiBudgets: () -> Unit,
    onAskCopilot: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val visibleItems = if (isExpanded) report.items else report.items.take(4)

    val statusColor = when (report.status) {
        CategoryBudgetAlertStatus.DANGER -> Rose400
        CategoryBudgetAlertStatus.WARNING -> Amber400
        CategoryBudgetAlertStatus.OK -> Emerald400
    }
    val statusText = when (report.status) {
        CategoryBudgetAlertStatus.DANGER -> "Atenção aos Tetos"
        CategoryBudgetAlertStatus.WARNING -> "Ritmo Acelerado"
        CategoryBudgetAlertStatus.OK -> "Dentro da Meta"
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Emerald500.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .border(1.dp, Emerald500.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ENVELOPES ORÇAMENTÁRIOS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (report.totalBudgeted > 0.0) {
                            val remText = if (report.totalRemaining >= 0.0) {
                                "Sobra: ${FinanceEngine.formatCurrency(report.totalRemaining)}"
                            } else {
                                "Excedido: ${FinanceEngine.formatCurrency(abs(report.totalRemaining))}"
                            }
                            Text(
                                text = "Orçado: ${FinanceEngine.formatCurrency(report.totalBudgeted)} • Gasto: ${FinanceEngine.formatCurrency(report.totalSpent)} (${report.overallPercentage.roundToInt()}%) • $remText",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Defina tetos por categoria para alertas inteligentes e compensação com IA.",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Action Buttons Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (report.topOverspentCategory != null || rebalance.canRebalance) {
                    OutlinedButton(
                        onClick = onOpenRebalance,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Purple400.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Purple500.copy(alpha = 0.12f),
                            contentColor = Purple400
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compensar IA", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        onAskCopilot("Analise o ritmo dos meus Envelopes Orçamentários deste mês, indique se há categorias em risco e sugira ajustes ou rebalanceamentos.")
                    },
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Emerald500.copy(alpha = 0.10f),
                        contentColor = Emerald400
                    )
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Analisar no Chat", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onOpenConfig,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkSurfaceVariant,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (report.totalBudgeted > 0.0) "Ajustar" else "Configurar", fontSize = 11.sp)
                }
            }

            // Global Progress Bar
            if (report.totalBudgeted > 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (report.overallPercentage.toFloat() / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor)
                    )
                }
            }

            // Preventive AI Alert Banner
            val topOver = report.topOverspentCategory
            if (topOver != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Purple500.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, Purple400.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                        .clickable { onOpenRebalance() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Purple400,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (topOver.status == CategoryBudgetAlertStatus.DANGER) {
                                "\"${topOver.category}\" excedeu o teto em ${FinanceEngine.formatCurrency(abs(topOver.remaining))}."
                            } else {
                                "Projeção de estouro em \"${topOver.category}\" até o fim do mês."
                            },
                            fontSize = 11.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Rebalancear",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple400,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Envelope Items
            if (report.items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in visibleItems) {
                        val itemColor = when (item.status) {
                            CategoryBudgetAlertStatus.DANGER -> Rose400
                            CategoryBudgetAlertStatus.WARNING -> Amber400
                            CategoryBudgetAlertStatus.OK -> Emerald400
                        }
                        val paceBadgeText = when {
                            item.burnRateStatus == CategoryBudgetPaceStatus.CRITICAL && item.limit > 0.0 && item.spent > item.limit -> "Estourado"
                            item.burnRateStatus == CategoryBudgetPaceStatus.CRITICAL -> "Crítico"
                            item.burnRateStatus == CategoryBudgetPaceStatus.FAST -> "Acelerado"
                            item.isFixedCategory -> "Fixo Mensal"
                            else -> "Normal"
                        }
                        val paceBadgeColor = when {
                            item.burnRateStatus == CategoryBudgetPaceStatus.CRITICAL -> Rose400
                            item.burnRateStatus == CategoryBudgetPaceStatus.FAST -> Amber400
                            item.isFixedCategory -> Cyan400
                            else -> Emerald400
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(itemColor, CircleShape)
                                    )
                                    Text(
                                        text = item.category,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(paceBadgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = paceBadgeText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = paceBadgeColor
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = FinanceEngine.formatCurrency(item.spent),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (item.limit > 0.0) "/ ${FinanceEngine.formatCurrency(item.limit)}" else "Sem teto",
                                    fontSize = 11.sp,
                                    color = TextSecondary
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
                                        .fillMaxWidth(fraction = (item.percentage.toFloat() / 100f).coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(itemColor)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (!item.isFixedCategory && item.remaining > 0.0 && item.limit > 0.0) {
                                        "Meta: ${FinanceEngine.formatCurrency(item.recommendedDailyRemaining)}/dia"
                                    } else {
                                        "${item.percentage.roundToInt()}% gasto"
                                    },
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )

                                val remLabel = when {
                                    item.remaining > 0.0 -> "+${FinanceEngine.formatCurrency(item.remaining)}"
                                    item.remaining < 0.0 -> "-${FinanceEngine.formatCurrency(abs(item.remaining))}"
                                    else -> "No limite"
                                }
                                val remColor = when {
                                    item.remaining > 0.0 -> Emerald400
                                    item.remaining < 0.0 -> Rose400
                                    else -> TextMuted
                                }
                                Text(
                                    text = remLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = remColor
                                )
                            }
                        }
                    }

                    if (report.items.size > 4) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isExpanded) "Recolher envelopes" else "Ver todos os ${report.items.size} envelopes",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                // Empty state with 1-click AI generation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Nenhum envelope definido neste mês. Ative tetos automáticos em 1 clique baseados na sua média histórica!",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onQuickApplyAiBudgets,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ativar Tetos com IA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenConfig,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Text("Personalizar", fontSize = 11.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialHealthDialog(
    health: FinancialHealthScore,
    summary: MonthSummary,
    monthKey: String,
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

                    // 4 Pillars Breakdown
                    PillarRowCard(
                        title = "1. Poupança & Margem",
                        scoreText = "${health.savingsScore}/35 pts",
                        progress = (health.savingsScore / 35f).coerceIn(0f, 1f),
                        color = Emerald400,
                        line1 = "Investido no mês: ${FinanceEngine.formatCurrency(summary.totalInvested)} (${health.savingsRatePercentage}%)",
                        line2 = "Potencial c/ Saldo Livre: ${health.potentialSavingsRatePercentage}% da renda (Meta: 20%)"
                    )

                    PillarRowCard(
                        title = "2. Custo de Vida Real",
                        scoreText = "${health.spendingLimitScore}/35 pts",
                        progress = (health.spendingLimitScore / 35f).coerceIn(0f, 1f),
                        color = Cyan400,
                        line1 = "Custo líquido (sem aportes): ${FinanceEngine.formatCurrency(livingCost)} (${health.spendingRatioPercentage}%)",
                        line2 = "Teto saudável recomendado: Até 70% da renda"
                    )

                    PillarRowCard(
                        title = "3. Reserva de Emergência",
                        scoreText = "${health.emergencyReserveScore}/30 pts",
                        progress = (health.emergencyReserveScore / 30f).coerceIn(0f, 1f),
                        color = Amber400,
                        line1 = "Cobertura atual: ${health.emergencyCoverageMonths} meses do custo de vida",
                        line2 = "Meta de blindagem (6 meses): ${FinanceEngine.formatCurrency(target6m)}"
                    )

                    val envBonusText = when {
                        health.budgetConfiguredCount == 0 -> "Não configurado"
                        health.budgetOverspentCount == 0 -> "+4 pts Bônus"
                        else -> "-${kotlin.math.min(8, health.budgetOverspentCount * 3)} pts"
                    }
                    PillarRowCard(
                        title = "4. Disciplina nos Envelopes",
                        scoreText = envBonusText,
                        progress = if (health.budgetConfiguredCount > 0) (health.budgetAdherenceScore / 100f).coerceIn(0f, 1f) else 0f,
                        color = if (health.budgetOverspentCount > 0) Rose400 else Purple400,
                        line1 = "Aderência orçamentária: ${if (health.budgetConfiguredCount > 0) "${health.budgetAdherenceScore}%" else "—"} (${health.budgetConfiguredCount} categorias)",
                        line2 = if (health.budgetConfiguredCount == 0) {
                            "Configure tetos para ganhar até +4 pts"
                        } else if (health.budgetOverspentCount == 0) {
                            "Todos os envelopes dentro do teto!"
                        } else {
                            "${health.budgetOverspentCount} envelope(s) acima do teto"
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
    onDismiss: () -> Unit,
    onSaveBudgets: (Map<String, Double>) -> Unit,
    onApplyRebalance: (String?, String?, Double?) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(initialTab) }
    val budgetInputs = remember(currentBudgets) {
        mutableStateMapOf<String, String>().apply {
            for (cat in BUDGETABLE_EXPENSE_CATEGORIES) {
                val v = currentBudgets[cat] ?: 0.0
                if (v > 0.0) {
                    put(cat, if (v % 1.0 == 0.0) v.toInt().toString() else v.toString())
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
                        // AI Suggestion Banner
                        if (suggestedBudgets.suggestedBudgets.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Emerald500.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Emerald500.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sugestão Inteligente IA (Média 3M + 10%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald400
                                    )
                                    Text(
                                        text = "Total sugerido: ${FinanceEngine.formatCurrency(suggestedBudgets.totalSuggested)} (${suggestedBudgets.incomeCoveragePct.roundToInt()}% da renda)",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                                Button(
                                    onClick = {
                                        for ((cat, lim) in suggestedBudgets.suggestedBudgets) {
                                            budgetInputs[cat] = lim.toInt().toString()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Preencher", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        for (category in BUDGETABLE_EXPENSE_CATEGORIES) {
                            val spentItem = report.items.find { it.category == category }
                            val avg3m = suggestedBudgets.categoryAverages[category] ?: 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    val subInfo = buildString {
                                        if (spentItem != null && spentItem.spent > 0.0) {
                                            append("Gasto atual: ${FinanceEngine.formatCurrency(spentItem.spent)}")
                                        }
                                        if (avg3m > 0.0) {
                                            if (isNotEmpty()) append(" • ")
                                            append("Média 3M: ${FinanceEngine.formatCurrency(avg3m)}")
                                        }
                                    }
                                    if (subInfo.isNotEmpty()) {
                                        Text(text = subInfo, fontSize = 10.sp, color = TextMuted)
                                    }
                                }

                                OutlinedTextField(
                                    value = budgetInputs[category] ?: "",
                                    onValueChange = { raw ->
                                        budgetInputs[category] = raw.filter { it.isDigit() || it == '.' || it == ',' }
                                    },
                                    placeholder = { Text("Sem teto", fontSize = 11.sp, color = TextMuted) },
                                    prefix = { Text("R$ ", fontSize = 11.sp, color = TextSecondary) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(125.dp),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White)
                                )
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
                                    if (v > 0.0) parsed[cat] = v
                                }
                                onSaveBudgets(parsed)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar Envelopes", fontWeight = FontWeight.Bold)
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
