package com.distractionfree.app

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-category "time back" and "data back" counters — both incremented
 * directly the moment a block happens, not computed from raw counts.
 * Mirrors the macOS daemon's TimeBack.swift.
 */
class TimeBack(private val context: Context) {
    companion object {
        // Rough, clearly-labeled per-category estimates — same spirit as
        // Brave/uBlock's own bandwidth/time-saved numbers: illustrative,
        // not measured. Deliberately conservative: one blocked DNS query is
        // one attempted connection, not a whole session, so this is priced
        // close to a single blocked request (~1 MB, ~10s) rather than an
        // assumed browsing session — the old numbers (up to 60 min / 150 MB
        // per block) compounded into implausible totals (weeks of "time
        // back" after a day of normal use). Minute values are clean
        // multiples of 0.1 to avoid truncation error in the tenths-based
        // storage below.
        const val ADULT_MINUTES_PER_BLOCK = 0.2   // 12s
        const val SOCIAL_MINUTES_PER_BLOCK = 0.2  // 12s
        const val YOUTUBE_MINUTES_PER_BLOCK = 0.3 // 18s — video preview/thumbnail fetch is a bit heavier
        const val OTHER_MINUTES_PER_BLOCK = 0.1   // 6s — gambling/piracy/bypass attempts are typically just a page load

        // Same conservative logic for data: video-heavy categories still
        // cost a bit more per attempt than a blocked gambling/piracy
        // request, but nowhere near a full session's worth.
        const val ADULT_MB_PER_BLOCK = 1.0
        const val SOCIAL_MB_PER_BLOCK = 1.0
        const val YOUTUBE_MB_PER_BLOCK = 2.0
        const val OTHER_MB_PER_BLOCK = 0.5
    }

    // Stored as tenths in AtomicLongs so concurrent increments from multiple
    // relay threads never race each other or the periodic write.
    private val adultMinTenths = AtomicLong(0)
    private val adultMbTenths = AtomicLong(0)
    private val socialMinTenths = AtomicLong(0)
    private val socialMbTenths = AtomicLong(0)
    private val youtubeMinTenths = AtomicLong(0)
    private val youtubeMbTenths = AtomicLong(0)
    private val otherMinTenths = AtomicLong(0)
    private val otherMbTenths = AtomicLong(0)
    @Volatile private var lastWrite = 0L
    private val writeIntervalMs = 10_000L

    init { load() }

    fun recordAdultBlock() {
        adultMinTenths.addAndGet((ADULT_MINUTES_PER_BLOCK * 10).toLong())
        adultMbTenths.addAndGet((ADULT_MB_PER_BLOCK * 10).toLong())
        writeIfDue()
    }
    fun recordSocialBlock() {
        socialMinTenths.addAndGet((SOCIAL_MINUTES_PER_BLOCK * 10).toLong())
        socialMbTenths.addAndGet((SOCIAL_MB_PER_BLOCK * 10).toLong())
        writeIfDue()
    }
    fun recordYoutubeBlock() {
        youtubeMinTenths.addAndGet((YOUTUBE_MINUTES_PER_BLOCK * 10).toLong())
        youtubeMbTenths.addAndGet((YOUTUBE_MB_PER_BLOCK * 10).toLong())
        writeIfDue()
    }
    fun recordOtherBlock() {
        otherMinTenths.addAndGet((OTHER_MINUTES_PER_BLOCK * 10).toLong())
        otherMbTenths.addAndGet((OTHER_MB_PER_BLOCK * 10).toLong())
        writeIfDue()
    }

    private fun load() {
        val file = AppPaths.timeBackFile(context)
        if (!file.exists()) return
        try {
            val obj = JSONObject(file.readText())
            adultMinTenths.set((obj.optDouble("adultMinutes", 0.0) * 10).toLong())
            adultMbTenths.set((obj.optDouble("adultMB", 0.0) * 10).toLong())
            socialMinTenths.set((obj.optDouble("socialMinutes", 0.0) * 10).toLong())
            socialMbTenths.set((obj.optDouble("socialMB", 0.0) * 10).toLong())
            youtubeMinTenths.set((obj.optDouble("youtubeMinutes", 0.0) * 10).toLong())
            youtubeMbTenths.set((obj.optDouble("youtubeMB", 0.0) * 10).toLong())
            otherMinTenths.set((obj.optDouble("otherMinutes", 0.0) * 10).toLong())
            otherMbTenths.set((obj.optDouble("otherMB", 0.0) * 10).toLong())
        } catch (e: Exception) { /* start from zero */ }
    }

    private fun writeIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastWrite < writeIntervalMs) return
        lastWrite = now
        val obj = JSONObject()
        obj.put("adultMinutes", adultMinTenths.get() / 10.0)
        obj.put("adultMB", adultMbTenths.get() / 10.0)
        obj.put("socialMinutes", socialMinTenths.get() / 10.0)
        obj.put("socialMB", socialMbTenths.get() / 10.0)
        obj.put("youtubeMinutes", youtubeMinTenths.get() / 10.0)
        obj.put("youtubeMB", youtubeMbTenths.get() / 10.0)
        obj.put("otherMinutes", otherMinTenths.get() / 10.0)
        obj.put("otherMB", otherMbTenths.get() / 10.0)
        try {
            AppPaths.timeBackFile(context).writeText(obj.toString())
        } catch (e: Exception) { /* best-effort */ }
    }
}
