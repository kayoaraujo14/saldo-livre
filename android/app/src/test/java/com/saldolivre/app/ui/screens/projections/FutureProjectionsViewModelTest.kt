package com.saldolivre.app.ui.screens.projections

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
class FutureProjectionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeFinanceRepository
    private lateinit var viewModel: FutureProjectionsViewModel

    private val myUid = "user_kayo"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeFinanceRepository(
            initialUser = UserProfile(uid = myUid, displayName = "Kayo", monthlyIncome = 8000.0),
            initialMonth = FinanceEngine.currentMonthKey()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state projects default horizon of 12 months`() = runTest {
        viewModel = FutureProjectionsViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(12, state.horizonCount)
        assertEquals(12, state.projections.size)
        assertEquals(myUid, state.currentUser.uid)
        assertTrue(state.totalProjectedIncome > 0)
    }

    @Test
    fun `setHorizonCount switches between 6 and 12 months safely`() = runTest {
        viewModel = FutureProjectionsViewModel(repository)

        // Altera para 6 meses
        viewModel.setHorizonCount(6)
        assertEquals(6, viewModel.horizonCount.value)
        assertEquals(6, viewModel.uiState.value.horizonCount)
        assertEquals(6, viewModel.uiState.value.projections.size)

        // Tentar valor inválido não deve alterar
        viewModel.setHorizonCount(24)
        assertEquals(6, viewModel.horizonCount.value)

        // Volta para 12 meses
        viewModel.setHorizonCount(12)
        assertEquals(12, viewModel.horizonCount.value)
        assertEquals(12, viewModel.uiState.value.horizonCount)
        assertEquals(12, viewModel.uiState.value.projections.size)
    }

    @Test
    fun `selectMonth updates active month in repository and uiState message`() = runTest {
        viewModel = FutureProjectionsViewModel(repository)

        val targetMonth = "2027-03"
        viewModel.selectMonth(targetMonth)

        assertEquals(targetMonth, repository.selectedMonth.value)
        assertNotNull(viewModel.uiState.value.userMessage)
        assertTrue(viewModel.uiState.value.userMessage!!.contains("Mês ativo alterado"))
    }

    @Test
    fun `computes projections aggregating expenses and free balance`() = runTest {
        val curMonth = FinanceEngine.currentMonthKey()
        val nextMonth = FinanceEngine.shiftMonth(curMonth, 1)

        val fixedBill = RecurringBill(
            id = "bill_rent",
            ownerUid = myUid,
            description = "Aluguel",
            category = "Moradia",
            defaultAmount = 2000.0,
            dueDay = 10,
            active = true
        )

        val card = Card(
            id = "card_1",
            ownerUid = myUid,
            cardName = "Nubank",
            closingDay = 25,
            dueDay = 5
        )

        val futureInstallment = Transaction(
            id = "tx_parcela",
            createdByUid = myUid,
            paidByUid = myUid,
            cardId = card.id,
            description = "Curso 1/5",
            category = "Educação",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            status = TransactionStatus.PENDING,
            totalAmount = 500.0,
            actualAmount = 500.0,
            competenceMonth = nextMonth,
            visibleToUids = listOf(myUid),
            userImpact = mapOf(myUid to 500.0)
        )

        repository.billsFlow.value = listOf(fixedBill)
        repository.cardsFlow.value = listOf(card)
        repository.transactionsFlow.value = listOf(futureInstallment)

        viewModel = FutureProjectionsViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state.totalFixedBillsExpense > 0)
        assertTrue(state.totalProjectedExpense > 0)
        assertTrue(state.averageFreeBalance > 0)
    }
}
