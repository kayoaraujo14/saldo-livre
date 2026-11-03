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
}
