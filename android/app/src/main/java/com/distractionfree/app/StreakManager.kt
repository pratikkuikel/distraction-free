package com.distractionfree.app

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Tracks consecutive days the VPN has been active. Cheap to check on every
 * query — a no-op unless the calendar day has changed. Disabling resets
 * currentStreak (see MainActivity.requestDisable()) but never touches
 * bestStreak or lastActiveDate, so the streak starts rebuilding from 1 the
 * next active day rather than the whole history being wiped.
 */
class StreakManager(private val context: Context) {
    companion object {
        private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        /** Trying to disable resets your streak — the VPN never actually stops. */
        @Synchronized
        fun resetCurrentStreak(context: Context) {
            val file = AppPaths.streakFile(context)
            val obj = if (file.exists()) {
                try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
            } else JSONObject()
            obj.put("currentStreak", 0)
            file.writeText(obj.toString())
        }
    }

    @Synchronized
    fun touch(now: Date = Date()) {
        val today = dayFormat.format(now)
        val file = AppPaths.streakFile(context)
        val obj = if (file.exists()) {
            try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
        } else JSONObject()

        val lastActiveDate: String? = if (obj.has("lastActiveDate")) obj.getString("lastActiveDate") else null
        if (lastActiveDate == today) return // already recorded today

        var currentStreak = obj.optInt("currentStreak", 0)
        var bestStreak = obj.optInt("bestStreak", 0)

        if (lastActiveDate != null) {
            try {
                val lastDate = dayFormat.parse(lastActiveDate)
                val daysBetween = TimeUnit.MILLISECONDS.toDays(now.time - lastDate!!.time)
                currentStreak = if (daysBetween == 1L) currentStreak + 1 else 1
            } catch (e: Exception) {
                currentStreak = 1
            }
        } else {
            currentStreak = 1
        }
        bestStreak = maxOf(bestStreak, currentStreak)

        obj.put("currentStreak", currentStreak)
        obj.put("bestStreak", bestStreak)
        obj.put("lastActiveDate", today)
        file.writeText(obj.toString())
    }
}
