package com.saldolivre.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.theme.TextPrimary

/**
 * Componente de Number Ticker com animação rápida de contagem até o valor alvo.
 * Proporciona sensação fluida e moderna típica de apps fintechs ao carregar ou alternar meses.
 */
@Composable
fun NumberTickerText(
    amount: Double,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSize: TextUnit = 28.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    isCurrency: Boolean = true,
    prefix: String = "",
    suffix: String = "",
    durationMillis: Int = 650,
    style: TextStyle = LocalTextStyle.current
) {
    val animatedValue = remember { Animatable(0f) }

    LaunchedEffect(amount) {
        animatedValue.animateTo(
            targetValue = amount.toFloat(),
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    val displayText = remember(animatedValue.value, isCurrency, prefix, suffix) {
        val currentVal = animatedValue.value.toDouble()
        val formatted = if (isCurrency) {
            FinanceEngine.formatCurrency(currentVal)
        } else {
            String.format(java.util.Locale.US, "%.1f", currentVal)
        }
        "$prefix$formatted$suffix"
    }

    Text(
        text = displayText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

