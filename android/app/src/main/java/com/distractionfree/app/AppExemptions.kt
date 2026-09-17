package com.distractionfree.app

import android.content.pm.PackageManager
import android.net.VpnService
import android.util.Log

/**
 * Communication apps are never part of the social-media block. Domain-level
 * exemption in the blocklist already covers this; addDisallowedApplication
 * is an extra, belt-and-suspenders guarantee at the OS level in case one of
 * these apps ever resolves DNS in a way our filter wouldn't otherwise see.
 */
object AppExemptions {
    val communicationPackages = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",        // WhatsApp Business
        "com.facebook.orca",       // Messenger
        "org.telegram.messenger",  // Telegram
        "im.botim.app",            // Botim (package name to confirm against current Play listing)
    )

    // Not a communication app itself, but push notifications for WhatsApp,
    // Telegram, and most other apps are delivered via Google Play Services'
    // FCM channel — on-device testing showed notification delays because
    // Play Services' own connections were still subject to the (imperfect)
    // TCP relay even though the messaging apps themselves were exempted.
    val systemServicePackages = listOf(
        "com.google.android.gms",
    )

    private val allExemptPackages = communicationPackages + systemServicePackages

    fun applyTo(builder: VpnService.Builder, packageManager: PackageManager) {
        for (pkg in allExemptPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                builder.addDisallowedApplication(pkg)
                Log.i("df-vpn", "exempted from VPN: $pkg")
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed — nothing to exempt.
            }
        }
    }
}
