package com.saldolivre.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.ui.theme.*

/**
 * Barra de Navegação Inferior Docked do Saldo Livre.
 *
 * Especialista de UI/UX:
 * - 2 botões na lateral esquerda (Início e Extrato).
 * - 1 botão verde proeminente ao centro (Copiloto IA), elevado e com anel de recorte (cutout).
 * - 2 botões na lateral direita (Cartões e Menu).
 * - Distribuição simétrica e ergonomia otimizada para toque com uma mão (Thumb Zone).
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigateToTab: (BottomNavItem) -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leftItems = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Transactions
    )
    val rightItems = listOf(
        BottomNavItem.Cards,
        BottomNavItem.Menu
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Container da barra de navegação com topo arredondado e borda sutil
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp) // Espaço para projeção superior do botão central de IA
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(DarkSurface.copy(alpha = 0.94f))
                .drawWithContent {
                    drawContent()
                    val strokeWidth = 0.5.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = strokeWidth
                    )
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(58.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dois botões à esquerda (Início, Extrato)
                leftItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    AppBottomNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigateToTab(item) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Espaço central reservado para o botão de Chat elevado
                Spacer(modifier = Modifier.width(68.dp))

                // Dois botões à direita (Cartões, Menu)
                rightItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    AppBottomNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigateToTab(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Botão Central de Chat (Copiloto IA) em Destaque Absoluto
        val chatInteractionSource = remember { MutableInteractionSource() }
        val isPressed by chatInteractionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.91f else 1f,
            animationSpec = tween(durationMillis = 120),
            label = "chatFabScale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .scale(scale)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = Emerald500.copy(alpha = 0.55f),
                    ambientColor = Emerald500.copy(alpha = 0.25f)
                )
                // Anel de corte circular (notch effect) com a cor do background
                .background(DarkBackground, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Emerald400,
                            Emerald500,
                            Emerald600
                        )
                    )
                )
                .size(54.dp)
                .clickable(
                    interactionSource = chatInteractionSource,
                    indication = null,
                    onClick = onOpenChat
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Abrir Copiloto IA",
                tint = DarkBackground,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun AppBottomNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "navItemScale"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.title,
            tint = if (isSelected) Emerald400 else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.title,
            color = if (isSelected) Emerald400 else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
