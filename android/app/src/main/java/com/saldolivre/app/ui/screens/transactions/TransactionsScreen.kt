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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.components.StatusBadge
import com.saldolivre.app.ui.components.TransactionItem
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddTransaction: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTxForDetails by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Extrato Financeiro",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = FinanceEngine.formatMonthLabel(uiState.selectedMonth),
                            fontSize = 11.sp,
                            color = TextMuted
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
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = {
                    Text(text = "Buscar por descrição, mercado, categoria...", color = TextMuted)
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
                    label = "A Pagar",
                    isSelected = uiState.statusFilter == TransactionStatus.PENDING,
                    onClick = {
                        viewModel.setStatusFilter(
                            if (uiState.statusFilter == TransactionStatus.PENDING) null else TransactionStatus.PENDING
                        )
                    }
                )

                // Status: Pago
                FilterChipItem(
                    label = "Pagos",
                    isSelected = uiState.statusFilter == TransactionStatus.PAID,
                    onClick = {
                        viewModel.setStatusFilter(
                            if (uiState.statusFilter == TransactionStatus.PAID) null else TransactionStatus.PAID
                        )
                    }
                )
            }

            // Stats Subheader
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.filteredTransactions.size} lançamentos",
                    style = Typography.labelLarge,
                    color = TextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Entradas: ${FinanceEngine.formatCurrency(uiState.totalIncome)}",
                        fontSize = 12.sp,
                        color = Emerald400,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Saídas: ${FinanceEngine.formatCurrency(uiState.totalExpense)}",
                        fontSize = 12.sp,
                        color = Rose400,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Transaction List
            if (uiState.filteredTransactions.isEmpty()) {
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
                    items(uiState.filteredTransactions) { tx ->
                        TransactionItem(
                            transaction = tx,
                            onClick = { selectedTxForDetails = tx }
                        )
                    }
                }
            }
        }
    }

    // Detail & Actions Dialog
    selectedTxForDetails?.let { tx ->
        AlertDialog(
            onDismissRequest = { selectedTxForDetails = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = tx.description,
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(label = "Valor Efetivo", value = FinanceEngine.formatCurrency(tx.effectiveAmount))
                    if (tx.estimatedAmount != null) {
                        DetailRow(label = "Valor Estimado", value = FinanceEngine.formatCurrency(tx.estimatedAmount))
                    }
                    DetailRow(label = "Categoria", value = tx.category + (tx.subcategory?.let { " › $it" } ?: ""))
                    DetailRow(label = "Vencimento", value = tx.dueDate)
                    DetailRow(label = "Mês de Competência", value = tx.competenceMonth)
                    DetailRow(label = "Forma de Pagamento", value = tx.paymentMethod.label)
                    tx.splitType?.let {
                        DetailRow(label = "Divisão", value = "${it.emoji} ${it.label}")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Status Atual:", style = Typography.bodyMedium, color = TextSecondary)
                        StatusBadge(status = tx.status)
                    }
                }
            },
            confirmButton = {
                if (tx.status != TransactionStatus.PAID) {
                    Button(
                        onClick = {
                            viewModel.markAsPaid(tx.id)
                            selectedTxForDetails = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Marcar como Pago", color = DarkBackground)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(tx.id)
                        selectedTxForDetails = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose400)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Excluir")
                }
            }
        )
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Emerald500.copy(alpha = 0.2f) else DarkSurface
    val borderColor = if (isSelected) Emerald500 else DarkBorder
    val textColor = if (isSelected) Emerald400 else TextSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = Typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = Typography.labelLarge, color = TextPrimary)
    }
}
