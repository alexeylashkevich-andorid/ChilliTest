package com.lashla.chillitest

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

@Composable
internal fun rememberOnlineState(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    val online = remember(manager) {
        connectivityState(
            register = manager::registerDefaultNetworkCallback,
            unregister = manager::unregisterNetworkCallback,
            isOnline = { manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() == true },
        )
    }
    return online.collectAsStateWithLifecycle(initialValue = manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() == true)
}

internal fun connectivityState(
    register: (ConnectivityManager.NetworkCallback, Handler) -> Unit,
    unregister: (ConnectivityManager.NetworkCallback) -> Unit,
    isOnline: () -> Boolean,
): Flow<Boolean> = callbackFlow {
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(capabilities.hasInternet())
        }

        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    register(callback, Handler(Looper.getMainLooper()))
    try {
        trySend(isOnline())
        awaitClose {}
    } finally {
        unregister(callback)
    }
}.flowOn(Dispatchers.Main.immediate).distinctUntilChanged()

private fun NetworkCapabilities.hasInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
