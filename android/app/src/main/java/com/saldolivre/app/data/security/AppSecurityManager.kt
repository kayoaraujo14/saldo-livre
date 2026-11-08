package com.saldolivre.app.data.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

sealed class BiometricAuthResult {
    object Success : BiometricAuthResult()
    data class Error(val errorCode: Int, val errString: String) : BiometricAuthResult()
    object Failed : BiometricAuthResult()
}

/**
 * Gerenciador nativo de segurança e privacidade do Saldo Livre:
 * - Autenticação Biométrica e fallback para PIN/Padrão (BiometricPrompt)
 * - Bloqueio automático de aplicativo por inatividade (tempo em segundo plano)
 * - Blindagem contra captura de tela e miniatura na multitarefa (FLAG_SECURE)
 */
class AppSecurityManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var lastBackgroundTimeMs: Long = 0L

    companion object {
        const val PREFS_NAME = "saldo_livre_security_prefs"
        const val KEY_BIOMETRIC_ENABLED = "pref_biometric_enabled"
        const val KEY_LOCK_TIMEOUT_MINUTES = "pref_lock_timeout_minutes"
        const val KEY_FLAG_SECURE_ENABLED = "pref_flag_secure_enabled"

        const val DEFAULT_TIMEOUT_MINUTES = 2

        @Volatile
        private var instance: AppSecurityManager? = null

        fun getInstance(context: Context): AppSecurityManager {
            return instance ?: synchronized(this) {
                instance ?: AppSecurityManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Função pura para testar a decisão de bloqueio por inatividade.
         * @param elapsedMillis Tempo decorrido desde que o app foi para segundo plano
         * @param timeoutMinutes Minutos configurados para tolerância (0 = Imediato)
         * @param isBiometricEnabled Se a proteção biométrica está ativada pelo usuário
         */
        fun shouldLockOnForeground(
            elapsedMillis: Long,
            timeoutMinutes: Int,
            isBiometricEnabled: Boolean
        ): Boolean {
            if (!isBiometricEnabled) return false
            if (timeoutMinutes <= 0) return true
            val timeoutMillis = timeoutMinutes * 60 * 1000L
            return elapsedMillis >= timeoutMillis
        }
    }

    // --------------------------------------------------------------------------
    // PREFERÊNCIAS DE SEGURANÇA
    // --------------------------------------------------------------------------

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()
            if (!value) {
                _isAppLocked.value = false
            }
        }

    var lockTimeoutMinutes: Int
        get() = prefs.getInt(KEY_LOCK_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_LOCK_TIMEOUT_MINUTES, value).apply()
        }

    var isFlagSecureEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLAG_SECURE_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FLAG_SECURE_ENABLED, value).apply()
        }

    // --------------------------------------------------------------------------
    // DISPONIBILIDADE BIOMÉTRICA
    // --------------------------------------------------------------------------

    fun checkBiometricStatus(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG
        }

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun isBiometricAvailable(): Boolean {
        val status = checkBiometricStatus()
        return status == BiometricStatus.AVAILABLE || status == BiometricStatus.NOT_ENROLLED
    }

    // --------------------------------------------------------------------------
    // CICLO DE VIDA DO APLICATIVO & BLOQUEIO
    // --------------------------------------------------------------------------

    fun onAppBackgrounded() {
        lastBackgroundTimeMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (!isBiometricEnabled) return

        if (lastBackgroundTimeMs > 0L) {
            val elapsed = System.currentTimeMillis() - lastBackgroundTimeMs
            if (shouldLockOnForeground(elapsed, lockTimeoutMinutes, isBiometricEnabled)) {
                _isAppLocked.value = true
            }
        }
    }

    fun lockAppImmediately() {
        if (isBiometricEnabled) {
            _isAppLocked.value = true
        }
    }

    fun unlockApp() {
        _isAppLocked.value = false
        lastBackgroundTimeMs = System.currentTimeMillis()
    }

    // --------------------------------------------------------------------------
    // BLINDAGEM DE TELA (FLAG_SECURE)
    // --------------------------------------------------------------------------

    fun applyFlagSecure(activity: Activity) {
        if (isFlagSecureEnabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // --------------------------------------------------------------------------
    // PROMPT DE AUTENTICAÇÃO
    // --------------------------------------------------------------------------

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Desbloquear Saldo Livre",
        subtitle: String = "Confirme sua identidade para acessar seus dados financeiros",
        onResult: (BiometricAuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                unlockApp()
                onResult(BiometricAuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onResult(BiometricAuthResult.Error(errorCode, errString.toString()))
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(BiometricAuthResult.Failed)
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            // Em APIs legadas, DEVICE_CREDENTIAL combinado com BIOMETRIC_STRONG exige compatibilidade
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        }

        try {
            prompt.authenticate(promptInfoBuilder.build())
        } catch (_: Exception) {
            // Fallback com botão cancelar se DEVICE_CREDENTIAL não for suportado no dispositivo
            val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Cancelar")
                .build()
            prompt.authenticate(fallbackPromptInfo)
        }
    }
}
