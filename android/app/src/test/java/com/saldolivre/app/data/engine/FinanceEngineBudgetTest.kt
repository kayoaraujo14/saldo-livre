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

    @Test
    fun `sanitizeAndValidateBudgetPlan locks envelopes to spent floor and issues deficit warning if commitments exceed income`() {
        val rawPlan = SmartBudgetPlan(
            summaryInsight = "Plano com valores altos",
            allocations = listOf(
                SmartBudgetAllocation("Alimentação", 2500.0, "Comida boa"),
                SmartBudgetAllocation("Moradia", 2000.0, "Aluguel"),
                SmartBudgetAllocation("Lazer", 1000.0, "Diversão"),
                SmartBudgetAllocation("Investimentos", 1000.0, "Futuro")
            )
        )
        // Renda de R$ 3.000, mas já tem R$ 1.800 de moradia e R$ 1.400 de alimentação gastos (Total = R$ 3.200)
        val spent = mapOf("Alimentação" to 1400.0, "Moradia" to 1800.0)
        val validated = FinanceEngine.sanitizeAndValidateBudgetPlan(rawPlan, 3000.0, spent, emptyMap())

        assertTrue(validated.summaryInsight.contains("Atenção: Seus gastos e contas já comprometidos"))
        assertTrue(validated.summaryInsight.contains("excedem a renda disponível"))

        val food = validated.allocations.find { it.category == "Alimentação" }
        val rent = validated.allocations.find { it.category == "Moradia" }
        val leisure = validated.allocations.find { it.category == "Lazer & Entretenimento" || it.category == "Lazer" }
        val inv = validated.allocations.find { it.category == "Investimentos" }

        assertNotNull(food)
        assertNotNull(rent)
        assertNotNull(leisure)
        assertNotNull(inv)

        assertEquals(1400.0, food!!.suggestedLimit, 0.01)
        assertEquals(1800.0, rent!!.suggestedLimit, 0.01)
        assertEquals(0.0, leisure!!.suggestedLimit, 0.01)
        assertEquals(0.0, inv!!.suggestedLimit, 0.01)
    }

    @Test
    fun `sanitizeAndValidateBudgetPlan distributes flex within available income rounded to multiples of 10`() {
        val rawPlan = SmartBudgetPlan(
            summaryInsight = "Plano padrão",
            allocations = listOf(
                SmartBudgetAllocation("Alimentação", 1500.0, "Alimentação"),
                SmartBudgetAllocation("Transporte", 800.0, "Transporte"),
                SmartBudgetAllocation("Lazer", 700.0, "Lazer")
            )
        )
        val spent = mapOf("Alimentação" to 400.0, "Transporte" to 200.0)
        val validated = FinanceEngine.sanitizeAndValidateBudgetPlan(rawPlan, 2000.0, spent, emptyMap())

        val totalAllocated = validated.allocations.sumOf { it.suggestedLimit }
        assertTrue(totalAllocated <= 2000.0)

        for (a in validated.allocations) {
            assertEquals(0.0, a.suggestedLimit % 10.0, 0.001)
        }

        val food = validated.allocations.find { it.category == "Alimentação" }
        val trans = validated.allocations.find { it.category == "Transporte" }

        assertNotNull(food)
        assertNotNull(trans)
        assertTrue(food!!.suggestedLimit >= 400.0)
        assertTrue(trans!!.suggestedLimit >= 200.0)
    }

    @Test
    fun `calculateFinancialHealthScore considers carryoverAmount from previous month as effective available income in savings and spending ratios`() {
        val summary = MonthSummary(
            monthKey = "2026-11",
            income = 0.0,
            carryoverAmount = 5000.0,
            previousMonthDeficit = 0.0,
            committed = 2500.0,
            paid = 2500.0,
            committedEstimated = 0.0,
            committedPending = 0.0,
            freeBalance = 2500.0,
            totalInvested = 1000.0,
            byCategory = listOf(
                CategoryExpense("Alimentação", 1500.0),
                CategoryExpense("Investimentos", 1000.0)
            )
        )

        val result = FinanceEngine.calculateFinancialHealthScore(summary, 20000.0)

        assertEquals(30, result.spendingRatioPercentage)
        assertEquals(30, result.spendingLimitScore)
        assertEquals(20, result.savingsRatePercentage)
        assertEquals(30, result.savingsScore)
        assertTrue(result.score >= 80)
    }

    @Test
    fun `calculateFinancialHealthScore reduces effective available income when previousMonthDeficit is inherited`() {
        val summary = MonthSummary(
            monthKey = "2026-11",
            income = 3000.0,
            carryoverAmount = 0.0,
            previousMonthDeficit = 2500.0,
            committed = 1000.0,
            paid = 1000.0,
            committedEstimated = 0.0,
            committedPending = 0.0,
            freeBalance = -500.0,
            totalInvested = 0.0,
            byCategory = listOf(CategoryExpense("Alimentação", 1000.0))
        )

        val result = FinanceEngine.calculateFinancialHealthScore(summary, 0.0)

        assertEquals(200, result.spendingRatioPercentage)
        assertEquals(0, result.spendingLimitScore)
        assertEquals(FinancialHealthClassification.CRITICAL, result.classification)
    }

    @Test
    fun `sanitizeAndValidateBudgetPlan never suggests lower than committed minimum and repairs hallucinated reasons`() {
        val rawPlan = SmartBudgetPlan(
            summaryInsight = "Estratégia 50/30/20",
            allocations = listOf(
                SmartBudgetAllocation(
                    category = "Compras & Estilo de Vida",
                    suggestedLimit = 170.0,
                    reason = "Sem compromissos no momento, zerado para priorizar as metas de investimento."
                )
            )
        )
        val pending = mapOf("Compras" to 479.08)
        val validated = FinanceEngine.sanitizeAndValidateBudgetPlan(rawPlan, 5000.0, emptyMap(), pending)

        val compras = validated.allocations.find {
            it.category == "Compras & Estilo de Vida"
        }
        assertNotNull(compras)
        assertTrue(compras!!.suggestedLimit >= 480.0)
        assertFalse(compras.reason.contains("Sem compromissos", ignoreCase = true))
        assertTrue(compras.reason.contains("Garante", ignoreCase = true))
    }

    @Test
    fun `sanitizeAndValidateBudgetPlan automatically injects omitted categories that have commitments`() {
        val rawPlan = SmartBudgetPlan(
            summaryInsight = "Plano com omissão",
            allocations = listOf(
                SmartBudgetAllocation(
                    category = "Alimentação",
                    suggestedLimit = 1200.0,
                    reason = "Alimentação"
                )
            )
        )
        val spent = mapOf("Transporte" to 250.0)
        val pending = mapOf("Assinaturas" to 79.9)
        val validated = FinanceEngine.sanitizeAndValidateBudgetPlan(rawPlan, 4000.0, spent, pending)

        val trans = validated.allocations.find { it.category == "Transporte" }
        assertNotNull(trans)
        assertTrue(trans!!.suggestedLimit >= 250.0)

        val assinaturas = validated.allocations.find { it.category == "Assinaturas & Serviços" }
        assertNotNull(assinaturas)
        assertTrue(assinaturas!!.suggestedLimit >= 80.0)
        assertTrue(assinaturas.reason.contains("Garante", ignoreCase = true))
    }

    @Test
    fun `sanitizeAndValidateBudgetPlan deduplicates legacy aliases under canonical category`() {
        val rawPlan = SmartBudgetPlan(
            summaryInsight = "Plano com duplicatas por alias",
            allocations = listOf(
                SmartBudgetAllocation(category = "Compras", suggestedLimit = 200.0, reason = "Compras"),
                SmartBudgetAllocation(category = "Compras & Estilo de Vida", suggestedLimit = 300.0, reason = "Estilo de vida")
            )
        )
        val validated = FinanceEngine.sanitizeAndValidateBudgetPlan(rawPlan, 500.0, emptyMap(), emptyMap())

        val comprasAllocs = validated.allocations.filter {
            it.category == "Compras & Estilo de Vida" || it.category == "Compras"
        }
        assertEquals(1, comprasAllocs.size)
        assertEquals("Compras & Estilo de Vida", comprasAllocs[0].category)
        assertEquals(500.0, comprasAllocs[0].suggestedLimit, 0.01)
    }

    @Test
    fun `normalizeBudgetCategory normalizes legacy aliases and folded casing`() {
        assertEquals("Lazer & Entretenimento", FinanceEngine.normalizeBudgetCategory("Lazer"))
        assertEquals("Lazer & Entretenimento", FinanceEngine.normalizeBudgetCategory("lazer"))
        assertEquals("Compras & Estilo de Vida", FinanceEngine.normalizeBudgetCategory("Compras"))
        assertEquals("Compras & Estilo de Vida", FinanceEngine.normalizeBudgetCategory("compras"))
        assertEquals("Assinaturas & Serviços", FinanceEngine.normalizeBudgetCategory("Assinaturas"))
        assertEquals("Educação & Desenvolvimento", FinanceEngine.normalizeBudgetCategory("Educação"))
        assertEquals("Educação & Desenvolvimento", FinanceEngine.normalizeBudgetCategory("educacao"))
        assertEquals("Financiamentos & Dívidas", FinanceEngine.normalizeBudgetCategory("Financiamentos & Empréstimos"))
        assertEquals("Financiamentos & Dívidas", FinanceEngine.normalizeBudgetCategory("financiamentos e empréstimos"))
        assertEquals("Família & Dependentes", FinanceEngine.normalizeBudgetCategory("Família & Pets"))
        assertEquals("Pets", FinanceEngine.normalizeBudgetCategory("Pets"))
        assertEquals("Pets", FinanceEngine.normalizeBudgetCategory("pets"))
    }

    @Test
    fun `computeSummary calculates closure carryover and abates redemptions with parity to Web`() {
        val userUid = "user_abc"
        val monthKey = "2026-11"
        val prevMonthKey = "2026-10"

        val txs = listOf(
            Transaction(
                id = "tx_income_1",
                createdByUid = userUid,
                paidByUid = userUid,
                visibleToUids = listOf(userUid),
                description = "Salário",
                category = "Renda",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.PIX,
                status = TransactionStatus.PAID,
                totalAmount = 6921.15,
                dueDate = "2026-11-05",
                competenceMonth = monthKey,
                createdAt = 1762000000000L
            ),
            Transaction(
                id = "tx_expense_1",
                createdByUid = userUid,
                paidByUid = userUid,
                visibleToUids = listOf(userUid),
                description = "Aluguel & Contas",
                category = "Moradia",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.BILL,
                status = TransactionStatus.PAID,
                totalAmount = 6085.80,
                dueDate = "2026-11-10",
                competenceMonth = monthKey,
                createdAt = 1762000000000L
            ),
            Transaction(
                id = "tx_expense_pending",
                createdByUid = userUid,
                paidByUid = userUid,
                visibleToUids = listOf(userUid),
                description = "Financiamentos & Dívidas",
                category = "Financiamentos & Dívidas",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.BILL,
                status = TransactionStatus.PENDING,
                totalAmount = 1227.17,
                dueDate = "2026-11-20",
                competenceMonth = monthKey,
                createdAt = 1762000000000L
            ),
            Transaction(
                id = "tx_invest_1",
                createdByUid = userUid,
                paidByUid = userUid,
                visibleToUids = listOf(userUid),
                description = "Aporte Tesouro",
                category = "Investimentos",
                subcategory = "Tesouro Direto",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.PIX,
                status = TransactionStatus.PAID,
                totalAmount = 3549.55,
                dueDate = "2026-11-15",
                competenceMonth = monthKey,
                createdAt = 1762000000000L
            ),
            Transaction(
                id = "tx_invest_resgate",
                createdByUid = userUid,
                paidByUid = userUid,
                visibleToUids = listOf(userUid),
                description = "Resgate CDB",
                category = "Investimentos",
                subcategory = "Resgate",
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.PIX,
                status = TransactionStatus.PAID,
                totalAmount = 450.45,
                dueDate = "2026-11-18",
                competenceMonth = monthKey,
                createdAt = 1762000000000L
            )
        )

        // Closure com UID em branco ou nulo para validar fallback
        val closures = listOf(
            MonthClosure(
                id = "closure_oct",
                uid = "", // Testando fallback de paridade
                closedMonth = prevMonthKey,
                targetMonth = monthKey,
                totalSurplus = 927.24,
                carryoverToNextMonth = 927.24,
                investedAmount = 0.0,
                cushionAmount = 0.0,
                closedAt = 1761900000000L
            )
        )

        val summary = FinanceEngine.computeSummary(
            all = txs,
            uid = userUid,
            monthKey = monthKey,
            cards = emptyList(),
            closures = closures
        )

        assertEquals(927.24, summary.carryoverAmount, 0.01)
        // totalInvested should abate the resgate: 4000.00 - 450.45 = 3549.55
        assertEquals(3549.55, summary.totalInvested, 0.01)
        assertEquals(7371.6, summary.income, 0.01)
    }
}
