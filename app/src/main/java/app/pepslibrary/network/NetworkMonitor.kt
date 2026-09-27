package app.pepslibrary.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device has a network that can reach the internet, kept live for the whole process.
 *
 * Deliberately generous: "online" means the default network claims internet capability, without also requiring
 * Android's validated flag. Validation can stay false on networks that work fine (some DNS setups, emulators), and
 * treating those as offline would pause the download queue indefinitely. The cost is that a captive portal counts
 * as online, and its failures are then ordinary retryable ones.
 */
class NetworkMonitor private constructor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val online = MutableStateFlow(currentlyOnline())

    val isOnline: StateFlow<Boolean> = online.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                online.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    companion object {
        @Volatile private var instance: NetworkMonitor? = null

        /** One per process: the callback is registered once and never needs unregistering. */
        fun get(context: Context): NetworkMonitor = instance ?: synchronized(this) {
            instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
        }
    }
}
