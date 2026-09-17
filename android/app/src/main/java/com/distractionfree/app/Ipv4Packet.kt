package com.distractionfree.app

/** Generic IPv4 header parse — just enough to route to the UDP or TCP handler. */
data class Ipv4Packet(
    val protocol: Int,
    val srcAddr: ByteArray,
    val dstAddr: ByteArray,
    val headerLen: Int,
    val totalLen: Int,
) {
    companion object {
        const val PROTO_TCP = 6
        const val PROTO_UDP = 17

        fun parse(buf: ByteArray, length: Int): Ipv4Packet? {
            if (length < 20) return null
            val version = (buf[0].toInt() shr 4) and 0x0F
            if (version != 4) return null
            val ihl = (buf[0].toInt() and 0x0F) * 4
            if (ihl < 20 || length < ihl) return null
            val totalLen = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
            val protocol = buf[9].toInt() and 0xFF
            return Ipv4Packet(protocol, buf.copyOfRange(12, 16), buf.copyOfRange(16, 20), ihl, totalLen)
        }

        fun checksum(bytes: ByteArray): Int {
            var sum = 0
            var i = 0
            while (i < bytes.size - 1) {
                sum += ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (bytes.size % 2 == 1) sum += (bytes[bytes.size - 1].toInt() and 0xFF) shl 8
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            return sum.inv() and 0xFFFF
        }

        fun ipHeader(srcAddr: ByteArray, dstAddr: ByteArray, protocol: Int, payloadLen: Int): ByteArray {
            val totalLen = 20 + payloadLen
            val h = ByteArray(20)
            h[0] = 0x45.toByte()
            h[1] = 0
            h[2] = ((totalLen shr 8) and 0xFF).toByte(); h[3] = (totalLen and 0xFF).toByte()
            h[4] = 0; h[5] = 0
            h[6] = 0x40; h[7] = 0
            h[8] = 64
            h[9] = protocol.toByte()
            h[10] = 0; h[11] = 0 // checksum placeholder
            srcAddr.copyInto(h, 12)
            dstAddr.copyInto(h, 16)
            val cs = checksum(h)
            h[10] = ((cs shr 8) and 0xFF).toByte(); h[11] = (cs and 0xFF).toByte()
            return h
        }
    }
}
