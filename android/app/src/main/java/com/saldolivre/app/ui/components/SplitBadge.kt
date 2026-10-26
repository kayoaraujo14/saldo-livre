package com.saldolivre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.ui.theme.*

private val SplitBadgeShape = CircleShape
private val MineSplitColors = Triple(DarkSurfaceVariant, TextSecondary, DarkBorder)
private val Split50Colors = Triple(Purple500.copy(alpha = 0.12f), Purple400, Purple500.copy(alpha = 0.25f))
private val PartnerSplitColors = Triple(Cyan500.copy(alpha = 0.12f), Cyan400, Cyan500.copy(alpha = 0.25f))
private val FriendSplitColors = Triple(Emerald500.copy(alpha = 0.12f), Emerald400, Emerald500.copy(alpha = 0.25f))
private val ExternalSplitColors = Triple(Amber500.copy(alpha = 0.12f), Amber400, Amber500.copy(alpha = 0.25f))
private val CustomSplitColors = Triple(DarkSurfaceVariant, TextPrimary, DarkBorder)

@Suppress("DEPRECATION")
@Composable
fun SplitBadge(
    splitType: SplitType?,
    modifier: Modifier = Modifier,
    labelOverride: String? = null
) {
    if (splitType == null && labelOverride == null) return

    val (bgColor, textColor, borderColor) = when (splitType) {
        SplitType.MINE -> MineSplitColors
        SplitType.SPLIT_50 -> Split50Colors
        SplitType.PARTNER -> PartnerSplitColors
        SplitType.FRIEND -> FriendSplitColors
        SplitType.EXTERNAL -> ExternalSplitColors
        SplitType.CUSTOM, null -> CustomSplitColors
    }

    val displayLabel = labelOverride ?: "${splitType?.emoji ?: "⚖️"} ${splitType?.label ?: ""}"

    Box(
        modifier = modifier
            .clip(SplitBadgeShape)
            .background(bgColor, SplitBadgeShape)
            .border(1.dp, borderColor, SplitBadgeShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = displayLabel,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1
        )
    }
}
