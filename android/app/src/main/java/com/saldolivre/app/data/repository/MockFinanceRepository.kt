package com.saldolivre.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saldolivre.app.SaldoLivreApplication
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class MockFinanceRepository(
    context: Context? = null
) : FinanceRepository {

    private val appContext: Context? = context ?: runCatching { SaldoLivreApplication.instance }.getOrNull()
    private val prefs: SharedPreferences? = appContext?.getSharedPreferences("saldo_livre_demo_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val PREF_KEY_TXS = "demo_transactions"
    private val PREF_KEY_BILLS = "demo_recurring_bills"
    private val PREF_KEY_CARDS = "demo_cards"
    private val PREF_KEY_FRIENDSHIPS = "demo_friendships"
    private val PREF_KEY_CURRENT_USER = "demo_current_user_uid"
    private val PREF_KEY_ACTIVITY_LOGS = "demo_activity_logs"
    private val PREF_KEY_MONTH_CLOSURES = "demo_month_closures"

    private val userKayo = UserProfile(
        uid = "demo-kayo",
        email = "kayo@demo.app",
        displayName = "Kayo Araújo",
        monthlyIncome = 8500.0
    )

    private val userMarina = UserProfile(
        uid = "demo-marina",
        email = "marina@demo.app",
        displayName = "Marina Costa",
        monthlyIncome = 6200.0
    )

    private val userLucas = UserProfile(
        uid = "demo-lucas",
        email = "lucas@demo.app",
        displayName = "Lucas Silva",
        monthlyIncome = 4500.0
    )

    override val allUsers: List<UserProfile> = listOf(userKayo, userMarina, userLucas)

    private val _currentUser = MutableStateFlow(
        run {
            val savedUid = prefs?.getString(PREF_KEY_CURRENT_USER, null)
            val base = allUsers.find { it.uid == savedUid } ?: userKayo
            val savedIncome = prefs?.getString("demo_income_${base.uid}", null)?.toDoubleOrNull()
            if (savedIncome != null) base.copy(monthlyIncome = savedIncome) else base
        }
    )
    override val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _selectedMonth = MutableStateFlow(FinanceEngine.currentMonthKey())
    override val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _cards = MutableStateFlow(loadStoredCards())
    private val _recurringBills = MutableStateFlow(loadStoredBills())
    private val _transactions = MutableStateFlow(loadStoredTransactions())
    private val _friendships = MutableStateFlow(loadStoredFriendships())
    private val _activityLogs = MutableStateFlow(loadStoredActivityLogs())
    private val _monthClosures = MutableStateFlow(loadStoredMonthClosures())

    override fun getActivityLogs(): Flow<List<ActivityLog>> = _activityLogs.asStateFlow()
    override fun getMonthClosures(): Flow<List<MonthClosure>> = _monthClosures.asStateFlow()

    private fun loadStoredMonthClosures(): List<MonthClosure> {
        val json = prefs?.getString(PREF_KEY_MONTH_CLOSURES, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<MonthClosure>>() {}.type
                val list: List<MonthClosure>? = gson.fromJson(json, type)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun persistMonthClosures(list: List<MonthClosure>) {
        try {
            prefs?.edit()?.putString(PREF_KEY_MONTH_CLOSURES, gson.toJson(list))?.apply()
        } catch (_: Exception) {
        }
    }

    private fun loadStoredActivityLogs(): List<ActivityLog> {
        val json = prefs?.getString(PREF_KEY_ACTIVITY_LOGS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<ActivityLog>>() {}.type
                val list: List<ActivityLog>? = gson.fromJson(json, type)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return buildInitialActivityLogs()
    }

    private fun persistActivityLogs(list: List<ActivityLog>) {
        try {
            prefs?.edit()?.putString(PREF_KEY_ACTIVITY_LOGS, gson.toJson(list))?.apply()
        } catch (_: Exception) {
        }
    }

    override suspend fun logActivity(log: ActivityLog) {
        val current = _activityLogs.value.toMutableList()
        current.add(0, log)
        val trimmed = current.take(100)
        _activityLogs.value = trimmed
        persistActivityLogs(trimmed)
    }

    override suspend fun clearActivityLogs() {
        _activityLogs.value = emptyList()
        persistActivityLogs(emptyList())
    }

    private fun loadStoredTransactions(): List<Transaction> {
        val json = prefs?.getString(PREF_KEY_TXS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Transaction>>() {}.type
                val list: List<Transaction>? = gson.fromJson(json, type)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return buildInitialTransactions()
    }

    private fun persistTransactions(list: List<Transaction>) {
        try {
            prefs?.edit()?.putString(PREF_KEY_TXS, gson.toJson(list))?.apply()
        } catch (_: Exception) {
        }
    }

    private fun loadStoredBills(): List<RecurringBill> {
        val json = prefs?.getString(PREF_KEY_BILLS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<RecurringBill>>() {}.type
                val list: List<RecurringBill>? = gson.fromJson(json, type)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return buildDemoRecurringBills()
    }

    private fun persistBills(list: List<RecurringBill>) {
        try {
            prefs?.edit()?.putString(PREF_KEY_BILLS, gson.toJson(list))?.apply()
        } catch (_: Exception) {
        }
    }

    private fun loadStoredCards(): List<Card> {
        val json = prefs?.getString(PREF_KEY_CARDS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Card>>() {}.type
                val list: List<Card>? = gson.fromJson(json, type)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return buildDemoCards()
    }

    private fun persistCards(list: List<Card>) {
        try {
            prefs?.edit()?.putString(PREF_KEY_CARDS, gson.toJson(list))?.apply()
        } catch (_: Exception) {
        }
    }

    private fun loadStoredFriendships(): List<Friendship> {
        val json = prefs?.getString(PREF_KEY_FRIENDSHIPS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Friendship>>() {}.type
                val list: List<Friendship>? = gson.fromJson(json, type)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return buildDemoFriendships()
    }

    private fun persistFriendships(list: List<Friendship>) {
        try {
            prefs?.edit()?.putString(PREF_KEY_FRIENDSHIPS, gson.toJson(list))?.apply()
        } catch (_: Exception) {
        }
    }

    fun resetDemoData() {
        prefs?.edit()?.clear()?.apply()
        _cards.value = buildDemoCards()
        _recurringBills.value = buildDemoRecurringBills()
        _transactions.value = buildInitialTransactions()
        _friendships.value = buildDemoFriendships()
        _currentUser.value = userKayo
    }

    override fun switchUser(uid: String) {
        val baseTarget = allUsers.find { it.uid == uid } ?: return
        val savedIncome = prefs?.getString("demo_income_${baseTarget.uid}", null)?.toDoubleOrNull()
        val target = if (savedIncome != null) baseTarget.copy(monthlyIncome = savedIncome) else baseTarget
        _currentUser.value = target
        prefs?.edit()?.putString(PREF_KEY_CURRENT_USER, uid)?.apply()
    }

    override fun setSelectedMonth(monthKey: String) {
        _selectedMonth.value = monthKey
    }

    override fun getTransactions(): Flow<List<Transaction>> = _transactions.asStateFlow()

    override fun getCards(): Flow<List<Card>> = _cards.asStateFlow()

    override fun getRecurringBills(): Flow<List<RecurringBill>> = _recurringBills.asStateFlow()

    override fun getMonthSummary(): Flow<MonthSummary> {
        return combine(
            _transactions,
            _currentUser,
            _selectedMonth,
            _cards,
            _monthClosures
        ) { txs, user, month, cards, closures ->
            FinanceEngine.computeSummary(
                all = txs,
                uid = user.uid,
                monthKey = month,
                cards = cards,
                closures = closures
            )
        }
    }

    override suspend fun saveMonthClosure(closure: MonthClosure) {
        val finalClosure = if (closure.id.isBlank()) closure.copy(id = "cls_" + UUID.randomUUID().toString().take(8)) else closure
        _monthClosures.update { current ->
            val filtered = current.filterNot { it.closedMonth == finalClosure.closedMonth && it.uid == finalClosure.uid }
            val updated = filtered + finalClosure
            persistMonthClosures(updated)
            updated
        }

        if (finalClosure.investedAmount > 0.0) {
            val user = _currentUser.value
            val lastDay = FinanceEngine.daysInMonth(finalClosure.closedMonth)
            val investDate = FinanceEngine.dateInMonth(finalClosure.closedMonth, lastDay)
            val investTx = Transaction(
                id = "tx_invest_" + UUID.randomUUID().toString().take(8),
                createdByUid = finalClosure.uid,
                paidByUid = finalClosure.uid,
                visibleToUids = listOf(finalClosure.uid),
                description = "Aporte Sobra ${FinanceEngine.formatMonthLabel(finalClosure.closedMonth)}",
                category = "Investimentos",
                subcategory = "Reserva de Emergência",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.DEBIT,
                status = TransactionStatus.PAID,
                estimatedAmount = finalClosure.investedAmount,
                actualAmount = finalClosure.investedAmount,
                totalAmount = finalClosure.investedAmount,
                userImpact = mapOf(finalClosure.uid to finalClosure.investedAmount),
                dueDate = investDate,
                competenceMonth = finalClosure.closedMonth,
                allocations = listOf(
                    Allocation(
                        type = "PLATFORM_USER",
                        uid = finalClosure.uid,
                        name = user.displayName.ifBlank { "Eu" },
                        amount = finalClosure.investedAmount,
                        reimbursed = false
                    )
                ),
                createdAt = System.currentTimeMillis()
            )
            addTransaction(investTx)
        }
    }

    override suspend fun addTransaction(tx: Transaction) {
        val newTx = if (tx.id.isBlank()) tx.copy(id = "tx_" + UUID.randomUUID().toString().take(8)) else tx
        _transactions.update { current ->
            val updated = listOf(newTx) + current
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun addTransactions(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        val newTxs = transactions.map { tx ->
            if (tx.id.isBlank()) tx.copy(id = "tx_" + UUID.randomUUID().toString().take(8)) else tx
        }
        _transactions.update { current ->
            val updated = newTxs + current
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun updateTransaction(tx: Transaction) {
        _transactions.update { current ->
            val updated = current.map { if (it.id == tx.id) tx else it }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun deleteTransaction(id: String) {
        _transactions.update { current ->
            val updated = current.filter { it.id != id }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun deleteInstallmentGroup(groupId: String): Int {
        if (groupId.isBlank()) return 0
        var count = 0
        _transactions.update { current ->
            val toRemove = current.filter { it.installmentGroupId == groupId }
            count = toRemove.size
            val updated = current.filter { it.installmentGroupId != groupId }
            persistTransactions(updated)
            updated
        }
        return count
    }

    override suspend fun updateInstallmentGroup(groupId: String, input: InstallmentGroupUpdateInput): Int {
        if (groupId.isBlank()) return 0
        val currentTxs = _transactions.value
        val group = currentTxs.filter { it.installmentGroupId == groupId }
            .sortedBy { it.installmentNumber ?: 0 }
        if (group.isEmpty()) return 0

        val user = _currentUser.value
        val partner = allUsers.find { it.uid != user.uid }
        val card = input.cardId?.let { cid -> _cards.value.find { it.id == cid } }
        val payerUid = if (input.paymentMethod == PaymentMethod.CREDIT_CARD && card != null) card.ownerUid else user.uid

        val newDay = input.date?.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull()
        val firstNum = group.first().installmentNumber ?: 1
        val baseMonth = group.first().competenceMonth.takeIf { it.isNotBlank() }
            ?: group.first().dueDate.take(7).takeIf { it.isNotBlank() }
            ?: FinanceEngine.currentMonthKey()

        val updatedMap = group.mapIndexed { idx, tx ->
            val num = tx.installmentNumber ?: (idx + 1)
            val total = tx.installmentsTotal ?: group.size
            val curAmount = if (input.amount != null && input.amount > 0.0) FinanceEngine.round2(input.amount) else tx.totalAmount

            val newDesc = if (!input.description.isNullOrBlank()) {
                FinanceEngine.formatInstallmentDesc(input.description, num, total)
            } else tx.description

            val targetMonth = FinanceEngine.shiftMonth(baseMonth, num - firstNum)
            val newDueDate = if (newDay != null) {
                FinanceEngine.dateInMonth(targetMonth, newDay)
            } else {
                val origDay = tx.dueDate.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull() ?: 10
                FinanceEngine.dateInMonth(targetMonth, origDay)
            }

            val split = FinanceEngine.buildSplit(
                splitType = input.splitType ?: tx.splitType ?: SplitType.MINE,
                total = curAmount,
                myUid = user.uid,
                myName = user.displayName,
                partnerUid = partner?.uid,
                partnerName = partner?.displayName,
                payerUid = payerUid,
                externalName = input.externalName,
                assignedFriendUid = input.friend?.uid,
                assignedFriendName = input.friend?.name,
                friendSharePct = input.friend?.pct
            )

            val updatedTx = tx.copy(
                description = newDesc,
                category = input.category ?: tx.category,
                subcategory = if (input.subcategory != null) input.subcategory else tx.subcategory,
                paymentMethod = input.paymentMethod ?: tx.paymentMethod,
                cardId = if (input.paymentMethod == PaymentMethod.CREDIT_CARD) (input.cardId ?: tx.cardId) else null,
                status = input.status ?: tx.status,
                dueDate = newDueDate,
                competenceMonth = targetMonth,
                estimatedAmount = if ((input.status ?: tx.status) == TransactionStatus.ESTIMATED) curAmount else null,
                actualAmount = if ((input.status ?: tx.status) != TransactionStatus.ESTIMATED) curAmount else null,
                totalAmount = curAmount,
                allocations = split.allocations,
                userImpact = split.userImpact,
                visibleToUids = split.visibleToUids,
                assignedFriendUid = input.friend?.uid ?: tx.assignedFriendUid,
                assignedFriendName = input.friend?.name ?: tx.assignedFriendName,
                friendSharePct = input.friend?.pct ?: tx.friendSharePct,
                acceptanceStatus = if (input.splitType == SplitType.FRIEND) AcceptanceStatus.PENDING_APPROVAL.value else tx.acceptanceStatus,
                splitType = input.splitType ?: tx.splitType
            )
            tx.id to updatedTx
        }.toMap()

        _transactions.update { list ->
            val updated = list.map { updatedMap[it.id] ?: it }
            persistTransactions(updated)
            updated
        }

        return group.size
    }

    override suspend fun markTransactionPaid(id: String) {
        setTransactionStatus(id, TransactionStatus.PAID)
    }

    override suspend fun toggleTransactionStatus(id: String) {
        _transactions.update { current ->
            val updated = current.map { tx ->
                if (tx.id == id) {
                    val newStatus = FinanceEngine.togglePaidStatus(tx.status)
                    FinanceEngine.applyStatusPatch(tx, newStatus)
                } else tx
            }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun cycleTransactionStatus(id: String) {
        _transactions.update { current ->
            val updated = current.map { tx ->
                if (tx.id == id) {
                    val newStatus = FinanceEngine.nextStatus(tx.status)
                    FinanceEngine.applyStatusPatch(tx, newStatus)
                } else tx
            }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun setTransactionStatus(id: String, status: TransactionStatus, actualAmount: Double?) {
        _transactions.update { current ->
            val updated = current.map { tx ->
                if (tx.id == id) {
                    FinanceEngine.applyStatusPatch(tx, status, actualAmount)
                } else tx
            }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun markAllocationReimbursed(transactionId: String, nameOrUid: String) {
        _transactions.update { current ->
            val updated = current.map { tx ->
                if (tx.id == transactionId) {
                    val updatedAllocs = tx.allocations.map { a ->
                        if (a.uid == nameOrUid || a.name.equals(nameOrUid, ignoreCase = true)) {
                            a.copy(reimbursed = true)
                        } else a
                    }
                    tx.copy(allocations = updatedAllocs)
                } else tx
            }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun updateEstimatedWithActual(id: String, actualAmount: Double) {
        _transactions.update { current ->
            val user = _currentUser.value
            val partner = allUsers.find { it.uid != user.uid }
            val updated = current.map { tx ->
                if (tx.id == id) {
                    val rounded = FinanceEngine.round2(actualAmount)
                    val split = tx.splitType?.let { st ->
                        FinanceEngine.buildSplit(
                            splitType = st,
                            total = rounded,
                            myUid = user.uid,
                            myName = user.displayName,
                            partnerUid = partner?.uid,
                            partnerName = partner?.displayName,
                            payerUid = tx.paidByUid
                        )
                    }

                    tx.copy(
                        actualAmount = rounded,
                        totalAmount = rounded,
                        status = TransactionStatus.PENDING,
                        allocations = split?.allocations ?: tx.allocations,
                        userImpact = split?.userImpact ?: tx.userImpact
                    )
                } else tx
            }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun saveRecurringBill(bill: RecurringBill) {
        val user = _currentUser.value
        val newId = if (bill.id.isBlank() || bill.id.startsWith("new_")) "bill_" + UUID.randomUUID().toString().take(8) else bill.id
        val newBill = bill.copy(
            id = newId,
            ownerUid = if (bill.ownerUid.isBlank()) user.uid else bill.ownerUid
        )
        _recurringBills.update { current ->
            val exists = current.any { it.id == newBill.id }
            val updated = if (exists) current.map { if (it.id == newBill.id) newBill else it } else listOf(newBill) + current
            persistBills(updated)
            updated
        }
    }

    override suspend fun deleteRecurringBill(id: String) {
        _recurringBills.update { current ->
            val updated = current.filter { it.id != id }
            persistBills(updated)
            updated
        }
    }

    override suspend fun updateMonthlyIncome(income: Double) {
        val current = _currentUser.value
        val updated = current.copy(monthlyIncome = income)
        _currentUser.value = updated
        prefs?.edit()?.putString("demo_income_${current.uid}", income.toString())?.apply()
    }

    override suspend fun saveCard(card: Card) {
        val user = _currentUser.value
        val newId = if (card.id.isBlank() || card.id.startsWith("new_")) "card_" + UUID.randomUUID().toString().take(8) else card.id
        val newCard = card.copy(
            id = newId,
            ownerUid = if (card.ownerUid.isBlank()) user.uid else card.ownerUid,
            ownerName = if (card.ownerName.isBlank()) user.displayName else card.ownerName
        )
        _cards.update { current ->
            val exists = current.any { it.id == newCard.id }
            val updated = if (exists) current.map { if (it.id == newCard.id) newCard else it } else listOf(newCard) + current
            persistCards(updated)
            updated
        }
    }

    override suspend fun deleteCard(id: String) {
        _cards.update { current ->
            val updated = current.filter { it.id != id }
            persistCards(updated)
            updated
        }
    }

    override fun getFriendships(): Flow<List<Friendship>> = _friendships.asStateFlow()

    override suspend fun sendFriendRequest(email: String): Pair<Boolean, String> {
        val trimmed = email.trim().lowercase()
        val me = _currentUser.value
        if (trimmed == me.email.lowercase()) {
            return Pair(false, "Você não pode adicionar o seu próprio e-mail.")
        }
        val exists = _friendships.value.any { f ->
            (f.requesterEmail.equals(trimmed, ignoreCase = true) || f.recipientEmail.equals(trimmed, ignoreCase = true)) &&
            f.status != FriendshipStatus.REJECTED
        }
        if (exists) {
            return Pair(false, "Já existe uma amizade ou convite pendente com este e-mail.")
        }

        val recipientUser = allUsers.find { it.email.equals(trimmed, ignoreCase = true) }
        val newFriendship = Friendship(
            id = "f_" + UUID.randomUUID().toString().take(8),
            requesterUid = me.uid,
            requesterEmail = me.email,
            requesterName = me.displayName,
            recipientUid = recipientUser?.uid ?: "user_${trimmed.hashCode()}",
            recipientEmail = trimmed,
            recipientName = recipientUser?.displayName ?: trimmed.split("@").first().replaceFirstChar { it.uppercase() },
            status = FriendshipStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )

        _friendships.update { current ->
            val updated = listOf(newFriendship) + current
            persistFriendships(updated)
            updated
        }
        return Pair(true, "Convite enviado com sucesso para $trimmed!")
    }

    override suspend fun respondFriendRequest(friendshipId: String, accept: Boolean) {
        _friendships.update { current ->
            val updated = current.map { f ->
                if (f.id == friendshipId) {
                    f.copy(status = if (accept) FriendshipStatus.ACCEPTED else FriendshipStatus.REJECTED)
                } else f
            }
            persistFriendships(updated)
            updated
        }
    }

    override suspend fun removeFriend(friendshipId: String) {
        _friendships.update { current ->
            val updated = current.filter { it.id != friendshipId }
            persistFriendships(updated)
            updated
        }
    }

    override suspend fun respondChargeRequest(tx: Transaction, accept: Boolean) {
        val newStatus = if (accept) AcceptanceStatus.ACCEPTED.value else AcceptanceStatus.REJECTED.value
        val now = System.currentTimeMillis()
        val gId = tx.installmentGroupId

        _transactions.update { current ->
            val updated = current.map { t ->
                val isTarget = if (!gId.isNullOrBlank()) {
                    t.installmentGroupId == gId && t.assignedFriendUid == tx.assignedFriendUid
                } else {
                    t.id == tx.id
                }
                if (isTarget) {
                    t.copy(
                        acceptanceStatus = newStatus,
                        respondedAt = now
                    )
                } else t
            }
            persistTransactions(updated)
            updated
        }
    }

    override suspend fun generateBillsForMonth(monthKey: String): Pair<Int, Int> {
        val user = _currentUser.value
        val partner = allUsers.find { it.uid != user.uid }
        val res = FinanceEngine.generateMonthBills(
            bills = _recurringBills.value,
            all = _transactions.value,
            monthKey = monthKey,
            me = user,
            partner = partner
        )
        if (res.created.isNotEmpty()) {
            addTransactions(res.created)
        }
        return Pair(res.created.size, res.skipped)
    }

    private fun buildDemoCards(): List<Card> {
        return listOf(
            Card(
                id = "card_nubank",
                ownerUid = userKayo.uid,
                ownerName = "Kayo",
                cardName = "Nubank Ultravioleta",
                closingDay = 3,
                dueDay = 10,
                sharedWithUsers = listOf(SharedUser(userMarina.uid, userMarina.email, "Marina")),
                sharedWithUids = listOf(userMarina.uid),
                frequentThirdParties = listOf("Mãe", "Irmão"),
                color = "violet"
            ),
            Card(
                id = "card_inter",
                ownerUid = userMarina.uid,
                ownerName = "Marina",
                cardName = "Inter Black",
                closingDay = 25,
                dueDay = 5,
                sharedWithUsers = listOf(SharedUser(userKayo.uid, userKayo.email, "Kayo")),
                sharedWithUids = listOf(userKayo.uid),
                frequentThirdParties = listOf("Irmã"),
                color = "orange"
            ),
            Card(
                id = "card_xp",
                ownerUid = userKayo.uid,
                ownerName = "Kayo",
                cardName = "XP Visa Infinite",
                closingDay = 20,
                dueDay = 28,
                sharedWithUsers = emptyList(),
                sharedWithUids = emptyList(),
                frequentThirdParties = emptyList(),
                color = "slate"
            )
        )
    }

    private fun buildDemoRecurringBills(): List<RecurringBill> {
        return listOf(
            RecurringBill(
                id = "rb_aluguel",
                ownerUid = userKayo.uid,
                description = "Aluguel",
                category = "Moradia",
                dueDay = 10,
                defaultAmount = 2400.0,
                estimationRule = EstimationRule.FIXED,
                active = true,
                splitType = SplitType.SPLIT_50
            ),
            RecurringBill(
                id = "rb_luz",
                ownerUid = userKayo.uid,
                description = "Conta de Luz",
                category = "Moradia",
                dueDay = 22,
                defaultAmount = 220.0,
                estimationRule = EstimationRule.AVG_3_MONTHS,
                active = true,
                splitType = SplitType.SPLIT_50
            ),
            RecurringBill(
                id = "rb_agua",
                ownerUid = userKayo.uid,
                description = "Conta de Água",
                category = "Moradia",
                dueDay = 18,
                defaultAmount = 95.0,
                estimationRule = EstimationRule.AVG_3_MONTHS,
                active = true,
                splitType = SplitType.SPLIT_50
            ),
            RecurringBill(
                id = "rb_internet",
                ownerUid = userKayo.uid,
                description = "Internet Fibra 600MB",
                category = "Moradia",
                dueDay = 15,
                defaultAmount = 119.9,
                estimationRule = EstimationRule.FIXED,
                active = true
            ),
            RecurringBill(
                id = "rb_financ",
                ownerUid = userKayo.uid,
                description = "Financiamento do Carro",
                category = "Transporte",
                dueDay = 20,
                defaultAmount = 1350.0,
                estimationRule = EstimationRule.LAST_MONTH,
                active = true
            ),
            RecurringBill(
                id = "rb_academia",
                ownerUid = userKayo.uid,
                description = "Academia SmartFit",
                category = "Saúde",
                dueDay = 5,
                defaultAmount = 129.9,
                estimationRule = EstimationRule.FIXED,
                active = true
            )
        )
    }

    private fun buildInitialTransactions(): List<Transaction> {
        val currentMonth = FinanceEngine.currentMonthKey()

        val list = mutableListOf<Transaction>()

        // Helper
        fun add(
            desc: String,
            cat: String,
            subcat: String? = null,
            total: Double,
            month: String,
            day: Int,
            status: TransactionStatus,
            type: TransactionType = TransactionType.EXPENSE,
            method: PaymentMethod = PaymentMethod.PIX,
            cardId: String? = null,
            splitType: SplitType = SplitType.MINE,
            byUid: String = userKayo.uid,
            payerUid: String = userKayo.uid,
            estimated: Double? = null,
            actual: Double? = null,
            externalName: String? = null,
            reserveCardId: String? = null,
            reimbursed: Boolean = false,
            instNumber: Int? = null,
            instTotal: Int? = null
        ) {
            val split = FinanceEngine.buildSplit(
                splitType = splitType,
                total = total,
                myUid = userKayo.uid,
                myName = "Kayo",
                partnerUid = userMarina.uid,
                partnerName = "Marina",
                payerUid = payerUid,
                externalName = externalName
            )

            val allocs = if (reimbursed) {
                split.allocations.map { it.copy(reimbursed = true) }
            } else split.allocations

            val dueDate = String.format("%s-%02d", month, day)

            list.add(
                Transaction(
                    id = "tx_" + UUID.randomUUID().toString().take(8),
                    createdByUid = byUid,
                    paidByUid = payerUid,
                    cardId = cardId,
                    description = desc,
                    category = cat,
                    subcategory = subcat,
                    type = type,
                    paymentMethod = method,
                    status = status,
                    estimatedAmount = estimated,
                    actualAmount = actual ?: if (status == TransactionStatus.PAID) total else null,
                    totalAmount = total,
                    dueDate = dueDate,
                    competenceMonth = month,
                    visibleToUids = if (reserveCardId != null) listOf(byUid) else split.visibleToUids,
                    userImpact = split.userImpact,
                    allocations = allocs,
                    reserveCardId = reserveCardId,
                    installmentNumber = instNumber,
                    installmentsTotal = instTotal,
                    splitType = splitType
                )
            )
        }

        // Mês Atual: Receitas
        add(
            desc = "Salário Principal",
            cat = "Receita",
            subcat = "Salário Principal",
            total = 8500.0,
            month = currentMonth,
            day = 5,
            status = TransactionStatus.PAID,
            type = TransactionType.INCOME,
            byUid = userKayo.uid
        )
        add(
            desc = "Freela — Landing page",
            cat = "Receita",
            subcat = "Freelance & Prestação de Serviços",
            total = 900.0,
            month = currentMonth,
            day = 25,
            status = TransactionStatus.PENDING,
            type = TransactionType.INCOME,
            byUid = userKayo.uid
        )
        add(
            desc = "Salário Marina",
            cat = "Receita",
            subcat = "Salário Principal",
            total = 6200.0,
            month = currentMonth,
            day = 5,
            status = TransactionStatus.PAID,
            type = TransactionType.INCOME,
            byUid = userMarina.uid,
            payerUid = userMarina.uid
        )

        // Contas Recorrentes Fixas & Previstas
        add(
            desc = "Aluguel",
            cat = "Moradia",
            subcat = "Aluguel & Condomínio",
            total = 2400.0,
            month = currentMonth,
            day = 10,
            status = TransactionStatus.PAID,
            method = PaymentMethod.BILL,
            splitType = SplitType.SPLIT_50,
            actual = 2400.0,
            reimbursed = true
        )
        add(
            desc = "Conta de Luz",
            cat = "Moradia",
            subcat = "Energia Elétrica",
            total = 231.67,
            month = currentMonth,
            day = 22,
            status = TransactionStatus.ESTIMATED,
            method = PaymentMethod.BILL,
            splitType = SplitType.SPLIT_50,
            estimated = 231.67
        )
        add(
            desc = "Conta de Água",
            cat = "Moradia",
            subcat = "Água & Saneamento",
            total = 97.38,
            month = currentMonth,
            day = 18,
            status = TransactionStatus.ESTIMATED,
            method = PaymentMethod.BILL,
            splitType = SplitType.SPLIT_50,
            estimated = 97.38
        )
        add(
            desc = "Internet Fibra 600MB",
            cat = "Moradia",
            subcat = "Internet & TV a Cabo",
            total = 119.90,
            month = currentMonth,
            day = 15,
            status = TransactionStatus.PAID,
            method = PaymentMethod.BILL,
            actual = 119.90
        )
        add(
            desc = "Financiamento do Carro",
            cat = "Financiamentos & Empréstimos",
            subcat = "Financiamento Veicular",
            total = 1371.15,
            month = currentMonth,
            day = 20,
            status = TransactionStatus.PENDING,
            method = PaymentMethod.BILL,
            estimated = 1362.40,
            actual = 1371.15
        )
        add(
            desc = "Aporte Renda Fixa CDB",
            cat = "Investimentos",
            subcat = "Renda Fixa (CDB / Tesouro / LCI / LCA)",
            total = 1000.0,
            month = currentMonth,
            day = 5,
            status = TransactionStatus.PAID,
            method = PaymentMethod.PIX
        )

        // Cartão Nubank Ultravioleta
        add(
            desc = "Supermercado Pão de Açúcar",
            cat = "Alimentação",
            subcat = "Supermercado",
            total = 486.30,
            month = currentMonth,
            day = 3,
            status = TransactionStatus.PENDING,
            method = PaymentMethod.CREDIT_CARD,
            cardId = "card_nubank",
            splitType = SplitType.SPLIT_50
        )
        add(
            desc = "Uber — Aeroporto",
            cat = "Transporte",
            subcat = "Aplicativos (Uber/99)",
            total = 38.50,
            month = currentMonth,
            day = 7,
            status = TransactionStatus.PAID,
            method = PaymentMethod.CREDIT_CARD,
            cardId = "card_nubank"
        )
        add(
            desc = "Farmácia Drogasil",
            cat = "Cartão Terceiros",
            subcat = "Compras de Familiares",
            total = 87.40,
            month = currentMonth,
            day = 9,
            status = TransactionStatus.PENDING,
            method = PaymentMethod.CREDIT_CARD,
            cardId = "card_nubank",
            splitType = SplitType.EXTERNAL,
            externalName = "Mãe"
        )
        add(
            desc = "Tênis Nike",
            cat = "Cartão Terceiros",
            subcat = "Compras de Familiares",
            total = 299.90,
            month = currentMonth,
            day = 11,
            status = TransactionStatus.PENDING,
            method = PaymentMethod.CREDIT_CARD,
            cardId = "card_nubank",
            splitType = SplitType.EXTERNAL,
            externalName = "Irmão"
        )

        // Cartão XP
        add(
            desc = "Parcela Notebook 4/10",
            cat = "Compras",
            subcat = "Eletrônicos & Informática",
            total = 420.00,
            month = currentMonth,
            day = 2,
            status = TransactionStatus.PENDING,
            method = PaymentMethod.CREDIT_CARD,
            cardId = "card_xp",
            instNumber = 4,
            instTotal = 10
        )
        add(
            desc = "iFood Jantar",
            cat = "Alimentação",
            subcat = "Delivery & Lanches",
            total = 72.80,
            month = currentMonth,
            day = 13,
            status = TransactionStatus.PAID,
            method = PaymentMethod.CREDIT_CARD,
            cardId = "card_xp"
        )

        // Cobrança de Amigo Pendente de Aprovação (Unitária)
        list.add(
            Transaction(
                id = "tx_friend_pizza",
                createdByUid = "demo-lucas",
                paidByUid = "demo-lucas",
                description = "Rodízio de Pizza 🍕",
                category = "Alimentação",
                subcategory = "Restaurantes & Bares",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                status = TransactionStatus.PENDING,
                totalAmount = 180.0,
                dueDate = FinanceEngine.todayKey(),
                competenceMonth = currentMonth,
                visibleToUids = listOf("demo-lucas", userKayo.uid),
                assignedFriendUid = userKayo.uid,
                assignedFriendName = userKayo.displayName,
                acceptanceStatus = AcceptanceStatus.PENDING_APPROVAL.value,
                friendSharePct = 50.0,
                allocations = listOf(
                    Allocation(type = "PLATFORM_USER", uid = userKayo.uid, name = userKayo.displayName, amount = 90.0),
                    Allocation(type = "PLATFORM_USER", uid = "demo-lucas", name = "Lucas Silva", amount = 90.0)
                ),
                splitType = SplitType.FRIEND
            )
        )

        // Cobrança de Amigo Parcelada Pendente de Aprovação (3x)
        val groupGid = "grp_beach_trip"
        for (i in 1..3) {
            val due = FinanceEngine.dateInMonth(FinanceEngine.shiftMonth(currentMonth, i - 1), 15)
            list.add(
                Transaction(
                    id = "tx_friend_trip_$i",
                    createdByUid = "demo-lucas",
                    paidByUid = "demo-lucas",
                    description = "Airbnb Fim de Semana Praia ($i/3)",
                    category = "Viagens & Lazer",
                    subcategory = "Hospedagem",
                    type = TransactionType.EXPENSE,
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    status = TransactionStatus.PENDING,
                    totalAmount = 300.0,
                    dueDate = due,
                    competenceMonth = FinanceEngine.shiftMonth(currentMonth, i - 1),
                    visibleToUids = listOf("demo-lucas", userKayo.uid),
                    assignedFriendUid = userKayo.uid,
                    assignedFriendName = userKayo.displayName,
                    acceptanceStatus = AcceptanceStatus.PENDING_APPROVAL.value,
                    friendSharePct = 50.0,
                    installmentGroupId = groupGid,
                    installmentNumber = i,
                    installmentsTotal = 3,
                    allocations = listOf(
                        Allocation(type = "PLATFORM_USER", uid = userKayo.uid, name = userKayo.displayName, amount = 150.0),
                        Allocation(type = "PLATFORM_USER", uid = "demo-lucas", name = "Lucas Silva", amount = 150.0)
                    ),
                    splitType = SplitType.FRIEND
                )
            )
        }

        return list
    }

    private fun buildDemoFriendships(): List<Friendship> {
        return listOf(
            Friendship(
                id = "f_lucas",
                requesterUid = "demo-lucas",
                requesterEmail = "lucas@demo.app",
                requesterName = "Lucas Silva",
                recipientUid = userKayo.uid,
                recipientEmail = userKayo.email,
                recipientName = userKayo.displayName,
                status = FriendshipStatus.ACCEPTED,
                createdAt = System.currentTimeMillis() - 86400000L * 10
            ),
            Friendship(
                id = "f_camila",
                requesterUid = "demo-camila",
                requesterEmail = "camila@demo.app",
                requesterName = "Camila Rocha",
                recipientUid = userKayo.uid,
                recipientEmail = userKayo.email,
                recipientName = userKayo.displayName,
                status = FriendshipStatus.PENDING,
                createdAt = System.currentTimeMillis() - 86400000L * 2
            )
        )
    }

    private fun buildInitialActivityLogs(): List<ActivityLog> {
        val now = System.currentTimeMillis()
        val currentMonth = FinanceEngine.currentMonthKey()
        return listOf(
            ActivityLog(
                id = "log_1",
                actionType = ActivityActionType.TRANSACTION_CREATED,
                title = "Novo Lançamento",
                description = "Supermercado Pão de Açúcar (R$ 280,00)",
                amount = 280.0,
                category = "Alimentação",
                competenceMonth = currentMonth,
                userUid = userKayo.uid,
                userName = userKayo.displayName,
                timestamp = now - 1000 * 60 * 15 // 15 mins ago
            ),
            ActivityLog(
                id = "log_2",
                actionType = ActivityActionType.TRANSACTION_PAID,
                title = "Pagamento Confirmado",
                description = "Conta de Luz Enel marcado como Pago",
                amount = 180.0,
                category = "Moradia",
                competenceMonth = currentMonth,
                userUid = userKayo.uid,
                userName = userKayo.displayName,
                timestamp = now - 1000 * 60 * 60 * 2 // 2 hours ago
            ),
            ActivityLog(
                id = "log_3",
                actionType = ActivityActionType.ESTIMATED_UPDATED,
                title = "Previsão Atualizada",
                description = "Internet Fibra atualizado para R$ 119,90",
                amount = 119.90,
                category = "Moradia",
                competenceMonth = currentMonth,
                userUid = userKayo.uid,
                userName = userKayo.displayName,
                timestamp = now - 1000 * 60 * 60 * 5 // 5 hours ago
            ),
            ActivityLog(
                id = "log_4",
                actionType = ActivityActionType.BILLS_GENERATED,
                title = "Contas do Mês Geradas",
                description = "Geração automática de previsões para $currentMonth",
                competenceMonth = currentMonth,
                userUid = userKayo.uid,
                userName = userKayo.displayName,
                timestamp = now - 1000 * 60 * 60 * 24 // ontem
            ),
            ActivityLog(
                id = "log_5",
                actionType = ActivityActionType.CARD_SAVED,
                title = "Cartão Salvo",
                description = "Cartão Nubank Ultravioleta configurado (Fechamento dia 2)",
                userUid = userKayo.uid,
                userName = userKayo.displayName,
                timestamp = now - 1000 * 60 * 60 * 48 // 2 days ago
            ),
            ActivityLog(
                id = "log_6",
                actionType = ActivityActionType.FRIEND_ACTION,
                title = "Rede de Amizades",
                description = "Lucas Silva conectado na sua rede de compartilhamento",
                userUid = userKayo.uid,
                userName = userKayo.displayName,
                timestamp = now - 1000 * 60 * 60 * 72 // 3 days ago
            )
        )
    }
}
