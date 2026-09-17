package com.distractionfree.app

import android.util.Log
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
class TcpNat(private val vpnService: BlockerVpnService, private val output: FileOutputStream) {

    companion object {
        // A phone under real load opens far more simultaneous TCP flows than
        // a quiet test session — a thread-per-connection design without a
        // cap crashed the whole app with pthread_create OOM on-device. A
        // shared bounded pool plus a hard concurrent-connection cap fixes
        // both the crash and the unbounded memory growth.
        // Matches RelayExecutor's pool size so our own cap (checked before
        // submitting) is what rejects excess connections, not the executor's
        // internal queue silently stalling them.
        private const val MAX_CONCURRENT_CONNECTIONS = 40

        // Circuit breaker: on-device testing showed some app (an ad/analytics
        // SDK, most likely) retrying a failing destination hundreds of times
        // per second with no backoff — that alone can fill the whole pool
        // and starve legitimate connections to other sites. After a few
        // failures in a row, stop even trying that destination for a while;
        // the failing app will still retry, but each retry is now a cheap
        // instant RST instead of a real socket + connect attempt.
        private const val FAILURE_THRESHOLD = 3
        private const val COOLDOWN_MS = 15_000L

        // Global new-connection rate limit: on-device testing showed that
        // the moment the VPN comes up, every app that had an open socket
        // gets cut over at once and tries to reconnect in the same instant —
        // a synchronized burst that doesn't happen under normal operation
        // (sockets fail/reconnect at naturally staggered times otherwise).
        // Firing dozens of brand-new outbound connections in the same
        // millisecond tripped what looked like carrier-side abuse
        // protection (instant ECONNREFUSED across many unrelated real
        // destinations), which then filled the whole pool and collaterally
        // blocked an unrelated, perfectly legitimate site. Silently
        // dropping (not RSTing) excess new-connection attempts lets the
        // client's own TCP retransmission spread them out naturally.
        private const val RATE_LIMIT_WINDOW_MS = 250L
        private const val RATE_LIMIT_MAX_PER_WINDOW = 6

        // Per-destination concurrency cap: on-device testing showed one
        // noisy background service (repeatedly hammering the same 1-2
        // destinations, apparently not backing off between attempts) can
        // still slowly monopolize the whole pool over several seconds even
        // with the global rate limit and circuit breaker in place, since it
        // just keeps feeding new attempts as old ones fail. Capping how many
        // *concurrent* attempts any single destination can hold means a
        // misbehaving destination can only ever tie up a small slice of the
        // pool, no matter how persistent it is — the rest stays free for
        // legitimate, unrelated sites.
        private const val MAX_CONCURRENT_PER_DESTINATION = 4
    }

    private val rateLimitWindowStart = AtomicLong(System.currentTimeMillis())
    private val rateLimitCount = AtomicInteger(0)

    private fun allowedByRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = rateLimitWindowStart.get()
        if (now - windowStart >= RATE_LIMIT_WINDOW_MS) {
            if (rateLimitWindowStart.compareAndSet(windowStart, now)) {
                rateLimitCount.set(0)
            }
        }
        return rateLimitCount.incrementAndGet() <= RATE_LIMIT_MAX_PER_WINDOW
    }

    private val activeCount = AtomicInteger(0)
    private val executor = RelayExecutor.shared
    private class FailureRecord(var count: Int, var cooldownUntil: Long)
    private val recentFailures = ConcurrentHashMap<String, FailureRecord>()
    private val perDestActive = ConcurrentHashMap<String, AtomicInteger>()

    private fun tryAcquireDestSlot(destKey: String): Boolean {
        val counter = perDestActive.getOrPut(destKey) { AtomicInteger(0) }
        return counter.incrementAndGet() <= MAX_CONCURRENT_PER_DESTINATION || run {
            counter.decrementAndGet(); false
        }
    }

    private fun releaseDestSlot(destKey: String) {
        perDestActive[destKey]?.decrementAndGet()
    }

    private fun destinationKey(addr: ByteArray, port: Int) =
        "${addr.joinToString(".") { (it.toInt() and 0xFF).toString() }}:$port"

    private fun isInCooldown(destKey: String): Boolean {
        val record = recentFailures[destKey] ?: return false
        return System.currentTimeMillis() < record.cooldownUntil
    }

    private fun recordFailure(destKey: String) {
        recentFailures.compute(destKey) { _, existing ->
            val record = existing ?: FailureRecord(0, 0)
            record.count += 1
            if (record.count >= FAILURE_THRESHOLD) {
                record.cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                record.count = 0
            }
            record
        }
    }

    private fun recordSuccess(destKey: String) {
        recentFailures.remove(destKey)
    }

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
            if (!allowedByRateLimit()) return // silent drop: let the client's own TCP retransmission spread the burst out
            val destKey = destinationKey(seg.dstAddr, seg.dstPort)
            if (activeCount.get() >= MAX_CONCURRENT_CONNECTIONS) {
                Log.w("df-vpn-tcp", "at capacity ($activeCount), rejecting SYN to $destKey")
            } else if (isInCooldown(destKey)) {
                Log.w("df-vpn-tcp", "$destKey in cooldown, rejecting SYN")
            }
            if (activeCount.get() >= MAX_CONCURRENT_CONNECTIONS || isInCooldown(destKey) || !tryAcquireDestSlot(destKey)) {
                // At capacity, this destination has been failing repeatedly,
                // or it already has too many attempts in flight: fail fast
                // with RST rather than queuing or retrying (TCP flows are
                // long-lived, so queuing would just delay a connection the
                // client will likely retry anyway).
                val rst = TcpSegment.build(seg.dstAddr, seg.srcAddr, seg.dstPort, seg.srcPort,
                    0, seg.seq + 1, syn = false, ackFlag = true, fin = false, rst = true, psh = false,
                    window = 0, payload = ByteArray(0))
                try { output.write(rst) } catch (e: Exception) {}
                return
            }
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
        val destKey = destinationKey(syn.dstAddr, syn.dstPort)

        activeCount.incrementAndGet()
        executor.execute {
            try {
                val dstIp = InetAddress.getByAddress(syn.dstAddr)
                val socket = Socket()
                vpnService.protectAndBind(socket)
                socket.connect(java.net.InetSocketAddress(dstIp, syn.dstPort), 4000)
                conn.socket = socket
                conn.state = State.ESTABLISHED
                recordSuccess(destKey)

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
                Log.e("df-vpn-tcp", "connection to $destKey failed: ${e.javaClass.simpleName}: ${e.message}")
                recordFailure(destKey)
                conn.sendToClient(conn.serverSeq, conn.clientSeq, syn = false, ackFlag = true, fin = false, rst = true, psh = false, payload = ByteArray(0))
            } finally {
                try { conn.socket?.close() } catch (e: Exception) {}
                connections.remove(key)
                activeCount.decrementAndGet()
                releaseDestSlot(destKey)
            }
        }
    }

    private fun resetConnection(conn: Connection) {
        conn.sendToClient(conn.serverSeq, conn.clientSeq, syn = false, ackFlag = true, fin = false, rst = true, psh = false, payload = ByteArray(0))
        try { conn.socket?.close() } catch (e: Exception) {}
        connections.remove(conn.key)
    }
}
