package com.soroush.wirecat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.soroush.wirecat.R
import com.soroush.wirecat.ui.HostActivity
import com.soroush.wirecat.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MonitorSnapshot(
    val sessionStartMillis: Long,
    val elapsedMillis: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val perApp: List<LiveAppUsage>
)

class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var statsHelper: NetworkStatsHelper
    private var sessionStart = 0L
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        statsHelper = NetworkStatsHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (pollJob == null && !_isRunning.value) {
            startForeground(NOTIFICATION_ID, buildNotification(0L))
        }
        when (intent?.action) {
            // stopService() never reaches onStartCommand, so Stop must go through an action intent instead
            ACTION_STOP -> stopMonitoring()
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (pollJob != null) return
        sessionStart = System.currentTimeMillis()
        _isRunning.value = true
        startForeground(NOTIFICATION_ID, buildNotification(0L))

        val apps = try {
            statsHelper.installedApps()
        } catch (e: Exception) {
            emptyList()
        }
        val appsByUid = apps.associateBy { it.uid }
        var lastTotal = 0L
        var lastPollTime = sessionStart

        pollJob = scope.launch {
            while (true) {
                try {
                    val now = System.currentTimeMillis()
                    val usageByUid = statsHelper.queryUsageSince(sessionStart)
                    val perApp = usageByUid.entries
                        .mapNotNull { (uid, bytes) ->
                            val app = appsByUid[uid] ?: return@mapNotNull null
                            if (bytes <= 0L) return@mapNotNull null
                            LiveAppUsage(app.packageName, app.label, bytes)
                        }
                        .sortedByDescending { it.bytes }

                    val total = usageByUid.values.sum()

                    val elapsedSincePoll = (now - lastPollTime).coerceAtLeast(1L)
                    val bytesPerSecond = ((total - lastTotal).coerceAtLeast(0L) * 1000L) / elapsedSincePoll
                    lastTotal = total
                    lastPollTime = now

                    _snapshot.value = MonitorSnapshot(sessionStart, now - sessionStart, total, bytesPerSecond, perApp)
                    updateNotification(total)
                } catch (e: Exception) {
                    // one bad poll shouldn't kill the whole session (this is a long-running loop)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitoring() {
        pollJob?.cancel()
        pollJob = null
        _isRunning.value = false
        saveSessionToHistory()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveSessionToHistory() {
        val snap = _snapshot.value ?: return
        if (snap.perApp.isEmpty() && snap.totalBytes == 0L) {
            return // nothing happened this session, don't clutter history with an empty entry
        }
        val endTime = System.currentTimeMillis()
        val session = SessionEntity(
            type = SessionType.MONITOR,
            startTimeMillis = snap.sessionStartMillis,
            endTimeMillis = endTime,
            totalBytes = snap.totalBytes
        )
        val apps = snap.perApp

        val packets = PacketLogStore.logFlow.value.filter {
            it.timestampMillis in snap.sessionStartMillis..endTime
        }
        scope.launch(Dispatchers.IO) {
            WirecatDbHelper.get(applicationContext).saveSession(session, apps, packets)
        }

    }

    private fun buildNotification(totalBytes: Long): android.app.Notification {
        ensureChannel()
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, HostActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Monitoring \u2022 ${FormatUtils.formatBytes(totalBytes)}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop_monitoring), stopIntent)
            .build()
    }

    private fun updateNotification(totalBytes: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(totalBytes))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Monitoring", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.soroush.wirecat.action.STOP_MONITOR"
        private const val CHANNEL_ID = "wirecat_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 1000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _snapshot = MutableStateFlow<MonitorSnapshot?>(null)
        val snapshot = _snapshot.asStateFlow()
    }
}
