package com.shilapi.xcertplay.carlife

import android.content.Context
import android.util.Log
import com.diplay.carlife.core.CarLife
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Finds the CarLife phone on the head unit's Wi-Fi/Ethernet LAN.
 *
 * CarLife wireless has no documented discovery protocol: after the user taps
 * 无线连接, the phone listens on CMD 7240 (and the other five ports). We probe
 * CMD only, with a short timeout, over the LAN reported by [WirelessNetwork].
 *
 * Two rounds are deliberate: phones often open the port a second or two after
 * the user taps, so a single sweep can miss a phone that is about to become
 * connectable. The last successfully used address is probed first, which makes
 * a reconnect after a Wi-Fi hiccup nearly instant.
 */
object SubnetScanner {

    private const val TAG = "CarLifeScan"
    private const val PROBE_PORT = CarLife.MD_PORT_CMD
    private const val CONNECT_TIMEOUT_MS = 180
    private const val WORKERS = 24
    private const val ROUNDS = 2
    private const val ROUND_GAP_MS = 1_200L
    private const val LAST_HOST_TIMEOUT_MS = 700

    /**
     * @param lastHost address that worked before; probed before the sweep.
     * @param cancel set by the UI on 停止 / destroy. The scanner stops probing
     *               and reports done; callers also guard with their own epoch.
     */
    fun findPhone(
        context: Context,
        lastHost: InetAddress?,
        cancel: AtomicBoolean,
        onFound: (InetAddress) -> Unit,
        onDone: () -> Unit,
    ): Thread = thread(name = "carlife-scan") {
        var reported = false
        val lock = Any()
        fun report(found: Boolean, address: InetAddress?) {
            synchronized(lock) {
                if (reported) return
                reported = true
                if (found && address != null) onFound(address) else onDone()
            }
        }

        try {
            if (cancel.get()) {
                report(false, null)
                return@thread
            }
            if (lastHost != null) {
                Log.i(TAG, "probing last host ${lastHost.hostAddress}")
                if (probe(lastHost, LAST_HOST_TIMEOUT_MS)) {
                    Log.i(TAG, "last host still listening: ${lastHost.hostAddress}")
                    report(true, lastHost)
                    return@thread
                }
            }

            val lan = WirelessNetwork.active(context)
            if (lan == null) {
                Log.w(TAG, "no Wi-Fi/Ethernet LAN")
                report(false, null)
                return@thread
            }
            Log.i(TAG, "scanning ${lan.summary()}")
            val candidates = WirelessNetwork.scanCandidates(lan)
            val stop = AtomicBoolean(false)

            for (round in 0 until ROUNDS) {
                if (cancel.get() || stop.get()) break
                val queue = ConcurrentLinkedQueue(candidates)
                val workers = List(WORKERS) { index ->
                    thread(name = "carlife-probe-$round-$index") {
                        while (!cancel.get() && !stop.get()) {
                            val address = queue.poll() ?: return@thread
                            if (probe(address, CONNECT_TIMEOUT_MS)) {
                                stop.set(true)
                                report(true, address)
                                return@thread
                            }
                        }
                    }
                }
                workers.forEach { it.join() }
                if (reported || cancel.get()) break
                if (round + 1 < ROUNDS) Thread.sleep(ROUND_GAP_MS)
            }
            report(false, null)
        } catch (t: Throwable) {
            Log.w(TAG, "scan aborted", t)
            report(false, null)
        }
    }

    /** One bounded CMD-port probe; never reads or writes a byte. */
    private fun probe(address: InetAddress, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, PROBE_PORT), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }
}
