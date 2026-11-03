package com.saldolivre.app.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Utilitário central de feedback tátil (Haptics) para o Saldo Livre no Android.
 * Fornece respostas de vibração sutis e elegantes ao confirmar pagamentos,
 * despesas, receitas e fechamentos de mês.
 */
object HapticFeedbackHelper {

    private const val TAG = "HapticFeedbackHelper"

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao obter serviço de vibração", e)
            null
        }
    }

    /**
     * Pulso de confirmação sutil e elegante (~25ms).
     * Ideal para cliques em botões primários de confirmação e salvamento.
     */
    fun confirm(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(25L, 120))
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25L)
            } catch (_: Exception) {}
        }
    }

    /**
     * Resposta de sucesso (duplo pulso tátil rítmico: toque curto, micro-pausa, toque de confirmação).
     * Ideal para liquidação de faturas, conclusão de despesas/receitas e Fechamento de Mês.
     */
    fun success(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                val timings = longArrayOf(0, 30, 50, 40)
                val amplitudes = intArrayOf(0, 100, 0, 160)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 50, 40), -1)
            } catch (_: Exception) {}
        }
    }

    /**
     * Feedback tátil micro (~12ms) para seletores rápidos, abas e filtros.
     */
    fun tick(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(12L, 60))
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12L)
            } catch (_: Exception) {}
        }
    }
}
