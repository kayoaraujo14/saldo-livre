package com.saldolivre.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection("cards")
            .whereArrayContains("sharedWithUids", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val cards = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseCard(doc)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()

                if (cards.isEmpty() && snapshot != null) {
                    createDefaultCardForUser(currentUid)
                }

                trySend(cards)
            }

        awaitClose { registration.remove() }
    }

    private fun createDefaultCardForUser(uid: String) {
        val user = _currentUser.value
        val cardId = "card_main_$uid"
        val cardDoc = mapOf(
            "id" to cardId,
            "ownerUid" to uid,
            "ownerName" to user.displayName,
            "cardName" to "Cartão Principal",
            "closingDay" to 1,
            "dueDay" to 10,
            "sharedWithUids" to listOf(uid),
            "color" to "violet"
        )
        firestore.collection("cards").document(cardId).set(cardDoc)
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

    override fun getMonthSummary(): Flow<MonthSummary> {
        return combine(
            getTransactions(),
            _currentUser,
            _selectedMonth,
            getCards()
        ) { txs, user, month, cards ->
            FinanceEngine.computeSummary(
                all = txs,
                uid = user.uid,
                monthKey = month,
                cards = cards
            )
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

    override suspend fun markTransactionPaid(id: String) {
        if (id.isNotBlank()) {
            firestore.collection("transactions").document(id)
                .update("status", TransactionStatus.PAID.value).await()
        }
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
        val docRef = firestore.collection("transactions").document(id)
        val rounded = FinanceEngine.round2(actualAmount)
        docRef.update(
            mapOf(
                "actualAmount" to rounded,
                "totalAmount" to rounded,
                "status" to TransactionStatus.PENDING.value
            )
        ).await()
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

        return Card(
            id = doc.id,
            ownerUid = ownerUid,
            ownerName = doc.getString("ownerName") ?: "",
            cardName = doc.getString("cardName") ?: "Cartão",
            closingDay = doc.getLong("closingDay")?.toInt() ?: 1,
            dueDay = doc.getLong("dueDay")?.toInt() ?: 10,
            sharedWithUsers = emptyList(),
            sharedWithUids = sharedUids,
            color = doc.getString("color") ?: "violet"
        )
    }

    private fun parseRecurringBill(doc: DocumentSnapshot): RecurringBill {
        return RecurringBill(
            id = doc.id,
            ownerUid = doc.getString("ownerUid") ?: "",
            description = doc.getString("description") ?: "",
            category = doc.getString("category") ?: "Moradia",
            subcategory = doc.getString("subcategory"),
            dueDay = doc.getLong("dueDay")?.toInt() ?: 10,
            defaultAmount = doc.getDouble("defaultAmount") ?: 0.0,
            active = doc.getBoolean("active") ?: true
        )
    }

    private fun parseTransaction(doc: DocumentSnapshot): Transaction {
        val rawVisible = (doc.get("visibleToUids") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val createdUid = doc.getString("createdByUid") ?: ""
        val visibleUids = if (rawVisible.isEmpty() && createdUid.isNotBlank()) listOf(createdUid) else rawVisible

        val rawAllocations = (doc.get("allocations") as? List<*>)?.mapNotNull { item ->
            if (item is Map<*, *>) {
                Allocation(
                    type = item["type"]?.toString() ?: "PLATFORM_USER",
                    uid = item["uid"]?.toString(),
                    name = item["name"]?.toString() ?: "",
                    amount = (item["amount"] as? Number)?.toDouble() ?: 0.0,
                    reimbursed = item["reimbursed"] as? Boolean ?: false
                )
            } else null
        } ?: emptyList()

        val rawUserImpact = (doc.get("userImpact") as? Map<*, *>)?.mapNotNull { (k, v) ->
            val uidKey = k?.toString() ?: return@mapNotNull null
            val valDouble = (v as? Number)?.toDouble() ?: 0.0
            uidKey to valDouble
        }?.toMap() ?: emptyMap()

        val typeStr = doc.getString("type") ?: "expense"
        val payStr = doc.getString("paymentMethod") ?: "pix"
        val statusStr = doc.getString("status") ?: "pending"
        val splitStr = doc.getString("splitType")

        return Transaction(
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
            userImpact = rawUserImpact,
            allocations = rawAllocations,
            recurringBillId = doc.getString("recurringBillId"),
            reserveCardId = doc.getString("reserveCardId"),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            assignedFriendUid = doc.getString("assignedFriendUid"),
            assignedFriendName = doc.getString("assignedFriendName"),
            acceptanceStatus = doc.getString("acceptanceStatus"),
            friendSharePct = doc.getDouble("friendSharePct"),
            installmentGroupId = doc.getString("installmentGroupId"),
            installmentNumber = doc.getLong("installmentNumber")?.toInt(),
            installmentsTotal = doc.getLong("installmentsTotal")?.toInt(),
            splitType = SplitType.from(splitStr)
        )
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
            "installmentGroupId" to tx.installmentGroupId,
            "installmentNumber" to tx.installmentNumber,
            "installmentsTotal" to tx.installmentsTotal,
            "splitType" to tx.splitType?.value
        )
    }
}
