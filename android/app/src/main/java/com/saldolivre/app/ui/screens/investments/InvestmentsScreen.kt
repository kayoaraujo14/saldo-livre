package com.saldolivre.app.ui.screens.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(
    viewModel: InvestmentsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredHistory = remember(uiState.allInvestments, uiState.historyFilter) {
        if (uiState.historyFilter == "all") uiState.allInvestments
        else uiState.allInvestments.filter { (it.subcategory ?: "Outros Investimentos") == uiState.historyFilter }
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

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
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Investimentos & Patrimônio",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Purple500.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = FinanceEngine.formatMonthLabel(uiState.selectedMonth),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Purple400
                                )
                            }
                        }
                        Text(
                            text = "Caixinhas seguras, ativos de risco e evolução",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.openAddAporte() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Aporte", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            // 1. KPI Cards Grid
            item {
                InvestmentsKpiGrid(
                    monthInvested = uiState.totalInvestedThisMonth,
                    allInvested = uiState.totalInvestedAllTime,
                    totalCurrentBalance = uiState.totalCurrentBalance,
                    monthlyIncome = uiState.monthlyIncome,
                    target = uiState.monthlyTarget,
                    isEditingTarget = uiState.isEditingTarget,
                    targetInput = uiState.targetInput,
                    onStartEditTarget = { viewModel.startEditingTarget() },
                    onCancelEditTarget = { viewModel.cancelEditingTarget() },
                    onTargetInputChange = { viewModel.setTargetInput(it) },
                    onSaveTarget = { viewModel.saveTarget() }
                )
            }

            // 2. Tab Navigation Pills
            item {
                InvestmentsTabPills(
                    activeTab = uiState.activeTab,
                    onSelectTab = { viewModel.setTab(it) }
                )
            }

            // 3. Tab Contents
            when (uiState.activeTab) {
                InvestmentTab.OVERVIEW -> {
                    // Caixinhas & Reservas Seguras
                    item {
                        SectionHeader(
                            title = "Caixinhas & Reservas Seguras",
                            subtitle = "Renda Fixa, Tesouro Selic e Reserva de Emergência",
                            icon = Icons.Default.Shield,
                            accentColor = Cyan400
                        )
                    }

                    val safeAssets = uiState.assets.filter { it.bucketType == InvestmentBucketType.SAFE_RESERVE }
                    items(safeAssets, key = { it.subcategory }) { asset ->
                        InvestmentAssetCard(
                            asset = asset,
                            onAddAporte = { viewModel.openAddAporte(asset) },
                            onResgate = { viewModel.openResgate(asset) },
                            onEditBalance = { viewModel.openEditBalance(asset) }
                        )
                    }

                    // Investimentos de Risco & Renda Variável
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Investimentos de Risco & Renda Variável",
                            subtitle = "Ações B3, FIIs, Criptomoedas e Ativos com Oscilação",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = Purple400
                        )
                    }

                    val riskAssets = uiState.assets.filter { it.bucketType == InvestmentBucketType.RISK_VARIABLE }
                    items(riskAssets, key = { it.subcategory }) { asset ->
                        InvestmentAssetCard(
                            asset = asset,
                            onAddAporte = { viewModel.openAddAporte(asset) },
                            onResgate = { viewModel.openResgate(asset) },
                            onEditBalance = { viewModel.openEditBalance(asset) }
                        )
                    }

                    // Lançamentos do Mês
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        MonthInvestmentsSection(
                            monthTxs = uiState.monthInvestments,
                            monthLabel = FinanceEngine.formatMonthLabel(uiState.selectedMonth)
                        )
                    }
                }

                InvestmentTab.CALCULATOR -> {
                    item {
                        CompoundInterestSimulator(
                            onSimulate = { init, month, rate, years ->
                                viewModel.simulateCompoundInterest(init, month, rate, years)
                            }
                        )
                    }
                }

                InvestmentTab.RULE_50_30_20 -> {
                    item {
                        Rule503020Section(data = uiState.rule503020)
                    }
                }

                InvestmentTab.HISTORY -> {
                    item {
                        HistoryFilterPills(
                            currentFilter = uiState.historyFilter,
                            allAssets = uiState.assets,
                            onSelectFilter = { viewModel.setHistoryFilter(it) }
                        )
                    }

                    if (filteredHistory.isEmpty()) {
                        item {
                            EmptyHistoryBox()
                        }
                    } else {
                        items(filteredHistory, key = { it.id }) { tx ->
                            InvestmentHistoryItemCard(tx = tx)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Modal: Novo Aporte
    if (uiState.isAddAporteOpen) {
        AporteFormDialog(
            preselectedSubcat = uiState.selectedAssetForAction?.subcategory,
            onDismiss = { viewModel.closeAddAporte() },
            onSubmit = { sub, amt, desc, date -> viewModel.submitAporte(sub, amt, desc, date) }
        )
    }

    // Modal: Resgatar
    if (uiState.isResgateOpen) {
        ResgateFormDialog(
            preselectedSubcat = uiState.selectedAssetForAction?.subcategory,
            onDismiss = { viewModel.closeResgate() },
            onSubmit = { sub, amt, desc, date -> viewModel.submitResgate(sub, amt, desc, date) }
        )
    }

    // Modal: Editar Saldo Atual
    if (uiState.isEditBalanceOpen && uiState.selectedAssetForAction != null) {
        val asset = uiState.selectedAssetForAction!!
        EditBalanceDialog(
            asset = asset,
            onDismiss = { viewModel.closeEditBalance() },
            onSubmit = { newBal -> viewModel.submitEditBalance(asset.subcategory, newBal) }
        )
    }
}

// -------------------------------------------------------------
// Componente: Header de Seção
// -------------------------------------------------------------
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
        }
    }
}

