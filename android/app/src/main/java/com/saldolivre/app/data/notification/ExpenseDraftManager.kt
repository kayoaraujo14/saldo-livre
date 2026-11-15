package com.saldolivre.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saldolivre.app.MainActivity
import com.saldolivre.app.R
import com.saldolivre.app.data.ai.AgentSecuritySanitizer
import com.saldolivre.app.data.ai.GeminiLoadBalancer
import com.saldolivre.app.data.ai.LearnedRulesManager
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.ActivityActionType
import com.saldolivre.app.data.model.ActivityLog
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.ExpenseDraft
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Gerenciador de esboços de despesas (ExpenseDraft) capturados por notificações,
 * persistência local, emissão de notificações interativas do Saldo Livre e aprovação direta.
 */
class ExpenseDraftManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val draftsLock = Any()

    companion object {
        private const val PREFS_NAME = "saldo_livre_notification_drafts_prefs"
        private const val KEY_CAPTURE_ENABLED = "notification_capture_enabled"
        private const val KEY_PENDING_DRAFTS_JSON = "pending_expense_drafts_json"
        private const val MAX_STORED_DRAFTS = 50

        const val CHANNEL_ID = "saldo_livre_expense_drafts"
        const val CHANNEL_NAME = "Despesas Detectadas"
        const val CHANNEL_DESCRIPTION = "Alertas interativos quando uma compra ou Pix é detectado em notificações bancárias."

        const val EXTRA_EXPENSE_DRAFT_JSON = "extra_expense_draft_json"
        const val EXTRA_EXPENSE_DRAFT_ID = "extra_expense_draft_id"
        const val ACTION_APPROVE_DRAFT = "com.saldolivre.app.ACTION_APPROVE_DRAFT"
        const val ACTION_IGNORE_DRAFT = "com.saldolivre.app.ACTION_IGNORE_DRAFT"

        const val SAMPLE_TEST_NOTIFICATION_PACKAGE = "com.nu.production"
        const val SAMPLE_TEST_NOTIFICATION_TITLE = "Compra aprovada"
        const val SAMPLE_TEST_NOTIFICATION_TEXT = "Compra aprovada no seu cartão de R$ 48,90 em IFD*IFOOD"

        @Volatile
        private var INSTANCE: ExpenseDraftManager? = null

        fun getInstance(context: Context): ExpenseDraftManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExpenseDraftManager(context).also { INSTANCE = it }
            }
        }

        fun notificationIdForDraft(draftId: String): Int {
            return (draftId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1000)
        }
    }

    var isCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CAPTURE_ENABLED, value).apply()
        }

    fun isListenerPermissionGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(appContext)
        return enabledPackages.contains(appContext.packageName)
    }

    fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    fun saveDraft(draft: ExpenseDraft) {
        synchronized(draftsLock) {
            val current = readDraftsInternal().toMutableList()
            current.removeAll { it.id == draft.id }
            current.add(0, draft)
            val trimmed = current.take(MAX_STORED_DRAFTS)
            prefs.edit().putString(KEY_PENDING_DRAFTS_JSON, gson.toJson(trimmed)).apply()
        }
    }

    fun getPendingDrafts(): List<ExpenseDraft> {
        synchronized(draftsLock) {
            return readDraftsInternal().sortedByDescending { it.createdAt }
        }
    }

    fun getDraftById(id: String?): ExpenseDraft? {
        if (id.isNullOrBlank()) return null
        synchronized(draftsLock) {
            return readDraftsInternal().find { it.id == id }
        }
    }

    fun removeDraft(id: String?) {
        if (id.isNullOrBlank()) return
        synchronized(draftsLock) {
            val current = readDraftsInternal().filterNot { it.id == id }
            prefs.edit().putString(KEY_PENDING_DRAFTS_JSON, gson.toJson(current)).apply()
        }
    }

    fun toJson(draft: ExpenseDraft): String = gson.toJson(draft)

    fun fromJson(json: String?): ExpenseDraft? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, ExpenseDraft::class.java) }.getOrNull()
    }

    fun extractDraftFromIntent(intent: Intent?): ExpenseDraft? {
        if (intent == null) return null
        val id = intent.getStringExtra(EXTRA_EXPENSE_DRAFT_ID)
        val stored = getDraftById(id)
        if (stored != null) return stored

        val json = intent.getStringExtra(EXTRA_EXPENSE_DRAFT_JSON)
        val parsed = fromJson(json)
        if (parsed != null) {
            saveDraft(parsed)
        }
        return parsed
    }

    fun clearDraftExtras(intent: Intent?) {
        intent?.removeExtra(EXTRA_EXPENSE_DRAFT_ID)
        intent?.removeExtra(EXTRA_EXPENSE_DRAFT_JSON)
    }

    fun ensureNotificationChannel() {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun showDraftNotification(draft: ExpenseDraft): Boolean {
        if (!hasPostNotificationsPermission()) return false
        ensureNotificationChannel()

        val draftJson = toJson(draft)
        val notifId = notificationIdForDraft(draft.id)
        val formattedAmount = FinanceEngine.formatCurrency(draft.amount).replace('\u00A0', ' ')

        val contentIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EXPENSE_DRAFT_ID, draft.id)
            putExtra(EXTRA_EXPENSE_DRAFT_JSON, draftJson)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            notifId * 10,
            contentIntent,
            pendingFlags
        )

        val approveIntent = Intent(appContext, DraftActionReceiver::class.java).apply {
            action = ACTION_APPROVE_DRAFT
            putExtra(EXTRA_EXPENSE_DRAFT_ID, draft.id)
            putExtra(EXTRA_EXPENSE_DRAFT_JSON, draftJson)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(
            appContext,
            notifId * 10 + 1,
            approveIntent,
            pendingFlags
        )

        val ignoreIntent = Intent(appContext, DraftActionReceiver::class.java).apply {
            action = ACTION_IGNORE_DRAFT
            putExtra(EXTRA_EXPENSE_DRAFT_ID, draft.id)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            appContext,
            notifId * 10 + 2,
            ignoreIntent,
            pendingFlags
        )

        val title = if (draft.type == TransactionType.INCOME) {
            "💰 Novo Pix recebido: $formattedAmount"
        } else {
            "💸 Nova despesa detectada: $formattedAmount"
        }
        val body = if (draft.type == TransactionType.INCOME) {
            "${draft.description} • Entrada (${draft.category}) — Toque para revisar no Chat"
        } else {
            "${draft.description} • ${draft.paymentMethodShortLabel()} (${draft.category}) — Toque para revisar no Chat"
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "✅ Aprovar", approvePendingIntent)
            .addAction(0, "🗑️ Ignorar", ignorePendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(appContext).notify(notifId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelDraftNotification(draftId: String?) {
        if (draftId.isNullOrBlank()) return
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(notificationIdForDraft(draftId))
        }
    }

    /**
     * Efetiva um [ExpenseDraft] diretamente no [FinanceRepository], aprende a regra no
     * [LearnedRulesManager] e limpa o rascunho/notificação pendentes.
     */
    suspend fun approveDraftIntoRepository(
        draft: ExpenseDraft,
        repository: FinanceRepository,
        rulesManager: LearnedRulesManager = LearnedRulesManager(appContext),
        neuralClassifier: NeuralFinanceClassifier = NeuralFinanceClassifier(appContext)
    ): Transaction {
        val user = repository.currentUser.value
        val cards = runCatching { repository.getCards().first() }.getOrDefault(emptyList())
        val isCreditExpense = draft.type == TransactionType.EXPENSE &&
            draft.paymentMethod == PaymentMethod.CREDIT_CARD
        val card = if (isCreditExpense) {
            cards.find { it.id == draft.cardId }
                ?: draft.cardName?.let { targetName ->
                    cards.find { it.cardName.equals(targetName, ignoreCase = true) }
                }
        } else null

        val partner = FinanceEngine.resolvePartner(user.uid, cards, repository.allUsers)
        val split = FinanceEngine.buildSplit(
            splitType = SplitType.MINE,
            total = draft.amount,
            myUid = user.uid,
            myName = user.displayName,
            partnerUid = partner?.uid,
            partnerName = partner?.displayName,
            payerUid = user.uid
        )

        val (dueDate, competenceMonth) = if (isCreditExpense) {
            val cardDates = FinanceEngine.calculateCardInvoiceDates(draft.date, card)
            Pair(draft.date, cardDates.second)
        } else {
            Pair(draft.date, draft.date.take(7))
        }

        val (strictCategory, strictSubcategory) = CategoryRegistry.resolveStrictCategory(
            rawCategory = draft.category,
            rawSubcategory = draft.subcategory,
            type = draft.type,
            description = draft.description
        )

        val cardUids = if (isCreditExpense && card != null) {
            (listOfNotNull(card.ownerUid.takeIf { it.isNotBlank() }) + card.sharedWithUids).distinct()
        } else emptyList()

        val status = if (isCreditExpense) {
            TransactionStatus.PENDING
        } else {
            TransactionStatus.PAID
        }

        val fallbackDesc = if (draft.type == TransactionType.INCOME) "Pix recebido" else "Compra detectada"
        val safeDescription = AgentSecuritySanitizer.sanitizeText(draft.description, maxLength = 120)
            .ifBlank { fallbackDesc }

        val tx = Transaction(
            id = "tx_${UUID.randomUUID().toString().replace("-", "").take(8)}",
            createdByUid = user.uid,
            paidByUid = user.uid,
            cardId = if (isCreditExpense) card?.id else null,
            description = safeDescription,
            category = strictCategory,
            subcategory = strictSubcategory,
            type = draft.type,
            paymentMethod = draft.paymentMethod,
            status = status,
            estimatedAmount = null,
            actualAmount = draft.amount,
            totalAmount = draft.amount,
            dueDate = dueDate,
            competenceMonth = competenceMonth,
            visibleToUids = (split.visibleToUids + cardUids).distinct(),
            userImpact = split.userImpact,
            allocations = split.allocations,
            splitType = SplitType.MINE
        )

        repository.addTransaction(tx)
        val typeTag = if (draft.type == TransactionType.INCOME) "Entrada" else "Despesa"
        repository.logActivity(
            ActivityLog(
                actionType = ActivityActionType.TRANSACTION_CREATED,
                title = safeDescription,
                description = "Aprovado via notificação ($typeTag) • $strictCategory (${FinanceEngine.formatCurrency(draft.amount)})",
                amount = draft.amount,
                category = strictCategory,
                competenceMonth = competenceMonth,
                userUid = user.uid,
                userName = user.displayName
            )
        )

        learnRuleForDraft(
            description = safeDescription,
            category = strictCategory,
            uid = user.uid,
            rulesManager = rulesManager,
            neuralClassifier = neuralClassifier
        )

        removeDraft(draft.id)
        cancelDraftNotification(draft.id)
        return tx
    }

    fun learnRuleForDraft(
        description: String,
        category: String,
        uid: String,
        rulesManager: LearnedRulesManager = LearnedRulesManager(appContext),
        neuralClassifier: NeuralFinanceClassifier = NeuralFinanceClassifier(appContext)
    ) {
        val cleanDesc = AgentSecuritySanitizer.sanitizeText(description, maxLength = 80).trim()
        if (cleanDesc.length < 2) return
        val ruleText = "$cleanDesc é $category"
        rulesManager.addRule(uid = uid, ruleText = ruleText, category = category)
        neuralClassifier.saveLearnedRule(ruleText)
    }

    /**
     * Pensa com a IA Gemini sobre a notificação bancária para substituir nomes fantasia/POS
     * por uma descrição financeira inteligente e refinar categoria/subcategoria (mantendo
     * regras aprendidas pelo usuário quando aplicáveis).
     */
    suspend fun enrichDraftWithGemini(
        draft: ExpenseDraft,
        uid: String = "",
        rulesManager: LearnedRulesManager = LearnedRulesManager(appContext)
    ): ExpenseDraft = withContext(Dispatchers.IO) {
        if (draft.aiEnriched) return@withContext draft

        val hasLearnedRule = if (uid.isNotBlank()) {
            val rules = rulesManager.getLocalRules(uid)
            NotificationExpenseParser.hasLearnedRuleMatch(
                description = draft.rawMerchant.ifBlank { draft.description },
                sanitizedCombined = draft.rawNotificationText,
                learnedRules = rules,
                transactionType = draft.type
            )
        } else {
            false
        }

        val refined = GeminiLoadBalancer(appContext).refineNotificationExpenseWithAi(
            rawNotificationText = draft.rawNotificationText,
            rawMerchant = draft.rawMerchant.ifBlank { draft.description },
            currentDescription = draft.description,
            amount = draft.amount,
            paymentMethod = draft.paymentMethod,
            currentCategory = draft.category,
            currentSubcategory = draft.subcategory,
            packageName = draft.packageName,
            transactionType = draft.type
        ) ?: return@withContext draft

        draft.copy(
            description = refined.description,
            category = if (hasLearnedRule) draft.category else refined.category,
            subcategory = if (hasLearnedRule) draft.subcategory else refined.subcategory,
            aiEnriched = true
        )
    }

    suspend fun simulateTestNotification(
        repository: FinanceRepository,
        rawText: String = SAMPLE_TEST_NOTIFICATION_TEXT,
        title: String = SAMPLE_TEST_NOTIFICATION_TITLE,
        packageName: String = SAMPLE_TEST_NOTIFICATION_PACKAGE
    ): ExpenseDraft? {
        val user = repository.currentUser.value
        val cards = runCatching { repository.getCards().first() }.getOrDefault(emptyList())
        val rulesManager = LearnedRulesManager(appContext)
        val neuralClassifier = NeuralFinanceClassifier(appContext)

        val parsedDraft = NotificationExpenseParser.parseWithManagers(
            packageName = packageName,
            title = title,
            text = rawText,
            uid = user.uid,
            cards = cards,
            rulesManager = rulesManager,
            neuralClassifier = neuralClassifier
        ) ?: return null

        val enrichedDraft = enrichDraftWithGemini(
            draft = parsedDraft,
            uid = user.uid,
            rulesManager = rulesManager
        )

        saveDraft(enrichedDraft)
        showDraftNotification(enrichedDraft)
        return enrichedDraft
    }

    private fun readDraftsInternal(): List<ExpenseDraft> {
        val json = prefs.getString(KEY_PENDING_DRAFTS_JSON, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ExpenseDraft>>() {}.type
            gson.fromJson<List<ExpenseDraft>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
