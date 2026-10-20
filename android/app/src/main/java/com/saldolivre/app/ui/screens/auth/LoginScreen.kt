package com.saldolivre.app.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.saldolivre.app.R
import com.saldolivre.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Salva ou atualiza perfil em /users/{uid} no Cloud Firestore
    suspend fun saveUserProfile(uid: String, emailStr: String, nameStr: String) {
        val userDoc = mapOf(
            "uid" to uid,
            "email" to emailStr,
            "displayName" to nameStr.ifBlank { "Usuário Saldo Livre" },
            "monthlyIncome" to 5000.0
        )
        firestore.collection("users").document(uid)
            .set(userDoc, SetOptions.merge())
            .await()
    }

    // Launcher do Google Sign-In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intentData = result.data
        if (intentData == null) {
            isLoading = false
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(intentData)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                isLoading = true
                errorMessage = null
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { authResult ->
                        val user = authResult.user
                        if (user != null) {
                            coroutineScope.launch {
                                try {
                                    saveUserProfile(
                                        uid = user.uid,
                                        emailStr = user.email ?: "",
                                        nameStr = user.displayName ?: "Usuário Google"
                                    )
                                } catch (_: Exception) {
                                } finally {
                                    isLoading = false
                                    onLoginSuccess()
                                }
                            }
                        } else {
                            isLoading = false
                            onLoginSuccess()
                        }
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        errorMessage = "Falha no Firebase Auth com conta Google: ${e.localizedMessage}"
                    }
            } else {
                isLoading = false
                errorMessage = "Não foi possível obter o ID Token do Google. Verifique a configuração no Firebase."
            }
        } catch (e: ApiException) {
            isLoading = false
            when (e.statusCode) {
                12501 -> {
                    // Usuário cancelou a janela de seleção
                    errorMessage = "Login cancelado pelo usuário."
                }
                10 -> {
                    // DEVELOPER_ERROR
                    errorMessage = "Erro de Configuração (Código 10 - DEVELOPER_ERROR): A chave SHA-1 do aplicativo (${context.packageName}) precisa ser cadastrada no Firebase Console."
                }
                12500 -> {
                    errorMessage = "Erro no Google Play Services (Código 12500). Verifique se o Google Play Services está atualizado."
                }
                7 -> {
                    errorMessage = "Sem conexão com a internet (Código 7 - NETWORK_ERROR)."
                }
                else -> {
                    errorMessage = "Erro no Google Sign-In (Código ${e.statusCode}): ${e.message}"
                }
            }
        } catch (e: Exception) {
            isLoading = false
            errorMessage = "Erro no login Google: ${e.localizedMessage}"
        }
    }

    fun startGoogleSignIn() {
        isLoading = true
        errorMessage = null
        try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            // Realiza signOut assíncrono para sempre reabrir o seletor de contas limpo
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        } catch (e: Exception) {
            isLoading = false
            errorMessage = "Erro ao iniciar Google Sign-In: ${e.localizedMessage}"
        }
    }

    fun handleGuestSignIn() {
        isLoading = true
        errorMessage = null
        auth.signInAnonymously()
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    coroutineScope.launch {
                        try {
                            saveUserProfile(
                                uid = user.uid,
                                emailStr = "convidado@saldolivre.app",
                                nameStr = "Usuário Convidado"
                            )
                        } catch (_: Exception) {
                        } finally {
                            isLoading = false
                            onLoginSuccess()
                        }
                    }
                } else {
                    isLoading = false
                    onLoginSuccess()
                }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = "Falha no login convidado: ${e.localizedMessage}"
            }
    }

    Scaffold(
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Emerald500.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .border(1.dp, Emerald400, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "Saldo Livre",
                        tint = Emerald400,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Saldo Livre",
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Controle Inteligente de Finanças Pessoais e Casal",
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Card de Login Exclusivo via Google
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Acesse sua conta com um clique",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        errorMessage?.let { errorText ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Rose500.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
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

                        // Botão Único Google Sign-In
                        Button(
                            onClick = { startGoogleSignIn() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald500,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = DarkBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(DarkBackground, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("G", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald400)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Entrar com o Google",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        // Botão Secundário Convidado/Demo
                        OutlinedButton(
                            onClick = { handleGuestSignIn() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Continuar como Convidado (Demo)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
