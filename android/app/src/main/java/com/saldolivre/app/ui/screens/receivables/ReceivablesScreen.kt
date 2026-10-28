package com.saldolivre.app.ui.screens.receivables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.MonthSummary
import com.saldolivre.app.data.model.ReceivableItem
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.theme.*

private val DarkAmberHeroTint = Color(0xFF78350F)
private val DarkRoseHeroTint = Color(0xFF4C0519)

private val ReceivablesHeroBrush = Brush.verticalGradient(
    colors = listOf(DarkAmberHeroTint.copy(alpha = 0.45f), DarkSurface)
)

private val PayablesHeroBrush = Brush.verticalGradient(
    colors = listOf(DarkRoseHeroTint.copy(alpha = 0.45f), DarkSurface)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivablesScreen(
    viewModel: ReceivablesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
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
                                    .background(Amber500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Amber500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Acertos & Reembolsos",
                                    style = Typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Divisões 50/50, amigos da rede e terceiros",
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

                // MonthSelector integrado
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    MonthSelector(
                        selectedMonth = uiState.selectedMonth,
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() },
                        onCurrentMonth = { viewModel.goToCurrentMonth() }
                    )
                }

                // TabRow estilo CardsAndInvoicesScreen
                val selectedIndex = if (uiState.activeTab == SettlementsTab.RECEIVABLES) 0 else 1
                TabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = DarkBackground,
                    contentColor = Amber400,
                    indicator = { tabPositions ->
                        if (selectedIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedIndex])
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                                height = 3.dp,
                                color = if (selectedIndex == 0) Amber400 else Rose400
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.7f))
                    }
                ) {
                    Tab(
                        selected = selectedIndex == 0,
                        onClick = { viewModel.selectTab(SettlementsTab.RECEIVABLES) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallReceived,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "A Receber (${uiState.items.size})",
                                    fontWeight = if (selectedIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        },
                        selectedContentColor = Amber400,
                        unselectedContentColor = TextSecondary
                    )

                    Tab(
                        selected = selectedIndex == 1,
                        onClick = { viewModel.selectTab(SettlementsTab.PAYABLES) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallMade,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Eu Devo (${uiState.payables.size})",
                                    fontWeight = if (selectedIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
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
            // 1. Hero Banner Consolidado do Mês
            item {
                SettlementsHeroBanner(
                    summary = uiState.summary,
                    activeTab = uiState.activeTab
                )
            }

            if (uiState.activeTab == SettlementsTab.RECEIVABLES) {
                if (uiState.items.isEmpty()) {
                    item {
                        EmptySettlementState(
                            title = "Tudo em dia!",
                            subtitle = "Ninguém possui reembolsos pendentes com você em ${FinanceEngine.formatMonthLabel(uiState.selectedMonth)}.",
                            accentColor = Emerald400
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "VALORES PENDENTES DE REEMBOLSO (${uiState.items.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                    items(uiState.items, key = { "${it.transactionId}_${it.name}" }) { item ->
                        ReceivableCardItem(
                            item = item,
                            onMarkReimbursed = { viewModel.markAsReimbursed(item) }
                        )
                    }
                }
            } else {
                if (uiState.payables.isEmpty()) {
                    item {
                        EmptySettlementState(
                            title = "Você não deve nada!",
                            subtitle = "Nenhuma despesa em cartões de parceiro(a) ou amigos pendente de acerto neste mês.",
                            accentColor = Emerald400
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "MINHA COTA EM CARTÕES/CONTAS DE OUTROS (${uiState.payables.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                    items(uiState.payables, key = { "${it.transactionId}_${it.myUid}" }) { item ->
                        PayableCardItem(
                            item = item,
                            onMarkSettled = { viewModel.markPayableAsSettled(item) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettlementsHeroBanner(
    summary: MonthSummary,
    activeTab: SettlementsTab
) {
    val isReceivables = activeTab == SettlementsTab.RECEIVABLES
    val accentColor = if (isReceivables) Amber400 else Rose400
    val borderColor = if (isReceivables) Amber500.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f)
    val brush = if (isReceivables) ReceivablesHeroBrush else PayablesHeroBrush

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = brush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isReceivables) "TOTAL A RECEBER NO MÊS" else "TOTAL QUE EU DEVO NO MÊS",
                    style = Typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.16f), CircleShape)
                        .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isReceivables) "Não afeta seu Saldo Livre" else "Já deduzido do Saldo Livre",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }

            Text(
                text = if (isReceivables) {
                    "+${FinanceEngine.formatCurrency(summary.receivableTotal)}"
                } else {
                    "-${FinanceEngine.formatCurrency(summary.payableToOthers)}"
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 3 Pilares de Rateio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillarBox(
                    label = "Parceiro(a)",
                    value = FinanceEngine.formatCurrency(summary.receivableFromPartner),
                    color = Cyan400,
                    modifier = Modifier.weight(1f)
                )
                PillarBox(
                    label = "Amigos",
                    value = FinanceEngine.formatCurrency(summary.receivableFromFriends),
                    color = Emerald400,
                    modifier = Modifier.weight(1f)
                )
                PillarBox(
                    label = "Terceiros",
                    value = FinanceEngine.formatCurrency(summary.receivableFromExternal),
                    color = Amber400,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PillarBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReceivableCardItem(
    item: ReceivableItem,
    onMarkReimbursed: () -> Unit
) {
    val isExternal = item.type == "EXTERNAL"
    val badgeColor = if (isExternal) Amber400 else Cyan400
    val badgeLabel = if (isExternal) "Terceiro Externo" else "Parceiro / Amigo"

    Surface(
        shape = RoundedCornerShape(16.dp),
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
                            .size(42.dp)
                            .background(badgeColor.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExternal) Icons.Default.PersonOutline else Icons.Default.People,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.description,
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.12f), CircleShape)
                                    .border(1.dp, badgeColor.copy(alpha = 0.3f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${item.name} • $badgeLabel",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "+${FinanceEngine.formatCurrency(item.amount)}",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Amber400
                    )
                    Text(
                        text = "Vence ${FinanceEngine.formatShortDate(item.dueDate)}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            HorizontalDivider(color = DarkBorder.copy(alpha = 0.55f))

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
                        imageVector = if (!item.cardName.isNullOrBlank()) Icons.Default.CreditCard else Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = item.cardName?.let { "Cartão $it" } ?: "Pago via Conta / Pix",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                FilledTonalButton(
                    onClick = onMarkReimbursed,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Emerald500.copy(alpha = 0.18f),
                        contentColor = Emerald400
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dar Baixa",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PayableCardItem(
    item: PayableItem,
    onMarkSettled: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
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
                            .size(42.dp)
                            .background(Rose500.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            .border(1.dp, Rose500.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = Rose400,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.description,
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Rose500.copy(alpha = 0.12f), CircleShape)
                                    .border(1.dp, Rose500.copy(alpha = 0.3f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Pagar a ${item.creditorName}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Rose400,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(DarkSurfaceVariant, CircleShape)
                                    .border(1.dp, DarkBorder, CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Já deduzido do Saldo",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${FinanceEngine.formatCurrency(item.amount)}",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Rose400
                    )
                    Text(
                        text = "Vence ${FinanceEngine.formatShortDate(item.dueDate)}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            HorizontalDivider(color = DarkBorder.copy(alpha = 0.55f))

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
                        imageVector = if (!item.cardName.isNullOrBlank()) Icons.Default.CreditCard else Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = item.cardName?.let { "Cartão $it" } ?: "Pago por ${item.creditorName}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                FilledTonalButton(
                    onClick = onMarkSettled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Emerald500.copy(alpha = 0.18f),
                        contentColor = Emerald400
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Já Reembolsei",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySettlementState(
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(accentColor.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = Typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
