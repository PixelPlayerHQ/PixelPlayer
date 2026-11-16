package com.theveloper.pixelplay.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Android 17 (API 37) blocks traffic to the local network for apps targeting SDK 37 unless they
 * hold the runtime permission [Manifest.permission.ACCESS_LOCAL_NETWORK] (NEARBY_DEVICES group).
 * Without it, connections to a LAN server simply time out.
 *
 * See https://developer.android.com/privacy-and-security/local-network-permission
 */
object LocalNetworkAccess {

    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    val isPermissionRequired: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    fun isGranted(context: Context): Boolean =
        !isPermissionRequired ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * True when the permission is required on this device, not granted yet, and [serverUrl]
     * points to the local network.
     */
    suspend fun needsPermissionFor(context: Context, serverUrl: String?): Boolean =
        !isGranted(context) && isLocalNetworkUrl(serverUrl)

    /**
     * Whether [url] targets the local network: a private, link-local or unique-local address, a
     * `.local` (mDNS) host, or a host name that resolves to one of those. Loopback is not local
     * network traffic and is ignored. Resolution failures are treated as "not local".
     */
    suspend fun isLocalNetworkUrl(url: String?): Boolean {
        val host = extractHost(url) ?: return false
        if (host.endsWith(".local", ignoreCase = true)) return true
        return withContext(Dispatchers.IO) {
            runCatching { InetAddress.getAllByName(host).any(::isLocalNetworkAddress) }
                .getOrDefault(false)
        }
    }

    internal fun extractHost(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        return runCatching { URI(withScheme).host }
            .getOrNull()
            ?.removeSurrounding("[", "]")
            ?.takeIf { it.isNotBlank() }
    }

    internal fun isLocalNetworkAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return false
        if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        // IPv6 unique local addresses (fc00::/7) are not covered by isSiteLocalAddress.
        return address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC
    }
}
