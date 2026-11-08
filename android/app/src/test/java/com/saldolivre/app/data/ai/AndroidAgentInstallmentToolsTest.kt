package com.saldolivre.app.data.ai

import com.google.gson.JsonObject
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.testutil.FakeFinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidAgentInstallmentToolsTest {

    private lateinit var repository: FakeFinanceRepository
    private lateinit var engine: AndroidAgentToolsEngine

    private val dummyCard = Card(
        id = "card_nubank",
        ownerUid = "user_test",
        cardName = "Nubank",
        dueDay = 10,
        closingDay = 3
    )

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
    fun `registrarTransacao com valorTotal divide o valor entre as parcelas e absorve no total`() = runBlocking {
        val json = JsonObject().apply {
            addProperty("action", "registrarTransacao")
            addProperty("descricao", "Smartphone Samsung")
            addProperty("valorTotal", 1200.0)
            addProperty("totalParcelas", 10)
            addProperty("parcelaAtual", 1)
            addProperty("categoria", "Eletrônicos")
            addProperty("metodoPagamento", "credit_card")
            addProperty("cartao", "Nubank")
            addProperty("confirmed", true)
        }

        val result = engine.executeFromJson(json)
        assertTrue(result.text.contains("registrado") || result.text.contains("criada") || result.text.contains("sucesso") || result.actionCard != null)

        val txs = repository.getTransactions().first()
        assertEquals(10, txs.size)

        for (tx in txs) {
            assertEquals(120.0, tx.totalAmount, 0.001)
        }

        val totalSum = txs.sumOf { it.totalAmount }
        assertEquals(1200.0, totalSum, 0.001)
    }

    @Test
    fun `registrarTransacao com valorParcela preserva o valor unitario informado`() = runBlocking {
        val json = JsonObject().apply {
            addProperty("action", "registrarTransacao")
            addProperty("descricao", "Curso de Design")
            addProperty("valorParcela", 150.0)
            addProperty("totalParcelas", 4)
            addProperty("parcelaAtual", 1)
            addProperty("categoria", "Educação")
            addProperty("metodoPagamento", "credit_card")
            addProperty("cartao", "Nubank")
            addProperty("confirmed", true)
        }

        val result = engine.executeFromJson(json)
        val txs = repository.getTransactions().first()
        assertEquals(4, txs.size)

        for (tx in txs) {
            assertEquals(150.0, tx.totalAmount, 0.001)
        }

        val totalSum = txs.sumOf { it.totalAmount }
        assertEquals(600.0, totalSum, 0.001)
    }

    @Test
    fun `registrarTransacao com valorTotal 100 em 3x absorve residuo de centavos`() = runBlocking {
        val json = JsonObject().apply {
            addProperty("action", "registrarTransacao")
            addProperty("descricao", "Fone Bluetooth")
            addProperty("valorTotal", 100.0)
            addProperty("totalParcelas", 3)
            addProperty("parcelaAtual", 1)
            addProperty("categoria", "Eletrônicos")
            addProperty("metodoPagamento", "credit_card")
            addProperty("cartao", "Nubank")
            addProperty("confirmed", true)
        }

        engine.executeFromJson(json)
        val txs = repository.getTransactions().first()
        assertEquals(3, txs.size)

        assertEquals(33.33, txs[0].totalAmount, 0.001)
        assertEquals(33.33, txs[1].totalAmount, 0.001)
        assertEquals(33.34, txs[2].totalAmount, 0.001)

        val totalSum = txs.sumOf { it.totalAmount }
        assertEquals(100.0, totalSum, 0.001)
    }

    @Test
    fun `excluirTransacoes com deleteAllInstallments = true exclui todas as parcelas do grupo`() = runBlocking {
        val groupId = "inst_grp_del_all"
        for (i in 1..3) {
            repository.addTransaction(
                Transaction(
                    id = "tx_$i",
                    description = "Notebook Dell ($i/3)",
                    totalAmount = 1000.0,
                    actualAmount = 1000.0,
                    category = "Eletrônicos",
                    type = TransactionType.EXPENSE,
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    status = if (i == 1) TransactionStatus.PAID else TransactionStatus.PENDING,
                    dueDate = "2026-1$i-10",
                    competenceMonth = "2026-1$i",
                    visibleToUids = listOf("user_test"),
                    userImpact = mapOf("user_test" to 1000.0),
                    installmentGroupId = groupId,
                    installmentNumber = i,
                    installmentsTotal = 3,
                    createdByUid = "user_test",
                    paidByUid = "user_test",
                    cardId = "card_nubank",
                    createdAt = System.currentTimeMillis() + i
                )
            )
        }

        assertEquals(3, repository.getTransactions().first().size)

        val json = JsonObject().apply {
            addProperty("action", "excluirTransacoes")
            addProperty("termoBusca", "Notebook Dell")
            addProperty("deleteAllInstallments", true)
            addProperty("confirmed", true)
        }

        val result = engine.executeFromJson(json)
        val txsAfter = repository.getTransactions().first()
        assertEquals(0, txsAfter.size)
    }

    @Test
    fun `excluirTransacoes com deleteAllInstallments = false exclui apenas uma parcela`() = runBlocking {
        val groupId = "inst_grp_del_single"
        for (i in 1..3) {
            repository.addTransaction(
                Transaction(
                    id = "tx_$i",
                    description = "Notebook Dell ($i/3)",
                    totalAmount = 1000.0,
                    actualAmount = 1000.0,
                    category = "Eletrônicos",
                    type = TransactionType.EXPENSE,
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    status = TransactionStatus.PENDING,
                    dueDate = "2026-1$i-10",
                    competenceMonth = "2026-1$i",
                    visibleToUids = listOf("user_test"),
                    userImpact = mapOf("user_test" to 1000.0),
                    installmentGroupId = groupId,
                    installmentNumber = i,
                    installmentsTotal = 3,
                    createdByUid = "user_test",
                    paidByUid = "user_test",
                    cardId = "card_nubank",
                    createdAt = System.currentTimeMillis() + i
                )
            )
        }

        val json = JsonObject().apply {
            addProperty("action", "excluirTransacoes")
            addProperty("termoBusca", "Notebook Dell")
            addProperty("deleteAllInstallments", false)
            addProperty("confirmed", true)
        }

        engine.executeFromJson(json)
        val txsAfter = repository.getTransactions().first()
        assertEquals(2, txsAfter.size)
    }

    @Test
    fun `editarTransacoes com nova data nao altera a data dos irmaos do grupo`() = runBlocking {
        val groupId = "inst_grp_date_protect"
        for (i in 1..3) {
            repository.addTransaction(
                Transaction(
                    id = "tx_$i",
                    description = "Notebook Dell ($i/3)",
                    totalAmount = 1000.0,
                    actualAmount = 1000.0,
                    category = "Eletrônicos",
                    type = TransactionType.EXPENSE,
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    status = TransactionStatus.PENDING,
                    dueDate = "2026-1$i-10",
                    competenceMonth = "2026-1$i",
                    visibleToUids = listOf("user_test"),
                    userImpact = mapOf("user_test" to 1000.0),
                    installmentGroupId = groupId,
                    installmentNumber = i,
                    installmentsTotal = 3,
                    createdByUid = "user_test",
                    paidByUid = "user_test",
                    cardId = "card_nubank",
                    createdAt = System.currentTimeMillis() + i
                )
            )
        }

        val json = JsonObject().apply {
            addProperty("action", "editarTransacoes")
            addProperty("transactionId", "tx_1")
            addProperty("newDueDate", "2026-11-20")
            addProperty("confirmed", true)
        }

        engine.executeFromJson(json)
        val txsAfter = repository.getTransactions().first()

        val p1 = txsAfter.find { it.id == "tx_1" }
        val p2 = txsAfter.find { it.id == "tx_2" }
        val p3 = txsAfter.find { it.id == "tx_3" }

        assertEquals("2026-11-20", p1?.dueDate)
        assertEquals("2026-12-10", p2?.dueDate)
        assertEquals("2026-13-10", p3?.dueDate)
    }
}
