package com.distractionfree.app

/**
 * Known public DNS-over-HTTPS/DNS-over-TLS resolver IPs. Domain-based
 * blocking (DomainTrie over DNS queries) only works when an app actually
 * asks *our* DNS server — apps that bootstrap their own encrypted resolver
 * with a hardcoded IP (no A/AAAA lookup involved) skip that entirely, and
 * their query then looks like ordinary opaque TCP/UDP to our NAT relay,
 * which doesn't inspect payloads. This is exactly how the YouTube app kept
 * resolving googlevideo.com after it was added to the social blocklist —
 * the video app's connection to Google's own resolver never touched our DNS
 * handler. Blocking these IPs outright at the NAT layer (TcpNat/UdpNat, all
 * ports — these addresses don't serve anything else) forces that kind of
 * resolution to fail and fall back to the system resolver, which we do see.
 *
 * Not exhaustive (there's no way to enumerate every DoH provider an app
 * might embed), but covers the major public ones apps commonly hardcode.
 */
object DnsBypassBlocklist {
    private val blockedIps = setOf(
        "8.8.8.8", "8.8.4.4",             // Google Public DNS
        "1.1.1.1", "1.0.0.1",             // Cloudflare
        "9.9.9.9", "149.112.112.112",     // Quad9
        "208.67.222.222", "208.67.220.220", // OpenDNS
        "94.140.14.14", "94.140.15.15",   // AdGuard DNS
    )

    fun isBlocked(addr: ByteArray): Boolean {
        if (addr.size != 4) return false
        val dotted = "${addr[0].toInt() and 0xFF}.${addr[1].toInt() and 0xFF}.${addr[2].toInt() and 0xFF}.${addr[3].toInt() and 0xFF}"
        return dotted in blockedIps
    }
}
