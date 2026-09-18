package com.distractionfree.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import kotlin.concurrent.thread

/** Fires once daily (4am) to keep the local blocklist copy from going stale. */
class BlocklistUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                val ok = BlocklistUpdater.update(context)
                Log.i("df-vpn", "daily blocklist auto-update: ${if (ok) "ok" else "failed"}")
                if (ok) {
                    BlocklistUpdateScheduler.markUpdateSucceeded(context)
                    // Restart the VPN so the reloaded lists take effect.
                    val stopIntent = Intent(context, BlockerVpnService::class.java)
                    context.stopService(stopIntent)
                    val startIntent = Intent(context, BlockerVpnService::class.java).apply {
                        action = BlockerVpnService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else {
                        context.startService(startIntent)
                    }
                    BlocklistUpdateScheduler.scheduleNext(context)
                } else {
                    // Failsafe: the daily slot was missed (no network, phone
                    // asleep, etc.) — instead of silently waiting a full day,
                    // retry sooner. BlockerVpnService's NetworkCallback also
                    // triggers an immediate retry the moment connectivity
                    // returns, so this interval is just the backstop.
                    BlocklistUpdateScheduler.scheduleRetrySoon(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

object BlocklistUpdateScheduler {
    private const val REQUEST_CODE = 4202
    private const val UPDATE_HOUR = 4
    private const val RETRY_INTERVAL_MS = 30 * 60 * 1000L // 30 min — cheap no-op if still offline
    private const val PREFS = "df-blocklist-update"
    private const val KEY_RETRY_PENDING = "retry_pending"

    /**
     * Failsafe for a missed 4am slot (no network, phone asleep, etc.): the
     * daily alarm alone would just wait a full day with no retry. This flag
     * lets BlockerVpnService's NetworkCallback fire an immediate retry the
     * moment connectivity returns, instead of waiting on the 30min backstop.
     */
    fun isRetryPending(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RETRY_PENDING, false)

    fun markUpdateSucceeded(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RETRY_PENDING, false).apply()
    }

    fun scheduleNext(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RETRY_PENDING, false).apply()
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, UPDATE_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        schedule(context, next.timeInMillis)
    }

    fun scheduleRetrySoon(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RETRY_PENDING, true).apply()
        schedule(context, System.currentTimeMillis() + RETRY_INTERVAL_MS)
    }

    private fun schedule(context: Context, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, BlocklistUpdateReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }
}
