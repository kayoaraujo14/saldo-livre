package com.saldolivre.app.ui.screens.investments

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.theme.*
import kotlin.math.min

private val DarkPurpleHeroTint = Color(0xFF3B0764)
private val PatrimonyHeroBrush = Brush.verticalGradient(
    colors = listOf(DarkPurpleHeroTint.copy(alpha = 0.55f), DarkSurface)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(
    viewModel: InvestmentsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val safeAssets = remember(uiState.assets) {
        uiState.assets.filter { it.bucketType == InvestmentBucketType.SAFE_RESERVE }
    }
    val riskAssets = remember(uiState.assets) {
        uiState.assets.filter { it.bucketType == InvestmentBucketType.RISK_VARIABLE }
    }
    val filteredHistory = remember(uiState.allInvestments, uiState.historyFilter) {
        if (uiState.historyFilter == "all") uiState.allInvestments
        else uiState.allInvestments.filter { (it.subcategory ?: "Outros Investimentos") == uiState.historyFilter }
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
                                    .background(Purple500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Purple500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = Purple400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Investimentos",
                                    style = Typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Caixinhas, patrimônio e regra 50/30/20",
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
                            onClick = { viewModel.openAddAporte(null) },
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
                                text = "Aportar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                )

                val selectedIndex = when (uiState.activeTab) {
                    InvestmentTab.OVERVIEW -> 0
                    InvestmentTab.CALCULATOR -> 1
                    InvestmentTab.RULE_50_30_20 -> 2
                    InvestmentTab.HISTORY -> 3
                }

                TabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = DarkBackground,
                    contentColor = Purple400,
                    indicator = { tabPositions ->
                        if (selectedIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedIndex])
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                                height = 3.dp,
                                color = Purple400
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.7f))
                    }
                ) {
                    listOf(
                        InvestmentTab.OVERVIEW to "Carteira",
                        InvestmentTab.CALCULATOR to "Simulador",
                        InvestmentTab.RULE_50_30_20 to "50/30/20",
                        InvestmentTab.HISTORY to "Histórico"
                    ).forEachIndexed { idx, (tab, label) ->
                        Tab(
                            selected = selectedIndex == idx,
                            onClick = { viewModel.setTab(tab) },
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            selectedContentColor = Purple400,
                            unselectedContentColor = TextSecondary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // MonthSelector
            item {
                MonthSelector(
                    selectedMonth = uiState.selectedMonth.ifBlank { FinanceEngine.currentMonthKey() },
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onCurrentMonth = { viewModel.goToCurrentMonth() }
                )
            }

            // Hero Patrimonial & Meta Mensal
            item {
                PatrimonyHeroSection(
                    uiState = uiState,
                    onStartEditTarget = { viewModel.startEditingTarget() },
                    onCancelEditTarget = { viewModel.cancelEditingTarget() },
                    onTargetInputChange = { viewModel.setTargetInput(it) },
                    onSaveTarget = { viewModel.saveTarget() },
                    onQuickResgate = { viewModel.openResgate(null) }
                )
            }

            when (uiState.activeTab) {
                InvestmentTab.OVERVIEW -> {
                    item {
                        BucketSectionHeader(
                            title = InvestmentBucketType.SAFE_RESERVE.title,
                            subtitle = InvestmentBucketType.SAFE_RESERVE.subtitle,
                            accentColor = Cyan400
                        )
                    }
                    items(safeAssets, key = { it.subcategory }) { asset ->
                        InvestmentAssetCard(
                            asset = asset,
                            totalPatrimony = uiState.totalCurrentBalance,
                            onAportar = { viewModel.openAddAporte(asset) },
                            onResgatar = { viewModel.openResgate(asset) },
                            onEditBalance = { viewModel.openEditBalance(asset) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        BucketSectionHeader(
                            title = InvestmentBucketType.RISK_VARIABLE.title,
                            subtitle = InvestmentBucketType.RISK_VARIABLE.subtitle,
                            accentColor = Purple400
                        )
                    }
                    items(riskAssets, key = { it.subcategory }) { asset ->
                        InvestmentAssetCard(
                            asset = asset,
                            totalPatrimony = uiState.totalCurrentBalance,
                            onAportar = { viewModel.openAddAporte(asset) },
                            onResgatar = { viewModel.openResgate(asset) },
                            onEditBalance = { viewModel.openEditBalance(asset) }
                        )
                    }
                }

                InvestmentTab.CALCULATOR -> {
                    item {
                        CompoundInterestSimulatorCard(
                            initialPatrimony = uiState.totalCurrentBalance,
                            defaultMonthlyTarget = uiState.monthlyTarget,
                            onSimulate = { init, monthly, rate, years ->
                                viewModel.simulateCompoundInterest(init, monthly, rate, years)
                            }
                        )
                    }
                }

                InvestmentTab.RULE_50_30_20 -> {
                    item {
                        Rule503020SectionCard(
                            data = uiState.rule503020,
                            monthLabel = FinanceEngine.formatMonthLabel(
                                uiState.selectedMonth.ifBlank { FinanceEngine.currentMonthKey() }
                            )
                        )
                    }
                }

                InvestmentTab.HISTORY -> {
                    item {
                        HistoryFilterPills(
                            selectedFilter = uiState.historyFilter,
                            assets = uiState.assets,
                            onSelectFilter = { viewModel.setHistoryFilter(it) }
                        )
                    }

                    if (filteredHistory.isEmpty()) {
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
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "Nenhuma movimentação encontrada",
                                        style = Typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Seus aportes e resgates aparecerão aqui em ordem cronológica.",
                                        style = Typography.bodySmall,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredHistory, key = { it.id }) { tx ->
                            InvestmentHistoryItemCard(tx = tx)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // BottomSheet: Aportar
    if (uiState.isAddAporteOpen) {
        AporteOrResgateBottomSheet(
            isAporte = true,
            initialAsset = uiState.selectedAssetForAction,
            assets = uiState.assets,
            onDismiss = { viewModel.closeAddAporte() },
            onSubmit = { subcat, amount, desc, date ->
                viewModel.submitAporte(subcat, amount, desc, date)
            }
        )
    }

    // BottomSheet: Resgatar
    if (uiState.isResgateOpen) {
        AporteOrResgateBottomSheet(
            isAporte = false,
            initialAsset = uiState.selectedAssetForAction,
            assets = uiState.assets,
            onDismiss = { viewModel.closeResgate() },
            onSubmit = { subcat, amount, desc, date ->
                viewModel.submitResgate(subcat, amount, desc, date)
            }
        )
    }

    // BottomSheet: Ajustar Saldo Patrimonial Real
    if (uiState.isEditBalanceOpen && uiState.selectedAssetForAction != null) {
        EditAssetBalanceBottomSheet(
            asset = uiState.selectedAssetForAction!!,
            onDismiss = { viewModel.closeEditBalance() },
            onSave = { newBal ->
                viewModel.submitEditBalance(uiState.selectedAssetForAction!!.subcategory, newBal)
            }
        )
    }
}

@Composable
private fun PatrimonyHeroSection(
    uiState: InvestmentsUiState,
    onStartEditTarget: () -> Unit,
    onCancelEditTarget: () -> Unit,
    onTargetInputChange: (String) -> Unit,
    onSaveTarget: () -> Unit,
    onQuickResgate: () -> Unit
) {
    val totalProfit = FinanceEngine.round2(uiState.totalCurrentBalance - uiState.totalInvestedAllTime)
    val profitPct = if (uiState.totalInvestedAllTime > 0) {
        FinanceEngine.round2((totalProfit / uiState.totalInvestedAllTime) * 100.0)
    } else 0.0
    val targetProgress = if (uiState.monthlyTarget > 0) {
        min(1f, (uiState.totalInvestedThisMonth / uiState.monthlyTarget).toFloat())
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Purple500.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = PatrimonyHeroBrush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PATRIMÔNIO CONSOLIDADO ATUAL",
                    style = Typography.labelMedium,
                    color = Purple400,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                OutlinedButton(
                    onClick = onQuickResgate,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallReceived,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resgatar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = FinanceEngine.formatCurrency(uiState.totalCurrentBalance),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                if (totalProfit != 0.0) {
                    val isPos = totalProfit >= 0
                    val pColor = if (isPos) Emerald400 else Rose400
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .background(pColor.copy(alpha = 0.16f), CircleShape)
                            .border(1.dp, pColor.copy(alpha = 0.4f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${if (isPos) "+" else ""}$profitPct%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = pColor,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }

            // Sub-containers: Aporte no Mês vs Total Aportado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "APORTE NO MÊS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = FinanceEngine.formatCurrency(uiState.totalInvestedThisMonth),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Emerald400
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "TOTAL APORTADO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = FinanceEngine.formatCurrency(uiState.totalInvestedAllTime),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Cyan400
                    )
                }
            }

            // Barra de Meta Mensal Interativa
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isEditingTarget) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.targetInput,
                            onValueChange = onTargetInputChange,
                            label = { Text("Meta mensal de aporte (R$)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onSaveTarget) {
                            Icon(Icons.Default.Check, contentDescription = "Salvar", tint = Emerald400)
                        }
                        IconButton(onClick = onCancelEditTarget) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = TextSecondary)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Meta Mensal: ${FinanceEngine.formatCurrency(uiState.monthlyTarget)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onStartEditTarget() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${(targetProgress * 100).toInt()}% atingida",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Meta",
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { targetProgress },
                        color = Emerald400,
                        trackColor = DarkSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun BucketSectionHeader(
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Column(modifier = Modifier.padding(start = 4.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            letterSpacing = 0.8.sp
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun InvestmentAssetCard(
    asset: InvestmentAsset,
    totalPatrimony: Double,
    onAportar: () -> Unit,
    onResgatar: () -> Unit,
    onEditBalance: () -> Unit
) {
    val sharePct = if (totalPatrimony > 0) ((asset.currentBalance / totalPatrimony) * 100).toInt() else 0
    val profit = asset.profitOrLoss

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            .size(40.dp)
                            .background(asset.color.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .border(1.dp, asset.color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (asset.bucketType == InvestmentBucketType.SAFE_RESERVE)
                                Icons.Default.Shield else Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = asset.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.subcategory,
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$sharePct% da carteira • Aportado: ${FinanceEngine.formatCurrency(asset.totalInvestedAllTime)}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FinanceEngine.formatCurrency(asset.currentBalance),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    if (profit != 0.0) {
                        val pColor = if (profit >= 0) Emerald400 else Rose400
                        Text(
                            text = "${if (profit >= 0) "+" else ""}${FinanceEngine.formatCurrency(profit)} (${asset.profitPercentage}%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = pColor
                        )
                    }
                }
            }

            HorizontalDivider(color = DarkBorder.copy(alpha = 0.55f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEditBalance,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajustar Saldo", fontSize = 11.sp, color = Cyan400, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (asset.currentBalance > 0) {
                        OutlinedButton(
                            onClick = onResgatar,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Resgatar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    FilledTonalButton(
                        onClick = onAportar,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Emerald500.copy(alpha = 0.18f),
                            contentColor = Emerald400
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aportar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompoundInterestSimulatorCard(
    initialPatrimony: Double,
    defaultMonthlyTarget: Double,
    onSimulate: (Double, Double, Double, Int) -> CompoundSimulationResult
) {
    var initialText by remember { mutableStateOf(initialPatrimony.toInt().toString()) }
    var monthlyText by remember { mutableStateOf(defaultMonthlyTarget.toInt().toString()) }
    var rateText by remember { mutableStateOf("11.5") }
    var years by remember { mutableIntStateOf(5) }

    val result = remember(initialText, monthlyText, rateText, years) {
        val init = initialText.replace(',', '.').toDoubleOrNull() ?: 0.0
        val mon = monthlyText.replace(',', '.').toDoubleOrNull() ?: 0.0
        val rate = rateText.replace(',', '.').toDoubleOrNull() ?: 10.0
        onSimulate(init, mon, rate, years)
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "SIMULADOR DE JUROS COMPOSTOS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Purple400,
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = initialText,
                    onValueChange = { initialText = it },
                    label = { Text("Patrimônio Inicial (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = monthlyText,
                    onValueChange = { monthlyText = it },
                    label = { Text("Aporte Mensal (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Taxa Anual (% a.a.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "Prazo: $years ano(s)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Slider(
                        value = years.toFloat(),
                        onValueChange = { years = it.toInt().coerceIn(1, 30) },
                        valueRange = 1f..30f,
                        colors = SliderDefaults.colors(
                            thumbColor = Purple400,
                            activeTrackColor = Purple500
                        )
                    )
                }
            }

            // Resultado Projetado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                    .border(1.dp, Purple500.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PATRIMÔNIO ESTIMADO EM $years ANO(S)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Purple400
                )
                Text(
                    text = FinanceEngine.formatCurrency(result.futureValue),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Emerald400
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Do seu bolso: ${FinanceEngine.formatCurrency(result.totalInvested)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Juros: +${FinanceEngine.formatCurrency(result.totalInterest)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400
                    )
                }
            }
        }
    }
}

@Composable
private fun Rule503020SectionCard(
    data: Rule503020Data,
    monthLabel: String
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "EQUILÍBRIO ORÇAMENTÁRIO 50 / 30 / 20",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cyan400,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Baseado na renda de ${FinanceEngine.formatCurrency(data.income)} em $monthLabel",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            RulePillarRow(
                title = "50% Necessidades Essenciais",
                realAmount = data.realNeeds,
                targetAmount = data.targetNeeds,
                realPct = data.needsPct,
                targetPct = 50.0,
                accentColor = Cyan400
            )

            RulePillarRow(
                title = "30% Desejos & Estilo de Vida",
                realAmount = data.realWants,
                targetAmount = data.targetWants,
                realPct = data.wantsPct,
                targetPct = 30.0,
                accentColor = Purple400
            )

            RulePillarRow(
                title = "20% Investimentos & Futuro",
                realAmount = data.realInvest,
                targetAmount = data.targetInvest,
                realPct = data.investPct,
                targetPct = 20.0,
                accentColor = Emerald400
            )
        }
    }
}

@Composable
private fun RulePillarRow(
    title: String,
    realAmount: Double,
    targetAmount: Double,
    realPct: Double,
    targetPct: Double,
    accentColor: Color
) {
    val progress = if (targetAmount > 0) min(1f, (realAmount / targetAmount).toFloat()) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${realPct.toInt()}% (Ideal: ${targetPct.toInt()}%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            color = accentColor,
            trackColor = DarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Realizado: ${FinanceEngine.formatCurrency(realAmount)}",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                text = "Teto sugerido: ${FinanceEngine.formatCurrency(targetAmount)}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun HistoryFilterPills(
    selectedFilter: String,
    assets: List<InvestmentAsset>,
    onSelectFilter: (String) -> Unit
) {
    val options = remember(assets) {
        listOf("all" to "Todas") + assets.map { it.subcategory to it.subcategory }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) ->
            val selected = selectedFilter == key
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Purple500.copy(alpha = 0.2f) else DarkSurface)
                    .border(
                        1.dp,
                        if (selected) Purple400 else DarkBorder,
                        CircleShape
                    )
                    .clickable { onSelectFilter(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Purple400 else TextSecondary,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

@Composable
private fun InvestmentHistoryItemCard(
    tx: Transaction
) {
    val isResgate = tx.type == TransactionType.INCOME
    val accent = if (isResgate) Amber400 else Emerald400

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isResgate) Icons.AutoMirrored.Filled.CallReceived else Icons.Default.Savings,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.description,
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tx.subcategory ?: "Investimentos"} • ${FinanceEngine.formatFullDate(tx.dueDate)}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "${if (isResgate) "-" else "+"}${FinanceEngine.formatCurrency(tx.effectiveAmount)}",
                style = Typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AporteOrResgateBottomSheet(
    isAporte: Boolean,
    initialAsset: InvestmentAsset?,
    assets: List<InvestmentAsset>,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, String, String) -> Unit
) {
    var selectedSubcat by remember {
        mutableStateOf(initialAsset?.subcategory ?: assets.firstOrNull()?.subcategory ?: "Reserva de Emergência")
    }
    var amountText by remember { mutableStateOf("") }
    var descText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(FinanceEngine.todayFormatted()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val accent = if (isAporte) Emerald500 else Amber500

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isAporte) "Novo Aporte Patrimonial" else "Resgate de Caixinha / Ativo",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSubcat,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Caixinha / Ativo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    assets.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.subcategory, color = TextPrimary) },
                            onClick = {
                                selectedSubcat = a.subcategory
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Data (DD/MM/AAAA)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = descText,
                onValueChange = { descText = it },
                label = { Text("Descrição opcional") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amt = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onSubmit(selectedSubcat, amt, descText.trim(), dateText.trim())
                    }
                },
                enabled = (amountText.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (isAporte) "Confirmar Aporte" else "Confirmar Resgate",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAssetBalanceBottomSheet(
    asset: InvestmentAsset,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var balanceText by remember(asset) { mutableStateOf(asset.currentBalance.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Ajustar Saldo Patrimonial",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${asset.subcategory} • Atualize com o valor real da corretora/banco para refletir os rendimentos sem afetar seu Saldo Livre do mês.",
                style = Typography.bodySmall,
                color = TextSecondary
            )

            OutlinedTextField(
                value = balanceText,
                onValueChange = { balanceText = it },
                label = { Text("Saldo atual na corretora/banco (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val v = balanceText.replace(',', '.').toDoubleOrNull() ?: asset.currentBalance
                    onSave(v)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Salvar Saldo Atualizado", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
