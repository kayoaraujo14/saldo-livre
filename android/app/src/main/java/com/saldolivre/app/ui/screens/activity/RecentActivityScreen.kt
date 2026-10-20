package com.saldolivre.app.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.components.StatusBadge
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentActivityScreen(
    viewModel: RecentActivityViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMonth: (monthKey: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Brush.linearGradient(listOf(Cyan500.copy(alpha = 0.3f), Emerald500.copy(alpha = 0.2f))),
                                    CircleShape
                                )
                                .border(1.dp, Cyan400, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Histórico Recente",
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Cyan500.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${uiState.filteredItems.size} itens",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Cyan400
                                    )
                                }
                            }
                            Text(
                                text = "Lançamentos por ordem de criação no sistema",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Metrics Ribbon (idêntico à Web)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Total Geral",
                    value = uiState.totalCount.toString(),
                    color = TextPrimary,
                    bgColor = DarkSurface,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Últimos 7 dias",
                    value = "+${uiState.last7DaysCount}",
                    color = Cyan400,
                    bgColor = Cyan500.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Com Terceiros",
                    value = uiState.withThirdPartyCount.toString(),
                    color = Amber400,
                    bgColor = Amber500.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Amigos",
                    value = uiState.friendsCount.toString(),
                    color = Purple400,
                    bgColor = Purple500.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                )
            }

            // Barra de Busca
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = {
                    Text(
                        text = "Buscar por descrição, terceiro, amigo, cartão...",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpar busca",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan400,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Chips de Filtro (Scroll Horizontal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityFilter.entries.forEach { filter ->
                    val isSelected = uiState.selectedFilter == filter
                    val chipBg = when {
                        isSelected && filter == ActivityFilter.ALL -> Cyan500
                        isSelected && filter == ActivityFilter.THIRD_PARTY -> Amber500
                        isSelected && filter == ActivityFilter.FRIENDS -> Purple500
                        isSelected && filter == ActivityFilter.INCOME -> Emerald500
                        isSelected && filter == ActivityFilter.EXPENSE -> Rose500
                        isSelected -> DarkSurfaceVariant
                        else -> DarkSurfaceVariant
                    }
                    val textColor = if (isSelected) DarkBackground else TextSecondary

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .border(1.dp, if (isSelected) chipBg else DarkBorder, RoundedCornerShape(12.dp))
                            .clickable { viewModel.onFilterChange(filter) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            // Timeline List
            if (uiState.filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Nenhuma atividade encontrada",
                            style = Typography.titleMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tente ajustar os filtros ou o termo pesquisado.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.filteredItems) { index, item ->
                        TimelineActivityCard(
                            item = item,
                            isLast = index == uiState.filteredItems.lastIndex,
                            onNavigateToMonth = {
                                viewModel.selectMonth(item.competenceMonth)
                                onNavigateToMonth(item.competenceMonth)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 9.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun TimelineActivityCard(
    item: ActivityItemUi,
    isLast: Boolean,
    onNavigateToMonth: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Indicador de Timeline (Nó com Ícone e Linha Conectora)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            val (icon, iconTint, badgeBg) = when {
                item.isIncome -> Triple(Icons.Default.ArrowDownward, Emerald400, Emerald500.copy(alpha = 0.2f))
                item.isRefund -> Triple(Icons.AutoMirrored.Filled.RotateLeft, Cyan400, Cyan500.copy(alpha = 0.2f))
                else -> Triple(Icons.Default.ArrowUpward, Rose400, Rose500.copy(alpha = 0.2f))
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .border(1.dp, iconTint.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(90.dp)
                        .background(DarkBorder)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Conteúdo do Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Header: Descrição e Valor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${if (item.isIncome) "+" else ""}${FinanceEngine.formatCurrency(item.amount)}",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.isIncome) Emerald400 else if (item.isRefund) Cyan400 else Rose400
                    )
                }

                // Linha de Badges (Categoria, Cartão, Amigo, Terceiro)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge Categoria
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.description,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    // Badge Cartão ou Método
                    item.cardName?.let { cName ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Purple500.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "💳 $cName",
                                fontSize = 10.sp,
                                color = Purple400,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } ?: run {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.paymentMethodLabel,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // Badge Amigo ou Terceiro
                    item.friendName?.let { fName ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Purple500.copy(alpha = 0.2f))
                                .border(1.dp, Purple400.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🧑‍🤝‍🧑 $fName${if (item.friendSharePct != null) " (${item.friendSharePct.toInt()}%)" else ""}",
                                fontSize = 10.sp,
                                color = Purple400,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    item.thirdPartyName?.let { tpName ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Amber500.copy(alpha = 0.15f))
                                .border(1.dp, Amber400.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🏷️ $tpName",
                                fontSize = 10.sp,
                                color = Amber400,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Impacto pessoal se houver divisão
                if (item.myImpact != item.amount && item.myImpact > 0) {
                    Text(
                        text = "Sua cota no Saldo Livre: ${FinanceEngine.formatCurrency(item.myImpact)}",
                        fontSize = 11.sp,
                        color = Emerald400,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))

                // Footer Row: Tempo Relativo, Competência e Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.formattedDate,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Atalho para competência
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { onNavigateToMonth() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.competenceMonth,
                                fontSize = 10.sp,
                                color = Cyan400,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        StatusBadge(status = item.status)
                    }
                }
            }
        }
    }
}
