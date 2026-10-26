package com.saldolivre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

private val TransactionItemShape = RoundedCornerShape(14.dp)

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
        categoryDetail.color.copy(alpha = 0.12f)
    }
    val catBorderColor = remember(categoryDetail.color) {
        categoryDetail.color.copy(alpha = 0.25f)
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

    val isPayerAndHasReceivable = !currentUserId.isNullOrBlank() &&
            transaction.paidByUid == currentUserId &&
            transaction.effectiveAmount > myImpact
    val toReceive = if (isPayerAndHasReceivable) transaction.effectiveAmount - myImpact else 0.0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(TransactionItemShape)
            .background(DarkSurface)
            .border(1.dp, itemBorderColor, TransactionItemShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon with circular translucent background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(catBgColor, CircleShape)
                    .border(1.dp, catBorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryDetail.icon,
                    contentDescription = transaction.category,
                    tint = categoryDetail.color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Main Content Area
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // ROW 1: Title (left) & Formatted Amount (right) - Perfectly aligned on same horizontal baseline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.description,
                            style = Typography.titleMedium.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (transaction.installmentNumber != null && transaction.installmentsTotal != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${transaction.installmentNumber}/${transaction.installmentsTotal}",
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formattedAmount,
                        style = Typography.titleMedium.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ROW 2: Metadata (Date, Category, Split) on Left & Status / Receivables on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left metadata badges
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = shortDate,
                            style = Typography.labelSmall.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = TextMuted,
                            maxLines = 1
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

                        // Category Pill Badge
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(catBgColor)
                                .border(1.dp, catBorderColor, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = categoryWithSubcategory,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = categoryDetail.color,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (transaction.splitType != null) {
                            val splitLabel = when {
                                transaction.splitType == SplitType.EXTERNAL && !transaction.externalName.isNullOrBlank() ->
                                    "🏷️ ${transaction.externalName}"
                                transaction.splitType == SplitType.FRIEND && !transaction.assignedFriendName.isNullOrBlank() ->
                                    "🧑‍🤝‍🧑 ${transaction.assignedFriendName}"
                                else -> null
                            }
                            SplitBadge(splitType = transaction.splitType, labelOverride = splitLabel)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right metadata badges (ToReceive + StatusBadge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (toReceive > 0.01) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Emerald500.copy(alpha = 0.12f))
                                    .border(1.dp, Emerald500.copy(alpha = 0.25f), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+${FinanceEngine.formatCurrency(toReceive)} a receber",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Emerald400,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    maxLines = 1
                                )
                            }
                        } else if (isSplit && displayAmount != transaction.effectiveAmount && displayAmount > 0.0) {
                            Text(
                                text = "Total: ${FinanceEngine.formatCurrency(transaction.effectiveAmount)}",
                                fontSize = 10.sp,
                                color = TextMuted,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                maxLines = 1
                            )
                        }

                        StatusBadge(
                            status = transaction.status,
                            onClick = onStatusClick
                        )
                    }
                }

                // Sub-tags: Only auxiliary status (Titular debt or friend acceptance)
                if (!currentUserId.isNullOrBlank()) {
                    val owesTitular = transaction.paidByUid != currentUserId && myImpact > 0.0
                    val isAssignedFriend = transaction.assignedFriendUid == currentUserId

                    if (owesTitular || isAssignedFriend) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (owesTitular) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Amber500.copy(alpha = 0.12f))
                                        .border(1.dp, Amber500.copy(alpha = 0.25f), CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Você deve ao titular",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Amber400,
                                        style = LocalTextStyle.current.copy(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }

                            if (isAssignedFriend) {
                                val isAccepted = transaction.acceptanceStatus == "accepted"
                                val pctStr = (transaction.friendSharePct ?: 50.0).toInt()
                                val accColor = if (isAccepted) Emerald400 else Amber400
                                val accBg = if (isAccepted) Emerald500.copy(alpha = 0.12f) else Amber500.copy(alpha = 0.12f)
                                val accBorder = if (isAccepted) Emerald500.copy(alpha = 0.25f) else Amber500.copy(alpha = 0.25f)

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(accBg)
                                        .border(1.dp, accBorder, CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAccepted) "✓ Aceito ($pctStr%)" else "🕒 Aguarda aceite ($pctStr%)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = accColor,
                                        style = LocalTextStyle.current.copy(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        maxLines = 1
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
