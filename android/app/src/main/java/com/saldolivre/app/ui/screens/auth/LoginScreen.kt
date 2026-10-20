@file:Suppress("DEPRECATION")

package com.saldolivre.app.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.saldolivre.app.R
import com.saldolivre.app.ui.theme.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (isDemo: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by authViewModel.uiState.collectAsState()

    // Google Sign-In Launcher
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
            }
        } catch (_: ApiException) {
            // Cancelado ou erro tratado pelo GoogleSignIn
        }
    }

    fun startGoogleSignIn() {
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
        } catch (_: Exception) {
            // Em caso de ausência do web client ID no ambiente, o usuário pode usar Email/Senha ou Modo Demo
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            Brush.linearGradient(listOf(Emerald500.copy(alpha = 0.3f), Cyan500.copy(alpha = 0.2f))),
                            RoundedCornerShape(22.dp)
                        )
                        .border(1.dp, Emerald400, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Saldo Livre",
                        tint = Emerald400,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Saldo Livre",
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Gestão Financeira Pessoal & Cartões Compartilhados",
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Card Principal com Abas
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Abas: Entrar vs Criar Conta
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (uiState.mode == AuthMode.SIGN_IN) Emerald500 else DarkSurfaceVariant)
                                    .clickable { authViewModel.setAuthMode(AuthMode.SIGN_IN) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Entrar",
                                    fontSize = 13.sp,
                                    fontWeight = if (uiState.mode == AuthMode.SIGN_IN) FontWeight.Bold else FontWeight.Medium,
                                    color = if (uiState.mode == AuthMode.SIGN_IN) DarkBackground else TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (uiState.mode == AuthMode.SIGN_UP) Emerald500 else DarkSurfaceVariant)
                                    .clickable { authViewModel.setAuthMode(AuthMode.SIGN_UP) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Criar Conta",
                                    fontSize = 13.sp,
                                    fontWeight = if (uiState.mode == AuthMode.SIGN_UP) FontWeight.Bold else FontWeight.Medium,
                                    color = if (uiState.mode == AuthMode.SIGN_UP) DarkBackground else TextSecondary
                                )
                            }
                        }

                        // Mensagem de Erro se houver
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

                        // Campo Nome (Apenas em SIGN_UP)
                        if (uiState.mode == AuthMode.SIGN_UP) {
                            OutlinedTextField(
                                value = uiState.name,
                                onValueChange = { authViewModel.onNameChange(it) },
                                label = { Text("Nome Completo") },
                                placeholder = { Text("Seu nome", color = TextMuted) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TextMuted)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald500,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedContainerColor = DarkBackground,
                                    unfocusedContainerColor = DarkBackground,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }

                        // Campo E-mail
                        OutlinedTextField(
                            value = uiState.email,
                            onValueChange = { authViewModel.onEmailChange(it) },
                            label = { Text("E-mail") },
                            placeholder = { Text("seu@email.com", color = TextMuted) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = TextMuted)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        // Campo Senha
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = { authViewModel.onPasswordChange(it) },
                            label = { Text("Senha") },
                            placeholder = { Text("No mínimo 6 caracteres", color = TextMuted) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted)
                            },
                            trailingIcon = {
                                IconButton(onClick = { authViewModel.togglePasswordVisibility() }) {
                                    Icon(
                                        imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Alternar visibilidade",
                                        tint = TextMuted
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        // Botão Primário (Login ou Cadastro)
                        Button(
                            onClick = {
                                if (uiState.mode == AuthMode.SIGN_IN) {
                                    authViewModel.signInWithEmail(onLoginSuccess)
                                } else {
                                    authViewModel.signUpWithEmail(onLoginSuccess)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald500,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = DarkBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (uiState.mode == AuthMode.SIGN_IN) "Entrar na Conta" else "Criar Conta Segura",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Divisor Elegante
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
                            Text(
                                text = "ou continue com",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
                        }

                        // Botão Google Sign-In
                        Button(
                            onClick = { startGoogleSignIn() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(DarkBackground, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald400)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Entrar com o Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Botão Modo Demonstração (Sem Login)
                        OutlinedButton(
                            onClick = { authViewModel.enterDemoMode(onLoginSuccess) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Purple500.copy(alpha = 0.08f),
                                contentColor = Purple400
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Purple400.copy(alpha = 0.5f))
                            )
                        ) {
                            Row(
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
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Card de Garantia de Privacidade e Isolamento (idêntico à Web)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Garantia de Isolamento e Privacidade",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                        }
                        Text(
                            text = "Gastos pessoais e faturas particulares nunca são exibidos para outros usuários. Apenas despesas explicitamente compartilhadas (50/50 ou no cartão de um para o outro) são refletidas nos saldos mútuos.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Feature Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(Icons.Default.Shield, "Dados 100% isolados", Emerald400),
                        Triple(Icons.Default.CreditCard, "Cartões Compartilhados", Purple400),
                        Triple(Icons.Default.AutoAwesome, "Copiloto IA Gemini", Cyan400)
                    ).forEach { (icon, label, color) ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.4f))
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, fontSize = 9.sp, color = TextMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
