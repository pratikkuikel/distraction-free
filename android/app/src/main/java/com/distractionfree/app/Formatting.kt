package com.distractionfree.app

/**
 * Human-readable, scale-appropriate formatting for the stats card — picks
 * the coarsest unit that keeps the number readable, growing from minutes up
 * through years, and from MB up through PB, as totals grow over weeks/
 * months of use. Mirrors the macOS menubar app's Formatting.swift.
 */
object Formatting {
    fun duration(totalMinutes: Int): String {
        if (totalMinutes < 60) return "${totalMinutes}m"
        val hours = totalMinutes / 60
        if (hours < 24) {
            val mins = totalMinutes % 60
            return if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        }
        val days = hours / 24
        if (days < 30) {
            val remHours = hours % 24
            return if (remHours > 0) "${days}d ${remHours}h" else "${days}d"
        }
        val months = days / 30
        if (months < 12) {
            val remDays = days % 30
            return if (remDays > 0) "${months}mo ${remDays}d" else "${months}mo"
        }
        val years = days / 365
        val remMonths = (days % 365) / 30
        return if (remMonths > 0) "${years}y ${remMonths}mo" else "${years}y"
    }

    fun dataSize(totalMB: Double): String {
        val k = 1024.0
        if (totalMB < k) return String.format("%.1f MB", totalMB)
        val gb = totalMB / k
        if (gb < k) return String.format("%.2f GB", gb)
        val tb = gb / k
        if (tb < k) return String.format("%.2f TB", tb)
        val pb = tb / k
        return String.format("%.2f PB", pb)
    }
}
