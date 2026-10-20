package com.saldolivre.app.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class RecurringTab {
    ALL,
    INCOME,
    EXPENSE
}

data class RecurringBillsUiState(
    val currentUser: UserProfile = UserProfile(),
    val partner: UserProfile? = null,
    val bills: List<RecurringBill> = emptyList(),
    val isLoading: Boolean = false,
    val activeTab: RecurringTab = RecurringTab.ALL,
    val editingIncome: Boolean = false,
    val incomeInput: String = "",
    val editingBill: RecurringBill? = null,
    val isFormOpen: Boolean = false,
    val billToDelete: RecurringBill? = null,
    val userMessage: String? = null
)

class RecurringBillsViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringBillsUiState())
    val uiState: StateFlow<RecurringBillsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.getRecurringBills()
            ) { user, bills ->
                val partner = repository.allUsers.find { it.uid != user.uid }
                user to (bills to partner)
            }.collect { (user, billsAndPartner) ->
                val (bills, partner) = billsAndPartner
                _uiState.update { current ->
                    current.copy(
                        currentUser = user,
                        partner = partner,
                        bills = bills,
                        incomeInput = if (!current.editingIncome) user.monthlyIncome.toString() else current.incomeInput
                    )
                }
            }
        }
    }

    fun setTab(tab: RecurringTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun startEditingIncome() {
        val currentIncome = _uiState.value.currentUser.monthlyIncome
        _uiState.update {
            it.copy(
                editingIncome = true,
                incomeInput = if (currentIncome > 0) currentIncome.toString() else ""
            )
        }
    }

    fun cancelEditingIncome() {
        _uiState.update { it.copy(editingIncome = false) }
    }

    fun setIncomeInput(input: String) {
        _uiState.update { it.copy(incomeInput = input) }
    }

    fun saveMonthlyIncome() {
        val raw = _uiState.value.incomeInput.replace(',', '.').trim()
        val num = raw.toDoubleOrNull()
        if (num != null && num >= 0.0) {
            viewModelScope.launch {
                repository.updateMonthlyIncome(num)
                _uiState.update { it.copy(editingIncome = false, userMessage = "Renda base atualizada!") }
            }
        }
    }

    fun openNewBill() {
        val user = _uiState.value.currentUser
        val initialCategory = "Moradia"
        val initialSubcategory = CategoryRegistry.get(initialCategory).subcategories.firstOrNull()
        val newBill = RecurringBill(
            id = "new_${System.currentTimeMillis()}",
            ownerUid = user.uid,
            description = "",
            category = initialCategory,
            subcategory = initialSubcategory,
            dueDay = 10,
            defaultAmount = 200.0,
            estimationRule = EstimationRule.FIXED,
            active = true,
            splitType = SplitType.MINE,
            paymentMethod = PaymentMethod.BILL,
            type = TransactionType.EXPENSE,
            startMonth = FinanceEngine.currentMonthKey(),
            installmentsCount = 3
        )
        _uiState.update { it.copy(editingBill = newBill, isFormOpen = true) }
    }

    fun editBill(bill: RecurringBill) {
        _uiState.update { it.copy(editingBill = bill, isFormOpen = true) }
    }

    fun closeForm() {
        _uiState.update { it.copy(editingBill = null, isFormOpen = false) }
    }

    fun saveBill(bill: RecurringBill) {
        if (bill.description.isBlank()) return
        viewModelScope.launch {
            repository.saveRecurringBill(bill)
            _uiState.update {
                it.copy(
                    editingBill = null,
                    isFormOpen = false,
                    userMessage = "Molde recorrente salvo com sucesso!"
                )
            }
        }
    }

    fun toggleBillActive(bill: RecurringBill) {
        viewModelScope.launch {
            val updated = bill.copy(active = !bill.active)
            repository.saveRecurringBill(updated)
        }
    }

    fun confirmDelete(bill: RecurringBill) {
        _uiState.update { it.copy(billToDelete = bill) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(billToDelete = null) }
    }

    fun executeDelete() {
        val toDelete = _uiState.value.billToDelete ?: return
        viewModelScope.launch {
            repository.deleteRecurringBill(toDelete.id)
            _uiState.update {
                it.copy(
                    billToDelete = null,
                    editingBill = if (it.editingBill?.id == toDelete.id) null else it.editingBill,
                    isFormOpen = if (it.editingBill?.id == toDelete.id) false else it.isFormOpen,
                    userMessage = "Molde excluído com sucesso."
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
