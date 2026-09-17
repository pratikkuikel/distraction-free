package com.distractionfree.app

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** Query-count stats only — never the actual queried domains. */
class Stats(private val context: Context) {
    private val total = AtomicInteger(0)
    private val blockedAlwaysOn = AtomicInteger(0)
    private val blockedSocial = AtomicInteger(0)
    private val safeSearchRewrites = AtomicInteger(0)
    @Volatile private var lastWrite = 0L
    private val writeIntervalMs = 10_000L

    fun recordTotal() { total.incrementAndGet(); writeIfDue() }
    fun recordBlockedAlwaysOn() = blockedAlwaysOn.incrementAndGet()
    fun recordBlockedSocial() = blockedSocial.incrementAndGet()
    fun recordSafeSearchRewrite() = safeSearchRewrites.incrementAndGet()

    private fun writeIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastWrite < writeIntervalMs) return
        lastWrite = now
        val obj = JSONObject()
        obj.put("totalQueries", total.get())
        obj.put("blockedAlwaysOn", blockedAlwaysOn.get())
        obj.put("blockedSocial", blockedSocial.get())
        obj.put("safeSearchRewrites", safeSearchRewrites.get())
        obj.put("updatedAt", now)
        try {
            AppPaths.statsFile(context).writeText(obj.toString())
        } catch (e: Exception) {
            // best-effort
        }
    }
}
