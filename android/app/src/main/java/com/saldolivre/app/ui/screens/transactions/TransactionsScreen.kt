package com.saldolivre.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.components.DeleteInstallmentDialog
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.components.StatusBadge
import com.saldolivre.app.ui.components.TransactionDetailDialog
import com.saldolivre.app.ui.components.TransactionItem
import com.saldolivre.app.ui.theme.*

private val FilterChipShape = RoundedCornerShape(20.dp)
private val SelectedFilterChipBg = Emerald500.copy(alpha = 0.2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToEditTransaction: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTxForDetails by remember { mutableStateOf<Transaction?>(null) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }

    val formattedTotalIncome = remember(uiState.totalIncome) {
        "Entradas: ${FinanceEngine.formatCurrency(uiState.totalIncome)}"
    }
    val formattedTotalExpense = remember(uiState.totalExpense) {
        "Saídas: ${FinanceEngine.formatCurrency(uiState.totalExpense)}"
    }
    val isListEmpty by remember(uiState.filteredTransactions) {
        derivedStateOf { uiState.filteredTransactions.isEmpty() }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                title = {
                    Column {
                        Text(
                            text = "Extrato Financeiro",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Gestão de lançamentos, categorias e status",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAddTransaction) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Adicionar",
                            tint = Emerald400
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Navegação de Meses no topo do Extrato
            MonthSelector(
                selectedMonth = uiState.selectedMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() },
                onCurrentMonth = { viewModel.currentMonth() },
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = {
                    Text(
                        text = "Buscar por descrição, mercado, categoria...",
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpar",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All
                FilterChipItem(
                    label = "Todos",
                    isSelected = uiState.typeFilter == null && uiState.statusFilter == null,
                    onClick = {
                        viewModel.setTypeFilter(null)
                        viewModel.setStatusFilter(null)
                    }
                )

                // Expense
                FilterChipItem(
                    label = "Despesas",
                    isSelected = uiState.typeFilter == TransactionType.EXPENSE,
                    onClick = {
                        viewModel.setTypeFilter(
                            if (uiState.typeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE
                        )
                    }
                )

                // Income
                FilterChipItem(
                    label = "Receitas",
                    isSelected = uiState.typeFilter == TransactionType.INCOME,
                    onClick = {
                        viewModel.setTypeFilter(
                            if (uiState.typeFilter == TransactionType.INCOME) null else TransactionType.INCOME
                        )
                    }
                )

                // Status: Estimado
                FilterChipItem(
                    label = "Estimados",
                    isSelected = uiState.statusFilter == TransactionStatus.ESTIMATED,
                    onClick = {
                        viewModel.setStatusFilter(
                            if (uiState.statusFilter == TransactionStatus.ESTIMATED) null else TransactionStatus.ESTIMATED
                        )
                    }
                )

                // Status: Pendente
                FilterChipItem(
                    label = "Pendentes (${uiState.pendingCount})",
                    isSelected = uiState.statusFilter == TransactionStatus.PENDING,
                    onClick = {
                        viewModel.setStatusFilter(
                            if (uiState.statusFilter == TransactionStatus.PENDING) null else TransactionStatus.PENDING
                        )
                    }
                )

                // Status: Pago
                FilterChipItem(
                    label = "Pagos (${uiState.paidCount})",
                    isSelected = uiState.statusFilter == TransactionStatus.PAID,
                    onClick = {
                        viewModel.setStatusFilter(
                            if (uiState.statusFilter == TransactionStatus.PAID) null else TransactionStatus.PAID
                        )
                    }
                )
            }

            // Stats Subheader + Saldo Real & Confirmação Rápida
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.filteredTransactions.size} lançamentos • Toque no status ou deslize p/ confirmar",
                        style = Typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    Text(
                        text = "Saldo Livre: ${FinanceEngine.formatCurrency(uiState.freeBalance)}",
                        fontSize = 11.sp,
                        color = if (uiState.freeBalance >= 0) Emerald400 else Rose400,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTotalIncome,
                        fontSize = 11.sp,
                        color = Emerald400,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = formattedTotalExpense,
                        fontSize = 11.sp,
                        color = Rose400,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Pago: ${FinanceEngine.formatCurrency(uiState.totalPaidExpense)}",
                        fontSize = 11.sp,
                        color = Cyan400,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Pendente: ${FinanceEngine.formatCurrency(uiState.totalPendingExpense)}",
                        fontSize = 11.sp,
                        color = Amber400,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Transaction List
            if (isListEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "Nenhuma transação encontrada para este filtro.",
                        color = TextMuted,
                        style = Typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = uiState.filteredTransactions,
                        key = { it.id },
                        contentType = { "transaction_item" }
                    ) { tx ->
                        val onItemClick = remember(tx) { { selectedTxForDetails = tx } }
                        val onStatusToggle = remember(tx.id) { { viewModel.toggleStatus(tx.id) } }

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.toggleStatus(tx.id)
                                }
                                false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val willMarkPaid = tx.status != TransactionStatus.PAID
                                val bgColor = if (willMarkPaid) Emerald500.copy(alpha = 0.22f) else Amber500.copy(alpha = 0.22f)
                                val borderColor = if (willMarkPaid) Emerald500.copy(alpha = 0.5f) else Amber500.copy(alpha = 0.5f)
                                val contentColor = if (willMarkPaid) Emerald400 else Amber400
                                val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    Alignment.CenterEnd
                                } else {
                                    Alignment.CenterStart
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bgColor)
                                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 18.dp),
                                    contentAlignment = alignment
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (willMarkPaid) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = contentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = if (willMarkPaid) {
                                                if (tx.type == TransactionType.INCOME) "Confirmar Recebimento" else "Confirmar Pagamento"
                                            } else {
                                                "Marcar como Pendente"
                                            },
                                            color = contentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            },
                            content = {
                                TransactionItem(
                                    transaction = tx,
                                    onClick = onItemClick,
                                    onStatusClick = onStatusToggle
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail & Actions Dialog
    selectedTxForDetails?.let { initialTx ->
        val tx = uiState.transactions.find { it.id == initialTx.id } ?: initialTx
        TransactionDetailDialog(
            transaction = tx,
            onDismissRequest = { selectedTxForDetails = null },
            onToggleStatus = { viewModel.toggleStatus(tx.id) },
            onSetStatus = { status, actualAmount ->
                viewModel.setStatus(tx.id, status, actualAmount)
            },
            onNavigateToEditTransaction = { editTxId ->
                selectedTxForDetails = null
                onNavigateToEditTransaction(editTxId)
            },
            onDeleteRequest = {
                txToDelete = tx
                selectedTxForDetails = null
            }
        )
    }

    txToDelete?.let { tx ->
        if (tx.isInstallment && !tx.installmentGroupId.isNullOrBlank()) {
            DeleteInstallmentDialog(
                transaction = tx,
                allTransactions = uiState.transactions,
                onDismissRequest = { txToDelete = null },
                onDeleteSingle = { singleTx ->
                    viewModel.deleteTransaction(singleTx.id)
                    txToDelete = null
                },
                onDeleteAll = { groupId ->
                    viewModel.deleteInstallmentGroup(groupId)
                    txToDelete = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { txToDelete = null },
                containerColor = DarkSurface,
                title = {
                    Text(
                        text = "Excluir Lançamento",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Deseja realmente excluir \"${tx.description}\"?",
                            color = TextSecondary,
                            style = Typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTransaction(tx.id)
                            txToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                    ) {
                        Text(text = "Excluir", color = TextPrimary, maxLines = 1)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { txToDelete = null }) {
                        Text(text = "Cancelar", color = TextSecondary, maxLines = 1)
                    }
                }
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) SelectedFilterChipBg else DarkSurface
    val borderColor = if (isSelected) Emerald500 else DarkBorder
    val textColor = if (isSelected) Emerald400 else TextSecondary

    Box(
        modifier = Modifier
            .clip(FilterChipShape)
            .background(bgColor)
            .border(1.dp, borderColor, FilterChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1
        )
    }
}
