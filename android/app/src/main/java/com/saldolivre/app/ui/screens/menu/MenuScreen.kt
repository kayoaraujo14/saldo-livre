package com.saldolivre.app.ui.screens.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onNavigateToReceivables: () -> Unit,
    onNavigateToRecurringBills: () -> Unit,
    onNavigateToInvestments: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToProjections: () -> Unit,
    onNavigateToRecentActivity: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.currentUser
    val partner = uiState.partnerUser

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var tempModel by remember(uiState.activeGeminiModel) { mutableStateOf(uiState.activeGeminiModel) }

    if (showApiKeyDialog) {
        var testFeedback by remember { mutableStateOf<String?>(null) }
        var isTestSuccess by remember { mutableStateOf<Boolean?>(null) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Chave API do Gemini",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Informe suas chaves da API do Google Gemini (do Google AI Studio). Você pode colar múltiplas chaves separadas por vírgula para ativar o Load Balancing automático:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = {
                            tempApiKey = it
                            testFeedback = null
                        },
                        placeholder = { Text("AIzaSy... (uma ou mais chaves)", color = TextMuted, fontSize = 13.sp) },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val firstKey = tempApiKey.split(',', ';', '\n')
                                    .map { it.trim() }
                                    .firstOrNull { it.isNotBlank() } ?: ""
                                viewModel.testConnection(firstKey) { outcome ->
                                    isTestSuccess = outcome.first
                                    testFeedback = outcome.second
                                }
                            },
                            enabled = tempApiKey.isNotBlank() && !uiState.isTestingConnection,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400)
                        ) {
                            if (uiState.isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Cyan400, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(text = "Testar Conexão", fontSize = 12.sp, maxLines = 1)
                        }

                        if (isTestSuccess != null && testFeedback != null) {
                            Text(
                                text = if (isTestSuccess == true) "✓ OK" else "✕ Falhou",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTestSuccess == true) Emerald400 else Rose400,
                                maxLines = 1
                            )
                        }
                    }

                    if (testFeedback != null) {
                        Text(
                            text = testFeedback!!,
                            fontSize = 11.sp,
                            color = if (isTestSuccess == true) Emerald400 else Rose400,
                            lineHeight = 15.sp
                        )
                    }

                    HorizontalDivider(color = DarkBorder)

                    Text(text = "Modelo de IA Padrão:", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MenuViewModel.AVAILABLE_MODELS.forEach { model ->
                            val isSelected = tempModel == model
                            FilterChip(
                                selected = isSelected,
                                onClick = { tempModel = model },
                                label = { Text(model, fontSize = 11.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald500.copy(alpha = 0.2f),
                                    selectedLabelColor = Emerald400
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveAiConfig(tempApiKey, tempModel)
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                ) {
                    Text(
                        "Salvar Configurações",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancelar", color = TextSecondary, maxLines = 1)
                }
            }
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                title = {
                    Column {
                        Text(
                            text = "Menu & Ferramentas",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Minha conta, parceria e gestão financeira",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
        ) {
            // =========================================================
            // 1. MINHA CONTA & CASAL / PARCERIA
            // =========================================================
            item(key = "section_account") {
                SectionLabel("MINHA CONTA & PARCERIA")
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(20.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DarkBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Linha Principal do Perfil + Botão Sair
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(
                                            Emerald500.copy(alpha = 0.2f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .border(
                                            1.dp,
                                            Emerald500.copy(alpha = 0.4f),
                                            RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.displayName.take(1).uppercase().ifBlank { "U" },
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald400
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = user.displayName.ifBlank { "Usuário" },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = user.email.ifBlank { "Conta Pessoal" },
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isDemoMode) Icons.Default.AutoAwesome else Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = if (uiState.isDemoMode) Amber400 else Emerald400,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (uiState.isDemoMode) "Modo Demonstração Ativo" else "Sessão Ativa · Nuvem Firebase",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (uiState.isDemoMode) Amber400 else Emerald400
                                        )
                                    }
                                }
                            }

                            // Botão Sair (Logout)
                            Surface(
                                onClick = onLogout,
                                color = Rose500.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Rose500.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Sair da conta",
                                        tint = Rose400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Sair",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Rose400
                                    )
                                }
                            }
                        }

                        // Alternador de Perfil Demo (apenas quando em Modo Demo)
                        if (uiState.isDemoMode && uiState.allUsers.size > 1) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Purple500.copy(alpha = 0.1f))
                                    .border(1.dp, Purple500.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = Purple400,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Alternar Perfil Demo:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Purple400
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.allUsers.forEach { demoUser ->
                                        val isSelected = demoUser.uid == user.uid
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) Purple500 else DarkBackground.copy(alpha = 0.8f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Purple400 else DarkBorder,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { viewModel.switchUser(demoUser.uid) }
                                                .padding(vertical = 8.dp, horizontal = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = demoUser.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) TextPrimary else TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Status de Vínculo de Casal / Parceria e Código de Convite
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkBackground.copy(alpha = 0.65f))
                                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                                .clickable { onNavigateToFriends() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            Purple500.copy(alpha = 0.15f),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Purple400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (partner != null) {
                                            "Parceria Ativa: ${partner.displayName}"
                                        } else {
                                            "Conectar Parceiro(a) ou Amigo"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (user.email.isNotBlank()) {
                                            "Seu convite: ${user.email}"
                                        } else {
                                            "Toque para vincular contas e dividir 50/50"
                                        },
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                    }
                }
            }

            // =========================================================
            // 2. FERRAMENTAS & GESTÃO FINANCEIRA
            // =========================================================
            item(key = "section_tools") {
                SectionLabel("FERRAMENTAS & GESTÃO FINANCEIRA")
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(20.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DarkBorder))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. Amigos & Divisões
                        MenuNavigationRow(
                            icon = Icons.Default.Group,
                            iconColor = Purple400,
                            title = "Amigos & Divisões",
                            subtitle = "Gerenciar conexões e aprovações de cartão",
                            badgeText = if (uiState.pendingAlertsCount > 0) {
                                "${uiState.pendingAlertsCount} pendente${if (uiState.pendingAlertsCount > 1) "s" else ""}"
                            } else null,
                            badgeColor = Amber500,
                            onClick = onNavigateToFriends
                        )
                        MenuDivider()

                        // 2. A Receber
                        MenuNavigationRow(
                            icon = Icons.Default.Payments,
                            iconColor = Emerald400,
                            title = "Valores a Receber",
                            subtitle = "Reembolsos de parceiro(a), amigos e terceiros",
                            badgeText = if (uiState.summary.receivableTotal > 0) {
                                FinanceEngine.formatCurrency(uiState.summary.receivableTotal)
                            } else null,
                            badgeColor = Emerald500,
                            onClick = onNavigateToReceivables
                        )
                        MenuDivider()

                        // 3. Contas Recorrentes & Salário
                        MenuNavigationRow(
                            icon = Icons.Default.Repeat,
                            iconColor = Amber400,
                            title = "Moldes & Salário Base",
                            subtitle = "Contas fixas, assinaturas e receitas mensais",
                            badgeText = if (uiState.activeRecurringCount > 0) "${uiState.activeRecurringCount} ativos" else null,
                            badgeColor = Amber500,
                            onClick = onNavigateToRecurringBills
                        )
                        MenuDivider()

                        // 5. Investimentos & Patrimônio
                        MenuNavigationRow(
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            iconColor = Purple400,
                            title = "Investimentos & Patrimônio",
                            subtitle = "Aportes, classes de ativos e simulador",
                            onClick = onNavigateToInvestments
                        )
                        MenuDivider()

                        // 6. Projeção de Fluxo de Caixa (6 Meses)
                        MenuNavigationRow(
                            icon = Icons.Default.CalendarMonth,
                            iconColor = Cyan400,
                            title = "Projeção de Fluxo (6 Meses)",
                            subtitle = "Previsão futura de parcelas e saldo livre",
                            onClick = onNavigateToProjections
                        )
                        MenuDivider()

                        // 7. Histórico de Atividades / Auditoria
                        MenuNavigationRow(
                            icon = Icons.Default.History,
                            iconColor = Cyan400,
                            title = "Histórico Recente",
                            subtitle = "Últimas transações cadastradas ou recebidas",
                            onClick = onNavigateToRecentActivity
                        )
                    }
                }
            }

            // =========================================================
            // 3. AUTOMAÇÕES & INTELIGÊNCIA ARTIFICIAL
            // =========================================================
            item(key = "section_automations") {
                SectionLabel("AUTOMAÇÕES & INTELIGÊNCIA ARTIFICIAL")
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(20.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DarkBorder))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Gerar Contas Fixas do Mês
                        MenuNavigationRow(
                            icon = Icons.Default.Bolt,
                            iconColor = Emerald400,
                            title = if (uiState.isGeneratingBills) "Gerando Contas..." else "Gerar Contas do Mês",
                            subtitle = "Importa os moldes recorrentes para ${FinanceEngine.monthLabel(uiState.selectedMonth)}",
                            onClick = { viewModel.generateBillsForCurrentMonth() }
                        )

                        uiState.generateFeedback?.let { feedback ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Emerald500.copy(alpha = 0.12f))
                                    .border(1.dp, Emerald500.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = feedback,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Emerald400
                                )
                            }
                        }

                        MenuDivider()

                        // Configuração exclusiva de Chave API do Gemini
                        MenuNavigationRow(
                            icon = Icons.Default.Key,
                            iconColor = Emerald400,
                            title = "Configurar Chave API Gemini",
                            subtitle = "Gerenciar chaves do Google AI Studio e modelo padrão",
                            badgeText = if (uiState.hasGeminiKey) uiState.activeGeminiModel else "Modo Local",
                            badgeColor = if (uiState.hasGeminiKey) Emerald500 else Cyan500,
                            onClick = {
                                tempApiKey = uiState.apiKey
                                tempModel = uiState.activeGeminiModel
                                showApiKeyDialog = true
                            }
                        )
                    }
                }
            }

            // Rodapé informativo
            item(key = "section_footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Saldo Livre Android",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sincronizado em Nuvem · Copiloto Gemini Integrado",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = TextMuted,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        color = DarkBorder.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun MenuNavigationRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = Emerald500,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .border(1.dp, iconColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(badgeColor.copy(alpha = 0.18f))
                                .border(1.dp, badgeColor.copy(alpha = 0.4f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
}
