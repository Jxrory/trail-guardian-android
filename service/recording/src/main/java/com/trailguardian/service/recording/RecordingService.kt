package com.trailguardian.service.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.GpsState
import com.trailguardian.core.model.GpsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: RecordingEngine
    private var locationSource: LocationSource? = null
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val engine = RecordingEngineProvider.instance
        if (engine == null) {
            stopSelf()
            return
        }
        this.engine = engine
        engine.setServiceActive(true)
        createChannel()
        startForegroundNotification(engine.state.value)
        observeJob = scope.launch {
            if (!engine.state.value.isRecording) {
                val session = engine.loadActiveSession()
                if (session == null) {
                    stopSelf()
                    return@launch
                }
                engine.restore(session, startService = false)
            }
            startLocation()
            engine.state.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::engine.isInitialized) return START_NOT_STICKY
        if (!engine.state.value.isRecording) {
            val restored = engine
            scope.launch {
                val session = restored.loadActiveSession()
                if (session == null) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        locationSource?.stop()
        locationSource = null
        if (::engine.isInitialized) {
            engine.setServiceActive(false)
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocation() {
        if (locationSource != null) return
        locationSource = LocationSource(
            context = this,
            onLocation = { location -> engine.handleLocation(location) },
            onGpsState = { gpsState -> engine.updateGpsState(gpsState) },
        ).also { it.start() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "轨迹记录",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "锁屏与后台持续记录轨迹"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(state: RecordingState) {
        val notification = buildNotification(state)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )
    }

    private fun updateNotification(state: RecordingState) {
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(state)) }
        if (!state.isRecording) {
            stopSelf()
        }
    }

    private fun buildNotification(state: RecordingState): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val summary = if (state.isPaused) {
            "已暂停"
        } else {
            "${String.format(java.util.Locale.US, "%.2f", state.distanceMeters / 1000.0)} km · " +
                GeoUtils.formatDuration(state.elapsedSeconds)
        }
        val text = when (state.gpsState.status) {
            GpsStatus.DENIED -> "等待定位权限 · $summary"
            GpsStatus.ERROR -> "定位异常 · $summary"
            GpsStatus.UNSUPPORTED -> "设备不支持定位 · $summary"
            else -> summary
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (state.isPaused) "轨迹记录已暂停" else "正在记录轨迹")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "trail_recording"
        const val NOTIFICATION_ID = 1101
    }
}
