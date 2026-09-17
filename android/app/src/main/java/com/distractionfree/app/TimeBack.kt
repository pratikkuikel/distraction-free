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
        // not measured.
        const val ADULT_MINUTES_PER_BLOCK = 60.0
        const val SOCIAL_MINUTES_PER_BLOCK = 60.0
        const val YOUTUBE_MINUTES_PER_BLOCK = 30.0
        const val OTHER_MINUTES_PER_BLOCK = 15.0 // gambling/piracy/bypass

        // Same per-category logic for data: video-heavy categories cost far
        // more bandwidth per visit than a blocked gambling/piracy attempt.
        const val ADULT_MB_PER_BLOCK = 150.0
        const val SOCIAL_MB_PER_BLOCK = 80.0
        const val YOUTUBE_MB_PER_BLOCK = 200.0
        const val OTHER_MB_PER_BLOCK = 20.0
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
