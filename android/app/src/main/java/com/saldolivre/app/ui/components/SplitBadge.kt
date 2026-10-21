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

private val SplitBadgeShape = RoundedCornerShape(6.dp)
private val MineSplitColors = Triple(DarkSurfaceVariant, TextSecondary, DarkBorder)
private val Split50Colors = Triple(Purple500.copy(alpha = 0.15f), Purple400, Purple500.copy(alpha = 0.35f))
private val PartnerSplitColors = Triple(Cyan500.copy(alpha = 0.15f), Cyan400, Cyan500.copy(alpha = 0.35f))
private val FriendSplitColors = Triple(Emerald500.copy(alpha = 0.15f), Emerald400, Emerald500.copy(alpha = 0.35f))
private val ExternalSplitColors = Triple(Amber500.copy(alpha = 0.15f), Amber400, Amber500.copy(alpha = 0.35f))
private val CustomSplitColors = Triple(DarkSurfaceVariant, TextPrimary, DarkBorder)

@Composable
fun SplitBadge(
    splitType: SplitType?,
    modifier: Modifier = Modifier
) {
    if (splitType == null) return

    val (bgColor, textColor, borderColor) = when (splitType) {
        SplitType.MINE -> MineSplitColors
        SplitType.SPLIT_50 -> Split50Colors
        SplitType.PARTNER -> PartnerSplitColors
        SplitType.FRIEND -> FriendSplitColors
        SplitType.EXTERNAL -> ExternalSplitColors
        SplitType.CUSTOM -> CustomSplitColors
    }

    Box(
        modifier = modifier
            .background(bgColor, SplitBadgeShape)
            .border(1.dp, borderColor, SplitBadgeShape)
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
