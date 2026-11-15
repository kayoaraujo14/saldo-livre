package com.saldolivre.app.ui.screens.menu

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.GeminiLoadBalancer
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.ExpenseDraft
import com.saldolivre.app.data.model.FriendshipStatus
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.data.notification.ExpenseDraftManager
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.data.repository.MockFinanceRepository
import com.saldolivre.app.data.security.AppSecurityManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
data class MenuUiState(
    val currentUser: UserProfile = UserProfile(),
    val partnerUser: UserProfile? = null,
    val allUsers: List<UserProfile> = emptyList(),
    val isDemoMode: Boolean = false,
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val summary: MonthSummary = MonthSummary(),
    val cardsCount: Int = 0,
    val activeRecurringCount: Int = 0,
    val acceptedFriendsCount: Int = 0,
    val pendingAlertsCount: Int = 0,
    val hasGeminiKey: Boolean = false,
    val apiKey: String = "",
    val activeGeminiModel: String = "gemini-3-flash",
    val isTestingConnection: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeoutMinutes: Int = 2,
    val flagSecureEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val notificationCaptureEnabled: Boolean = true,
    val isSimulatingNotification: Boolean = false,
    val simulatedNotificationMessage: String? = null,
    val lastSimulatedDraft: ExpenseDraft? = null
)

class MenuViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val appContext = runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val loadBalancer = GeminiLoadBalancer(appContext)
    private val securityManager = appContext?.let { AppSecurityManager.getInstance(it) }
    private val expenseDraftManager = appContext?.let { ExpenseDraftManager.getInstance(it) }

    companion object {
        val AVAILABLE_MODELS = GeminiLoadBalancer.DEFAULT_MODELS_POOL
    }

    private val _uiState = MutableStateFlow(
        MenuUiState(
            biometricEnabled = securityManager?.isBiometricEnabled ?: false,
            lockTimeoutMinutes = securityManager?.lockTimeoutMinutes ?: 2,
            flagSecureEnabled = securityManager?.isFlagSecureEnabled ?: false,
            biometricAvailable = securityManager?.isBiometricAvailable() ?: false,
            notificationListenerGranted = expenseDraftManager?.isListenerPermissionGranted() ?: false,
            notificationCaptureEnabled = expenseDraftManager?.isCaptureEnabled ?: true
        )
    )
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getMonthSummary(),
                repository.getCards(),
                repository.getRecurringBills()
            ) { user, month, summary, cards, recurring ->
                Triple(
                    Pair(user, month),
                    summary,
                    Pair(cards.size, recurring.count { it.active && it.ownerUid == user.uid })
                )
            }.combine(
                combine(
                    repository.getFriendships(),
                    repository.getTransactions()
                ) { friendships, transactions ->
                    Pair(friendships, transactions)
                }
            ) { base, social ->
                val (userAndMonth, summary, counts) = base
                val (user, month) = userAndMonth
                val (cardsCount, recurringCount) = counts
                val (friendships, transactions) = social

                val pendingFriendReqs = friendships.count {
                    it.status == FriendshipStatus.PENDING &&
                            (it.recipientUid == user.uid || it.recipientEmail.equals(user.email, ignoreCase = true))
                }
                val pendingCharges = FinanceEngine.groupPendingCharges(
                    transactions = transactions,
                    currentUid = user.uid,
                    allUsers = repository.allUsers
                ).size

                val acceptedFriends = friendships.filter { it.status == FriendshipStatus.ACCEPTED }
                val partnerFromUsers = repository.allUsers.find { it.uid != user.uid }
                val partnerProfile = partnerFromUsers ?: acceptedFriends.firstOrNull()?.let { f ->
                    val (fUid, fName) = f.otherUser(user.uid)
                    val fEmail = if (f.requesterUid == user.uid) f.recipientEmail else f.requesterEmail
                    UserProfile(uid = fUid, displayName = fName, email = fEmail)
                }

                val keys = loadBalancer.getApiKeys()

                _uiState.value.copy(
                    currentUser = user,
                    partnerUser = partnerProfile,
                    allUsers = repository.allUsers,
                    isDemoMode = repository is MockFinanceRepository,
                    selectedMonth = month,
                    summary = summary,
                    cardsCount = cardsCount,
                    activeRecurringCount = recurringCount,
                    acceptedFriendsCount = acceptedFriends.size,
                    pendingAlertsCount = pendingFriendReqs + pendingCharges,
                    hasGeminiKey = keys.isNotEmpty(),
                    apiKey = keys.joinToString(", "),
                    activeGeminiModel = loadBalancer.getSelectedModel(),
                    biometricEnabled = securityManager?.isBiometricEnabled ?: false,
                    lockTimeoutMinutes = securityManager?.lockTimeoutMinutes ?: 2,
                    flagSecureEnabled = securityManager?.isFlagSecureEnabled ?: false,
                    biometricAvailable = securityManager?.isBiometricAvailable() ?: false,
                    notificationListenerGranted = expenseDraftManager?.isListenerPermissionGranted() ?: false,
                    notificationCaptureEnabled = expenseDraftManager?.isCaptureEnabled ?: true
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // --------------------------------------------------------------------------
    // SEGURANÇA & PRIVACIDADE
    // --------------------------------------------------------------------------

    fun setBiometricEnabled(enabled: Boolean) {
        securityManager?.isBiometricEnabled = enabled
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun setLockTimeoutMinutes(minutes: Int) {
        securityManager?.lockTimeoutMinutes = minutes
        _uiState.update { it.copy(lockTimeoutMinutes = minutes) }
    }

    fun setFlagSecureEnabled(enabled: Boolean, activity: Activity? = null) {
        securityManager?.isFlagSecureEnabled = enabled
        activity?.let { securityManager?.applyFlagSecure(it) }
        _uiState.update { it.copy(flagSecureEnabled = enabled) }
    }

    // --------------------------------------------------------------------------
    // CAPTURA AUTOMÁTICA DE NOTIFICAÇÕES BANCÁRIAS
    // --------------------------------------------------------------------------

    fun refreshNotificationListenerStatus() {
        _uiState.update {
            it.copy(
                notificationListenerGranted = expenseDraftManager?.isListenerPermissionGranted() ?: false,
                notificationCaptureEnabled = expenseDraftManager?.isCaptureEnabled ?: true
            )
        }
    }

    fun setNotificationCaptureEnabled(enabled: Boolean) {
        expenseDraftManager?.isCaptureEnabled = enabled
        _uiState.update { it.copy(notificationCaptureEnabled = enabled) }
    }

    fun simulateBankNotification() {
        val manager = expenseDraftManager ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSimulatingNotification = true, simulatedNotificationMessage = null)
            }
            try {
                val draft = manager.simulateTestNotification(repository)
                if (draft != null) {
                    val amountText = FinanceEngine.formatCurrency(draft.amount).replace('\u00A0', ' ')
                    val msg = if (manager.hasPostNotificationsPermission()) {
                        "Notificação disparada! Esboço: ${draft.description} ($amountText • ${draft.category}). Toque na notificação ou abra no Chat."
                    } else {
                        "Esboço criado: ${draft.description} ($amountText • ${draft.category}). Permissão POST_NOTIFICATIONS pendente; você já pode revisar direto no Chat."
                    }
                    _uiState.update {
                        it.copy(
                            isSimulatingNotification = false,
                            simulatedNotificationMessage = msg,
                            lastSimulatedDraft = draft
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSimulatingNotification = false,
                            simulatedNotificationMessage = "Não foi possível gerar esboço a partir do texto de teste."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSimulatingNotification = false,
                        simulatedNotificationMessage = "Erro ao simular notificação: ${e.message ?: "desconhecido"}"
                    )
                }
            }
        }
    }

    // --------------------------------------------------------------------------
    // INTELIGÊNCIA ARTIFICIAL (GEMINI)
    // --------------------------------------------------------------------------

    fun saveAiConfig(keyInput: String, model: String) {
        val trimmed = keyInput.trim()
        loadBalancer.saveApiKeys(trimmed)
        loadBalancer.setSelectedModel(model)
        val keys = loadBalancer.getApiKeys()
        _uiState.update {
            it.copy(
                hasGeminiKey = keys.isNotEmpty(),
                apiKey = keys.joinToString(", "),
                activeGeminiModel = loadBalancer.getSelectedModel()
            )
        }
    }

    fun clearAiKey() {
        loadBalancer.saveApiKeys("")
        _uiState.update {
            it.copy(
                hasGeminiKey = false,
                apiKey = ""
            )
        }
    }

    fun testConnection(testKey: String, onResult: (Pair<Boolean, String>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            val outcome = loadBalancer.testConnection(testKey)
            _uiState.update { it.copy(isTestingConnection = false) }
            onResult(outcome)
        }
    }

    fun switchUser(uid: String) {
        repository.switchUser(uid)
    }
}
