package com.distractionfree.app

import android.content.pm.PackageManager
import android.net.VpnService

/**
 * Communication apps are never part of the social-media block. Domain-level
 * exemption in the blocklist already covers this; addDisallowedApplication
 * is an extra, belt-and-suspenders guarantee at the OS level in case one of
 * these apps ever resolves DNS in a way our filter wouldn't otherwise see.
 */
object AppExemptions {
    val communicationPackages = listOf(
        "com.whatsapp",
        "com.facebook.orca",       // Messenger
        "org.telegram.messenger",  // Telegram
        "im.botim.app",            // Botim (package name to confirm against current Play listing)
    )

    fun applyTo(builder: VpnService.Builder, packageManager: PackageManager) {
        for (pkg in communicationPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                builder.addDisallowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed — nothing to exempt.
            }
        }
    }
}
