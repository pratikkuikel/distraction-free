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
}
