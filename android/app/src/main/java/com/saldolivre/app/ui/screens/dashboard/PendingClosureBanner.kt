package com.saldolivre.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.theme.*

private val BannerBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF064E3B), // Dark Emerald
        Color(0xFF0F3E3B)
    )
)

private val BannerBorder = Emerald500.copy(alpha = 0.5f)

@Composable
fun PendingClosureBanner(
    monthKey: String,
    surplusAmount: Double,
    onOpenClosure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthName = FinanceEngine.formatMonthLabel(monthKey)
    val formattedSurplus = FinanceEngine.formatCurrency(surplusAmount)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenClosure),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BannerBorder),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BannerBrush)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Emerald500.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Sobra de $monthName 🎉",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$formattedSurplus disponível",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Distribua entre Saldo Livre, Investir e Colchão",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onOpenClosure,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Destinar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
