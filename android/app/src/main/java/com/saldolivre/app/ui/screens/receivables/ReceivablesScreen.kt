package com.saldolivre.app.ui.screens.receivables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivablesScreen(
    viewModel: ReceivablesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val summary = uiState.summary

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
                    }
                },
                title = {
                    Text(
                        text = "Valores a Receber",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item(key = "receivables_summary_header", contentType = "receivables_summary_header") {
                val formattedTotal = remember(summary.receivableTotal) { FinanceEngine.formatCurrency(summary.receivableTotal) }
                val formattedPartner = remember(summary.receivableFromPartner) { FinanceEngine.formatCurrency(summary.receivableFromPartner) }
                val formattedFriends = remember(summary.receivableFromFriends) { FinanceEngine.formatCurrency(summary.receivableFromFriends) }
                val formattedExternal = remember(summary.receivableFromExternal) { FinanceEngine.formatCurrency(summary.receivableFromExternal) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(1.dp, Purple500.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "TOTAL A RECEBER NO MÊS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formattedTotal,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Parceiro(a)",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedPartner,
                                    style = Typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Amigos da Rede",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedFriends,
                                    style = Typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Terceiros",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedExternal,
                                    style = Typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item(key = "receivables_list_header", contentType = "receivables_list_header") {
                Text(
                    text = "Lançamentos Pendentes (${uiState.items.size})",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (uiState.items.isEmpty()) {
                item(key = "receivables_empty", contentType = "receivables_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Nenhum valor a receber pendente.", color = TextMuted)
                    }
                }
            } else {
                items(
                    items = uiState.items,
                    key = { "${it.transactionId}_${it.name}" },
                    contentType = { "receivable_item" }
                ) { item ->
                    val formattedItemAmount = remember(item.amount) { FinanceEngine.formatCurrency(item.amount) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Purple500.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, Purple500.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Purple400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.description,
                                    style = Typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                item.cardName?.let {
                                    Text(
                                        text = "Pago com $it",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formattedItemAmount,
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Purple400,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { viewModel.markAsReimbursed(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Dar baixa",
                                        fontSize = 11.sp,
                                        color = Emerald400,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
