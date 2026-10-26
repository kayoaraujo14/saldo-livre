package com.saldolivre.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.data.model.UserProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val currentUser: UserProfile = UserProfile(),
    val transactions: List<Transaction> = emptyList(),
    val allTransactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val searchQuery: String = "",

    // Single filters (legacy compatibility)
    val typeFilter: TransactionType? = null,
    val statusFilter: TransactionStatus? = null,
    val selectedCategory: String? = null,

    // Excel-style multi-select filters
    val selectedTypes: Set<TransactionType> = emptySet(),
    val selectedStatuses: Set<TransactionStatus> = emptySet(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedPersons: Set<String> = emptySet(),

    // Available dynamic options for filter sheet
    val availableCategories: List<String> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val availablePersons: List<String> = emptyList(),

    // Dynamic Filtered Sums (reflecting current selection)
    val filteredIncome: Double = 0.0,
    val filteredExpense: Double = 0.0,
    val filteredFreeBalance: Double = 0.0,
    val isFiltered: Boolean = false,
    val activeFilterCount: Int = 0,

    // Month global summaries
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalPaidExpense: Double = 0.0,
    val totalPendingExpense: Double = 0.0,
    val freeBalance: Double = 0.0,
    val carryoverAmount: Double = 0.0,
    val previousMonthDeficit: Double = 0.0,
    val cushionAmount: Double = 0.0,
    val pendingCount: Int = 0,
    val paidCount: Int = 0
)

class TransactionsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.selectedMonth,
                repository.getTransactions(),
                repository.getMonthSummary()
            ) { user, month, allTxs, summary ->
                val monthTxs = allTxs.filter {
                    it.competenceMonth == month && it.visibleToUids.contains(user.uid)
                }.sortedByDescending { it.dueDate }

                val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf {
                    FinanceEngine.impactFor(it, user.uid, allTxs)
                }
                val expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf {
                    FinanceEngine.impactFor(it, user.uid, allTxs)
                }
                val paidCount = monthTxs.count { it.status == TransactionStatus.PAID }
                val pendingCount = monthTxs.count { it.status != TransactionStatus.PAID }

                val availableCats = monthTxs.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                val catCounts = monthTxs.groupingBy { it.category }.eachCount()
                val availablePers = monthTxs.flatMap { getPersonsForTransaction(it, user.uid) }
                    .distinct()
                    .sortedWith { a, b ->
                        if (a == "100% Meu") -1 else if (b == "100% Meu") 1 else a.compareTo(b)
                    }

                _uiState.update { current ->
                    current.copy(
                        currentUser = user,
                        transactions = monthTxs,
                        allTransactions = allTxs.filter { it.visibleToUids.contains(user.uid) },
                        selectedMonth = month,
                        availableCategories = availableCats,
                        categoryCounts = catCounts,
                        availablePersons = availablePers,
                        totalIncome = FinanceEngine.round2(income),
                        totalExpense = FinanceEngine.round2(expense),
                        totalPaidExpense = summary.paid,
                        totalPendingExpense = summary.committed,
                        freeBalance = summary.freeBalance,
                        carryoverAmount = summary.carryoverAmount,
                        previousMonthDeficit = summary.previousMonthDeficit,
                        cushionAmount = summary.cushionAmount,
                        pendingCount = pendingCount,
                        paidCount = paidCount
                    )
                }
                applyFilters()
            }.collect()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setTypeFilter(type: TransactionType?) {
        _uiState.update {
            val newSet = if (type != null) setOf(type) else emptySet()
            it.copy(typeFilter = type, selectedTypes = newSet)
        }
        applyFilters()
    }

    fun setStatusFilter(status: TransactionStatus?) {
        _uiState.update {
            val newSet = if (status != null) setOf(status) else emptySet()
            it.copy(statusFilter = status, selectedStatuses = newSet)
        }
        applyFilters()
    }

    fun setCategoryFilter(category: String?) {
        _uiState.update {
            val newSet = if (category != null) setOf(category) else emptySet()
            it.copy(selectedCategory = category, selectedCategories = newSet)
        }
        applyFilters()
    }

    fun toggleTypeFilter(type: TransactionType) {
        _uiState.update { current ->
            val updated = if (current.selectedTypes.contains(type)) {
                current.selectedTypes - type
            } else {
                current.selectedTypes + type
            }
            current.copy(selectedTypes = updated, typeFilter = if (updated.size == 1) updated.first() else null)
        }
        applyFilters()
    }

    fun toggleStatusFilter(status: TransactionStatus) {
        _uiState.update { current ->
            val updated = if (current.selectedStatuses.contains(status)) {
                current.selectedStatuses - status
            } else {
                current.selectedStatuses + status
            }
            current.copy(selectedStatuses = updated, statusFilter = if (updated.size == 1) updated.first() else null)
        }
        applyFilters()
    }

    fun toggleCategoryFilter(category: String) {
        _uiState.update { current ->
            val updated = if (current.selectedCategories.contains(category)) {
                current.selectedCategories - category
            } else {
                current.selectedCategories + category
            }
            current.copy(selectedCategories = updated, selectedCategory = if (updated.size == 1) updated.first() else null)
        }
        applyFilters()
    }

    fun togglePersonFilter(person: String) {
        _uiState.update { current ->
            val updated = if (current.selectedPersons.contains(person)) {
                current.selectedPersons - person
            } else {
                current.selectedPersons + person
            }
            current.copy(selectedPersons = updated)
        }
        applyFilters()
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                typeFilter = null,
                statusFilter = null,
                selectedCategory = null,
                selectedTypes = emptySet(),
                selectedStatuses = emptySet(),
                selectedCategories = emptySet(),
                selectedPersons = emptySet()
            )
        }
        applyFilters()
    }

    fun previousMonth() {
        val prev = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, -1)
        repository.setSelectedMonth(prev)
    }

    fun nextMonth() {
        val next = FinanceEngine.shiftMonth(_uiState.value.selectedMonth, 1)
        repository.setSelectedMonth(next)
    }

    fun currentMonth() {
        repository.setSelectedMonth(FinanceEngine.currentMonthKey())
    }

    fun markAsPaid(id: String) {
        viewModelScope.launch {
            repository.markTransactionPaid(id)
        }
    }

    fun toggleStatus(id: String) {
        viewModelScope.launch {
            repository.toggleTransactionStatus(id)
        }
    }

    fun cycleStatus(id: String) {
        viewModelScope.launch {
            repository.cycleTransactionStatus(id)
        }
    }

    fun setStatus(id: String, status: TransactionStatus, actualAmount: Double? = null) {
        viewModelScope.launch {
            repository.setTransactionStatus(id, status, actualAmount)
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun deleteInstallmentGroup(groupId: String) {
        viewModelScope.launch {
            repository.deleteInstallmentGroup(groupId)
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase()
        val uid = state.currentUser.uid

        val filtered = state.transactions.filter { tx ->
            val matchesQuery = query.isBlank() ||
                    tx.description.lowercase().contains(query) ||
                    tx.category.lowercase().contains(query) ||
                    (tx.subcategory?.lowercase()?.contains(query) == true) ||
                    (tx.externalName?.lowercase()?.contains(query) == true) ||
                    (tx.assignedFriendName?.lowercase()?.contains(query) == true) ||
                    tx.allocations.any { it.name.lowercase().contains(query) }

            val matchesType = when {
                state.selectedTypes.isNotEmpty() -> tx.type in state.selectedTypes
                state.typeFilter != null -> tx.type == state.typeFilter
                else -> true
            }

            val matchesStatus = when {
                state.selectedStatuses.isNotEmpty() -> tx.status in state.selectedStatuses
                state.statusFilter != null -> tx.status == state.statusFilter
                else -> true
            }

            val matchesCategory = when {
                state.selectedCategories.isNotEmpty() -> {
                    if (tx.category in state.selectedCategories) true
                    else if (tx.category == "Receita") {
                        // Compatibilidade com transações legadas registradas como "Receita"
                        state.selectedCategories.any { selCat ->
                            CategoryRegistry.get(selCat).subcategories.any { sub ->
                                sub.equals(tx.subcategory, ignoreCase = true)
                            }
                        }
                    } else false
                }
                state.selectedCategory != null -> tx.category == state.selectedCategory
                else -> true
            }

            val matchesPerson = if (state.selectedPersons.isNotEmpty()) {
                val txPersons = getPersonsForTransaction(tx, uid)
                txPersons.any { it in state.selectedPersons }
            } else {
                true
            }

            matchesQuery && matchesType && matchesStatus && matchesCategory && matchesPerson
        }

        val filteredInc = filtered.filter { it.type == TransactionType.INCOME }.sumOf {
            FinanceEngine.impactFor(it, uid, state.allTransactions)
        }
        val filteredExp = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf {
            FinanceEngine.impactFor(it, uid, state.allTransactions)
        }

        val isAnyFilterActive = state.searchQuery.isNotBlank() ||
                state.selectedTypes.isNotEmpty() ||
                state.selectedStatuses.isNotEmpty() ||
                state.selectedCategories.isNotEmpty() ||
                state.selectedPersons.isNotEmpty() ||
                state.typeFilter != null ||
                state.statusFilter != null ||
                state.selectedCategory != null

        val activeCount = (if (state.searchQuery.isNotBlank()) 1 else 0) +
                state.selectedTypes.size +
                state.selectedStatuses.size +
                state.selectedCategories.size +
                state.selectedPersons.size

        val dispBalance = if (isAnyFilterActive) {
            FinanceEngine.round2(filteredInc - filteredExp)
        } else {
            state.freeBalance
        }

        _uiState.update {
            it.copy(
                filteredTransactions = filtered,
                filteredIncome = FinanceEngine.round2(filteredInc),
                filteredExpense = FinanceEngine.round2(filteredExp),
                filteredFreeBalance = dispBalance,
                isFiltered = isAnyFilterActive,
                activeFilterCount = activeCount
            )
        }
    }

    companion object {
        fun getPersonsForTransaction(tx: Transaction, currentUid: String): List<String> {
            val persons = mutableListOf<String>()

            if (tx.allocations.isNotEmpty()) {
                for (alloc in tx.allocations) {
                    val name = alloc.name.trim()
                    if (alloc.uid == currentUid ||
                        name.equals("você", ignoreCase = true) ||
                        name.equals("meu", ignoreCase = true) ||
                        name.equals("100% meu", ignoreCase = true)
                    ) {
                        if ("100% Meu" !in persons) persons.add("100% Meu")
                    } else if (name.isNotBlank()) {
                        if (name !in persons) persons.add(name)
                    }
                }
            } else {
                val externalName = tx.externalName?.trim()
                val friendName = tx.assignedFriendName?.trim()

                if (!externalName.isNullOrBlank() || tx.splitType == SplitType.EXTERNAL) {
                    val name = if (!externalName.isNullOrBlank()) externalName else "Terceiro Externo"
                    if (name !in persons) persons.add(name)
                    if (currentUid.isNotBlank() && (tx.paidByUid == currentUid || tx.createdByUid == currentUid)) {
                        if ("100% Meu" !in persons) persons.add("100% Meu")
                    }
                } else if (!friendName.isNullOrBlank() || !tx.assignedFriendUid.isNullOrBlank() || tx.splitType == SplitType.FRIEND) {
                    val name = if (!friendName.isNullOrBlank()) friendName else "Amigo"
                    if (name !in persons) persons.add(name)
                    if (currentUid.isNotBlank() && (tx.paidByUid == currentUid || tx.createdByUid == currentUid)) {
                        if ("100% Meu" !in persons) persons.add("100% Meu")
                    }
                } else if (tx.splitType == SplitType.SPLIT_50) {
                    if ("50/50 (Casal)" !in persons) persons.add("50/50 (Casal)")
                    if ("100% Meu" !in persons) persons.add("100% Meu")
                } else if (tx.splitType == SplitType.PARTNER) {
                    if ("Parceiro(a)" !in persons) persons.add("Parceiro(a)")
                } else {
                    if ("100% Meu" !in persons) persons.add("100% Meu")
                }
            }

            if (persons.isEmpty()) {
                persons.add("100% Meu")
            }
            return persons
        }
    }
}
