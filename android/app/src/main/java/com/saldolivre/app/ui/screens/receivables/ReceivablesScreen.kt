package com.saldolivre.app.ui.screens.receivables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
                    }
                },
                title = {
                    Text(text = "Valores a Receber", style = Typography.titleLarge, fontWeight = FontWeight.Bold)
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
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(1.dp, Purple500.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(text = "TOTAL A RECEBER NO MÊS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple400)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = FinanceEngine.formatCurrency(summary.receivableTotal),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple400
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Parceiro(a)", fontSize = 11.sp, color = TextMuted)
                                Text(text = FinanceEngine.formatCurrency(summary.receivableFromPartner), style = Typography.labelLarge)
                            }
                            Column {
                                Text(text = "Amigos da Rede", fontSize = 11.sp, color = TextMuted)
                                Text(text = FinanceEngine.formatCurrency(summary.receivableFromFriends), style = Typography.labelLarge)
                            }
                            Column {
                                Text(text = "Terceiros", fontSize = 11.sp, color = TextMuted)
                                Text(text = FinanceEngine.formatCurrency(summary.receivableFromExternal), style = Typography.labelLarge)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Lançamentos Pendentes (${uiState.items.size})",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (uiState.items.isEmpty()) {
                item {
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
                items(uiState.items) { item ->
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
                                Text(text = item.name, style = Typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(text = item.description, style = Typography.bodyMedium, color = TextSecondary)
                                item.cardName?.let {
                                    Text(text = "Pago com $it", fontSize = 11.sp, color = TextMuted)
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = FinanceEngine.formatCurrency(item.amount),
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Purple400
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { viewModel.markAsReimbursed(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Dar baixa", fontSize = 11.sp, color = Emerald400)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
