package com.distractionfree.app

/** Mirrors the macOS daemon's DNSMessage helpers — same wire-format approach. */
object DnsMessage {

    fun questionName(packet: ByteArray): String? {
        if (packet.size <= 12) return null
        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < packet.size) {
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) return labels.joinToString(".")
            if (len and 0xC0 != 0) return null // compression pointer; not expected in a query's own qname
            offset += 1
            if (offset + len > packet.size) return null
            labels.add(String(packet, offset, len, Charsets.UTF_8))
            offset += len
        }
        return null
    }

    fun nxdomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size < 12) return response
        response[2] = (response[2].toInt() or 0b1000_0000).toByte()
        response[3] = ((response[3].toInt() and 0b0111_0000) or 0b1000_0000 or 0x03).toByte()
        for (i in 6..11) response[i] = 0
        return response
    }

    fun aRecordResponse(query: ByteArray, address: ByteArray, ttl: Int = 60): ByteArray {
        if (query.size <= 12) return nxdomainResponse(query)

        var offset = 12
        while (offset < query.size) {
            val len = query[offset].toInt() and 0xFF
            if (len == 0) { offset += 1; break }
            offset += 1 + len
        }
        offset += 4 // QTYPE + QCLASS
        if (offset > query.size) return nxdomainResponse(query)

        val record = ByteArray(2 + 2 + 2 + 4 + 2 + 4)
        var i = 0
        record[i++] = 0xC0.toByte(); record[i++] = 0x0C.toByte() // pointer to name @12
        record[i++] = 0x00; record[i++] = 0x01 // TYPE A
        record[i++] = 0x00; record[i++] = 0x01 // CLASS IN
        record[i++] = ((ttl shr 24) and 0xFF).toByte()
        record[i++] = ((ttl shr 16) and 0xFF).toByte()
        record[i++] = ((ttl shr 8) and 0xFF).toByte()
        record[i++] = (ttl and 0xFF).toByte()
        record[i++] = 0x00; record[i++] = 0x04 // RDLENGTH
        address.copyInto(record, i)

        val header = query.copyOfRange(0, offset)
        header[2] = (header[2].toInt() or 0b1000_0000).toByte()
        header[3] = ((header[3].toInt() and 0b0111_0000) or 0b1000_0000).toByte()
        header[6] = 0; header[7] = 1 // ANCOUNT = 1
        header[8] = 0; header[9] = 0
        header[10] = 0; header[11] = 0

        return header + record
    }
}
