package com.saldolivre.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Dashboard : BottomNavItem("dashboard", "Início", Icons.Default.Dashboard)
    data object Transactions : BottomNavItem("transactions", "Extrato", Icons.Default.ReceiptLong)
    data object Receivables : BottomNavItem("receivables", "A Receber", Icons.Default.Payments)
    data object Cards : BottomNavItem("cards", "Cartões", Icons.Default.CreditCard)
    data object GeminiChat : BottomNavItem("chat", "Copiloto", Icons.Default.AutoAwesome)
    data object Menu : BottomNavItem("menu", "Menu", Icons.Default.Menu)
}
