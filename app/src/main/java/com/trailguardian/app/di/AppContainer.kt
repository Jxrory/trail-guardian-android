package com.trailguardian.app.di

import android.content.Context
import com.trailguardian.core.data.db.TrailGuardianDatabase
import com.trailguardian.core.data.repository.AppSettingsRepository
import com.trailguardian.core.data.repository.OfflineMapRepository
import com.trailguardian.core.data.repository.SessionRepository
import com.trailguardian.core.data.repository.WorkspaceRepository
import com.trailguardian.core.data.tile.ContourRenderer
import com.trailguardian.core.data.tile.LocalTileServer
import com.trailguardian.core.data.tile.TileCache
import com.trailguardian.service.recording.RecordingEngine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val database: TrailGuardianDatabase = TrailGuardianDatabase.getInstance(context)
    val settingsRepository = AppSettingsRepository(context)
    val tileCache = TileCache(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()
    val tileServer = LocalTileServer(tileCache, httpClient, ContourRenderer())
    val workspaceRepository = WorkspaceRepository(database)
    val sessionRepository = SessionRepository(database)
    val offlineMapRepository = OfflineMapRepository(context, database, tileCache)
    val recordingEngine = RecordingEngine(
        context,
        sessionRepository,
        settingsRepository,
        workspaceRepository,
    )
}
