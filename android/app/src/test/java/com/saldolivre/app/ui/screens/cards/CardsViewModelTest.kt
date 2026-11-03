package com.saldolivre.app.ui.screens.cards

import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.testutil.FakeFinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeFinanceRepository
    private lateinit var viewModel: CardsViewModel

    private val myUid = "user_me"
    private val sampleCard = Card(
        id = "card_1",
        ownerUid = myUid,
        cardName = "Nubank Ultravioleta",
        color = "#820AD1",
        closingDay = 20,
        dueDay = 27,
        limit = 5000.0
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeFinanceRepository(
            initialUser = UserProfile(uid = myUid, displayName = "Kayo", monthlyIncome = 6000.0),
            initialMonth = "2026-10"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState computes card invoices, limits and usage percentage`() = runTest {
        val tx1 = Transaction(
            id = "tx1",
            createdByUid = myUid,
            paidByUid = myUid,
            cardId = sampleCard.id,
            description = "Supermercado",
            category = "Alimentação",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            status = TransactionStatus.PENDING,
            totalAmount = 500.0,
            actualAmount = 500.0,
            competenceMonth = "2026-10",
            visibleToUids = listOf(myUid)
        )

        val txFuture = Transaction(
            id = "tx_future",
            createdByUid = myUid,
            paidByUid = myUid,
            cardId = sampleCard.id,
            description = "Smartphone Parcelado 2/3",
            category = "Eletrônicos",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            status = TransactionStatus.PENDING,
            totalAmount = 300.0,
            actualAmount = 300.0,
            competenceMonth = "2026-11",
            visibleToUids = listOf(myUid)
        )

        repository.cardsFlow.value = listOf(sampleCard)
        repository.transactionsFlow.value = listOf(tx1, txFuture)

        viewModel = CardsViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(1, state.cardsWithInvoices.size)

        val cardInvoice = state.cardsWithInvoices.first()
        assertEquals(sampleCard.id, cardInvoice.card.id)
        assertTrue(cardInvoice.isOwner)
        assertEquals(500.0, cardInvoice.invoiceAmount, 0.001)
        assertEquals(300.0, cardInvoice.futureInvoicesAmount, 0.001)
        assertEquals(800.0, cardInvoice.totalCommitted, 0.001)
        assertEquals(5000.0, cardInvoice.limit, 0.001)
        assertEquals(4200.0, cardInvoice.availableLimit, 0.001)
        assertEquals(16, cardInvoice.usagePercentage)

        assertEquals(500.0, state.totalInvoicesAmount, 0.001)
        assertEquals(5000.0, state.totalLimitsAmount, 0.001)
        assertEquals(800.0, state.totalCommittedAmount, 0.001)
    }

    @Test
    fun `form toggles and card selection update uiState correctly`() = runTest {
        viewModel = CardsViewModel(repository)

        // Abrir modal de criação de cartão
        viewModel.openNewCard()
        assertTrue(viewModel.uiState.value.isFormOpen)
        assertNull(viewModel.uiState.value.editingCard)

        // Fechar formulário
        viewModel.closeForm()
        assertFalse(viewModel.uiState.value.isFormOpen)

        // Abrir para edição de cartão existente
        viewModel.editCard(sampleCard)
        assertTrue(viewModel.uiState.value.isFormOpen)
        assertEquals(sampleCard.id, viewModel.uiState.value.editingCard?.id)

        // Alternar detalhes do cartão
        viewModel.toggleCardDetails(sampleCard.id)
        assertEquals(sampleCard.id, viewModel.uiState.value.selectedCardDetailsId)

        // Fechar detalhes do cartão
        viewModel.toggleCardDetails(sampleCard.id)
        assertNull(viewModel.uiState.value.selectedCardDetailsId)
    }

    @Test
    fun `confirmDelete and dismissDelete manage confirmation state`() = runTest {
        viewModel = CardsViewModel(repository)

        viewModel.confirmDelete(sampleCard)
        assertEquals(sampleCard.id, viewModel.uiState.value.cardToDelete?.id)

        viewModel.dismissDelete()
        assertNull(viewModel.uiState.value.cardToDelete)
    }
}
