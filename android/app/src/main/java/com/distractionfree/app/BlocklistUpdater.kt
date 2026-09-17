package com.distractionfree.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the same HaGeZi wildcard-format lists the macOS daemon uses for
 * the always-on categories, and copies the bundled hand-written social list
 * from assets. Run on first launch and from the "Update blocklist" action;
 * never on the main thread.
 */
object BlocklistUpdater {
    private const val BASE_URL = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard"
    private val ALWAYS_ON_SOURCES = listOf(
        "$BASE_URL/nsfw.txt",
        "$BASE_URL/gambling.medium.txt",
        "$BASE_URL/anti.piracy.txt",
        "$BASE_URL/doh-vpn-proxy-bypass.txt",
    )

    fun needsInitialPopulate(context: Context): Boolean =
        !AppPaths.alwaysOnList(context).exists() || !AppPaths.socialList(context).exists()

    /** Blocking call — invoke off the main thread. Returns true on success. */
    fun update(context: Context): Boolean {
        return try {
            val merged = LinkedHashSet<String>()
            for (url in ALWAYS_ON_SOURCES) {
                fetchLines(url).forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        merged.add(trimmed.removePrefix("*."))
                    }
                }
            }
            AppPaths.alwaysOnList(context).writeText(merged.joinToString("\n"))

            // Adult saved separately too — lets the time-back estimate tell
            // "adult" blocks apart from gambling/piracy/bypass.
            val adultOnly = fetchLines("$BASE_URL/nsfw.txt")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.removePrefix("*.") }
            AppPaths.adultList(context).writeText(adultOnly.joinToString("\n"))

            context.assets.open("social-domains.txt").use { input ->
                AppPaths.socialList(context).outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchLines(urlString: String): List<String> {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        return try {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readLines() }
        } finally {
            conn.disconnect()
        }
    }
}
