package com.saldolivre.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/**
 * Repositório blindado e integrado 100% em tempo real ao Cloud Firestore do Firebase.
 * Conecta às coleções 'transactions', 'cards', 'recurring_bills' e 'users' com parsing seguro contra crashes.
 */
class FirestoreFinanceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : FinanceRepository {

    private val _currentUser = MutableStateFlow(
        UserProfile(
            uid = auth.currentUser?.uid ?: "default_uid",
            email = auth.currentUser?.email ?: "usuario@saldolivre.app",
            displayName = auth.currentUser?.displayName ?: "Meu Perfil",
            monthlyIncome = 5000.0
        )
    )
    override val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _selectedMonth = MutableStateFlow(FinanceEngine.currentMonthKey())
    override val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    override val allUsers: List<UserProfile>
        get() = listOf(_currentUser.value)

    init {
        val user = auth.currentUser
        if (user != null && user.uid.isNotBlank()) {
            _currentUser.value = UserProfile(
                uid = user.uid,
                email = user.email ?: "",
                displayName = if (!user.displayName.isNullOrBlank()) user.displayName!! else "Meu Perfil",
                photoURL = user.photoUrl?.toString(),
                monthlyIncome = 5000.0
            )

            // Escuta perfil em tempo real no Firestore /users/{uid}
            firestore.collection("users").document(user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && snapshot.exists()) {
                        val profile = parseUserProfile(snapshot)
                        if (profile != null) {
                            _currentUser.value = profile
                        }
                    } else if (snapshot != null && !snapshot.exists()) {
                        val defaultDoc = mapOf(
                            "uid" to user.uid,
                            "email" to (user.email ?: ""),
                            "displayName" to (user.displayName ?: "Meu Perfil"),
                            "monthlyIncome" to 5000.0
                        )
                        firestore.collection("users").document(user.uid).set(defaultDoc)
                    }
                }
        }
    }

    override fun switchUser(uid: String) {
        // No modo Firebase Auth, o usuário ativo é controlado pela sessão do Firebase
    }

    override fun setSelectedMonth(monthKey: String) {
        _selectedMonth.value = monthKey
    }

    override fun getTransactions(): Flow<List<Transaction>> = callbackFlow {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration: ListenerRegistration = firestore.collection("transactions")
            .whereArrayContains("visibleToUids", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val txs = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseTransaction(doc)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(txs)
            }

        awaitClose { registration.remove() }
    }

    override fun getCards(): Flow<List<Card>> = callbackFlow {
        val currentUid = auth.currentUser?.uid ?: _currentUser.value.uid
        if (currentUid.isBlank() || currentUid == "default_uid") {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val ownedCardsMap = mutableMapOf<String, Card>()
        val sharedCardsMap = mutableMapOf<String, Card>()

        fun emitCombined() {
            val combined = (ownedCardsMap + sharedCardsMap).values.toList()
            trySend(combined)
        }

        val regOwned = firestore.collection("cards")
            .whereEqualTo("ownerUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                ownedCardsMap.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        val card = parseCard(doc)
                        ownedCardsMap[card.id] = card
                    } catch (_: Exception) {}
                }
                emitCombined()
            }

        val regShared = firestore.collection("cards")
            .whereArrayContains("sharedWithUids", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                sharedCardsMap.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        val card = parseCard(doc)
                        sharedCardsMap[card.id] = card
                    } catch (_: Exception) {}
                }
                emitCombined()
            }

        awaitClose {
            regOwned.remove()
            regShared.remove()
        }
    }

    override fun getRecurringBills(): Flow<List<RecurringBill>> = callbackFlow {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection("recurring_bills")
            .whereEqualTo("ownerUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val bills = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseRecurringBill(doc)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(bills)
            }

        awaitClose { registration.remove() }
    }

    override fun getMonthClosures(): Flow<List<MonthClosure>> = callbackFlow {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = firestore.collection("users")
            .document(currentUid)
            .collection("month_closures")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val closures = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseMonthClosure(doc)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(closures)
            }

        awaitClose { registration.remove() }
    }

    override fun getMonthSummary(): Flow<MonthSummary> {
        return combine(
            getTransactions(),
            _currentUser,
            _selectedMonth,
            getCards(),
            getMonthClosures()
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
        val currentUid = auth.currentUser?.uid ?: closure.uid
        if (currentUid.isBlank()) return

        try {
            val docRef = if (closure.id.isNotBlank()) {
                firestore.collection("users").document(currentUid).collection("month_closures").document(closure.id)
            } else {
                firestore.collection("users").document(currentUid).collection("month_closures").document(closure.closedMonth)
            }
            val finalClosure = closure.copy(id = docRef.id, uid = currentUid)
            docRef.set(monthClosureToMap(finalClosure)).await()

            if (closure.investedAmount > 0.0) {
                val user = _currentUser.value
                val lastDay = FinanceEngine.daysInMonth(closure.closedMonth)
                val investDate = FinanceEngine.dateInMonth(closure.closedMonth, lastDay)
                val investTx = Transaction(
                    id = "",
                    createdByUid = currentUid,
                    paidByUid = currentUid,
                    visibleToUids = listOf(currentUid),
                    description = "Aporte Sobra ${FinanceEngine.formatMonthLabel(closure.closedMonth)}",
                    category = "Investimentos",
                    subcategory = "Reserva de Emergência",
                    type = TransactionType.EXPENSE,
                    paymentMethod = PaymentMethod.DEBIT,
                    status = TransactionStatus.PAID,
                    estimatedAmount = closure.investedAmount,
                    actualAmount = closure.investedAmount,
                    totalAmount = closure.investedAmount,
                    userImpact = mapOf(currentUid to closure.investedAmount),
                    dueDate = investDate,
                    competenceMonth = closure.closedMonth,
                    allocations = listOf(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = currentUid,
                            name = user.displayName.ifBlank { "Eu" },
                            amount = closure.investedAmount,
                            reimbursed = false
                        )
                    ),
                    createdAt = System.currentTimeMillis()
                )
                addTransaction(investTx)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreFinanceRepo", "Erro ao salvar fechamento de mês", e)
            throw e
        }
    }

    override suspend fun deleteMonthClosure(closedMonth: String) {
        val currentUid = auth.currentUser?.uid ?: _currentUser.value.uid
        if (currentUid.isBlank()) return
        try {
            firestore.collection("users")
                .document(currentUid)
                .collection("month_closures")
                .document(closedMonth)
                .delete()
                .await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreFinanceRepo", "Erro ao excluir fechamento de mês", e)
        }
    }

    override suspend fun addTransaction(tx: Transaction) {
        val docRef = if (tx.id.isNotBlank()) {
            firestore.collection("transactions").document(tx.id)
        } else {
            firestore.collection("transactions").document()
        }
        val finalTx = tx.copy(id = docRef.id)
        docRef.set(transactionToMap(finalTx)).await()
    }

    override suspend fun addTransactions(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        val batch = firestore.batch()
        for (tx in transactions) {
            val docRef = if (tx.id.isNotBlank()) {
                firestore.collection("transactions").document(tx.id)
            } else {
                firestore.collection("transactions").document()
            }
            val finalTx = if (tx.id.isBlank()) tx.copy(id = docRef.id) else tx
            batch.set(docRef, transactionToMap(finalTx))
        }
        batch.commit().await()
    }

    override suspend fun updateTransaction(tx: Transaction) {
        if (tx.id.isNotBlank()) {
            firestore.collection("transactions").document(tx.id).set(transactionToMap(tx)).await()
        }
    }

    override suspend fun deleteTransaction(id: String) {
        if (id.isNotBlank()) {
            firestore.collection("transactions").document(id).delete().await()
        }
    }

    override suspend fun deleteInstallmentGroup(groupId: String): Int {
        if (groupId.isBlank()) return 0
        val all = getTransactions().first()
        val targetIds = all.filter { it.installmentGroupId == groupId }.map { it.id }
        if (targetIds.isEmpty()) return 0

        for (chunk in targetIds.chunked(450)) {
            val batch = firestore.batch()
            for (id in chunk) {
                batch.delete(firestore.collection("transactions").document(id))
            }
            batch.commit().await()
        }
        return targetIds.size
    }

    override suspend fun updateInstallmentGroup(groupId: String, input: InstallmentGroupUpdateInput): Int {
        if (groupId.isBlank()) return 0
        val all = getTransactions().first()
        val group = all.filter { it.installmentGroupId == groupId }
            .sortedBy { it.installmentNumber ?: 0 }
        if (group.isEmpty()) return 0

        val user = _currentUser.value
        val partner = allUsers.find { it.uid != user.uid }
        val cards = getCards().first()
        val card = input.cardId?.let { cid -> cards.find { it.id == cid } }
        val payerUid = if (input.paymentMethod == PaymentMethod.CREDIT_CARD && card != null) card.ownerUid else user.uid

        val newDay = input.date?.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull()
        val firstNum = group.first().installmentNumber ?: 1
        val baseMonth = group.first().competenceMonth.takeIf { it.isNotBlank() }
            ?: group.first().dueDate.take(7).takeIf { it.isNotBlank() }
            ?: FinanceEngine.currentMonthKey()

        val updatedTxs = group.mapIndexed { idx, tx ->
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

            tx.copy(
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
        }

        for (chunk in updatedTxs.chunked(450)) {
            val batch = firestore.batch()
            for (tx in chunk) {
                batch.set(firestore.collection("transactions").document(tx.id), transactionToMap(tx))
            }
            batch.commit().await()
        }

        return group.size
    }

    override suspend fun markTransactionPaid(id: String) {
        setTransactionStatus(id, TransactionStatus.PAID)
    }

    override suspend fun toggleTransactionStatus(id: String) {
        if (id.isBlank()) return
        val docRef = firestore.collection("transactions").document(id)
        val snap = docRef.get().await()
        if (!snap.exists()) return
        val tx = parseTransaction(snap)
        val newStatus = FinanceEngine.togglePaidStatus(tx.status)
        val updatedTx = FinanceEngine.applyStatusPatch(tx, newStatus)
        docRef.set(transactionToMap(updatedTx)).await()
    }

    override suspend fun cycleTransactionStatus(id: String) {
        if (id.isBlank()) return
        val docRef = firestore.collection("transactions").document(id)
        val snap = docRef.get().await()
        if (!snap.exists()) return
        val tx = parseTransaction(snap)
        val newStatus = FinanceEngine.nextStatus(tx.status)
        val updatedTx = FinanceEngine.applyStatusPatch(tx, newStatus)
        docRef.set(transactionToMap(updatedTx)).await()
    }

    override suspend fun setTransactionStatus(id: String, status: TransactionStatus, actualAmount: Double?) {
        if (id.isBlank()) return
        val docRef = firestore.collection("transactions").document(id)
        val snap = docRef.get().await()
        if (!snap.exists()) return
        val tx = parseTransaction(snap)
        val updatedTx = FinanceEngine.applyStatusPatch(tx, status, actualAmount)
        docRef.set(transactionToMap(updatedTx)).await()
    }

    override suspend fun markAllocationReimbursed(transactionId: String, nameOrUid: String) {
        val docRef = firestore.collection("transactions").document(transactionId)
        val snap = docRef.get().await()
        if (!snap.exists()) return
        val tx = parseTransaction(snap)

        val updatedAllocs = tx.allocations.map { a ->
            if (a.uid == nameOrUid || a.name.equals(nameOrUid, ignoreCase = true)) {
                a.copy(reimbursed = true)
            } else a
        }

        val allocsMap = updatedAllocs.map { a ->
            mapOf(
                "type" to a.type,
                "uid" to a.uid,
                "name" to a.name,
                "amount" to a.amount,
                "reimbursed" to a.reimbursed
            )
        }

        docRef.update("allocations", allocsMap).await()
    }

    override suspend fun updateEstimatedWithActual(id: String, actualAmount: Double) {
        setTransactionStatus(id, TransactionStatus.PENDING, actualAmount)
    }

    override suspend fun generateBillsForMonth(monthKey: String): Pair<Int, Int> {
        val user = _currentUser.value
        val allTxs = getTransactions().first()
        val bills = getRecurringBills().first()
        val res = FinanceEngine.generateMonthBills(
            bills = bills,
            all = allTxs,
            monthKey = monthKey,
            me = user,
            partner = null
        )
        if (res.created.isNotEmpty()) {
            addTransactions(res.created)
        }
        return Pair(res.created.size, res.skipped)
    }

    override suspend fun autoLaunchDueBillsForMonth(monthKey: String, todayStr: String): FinanceEngine.AutoLaunchBillsResult {
        val user = _currentUser.value
        val allTxs = getTransactions().first()
        val bills = getRecurringBills().first()
        val res = FinanceEngine.autoLaunchDueBills(
            bills = bills,
            all = allTxs,
            monthKey = monthKey,
            todayStr = todayStr,
            me = user,
            partner = null
        )
        if (res.created.isNotEmpty()) {
            addTransactions(res.created)
        }
        return res
    }

    override suspend fun saveRecurringBill(bill: RecurringBill) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        val billId = if (bill.id.isBlank() || bill.id.startsWith("new_")) {
            firestore.collection("recurring_bills").document().id
        } else {
            bill.id
        }
        val toSave = bill.copy(
            id = billId,
            ownerUid = if (bill.ownerUid.isBlank()) uid else bill.ownerUid
        )
        firestore.collection("recurring_bills").document(billId).set(recurringBillToMap(toSave)).await()
    }

    override suspend fun deleteRecurringBill(id: String) {
        firestore.collection("recurring_bills").document(id).delete().await()
    }

    override suspend fun updateMonthlyIncome(income: Double) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        _currentUser.value = _currentUser.value.copy(monthlyIncome = income)
        if (uid.isNotBlank() && uid != "default_uid") {
            firestore.collection("users").document(uid).update("monthlyIncome", income).await()
        }
    }

    override suspend fun saveCard(card: Card) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        val cardId = if (card.id.isBlank() || card.id.startsWith("new_")) {
            firestore.collection("cards").document().id
        } else {
            card.id
        }
        val toSave = card.copy(
            id = cardId,
            ownerUid = if (card.ownerUid.isBlank()) uid else card.ownerUid,
            ownerName = if (card.ownerName.isBlank()) _currentUser.value.displayName else card.ownerName
        )
        firestore.collection("cards").document(cardId).set(cardToMap(toSave)).await()
    }

    override suspend fun deleteCard(id: String) {
        firestore.collection("cards").document(id).delete().await()
    }

    override fun getFriendships(): Flow<List<Friendship>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        if (uid.isBlank() || uid == "default_uid") {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = firestore.collection("friendships")
            .where(
                Filter.or(
                    Filter.equalTo("requesterUid", uid),
                    Filter.equalTo("recipientUid", uid)
                )
            )
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { parseFriendship(it) }
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun sendFriendRequest(email: String): Pair<Boolean, String> {
        val trimmed = email.trim().lowercase()
        val me = _currentUser.value
        val myUid = auth.currentUser?.uid ?: me.uid
        if (trimmed == me.email.lowercase()) {
            return Pair(false, "Você não pode adicionar o seu próprio e-mail.")
        }

        val userSnap = firestore.collection("users")
            .whereEqualTo("email", trimmed)
            .limit(1)
            .get()
            .await()

        val recipientDoc = userSnap.documents.firstOrNull()
        val recipientUid = recipientDoc?.id ?: "pending_${trimmed.hashCode()}"
        val recipientName = recipientDoc?.getString("displayName") ?: trimmed.split("@").first().replaceFirstChar { it.uppercase() }

        val existing = firestore.collection("friendships")
            .whereEqualTo("requesterUid", myUid)
            .whereEqualTo("recipientEmail", trimmed)
            .get()
            .await()

        if (!existing.isEmpty) {
            return Pair(false, "Você já enviou um convite para este e-mail.")
        }

        val friendshipId = firestore.collection("friendships").document().id
        val data = mapOf(
            "id" to friendshipId,
            "requesterUid" to myUid,
            "requesterEmail" to me.email,
            "requesterName" to me.displayName,
            "recipientUid" to recipientUid,
            "recipientEmail" to trimmed,
            "recipientName" to recipientName,
            "status" to FriendshipStatus.PENDING.value,
            "createdAt" to System.currentTimeMillis()
        )
        firestore.collection("friendships").document(friendshipId).set(data).await()
        return Pair(true, "Convite enviado com sucesso para $trimmed!")
    }

    override suspend fun respondFriendRequest(friendshipId: String, accept: Boolean) {
        val status = if (accept) FriendshipStatus.ACCEPTED.value else FriendshipStatus.REJECTED.value
        firestore.collection("friendships").document(friendshipId)
            .update("status", status).await()
    }

    override suspend fun removeFriend(friendshipId: String) {
        firestore.collection("friendships").document(friendshipId).delete().await()
    }

    override suspend fun respondChargeRequest(tx: Transaction, accept: Boolean) {
        val status = if (accept) AcceptanceStatus.ACCEPTED.value else AcceptanceStatus.REJECTED.value
        val now = System.currentTimeMillis()
        val gId = tx.installmentGroupId

        val allTxs = getTransactions().first()
        val targetIds = if (!gId.isNullOrBlank()) {
            allTxs.filter { it.installmentGroupId == gId && it.assignedFriendUid == tx.assignedFriendUid }.map { it.id }
        } else {
            listOf(tx.id)
        }

        val batch = firestore.batch()
        for (id in targetIds) {
            val ref = firestore.collection("transactions").document(id)
            batch.update(ref, mapOf(
                "acceptanceStatus" to status,
                "respondedAt" to now
            ))
        }
        batch.commit().await()
    }

    override fun getActivityLogs(): Flow<List<ActivityLog>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = firestore.collection("users")
            .document(uid)
            .collection("activity_logs")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val actionTypeStr = doc.getString("actionType") ?: "TRANSACTION_CREATED"
                        val type = ActivityActionType.entries.find {
                            it.name.equals(actionTypeStr, ignoreCase = true)
                        } ?: ActivityActionType.TRANSACTION_CREATED

                        ActivityLog(
                            id = doc.getString("id") ?: doc.id,
                            actionType = type,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            amount = doc.getDouble("amount"),
                            category = doc.getString("category"),
                            competenceMonth = doc.getString("competenceMonth"),
                            userUid = doc.getString("userUid") ?: uid,
                            userName = doc.getString("userName") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun logActivity(log: ActivityLog) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val map = mapOf(
                "id" to log.id,
                "actionType" to log.actionType.name,
                "title" to log.title,
                "description" to log.description,
                "amount" to log.amount,
                "category" to log.category,
                "competenceMonth" to log.competenceMonth,
                "userUid" to (if (log.userUid.isNotBlank()) log.userUid else uid),
                "userName" to log.userName,
                "timestamp" to log.timestamp
            )
            firestore.collection("users")
                .document(uid)
                .collection("activity_logs")
                .document(log.id)
                .set(map)
                .await()
        } catch (_: Exception) {
        }
    }

    override suspend fun clearActivityLogs() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val snap = firestore.collection("users")
                .document(uid)
                .collection("activity_logs")
                .get()
                .await()
            val batch = firestore.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (_: Exception) {
        }
    }

    private fun parseFriendship(doc: DocumentSnapshot): Friendship? {
        if (!doc.exists()) return null
        return Friendship(
            id = doc.id,
            requesterUid = doc.getString("requesterUid") ?: "",
            requesterEmail = doc.getString("requesterEmail") ?: "",
            requesterName = doc.getString("requesterName") ?: "",
            recipientUid = doc.getString("recipientUid") ?: "",
            recipientEmail = doc.getString("recipientEmail") ?: "",
            recipientName = doc.getString("recipientName") ?: "",
            status = FriendshipStatus.from(doc.getString("status")),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    // Parsers seguros
    private fun parseUserProfile(doc: DocumentSnapshot): UserProfile? {
        if (!doc.exists()) return null
        return UserProfile(
            uid = doc.id,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: "Meu Perfil",
            photoURL = doc.getString("photoURL"),
            monthlyIncome = doc.getDouble("monthlyIncome") ?: 5000.0
        )
    }

    private fun parseCard(doc: DocumentSnapshot): Card {
        val rawShared = (doc.get("sharedWithUids") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val ownerUid = doc.getString("ownerUid") ?: ""
        val sharedUids = if (rawShared.isEmpty() && ownerUid.isNotBlank()) listOf(ownerUid) else rawShared
        val rawParties = (doc.get("frequentThirdParties") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        val rawUsers = (doc.get("sharedWithUsers") as? List<*>)?.mapNotNull { item ->
            if (item is Map<*, *>) {
                SharedUser(
                    uid = item["uid"]?.toString() ?: "",
                    email = item["email"]?.toString() ?: "",
                    name = item["name"]?.toString() ?: ""
                )
            } else null
        } ?: emptyList()

        return Card(
            id = doc.id,
            ownerUid = ownerUid,
            ownerName = doc.getString("ownerName") ?: "",
            cardName = doc.getString("cardName") ?: "Cartão",
            closingDay = doc.getLong("closingDay")?.toInt() ?: 1,
            dueDay = doc.getLong("dueDay")?.toInt() ?: 10,
            sharedWithUsers = rawUsers,
            sharedWithUids = sharedUids,
            frequentThirdParties = rawParties,
            color = doc.getString("color") ?: "violet",
            limit = doc.getDouble("limit")
        )
    }

    private fun cardToMap(card: Card): Map<String, Any?> {
        return mapOf(
            "id" to card.id,
            "ownerUid" to card.ownerUid,
            "ownerName" to card.ownerName,
            "cardName" to card.cardName,
            "closingDay" to card.closingDay,
            "dueDay" to card.dueDay,
            "sharedWithUsers" to card.sharedWithUsers.map {
                mapOf("uid" to it.uid, "email" to it.email, "name" to it.name)
            },
            "sharedWithUids" to card.sharedWithUids,
            "frequentThirdParties" to card.frequentThirdParties,
            "color" to card.color,
            "limit" to card.limit
        )
    }

    private fun parseRecurringBill(doc: DocumentSnapshot): RecurringBill {
        val typeStr = doc.getString("type") ?: "expense"
        val payStr = doc.getString("paymentMethod")
        val splitStr = doc.getString("splitType")
        val ruleStr = doc.getString("estimationRule")

        return RecurringBill(
            id = doc.id,
            ownerUid = doc.getString("ownerUid") ?: "",
            description = doc.getString("description") ?: "",
            category = doc.getString("category") ?: "Moradia",
            subcategory = doc.getString("subcategory"),
            dueDay = doc.getLong("dueDay")?.toInt() ?: 10,
            defaultAmount = doc.getDouble("defaultAmount") ?: 0.0,
            estimationRule = EstimationRule.from(ruleStr),
            active = doc.getBoolean("active") ?: true,
            splitType = SplitType.from(splitStr),
            paymentMethod = PaymentMethod.from(payStr),
            type = TransactionType.from(typeStr),
            startMonth = doc.getString("startMonth"),
            endMonth = doc.getString("endMonth"),
            installmentsCount = doc.getLong("installmentsCount")?.toInt()
        )
    }

    private fun recurringBillToMap(bill: RecurringBill): Map<String, Any?> {
        return mapOf(
            "id" to bill.id,
            "ownerUid" to bill.ownerUid,
            "description" to bill.description,
            "category" to bill.category,
            "subcategory" to bill.subcategory,
            "dueDay" to bill.dueDay,
            "defaultAmount" to bill.defaultAmount,
            "estimationRule" to bill.estimationRule.value,
            "active" to bill.active,
            "splitType" to bill.splitType?.value,
            "paymentMethod" to bill.paymentMethod?.value,
            "type" to bill.type.value,
            "startMonth" to bill.startMonth,
            "endMonth" to bill.endMonth,
            "installmentsCount" to bill.installmentsCount
        )
    }

    private fun parseTransaction(doc: DocumentSnapshot): Transaction {
        val rawVisible = (doc.get("visibleToUids") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val createdUid = doc.getString("createdByUid") ?: ""
        val visibleUids = if (rawVisible.isEmpty() && createdUid.isNotBlank()) listOf(createdUid) else rawVisible

        val rawAllocationsData = doc.get("allocations")
        val allocationsList: List<*> = when (rawAllocationsData) {
            is List<*> -> rawAllocationsData
            is Map<*, *> -> rawAllocationsData.values.toList()
            else -> emptyList<Any>()
        }
        val rawAllocations = allocationsList.mapNotNull { item ->
            if (item is Map<*, *>) {
                Allocation(
                    type = item["type"]?.toString() ?: "PLATFORM_USER",
                    uid = item["uid"]?.toString(),
                    name = item["name"]?.toString() ?: "",
                    amount = (item["amount"] as? Number)?.toDouble()
                        ?: item["amount"]?.toString()?.toDoubleOrNull() ?: 0.0,
                    reimbursed = item["reimbursed"] as? Boolean ?: false
                )
            } else null
        }

        val rawUserImpact = (doc.get("userImpact") as? Map<*, *>)?.mapNotNull { (k, v) ->
            val uidKey = k?.toString() ?: return@mapNotNull null
            val valDouble = (v as? Number)?.toDouble()
                ?: v?.toString()?.toDoubleOrNull() ?: 0.0
            uidKey to valDouble
        }?.toMap() ?: emptyMap()

        val resolvedUserImpact = if (rawUserImpact.isNotEmpty()) {
            rawUserImpact
        } else if (rawAllocations.isNotEmpty()) {
            FinanceEngine.deriveImpact(rawAllocations, visibleUids)
        } else {
            emptyMap()
        }

        val typeStr = doc.getString("type") ?: "expense"
        val payStr = doc.getString("paymentMethod") ?: "pix"
        val statusStr = doc.getString("status") ?: "pending"
        val splitStr = doc.getString("splitType")
        val parsedSplitType = SplitType.from(splitStr)

        val extName = doc.getString("externalName")
            ?: doc.getString("thirdPartyName")
            ?: rawAllocations.find { it.type == "EXTERNAL" }?.name

        val tx = Transaction(
            id = doc.id,
            createdByUid = createdUid,
            paidByUid = doc.getString("paidByUid") ?: createdUid,
            cardId = doc.getString("cardId"),
            description = doc.getString("description") ?: "",
            category = doc.getString("category") ?: "Outros",
            subcategory = doc.getString("subcategory"),
            type = TransactionType.from(typeStr),
            paymentMethod = PaymentMethod.from(payStr),
            status = TransactionStatus.from(statusStr),
            estimatedAmount = doc.getDouble("estimatedAmount"),
            actualAmount = doc.getDouble("actualAmount"),
            totalAmount = doc.getDouble("totalAmount") ?: 0.0,
            dueDate = doc.getString("dueDate") ?: FinanceEngine.todayKey(),
            competenceMonth = doc.getString("competenceMonth") ?: FinanceEngine.currentMonthKey(),
            visibleToUids = visibleUids,
            userImpact = resolvedUserImpact,
            allocations = rawAllocations,
            recurringBillId = doc.getString("recurringBillId"),
            reserveCardId = doc.getString("reserveCardId"),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            assignedFriendUid = doc.getString("assignedFriendUid"),
            assignedFriendName = doc.getString("assignedFriendName"),
            acceptanceStatus = doc.getString("acceptanceStatus"),
            friendSharePct = doc.getDouble("friendSharePct"),
            respondedAt = doc.getLong("respondedAt"),
            installmentGroupId = doc.getString("installmentGroupId"),
            installmentNumber = doc.getLong("installmentNumber")?.toInt(),
            installmentsTotal = doc.getLong("installmentsTotal")?.toInt(),
            splitType = parsedSplitType,
            externalName = extName
        )
        val finalSplit = parsedSplitType ?: FinanceEngine.detectSplit(tx, createdUid)
        return tx.copy(splitType = finalSplit)
    }

    private fun transactionToMap(tx: Transaction): Map<String, Any?> {
        return mapOf(
            "id" to tx.id,
            "createdByUid" to tx.createdByUid,
            "paidByUid" to tx.paidByUid,
            "cardId" to tx.cardId,
            "description" to tx.description,
            "category" to tx.category,
            "subcategory" to tx.subcategory,
            "type" to tx.type.value,
            "paymentMethod" to tx.paymentMethod.value,
            "status" to tx.status.value,
            "estimatedAmount" to tx.estimatedAmount,
            "actualAmount" to tx.actualAmount,
            "totalAmount" to tx.totalAmount,
            "dueDate" to tx.dueDate,
            "competenceMonth" to tx.competenceMonth,
            "visibleToUids" to tx.visibleToUids,
            "userImpact" to tx.userImpact,
            "allocations" to tx.allocations.map {
                mapOf(
                    "type" to it.type,
                    "uid" to it.uid,
                    "name" to it.name,
                    "amount" to it.amount,
                    "reimbursed" to it.reimbursed
                )
            },
            "recurringBillId" to tx.recurringBillId,
            "reserveCardId" to tx.reserveCardId,
            "createdAt" to tx.createdAt,
            "assignedFriendUid" to tx.assignedFriendUid,
            "assignedFriendName" to tx.assignedFriendName,
            "acceptanceStatus" to tx.acceptanceStatus,
            "friendSharePct" to tx.friendSharePct,
            "respondedAt" to tx.respondedAt,
            "installmentGroupId" to tx.installmentGroupId,
            "installmentNumber" to tx.installmentNumber,
            "installmentsTotal" to tx.installmentsTotal,
            "splitType" to tx.splitType?.value,
            "externalName" to tx.externalName,
            "thirdPartyName" to tx.externalName
        )
    }

    private fun parseMonthClosure(doc: com.google.firebase.firestore.DocumentSnapshot): MonthClosure {
        return MonthClosure(
            id = doc.id,
            uid = doc.getString("uid") ?: "",
            closedMonth = doc.getString("closedMonth") ?: "",
            targetMonth = doc.getString("targetMonth") ?: "",
            totalSurplus = doc.getDouble("totalSurplus") ?: 0.0,
            carryoverToNextMonth = doc.getDouble("carryoverToNextMonth") ?: 0.0,
            investedAmount = doc.getDouble("investedAmount") ?: 0.0,
            cushionAmount = doc.getDouble("cushionAmount") ?: 0.0,
            investmentTransactionId = doc.getString("investmentTransactionId"),
            closedAt = doc.getLong("closedAt") ?: System.currentTimeMillis()
        )
    }

    private fun monthClosureToMap(closure: MonthClosure): Map<String, Any?> {
        return mapOf(
            "id" to closure.id,
            "uid" to closure.uid,
            "closedMonth" to closure.closedMonth,
            "targetMonth" to closure.targetMonth,
            "totalSurplus" to closure.totalSurplus,
            "carryoverToNextMonth" to closure.carryoverToNextMonth,
            "investedAmount" to closure.investedAmount,
            "cushionAmount" to closure.cushionAmount,
            "investmentTransactionId" to closure.investmentTransactionId,
            "closedAt" to closure.closedAt
        )
    }
}
