package com.saldolivre.app.ui.screens.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.theme.*

private val DarkEmeraldHeroTint = Color(0xFF064E3B)
private val ProfileHeroBrush = Brush.verticalGradient(
    colors = listOf(
        DarkEmeraldHeroTint.copy(alpha = 0.55f),
        DarkSurface
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onNavigateToReceivables: () -> Unit = {},
    onNavigateToRecurringBills: () -> Unit = {},
    onNavigateToInvestments: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToProjections: () -> Unit = {},
    onNavigateToRecentActivity: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showAiSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Emerald500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Menu & Ferramentas",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Gestão patrimonial, rateios e inteligência",
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
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.hasGeminiKey) Emerald500.copy(alpha = 0.14f) else Amber500.copy(alpha = 0.14f)
                            )
                            .border(
                                1.dp,
                                if (uiState.hasGeminiKey) Emerald500.copy(alpha = 0.35f) else Amber500.copy(alpha = 0.35f),
                                CircleShape
                            )
                            .clickable { showAiSheet = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (uiState.hasGeminiKey) Emerald400 else Amber400,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (uiState.hasGeminiKey) "IA Ativa" else "Configurar IA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.hasGeminiKey) Emerald400 else Amber400,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Card: Perfil, Parceria 50/50 & Resumo Mensal
            ProfilePartnershipHeroCard(
                uiState = uiState,
                onSwitchUser = { viewModel.switchUser(it) }
            )

            // 2. Grupo: Divisões, Acertos & Rede Social
            MenuSectionGroup(
                title = "RATEIOS, ACERTOS & CONEXÕES",
                items = listOf(
                    MenuToolItemData(
                        title = "Acertos & Reembolsos do Mês",
                        subtitle = if (uiState.summary.receivableTotal > 0 || uiState.summary.payableToOthers > 0) {
                            buildString {
                                if (uiState.summary.receivableTotal > 0) {
                                    append("A receber: ${FinanceEngine.formatCurrency(uiState.summary.receivableTotal)}")
                                }
                                if (uiState.summary.payableToOthers > 0) {
                                    if (isNotEmpty()) append(" • ")
                                    append("Eu devo: ${FinanceEngine.formatCurrency(uiState.summary.payableToOthers)}")
                                }
                            }
                        } else {
                            "Reembolsos de parceiro(a), amigos e terceiros"
                        },
                        icon = Icons.Default.Payments,
                        accentColor = Amber400,
                        badgeText = if (uiState.summary.receivableTotal > 0) {
                            "+${FinanceEngine.formatCurrency(uiState.summary.receivableTotal)}"
                        } else if (uiState.summary.payableToOthers > 0) {
                            "-${FinanceEngine.formatCurrency(uiState.summary.payableToOthers)}"
                        } else null,
                        badgeColor = if (uiState.summary.receivableTotal > 0) Amber400 else Rose400,
                        onClick = onNavigateToReceivables
                    ),
                    MenuToolItemData(
                        title = "Rede de Amigos & Rateios",
                        subtitle = if (uiState.pendingAlertsCount > 0) {
                            "${uiState.pendingAlertsCount} solicitação(ões) aguardando sua aprovação"
                        } else {
                            "${uiState.acceptedFriendsCount} amigo(s) conectado(s) para divisão de despesas"
                        },
                        icon = Icons.Default.People,
                        accentColor = Cyan400,
                        badgeText = if (uiState.pendingAlertsCount > 0) "${uiState.pendingAlertsCount} pendente(s)" else "${uiState.acceptedFriendsCount}",
                        badgeColor = if (uiState.pendingAlertsCount > 0) Amber400 else Cyan400,
                        highlightBadge = uiState.pendingAlertsCount > 0,
                        onClick = onNavigateToFriends
                    )
                )
            )

            // 3. Grupo: Planejamento, Patrimônio & Automação
            MenuSectionGroup(
                title = "PLANEJAMENTO & PATRIMÔNIO",
                items = listOf(
                    MenuToolItemData(
                        title = "Contas Fixas & Recorrentes",
                        subtitle = "${uiState.activeRecurringCount} conta(s) ativa(s) com lançamento automático",
                        icon = Icons.Default.EventRepeat,
                        accentColor = Emerald400,
                        badgeText = "${uiState.activeRecurringCount} ativa(s)",
                        badgeColor = Emerald400,
                        onClick = onNavigateToRecurringBills
                    ),
                    MenuToolItemData(
                        title = "Investimentos & Caixinhas",
                        subtitle = if (uiState.summary.totalInvested > 0) {
                            "Aportado em ${FinanceEngine.formatMonthLabel(uiState.selectedMonth)}: ${FinanceEngine.formatCurrency(uiState.summary.totalInvested)}"
                        } else {
                            "Carteira patrimonial, simulador de juros e regra 50/30/20"
                        },
                        icon = Icons.Default.Savings,
                        accentColor = Purple400,
                        badgeText = if (uiState.summary.totalInvested > 0) {
                            FinanceEngine.formatCurrency(uiState.summary.totalInvested)
                        } else null,
                        badgeColor = Purple400,
                        onClick = onNavigateToInvestments
                    ),
                    MenuToolItemData(
                        title = "Projeção de Fluxo (6 Meses)",
                        subtitle = "Simulação preditiva de receitas, faturas e saldo livre futuro",
                        icon = Icons.Default.QueryStats,
                        accentColor = Cyan400,
                        badgeText = "6 meses",
                        badgeColor = Cyan400,
                        onClick = onNavigateToProjections
                    )
                )
            )

            // 4. Grupo: Inteligência Artificial & Auditoria
            MenuSectionGroup(
                title = "INTELIGÊNCIA & AUDITORIA",
                items = listOf(
                    MenuToolItemData(
                        title = "Histórico de Atividades",
                        subtitle = "Linha do tempo de cadastros, vencimentos e rateios",
                        icon = Icons.Default.History,
                        accentColor = Color(0xFF38BDF8),
                        onClick = onNavigateToRecentActivity
                    ),
                    MenuToolItemData(
                        title = "Motor de IA (Google Gemini)",
                        subtitle = if (uiState.hasGeminiKey) {
                            "Conectado • Modelo ativo: ${uiState.activeGeminiModel}"
                        } else {
                            "Modo local heurístico • Toque para conectar sua API Key"
                        },
                        icon = Icons.Default.AutoAwesome,
                        accentColor = if (uiState.hasGeminiKey) Emerald400 else Amber400,
                        badgeText = if (uiState.hasGeminiKey) "Online" else "Local",
                        badgeColor = if (uiState.hasGeminiKey) Emerald400 else Amber400,
                        onClick = { showAiSheet = true }
                    )
                )
            )

            // 5. Botão de Encerrar Sessão
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Rose500.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Rose500.copy(alpha = 0.28f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onLogout)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = Rose400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isDemoMode) "Sair do Modo Demonstração" else "Encerrar Sessão da Conta",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Rose400
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAiSheet) {
        GeminiApiKeyBottomSheet(
            uiState = uiState,
            onDismiss = { showAiSheet = false },
            onSave = { key, model ->
                viewModel.saveAiConfig(key, model)
                showAiSheet = false
            },
            onClearKey = {
                viewModel.clearAiKey()
                showAiSheet = false
            },
            onTestConnection = { testKey, callback ->
                viewModel.testConnection(testKey, callback)
            }
        )
    }
}

