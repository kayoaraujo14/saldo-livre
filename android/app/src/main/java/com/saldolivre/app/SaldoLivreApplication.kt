package com.saldolivre.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.data.repository.FirestoreFinanceRepository
import com.saldolivre.app.data.repository.MockFinanceRepository

class SaldoLivreApplication : Application() {

    companion object {
        private const val TAG = "SaldoLivreApp"
        lateinit var instance: SaldoLivreApplication
            private set
    }

    // Instância do repositório principal conectado ao Firestore ou Mock
    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        initFirebaseAppCheck()
        initCrashlytics()

        val prefs = getSharedPreferences("saldo_livre_auth_prefs", Context.MODE_PRIVATE)
        val isDemo = prefs.getBoolean("pref_is_demo_mode", false)

        repository = if (isDemo) {
            MockFinanceRepository(this)
        } else {
            FirestoreFinanceRepository()
        }
    }

    private fun initFirebaseAppCheck() {
        try {
            FirebaseApp.initializeApp(this)
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check inicializado com DebugAppCheckProviderFactory")
            } else {
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check inicializado com PlayIntegrityAppCheckProviderFactory")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Inicialização defensiva do Firebase App Check: ${e.message}")
        }
    }

    private fun initCrashlytics() {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            val prefs = getSharedPreferences("saldo_livre_auth_prefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("pref_is_demo_mode", false)
            crashlytics.setCustomKey("is_demo_mode", isDemo)
            crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
            // Telemetria ativa em produção
            crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            Log.d(TAG, "Firebase Crashlytics inicializado com sucesso (demo: $isDemo)")
        } catch (e: Exception) {
            Log.w(TAG, "Inicialização defensiva do Firebase Crashlytics: ${e.message}")
        }
    }

    fun setRepositoryInstance(newRepository: FinanceRepository) {
        repository = newRepository
    }
}
