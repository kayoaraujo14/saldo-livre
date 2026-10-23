@file:Suppress("DEPRECATION")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.saldolivre.app.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.saldolivre.app.R
import com.saldolivre.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (isDemo: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by authViewModel.uiState.collectAsState()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intentData = result.data ?: return@rememberLauncherForActivityResult
        val task = GoogleSignIn.getSignedInAccountFromIntent(intentData)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                authViewModel.signInWithGoogleIdToken(idToken, onLoginSuccess)
            } else {
                authViewModel.setError("Token de autenticação Google indisponível.")
            }
        } catch (e: ApiException) {
            // 12501 = SIGN_IN_CANCELLED (usuário fechou o seletor de contas)
            if (e.statusCode != 12501) {
                authViewModel.setError("Não foi possível autenticar com o Google (código ${e.statusCode}).")
            }
        }
    }

    fun startGoogleSignIn() {
        authViewModel.clearError()
        try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            val client = GoogleSignIn.getClient(context, gso)
            client.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(client.signInIntent)
            }
        } catch (e: Exception) {
            authViewModel.setError("Configuração Google Sign-In indisponível neste dispositivo.")
        }
    }

    Scaffold(
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Brand Header (Paridade Visual com LoginScreen.tsx da Web)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(listOf(Emerald500, Cyan400)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Saldo Livre",
                            tint = Emerald400,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Saldo Livre",
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Gestão Financeira",
                    fontSize = 14.sp,
                    color = TextSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Login Card Principal
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(DarkBorder)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Cabeçalho do Card
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Acesse sua Conta",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Conecte-se com sua conta Google",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Mensagem de Erro (se houver)
                        uiState.errorMessage?.let { errorText ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Rose500.copy(alpha = 0.15f))
                                    .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = errorText,
                                    color = Rose400,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Ações Exclusivas: Google Sign-In e Modo Demo
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Botão Primário: Entrar com Google
                            Button(
                                onClick = { startGoogleSignIn() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Conectando com o Google...",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .background(DarkBackground, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "G",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Emerald400
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Entrar com Google",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            // 2. Botão Secundário: Experimentar Modo Demo
                            OutlinedButton(
                                onClick = { authViewModel.enterDemoMode(onLoginSuccess) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                enabled = !uiState.isLoading,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkSurfaceVariant.copy(alpha = 0.6f),
                                    contentColor = TextPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(DarkBorder)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Purple400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Experimentar Modo Demonstração (Sem Login)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }

                        // Garantia de Isolamento e Privacidade (Sem Box-in-Box, sutil e posicionado livremente)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier
                                    .size(15.dp)
                                    .padding(top = 1.dp)
                            )
                            Text(
                                text = buildAnnotatedString {
                                    append("Gastos pessoais e faturas particulares ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary.copy(alpha = 0.85f))) {
                                        append("nunca são exibidos")
                                    }
                                    append(" para outros usuários. Apenas despesas explicitamente compartilhadas (50/50 ou no cartão de um para o outro) são refletidas nos saldos mútuos.")
                                },
                                fontSize = 11.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Feature Highlights Carousel (Design limpo, paginado com indicadores)
                val featureHighlights = remember {
                    listOf(
                        "Dados 100% Isolados",
                        "Cartões Compartilhados",
                        "Copiloto Gemini IA"
                    )
                }
                val pagerState = rememberPagerState(pageCount = { featureHighlights.size })
                val coroutineScope = rememberCoroutineScope()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        Text(
                            text = featureHighlights[page],
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Indicadores (Três pontinhos)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pagerState.pageCount) { iteration ->
                            val isSelected = pagerState.currentPage == iteration
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Emerald400 else TextSecondary.copy(alpha = 0.3f)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(iteration)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
