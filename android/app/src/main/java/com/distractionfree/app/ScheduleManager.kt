package com.distractionfree.app

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

/**
 * Social media: hard-blocked 20:00-09:00 daily (no override), otherwise
 * driven by a manual toggle file — same rules as the macOS daemon.
 */
class ScheduleManager(private val context: Context) {

    companion object {
        const val BLOCK_START_HOUR = 20
        const val BLOCK_END_HOUR = 9
    }

    fun isInForcedWindow(now: Calendar = Calendar.getInstance()): Boolean {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour >= BLOCK_START_HOUR || hour < BLOCK_END_HOUR
    }

    fun isSocialBlockedNow(now: Calendar = Calendar.getInstance()): Boolean {
        if (isInForcedWindow(now)) return true
        return readManualToggle()
    }

    fun readManualToggle(): Boolean {
        val file = AppPaths.socialToggleFile(context)
        if (!file.exists()) return false
        return try {
            JSONObject(file.readText()).optBoolean("manualBlock", false)
        } catch (e: Exception) {
            false
        }
    }

    fun setManualToggle(blocked: Boolean) {
        val obj = JSONObject()
        obj.put("manualBlock", blocked)
        obj.put("updatedAt", System.currentTimeMillis())
        AppPaths.socialToggleFile(context).writeText(obj.toString())
    }
}
