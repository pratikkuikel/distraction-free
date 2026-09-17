package com.distractionfree.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

/**
 * Restarts protection after reboot — but only if VPN permission was already
 * granted (Android requires the user to have approved it at least once
 * in-app; a boot receiver can't silently obtain that consent).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (VpnService.prepare(context) != null) return // permission not (still) granted

        val serviceIntent = Intent(context, BlockerVpnService::class.java).apply {
            action = BlockerVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
