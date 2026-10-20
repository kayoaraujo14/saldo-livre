package com.saldolivre.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.saldolivre.app.data.repository.FirestoreFinanceRepository
import com.saldolivre.app.ui.navigation.AppNavHost
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

                    val user = currentUser
                    if (user == null) {
                        LoginScreen(
                            onLoginSuccess = {
                                currentUser = auth.currentUser
                            }
                        )
                    } else {
                        val repository = remember(user.uid) {
                            FirestoreFinanceRepository()
                        }
                        app.setRepositoryInstance(repository)

                        val navController = rememberNavController()
                        AppNavHost(
                            navController = navController,
                            repository = repository
                        )
                    }
                }
            }
        }
    }
}
