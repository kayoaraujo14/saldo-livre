package com.saldolivre.app.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FriendsTab {
    APPROVALS,
    FRIENDS,
    STATEMENT
}

data class FriendItem(
    val friendshipId: String,
    val friendUid: String,
    val friendName: String,
    val friendEmail: String
)

data class FriendRequestItem(
    val friendshipId: String,
    val requesterUid: String,
    val requesterName: String,
    val requesterEmail: String,
    val createdAt: Long
)

data class FriendsUiState(
    val currentUser: UserProfile = UserProfile(),
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val activeTab: FriendsTab = FriendsTab.APPROVALS,
    val pendingChargeGroups: List<PendingChargeGroup> = emptyList(),
    val activeFriends: List<FriendItem> = emptyList(),
    val pendingRequests: List<FriendRequestItem> = emptyList(),
    val cards: List<Card> = emptyList(),
    val receivableFromFriends: Double = 0.0,
    val payableToOthers: Double = 0.0,
    val isSendingInvite: Boolean = false,
    val expandedGroupIds: Set<String> = emptySet(),
    val userMessage: String? = null
)

class FriendsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.getTransactions(),
                repository.getFriendships(),
                repository.getCards(),
                repository.getMonthSummary()
            ) { user, transactions, friendships, cards, summary ->
                val chargeGroups = FinanceEngine.groupPendingCharges(
                    transactions = transactions,
                    currentUid = user.uid,
                    allUsers = repository.allUsers
                )

                val active = friendships.filter { it.status == FriendshipStatus.ACCEPTED }.map { f ->
                    val isRequester = f.requesterUid == user.uid
                    FriendItem(
                        friendshipId = f.id,
                        friendUid = if (isRequester) f.recipientUid else f.requesterUid,
                        friendName = if (isRequester) f.recipientName else f.requesterName,
                        friendEmail = if (isRequester) f.recipientEmail else f.requesterEmail
                    )
                }

                val requests = friendships.filter {
                    it.status == FriendshipStatus.PENDING &&
                    (it.recipientUid == user.uid || it.recipientEmail.equals(user.email, ignoreCase = true))
                }.map { f ->
                    FriendRequestItem(
                        friendshipId = f.id,
                        requesterUid = f.requesterUid,
                        requesterName = f.requesterName,
                        requesterEmail = f.requesterEmail,
                        createdAt = f.createdAt
                    )
                }

                val autoTab = if (_uiState.value.pendingChargeGroups.isEmpty() && chargeGroups.isNotEmpty()) {
                    FriendsTab.APPROVALS
                } else {
                    _uiState.value.activeTab
                }

                _uiState.value.copy(
                    currentUser = user,
                    selectedMonth = summary.monthKey.ifBlank { FinanceEngine.currentMonthKey() },
                    activeTab = autoTab,
                    pendingChargeGroups = chargeGroups,
                    activeFriends = active,
                    pendingRequests = requests,
                    cards = cards,
                    receivableFromFriends = summary.receivableFromFriends,
                    payableToOthers = summary.payableToOthers
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectTab(tab: FriendsTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun previousMonth() {
        val prev = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, -1)
        repository.setSelectedMonth(prev)
    }

    fun nextMonth() {
        val next = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, 1)
        repository.setSelectedMonth(next)
    }

    fun goToCurrentMonth() {
        repository.setSelectedMonth(FinanceEngine.currentMonthKey())
    }

    fun toggleGroupExpand(groupId: String) {
        _uiState.update { current ->
            val set = current.expandedGroupIds.toMutableSet()
            if (set.contains(groupId)) set.remove(groupId) else set.add(groupId)
            current.copy(expandedGroupIds = set)
        }
    }

    fun sendFriendInvite(email: String) {
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingInvite = true) }
            val (_, message) = repository.sendFriendRequest(email)
            _uiState.update {
                it.copy(
                    isSendingInvite = false,
                    userMessage = message
                )
            }
        }
    }

    fun respondFriendRequest(friendshipId: String, accept: Boolean) {
        viewModelScope.launch {
            repository.respondFriendRequest(friendshipId, accept)
            _uiState.update {
                it.copy(
                    userMessage = if (accept) "Convite de amizade aceito com sucesso!" else "Convite de amizade recusado."
                )
            }
        }
    }

    fun removeFriend(friendshipId: String) {
        viewModelScope.launch {
            repository.removeFriend(friendshipId)
            _uiState.update {
                it.copy(userMessage = "Conexão de amizade removida.")
            }
        }
    }

    fun respondCharge(group: PendingChargeGroup, accept: Boolean) {
        viewModelScope.launch {
            repository.respondChargeRequest(group.primaryTx, accept)
            val msg = if (accept) {
                if (group.isInstallment) {
                    "Compra parcelada \"${group.baseDescription}\" (${group.installmentsCount} parcelas) aceita! Entrou no seu comprometimento."
                } else {
                    "Gasto \"${group.baseDescription}\" aceito com sucesso!"
                }
            } else {
                "Cobrança \"${group.baseDescription}\" recusada."
            }
            _uiState.update { it.copy(userMessage = msg) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
