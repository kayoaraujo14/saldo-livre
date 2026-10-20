package com.saldolivre.app.ui.screens.import_statement

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.data.parser.ParsedStatementItem
import com.saldolivre.app.data.parser.StatementParser
import com.saldolivre.app.data.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ImportStatementUiState(
    val items: List<ParsedStatementItem> = emptyList(),
    val availableCards: List<Card> = emptyList(),
    val selectedCardId: String? = null,
    val isProcessing: Boolean = false,
    val hideIgnored: Boolean = true,
    val showPasteDialog: Boolean = false,
    val errorMessage: String? = null,
    val importSuccessCount: Int? = null
)

class ImportStatementViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportStatementUiState())
    val uiState: StateFlow<ImportStatementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getCards().collect { cards ->
                _uiState.update { current ->
                    current.copy(
                        availableCards = cards,
                        selectedCardId = current.selectedCardId ?: cards.firstOrNull()?.id
                    )
                }
            }
        }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            try {
                val currentMonth = repository.selectedMonth.value
                val parsed = withContext(Dispatchers.IO) {
                    StatementParser.parseFromUri(context, uri, currentMonth)
                }

                if (parsed.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Nenhuma transação foi identificada no arquivo selecionado. Verifique se o formato é suportado (PDF, OFX, CSV ou TXT)."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            items = parsed,
                            isProcessing = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Erro ao processar arquivo: ${e.localizedMessage ?: "Formato não reconhecido"}"
                    )
                }
            }
        }
    }

    fun onParseRawText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, showPasteDialog = false) }
            try {
                val currentMonth = repository.selectedMonth.value
                val parsed = withContext(Dispatchers.Default) {
                    StatementParser.parseFromRawText(text, currentMonth)
                }

                if (parsed.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Não foram encontrados lançamentos no texto inserido."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            items = parsed,
                            isProcessing = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Erro ao ler texto: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun toggleItemSelection(id: String) {
        _uiState.update { state ->
            val updated = state.items.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(items = updated)
        }
    }

    fun toggleSelectAll(select: Boolean) {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (state.hideIgnored && item.isIgnored) item else item.copy(isSelected = select)
            }
            state.copy(items = updated)
        }
    }

    fun setSelectedCard(cardId: String?) {
        _uiState.update { it.copy(selectedCardId = cardId) }
    }

    fun setHideIgnored(hide: Boolean) {
        _uiState.update { it.copy(hideIgnored = hide) }
    }

    fun setShowPasteDialog(show: Boolean) {
        _uiState.update { it.copy(showPasteDialog = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun confirmImport(onSuccess: (count: Int) -> Unit) {
        val currentState = _uiState.value
        val selectedItems = currentState.items.filter { it.isSelected }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val user = repository.currentUser.value
                val selectedCard = currentState.availableCards.find { it.id == currentState.selectedCardId }
                val defaultMonth = repository.selectedMonth.value

                val transactions = selectedItems.map { item ->
                    val isCard = item.paymentMethod == PaymentMethod.CREDIT_CARD
                    val (calcDueDate, calcCompetence) = if (isCard && selectedCard != null) {
                        FinanceEngine.calculateCardInvoiceDates(
                            purchaseDate = item.date,
                            card = selectedCard
                        )
                    } else {
                        val m = if (item.date.length >= 7) item.date.substring(0, 7) else defaultMonth
                        Pair(item.date, m)
                    }

                    val finalDescription = if (item.installmentNumber != null && item.installmentsTotal != null) {
                        FinanceEngine.formatInstallmentDesc(item.description, item.installmentNumber, item.installmentsTotal)
                    } else {
                        item.description
                    }

                    val status = if (isCard) {
                        TransactionStatus.PENDING
                    } else if (item.type == TransactionType.INCOME) {
                        TransactionStatus.PAID
                    } else {
                        TransactionStatus.PAID
                    }

                    Transaction(
                        id = "tx_" + UUID.randomUUID().toString().take(10),
                        createdByUid = user.uid,
                        paidByUid = user.uid,
                        cardId = if (isCard) currentState.selectedCardId else null,
                        description = finalDescription,
                        category = item.category,
                        subcategory = item.subcategory,
                        type = item.type,
                        paymentMethod = item.paymentMethod,
                        status = status,
                        actualAmount = item.amount,
                        totalAmount = item.amount,
                        dueDate = calcDueDate,
                        competenceMonth = calcCompetence,
                        visibleToUids = listOf(user.uid),
                        userImpact = mapOf(user.uid to item.amount),
                        splitType = SplitType.MINE,
                        installmentNumber = item.installmentNumber,
                        installmentsTotal = item.installmentsTotal,
                        createdAt = System.currentTimeMillis()
                    )
                }

                repository.addTransactions(transactions)

                repository.logActivity(
                    ActivityLog(
                        actionType = ActivityActionType.TRANSACTION_CREATED,
                        title = "Extrato Bancário Importado",
                        description = "Importados ${transactions.size} lançamentos a partir de extrato bancário",
                        amount = transactions.sumOf { it.effectiveAmount },
                        category = "Importação",
                        competenceMonth = defaultMonth,
                        userUid = user.uid,
                        userName = user.displayName
                    )
                )

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        items = emptyList(),
                        importSuccessCount = transactions.size
                    )
                }

                onSuccess(transactions.size)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Erro ao salvar transações: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
