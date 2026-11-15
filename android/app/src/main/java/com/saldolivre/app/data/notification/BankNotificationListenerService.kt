package com.saldolivre.app.data.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.AgentSecuritySanitizer
import com.saldolivre.app.data.ai.LearnedRulesManager
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Serviço em segundo plano que monitora notificações bancárias brasileiras em tempo real,
 * aplica filtragem de pacotes, deduplicação (2 min) e sanitização via [AgentSecuritySanitizer]
 * antes de criar um [com.saldolivre.app.data.model.ExpenseDraft] e disparar a notificação local.
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "BankNotifListener"
        internal val deduplicationCache = NotificationDeduplicationCache()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotif = sbn ?: return
        val pkg = statusBarNotif.packageName ?: return
        if (pkg == applicationContext.packageName) return

        // Ignora notificações contínuas e resumos de grupo
        if (statusBarNotif.isOngoing) return
        val notification = statusBarNotif.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val draftManager = ExpenseDraftManager.getInstance(applicationContext)
        if (!draftManager.isCaptureEnabled) return
        if (!NotificationExpenseParser.isSupportedBankPackage(pkg)) return

        val extras = notification.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val rawBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val rawShortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val rawBody = rawBigText?.takeIf { it.isNotBlank() } ?: rawShortText

        // Sanitização obrigatória antes de qualquer processamento ou envio ao Gemini
        val sanitizedTitle = AgentSecuritySanitizer.sanitizeText(rawTitle, maxLength = 150)
        val sanitizedBody = AgentSecuritySanitizer.sanitizeText(rawBody, maxLength = 500)
        if (sanitizedTitle.isBlank() && sanitizedBody.isBlank()) return

        if (deduplicationCache.isDuplicateAndRecord(pkg, sanitizedTitle, sanitizedBody)) {
            return
        }

        serviceScope.launch {
            try {
                val repository = SaldoLivreApplication.instance.repository
                val uid = repository.currentUser.value.uid
                val cards = runCatching { repository.getCards().first() }.getOrDefault(emptyList())
                val rulesManager = LearnedRulesManager(applicationContext)
                val neuralClassifier = NeuralFinanceClassifier(applicationContext)

                val draft = NotificationExpenseParser.parseWithManagers(
                    packageName = pkg,
                    title = sanitizedTitle,
                    text = sanitizedBody,
                    uid = uid,
                    cards = cards,
                    rulesManager = rulesManager,
                    neuralClassifier = neuralClassifier
                ) ?: return@launch

                draftManager.saveDraft(draft)
                draftManager.showDraftNotification(draft)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar notificação bancária de $pkg", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
