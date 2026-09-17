package com.distractionfree.app

import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var socialToggleButton: Button
    private lateinit var disableButton: Button
    private lateinit var schedule: ScheduleManager

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpnService()
        else Toast.makeText(this, "VPN permission is required for protection to work.", Toast.LENGTH_LONG).show()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way; foreground service still runs, notification just may not show pre-grant */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        schedule = ScheduleManager(this)
        setContentView(buildUi())
        maybeRequestNotificationPermission()
        AlarmScheduler.scheduleNextTransitions(this)

        if (BlocklistUpdater.needsInitialPopulate(this)) {
            statusText.text = "Downloading blocklist..."
            Thread {
                val ok = BlocklistUpdater.update(this)
                runOnUiThread {
                    if (!ok) Toast.makeText(this, "Blocklist download failed — check connection and retry from the app.", Toast.LENGTH_LONG).show()
                    requestVpnPermissionAndStart()
                }
            }.start()
        } else {
            requestVpnPermissionAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi(): LinearLayout {
        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#1B1F3B"))
        }

        fun title(text: String, size: Float) = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = size
            setPadding(0, dp(8), 0, dp(8))
        }

        root.addView(title("Distraction Free", 26f))
        statusText = title("Starting...", 16f)
        root.addView(statusText)
        statsText = title("", 13f).apply { setTextColor(Color.parseColor("#AAAAAA")) }
        root.addView(statsText)

        socialToggleButton = Button(this).apply {
            text = "Block social media now"
            setOnClickListener { toggleSocialBlock() }
        }
        root.addView(socialToggleButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        disableButton = Button(this).apply {
            text = "Disable protection"
            setOnClickListener { requestDisable() }
        }
        root.addView(disableButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val updateButton = Button(this).apply {
            text = "Update blocklist"
            setOnClickListener { updateBlocklist() }
        }
        root.addView(updateButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val note = TextView(this).apply {
            text = "WhatsApp, Messenger, Telegram, and Botim always work — this only filters adult, gambling, piracy, VPN/proxy-bypass, and (on schedule) social feed content."
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(note)

        return root
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun requestVpnPermissionAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, BlockerVpnService::class.java).apply {
            action = BlockerVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        refresh()
    }

    private fun toggleSocialBlock() {
        val currentlyManuallyBlocked = schedule.readManualToggle()
        schedule.setManualToggle(!currentlyManuallyBlocked)
        refresh()
    }

    private fun requestDisable() {
        val intent = Intent(this, BlockerVpnService::class.java).apply {
            action = BlockerVpnService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Disable requested — takes effect in 24h.", Toast.LENGTH_LONG).show()
        refresh()
    }

    private fun updateBlocklist() {
        Toast.makeText(this, "Updating blocklist...", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = BlocklistUpdater.update(this)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "Blocklist updated. Restarting protection to apply it..." else "Update failed — check connection.",
                    Toast.LENGTH_LONG
                ).show()
                if (ok) {
                    stopService(Intent(this, BlockerVpnService::class.java))
                    startVpnService()
                }
            }
        }.start()
    }

    private fun refresh() {
        val running = BlockerVpnService.isRunning
        val inForcedWindow = schedule.isInForcedWindow()
        val socialBlocked = schedule.isSocialBlockedNow()

        statusText.text = buildString {
            append(if (running) "Protection: ON\n" else "Protection: starting...\n")
            append(if (inForcedWindow) "Social media: blocked (nightly window, 8pm-9am)\n"
                   else if (socialBlocked) "Social media: blocked (manual)\n"
                   else "Social media: open\n")
            val hoursLeft = DisableLog.hoursRemaining(this@MainActivity)
            if (hoursLeft != null) append("Disable requested — $hoursLeft h remaining (never actually completes)")
        }

        socialToggleButton.text = if (schedule.readManualToggle()) "Unblock social media" else "Block social media now"
        socialToggleButton.isEnabled = !inForcedWindow

        val statsFile = AppPaths.statsFile(this)
        if (statsFile.exists()) {
            try {
                val obj = JSONObject(statsFile.readText())
                statsText.text = "Queries: ${obj.optInt("totalQueries")} · Blocked: ${obj.optInt("blockedAlwaysOn") + obj.optInt("blockedSocial")} · SafeSearch: ${obj.optInt("safeSearchRewrites")}"
            } catch (e: Exception) { /* ignore */ }
        }
    }
}
