package com.saldolivre.app.data.repository

import com.saldolivre.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FinanceRepository {
    val currentUser: StateFlow<UserProfile>
    val allUsers: List<UserProfile>
    val selectedMonth: StateFlow<String>

    fun switchUser(uid: String)
    fun setSelectedMonth(monthKey: String)

    fun getTransactions(): Flow<List<Transaction>>
    fun getCards(): Flow<List<Card>>
    fun getRecurringBills(): Flow<List<RecurringBill>>
    fun getMonthSummary(): Flow<MonthSummary>

    suspend fun addTransaction(tx: Transaction)
    suspend fun addTransactions(transactions: List<Transaction>)
    suspend fun updateTransaction(tx: Transaction)
    suspend fun deleteTransaction(id: String)
    suspend fun deleteInstallmentGroup(groupId: String): Int
    suspend fun updateInstallmentGroup(groupId: String, input: InstallmentGroupUpdateInput): Int
    suspend fun markTransactionPaid(id: String)
    suspend fun toggleTransactionStatus(id: String)
    suspend fun cycleTransactionStatus(id: String)
    suspend fun setTransactionStatus(id: String, status: TransactionStatus, actualAmount: Double? = null)
    suspend fun markAllocationReimbursed(transactionId: String, nameOrUid: String)
    suspend fun updateEstimatedWithActual(id: String, actualAmount: Double)
    suspend fun generateBillsForMonth(monthKey: String): Pair<Int, Int>
    suspend fun saveRecurringBill(bill: RecurringBill)
    suspend fun deleteRecurringBill(id: String)
    suspend fun updateMonthlyIncome(income: Double)
    suspend fun saveCard(card: Card)
    suspend fun deleteCard(id: String)

    fun getFriendships(): Flow<List<Friendship>>
    suspend fun sendFriendRequest(email: String): Pair<Boolean, String>
    suspend fun respondFriendRequest(friendshipId: String, accept: Boolean)
    suspend fun removeFriend(friendshipId: String)
    suspend fun respondChargeRequest(tx: Transaction, accept: Boolean)

    fun getActivityLogs(): Flow<List<ActivityLog>>
    suspend fun logActivity(log: ActivityLog)
    suspend fun clearActivityLogs()
}
