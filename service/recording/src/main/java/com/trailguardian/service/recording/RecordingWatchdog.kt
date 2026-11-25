package com.trailguardian.service.recording

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

object RecordingWatchdog {
    const val ACTION_WATCHDOG = "com.trailguardian.service.recording.WATCHDOG"

    private const val PREFS = "trail_recording_watchdog"
    private const val KEY_ACTIVE = "active"
    private const val KEY_HEARTBEAT = "heartbeat"
    private const val KEY_LAST_ALERT = "last_alert"
    private const val KEY_ENABLED = "enabled"
    private const val ALERT_CHANNEL_ID = "trail_recording_alert"
    private const val ALERT_NOTIFICATION_ID = 1201
    private const val ALARM_INTERVAL_MS = 5 * 60 * 1000L
    private const val STALE_MS = 3 * 60 * 1000L
    private const val ALERT_THROTTLE_MS = 10 * 60 * 1000L

    fun setActive(context: Context, active: Boolean) {
        val prefs = prefs(context)
        prefs.edit().putBoolean(KEY_ACTIVE, active).apply()
        if (active) {
            heartbeat(context)
            clearAlert(context)
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            clearAlert(context)
        } else if (isActive(context)) {
            schedule(context)
        }
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun heartbeat(context: Context) {
        if (!isActive(context)) return
        prefs(context).edit()
            .putLong(KEY_HEARTBEAT, SystemClock.elapsedRealtime())
            .apply()
    }

    fun lastHeartbeat(context: Context): Long = prefs(context).getLong(KEY_HEARTBEAT, 0L)

    fun isStale(context: Context): Boolean {
        if (!isActive(context)) return false
        val last = lastHeartbeat(context)
        return last <= 0 || SystemClock.elapsedRealtime() - last > STALE_MS
    }

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        val triggerAt = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !manager.canScheduleExactAlarms()
            ) {
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending,
                )
            } else {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending,
                )
            }
        }.onFailure {
            runCatching {
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending,
                )
            }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context))
    }

    fun restartService(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, RecordingService::class.java),
        )
    }

    fun alert(context: Context, restarted: Boolean): Boolean {
        if (!isEnabled(context)) return false
        val prefs = prefs(context)
        val now = SystemClock.elapsedRealtime()
        if (now - prefs.getLong(KEY_LAST_ALERT, 0L) < ALERT_THROTTLE_MS) return false
        prefs.edit().putLong(KEY_LAST_ALERT, now).apply()
        ensureChannel(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                1,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val text = if (restarted) {
            "记录服务曾退出，已尝试自动恢复。请打开应用确认记录仍在进行。"
        } else {
            "记录服务已退出。请打开应用检查并重新开始记录。"
        }
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("轨迹记录已中断")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .apply { contentIntent?.let(::setContentIntent) }
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ALERT_NOTIFICATION_ID, notification)
        }
        vibrate(context)
        return true
    }

    fun clearAlert(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(ALERT_NOTIFICATION_ID)
        }
    }

    fun speak(context: Context, text: String) {
        val handler = Handler(Looper.getMainLooper())
        var tts: TextToSpeech? = null
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val chinese = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (chinese == TextToSpeech.LANG_MISSING_DATA ||
                        chinese == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    tts?.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "trail-guardian-watchdog",
                    )
                }
                handler.postDelayed({
                    runCatching { tts?.shutdown() }
                }, 5_000L)
            }
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "轨迹记录中断提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "记录服务退出时震动并语音提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 400, 700, 400, 700)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 700, 400, 700, 400, 700),
                    -1,
                ),
            )
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecordingWatchdogReceiver::class.java)
            .setAction(ACTION_WATCHDOG)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
