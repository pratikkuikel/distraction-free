package com.distractionfree.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Self-healing for the VPN service. START_STICKY alone isn't enough: on a
 * real process kill (OEM "close app" cleaners, low-memory kills, crashes)
 * the OS often never restarts the service — verified on a stock Android 15
 * emulator, where a killed process stayed dead indefinitely and protection
 * silently stayed off until the user reopened the app. AlarmManager state
 * lives in system_server, so a pending alarm survives the app's process
 * dying; this alarm chain checks in and starts the VPN back up if it isn't
 * running.
 *
 * Exact alarms (USE_EXACT_ALARM, already declared) also exempt the resulting
 * foreground-service start from Android 12+'s background-start restrictions,
 * which a plain inexact alarm would not.
 *
 * Cannot survive a real "Force stop" (Settings, or an OEM cleaner that uses
 * it) — Android cancels every alarm and blocks all broadcasts to a
 * force-stopped app until the user opens it again. No app can get around that.
 */
object ServiceWatchdog {
    private const val REQUEST_CODE = 4202
    private const val HEARTBEAT_MS = 5 * 60 * 1000L
    const val QUICK_CHECK_MS = 2_000L

    fun scheduleHeartbeat(context: Context, delayMs: Long = HEARTBEAT_MS) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        }
    }

    fun startService(context: Context) {
        val intent = Intent(context, BlockerVpnService::class.java).apply {
            action = BlockerVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Re-arm first: nothing below may be allowed to break the chain.
        ServiceWatchdog.scheduleHeartbeat(context)

        if (BlockerVpnService.isRunning) return
        // Only the user can grant VPN consent, in-app. Without it there's
        // nothing to restart — and no point retrying it every few minutes.
        if (VpnService.prepare(context) != null) return

        Log.w("df-watchdog", "VPN service wasn't running — restarting it")
        try {
            ServiceWatchdog.startService(context)
        } catch (e: Exception) {
            Log.e("df-watchdog", "restart failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
