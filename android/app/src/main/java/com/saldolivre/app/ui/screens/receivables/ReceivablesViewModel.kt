package com.saldolivre.app.ui.screens.receivables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.ReceivableItem
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReceivablesUiState(
    val summary: MonthSummary = MonthSummary(),
    val items: List<ReceivableItem> = emptyList(),
    val isLoading: Boolean = false
)

class ReceivablesViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceivablesUiState(isLoading = true))
    val uiState: StateFlow<ReceivablesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMonthSummary().collect { summary ->
                _uiState.value = ReceivablesUiState(
                    summary = summary,
                    items = summary.receivables,
                    isLoading = false
                )
            }
        }
    }

    fun markAsReimbursed(item: ReceivableItem) {
        viewModelScope.launch {
            repository.markAllocationReimbursed(item.transactionId, item.name)
        }
    }
}
