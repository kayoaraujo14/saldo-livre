package com.saldolivre.app.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.theme.*

data class ChartDataItem(
    val label: String,
    val value: Double,
    val color: Color,
    val sublabel: String? = null
)

enum class ChartType {
    DONUT,
    BAR
}

data class ChartPayload(
    val type: ChartType = ChartType.DONUT,
    val title: String = "Análise Financeira",
    val description: String? = null,
    val totalLabel: String? = null,
    val data: List<ChartDataItem> = emptyList()
)

@Composable
fun DynamicChatChart(
    payload: ChartPayload,
    modifier: Modifier = Modifier
) {
    if (payload.data.isEmpty()) return

    val total = remember(payload.data) { payload.data.sumOf { it.value } }
    val maxValue = remember(payload.data) { payload.data.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0 }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(DarkSurfaceVariant, DarkSurface)
                )
            )
            .border(1.dp, Cyan500.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .animateContentSize()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header do Gráfico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Cyan500.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Cyan400, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (payload.type == ChartType.DONUT) Icons.Default.PieChart else Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = payload.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!payload.description.isNullOrBlank()) {
                            Text(
                                text = payload.description,
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Cyan500.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (payload.type == ChartType.DONUT) "Distribuição" else "Comparativo",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Cyan400,
                        maxLines = 1
                    )
                }
            }

            // Corpo do Gráfico (Rosca Canvas ou Barras)
            if (payload.type == ChartType.DONUT) {
                DonutCanvasView(
                    payload = payload,
                    total = total,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { selectedIndex = it }
                )
            } else {
                BarChartView(
                    payload = payload,
                    total = total,
                    maxValue = maxValue,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { selectedIndex = it }
                )
            }

            // Legenda Interativa Inferior
            FlowLegend(
                data = payload.data,
                total = total,
                selectedIndex = selectedIndex,
                onSelectIndex = { idx ->
                    selectedIndex = if (selectedIndex == idx) null else idx
                }
            )
        }
    }
}

@Composable
private fun DonutCanvasView(
    payload: ChartPayload,
    total: Double,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    val density = LocalDensity.current
    val strokeWidth = with(density) { 22.dp.toPx() }
    val padding = with(density) { 14.dp.toPx() }

    val activeItem = selectedIndex?.let { payload.data.getOrNull(it) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rosca nativa desenhada no Canvas
        Canvas(
            modifier = Modifier
                .size(170.dp)
                .clickable { onSelectIndex(null) }
        ) {
            var currentStartAngle = -90f
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val arcOffset = Offset(padding, padding)

            payload.data.forEachIndexed { index, item ->
                val fraction = if (total > 0) (item.value / total).toFloat() else 0f
                val sweep = fraction * 360f

                val isSelected = selectedIndex == index
                val currentStroke = if (isSelected) strokeWidth * 1.3f else strokeWidth
                val sliceColor = if (selectedIndex == null || isSelected) item.color else item.color.copy(alpha = 0.35f)

                val gap = if (payload.data.size > 1 && sweep > 6f) 3f else 0f
                val adjustedSweep = (sweep - gap).coerceAtLeast(1f)

                drawArc(
                    color = sliceColor,
                    startAngle = currentStartAngle + gap / 2f,
                    sweepAngle = adjustedSweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = currentStroke, cap = StrokeCap.Round)
                )

                currentStartAngle += sweep
            }
        }

        // Texto central dinâmico dentro da rosca
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = activeItem?.label ?: payload.totalLabel ?: "Total Geral",
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = FinanceEngine.formatCurrency(activeItem?.value ?: total),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (activeItem != null && total > 0) {
                val pct = (activeItem.value / total) * 100.0
                Text(
                    text = String.format(java.util.Locale.US, "%.1f%%", pct),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeItem.color,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BarChartView(
    payload: ChartPayload,
    total: Double,
    maxValue: Double,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        payload.data.forEachIndexed { index, item ->
            val fraction = if (maxValue > 0) (item.value / maxValue).toFloat().coerceIn(0.04f, 1f) else 0.04f
            val pctOfTotal = if (total > 0) (item.value / total) * 100.0 else 0.0
            val isSelected = selectedIndex == index

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) DarkSurface.copy(alpha = 0.8f) else Color.Transparent)
                    .clickable { onSelectIndex(if (isSelected) null else index) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(item.color, CircleShape)
                        )
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FinanceEngine.formatCurrency(item.value),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "(%.0f%%)", pctOfTotal),
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Barra de progresso com cor da categoria
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkBorder.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(item.color)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowLegend(
    data: List<ChartDataItem>,
    total: Double,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Dividir em pares ou linhas de chips
            data.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { item ->
                        val originalIndex = data.indexOf(item)
                        val isSelected = selectedIndex == originalIndex
                        val pct = if (total > 0) (item.value / total) * 100.0 else 0.0

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) item.color.copy(alpha = 0.2f) else DarkSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) item.color else DarkBorder.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectIndex(originalIndex) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(item.color, CircleShape)
                                    )
                                    Text(
                                        text = item.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = String.format(java.util.Locale.US, "%.0f%%", pct),
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                    // Preencher slots vazios se houver menos de 3 no chunk
                    if (rowItems.size < 3) {
                        for (i in 0 until (3 - rowItems.size)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
