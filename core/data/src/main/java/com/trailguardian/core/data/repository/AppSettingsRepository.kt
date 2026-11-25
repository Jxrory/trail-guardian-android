package com.trailguardian.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trailguardian.core.model.MapSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.trailDataStore: DataStore<Preferences> by preferencesDataStore(name = "trail_settings")

class AppSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.trailDataStore

    private object Keys {
        val mapSource = stringPreferencesKey("trail_map_source")
        val contourEnabled = booleanPreferencesKey("trail_contour_enabled")
        val contourOpacity = floatPreferencesKey("trail_contour_opacity")
        val lowPower = booleanPreferencesKey("trail_low_power")
        val watchdogAlert = booleanPreferencesKey("trail_watchdog_alert")
        val routeDeviationAlert = booleanPreferencesKey("trail_route_deviation_alert")
        val activeWorkspaceId = stringPreferencesKey("trail_active_workspace_id")
    }

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val mapSourceId: Flow<String> = preferences.map { prefs ->
        MapSources.get(prefs[Keys.mapSource]).id
    }

    val contourEnabled: Flow<Boolean> = preferences.map { it[Keys.contourEnabled] ?: true }

    val contourOpacity: Flow<Float> = preferences.map { prefs ->
        (prefs[Keys.contourOpacity] ?: 0.72f).coerceIn(0.1f, 1.0f)
    }

    val lowPower: Flow<Boolean> = preferences.map { it[Keys.lowPower] ?: true }

    val watchdogAlertEnabled: Flow<Boolean> = preferences.map {
        it[Keys.watchdogAlert] ?: true
    }

    val routeDeviationAlertEnabled: Flow<Boolean> = preferences.map {
        it[Keys.routeDeviationAlert] ?: true
    }

    val activeWorkspaceId: Flow<String?> = preferences.map { it[Keys.activeWorkspaceId] }

    suspend fun setMapSource(id: String) {
        val sanitized = if (id == MapSources.ESRI_SATELLITE.id) id else MapSources.ESRI_SATELLITE.id
        dataStore.edit { it[Keys.mapSource] = sanitized }
    }

    suspend fun setContourEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.contourEnabled] = enabled }
    }

    suspend fun setContourOpacity(opacity: Float) {
        dataStore.edit { it[Keys.contourOpacity] = opacity.coerceIn(0.1f, 1.0f) }
    }

    suspend fun setLowPower(enabled: Boolean) {
        dataStore.edit { it[Keys.lowPower] = enabled }
    }

    suspend fun setWatchdogAlertEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.watchdogAlert] = enabled }
    }

    suspend fun setRouteDeviationAlertEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.routeDeviationAlert] = enabled }
    }

    suspend fun setActiveWorkspaceId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.activeWorkspaceId) else prefs[Keys.activeWorkspaceId] = id
        }
    }
}
