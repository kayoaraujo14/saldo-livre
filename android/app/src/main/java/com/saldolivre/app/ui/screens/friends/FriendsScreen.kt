package com.saldolivre.app.ui.screens.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.PendingChargeGroup
import com.saldolivre.app.ui.components.MonthSelector
import com.saldolivre.app.ui.theme.*

private val DarkCyanHeroTint = Color(0xFF164E63)
private val StatementHeroBrush = Brush.verticalGradient(
    colors = listOf(DarkCyanHeroTint.copy(alpha = 0.5f), DarkSurface)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
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

    val totalApprovals = uiState.pendingChargeGroups.size + uiState.pendingRequests.size

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
                                    .background(Cyan500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Cyan500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Rede de Amigos",
                                    style = Typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Rateios com aceite prévio e conexões",
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

                val selectedIndex = when (uiState.activeTab) {
                    FriendsTab.APPROVALS -> 0
                    FriendsTab.FRIENDS -> 1
                    FriendsTab.STATEMENT -> 2
                }

                TabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = DarkBackground,
                    contentColor = Cyan400,
                    indicator = { tabPositions ->
                        if (selectedIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedIndex])
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                                height = 3.dp,
                                color = if (selectedIndex == 0 && totalApprovals > 0) Amber400 else Cyan400
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.7f))
                    }
                ) {
                    Tab(
                        selected = selectedIndex == 0,
                        onClick = { viewModel.selectTab(FriendsTab.APPROVALS) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Aprovações ($totalApprovals)",
                                    fontWeight = if (selectedIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        },
                        selectedContentColor = if (totalApprovals > 0) Amber400 else Cyan400,
                        unselectedContentColor = TextSecondary
                    )

                    Tab(
                        selected = selectedIndex == 1,
                        onClick = { viewModel.selectTab(FriendsTab.FRIENDS) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Amigos (${uiState.activeFriends.size})",
                                    fontWeight = if (selectedIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        },
                        selectedContentColor = Cyan400,
                        unselectedContentColor = TextSecondary
                    )

                    Tab(
                        selected = selectedIndex == 2,
                        onClick = { viewModel.selectTab(FriendsTab.STATEMENT) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Balance,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Balanço",
                                    fontWeight = if (selectedIndex == 2) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        },
                        selectedContentColor = Cyan400,
                        unselectedContentColor = TextSecondary
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.activeTab) {
                FriendsTab.APPROVALS -> ApprovalsTabContent(
                    chargeGroups = uiState.pendingChargeGroups,
                    friendRequests = uiState.pendingRequests,
                    cards = uiState.cards,
                    expandedGroupIds = uiState.expandedGroupIds,
                    onToggleExpand = { viewModel.toggleGroupExpand(it) },
                    onRespondCharge = { group, accept -> viewModel.respondCharge(group, accept) },
                    onRespondFriendRequest = { id, accept -> viewModel.respondFriendRequest(id, accept) }
                )

                FriendsTab.FRIENDS -> FriendsTabContent(
                    friends = uiState.activeFriends,
                    isSending = uiState.isSendingInvite,
                    onSendInvite = { email -> viewModel.sendFriendInvite(email) },
                    onRemoveFriend = { id -> viewModel.removeFriend(id) }
                )

                FriendsTab.STATEMENT -> StatementTabContent(
                    selectedMonth = uiState.selectedMonth,
                    receivable = uiState.receivableFromFriends,
                    payable = uiState.payableToOthers,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onCurrentMonth = { viewModel.goToCurrentMonth() }
                )
            }
        }
    }
}

