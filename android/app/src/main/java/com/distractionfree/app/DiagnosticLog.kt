package com.distractionfree.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in diagnostic logging: off by default. Category-match counts are all
 * stats.json ever needed for the app to work, but debugging a specific
 * report ("X is wrongly blocked", "Y isn't blocked") needs to see actual
 * domain-level decisions — this exists only for that, only when the user
 * turns it on themselves, and never leaves the device automatically. See
 * docs/privacy.md. Mirrors the macOS daemon's DiagnosticLog.swift.
 */
class DiagnosticLog(private val context: Context) {
    companion object {
        private const val MAX_BYTES = 5 * 1024 * 1024 // rotate at 5MB
        private const val CHECK_INTERVAL_MS = 2000L
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    @Volatile private var lastEnabledCheck = 0L
    @Volatile private var enabledCache = false
    private val writeLock = Object()

    fun log(line: String) {
        if (!isEnabled()) return
        val entry = "[${dateFormat.format(Date())}] $line\n"
        synchronized(writeLock) {
            try {
                val file = AppPaths.diagnosticLogFile(context)
                if (file.exists() && file.length() > MAX_BYTES) file.delete()
                file.appendText(entry)
            } catch (e: Exception) { /* best-effort, never crash the query path over logging */ }
        }
    }

    private fun isEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastEnabledCheck < CHECK_INTERVAL_MS) return enabledCache
        val enabled = AppPaths.loggingEnabledFile(context).exists()
        enabledCache = enabled
        lastEnabledCheck = now
        return enabled
    }
}
