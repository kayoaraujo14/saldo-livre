package com.saldolivre.app.ui.components

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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
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
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.ui.theme.*
import java.util.Locale

@Composable
fun QuickRefundDialog(
    origTx: Transaction,
    currentUser: UserProfile,
    cardName: String? = null,
    onDismissRequest: () -> Unit,
    onConfirmRefund: (Double) -> Unit
) {
    val origTotal = remember(origTx) { Math.abs(origTx.effectiveAmount) }
    var amountInput by remember(origTx.id) {
        mutableStateOf(if (origTotal > 0.0) String.format(Locale.US, "%.2f", origTotal) else "")
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedAmount = remember(amountInput) {
        val sanitized = amountInput.replace(",", ".").trim()
        sanitized.toDoubleOrNull() ?: 0.0
    }
    val isFullRefund = remember(parsedAmount, origTotal) {
        Math.abs(parsedAmount - origTotal) < 0.01
    }
    val isOverAmount = remember(parsedAmount, origTotal) {
        parsedAmount > origTotal + 0.01
    }

    // Proporções de rateio calculadas diretamente do rescaleSplit para garantir proporção e soma exata de 100%
    val previewAllocations = remember(origTx, origTotal, currentUser) {
        FinanceEngine.rescaleSplit(origTx, origTotal, currentUser.uid).allocations
    }
    val hasSplit = previewAllocations.size > 1 || origTx.splitType == com.saldolivre.app.data.model.SplitType.SPLIT_50 || !origTx.assignedFriendUid.isNullOrBlank()

    // Cálculo em tempo real do alívio com o FinanceEngine
    val reliefPreview = remember(parsedAmount, currentUser, origTx) {
        if (parsedAmount <= 0.0) null
        else {
            try {
                val draft = FinanceEngine.createRefundTransaction(origTx, parsedAmount, currentUser)
                val myRelief = Math.abs(draft.userImpact[currentUser.uid] ?: -parsedAmount)
                val othersRelief = Math.max(0.0, FinanceEngine.round2(parsedAmount - myRelief))
                Pair(myRelief, othersRelief)
            } catch (_: Exception) {
                null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = DarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Cyan500.copy(alpha = 0.16f))
                        .border(1.dp, Cyan500.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Registrar Estorno no Cartão",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Vinculado à compra selecionada",
                        style = Typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Card Resumo da Compra Original
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "COMPRA ORIGINAL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                                Text(
                                    text = origTx.description,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Valor original",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = FinanceEngine.formatCurrency(origTotal),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        // Metadados replicados
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkBackground)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = Cyan400, modifier = Modifier.size(11.dp))
                                    Text(
                                        text = origTx.category,
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (!cardName.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkBackground)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CreditCard, contentDescription = null, tint = Cyan400, modifier = Modifier.size(11.dp))
                                        Text(
                                            text = cardName,
                                            fontSize = 10.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkBackground)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = Cyan400, modifier = Modifier.size(11.dp))
                                    Text(
                                        text = origTx.competenceMonth,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Participantes Replicados
                        if (hasSplit) {
                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.People, contentDescription = null, tint = Cyan400, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "Rateio Replicado Proporcionalmente:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                            }

                            if (previewAllocations.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    previewAllocations.forEach { a ->
                                        val pct = if (origTotal > 0.0) FinanceEngine.round2((Math.abs(a.amount) / origTotal) * 100.0) else 0.0
                                        val partAmount = if (parsedAmount > 0.0) FinanceEngine.round2((parsedAmount * pct) / 100.0) else Math.abs(a.amount)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = a.name, fontSize = 11.sp, color = TextMuted)
                                            Text(
                                                text = "${pct.toInt()}% (${FinanceEngine.formatCurrency(partAmount)})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Cyan400
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Campo: Qual o valor do estorno?
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cyan500.copy(alpha = 0.08f))
                        .border(1.dp, Cyan500.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Qual o valor do estorno?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cyan400
                            )

                            if (!isFullRefund && origTotal > 0.0) {
                                Text(
                                    text = "Estorno Total (${FinanceEngine.formatCurrency(origTotal)})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Cyan400,
                                    modifier = Modifier.clickable {
                                        amountInput = String.format(Locale.US, "%.2f", origTotal)
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = {
                                amountInput = it
                                errorMessage = null
                            },
                            prefix = {
                                Text(
                                    text = "− R$ ",
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan400,
                                    fontSize = 18.sp
                                )
                            },
                            placeholder = { Text("0,00", color = TextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Cyan400,
                                unfocusedBorderColor = Cyan500.copy(alpha = 0.4f),
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        if (isOverAmount) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Amber400, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "Atenção: o estorno é maior que a compra original.",
                                    fontSize = 11.sp,
                                    color = Amber400
                                )
                            }
                        }

                        // Preview de Alívio
                        if (reliefPreview != null && parsedAmount > 0.0) {
                            val (myRelief, othersRelief) = reliefPreview
                            HorizontalDivider(color = Cyan500.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurface)
                                        .border(1.dp, Cyan500.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(text = "Alívio no seu saldo:", fontSize = 10.sp, color = TextMuted)
                                        Text(
                                            text = "+${FinanceEngine.formatCurrency(myRelief)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Cyan400
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurface)
                                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = if (hasSplit) "Abatimento de outros:" else "Abatimento na fatura:",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                        Text(
                                            text = if (hasSplit) "−${FinanceEngine.formatCurrency(othersRelief)}" else "−${FinanceEngine.formatCurrency(parsedAmount)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Amber400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = Rose500,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = CircleShape,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Text(text = "Cancelar", color = TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedAmount <= 0.0) {
                        errorMessage = "Informe um valor de estorno válido maior que zero."
                        return@Button
                    }
                    onConfirmRefund(parsedAmount)
                },
                enabled = parsedAmount > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Confirmar Estorno (${if (parsedAmount > 0.0) FinanceEngine.formatCurrency(parsedAmount) else "R$ 0,00"})",
                    fontWeight = FontWeight.Bold,
                    color = DarkBackground
                )
            }
        }
    )
}
