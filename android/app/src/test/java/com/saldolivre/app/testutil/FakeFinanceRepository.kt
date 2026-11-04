package com.saldolivre.app.testutil

import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeFinanceRepository(
    initialUser: UserProfile = UserProfile(uid = "user_test", displayName = "Usuário Teste", monthlyIncome = 5000.0),
    initialMonth: String = FinanceEngine.currentMonthKey()
) : FinanceRepository {

    private val _currentUser = MutableStateFlow(initialUser)
    override val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _allUsers = mutableListOf(initialUser)
    override val allUsers: List<UserProfile> get() = _allUsers

    private val _selectedMonth = MutableStateFlow(initialMonth)
    override val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    val cardsFlow = MutableStateFlow<List<Card>>(emptyList())
    val billsFlow = MutableStateFlow<List<RecurringBill>>(emptyList())
    val closuresFlow = MutableStateFlow<List<MonthClosure>>(emptyList())
    val summaryFlow = MutableStateFlow(MonthSummary())
    val friendshipsFlow = MutableStateFlow<List<Friendship>>(emptyList())
    val activityLogsFlow = MutableStateFlow<List<ActivityLog>>(emptyList())

    override fun switchUser(uid: String) {
        val found = _allUsers.find { it.uid == uid } ?: UserProfile(uid = uid)
        _currentUser.value = found
    }

    override fun setSelectedMonth(monthKey: String) {
        _selectedMonth.value = monthKey
    }

    override fun getTransactions(): Flow<List<Transaction>> = transactionsFlow
    override fun getCards(): Flow<List<Card>> = cardsFlow
    override fun getRecurringBills(): Flow<List<RecurringBill>> = billsFlow
    override fun getMonthClosures(): Flow<List<MonthClosure>> = closuresFlow
    override fun getMonthSummary(): Flow<MonthSummary> = summaryFlow

    override suspend fun addTransaction(tx: Transaction) {
        transactionsFlow.value = transactionsFlow.value + tx
    }

    override suspend fun addTransactions(transactions: List<Transaction>) {
        transactionsFlow.value = transactionsFlow.value + transactions
    }

    override suspend fun updateTransaction(tx: Transaction) {
        transactionsFlow.value = transactionsFlow.value.map { if (it.id == tx.id) tx else it }
    }

    override suspend fun deleteTransaction(id: String) {
        transactionsFlow.value = transactionsFlow.value.filterNot { it.id == id }
    }

    override suspend fun saveMonthClosure(closure: MonthClosure) {
        closuresFlow.value = closuresFlow.value.filterNot { it.closedMonth == closure.closedMonth } + closure
    }

    override suspend fun deleteMonthClosure(closedMonth: String) {
        closuresFlow.value = closuresFlow.value.filterNot { it.closedMonth == closedMonth }
    }

    override suspend fun deleteInstallmentGroup(groupId: String): Int = 0
    override suspend fun updateInstallmentGroup(groupId: String, input: InstallmentGroupUpdateInput): Int = 0

    override suspend fun markTransactionPaid(id: String) {
        transactionsFlow.value = transactionsFlow.value.map {
            if (it.id == id) it.copy(status = TransactionStatus.PAID) else it
        }
    }

    override suspend fun toggleTransactionStatus(id: String) {
        transactionsFlow.value = transactionsFlow.value.map {
            if (it.id == id) {
                val next = if (it.status == TransactionStatus.PAID) TransactionStatus.PENDING else TransactionStatus.PAID
                it.copy(status = next)
            } else it
        }
    }

    override suspend fun cycleTransactionStatus(id: String) {}

    override suspend fun setTransactionStatus(id: String, status: TransactionStatus, actualAmount: Double?) {
        transactionsFlow.value = transactionsFlow.value.map {
            if (it.id == id) it.copy(status = status, actualAmount = actualAmount ?: it.actualAmount) else it
        }
    }

    override suspend fun markAllocationReimbursed(transactionId: String, nameOrUid: String) {}
    override suspend fun updateEstimatedWithActual(id: String, actualAmount: Double) {}
    override suspend fun generateBillsForMonth(monthKey: String): Pair<Int, Int> = 0 to 0
    override suspend fun autoLaunchDueBillsForMonth(monthKey: String, todayStr: String): FinanceEngine.AutoLaunchBillsResult =
        FinanceEngine.AutoLaunchBillsResult(created = emptyList(), skipped = 0, launchedBillIds = emptyList())

    override suspend fun markRecurringResolved(monthKey: String, billId: String) {}
    override suspend fun getResolvedRecurring(): Map<String, List<String>> = emptyMap()

    override suspend fun saveRecurringBill(bill: RecurringBill) {
        billsFlow.value = billsFlow.value.filterNot { it.id == bill.id } + bill
    }

    override suspend fun deleteRecurringBill(id: String) {
        billsFlow.value = billsFlow.value.filterNot { it.id == id }
    }

    override suspend fun updateMonthlyIncome(income: Double) {
        _currentUser.value = _currentUser.value.copy(monthlyIncome = income)
    }

    override suspend fun saveCard(card: Card) {
        cardsFlow.value = cardsFlow.value.filterNot { it.id == card.id } + card
    }

    override suspend fun deleteCard(id: String) {
        cardsFlow.value = cardsFlow.value.filterNot { it.id == id }
    }

    override suspend fun syncCardTransactionsVisibility(card: Card) {}

    override fun getFriendships(): Flow<List<Friendship>> = friendshipsFlow
    override suspend fun sendFriendRequest(email: String): Pair<Boolean, String> = true to "Success"
    override suspend fun respondFriendRequest(friendshipId: String, accept: Boolean) {}
    override suspend fun removeFriend(friendshipId: String) {}
    override suspend fun respondChargeRequest(tx: Transaction, accept: Boolean) {}

    override fun getActivityLogs(): Flow<List<ActivityLog>> = activityLogsFlow
    override suspend fun logActivity(log: ActivityLog) {}
    override suspend fun clearActivityLogs() {}

    override suspend fun saveUserProfile(profile: UserProfile) {
        _currentUser.value = profile
    }

    override suspend fun updateInvestmentAssetBalances(balances: Map<String, Double>) {}
    override suspend fun updateInvestmentMonthlyTarget(target: Double) {}
    override suspend fun updateCategoryBudgets(budgets: Map<String, Double>, targetMonth: String?) {
        val current = _currentUser.value
        val updatedMonthly = if (!targetMonth.isNullOrBlank()) {
            current.monthlyCategoryBudgets.toMutableMap().apply {
                put(targetMonth, budgets)
            }
        } else {
            current.monthlyCategoryBudgets
        }
        _currentUser.value = current.copy(
            categoryBudgets = budgets,
            monthlyCategoryBudgets = updatedMonthly
        )
    }
}
