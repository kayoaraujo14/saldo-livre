package com.saldolivre.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.engine.InvoiceEngine
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

    private var userProfileListener: ListenerRegistration? = null

    init {
        fun attachUser(user: com.google.firebase.auth.FirebaseUser?) {
            userProfileListener?.remove()
            userProfileListener = null

            if (user != null && user.uid.isNotBlank()) {
                _currentUser.value = UserProfile(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = if (!user.displayName.isNullOrBlank()) user.displayName!! else "Meu Perfil",
                    photoURL = user.photoUrl?.toString(),
                    monthlyIncome = 5000.0
                )

                // Escuta perfil em tempo real no Firestore /users/{uid}
                userProfileListener = firestore.collection("users").document(user.uid)
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
                            val publicDoc = mapOf(
                                "uid" to user.uid,
                                "email" to (user.email ?: "").lowercase().trim(),
                                "displayName" to (user.displayName ?: "Meu Perfil"),
                                "photoURL" to user.photoUrl?.toString(),
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            val batch = firestore.batch()
                            batch.set(firestore.collection("users").document(user.uid), defaultDoc, com.google.firebase.firestore.SetOptions.merge())
                            batch.set(firestore.collection("public_profiles").document(user.uid), publicDoc, com.google.firebase.firestore.SetOptions.merge())
                            batch.commit()
                        }
                    }
            }
        }

        attachUser(auth.currentUser)
        auth.addAuthStateListener { firebaseAuth ->
            attachUser(firebaseAuth.currentUser)
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

        val myTxsMap = mutableMapOf<String, Transaction>()
        val cardTxsMap = mutableMapOf<String, Map<String, Transaction>>()
        val cardListeners = mutableMapOf<String, ListenerRegistration>()
        val ownedCardIds = mutableSetOf<String>()
        val sharedCardIds = mutableSetOf<String>()

        fun emitMerged() {
            val allMerged = LinkedHashMap<String, Transaction>()
            allMerged.putAll(myTxsMap)
            for (cardTxs in cardTxsMap.values) {
                for ((id, tx) in cardTxs) {
                    if (!allMerged.containsKey(id)) {
                        allMerged[id] = tx
                    }
                }
            }
            trySend(allMerged.values.toList())
        }

        // Emite imediatamente lista vazia defensiva para que o combine não fique bloqueado
        emitMerged()

        fun updateCardListeners() {
            val currentCardIds = (ownedCardIds + sharedCardIds).toSet()

            // Remove listeners de cartões que deixaram de pertencer ou ser compartilhados
            val removed = cardListeners.keys - currentCardIds
            for (id in removed) {
                cardListeners.remove(id)?.remove()
                cardTxsMap.remove(id)
            }

            // Adiciona listeners para novos cartões
            val added = currentCardIds - cardListeners.keys
            for (cardId in added) {
                val reg = firestore.collection("transactions")
                    .whereEqualTo("cardId", cardId)
                    .addSnapshotListener { txSnap, txErr ->
                        if (txErr != null) {
                            emitMerged()
                            return@addSnapshotListener
                        }
                        val txMap = mutableMapOf<String, Transaction>()
                        txSnap?.documents?.forEach { doc ->
                            try {
                                val tx = parseTransaction(doc)
                                txMap[tx.id] = tx
                            } catch (e: Exception) {
                                android.util.Log.w("FirestoreFinanceRepo", "Falha ao analisar transação de cartão ${doc.id}", e)
                            }
                        }
                        cardTxsMap[cardId] = txMap
                        emitMerged()
                    }
                cardListeners[cardId] = reg
            }

            if (removed.isNotEmpty()) {
                emitMerged()
            }
        }

        // 1. Transações com visibleToUids do usuário
        val myRegistration: ListenerRegistration = firestore.collection("transactions")
            .whereArrayContains("visibleToUids", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    emitMerged()
                    return@addSnapshotListener
                }
                myTxsMap.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        val tx = parseTransaction(doc)
                        myTxsMap[tx.id] = tx
                    } catch (e: Exception) {
                        android.util.Log.w("FirestoreFinanceRepo", "Falha ao analisar transação ${doc.id}", e)
                    }
                }
                emitMerged()
            }

        // 2. Escuta cartões próprios onde o usuário é titular
        val ownedCardsRegistration = firestore.collection("cards")
            .whereEqualTo("ownerUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                ownedCardIds.clear()
                snapshot?.documents?.mapNotNull { it.id }?.let { ownedCardIds.addAll(it) }
                updateCardListeners()
            }

        // 3. Escuta cartões compartilhados onde o usuário é convidado
        val sharedCardsRegistration = firestore.collection("cards")
            .whereArrayContains("sharedWithUids", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                sharedCardIds.clear()
                snapshot?.documents?.mapNotNull { it.id }?.let { sharedCardIds.addAll(it) }
                updateCardListeners()
            }

        awaitClose {
            myRegistration.remove()
            ownedCardsRegistration.remove()
            sharedCardsRegistration.remove()
            for (reg in cardListeners.values) {
                reg.remove()
            }
            cardListeners.clear()
        }
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

        // Emite imediatamente defensivo para desbloquear flows downstream
        emitCombined()

        val regOwned = firestore.collection("cards")
            .whereEqualTo("ownerUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    emitCombined()
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
                    emitCombined()
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
                val subName = closure.investmentSubcategory?.ifBlank { "Reserva de Emergência" } ?: "Reserva de Emergência"
                val txId = closure.investmentTransactionId?.ifBlank { null } ?: ("tx_invest_" + java.util.UUID.randomUUID().toString().take(8))
                val investTx = Transaction(
                    id = txId,
                    createdByUid = currentUid,
                    paidByUid = currentUid,
                    visibleToUids = listOf(currentUid),
                    description = "Aporte Sobra ${FinanceEngine.formatMonthLabel(closure.closedMonth)}",
                    category = "Investimentos",
                    subcategory = subName,
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
            val userClosuresCol = firestore.collection("users")
                .document(currentUid)
                .collection("month_closures")

            // 1. Tenta deletar por closedMonth direto
            try {
                userClosuresCol.document(closedMonth).delete().await()
            } catch (_: Exception) {}

            // 2. Tenta deletar por cls_${closedMonth}_$currentUid
            val idWithPrefix = "cls_${closedMonth}_$currentUid"
            try {
                userClosuresCol.document(idWithPrefix).delete().await()
            } catch (_: Exception) {}

            // 3. Query por closedMonth para garantir exclusão completa
            val querySnap = userClosuresCol
                .whereEqualTo("closedMonth", closedMonth)
                .get()
                .await()

            for (doc in querySnap.documents) {
                try {
                    doc.reference.delete().await()
                } catch (_: Exception) {}
            }
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
            val userUid = auth.currentUser?.uid ?: _currentUser.value.uid
            val safeTx = if (tx.createdByUid.isBlank()) tx.copy(createdByUid = userUid) else tx
            firestore.collection("transactions").document(tx.id).set(transactionToMap(safeTx)).await()
        }
    }

    override suspend fun deleteTransaction(id: String) {
        if (id.isBlank() || id.startsWith("invoice_")) return
        try {
            firestore.collection("transactions").document(id).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreFinanceRepo", "Erro ao excluir transação $id", e)
            throw e
        }
    }

    override suspend fun deleteFutureInstallments(groupId: String, fromInstallmentNumber: Int): Int {
        if (groupId.isBlank()) return 0
        try {
            val all = getTransactions().first()
            val targetIds = all.filter {
                it.installmentGroupId == groupId &&
                !it.id.startsWith("invoice_") &&
                (it.installmentNumber ?: 1) >= fromInstallmentNumber &&
                it.status != TransactionStatus.PAID
            }.map { it.id }
            if (targetIds.isEmpty()) return 0

            for (chunk in targetIds.chunked(450)) {
                val batch = firestore.batch()
                for (id in chunk) {
                    batch.delete(firestore.collection("transactions").document(id))
                }
                batch.commit().await()
            }
            return targetIds.size
        } catch (e: Exception) {
            android.util.Log.e("FirestoreFinanceRepo", "Erro ao excluir parcelas futuras do grupo $groupId", e)
            throw e
        }
    }

    override suspend fun refundFullInstallmentGroup(groupId: String, targetMonth: String?): Pair<Int, Double> {
        if (groupId.isBlank()) return Pair(0, 0.0)
        val all = getTransactions().first()
        val group = all.filter { it.installmentGroupId == groupId }
        if (group.isEmpty()) return Pair(0, 0.0)

        val result = FinanceEngine.calculateFullInstallmentRefund(
            groupTxs = group,
            currentUser = _currentUser.value,
            todayIso = FinanceEngine.todayKey(),
            targetCompetenceMonth = targetMonth
        )

        if (result.toDeleteIds.isNotEmpty()) {
            for (chunk in result.toDeleteIds.chunked(450)) {
                val batch = firestore.batch()
                for (id in chunk) {
                    batch.delete(firestore.collection("transactions").document(id))
                }
                batch.commit().await()
            }
        }

        if (result.refundTransaction != null) {
            addTransaction(result.refundTransaction)
        }

        return Pair(result.toDeleteIds.size, result.totalPaidAmount)
    }

    override suspend fun anticipateInstallments(groupId: String, count: Int, targetMonth: String, discount: Double): Pair<Int, Double> {
        if (groupId.isBlank() || count <= 0) return Pair(0, 0.0)
        val all = getTransactions().first()
        val result = InvoiceEngine.anticipateInstallments(
            transactions = all,
            groupId = groupId,
            count = count,
            targetMonth = targetMonth,
            discount = discount,
            todayIso = FinanceEngine.todayKey(),
            currentUserUid = _currentUser.value.uid
        )

        if (result.anticipatedCount == 0) return Pair(0, 0.0)

        for (chunk in result.updatedTransactions.chunked(450)) {
            val batch = firestore.batch()
            for (tx in chunk) {
                val docRef = firestore.collection("transactions").document(tx.id)
                batch.update(docRef, "competenceMonth", tx.competenceMonth, "anticipatedFromMonth", tx.anticipatedFromMonth ?: "", "isAnticipated", true)
            }
            batch.commit().await()
        }

        if (result.discountTransaction != null) {
            addTransaction(result.discountTransaction)
        }

        return Pair(result.anticipatedCount, discount)
    }

    override suspend fun deleteInstallmentGroup(groupId: String): Int {
        if (groupId.isBlank()) return 0
        try {
            val all = getTransactions().first()
            val targetIds = all.filter { it.installmentGroupId == groupId && !it.id.startsWith("invoice_") }.map { it.id }
            if (targetIds.isEmpty()) return 0

            for (chunk in targetIds.chunked(450)) {
                val batch = firestore.batch()
                for (id in chunk) {
                    batch.delete(firestore.collection("transactions").document(id))
                }
                batch.commit().await()
            }
            return targetIds.size
        } catch (e: Exception) {
            android.util.Log.e("FirestoreFinanceRepo", "Erro ao excluir grupo de parcelamento $groupId", e)
            throw e
        }
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

        val firstTx = group.first()
        val firstNum = firstTx.installmentNumber ?: 1
        val effectivePaymentMethod = input.paymentMethod ?: firstTx.paymentMethod
        val isCardChange = input.cardId != null && input.cardId != firstTx.cardId
        val targetCardId = if (effectivePaymentMethod == PaymentMethod.CREDIT_CARD) {
            input.cardId ?: firstTx.cardId
        } else null

        val card = targetCardId?.let { cid -> cards.find { it.id == cid } }
        val payerUid = if (effectivePaymentMethod == PaymentMethod.CREDIT_CARD && card != null) card.ownerUid else user.uid

        val firstPurchaseDate = input.date ?: firstTx.dueDate.takeIf { it.isNotBlank() } ?: FinanceEngine.todayKey()
        val shouldRecalcCycle = effectivePaymentMethod == PaymentMethod.CREDIT_CARD && card != null && (isCardChange || input.date != null)
        val initialCardDates = if (shouldRecalcCycle && card != null) {
            FinanceEngine.calculateCardInvoiceDates(firstPurchaseDate, card)
        } else null
        val initialMonth = initialCardDates?.second

        val newDay = input.date?.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull()
        val baseMonth = firstTx.competenceMonth.takeIf { it.isNotBlank() }
            ?: firstTx.dueDate.take(7).takeIf { it.isNotBlank() }
            ?: FinanceEngine.currentMonthKey()

        val updatedTxs = group.mapIndexed { idx, tx ->
            val num = tx.installmentNumber ?: (idx + 1)
            val total = tx.installmentsTotal ?: group.size
            val curAmount = if (input.amount != null && input.amount > 0.0) FinanceEngine.round2(input.amount) else tx.totalAmount

            // 3. Extinção do sufixo (X/Y)
            val rawDesc = if (!input.description.isNullOrBlank()) input.description else tx.description
            val newDesc = FinanceEngine.formatInstallmentDesc(rawDesc, num, total)

            // 4. Recálculo de Ciclo ao Mudar Cartão / Data
            var targetMonth = tx.competenceMonth.takeIf { it.isNotBlank() } ?: tx.dueDate.take(7).takeIf { it.isNotBlank() } ?: FinanceEngine.currentMonthKey()
            var newDueDate = tx.dueDate

            if (shouldRecalcCycle && initialMonth != null) {
                targetMonth = FinanceEngine.shiftMonth(initialMonth, num - firstNum)
                newDueDate = FinanceEngine.shiftDateMonth(firstPurchaseDate, num - firstNum)
            } else if (effectivePaymentMethod != PaymentMethod.CREDIT_CARD && input.date != null) {
                newDueDate = FinanceEngine.shiftDateMonth(firstPurchaseDate, num - firstNum)
                targetMonth = newDueDate.take(7)
            } else {
                targetMonth = FinanceEngine.shiftMonth(baseMonth, num - firstNum)
                newDueDate = if (newDay != null) {
                    FinanceEngine.dateInMonth(targetMonth, newDay)
                } else {
                    val origDay = tx.dueDate.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull() ?: 10
                    FinanceEngine.dateInMonth(targetMonth, origDay)
                }
            }

            val targetFriendUid = input.friend?.uid ?: tx.assignedFriendUid
            val targetFriendName = input.friend?.name ?: tx.assignedFriendName
            val targetFriendPct = input.friend?.pct ?: tx.friendSharePct
            val hasFriend = !targetFriendUid.isNullOrBlank()

            // 2. Preservação de Reembolsos de Amigos
            val split = FinanceEngine.buildSplit(
                splitType = input.splitType ?: tx.splitType ?: SplitType.MINE,
                total = curAmount,
                myUid = user.uid,
                myName = user.displayName,
                partnerUid = partner?.uid,
                partnerName = partner?.displayName,
                payerUid = payerUid,
                externalName = input.externalName,
                assignedFriendUid = targetFriendUid,
                assignedFriendName = targetFriendName,
                friendSharePct = targetFriendPct,
                customAllocations = input.customAllocations,
                previous = tx.allocations
            )

            val customUids = input.customAllocations?.mapNotNull { it.uid } ?: emptyList()
            val cardUids = if (card != null) {
                (listOfNotNull(card.ownerUid.takeIf { it.isNotBlank() }) + card.sharedWithUids).distinct()
            } else emptyList()
            val effectiveVisible = (split.visibleToUids + listOfNotNull(targetFriendUid) + cardUids + customUids).distinct()

            // 1. Isolamento de Status: cada parcela preserva seu próprio status
            val effectiveStatus = tx.status
            val (nextAcceptanceStatus, nextRespondedAt) = FinanceEngine.resolveNextAcceptanceStatus(
                prevTx = tx,
                nextAssignedFriendUid = targetFriendUid,
                nextFriendSharePct = targetFriendPct,
                nextTotalAmount = curAmount,
                nextAllocations = split.allocations
            )

            tx.copy(
                description = newDesc,
                category = input.category ?: tx.category,
                subcategory = if (input.subcategory != null) input.subcategory else tx.subcategory,
                paymentMethod = effectivePaymentMethod,
                cardId = targetCardId,
                status = effectiveStatus,
                dueDate = newDueDate,
                competenceMonth = targetMonth,
                estimatedAmount = if (effectiveStatus == TransactionStatus.ESTIMATED) curAmount else null,
                actualAmount = if (effectiveStatus != TransactionStatus.ESTIMATED) curAmount else null,
                totalAmount = curAmount,
                allocations = split.allocations,
                userImpact = split.userImpact,
                visibleToUids = effectiveVisible,
                assignedFriendUid = targetFriendUid,
                assignedFriendName = targetFriendName,
                friendSharePct = targetFriendPct,
                acceptanceStatus = nextAcceptanceStatus,
                respondedAt = nextRespondedAt,
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
        return FinanceEngine.autoLaunchDueBills(
            bills = bills,
            all = allTxs,
            monthKey = monthKey,
            todayStr = todayStr,
            me = user,
            partner = null
        )
    }

    override suspend fun markRecurringResolved(monthKey: String, billId: String) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        val currentMap = _currentUser.value.resolvedRecurring.toMutableMap()
        val currentList = (currentMap[monthKey] ?: emptyList()).toMutableList()
        if (!currentList.contains(billId)) {
            currentList.add(billId)
            currentMap[monthKey] = currentList
            _currentUser.value = _currentUser.value.copy(resolvedRecurring = currentMap)
            if (uid.isNotBlank() && uid != "default_uid") {
                firestore.collection("users").document(uid).update("resolvedRecurring", currentMap).await()
            }
        }
    }

    override suspend fun getResolvedRecurring(): Map<String, List<String>> {
        return _currentUser.value.resolvedRecurring
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

    override suspend fun saveUserProfile(profile: UserProfile) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        _currentUser.value = profile
        if (uid.isNotBlank() && uid != "default_uid") {
            val userDoc = mapOf(
                "uid" to profile.uid,
                "email" to profile.email,
                "displayName" to profile.displayName,
                "photoURL" to profile.photoURL,
                "monthlyIncome" to profile.monthlyIncome,
                "emergencyReserveBalance" to profile.emergencyReserveBalance,
                "investmentMonthlyTarget" to profile.investmentMonthlyTarget,
                "investmentAssetBalances" to profile.investmentAssetBalances,
                "categoryBudgets" to profile.categoryBudgets,
                "monthlyCategoryBudgets" to profile.monthlyCategoryBudgets,
                "resolvedRecurring" to profile.resolvedRecurring
            )
            val publicDoc = mapOf(
                "uid" to profile.uid,
                "email" to profile.email.lowercase().trim(),
                "displayName" to profile.displayName,
                "photoURL" to profile.photoURL,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            val batch = firestore.batch()
            batch.set(firestore.collection("users").document(uid), userDoc, com.google.firebase.firestore.SetOptions.merge())
            batch.set(firestore.collection("public_profiles").document(uid), publicDoc, com.google.firebase.firestore.SetOptions.merge())
            batch.commit().await()
        }
    }

    override suspend fun updateInvestmentAssetBalances(balances: Map<String, Double>) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        _currentUser.value = _currentUser.value.copy(investmentAssetBalances = balances)
        if (uid.isNotBlank() && uid != "default_uid") {
            firestore.collection("users").document(uid).update("investmentAssetBalances", balances).await()
        }
    }

    override suspend fun updateInvestmentMonthlyTarget(target: Double) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        _currentUser.value = _currentUser.value.copy(investmentMonthlyTarget = target)
        if (uid.isNotBlank() && uid != "default_uid") {
            firestore.collection("users").document(uid).update("investmentMonthlyTarget", target).await()
        }
    }

    override suspend fun updateCategoryBudgets(budgets: Map<String, Double>, targetMonth: String?) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
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
        if (uid.isNotBlank() && uid != "default_uid") {
            val updates = mutableMapOf<String, Any>(
                "categoryBudgets" to budgets
            )
            if (!targetMonth.isNullOrBlank()) {
                updates["monthlyCategoryBudgets.$targetMonth"] = budgets
            }
            firestore.collection("users").document(uid).update(updates).await()
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
        syncCardTransactionsVisibility(toSave)
    }

    override suspend fun syncCardTransactionsVisibility(card: Card) {
        val uid = auth.currentUser?.uid ?: _currentUser.value.uid
        if (card.ownerUid.isNotBlank() && card.ownerUid != uid) return
        if (card.sharedWithUids.isEmpty()) return

        try {
            val snap = firestore.collection("transactions")
                .whereEqualTo("cardId", card.id)
                .get()
                .await()

            val batch = firestore.batch()
            var count = 0
            for (doc in snap.documents) {
                // As regras só permitem alterar lançamentos criados pelo próprio usuário
                val creator = doc.getString("createdByUid")?.takeIf { it.isNotBlank() } ?: doc.getString("paidByUid")
                if (creator != uid) continue
                val cur = (doc.get("visibleToUids") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val merged = (cur + uid + card.sharedWithUids).distinct()
                if (merged.size != cur.size) {
                    batch.update(doc.reference, "visibleToUids", merged)
                    count++
                    if (count >= 400) {
                        batch.commit().await()
                        count = 0
                    }
                }
            }
            if (count > 0) {
                batch.commit().await()
            }
        } catch (_: Exception) {}
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

        val map1 = mutableMapOf<String, Friendship>()
        val map2 = mutableMapOf<String, Friendship>()

        fun emitCombined() {
            val combined = (map1 + map2).values.toList()
            trySend(combined)
        }

        val l1 = firestore.collection("friendships")
            .whereEqualTo("requesterUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    map1.clear()
                    emitCombined()
                    return@addSnapshotListener
                }
                map1.clear()
                for (doc in snap.documents) {
                    parseFriendship(doc)?.let { map1[it.id] = it }
                }
                emitCombined()
            }

        val l2 = firestore.collection("friendships")
            .whereEqualTo("recipientUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    map2.clear()
                    emitCombined()
                    return@addSnapshotListener
                }
                map2.clear()
                for (doc in snap.documents) {
                    parseFriendship(doc)?.let { map2[it.id] = it }
                }
                emitCombined()
            }

        awaitClose {
            l1.remove()
            l2.remove()
        }
    }

    override suspend fun sendFriendRequest(email: String): Pair<Boolean, String> {
        val trimmed = email.trim().lowercase()
        val me = _currentUser.value
        val myUid = auth.currentUser?.uid ?: me.uid
        if (trimmed == me.email.lowercase()) {
            return Pair(false, "Você não pode adicionar o seu próprio e-mail.")
        }

        val userSnap = firestore.collection("public_profiles")
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
        val currentUid = auth.currentUser?.uid ?: tx.assignedFriendUid

        val allTxs = getTransactions().first()
        val targetTxs = if (!gId.isNullOrBlank()) {
            val siblings = allTxs.filter { t ->
                t.installmentGroupId == gId &&
                    t.acceptanceStatus == AcceptanceStatus.PENDING_APPROVAL.value &&
                    (
                        (!currentUid.isNullOrBlank() && t.assignedFriendUid == currentUid) ||
                            (!tx.assignedFriendUid.isNullOrBlank() && t.assignedFriendUid == tx.assignedFriendUid) ||
                            (!currentUid.isNullOrBlank() && t.allocations.any { a -> a.uid == currentUid && a.type == "PLATFORM_USER" })
                        )
            }
            if (siblings.isNotEmpty()) siblings else listOf(tx)
        } else {
            listOf(tx)
        }

        val targetIds = targetTxs.map { it.id }.distinct()
        val payload = mutableMapOf<String, Any?>(
            "acceptanceStatus" to status,
            "respondedAt" to now
        )
        if (!currentUid.isNullOrBlank()) {
            payload["assignedFriendUid"] = currentUid
        }

        try {
            val batch = firestore.batch()
            for (id in targetIds) {
                val ref = firestore.collection("transactions").document(id)
                batch.update(ref, payload)
            }
            batch.commit().await()
        } catch (batchErr: Exception) {
            var updatedCount = 0
            var lastErr: Exception = batchErr
            for (id in targetIds) {
                try {
                    firestore.collection("transactions").document(id).update(payload).await()
                    updatedCount++
                } catch (err: Exception) {
                    lastErr = err
                }
            }
            if (updatedCount == 0) {
                throw lastErr
            }
        }
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
            createdAt = doc.getLongSafe("createdAt") ?: System.currentTimeMillis()
        )
    }

    // Parsers seguros
    private fun parseUserProfile(doc: DocumentSnapshot): UserProfile? {
        if (!doc.exists()) return null
        val rawResolved = doc.get("resolvedRecurring") as? Map<*, *>
        val resolvedMap = mutableMapOf<String, List<String>>()
        rawResolved?.forEach { (k, v) ->
            if (k != null) {
                val list = (v as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                resolvedMap[k.toString()] = list
            }
        }
        val rawBalances = doc.get("investmentAssetBalances") as? Map<*, *>
        val assetBalances = mutableMapOf<String, Double>()
        rawBalances?.forEach { (k, v) ->
            if (k != null) {
                val num = (v as? Number)?.toDouble() ?: 0.0
                assetBalances[k.toString()] = num
            }
        }
        val rawBudgets = doc.get("categoryBudgets") as? Map<*, *>
        val budgetsMap = mutableMapOf<String, Double>()
        rawBudgets?.forEach { (k, v) ->
            if (k != null) {
                val num = (v as? Number)?.toDouble() ?: 0.0
                budgetsMap[k.toString()] = num
            }
        }
        val rawMonthlyBudgets = doc.get("monthlyCategoryBudgets") as? Map<*, *>
        val monthlyBudgetsMap = mutableMapOf<String, Map<String, Double>>()
        rawMonthlyBudgets?.forEach { (mKey, mVal) ->
            if (mKey != null && mVal is Map<*, *>) {
                val catMap = mutableMapOf<String, Double>()
                mVal.forEach { (cKey, cVal) ->
                    if (cKey != null) {
                        val num = (cVal as? Number)?.toDouble() ?: 0.0
                        catMap[cKey.toString()] = num
                    }
                }
                monthlyBudgetsMap[mKey.toString()] = catMap
            }
        }
        return UserProfile(
            uid = doc.id,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: "Meu Perfil",
            photoURL = doc.getString("photoURL"),
            monthlyIncome = doc.getDoubleSafe("monthlyIncome") ?: 5000.0,
            emergencyReserveBalance = doc.getDoubleSafe("emergencyReserveBalance") ?: 0.0,
            investmentMonthlyTarget = doc.getDoubleSafe("investmentMonthlyTarget") ?: 1000.0,
            investmentAssetBalances = assetBalances,
            categoryBudgets = budgetsMap,
            monthlyCategoryBudgets = monthlyBudgetsMap,
            resolvedRecurring = resolvedMap
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
            closingDay = doc.getIntSafe("closingDay") ?: 1,
            dueDay = doc.getIntSafe("dueDay") ?: 10,
            sharedWithUsers = rawUsers,
            sharedWithUids = sharedUids,
            frequentThirdParties = rawParties,
            color = doc.getString("color") ?: "violet",
            limit = doc.getDoubleSafe("limit")
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
        val triggerStr = doc.getString("launchTrigger")

        return RecurringBill(
            id = doc.id,
            ownerUid = doc.getString("ownerUid") ?: "",
            description = doc.getString("description") ?: "",
            category = doc.getString("category") ?: "Moradia",
            subcategory = doc.getString("subcategory"),
            dueDay = doc.getIntSafe("dueDay") ?: 10,
            defaultAmount = doc.getDoubleSafe("defaultAmount") ?: 0.0,
            estimationRule = EstimationRule.from(ruleStr),
            active = doc.getBooleanSafe("active") ?: true,
            splitType = SplitType.from(splitStr),
            paymentMethod = PaymentMethod.from(payStr),
            cardId = doc.getString("cardId"),
            type = TransactionType.from(typeStr),
            startMonth = doc.getString("startMonth"),
            endMonth = doc.getString("endMonth"),
            installmentsCount = doc.getIntSafe("installmentsCount"),
            launchTrigger = LaunchTrigger.from(triggerStr)
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
            "cardId" to bill.cardId,
            "type" to bill.type.value,
            "startMonth" to bill.startMonth,
            "endMonth" to bill.endMonth,
            "installmentsCount" to bill.installmentsCount,
            "launchTrigger" to bill.launchTrigger.value
        )
    }

    internal fun parseTransaction(doc: DocumentSnapshot): Transaction {
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
            estimatedAmount = doc.getDoubleSafe("estimatedAmount"),
            actualAmount = doc.getDoubleSafe("actualAmount"),
            totalAmount = doc.getDoubleSafe("totalAmount") ?: 0.0,
            dueDate = doc.getString("dueDate") ?: FinanceEngine.todayKey(),
            competenceMonth = doc.getString("competenceMonth") ?: FinanceEngine.currentMonthKey(),
            visibleToUids = visibleUids,
            userImpact = resolvedUserImpact,
            allocations = rawAllocations,
            recurringBillId = doc.getString("recurringBillId"),
            reserveCardId = doc.getString("reserveCardId"),
            notes = doc.getString("notes"),
            createdAt = doc.getLongSafe("createdAt") ?: System.currentTimeMillis(),
            assignedFriendUid = doc.getString("assignedFriendUid"),
            assignedFriendName = doc.getString("assignedFriendName"),
            acceptanceStatus = doc.getString("acceptanceStatus"),
            friendSharePct = doc.getDoubleSafe("friendSharePct"),
            respondedAt = doc.getLongSafe("respondedAt"),
            installmentGroupId = doc.getString("installmentGroupId"),
            installmentNumber = doc.getIntSafe("installmentNumber"),
            installmentsTotal = doc.getIntSafe("installmentsTotal"),
            splitType = parsedSplitType,
            externalName = extName,
            refundedTransactionId = doc.getString("refundedTransactionId"),
            isAnticipated = doc.getBooleanSafe("isAnticipated"),
            anticipatedFromMonth = doc.getString("anticipatedFromMonth"),
            discountAmount = doc.getDoubleSafe("discountAmount")
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
            "thirdPartyName" to tx.externalName,
            "refundedTransactionId" to tx.refundedTransactionId,
            "isAnticipated" to tx.isAnticipated,
            "anticipatedFromMonth" to tx.anticipatedFromMonth,
            "discountAmount" to tx.discountAmount,
            "notes" to tx.notes
        )
    }

    private fun parseMonthClosure(doc: com.google.firebase.firestore.DocumentSnapshot): MonthClosure {
        val currentUid = auth.currentUser?.uid ?: _currentUser.value.uid
        val parsedUid = doc.getString("uid")?.ifBlank { null } ?: currentUid
        return MonthClosure(
            id = doc.id,
            uid = parsedUid,
            closedMonth = doc.getString("closedMonth") ?: "",
            targetMonth = doc.getString("targetMonth") ?: "",
            totalSurplus = doc.getDoubleSafe("totalSurplus") ?: 0.0,
            carryoverToNextMonth = doc.getDoubleSafe("carryoverToNextMonth") ?: 0.0,
            investedAmount = doc.getDoubleSafe("investedAmount") ?: 0.0,
            cushionAmount = doc.getDoubleSafe("cushionAmount") ?: 0.0,
            investmentTransactionId = doc.getString("investmentTransactionId"),
            closedAt = doc.getLongSafe("closedAt") ?: System.currentTimeMillis()
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
