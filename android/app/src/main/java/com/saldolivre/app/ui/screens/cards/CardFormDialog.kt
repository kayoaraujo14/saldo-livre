package com.saldolivre.app.ui.screens.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.saldolivre.app.data.model.SharedUser
import com.saldolivre.app.data.model.UserProfile
import com.saldolivre.app.ui.theme.*

@Composable
fun CardFormDialog(
    card: Card,
    partner: UserProfile?,
    onDismiss: () -> Unit,
    onSave: (Card) -> Unit
) {
    var cardName by remember { mutableStateOf(card.cardName) }
    var limitInput by remember { mutableStateOf(card.limit?.toString() ?: "5000.0") }
    var closingDay by remember { mutableStateOf(card.closingDay.toString()) }
    var dueDay by remember { mutableStateOf(card.dueDay.toString()) }
    var thirdPartiesInput by remember { mutableStateOf(card.frequentThirdParties.joinToString(", ")) }
    var shareWithPartner by remember { mutableStateOf(card.sharedWithUsers.isNotEmpty() || card.sharedWithUids.isNotEmpty()) }
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

                // Partner Sharing Switch
                if (partner != null) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
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
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        text = "Compartilhar com ${partner.displayName}",
                                        style = Typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Permite que seu parceiro selecione este cartão ao lançar despesas",
                                        style = Typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = shareWithPartner,
                                onCheckedChange = { shareWithPartner = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Emerald400,
                                    checkedTrackColor = Emerald600.copy(alpha = 0.5f),
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkBackground
                                )
                            )
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

                            val sharedUsers = if (shareWithPartner && partner != null) {
                                listOf(SharedUser(uid = partner.uid, email = partner.email, name = partner.displayName))
                            } else emptyList()

                            val updated = card.copy(
                                cardName = cardName.trim(),
                                closingDay = parsedClosing,
                                dueDay = parsedDue,
                                limit = parsedLimit,
                                frequentThirdParties = parties,
                                sharedWithUsers = sharedUsers,
                                sharedWithUids = sharedUsers.map { it.uid },
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
