package com.saldolivre.app.ui.screens.crash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.ui.theme.*

/**
 * Tela de recuperação graciosa do aplicativo Android quando ocorre uma falha crítica.
 * Permite ao usuário tentar recarregar a aplicação ou restaurar o modo demonstração,
 * evitando fechamentos abruptos do aplicativo (crashes fatais).
 */
@Composable
fun CrashFallbackScreen(
    error: Throwable,
    onReload: () -> Unit,
    onSwitchToDemo: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ícone de Alerta com Glow Suave
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(Rose500, Amber500))
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta de erro",
                        tint = Rose400,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Título & Descrição
            Text(
                text = "Ops! Algo deu errado ao carregar",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ocorreu uma falha inesperada na interface. Seus dados estão seguros e você pode restaurar o app com as opções abaixo.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Card com Ações de Recuperação
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurface.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botão Primário: Recarregar Aplicativo
                Button(
                    onClick = onReload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Color(0xFF021A15)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recarregar Aplicativo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Botão Secundário: Modo Demonstração
                OutlinedButton(
                    onClick = onSwitchToDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Purple500.copy(alpha = 0.6f), Cyan500.copy(alpha = 0.6f)))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Purple400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restaurar em Modo Demonstração",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Seção Expansível de Detalhes Técnicos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDetails = !showDetails }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detalhes técnicos do erro",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showDetails,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = error.message ?: error.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Rose400
                        )
                    }
                }
            }
        }
    }
}
