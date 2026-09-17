package com.distractionfree.app

import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var streakText: TextView
    private lateinit var statusText: TextView
    private lateinit var statsCard: LinearLayout
    private lateinit var statBlocked: TextView
    private lateinit var statSaved: TextView
    private lateinit var statTime: TextView
    private lateinit var socialToggleButton: Button
    private lateinit var disableButton: Button
    private lateinit var quoteText: TextView
    private lateinit var schedule: ScheduleManager

    private val quotes: List<String> by lazy {
        try {
            assets.open("quotes.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }

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
        showTodayQuote()
        maybeRequestNotificationPermission()
        AlarmScheduler.scheduleNextTransitions(this)
        BlocklistUpdateScheduler.scheduleNext(this)

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

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(title("Distraction Free", 26f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        streakText = TextView(this).apply {
            setTextColor(Color.parseColor("#FF9500"))
            textSize = 20f
            setPadding(0, dp(8), 0, dp(8))
        }
        header.addView(streakText)
        root.addView(header)

        statusText = title("Starting...", 16f)
        root.addView(statusText)

        statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#2A2F52"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fun statColumn(): TextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        statBlocked = statColumn()
        statSaved = statColumn()
        statTime = statColumn()
        for (v in listOf(statBlocked, statSaved, statTime)) {
            statsCard.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(statsCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        quoteText = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(quoteText)

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
            text = "WhatsApp, Messenger, Telegram, and Botim always work — this only filters adult, gambling, piracy, VPN/proxy-bypass, and (on schedule) social feed content. Blocklist auto-updates daily at 4am."
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(note)

        return root
    }

    private fun showTodayQuote() {
        if (quotes.isEmpty()) return
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        quoteText.text = "“${quotes[dayOfYear % quotes.size]}”"
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
        StreakManager.resetCurrentStreak(this) // trying to disable resets your streak
        Toast.makeText(this, "Disable requested — takes effect in 24h. Streak reset.", Toast.LENGTH_LONG).show()
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
        // Only shown/usable when it can actually do something — during the
        // forced window social is already blocked regardless, so a control
        // that visibly does nothing is just confusing.
        socialToggleButton.visibility = if (inForcedWindow) android.view.View.GONE else android.view.View.VISIBLE

        val streakFile = AppPaths.streakFile(this)
        var currentStreak = 0
        var bestStreak = 0
        if (streakFile.exists()) {
            try {
                val obj = JSONObject(streakFile.readText())
                currentStreak = obj.optInt("currentStreak", 0)
                bestStreak = obj.optInt("bestStreak", 0)
            } catch (e: Exception) { /* ignore */ }
        }
        streakText.text = if (currentStreak > 0) "🔥 $currentStreak" else ""

        val statsFile = AppPaths.statsFile(this)
        var blocked = 0
        if (statsFile.exists()) {
            try {
                val obj = JSONObject(statsFile.readText())
                blocked = obj.optInt("blockedAlwaysOn") + obj.optInt("blockedSocial")
            } catch (e: Exception) { /* ignore */ }
        }
        val timeBackFile = AppPaths.timeBackFile(this)
        var totalMinutes = 0
        var totalMB = 0.0
        if (timeBackFile.exists()) {
            try {
                val obj = JSONObject(timeBackFile.readText())
                totalMinutes = (obj.optDouble("adultMinutes", 0.0) + obj.optDouble("socialMinutes", 0.0) +
                    obj.optDouble("youtubeMinutes", 0.0) + obj.optDouble("otherMinutes", 0.0)).toInt()
                totalMB = obj.optDouble("adultMB", 0.0) + obj.optDouble("socialMB", 0.0) +
                    obj.optDouble("youtubeMB", 0.0) + obj.optDouble("otherMB", 0.0)
            } catch (e: Exception) { /* ignore */ }
        }

        statBlocked.text = "$blocked\nBlocked"
        statSaved.text = "${Formatting.dataSize(totalMB)}\nEst. data saved"
        statTime.text = "${Formatting.duration(totalMinutes)}\nEst. time back"

        val bestLine = if (bestStreak > 0) " · Best: $bestStreak" else ""
        statusText.append("$bestLine")
    }
}
