package com.distractionfree.app

import java.net.InetAddress

/**
 * Same live-resolved SafeSearch approach as the macOS daemon: resolve the
 * mapped forced-safe-search hostname's current A record rather than
 * hardcoding an IP, since some (Bing) are CDN-backed and rotate.
 */
object SafeSearch {
    val targets = mapOf(
        "www.google.com" to "forcesafesearch.google.com",
        "google.com" to "forcesafesearch.google.com",
        "www.bing.com" to "strict.bing.com",
        "bing.com" to "strict.bing.com",
        "www.youtube.com" to "restrict.youtube.com",
        "youtube.com" to "restrict.youtube.com",
        "m.youtube.com" to "restrict.youtube.com",
    )

    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private data class CacheEntry(val address: ByteArray, val expiresAt: Long)
    private val cache = HashMap<String, CacheEntry>()

    @Synchronized
    fun resolve(hostname: String): ByteArray? {
        val now = System.currentTimeMillis()
        cache[hostname]?.let { if (it.expiresAt > now) return it.address }
        return try {
            val addr = InetAddress.getByName(hostname).address
            if (addr.size == 4) {
                cache[hostname] = CacheEntry(addr, now + CACHE_TTL_MS)
                addr
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
