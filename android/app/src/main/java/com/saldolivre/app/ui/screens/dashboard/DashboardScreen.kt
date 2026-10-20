package com.saldolivre.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.saldolivre.app.ui.components.KpiCard
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.components.TransactionItem
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToReceivables: () -> Unit,
    onNavigateToCards: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val summary = uiState.summary
    var showUserMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Emerald500.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Saldo Livre",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Finanças Pessoais & Casal",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    // Profile demo toggle button
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                                .clickable { showUserMenu = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Cyan500, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.currentUser.displayName.take(1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkBackground
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.currentUser.displayName.split(" ").firstOrNull() ?: "",
                                style = Typography.labelLarge,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Alternar Perfil",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showUserMenu,
                            onDismissRequest = { showUserMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            Text(
                                text = "Alternar Visão Demo:",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            uiState.allUsers.forEach { user ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = user.displayName,
                                            color = if (user.uid == uiState.currentUser.uid) Emerald400 else TextPrimary,
                                            fontWeight = if (user.uid == uiState.currentUser.uid) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.switchUser(user.uid)
                                        showUserMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddTransaction,
                containerColor = Emerald500,
                contentColor = DarkBackground,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nova Transação"
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month Selector
            item {
                MonthSelector(
                    selectedMonth = uiState.selectedMonth,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onCurrentMonth = { viewModel.currentMonth() }
                )
            }

            // Hero KPI Card: Saldo Livre Real do Mês
            item {
                val bannerShape = RoundedCornerShape(20.dp)
                val isNegative = summary.freeBalance < 0

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bannerShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    if (isNegative) Rose500.copy(alpha = 0.2f) else Emerald500.copy(alpha = 0.2f),
                                    DarkSurface
                                )
                            )
                        )
                        .border(
                            1.dp,
                            if (isNegative) Rose500.copy(alpha = 0.5f) else Emerald500.copy(alpha = 0.5f),
                            bannerShape
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SALDO LIVRE REAL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = if (isNegative) Rose400 else Emerald400
                            )

                            Box(
                                modifier = Modifier
                                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Receitas: ${FinanceEngine.formatCurrency(summary.income)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = FinanceEngine.formatCurrency(summary.freeBalance),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNegative) Rose400 else Emerald400
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Daily Safe Limit Banner
                        val dailyLimit = summary.dailyLimit
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Cyan400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Disponível por Dia:",
                                        style = Typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }

                                Text(
                                    text = if (dailyLimit != null) "${FinanceEngine.formatCurrency(dailyLimit)} /dia" else "—",
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dailyLimit != null && dailyLimit > 0) Cyan400 else Rose400
                                )
                            }
                        }
                    }
                }
            }

            // 2x2 Grid of Secondary KPIs
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiCard(
                            title = "Comprometido",
                            value = FinanceEngine.formatCurrency(summary.committed),
                            icon = Icons.Default.Schedule,
                            accentColor = Amber500,
                            subtitle = "Prev: ${FinanceEngine.formatCurrency(summary.committedEstimated)}",
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "Já Pago",
                            value = FinanceEngine.formatCurrency(summary.paid),
                            icon = Icons.Default.CheckCircle,
                            accentColor = Emerald500,
                            subtitle = "Liquidado no mês",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiCard(
                            title = "A Receber",
                            value = FinanceEngine.formatCurrency(summary.receivableTotal),
                            icon = Icons.Default.Group,
                            accentColor = Purple500,
                            subtitle = "Cartão / Terceiros",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToReceivables
                        )
                        KpiCard(
                            title = "Investimentos",
                            value = FinanceEngine.formatCurrency(summary.totalInvested),
                            icon = Icons.Default.TrendingUp,
                            accentColor = Cyan500,
                            subtitle = "Aportes do mês",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Actions: Cartões Compartilhados & A Receber
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToCards,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Meus Cartões", style = Typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onNavigateToReceivables,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Purple400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "A Receber", style = Typography.labelLarge)
                    }
                }
            }

            // Section Header: Transações Recentes
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transações Recentes",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Ver todas",
                        style = Typography.labelLarge,
                        color = Emerald400,
                        modifier = Modifier.clickable { onNavigateToTransactions() }
                    )
                }
            }

            // Recent Transactions List
            if (uiState.recentTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma transação neste mês.",
                            color = TextMuted,
                            style = Typography.bodyMedium
                        )
                    }
                }
            } else {
                items(uiState.recentTransactions) { tx ->
                    TransactionItem(
                        transaction = tx,
                        onClick = { onNavigateToTransactions() }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
