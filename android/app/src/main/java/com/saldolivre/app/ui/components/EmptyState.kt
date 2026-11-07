package com.saldolivre.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.ui.theme.*

/**
 * Componente elegante de Estado Vazio (EmptyState) para listagens sem itens.
 * Apresenta ícone em destaque com fundo translúcido, título claro, descrição de apoio
 * e botões primário e secundário opcionais para ações diretas de 1-clique.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    buttonText: String? = null,
    buttonIcon: ImageVector? = null,
    onButtonClick: (() -> Unit)? = null,
    secondaryButtonText: String? = null,
    secondaryButtonIcon: ImageVector? = null,
    onSecondaryButtonClick: (() -> Unit)? = null,
    iconTint: Color = Emerald400,
    iconBackgroundColor: Color = Emerald500.copy(alpha = 0.12f),
    iconBorderColor: Color = Emerald500.copy(alpha = 0.28f)
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Círculo com ícone translúcido sofisticado
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(iconBackgroundColor)
                        .border(1.dp, iconBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Título do estado vazio
                Text(
                    text = title,
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                // Descrição explicativa opcional
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = Typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Botões de ação contextuais opcionais
                val hasPrimary = !buttonText.isNullOrBlank() && onButtonClick != null
                val hasSecondary = !secondaryButtonText.isNullOrBlank() && onSecondaryButtonClick != null

                if (hasPrimary || hasSecondary) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasPrimary) {
                            Button(
                                onClick = onButtonClick!!,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald500,
                                    contentColor = DarkBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                if (buttonIcon != null) {
                                    Icon(
                                        imageVector = buttonIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = buttonText!!,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (hasSecondary) {
                            OutlinedButton(
                                onClick = onSecondaryButtonClick!!,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextSecondary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                if (secondaryButtonIcon != null) {
                                    Icon(
                                        imageVector = secondaryButtonIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = secondaryButtonText!!,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Empty State - Single CTA", showBackground = true, backgroundColor = 0xFF090D16)
@Composable
fun EmptyStateSinglePreview() {
    SaldoLivreTheme {
        EmptyState(
            icon = Icons.Default.ReceiptLong,
            title = "Nenhum lançamento no extrato",
            description = "Você ainda não possui transações registradas para este mês.",
            buttonText = "Novo Lançamento",
            buttonIcon = Icons.Default.Add,
            onButtonClick = {}
        )
    }
}

@Preview(name = "Empty State - Dual CTA", showBackground = true, backgroundColor = 0xFF090D16)
@Composable
fun EmptyStateDualPreview() {
    SaldoLivreTheme {
        EmptyState(
            icon = Icons.Default.ReceiptLong,
            title = "Nenhum resultado filtrado",
            description = "Tente limpar os filtros ativos para visualizar as despesas do mês.",
            buttonText = "Limpar Filtros",
            buttonIcon = Icons.Default.Close,
            onButtonClick = {},
            secondaryButtonText = "Nova Despesa",
            onSecondaryButtonClick = {}
        )
    }
}
