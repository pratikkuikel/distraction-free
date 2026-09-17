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
                }
            } finally {
                BlocklistUpdateScheduler.scheduleNext(context)
                pending.finish()
            }
        }
    }
}

object BlocklistUpdateScheduler {
    private const val REQUEST_CODE = 4202
    private const val UPDATE_HOUR = 4

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, BlocklistUpdateReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, UPDATE_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        }
    }
}
