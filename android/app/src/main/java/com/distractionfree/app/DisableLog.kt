package com.distractionfree.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The "disable" flow, exactly as designed: every attempt is logged with a
 * timestamp and shown as a 24h countdown that never actually completes.
 * There is no code path here that ever disables enforcement — the only real
 * way off is uninstalling the app or revoking the VPN permission yourself,
 * outside this UI.
 */
object DisableLog {
    private const val WINDOW_HOURS = 24

    fun recordAttempt(context: Context) {
        val file = AppPaths.disableLogFile(context)
        val arr = if (file.exists()) {
            try { JSONArray(file.readText()) } catch (e: Exception) { JSONArray() }
        } else JSONArray()
        val entry = JSONObject()
        entry.put("timestamp", System.currentTimeMillis())
        arr.put(entry)
        file.writeText(arr.toString())
    }

    fun attemptCount(context: Context): Int {
        val file = AppPaths.disableLogFile(context)
        if (!file.exists()) return 0
        return try { JSONArray(file.readText()).length() } catch (e: Exception) { 0 }
    }

    fun lastAttemptMillis(context: Context): Long? {
        val file = AppPaths.disableLogFile(context)
        if (!file.exists()) return null
        return try {
            val arr = JSONArray(file.readText())
            if (arr.length() == 0) null
            else arr.getJSONObject(arr.length() - 1).getLong("timestamp")
        } catch (e: Exception) { null }
    }

    /** Hours remaining on the (permanently self-renewing) fake countdown. */
    fun hoursRemaining(context: Context): Int? {
        val last = lastAttemptMillis(context) ?: return null
        val elapsedHours = ((System.currentTimeMillis() - last) / (1000 * 60 * 60)).toInt()
        return (WINDOW_HOURS - elapsedHours).coerceAtLeast(0)
    }
}
