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
private val KpiBadgeShape = RoundedCornerShape(6.dp)

@Composable
fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlight: Boolean = false,
    badgeText: String? = null,
    badgeColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val backgroundBrush = remember(highlight, accentColor) {
        if (highlight) {
            Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.12f),
                    DarkSurface.copy(alpha = 0.90f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.04f),
                    DarkSurface.copy(alpha = 0.85f)
                )
            )
        }
    }

    val borderColor = remember(highlight, accentColor) {
        if (highlight) accentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
    }
    val iconBgColor = remember(accentColor) { accentColor.copy(alpha = 0.12f) }

    Box(
        modifier = modifier
            .clip(KpiCardShape)
            .background(backgroundBrush)
            .border(1.dp, borderColor, KpiCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(iconBgColor, KpiIconShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!badgeText.isNullOrBlank()) {
                    val effBadgeColor = badgeColor ?: accentColor
                    Box(
                        modifier = Modifier
                            .background(effBadgeColor.copy(alpha = 0.12f), KpiBadgeShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = effBadgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) accentColor else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (content != null) {
                content()
            }

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
