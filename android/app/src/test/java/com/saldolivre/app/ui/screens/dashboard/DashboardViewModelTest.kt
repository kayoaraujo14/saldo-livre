package com.saldolivre.app.ui.screens.dashboard

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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeFinanceRepository
    private lateinit var viewModel: DashboardViewModel

    private val myUid = "user_me"
    private val currentUser = UserProfile(
        uid = myUid,
        displayName = "Kayo",
        monthlyIncome = 6000.0
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeFinanceRepository(
            initialUser = currentUser,
            initialMonth = FinanceEngine.currentMonthKey()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState reflects repository current user and selected month`() = runTest {
        viewModel = DashboardViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(myUid, state.currentUser.uid)
        assertEquals("Kayo", state.currentUser.displayName)
        assertEquals(FinanceEngine.currentMonthKey(), state.selectedMonth)
    }

    @Test
    fun `income consumption calculates correct percentages and badges`() = runTest {
        // Cenário 1: 50% de consumo -> "No Limite"
        repository.summaryFlow.value = MonthSummary(
            income = 5000.0,
            paid = 2500.0
        )
        viewModel = DashboardViewModel(repository)
        var state = viewModel.uiState.value
        assertEquals(50, state.incomeConsumption.percentage)
        assertEquals("No Limite", state.incomeConsumption.statusBadge)

        // Cenário 2: 85% de consumo -> "Atenção"
        repository.summaryFlow.value = MonthSummary(
            income = 5000.0,
            paid = 4250.0
        )
        state = viewModel.uiState.value
        assertEquals(85, state.incomeConsumption.percentage)
        assertEquals("Atenção", state.incomeConsumption.statusBadge)

        // Cenário 3: 110% de consumo -> "Alerta"
        repository.summaryFlow.value = MonthSummary(
            income = 5000.0,
            paid = 5500.0
        )
        state = viewModel.uiState.value
        assertEquals(110, state.incomeConsumption.percentage)
        assertEquals("Alerta", state.incomeConsumption.statusBadge)
    }

    @Test
    fun `dismissClosureDialog hides popup and updates dismissed flag`() = runTest {
        repository.summaryFlow.value = MonthSummary(
            pendingClosureSurplus = 800.0
        )
        viewModel = DashboardViewModel(repository)

        assertTrue(viewModel.uiState.value.showClosureDialog)
        assertFalse(viewModel.uiState.value.closureDialogDismissed)

        viewModel.dismissClosureDialog()

        assertFalse(viewModel.uiState.value.showClosureDialog)
        assertTrue(viewModel.uiState.value.closureDialogDismissed)
    }

    @Test
    fun `radar 7 days aggregates upcoming unpaid non-card expenses within one week`() = runTest {
        val today = LocalDate.now()
        val in3Days = today.plusDays(3).toString()

        val upcomingTx = Transaction(
            id = "tx_upcoming",
            description = "Conta de Luz Enel",
            category = "Moradia",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.BILL,
            status = TransactionStatus.PENDING,
            estimatedAmount = 180.0,
            actualAmount = null,
            totalAmount = 180.0,
            dueDate = in3Days,
            competenceMonth = FinanceEngine.currentMonthKey(),
            createdByUid = myUid,
            paidByUid = myUid,
            visibleToUids = listOf(myUid)
        )

        repository.transactionsFlow.value = listOf(upcomingTx)
        viewModel = DashboardViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(1, state.radar7Days.count)
        assertEquals(180.0, state.radar7Days.totalAmount, 0.001)
        assertNotEquals("Sem pendências", state.radar7Days.urgencyBadge)
    }
}

