package com.saldolivre.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.LearnedRulesManager
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver para os botões de ação rápida da notificação de esboço:
 * - [ExpenseDraftManager.ACTION_APPROVE_DRAFT]: efetiva a despesa e aprende a regra.
 * - [ExpenseDraftManager.ACTION_IGNORE_DRAFT]: descarta o esboço e fecha a notificação.
 */
class DraftActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DraftActionReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        val actionIntent = intent ?: return
        val draftManager = ExpenseDraftManager.getInstance(appContext)
        val draftId = actionIntent.getStringExtra(ExpenseDraftManager.EXTRA_EXPENSE_DRAFT_ID)

        when (actionIntent.action) {
            ExpenseDraftManager.ACTION_IGNORE_DRAFT -> {
                draftManager.removeDraft(draftId)
                draftManager.cancelDraftNotification(draftId)
            }

            ExpenseDraftManager.ACTION_APPROVE_DRAFT -> {
                val draft = draftManager.extractDraftFromIntent(actionIntent)
                if (draft == null) {
                    draftManager.cancelDraftNotification(draftId)
                    return
                }

                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val repository = SaldoLivreApplication.instance.repository
                        draftManager.approveDraftIntoRepository(
                            draft = draft,
                            repository = repository,
                            rulesManager = LearnedRulesManager(appContext),
                            neuralClassifier = NeuralFinanceClassifier(appContext)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao aprovar esboço ${draft.id}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
