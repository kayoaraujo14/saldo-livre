package com.saldolivre.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.ui.screens.activity.RecentActivityScreen
import com.saldolivre.app.ui.screens.activity.RecentActivityViewModel
import com.saldolivre.app.ui.screens.cards.CardsScreen
import com.saldolivre.app.ui.screens.cards.CardsViewModel
import com.saldolivre.app.ui.screens.chat.GeminiChatScreen
import com.saldolivre.app.ui.screens.chat.GeminiChatViewModel
import com.saldolivre.app.ui.screens.dashboard.DashboardScreen
import com.saldolivre.app.ui.screens.dashboard.DashboardViewModel
import com.saldolivre.app.ui.screens.form.TransactionFormScreen
import com.saldolivre.app.ui.screens.form.TransactionFormViewModel
import com.saldolivre.app.ui.screens.friends.FriendsScreen
import com.saldolivre.app.ui.screens.friends.FriendsViewModel
import com.saldolivre.app.ui.screens.investments.InvestmentsScreen
import com.saldolivre.app.ui.screens.investments.InvestmentsViewModel
import com.saldolivre.app.ui.screens.menu.MenuScreen
import com.saldolivre.app.ui.screens.menu.MenuViewModel
import com.saldolivre.app.ui.screens.projections.FutureProjectionsScreen
import com.saldolivre.app.ui.screens.projections.FutureProjectionsViewModel
import com.saldolivre.app.ui.screens.receivables.ReceivablesScreen
import com.saldolivre.app.ui.screens.receivables.ReceivablesViewModel
import com.saldolivre.app.ui.screens.recurring.RecurringBillsScreen
import com.saldolivre.app.ui.screens.recurring.RecurringBillsViewModel
import com.saldolivre.app.ui.screens.transactions.TransactionsScreen
import com.saldolivre.app.ui.screens.transactions.TransactionsViewModel
import com.saldolivre.app.ui.theme.*

@Composable
fun AppNavHost(
    navController: NavHostController,
    repository: FinanceRepository,
    onLogout: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Transactions,
        BottomNavItem.Cards,
        BottomNavItem.GeminiChat,
        BottomNavItem.Menu
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = Typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Emerald400,
                                selectedTextColor = Emerald400,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. Dashboard (Início)
            composable(BottomNavItem.Dashboard.route) {
                val dashboardViewModel = viewModel { DashboardViewModel(repository) }
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToTransactions = {
                        navController.navigate(BottomNavItem.Transactions.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAddTransaction = { navController.navigate("add_transaction") },
                    onNavigateToReceivables = { navController.navigate(BottomNavItem.Receivables.route) },
                    onNavigateToInvestments = { navController.navigate("investments") }
                )
            }

            // 2. Transactions (Extrato)
            composable(BottomNavItem.Transactions.route) {
                val transactionsViewModel = viewModel { TransactionsViewModel(repository) }
                TransactionsScreen(
                    viewModel = transactionsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddTransaction = { navController.navigate("add_transaction") },
                    onNavigateToEditTransaction = { txId -> navController.navigate("edit_transaction/$txId") }
                )
            }

            // 3. Cartões
            composable(BottomNavItem.Cards.route) {
                val cardsViewModel = viewModel { CardsViewModel(repository) }
                CardsScreen(
                    viewModel = cardsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 4. Copiloto IA Gemini
            composable(BottomNavItem.GeminiChat.route) {
                val chatViewModel = viewModel { GeminiChatViewModel(repository) }
                GeminiChatScreen(
                    viewModel = chatViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 5. Menu & Ferramentas
            composable(BottomNavItem.Menu.route) {
                val menuViewModel = viewModel { MenuViewModel(repository) }
                MenuScreen(
                    viewModel = menuViewModel,
                    onNavigateToReceivables = { navController.navigate(BottomNavItem.Receivables.route) },
                    onNavigateToCards = {
                        navController.navigate(BottomNavItem.Cards.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToRecurringBills = { navController.navigate("recurring_bills") },
                    onNavigateToInvestments = { navController.navigate("investments") },
                    onNavigateToFriends = { navController.navigate("friends") },
                    onNavigateToProjections = { navController.navigate("projections") },
                    onNavigateToRecentActivity = { navController.navigate("recent_activity") },
                    onNavigateToChat = {
                        navController.navigate(BottomNavItem.GeminiChat.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onLogout = onLogout
                )
            }

            // Form: Nova Transação
            composable("add_transaction") {
                val formViewModel = viewModel { TransactionFormViewModel(repository) }
                TransactionFormScreen(
                    viewModel = formViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Form: Editar Transação Existente
            composable("edit_transaction/{txId}") { backStackEntry ->
                val txId = backStackEntry.arguments?.getString("txId")
                val formViewModel = viewModel(key = "edit_tx_$txId") {
                    TransactionFormViewModel(repository, editingTxId = txId)
                }
                TransactionFormScreen(
                    viewModel = formViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Moldes Recorrentes
            composable("recurring_bills") {
                val recurringViewModel = viewModel { RecurringBillsViewModel(repository) }
                RecurringBillsScreen(
                    viewModel = recurringViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Investimentos & Patrimônio
            composable("investments") {
                val investmentsViewModel = viewModel { InvestmentsViewModel(repository) }
                InvestmentsScreen(
                    viewModel = investmentsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // A Receber (Acessível via Menu e Card KPI)
            composable(BottomNavItem.Receivables.route) {
                val receivablesViewModel = viewModel { ReceivablesViewModel(repository) }
                ReceivablesScreen(
                    viewModel = receivablesViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Rede de Amigos & Aprovações
            composable("friends") {
                val friendsViewModel = viewModel { FriendsViewModel(repository) }
                FriendsScreen(
                    viewModel = friendsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Projeção de Fluxo de Caixa (6 Meses)
            composable("projections") {
                val projectionsViewModel = viewModel { FutureProjectionsViewModel(repository) }
                FutureProjectionsScreen(
                    viewModel = projectionsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Histórico de Atividades / Auditoria
            composable("recent_activity") {
                val activityViewModel = viewModel { RecentActivityViewModel(repository) }
                RecentActivityScreen(
                    viewModel = activityViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMonth = { _ ->
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
