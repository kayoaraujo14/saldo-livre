package com.saldolivre.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

enum class ClosureDestinationMode {
    KEEP_IN_ACCOUNT, // 100% no Saldo Livre da conta
    INVEST_RESERVE,  // 100% guardado em caixinha/investimento
    CUSTOM_SPLIT     // Dividir valores entre conta e caixinha
}

@Composable
fun MonthClosureDialog(
    closedMonthKey: String,
    targetMonthKey: String,
    totalSurplus: Double,
    onConfirm: (carryover: Double, invested: Double, subcategory: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val closedMonthLabel = remember(closedMonthKey) { FinanceEngine.formatMonthLabel(closedMonthKey) }
    val targetMonthLabel = remember(targetMonthKey) { FinanceEngine.formatMonthLabel(targetMonthKey) }
    val formattedTotal = remember(totalSurplus) { FinanceEngine.formatCurrency(totalSurplus) }
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var destinationMode by remember { mutableStateOf(ClosureDestinationMode.KEEP_IN_ACCOUNT) }
    var selectedSubcategory by remember { mutableStateOf("Reserva de Emergência") }

    // States for custom split inputs
    var carryoverInput by remember { mutableStateOf("") }
    var investedInput by remember { mutableStateOf("") }

    val carryoverVal = if (destinationMode == ClosureDestinationMode.KEEP_IN_ACCOUNT) {
        totalSurplus
    } else if (destinationMode == ClosureDestinationMode.INVEST_RESERVE) {
        0.0
    } else {
        carryoverInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    }

    val investedVal = if (destinationMode == ClosureDestinationMode.INVEST_RESERVE) {
        totalSurplus
    } else if (destinationMode == ClosureDestinationMode.KEEP_IN_ACCOUNT) {
        0.0
    } else {
        investedInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    }

    val allocatedSum = FinanceEngine.round2(carryoverVal + investedVal)
    val remaining = FinanceEngine.round2(totalSurplus - allocatedSum)
    val isExact = abs(remaining) < 0.01

    val investmentOptions = listOf(
        "Reserva de Emergência",
        "Renda Fixa (CDB / Tesouro / LCI / LCA)",
        "Ações & Fundos Imobiliários (FIIs)",
        "Criptomoedas & Ativos Digitais",
        "Outros Investimentos"
    )

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
                    text = "Em $closedMonthLabel sobraram $formattedTotal! Como você prefere destinar essa sobra?",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Card 1: Deixar na Conta Corrente (Saldo Livre)
                DestinationCard(
                    title = "Deixar na Conta Corrente",
                    subtitle = "Soma ao seu Saldo Livre de $targetMonthLabel para continuar disponível no fluxo diário.",
                    amount = if (destinationMode == ClosureDestinationMode.KEEP_IN_ACCOUNT) formattedTotal else null,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = Cyan400,
                    selected = destinationMode == ClosureDestinationMode.KEEP_IN_ACCOUNT,
                    onClick = {
                        destinationMode = ClosureDestinationMode.KEEP_IN_ACCOUNT
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Card 2: Guardar em Investimentos / Reserva
                DestinationCard(
                    title = "Guardar em Investimentos",
                    subtitle = "Protege o valor guardando diretamente em uma Caixinha ou Aplicação financeira.",
                    amount = if (destinationMode == ClosureDestinationMode.INVEST_RESERVE) formattedTotal else null,
                    icon = Icons.Default.Savings,
                    iconTint = Emerald400,
                    selected = destinationMode == ClosureDestinationMode.INVEST_RESERVE,
                    onClick = {
                        destinationMode = ClosureDestinationMode.INVEST_RESERVE
                    }
                )

                // Seletor de Caixinha quando guardar em investimentos
                AnimatedVisibility(visible = destinationMode == ClosureDestinationMode.INVEST_RESERVE || (destinationMode == ClosureDestinationMode.CUSTOM_SPLIT && investedVal > 0.0)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = "SELECIONE A CAIXINHA DE DESTINO:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        investmentOptions.forEach { opt ->
                            val isSelected = selectedSubcategory == opt
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Emerald500.copy(alpha = 0.12f) else DarkSurfaceVariant)
                                    .border(1.dp, if (isSelected) Emerald500.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedSubcategory = opt }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Emerald400 else TextPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle para divisão personalizada
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (destinationMode == ClosureDestinationMode.CUSTOM_SPLIT) {
                                destinationMode = ClosureDestinationMode.KEEP_IN_ACCOUNT
                            } else {
                                val half = FinanceEngine.round2(totalSurplus / 2.0)
                                val otherHalf = FinanceEngine.round2(totalSurplus - half)
                                carryoverInput = String.format(java.util.Locale.US, "%.2f", half)
                                investedInput = String.format(java.util.Locale.US, "%.2f", otherHalf)
                                destinationMode = ClosureDestinationMode.CUSTOM_SPLIT
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (destinationMode == ClosureDestinationMode.CUSTOM_SPLIT) "← Voltar às opções simples" else "Prefiro dividir os valores (Personalizado)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (destinationMode == ClosureDestinationMode.CUSTOM_SPLIT) Amber400 else TextSecondary
                    )
                }

                // Campos de divisão personalizada
                AnimatedVisibility(visible = destinationMode == ClosureDestinationMode.CUSTOM_SPLIT) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        DestinationInputField(
                            title = "Conta Corrente ($targetMonthLabel)",
                            subtitle = "Soma ao Saldo Livre para gastar",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            iconTint = Cyan400,
                            value = carryoverInput,
                            onValueChange = { carryoverInput = it },
                            onUseRemaining = {
                                val newAmount = FinanceEngine.round2(carryoverVal + max(0.0, remaining))
                                carryoverInput = if (newAmount > 0.0) String.format(java.util.Locale.US, "%.2f", newAmount) else ""
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        DestinationInputField(
                            title = "Guardar na Caixinha",
                            subtitle = "Aporte para poupar ou investir",
                            icon = Icons.Default.Savings,
                            iconTint = Emerald400,
                            value = investedInput,
                            onValueChange = { investedInput = it },
                            onUseRemaining = {
                                val newAmount = FinanceEngine.round2(investedVal + max(0.0, remaining))
                                investedInput = if (newAmount > 0.0) String.format(java.util.Locale.US, "%.2f", newAmount) else ""
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Distribuído: ${FinanceEngine.formatCurrency(allocatedSum)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            Text(
                                text = when {
                                    isExact -> "✅ 100% distribuído"
                                    remaining > 0 -> "Faltam ${FinanceEngine.formatCurrency(remaining)}"
                                    else -> "Excesso de ${FinanceEngine.formatCurrency(abs(remaining))}"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isExact -> Emerald400
                                    remaining > 0 -> Amber400
                                    else -> Rose400
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Botão de confirmação
                Button(
                    onClick = {
                        if (isExact) {
                            com.saldolivre.app.ui.util.HapticFeedbackHelper.success(context)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm(carryoverVal, investedVal, if (investedVal > 0.0) selectedSubcategory else null)
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

                Spacer(modifier = Modifier.height(6.dp))

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
private fun DestinationCard(
    title: String,
    subtitle: String,
    amount: String?,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Emerald500.copy(alpha = 0.12f) else DarkSurfaceVariant)
            .border(
                1.5.dp,
                if (selected) Emerald500 else DarkBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Emerald400 else TextPrimary
                    )
                    if (amount != null) {
                        Text(
                            text = amount,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald400
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
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
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("0,00", color = TextMuted, fontSize = 13.sp) },
                prefix = { Text("R$ ", color = TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(8.dp),
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
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedButton(
                onClick = onUseRemaining,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DarkBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Restante", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
