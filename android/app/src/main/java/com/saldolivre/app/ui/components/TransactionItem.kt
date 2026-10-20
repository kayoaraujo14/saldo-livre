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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val categoryDetail = CategoryRegistry.get(transaction.category)
    val isIncome = transaction.type == TransactionType.INCOME
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DarkSurface)
            .border(1.dp, DarkBorder, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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
                    .background(categoryDetail.color.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, categoryDetail.color.copy(alpha = 0.35f), CircleShape),
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
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (transaction.installmentNumber != null && transaction.installmentsTotal != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${transaction.installmentNumber}/${transaction.installmentsTotal}",
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
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
                        text = transaction.dueDate.takeLast(5).replace("-", "/"),
                        style = Typography.labelSmall,
                        color = TextMuted
                    )

                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = TextMuted
                    )

                    Text(
                        text = transaction.subcategory ?: transaction.category,
                        style = Typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1
                    )

                    if (transaction.splitType != null) {
                        SplitBadge(splitType = transaction.splitType)
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right side: Amount & Status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val prefix = if (isIncome) "+ " else "- "
                val amountColor = if (isIncome) Emerald400 else TextPrimary

                Text(
                    text = prefix + FinanceEngine.formatCurrency(transaction.effectiveAmount),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                StatusBadge(status = transaction.status)
            }
        }
    }
}
