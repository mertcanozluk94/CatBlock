package com.catblock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.catblock.app.MainActivity
import com.catblock.app.R
import com.catblock.app.data.AppRule
import com.catblock.app.data.SettingsRepository
import com.catblock.app.overlay.CatOverlayManager
import com.catblock.app.permissions.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageMonitorService : Service() {

    private lateinit var repo: SettingsRepository
    private lateinit var overlay: CatOverlayManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loop: Job? = null

    /** Tracks how long (ms) each rule-protected package has been continuously foreground. */
    private val continuousForegroundMs = mutableMapOf<String, Long>()
    private var lastForeground: String? = null
    private var lastTickMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        overlay = CatOverlayManager(applicationContext)
        // When the user taps the Skip button on the overlay, end the break.
        overlay.onSkip = {
            scope.launch {
                repo.clearBreak()
                continuousForegroundMs.clear()
            }
        }
        startForegroundCompat()
        startMonitorLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STICKY so Android tries to bring it back if killed.
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        overlay.destroy()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                getString(R.string.notif_channel_id),
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }

        val tap = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tap)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startMonitorLoop() {
        loop = scope.launch {
            lastTickMs = System.currentTimeMillis()
            while (isActive) {
                tick()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastTickMs).coerceAtLeast(0)
        lastTickMs = now

        if (!PermissionUtils.hasUsageStatsPermission(this) ||
            !PermissionUtils.hasOverlayPermission(this)) {
            return
        }

        val foreground = getForegroundPackage(now)
        val voiceOn = repo.isVoiceEnabled()

        // 1. If a break is active, enforce the overlay until it expires.
        val breakUntil = repo.getBreakUntil()
        val breakPkg = repo.getActiveBreakPackage()
        if (breakUntil > now && breakPkg != null) {
            val rules = repo.getRules()
            val ruleForBreakPkg = rules.firstOrNull { it.packageName == breakPkg }
            if (foreground == breakPkg) {
                runOnMain {
                    if (overlay.isShowing) overlay.updateCountdown(breakUntil - now)
                    else overlay.show(breakUntil - now, voiceOn)
                }
            } else {
                // User left the offending app — hide overlay but keep break timer running.
                runOnMain { overlay.hide() }
            }
            // Reset accumulator so they don't re-trigger immediately after break ends.
            ruleForBreakPkg?.let { continuousForegroundMs[it.packageName] = 0L }
            return
        } else if (breakUntil > 0L && breakUntil <= now) {
            repo.clearBreak()
            continuousForegroundMs.clear()
            runOnMain { overlay.hide() }
        } else {
            // No break active. Make sure overlay is gone.
            if (overlay.isShowing) runOnMain { overlay.hide() }
        }

        // 2. Accumulate continuous usage time and check thresholds.
        val rules = repo.getRules().associateBy { it.packageName }
        if (foreground != null) {
            if (foreground != lastForeground) {
                // App switched — reset accumulator for new app, keep others as-is.
                continuousForegroundMs[foreground] = 0L
            }
            val rule: AppRule? = rules[foreground]
            if (rule != null) {
                val acc = (continuousForegroundMs[foreground] ?: 0L) + elapsed
                continuousForegroundMs[foreground] = acc
                val triggerMs = rule.triggerMinutes * 60_000L
                if (acc >= triggerMs) {
                    val breakMs = rule.breakMinutes * 60_000L
                    repo.startBreak(rule.packageName, breakMs)
                    runOnMain { overlay.show(breakMs, voiceOn) }
                }
            }
        }
        lastForeground = foreground
    }

    /** Most recent foreground package within the last few seconds. */
    private fun getForegroundPackage(now: Long): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val begin = now - 10_000L
        val events = usm.queryEvents(begin, now)
        val ev = UsageEvents.Event()
        var lastPkg: String? = null
        var lastTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (ev.timeStamp > lastTime) {
                    lastTime = ev.timeStamp
                    lastPkg = ev.packageName
                }
            }
        }
        return lastPkg ?: lastForeground
    }

    private suspend fun runOnMain(block: () -> Unit) {
        withContext(Dispatchers.Main) { block() }
    }

    companion object {
        private const val NOTIF_ID = 4711
        private const val POLL_INTERVAL_MS = 2_000L

        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