@Composable
private fun ApprovalsTabContent(
    chargeGroups: List<PendingChargeGroup>,
    friendRequests: List<FriendRequestItem>,
    cards: List<Card>,
    expandedGroupIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onRespondCharge: (PendingChargeGroup, Boolean) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (friendRequests.isNotEmpty()) {
            item {
                Text(
                    text = "CONVITES DE AMIZADE RECEBIDOS (${friendRequests.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cyan400,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            items(friendRequests, key = { it.friendshipId }) { req ->
                FriendRequestCard(
                    request = req,
                    onAccept = { onRespondFriendRequest(req.friendshipId, true) },
                    onReject = { onRespondFriendRequest(req.friendshipId, false) }
                )
            }
        }

        item {
            Text(
                text = "RATEIOS AGUARDANDO SEU ACEITE (${chargeGroups.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (chargeGroups.isNotEmpty()) Amber400 else TextMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = if (friendRequests.isNotEmpty()) 8.dp else 0.dp, start = 4.dp)
            )
        }

        if (chargeGroups.isEmpty()) {
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
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(Emerald500.copy(alpha = 0.14f), CircleShape)
                                .border(1.dp, Emerald500.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "Nenhuma cobrança pendente!",
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Quando um amigo dividir uma despesa à vista ou parcelada com você, ela aparecerá aqui para aprovação antes de afetar seu Saldo Livre.",
                            style = Typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(chargeGroups, key = { it.id }) { group ->
                ChargeGroupCard(
                    group = group,
                    cards = cards,
                    isExpanded = expandedGroupIds.contains(group.id),
                    onToggleExpand = { onToggleExpand(group.id) },
                    onAccept = { onRespondCharge(group, true) },
                    onReject = { onRespondCharge(group, false) }
                )
            }
        }
    }
}

@Composable
private fun FriendRequestCard(
    request: FriendRequestItem,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                        .size(42.dp)
                        .background(Cyan500.copy(alpha = 0.16f), CircleShape)
                        .border(1.dp, Cyan500.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.requesterName,
                        style = Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = request.requesterEmail,
                        style = Typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Recusar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onAccept,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Emerald500.copy(alpha = 0.2f),
                        contentColor = Emerald400
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Aceitar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChargeGroupCard(
    group: PendingChargeGroup,
    cards: List<Card>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val cardName = group.cardId?.let { id -> cards.find { it.id == id }?.cardName }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Amber500.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (group.isInstallment) Purple500.copy(alpha = 0.16f) else Amber500.copy(alpha = 0.16f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (group.isInstallment) Purple500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f),
                                    CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (group.isInstallment) "Parcelado (${group.installmentsCount}x)" else "À Vista",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (group.isInstallment) Purple400 else Amber400,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Cyan500.copy(alpha = 0.14f), CircleShape)
                                .border(1.dp, Cyan500.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Sua parte: ${group.friendSharePct.toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cyan400,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = group.baseDescription,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = buildString {
                            append("Enviado por ${group.senderName}")
                            if (!cardName.isNullOrBlank()) append(" • Cartão $cardName")
                        },
                        style = Typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FinanceEngine.formatCurrency(group.friendShareAmount),
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Amber400
                    )
                    Text(
                        text = "de ${FinanceEngine.formatCurrency(group.totalAmount)} total",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Resumo de parcelas (se parcelado)
            if (group.isInstallment) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${group.installmentsCount}x de ${FinanceEngine.formatCurrency(group.perInstallmentFriendShare)}/mês",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple400
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onToggleExpand() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Ocultar cronograma" else "Ver parcelas",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Cyan400
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))
                            group.items.forEachIndexed { idx, tx ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Parcela ${idx + 1}/${group.installmentsCount} • ${FinanceEngine.formatMonthLabel(tx.competenceMonth)}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = FinanceEngine.formatCurrency(FinanceEngine.friendShareOf(tx)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Botões de Decisão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recusar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (group.isInstallment) "Aceitar (${group.installmentsCount}x)" else "Aceitar Rateio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendsTabContent(
    friends: List<FriendItem>,
    isSending: Boolean,
    onSendInvite: (String) -> Unit,
    onRemoveFriend: (String) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card de Adicionar Amigo (CircleShape search/invite bar)
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CONECTAR NOVO AMIGO POR E-MAIL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Cyan400,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "Conecte-se com amigos que usam o Saldo Livre para dividir contas e compras parceladas com aprovação transparente.",
                        style = Typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            placeholder = { Text("email@amigo.com", color = TextMuted, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AlternateEmail,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            shape = CircleShape,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Send
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Cyan400,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalButton(
                            onClick = {
                                if (emailInput.isNotBlank()) {
                                    onSendInvite(emailInput.trim())
                                    emailInput = ""
                                }
                            },
                            enabled = emailInput.isNotBlank() && !isSending,
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Cyan500.copy(alpha = 0.2f),
                                contentColor = Cyan400
                            ),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Cyan400
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Convidar",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Convidar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "CONEXÕES ATIVAS (${friends.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (friends.isEmpty()) {
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
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Nenhum amigo conectado ainda",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Envie um convite pelo e-mail acima para começar a dividir despesas.",
                            style = Typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(friends, key = { it.friendshipId }) { friend ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                    .size(42.dp)
                                    .background(Cyan500.copy(alpha = 0.14f), CircleShape)
                                    .border(1.dp, Cyan500.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = friend.friendName.take(2).uppercase(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Cyan400,
                                    fontSize = 14.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.friendName,
                                    style = Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = friend.friendEmail,
                                    style = Typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(onClick = { onRemoveFriend(friend.friendshipId) }) {
                            Icon(
                                imageVector = Icons.Default.PersonRemove,
                                contentDescription = "Remover Amigo",
                                tint = Rose400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementTabContent(
    selectedMonth: String,
    receivable: Double,
    payable: Double,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit
) {
    val netBalance = FinanceEngine.round2(receivable - payable)
    val netColor = if (netBalance > 0) Emerald400 else if (netBalance < 0) Rose400 else Cyan400

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MonthSelector(
            selectedMonth = selectedMonth,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onCurrentMonth = onCurrentMonth
        )

        // Hero Card: Saldo Líquido de Rateios no Mês
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.dp, netColor.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = StatementHeroBrush)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SALDO LÍQUIDO DE RATEIOS NO MÊS",
                    style = Typography.labelMedium,
                    color = netColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "${if (netBalance > 0) "+" else ""}${FinanceEngine.formatCurrency(netBalance)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = netColor
                )

                Text(
                    text = when {
                        netBalance > 0 -> "Seus amigos têm mais reembolsos a fazer para você neste mês."
                        netBalance < 0 -> "Você possui mais valores a reembolsar para amigos/parceiro neste mês."
                        else -> "Seus rateios estão perfeitamente equilibrados neste mês."
                    },
                    style = Typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Cards detalhados: A Receber vs A Pagar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallReceived,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "A RECEBER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald400,
                            letterSpacing = 0.6.sp
                        )
                    }
                    Text(
                        text = FinanceEngine.formatCurrency(receivable),
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Emerald400
                    )
                    Text(
                        text = "Cotas de amigos em compras pagas por você",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, Rose500.copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = Rose400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "EU DEVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Rose400,
                            letterSpacing = 0.6.sp
                        )
                    }
                    Text(
                        text = FinanceEngine.formatCurrency(payable),
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Rose400
                    )
                    Text(
                        text = "Sua cota em compras pagas por outros",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
