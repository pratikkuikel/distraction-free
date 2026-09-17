package com.distractionfree.app

import android.net.VpnService
import android.util.Log
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Generic UDP relay for everything that isn't a DNS query (QUIC/HTTP3 and
 * plenty of other app traffic) — same reasoning as TcpNat: once we route
 * 0.0.0.0/0 into the tun, we have to actually forward what we're not
 * specifically filtering, or the device loses all connectivity.
 */
class UdpNat(private val vpnService: VpnService, private val output: FileOutputStream) {

    private class Flow(val socket: DatagramSocket)

    private val flows = ConcurrentHashMap<String, Flow>()
    private val idleTimeoutMs = 60_000L

    fun handle(packet: Ipv4UdpPacket) {
        val key = "${packet.srcAddr.joinToString(".") { (it.toInt() and 0xFF).toString() }}:${packet.srcPort}-" +
                   "${packet.dstAddr.joinToString(".") { (it.toInt() and 0xFF).toString() }}:${packet.dstPort}"

        val flow = flows.getOrPut(key) {
            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = idleTimeoutMs.toInt()
            val f = Flow(socket)
            startReader(key, f, packet)
            f
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
        thread(name = "df-udp-$key") {
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
