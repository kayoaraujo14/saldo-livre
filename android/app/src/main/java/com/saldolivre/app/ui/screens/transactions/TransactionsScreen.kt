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

                // Status: Previsão
                FilterChipItem(
                    label = "Previsões",
                    isSelected = uiState.statusFilter == TransactionStatus.ESTIMATED,
                    onClick = {
                        viewModel.setStatusFilter(
                            if (uiState.statusFilter == TransactionStatus.ESTIMATED) null else TransactionStatus.ESTIMATED
                        )
                    }
                )

                // Status: A Pagar
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
                    label = "Confirmados / Pagos (${uiState.paidCount})",
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
        var customActualInput by remember(tx.id, tx.effectiveAmount) {
            mutableStateOf(String.format(java.util.Locale.US, "%.2f", tx.effectiveAmount))
        }
        val isIncome = tx.type == TransactionType.INCOME

        AlertDialog(
            onDismissRequest = { selectedTxForDetails = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = tx.description,
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(label = "Valor Efetivo", value = FinanceEngine.formatCurrency(tx.effectiveAmount))
                    if (tx.estimatedAmount != null) {
                        DetailRow(label = "Valor Estimado", value = FinanceEngine.formatCurrency(tx.estimatedAmount))
                    }
                    DetailRow(label = "Categoria", value = tx.category + (tx.subcategory?.let { " › $it" } ?: ""))
                    DetailRow(label = "Vencimento", value = FinanceEngine.formatFullDate(tx.dueDate))
                    DetailRow(label = "Mês de Competência", value = FinanceEngine.formatMonthLabel(tx.competenceMonth))
                    DetailRow(label = "Forma de Pagamento", value = tx.paymentMethod.label)
                    tx.splitType?.let {
                        DetailRow(label = "Divisão", value = "${it.emoji} ${it.label}")
                    }

                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status Atual:",
                            style = Typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        StatusBadge(
                            status = tx.status,
                            isIncome = isIncome,
                            onClick = { viewModel.toggleStatus(tx.id) }
                        )
                    }

                    Text(
                        text = "Alternar Status (Paridade Web):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TransactionStatus.entries.forEach { st ->
                            val isSelected = tx.status == st
                            val chipLabel = when (st) {
                                TransactionStatus.ESTIMATED -> "Previsão"
                                TransactionStatus.PENDING -> if (isIncome) "A Receber" else "Pendente"
                                TransactionStatus.PAID -> if (isIncome) "Na Conta" else "Pago"
                            }
                            val activeColor = when (st) {
                                TransactionStatus.PAID -> Emerald500
                                TransactionStatus.PENDING -> Amber500
                                TransactionStatus.ESTIMATED -> Cyan500
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) activeColor.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                    .border(
                                        1.dp,
                                        if (isSelected) activeColor else DarkBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        val parsedAmount = customActualInput.replace(",", ".").toDoubleOrNull()
                                        viewModel.setStatus(tx.id, st, parsedAmount)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) activeColor else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (tx.status != TransactionStatus.PAID || tx.estimatedAmount != null) {
                        OutlinedTextField(
                            value = customActualInput,
                            onValueChange = { customActualInput = it },
                            label = { Text("Valor Real Efetivado (R$)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val targetId = tx.id
                            selectedTxForDetails = null
                            onNavigateToEditTransaction(targetId)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Cyan500.copy(alpha = 0.2f),
                            contentColor = Cyan400
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Editar",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    if (tx.status != TransactionStatus.PAID) {
                        Button(
                            onClick = {
                                val parsedAmount = customActualInput.replace(",", ".").toDoubleOrNull()
                                viewModel.setStatus(tx.id, TransactionStatus.PAID, parsedAmount)
                                selectedTxForDetails = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isIncome) "Confirmar" else "Confirmar Pago",
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                viewModel.setStatus(tx.id, TransactionStatus.PENDING)
                                selectedTxForDetails = null
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Amber500.copy(alpha = 0.2f),
                                contentColor = Amber400
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Voltar p/ Pendente",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        txToDelete = tx
                        selectedTxForDetails = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose400)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Excluir", maxLines = 1)
                }
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

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = Typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Text(
            text = value,
            style = Typography.labelLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
