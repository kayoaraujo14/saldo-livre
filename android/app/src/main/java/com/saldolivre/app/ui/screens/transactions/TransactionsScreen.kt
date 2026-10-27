package com.saldolivre.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.ui.components.DeleteInstallmentDialog
import com.saldolivre.app.ui.components.InvoiceDateGroup
import com.saldolivre.app.ui.components.InvoiceDateGroupHeader
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.components.TransactionDetailDialog
import com.saldolivre.app.ui.components.TransactionItem
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToEditTransaction: (String) -> Unit = {},
    onNavigateToRefundTransaction: (String) -> Unit = {},
    onNavigateToCards: (String?) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTxForDetails by remember { mutableStateOf<Transaction?>(null) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }

    val isListEmpty by remember(uiState.filteredTransactions) {
        derivedStateOf { uiState.filteredTransactions.isEmpty() }
    }

    val groupedTransactions = remember(uiState.filteredTransactions, uiState.currentUser.uid, uiState.transactions) {
        val map = LinkedHashMap<String, MutableList<Transaction>>()
        for (tx in uiState.filteredTransactions) {
            val key = tx.dueDate.ifBlank { "Sem data" }
            map.getOrPut(key) { mutableListOf() }.add(tx)
        }
        map.map { (dateKey, items) ->
            var dayExpenses = 0.0
            var dayIncomes = 0.0
            for (tx in items) {
                val impact = FinanceEngine.impactFor(tx, uiState.currentUser.uid, uiState.transactions)
                if (tx.type == com.saldolivre.app.data.model.TransactionType.INCOME || tx.isRefund) {
                    dayIncomes += kotlin.math.abs(impact)
                } else {
                    dayExpenses += kotlin.math.abs(impact)
                }
            }
            val isNetIncome = dayIncomes > dayExpenses
            val netAmount = if (isNetIncome) dayIncomes - dayExpenses else dayExpenses - dayIncomes
            InvoiceDateGroup(
                dateKey = dateKey,
                items = items,
                totalAmount = if (dayExpenses == 0.0 && dayIncomes > 0.0) dayIncomes else if (dayIncomes == 0.0) dayExpenses else netAmount,
                isIncome = isNetIncome
            )
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                title = {
                    Text(
                        text = "Extrato Financeiro",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleConsolidateInvoices() }) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = if (uiState.consolidateInvoices) "Faturas consolidadas ativadas" else "Lançamentos individuais de cartão",
                            tint = if (uiState.consolidateInvoices) Cyan400 else TextSecondary
                        )
                    }
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
            // Seletor de Mês no topo do Extrato
            MonthSelector(
                selectedMonth = uiState.selectedMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() },
                onCurrentMonth = { viewModel.currentMonth() },
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            // Search Bar + Botão de Filtros Estilo Excel (com respiro visual aprimorado)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = if (uiState.isFiltered) 4.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Buscar por descrição, pessoa, categoria...",
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar busca",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Botão de Filtros Avançados com Badge Flutuante e Ícone Perfeitamente Centralizado
                val hasActiveFilters = uiState.isFiltered
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (hasActiveFilters) Emerald500.copy(alpha = 0.15f) else DarkSurface)
                            .border(
                                1.dp,
                                if (hasActiveFilters) Emerald500.copy(alpha = 0.5f) else DarkBorder,
                                CircleShape
                            )
                            .clickable { showFilterBottomSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtros Avançados",
                            tint = if (hasActiveFilters) Emerald400 else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (uiState.activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(18.dp)
                                .background(Emerald500, CircleShape)
                                .border(1.5.dp, DarkBackground, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${uiState.activeFilterCount}",
                                color = DarkBackground,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeight = 10.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Exibição de filtros ativos (Consome espaço vertical APENAS se houver filtros ativos)
            if (uiState.isFiltered) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiState.selectedTypes.forEach { type ->
                        RemovableFilterPill(
                            label = if (type == com.saldolivre.app.data.model.TransactionType.INCOME) "Receitas" else "Despesas",
                            onRemove = { viewModel.toggleTypeFilter(type) }
                        )
                    }

                    uiState.selectedStatuses.forEach { status ->
                        RemovableFilterPill(
                            label = status.label,
                            onRemove = { viewModel.toggleStatusFilter(status) }
                        )
                    }

                    uiState.selectedPersons.forEach { person ->
                        RemovableFilterPill(
                            label = person,
                            onRemove = { viewModel.togglePersonFilter(person) }
                        )
                    }

                    uiState.selectedCategories.forEach { cat ->
                        val catDetail = CategoryRegistry.get(cat)
                        RemovableFilterPill(
                            label = cat,
                            icon = catDetail.icon,
                            iconTint = catDetail.color,
                            onRemove = { viewModel.toggleCategoryFilter(cat) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Rose500.copy(alpha = 0.12f))
                            .border(1.dp, Rose500.copy(alpha = 0.35f), CircleShape)
                            .clickable { viewModel.clearAllFilters() }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Rose400,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Limpar tudo",
                                fontSize = 11.sp,
                                color = Rose400,
                                fontWeight = FontWeight.SemiBold,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Lista de Lançamentos (Espaço máximo de tela aproveitado)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
                ) {
                    if (uiState.carryoverAmount > 0.0 || uiState.previousMonthDeficit > 0.0) {
                        item(key = "opening_balance_item", contentType = "opening_balance") {
                            val isDeficit = uiState.previousMonthDeficit > 0.0
                            val prevMonth = FinanceEngine.shiftMonth(uiState.selectedMonth, -1)
                            val prevMonthLabel = FinanceEngine.formatMonthLabel(prevMonth)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isDeficit) Rose500.copy(alpha = 0.12f)
                                        else Emerald500.copy(alpha = 0.12f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isDeficit) Rose500.copy(alpha = 0.35f)
                                        else Emerald500.copy(alpha = 0.35f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(
                                                    if (isDeficit) Rose500.copy(alpha = 0.2f)
                                                    else Emerald500.copy(alpha = 0.2f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isDeficit) Icons.Default.Warning else Icons.Default.AccountBalance,
                                                contentDescription = null,
                                                tint = if (isDeficit) Rose400 else Emerald400,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Text(
                                                text = if (isDeficit) "Déficit Anterior Acumulado" else "Saldo Inicial do Mês",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = if (isDeficit) "Débito herdado de $prevMonthLabel" else "Sobra de $prevMonthLabel",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (isDeficit) "- ${FinanceEngine.formatCurrency(uiState.previousMonthDeficit)}"
                                        else "+ ${FinanceEngine.formatCurrency(uiState.carryoverAmount)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDeficit) Rose400 else Emerald400
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.cushionAmount > 0.0) {
                        item(key = "cushion_balance_item", contentType = "cushion_balance") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Amber500.copy(alpha = 0.12f))
                                    .border(1.dp, Amber500.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(Amber500.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Shield,
                                                contentDescription = null,
                                                tint = Amber400,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Text(
                                                text = "Colchão de Proteção",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "Reserva blindada para imprevistos",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Text(
                                        text = FinanceEngine.formatCurrency(uiState.cushionAmount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Amber400
                                    )
                                }
                            }
                        }
                    }

                    groupedTransactions.forEach { group ->
                        item(key = "date_header_${group.dateKey}", contentType = "date_header") {
                            InvoiceDateGroupHeader(
                                dateKey = group.dateKey,
                                totalAmount = group.totalAmount,
                                isIncome = group.isIncome
                            )
                        }
                        items(
                            items = group.items,
                            key = { it.id },
                            contentType = { "transaction_item" }
                        ) { tx ->
                            val onItemClick = remember(tx) {
                                {
                                    if (tx.id.startsWith("invoice_")) {
                                        onNavigateToCards(tx.cardId)
                                    } else {
                                        selectedTxForDetails = tx
                                    }
                                }
                            }
                            val onStatusToggle = remember(tx.id) { { viewModel.toggleStatus(tx.id) } }

                            TransactionItem(
                                transaction = tx,
                                currentUserId = uiState.currentUser.uid,
                                allTransactions = uiState.transactions,
                                showDate = false,
                                onClick = onItemClick,
                                onStatusClick = onStatusToggle
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de Filtros Avançados Estilo Excel (BottomSheet)
    if (showFilterBottomSheet) {
        AdvancedFiltersBottomSheet(
            uiState = uiState,
            onDismissRequest = { showFilterBottomSheet = false },
            onToggleType = { viewModel.toggleTypeFilter(it) },
            onToggleStatus = { viewModel.toggleStatusFilter(it) },
            onToggleCategory = { viewModel.toggleCategoryFilter(it) },
            onTogglePerson = { viewModel.togglePersonFilter(it) },
            onClearAll = { viewModel.clearAllFilters() }
        )
    }

    // Modal de Detalhes e Ações do Lançamento
    selectedTxForDetails?.let { initialTx ->
        val tx = uiState.transactions.find { it.id == initialTx.id } ?: initialTx
        TransactionDetailDialog(
            transaction = tx,
            currentUserId = uiState.currentUser.uid,
            currentUser = uiState.currentUser,
            allTransactions = uiState.allTransactions.ifEmpty { uiState.transactions },
            onDismissRequest = { selectedTxForDetails = null },
            onToggleStatus = { viewModel.toggleStatus(tx.id) },
            onSetStatus = { status, actualAmount ->
                viewModel.setStatus(tx.id, status, actualAmount)
            },
            onNavigateToEditTransaction = { editTxId ->
                selectedTxForDetails = null
                onNavigateToEditTransaction(editTxId)
            },
            onNavigateToRefundTransaction = { refundTxId ->
                selectedTxForDetails = null
                onNavigateToRefundTransaction(refundTxId)
            },
            onConfirmRefund = { origTx, refundAmount ->
                viewModel.createRefund(origTx, refundAmount)
            },
            onDeleteRequest = {
                txToDelete = tx
                selectedTxForDetails = null
            }
        )
    }

    // Confirmação de Exclusão
    txToDelete?.let { tx ->
        if (tx.isInstallment && !tx.installmentGroupId.isNullOrBlank()) {
            DeleteInstallmentDialog(
                transaction = tx,
                allTransactions = uiState.allTransactions,
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
private fun RemovableFilterPill(
    label: String,
    onRemove: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color? = null
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Emerald500.copy(alpha = 0.12f))
            .border(1.dp, Emerald500.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onRemove)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: Emerald400,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Emerald400,
                style = LocalTextStyle.current.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remover",
                tint = Emerald400,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}
