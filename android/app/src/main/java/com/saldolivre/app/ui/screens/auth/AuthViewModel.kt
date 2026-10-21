package com.saldolivre.app.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.saldolivre.app.SaldoLivreApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
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

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    fun signInWithGoogleIdToken(idToken: String, onSuccess: (isDemo: Boolean) -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    val userRef = firestore.collection("users").document(user.uid)
                    val snap = runCatching { userRef.get().await() }.getOrNull()

                    val userDoc = mutableMapOf<String, Any?>(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "displayName" to (user.displayName ?: user.email ?: "Usuário"),
                        "photoURL" to user.photoUrl?.toString()
                    )
                    if (snap == null || !snap.exists()) {
                        userDoc["monthlyIncome"] = 0.0
                    }

                    userRef.set(userDoc, SetOptions.merge()).await()

                    prefs?.edit()?.putBoolean(PREF_IS_DEMO_MODE, false)?.apply()
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(false)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Falha ao autenticar com o Google."
                        )
                    }
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
        _uiState.update { it.copy(errorMessage = null) }
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
            code == "ERROR_NETWORK_REQUEST_FAILED" || msg.contains("network", ignoreCase = true) ->
                "Sem conexão com a internet. Verifique sua rede e tente novamente."
            else -> "Falha ao autenticar com o Google: $msg"
        }
    }
}
