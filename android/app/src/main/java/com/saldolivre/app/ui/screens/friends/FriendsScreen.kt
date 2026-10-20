package com.saldolivre.app.ui.screens.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.PendingChargeGroup
import com.saldolivre.app.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var friendToRemove by remember { mutableStateOf<FriendItem?>(null) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Purple500.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = Purple400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Rede de Amizades",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Conexões e aprovações de cartão",
                                style = Typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Segmented Tab Pills
            FriendsTabPills(
                activeTab = uiState.activeTab,
                pendingApprovalsCount = uiState.pendingChargeGroups.size,
                friendsCount = uiState.activeFriends.size,
                pendingRequestsCount = uiState.pendingRequests.size,
                onSelectTab = { viewModel.selectTab(it) }
            )

            when (uiState.activeTab) {
                FriendsTab.APPROVALS -> {
                    ApprovalsTabContent(
                        groups = uiState.pendingChargeGroups,
                        cards = uiState.cards,
                        expandedGroupIds = uiState.expandedGroupIds,
                        onToggleExpand = { viewModel.toggleGroupExpand(it) },
                        onRespond = { group, accept -> viewModel.respondCharge(group, accept) }
                    )
                }
                FriendsTab.FRIENDS -> {
                    FriendsTabContent(
                        activeFriends = uiState.activeFriends,
                        pendingRequests = uiState.pendingRequests,
                        isSendingInvite = uiState.isSendingInvite,
                        onSendInvite = { viewModel.sendFriendInvite(it) },
                        onRespondRequest = { id, accept -> viewModel.respondFriendRequest(id, accept) },
                        onRemoveFriend = { friendToRemove = it }
                    )
                }
                FriendsTab.STATEMENT -> {
                    StatementTabContent(
                        receivableFromFriends = uiState.receivableFromFriends,
                        payableToOthers = uiState.payableToOthers
                    )
                }
            }
        }
    }

    // Confirmation Dialog for removing friend
    if (friendToRemove != null) {
        val friend = friendToRemove!!
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Remover Amizade",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Deseja remover a conexão de amizade com ${friend.friendName}? Novas cobranças não poderão ser enviadas diretamente até um novo convite.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeFriend(friend.friendshipId)
                        friendToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Remover", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemove = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// 1. Navigation Tab Pills
// -------------------------------------------------------------
@Composable
private fun FriendsTabPills(
    activeTab: FriendsTab,
    pendingApprovalsCount: Int,
    friendsCount: Int,
    pendingRequestsCount: Int,
    onSelectTab: (FriendsTab) -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Approvals Pill
            TabPillItem(
                label = "Aprovações",
                icon = Icons.Default.Schedule,
                badgeCount = pendingApprovalsCount,
                badgeColor = Amber500,
                isSelected = activeTab == FriendsTab.APPROVALS,
                selectedColor = Amber500.copy(alpha = 0.2f),
                selectedTextColor = Amber400,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(FriendsTab.APPROVALS) }
            )

            // Friends Pill
            TabPillItem(
                label = "Amigos ($friendsCount)",
                icon = Icons.Default.Group,
                badgeCount = pendingRequestsCount,
                badgeColor = Purple500,
                isSelected = activeTab == FriendsTab.FRIENDS,
                selectedColor = Purple500.copy(alpha = 0.2f),
                selectedTextColor = Purple400,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(FriendsTab.FRIENDS) }
            )

            // Statement Pill
            TabPillItem(
                label = "Balanço",
                icon = Icons.Default.CreditCard,
                badgeCount = 0,
                badgeColor = Color.Transparent,
                isSelected = activeTab == FriendsTab.STATEMENT,
                selectedColor = Emerald500.copy(alpha = 0.2f),
                selectedTextColor = Emerald400,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(FriendsTab.STATEMENT) }
            )
        }
    }
}

