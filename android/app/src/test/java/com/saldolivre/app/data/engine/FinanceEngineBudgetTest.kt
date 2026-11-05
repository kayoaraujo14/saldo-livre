package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class FinanceEngineBudgetTest {

    private val myUid = "u1"
    private val me = UserProfile(
        uid = myUid,
        displayName = "Kayo",
        monthlyIncome = 5000.0
    )

    @Test
    fun `calculateFinancialHealthScore weights sum exactly to 100 points`() {
        // Ideal scenario: 25% savings, 60% spending, 6 months emergency reserve, 100% budget compliance
        val summary = MonthSummary(
            income = 10000.0,
            paid = 6000.0,
            committed = 0.0,
            totalInvested = 2500.0,
            freeBalance = 1500.0
        )
        val report = CategoryBudgetReport(
            items = listOf(
                CategoryBudgetStatus(
                    category = "Alimentação",
                    limit = 1000.0,
                    spent = 500.0,
                    committedAmount = 500.0,
                    remaining = 500.0,
                    realRemaining = 500.0,
                    percentage = 50.0,
                    status = CategoryBudgetAlertStatus.OK
                )
            ),
            totalSpent = 500.0,
            totalBudgeted = 1000.0,
            status = CategoryBudgetAlertStatus.OK
        )

        val health = FinanceEngine.calculateFinancialHealthScore(
            summary = summary,
            emergencyReserveBalance = 60000.0, // 10x monthly living cost (well above 6m)
            budgetReport = report
        )

        // Weights: savings (max 30), spendingLimit (max 30), emergency (max 25), envelope (max 15) = 100
        assertEquals(30, health.savingsScore)
        assertEquals(30, health.spendingLimitScore)
        assertEquals(25, health.emergencyReserveScore)
        assertEquals(15, health.envelopeScore)
        assertEquals(100, health.score)
        assertEquals(FinancialHealthClassification.EXCELLENT, health.classification)
    }

    @Test
    fun `computeCategoryBudgetsFromTransactions isolates Investimentos from total expense envelopes`() {
        val txs = listOf(
            Transaction(
                id = "tx1",
                description = "Mercado",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                totalAmount = 400.0,
                category = "Alimentação",
                dueDate = "2026-11-05",
                status = TransactionStatus.PAID,
                visibleToUids = listOf(myUid),
                userImpact = mapOf(myUid to 400.0)
            ),
            Transaction(
                id = "tx2",
                description = "Aporte Tesouro Selic",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                totalAmount = 1500.0,
                category = "Investimentos",
                dueDate = "2026-11-06",
                status = TransactionStatus.PAID,
                visibleToUids = listOf(myUid),
                userImpact = mapOf(myUid to 1500.0)
            )
        )

        val ceilings = mapOf(
            "Alimentação" to 800.0,
            "Investimentos" to 1500.0
        )

        val report = FinanceEngine.computeCategoryBudgetsFromTransactions(
            transactions = txs,
            categoryBudgets = ceilings,
            expectedIncome = 5000.0,
            recurringBills = emptyList(),
            uid = myUid,
            monthKey = "2026-11",
            referenceDate = "2026-11-10"
        )

        // totalSpent must NOT include Investimentos
        assertEquals(400.0, report.totalSpent, 0.001)
        // totalBudgeted must NOT include Investimentos
        assertEquals(800.0, report.totalBudgeted, 0.001)
        // Investment target and spent must be populated separately
        assertEquals(1500.0, report.investmentSpent, 0.001)
        assertEquals(1500.0, report.investmentTarget, 0.001)
    }

    @Test
    fun `computeCategoryBudgetsFromTransactions accounts for pending transactions and recurring bills without duplication`() {
        val recurringBill = RecurringBill(
            id = "bill_rent",
            ownerUid = myUid,
            description = "Aluguel",
            category = "Moradia",
            defaultAmount = 1200.0,
            dueDay = 20,
            active = true
        )

        val txs = listOf(
            // Gasto já pago de alimentação
            Transaction(
                id = "tx_food",
                description = "Almoço Restaurante",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.DEBIT,
                totalAmount = 300.0,
                category = "Alimentação",
                dueDate = "2026-11-02",
                status = TransactionStatus.PAID,
                userImpact = mapOf(myUid to 300.0)
            ),
            // Compra pendente no cartão
            Transaction(
                id = "tx_food_pending",
                description = "Supermercado Semanal",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                totalAmount = 150.0,
                category = "Alimentação",
                dueDate = "2026-11-25",
                status = TransactionStatus.PENDING,
                userImpact = mapOf(myUid to 150.0)
            )
        )

        val ceilings = mapOf(
            "Alimentação" to 600.0,
            "Moradia" to 1500.0
        )

        val report = FinanceEngine.computeCategoryBudgetsFromTransactions(
            transactions = txs,
            categoryBudgets = ceilings,
            expectedIncome = 5000.0,
            recurringBills = listOf(recurringBill),
            uid = myUid,
            monthKey = "2026-11",
            referenceDate = "2026-11-10"
        )

        val foodItem = report.items.first { it.category == "Alimentação" }
        assertEquals(300.0, foodItem.spentAmount, 0.001)
        assertEquals(150.0, foodItem.pendingAmount, 0.001)
        assertEquals(450.0, foodItem.committedAmount, 0.001)
        assertEquals(150.0, foodItem.realRemaining, 0.001)

        val moradiaItem = report.items.first { it.category == "Moradia" }
        assertEquals(0.0, moradiaItem.spentAmount, 0.001)
        assertEquals(1200.0, moradiaItem.pendingAmount, 0.001) // from recurring bill
        assertEquals(1200.0, moradiaItem.committedAmount, 0.001)
        assertEquals(300.0, moradiaItem.realRemaining, 0.001)
    }

    @Test
    fun `no false overbudget when difference is under 0_01 cent`() {
        val txs = listOf(
            Transaction(
                id = "tx1",
                description = "Cinema",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                totalAmount = 500.00000001, // floating point rounding artifact
                category = "Lazer",
                status = TransactionStatus.PAID,
                userImpact = mapOf(myUid to 500.00000001)
            )
        )

        val report = FinanceEngine.computeCategoryBudgetsFromTransactions(
            transactions = txs,
            categoryBudgets = mapOf("Lazer" to 500.0),
            expectedIncome = 5000.0,
            recurringBills = emptyList(),
            uid = myUid,
            monthKey = "2026-11",
            referenceDate = "2026-11-20"
        )

        val lazer = report.items.first { it.category.startsWith("Lazer") }
        assertNotEquals(CategoryBudgetAlertStatus.DANGER, lazer.status)
        assertEquals("No Limite", lazer.badgeLabel)
    }

    @Test
    fun `suggestCategoryBudgets rounds ceilings to multiples of 10 without broken cents`() {
        val history = listOf(
            Transaction(
                id = "tx1",
                description = "Supermercado",
                competenceMonth = "2026-10",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.DEBIT,
                totalAmount = 338.38,
                category = "Alimentação",
                status = TransactionStatus.PAID,
                userImpact = mapOf(myUid to 338.38)
            ),
            Transaction(
                id = "tx2",
                description = "Combustível",
                competenceMonth = "2026-09",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.DEBIT,
                totalAmount = 499.08,
                category = "Transporte",
                status = TransactionStatus.PAID,
                userImpact = mapOf(myUid to 499.08)
            )
        )

        val result = FinanceEngine.suggestCategoryBudgets(
            allTransactions = history,
            uid = myUid,
            referenceMonth = "2026-11",
            monthlyIncome = 5000.0
        )

        for ((_, limit) in result.suggestedBudgets) {
            // Must be divisible by 10
            assertEquals("Teto $limit deve ser múltiplo de 10", 0.0, limit % 10.0, 0.001)
            assertEquals("Teto $limit não deve ter centavos quebrados", limit, kotlin.math.floor(limit), 0.001)
        }
    }

    @Test
    fun `calculateFinancialHealthScore returns strictly 0 for savings when totalInvested is 0 even with free balance`() {
        val summary = MonthSummary(
            income = 5000.0,
            paid = 2000.0,
            committed = 0.0,
            totalInvested = 0.0,
            freeBalance = 3000.0
        )
        val health = FinanceEngine.calculateFinancialHealthScore(
            summary = summary,
            emergencyReserveBalance = 0.0,
            budgetReport = null
        )

        assertEquals("Score de poupança deve ser 0 quando totalInvested for 0", 0, health.savingsScore)
        assertEquals(0, health.savingsRatePercentage)
    }

    @Test
    fun `computeCategoryBudgets assigns Previsto no Teto badge when spentAmount is 0 and pending is within ceiling`() {
        val txs = listOf(
            Transaction(
                id = "tx_pending",
                description = "Compras a vencer",
                competenceMonth = "2026-11",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                totalAmount = 366.58,
                category = "Compras & Estilo de Vida",
                dueDate = "2026-11-28",
                status = TransactionStatus.PENDING,
                visibleToUids = listOf(myUid),
                userImpact = mapOf(myUid to 366.58)
            )
        )
        val budgets = mapOf("Compras & Estilo de Vida" to 450.0)
        val report = FinanceEngine.computeCategoryBudgetsFromTransactions(
            transactions = txs,
            categoryBudgets = budgets,
            monthKey = "2026-11",
            referenceDate = "2026-11-05",
            uid = myUid,
            expectedIncome = 5000.0
        )

        val item = report.items.find { it.category == "Compras & Estilo de Vida" }
        assertNotNull(item)
        assertEquals(CategoryBudgetAlertStatus.OK, item!!.status)
        assertEquals("Previsto no Teto", item.badgeLabel)
        assertEquals(0.0, item.spentAmount, 0.01)
        assertEquals(366.58, item.pendingAmount, 0.01)
    }

    @Test
    fun `suggestCategoryBudgets applies 15-20 percent buffer rounded to multiples of 50 for variable categories`() {
        val txs = listOf(
            Transaction(
                id = "tx1",
                description = "Compras Setembro",
                competenceMonth = "2026-09",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                totalAmount = 366.58,
                category = "Compras & Estilo de Vida",
                dueDate = "2026-09-20",
                status = TransactionStatus.PAID,
                userImpact = mapOf(myUid to 366.58)
            )
        )
        val result = FinanceEngine.suggestCategoryBudgets(
            allTransactions = txs,
            uid = myUid,
            referenceMonth = "2026-11",
            monthlyIncome = 5000.0
        )

        val limit = result.suggestedBudgets["Compras & Estilo de Vida"]
        assertNotNull(limit)
        // 366.58 * 1.15 = 421.56 -> múltiplo de 50 = 450.0
        assertEquals(450.0, limit!!, 0.01)
        assertEquals(0.0, limit % 50.0, 0.001)
    }
}
