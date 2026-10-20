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
    suspend fun updateTransaction(tx: Transaction)
    suspend fun deleteTransaction(id: String)
    suspend fun markTransactionPaid(id: String)
    suspend fun markAllocationReimbursed(transactionId: String, nameOrUid: String)
    suspend fun updateEstimatedWithActual(id: String, actualAmount: Double)
}
