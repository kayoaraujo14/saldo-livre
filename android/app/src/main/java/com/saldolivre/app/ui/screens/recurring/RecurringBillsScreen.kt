package com.saldolivre.app.ui.screens.recurring

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.ui.theme.*

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

    val myBills = remember(uiState.bills, uiState.currentUser.uid, uiState.activeTab) {
        uiState.bills
            .filter { it.ownerUid == uiState.currentUser.uid }
            .filter {
                when (uiState.activeTab) {
                    RecurringTab.ALL -> true
                    RecurringTab.INCOME -> it.type == TransactionType.INCOME
                    RecurringTab.EXPENSE -> it.type != TransactionType.INCOME
                }
            }
    }

    val totalCount = uiState.bills.count { it.ownerUid == uiState.currentUser.uid }
    val incomeCount = uiState.bills.count { it.ownerUid == uiState.currentUser.uid && it.type == TransactionType.INCOME }
    val expenseCount = uiState.bills.count { it.ownerUid == uiState.currentUser.uid && it.type != TransactionType.INCOME }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    navigationIconContentColor = TextPrimary,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Moldes Recorrentes",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Previsões automáticas e salários",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.openNewBill() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber500,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Novo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Renda Base / Salário Principal Card
            item {
                BaseSalaryCard(
                    monthlyIncome = uiState.currentUser.monthlyIncome,
                    isEditing = uiState.editingIncome,
                    incomeInput = uiState.incomeInput,
                    onStartEdit = { viewModel.startEditingIncome() },
                    onCancelEdit = { viewModel.cancelEditingIncome() },
                    onInputChange = { viewModel.setIncomeInput(it) },
                    onSave = { viewModel.saveMonthlyIncome() }
                )
            }

            // 2. Tabs Filter
            item {
                FilterTabsRow(
                    activeTab = uiState.activeTab,
                    totalCount = totalCount,
                    incomeCount = incomeCount,
                    expenseCount = expenseCount,
                    onSelectTab = { viewModel.setTab(it) }
                )
            }

            // 3. Bills List
            if (myBills.isEmpty()) {
                item {
                    EmptyRecurringState()
                }
            } else {
                items(myBills, key = { it.id }) { bill ->
                    RecurringBillItemCard(
                        bill = bill,
                        onToggleActive = { viewModel.toggleBillActive(bill) },
                        onEdit = { viewModel.editBill(bill) },
                        onDelete = { viewModel.confirmDelete(bill) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Modal Form Dialog
    if (uiState.isFormOpen && uiState.editingBill != null) {
        RecurringBillFormDialog(
            initialBill = uiState.editingBill!!,
            partner = uiState.partner,
            onDismiss = { viewModel.closeForm() },
            onSave = { viewModel.saveBill(it) }
        )
    }

    // Delete Confirmation Dialog
    uiState.billToDelete?.let { bill ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Excluir Molde?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Deseja realmente excluir o molde \"${bill.description}\"?\nLançamentos já gerados nos meses anteriores permanecerão intactos no extrato.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text(text = "Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text(text = "Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Componente 1: Card de Renda Base / Salário Principal
// -------------------------------------------------------------
@Composable
private fun BaseSalaryCard(
    monthlyIncome: Double,
    isEditing: Boolean,
    incomeInput: String,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Emerald500.copy(alpha = 0.15f),
                        DarkSurfaceVariant.copy(alpha = 0.5f),
                        Emerald500.copy(alpha = 0.08f)
                    )
                )
            )
            .border(1.dp, Emerald500.copy(alpha = 0.3f), shape)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Emerald500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "RENDA BASE / SALÁRIO PRINCIPAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400,
                            letterSpacing = 0.5.sp
                        )
                        if (!isEditing) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = FinanceEngine.formatCurrency(monthlyIncome),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Emerald400
                                )
                                Text(
                                    text = "/ mês",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (!isEditing) {
                    OutlinedButton(
                        onClick = onStartEdit,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder))
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Alterar", fontSize = 11.sp, color = TextSecondary)
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
                        label = { Text("Valor em R$", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald400,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Salvar",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salvar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onCancelEdit) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = TextMuted
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Componente 2: Tabs de Filtro
// -------------------------------------------------------------
@Composable
private fun FilterTabsRow(
    activeTab: RecurringTab,
    totalCount: Int,
    incomeCount: Int,
    expenseCount: Int,
    onSelectTab: (RecurringTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabButton(
            title = "Todos ($totalCount)",
            isSelected = activeTab == RecurringTab.ALL,
            selectedColor = DarkSurface,
            selectedTextColor = TextPrimary,
            modifier = Modifier.weight(1f),
            onClick = { onSelectTab(RecurringTab.ALL) }
        )
        TabButton(
            title = "Entradas ($incomeCount)",
            isSelected = activeTab == RecurringTab.INCOME,
            selectedColor = Emerald500.copy(alpha = 0.25f),
            selectedTextColor = Emerald400,
            modifier = Modifier.weight(1f),
            onClick = { onSelectTab(RecurringTab.INCOME) }
        )
        TabButton(
            title = "Despesas ($expenseCount)",
            isSelected = activeTab == RecurringTab.EXPENSE,
            selectedColor = Amber500.copy(alpha = 0.25f),
            selectedTextColor = Amber400,
            modifier = Modifier.weight(1f),
            onClick = { onSelectTab(RecurringTab.EXPENSE) }
        )
    }
}

@Composable
private fun TabButton(
    title: String,
    isSelected: Boolean,
    selectedColor: Color,
    selectedTextColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) selectedTextColor else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// Componente 3: Card de Item Recorrente
// -------------------------------------------------------------
@Composable
private fun RecurringBillItemCard(
    bill: RecurringBill,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome = bill.type == TransactionType.INCOME
    val shape = RoundedCornerShape(16.dp)
    val categoryDetail = remember(bill.category) { CategoryRegistry.get(bill.category) }

    val borderColor = if (isIncome) {
        Emerald500.copy(alpha = if (bill.active) 0.35f else 0.15f)
    } else {
        DarkBorder
    }

    val backgroundColor = if (isIncome) {
        Emerald500.copy(alpha = if (bill.active) 0.08f else 0.03f)
    } else {
        DarkSurface.copy(alpha = if (bill.active) 1f else 0.6f)
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Category icon + Description + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                (if (isIncome) Emerald500 else categoryDetail.color).copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isIncome) Icons.Default.AttachMoney else categoryDetail.icon,
                            contentDescription = null,
                            tint = if (isIncome) Emerald400 else categoryDetail.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bill.description,
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (bill.active) TextPrimary else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isIncome) "Recebe todo dia ${bill.dueDay}" else "Vence todo dia ${bill.dueDay}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Switch(
                    checked = bill.active,
                    onCheckedChange = { onToggleActive() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = if (isIncome) Emerald500 else Amber500,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category / Type Badge
                TagBadge(
                    text = if (isIncome) "Entrada" else bill.category,
                    backgroundColor = if (isIncome) Emerald500.copy(alpha = 0.2f) else DarkSurfaceVariant,
                    textColor = if (isIncome) Emerald400 else TextSecondary
                )

                // Subcategory
                bill.subcategory?.let { sub ->
                    TagBadge(
                        text = "› $sub",
                        backgroundColor = DarkSurfaceVariant,
                        textColor = TextSecondary
                    )
                }

                // Estimation rule badge
                val ruleLabel = when (bill.estimationRule) {
                    EstimationRule.FIXED -> "📌 Fixo"
                    EstimationRule.LAST_MONTH -> "📅 Último Mês"
                    EstimationRule.AVG_3_MONTHS -> "📊 Média 3M"
                }
                TagBadge(
                    text = ruleLabel,
                    backgroundColor = Amber500.copy(alpha = 0.15f),
                    textColor = Amber400
                )

                // Split badge
                if (bill.splitType == SplitType.SPLIT_50) {
                    TagBadge(
                        text = "🤝 50/50",
                        backgroundColor = Purple500.copy(alpha = 0.2f),
                        textColor = Purple400
                    )
                }

                // Duration badge
                if (!bill.endMonth.isNullOrBlank()) {
                    val countLabel = bill.installmentsCount?.let { " (${it}x)" } ?: ""
                    TagBadge(
                        text = "📅 Até ${FinanceEngine.monthLabel(bill.endMonth)}$countLabel",
                        backgroundColor = Cyan500.copy(alpha = 0.15f),
                        textColor = Cyan400
                    )
                } else {
                    TagBadge(
                        text = "Contínua",
                        backgroundColor = DarkSurfaceVariant.copy(alpha = 0.4f),
                        textColor = TextMuted
                    )
                }
            }

            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))

            // Bottom Row: Amount + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isIncome) "+${FinanceEngine.formatCurrency(bill.defaultAmount)}" else FinanceEngine.formatCurrency(bill.defaultAmount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) Emerald400 else if (bill.active) TextPrimary else TextMuted
                    )
                    Text(text = "padrão", fontSize = 11.sp, color = TextMuted)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = Rose400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyRecurringState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface.copy(alpha = 0.5f))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 40.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Nenhum molde nesta categoria",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Text(
                text = "Cadastre moldes recorrentes para que suas contas e salários sejam projetados automaticamente a cada mês.",
                style = Typography.bodySmall,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------
// Componente 4: Form Dialog (Criar / Editar Molde)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringBillFormDialog(
    initialBill: RecurringBill,
    partner: UserProfile?,
    onDismiss: () -> Unit,
    onSave: (RecurringBill) -> Unit
) {
    var billType by remember { mutableStateOf(initialBill.type) }
    var description by remember { mutableStateOf(initialBill.description) }
    var category by remember { mutableStateOf(initialBill.category) }
    var subcategory by remember { mutableStateOf(initialBill.subcategory) }
    var dueDay by remember { mutableStateOf(initialBill.dueDay.toString()) }
    var defaultAmount by remember { mutableStateOf(if (initialBill.defaultAmount > 0) initialBill.defaultAmount.toString() else "") }
    var estimationRule by remember { mutableStateOf(initialBill.estimationRule) }
    var splitType by remember { mutableStateOf(initialBill.splitType ?: SplitType.MINE) }
    var paymentMethod by remember { mutableStateOf(initialBill.paymentMethod ?: PaymentMethod.BILL) }
    var active by remember { mutableStateOf(initialBill.active) }

    // Duração
    var durationMode by remember {
        mutableStateOf(if (!initialBill.endMonth.isNullOrBlank()) "installments" else "indefinite")
    }
    var startMonth by remember {
        mutableStateOf(initialBill.startMonth ?: FinanceEngine.currentMonthKey())
    }
    var installmentsCount by remember {
        mutableStateOf(initialBill.installmentsCount ?: 3)
    }

    val availableCategories = remember {
        CategoryRegistry.CATEGORIES.filter { it.name != "Receita" }
    }

    val currentSubcategories = remember(billType, category) {
        val targetCat = if (billType == TransactionType.INCOME) "Receita" else category
        CategoryRegistry.get(targetCat).subcategories
    }

    var expandedCategoryDropdown by remember { mutableStateOf(false) }
    var expandedSubcategoryDropdown by remember { mutableStateOf(false) }
    var expandedRuleDropdown by remember { mutableStateOf(false) }
    var expandedSplitDropdown by remember { mutableStateOf(false) }
    var expandedPaymentDropdown by remember { mutableStateOf(false) }
    var expandedInstallmentsDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBackground)
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (initialBill.id.startsWith("new_")) "Novo Molde Recorrente" else "Editar Molde",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Configuração de automação mensal",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Type Selector: Despesa vs Entrada
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (billType == TransactionType.EXPENSE) Amber500.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable {
                                    billType = TransactionType.EXPENSE
                                    if (category == "Receita") category = "Moradia"
                                    subcategory = CategoryRegistry.get(category).subcategories.firstOrNull()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Despesa",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (billType == TransactionType.EXPENSE) Amber400 else TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (billType == TransactionType.INCOME) Emerald500.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable {
                                    billType = TransactionType.INCOME
                                    category = "Receita"
                                    subcategory = CategoryRegistry.get("Receita").subcategories.firstOrNull()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Entrada / Renda",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (billType == TransactionType.INCOME) Emerald400 else TextMuted
                            )
                        }
                    }

                    // Descrição
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(if (billType == TransactionType.INCOME) "Descrição da Entrada (Ex: Freelance, Renda Extra)" else "Descrição da Conta (Ex: Conta de Luz, Aluguel)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (billType == TransactionType.INCOME) Emerald400 else Amber400,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Categoria (apenas para despesas)
                    if (billType == TransactionType.EXPENSE) {
                        ExposedDropdownMenuBox(
                            expanded = expandedCategoryDropdown,
                            onExpandedChange = { expandedCategoryDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Categoria") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCategoryDropdown,
                                onDismissRequest = { expandedCategoryDropdown = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                availableCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = TextPrimary) },
                                        leadingIcon = {
                                            Icon(imageVector = cat.icon, contentDescription = null, tint = cat.color)
                                        },
                                        onClick = {
                                            category = cat.name
                                            subcategory = cat.subcategories.firstOrNull()
                                            expandedCategoryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Subcategoria
                    if (currentSubcategories.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedSubcategoryDropdown,
                            onExpandedChange = { expandedSubcategoryDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = subcategory ?: "Nenhuma",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Subcategoria") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSubcategoryDropdown) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (billType == TransactionType.INCOME) Emerald400 else Amber400,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedSubcategoryDropdown,
                                onDismissRequest = { expandedSubcategoryDropdown = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                currentSubcategories.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub, color = TextPrimary) },
                                        onClick = {
                                            subcategory = sub
                                            expandedSubcategoryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Row: Dia do Vencimento / Recebimento + Valor Base
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dueDay,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                val num = filtered.toIntOrNull()
                                if (num == null || (num in 1..31)) {
                                    dueDay = filtered
                                }
                            },
                            label = { Text(if (billType == TransactionType.INCOME) "Dia Receb." else "Dia Venc.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (billType == TransactionType.INCOME) Emerald400 else Amber400,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = defaultAmount,
                            onValueChange = { defaultAmount = it },
                            label = { Text("Valor Base (R$)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (billType == TransactionType.INCOME) Emerald400 else Amber400,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Regra de Estimativa
                    ExposedDropdownMenuBox(
                        expanded = expandedRuleDropdown,
                        onExpandedChange = { expandedRuleDropdown = it }
                    ) {
                        val ruleLabel = when (estimationRule) {
                            EstimationRule.FIXED -> "📌 Valor Fixo Padrão"
                            EstimationRule.LAST_MONTH -> "📅 Último Mês Pago"
                            EstimationRule.AVG_3_MONTHS -> "📊 Média dos Últimos 3 Meses"
                        }
                        OutlinedTextField(
                            value = ruleLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Regra de Estimativa") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRuleDropdown) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber400,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedRuleDropdown,
                            onDismissRequest = { expandedRuleDropdown = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("📌 Valor Fixo Padrão", color = TextPrimary) },
                                onClick = {
                                    estimationRule = EstimationRule.FIXED
                                    expandedRuleDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📅 Último Mês Pago", color = TextPrimary) },
                                onClick = {
                                    estimationRule = EstimationRule.LAST_MONTH
                                    expandedRuleDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📊 Média dos Últimos 3 Meses", color = TextPrimary) },
                                onClick = {
                                    estimationRule = EstimationRule.AVG_3_MONTHS
                                    expandedRuleDropdown = false
                                }
                            )
                        }
                    }

                    // Divisão Padrão (se Despesa)
                    if (billType == TransactionType.EXPENSE) {
                        ExposedDropdownMenuBox(
                            expanded = expandedSplitDropdown,
                            onExpandedChange = { expandedSplitDropdown = it }
                        ) {
                            val splitLabel = if (splitType == SplitType.SPLIT_50) {
                                "🤝 Dividido 50/50" + (partner?.let { " com ${it.displayName}" } ?: "")
                            } else {
                                "👤 100% Meu"
                            }
                            OutlinedTextField(
                                value = splitLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Divisão Padrão") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSplitDropdown) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedSplitDropdown,
                                onDismissRequest = { expandedSplitDropdown = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("👤 100% Meu", color = TextPrimary) },
                                    onClick = {
                                        splitType = SplitType.MINE
                                        expandedSplitDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🤝 Dividido 50/50" + (partner?.let { " com ${it.displayName}" } ?: ""), color = TextPrimary) },
                                    onClick = {
                                        splitType = SplitType.SPLIT_50
                                        expandedSplitDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Meio de Pagamento
                    ExposedDropdownMenuBox(
                        expanded = expandedPaymentDropdown,
                        onExpandedChange = { expandedPaymentDropdown = it }
                    ) {
                        val payLabel = when (paymentMethod) {
                            PaymentMethod.PIX -> "⚡ Pix"
                            PaymentMethod.BILL -> "📄 Boleto Bancário"
                            PaymentMethod.DEBIT -> "🏦 Débito em Conta"
                            PaymentMethod.CREDIT_CARD -> "💳 Cartão de Crédito"
                        }
                        OutlinedTextField(
                            value = payLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Meio de Pagamento / Cobrança") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPaymentDropdown) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber400,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPaymentDropdown,
                            onDismissRequest = { expandedPaymentDropdown = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("⚡ Pix", color = TextPrimary) },
                                onClick = { paymentMethod = PaymentMethod.PIX; expandedPaymentDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 Boleto Bancário", color = TextPrimary) },
                                onClick = { paymentMethod = PaymentMethod.BILL; expandedPaymentDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("🏦 Débito em Conta", color = TextPrimary) },
                                onClick = { paymentMethod = PaymentMethod.DEBIT; expandedPaymentDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("💳 Cartão de Crédito", color = TextPrimary) },
                                onClick = { paymentMethod = PaymentMethod.CREDIT_CARD; expandedPaymentDropdown = false }
                            )
                        }
                    }

                    // Bloco de Duração & Término
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Duração & Término",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Projeção inteligente",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (durationMode == "indefinite") Emerald500.copy(alpha = 0.2f) else DarkSurfaceVariant.copy(alpha = 0.4f))
                                        .clickable { durationMode = "indefinite" }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🔄 Contínua",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (durationMode == "indefinite") Emerald400 else TextMuted
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (durationMode == "installments") Cyan500.copy(alpha = 0.2f) else DarkSurfaceVariant.copy(alpha = 0.4f))
                                        .clickable { durationMode = "installments" }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "📅 Prazo Fixo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (durationMode == "installments") Cyan400 else TextMuted
                                    )
                                }
                            }

                            if (durationMode == "installments") {
                                val calculatedEnd = remember(startMonth, installmentsCount) {
                                    FinanceEngine.shiftMonth(startMonth, installmentsCount - 1)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = startMonth,
                                        onValueChange = { startMonth = it },
                                        label = { Text("Mês Inicial (AAAA-MM)") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Cyan400,
                                            unfocusedBorderColor = DarkBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    ExposedDropdownMenuBox(
                                        expanded = expandedInstallmentsDropdown,
                                        onExpandedChange = { expandedInstallmentsDropdown = it },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = "$installmentsCount meses",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Parcelas") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInstallmentsDropdown) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Cyan400,
                                                unfocusedBorderColor = DarkBorder,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            ),
                                            modifier = Modifier.menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expandedInstallmentsDropdown,
                                            onDismissRequest = { expandedInstallmentsDropdown = false },
                                            modifier = Modifier.background(DarkSurface)
                                        ) {
                                            listOf(2, 3, 4, 5, 6, 8, 10, 12, 18, 24, 36).forEach { n ->
                                                DropdownMenuItem(
                                                    text = { Text("$n parcelas ($n meses)", color = TextPrimary) },
                                                    onClick = {
                                                        installmentsCount = n
                                                        expandedInstallmentsDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Cyan500.copy(alpha = 0.1f))
                                        .border(1.dp, Cyan500.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "💡 Previsão Ativa: de ${FinanceEngine.monthLabel(startMonth)} até ${FinanceEngine.monthLabel(calculatedEnd)}.",
                                        fontSize = 11.sp,
                                        color = Cyan400
                                    )
                                }
                            }
                        }
                    }

                    // Molde Ativo Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Molde Ativo (gerar nos meses futuros)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Switch(
                            checked = active,
                            onCheckedChange = { active = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Amber500,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Footer Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            if (description.isBlank()) return@Button
                            val amount = defaultAmount.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val day = dueDay.toIntOrNull() ?: 10
                            val endMonthVal = if (durationMode == "installments") {
                                FinanceEngine.shiftMonth(startMonth, installmentsCount - 1)
                            } else null

                            val updated = initialBill.copy(
                                description = description.trim(),
                                category = if (billType == TransactionType.INCOME) "Receita" else category,
                                subcategory = subcategory,
                                dueDay = day,
                                defaultAmount = amount,
                                estimationRule = estimationRule,
                                splitType = splitType,
                                paymentMethod = paymentMethod,
                                type = billType,
                                active = active,
                                startMonth = if (durationMode == "installments") startMonth else null,
                                endMonth = endMonthVal,
                                installmentsCount = if (durationMode == "installments") installmentsCount else null
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (billType == TransactionType.INCOME) Emerald500 else Amber500,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Salvar Molde", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
