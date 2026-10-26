package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.Allocation
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceEngineTest {

    private val myUid = "user_me"

    private val testCard = Card(
        id = "card_1",
        ownerUid = myUid,
        cardName = "Nubank",
        color = "violet",
        closingDay = 1,
        dueDay = 10,
        limit = 5000.0
    )

    private fun createExpense(
        id: String,
        description: String,
        category: String,
        amount: Double,
        splitType: SplitType = SplitType.MINE,
        assignedFriendName: String? = null,
        assignedFriendUid: String? = null,
        friendSharePct: Double? = null,
        externalName: String? = null,
        allocations: List<Allocation> = emptyList(),
        userImpact: Map<String, Double> = emptyMap()
    ): Transaction {
        return Transaction(
            id = id,
            createdByUid = myUid,
            paidByUid = myUid,
            cardId = testCard.id,
            description = description,
            category = category,
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            status = TransactionStatus.PENDING,
            totalAmount = amount,
            actualAmount = amount,
            dueDate = "2026-10-10",
            competenceMonth = "2026-10",
            visibleToUids = listOf(myUid),
            splitType = splitType,
            assignedFriendName = assignedFriendName,
            assignedFriendUid = assignedFriendUid,
            friendSharePct = friendSharePct,
            externalName = externalName,
            allocations = allocations,
            userImpact = userImpact
        )
    }

    @Test
    fun `split 50 category breakdown and participant amounts do not exceed total`() {
        val tx1 = createExpense(
            id = "tx_1",
            description = "Supermercado Semanal",
            category = "Alimentação",
            amount = 100.0,
            splitType = SplitType.SPLIT_50
        )

        val invoice = InvoiceEngine.buildInvoice(testCard, "2026-10", listOf(tx1), myUid)

        // Fatura total e categoria geral
        assertEquals(100.0, invoice.total, 0.001)
        val generalSlice = invoice.byCategory.find { it.category == "Alimentação" }
        assertNotNull(generalSlice)
        assertEquals(100.0, generalSlice!!.amount, 0.001)

        // Participantes no rateio
        val mePart = invoice.splitBreakdown.find { it.isMe }
        assertNotNull(mePart)
        assertEquals(50.0, mePart!!.amount, 0.001)

        val partnerPart = invoice.splitBreakdown.find { !it.isMe }
        assertNotNull(partnerPart)
        assertEquals("Parceiro(a)", partnerPart!!.name)
        assertEquals(50.0, partnerPart.amount, 0.001)

        // Valores por lançamento
        val meAmountInTx = InvoiceEngine.participantAmountIn(tx1, mePart, myUid)
        val partnerAmountInTx = InvoiceEngine.participantAmountIn(tx1, partnerPart, myUid)

        assertEquals(50.0, meAmountInTx, 0.001)
        assertEquals(50.0, partnerAmountInTx, 0.001)
        assertEquals(100.0, meAmountInTx + partnerAmountInTx, 0.001)

        // Categorias por participante
        val meBreakdown = InvoiceEngine.calculateCategoryBreakdownForParticipant(
            listOf(tx1),
            mePart,
            myUid
        )
        val partnerBreakdown = InvoiceEngine.calculateCategoryBreakdownForParticipant(
            listOf(tx1),
            partnerPart,
            myUid
        )

        val meCatAmount = meBreakdown.find { it.category == "Alimentação" }?.amount ?: 0.0
        val partnerCatAmount = partnerBreakdown.find { it.category == "Alimentação" }?.amount ?: 0.0

        assertEquals(50.0, meCatAmount, 0.001)
        assertEquals(50.0, partnerCatAmount, 0.001)
        assertEquals(generalSlice.amount, meCatAmount + partnerCatAmount, 0.001)
    }

    @Test
    fun `friend split with percentage allocates correct share and sums to category total`() {
        val tx = createExpense(
            id = "tx_2",
            description = "Jantar com Amigo",
            category = "Restaurante",
            amount = 200.0,
            splitType = SplitType.FRIEND,
            assignedFriendName = "Lucas",
            friendSharePct = 40.0
        )

        val invoice = InvoiceEngine.buildInvoice(testCard, "2026-10", listOf(tx), myUid)

        val generalRestaurante = invoice.byCategory.find { it.category == "Restaurante" }?.amount ?: 0.0
        assertEquals(200.0, generalRestaurante, 0.001)

        val mePart = invoice.splitBreakdown.find { it.isMe }!!
        val lucasPart = invoice.splitBreakdown.find { it.name == "Lucas" }!!

        assertEquals(120.0, mePart.amount, 0.001)
        assertEquals(80.0, lucasPart.amount, 0.001)

        // Verifica que o lançamento individual não mostra o total de 200 para Lucas
        val meShare = InvoiceEngine.participantAmountIn(tx, mePart, myUid)
        val lucasShare = InvoiceEngine.participantAmountIn(tx, lucasPart, myUid)
        assertEquals(120.0, meShare, 0.001)
        assertEquals(80.0, lucasShare, 0.001)

        // Categorias
        val meCats = InvoiceEngine.calculateCategoryBreakdownForParticipant(listOf(tx), mePart, myUid)
        val lucasCats = InvoiceEngine.calculateCategoryBreakdownForParticipant(listOf(tx), lucasPart, myUid)

        val meCatSum = meCats.find { it.category == "Restaurante" }?.amount ?: 0.0
        val lucasCatSum = lucasCats.find { it.category == "Restaurante" }?.amount ?: 0.0

        assertEquals(120.0, meCatSum, 0.001)
        assertEquals(80.0, lucasCatSum, 0.001)
        assertEquals(generalRestaurante, meCatSum + lucasCatSum, 0.001)
    }

    @Test
    fun `combination of multiple rateado transactions sum to exact category totals`() {
        // 1. Despesa 100% minha em Alimentação: R$ 80
        val tx1 = createExpense("t1", "Padaria", "Alimentação", 80.0, splitType = SplitType.MINE)
        // 2. Despesa rateada 50/50 em Alimentação com Parceiro: R$ 100 (50 meu, 50 parceiro)
        val tx2 = createExpense("t2", "Açougue", "Alimentação", 100.0, splitType = SplitType.SPLIT_50)
        // 3. Despesa rateada com Amigo em Alimentação: R$ 60 (30 meu, 30 amigo)
        val tx3 = createExpense("t3", "Almoço", "Alimentação", 60.0, splitType = SplitType.FRIEND, assignedFriendName = "Bruno")

        val expenses = listOf(tx1, tx2, tx3)
        val invoice = InvoiceEngine.buildInvoice(testCard, "2026-10", expenses, myUid)

        // Total geral de Alimentação: 80 + 100 + 60 = 240
        val generalAlimentacao = invoice.byCategory.find { it.category == "Alimentação" }?.amount ?: 0.0
        assertEquals(240.0, generalAlimentacao, 0.001)

        var sumCategoryAcrossAllParticipants = 0.0

        for (part in invoice.splitBreakdown) {
            val partCats = InvoiceEngine.calculateCategoryBreakdownForParticipant(expenses, part, myUid)
            val catAmt = partCats.find { it.category == "Alimentação" }?.amount ?: 0.0
            sumCategoryAcrossAllParticipants += catAmt

            // Garante que cada transação reporta apenas a cota referente a este participante
            for (tx in expenses) {
                val share = InvoiceEngine.participantAmountIn(tx, part, myUid)
                assertTrue("Cota do participante não pode exceder o valor da despesa", share <= tx.effectiveAmount)
            }
        }

        // A soma das categorias filtradas uma por uma deve ser EXATAMENTE o total geral
        assertEquals(generalAlimentacao, sumCategoryAcrossAllParticipants, 0.001)
    }

    @Test
    fun `odd cent rounding across split transactions reconciles exactly to general category total without 2 cents surplus`() {
        // Simulação do caso real do usuário:
        // Transações de valores ímpares com divisão onde as alocações arredondariam para cima gerando 2 centavos excedentes.
        val t1 = createExpense(
            id = "tx_odd_1",
            description = "Mercado 1",
            category = "Alimentação",
            amount = 55.45,
            splitType = SplitType.CUSTOM,
            allocations = listOf(
                Allocation(type = "PLATFORM_USER", uid = myUid, name = "Você", amount = 27.73),
                Allocation(type = "EXTERNAL", uid = null, name = "Mãe", amount = 27.73)
            )
        )
        val t2 = createExpense(
            id = "tx_odd_2",
            description = "Mercado 2",
            category = "Alimentação",
            amount = 99.99,
            splitType = SplitType.CUSTOM,
            allocations = listOf(
                Allocation(type = "PLATFORM_USER", uid = myUid, name = "Você", amount = 50.00),
                Allocation(type = "EXTERNAL", uid = null, name = "Kayo Araujo", amount = 50.00)
            )
        )
        val t3 = createExpense(
            id = "tx_base",
            description = "Mercado Geral",
            category = "Alimentação",
            amount = 943.58,
            splitType = SplitType.MINE
        )

        val expenses = listOf(t1, t2, t3)
        val invoice = InvoiceEngine.buildInvoice(testCard, "2026-10", expenses, myUid)

        // Total geral de Alimentação: 55.45 + 99.99 + 943.58 = 1099.02
        val generalAlimentacao = invoice.byCategory.find { it.category == "Alimentação" }?.amount ?: 0.0
        assertEquals(1099.02, generalAlimentacao, 0.0001)

        var sumCategoryAcrossAllParticipants = 0.0
        for (part in invoice.splitBreakdown) {
            val partCats = InvoiceEngine.calculateCategoryBreakdownForParticipant(expenses, part, myUid)
            val catAmt = partCats.find { it.category == "Alimentação" }?.amount ?: 0.0
            sumCategoryAcrossAllParticipants += catAmt
        }

        // Deve bater EXATAMENTE 1099.02 (e NÃO 1099.04)
        assertEquals(1099.02, sumCategoryAcrossAllParticipants, 0.0001)
    }

    @Test
    fun `test formatNubankDateHeader for today, yesterday and other dates`() {
        val baseDate = java.time.LocalDate.of(2026, 10, 26) // Segunda-feira

        val todayHeader = FinanceEngine.formatNubankDateHeader("2026-10-26", today = baseDate)
        assertEquals("HOJE", todayHeader.title)
        assertEquals("26 OUT", todayHeader.subtitle)
        assertTrue(todayHeader.isToday)

        val yesterdayHeader = FinanceEngine.formatNubankDateHeader("2026-10-25", today = baseDate)
        assertEquals("ONTEM", yesterdayHeader.title)
        assertEquals("25 OUT", yesterdayHeader.subtitle)
        assertTrue(yesterdayHeader.isYesterday)

        val pastHeader = FinanceEngine.formatNubankDateHeader("2026-10-24", today = baseDate)
        assertEquals("24 OUT", pastHeader.title)
        assertEquals("sábado", pastHeader.subtitle)

        val pastYearHeader = FinanceEngine.formatNubankDateHeader("2025-12-15", today = baseDate)
        assertEquals("15 DEZ 2025", pastYearHeader.title)
        assertEquals("segunda-feira", pastYearHeader.subtitle)
    }
}
