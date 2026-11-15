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

        // Ignora notificações contínuas e apps claramente não bancários (WhatsApp, redes sociais, etc.)
        if (statusBarNotif.isOngoing) return
        val notification = statusBarNotif.notification ?: return
        if (NotificationExpenseParser.isExcludedNonBankPackage(pkg)) return

        val draftManager = ExpenseDraftManager.getInstance(applicationContext)
        if (!draftManager.isCaptureEnabled) return

        val extras = notification.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotBlank() } }
            ?.joinToString(" - ")
        val candidateParts = listOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            textLines,
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
            notification.tickerText?.toString()
        ).mapNotNull { it?.trim()?.takeIf { s -> s.isNotBlank() } }

        val uniqueParts = mutableListOf<String>()
        for (part in candidateParts) {
            if (uniqueParts.none { existing -> existing.contains(part, ignoreCase = true) }) {
                uniqueParts.removeAll { existing -> part.contains(existing, ignoreCase = true) }
                uniqueParts.add(part)
            }
        }
        val rawBody = uniqueParts.joinToString(" - ")

        // Sanitização obrigatória antes de qualquer processamento, filtro ou envio ao Gemini
        val sanitizedTitle = AgentSecuritySanitizer.sanitizeText(rawTitle, maxLength = 150)
        val sanitizedBody = AgentSecuritySanitizer.sanitizeText(rawBody, maxLength = 500)
        if (sanitizedTitle.isBlank() && sanitizedBody.isBlank()) return

        // Resumos de grupo vazios (ex: "2 novas notificações") são descartados; resumos que carregam valor monetário passam pelo cache de deduplicação
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary && !NotificationExpenseParser.containsCurrencyAmount("$sanitizedTitle $sanitizedBody")) {
            return
        }

        if (!NotificationExpenseParser.isSupportedBankNotification(pkg, sanitizedTitle, sanitizedBody)) {
            return
        }

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

                val parsedDraft = NotificationExpenseParser.parseWithManagers(
                    packageName = pkg,
                    title = sanitizedTitle,
                    text = sanitizedBody,
                    uid = uid,
                    cards = cards,
                    rulesManager = rulesManager,
                    neuralClassifier = neuralClassifier
                ) ?: return@launch

                val enrichedDraft = draftManager.enrichDraftWithGemini(
                    draft = parsedDraft,
                    uid = uid,
                    rulesManager = rulesManager
                )

                draftManager.saveDraft(enrichedDraft)
                draftManager.showDraftNotification(enrichedDraft)
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
