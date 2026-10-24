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
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

private val TransactionItemShape = RoundedCornerShape(14.dp)
private val InstallmentBadgeShape = RoundedCornerShape(4.dp)

@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
    allTransactions: List<Transaction> = emptyList(),
    onClick: (() -> Unit)? = null,
    onStatusClick: (() -> Unit)? = null
) {
    val categoryDetail = remember(transaction.category) {
        CategoryRegistry.get(transaction.category)
    }
    val catBgColor = remember(categoryDetail.color) {
        categoryDetail.color.copy(alpha = 0.15f)
    }
    val catBorderColor = remember(categoryDetail.color) {
        categoryDetail.color.copy(alpha = 0.35f)
    }
    val isIncome = transaction.type == TransactionType.INCOME
    val isPaid = transaction.status == com.saldolivre.app.data.model.TransactionStatus.PAID
    val shortDate = remember(transaction.dueDate) {
        FinanceEngine.formatShortDate(transaction.dueDate)
    }

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

    val displayAmount = if (!currentUserId.isNullOrBlank() && isSplit) myImpact else transaction.effectiveAmount

    val formattedAmount = remember(displayAmount, isIncome) {
        val prefix = if (isIncome) "+ " else "- "
        prefix + FinanceEngine.formatCurrency(displayAmount)
    }
    val amountColor = if (isIncome) Emerald400 else TextPrimary
    val itemBorderColor = if (isPaid) Emerald500.copy(alpha = 0.25f) else DarkBorder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(TransactionItemShape)
            .background(DarkSurface)
            .border(1.dp, itemBorderColor, TransactionItemShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon with tinted background
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(catBgColor, CircleShape)
                    .border(1.dp, catBorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryDetail.icon,
                    contentDescription = transaction.category,
                    tint = categoryDetail.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.description,
                        style = Typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (transaction.installmentNumber != null && transaction.installmentsTotal != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(DarkSurfaceVariant, InstallmentBadgeShape)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${transaction.installmentNumber}/${transaction.installmentsTotal}",
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = shortDate,
                        style = Typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1
                    )

                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = TextMuted
                    )

                    val categoryWithSubcategory = remember(transaction.category, transaction.subcategory) {
                        if (!transaction.subcategory.isNullOrBlank() &&
                            !transaction.subcategory.equals(transaction.category, ignoreCase = true)
                        ) {
                            "${transaction.category} › ${transaction.subcategory}"
                        } else {
                            transaction.category
                        }
                    }

                    Text(
                        text = categoryWithSubcategory,
                        style = Typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (transaction.splitType != null) {
                        SplitBadge(splitType = transaction.splitType)
                    }
                }

                // Sub-tags: Friend and Payer responsibility
                if (!currentUserId.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (transaction.paidByUid != currentUserId && myImpact > 0.0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF451A03).copy(alpha = 0.7f))
                                    .border(1.dp, Color(0xFF9A3412).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "Você deve ao titular",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFDBA74),
                                    maxLines = 1
                                )
                            }
                        } else if (transaction.paidByUid == currentUserId && transaction.effectiveAmount > myImpact) {
                            val toReceive = transaction.effectiveAmount - myImpact
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Emerald500.copy(alpha = 0.15f))
                                    .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "+${FinanceEngine.formatCurrency(toReceive)} a receber",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Emerald400,
                                    maxLines = 1
                                )
                            }
                        }

                        if (transaction.assignedFriendUid == currentUserId) {
                            val isAccepted = transaction.acceptanceStatus == "accepted"
                            val pctStr = (transaction.friendSharePct ?: 50.0).toInt()
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isAccepted) Emerald500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f))
                                    .border(1.dp, if (isAccepted) Emerald500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = if (isAccepted) "✓ Aceito ($pctStr%)" else "🕒 Aguarda aceite ($pctStr%)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isAccepted) Emerald400 else Amber400,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right side: Amount & Status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formattedAmount,
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isSplit && displayAmount != transaction.effectiveAmount && displayAmount > 0.0) {
                    Text(
                        text = "Total: ${FinanceEngine.formatCurrency(transaction.effectiveAmount)}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                StatusBadge(
                    status = transaction.status,
                    isIncome = isIncome,
                    onClick = onStatusClick
                )
            }
        }
    }
}
