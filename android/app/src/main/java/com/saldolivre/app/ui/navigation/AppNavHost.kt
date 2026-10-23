package com.saldolivre.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.saldolivre.app.ui.screens.invoices.CardInvoicesScreen
import com.saldolivre.app.ui.screens.invoices.CardInvoicesViewModel
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
        BottomNavItem.Menu
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    val bottomNavRoutes = remember {
        setOf(
            BottomNavItem.Dashboard.route,
            BottomNavItem.Transactions.route,
            BottomNavItem.Menu.route
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        floatingActionButton = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200)),
                exit = scaleOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(150))
            ) {
                FloatingActionButton(
                    onClick = { navController.navigate("chat") },
                    containerColor = Emerald500,
                    contentColor = DarkBackground,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Abrir Copiloto IA"
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                NavigationBar(
                    modifier = Modifier
                        .height(54.dp)
                        .drawWithContent {
                            drawContent()
                            val strokeWidth = 0.5.dp.toPx()
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = strokeWidth
                            )
                        },
                    containerColor = DarkSurface.copy(alpha = 0.85f),
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
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
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Emerald400,
                                unselectedIconColor = TextSecondary,
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
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute in bottomNavRoutes && toRoute in bottomNavRoutes) {
                    // Transição entre abas da navbar: crossfade ágil com micro-escala
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                } else {
                    // Transição para submenus/telas filhas: deslizamento suave da direita
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                }
            },
            exitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute in bottomNavRoutes && toRoute in bottomNavRoutes) {
                    fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing))
                } else if (toRoute == "chat" || toRoute == "add_transaction" || toRoute?.startsWith("edit_transaction") == true) {
                    // Recuo sutil em profundidade quando abre o Chat ou formulário modal
                    scaleOut(targetScale = 0.96f, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                } else {
                    // Tela anterior desliza levemente em paralaxe para a esquerda
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        targetOffset = { (it * 0.15f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            },
            popEnterTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute in bottomNavRoutes && toRoute in bottomNavRoutes) {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                } else if (fromRoute == "chat" || fromRoute == "add_transaction" || fromRoute?.startsWith("edit_transaction") == true) {
                    // Retorno de modal / Chat para tela anterior
                    scaleIn(initialScale = 0.96f, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                } else {
                    // Ao voltar de um submenu, a tela anterior ressurge pela esquerda
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        initialOffset = { -(it * 0.15f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                }
            },
            popExitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route
                if (fromRoute in bottomNavRoutes && toRoute in bottomNavRoutes) {
                    fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing))
                } else {
                    // Ao fechar um submenu, ele desliza para a direita de forma fluida
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            }
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
                    onNavigateToReceivables = { navController.navigate("receivables") },
                    onNavigateToInvestments = { navController.navigate("investments") },
                    onNavigateToEditTransaction = { txId -> navController.navigate("edit_transaction/$txId") }
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
            composable("cards") {
                val cardsViewModel = viewModel { CardsViewModel(repository) }
                CardsScreen(
                    viewModel = cardsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 4. Copiloto IA Gemini (Abertura fluida estilo modal drawer de alta tecnologia)
            composable(
                route = "chat",
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180)) +
                        scaleOut(targetScale = 0.96f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180)) +
                        scaleOut(targetScale = 0.96f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                }
            ) {
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
                    onNavigateToReceivables = { navController.navigate("receivables") },
                    onNavigateToCards = { navController.navigate("cards") },
                    onNavigateToCardInvoices = { navController.navigate("card_invoices") },
                    onNavigateToRecurringBills = { navController.navigate("recurring_bills") },
                    onNavigateToInvestments = { navController.navigate("investments") },
                    onNavigateToFriends = { navController.navigate("friends") },
                    onNavigateToProjections = { navController.navigate("projections") },
                    onNavigateToRecentActivity = { navController.navigate("recent_activity") },
                    onLogout = onLogout
                )
            }

            // Form: Nova Transação (Modal Slide Up)
            composable(
                route = "add_transaction",
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                val formViewModel = viewModel { TransactionFormViewModel(repository) }
                TransactionFormScreen(
                    viewModel = formViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Form: Editar Transação Existente (Modal Slide Up)
            composable(
                route = "edit_transaction/{txId}",
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) { backStackEntry ->
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

            // Faturas por Cartão (acompanhamento de fechamento/vencimento/pagamento)
            composable("card_invoices") {
                val invoicesViewModel = viewModel { CardInvoicesViewModel(repository) }
                CardInvoicesScreen(
                    viewModel = invoicesViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditTransaction = { txId -> navController.navigate("edit_transaction/$txId") }
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
            composable("receivables") {
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