// -------------------------------------------------------------
// 1. Grid de 4 KPIs
// -------------------------------------------------------------
@Composable
private fun InvestmentsKpiGrid(
    monthInvested: Double,
    allInvested: Double,
    totalCurrentBalance: Double,
    monthlyIncome: Double,
    target: Double,
    isEditingTarget: Boolean,
    targetInput: String,
    onStartEditTarget: () -> Unit,
    onCancelEditTarget: () -> Unit,
    onTargetInputChange: (String) -> Unit,
    onSaveTarget: () -> Unit
) {
    val savingsRate = if (monthlyIncome > 0) (monthInvested / monthlyIncome) * 100 else 0.0
    val targetProgress = if (target > 0) ((monthInvested / target) * 100).coerceIn(0.0, 100.0) else 0.0
    val overallProfit = totalCurrentBalance - allInvested

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Aporte do Mês
            KpiItemCard(
                title = "Aporte do Mês",
                value = FinanceEngine.formatCurrency(monthInvested),
                subtitle = "${savingsRate.toInt()}% da renda do mês",
                icon = Icons.Default.Wallet,
                accentColor = Emerald400,
                modifier = Modifier.weight(1f)
            )

            // Card 2: Saldo Atual / Patrimônio
            KpiItemCard(
                title = "Saldo Patrimonial",
                value = FinanceEngine.formatCurrency(totalCurrentBalance),
                subtitle = if (overallProfit >= 0) "+${FinanceEngine.formatCurrency(overallProfit)} rendimento" else "${FinanceEngine.formatCurrency(overallProfit)} oscilação",
                icon = Icons.Default.AccountBalance,
                accentColor = Cyan400,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Histórico Total Aportado
            KpiItemCard(
                title = "Total Aportado",
                value = FinanceEngine.formatCurrency(allInvested),
                subtitle = "Acumulado em carteira",
                icon = Icons.Default.Savings,
                accentColor = Purple400,
                modifier = Modifier.weight(1f)
            )

            // Card 4: Meta Mensal
            val shape = RoundedCornerShape(16.dp)
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, DarkBorder, shape)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Meta Mensal", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                        Icon(imageVector = Icons.Default.TrackChanges, contentDescription = null, tint = Amber400, modifier = Modifier.size(16.dp))
                    }

                    if (isEditingTarget) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = targetInput,
                                onValueChange = onTargetInputChange,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(44.dp)
                            )
                            IconButton(onClick = onSaveTarget, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Emerald400)
                            }
                            IconButton(onClick = onCancelEditTarget, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().clickable { onStartEditTarget() }
                        ) {
                            Text(
                                text = FinanceEngine.formatCurrency(target),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Icon(Icons.Default.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        }
                    }

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { (targetProgress / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = Amber500,
                        trackColor = DarkSurfaceVariant
                    )
                    Text(
                        text = "${targetProgress.toInt()}% da meta atingida",
                        fontSize = 10.sp,
                        color = Amber400,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiItemCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = modifier.border(1.dp, DarkBorder, shape)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = accentColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// 2. Tabs de Navegação
// -------------------------------------------------------------
@Composable
private fun InvestmentsTabPills(
    activeTab: InvestmentTab,
    onSelectTab: (InvestmentTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabItem(
            title = "Carteira",
            icon = Icons.Default.PieChart,
            isSelected = activeTab == InvestmentTab.OVERVIEW,
            modifier = Modifier.weight(1f),
            onClick = { onSelectTab(InvestmentTab.OVERVIEW) }
        )
        TabItem(
            title = "Simulador",
            icon = Icons.Default.Calculate,
            isSelected = activeTab == InvestmentTab.CALCULATOR,
            modifier = Modifier.weight(1f),
            onClick = { onSelectTab(InvestmentTab.CALCULATOR) }
        )
        TabItem(
            title = "50/30/20",
            icon = Icons.Default.AutoAwesome,
            isSelected = activeTab == InvestmentTab.RULE_50_30_20,
            modifier = Modifier.weight(1f),
            onClick = { onSelectTab(InvestmentTab.RULE_50_30_20) }
        )
        TabItem(
            title = "Histórico",
            icon = Icons.Default.History,
            isSelected = activeTab == InvestmentTab.HISTORY,
            modifier = Modifier.weight(1f),
            onClick = { onSelectTab(InvestmentTab.HISTORY) }
        )
    }
}

@Composable
private fun TabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Purple500 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else TextMuted
            )
        }
    }
}

