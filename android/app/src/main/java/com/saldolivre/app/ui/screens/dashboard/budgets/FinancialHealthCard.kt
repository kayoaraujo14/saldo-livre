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
