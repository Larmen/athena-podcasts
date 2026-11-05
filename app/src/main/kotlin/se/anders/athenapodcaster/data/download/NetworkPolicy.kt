package se.anders.athenapodcaster.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Whether the current network is unmetered (Wi-Fi), for the "download over mobile data?" question (FR-033). */
class NetworkPolicy(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun isUnmetered(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
