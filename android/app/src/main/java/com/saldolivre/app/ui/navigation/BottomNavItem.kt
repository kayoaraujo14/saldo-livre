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
    data object Menu : BottomNavItem("menu", "Menu", Icons.Default.Menu)
}
