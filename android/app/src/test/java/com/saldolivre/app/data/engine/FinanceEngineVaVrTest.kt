package com.saldolivre.app.data.engine

import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import com.saldolivre.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class FinanceEngineVaVrTest {

    private val myUid = "u1"
    private val partnerUid = "u2"

    private val me = UserProfile(
        uid = myUid,
        displayName = "Kayo",
        email = "kayo@test.com",
        monthlyIncome = 5000.0
    )

    private fun makeTx(
        id: String = "tx_${System.nanoTime()}",
        description: String = "Lançamento Teste",
        category: String = "Alimentação",
        subcategory: String? = "Supermercado",
        type: TransactionType = TransactionType.EXPENSE,
        paymentMethod: PaymentMethod = PaymentMethod.PIX,
        status: TransactionStatus = TransactionStatus.PAID,
        totalAmount: Double = 100.0,
        dueDate: String = "2099-01-10",
        competenceMonth: String = "2099-01",
        paidByUid: String = myUid,
        splitType: SplitType? = SplitType.MINE,
        allocations: List<Allocation> = listOf(
            Allocation(type = "PLATFORM_USER", uid = myUid, name = "Kayo", amount = totalAmount, reimbursed = true)
        ),
        userImpact: Map<String, Double> = mapOf(myUid to totalAmount),
        visibleToUids: List<String> = listOf(myUid)
    ) = Transaction(
        id = id,
        createdByUid = myUid,
        paidByUid = paidByUid,
        description = description,
        category = category,
        subcategory = subcategory,
        type = type,
        paymentMethod = paymentMethod,
        status = status,
        estimatedAmount = null,
        actualAmount = totalAmount,
        totalAmount = totalAmount,
        dueDate = dueDate,
        competenceMonth = competenceMonth,
        visibleToUids = visibleToUids,
        userImpact = userImpact,
        allocations = allocations,
        splitType = splitType,
        createdAt = 1L
    )

    @Test
    fun `1 - isolates 100pct of VA VR inflows outflows and recurring bills from checking freeBalance and projections`() {
        val txs = listOf(
            makeTx(
                id = "sal",
                description = "Salário CLT",
                category = "Salário & Remuneração",
                subcategory = "Salário Principal",
                type = TransactionType.INCOME,
                totalAmount = 5000.0
            ),
            makeTx(
                id = "rent",
                description = "Aluguel",
                category = "Moradia",
                subcategory = "Aluguel",
                type = TransactionType.EXPENSE,
                totalAmount = 1500.0
            ),
            makeTx(
                id = "vr_recarga",
                description = "Recarga Alelo VA/VR",
                category = "VA/VR",
                subcategory = "Recarga VA/VR",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.PIX,
                totalAmount = 1200.0
            ),
            makeTx(
                id = "vr_mercado",
                description = "Supermercado Pão de Açúcar",
                category = "VA/VR",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.DEBIT,
                totalAmount = 450.0
            ),
            makeTx(
                id = "vr_legado",
                description = "Lanche VR",
                category = "Benefícios",
                subcategory = "Vale Refeição (VR)",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.DEBIT,
                status = TransactionStatus.PENDING,
                totalAmount = 50.0
            )
        )

        val vaVrBill = RecurringBill(
            id = "rec_vr",
            ownerUid = myUid,
            description = "Recarga Mensal Flash VA/VR",
            category = "VA/VR",
            subcategory = "Recarga VA/VR",
            dueDay = 1,
            defaultAmount = 1100.0,
            estimationRule = EstimationRule.FIXED,
            active = true,
            paymentMethod = PaymentMethod.PIX,
            type = TransactionType.INCOME,
            startMonth = "2099-02"
        )

        val summary = FinanceEngine.computeSummary(
            all = txs,
            uid = myUid,
            monthKey = "2099-01",
            cards = emptyList(),
            closures = emptyList(),
            recurringBills = listOf(vaVrBill)
        )
        assertEquals(5000.0, summary.income, 0.01)
        assertEquals(1500.0, summary.paid, 0.01)
        assertEquals(0.0, summary.committed, 0.01)
        assertEquals(3500.0, summary.freeBalance, 0.01)

        val cashFlow = FinanceEngine.computeAccountCashFlow(
            all = txs,
            uid = myUid,
            monthKey = "2099-01",
            summary = summary,
            cards = emptyList(),
            today = "2099-01-15"
        )
        assertEquals(5000.0, cashFlow.accountIncomesReceived, 0.01)
        assertEquals(1500.0, cashFlow.accountExpensesPaid, 0.01)
        assertEquals(3500.0, cashFlow.currentBankBalance, 0.01)

        val proj = FinanceEngine.projectFutureMonths(
            all = txs,
            bills = listOf(vaVrBill),
            profile = me.copy(monthlyIncome = 0.0),
            uid = myUid,
            startMonthKey = "2099-01",
            count = 3,
            cards = emptyList()
        )
        assertEquals(5000.0, proj[0].projectedIncome, 0.01)
        assertEquals(1500.0, proj[0].projectedExpense, 0.01)
        assertEquals(3500.0, proj[0].projectedFreeBalance, 0.01)
        assertEquals(0.0, proj[1].projectedIncome, 0.01)
    }

    @Test
    fun `2 and 3 - separates Available Now vs Projected Balance and computes Daily Limit VA VR`() {
        val txs = listOf(
            makeTx(
                id = "vr_in_paid",
                description = "Recarga Pluxee Paga",
                category = "VA/VR",
                subcategory = "Recarga VA/VR",
                type = TransactionType.INCOME,
                status = TransactionStatus.PAID,
                totalAmount = 1000.0
            ),
            makeTx(
                id = "vr_in_pending",
                description = "Bônus Alimentação Previsto",
                category = "VA/VR",
                subcategory = "Vale Alimentação (VA)",
                type = TransactionType.INCOME,
                status = TransactionStatus.PENDING,
                totalAmount = 200.0
            ),
            makeTx(
                id = "vr_out_paid",
                description = "Almoço Restaurante",
                category = "VA/VR",
                subcategory = "Restaurante",
                type = TransactionType.EXPENSE,
                status = TransactionStatus.PAID,
                totalAmount = 380.0
            ),
            makeTx(
                id = "vr_out_pending",
                description = "Mercado Fim de Semana",
                category = "VA/VR",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                status = TransactionStatus.PENDING,
                totalAmount = 120.0
            )
        )

        val unlaunchedBill = RecurringBill(
            id = "rec_vr_in",
            ownerUid = myUid,
            description = "Complemento VR Mensal",
            category = "VA/VR",
            subcategory = "Vale Refeição (VR)",
            dueDay = 20,
            defaultAmount = 100.0,
            estimationRule = EstimationRule.FIXED,
            active = true,
            paymentMethod = PaymentMethod.PIX,
            type = TransactionType.INCOME,
            startMonth = "2099-01"
        )

        val summary = FinanceEngine.computeSummary(
            all = txs,
            uid = myUid,
            monthKey = "2099-01",
            cards = emptyList(),
            closures = emptyList(),
            recurringBills = listOf(unlaunchedBill)
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
        assertEquals(31, summary.daysRemaining)
        assertEquals(20.0, summary.vaVrDailyLimit!!, 0.01)
        assertEquals(2, summary.vaVrBySubcategory.sumOf { it.count })
    }

    @Test
    fun `4 - performs automatic chronological rollover and allows negative VA VR without touching checking balance`() {
        val txs = listOf(
            makeTx(
                id = "m0_in",
                category = "VA/VR",
                subcategory = "Recarga VA/VR",
                type = TransactionType.INCOME,
                dueDate = "2098-11-05",
                competenceMonth = "2098-11",
                totalAmount = 800.0
            ),
            makeTx(
                id = "m0_out",
                category = "VA/VR",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                dueDate = "2098-11-20",
                competenceMonth = "2098-11",
                totalAmount = 500.0
            ),
            makeTx(
                id = "m1_in",
                category = "VA/VR",
                subcategory = "Recarga VA/VR",
                type = TransactionType.INCOME,
                dueDate = "2098-12-05",
                competenceMonth = "2098-12",
                totalAmount = 400.0
            ),
            makeTx(
                id = "m1_out",
                category = "VA/VR",
                subcategory = "Restaurante",
                type = TransactionType.EXPENSE,
                dueDate = "2098-12-18",
                competenceMonth = "2098-12",
                totalAmount = 250.0
            ),
            makeTx(
                id = "m2_sal",
                category = "Salário & Remuneração",
                subcategory = "Salário Principal",
                type = TransactionType.INCOME,
                dueDate = "2099-01-05",
                competenceMonth = "2099-01",
                totalAmount = 5000.0
            ),
            makeTx(
                id = "m2_conta",
                category = "Moradia",
                subcategory = "Aluguel",
                type = TransactionType.EXPENSE,
                dueDate = "2099-01-10",
                competenceMonth = "2099-01",
                totalAmount = 1000.0
            ),
            makeTx(
                id = "m2_vr_estouro",
                category = "VA/VR",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                dueDate = "2099-01-12",
                competenceMonth = "2099-01",
                totalAmount = 600.0
            )
        )

        val janSummary = FinanceEngine.computeSummary(
            all = txs,
            uid = myUid,
            monthKey = "2099-01",
            cards = emptyList(),
            closures = emptyList()
        )
        assertEquals(450.0, janSummary.vaVrCarryover, 0.01)
        assertEquals(-150.0, janSummary.vaVrAvailableBalance, 0.01)
        assertEquals(0.0, janSummary.vaVrDailyLimit!!, 0.01)
        assertEquals(4000.0, janSummary.freeBalance, 0.01)

        val febSummary = FinanceEngine.computeSummary(
            all = txs,
            uid = myUid,
            monthKey = "2099-02",
            cards = emptyList(),
            closures = emptyList()
        )
        assertEquals(0.0, febSummary.vaVrCarryover, 0.01)
    }

    @Test
    fun `5 - split VA VR purchase deducts full purchase from payer pot while generating checking receivable`() {
        val splitPurchase = makeTx(
            id = "vr_split_200",
            paidByUid = myUid,
            description = "Compras do Mês Carrefour (VA)",
            category = "VA/VR",
            subcategory = "Supermercado",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.DEBIT,
            status = TransactionStatus.PAID,
            totalAmount = 200.0,
            splitType = SplitType.SPLIT_50,
            allocations = listOf(
                Allocation(type = "PLATFORM_USER", uid = myUid, name = "Kayo", amount = 100.0, reimbursed = true),
                Allocation(type = "PLATFORM_USER", uid = partnerUid, name = "Ana", amount = 100.0, reimbursed = false)
            ),
            userImpact = mapOf(myUid to 100.0, partnerUid to 100.0),
            visibleToUids = listOf(myUid, partnerUid)
        )

        val payerSummary = FinanceEngine.computeSummary(
            all = listOf(splitPurchase),
            uid = myUid,
            monthKey = "2099-01"
        )
        assertEquals(200.0, payerSummary.vaVrSpent, 0.01)
        assertEquals(-200.0, payerSummary.vaVrAvailableBalance, 0.01)
        assertEquals(100.0, payerSummary.receivableFromPartner, 0.01)
        assertEquals(100.0, payerSummary.receivableTotal, 0.01)

        val partnerSummary = FinanceEngine.computeSummary(
            all = listOf(splitPurchase),
            uid = partnerUid,
            monthKey = "2099-01"
        )
        assertEquals(0.0, partnerSummary.vaVrSpent, 0.01)
        assertEquals(100.0, partnerSummary.payableToOthers, 0.01)
    }

    @Test
    fun `6 - VA VR is self-budgeted and excluded from traditional envelopes suggestions and health living cost`() {
        assertTrue(FinanceEngine.NON_ENVELOPE_CATEGORIES.contains("VA/VR"))
        assertEquals(BudgetNature.NEED, CategoryRegistry.getBudgetNature("VA/VR"))
        assertEquals(BudgetNature.INCOME, CategoryRegistry.getBudgetNature("VA/VR", "Recarga VA/VR"))
        assertTrue(CategoryRegistry.EXPENSE_CATEGORIES.any { it.name == "VA/VR" })
        assertTrue(CategoryRegistry.INCOME_CATEGORIES.any { it.name == "VA/VR" })

        val txs = listOf(
            makeTx(
                id = "s_sal",
                category = "Salário & Remuneração",
                subcategory = "Salário Principal",
                type = TransactionType.INCOME,
                dueDate = "2026-10-05",
                competenceMonth = "2026-10",
                totalAmount = 5000.0
            ),
            makeTx(
                id = "s_alim",
                category = "Alimentação",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                dueDate = "2026-10-12",
                competenceMonth = "2026-10",
                totalAmount = 600.0
            ),
            makeTx(
                id = "s_vavr_huge",
                category = "VA/VR",
                subcategory = "Supermercado",
                type = TransactionType.EXPENSE,
                dueDate = "2026-10-15",
                competenceMonth = "2026-10",
                totalAmount = 4000.0
            )
        )

        val report = FinanceEngine.computeCategoryBudgetsFromTransactions(
            transactions = txs,
            categoryBudgets = mapOf("Alimentação" to 1000.0),
            monthKey = "2026-10",
            referenceDate = "2026-10-20",
            uid = myUid,
            allTransactions = txs,
            expectedIncome = 5000.0
        )
        assertFalse(report.items.any { it.category == "VA/VR" })
        assertEquals(600.0, report.totalSpent, 0.01)

        val suggested = FinanceEngine.suggestCategoryBudgets(
            allTransactions = txs,
            uid = myUid,
            referenceMonth = "2026-11",
            monthlyIncome = 5000.0
        )
        assertNull(suggested.suggestedBudgets["VA/VR"])

        val janSummary = FinanceEngine.computeSummary(
            all = txs.map { it.copy(competenceMonth = "2099-01", dueDate = "2099-01-15") },
            uid = myUid,
            monthKey = "2099-01"
        )
        val health = FinanceEngine.calculateFinancialHealthScore(janSummary, 3000.0, report)
        assertEquals(600.0, health.monthlyLivingCost, 0.01)
        assertEquals(12, health.spendingRatioPercentage)
    }

    @Test
    fun `7 - NeuralFinanceClassifier and CategoryRegistry recognize VA VR brands and dual categories`() {
        assertTrue(CategoryRegistry.isVaVrCategory("VA/VR"))
        assertTrue(CategoryRegistry.isVaVrCategory("Benefícios", "Vale Refeição (VR)"))
        assertTrue(CategoryRegistry.isVaVrTransaction(makeTx(category = "VA/VR", subcategory = "Padaria")))

        val classifier = NeuralFinanceClassifier(null)
        val samples = listOf(
            Triple("Recarga Benefícios Pluxee mensal", TransactionType.INCOME, "Recarga VA/VR"),
            Triple("Recarga Alelo VA mensal", TransactionType.INCOME, "Vale Alimentação (VA)"),
            Triple("Crédito Caju Vale Refeição", TransactionType.INCOME, "Vale Refeição (VR)"),
            Triple("Restaurante Pluxee almoço", TransactionType.EXPENSE, "Restaurante"),
            Triple("Supermercado iFood Benefícios", TransactionType.EXPENSE, "Supermercado"),
            Triple("Ticket Padaria café", TransactionType.EXPENSE, "Padaria"),
            Triple("Compra Flash Benefícios", TransactionType.EXPENSE, null),
            Triple("Almoço Sodexo Express", TransactionType.EXPENSE, null)
        )

        for ((text, txType, expectedSub) in samples) {
            val guess = classifier.guessCategoryAndSubcategory(text, type = txType)
            assertEquals("Expected VA/VR for '$text'", "VA/VR", guess.category)
            if (expectedSub != null) {
                assertEquals("Expected subcat for '$text'", expectedSub, guess.subcategory)
            }
        }

        val strictExp = CategoryRegistry.resolveStrictCategory("VA/VR", "Supermercado", TransactionType.EXPENSE)
        assertEquals("VA/VR", strictExp.first)
        assertEquals("Supermercado", strictExp.second)

        val strictInc = CategoryRegistry.resolveStrictCategory("VA/VR", "Recarga VA/VR", TransactionType.INCOME)
        assertEquals("VA/VR", strictInc.first)
        assertEquals("Recarga VA/VR", strictInc.second)

        val strictAlias = CategoryRegistry.resolveStrictCategory("Vale Alimentação", "Vale Alimentação (VA)", TransactionType.INCOME)
        assertEquals("VA/VR", strictAlias.first)
        assertEquals("Vale Alimentação (VA)", strictAlias.second)
    }
}
