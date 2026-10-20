package com.saldolivre.app

import android.app.Application
import com.saldolivre.app.data.repository.FinanceRepository
import com.saldolivre.app.data.repository.FirestoreFinanceRepository

class SaldoLivreApplication : Application() {

    // Instância do repositório principal conectado ao Firebase Firestore em tempo real
    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Inicializa por padrão com FirestoreFinanceRepository
        repository = FirestoreFinanceRepository()
    }

    fun setRepositoryInstance(newRepository: FinanceRepository) {
        repository = newRepository
    }
}
