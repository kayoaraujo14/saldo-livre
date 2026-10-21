package com.saldolivre.app.ui.screens.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.model.*
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    viewModel: TransactionFormViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

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
                        text = if (state.type == TransactionType.EXPENSE) "Nova Despesa" else "Nova Receita",
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
                        .padding(vertical = 10.dp, horizontal = 6.dp),
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
                        .padding(vertical = 10.dp, horizontal = 6.dp),
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
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(text = "VALOR (R$)", style = Typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = { viewModel.onAmountChange(it) },
                        placeholder = { Text(text = "0,00", fontSize = 28.sp, color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = Typography.headlineLarge.copy(
                            color = if (state.type == TransactionType.INCOME) Emerald400 else TextPrimary
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
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

            // Category Selector
            Text(text = "Categoria", style = Typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryRegistry.CATEGORIES.forEach { cat ->
                    val isSelected = state.category == cat.name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) cat.color.copy(alpha = 0.2f) else DarkSurface)
                            .border(1.dp, if (isSelected) cat.color else DarkBorder, RoundedCornerShape(10.dp))
                            .clickable { viewModel.onCategoryChange(cat.name) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) cat.color else TextSecondary
                        )
                    }
                }
            }

            // Payment Method
            Text(text = "Forma de Pagamento", style = Typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMethod.entries.forEach { method ->
                    val isSelected = state.paymentMethod == method
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Emerald500.copy(alpha = 0.2f) else DarkSurface)
                            .border(1.dp, if (isSelected) Emerald500 else DarkBorder, RoundedCornerShape(10.dp))
                            .clickable { viewModel.onPaymentMethodChange(method) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = method.label,
                            fontSize = 12.sp,
                            color = if (isSelected) Emerald400 else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Card selector if credit card
            if (state.paymentMethod == PaymentMethod.CREDIT_CARD && state.availableCards.isNotEmpty()) {
                Text(text = "Cartão de Crédito", style = Typography.titleMedium, color = TextPrimary)
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

            // Split Type (Divisão)
            Text(text = "Divisão do Lançamento", style = Typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    SplitType.MINE,
                    SplitType.SPLIT_50,
                    SplitType.FRIEND,
                    SplitType.EXTERNAL
                ).forEach { split ->
                    val isSelected = state.splitType == split
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Purple500.copy(alpha = 0.2f) else DarkSurface)
                            .border(1.dp, if (isSelected) Purple500 else DarkBorder, RoundedCornerShape(10.dp))
                            .clickable { viewModel.onSplitTypeChange(split) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${split.emoji} ${split.label}",
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

            Spacer(modifier = Modifier.height(10.dp))

            // Submit Button
            Button(
                onClick = { viewModel.saveTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Salvar Lançamento",
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
