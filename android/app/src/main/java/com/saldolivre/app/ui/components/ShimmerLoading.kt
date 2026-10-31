package com.saldolivre.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.saldolivre.app.ui.theme.*

/**
 * Modificador reutilizável para aplicar efeito de shimmer em qualquer componente.
 */
@Composable
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = DarkSurfaceVariant.copy(alpha = 0.5f),
    highlightColor: Color = DarkBorder.copy(alpha = 0.6f)
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnim"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    return this
        .clip(shape)
        .background(brush)
}

/**
 * Caixa com efeito de shimmer loading.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(modifier = modifier.shimmerEffect(shape = shape))
}

/**
 * Esqueleto de carregamento completo para a tela de Extrato (TransactionsScreen).
 */
@Composable
fun TransactionsSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Seletor de mês skeleton
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        )

        // Barra de busca skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = CircleShape
            )
            ShimmerBox(
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Lista de itens skeleton
        repeat(5) {
            TransactionItemSkeleton()
        }
    }
}

/**
 * Esqueleto de item individual de transação.
 */
@Composable
fun TransactionItemSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone circular
            ShimmerBox(
                modifier = Modifier.size(36.dp),
                shape = CircleShape
            )

            Spacer(Modifier.width(10.dp))

            // Detalhes texto
            Column(Modifier.weight(1f)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(10.dp),
                    shape = RoundedCornerShape(4.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Valor e status
            Column(horizontalAlignment = Alignment.End) {
                ShimmerBox(
                    modifier = Modifier
                        .width(70.dp)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .width(50.dp)
                        .height(14.dp),
                    shape = CircleShape
                )
            }
        }
    }
}

/**
 * Esqueleto de carregamento completo para o Dashboard (DashboardScreen).
 */
@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Seletor de mês
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        )

        // 2. Hero Card de Saldo Livre
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(modifier = Modifier.size(28.dp), shape = RoundedCornerShape(8.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        ShimmerBox(modifier = Modifier.width(110.dp).height(12.dp), shape = RoundedCornerShape(4.dp))
                    }
                    ShimmerBox(modifier = Modifier.width(60.dp).height(18.dp), shape = RoundedCornerShape(6.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                ShimmerBox(modifier = Modifier.width(180.dp).height(32.dp), shape = RoundedCornerShape(6.dp))

                Spacer(modifier = Modifier.height(6.dp))

                ShimmerBox(modifier = Modifier.width(130.dp).height(12.dp), shape = RoundedCornerShape(4.dp))

                Spacer(modifier = Modifier.height(14.dp))

                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // 3. Grid 2x2 de KPIs
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp),
                    shape = RoundedCornerShape(18.dp)
                )
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp),
                    shape = RoundedCornerShape(18.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp),
                    shape = RoundedCornerShape(18.dp)
                )
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp),
                    shape = RoundedCornerShape(18.dp)
                )
            }
        }

        // 4. Últimos Lançamentos Header & Itens
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShimmerBox(modifier = Modifier.width(140.dp).height(18.dp), shape = RoundedCornerShape(4.dp))
            ShimmerBox(modifier = Modifier.width(60.dp).height(14.dp), shape = RoundedCornerShape(4.dp))
        }

        repeat(3) {
            TransactionItemSkeleton()
        }
    }
}