// -------------------------------------------------------------
// 3. Card de Ativo Individual (Caixinha ou Ativo de Risco)
// -------------------------------------------------------------
@Composable
private fun InvestmentAssetCard(
    asset: InvestmentAsset,
    onAddAporte: () -> Unit,
    onResgate: () -> Unit,
    onEditBalance: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val isProfit = asset.profitOrLoss >= 0

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Dot color + Name + Edit Balance button
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
                            .size(10.dp)
                            .background(asset.color, CircleShape)
                    )
                    Text(
                        text = asset.subcategory,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onEditBalance,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar Saldo",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Values Row: Saldo Atual & Total Aportado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Saldo Atual", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = FinanceEngine.formatCurrency(asset.currentBalance),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Total Aportado", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = FinanceEngine.formatCurrency(asset.totalInvestedAllTime),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            // Rendimento / Oscilação Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isProfit) Emerald500.copy(alpha = 0.15f) else Rose500.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isProfit) "+${FinanceEngine.formatCurrency(asset.profitOrLoss)} (+${asset.profitPercentage}%)"
                            else "${FinanceEngine.formatCurrency(asset.profitOrLoss)} (${asset.profitPercentage}%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfit) Emerald400 else Rose400
                        )
                    }

                    if (asset.totalInvestedThisMonth > 0) {
                        Text(
                            text = "+${FinanceEngine.formatCurrency(asset.totalInvestedThisMonth)} no mês",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                // Action Buttons: Aporte (+) e Resgate (-)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onResgate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Resgatar", fontSize = 10.sp, color = TextSecondary)
                    }

                    Button(
                        onClick = onAddAporte,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Aportar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. Lançamentos do Mês
// -------------------------------------------------------------
@Composable
private fun MonthInvestmentsSection(
    monthTxs: List<Transaction>,
    monthLabel: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Lançamentos em $monthLabel", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "${monthTxs.size} movimentação(ões)", fontSize = 11.sp, color = TextMuted)
            }

            if (monthTxs.isEmpty()) {
                Text(
                    text = "Nenhum aporte ou resgate neste mês ainda.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                monthTxs.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBackground.copy(alpha = 0.5f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = tx.description, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "${tx.dueDate} · ${tx.subcategory ?: "Investimentos"}", fontSize = 10.sp, color = TextMuted)
                        }
                        Text(
                            text = if (tx.type == TransactionType.INCOME) "-${FinanceEngine.formatCurrency(tx.effectiveAmount)}"
                            else "+${FinanceEngine.formatCurrency(tx.effectiveAmount)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tx.type == TransactionType.INCOME) Rose400 else Emerald400
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. Simulador de Juros Compostos
// -------------------------------------------------------------
@Composable
private fun CompoundInterestSimulator(
    onSimulate: (Double, Double, Double, Int) -> CompoundSimulationResult
) {
    var initialAmount by remember { mutableStateOf("1000") }
    var monthlyContribution by remember { mutableStateOf("500") }
    var annualRate by remember { mutableStateOf("11.0") }
    var years by remember { mutableStateOf("10") }

    val simulation = remember(initialAmount, monthlyContribution, annualRate, years) {
        val init = initialAmount.toDoubleOrNull() ?: 1000.0
        val month = monthlyContribution.toDoubleOrNull() ?: 500.0
        val rate = annualRate.toDoubleOrNull() ?: 11.0
        val yr = years.toIntOrNull() ?: 10
        onSimulate(init, month, rate, yr)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Simulador de Futuro & Bola de Neve",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Descubra como o efeito dos juros compostos transforma pequenos aportes regulares.",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = initialAmount,
                        onValueChange = { initialAmount = it },
                        label = { Text("Aporte Inicial (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = monthlyContribution,
                        onValueChange = { monthlyContribution = it },
                        label = { Text("Aporte Mensal (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = annualRate,
                        onValueChange = { annualRate = it },
                        label = { Text("Taxa (% a.a.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = years,
                        onValueChange = { years = it },
                        label = { Text("Prazo (anos)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Benchmarks
                Text(text = "Benchmarks de Mercado:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presets = listOf(
                        "CDI / Selic (10.75%)" to "10.75",
                        "Tesouro IPCA+ (12%)" to "12.0",
                        "Ações / FIIs (14%)" to "14.0",
                        "Conservador (8%)" to "8.0"
                    )
                    items(presets) { (label, rateVal) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { annualRate = rateVal }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = label, fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Result Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiItemCard(
                title = "Valor Final Acumulado",
                value = FinanceEngine.formatCurrency(simulation.futureValue),
                subtitle = "Patrimônio em ${years} anos",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accentColor = Emerald400,
                modifier = Modifier.weight(1f)
            )

            KpiItemCard(
                title = "Juros Compostos",
                value = FinanceEngine.formatCurrency(simulation.totalInterest),
                subtitle = "Rendimento gerado",
                icon = Icons.Default.AutoAwesome,
                accentColor = Purple400,
                modifier = Modifier.weight(1f)
            )
        }

        // Yearly Timeline
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Evolução Ano a Ano", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Purple400, CircleShape))
                            Text("Aportado", fontSize = 10.sp, color = TextMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Emerald400, CircleShape))
                            Text("Juros", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }

                simulation.yearlyData.forEach { data ->
                    val maxVal = simulation.futureValue.coerceAtLeast(1.0)
                    val investedWeight = ((data.invested / maxVal) * 100).toFloat().coerceIn(0.01f, 100f)
                    val interestWeight = ((data.interest / maxVal) * 100).toFloat().coerceIn(0.01f, 100f)
                    val remainingWeight = (100f - investedWeight - interestWeight).coerceAtLeast(0f)

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Ano ${data.year}", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text(text = FinanceEngine.formatCurrency(data.total), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(DarkSurfaceVariant)
                        ) {
                            Box(modifier = Modifier.weight(investedWeight).fillMaxHeight().background(Purple400))
                            Box(modifier = Modifier.weight(interestWeight).fillMaxHeight().background(Emerald400))
                            if (remainingWeight > 0f) {
                                Box(modifier = Modifier.weight(remainingWeight).fillMaxHeight().background(DarkSurfaceVariant))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. Diagnóstico Regra 50 / 30 / 20
// -------------------------------------------------------------
@Composable
private fun Rule503020Section(data: Rule503020Data) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Diagnóstico 50 / 30 / 20", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Equilíbrio recomendado por educadores financeiros", fontSize = 11.sp, color = TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Renda Computada", fontSize = 10.sp, color = TextMuted)
                    Text(text = FinanceEngine.formatCurrency(data.income), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald400)
                }
            }
        }

        // 3 Pillars
        RulePillarCard(
            title = "50% Necessidades Essenciais",
            description = "Moradia, Alimentação, Saúde e Transporte",
            targetAmount = data.targetNeeds,
            actualAmount = data.realNeeds,
            actualPct = data.needsPct,
            targetPct = 50.0,
            accentColor = Color(0xFF818CF8)
        )

        RulePillarCard(
            title = "30% Estilo de Vida & Lazer",
            description = "Compras, Passeios, Delivery e Streaming",
            targetAmount = data.targetWants,
            actualAmount = data.realWants,
            actualPct = data.wantsPct,
            targetPct = 30.0,
            accentColor = Cyan400
        )

        RulePillarCard(
            title = "20% Investimentos & Futuro",
            description = "Aportes em Caixinhas, Renda Fixa e Ações",
            targetAmount = data.targetInvest,
            actualAmount = data.realInvest,
            actualPct = data.investPct,
            targetPct = 20.0,
            accentColor = Purple400
        )
    }
}

@Composable
private fun RulePillarCard(
    title: String,
    description: String,
    targetAmount: Double,
    actualAmount: Double,
    actualPct: Double,
    targetPct: Double,
    accentColor: Color
) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, shape)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "Teto: ${FinanceEngine.formatCurrency(targetAmount)}", fontSize = 11.sp, color = TextMuted)
            }
            Text(text = description, fontSize = 10.sp, color = TextMuted)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = FinanceEngine.formatCurrency(actualAmount), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "${actualPct.toInt()}% da renda", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
            }

            val progressRatio = (actualPct / (targetPct * 2.0)).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = accentColor,
                trackColor = DarkSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------
// 7. Histórico & Filtros
// -------------------------------------------------------------
@Composable
private fun HistoryFilterPills(
    currentFilter: String,
    allAssets: List<InvestmentAsset>,
    onSelectFilter: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentFilter == "all") Purple500 else DarkSurfaceVariant)
                    .clickable { onSelectFilter("all") }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = "Todos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (currentFilter == "all") Color.White else TextMuted)
            }
        }

        items(allAssets) { asset ->
            val isSelected = currentFilter == asset.subcategory
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Purple500 else DarkSurfaceVariant)
                    .clickable { onSelectFilter(asset.subcategory) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = asset.subcategory, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (isSelected) Color.White else TextMuted)
            }
        }
    }
}

