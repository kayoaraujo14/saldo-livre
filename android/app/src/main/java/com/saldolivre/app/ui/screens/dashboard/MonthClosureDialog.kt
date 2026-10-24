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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.max

@Composable
fun MonthClosureDialog(
    closedMonthKey: String,
    targetMonthKey: String,
    totalSurplus: Double,
    onConfirm: (carryover: Double, invested: Double, cushion: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val closedMonthLabel = remember(closedMonthKey) { FinanceEngine.formatMonthLabel(closedMonthKey) }
    val targetMonthLabel = remember(targetMonthKey) { FinanceEngine.formatMonthLabel(targetMonthKey) }
    val formattedTotal = remember(totalSurplus) { FinanceEngine.formatCurrency(totalSurplus) }

    // States for custom distribution inputs
    var carryoverInput by remember { mutableStateOf("") }
    var investedInput by remember { mutableStateOf("") }
    var cushionInput by remember { mutableStateOf("") }

    val carryoverVal = carryoverInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    val investedVal = investedInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    val cushionVal = cushionInput.replace(",", ".").toDoubleOrNull() ?: 0.0

    val allocatedSum = FinanceEngine.round2(carryoverVal + investedVal + cushionVal)
    val remaining = FinanceEngine.round2(totalSurplus - allocatedSum)
    val isExact = abs(remaining) < 0.01

    fun applyPreset(carry: Double, invest: Double, cush: Double) {
        carryoverInput = if (carry > 0.0) String.format(java.util.Locale.US, "%.2f", carry) else ""
        investedInput = if (invest > 0.0) String.format(java.util.Locale.US, "%.2f", invest) else ""
        cushionInput = if (cush > 0.0) String.format(java.util.Locale.US, "%.2f", cush) else ""
    }

    // Default: suggest 50% invest / 50% cushion or 100% invest
    LaunchedEffect(totalSurplus) {
        if (totalSurplus > 0) {
            val half = FinanceEngine.round2(totalSurplus / 2.0)
            val otherHalf = FinanceEngine.round2(totalSurplus - half)
            applyPreset(0.0, half, otherHalf)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header Celebration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Emerald500.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Fechamento de Mês",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Em $closedMonthLabel sobraram $formattedTotal no azul! Como você quer distribuir essa conquista?",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Presets
                Text(
                    text = "ATALHOS RÁPIDOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetChip(
                        label = "💰 Investir",
                        selected = (investedVal == totalSurplus && carryoverVal == 0.0 && cushionVal == 0.0),
                        onClick = { applyPreset(0.0, totalSurplus, 0.0) },
                        modifier = Modifier.weight(1f)
                    )

                    PresetChip(
                        label = "🚀 Saldo Livre",
                        selected = (carryoverVal == totalSurplus && investedVal == 0.0 && cushionVal == 0.0),
                        onClick = { applyPreset(totalSurplus, 0.0, 0.0) },
                        modifier = Modifier.weight(1f)
                    )

                    val half = FinanceEngine.round2(totalSurplus / 2.0)
                    val otherHalf = FinanceEngine.round2(totalSurplus - half)
                    PresetChip(
                        label = "🛡️ Protegido",
                        selected = (investedVal == half && cushionVal == otherHalf && carryoverVal == 0.0),
                        onClick = { applyPreset(0.0, half, otherHalf) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Distribution Inputs
                Text(
                    text = "FATIAS DE DESTINO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Saldo Livre
                DestinationInputField(
                    title = "🚀 Saldo Livre ($targetMonthLabel)",
                    subtitle = "Aumenta o limite diário de gastos no mês",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = Cyan400,
                    value = carryoverInput,
                    onValueChange = { carryoverInput = it },
                    onUseRemaining = {
                        val newAmount = FinanceEngine.round2(carryoverVal + max(0.0, remaining))
                        carryoverInput = if (newAmount > 0.0) String.format(java.util.Locale.US, "%.2f", newAmount) else ""
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Investimentos
                DestinationInputField(
                    title = "📈 Investimentos / Reserva",
                    subtitle = "Registra aporte patrimonial comemorativo",
                    icon = Icons.Default.Savings,
                    iconTint = Emerald400,
                    value = investedInput,
                    onValueChange = { investedInput = it },
                    onUseRemaining = {
                        val newAmount = FinanceEngine.round2(investedVal + max(0.0, remaining))
                        investedInput = if (newAmount > 0.0) String.format(java.util.Locale.US, "%.2f", newAmount) else ""
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Colchão de Proteção
                DestinationInputField(
                    title = "🛡️ Colchão de Segurança",
                    subtitle = "Proteção para imprevistos (não entra no limite diário)",
                    icon = Icons.Default.Shield,
                    iconTint = Amber400,
                    value = cushionInput,
                    onValueChange = { cushionInput = it },
                    onUseRemaining = {
                        val newAmount = FinanceEngine.round2(cushionVal + max(0.0, remaining))
                        cushionInput = if (newAmount > 0.0) String.format(java.util.Locale.US, "%.2f", newAmount) else ""
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Progress Bar
                DistributionProgressBar(
                    total = totalSurplus,
                    carryover = carryoverVal,
                    invested = investedVal,
                    cushion = cushionVal
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total alocado: ${FinanceEngine.formatCurrency(allocatedSum)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Text(
                        text = when {
                            isExact -> "✅ 100% distribuído"
                            remaining > 0 -> "Faltam ${FinanceEngine.formatCurrency(remaining)}"
                            else -> "Excesso de ${FinanceEngine.formatCurrency(abs(remaining))}"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isExact -> Emerald400
                            remaining > 0 -> Amber400
                            else -> Rose400
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Button(
                    onClick = {
                        if (isExact) {
                            onConfirm(carryoverVal, investedVal, cushionVal)
                        }
                    },
                    enabled = isExact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        disabledContainerColor = DarkSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirmar Fechamento",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Decidir mais tarde",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Emerald500.copy(alpha = 0.2f) else DarkSurfaceVariant)
            .border(
                1.dp,
                if (selected) Emerald500 else DarkBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Emerald400 else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun DestinationInputField(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    onValueChange: (String) -> Unit,
    onUseRemaining: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("0,00", color = TextMuted, fontSize = 14.sp) },
                prefix = { Text("R$ ", color = TextSecondary, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onUseRemaining,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DarkBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Restante", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DistributionProgressBar(
    total: Double,
    carryover: Double,
    invested: Double,
    cushion: Double
) {
    val totalSafe = if (total > 0) total else 1.0
    val carryoverFrac = (carryover / totalSafe).toFloat().coerceIn(0f, 1f)
    val investedFrac = (invested / totalSafe).toFloat().coerceIn(0f, 1f)
    val cushionFrac = (cushion / totalSafe).toFloat().coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(DarkBorder)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (investedFrac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(investedFrac)
                        .background(Emerald500)
                )
            }
            if (carryoverFrac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(carryoverFrac)
                        .background(Cyan400)
                )
            }
            if (cushionFrac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(cushionFrac)
                        .background(Amber400)
                )
            }
            val remainingFrac = (1f - (investedFrac + carryoverFrac + cushionFrac)).coerceAtLeast(0f)
            if (remainingFrac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(remainingFrac)
                        .background(Color.Transparent)
                )
            }
        }
    }
}
