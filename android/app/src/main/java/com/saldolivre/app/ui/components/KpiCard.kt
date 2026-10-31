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

private val KpiCardShape = RoundedCornerShape(18.dp)
private val KpiIconShape = RoundedCornerShape(10.dp)
private val KpiBadgeShape = RoundedCornerShape(6.dp)

@Composable
fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    numericValue: Double? = null,
    subtitle: String? = null,
    highlight: Boolean = false,
    badgeText: String? = null,
    badgeColor: Color? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val backgroundBrush = remember(highlight, accentColor) {
        if (highlight) {
            Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.14f),
                    DarkSurface.copy(alpha = 0.92f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.05f),
                    DarkSurface.copy(alpha = 0.88f)
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
            // Linha Superior: Ícone à esquerda e Badge/Status à direita (evita truncar títulos!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

                if (!badgeText.isNullOrBlank()) {
                    val effBadgeColor = badgeColor ?: accentColor
                    Box(
                        modifier = Modifier
                            .background(effBadgeColor.copy(alpha = 0.12f), KpiBadgeShape)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
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

            // Título do KPI em linha própria (largura total, sem compressão por badges)
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Métrica Principal em destaque tipográfico com Number Ticker opcional
            val effValueColor = valueColor ?: if (highlight) accentColor else TextPrimary
            if (numericValue != null) {
                NumberTickerText(
                    amount = numericValue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = effValueColor
                )
            } else {
                Text(
                    text = value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = effValueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Slot para elementos dinâmicos (barra de progresso, circular gauge, etc.)
            if (content != null) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }

            // Subtítulo descritivo no rodapé do card
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
