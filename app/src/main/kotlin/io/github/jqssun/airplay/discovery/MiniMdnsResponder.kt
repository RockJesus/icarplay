package io.github.jqssun.airplay.discovery

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 兜底 mDNS 应答器。
 *
 * 系统 NsdManager 依赖 mdnsd 守护进程；部分车机 / 模拟器上该进程起不来，
 * `_airplay._tcp` 注册会失败，iPhone 控制中心的「屏幕镜像」就看不到本机。
 * 这里在应用进程内直接用 UDP 5353 + 组播 224.0.0.251 应答查询，
 * 使 AirPlay 可发现性不再依赖系统 mdnsd。
 */
class MiniMdnsResponder(
    private val services: List<MdnsService>,
    private val displayName: String,
) {
    private val running = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    private var worker: Thread? = null

    private val hostLabel: String = run {
        val mac = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .firstOrNull { it.isUp && !it.isLoopback && it.hardwareAddress != null }
                ?.hardwareAddress
        } catch (_: Exception) {
            null
        }
        val tail = mac?.joinToString("") { String.format("%02X", it) }?.takeLast(4)
            ?: runCatching { java.io.File("/sys/class/net/wlan0/address").readText() }
                .getOrNull()?.replace(":", "")?.takeLast(4)?.uppercase()
            ?: "0000"
        "iCarPlay-$tail"
    }

    private fun localIpv4(): InetAddress? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
    } catch (_: Exception) {
        null
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val sock = MulticastSocket(5353).apply {
            reuseAddress = true
            timeToLive = 255
            joinGroup(java.net.InetSocketAddress(InetAddress.getByName(GROUP), 0), null)
        }
        socket = sock
        worker = thread(name = "mini-mdns", isDaemon = true) {
            val buf = ByteArray(1024)
            while (running.get()) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    handle(sock, pkt)
                } catch (e: Exception) {
                    if (running.get()) Log.w(TAG, "recv failed: ${e.message}")
                }
            }
        }
        // 主动宣告三次，提高 iPhone 缓存命中率
        thread(isDaemon = true) {
            repeat(3) {
                try {
                    sendAnswers(sock, null, null, multicast = true)
                    Thread.sleep(900)
                } catch (_: Exception) {
                }
            }
        }
        Log.i(TAG, "started, host=$hostLabel services=${services.map { it.type }}")
    }

    fun stop() {
        running.set(false)
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        worker = null
    }

    private fun handle(sock: MulticastSocket, pkt: DatagramPacket) {
        val data = pkt.data
        val len = pkt.length
        if (len < 12) return
        val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if (flags and 0x8000 != 0) return // 应答包，忽略
        val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (qdCount <= 0) return
        var pos = 12
        val questions = ArrayList<Pair<String, Int>>()
        repeat(qdCount) {
            val (name, next) = readName(data, len, pos)
            pos = next
            if (pos + 4 > len) return
            val qtype = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 4
            questions += name to qtype
        }
        val want = questions.any { (name, qtype) ->
            val n = name.lowercase()
            (qtype == TYPE_PTR && (n == "_airplay._tcp.local" || n == "_raop._tcp.local")) ||
                (qtype == TYPE_A && n == "$hostLabel.local".lowercase()) ||
                ((qtype == TYPE_SRV || qtype == TYPE_TXT) && services.any { it.matches(n) })
        }
        if (!want) return
        sendAnswers(sock, pkt.address, pkt.port, multicast = false)
    }

    private fun MdnsService.matches(lowerName: String): Boolean {
        val inst = "$name.$type.local".lowercase()
        return lowerName == inst
    }

    private fun sendAnswers(
        sock: MulticastSocket,
        replyAddr: InetAddress?,
        replyPort: Int?,
        multicast: Boolean,
    ) {
        val ip = localIpv4() ?: return
        val answers = ByteArrayOutputStream()
        var count = 0

        services.forEach { s ->
            val type = "${s.type}.local"
            val instance = "${s.name}.${s.type}.local"
            // PTR  type -> instance
            writePtr(answers, type, instance)
            count++
            // SRV  instance -> host:port
            writeSrv(answers, instance, "$hostLabel.local", s.port)
            count++
            // TXT  instance -> key=value
            writeTxt(answers, instance, s.txt)
            count++
        }
        writeA(answers, "$hostLabel.local", ip)
        count++

        val out = ByteArrayOutputStream()
        writeShort(out, 0) // id
        writeShort(out, 0x8400) // 应答 + 权威
        writeShort(out, 0) // qd
        writeShort(out, count) // an
        writeShort(out, 0) // ns
        writeShort(out, 0) // ar
        out.write(answers.toByteArray())
        val bytes = out.toByteArray()

        if (multicast) {
            sock.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(GROUP), 5353))
        } else if (replyAddr != null && replyPort != null) {
            sock.send(DatagramPacket(bytes, bytes.size, replyAddr, replyPort))
            sock.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(GROUP), 5353))
        }
    }

    private fun writePtr(out: ByteArrayOutputStream, type: String, instance: String) {
        writeName(out, type)
        writeShort(out, TYPE_PTR); writeShort(out, 0x0001); writeInt(out, TTL)
        writeShort(out, nameLength(instance))
        writeName(out, instance)
    }

    private fun writeSrv(out: ByteArrayOutputStream, instance: String, target: String, port: Int) {
        writeName(out, instance)
        val rd = ByteArrayOutputStream()
        writeShort(rd, 0) // priority
        writeShort(rd, 0) // weight
        writeShort(rd, port)
        writeName(rd, target)
        writeShort(out, TYPE_SRV); writeShort(out, 0x0001); writeInt(out, TTL); writeShort(out, rd.size())
        out.write(rd.toByteArray())
    }

    private fun writeTxt(out: ByteArrayOutputStream, instance: String, txt: Map<String, String>) {
        val rd = ByteArrayOutputStream()
        if (txt.isEmpty()) {
            rd.write(0)
        } else {
            txt.forEach { (k, v) ->
                val s = "$k=$v"
                rd.write(s.length)
                rd.write(s.toByteArray(Charsets.UTF_8))
            }
        }
        writeName(out, instance)
        writeShort(out, TYPE_TXT); writeShort(out, 0x0001); writeInt(out, TTL); writeShort(out, rd.size())
        out.write(rd.toByteArray())
    }

    private fun writeA(out: ByteArrayOutputStream, host: String, ip: InetAddress) {
        val a = ip.address
        writeName(out, host)
        writeShort(out, TYPE_A); writeShort(out, 0x0001); writeInt(out, 120); writeShort(out, 4)
        out.write(a)
    }

    private fun nameLength(name: String): Int =
        name.trimEnd('.').split('.').filter { it.isNotEmpty() }
            .sumOf { 1 + it.toByteArray(Charsets.UTF_8).size } + 1

    private fun writeName(out: ByteArrayOutputStream, name: String) {
        name.trimEnd('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
            val b = label.toByteArray(Charsets.UTF_8)
            out.write(b.size)
            out.write(b)
        }
        out.write(0)
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 24) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun readName(data: ByteArray, len: Int, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var pos = start
        var jumps = 0
        var next = -1
        while (pos < len) {
            val l = data[pos].toInt() and 0xFF
            if (l == 0) {
                pos++
                break
            }
            if (l and 0xC0 == 0xC0) { // 指针
                if (pos + 1 >= len) { pos += 2; break }
                val ptr = ((l and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                if (next < 0) next = pos + 2
                jumps++
                if (jumps > 8) { pos += 2; break }
                pos = ptr
                continue
            }
            if (pos + 1 + l > len) { pos = len; break }
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(data, pos + 1, l, Charsets.UTF_8))
            pos += 1 + l
        }
        return sb.toString() to (if (next > 0) next else pos)
    }

    companion object {
        private const val TAG = "MiniMdns"
        private const val GROUP = "224.0.0.251"
        private const val TTL = 4500
        private const val TYPE_A = 1
        private const val TYPE_PTR = 12
        private const val TYPE_TXT = 16
        private const val TYPE_SRV = 33
    }
}
