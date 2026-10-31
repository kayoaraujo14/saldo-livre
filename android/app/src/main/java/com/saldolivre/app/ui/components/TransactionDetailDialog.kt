package com.saldolivre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.ui.theme.*

private data class ParticipantDisplayItem(
    val name: String,
    val amount: Double,
    val percentage: Int,
    val isPayer: Boolean,
    val isFriend: Boolean,
    val isCurrentUser: Boolean,
    val acceptanceStatus: String?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    currentUserId: String? = null,
    currentUser: UserProfile? = null,
    cardName: String? = null,
    allTransactions: List<Transaction> = emptyList(),
    onDismissRequest: () -> Unit,
    onToggleStatus: () -> Unit,
    onSetStatus: (TransactionStatus, Double?) -> Unit,
    onNavigateToEditTransaction: (String) -> Unit,
    onNavigateToRefundTransaction: (String) -> Unit = {},
    onConfirmRefund: ((Transaction, Double) -> Unit)? = null,
    onDeleteRequest: () -> Unit
) {
    var showQuickRefundDialog by remember { mutableStateOf(false) }

    val resolvedUserId = currentUserId ?: currentUser?.uid
    val myImpact = remember(transaction, resolvedUserId, allTransactions) {
        if (!resolvedUserId.isNullOrBlank()) {
            FinanceEngine.impactFor(transaction, resolvedUserId, allTransactions)
        } else {
            transaction.effectiveAmount
        }
    }
    val canEdit = remember(transaction, resolvedUserId) {
        FinanceEngine.canUserEditTransaction(transaction, resolvedUserId)
    }
    val linkedRefund = remember(transaction.id, allTransactions) {
        allTransactions.find { it.refundedTransactionId == transaction.id }
    }
    val isSplit = remember(transaction, myImpact) {
        kotlin.math.abs(myImpact - transaction.effectiveAmount) > 0.01 ||
                (transaction.splitType != null && transaction.splitType != com.saldolivre.app.data.model.SplitType.MINE) ||
                !transaction.assignedFriendUid.isNullOrBlank() ||
                transaction.allocations.size > 1
    }
    var customActualInput by remember(transaction.id, transaction.effectiveAmount) {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", transaction.effectiveAmount))
    }
    val isIncome = transaction.type == TransactionType.INCOME
    val categoryDetail = remember(transaction.category) {
        CategoryRegistry.get(transaction.category)
    }

    // Processamento unificado de participantes do rateio e amigos vinculados
    val participants = remember(transaction, resolvedUserId, currentUser) {
        val total = Math.abs(transaction.effectiveAmount)
        val list = mutableListOf<ParticipantDisplayItem>()
        val payerUid = transaction.paidByUid.ifBlank { transaction.createdByUid }
        val friendUid = transaction.assignedFriendUid
        val friendName = transaction.assignedFriendName

        if (transaction.allocations.isNotEmpty()) {
            transaction.allocations.forEach { alloc ->
                val isFriend = (friendUid != null && alloc.uid == friendUid) ||
                        (!friendName.isNullOrBlank() && alloc.name.equals(friendName, ignoreCase = true))
                val isCurrentUser = (!resolvedUserId.isNullOrBlank() && alloc.uid == resolvedUserId) ||
                        (!currentUser?.displayName.isNullOrBlank() && alloc.name.equals(currentUser?.displayName, ignoreCase = true))
                val isPayer = (!payerUid.isNullOrBlank() && alloc.uid == payerUid) ||
                        (!isFriend && list.isEmpty())
                val pct = if (total > 0.0) FinanceEngine.round2((Math.abs(alloc.amount) / total) * 100.0).toInt() else 0

                list.add(
                    ParticipantDisplayItem(
                        name = alloc.name,
                        amount = Math.abs(alloc.amount),
                        percentage = pct,
                        isPayer = isPayer,
                        isFriend = isFriend,
                        isCurrentUser = isCurrentUser,
                        acceptanceStatus = if (isFriend) transaction.acceptanceStatus else null
                    )
                )
            }
        } else if (!friendUid.isNullOrBlank() || !friendName.isNullOrBlank()) {
            val friendAmount = FinanceEngine.friendShareOf(transaction)
            val myAmount = Math.max(0.0, FinanceEngine.round2(total - friendAmount))
            val friendPct = if (total > 0.0) FinanceEngine.round2((friendAmount / total) * 100.0).toInt() else 50
            val myPct = 100 - friendPct

            val fName = friendName?.takeIf { it.isNotBlank() } ?: "Amigo"
            val myName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Você"

            list.add(
                ParticipantDisplayItem(
                    name = myName,
                    amount = myAmount,
                    percentage = myPct,
                    isPayer = true,
                    isFriend = false,
                    isCurrentUser = true,
                    acceptanceStatus = null
                )
            )
            list.add(
                ParticipantDisplayItem(
                    name = fName,
                    amount = friendAmount,
                    percentage = friendPct,
                    isPayer = false,
                    isFriend = true,
                    isCurrentUser = false,
                    acceptanceStatus = transaction.acceptanceStatus
                )
            )
        }
        list
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = TextSecondary.copy(alpha = 0.4f),
                width = 38.dp,
                height = 4.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. HERO HEADER: Ícone de Categoria, Título e Valor
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(categoryDetail.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryDetail.icon,
                    contentDescription = transaction.category,
                    tint = categoryDetail.color,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = transaction.description,
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val amountPrefix = if (isIncome) "+ " else "- "
                val amountColor = if (isIncome) Emerald400 else Color.White
                Text(
                    text = amountPrefix + FinanceEngine.formatCurrency(transaction.effectiveAmount),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    textAlign = TextAlign.Center
                )

                // Subtexto se houver rateio ou valores a receber
                if (!currentUserId.isNullOrBlank() && isSplit && myImpact != transaction.effectiveAmount) {
                    val toReceive = transaction.effectiveAmount - myImpact
                    val sublineText = if (transaction.paidByUid == currentUserId && toReceive > 0.01) {
                        "Sua cota: ${FinanceEngine.formatCurrency(myImpact)} • +${FinanceEngine.formatCurrency(toReceive)} a receber"
                    } else if (transaction.paidByUid != currentUserId && myImpact > 0.0) {
                        "Sua cota: ${FinanceEngine.formatCurrency(myImpact)} (deve ao titular)"
                    } else {
                        "Sua cota: ${FinanceEngine.formatCurrency(myImpact)}"
                    }
                    Text(
                        text = sublineText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (toReceive > 0.01) Emerald400 else TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                // Tag de Status Atual (pílula suave sob o valor hero)
                val currentVisuals = when (transaction.status) {
                    TransactionStatus.PAID -> StatusVisuals("Pago", Emerald400, Emerald500.copy(alpha = 0.15f), Icons.Default.CheckCircle)
                    TransactionStatus.PENDING -> StatusVisuals("Pendente", Amber400, Amber500.copy(alpha = 0.15f), Icons.Default.Schedule)
                    TransactionStatus.ESTIMATED -> StatusVisuals("Estimado", TextSecondary, TextSecondary.copy(alpha = 0.15f), Icons.Default.Timeline)
                }
                Spacer(modifier = Modifier.height(2.dp))
                PillBadge(
                    text = currentVisuals.label,
                    textColor = currentVisuals.color,
                    bgColor = currentVisuals.bgColor,
                    icon = currentVisuals.icon,
                    onClick = onToggleStatus
                )
            }

            // 2. SELETOR "ALTERNAR STATUS" (Segmented Control em CircleShape)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.04f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TransactionStatus.entries.forEach { st ->
                        val isSelected = transaction.status == st
                        val chipLabel = when (st) {
                            TransactionStatus.ESTIMATED -> "Estimado"
                            TransactionStatus.PENDING -> "Pendente"
                            TransactionStatus.PAID -> "Pago"
                        }
                        val (activeColor, activeBg) = when (st) {
                            TransactionStatus.PAID -> Pair(Emerald400, Emerald500.copy(alpha = 0.2f))
                            TransactionStatus.PENDING -> Pair(Amber400, Amber500.copy(alpha = 0.2f))
                            TransactionStatus.ESTIMATED -> Pair(Color.White, Color.White.copy(alpha = 0.15f))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (isSelected) activeBg else Color.Transparent)
                                .clickable {
                                    val parsedAmount = customActualInput.replace(",", ".").toDoubleOrNull()
                                    onSetStatus(st, parsedAmount)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chipLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeColor else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Campo de Input: Apenas quando Pendente ou Estimado
            if (transaction.status != TransactionStatus.PAID || transaction.estimatedAmount != null) {
                OutlinedTextField(
                    value = customActualInput,
                    onValueChange = { customActualInput = it },
                    label = {
                        Text(
                            "Valor Real Efetivado (R$)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500.copy(alpha = 0.4f),
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.06f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Emerald400
                    )
                )
            }

            // 3. CARD DE DETALHES GERAIS (Metadados da Transação)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (transaction.estimatedAmount != null) {
                        DetailRow(label = "Valor Estimado", value = FinanceEngine.formatCurrency(transaction.estimatedAmount))
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.25f))
                    }

                    DetailRow(
                        label = "Categoria",
                        value = transaction.category + (transaction.subcategory?.let { " › $it" } ?: "")
                    )
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.25f))

                    DetailRow(
                        label = "Vencimento",
                        value = FinanceEngine.formatFullDate(transaction.dueDate)
                    )
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.25f))

                    DetailRow(
                        label = "Mês de Competência",
                        value = FinanceEngine.formatMonthLabel(transaction.competenceMonth)
                    )
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.25f))

                    val paymentLabel = buildString {
                        append(transaction.paymentMethod.label)
                        if (!cardName.isNullOrBlank() && transaction.paymentMethod == com.saldolivre.app.data.model.PaymentMethod.CREDIT_CARD) {
                            append(" ($cardName)")
                        }
                    }
                    DetailRow(label = "Forma de Pagamento", value = paymentLabel)

                    // Se não for compartilhado, exibe a divisão simples aqui (ex: 100% Meu)
                    if (participants.size <= 1 && !isSplit) {
                        transaction.splitType?.let { split ->
                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.25f))
                            DetailRow(
                                label = "Divisão",
                                value = "${split.emoji} ${split.label}",
                                valueContent = {
                                    PillBadge(
                                        text = "${split.emoji} ${split.label}",
                                        textColor = Emerald400,
                                        bgColor = Emerald500.copy(alpha = 0.15f)
                                    )
                                }
                            )
                        }
                    }

                    if (linkedRefund != null) {
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.25f))
                        DetailRow(
                            label = "Estorno Vinculado",
                            value = "−${FinanceEngine.formatCurrency(Math.abs(linkedRefund.effectiveAmount))}"
                        )
                    }
                }
            }

            // 4. CARD DEDICADO DE DIVISÃO & AMIGOS VINCULADOS (Apenas quando houver múltiplos participantes ou rateio)
            if (participants.size > 1 || isSplit) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cabeçalho do Rateio
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
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Divisão & Amigos (${participants.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            transaction.splitType?.let { split ->
                                PillBadge(
                                    text = "${split.emoji} ${split.label}",
                                    textColor = Emerald400,
                                    bgColor = Emerald500.copy(alpha = 0.15f)
                                )
                            }
                        }

                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.25f))

                        // Lista Unificada de Participantes com Avatares, Cotas e Status de Aceite
                        participants.forEachIndexed { index, participant ->
                            if (index > 0) {
                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.15f))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar com inicial e Nome do Participante
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val initial = participant.name.trim().take(1).uppercase().ifEmpty { "?" }
                                    val isMe = participant.isCurrentUser
                                    val avatarBg = when {
                                        participant.isFriend -> Color(0xFF06B6D4).copy(alpha = 0.18f)
                                        isMe -> Emerald500.copy(alpha = 0.18f)
                                        else -> Purple500.copy(alpha = 0.18f)
                                    }
                                    val avatarTextColor = when {
                                        participant.isFriend -> Color(0xFF22D3EE)
                                        isMe -> Emerald400
                                        else -> Purple400
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(avatarBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initial,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = avatarTextColor
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = if (isMe && !participant.name.contains("(Você)", true)) "${participant.name} (Você)" else participant.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val roleText = buildString {
                                            if (participant.isPayer) append("Titular da compra")
                                            else if (participant.isFriend) append("Amigo vinculado")
                                            else append("Participante")
                                            append(" • ${participant.percentage}%")
                                        }
                                        Text(
                                            text = roleText,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Valor da Cota e Badge de Status
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = FinanceEngine.formatCurrency(participant.amount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    if (participant.isFriend && !participant.acceptanceStatus.isNullOrBlank()) {
                                        val isAccepted = participant.acceptanceStatus == "accepted"
                                        val isPending = participant.acceptanceStatus == "pending_approval"
                                        val (badgeText, badgeColor, badgeBg) = when {
                                            isAccepted -> Triple("✓ Aceito", Emerald400, Emerald500.copy(alpha = 0.15f))
                                            isPending -> Triple("🕒 Aguardando", Amber400, Amber500.copy(alpha = 0.15f))
                                            else -> Triple("✕ Recusado", Rose400, Color.Red.copy(alpha = 0.12f))
                                        }
                                        PillBadge(
                                            text = badgeText,
                                            textColor = badgeColor,
                                            bgColor = badgeBg
                                        )
                                    } else if (participant.isPayer) {
                                        PillBadge(
                                            text = "Pagou",
                                            textColor = TextSecondary,
                                            bgColor = Color.White.copy(alpha = 0.06f)
                                        )
                                    }
                                }
                            }
                        }

                        // Resumo financeiro inteligente de cobrança entre as partes
                        if (!currentUserId.isNullOrBlank() && isSplit && myImpact != transaction.effectiveAmount) {
                            val toReceive = transaction.effectiveAmount - myImpact
                            val summaryText = if (transaction.paidByUid == currentUserId && toReceive > 0.01) {
                                val friendNameStr = participants.find { it.isFriend || !it.isCurrentUser }?.name ?: "amigo"
                                "💡 Você pagou o valor total e tem ${FinanceEngine.formatCurrency(toReceive)} a receber de $friendNameStr."
                            } else if (transaction.paidByUid != currentUserId && myImpact > 0.0) {
                                val payerNameStr = participants.find { it.isPayer }?.name ?: "o titular"
                                "💡 $payerNameStr pagou a conta e você deve ${FinanceEngine.formatCurrency(myImpact)}."
                            } else null

                            if (summaryText != null) {
                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.2f))
                                Text(
                                    text = summaryText,
                                    fontSize = 11.sp,
                                    color = Emerald400,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 5. BOTÕES DE AÇÃO: Lado a Lado (Editar & Estornar)
            if (canEdit) {
                val hasRefundAction = transaction.type == TransactionType.EXPENSE && !transaction.isRefund
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val targetId = transaction.id
                            onDismissRequest()
                            onNavigateToEditTransaction(targetId)
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500.copy(alpha = 0.12f),
                            contentColor = Emerald400
                        ),
                        contentPadding = PaddingValues(vertical = 11.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Emerald400
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Editar Lançamento",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Emerald400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hasRefundAction) {
                        Button(
                            onClick = { showQuickRefundDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 11.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Estornar Compra",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Amber500.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒 Lançamento recebido por rateio (somente leitura)",
                        fontSize = 12.sp,
                        color = Amber400,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 6. RODAPÉ DE FINALIZAÇÃO: Excluir e Fechar / Confirmar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canEdit) {
                    Button(
                        onClick = onDeleteRequest,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red.copy(alpha = 0.1f),
                            contentColor = Rose400
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Rose400
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Excluir",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Rose400,
                            maxLines = 1
                        )
                    }
                }

                if (canEdit && transaction.status != TransactionStatus.PAID) {
                    Button(
                        onClick = {
                            val parsedAmount = customActualInput.replace(",", ".").toDoubleOrNull()
                            onSetStatus(TransactionStatus.PAID, parsedAmount)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = DarkBackground
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = DarkBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isIncome) "Confirmar" else "Confirmar Pago",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = onDismissRequest,
                        modifier = if (canEdit) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Fechar",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showQuickRefundDialog) {
        QuickRefundDialog(
            origTx = transaction,
            currentUser = currentUser ?: UserProfile(uid = resolvedUserId ?: ""),
            cardName = cardName,
            onDismissRequest = { showQuickRefundDialog = false },
            onConfirmRefund = { refundAmount ->
                onConfirmRefund?.invoke(transaction, refundAmount)
                showQuickRefundDialog = false
                onDismissRequest()
            }
        )
    }
}

private data class StatusVisuals(
    val label: String,
    val color: Color,
    val bgColor: Color,
    val icon: ImageVector
)

@Composable
private fun PillBadge(
    text: String,
    textColor: Color,
    bgColor: Color,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isPositive: Boolean = false,
    valueContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        if (valueContent != null) {
            valueContent()
        } else {
            Text(
                text = value,
                style = Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isPositive) Emerald400 else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
