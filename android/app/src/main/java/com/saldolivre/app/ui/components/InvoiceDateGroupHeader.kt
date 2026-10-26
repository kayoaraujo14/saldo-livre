package com.saldolivre.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.ui.theme.*

@Immutable
data class InvoiceDateGroup(
    val dateKey: String,
    val items: List<Transaction>,
    val totalAmount: Double
)

@Composable
fun InvoiceDateGroupHeader(
    dateKey: String,
    totalAmount: Double,
    modifier: Modifier = Modifier
) {
    val header = remember(dateKey) { FinanceEngine.formatNubankDateHeader(dateKey) }
    val formattedTotal = remember(totalAmount) {
        if (totalAmount < 0.0) {
            "- " + FinanceEngine.formatCurrency(-totalAmount)
        } else {
            FinanceEngine.formatCurrency(totalAmount)
        }
    }
    val titleColor = when {
        header.isToday -> Cyan400
        header.isYesterday -> Amber400
        else -> TextPrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = header.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                letterSpacing = 0.5.sp
            )
            header.subtitle?.let { subtitle ->
                Text(
                    text = "· $subtitle",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Text(
            text = formattedTotal,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (totalAmount < 0.0) Cyan400 else TextSecondary
        )
    }
}
