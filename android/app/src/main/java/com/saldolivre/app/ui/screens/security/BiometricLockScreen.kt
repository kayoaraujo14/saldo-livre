package com.saldolivre.app.ui.screens.security

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.saldolivre.app.data.security.AppSecurityManager
import com.saldolivre.app.data.security.BiometricAuthResult
import com.saldolivre.app.ui.theme.*

/**
 * Tela de bloqueio biométrico (Biometric Lock Screen) do Saldo Livre.
 * Protege os dados financeiros confidenciais quando o app é iniciado ou retorna de inatividade.
 */
@Composable
fun BiometricLockScreen(
    userName: String? = null,
    userEmail: String? = null,
    onUnlocked: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val securityManager = remember { AppSecurityManager.getInstance(context) }

    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    // Animação de pulso suave no ícone biométrico
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    fun triggerBiometric() {
        if (activity == null) return
        authErrorMessage = null
        securityManager.authenticate(
            activity = activity,
            title = "Desbloquear Saldo Livre",
            subtitle = "Confirme sua identidade para acessar seu saldo e movimentações",
            onResult = { result ->
                when (result) {
                    is BiometricAuthResult.Success -> {
                        authErrorMessage = null
                        onUnlocked()
                    }
                    is BiometricAuthResult.Error -> {
                        // Se não foi cancelado propositalmente pelo usuário
                        if (result.errorCode != 10 && result.errorCode != 13) {
                            authErrorMessage = result.errString
                        }
                    }
                    is BiometricAuthResult.Failed -> {
                        authErrorMessage = "Biometria não reconhecida. Tente novamente."
                    }
                }
            }
        )
    }

    // Dispara o prompt biométrico automaticamente ao exibir a tela
    LaunchedEffect(Unit) {
        triggerBiometric()
    }

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
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ícone Biométrico com Glow e Efeito de Pulso
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .background(
                        Brush.radialGradient(
                            listOf(Emerald500.copy(alpha = 0.25f), Color.Transparent)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                        .border(1.5.dp, Emerald500.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometria",
                        tint = Emerald400,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Saldo Livre Bloqueado",
                style = Typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Seus dados financeiros e patrimoniais estão protegidos.",
                style = Typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )

            // Identificação do Usuário
            if (!userName.isNullOrBlank() || !userEmail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = CircleShape,
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Emerald500.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (userName ?: userEmail ?: "U").take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Emerald400,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = userName ?: userEmail.orEmpty(),
                            style = Typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Mensagem de Erro (se houver)
            if (authErrorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Rose500.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Rose500.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authErrorMessage!!,
                        style = Typography.bodySmall,
                        color = Rose400,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botão Principal: Desbloquear com Biometria / PIN
            Button(
                onClick = { triggerBiometric() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald500,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Desbloquear com Biometria / PIN",
                    style = Typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botão Secundário: Encerrar Sessão / Trocar de Conta
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextMuted
                ),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sair ou Trocar de Conta",
                    style = Typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}
