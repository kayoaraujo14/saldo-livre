package com.saldolivre.app.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Monitor reativo de conectividade de rede para Android.
 * Notifica os fluxos do Compose e ViewModels quando o dispositivo entra ou sai do modo offline.
 * Seguro para execução em testes JVM puros (quando context ou connectivityManager forem nulos).
 */
class NetworkMonitor(context: Context? = null) {

    private val connectivityManager: ConnectivityManager? = runCatching {
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    /**
     * Fluxo reativo do status de conexão à Internet (true = online, false = offline).
     */
    val isOnline: Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(true)
            awaitClose {}
            return@callbackFlow
        }

        // Emite status inicial imediatamente
        trySend(isCurrentlyOnline())

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            private val validNetworks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull()
                val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (hasInternet) {
                    validNetworks.add(network)
                    trySend(true)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet) {
                    validNetworks.add(network)
                    trySend(true)
                } else {
                    validNetworks.remove(network)
                    if (validNetworks.isEmpty()) {
                        trySend(isCurrentlyOnline())
                    }
                }
            }

            override fun onLost(network: Network) {
                validNetworks.remove(network)
                trySend(validNetworks.isNotEmpty() || isCurrentlyOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            trySend(isCurrentlyOnline())
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged().conflate()

    /**
     * Checagem síncrona instantânea do status de conexão.
     */
    fun isCurrentlyOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = runCatching { cm.activeNetwork }.getOrNull() ?: return false
        val capabilities = runCatching { cm.getNetworkCapabilities(activeNetwork) }.getOrNull() ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
