package com.saldolivre.app.ui.screens.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.ui.components.SelectionChip
import com.saldolivre.app.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val ParticipantPalette = listOf(
    Emerald400,
    Purple400,
    Cyan400,
    Amber400,
    Rose400,
    CardNubankViolet,
    CardInterOrange
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
    viewModel: TransactionFormViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // Auto-focus no campo de valor ao abrir a tela e exibe teclado numérico
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onNavigateBack()
        }
    }

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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Text(
                        text = when {
                            state.isEditing -> if (state.isEstorno) "Editar Estorno" else "Editar Lançamento"
                            state.type == TransactionType.EXPENSE -> "Nova Despesa"
                            else -> "Nova Receita"
                        },
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner if any
            state.errorMessage?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Rose500.copy(alpha = 0.15f))
                        .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(text = error, color = Rose400, fontSize = 13.sp)
                }
            }

            // Banner de Escopo de Edição para Compras Parceladas (idêntico à Web)
            val editingTx = state.editingTx
            if (state.isEditing && editingTx != null && !editingTx.installmentGroupId.isNullOrBlank()) {
                val totalInst = editingTx.installmentsTotal ?: state.installmentsCount
                val curInst = editingTx.installmentNumber ?: 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Purple500.copy(alpha = 0.12f))
                        .border(1.dp, Purple500.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Purple500.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = Purple400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Compra Parcelada Conectada",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Onde deseja aplicar as alterações?",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(Purple500.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Purple400.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Parcela $curInst/$totalInst",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Purple400
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isAll = state.editScope == "all"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isAll) Purple500.copy(alpha = 0.28f) else DarkSurface)
                                    .border(1.dp, if (isAll) Purple400 else DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.onEditScopeChange("all") }
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = if (isAll) Purple400 else TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Todas as $totalInst Parcelas",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAll) TextPrimary else TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Sincroniza em toda a série",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val isSingle = state.editScope == "single"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSingle) Purple500.copy(alpha = 0.28f) else DarkSurface)
                                    .border(1.dp, if (isSingle) Purple400 else DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.onEditScopeChange("single") }
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Apenas Nesta ($curInst/$totalInst)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSingle) TextPrimary else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Modifica só este mês",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Type Toggle: Despesa vs Receita
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (state.type == TransactionType.EXPENSE) Rose500 else Color.Transparent)
                        .clickable { viewModel.onTypeChange(TransactionType.EXPENSE) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💸 Despesa",
                        fontWeight = FontWeight.Bold,
                        color = if (state.type == TransactionType.EXPENSE) DarkBackground else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (state.type == TransactionType.INCOME) Emerald500 else Color.Transparent)
                        .clickable { viewModel.onTypeChange(TransactionType.INCOME) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💰 Receita",
                        fontWeight = FontWeight.Bold,
                        color = if (state.type == TransactionType.INCOME) DarkBackground else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Amount Input Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(
                        1.dp,
                        if (state.isEstorno) Cyan500.copy(alpha = 0.45f) else DarkBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    val amountLabel = when {
                        state.isEstorno -> "VALOR DO ESTORNO / CRÉDITO A ABATER (R$)"
                        state.isInstallment && state.installmentsCount > 1 -> {
                            if (state.installmentMode == "total") "VALOR TOTAL DA COMPRA (R$)" else "VALOR POR PARCELA (R$)"
                        }
                        else -> "VALOR (R$)"
                    }
                    Text(text = amountLabel, style = Typography.labelSmall, color = if (state.isEstorno) Cyan400 else TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isEstorno) {
                            Text(text = "- R$ ", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                        }
                        OutlinedTextField(
                            value = state.amountText,
                            onValueChange = { viewModel.onAmountChange(it) },
                            placeholder = { Text(text = "0,00", fontSize = 28.sp, color = TextMuted) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            textStyle = Typography.headlineLarge.copy(
                                color = if (state.isEstorno) Cyan400 else if (state.type == TransactionType.INCOME) Emerald400 else TextPrimary
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                }
            }

            // Description Input
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text("Descrição do Gasto ou Entrada") },
                placeholder = { Text("Ex: Supermercado, Aluguel, Almoço...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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

            // Due Date Input (DD/MM/YYYY)
            OutlinedTextField(
                value = state.dueDateText,
                onValueChange = { viewModel.onDueDateChange(it) },
                label = {
                    Text(
                        if (state.type == TransactionType.INCOME) "Data de Recebimento (DD/MM/AAAA)"
                        else if (state.paymentMethod == PaymentMethod.CREDIT_CARD) "Data da Compra (DD/MM/AAAA)"
                        else "Data de Vencimento (DD/MM/AAAA)"
                    )
                },
                placeholder = { Text("Ex: 21/10/2026") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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

            // Category & Subcategory Selector
            val currentCategoryDetail = remember(state.category) { CategoryRegistry.get(state.category) }
            val subcategoriesList = remember(currentCategoryDetail, state.subcategory) {
                val base = currentCategoryDetail.subcategories
                if (!state.subcategory.isNullOrBlank() && state.subcategory !in base) {
                    listOf(state.subcategory!!) + base
                } else {
                    base
                }
            }

            val categoriesToShow = remember(state.type, state.category, state.isEstorno) {
                val list = if (state.type == TransactionType.INCOME) {
                    CategoryRegistry.INCOME_CATEGORIES
                } else {
                    val base = CategoryRegistry.EXPENSE_CATEGORIES
                    val estornoCat = CategoryRegistry.get("Estorno")
                    if (base.none { it.name == "Estorno" }) listOf(estornoCat) + base else base
                }
                if (list.none { it.name == state.category }) {
                    list + CategoryRegistry.get(state.category)
                } else {
                    list
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Categoria & Subcategoria",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentCategoryDetail.color.copy(alpha = 0.15f))
                            .border(1.dp, currentCategoryDetail.color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = state.category + (state.subcategory?.let { " › $it" } ?: ""),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = currentCategoryDetail.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Seleção Rápida de Categoria Principal via Chips de Rolagem Horizontal
                Text(
                    text = if (state.type == TransactionType.INCOME) "Selecione a Categoria de Receita" else "Selecione a Categoria de Despesa",
                    style = Typography.labelMedium,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categoriesToShow.forEach { cat ->
                        SelectionChip(
                            label = cat.name,
                            selected = state.category == cat.name,
                            onClick = { viewModel.onCategoryChange(cat.name) },
                            icon = cat.icon,
                            accentColor = cat.color
                        )
                    }
                }

                // Seleção Rápida de Subcategoria via Chips de Rolagem Horizontal
                if (subcategoriesList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Subcategoria (${state.category})",
                        style = Typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        subcategoriesList.forEach { sub ->
                            SelectionChip(
                                label = sub,
                                selected = state.subcategory == sub,
                                onClick = {
                                    if (state.subcategory == sub) viewModel.onSubcategoryChange(null)
                                    else viewModel.onSubcategoryChange(sub)
                                },
                                accentColor = Cyan400
                            )
                        }
                    }
                }
            }

            // Payment Method via SelectionChips
            Text(text = "Forma de Pagamento", style = Typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaymentMethod.entries.forEach { method ->
                    SelectionChip(
                        label = method.label,
                        selected = state.paymentMethod == method,
                        onClick = { viewModel.onPaymentMethodChange(method) },
                        accentColor = Emerald400
                    )
                }
            }

            // Card selector if credit card
            if (state.paymentMethod == PaymentMethod.CREDIT_CARD) {
                Text(text = "Cartão de Crédito", style = Typography.titleMedium, color = TextPrimary)
                if (state.availableCards.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, Amber400.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Nenhum cartão cadastrado ainda. Cadastre um cartão no menu 'Cartões de Crédito' para vincular compras e faturas.",
                            style = Typography.bodySmall,
                            color = Amber400
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableCards.forEach { card ->
                            val isSelected = state.selectedCardId == card.id
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) DarkSurfaceVariant else DarkSurface)
                                    .border(1.dp, if (isSelected) Cyan400 else DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.onCardChange(card.id) }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = card.cardName,
                                            style = Typography.titleMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Fecha dia ${card.closingDay} • Vence dia ${card.dueDay}",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Cyan400)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Seção de Compra Parcelada (idêntica à Web)
            if (state.type == TransactionType.EXPENSE && !state.isEditing && !state.isEstorno) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurface)
                        .border(1.dp, if (state.isInstallment) Purple500.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        .clip(CircleShape)
                                        .background(if (state.isInstallment) Purple500.copy(alpha = 0.2f) else DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = if (state.isInstallment) Purple400 else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Compra Parcelada?",
                                        style = Typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Gera repetições automáticas nos meses seguintes",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Switch(
                                checked = state.isInstallment,
                                onCheckedChange = { viewModel.onIsInstallmentChange(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Purple500,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceVariant
                                )
                            )
                        }

                        if (state.isInstallment) {
                            HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                            // 1. O valor digitado acima é:
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "O valor digitado acima é:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Valor Total
                                    val isTotal = state.installmentMode == "total"
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isTotal) Purple500.copy(alpha = 0.25f) else DarkSurfaceVariant)
                                            .border(1.dp, if (isTotal) Purple400 else DarkBorder, RoundedCornerShape(10.dp))
                                            .clickable { viewModel.onInstallmentModeChange("total") }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Valor Total",
                                            fontSize = 12.sp,
                                            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTotal) Purple400 else TextSecondary
                                        )
                                    }

                                    // Por Parcela
                                    val isPer = state.installmentMode == "per_installment"
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isPer) Purple500.copy(alpha = 0.25f) else DarkSurfaceVariant)
                                            .border(1.dp, if (isPer) Purple400 else DarkBorder, RoundedCornerShape(10.dp))
                                            .clickable { viewModel.onInstallmentModeChange("per_installment") }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Por Parcela",
                                            fontSize = 12.sp,
                                            fontWeight = if (isPer) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isPer) Purple400 else TextSecondary
                                        )
                                    }
                                }
                            }

                            // 2. Número de Parcelas
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Número de Parcelas",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 18, 24, 36, 48).forEach { count ->
                                        val isSelected = state.installmentsCount == count
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) Purple500 else DarkSurfaceVariant)
                                                .clickable { viewModel.onInstallmentsChange(count) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "${count}x",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Resumo Visual das Parcelas em Tempo Real
                            val rawAmt = state.amountText.toDoubleOrNull() ?: 0.0
                            if (rawAmt > 0) {
                                val perInst = if (state.installmentMode == "total") {
                                    FinanceEngine.round2(rawAmt / state.installmentsCount)
                                } else {
                                    rawAmt
                                }
                                val totalPurch = if (state.installmentMode == "total") {
                                    rawAmt
                                } else {
                                    FinanceEngine.round2(rawAmt * state.installmentsCount)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Purple500.copy(alpha = 0.12f))
                                        .border(1.dp, Purple500.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Purple400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Serão geradas ${state.installmentsCount} parcelas de ${FinanceEngine.formatCurrency(perInst)} cada (Total: ${FinanceEngine.formatCurrency(totalPurch)}).",
                                            fontSize = 12.sp,
                                            color = Purple400,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Divisão do Lançamento (se despesa)
            if (state.type == TransactionType.EXPENSE) {
                Text(text = "Divisão do Lançamento", style = Typography.titleMedium, color = TextPrimary)
                val splitOptions = buildList {
                    add(SplitType.MINE)
                    if (state.partner != null) {
                        add(SplitType.SPLIT_50)
                    }
                    add(SplitType.CUSTOM)
                    add(SplitType.EXTERNAL)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    splitOptions.forEach { split ->
                        val isSelected = state.splitType == split || (split == SplitType.CUSTOM && state.splitType == SplitType.FRIEND)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Purple500.copy(alpha = 0.2f) else DarkSurface)
                                .border(1.dp, if (isSelected) Purple500 else DarkBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.onSplitTypeChange(split) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = when (split) {
                                    SplitType.CUSTOM -> "🧑‍🤝‍🧑 Personalizada (% ou R$)"
                                    else -> "${split.emoji} ${split.label}"
                                },
                                fontSize = 12.sp,
                                color = if (isSelected) Purple400 else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }

                // External Person Name if EXTERNAL
                if (state.splitType == SplitType.EXTERNAL) {
                    OutlinedTextField(
                        value = state.externalName,
                        onValueChange = { viewModel.onExternalNameChange(it) },
                        label = { Text("Nome da Pessoa / Terceiro") },
                        placeholder = { Text("Ex: Mãe, Irmão, João...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber500,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                // Painel de Divisão Personalizada Multi-Pessoas (CUSTOM ou FRIEND)
                if (state.splitType == SplitType.CUSTOM || state.splitType == SplitType.FRIEND) {
                    CustomSplitPanel(
                        state = state,
                        viewModel = viewModel
                    )
                }

                // Resumo do Impacto Financeiro (Live Financial Impact)
                val rawTypedAmt = state.amountText.toDoubleOrNull() ?: 0.0
                val totalAmount = if (state.isInstallment && state.installmentsCount > 1 && state.installmentMode == "total") {
                    FinanceEngine.round2(rawTypedAmt / state.installmentsCount)
                } else {
                    rawTypedAmt
                }
                if (totalAmount > 0) {
                    val myVal = when (state.splitType) {
                        SplitType.MINE -> totalAmount
                        SplitType.SPLIT_50 -> totalAmount / 2.0
                        SplitType.EXTERNAL, SplitType.PARTNER -> 0.0
                        SplitType.CUSTOM, SplitType.FRIEND -> state.customParticipants.find { it.type == "me" }?.amount ?: 0.0
                    }
                    val othersVal = max(0.0, totalAmount - myVal)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface.copy(alpha = 0.6f))
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (state.isInstallment && state.installmentsCount > 1) "Resumo do Impacto Mensal (1ª Parcela)" else "Resumo do Impacto Financeiro",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Seu Saldo Livre",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (myVal > 0) "− ${FinanceEngine.formatCurrency(myVal)}" else "R$ 0,00 (Neutro)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (myVal > 0) Rose400 else TextSecondary
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "A Receber / Terceiros",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (othersVal > 0) "+ ${FinanceEngine.formatCurrency(othersVal)}" else "R$ 0,00",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Emerald400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Status Selector: Previsão, A Pagar, Pago
            Text(text = "Status Inicial", style = Typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionStatus.entries.forEach { status ->
                    val isSelected = state.status == status
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) DarkSurfaceVariant else DarkSurface)
                            .border(1.dp, if (isSelected) Emerald400 else DarkBorder, RoundedCornerShape(10.dp))
                            .clickable { viewModel.onStatusChange(status) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = status.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Emerald400 else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (state.isEstorno) {
                val estornoAmt = state.amountText.toDoubleOrNull() ?: 0.0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cyan500.copy(alpha = 0.10f))
                        .border(1.dp, Cyan500.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Resumo do Alívio Financeiro (Estorno Rateado)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cyan400
                            )
                        }
                        Text(
                            text = "Este estorno abaterá ${FinanceEngine.formatCurrency(estornoAmt)} diretamente da fatura ou despesas deste ciclo, aliviando o Saldo Livre e reduzindo os recebíveis/pagáveis de cada participante do rateio.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Submit Button
            Button(
                onClick = { viewModel.saveTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isEstorno) Cyan500 else Emerald500
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                val buttonText = when {
                    state.isEstorno ->
                        "Salvar Estorno Rateado"
                    state.isEditing && editingTx?.installmentGroupId != null && state.editScope == "all" ->
                        "Salvar em Todas as ${editingTx.installmentsTotal ?: state.installmentsCount} Parcelas"
                    state.isEditing ->
                        "Salvar Alterações"
                    state.isInstallment && state.installmentsCount > 1 ->
                        "Criar ${state.installmentsCount} Parcelas"
                    else ->
                        "Salvar Lançamento"
                }
                Text(
                    text = buttonText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomSplitPanel(
    state: TransactionFormState,
    viewModel: TransactionFormViewModel
) {
    val totalAmount = state.amountText.toDoubleOrNull() ?: 0.0
    val totalAllocated = state.customParticipants.sumOf { it.amount }
    val diff = FinanceEngine.round2(totalAmount - totalAllocated)
    val isBalanced = totalAmount > 0.0 && abs(diff) <= 0.05

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface.copy(alpha = 0.8f))
            .border(1.dp, Purple500.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Purple500.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Purple400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Participantes da Divisão",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Ajuste em porcentagem (%) ou valor exato (R$)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Quick preset pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.friends.isNotEmpty()) {
                    val first = state.friends.first()
                    val shortName = first.friendName.split(" ").firstOrNull() ?: "Amigo"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Purple500.copy(alpha = 0.2f))
                            .border(1.dp, Purple400.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.onQuickFriendPreset(first.friendUid, 100.0) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "100% $shortName",
                            fontSize = 11.sp,
                            color = Purple400,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Purple500.copy(alpha = 0.2f))
                            .border(1.dp, Purple400.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.onQuickFriendPreset(first.friendUid, 50.0) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "50/50 $shortName",
                            fontSize = 11.sp,
                            color = Purple400,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.onDistributeEqually() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⚖️ Igual",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Emerald500.copy(alpha = 0.15f))
                        .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { viewModel.onAdjustDifferenceToMe() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⚡ Diferença em Mim",
                        fontSize = 11.sp,
                        color = Emerald400,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Progress Bar & Balance Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkBackground)
                    .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total: ${FinanceEngine.formatCurrency(totalAmount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )

                        if (isBalanced) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Emerald500.copy(alpha = 0.15f))
                                    .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "100% Equilibrado",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald400
                                    )
                                }
                            }
                        } else if (diff > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Amber500.copy(alpha = 0.15f))
                                    .border(1.dp, Amber500.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Amber400,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Restam ${FinanceEngine.formatCurrency(diff)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber400
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Rose500.copy(alpha = 0.15f))
                                    .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Rose400,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Excedeu ${FinanceEngine.formatCurrency(abs(diff))}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Rose400
                                    )
                                }
                            }
                        }
                    }

                    // Multi-color segmented progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant)
                    ) {
                        state.customParticipants.forEachIndexed { idx, p ->
                            val color = ParticipantPalette[idx % ParticipantPalette.size]
                            val weight = p.percentage.coerceIn(0.0, 100.0).toFloat()
                            if (weight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }

            // Participant rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.customParticipants.forEachIndexed { idx, p ->
                    val color = ParticipantPalette[idx % ParticipantPalette.size]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(color, CircleShape)
                                    )

                                    if (p.type == "external") {
                                        OutlinedTextField(
                                            value = p.name,
                                            onValueChange = { viewModel.onUpdateParticipantExternalName(p.id, it) },
                                            placeholder = { Text("Nome do terceiro", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.widthIn(max = 140.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Amber500,
                                                unfocusedBorderColor = DarkBorder,
                                                focusedContainerColor = DarkSurface,
                                                unfocusedContainerColor = DarkSurface,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            )
                                        )
                                    } else {
                                        Text(
                                            text = p.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when (p.type) {
                                                "me" -> "Você"
                                                "partner" -> "Parceiro(a)"
                                                "friend" -> "Amigo(a)"
                                                else -> "Terceiro"
                                            },
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                if (p.type != "me" && state.customParticipants.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.onRemoveParticipant(p.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remover",
                                            tint = Rose400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Controls Row: Presets + % + R$
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Presets
                                listOf(100.0, 50.0, 25.0).forEach { preset ->
                                    val isSelected = abs(p.percentage - preset) < 0.1
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Purple500.copy(alpha = 0.25f) else DarkSurfaceVariant)
                                            .border(1.dp, if (isSelected) Purple400 else DarkBorder, RoundedCornerShape(6.dp))
                                            .clickable { viewModel.onSetParticipantPreset(p.id, preset) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${preset.toInt()}%",
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Purple400 else TextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // % Input
                                OutlinedTextField(
                                    value = if (p.percentage > 0) String.format(Locale.US, "%.1f", p.percentage).removeSuffix(".0") else "0",
                                    onValueChange = { v ->
                                        val clean = v.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", ".")
                                        clean.toDoubleOrNull()?.let { viewModel.onParticipantPercentageChange(p.id, it) }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    suffix = { Text("%", fontSize = 11.sp, color = TextMuted) },
                                    modifier = Modifier.width(75.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Cyan400,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                // R$ Input
                                OutlinedTextField(
                                    value = if (p.amount > 0) String.format(Locale.US, "%.2f", p.amount) else "0.00",
                                    onValueChange = { v ->
                                        val clean = v.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", ".")
                                        clean.toDoubleOrNull()?.let { viewModel.onParticipantAmountChange(p.id, it) }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    prefix = { Text("R$ ", fontSize = 11.sp, color = TextMuted) },
                                    modifier = Modifier.width(110.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Emerald400,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // + Adicionar Participante
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "+ Adicionar:",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )

                if (state.partner != null && !state.customParticipants.any { it.type == "partner" }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Cyan500.copy(alpha = 0.15f))
                            .border(1.dp, Cyan400.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.onAddParticipant("partner") }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "🤝 ${state.partner.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Cyan400
                        )
                    }
                }

                state.friends
                    .filter { f -> !state.customParticipants.any { it.uid == f.friendUid } }
                    .forEach { f ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Purple500.copy(alpha = 0.15f))
                                .border(1.dp, Purple400.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { viewModel.onAddParticipant("friend", f.friendUid) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "🧑‍🤝‍🧑 ${f.friendName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Purple400
                            )
                        }
                    }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.onAddParticipant("external") }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "🏷️ + Terceiro",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            // Mutual approval warning
            val friendsInSplit = state.customParticipants.filter { it.type == "friend" }
            if (friendsInSplit.isNotEmpty()) {
                val friendNames = friendsInSplit.joinToString(", ") { it.name }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Purple500.copy(alpha = 0.12f))
                        .border(1.dp, Purple500.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Purple400,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = "A despesa será lançada no seu cartão e uma solicitação de aprovação será enviada automaticamente para $friendNames.",
                            fontSize = 11.sp,
                            color = Purple400,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
