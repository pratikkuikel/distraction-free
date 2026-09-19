package com.distractionfree.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * Restarts protection after reboot or after the app itself is updated (an
 * update kills the running service and nothing else would bring it back) —
 * but only if VPN permission was already granted (Android requires the user
 * to have approved it at least once in-app; a receiver can't silently obtain
 * that consent).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {}
            else -> return
        }
        if (VpnService.prepare(context) != null) return // permission not (still) granted

        ServiceWatchdog.startService(context)
    }
}
