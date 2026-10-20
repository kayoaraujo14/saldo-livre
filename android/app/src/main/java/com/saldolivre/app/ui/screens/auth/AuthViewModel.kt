package com.saldolivre.app.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.saldolivre.app.SaldoLivreApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val appContext: Context? = runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val prefs = appContext?.getSharedPreferences("saldo_livre_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_IS_DEMO_MODE = "pref_is_demo_mode"
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setAuthMode(mode: AuthMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun signInWithEmail(onSuccess: (isDemo: Boolean) -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor, preencha o e-mail e a senha.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user
                if (user != null) {
                    prefs?.edit()?.putBoolean(PREF_IS_DEMO_MODE, false)?.apply()
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(false)
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Falha ao autenticar usuário.") }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = formatFirebaseAuthError(e)
                    )
                }
            }
        }
    }

    fun signUpWithEmail(onSuccess: (isDemo: Boolean) -> Unit) {
        val name = _uiState.value.name.trim()
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor, preencha todos os campos para criar sua conta.") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "A senha deve conter no mínimo 6 caracteres.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user
                if (user != null) {
                    // Atualiza o perfil no Firebase Auth
                    user.updateProfile(userProfileChangeRequest {
                        displayName = name
                    }).await()

                    // Salva documento em /users/{uid} no Firestore
                    val userDoc = mapOf(
                        "uid" to user.uid,
                        "email" to email,
                        "displayName" to name,
                        "monthlyIncome" to 5000.0,
                        "createdAt" to System.currentTimeMillis()
                    )
                    firestore.collection("users").document(user.uid)
                        .set(userDoc, SetOptions.merge())
                        .await()

                    prefs?.edit()?.putBoolean(PREF_IS_DEMO_MODE, false)?.apply()
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(false)
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível criar a conta.") }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = formatFirebaseAuthError(e)
                    )
                }
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String, onSuccess: (isDemo: Boolean) -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    val userDoc = mapOf(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "displayName" to (user.displayName ?: "Usuário Google"),
                        "photoURL" to (user.photoUrl?.toString() ?: ""),
                        "monthlyIncome" to 5000.0
                    )
                    firestore.collection("users").document(user.uid)
                        .set(userDoc, SetOptions.merge())
                        .await()

                    prefs?.edit()?.putBoolean(PREF_IS_DEMO_MODE, false)?.apply()
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(false)
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Falha ao autenticar com o Google.") }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = formatFirebaseAuthError(e)
                    )
                }
            }
        }
    }

    fun enterDemoMode(onSuccess: (isDemo: Boolean) -> Unit) {
        prefs?.edit()?.putBoolean(PREF_IS_DEMO_MODE, true)?.apply()
        onSuccess(true)
    }

    fun signOut(onSignedOut: () -> Unit) {
        prefs?.edit()?.putBoolean(PREF_IS_DEMO_MODE, false)?.apply()
        auth.signOut()
        _uiState.update { AuthUiState() }
        onSignedOut()
    }

    private fun formatFirebaseAuthError(e: Exception): String {
        val code = (e as? FirebaseAuthException)?.errorCode ?: ""
        val msg = e.localizedMessage ?: e.message ?: "Erro desconhecido"

        return when {
            code == "ERROR_INVALID_EMAIL" || msg.contains("invalid-email") ->
                "O formato do e-mail informado é inválido."
            code == "ERROR_WRONG_PASSWORD" || msg.contains("wrong-password") || msg.contains("invalid-credential") ->
                "E-mail ou senha incorretos. Por favor, tente novamente."
            code == "ERROR_USER_NOT_FOUND" || msg.contains("user-not-found") ->
                "Nenhuma conta cadastrada com este e-mail."
            code == "ERROR_EMAIL_ALREADY_IN_USE" || msg.contains("email-already-in-use") ->
                "Este e-mail já está em uso por outra conta."
            code == "ERROR_WEAK_PASSWORD" || msg.contains("weak-password") ->
                "A senha é muito fraca. Digite pelo menos 6 caracteres."
            code == "ERROR_NETWORK_REQUEST_FAILED" || msg.contains("network") ->
                "Sem conexão com a internet. Verifique sua rede e tente novamente."
            else -> "Erro na autenticação: $msg"
        }
    }
}
