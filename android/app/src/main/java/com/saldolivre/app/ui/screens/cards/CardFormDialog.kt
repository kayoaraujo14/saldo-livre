package com.saldolivre.app.ui.screens.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.ui.screens.friends.FriendItem
import com.saldolivre.app.data.model.SharedUser
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.ui.theme.*

@Composable
fun CardFormDialog(
    card: Card,
    partner: UserProfile?,
    friends: List<FriendItem> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Card) -> Unit
) {
    var cardName by remember { mutableStateOf(card.cardName) }
    var limitInput by remember { mutableStateOf(card.limit?.toString() ?: "5000.0") }
    var closingDay by remember { mutableStateOf(card.closingDay.toString()) }
    var dueDay by remember { mutableStateOf(card.dueDay.toString()) }
    var thirdPartiesInput by remember { mutableStateOf(card.frequentThirdParties.joinToString(", ")) }
    var sharedUsers by remember(card) {
        val initial = card.sharedWithUsers.toMutableList()
        if (initial.isEmpty() && card.sharedWithUids.isNotEmpty() && partner != null && card.sharedWithUids.contains(partner.uid)) {
            initial.add(SharedUser(uid = partner.uid, email = partner.email, name = partner.displayName))
        }
        mutableStateOf(initial)
    }
    var emailInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(card.color.ifBlank { "violet" }) }

    val isNew = card.id.startsWith("new_")

    val colorOptions = listOf(
        "violet" to Color(0xFF8B5CF6),
        "orange" to Color(0xFFF97316),
        "slate" to Color(0xFF475569),
        "emerald" to Color(0xFF10B981),
        "cyan" to Color(0xFF06B6D4),
        "rose" to Color(0xFFF43F5E)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (isNew) "Novo Cartão" else "Editar Cartão",
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Titular: ${card.ownerName.ifBlank { "Você" }}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 130.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // Card Name
                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it },
                    label = { Text("Nome do Cartão (ex: Nubank, Inter)") },
                    placeholder = { Text("Ex: Nubank Ultravioleta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = Emerald400,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Limit
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Limite Total do Cartão") },
                    prefix = { Text("R$ ", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = Emerald400,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Closing and Due Days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = closingDay,
                        onValueChange = { closingDay = it },
                        label = { Text("Fechamento") },
                        placeholder = { Text("Dia (ex: 5)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald400,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = Emerald400,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it },
                        label = { Text("Vencimento") },
                        placeholder = { Text("Dia (ex: 12)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald400,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = Emerald400,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                // Frequent Third Parties
                OutlinedTextField(
                    value = thirdPartiesInput,
                    onValueChange = { thirdPartiesInput = it },
                    label = { Text("Terceiros Frequentes") },
                    placeholder = { Text("Mãe, Irmão, Carlos") },
                    supportingText = {
                        Text(
                            text = "Gastos lançados para estes terceiros entram no cálculo de A Receber.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = Emerald400,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Compartilhamento com Amigos da Plataforma (Somente Leitura)
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Compartilhar Cartão (Modo Visualização)",
                                style = Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "Amigos convidados terão visualização do cartão no app deles (somente leitura): podem ver os lançamentos de suas cotas e recebem a fatura fechada no extrato deles. Não podem editar o cartão nem lançar novas compras.",
                            style = Typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        // Sugestão de Parceiro (Casal) se existir
                        if (partner != null) {
                            val isPartnerShared = sharedUsers.any { it.uid == partner.uid || (it.email.isNotBlank() && it.email.equals(partner.email, ignoreCase = true)) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Parceiro(a): ${partner.displayName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Switch(
                                    checked = isPartnerShared,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!isPartnerShared) {
                                                sharedUsers = (sharedUsers + SharedUser(uid = partner.uid, email = partner.email, name = partner.displayName)).toMutableList()
                                            }
                                        } else {
                                            sharedUsers = sharedUsers.filter { it.uid != partner.uid && !it.email.equals(partner.email, ignoreCase = true) }.toMutableList()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Emerald400,
                                        checkedTrackColor = Emerald600.copy(alpha = 0.5f),
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = DarkBackground
                                    )
                                )
                            }
                        }

                        // Amigos da rede disponíveis para convite rápido
                        val availableFriends = friends.filter { f ->
                            sharedUsers.none { it.uid == f.friendUid || (it.email.isNotBlank() && it.email.equals(f.friendEmail, ignoreCase = true)) }
                        }
                        if (availableFriends.isNotEmpty()) {
                            Text(
                                text = "Convidar amigos da rede:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(availableFriends) { fr ->
                                    Surface(
                                        shape = CircleShape,
                                        color = Cyan400.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable {
                                            sharedUsers = (sharedUsers + SharedUser(uid = fr.friendUid, email = fr.friendEmail, name = fr.friendName.ifBlank { fr.friendEmail })).toMutableList()
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Cyan400, modifier = Modifier.size(12.dp))
                                            Text(fr.friendName.ifBlank { fr.friendEmail }, fontSize = 11.sp, color = Cyan400, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }

                        // Campo para convidar por e-mail
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                placeholder = { Text("Convidar por e-mail...", fontSize = 12.sp, color = TextMuted) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Emerald400,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            IconButton(
                                onClick = {
                                    val trimmed = emailInput.trim().lowercase()
                                    if (trimmed.isNotBlank() && trimmed.contains("@") && sharedUsers.none { it.email.equals(trimmed, ignoreCase = true) }) {
                                        sharedUsers = (sharedUsers + SharedUser(uid = "", email = trimmed, name = trimmed.substringBefore("@"))).toMutableList()
                                        emailInput = ""
                                    }
                                },
                                enabled = emailInput.trim().contains("@")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Adicionar", tint = if (emailInput.trim().contains("@")) Emerald400 else TextMuted)
                            }
                        }

                        // Lista de convidados atuais
                        if (sharedUsers.isNotEmpty()) {
                            Text(
                                text = "Membros com acesso (${sharedUsers.size}):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sharedUsers.forEach { user ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = DarkSurface,
                                        border = BorderStroke(1.dp, DarkBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = user.name.ifBlank { user.email },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    maxLines = 1
                                                )
                                                if (user.email.isNotBlank() && user.name.isNotBlank() && user.name != user.email) {
                                                    Text(user.email, fontSize = 10.sp, color = TextMuted, maxLines = 1)
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Cyan400.copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.3f))
                                                ) {
                                                    Text(
                                                        text = "👁️ Somente Leitura",
                                                        fontSize = 9.sp,
                                                        color = Cyan400,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { sharedUsers = sharedUsers.filter { it != user }.toMutableList() },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = Rose400, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Color Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cor de Identificação",
                        style = Typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorOptions.forEach { (key, colorVal) ->
                            val isSelected = selectedColor == key
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .clickable { selectedColor = key }
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancelar", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedClosing = closingDay.toIntOrNull()?.coerceIn(1, 31) ?: 5
                            val parsedDue = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: 12
                            val parsedLimit = limitInput.replace(",", ".").toDoubleOrNull() ?: 5000.0
                            val parties = thirdPartiesInput
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            val updated = card.copy(
                                cardName = cardName.trim(),
                                closingDay = parsedClosing,
                                dueDay = parsedDue,
                                limit = parsedLimit,
                                frequentThirdParties = parties,
                                sharedWithUsers = sharedUsers,
                                sharedWithUids = sharedUsers.map { it.uid }.filter { it.isNotBlank() },
                                color = selectedColor
                            )
                            onSave(updated)
                        },
                        enabled = cardName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Salvar Cartão", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
