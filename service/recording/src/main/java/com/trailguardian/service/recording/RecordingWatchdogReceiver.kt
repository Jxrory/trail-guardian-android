package com.trailguardian.service.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class RecordingWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecordingWatchdog.ACTION_WATCHDOG) return
        val pending = goAsync()
        if (!RecordingWatchdog.isActive(context)) {
            RecordingWatchdog.cancel(context)
            pending.finish()
            return
        }
        var alerted = false
        if (RecordingWatchdog.isStale(context)) {
            val restarted = runCatching { RecordingWatchdog.restartService(context) }.isSuccess
            alerted = RecordingWatchdog.alert(context, restarted)
            if (alerted) {
                RecordingWatchdog.speak(
                    context,
                    if (restarted) {
                        "轨迹记录服务已中断，已尝试自动恢复，请打开应用确认记录仍在进行"
                    } else {
                        "轨迹记录服务已中断，请打开应用重新开始记录"
                    },
                )
            }
        }
        RecordingWatchdog.schedule(context)
        if (alerted) {
            Handler(Looper.getMainLooper()).postDelayed(
                { pending.finish() },
                4_000L,
            )
        } else {
            pending.finish()
        }
    }
}
