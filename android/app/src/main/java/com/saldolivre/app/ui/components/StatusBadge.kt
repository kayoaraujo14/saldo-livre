package com.saldolivre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.ui.theme.*

private val StatusBadgeShape = RoundedCornerShape(6.dp)
private val PaidColors = Triple(Emerald500.copy(alpha = 0.15f), Emerald400, Emerald500.copy(alpha = 0.35f))
private val PendingColors = Triple(Amber500.copy(alpha = 0.15f), Amber400, Amber500.copy(alpha = 0.35f))
private val EstimatedColors = Triple(Cyan500.copy(alpha = 0.15f), Cyan400, Cyan500.copy(alpha = 0.35f))

@Composable
fun StatusBadge(
    status: TransactionStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, borderColor) = when (status) {
        TransactionStatus.PAID -> PaidColors
        TransactionStatus.PENDING -> PendingColors
        TransactionStatus.ESTIMATED -> EstimatedColors
    }

    Box(
        modifier = modifier
            .background(bgColor, StatusBadgeShape)
            .border(1.dp, borderColor, StatusBadgeShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
