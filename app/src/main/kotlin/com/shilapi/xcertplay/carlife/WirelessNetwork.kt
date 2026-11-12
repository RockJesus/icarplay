package com.shilapi.xcertplay.carlife

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Finds the LAN the head unit is actually on.
 *
 * A car can have cellular, Wi-Fi and a VPN active at the same time. Scanning
 * every interface (the old SubnetScanner behaviour) wasted seconds on rmnet
 * and VPN /24s and could probe addresses the phone can never be on. CarLife
 * wireless needs the Wi-Fi/Ethernet LAN only, so enumerate that network's own
 * address and prefix here.
 */
object WirelessNetwork {

    data class Lan(
        val interfaceName: String,
        val localAddress: Inet4Address,
        val prefixLength: Int,
    ) {
        fun summary(): String = "$interfaceName ${localAddress.hostAddress}/$prefixLength"
    }

    /** First active Wi-Fi or Ethernet IPv4 LAN, or null when offline. */
    fun active(context: Context): Lan? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                continue
            }
            val lp = cm.getLinkProperties(network) ?: continue
            for (link in lp.linkAddresses) {
                val address = link.address
                if (address is Inet4Address &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress
                ) {
                    return Lan(
                        interfaceName = lp.interfaceName ?: "lan",
                        localAddress = address,
                        prefixLength = link.prefixLength.coerceIn(0, 32),
                    )
                }
            }
        }
        return null
    }

    /**
     * Addresses to probe: the LAN around the head unit's own IPv4.
     *
     * If the prefix is wider than /24, only the local /24 is scanned; probing
     * 65k addresses is not a connection attempt, it is a network scan. The
     * manual-IP dialog covers unusual /16 topologies.
     */
    fun scanCandidates(lan: Lan): List<InetAddress> {
        val local = toInt(lan.localAddress.address)
        val prefix = lan.prefixLength.coerceIn(0, 32)
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        var network = local and mask
        var broadcast = network or mask.inv()
        // Keep the sweep bounded even on a /8 lab network.
        if (broadcast.toLong() - network.toLong() > 254L) {
            network = local and -256
            broadcast = network or 255
        }
        val result = ArrayList<InetAddress>(254)
        for (value in (network.toLong() + 1) until broadcast.toLong()) {
            if (value == local.toLong()) continue
            val bytes = byteArrayOf(
                ((value ushr 24) and 0xFF).toByte(),
                ((value ushr 16) and 0xFF).toByte(),
                ((value ushr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
            result.add(InetAddress.getByAddress(bytes))
        }
        return result
    }

    /** Strict IPv4 literal only: never let a typed hostname reach DNS. */
    fun parseIpv4(text: String): Inet4Address? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            bytes[index] = value.toByte()
        }
        val address = InetAddress.getByAddress(bytes) as? Inet4Address ?: return null
        if (address.isAnyLocalAddress || address.isLoopbackAddress) return null
        return address
    }

    fun describe(context: Context): String {
        val lan = active(context) ?: return "未连接 Wi-Fi / 有线网络"
        return "本机 ${lan.summary()}"
    }

    private fun toInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
}
