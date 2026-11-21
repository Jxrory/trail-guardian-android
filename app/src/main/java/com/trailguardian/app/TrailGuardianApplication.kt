package com.trailguardian.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.trailguardian.app.di.AppContainer
import com.trailguardian.service.recording.RecordingEngineProvider

class TrailGuardianApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RecordingEngineProvider.instance = container.recordingEngine
        container.tileServer.start()
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> container.recordingEngine.onAppForeground()
                    Lifecycle.Event.ON_STOP -> container.recordingEngine.onAppBackground()
                    else -> Unit
                }
            },
        )
    }
}
