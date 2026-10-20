package com.saldolivre.app.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ActivityFilter(val label: String) {
    ALL("Todos"),
    THIRD_PARTY("Com Terceiros"),
    FRIENDS("Amigos & Compartilhados"),
    MINE("Individuais"),
    INCOME("Entradas"),
    EXPENSE("Despesas")
}

data class ActivityItemUi(
    val id: String,
    val title: String,
    val description: String,
    val amount: Double,
    val myImpact: Double,
    val category: String,
    val subcategory: String?,
    val isIncome: Boolean,
    val isRefund: Boolean,
    val paymentMethodLabel: String,
    val cardName: String?,
    val thirdPartyName: String?,
    val friendName: String?,
    val friendSharePct: Double?,
    val status: TransactionStatus,
    val competenceMonth: String,
    val dueDate: String,
    val formattedDate: String,
    val timestamp: Long
)

data class RecentActivityUiState(
    val items: List<ActivityItemUi> = emptyList(),
    val filteredItems: List<ActivityItemUi> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ActivityFilter = ActivityFilter.ALL,
    val totalCount: Int = 0,
    val last7DaysCount: Int = 0,
    val withThirdPartyCount: Int = 0,
    val friendsCount: Int = 0,
    val isLoading: Boolean = false
)

class RecentActivityViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(ActivityFilter.ALL)
    private val _uiState = MutableStateFlow(RecentActivityUiState())
    val uiState: StateFlow<RecentActivityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getTransactions(),
                repository.getCards(),
                repository.currentUser,
                _searchQuery,
                _selectedFilter
            ) { txs, cards, user, query, filter ->
                val now = System.currentTimeMillis()
                val sevenDaysAgo = now - 7 * 86400000L

                var last7Days = 0
                var withThirdParty = 0
                var friends = 0

                val sortedTxs = txs.sortedByDescending { it.createdAt }

                val items = sortedTxs.map { tx ->
                    val effAmount = tx.effectiveAmount
                    val myImpact = tx.userImpact[user.uid] ?: if (tx.createdByUid == user.uid) effAmount else 0.0
                    val isIncome = tx.type == TransactionType.INCOME
                    val isRefund = tx.category.equals("Estorno", ignoreCase = true) || effAmount < 0

                    if (tx.createdAt >= sevenDaysAgo) last7Days++

                    val thirdAlloc = tx.allocations.firstOrNull { it.type == "EXTERNAL" }
                    if (thirdAlloc != null || tx.splitType == SplitType.EXTERNAL) withThirdParty++

                    val isSharedWithFriend = !tx.assignedFriendUid.isNullOrBlank() || tx.splitType == SplitType.FRIEND
                    if (isSharedWithFriend) friends++

                    val card = cards.find { it.id == tx.cardId }

                    ActivityItemUi(
                        id = tx.id,
                        title = tx.description,
                        description = "${tx.category}${if (!tx.subcategory.isNullOrBlank()) " › ${tx.subcategory}" else ""}",
                        amount = effAmount,
                        myImpact = myImpact,
                        category = tx.category,
                        subcategory = tx.subcategory,
                        isIncome = isIncome,
                        isRefund = isRefund,
                        paymentMethodLabel = tx.paymentMethod.label,
                        cardName = card?.cardName,
                        thirdPartyName = thirdAlloc?.name,
                        friendName = tx.assignedFriendName,
                        friendSharePct = tx.friendSharePct,
                        status = tx.status,
                        competenceMonth = tx.competenceMonth,
                        dueDate = tx.dueDate,
                        formattedDate = formatRelativeTime(tx.createdAt),
                        timestamp = tx.createdAt
                    )
                }

                // Aplica filtros de busca e aba
                val filtered = items.filter { item ->
                    // 1. Busca por texto
                    val matchesSearch = if (query.isBlank()) true else {
                        val q = query.lowercase().trim()
                        item.title.lowercase().contains(q) ||
                                item.category.lowercase().contains(q) ||
                                (item.subcategory?.lowercase()?.contains(q) == true) ||
                                (item.cardName?.lowercase()?.contains(q) == true) ||
                                (item.thirdPartyName?.lowercase()?.contains(q) == true) ||
                                (item.friendName?.lowercase()?.contains(q) == true)
                    }

                    // 2. Filtro de abas
                    val matchesFilter = when (filter) {
                        ActivityFilter.ALL -> true
                        ActivityFilter.INCOME -> item.isIncome
                        ActivityFilter.EXPENSE -> !item.isIncome
                        ActivityFilter.THIRD_PARTY -> !item.thirdPartyName.isNullOrBlank()
                        ActivityFilter.FRIENDS -> !item.friendName.isNullOrBlank()
                        ActivityFilter.MINE -> item.thirdPartyName.isNullOrBlank() && item.friendName.isNullOrBlank()
                    }

                    matchesSearch && matchesFilter
                }

                RecentActivityUiState(
                    items = items,
                    filteredItems = filtered,
                    searchQuery = query,
                    selectedFilter = filter,
                    totalCount = items.size,
                    last7DaysCount = last7Days,
                    withThirdPartyCount = withThirdParty,
                    friendsCount = friends,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(filter: ActivityFilter) {
        _selectedFilter.value = filter
    }

    fun selectMonth(monthKey: String) {
        repository.setSelectedMonth(monthKey)
    }

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "Data não registrada"
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val diffMins = diffMs / 60000

        val calThen = Calendar.getInstance().apply { timeInMillis = timestamp }
        val calNow = Calendar.getInstance().apply { timeInMillis = now }

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormatter.format(Date(timestamp))

        if (diffMins < 1) return "Agora há pouco"
        if (diffMins < 60) return "Há $diffMins min ($timeStr)"

        val isToday = calThen.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calThen.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
        if (isToday) return "Hoje às $timeStr"

        val calYesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = calThen.get(Calendar.YEAR) == calYesterday.get(Calendar.YEAR) &&
                calThen.get(Calendar.DAY_OF_YEAR) == calYesterday.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "Ontem às $timeStr"

        val dateFormatter = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        return dateFormatter.format(Date(timestamp))
    }
}
