package com.saldolivre.app.ui.screens.activity

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.saldolivre.app.ui.components.StatusBadge
import com.saldolivre.app.ui.theme.*
import kotlin.math.abs

private val SkyBlue = Color(0xFF38BDF8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentActivityScreen(
    viewModel: RecentActivityViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMonth: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
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
                                .background(SkyBlue.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .border(1.dp, SkyBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = SkyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Histórico de Atividades",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Auditoria cronológica de lançamentos e rateios",
                                style = Typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Faixa de Métricas Rápidas (4 KPIs Compactos)
            item {
                ActivityMetricsRibbon(
                    totalCount = uiState.totalCount,
                    last7DaysCount = uiState.last7DaysCount,
                    thirdPartyCount = uiState.withThirdPartyCount,
                    friendsCount = uiState.friendsCount
                )
            }

            // 2. Barra de Busca CircleShape + Pílulas de Filtro CircleShape (Estilo Extrato)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = {
                            Text(
                                text = "Buscar por descrição, categoria, cartão ou amigo...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpar",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityFilter.entries.forEach { filter ->
                            val selected = uiState.selectedFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) SkyBlue.copy(alpha = 0.2f) else DarkSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) SkyBlue.copy(alpha = 0.5f) else DarkBorder,
                                        CircleShape
                                    )
                                    .clickable { viewModel.onFilterChange(filter) }
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) SkyBlue else TextSecondary,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Lista Cronológica de Auditoria
            if (uiState.filteredItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Nenhum registro encontrado",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Tente ajustar o termo de busca ou o filtro selecionado.",
                                style = Typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "LINHA DO TEMPO DE CADASTROS (${uiState.filteredItems.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                items(uiState.filteredItems, key = { it.id }) { item ->
                    TimelineActivityCard(
                        item = item,
                        onOpenMonth = {
                            viewModel.selectMonth(item.competenceMonth)
                            onNavigateToMonth(item.competenceMonth)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ActivityMetricsRibbon(
    totalCount: Int,
    last7DaysCount: Int,
    thirdPartyCount: Int,
    friendsCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricBox(
            label = "Total",
            value = totalCount.toString(),
            color = SkyBlue,
            modifier = Modifier.weight(1f)
        )
        MetricBox(
            label = "Últ. 7 dias",
            value = last7DaysCount.toString(),
            color = Emerald400,
            modifier = Modifier.weight(1f)
        )
        MetricBox(
            label = "Terceiros",
            value = thirdPartyCount.toString(),
            color = Amber400,
            modifier = Modifier.weight(1f)
        )
        MetricBox(
            label = "Amigos",
            value = friendsCount.toString(),
            color = Cyan400,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimelineActivityCard(
    item: ActivityItemUi,
    onOpenMonth: () -> Unit
) {
    val categoryDetail = remember(item.category) { CategoryRegistry.get(item.category) }
    val amountColor = when {
        item.isIncome -> Emerald400
        item.isRefund -> Cyan400
        else -> TextPrimary
    }
    val hasDiffImpact = abs(abs(item.amount) - abs(item.myImpact)) > 0.01

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Linha 1: Ícone + Título + Categoria + Valor + Impacto Pessoal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(categoryDetail.color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, categoryDetail.color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryDetail.icon,
                            contentDescription = null,
                            tint = categoryDetail.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            item.installmentTag?.let { tag ->
                                Box(
                                    modifier = Modifier
                                        .background(Purple500.copy(alpha = 0.16f), CircleShape)
                                        .border(1.dp, Purple500.copy(alpha = 0.4f), CircleShape)
                                        .padding(horizontal = 7.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Purple400,
                                        style = LocalTextStyle.current.copy(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                append(item.description)
                                append(" • ")
                                append(item.cardName?.let { "Cartão $it" } ?: item.paymentMethodLabel)
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
                        text = "${if (item.isIncome) "+" else ""}${FinanceEngine.formatCurrency(item.amount)}",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = amountColor
                    )
                    if (hasDiffImpact && !item.isIncome) {
                        Text(
                            text = "Meu impacto: ${FinanceEngine.formatCurrency(item.myImpact)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.myImpact == 0.0) Emerald400 else Cyan400
                        )
                    }
                }
            }

            // Linha 2: Duplo Badge de Datas (Data de Cadastro vs Data de Vencimento/Competência) + Rateios
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge 1: Data/Hora do Cadastro (Auditoria)
                Box(
                    modifier = Modifier
                        .background(SkyBlue.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, SkyBlue.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = SkyBlue,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Cadastrado: ${item.formattedDate}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SkyBlue,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }

                // Badge 2: Vencimento e Mês de Competência
                Box(
                    modifier = Modifier
                        .background(DarkBackground.copy(alpha = 0.65f), CircleShape)
                        .border(1.dp, DarkBorder, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "Venc: ${FinanceEngine.formatFullDate(item.dueDate)} • ${FinanceEngine.formatMonthLabel(item.competenceMonth)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }

                // Badge de Terceiro ou Amigo se existir
                item.thirdPartyName?.takeIf { it.isNotBlank() }?.let { third ->
                    Box(
                        modifier = Modifier
                            .background(Amber500.copy(alpha = 0.14f), CircleShape)
                            .border(1.dp, Amber500.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Terceiro: $third",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber400,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }

                item.friendName?.takeIf { it.isNotBlank() }?.let { friend ->
                    Box(
                        modifier = Modifier
                            .background(Cyan500.copy(alpha = 0.14f), CircleShape)
                            .border(1.dp, Cyan500.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Amigo: $friend (${(item.friendSharePct ?: 50.0).toInt()}%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan400,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = DarkBorder.copy(alpha = 0.55f))

            // Linha 3: StatusBadge + Botão "Ver no Mês"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = item.status)

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenMonth)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Ver em ${FinanceEngine.formatMonthLabel(item.competenceMonth)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkyBlue
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = SkyBlue,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
