package com.saldolivre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.ui.theme.*

private val KpiCardShape = RoundedCornerShape(16.dp)
private val KpiIconShape = RoundedCornerShape(10.dp)
private val DefaultSurfaceBrush = Brush.verticalGradient(colors = listOf(DarkSurface, DarkSurface))

@Composable
fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val backgroundBrush = remember(highlight, accentColor) {
        if (highlight) {
            Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.12f),
                    DarkSurface
                )
            )
        } else {
            DefaultSurfaceBrush
        }
    }

    val borderColor = remember(highlight, accentColor) {
        if (highlight) accentColor.copy(alpha = 0.45f) else DarkBorder
    }
    val iconBgColor = remember(accentColor) { accentColor.copy(alpha = 0.15f) }
    val iconBorderColor = remember(accentColor) { accentColor.copy(alpha = 0.3f) }

    Box(
        modifier = modifier
            .clip(KpiCardShape)
            .background(backgroundBrush)
            .border(1.dp, borderColor, KpiCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = Typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconBgColor, KpiIconShape)
                        .border(1.dp, iconBorderColor, KpiIconShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = Typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) accentColor else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = Typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
