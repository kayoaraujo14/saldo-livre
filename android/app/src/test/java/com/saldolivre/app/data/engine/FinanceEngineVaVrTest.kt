package com.saldolivre.app.data.engine

import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import com.saldolivre.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class FinanceEngineVaVrTest {

    companion object {
        private const val MY_UID = "user_me"
        private const val PARTNER_UID = "user_partner"

        private val VALE_CARD = Card(
            id = "card_vale_1",
            ownerUid = MY_UID,
            ownerName = "Titular",
            cardName = "Caju Multi",
            cardType = "va_vr",
            color = "teal"
        )

        private val CREDIT_CARD = Card(
            id = "card_cred_1",
            ownerUid = MY_UID,
            ownerName = "Titular",
            cardName = "Nubank Violet",
            cardType = "credit",
            color = "violet"
        )
    }

    private fun makeTx(
        id: String = "tx_1",
        description: String = "Lançamento",
        category: String = "Outros",
        subcategory: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        paymentMethod: PaymentMethod = PaymentMethod.PIX,
        cardId: String? = null,
        status: TransactionStatus = TransactionStatus.PAID,
        amount: Double = 100.0,
        competenceMonth: String = "2099-01",
        dueDate: String = "2099-01-15",
        paidByUid: String = MY_UID,
        visibleToUids: List<String> = listOf(MY_UID),
        splitType: SplitType? = null,
        userImpact: Map<String, Double> = mapOf(MY_UID to amount),
        allocations: List<Allocation> = emptyList()
    ): Transaction {
        return Transaction(
            id = id,
            createdByUid = paidByUid,
            paidByUid = paidByUid,
            cardId = cardId,
            description = description,
            category = category,
            subcategory = subcategory,
            type = type,
            paymentMethod = paymentMethod,
            status = status,
            estimatedAmount = amount,
            actualAmount = amount,
            totalAmount = amount,
            dueDate = dueDate,
            competenceMonth = competenceMonth,
            visibleToUids = visibleToUids,
            splitType = splitType,
            userImpact = userImpact,
            allocations = allocations
        )
    }

    @Test
    fun `1 isola 100 pct entradas saidas com categorias reais e recorrentes do Vale do Saldo Livre e projecoes`() {
        val cards = listOf(VALE_CARD, CREDIT_CARD)
        val txs = listOf(
            // Conta bancária principal: +5.000 salário, -1.500 aluguel -> Saldo Livre = 3.500
            makeTx(
                id = "sal",
                description = "Salário",
                category = "Salário & Remuneração",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.PIX,
                amount = 5000.0
            ),
            makeTx(
                id = "aluguel",
                description = "Aluguel",
                category = "Moradia",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                amount = 1500.0
            ),
            // Recarga no Vale via PaymentMethod.VA_VR usando categoria real 'Benefícios'
            makeTx(
                id = "va_in",
                description = "Recarga Caju Benefícios",
                category = "Benefícios",
                subcategory = "Vale Alimentação (VA)",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                amount = 1200.0
            ),
            // Gasto no Vale com categoria real 'Alimentação'
            makeTx(
                id = "va_out_food",
                description = "Supermercado Extra",
                category = "Alimentação",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                amount = 350.0
            ),
            // Gasto no Vale com categoria real 'Transporte' (multi-benefícios no cartão pré-pago)
            makeTx(
                id = "va_out_transp",
                description = "Uber Mobilidade Flash",
                category = "Transporte",
                subcategory = "Apps (Uber / 99)",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                amount = 50.0
            ),
            // Transação legada sob categoria 'VA/VR' continua compatível
            makeTx(
                id = "va_legacy",
                description = "Padaria Central Legado",
                category = "VA/VR",
                subcategory = "Padaria & Café",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.DEBIT,
                amount = 40.0
            )
        )

        val vaVrBill = RecurringBill(
            id = "bill_vavr",
            ownerUid = MY_UID,
            description = "Recarga Mensal Flash VA/VR",
            category = "Benefícios",
            subcategory = "Vale Alimentação (VA)",
            dueDay = 1,
            defaultAmount = 1100.0,
            estimationRule = EstimationRule.FIXED,
            active = true,
            type = TransactionType.INCOME,
            paymentMethod = PaymentMethod.VA_VR,
            cardId = VALE_CARD.id,
            startMonth = "2099-02"
        )

        val summary = FinanceEngine.computeSummary(
            all = txs,
            uid = MY_UID,
            monthKey = "2099-01",
            cards = cards,
            closures = emptyList(),
            recurringBills = listOf(vaVrBill)
        )

        // Fluxo bancário blindado
        assertEquals(5000.0, summary.income, 0.01)
        assertEquals(1500.0, summary.paid, 0.01)
        assertEquals(0.0, summary.committed, 0.01)
        assertEquals(3500.0, summary.freeBalance, 0.01)

        // Módulo Vale isolado (1200 - 350 - 50 - 40 = 760)
        assertEquals(1200.0, summary.vaVrIncome, 0.01)
        assertEquals(440.0, summary.vaVrSpent, 0.01)
        assertEquals(760.0, summary.vaVrAvailableBalance, 0.01)
        assertEquals(760.0, summary.vaVrProjectedBalance, 0.01)

        // Projeção futura da conta corrente ignora recorrente do Vale
        val projections = FinanceEngine.projectFutureMonths(
            all = txs,
            bills = listOf(vaVrBill),
            profile = UserProfile(uid = MY_UID, monthlyIncome = 5000.0),
            uid = MY_UID,
            startMonthKey = "2099-02",
            count = 2,
            cards = cards
        )
        val feb = projections.find { it.monthKey == "2099-02" }
        assertNotNull(feb)
        assertEquals(5000.0, feb!!.projectedIncome, 0.01)
        assertEquals(0, feb.expenseCount)
    }

    @Test
    fun `2 e 3 separam Disponivel Agora paid de Projetado no Mes pending e calcula Limite Diario`() {
        val cards = listOf(VALE_CARD)
        val txs = listOf(
            makeTx(
                id = "va_in_paid",
                description = "Recarga VA Confirmada",
                category = "Benefícios",
                subcategory = "Vale Alimentação (VA)",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                status = TransactionStatus.PAID,
                amount = 1000.0
            ),
            makeTx(
                id = "va_in_pending",
                description = "Recarga VR Programada",
                category = "Benefícios",
                subcategory = "Vale Refeição (VR)",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                status = TransactionStatus.PENDING,
                amount = 200.0
            ),
            makeTx(
                id = "va_out_paid",
                description = "Mercado",
                category = "Alimentação",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                status = TransactionStatus.PAID,
                amount = 380.0
            ),
            makeTx(
                id = "va_out_pending",
                description = "Almoço Agendado",
                category = "Alimentação",
                subcategory = "Restaurante",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                status = TransactionStatus.PENDING,
                amount = 120.0
            )
        )

        val pendingBill = RecurringBill(
            id = "bill_va_extra",
            ownerUid = MY_UID,
            description = "Complemento VR",
            category = "Benefícios",
            subcategory = "Vale Refeição (VR)",
            dueDay = 10,
            defaultAmount = 100.0,
            estimationRule = EstimationRule.FIXED,
            active = true,
            type = TransactionType.INCOME,
            paymentMethod = PaymentMethod.VA_VR,
            cardId = VALE_CARD.id
        )

        val summary = FinanceEngine.computeSummary(
            all = txs,
            uid = MY_UID,
            monthKey = "2099-01",
            cards = cards,
            closures = emptyList(),
            recurringBills = listOf(pendingBill)
        )

        assertEquals(0.0, summary.vaVrCarryover, 0.01)
        assertEquals(1000.0, summary.vaVrIncomePaid, 0.01)
        assertEquals(300.0, summary.vaVrIncomePending, 0.01)
        assertEquals(1300.0, summary.vaVrIncome, 0.01)
        assertEquals(380.0, summary.vaVrSpentPaid, 0.01)
        assertEquals(120.0, summary.vaVrSpentPending, 0.01)
        assertEquals(500.0, summary.vaVrSpent, 0.01)
        assertEquals(620.0, summary.vaVrAvailableBalance, 0.01)
        assertEquals(800.0, summary.vaVrProjectedBalance, 0.01)
        assertNotNull(summary.vaVrDailyLimit)
        assertEquals(20.0, summary.vaVrDailyLimit!!, 0.01)
        assertEquals(2, summary.vaVrBySubcategory.sumOf { it.count })
    }

    @Test
    fun `4 faz rollover cronologico automatico e permite saldo Vale negativo sem tocar na conta principal`() {
        val cards = listOf(VALE_CARD)
        val closure = MonthClosure(
            id = "cls_2098-12",
            uid = MY_UID,
            closedMonth = "2098-12",
            targetMonth = "2099-01",
            totalSurplus = 900.0,
            carryoverToNextMonth = 900.0
        )

        val txs = listOf(
            makeTx(
                id = "dec_in",
                category = "Benefícios",
                subcategory = "Vale Alimentação (VA)",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                status = TransactionStatus.PAID,
                competenceMonth = "2098-12",
                dueDate = "2098-12-02",
                amount = 800.0
            ),
            makeTx(
                id = "dec_out",
                category = "Alimentação",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                status = TransactionStatus.PAID,
                competenceMonth = "2098-12",
                dueDate = "2098-12-10",
                amount = 350.0
            ),
            makeTx(
                id = "jan_sal",
                category = "Salário & Remuneração",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.PIX,
                status = TransactionStatus.PAID,
                competenceMonth = "2099-01",
                dueDate = "2099-01-05",
                amount = 4000.0
            ),
            makeTx(
                id = "jan_va_out",
                category = "Alimentação",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                status = TransactionStatus.PAID,
                competenceMonth = "2099-01",
                dueDate = "2099-01-12",
                amount = 600.0
            )
        )

        val janSummary = FinanceEngine.computeSummary(txs, MY_UID, "2099-01", cards, listOf(closure))
        assertEquals(450.0, janSummary.vaVrCarryover, 0.01)
        assertEquals(-150.0, janSummary.vaVrAvailableBalance, 0.01)
        assertEquals(0.0, janSummary.vaVrDailyLimit!!, 0.01)
        // Conta corrente recebe +900 do fechamento anterior e segue 100% blindada do -150 do Vale (4000 + 900 = 4900)
        assertEquals(4900.0, janSummary.freeBalance, 0.01)

        val febSummary = FinanceEngine.computeSummary(txs, MY_UID, "2099-02", cards, listOf(closure))
        assertEquals(0.0, febSummary.vaVrCarryover, 0.01)
    }

    @Test
    fun `5 rateio no Vale desconta 100 pct do saldo pre-pago do pagador e gera receivable pro terceiro`() {
        val cards = listOf(VALE_CARD)
        val sharedTx = makeTx(
            id = "va_split",
            description = "Jantar Compartilhado no Vale",
            category = "Alimentação",
            subcategory = "Restaurante",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.VA_VR,
            cardId = VALE_CARD.id,
            status = TransactionStatus.PAID,
            paidByUid = MY_UID,
            visibleToUids = listOf(MY_UID, PARTNER_UID),
            splitType = SplitType.SPLIT_50,
            amount = 200.0,
            userImpact = mapOf(MY_UID to 100.0, PARTNER_UID to 100.0),
            allocations = listOf(
                Allocation(uid = MY_UID, type = "PLATFORM_USER", name = "Eu", amount = 100.0, reimbursed = false),
                Allocation(uid = PARTNER_UID, type = "PLATFORM_USER", name = "Parceiro", amount = 100.0, reimbursed = false)
            )
        )

        val payerSummary = FinanceEngine.computeSummary(listOf(sharedTx), MY_UID, "2099-01", cards)
        assertEquals(200.0, payerSummary.vaVrSpent, 0.01)
        assertEquals(-200.0, payerSummary.vaVrAvailableBalance, 0.01)
        assertEquals(100.0, payerSummary.receivableFromPartner, 0.01)
        assertEquals(0.0, payerSummary.paid, 0.01)

        val partnerSummary = FinanceEngine.computeSummary(listOf(sharedTx), PARTNER_UID, "2099-01", cards)
        assertEquals(0.0, partnerSummary.vaVrSpent, 0.01)
        assertEquals(100.0, partnerSummary.payableToOthers, 0.01)
    }

    @Test
    fun `6 remove VA VR de categorias ativas e isola gastos Vale de envelopes tradicionais, score e faturas`() {
        assertFalse(CategoryRegistry.EXPENSE_CATEGORIES.any { it.name == "VA/VR" })
        assertFalse(CategoryRegistry.INCOME_CATEGORIES.any { it.name == "VA/VR" })
        assertFalse(CategoryRegistry.ACTIVE_CATEGORIES.contains("VA/VR"))
        assertTrue(CategoryRegistry.LEGACY_CATEGORIES.any { it.name == "VA/VR" })
        assertTrue(FinanceEngine.NON_ENVELOPE_CATEGORIES.contains("VA/VR"))
        assertEquals(BudgetNature.NEED, CategoryRegistry.getBudgetNature("VA/VR"))
        assertEquals(BudgetNature.INCOME, CategoryRegistry.getBudgetNature("VA/VR", "Recarga VA/VR"))
        assertTrue(VALE_CARD.isVaVrCard)
        assertFalse(CREDIT_CARD.isVaVrCard)

        val cards = listOf(VALE_CARD, CREDIT_CARD)
        val txs = listOf(
            makeTx(
                id = "s_sal",
                category = "Salário & Remuneração",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.PIX,
                amount = 6000.0
            ),
            makeTx(
                id = "s_va_food",
                description = "Supermercado no Vale",
                category = "Alimentação",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                cardId = VALE_CARD.id,
                amount = 2000.0
            ),
            makeTx(
                id = "s_pix_food",
                description = "Jantar no PIX",
                category = "Alimentação",
                subcategory = "Restaurante",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                amount = 500.0
            )
        )

        val summary = FinanceEngine.computeSummary(txs, MY_UID, "2099-01", cards)
        val report = FinanceEngine.computeCategoryBudgetsFromTransactions(
            transactions = txs,
            categoryBudgets = mapOf("Alimentação" to 1000.0),
            monthKey = "2099-01",
            referenceDate = "2099-01-15",
            uid = MY_UID,
            allTransactions = txs,
            expectedIncome = 6000.0,
            cards = cards
        )

        val foodItem = report.items.find { it.category == "Alimentação" }
        assertNotNull(foodItem)
        assertEquals(500.0, foodItem!!.spent, 0.01)
        assertFalse(report.items.any { it.category == "VA/VR" })

        val health = FinanceEngine.calculateFinancialHealthScore(summary, 0.0, report)
        assertEquals(500.0, health.monthlyLivingCost, 0.01)
        assertEquals(30, health.spendingLimitScore)
        assertEquals(15, health.envelopeScore)
        assertEquals(45, health.score)

        val pastTxs = txs.map { it.copy(competenceMonth = "2098-12", dueDate = "2098-12-10") }
        val suggested = FinanceEngine.suggestCategoryBudgets(
            allTransactions = pastTxs,
            uid = MY_UID,
            referenceMonth = "2099-01",
            monthlyIncome = 6000.0,
            cards = cards
        )
        assertNull(suggested.suggestedBudgets["VA/VR"])
        assertEquals(500.0, suggested.categoryAverages["Alimentação"] ?: 0.0, 0.01)
        assertEquals(600.0, suggested.suggestedBudgets["Alimentação"] ?: 0.0, 0.01)

        // Cartão tipo 'va_vr' nunca gera fatura pós-paga
        val valeInvoice = InvoiceEngine.buildInvoice(VALE_CARD, "2099-01", txs, MY_UID, "2099-01-15")
        assertTrue(valeInvoice.items.isEmpty())
        assertEquals(0.0, valeInvoice.total, 0.01)
        assertEquals(0.0, valeInvoice.grossExpenses, 0.01)
    }

    @Test
    fun `7 classificadores detectam metodo VA_VR, preservam categorias reais e migram VA VR legado`() {
        assertTrue(CategoryRegistry.isVaVrCategory("VA/VR"))
        assertTrue(CategoryRegistry.isVaVrCategory("Benefícios", "Vale Refeição (VR)"))
        assertTrue(
            CategoryRegistry.isVaVrTransaction(
                makeTx(paymentMethod = PaymentMethod.VA_VR, category = "Saúde"),
                listOf(VALE_CARD)
            )
        )
        assertTrue(
            CategoryRegistry.isVaVrTransaction(
                makeTx(paymentMethod = PaymentMethod.CREDIT_CARD, cardId = VALE_CARD.id, category = "Transporte"),
                listOf(VALE_CARD)
            )
        )
        assertTrue(
            CategoryRegistry.isVaVrRecurringBill(
                RecurringBill(
                    id = "b_vavr",
                    ownerUid = MY_UID,
                    description = "Vale",
                    category = "Benefícios",
                    paymentMethod = PaymentMethod.VA_VR
                ),
                listOf(VALE_CARD)
            )
        )

        val classifier = NeuralFinanceClassifier(context = null)
        assertEquals(PaymentMethod.VA_VR, classifier.detectPaymentMethod("Recarga Pluxee VA"))
        assertEquals(PaymentMethod.VA_VR, classifier.detectPaymentMethod("Uber com VR Flash"))
        assertEquals(PaymentMethod.VA_VR, classifier.detectPaymentMethod("Supermercado iFood Benefícios"))
        assertNotEquals(PaymentMethod.VA_VR, classifier.detectPaymentMethod("Ticket show rock"))
        assertNotEquals(PaymentMethod.VA_VR, classifier.detectPaymentMethod("Flash drive USB 64gb"))

        val recarga = classifier.guessCategoryAndSubcategory("Recarga Benefícios Pluxee mensal", type = TransactionType.INCOME)
        assertEquals("Benefícios", recarga.category)
        assertEquals("Vale Alimentação (VA)", recarga.subcategory)

        val supermercado = classifier.guessCategoryAndSubcategory("Supermercado iFood Benefícios", type = TransactionType.EXPENSE)
        assertEquals("Alimentação", supermercado.category)
        assertEquals("Supermercado", supermercado.subcategory)

        val uberFlash = classifier.guessCategoryAndSubcategory("Uber com VR Flash", type = TransactionType.EXPENSE)
        assertEquals("Transporte", uberFlash.category)

        val almocoAlelo = classifier.guessCategoryAndSubcategory("Almoço Alelo Restaurante", type = TransactionType.EXPENSE)
        assertEquals("Alimentação", almocoAlelo.category)
        assertEquals("Restaurante", almocoAlelo.subcategory)

        val strictExp = CategoryRegistry.resolveStrictCategory("VA/VR", "Supermercado", TransactionType.EXPENSE)
        assertEquals("Alimentação", strictExp.first)
        assertEquals("Supermercado", strictExp.second)

        val strictInc = CategoryRegistry.resolveStrictCategory("VA/VR", "Recarga VA/VR", TransactionType.INCOME)
        assertEquals("Benefícios", strictInc.first)
        assertEquals("Vale Alimentação (VA)", strictInc.second)
    }
}