@Composable
private fun TabPillItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    badgeColor: Color,
    isSelected: Boolean,
    selectedColor: Color,
    selectedTextColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) selectedColor else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) selectedTextColor.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) selectedTextColor else TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedTextColor else TextSecondary
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeColor)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. TAB 1: Aprovações de Cobrança Pendentes
// -------------------------------------------------------------
@Composable
private fun ApprovalsTabContent(
    groups: List<PendingChargeGroup>,
    cards: List<Card>,
    expandedGroupIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onRespond: (PendingChargeGroup, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (groups.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Emerald500.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Nenhuma cobrança pendente",
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Quando amigos cadastrarem compras feitas no cartão deles com a sua cota, elas aparecerão aqui para você aprovar antes de comprometer seu Saldo Livre.",
                            style = Typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    color = Amber500.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Ao aceitar, a sua cota atribuída entrará no cálculo das suas saídas comprometidas mês a mês, atualizando com precisão seu Saldo Livre.",
                            style = Typography.bodySmall,
                            color = Amber400,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            items(groups, key = { it.id }) { group ->
                val card = cards.find { it.id == group.cardId }
                val isExpanded = expandedGroupIds.contains(group.id)

                ChargeGroupCard(
                    group = group,
                    card = card,
                    isExpanded = isExpanded,
                    onToggleExpand = { onToggleExpand(group.id) },
                    onAccept = { onRespond(group, true) },
                    onReject = { onRespond(group, false) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChargeGroupCard(
    group: PendingChargeGroup,
    card: Card?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val formattedFriendShare = String.format(Locale("pt", "BR"), "R$ %,.2f", group.friendShareAmount)
    val formattedPerInstallment = String.format(Locale("pt", "BR"), "R$ %,.2f", group.perInstallmentFriendShare)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Description + Badges + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.baseDescription,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (group.isInstallment) {
                            Surface(
                                color = Purple500.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${group.installmentsCount}x Parcelas",
                                    color = Purple400,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            color = if (group.friendSharePct < 100) Cyan500.copy(alpha = 0.2f) else Amber500.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (group.friendSharePct < 100) "Sua Cota: ${group.friendSharePct.toInt()}%" else "100% Para Você",
                                color = if (group.friendSharePct < 100) Cyan400 else Amber400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formattedFriendShare,
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Amber400
                    )
                    if (group.isInstallment) {
                        Text(
                            text = "${group.installmentsCount}x de $formattedPerInstallment",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Info Details: Dates, Card, Sender
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (group.isInstallment) "Período: ${group.firstDueDate} até ${group.lastDueDate}" else "Vencimento: ${group.firstDueDate}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                if (card != null) {
                    Text(text = "•", color = TextMuted)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CreditCard, contentDescription = null, tint = Cyan400, modifier = Modifier.size(12.dp))
                        Text(text = card.cardName, fontSize = 11.sp, color = TextPrimary)
                    }
                }

                if (group.senderName.isNotBlank()) {
                    Text(text = "•", color = TextMuted)
                    Text(text = "Por: ${group.senderName}", fontSize = 11.sp, color = Amber400, fontWeight = FontWeight.Medium)
                }
            }

            // Expandable Schedule for Installments
            if (group.isInstallment) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleExpand() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Purple400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isExpanded) "Ocultar cronograma" else "Ver cronograma das ${group.installmentsCount} parcelas",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Purple400
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = DarkBackground.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            group.items.forEach { itx ->
                                val itxShare = FinanceEngine.friendShareOf(itx)
                                val formattedItxShare = String.format(Locale("pt", "BR"), "R$ %,.2f", itxShare)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Parcela ${itx.installmentNumber ?: 1}/${group.installmentsCount}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = itx.dueDate,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = formattedItxShare,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber400
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = DarkBorder)

            // Action Buttons: Recusar e Aceitar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (group.isInstallment) "${group.installmentsCount} parcelas serão agendadas" else "Entra nas despesas do mês",
                    fontSize = 10.sp,
                    color = TextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Recusar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Aceitar ($formattedFriendShare)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. TAB 2: Amigos e Convites
// -------------------------------------------------------------
@Composable
private fun FriendsTabContent(
    activeFriends: List<FriendItem>,
    pendingRequests: List<FriendRequestItem>,
    isSendingInvite: Boolean,
    onSendInvite: (String) -> Unit,
    onRespondRequest: (String, Boolean) -> Unit,
    onRemoveFriend: (FriendItem) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Invite Form Card
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = Purple400, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Adicionar Novo Amigo por E-mail",
                            style = Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            placeholder = { Text("email.do.amigo@exemplo.com", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (emailInput.isNotBlank()) {
                                    onSendInvite(emailInput.trim())
                                    emailInput = ""
                                    keyboardController?.hide()
                                }
                            }),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Purple400,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Button(
                            onClick = {
                                if (emailInput.isNotBlank()) {
                                    onSendInvite(emailInput.trim())
                                    emailInput = ""
                                    keyboardController?.hide()
                                }
                            },
                            enabled = emailInput.isNotBlank() && !isSendingInvite,
                            colors = ButtonDefaults.buttonColors(containerColor = Purple500),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isSendingInvite) "..." else "Convidar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Pending Received Requests
        if (pendingRequests.isNotEmpty()) {
            item {
                Text(
                    text = "Convites Recebidos (${pendingRequests.size})",
                    style = Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Amber400
                )
            }

            items(pendingRequests, key = { it.friendshipId }) { req ->
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = req.requesterName, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = req.requesterEmail, style = Typography.bodySmall, color = TextSecondary, fontSize = 11.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { onRespondRequest(req.friendshipId, false) },
                                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                            ) {
                                Text(text = "Recusar", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onRespondRequest(req.friendshipId, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(text = "Aceitar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Active Friends List
        item {
            Text(
                text = "Amigos Conectados (${activeFriends.size})",
                style = Typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (activeFriends.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = TextMuted, modifier = Modifier.size(28.dp))
                        Text(text = "Nenhum amigo conectado ainda", style = Typography.bodyMedium, color = TextSecondary)
                        Text(text = "Convide amigos pelo formulário acima para dividir compras com agilidade.", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        } else {
            items(activeFriends, key = { it.friendshipId }) { friend ->
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Purple500.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = friend.friendName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Purple400,
                                    fontSize = 14.sp
                                )
                            }

                            Column {
                                Text(
                                    text = friend.friendName,
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = friend.friendEmail,
                                    style = Typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = Emerald500.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = Emerald400, modifier = Modifier.size(10.dp))
                                    Text(text = "Conectado", fontSize = 10.sp, color = Emerald400, fontWeight = FontWeight.Bold)
                                }
                            }

                            IconButton(
                                onClick = { onRemoveFriend(friend) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remover",
                                    tint = Rose400.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// 4. TAB 3: Balanço e Acertos
// -------------------------------------------------------------
@Composable
private fun StatementTabContent(
    receivableFromFriends: Double,
    payableToOthers: Double
) {
    val formattedReceivable = String.format(Locale("pt", "BR"), "R$ %,.2f", receivableFromFriends)
    val formattedPayable = String.format(Locale("pt", "BR"), "R$ %,.2f", payableToOthers)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Receivable Card
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Emerald500.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, tint = Emerald400, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "A Receber de Amigos",
                        style = Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400
                    )
                }

                Text(
                    text = formattedReceivable,
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Emerald400
                )

                Text(
                    text = "Compras efetuadas no seu cartão em benefício de amigos que já foram aceitas e aguardam acerto.",
                    style = Typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Payable Card
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Rose500.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, tint = Rose400, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "A Pagar para Amigos",
                        style = Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Rose400
                    )
                }

                Text(
                    text = formattedPayable,
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Rose400
                )

                Text(
                    text = "Compras realizadas em cartões de amigos aceitas por você para pagamento no mês de competência.",
                    style = Typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
