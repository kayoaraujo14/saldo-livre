package com.saldolivre.app.ui.screens.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.ai.GeminiLoadBalancer
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.AcceptanceStatus
import com.saldolivre.app.data.model.FriendshipStatus
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.data.repository.MockFinanceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val isGeneratingBills: Boolean = false,
    val generateFeedback: String? = null
)

class MenuViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val appContext = runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val loadBalancer = GeminiLoadBalancer(appContext)

    companion object {
        val AVAILABLE_MODELS = GeminiLoadBalancer.DEFAULT_MODELS_POOL
    }

    private val _uiState = MutableStateFlow(MenuUiState())
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
                    Pair(cards.size, recurring.count { it.active })
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
                    activeGeminiModel = loadBalancer.getSelectedModel()
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

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

    fun generateBillsForCurrentMonth() {
        if (_uiState.value.isGeneratingBills) return
        _uiState.update { it.copy(isGeneratingBills = true, generateFeedback = null) }

        viewModelScope.launch {
            try {
                val (created, skipped) = repository.generateBillsForMonth(_uiState.value.selectedMonth)
                val msg = "✓ $created conta(s) gerada(s) · $skipped já existiam em ${FinanceEngine.monthLabel(_uiState.value.selectedMonth)}"
                _uiState.update { it.copy(isGeneratingBills = false, generateFeedback = msg) }
                delay(4000)
                _uiState.update { it.copy(generateFeedback = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingBills = false,
                        generateFeedback = "Não foi possível gerar as contas recorrentes."
                    )
                }
            }
        }
    }
}
