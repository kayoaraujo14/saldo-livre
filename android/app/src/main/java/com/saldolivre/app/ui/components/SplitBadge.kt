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
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.ui.theme.*

@Composable
fun SplitBadge(
    splitType: SplitType?,
    modifier: Modifier = Modifier
) {
    if (splitType == null) return

    val (bgColor, textColor, borderColor) = when (splitType) {
        SplitType.MINE -> Triple(
            DarkSurfaceVariant,
            TextSecondary,
            DarkBorder
        )
        SplitType.SPLIT_50 -> Triple(
            Purple500.copy(alpha = 0.15f),
            Purple400,
            Purple500.copy(alpha = 0.35f)
        )
        SplitType.PARTNER -> Triple(
            Cyan500.copy(alpha = 0.15f),
            Cyan400,
            Cyan500.copy(alpha = 0.35f)
        )
        SplitType.FRIEND -> Triple(
            Emerald500.copy(alpha = 0.15f),
            Emerald400,
            Emerald500.copy(alpha = 0.35f)
        )
        SplitType.EXTERNAL -> Triple(
            Amber500.copy(alpha = 0.15f),
            Amber400,
            Amber500.copy(alpha = 0.35f)
        )
        SplitType.CUSTOM -> Triple(
            DarkSurfaceVariant,
            TextPrimary,
            DarkBorder
        )
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${splitType.emoji} ${splitType.label}",
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
