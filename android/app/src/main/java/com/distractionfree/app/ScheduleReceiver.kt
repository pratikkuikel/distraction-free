package com.distractionfree.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Fires at the 20:00/09:00 social-media transitions purely to refresh the
 * status notification promptly. Enforcement itself doesn't depend on this —
 * BlockerVpnService checks wall-clock time on every DNS query regardless,
 * so a missed alarm (e.g. Doze) never opens a blocking gap, only delays the
 * notification text update.
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, BlockerVpnService::class.java).apply {
            action = BlockerVpnService.ACTION_REFRESH_NOTIFICATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        AlarmScheduler.scheduleNextTransitions(context)
    }
}

object AlarmScheduler {
    private const val REQUEST_CODE = 4201

    fun scheduleNextTransitions(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ScheduleReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = Calendar.getInstance()
        val next = nextTransition(now)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        }
    }

    private fun nextTransition(now: Calendar): Calendar {
        val startToday = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, ScheduleManager.BLOCK_START_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val endToday = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, ScheduleManager.BLOCK_END_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val candidates = listOf(startToday, endToday, (startToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) })
        return candidates.filter { it.after(now) }.minByOrNull { it.timeInMillis } ?: candidates.last()
    }
}
