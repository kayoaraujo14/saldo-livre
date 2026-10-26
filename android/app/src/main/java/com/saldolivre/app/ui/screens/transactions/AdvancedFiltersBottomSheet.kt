package com.saldolivre.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedFiltersBottomSheet(
    uiState: TransactionsUiState,
    onDismissRequest: () -> Unit,
    onToggleType: (TransactionType) -> Unit,
    onToggleStatus: (TransactionStatus) -> Unit,
    onToggleCategory: (String) -> Unit,
    onTogglePerson: (String) -> Unit,
    onClearAll: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
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
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Filtros Avançados",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (uiState.isFiltered) {
                    TextButton(
                        onClick = onClearAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Limpar Filtros",
                            fontSize = 12.sp,
                            color = Rose400,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section 1: Tipo de Lançamento
                FilterSection(title = "Tipo de Lançamento") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterPillChip(
                            label = "Despesas",
                            isSelected = uiState.selectedTypes.contains(TransactionType.EXPENSE),
                            onClick = { onToggleType(TransactionType.EXPENSE) }
                        )
                        FilterPillChip(
                            label = "Receitas",
                            isSelected = uiState.selectedTypes.contains(TransactionType.INCOME),
                            onClick = { onToggleType(TransactionType.INCOME) }
                        )
                    }
                }

                // Section 2: Status
                FilterSection(title = "Status do Pagamento") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterPillChip(
                            label = "Pago",
                            isSelected = uiState.selectedStatuses.contains(TransactionStatus.PAID),
                            onClick = { onToggleStatus(TransactionStatus.PAID) }
                        )
                        FilterPillChip(
                            label = "Pendente",
                            isSelected = uiState.selectedStatuses.contains(TransactionStatus.PENDING),
                            onClick = { onToggleStatus(TransactionStatus.PENDING) }
                        )
                        FilterPillChip(
                            label = "Estimado",
                            isSelected = uiState.selectedStatuses.contains(TransactionStatus.ESTIMATED),
                            onClick = { onToggleStatus(TransactionStatus.ESTIMATED) }
                        )
                    }
                }

                // Section 3: Participação & Responsável
                if (uiState.availablePersons.isNotEmpty()) {
                    FilterSection(
                        title = "Participação & Responsável",
                        subtitle = "Filtre seus gastos pessoais (todos ou 100%), rateios ou de terceiros"
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.availablePersons.forEach { person ->
                                val (icon, iconTint) = when (person) {
                                    "Todos os Meus", "Todos os Meus Gastos" -> Icons.Default.Person to Emerald400
                                    "100% Meu (Exclusivo)", "100% Meu" -> Icons.Default.PersonOutline to Emerald400
                                    "Meus Rateados" -> Icons.AutoMirrored.Filled.CallSplit to Amber400
                                    "50/50 (Casal)" -> Icons.Default.People to Purple400
                                    "Parceiro(a)" -> Icons.Default.Sync to Purple400
                                    else -> Icons.AutoMirrored.Filled.Label to Cyan400
                                }
                                val count = uiState.personCounts[person] ?: 0
                                FilterPillChip(
                                    label = person,
                                    icon = icon,
                                    iconTint = iconTint,
                                    count = count,
                                    isSelected = uiState.selectedPersons.contains(person),
                                    onClick = { onTogglePerson(person) }
                                )
                            }
                        }
                    }
                }

                // Section 4: Categorias organizadas por Despesas e Receitas
                val selectedTypes = uiState.selectedTypes
                val showExpenseCats = selectedTypes.isEmpty() || selectedTypes.contains(TransactionType.EXPENSE)
                val showIncomeCats = selectedTypes.isEmpty() || selectedTypes.contains(TransactionType.INCOME)

                val expenseCategories = CategoryRegistry.EXPENSE_CATEGORIES
                val incomeCategories = CategoryRegistry.INCOME_CATEGORIES

                if (showExpenseCats) {
                    FilterSection(
                        title = "Categorias de Despesa",
                        subtitle = "Filtre por grupos específicos de saídas"
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            expenseCategories.forEach { cat ->
                                val count = uiState.categoryCounts[cat.name] ?: 0
                                FilterPillChip(
                                    label = cat.name,
                                    icon = cat.icon,
                                    iconTint = cat.color,
                                    count = count,
                                    isSelected = uiState.selectedCategories.contains(cat.name),
                                    onClick = { onToggleCategory(cat.name) }
                                )
                            }
                        }
                    }
                }

                if (showIncomeCats) {
                    FilterSection(
                        title = "Categorias de Receita",
                        subtitle = "Filtre por tipos de entradas e rendimentos"
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            incomeCategories.forEach { cat ->
                                val count = uiState.categoryCounts[cat.name] ?: 0
                                FilterPillChip(
                                    label = cat.name,
                                    icon = cat.icon,
                                    iconTint = cat.color,
                                    count = count,
                                    isSelected = uiState.selectedCategories.contains(cat.name),
                                    onClick = { onToggleCategory(cat.name) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald500,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "Ver ${uiState.filteredTransactions.size} ${if (uiState.filteredTransactions.size == 1) "resultado" else "resultados"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
        content()
    }
}

@Composable
fun FilterPillChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    count: Int? = null
) {
    val bgColor = if (isSelected) Emerald500.copy(alpha = 0.15f) else DarkSurfaceVariant
    val borderColor = if (isSelected) Emerald500.copy(alpha = 0.45f) else DarkBorder
    val textColor = if (isSelected) Emerald400 else TextSecondary

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: (if (isSelected) Emerald400 else TextSecondary),
                    modifier = Modifier.size(13.dp)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                style = LocalTextStyle.current.copy(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                ),
                maxLines = 1
            )
            if (count != null && count > 0) {
                Text(
                    text = "($count)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Emerald400.copy(alpha = 0.85f) else TextMuted
                )
            }
        }
    }
}
