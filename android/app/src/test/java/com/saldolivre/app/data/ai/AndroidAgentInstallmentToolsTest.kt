package com.saldolivre.app.data.ai

import com.google.gson.JsonArray
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
import org.junit.Assert.assertNull
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

    @Test
    fun `BATCH_TRANSACTIONS no debito com VA_VR cadastrado e mes selecionado dezembro grava DEBIT em novembro sem cartao`() = runBlocking {
        repository.setSelectedMonth("2026-12")
        repository.cardsFlow.value = listOf(
            Card(id = "card_vavr", ownerUid = "user_test", ownerName = "Usuário Teste", cardName = "Flash Vale", cardType = "va_vr", dueDay = 1, closingDay = 1),
            Card(id = "card_terceiro", ownerUid = "friend_terceiro", ownerName = "Lucas", cardName = "Nubank Terceiro", cardType = "credit", dueDay = 10, closingDay = 3)
        )

        val items = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("description", "99")
                addProperty("amount", 7.39)
                addProperty("type", "expense")
                addProperty("date", "2026-11-17")
                addProperty("competenceMonth", "2026-12")
            })
            add(JsonObject().apply {
                addProperty("description", "99")
                addProperty("amount", 7.14)
                addProperty("type", "expense")
                addProperty("date", "2026-11-17")
                addProperty("competenceMonth", "2026-12")
            })
            add(JsonObject().apply {
                addProperty("description", "Reembolso 99Food")
                addProperty("amount", 58.25)
                addProperty("type", "income")
                addProperty("date", "2026-11-16")
                addProperty("competenceMonth", "2026-12")
            })
            add(JsonObject().apply {
                addProperty("description", "Uber")
                addProperty("amount", 6.91)
                addProperty("type", "expense")
                addProperty("date", "2026-11-16")
                addProperty("competenceMonth", "2026-12")
            })
        }

        val json = JsonObject().apply {
            addProperty("action", "BATCH_TRANSACTIONS")
            addProperty("paymentMethod", "debit")
            addProperty("confirmed", true)
            add("transactions", items)
        }

        engine.executeFromJson(json)
        val txs = repository.getTransactions().first()
        assertEquals(4, txs.size)
        for (tx in txs) {
            assertEquals(PaymentMethod.DEBIT, tx.paymentMethod)
            assertNull(tx.cardId)
            assertEquals("2026-11", tx.competenceMonth)
        }
        val refund = txs.find { it.description == "Reembolso 99Food" }
        assertEquals(TransactionType.INCOME, refund?.type)
        assertEquals(PaymentMethod.DEBIT, refund?.paymentMethod)
    }

    @Test
    fun `BATCH_TRANSACTIONS sem forma de pagamento pede clarificacao e nao usa cartao de terceiro`() = runBlocking {
        repository.cardsFlow.value = listOf(
            Card(id = "card_terceiro", ownerUid = "friend_terceiro", ownerName = "Lucas", cardName = "Nubank Terceiro", cardType = "credit")
        )

        val items = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("description", "99")
                addProperty("amount", 7.39)
                addProperty("date", "2026-11-17")
            })
        }

        val json = JsonObject().apply {
            addProperty("action", "BATCH_TRANSACTIONS")
            add("transactions", items)
        }

        val result = engine.executeFromJson(json)
        val txs = repository.getTransactions().first()
        assertTrue(txs.isEmpty())
        assertNull(result.actionCard)
        assertTrue(result.text.lowercase().contains("pagamento") || result.text.lowercase().contains("débito"))
    }

    @Test
    fun `CREATE_TRANSACTION no credito com apenas cartao de terceiro sem especificar pede clarificacao`() = runBlocking {
        repository.cardsFlow.value = listOf(
            Card(id = "card_terceiro", ownerUid = "friend_terceiro", ownerName = "Lucas", cardName = "Nubank Terceiro", cardType = "credit")
        )

        val json = JsonObject().apply {
            addProperty("action", "CREATE_TRANSACTION")
            addProperty("description", "Mercado")
            addProperty("amount", 50.0)
            addProperty("paymentMethod", "credit_card")
        }

        val result = engine.executeFromJson(json)
        val txs = repository.getTransactions().first()
        assertTrue(txs.isEmpty())
        assertNull(result.actionCard)
    }

    @Test
    fun `EDIT_TRANSACTION corrige lote errado de VA_VR e cartao de terceiro para DEBIT atualizando duplicadas`() = runBlocking {
        repository.setSelectedMonth("2026-12")
        repository.addTransaction(
            Transaction(
                id = "tx_batch_1",
                description = "99",
                totalAmount = 7.39,
                actualAmount = 7.39,
                category = "Transporte",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.VA_VR,
                status = TransactionStatus.PAID,
                dueDate = "2026-11-17",
                competenceMonth = "2026-12",
                visibleToUids = listOf("user_test"),
                userImpact = mapOf("user_test" to 7.39),
                createdByUid = "user_test",
                paidByUid = "user_test",
                cardId = "card_vavr",
                createdAt = 1700000000000L
            )
        )
        repository.addTransaction(
            Transaction(
                id = "tx_batch_2",
                description = "99",
                totalAmount = 7.14,
                actualAmount = 7.14,
                category = "Transporte",
                type = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                status = TransactionStatus.PENDING,
                dueDate = "2026-11-17",
                competenceMonth = "2026-12",
                visibleToUids = listOf("user_test"),
                userImpact = mapOf("user_test" to 7.14),
                createdByUid = "user_test",
                paidByUid = "friend_terceiro",
                cardId = "card_terceiro",
                createdAt = 1700000001000L
            )
        )

        val json = JsonObject().apply {
            addProperty("action", "EDIT_TRANSACTION")
            addProperty("searchTerm", "99")
            addProperty("mesOrigem", "2026-11")
            addProperty("newPaymentMethod", "debit")
            addProperty("confirmed", true)
        }

        engine.executeFromJson(json)
        val txsAfter = repository.getTransactions().first()
        assertEquals(2, txsAfter.size)
        for (tx in txsAfter) {
            assertEquals(PaymentMethod.DEBIT, tx.paymentMethod)
            assertNull(tx.cardId)
            assertEquals("user_test", tx.paidByUid)
            assertEquals(TransactionStatus.PAID, tx.status)
            assertEquals("2026-11", tx.competenceMonth)
        }
    }
}
