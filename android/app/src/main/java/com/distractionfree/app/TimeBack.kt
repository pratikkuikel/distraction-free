package com.distractionfree.app

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-category "time back" counters — incremented directly the moment a
 * block happens, not computed from raw counts. Mirrors the macOS daemon's
 * TimeBack.swift.
 */
class TimeBack(private val context: Context) {
    companion object {
        // Rough, clearly-labeled per-category estimates — same spirit as
        // Brave/uBlock's own bandwidth/time-saved numbers: illustrative,
        // not measured.
        const val ADULT_MINUTES_PER_BLOCK = 60.0
        const val SOCIAL_MINUTES_PER_BLOCK = 60.0
        const val YOUTUBE_MINUTES_PER_BLOCK = 30.0
        const val OTHER_MINUTES_PER_BLOCK = 15.0 // gambling/piracy/bypass
    }

    // Stored as tenths of a minute in an AtomicLong so concurrent increments
    // from multiple relay threads never race each other or the periodic write.
    private val adultTenths = AtomicLong(0)
    private val socialTenths = AtomicLong(0)
    private val youtubeTenths = AtomicLong(0)
    private val otherTenths = AtomicLong(0)
    @Volatile private var lastWrite = 0L
    private val writeIntervalMs = 10_000L

    init { load() }

    fun recordAdultBlock() { adultTenths.addAndGet((ADULT_MINUTES_PER_BLOCK * 10).toLong()); writeIfDue() }
    fun recordSocialBlock() { socialTenths.addAndGet((SOCIAL_MINUTES_PER_BLOCK * 10).toLong()); writeIfDue() }
    fun recordYoutubeBlock() { youtubeTenths.addAndGet((YOUTUBE_MINUTES_PER_BLOCK * 10).toLong()); writeIfDue() }
    fun recordOtherBlock() { otherTenths.addAndGet((OTHER_MINUTES_PER_BLOCK * 10).toLong()); writeIfDue() }

    private fun load() {
        val file = AppPaths.timeBackFile(context)
        if (!file.exists()) return
        try {
            val obj = JSONObject(file.readText())
            adultTenths.set((obj.optDouble("adultMinutes", 0.0) * 10).toLong())
            socialTenths.set((obj.optDouble("socialMinutes", 0.0) * 10).toLong())
            youtubeTenths.set((obj.optDouble("youtubeMinutes", 0.0) * 10).toLong())
            otherTenths.set((obj.optDouble("otherMinutes", 0.0) * 10).toLong())
        } catch (e: Exception) { /* start from zero */ }
    }

    private fun writeIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastWrite < writeIntervalMs) return
        lastWrite = now
        val obj = JSONObject()
        obj.put("adultMinutes", adultTenths.get() / 10.0)
        obj.put("socialMinutes", socialTenths.get() / 10.0)
        obj.put("youtubeMinutes", youtubeTenths.get() / 10.0)
        obj.put("otherMinutes", otherTenths.get() / 10.0)
        try {
            AppPaths.timeBackFile(context).writeText(obj.toString())
        } catch (e: Exception) { /* best-effort */ }
    }
}
