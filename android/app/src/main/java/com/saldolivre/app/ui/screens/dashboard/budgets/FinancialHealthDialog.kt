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
                                    text = "DiagnÃ³stico de SaÃºde Financeira",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "AvaliaÃ§Ã£o integrada dos 4 pilares em ${FinanceEngine.formatMonthLabel(monthKey)}",
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
                                        onAskCopilot("FaÃ§a um diagnÃ³stico completo da minha SaÃºde Financeira (4 pilares) e dos meus Envelopes OrÃ§amentÃ¡rios deste mÃªs, indicando um plano prÃ¡tico para subir meu Score.")
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

                    // Resumo Executivo do MÃªs
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
                                Text("Custo de Vida LÃ­quido", fontSize = 9.sp, color = TextMuted)
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
                                Text("Investido no MÃªs", fontSize = 9.sp, color = TextMuted)
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

                    // Plano de AÃ§Ã£o Gemini AI
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
                                        text = "Plano de AÃ§Ã£o Gemini AI âœ¨",
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
                                    Text("Gerando recomendaÃ§Ãµes prÃ¡ticas com o Gemini...", fontSize = 11.sp, color = TextSecondary)
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
                                    text = "Toque em 'Diagnosticar' para obter um plano de aÃ§Ã£o estratÃ©gico gerado pelo Gemini com foco em elevar seu Score.",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    // 4 Pillars Breakdown (Total = 100 pts)
                    PillarRowCard(
                        title = "1. Taxa de PoupanÃ§a & Investimento",
                        scoreText = "${health.savingsScore}/30 pts",
                        progress = (health.savingsScore / 30f).coerceIn(0f, 1f),
                        color = Emerald400,
                        line1 = "Investido no mÃªs: ${FinanceEngine.formatCurrency(summary.totalInvested)} (${health.savingsRatePercentage}%)",
                        line2 = "Meta saudÃ¡vel: >= 20% da renda"
                    )

                    PillarRowCard(
                        title = "2. Custo de Vida / Comprometimento",
                        scoreText = "${health.spendingLimitScore}/30 pts",
                        progress = (health.spendingLimitScore / 30f).coerceIn(0f, 1f),
                        color = Cyan400,
                        line1 = "Custo lÃ­quido (sem aportes): ${FinanceEngine.formatCurrency(livingCost)} (${health.spendingRatioPercentage}%)",
                        line2 = "Teto saudÃ¡vel recomendado: AtÃ© 70% da renda"
                    )

                    PillarRowCard(
                        title = "3. Reserva de EmergÃªncia",
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
                        line1 = "AderÃªncia dos envelopes variÃ¡veis",
                        line2 = if (health.budgetConfiguredCount == 0) {
                            "Configure tetos para pontuar atÃ© 15 pts"
                        } else if (health.budgetOverspentCount == 0) {
                            "Todos os envelopes sob controle (+15 pts)!"
                        } else {
                            "${health.budgetOverspentCount} envelope(s) com estouro no mÃªs"
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
                                        text = "ALAVANCAS PRÃTICAS PARA SUBIR SEU SCORE",
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