@Composable
private fun ProfilePartnershipHeroCard(
    uiState: MenuUiState,
    onSwitchUser: (String) -> Unit
) {
    val user = uiState.currentUser
    val partner = uiState.partnerUser
    val initials = remember(user.displayName) {
        user.displayName
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "SL" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = ProfileHeroBrush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Linha Principal: Avatar + Nome + Email + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            Brush.linearGradient(listOf(Emerald500, Cyan500)),
                            RoundedCornerShape(16.dp)
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = user.displayName.ifBlank { "Usuário Saldo Livre" },
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (uiState.isDemoMode) Amber500.copy(alpha = 0.18f) else Emerald500.copy(alpha = 0.18f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (uiState.isDemoMode) Amber500.copy(alpha = 0.4f) else Emerald500.copy(alpha = 0.4f),
                                    CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (uiState.isDemoMode) "DEMO" else "SINCRONIZADO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.isDemoMode) Amber400 else Emerald400,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = user.email.ifBlank { "Conta vinculada ao Saldo Livre" },
                        style = Typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Sub-container: Parceria 50/50 e Renda Base
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "PARCERIA 50/50",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = partner?.displayName?.ifBlank { "Conectado" } ?: "Individual",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (partner != null) Cyan400 else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "SALÁRIO BASE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (user.monthlyIncome > 0) FinanceEngine.formatCurrency(user.monthlyIncome) else "Não definido",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Seletor de Perfil Rápido (Apenas Modo Demo com múltiplos usuários)
            if (uiState.isDemoMode && uiState.allUsers.size > 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ALTERNAR VISÃO DO PERFIL (MODO DEMO)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.6.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.allUsers.forEach { u ->
                            val isSelected = u.uid == user.uid
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Emerald500.copy(alpha = 0.2f) else DarkSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Emerald500.copy(alpha = 0.5f) else DarkBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSwitchUser(u.uid) }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = u.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Emerald400 else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MenuToolItemData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val badgeText: String? = null,
    val badgeColor: Color = Emerald400,
    val highlightBadge: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun MenuSectionGroup(
    title: String,
    items: List<MenuToolItemData>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(item.accentColor.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                .border(1.dp, item.accentColor.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = item.accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                style = Typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!item.badgeText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (item.highlightBadge) item.badgeColor.copy(alpha = 0.22f)
                                        else item.badgeColor.copy(alpha = 0.12f),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        item.badgeColor.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = item.badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = item.badgeColor,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            color = DarkBorder.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiApiKeyBottomSheet(
    uiState: MenuUiState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onClearKey: () -> Unit,
    onTestConnection: (String, (Pair<Boolean, String>) -> Unit) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var apiKeyInput by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var selectedModel by remember(uiState.activeGeminiModel) { mutableStateOf(uiState.activeGeminiModel) }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Emerald500.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .border(1.dp, Emerald500.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Configurar Google Gemini IA",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Motor conversacional e extrator inteligente de gastos",
                        style = Typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Status Banner (Conectado vs Modo Local Heurístico)
            val isConnected = uiState.hasGeminiKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isConnected) Emerald500.copy(alpha = 0.1f) else Amber500.copy(alpha = 0.1f),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        if (isConnected) Emerald500.copy(alpha = 0.3f) else Amber500.copy(alpha = 0.3f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.AutoAwesome else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isConnected) Emerald400 else Amber400,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp)
                )
                Column {
                    Text(
                        text = if (isConnected) "Gemini Conectado (${uiState.activeGeminiModel})" else "Modo Local (Heurístico) Ativo",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Emerald400 else Amber400
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) {
                            "O copiloto utiliza a API oficial do Google Gemini com balanceamento inteligente de chaves."
                        } else {
                            "Adicione uma chave gratuita do Google AI Studio para ativar interpretação natural avançada e análise financeira completa."
                        },
                        style = Typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Input de API Key
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CHAVE(S) DE API DO GEMINI (SEPARADAS POR VÍRGULA)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        testResult = null
                    },
                    placeholder = { Text("AIzaSy...", color = TextMuted) },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Ocultar" else "Mostrar",
                                tint = TextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Seletor de Modelo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MODELO PREFERENCIAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                MenuViewModel.AVAILABLE_MODELS.forEach { modelId ->
                    val isSelected = selectedModel == modelId
                    val description = when {
                        modelId.contains("flash-lite") -> "Ultra-rápido e econômico para lançamentos instantâneos"
                        modelId.contains("3-flash") -> "Recomendado • Equilíbrio ideal entre velocidade e raciocínio"
                        modelId.contains("pro") -> "Máxima capacidade analítica para planejamento complexo"
                        else -> "Modelo versátil de alta velocidade"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Emerald500.copy(alpha = 0.14f) else DarkBackground)
                            .border(
                                1.dp,
                                if (isSelected) Emerald500.copy(alpha = 0.45f) else DarkBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedModel = modelId }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = modelId,
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Emerald400 else TextPrimary
                            )
                            Text(
                                text = description,
                                style = Typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedModel = modelId },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Emerald400,
                                unselectedColor = TextMuted
                            )
                        )
                    }
                }
            }

            // Resultado do Teste de Conexão
            testResult?.let { (ok, message) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (ok) Emerald500.copy(alpha = 0.12f) else Rose500.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (ok) Emerald500.copy(alpha = 0.35f) else Rose500.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (ok) Emerald400 else Rose400,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message,
                        style = Typography.bodySmall,
                        color = if (ok) Emerald400 else Rose400,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Link para Google AI Studio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        runCatching { uriHandler.openUri("https://aistudio.google.com/app/apikey") }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Obter chave gratuita no Google AI Studio",
                    style = Typography.bodySmall,
                    color = Cyan400,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.hasGeminiKey) {
                    OutlinedButton(
                        onClick = onClearKey,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remover Chave",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        onTestConnection(apiKeyInput) { res ->
                            testResult = res
                        }
                    },
                    enabled = apiKeyInput.isNotBlank() && !uiState.isTestingConnection,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    if (uiState.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Emerald400
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Testar", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { onSave(apiKeyInput, selectedModel) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
