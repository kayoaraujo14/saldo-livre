package com.saldolivre.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.ui.theme.*

/**
 * Componente moderno e reutilizável de Chip de Seleção para listas de rolagem horizontal.
 * Oferece suporte a ícones, indicação de cor de categoria/método e microinteração suave de seleção.
 */
@Composable
fun SelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = Emerald400,
    leadingDotColor: Color? = null,
    height: Dp = 38.dp
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.18f) else DarkSurface,
        animationSpec = tween(durationMillis = 180),
        label = "chipBgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.55f) else DarkBorder,
        animationSpec = tween(durationMillis = 180),
        label = "chipBorderColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) accentColor else TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "chipTextColor"
    )

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            } else if (leadingDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(leadingDotColor)
                )
            }

            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

