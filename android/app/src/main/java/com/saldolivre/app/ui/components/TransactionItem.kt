package com.saldolivre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceStatus
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

private val TransactionItemShape = RoundedCornerShape(14.dp)

private fun itemStatusColor(status: TransactionStatus): Color = when (status) {
    TransactionStatus.ESTIMATED -> Amber400
    TransactionStatus.PENDING -> CardInterOrange
    TransactionStatus.PAID -> Emerald400
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
    allTransactions: List<Transaction> = emptyList(),
    showDate: Boolean = false,
    onClick: (() -> Unit)? = null,
    onStatusClick: (() -> Unit)? = null
) {
    if (transaction.id.startsWith("invoice_")) {
        val meta = transaction.invoiceMeta
        val isPaid = transaction.status == TransactionStatus.PAID
        val isClosed = meta?.invoiceStatus == InvoiceStatus.CLOSED
        val isOwner = meta?.isOwner ?: (meta?.card?.ownerUid == currentUserId)

        val statusBg = if (isPaid) Emerald500.copy(alpha = 0.15f) else if (isClosed) Amber500.copy(alpha = 0.15f) else Cyan400.copy(alpha = 0.15f)
        val statusBorder = if (isPaid) Emerald500.copy(alpha = 0.35f) else if (isClosed) Amber500.copy(alpha = 0.35f) else Cyan400.copy(alpha = 0.35f)
        val statusText = if (isPaid) Emerald400 else if (isClosed) Amber400 else Cyan400
        val statusLabel = if (isPaid) "Fatura Paga" else if (isClosed) "Fatura Fechada" else "Fatura Aberta"

        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(TransactionItemShape)
                .background(DarkSurface)
                .border(1.dp, if (isPaid) Emerald500.copy(alpha = 0.3f) else Cyan400.copy(alpha = 0.3f), TransactionItemShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cyan400.copy(alpha = 0.14f))
                    .border(1.dp, Cyan400.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = "Fatura de Cartão",
                    tint = Cyan400,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    meta?.itemsCount?.let { count ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Cyan400.copy(alpha = 0.14f))
                                .border(1.dp, Cyan400.copy(alpha = 0.3f), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$count compras",
                                fontSize = 9.sp,
                                color = Cyan400,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = transaction.subcategory ?: "Vencimento dia ${meta?.card?.dueDay ?: ""}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(statusBg)
                            .border(1.dp, statusBorder, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 9.sp,
                            color = statusText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "- " + FinanceEngine.formatCurrency(transaction.effectiveAmount),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (isOwner) "Minha cota" else "Sua parte",
                    fontSize = 10.sp,
                    color = Emerald400,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }
    val categoryDetail = remember(transaction.category) {
        CategoryRegistry.get(transaction.category)
    }
    val catBgColor = remember(categoryDetail.color) {
        categoryDetail.color.copy(alpha = 0.14f)
    }
    val catBorderColor = remember(categoryDetail.color) {
        categoryDetail.color.copy(alpha = 0.28f)
    }

    val isIncome = transaction.type == TransactionType.INCOME
    val isPaid = transaction.status == TransactionStatus.PAID
    val stColor = itemStatusColor(transaction.status)

    val myImpact = remember(transaction, currentUserId, allTransactions) {
        if (!currentUserId.isNullOrBlank()) {
            FinanceEngine.impactFor(transaction, currentUserId, allTransactions)
        } else {
            transaction.effectiveAmount
        }
    }

    val isSplit = remember(transaction, myImpact) {
        kotlin.math.abs(myImpact - transaction.effectiveAmount) > 0.01 ||
                (transaction.splitType != null && transaction.splitType != SplitType.MINE) ||
                !transaction.assignedFriendUid.isNullOrBlank()
    }

    val displayAmount = if (!currentUserId.isNullOrBlank() && isSplit) myImpact else transaction.effectiveAmount
    val isRefund = transaction.isRefund || displayAmount < 0.0

    val formattedAmount = remember(displayAmount, isIncome, isRefund) {
        if (isRefund) {
            "+ " + FinanceEngine.formatCurrency(kotlin.math.abs(displayAmount))
        } else {
            val prefix = if (isIncome) "+ " else "- "
            prefix + FinanceEngine.formatCurrency(kotlin.math.abs(displayAmount))
        }
    }
    val amountColor = if (isRefund) Cyan400 else if (isIncome) Emerald400 else TextPrimary
    val itemBorderColor = if (isPaid) Emerald500.copy(alpha = 0.25f) else DarkBorder.copy(alpha = 0.6f)

    val isPayerAndHasReceivable = !isRefund && !currentUserId.isNullOrBlank() &&
            transaction.paidByUid == currentUserId &&
            transaction.effectiveAmount > myImpact
    val toReceive = if (isPayerAndHasReceivable) transaction.effectiveAmount - myImpact else 0.0
    val isSharedAndDifferent = isSplit && kotlin.math.abs(displayAmount - transaction.effectiveAmount) > 0.01

    val thirdParty = remember(transaction) {
        transaction.allocations.firstOrNull { it.type == "EXTERNAL" }?.name
            ?: transaction.assignedFriendName
            ?: transaction.externalName
    }

    val owesTitular = !currentUserId.isNullOrBlank() && transaction.paidByUid != currentUserId && myImpact > 0.0

    val installmentTag = remember(transaction) {
        transaction.installmentTag ?: if (transaction.installmentNumber != null && transaction.installmentsTotal != null) {
            "${transaction.installmentNumber}/${transaction.installmentsTotal}"
        } else null
    }

    val shortDate = remember(transaction.dueDate) {
        FinanceEngine.formatShortDate(transaction.dueDate)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TransactionItemShape)
            .background(DarkSurface)
            .border(1.dp, itemBorderColor, TransactionItemShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Ícone da Categoria Circular Translúcido
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(catBgColor)
                .border(1.dp, catBorderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryDetail.icon,
                contentDescription = transaction.category,
                tint = categoryDetail.color,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        // 2. Coluna Central de Conteúdo
        Column(Modifier.weight(1f)) {
            // Linha 1: Título + Badge de Parcela (Cyan) + Badge de Terceiro/Rateio (Amber)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                installmentTag?.let { tag ->
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Cyan400.copy(alpha = 0.14f))
                            .border(1.dp, Cyan400.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 9.sp,
                            color = Cyan400,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!thirdParty.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Amber500.copy(alpha = 0.14f))
                            .border(1.dp, Amber500.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "🏷️ $thirdParty",
                            fontSize = 9.sp,
                            color = Amber400,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Linha 2: Categoria › Subcategoria (e info auxiliar se houver)
            val categorySubtitle = remember(transaction.category, transaction.subcategory) {
                listOfNotNull(
                    transaction.category.takeIf { it.isNotBlank() },
                    transaction.subcategory?.takeIf { it.isNotBlank() && !it.equals(transaction.category, ignoreCase = true) }
                ).joinToString(" › ")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val fullSubtitle = buildString {
                    if (showDate && shortDate.isNotBlank()) {
                        append(shortDate)
                        if (categorySubtitle.isNotBlank()) append(" · ")
                    }
                    append(categorySubtitle)
                }

                Text(
                    text = fullSubtitle,
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (owesTitular) {
                    Text(
                        text = "· deve ao titular",
                        fontSize = 10.sp,
                        color = Amber400,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                } else if (!transaction.acceptanceStatus.isNullOrBlank() && transaction.assignedFriendUid == currentUserId) {
                    val isAccepted = transaction.acceptanceStatus == "accepted"
                    Text(
                        text = if (isAccepted) "· ✓ aceito" else "· 🕒 pendente",
                        fontSize = 10.sp,
                        color = if (isAccepted) Emerald400 else Amber400,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // 3. Coluna Direita: Valor e Status
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formattedAmount,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                maxLines = 1
            )

            if (toReceive > 0.01) {
                Text(
                    text = "+${FinanceEngine.formatCurrency(toReceive)} a receber",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Emerald400,
                    maxLines = 1
                )
            } else if (isSharedAndDifferent && transaction.effectiveAmount > 0.0) {
                Text(
                    text = "de " + FinanceEngine.formatCurrency(transaction.effectiveAmount),
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(stColor.copy(alpha = 0.14f))
                    .then(if (onStatusClick != null) Modifier.clickable(onClick = onStatusClick) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = transaction.status.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = stColor
                )
            }
        }
    }
}
