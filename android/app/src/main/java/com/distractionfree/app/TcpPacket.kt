package com.distractionfree.app

data class TcpSegment(
    val srcAddr: ByteArray, val dstAddr: ByteArray,
    val srcPort: Int, val dstPort: Int,
    val seq: Long, val ack: Long,
    val flagSyn: Boolean, val flagAck: Boolean, val flagFin: Boolean, val flagRst: Boolean, val flagPsh: Boolean,
    val window: Int,
    val payload: ByteArray,
) {
    companion object {
        fun parse(ip: Ipv4Packet, buf: ByteArray, length: Int): TcpSegment? {
            val o = ip.headerLen
            if (length < o + 20) return null
            val srcPort = u16(buf, o); val dstPort = u16(buf, o + 2)
            val seq = u32(buf, o + 4); val ack = u32(buf, o + 8)
            val dataOff = ((buf[o + 12].toInt() shr 4) and 0x0F) * 4
            val flags = buf[o + 13].toInt() and 0xFF
            val window = u16(buf, o + 14)
            val payloadStart = o + dataOff
            val ipPayloadEnd = if (ip.totalLen in 1..length) ip.totalLen else length
            if (payloadStart > ipPayloadEnd) return null
            val payload = buf.copyOfRange(payloadStart, ipPayloadEnd)
            return TcpSegment(
                ip.srcAddr, ip.dstAddr, srcPort, dstPort, seq, ack,
                flagSyn = flags and 0x02 != 0, flagAck = flags and 0x10 != 0,
                flagFin = flags and 0x01 != 0, flagRst = flags and 0x04 != 0, flagPsh = flags and 0x08 != 0,
                window, payload
            )
        }

        private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        private fun u32(b: ByteArray, i: Int): Long =
            (((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
             ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF))

        /** Builds a full IPv4+TCP packet, replying from (dstAddr:dstPort) back to (srcAddr:srcPort). */
        fun build(
            srcAddr: ByteArray, dstAddr: ByteArray, srcPort: Int, dstPort: Int,
            seq: Long, ack: Long, syn: Boolean, ackFlag: Boolean, fin: Boolean, rst: Boolean, psh: Boolean,
            window: Int, payload: ByteArray
        ): ByteArray {
            val tcpLen = 20 + payload.size
            val tcp = ByteArray(tcpLen)
            putU16(tcp, 0, srcPort); putU16(tcp, 2, dstPort)
            putU32(tcp, 4, seq); putU32(tcp, 8, ack)
            tcp[12] = (5 shl 4).toByte() // data offset = 5 (no options)
            var flags = 0
            if (fin) flags = flags or 0x01
            if (syn) flags = flags or 0x02
            if (rst) flags = flags or 0x04
            if (psh) flags = flags or 0x08
            if (ackFlag) flags = flags or 0x10
            tcp[13] = flags.toByte()
            putU16(tcp, 14, window)
            putU16(tcp, 16, 0) // checksum placeholder
            putU16(tcp, 18, 0) // urgent pointer
            payload.copyInto(tcp, 20)

            // Pseudo-header for checksum: src, dst, zero, protocol, tcp length
            val pseudo = ByteArray(12 + tcpLen)
            srcAddr.copyInto(pseudo, 0); dstAddr.copyInto(pseudo, 4)
            pseudo[8] = 0; pseudo[9] = Ipv4Packet.PROTO_TCP.toByte()
            putU16(pseudo, 10, tcpLen)
            tcp.copyInto(pseudo, 12)
            val cs = Ipv4Packet.checksum(pseudo)
            putU16(tcp, 16, cs)

            val ipHeader = Ipv4Packet.ipHeader(srcAddr, dstAddr, Ipv4Packet.PROTO_TCP, tcpLen)
            return ipHeader + tcp
        }

        private fun putU16(b: ByteArray, i: Int, v: Int) { b[i] = ((v shr 8) and 0xFF).toByte(); b[i + 1] = (v and 0xFF).toByte() }
        private fun putU32(b: ByteArray, i: Int, vIn: Long) {
            val v = vIn and 0xFFFFFFFFL
            b[i] = ((v shr 24) and 0xFF).toByte(); b[i + 1] = ((v shr 16) and 0xFF).toByte()
            b[i + 2] = ((v shr 8) and 0xFF).toByte(); b[i + 3] = (v and 0xFF).toByte()
        }
    }
}
