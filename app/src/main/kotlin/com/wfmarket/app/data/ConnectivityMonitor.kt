package com.wfmarket.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device has a network, and a signal for whatever should retry once it returns. */
class ConnectivityMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(hasNetwork())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_online.compareAndSet(false, true)) _reconnected.tryEmit(Unit)
            }

            override fun onLost(network: Network) {
                _online.value = false
            }
        })
    }

