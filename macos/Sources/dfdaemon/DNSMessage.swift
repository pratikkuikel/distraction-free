import Foundation

enum DNSMessage {
    /// Extracts the QNAME from the Question section of a DNS query packet.
    /// Queries never use compression pointers for their own question name
    /// (there's nothing earlier in the message to point at), so a plain
    /// label walk starting at offset 12 is sufficient and safe.
    static func questionName(from packet: [UInt8]) -> String? {
        guard packet.count > 12 else { return nil }
        var offset = 12
        var labels: [String] = []
        while offset < packet.count {
            let len = Int(packet[offset])
            if len == 0 {
                return labels.joined(separator: ".")
            }
            // Bail on compression pointers or malformed lengths; not expected
            // in a well-formed query's own question name.
            if len & 0xC0 != 0 { return nil }
            offset += 1
            guard offset + len <= packet.count else { return nil }
            let bytes = packet[offset..<(offset + len)]
            guard let label = String(bytes: bytes, encoding: .utf8) else { return nil }
            labels.append(label)
            offset += len
        }
        return nil
    }

    /// Builds an NXDOMAIN response by flipping flags on a copy of the
    /// original query — avoids re-serializing the question section.
    static func nxdomainResponse(for query: [UInt8]) -> [UInt8] {
        var response = query
        guard response.count >= 12 else { return response }
        // Byte 2: QR=1 (response), keep opcode/AA/TC/RD bits from the query.
        response[2] |= 0b1000_0000
        // Byte 3: RA=1, Z=0, RCODE=3 (NXDOMAIN).
        response[3] = (response[3] & 0b0111_0000) | 0b1000_0000 | 0x03
        // ANCOUNT / NSCOUNT / ARCOUNT = 0 (we return no records).
        if response.count >= 8 {
            response[6] = 0; response[7] = 0
            response[8] = 0; response[9] = 0
            response[10] = 0; response[11] = 0
        }
        return response
    }

    /// Builds an A-record response pointing a query at a fixed IPv4 address
    /// (used for SafeSearch DNS rewrites).
    static func aRecordResponse(for query: [UInt8], address: (UInt8, UInt8, UInt8, UInt8), ttl: UInt32 = 60) -> [UInt8] {
        guard query.count > 12 else { return nxdomainResponse(for: query) }
        var response = query
        response[2] |= 0b1000_0000
        response[3] = (response[3] & 0b0111_0000) | 0b1000_0000
        response[6] = 0; response[7] = 1  // ANCOUNT = 1
        response[8] = 0; response[9] = 0
        response[10] = 0; response[11] = 0

        // Find end of question section to append the answer after it.
        var offset = 12
        while offset < response.count {
            let len = Int(response[offset])
            if len == 0 { offset += 1; break }
            offset += 1 + len
        }
        offset += 4 // QTYPE + QCLASS

        var record: [UInt8] = [0xC0, 0x0C] // pointer to name at offset 12
        record += [0x00, 0x01] // TYPE A
        record += [0x00, 0x01] // CLASS IN
        record += withUnsafeBytes(of: ttl.bigEndian, Array.init)
        record += [0x00, 0x04] // RDLENGTH
        record += [address.0, address.1, address.2, address.3]

        if offset <= response.count {
            response.insert(contentsOf: record, at: offset)
        } else {
            response += record
        }
        return response
    }
}
