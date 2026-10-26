package com.saldolivre.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    currentUserId: String? = null,
    allTransactions: List<Transaction> = emptyList(),
    onDismissRequest: () -> Unit,
    onToggleStatus: () -> Unit,
    onSetStatus: (TransactionStatus, Double?) -> Unit,
    onNavigateToEditTransaction: (String) -> Unit,
    onDeleteRequest: () -> Unit
) {
    val myImpact = remember(transaction, currentUserId, allTransactions) {
        if (!currentUserId.isNullOrBlank()) {
            FinanceEngine.impactFor(transaction, currentUserId, allTransactions)
        } else {
            transaction.effectiveAmount
        }
    }
    val isSplit = remember(transaction, myImpact) {
        kotlin.math.abs(myImpact - transaction.effectiveAmount) > 0.01 ||
                (transaction.splitType != null && transaction.splitType != com.saldolivre.app.data.model.SplitType.MINE) ||
                !transaction.assignedFriendUid.isNullOrBlank()
    }
    var customActualInput by remember(transaction.id, transaction.effectiveAmount) {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", transaction.effectiveAmount))
    }
    val isIncome = transaction.type == TransactionType.INCOME

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = DarkSurface,
        title = {
            Text(
                text = transaction.description,
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!currentUserId.isNullOrBlank() && isSplit && myImpact != transaction.effectiveAmount) {
                    DetailRow(label = "Sua Cota (Impacto)", value = FinanceEngine.formatCurrency(myImpact))
                    DetailRow(label = "Valor Total da Compra", value = FinanceEngine.formatCurrency(transaction.effectiveAmount))
                    if (transaction.paidByUid != currentUserId && myImpact > 0.0) {
                        DetailRow(label = "Responsabilidade", value = "Você deve ao titular da compra")
                    } else if (transaction.paidByUid == currentUserId && transaction.effectiveAmount > myImpact) {
                        DetailRow(label = "A Receber", value = "+${FinanceEngine.formatCurrency(transaction.effectiveAmount - myImpact)}")
                    }
                } else {
                    DetailRow(label = "Valor Efetivo", value = FinanceEngine.formatCurrency(transaction.effectiveAmount))
                }
                if (transaction.estimatedAmount != null) {
                    DetailRow(label = "Valor Estimado", value = FinanceEngine.formatCurrency(transaction.estimatedAmount))
                }
                DetailRow(label = "Categoria", value = transaction.category + (transaction.subcategory?.let { " › $it" } ?: ""))
                DetailRow(label = "Vencimento", value = FinanceEngine.formatFullDate(transaction.dueDate))
                DetailRow(label = "Mês de Competência", value = FinanceEngine.formatMonthLabel(transaction.competenceMonth))
                DetailRow(label = "Forma de Pagamento", value = transaction.paymentMethod.label)
                transaction.splitType?.let {
                    DetailRow(label = "Divisão", value = "${it.emoji} ${it.label}")
                }
                if (transaction.allocations.isNotEmpty()) {
                    DetailRow(
                        label = "Rateio / Participantes",
                        value = transaction.allocations.joinToString(", ") { "${it.name}: ${FinanceEngine.formatCurrency(it.amount)}" }
                    )
                }
                if (!transaction.assignedFriendUid.isNullOrBlank()) {
                    val statusText = if (transaction.acceptanceStatus == "accepted") "Aceito" else "Aguardando aceite"
                    val pct = (transaction.friendSharePct ?: 50.0).toInt()
                    DetailRow(
                        label = "Amigo Vinculado",
                        value = "${transaction.assignedFriendName ?: "Amigo"} ($pct%) • $statusText"
                    )
                }

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status Atual:",
                        style = Typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(
                        status = transaction.status,
                        onClick = onToggleStatus
                    )
                }

                Text(
                    text = "Alternar Status (Paridade Web):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TransactionStatus.entries.forEach { st ->
                        val isSelected = transaction.status == st
                        val chipLabel = when (st) {
                            TransactionStatus.ESTIMATED -> "Estimado"
                            TransactionStatus.PENDING -> "Pendente"
                            TransactionStatus.PAID -> "Pago"
                        }
                        val activeColor = when (st) {
                            TransactionStatus.PAID -> Emerald500
                            TransactionStatus.PENDING -> Amber500
                            TransactionStatus.ESTIMATED -> Cyan500
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) activeColor.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) activeColor else DarkBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val parsedAmount = customActualInput.replace(",", ".").toDoubleOrNull()
                                    onSetStatus(st, parsedAmount)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chipLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeColor else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (transaction.status != TransactionStatus.PAID || transaction.estimatedAmount != null) {
                    OutlinedTextField(
                        value = customActualInput,
                        onValueChange = { customActualInput = it },
                        label = { Text("Valor Real Efetivado (R$)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Botão de Destaque: Editar Lançamento (Paridade Web)
                OutlinedButton(
                    onClick = {
                        val targetId = transaction.id
                        onDismissRequest()
                        onNavigateToEditTransaction(targetId)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Cyan500.copy(alpha = 0.12f),
                        contentColor = Cyan400
                    ),
                    border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.35f))
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Editar Lançamento",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (transaction.status != TransactionStatus.PAID) {
                    Button(
                        onClick = {
                            val parsedAmount = customActualInput.replace(",", ".").toDoubleOrNull()
                            onSetStatus(TransactionStatus.PAID, parsedAmount)
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = DarkBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isIncome) "Confirmar" else "Confirmar Pago",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            onSetStatus(TransactionStatus.PENDING, null)
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Amber500.copy(alpha = 0.2f),
                            contentColor = Amber400
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Voltar p/ Pendente",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDeleteRequest,
                colors = ButtonDefaults.textButtonColors(contentColor = Rose400)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Excluir", maxLines = 1)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = Typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Text(
            text = value,
            style = Typography.labelLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
