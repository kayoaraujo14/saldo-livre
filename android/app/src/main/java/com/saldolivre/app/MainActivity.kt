package com.saldolivre.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.saldolivre.app.data.repository.FirestoreFinanceRepository
import com.saldolivre.app.data.repository.MockFinanceRepository
import com.saldolivre.app.ui.navigation.AppNavHost
import com.saldolivre.app.ui.screens.auth.AuthViewModel
import com.saldolivre.app.ui.screens.auth.LoginScreen
import com.saldolivre.app.ui.theme.DarkBackground
import com.saldolivre.app.ui.theme.SaldoLivreTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SaldoLivreApplication

        setContent {
            SaldoLivreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val authViewModel: AuthViewModel = viewModel { AuthViewModel() }
                    val prefs = remember { getSharedPreferences("saldo_livre_auth_prefs", Context.MODE_PRIVATE) }
                    var isDemoMode by remember { mutableStateOf(prefs.getBoolean(AuthViewModel.PREF_IS_DEMO_MODE, false)) }

                    val auth = remember { FirebaseAuth.getInstance() }
                    var currentUser by remember { mutableStateOf(auth.currentUser) }

                    DisposableEffect(auth) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            currentUser = firebaseAuth.currentUser
                        }
                        auth.addAuthStateListener(listener)
                        onDispose {
                            auth.removeAuthStateListener(listener)
                        }
                    }

                    val isLoggedIn = isDemoMode || currentUser != null

                    if (!isLoggedIn) {
                        LoginScreen(
                            authViewModel = authViewModel,
                            onLoginSuccess = { isDemo ->
                                if (isDemo) {
                                    prefs.edit().putBoolean(AuthViewModel.PREF_IS_DEMO_MODE, true).apply()
                                    isDemoMode = true
                                } else {
                                    prefs.edit().putBoolean(AuthViewModel.PREF_IS_DEMO_MODE, false).apply()
                                    isDemoMode = false
                                    currentUser = auth.currentUser
                                }
                            }
                        )
                    } else {
                        val repository = remember(isDemoMode, currentUser?.uid) {
                            if (isDemoMode) {
                                MockFinanceRepository(this@MainActivity)
                            } else {
                                FirestoreFinanceRepository()
                            }
                        }
                        app.setRepositoryInstance(repository)

                        val navController = rememberNavController()
                        AppNavHost(
                            navController = navController,
                            repository = repository,
                            onLogout = {
                                authViewModel.signOut {
                                    prefs.edit().putBoolean(AuthViewModel.PREF_IS_DEMO_MODE, false).apply()
                                    isDemoMode = false
                                    currentUser = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
