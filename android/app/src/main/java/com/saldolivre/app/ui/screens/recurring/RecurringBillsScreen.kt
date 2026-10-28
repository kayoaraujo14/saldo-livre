package com.saldolivre.app.ui.screens.recurring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.ui.theme.*
import java.time.YearMonth
import java.util.Locale

private val DarkEmeraldTint = Color(0xFF064E3B)
private val SalaryHeroBrush = Brush.verticalGradient(
    colors = listOf(DarkEmeraldTint.copy(alpha = 0.5f), DarkSurface)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringBillsScreen(
    viewModel: RecurringBillsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val incomeCount = uiState.bills.count { it.type == TransactionType.INCOME }
    val expenseCount = uiState.bills.count { it.type == TransactionType.EXPENSE }

    val filteredBills = remember(uiState.bills, uiState.activeTab) {
        uiState.bills
            .filter {
                when (uiState.activeTab) {
                    RecurringTab.ALL -> true
                    RecurringTab.INCOME -> it.type == TransactionType.INCOME
                    RecurringTab.EXPENSE -> it.type == TransactionType.EXPENSE
                }
            }
            .sortedWith(compareByDescending<RecurringBill> { it.active }.thenBy { it.dueDay })
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(DarkBackground)) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextPrimary
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Emerald500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventRepeat,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Contas Fixas",
                                    style = Typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Automação inteligente • ${FinanceEngine.formatMonthLabel(uiState.selectedMonth)}",
                                    style = Typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = { viewModel.openNewBill() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Emerald500.copy(alpha = 0.2f),
                                contentColor = Emerald400
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nova Fixa",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                )

                val selectedIndex = when (uiState.activeTab) {
                    RecurringTab.ALL -> 0
                    RecurringTab.INCOME -> 1
                    RecurringTab.EXPENSE -> 2
                }

                TabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = DarkBackground,
                    contentColor = Emerald400,
                    indicator = { tabPositions ->
                        if (selectedIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedIndex])
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                                height = 3.dp,
                                color = when (selectedIndex) {
                                    1 -> Emerald400
                                    2 -> Rose400
                                    else -> Cyan400
                                }
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.7f))
                    }
                ) {
                    Tab(
                        selected = selectedIndex == 0,
                        onClick = { viewModel.setTab(RecurringTab.ALL) },
                        text = {
                            Text(
                                text = "Todas (${uiState.bills.size})",
                                fontWeight = if (selectedIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        selectedContentColor = Cyan400,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedIndex == 1,
                        onClick = { viewModel.setTab(RecurringTab.INCOME) },
                        text = {
                            Text(
                                text = "Rendas ($incomeCount)",
                                fontWeight = if (selectedIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        selectedContentColor = Emerald400,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedIndex == 2,
                        onClick = { viewModel.setTab(RecurringTab.EXPENSE) },
                        text = {
                            Text(
                                text = "Despesas ($expenseCount)",
                                fontWeight = if (selectedIndex == 2) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        selectedContentColor = Rose400,
                        unselectedContentColor = TextSecondary
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Card de Salário Base Principal
            item {
                BaseSalaryHeroCard(
                    userName = uiState.currentUser.displayName,
                    monthlyIncome = uiState.currentUser.monthlyIncome,
                    isEditing = uiState.editingIncome,
                    incomeInput = uiState.incomeInput,
                    onStartEdit = { viewModel.startEditingIncome() },
                    onCancelEdit = { viewModel.cancelEditingIncome() },
                    onInputChange = { viewModel.setIncomeInput(it) },
                    onSave = { viewModel.saveMonthlyIncome() }
                )
            }

            // 2. Painel Executivo de Comprometimento Fixo (4 Cards)
            item {
                ExecutiveCommitmentGrid(stats = uiState.executiveStats)
            }

            // 3. Lista de Contas Fixas & Assinaturas
            if (filteredBills.isEmpty()) {
                item {
                    EmptyRecurringState(onAddClick = { viewModel.openNewBill() })
                }
            } else {
                item {
                    Text(
                        text = "REGRAS CADASTRADAS & STATUS NO MÊS (${filteredBills.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                items(filteredBills, key = { it.id }) { bill ->
                    val cardName = bill.cardId?.let { id -> uiState.cards.find { it.id == id }?.cardName }
                    val monthStatus = uiState.billStatuses[bill.id] ?: BillMonthStatusUi(
                        state = BillMonthlyState.NOT_LAUNCHED,
                        estimatedAmount = bill.defaultAmount
                    )

                    RecurringBillItemCard(
                        bill = bill,
                        cardName = cardName,
                        monthStatus = monthStatus,
                        onToggleActive = { viewModel.toggleBillActive(bill) },
                        onQuickEffectivate = { viewModel.quickEffectivateBillInMonth(bill) },
                        onEdit = { viewModel.editBill(bill) },
                        onDelete = { viewModel.confirmDelete(bill) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Modal de Formulário (Criar / Editar)
    if (uiState.isFormOpen && uiState.editingBill != null) {
        RecurringBillFormDialog(
            initialBill = uiState.editingBill!!,
            cards = uiState.cards,
            partnerName = uiState.partner?.displayName,
            onDismiss = { viewModel.closeForm() },
            onSave = { viewModel.saveBill(it) }
        )
    }

    // Confirmação de Exclusão
    uiState.billToDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            containerColor = DarkSurface,
            title = {
                Text("Excluir Conta Fixa", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Deseja realmente remover \"${toDelete.description}\" das suas regras recorrentes? Lançamentos passados no extrato serão mantidos.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun BaseSalaryHeroCard(
    userName: String,
    monthlyIncome: Double,
    isEditing: Boolean,
    incomeInput: String,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = SalaryHeroBrush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SALÁRIO BASE PRINCIPAL (${userName.uppercase()})",
                        style = Typography.labelMedium,
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                if (!isEditing) {
                    FilledTonalButton(
                        onClick = onStartEdit,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Emerald500.copy(alpha = 0.2f),
                            contentColor = Emerald400
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajustar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = incomeInput,
                        onValueChange = onInputChange,
                        label = { Text("Salário líquido mensal (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Salvar", tint = Color.White)
                    }
                    OutlinedButton(
                        onClick = onCancelEdit,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = TextSecondary)
                    }
                }
            } else {
                Text(
                    text = if (monthlyIncome > 0) FinanceEngine.formatCurrency(monthlyIncome) else "R$ 0,00 (Não definido)",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (monthlyIncome > 0) Emerald400 else Amber400
                )
                Text(
                    text = "Programado automaticamente para o dia 5 de cada mês com confirmação inteligente na Tela Inicial.",
                    style = Typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ExecutiveCommitmentGrid(
    stats: RecurringExecutiveStats
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExecutiveMiniCard(
                label = "Renda Fixa Total",
                value = FinanceEngine.formatCurrency(stats.totalFixedIncome),
                sublabel = "Salário + rendas fixas",
                color = Emerald400,
                modifier = Modifier.weight(1f)
            )
            ExecutiveMiniCard(
                label = "Fixas (Extrato/Boleto)",
                value = FinanceEngine.formatCurrency(stats.fixedCashExpenses),
                sublabel = "Aluguel, luz, água, etc.",
                color = Rose400,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExecutiveMiniCard(
                label = "Assinaturas Cartão",
                value = FinanceEngine.formatCurrency(stats.fixedCardSubscriptions),
                sublabel = "Recorrentes na fatura",
                color = Purple400,
                modifier = Modifier.weight(1f)
            )
            val ratioColor = when {
                stats.commitmentRatioPct > 60.0 -> Rose400
                stats.commitmentRatioPct > 45.0 -> Amber400
                else -> Cyan400
            }
            ExecutiveMiniCard(
                label = "Comprometimento Fixo",
                value = "${stats.commitmentRatioPct.toInt()}% da renda",
                sublabel = "Livre base: ${FinanceEngine.formatCurrency(stats.freeBaseBalance)}",
                color = ratioColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExecutiveMiniCard(
    label: String,
    value: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sublabel,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecurringBillItemCard(
    bill: RecurringBill,
    cardName: String?,
    monthStatus: BillMonthStatusUi,
    onToggleActive: () -> Unit,
    onQuickEffectivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome = bill.type == TransactionType.INCOME
    val isCard = !isIncome && (bill.paymentMethod == PaymentMethod.CREDIT_CARD || !bill.cardId.isNullOrBlank())
    val categoryDetail = remember(bill.category) { CategoryRegistry.get(bill.category) }
    val budgetNature = remember(bill.category, bill.subcategory) {
        CategoryRegistry.getBudgetNature(bill.category, bill.subcategory)
    }

    val triggerLabel = when (bill.launchTrigger) {
        LaunchTrigger.DAY_BEFORE -> "Lança 1 dia antes"
        LaunchTrigger.ON_DUE_DATE -> "Lança no vencimento"
        LaunchTrigger.DAY_AFTER -> "Lança 1 dia após"
        LaunchTrigger.FIRST_DAY_OF_MONTH -> "Lança dia 1º do mês"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(
            1.dp,
            if (!bill.active) DarkBorder.copy(alpha = 0.5f)
            else if (isIncome) Emerald500.copy(alpha = 0.3f)
            else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (bill.active) 1f else 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Linha 1: Ícone + Título + Valor + Switch Ativo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                (if (isIncome) Emerald500 else categoryDetail.color).copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                (if (isIncome) Emerald500 else categoryDetail.color).copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isIncome) Icons.AutoMirrored.Filled.TrendingUp else categoryDetail.icon,
                            contentDescription = null,
                            tint = if (isIncome) Emerald400 else categoryDetail.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bill.description,
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append("Dia ${bill.dueDay} • ")
                                append(if (isIncome) "Receita Fixa" else bill.category)
                                if (!bill.subcategory.isNullOrBlank()) append(" › ${bill.subcategory}")
                            },
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isIncome) "+" else ""}${FinanceEngine.formatCurrency(monthStatus.estimatedAmount)}",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isIncome) Emerald400 else TextPrimary
                    )
                    if (bill.splitType == SplitType.SPLIT_50 && !isIncome) {
                        Text(
                            text = "Sua metade: ${FinanceEngine.formatCurrency(monthStatus.estimatedAmount * 0.5)}",
                            fontSize = 10.sp,
                            color = Cyan400,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (bill.estimationRule != EstimationRule.FIXED) {
                        Text(
                            text = monthStatus.estimationBasis,
                            fontSize = 10.sp,
                            color = Amber400
                        )
                    }
                }
            }

            // Linha 2: Badges de Automação, 50/30/20, Método e Status Mensal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge de Status no Mês Selecionado
                val (statusLabel, statusColor) = when (monthStatus.state) {
                    BillMonthlyState.PAID_OR_RECEIVED -> (if (isIncome) "Recebido" else "Pago") to Emerald400
                    BillMonthlyState.IN_CARD_INVOICE -> "Na Fatura" to Purple400
                    BillMonthlyState.IN_STATEMENT_PENDING -> "Previsto no Extrato" to Amber400
                    BillMonthlyState.NOT_LAUNCHED -> "A lançar" to TextMuted
                }
                MiniPillBadge(text = statusLabel, color = statusColor)

                // Badge 50/30/20 para despesas
                if (!isIncome) {
                    val (natLabel, natColor) = when (budgetNature) {
                        BudgetNature.NEED -> "Necessidade 50%" to Cyan400
                        BudgetNature.WANT -> "Desejo 30%" to Purple400
                        BudgetNature.INVESTMENT -> "Investimento 20%" to Emerald400
                        BudgetNature.INCOME -> "Receita" to Emerald400
                    }
                    MiniPillBadge(text = natLabel, color = natColor)
                }

                // Badge de Método / Cartão / Gatilho
                MiniPillBadge(
                    text = if (isCard && !cardName.isNullOrBlank()) "Cartão $cardName" else triggerLabel,
                    color = TextSecondary
                )
            }

            HorizontalDivider(color = DarkBorder.copy(alpha = 0.55f))

            // Linha 3: Ações Rápidas (Efetivar em 1 toque + Ativar/Pausar + Editar + Excluir)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = bill.active,
                        onCheckedChange = { onToggleActive() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Emerald500,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBackground
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                    Text(
                        text = if (bill.active) "Ativa" else "Pausada",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (bill.active) Emerald400 else TextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Botão de 1 toque para efetivar no mês caso ainda não esteja pago/na fatura
                    if (bill.active && (monthStatus.state == BillMonthlyState.NOT_LAUNCHED || monthStatus.state == BillMonthlyState.IN_STATEMENT_PENDING)) {
                        val quickLabel = when {
                            monthStatus.state == BillMonthlyState.IN_STATEMENT_PENDING -> "Confirmar Pago"
                            isCard -> "Lançar Fatura"
                            isIncome -> "Já Recebi"
                            else -> "Já Paguei"
                        }
                        FilledTonalButton(
                            onClick = onQuickEffectivate,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Emerald500.copy(alpha = 0.18f),
                                contentColor = Emerald400
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(quickLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir",
                            tint = Rose400,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPillBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.32f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyRecurringState(
    onAddClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EventRepeat,
                contentDescription = null,
                tint = Emerald400,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Nenhuma conta fixa nesta aba",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Cadastre aluguel, energia, internet, assinaturas de streaming ou rendas extras para automatizar seus lançamentos mensais.",
                style = Typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Criar Conta Fixa", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringBillFormDialog(
    initialBill: RecurringBill,
    cards: List<Card>,
    partnerName: String?,
    onDismiss: () -> Unit,
    onSave: (RecurringBill) -> Unit
) {
    val isNew = initialBill.id.startsWith("new_")
    var type by remember { mutableStateOf(initialBill.type) }
    var description by remember { mutableStateOf(initialBill.description) }
    var amountText by remember {
        mutableStateOf(if (initialBill.defaultAmount > 0) initialBill.defaultAmount.toString() else "")
    }
    var dueDayText by remember { mutableStateOf(initialBill.dueDay.toString()) }
    var category by remember { mutableStateOf(initialBill.category.ifBlank { "Moradia" }) }
    var subcategory by remember { mutableStateOf(initialBill.subcategory) }
    var paymentMethod by remember { mutableStateOf(initialBill.paymentMethod ?: PaymentMethod.BILL) }
    var cardId by remember { mutableStateOf(initialBill.cardId) }
    var estimationRule by remember { mutableStateOf(initialBill.estimationRule) }
    var splitType by remember { mutableStateOf(initialBill.splitType ?: SplitType.MINE) }
    var launchTrigger by remember { mutableStateOf(initialBill.launchTrigger) }

    var hasEndLimit by remember { mutableStateOf(!initialBill.endMonth.isNullOrBlank()) }
    var startMonth by remember { mutableStateOf(initialBill.startMonth ?: FinanceEngine.currentMonthKey()) }
    var installmentsCountText by remember { mutableStateOf((initialBill.installmentsCount ?: 12).toString()) }

    val availableCategories = remember(type) {
        if (type == TransactionType.INCOME) CategoryRegistry.INCOME_CATEGORIES
        else CategoryRegistry.EXPENSE_CATEGORIES
    }
    val currentCategoryDetail = remember(category) { CategoryRegistry.get(category) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNew) "Nova Conta Fixa / Recorrente" else "Editar Conta Fixa",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextSecondary)
                    }
                }

                HorizontalDivider(color = DarkBorder)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Tipo: Despesa Fixa vs Renda Fixa
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            TransactionType.EXPENSE to "Despesa / Assinatura",
                            TransactionType.INCOME to "Renda Extra Fixa"
                        ).forEach { (t, label) ->
                            val selected = type == t
                            val accent = if (t == TransactionType.INCOME) Emerald400 else Rose400
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) accent.copy(alpha = 0.18f) else DarkBackground)
                                    .border(1.dp, if (selected) accent else DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        type = t
                                        if (t == TransactionType.INCOME) {
                                            category = "Receita"
                                            subcategory = "Salário Principal"
                                            paymentMethod = PaymentMethod.PIX
                                            cardId = null
                                        } else {
                                            category = "Moradia"
                                            subcategory = CategoryRegistry.get("Moradia").subcategories.firstOrNull()
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) accent else TextSecondary
                                )
                            }
                        }
                    }

                    // Descrição
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descrição (ex: Aluguel, Energia, Netflix)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Valor + Dia de Vencimento
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Valor Padrão (R$)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground
                            ),
                            modifier = Modifier.weight(1.3f)
                        )

                        OutlinedTextField(
                            value = dueDayText,
                            onValueChange = { dueDayText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Dia Venc. (1-31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Categoria & Subcategoria (Dropdowns)
                    if (type == TransactionType.EXPENSE) {
                        var catExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = catExpanded,
                            onExpandedChange = { catExpanded = !catExpanded }
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Categoria") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald500,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedContainerColor = DarkBackground,
                                    unfocusedContainerColor = DarkBackground
                                ),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = catExpanded,
                                onDismissRequest = { catExpanded = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                availableCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = TextPrimary) },
                                        onClick = {
                                            category = cat.name
                                            subcategory = cat.subcategories.firstOrNull()
                                            catExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (currentCategoryDetail.subcategories.isNotEmpty()) {
                            var subExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = subExpanded,
                                onExpandedChange = { subExpanded = !subExpanded }
                            ) {
                                OutlinedTextField(
                                    value = subcategory ?: currentCategoryDetail.subcategories.first(),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Subcategoria (Regra 50/30/20)") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Emerald500,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedContainerColor = DarkBackground,
                                        unfocusedContainerColor = DarkBackground
                                    ),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = subExpanded,
                                    onDismissRequest = { subExpanded = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    currentCategoryDetail.subcategories.forEach { sub ->
                                        DropdownMenuItem(
                                            text = { Text(sub, color = TextPrimary) },
                                            onClick = {
                                                subcategory = sub
                                                subExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Forma de Pagamento e Cartão
                    if (type == TransactionType.EXPENSE) {
                        Text(
                            text = "MÉTODO DE PAGAMENTO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                PaymentMethod.BILL to "Boleto/Pix",
                                PaymentMethod.CREDIT_CARD to "Cartão de Crédito"
                            ).forEach { (pm, label) ->
                                val selected = (pm == PaymentMethod.CREDIT_CARD && paymentMethod == PaymentMethod.CREDIT_CARD) ||
                                        (pm != PaymentMethod.CREDIT_CARD && paymentMethod != PaymentMethod.CREDIT_CARD)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) Cyan500.copy(alpha = 0.16f) else DarkBackground)
                                        .border(1.dp, if (selected) Cyan400 else DarkBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            paymentMethod = pm
                                            if (pm == PaymentMethod.CREDIT_CARD && cardId.isNullOrBlank()) {
                                                cardId = cards.firstOrNull()?.id
                                            } else if (pm != PaymentMethod.CREDIT_CARD) {
                                                cardId = null
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Cyan400 else TextSecondary
                                    )
                                }
                            }
                        }

                        if (paymentMethod == PaymentMethod.CREDIT_CARD && cards.isNotEmpty()) {
                            var cardExpanded by remember { mutableStateOf(false) }
                            val selectedCardName = cards.find { it.id == cardId }?.cardName ?: cards.first().cardName
                            ExposedDropdownMenuBox(
                                expanded = cardExpanded,
                                onExpandedChange = { cardExpanded = !cardExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedCardName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Cartão da Assinatura") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cardExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Cyan400,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedContainerColor = DarkBackground,
                                        unfocusedContainerColor = DarkBackground
                                    ),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = cardExpanded,
                                    onDismissRequest = { cardExpanded = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    cards.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c.cardName, color = TextPrimary) },
                                            onClick = {
                                                cardId = c.id
                                                cardExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Gatilho de Lançamento Automático (Grid 2x2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "QUANDO LANÇAR AUTOMATICAMENTE NO MÊS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400,
                            letterSpacing = 0.5.sp
                        )
                        val triggerOptions = listOf(
                            Triple(LaunchTrigger.DAY_BEFORE, "1 dia antes", "Avisa na véspera do venc."),
                            Triple(LaunchTrigger.ON_DUE_DATE, "No vencimento", "Lança no dia exato"),
                            Triple(LaunchTrigger.DAY_AFTER, "1 dia após", "Confirma após o venc."),
                            Triple(LaunchTrigger.FIRST_DAY_OF_MONTH, "Dia 1º do mês", "Já reserva no início do mês")
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            triggerOptions.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (trig, title, sub) ->
                                        val isSel = launchTrigger == trig
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) Emerald500.copy(alpha = 0.16f) else DarkBackground)
                                                .border(
                                                    1.dp,
                                                    if (isSel) Emerald500.copy(alpha = 0.5f) else DarkBorder,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { launchTrigger = trig }
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Emerald400 else TextPrimary
                                            )
                                            Text(
                                                text = sub,
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Regra de Estimativa (Fixo, Mês Anterior, Média 3 Meses)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "REGRA DE CÁLCULO DO VALOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                EstimationRule.FIXED to "Valor Fixo",
                                EstimationRule.LAST_MONTH to "Mês Anterior",
                                EstimationRule.AVG_3_MONTHS to "Média 3 Meses"
                            ).forEach { (rule, label) ->
                                val selected = estimationRule == rule
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) Amber500.copy(alpha = 0.16f) else DarkBackground)
                                        .border(1.dp, if (selected) Amber400 else DarkBorder, RoundedCornerShape(10.dp))
                                        .clickable { estimationRule = rule }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Amber400 else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Divisão (100% Meu vs 50/50 Casal)
                    if (type == TransactionType.EXPENSE) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DIVISÃO DA DESPESA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    SplitType.MINE to "100% Meu",
                                    SplitType.SPLIT_50 to "50/50 (${partnerName ?: "Parceiro"})"
                                ).forEach { (st, label) ->
                                    val selected = splitType == st
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) Cyan500.copy(alpha = 0.16f) else DarkBackground)
                                            .border(1.dp, if (selected) Cyan400 else DarkBorder, RoundedCornerShape(10.dp))
                                            .clickable { splitType = st }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Cyan400 else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Prazo determinado opcional
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Possui prazo final determinado?",
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Útil para contratos ou cursos com duração fixa",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = hasEndLimit,
                            onCheckedChange = { hasEndLimit = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Emerald500
                            )
                        )
                    }

                    if (hasEndLimit) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = startMonth,
                                onValueChange = { startMonth = it },
                                label = { Text("Mês Início (AAAA-MM)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = installmentsCountText,
                                onValueChange = { installmentsCountText = it.filter { c -> c.isDigit() } },
                                label = { Text("Duração (Meses)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Cancelar", color = TextSecondary, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val parsedAmount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val parsedDueDay = (dueDayText.toIntOrNull() ?: 10).coerceIn(1, 31)
                            val count = (installmentsCountText.toIntOrNull() ?: 12).coerceAtLeast(1)
                            val computedEndMonth = if (hasEndLimit) {
                                runCatching {
                                    val parts = startMonth.split("-")
                                    val ym = YearMonth.of(parts[0].toInt(), parts[1].toInt()).plusMonths((count - 1).toLong())
                                    String.format(Locale.US, "%04d-%02d", ym.year, ym.monthValue)
                                }.getOrNull()
                            } else null

                            onSave(
                                initialBill.copy(
                                    description = description.trim(),
                                    defaultAmount = parsedAmount,
                                    dueDay = parsedDueDay,
                                    category = if (type == TransactionType.INCOME) "Receita" else category,
                                    subcategory = subcategory,
                                    type = type,
                                    paymentMethod = paymentMethod,
                                    cardId = if (paymentMethod == PaymentMethod.CREDIT_CARD) cardId else null,
                                    estimationRule = estimationRule,
                                    splitType = if (type == TransactionType.INCOME) SplitType.MINE else splitType,
                                    launchTrigger = launchTrigger,
                                    startMonth = if (hasEndLimit) startMonth else null,
                                    endMonth = computedEndMonth,
                                    installmentsCount = if (hasEndLimit) count else null
                                )
                            )
                        },
                        enabled = description.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Regra", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