@Composable
private fun InvestmentHistoryItemCard(tx: Transaction) {
    val shape = RoundedCornerShape(14.dp)
    val isRedemption = tx.type == TransactionType.INCOME

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, shape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                        .size(36.dp)
                        .background((if (isRedemption) Rose500 else Emerald500).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRedemption) Icons.Default.ArrowDownward else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = if (isRedemption) Rose400 else Emerald400,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = tx.description, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "${tx.dueDate} · ${tx.subcategory ?: "Investimentos"}", fontSize = 11.sp, color = TextMuted)
                }
            }

            Text(
                text = if (isRedemption) "-${FinanceEngine.formatCurrency(tx.effectiveAmount)}" else "+${FinanceEngine.formatCurrency(tx.effectiveAmount)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRedemption) Rose400 else Emerald400
            )
        }
    }
}

@Composable
private fun EmptyHistoryBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface.copy(alpha = 0.5f))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Nenhum lançamento encontrado para este filtro.", fontSize = 12.sp, color = TextMuted)
    }
}

// -------------------------------------------------------------
// 8. Diálogos de Aporte, Resgate e Edição de Saldo
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AporteFormDialog(
    preselectedSubcat: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, String, String) -> Unit
) {
    var subcat by remember { mutableStateOf(preselectedSubcat ?: "Renda Fixa (CDB / Tesouro / LCI / LCA)") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(FinanceEngine.todayKey()) }
    var expandedDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkBackground)
                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Registrar Novo Aporte", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                ExposedDropdownMenuBox(expanded = expandedDropdown, onExpandedChange = { expandedDropdown = it }) {
                    OutlinedTextField(
                        value = subcat,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Classe / Ativo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        InvestmentsViewModel.ASSET_DEFINITIONS.forEach { (name, _, _) ->
                            DropdownMenuItem(
                                text = { Text(name, color = TextPrimary) },
                                onClick = { subcat = name; expandedDropdown = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor do Aporte (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição (Opcional)") },
                    placeholder = { Text("Ex: Compra CDB 110%, Aporte Tesouro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Data (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            val num = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (num > 0.0) onSubmit(subcat, num, description.trim(), date.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirmar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResgateFormDialog(
    preselectedSubcat: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, String, String) -> Unit
) {
    var subcat by remember { mutableStateOf(preselectedSubcat ?: "Reserva de Emergência") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(FinanceEngine.todayKey()) }
    var expandedDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkBackground)
                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Resgatar Aplicação", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                ExposedDropdownMenuBox(expanded = expandedDropdown, onExpandedChange = { expandedDropdown = it }) {
                    OutlinedTextField(
                        value = subcat,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Resgatar de") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        InvestmentsViewModel.ASSET_DEFINITIONS.forEach { (name, _, _) ->
                            DropdownMenuItem(
                                text = { Text(name, color = TextPrimary) },
                                onClick = { subcat = name; expandedDropdown = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor a Resgatar (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Motivo / Descrição (Opcional)") },
                    placeholder = { Text("Ex: Resgate para cobrir imprevisto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            val num = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (num > 0.0) onSubmit(subcat, num, description.trim(), date.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Resgatar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBalanceDialog(
    asset: InvestmentAsset,
    onDismiss: () -> Unit,
    onSubmit: (Double) -> Unit
) {
    var newBalanceInput by remember { mutableStateOf(asset.currentBalance.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkBackground)
                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Ajustar Saldo Atual", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    text = "Defina o saldo real atual para ${asset.subcategory}. Isso calculará a rentabilidade ou oscilação de mercado.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                OutlinedTextField(
                    value = newBalanceInput,
                    onValueChange = { newBalanceInput = it },
                    label = { Text("Saldo Atual (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            val num = newBalanceInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                            onSubmit(num)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salvar Saldo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
