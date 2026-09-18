package com.distractionfree.app

import android.content.Context

/**
 * Instagram/Facebook CDN traffic doesn't always ask our DNS server either —
 * same failure mode as YouTube (see YoutubeIpBlocklist): the app keeps a
 * pool of already-known edge IPs and opens connections directly, or the feed
 * loads from fbcdn.net edge nodes that were never re-resolved after the app
 * cold-started. Domain blocking (instagram.com, cdninstagram.com) can't stop
 * that — a real-device log showed graph.facebook.com being correctly
 * blocked on every request while photos/video kept loading with zero
 * matching DNS traffic at all. This is the backstop: Meta's published CDN
 * IPv4 ranges (community-sourced, refreshed on the same schedule as the
 * other blocklists), checked by raw destination IP.
 *
 * Safe re: WhatsApp even though it shares Meta's infrastructure — WhatsApp
 * is exempted at the VPN level (AppExemptions/addDisallowedApplication), so
 * its packets never enter the tun interface and never reach this check.
 *
 * Deliberately NOT part of the always-on categories — only ever consulted
 * when the social schedule says Instagram/Facebook should be blocked right
 * now, same as the domain-based social block and YoutubeIpBlocklist.
 */
object MetaCdnIpBlocklist {
    private data class Cidr(val network: Int, val maskBits: Int) {
        val mask: Int = if (maskBits == 0) 0 else -1 shl (32 - maskBits)
        fun matches(addr: Int): Boolean = (addr and mask) == (network and mask)
    }

    @Volatile private var ranges: List<Cidr> = emptyList()

    fun load(context: Context) {
        val file = AppPaths.metaCidrList(context)
        if (!file.exists()) return
        ranges = file.readLines().mapNotNull { parseLine(it) }
    }

    fun isBlocked(addr: ByteArray): Boolean {
        if (addr.size != 4) return false
        val value = ((addr[0].toInt() and 0xFF) shl 24) or
            ((addr[1].toInt() and 0xFF) shl 16) or
            ((addr[2].toInt() and 0xFF) shl 8) or
            (addr[3].toInt() and 0xFF)
        val snapshot = ranges
        for (r in snapshot) if (r.matches(value)) return true
        return false
    }

    private fun parseLine(rawLine: String): Cidr? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val parts = line.split("/")
        if (parts.size != 2) return null
        val octets = parts[0].split(".")
        if (octets.size != 4) return null
        return try {
            val bits = octets.map { it.toInt() }
            val network = (bits[0] shl 24) or (bits[1] shl 16) or (bits[2] shl 8) or bits[3]
            Cidr(network, parts[1].toInt())
        } catch (e: NumberFormatException) {
            null
        }
    }
}
