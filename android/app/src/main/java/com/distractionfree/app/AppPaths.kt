package com.distractionfree.app

import android.content.Context
import java.io.File

/** Where blocklists and runtime state live — all local, all on-device. */
object AppPaths {
    fun alwaysOnList(context: Context) = File(context.filesDir, "always-on.txt")
    fun socialList(context: Context) = File(context.filesDir, "social.txt")
    fun stateDir(context: Context) = File(context.filesDir, "state").apply { mkdirs() }
    fun socialToggleFile(context: Context) = File(stateDir(context), "social-toggle.json")
    fun disableLogFile(context: Context) = File(stateDir(context), "disable-log.json")
    fun statsFile(context: Context) = File(stateDir(context), "stats.json")
    fun streakFile(context: Context) = File(stateDir(context), "streak.json")
    fun timeBackFile(context: Context) = File(stateDir(context), "timeback.json")
    /// Adult-only subset — lets us tell "adult" blocks apart from
    /// gambling/piracy/bypass for the time-back estimate. The actual block
    /// decision still uses the single merged always-on.txt trie.
    fun adultList(context: Context) = File(context.filesDir, "adult.txt")
    /// Community-maintained YouTube CDN IPv4 ranges — backstop for when the
    /// app resolves googlevideo.com without asking our DNS server at all.
    fun youtubeCidrList(context: Context) = File(context.filesDir, "youtube-cidr.txt")
    /// Community-maintained Meta (Facebook/Instagram) CDN IPv4 ranges —
    /// backstop for when the app resolves its CDN without asking our DNS
    /// server at all. See MetaCdnIpBlocklist.
    fun metaCidrList(context: Context) = File(context.filesDir, "meta-cidr.txt")
    /// Opt-in diagnostic logging — see DiagnosticLog.kt.
    fun loggingEnabledFile(context: Context) = File(stateDir(context), "logging-enabled")
    fun diagnosticLogFile(context: Context) = File(stateDir(context), "diagnostic.log")
}
