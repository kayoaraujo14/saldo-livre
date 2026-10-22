package com.saldolivre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.ui.theme.*

private val StatusBadgeShape = RoundedCornerShape(8.dp)
private val PaidColors = Triple(Emerald500.copy(alpha = 0.16f), Emerald400, Emerald500.copy(alpha = 0.4f))
private val PendingColors = Triple(Amber500.copy(alpha = 0.16f), Amber400, Amber500.copy(alpha = 0.4f))
private val EstimatedColors = Triple(Cyan500.copy(alpha = 0.16f), Cyan400, Cyan500.copy(alpha = 0.4f))

@Composable
fun StatusBadge(
    status: TransactionStatus,
    modifier: Modifier = Modifier,
    isIncome: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val (bgColor, textColor, borderColor) = when (status) {
        TransactionStatus.PAID -> PaidColors
        TransactionStatus.PENDING -> PendingColors
        TransactionStatus.ESTIMATED -> EstimatedColors
    }

    val icon = when (status) {
        TransactionStatus.PAID -> Icons.Default.CheckCircle
        TransactionStatus.PENDING -> Icons.Default.Schedule
        TransactionStatus.ESTIMATED -> Icons.Default.Timeline
    }

    val label = when (status) {
        TransactionStatus.PAID -> if (isIncome) "Confirmado • Na Conta" else "Confirmado • Pago"
        TransactionStatus.PENDING -> if (isIncome) "Pendente • A Receber" else "Pendente • A Pagar"
        TransactionStatus.ESTIMATED -> if (isIncome) "Previsto" else "Previsão"
    }

    Row(
        modifier = modifier
            .clip(StatusBadgeShape)
            .background(bgColor, StatusBadgeShape)
            .border(1.dp, borderColor, StatusBadgeShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
