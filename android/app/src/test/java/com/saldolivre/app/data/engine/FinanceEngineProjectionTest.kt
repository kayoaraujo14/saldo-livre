package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceEngineProjectionTest {

    private val myUid = "u1"
    private val me = UserProfile(
        uid = myUid,
        displayName = "Kayo",
        email = "kayo@test.com",
        monthlyIncome = 5000.0
    )

    private val cards = listOf(
        Card(
            id = "c1",
            ownerUid = myUid,
            cardName = "Nubank",
            closingDay = 25,
            dueDay = 5
        )
    )

    @Test
    fun `projectFutureMonths returns exact count for 6 and 12 months horizon`() {
        val proj6 = FinanceEngine.projectFutureMonths(
            all = emptyList(),
            bills = emptyList(),
            profile = me,
            uid = myUid,
            startMonthKey = "2026-11",
            count = 6,
            cards = cards
        )
        assertEquals(6, proj6.size)

        val proj12 = FinanceEngine.projectFutureMonths(
            all = emptyList(),
            bills = emptyList(),
            profile = me,
            uid = myUid,
            startMonthKey = "2026-11",
            count = 12,
            cards = cards
        )
        assertEquals(12, proj12.size)
        assertEquals("2026-11", proj12.first().monthKey)
        assertEquals("2027-10", proj12.last().monthKey)
    }

    @Test
    fun `projectFutureMonths correctly breaks down invoices, fixed bills, and debit pix`() {
        val transactions = listOf(
            // Compra no cartão de crédito em 2026-11
            Transaction(
                id = "tx1",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                cardId = "c1",
                totalAmount = 300.0,
                category = "Eletrônicos",
                description = "Fone de ouvido",
                dueDate = "2026-11-05",
                visibleToUids = listOf(myUid),
                userImpact = mapOf(myUid to 300.0)
            ),
            // Despesa fixa de extrato (luz)
            Transaction(
                id = "tx2",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.BILL,
                recurringBillId = "bill_light",
                totalAmount = 150.0,
                category = "Moradia",
                description = "Conta de Luz",
                dueDate = "2026-11-10",
                visibleToUids = listOf(myUid),
                userImpact = mapOf(myUid to 150.0)
            ),
            // Compra avulsa no débito/pix (mercado)
            Transaction(
                id = "tx3",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                totalAmount = 200.0,
                category = "Alimentação",
                description = "Supermercado",
                dueDate = "2026-11-15",
                visibleToUids = listOf(myUid),
                userImpact = mapOf(myUid to 200.0)
            )
        )

        val recurringBills = listOf(
            RecurringBill(
                id = "bill_internet",
                ownerUid = myUid,
                description = "Internet Fibra",
                category = "Moradia",
                defaultAmount = 100.0,
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.BILL,
                active = true,
                dueDay = 20
            )
        )

        val proj = FinanceEngine.projectFutureMonths(
            all = transactions,
            bills = recurringBills,
            profile = me,
            uid = myUid,
            startMonthKey = "2026-11",
            count = 1,
            cards = cards
        ).first()

        assertEquals(5000.0, proj.projectedIncome, 0.01)
        assertEquals(300.0, proj.invoiceExpense, 0.01)
        assertEquals(250.0, proj.fixedBillsExpense, 0.01) // 150 (luz) + 100 (internet não gerada)
        assertEquals(200.0, proj.variableAccountExpense, 0.01) // 200 (pix mercado)
        assertEquals(750.0, proj.projectedExpense, 0.01)
        assertEquals(4250.0, proj.projectedFreeBalance, 0.01)

        // Verifica itens e categorias do Raio-X
        assertTrue(proj.items.isNotEmpty())
        assertTrue(proj.byCategory.isNotEmpty())

        val moradiaCat = proj.byCategory.find { it.category == "Moradia" }
        assertNotNull(moradiaCat)
        assertEquals(250.0, moradiaCat!!.amount, 0.01)
        assertEquals(250.0, moradiaCat.fixedBillAmount, 0.01)

        val foneItem = proj.items.find { it.description == "Fone de ouvido" }
        assertNotNull(foneItem)
        assertEquals("invoice", foneItem!!.origin)
        assertEquals("Nubank", foneItem.cardName)
    }

    @Test
    fun `projectFutureMonths handles carryover and deficit correctly`() {
        val closures = listOf(
            MonthClosure(
                id = "cl1",
                closedMonth = "2026-10",
                targetMonth = "2026-11",
                uid = myUid,
                carryoverToNextMonth = 400.0
            )
        )

        val proj = FinanceEngine.projectFutureMonths(
            all = emptyList(),
            bills = emptyList(),
            profile = me,
            uid = myUid,
            startMonthKey = "2026-11",
            count = 1,
            cards = cards,
            closures = closures
        ).first()

        assertEquals(400.0, proj.carryoverAmount, 0.01)
        // Salário 5000 + carryover 400 = 5400 saldo livre
        assertEquals(5400.0, proj.projectedFreeBalance, 0.01)
    }

    @Test
    fun `computeCategoryBudgets distinguishes fixed vs variable categories and excludes Investments from unbudgeted`() {
        val summary = MonthSummary(
            monthKey = "2026-10",
            income = 8000.0,
            committed = 4100.0,
            paid = 3000.0,
            freeBalance = 3900.0,
            byCategory = listOf(
                CategoryExpense("Moradia", 2000.0),
                CategoryExpense("Alimentação", 900.0),
                CategoryExpense("Lazer", 500.0),
                CategoryExpense("Investimentos", 700.0)
            )
        )

        val budgets = mapOf(
            "Moradia" to 2000.0,      // 100% no início do mês -> categoria fixa -> SAFE
            "Alimentação" to 1200.0,  // 75% -> SAFE
            "Lazer" to 400.0          // 125% -> DANGER
        )

        val report = FinanceEngine.computeCategoryBudgets(
            summary = summary,
            categoryBudgets = budgets,
            referenceDate = "2026-10-08"
        )

        val moradia = report.categories.find { it.category == "Moradia" }
        assertNotNull(moradia)
        assertTrue(moradia!!.isFixedCategory)
        assertEquals(BudgetEnvelopeStatus.SAFE, moradia.status)
        assertEquals(BudgetBurnRateStatus.ON_TRACK, moradia.burnRateStatus)

        val lazer = report.categories.find { it.category == "Lazer" }
        assertNotNull(lazer)
        assertFalse(lazer!!.isFixedCategory)
        assertEquals(BudgetEnvelopeStatus.DANGER, lazer.status)

        // Investimentos não deve aparecer como categoria desprotegida sem teto
        assertTrue(report.unbudgetedCategories.none { it.category == "Investimentos" })

        val rebalance = FinanceEngine.suggestBudgetRebalance(report)
        assertTrue(rebalance.canCoverFullDeficit)
        assertEquals(100.0, rebalance.totalDeficit, 0.01)
        assertEquals("Alimentação", rebalance.transfers.first().fromCategory)
        assertEquals("Lazer", rebalance.transfers.first().toCategory)
    }

    @Test
    fun `calculateFinancialHealthScore applies 4-pillar synergy and discounts investments from living cost`() {
        val summary = MonthSummary(
            monthKey = "2026-10",
            income = 10000.0,
            committed = 8000.0, // 5000 custo de vida + 3000 investimentos
            paid = 8000.0,
            freeBalance = 2000.0,
            totalInvested = 3000.0,
            byCategory = listOf(
                CategoryExpense("Moradia", 3000.0),
                CategoryExpense("Alimentação", 2000.0),
                CategoryExpense("Investimentos", 3000.0)
            )
        )

        val budgets = mapOf(
            "Moradia" to 3000.0,
            "Alimentação" to 2200.0
        )
        val report = FinanceEngine.computeCategoryBudgets(summary, budgets, "2026-10-15")
        val health = FinanceEngine.calculateFinancialHealthScore(
            summary = summary,
            emergencyReserveBalance = 30000.0, // 6x custo de vida (5000)
            budgetReport = report
        )

        // Custo de vida deve descontar Investimentos (8000 - 3000 = 5000 -> 50% da renda -> 30/30 pts)
        assertEquals(5000.0, health.monthlyLivingCost, 0.01)
        assertEquals(50, health.spendingRatioPercentage)
        assertEquals(30, health.spendingLimitScore)
        assertEquals(30, health.savingsScore)
        assertEquals(25, health.emergencyReserveScore)
        assertEquals(15, health.envelopeScore)
        assertEquals(100, health.score)
        assertEquals(FinancialHealthClassification.EXCELLENT, health.classification)
        assertTrue(health.actionableTips.isNotEmpty())
    }

    @Test
    fun `computeCategoryBudgets and suggestCategoryBudgets cap total budget to expected monthly income and reconcile aliases`() {
        val summary = MonthSummary(
            monthKey = "2026-11",
            income = 4500.0,
            committed = 1500.0,
            paid = 2000.0,
            freeBalance = 1000.0,
            byCategory = listOf(
                CategoryExpense("Moradia", 2000.0),
                CategoryExpense("Alimentação", 1200.0),
                CategoryExpense("Lazer", 300.0)
            )
        )

        // Soma bruta = 6000, mas entrada esperada no mês (summary.income) = 4500
        val budgets = mapOf(
            "Moradia" to 3000.0,
            "Alimentação" to 2000.0,
            "Lazer & Entretenimento" to 1000.0
        )

        val report = FinanceEngine.computeCategoryBudgets(
            summary = summary,
            categoryBudgets = budgets,
            referenceDate = "2026-11-15"
        )

        assertTrue(report.isCappedToIncome)
        assertEquals(6000.0, report.rawTotalBudgeted, 0.01)
        assertEquals(4500.0, report.expectedIncome, 0.01)
        assertEquals(4500.0, report.totalBudgeted, 0.01)

        val lazer = report.items.find { it.category == "Lazer & Entretenimento" }
        assertNotNull(lazer)
        assertEquals(300.0, lazer!!.spent, 0.01)
        assertEquals(750.0, lazer.limit, 0.01)
    }

    @Test
    fun `buildBudgetInsights produces actionable insights for over budget categories`() {
        val item1 = CategoryBudgetStatus(
            category = "Alimentação & Mercado",
            spent = 800.0,
            pendingAmount = 100.0,
            committedAmount = 900.0,
            limit = 600.0,
            remaining = -300.0,
            realRemaining = -300.0,
            percentage = 150.0,
            status = CategoryBudgetAlertStatus.DANGER
        )
        val item2 = CategoryBudgetStatus(
            category = "Lazer & Entretenimento",
            spent = 100.0,
            pendingAmount = 0.0,
            committedAmount = 100.0,
            limit = 400.0,
            remaining = 300.0,
            realRemaining = 300.0,
            percentage = 25.0,
            status = CategoryBudgetAlertStatus.OK
        )

        // Cenário 1: Há sobra em outra categoria -> sugere REBALANCE
        val reportWithSurplus = CategoryBudgetReport(
            items = listOf(item1, item2),
            totalSpent = 900.0,
            totalBudgeted = 1000.0,
            overallPercentage = 90.0,
            isCappedToIncome = false,
            rawTotalBudgeted = 1000.0,
            expectedIncome = 3000.0,
            totalCommitted = 900.0,
            totalPending = 100.0,
            totalRealRemaining = 100.0,
            investmentGoal = 600.0
        )

        val insights1 = FinanceEngine.buildBudgetInsights(reportWithSurplus)
        assertTrue(insights1.isNotEmpty())
        val overInsight1 = insights1.find { it.severity == BudgetInsightSeverity.DANGER }
        assertNotNull(overInsight1)
        assertNotNull(overInsight1?.action)
        assertEquals(BudgetInsightActionType.REBALANCE, overInsight1?.action?.type)
        assertEquals("Alimentação & Mercado", overInsight1?.action?.category)
        assertEquals("Lazer & Entretenimento", overInsight1?.action?.fromCategory)

        // Cenário 2: Sem categorias com sobra para compensar -> sugere ADJUST_LIMIT (subir teto)
        val reportWithoutSurplus = CategoryBudgetReport(
            items = listOf(item1),
            totalSpent = 800.0,
            totalBudgeted = 600.0,
            overallPercentage = 150.0,
            isCappedToIncome = false,
            rawTotalBudgeted = 600.0,
            expectedIncome = 3000.0,
            totalCommitted = 900.0,
            totalPending = 100.0,
            totalRealRemaining = -300.0,
            investmentGoal = 600.0
        )

        val insights2 = FinanceEngine.buildBudgetInsights(reportWithoutSurplus)
        assertTrue(insights2.isNotEmpty())
        val overInsight2 = insights2.find { it.severity == BudgetInsightSeverity.DANGER }
        assertNotNull(overInsight2)
        assertNotNull(overInsight2?.action)
        assertEquals(BudgetInsightActionType.ADJUST_LIMIT, overInsight2?.action?.type)
        assertEquals("Alimentação & Mercado", overInsight2?.action?.category)
    }
}
