package com.saldolivre.app.data.engine

import com.google.gson.JsonObject
import com.saldolivre.app.data.ai.AndroidAgentToolsEngine
import com.saldolivre.app.data.ai.LearnedRulesManager
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.testutil.FakeFinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class InstallmentLifecycleTest {

    private lateinit var repository: FakeFinanceRepository
    private lateinit var engine: AndroidAgentToolsEngine

    private val currentUser = UserProfile(
        uid = "user_test",
        displayName = "Usuário Teste",
        email = "usuario@teste.com"
    )

    private val dummyCard = Card(
        id = "card_nubank",
        ownerUid = currentUser.uid,
        cardName = "Nubank",
        dueDay = 10,
        closingDay = 3
    )

    private fun createInstallmentSeries(
        groupId: String,
        description: String,
        totalInstallments: Int,
        paidUntil: Int
    ): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val startYear = 2026
        val startMonth = 6
        val baseTime = 1762600000000L

        for (i in 1..totalInstallments) {
            val m = startMonth + i - 1
            val year = startYear + (m - 1) / 12
            val monthNum = ((m - 1) % 12) + 1
            val compMonth = String.format("%04d-%02d", year, monthNum)
            val isPaid = i <= paidUntil

            list.add(
                Transaction(
                    id = "tx_${groupId}_p$i",
                    createdByUid = currentUser.uid,
                    paidByUid = currentUser.uid,
                    description = description,
                    category = "Compras & Estilo de Vida",
                    subcategory = "Eletrônicos & Informática",
                    totalAmount = 200.0,
                    actualAmount = 200.0,
                    type = TransactionType.EXPENSE,
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    cardId = dummyCard.id,
                    competenceMonth = compMonth,
                    dueDate = "$compMonth-10",
                    status = if (isPaid) TransactionStatus.PAID else TransactionStatus.PENDING,
                    installmentNumber = i,
                    installmentsTotal = totalInstallments,
                    installmentGroupId = groupId,
                    visibleToUids = listOf(currentUser.uid),
                    createdAt = baseTime
                )
            )
        }
        return list
    }

    @Before
    fun setUp() = runBlocking {
        repository = FakeFinanceRepository()
        repository.cardsFlow.value = listOf(dummyCard)

        val classifier = NeuralFinanceClassifier(context = null)
        val rules = LearnedRulesManager(context = null, firestore = null)

        engine = AndroidAgentToolsEngine(
            repository = repository,
            neuralClassifier = classifier,
            rulesManager = rules,
            context = null
        )
    }

    @Test
    fun `InvoiceEngine anticipateInstallments antecipa parcelas finais e calcula desconto`() {
        val series = createInstallmentSeries("grp_celular", "iPhone 15", 10, 2)

        val result = InvoiceEngine.anticipateInstallments(
            transactions = series,
            groupId = "grp_celular",
            count = 3,
            targetMonth = "2026-11",
            discount = 45.0,
            todayIso = "2026-11-05",
            currentUserUid = currentUser.uid
        )

        assertEquals(3, result.updatedTransactions.size)
        val nums = result.updatedTransactions.mapNotNull { it.installmentNumber }
        assertTrue(nums.contains(10))
        assertTrue(nums.contains(9))
        assertTrue(nums.contains(8))

        result.updatedTransactions.forEach { tx ->
            assertEquals("2026-11", tx.competenceMonth)
            assertEquals(true, tx.isAnticipated)
            assertNotNull(tx.anticipatedFromMonth)
        }

        assertNotNull(result.discountTransaction)
        assertEquals(45.0, abs(result.discountTransaction?.actualAmount ?: 0.0), 0.001)
        assertEquals("2026-11", result.discountTransaction?.competenceMonth)
        assertEquals(600.0, result.totalAnticipatedAmount, 0.001)
    }

    @Test
    fun `FinanceEngine calculateFullInstallmentRefund gera estorno para pagas e exclusao para pendentes`() {
        val series = createInstallmentSeries("grp_curso", "Curso de Kotlin", 6, 2)

        val result = FinanceEngine.calculateFullInstallmentRefund(
            groupTxs = series,
            currentUser = currentUser,
            todayIso = "2026-11-05",
            targetCompetenceMonth = "2026-11"
        )

        assertEquals(400.0, result.totalPaidAmount, 0.001)
        assertEquals(4, result.toDeleteIds.size)
        assertTrue(result.toDeleteIds.contains("tx_grp_curso_p3"))
        assertTrue(result.toDeleteIds.contains("tx_grp_curso_p6"))

        assertNotNull(result.refundTransaction)
        assertEquals("2026-11", result.refundTransaction?.competenceMonth)
        assertTrue(result.refundTransaction?.description?.contains("Estorno Integral") == true)
        assertEquals(400.0, abs(result.refundTransaction?.actualAmount ?: 0.0), 0.001)
    }

    @Test
    fun `AndroidAgentToolsEngine ANTICIPATE_INSTALLMENTS antecipa com desconto via Json`() = runBlocking {
        val series = createInstallmentSeries("grp_tv", "Smart TV 4K", 8, 2)
        repository.addTransactions(series)

        val json = JsonObject().apply {
            addProperty("action", "ANTICIPATE_INSTALLMENTS")
            addProperty("searchTerm", "Smart TV 4K")
            addProperty("quantidadeParcelas", 3)
            addProperty("mesDestino", "2026-11")
            addProperty("desconto", 50.0)
            addProperty("confirmed", true)
        }

        val result = engine.executeFromJson(json)
        assertTrue(result.text.contains("parcelas antecipadas"))

        val all = repository.getTransactions().first()
        val anticipated = all.filter { it.isAnticipated == true }
        assertEquals(3, anticipated.size)
        anticipated.forEach {
            assertEquals("2026-11", it.competenceMonth)
        }

        val discountTx = all.find { (it.actualAmount ?: 0.0) < 0.0 && it.description.contains("Desconto por Antecipação") }
        assertNotNull(discountTx)
        assertEquals(50.0, abs(discountTx?.actualAmount ?: 0.0), 0.001)
    }

    @Test
    fun `AndroidAgentToolsEngine REFUND_FULL_INSTALLMENTS cancela pendentes e cria estorno via Json`() = runBlocking {
        val series = createInstallmentSeries("grp_viagem", "Pacote Viagem", 6, 2)
        repository.addTransactions(series)

        val json = JsonObject().apply {
            addProperty("action", "REFUND_FULL_INSTALLMENTS")
            addProperty("searchTerm", "Pacote Viagem")
            addProperty("mesDestino", "2026-11")
            addProperty("confirmed", true)
        }

        val result = engine.executeFromJson(json)
        assertTrue(result.text.contains("Estorno integral"))

        val all = repository.getTransactions().first()
        val originalPaid = all.filter { it.installmentGroupId == "grp_viagem" && (it.actualAmount ?: 0.0) > 0.0 }
        assertEquals(2, originalPaid.size)
        originalPaid.forEach {
            assertEquals(TransactionStatus.PAID, it.status)
        }

        val refundTx = all.find { (it.actualAmount ?: 0.0) < 0.0 && it.description.contains("Estorno Integral") }
        assertNotNull(refundTx)
        assertEquals(400.0, abs(refundTx?.actualAmount ?: 0.0), 0.001)
    }

    @Test
    fun `AndroidAgentToolsEngine DELETE_TRANSACTION com deleteFutureOnly cancela apenas futuras`() = runBlocking {
        val series = createInstallmentSeries("grp_academia", "Academia Anual", 6, 2)
        repository.addTransactions(series)

        val json = JsonObject().apply {
            addProperty("action", "DELETE_TRANSACTION")
            addProperty("searchTerm", "Academia Anual")
            addProperty("deleteFutureOnly", true)
            addProperty("confirmed", true)
        }

        val result = engine.executeFromJson(json)
        val all = repository.getTransactions().first()
        val remaining = all.filter { it.installmentGroupId == "grp_academia" }
        assertTrue(result.text.contains("Lançamento excluído com sucesso"))
        assertEquals(2, remaining.size)
        assertEquals(listOf(1, 2), remaining.mapNotNull { it.installmentNumber })
    }
}
