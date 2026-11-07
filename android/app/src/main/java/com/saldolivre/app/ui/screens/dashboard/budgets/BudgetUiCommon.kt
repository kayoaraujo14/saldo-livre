package com.saldolivre.app.ui.screens.dashboard.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.ui.theme.*

val BUDGETABLE_EXPENSE_CATEGORIES = listOf(
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

enum class EnvelopeFilterMode(val label: String) {
    ALL("Todos"),
    ALERTS("Em Alerta"),
    VARIABLE("Variáveis"),
    FIXED("Fixos")
}

val InvestBlue = Color(0xFF60A5FA)

fun brl(value: Double): String = FinanceEngine.formatCurrency(value)

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

fun envelopeSeverityRank(item: CategoryBudgetStatus): Int = when {
    item.limit > 0.0 && (item.committedAmount - item.limit) >= 0.01 -> 0
    item.status == CategoryBudgetAlertStatus.DANGER -> 0
    item.status == CategoryBudgetAlertStatus.WARNING -> 1
    else -> 2
}

fun insightColor(insight: BudgetInsight): Color = when (insight.severity) {
    BudgetInsightSeverity.DANGER -> Rose400
    BudgetInsightSeverity.ATTENTION -> Amber400
    BudgetInsightSeverity.INFO -> if (insight.id == "all_good") Emerald400 else TextSecondary
}

@Composable
fun DualLayerProgressBar(
    spentRatio: Float,
    pendingRatio: Float,
    statusColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
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
