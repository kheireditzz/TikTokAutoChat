package com.autochat.floating

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

class NetworkMonitor private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    @Volatile
    var isConnected: Boolean = true
        private set

    init {
        isConnected = checkCurrentConnectivity()
        registerNetworkCallback()
    }

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
        // Kirim status awal langsung
        listener(isConnected)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun isOnline(): Boolean {
        return checkCurrentConnectivity()
    }

    private fun checkCurrentConnectivity(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork ?: return false
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            } else {
                @Suppress("DEPRECATION")
                val netInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                netInfo != null && netInfo.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun registerNetworkCallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        updateStatus(true)
                    }

                    override fun onLost(network: Network) {
                        // Cek apakah masih ada network aktif lain
                        mainHandler.postDelayed({
                            updateStatus(checkCurrentConnectivity())
                        }, 500)
                    }

                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        updateStatus(hasInternet)
                    }
                })
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        updateStatus(true)
                    }

                    override fun onLost(network: Network) {
                        mainHandler.postDelayed({
                            updateStatus(checkCurrentConnectivity())
                        }, 500)
                    }
                })
            }
        } catch (_: Exception) {
            isConnected = true
        }
    }

    private fun updateStatus(connected: Boolean) {
        if (isConnected != connected) {
            isConnected = connected
            mainHandler.post {
                for (listener in listeners) {
                    try {
                        listener(connected)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
