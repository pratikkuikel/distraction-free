package com.distractionfree.app

import android.net.VpnService
import android.util.Log
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Minimal userspace TCP relay: terminates each client TCP connection at the
 * tun and bridges it to a real socket to the actual destination, so that
 * routing 0.0.0.0/0 into this VPN (required so captured apps keep normal
 * internet access) doesn't just black-hole every non-DNS connection.
 *
 * This assumes a well-behaved, effectively-lossless transport between the
 * app and the tun (true in practice — it's a local device interface, not a
 * real lossy link), so it skips retransmission/reordering handling that a
 * general-purpose TCP stack would need. It is not a full RFC 793
 * implementation; it's sized for "don't break the internet" in this MVP.
 */
class TcpNat(private val vpnService: VpnService, private val output: FileOutputStream) {

    private enum class State { SYN_RECEIVED, ESTABLISHED, CLOSING }

    private inner class Connection(
        val key: String,
        val clientAddr: ByteArray, val serverAddr: ByteArray,
        val clientPort: Int, val serverPort: Int,
    ) {
        @Volatile var state = State.SYN_RECEIVED
        @Volatile var clientSeq: Long = 0   // next expected byte from client
        @Volatile var serverSeq: Long = 0   // next byte we send to client
        var socket: Socket? = null
        val writeLock = Object()

        fun sendToClient(seq: Long, ack: Long, syn: Boolean, ackFlag: Boolean, fin: Boolean, rst: Boolean, psh: Boolean, payload: ByteArray) {
            val packet = TcpSegment.build(serverAddr, clientAddr, serverPort, clientPort, seq, ack, syn, ackFlag, fin, rst, psh, 65535, payload)
            synchronized(writeLock) {
                try { output.write(packet) } catch (e: Exception) { Log.e("df-vpn-tcp", "write failed: $e") }
            }
        }
    }

    private val connections = ConcurrentHashMap<String, Connection>()

    private fun keyFor(seg: TcpSegment) =
        "${seg.srcAddr.joinToString(".") { (it.toInt() and 0xFF).toString() }}:${seg.srcPort}-" +
        "${seg.dstAddr.joinToString(".") { (it.toInt() and 0xFF).toString() }}:${seg.dstPort}"

    fun handle(seg: TcpSegment) {
        val key = keyFor(seg)
        val existing = connections[key]

        if (seg.flagRst) {
            existing?.socket?.let { try { it.close() } catch (e: Exception) {} }
            connections.remove(key)
            return
        }

        if (existing == null) {
            if (!seg.flagSyn) return // unknown connection, not a new one — drop
            openConnection(key, seg)
            return
        }

        when {
            seg.flagFin -> {
                existing.clientSeq = seg.seq + 1
                try { existing.socket?.shutdownOutput() } catch (e: Exception) {}
                existing.sendToClient(existing.serverSeq, existing.clientSeq, syn = false, ackFlag = true, fin = false, rst = false, psh = false, payload = ByteArray(0))
            }
            seg.payload.isNotEmpty() -> {
                try {
                    existing.socket?.getOutputStream()?.write(seg.payload)
                    existing.clientSeq = seg.seq + seg.payload.size
                    existing.sendToClient(existing.serverSeq, existing.clientSeq, syn = false, ackFlag = true, fin = false, rst = false, psh = false, payload = ByteArray(0))
                } catch (e: Exception) {
                    resetConnection(existing)
                }
            }
            else -> {
                // Pure ACK (e.g. completing the handshake) — nothing to relay.
            }
        }
    }

    private fun openConnection(key: String, syn: TcpSegment) {
        val conn = Connection(key, syn.srcAddr, syn.dstAddr, syn.srcPort, syn.dstPort)
        conn.clientSeq = syn.seq + 1
        conn.serverSeq = Random.nextLong(0, 0xFFFFFFFL)
        connections[key] = conn

        thread(name = "df-tcp-$key") {
            try {
                val dstIp = InetAddress.getByAddress(syn.dstAddr)
                val socket = Socket()
                vpnService.protect(socket)
                socket.connect(java.net.InetSocketAddress(dstIp, syn.dstPort), 8000)
                conn.socket = socket
                conn.state = State.ESTABLISHED

                // SYN-ACK
                conn.sendToClient(conn.serverSeq, conn.clientSeq, syn = true, ackFlag = true, fin = false, rst = false, psh = false, payload = ByteArray(0))
                conn.serverSeq += 1

                // Pump server->client data until EOF/close.
                val buf = ByteArray(4096)
                val input = socket.getInputStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    val chunk = buf.copyOfRange(0, n)
                    conn.sendToClient(conn.serverSeq, conn.clientSeq, syn = false, ackFlag = true, fin = false, rst = false, psh = true, payload = chunk)
                    conn.serverSeq += n
                }
                // Server closed — send FIN.
                conn.sendToClient(conn.serverSeq, conn.clientSeq, syn = false, ackFlag = true, fin = true, rst = false, psh = false, payload = ByteArray(0))
                conn.serverSeq += 1
            } catch (e: Exception) {
                Log.e("df-vpn-tcp", "connection to ${syn.dstPort} failed: $e")
                conn.sendToClient(conn.serverSeq, conn.clientSeq, syn = false, ackFlag = true, fin = false, rst = true, psh = false, payload = ByteArray(0))
            } finally {
                try { conn.socket?.close() } catch (e: Exception) {}
                connections.remove(key)
            }
        }
    }

    private fun resetConnection(conn: Connection) {
        conn.sendToClient(conn.serverSeq, conn.clientSeq, syn = false, ackFlag = true, fin = false, rst = true, psh = false, payload = ByteArray(0))
        try { conn.socket?.close() } catch (e: Exception) {}
        connections.remove(conn.key)
    }
}
