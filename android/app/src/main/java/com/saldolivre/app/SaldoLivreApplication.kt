package com.saldolivre.app

import android.app.Application
import android.content.Context
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.data.repository.FirestoreFinanceRepository
import com.saldolivre.app.data.repository.MockFinanceRepository

class SaldoLivreApplication : Application() {

    companion object {
        lateinit var instance: SaldoLivreApplication
            private set
    }

    // Instância do repositório principal conectado ao Firestore ou Mock
    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val prefs = getSharedPreferences("saldo_livre_auth_prefs", Context.MODE_PRIVATE)
        val isDemo = prefs.getBoolean("pref_is_demo_mode", false)

        repository = if (isDemo) {
            MockFinanceRepository(this)
        } else {
            FirestoreFinanceRepository()
        }
    }

    fun setRepositoryInstance(newRepository: FinanceRepository) {
        repository = newRepository
    }
}
