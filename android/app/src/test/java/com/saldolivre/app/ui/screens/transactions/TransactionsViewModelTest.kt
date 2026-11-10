package com.saldolivre.app.ui.screens.transactions

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
class TransactionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeFinanceRepository
    private lateinit var viewModel: TransactionsViewModel

    private val myUid = "user_me"
    private val currentUser = UserProfile(
        uid = myUid,
        displayName = "Kayo",
        monthlyIncome = 6000.0
    )
    private val currentMonth = FinanceEngine.currentMonthKey()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeFinanceRepository(
            initialUser = currentUser,
            initialMonth = currentMonth
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteTransaction immediately removes transaction from state and sets userMessage`() = runTest {
        val tx1 = Transaction(
            id = "tx_1",
            createdByUid = myUid,
            description = "Supermercado",
            competenceMonth = currentMonth,
            dueDate = "$currentMonth-10",
            totalAmount = 150.0,
            status = TransactionStatus.PENDING,
            type = TransactionType.EXPENSE,
            category = "Alimentação"
        )
        val tx2 = Transaction(
            id = "tx_2",
            createdByUid = myUid,
            description = "Farmácia",
            competenceMonth = currentMonth,
            dueDate = "$currentMonth-11",
            totalAmount = 50.0,
            status = TransactionStatus.PENDING,
            type = TransactionType.EXPENSE,
            category = "Saúde"
        )
        repository.transactionsFlow.value = listOf(tx1, tx2)
        viewModel = TransactionsViewModel(repository)

        // Verifica estado inicial com 2 transações
        assertEquals(2, viewModel.uiState.value.transactions.size)
        assertEquals(2, viewModel.uiState.value.filteredTransactions.size)

        // Exclui tx_1
        viewModel.deleteTransaction("tx_1")

        val state = viewModel.uiState.value
        assertEquals("Lançamento excluído com sucesso.", state.userMessage)
        assertEquals(1, state.transactions.size)
        assertEquals(1, state.filteredTransactions.size)
        assertEquals("tx_2", state.transactions.first().id)
        assertEquals("tx_2", state.filteredTransactions.first().id)
        assertTrue(repository.transactionsFlow.value.none { it.id == "tx_1" })
    }

    @Test
    fun `deleteTransaction rolls back when repository throws error`() = runTest {
        val tx = Transaction(
            id = "tx_1",
            createdByUid = myUid,
            description = "Supermercado",
            competenceMonth = currentMonth,
            dueDate = "$currentMonth-10",
            totalAmount = 150.0,
            status = TransactionStatus.PENDING,
            type = TransactionType.EXPENSE,
            category = "Alimentação"
        )
        repository.transactionsFlow.value = listOf(tx)
        repository.shouldFailDelete = true
        viewModel = TransactionsViewModel(repository)

        viewModel.deleteTransaction("tx_1")

        val state = viewModel.uiState.value
        assertEquals("Não foi possível excluir o lançamento. Verifique suas permissões.", state.userMessage)
        assertEquals(1, state.transactions.size)
        assertEquals("tx_1", state.transactions.first().id)
    }

    @Test
    fun `deleteInstallmentGroup immediately removes all transactions of group`() = runTest {
        val groupId = "group_abc"
        val tx1 = Transaction(
            id = "tx_inst_1",
            createdByUid = myUid,
            installmentGroupId = groupId,
            installmentNumber = 1,
            installmentsTotal = 2,
            competenceMonth = currentMonth,
            dueDate = "$currentMonth-10"
        )
        val tx2 = Transaction(
            id = "tx_inst_2",
            createdByUid = myUid,
            installmentGroupId = groupId,
            installmentNumber = 2,
            installmentsTotal = 2,
            competenceMonth = currentMonth,
            dueDate = "$currentMonth-20"
        )
        repository.transactionsFlow.value = listOf(tx1, tx2)
        viewModel = TransactionsViewModel(repository)

        assertEquals(2, viewModel.uiState.value.transactions.size)

        viewModel.deleteInstallmentGroup(groupId)

        val state = viewModel.uiState.value
        assertEquals("Parcelamento excluído com sucesso.", state.userMessage)
        assertEquals(0, state.transactions.size)
        assertEquals(0, state.filteredTransactions.size)
    }
}
