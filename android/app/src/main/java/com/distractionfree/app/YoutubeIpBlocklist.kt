package com.distractionfree.app

import android.content.Context

/**
 * YouTube's video/Shorts playback goes to *.googlevideo.com, but not every
 * client resolves that through a DNS query our resolver ever sees (see
 * DnsBypassBlocklist) — Shorts in particular keeps a pool of already-known
 * edge IPs and often just opens a new connection to one directly. Domain
 * blocking can't stop that. This is the backstop: a maintained list of
 * YouTube's actual CDN IPv4 ranges (community-sourced, refreshed on the same
 * schedule as the other blocklists), checked by raw destination IP.
 *
 * Deliberately NOT part of the always-on categories — only ever consulted
 * when the social schedule says YouTube should be blocked right now, same
 * as the domain-based social block.
 */
object YoutubeIpBlocklist {
    private data class Cidr(val network: Int, val maskBits: Int) {
        val mask: Int = if (maskBits == 0) 0 else -1 shl (32 - maskBits)
        fun matches(addr: Int): Boolean = (addr and mask) == (network and mask)
    }

    @Volatile private var ranges: List<Cidr> = emptyList()

    fun load(context: Context) {
        val file = AppPaths.youtubeCidrList(context)
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
