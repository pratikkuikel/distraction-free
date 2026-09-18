package com.distractionfree.app

import android.util.Log
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic UDP relay for everything that isn't a DNS query (QUIC/HTTP3 and
 * plenty of other app traffic) — same reasoning as TcpNat: once we route
 * 0.0.0.0/0 into the tun, we have to actually forward what we're not
 * specifically filtering, or the device loses all connectivity.
 */
class UdpNat(private val vpnService: BlockerVpnService, private val output: FileOutputStream) {

    companion object {
        // Shares RelayExecutor's pool with TcpNat — see TcpNat for why an
        // unbounded thread-per-flow design isn't safe on a real device.
        private const val MAX_CONCURRENT_FLOWS = 8
    }

    private class Flow(val socket: DatagramSocket)

    private val flows = ConcurrentHashMap<String, Flow>()
    private val idleTimeoutMs = 60_000L
    private val executor = RelayExecutor.shared

    fun handle(packet: Ipv4UdpPacket) {
        if (DnsBypassBlocklist.isBlocked(packet.dstAddr)) return // see DnsBypassBlocklist for why
        if (vpnService.isYoutubeCdnBlockedNow(packet.dstAddr)) return // see YoutubeIpBlocklist for why

        val key = "${packet.srcAddr.joinToString(".") { (it.toInt() and 0xFF).toString() }}:${packet.srcPort}-" +
                   "${packet.dstAddr.joinToString(".") { (it.toInt() and 0xFF).toString() }}:${packet.dstPort}"

        val flow = flows[key] ?: run {
            if (flows.size >= MAX_CONCURRENT_FLOWS) return // at capacity: drop, client will retry or give up
            val socket = try {
                DatagramSocket().also { vpnService.protectAndBind(it); it.soTimeout = idleTimeoutMs.toInt() }
            } catch (e: Exception) { return }
            val f = Flow(socket)
            val prior = flows.putIfAbsent(key, f)
            if (prior != null) { socket.close(); prior } else { startReader(key, f, packet); f }
        }

        try {
            val dstIp = InetAddress.getByAddress(packet.dstAddr)
            flow.socket.send(DatagramPacket(packet.payload, packet.payload.size, dstIp, packet.dstPort))
        } catch (e: Exception) {
            flows.remove(key)
            try { flow.socket.close() } catch (e2: Exception) {}
        }
    }

    private fun startReader(key: String, flow: Flow, originalPacket: Ipv4UdpPacket) {
        executor.execute {
            val buf = ByteArray(65507)
            try {
                while (true) {
                    val resp = DatagramPacket(buf, buf.size)
                    flow.socket.receive(resp)
                    val payload = buf.copyOfRange(0, resp.length)
                    output.write(originalPacket.buildResponse(payload))
                }
            } catch (e: Exception) {
                // idle timeout or socket closed — flow is done
            } finally {
                flows.remove(key)
                try { flow.socket.close() } catch (e2: Exception) {}
            }
        }
    }
}
