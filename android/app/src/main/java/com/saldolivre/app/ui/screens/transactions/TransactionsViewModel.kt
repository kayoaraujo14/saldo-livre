package com.saldolivre.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val selectedMonth: String = FinanceEngine.currentMonthKey(),
    val searchQuery: String = "",
    val typeFilter: TransactionType? = null,
    val statusFilter: TransactionStatus? = null,
    val selectedCategory: String? = null,
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0
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
                repository.getTransactions()
            ) { user, month, allTxs ->
                val monthTxs = allTxs.filter {
                    it.competenceMonth == month && it.visibleToUids.contains(user.uid)
                }.sortedByDescending { it.dueDate }

                val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.effectiveAmount }
                val expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.effectiveAmount }

                _uiState.update { current ->
                    current.copy(
                        transactions = monthTxs,
                        selectedMonth = month,
                        totalIncome = FinanceEngine.round2(income),
                        totalExpense = FinanceEngine.round2(expense)
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
        _uiState.update { it.copy(typeFilter = type) }
        applyFilters()
    }

    fun setStatusFilter(status: TransactionStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        applyFilters()
    }

    fun setCategoryFilter(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        applyFilters()
    }

    fun markAsPaid(id: String) {
        viewModelScope.launch {
            repository.markTransactionPaid(id)
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

        val filtered = state.transactions.filter { tx ->
            val matchesQuery = query.isBlank() ||
                    tx.description.lowercase().contains(query) ||
                    tx.category.lowercase().contains(query) ||
                    (tx.subcategory?.lowercase()?.contains(query) == true)

            val matchesType = state.typeFilter == null || tx.type == state.typeFilter
            val matchesStatus = state.statusFilter == null || tx.status == state.statusFilter
            val matchesCategory = state.selectedCategory == null || tx.category == state.selectedCategory

            matchesQuery && matchesType && matchesStatus && matchesCategory
        }

        _uiState.update { it.copy(filteredTransactions = filtered) }
    }
}
