package com.distractionfree.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A parsed IPv4/UDP packet — used only for the DNS traffic this VPN sees. */
data class Ipv4UdpPacket(
    val srcAddr: ByteArray,
    val dstAddr: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
) {
    companion object {
        private const val IPV4_HEADER_MIN_LEN = 20
        private const val UDP_HEADER_LEN = 8
        private const val PROTO_UDP = 17

        fun parse(buf: ByteArray, length: Int): Ipv4UdpPacket? {
            if (length < IPV4_HEADER_MIN_LEN) return null
            val version = (buf[0].toInt() shr 4) and 0x0F
            if (version != 4) return null
            val ihl = (buf[0].toInt() and 0x0F) * 4
            if (ihl < IPV4_HEADER_MIN_LEN || length < ihl + UDP_HEADER_LEN) return null
            val protocol = buf[9].toInt() and 0xFF
            if (protocol != PROTO_UDP) return null

            val srcAddr = buf.copyOfRange(12, 16)
            val dstAddr = buf.copyOfRange(16, 20)

            val udpOffset = ihl
            val srcPort = ((buf[udpOffset].toInt() and 0xFF) shl 8) or (buf[udpOffset + 1].toInt() and 0xFF)
            val dstPort = ((buf[udpOffset + 2].toInt() and 0xFF) shl 8) or (buf[udpOffset + 3].toInt() and 0xFF)
            val udpLen = ((buf[udpOffset + 4].toInt() and 0xFF) shl 8) or (buf[udpOffset + 5].toInt() and 0xFF)
            val payloadLen = udpLen - UDP_HEADER_LEN
            if (payloadLen < 0 || udpOffset + UDP_HEADER_LEN + payloadLen > length) return null
            val payload = buf.copyOfRange(udpOffset + UDP_HEADER_LEN, udpOffset + UDP_HEADER_LEN + payloadLen)

            return Ipv4UdpPacket(srcAddr, dstAddr, srcPort, dstPort, payload)
        }
    }

    /** Builds a response packet with src/dst swapped (as if replying from dstAddr:dstPort). */
    fun buildResponse(responsePayload: ByteArray): ByteArray {
        val totalLen = IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN + responsePayload.size
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv4 header
        buf.put(0x45.toByte())       // version=4, IHL=5 (20 bytes)
        buf.put(0x00.toByte())       // DSCP/ECN
        buf.putShort(totalLen.toShort())
        buf.putShort(0)              // identification
        buf.putShort(0x4000.toShort()) // flags: don't fragment
        buf.put(64.toByte())         // TTL
        buf.put(PROTO_UDP.toByte())
        val checksumPos = buf.position()
        buf.putShort(0)              // header checksum placeholder
        buf.put(dstAddr)             // now the source (we're replying)
        buf.put(srcAddr)             // now the destination

        val ipHeader = buf.array().copyOfRange(0, IPV4_HEADER_MIN_LEN)
        val ipChecksum = checksum(ipHeader)
        buf.putShort(checksumPos, ipChecksum.toShort())

        // UDP header
        val udpLen = UDP_HEADER_LEN + responsePayload.size
        buf.putShort(dstPort.toShort())
        buf.putShort(srcPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0) // checksum optional over IPv4; 0 = not computed

        buf.put(responsePayload)
        return buf.array()
    }

    private fun checksum(bytes: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < bytes.size - 1) {
            sum += ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (bytes.size % 2 == 1) {
            sum += (bytes[bytes.size - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
