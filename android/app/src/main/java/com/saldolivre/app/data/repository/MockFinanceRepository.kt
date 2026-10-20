package com.saldolivre.app.data.repository

import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class MockFinanceRepository : FinanceRepository {

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

    override val allUsers: List<UserProfile> = listOf(userKayo, userMarina)

    private val _currentUser = MutableStateFlow(userKayo)
    override val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _selectedMonth = MutableStateFlow(FinanceEngine.currentMonthKey())
    override val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _cards = MutableStateFlow(buildDemoCards())
    private val _recurringBills = MutableStateFlow(buildDemoRecurringBills())
    private val _transactions = MutableStateFlow(buildInitialTransactions())

    override fun switchUser(uid: String) {
        val target = allUsers.find { it.uid == uid } ?: return
        _currentUser.value = target
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
            _cards
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
        val newTx = if (tx.id.isBlank()) tx.copy(id = "tx_" + UUID.randomUUID().toString().take(8)) else tx
        _transactions.update { current -> listOf(newTx) + current }
    }

    override suspend fun updateTransaction(tx: Transaction) {
        _transactions.update { current ->
            current.map { if (it.id == tx.id) tx else it }
        }
    }

    override suspend fun deleteTransaction(id: String) {
        _transactions.update { current ->
            current.filter { it.id != id }
        }
    }

    override suspend fun markTransactionPaid(id: String) {
        _transactions.update { current ->
            current.map { tx ->
                if (tx.id == id) tx.copy(status = TransactionStatus.PAID) else tx
            }
        }
    }

    override suspend fun markAllocationReimbursed(transactionId: String, nameOrUid: String) {
        _transactions.update { current ->
            current.map { tx ->
                if (tx.id == transactionId) {
                    val updatedAllocs = tx.allocations.map { a ->
                        if (a.uid == nameOrUid || a.name.equals(nameOrUid, ignoreCase = true)) {
                            a.copy(reimbursed = true)
                        } else a
                    }
                    tx.copy(allocations = updatedAllocs)
                } else tx
            }
        }
    }

    override suspend fun updateEstimatedWithActual(id: String, actualAmount: Double) {
        _transactions.update { current ->
            current.map { tx ->
                if (tx.id == id) {
                    val rounded = FinanceEngine.round2(actualAmount)
                    val split = tx.splitType?.let { st ->
                        FinanceEngine.buildSplit(
                            splitType = st,
                            total = rounded,
                            myUid = userKayo.uid,
                            myName = "Kayo",
                            partnerUid = userMarina.uid,
                            partnerName = "Marina",
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
        }
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
        val prevMonth = FinanceEngine.shiftMonth(currentMonth, -1)

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

        return list
    }
}
