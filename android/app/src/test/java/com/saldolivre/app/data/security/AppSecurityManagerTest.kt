package com.saldolivre.app.data.security

import org.junit.Assert.*
import org.junit.Test

class AppSecurityManagerTest {

    @Test
    fun shouldLockOnForeground_whenBiometricDisabled_returnsFalseAlways() {
        assertFalse(
            "Biometria desabilitada nunca deve bloquear em tempo 0",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 0L,
                timeoutMinutes = 0,
                isBiometricEnabled = false
            )
        )

        assertFalse(
            "Biometria desabilitada nunca deve bloquear após 10 horas",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 10 * 3600 * 1000L,
                timeoutMinutes = 1,
                isBiometricEnabled = false
            )
        )
    }

    @Test
    fun shouldLockOnForeground_immediateTimeout_locksImmediately() {
        assertTrue(
            "Timeout 0 (imediato) deve bloquear com 0ms decorrido",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 0L,
                timeoutMinutes = 0,
                isBiometricEnabled = true
            )
        )

        assertTrue(
            "Timeout negativo deve ser tratado como imediato",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 500L,
                timeoutMinutes = -1,
                isBiometricEnabled = true
            )
        )

        assertTrue(
            "Timeout imediato deve bloquear com 10 segundos decorridos",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 10_000L,
                timeoutMinutes = 0,
                isBiometricEnabled = true
            )
        )
    }

    @Test
    fun shouldLockOnForeground_oneMinuteTimeout_respectsThreshold() {
        assertFalse(
            "Não deve bloquear com menos de 1 minuto decorrido",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 59_999L,
                timeoutMinutes = 1,
                isBiometricEnabled = true
            )
        )

        assertTrue(
            "Deve bloquear exatamente ao atingir 1 minuto (60.000 ms)",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 60_000L,
                timeoutMinutes = 1,
                isBiometricEnabled = true
            )
        )

        assertTrue(
            "Deve bloquear quando decorrido mais de 1 minuto",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 65_000L,
                timeoutMinutes = 1,
                isBiometricEnabled = true
            )
        )
    }

    @Test
    fun shouldLockOnForeground_defaultTwoMinuteTimeout_respectsThreshold() {
        assertEquals("Timeout padrão deve ser 2 minutos", 2, AppSecurityManager.DEFAULT_TIMEOUT_MINUTES)

        assertFalse(
            "Não deve bloquear com 1m59s de inatividade",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 119_999L,
                timeoutMinutes = AppSecurityManager.DEFAULT_TIMEOUT_MINUTES,
                isBiometricEnabled = true
            )
        )

        assertTrue(
            "Deve bloquear exatamente aos 2 minutos (120.000 ms)",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 120_000L,
                timeoutMinutes = AppSecurityManager.DEFAULT_TIMEOUT_MINUTES,
                isBiometricEnabled = true
            )
        )
    }

    @Test
    fun shouldLockOnForeground_fiveMinuteTimeout_respectsThreshold() {
        assertFalse(
            "Não deve bloquear com 4m59s de inatividade",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 299_999L,
                timeoutMinutes = 5,
                isBiometricEnabled = true
            )
        )

        assertTrue(
            "Deve bloquear exatamente aos 5 minutos (300.000 ms)",
            AppSecurityManager.shouldLockOnForeground(
                elapsedMillis = 300_000L,
                timeoutMinutes = 5,
                isBiometricEnabled = true
            )
        )
    }

    @Test
    fun constants_verifySecurityPreferencesNames() {
        assertEquals("saldo_livre_security_prefs", AppSecurityManager.PREFS_NAME)
        assertEquals("pref_biometric_enabled", AppSecurityManager.KEY_BIOMETRIC_ENABLED)
        assertEquals("pref_lock_timeout_minutes", AppSecurityManager.KEY_LOCK_TIMEOUT_MINUTES)
        assertEquals("pref_flag_secure_enabled", AppSecurityManager.KEY_FLAG_SECURE_ENABLED)
    }
}
