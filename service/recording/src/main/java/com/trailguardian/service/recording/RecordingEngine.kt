package com.trailguardian.service.recording

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import com.trailguardian.core.data.repository.AppSettingsRepository
import com.trailguardian.core.data.repository.SessionRepository
import com.trailguardian.core.data.repository.WorkspaceRepository
import com.trailguardian.core.model.ActiveSession
import com.trailguardian.core.model.CompletedRecording
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.GpsState
import com.trailguardian.core.model.GpsStatus
import com.trailguardian.core.model.LivePosition
import com.trailguardian.core.model.MotionState
import com.trailguardian.core.geo.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class RecordingState(
    val workspaceId: String? = null,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val points: List<GeoPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val elevationGain: Double = 0.0,
    val currentElevation: Int? = null,
    val currentPosition: LivePosition? = null,
    val gpsState: GpsState = GpsState.waiting,
    val motionState: MotionState = MotionState.STANDBY,
    val startedAt: Long? = null,
    val elapsedSeconds: Long = 0,
)

class RecordingEngine(
    private val appContext: Context,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: AppSettingsRepository,
    private val workspaceRepository: WorkspaceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()
    private val lock = Any()
    private var tickerJob: Job? = null

    @Volatile
    private var lowPower = true

    @Volatile
    private var routeDeviationAlertEnabled = true

    @Volatile
    private var targetRoute: List<GeoPoint> = emptyList()

    @Volatile
    private var serviceActive = false

    private var deviationSamples = 0
    private var routeDeviationActive = false
    private var lastDeviationAlertAt = 0L
    private var lastRouteCheckAt = 0L

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    init {
        scope.launch {
            settingsRepository.lowPower.collect { enabled -> lowPower = enabled }
        }
        scope.launch {
            settingsRepository.watchdogAlertEnabled.collect { enabled ->
                RecordingWatchdog.setEnabled(appContext, enabled)
            }
        }
        scope.launch {
            settingsRepository.routeDeviationAlertEnabled.collect { enabled ->
                routeDeviationAlertEnabled = enabled
                if (!enabled) synchronized(lock) { resetRouteDeviationLocked() }
            }
        }
    }

    fun setServiceActive(active: Boolean) {
        serviceActive = active
        _serviceRunning.value = active
    }

    suspend fun start(workspaceId: String) {
        if (_state.value.isRecording) return
        val now = System.currentTimeMillis()
        val initialState = RecordingState(
            workspaceId = workspaceId,
            isRecording = true,
            isPaused = false,
            points = emptyList(),
            distanceMeters = 0.0,
            elevationGain = 0.0,
            currentElevation = _state.value.currentElevation,
            currentPosition = _state.value.currentPosition,
            gpsState = _state.value.gpsState,
            motionState = MotionState.MOVING,
            startedAt = now,
            elapsedSeconds = 0,
        )
        synchronized(lock) { resetRouteDeviationLocked() }
        _state.value = initialState
        val session = buildSession(initialState, now)
        persistenceMutex.withLock {
            sessionRepository.clear()
            sessionRepository.save(session)
        }
        loadTargetRoute(workspaceId)
        startTicker()
        startService()
    }

    fun pause() {
        synchronized(lock) {
            val current = _state.value
            if (!current.isRecording || current.isPaused) return
            val now = System.currentTimeMillis()
            pauseStartedAt = now
            val next = current.copy(
                isPaused = true,
                motionState = MotionState.STANDBY,
            )
            resetRouteDeviationLocked()
            _state.value = next
            persist(next, sessionStart = next.startedAt ?: now, now = now)
        }
    }

    fun resume() {
        synchronized(lock) {
            val current = _state.value
            if (!current.isRecording || !current.isPaused) return
            val now = System.currentTimeMillis()
            val pauseStarted = pauseStartedAt ?: now
            accumulatedPausedMs += (now - pauseStarted).coerceAtLeast(0)
            pauseStartedAt = null
            val next = current.copy(
                isPaused = false,
                motionState = MotionState.MOVING,
            )
            resetRouteDeviationLocked()
            _state.value = next
            persist(next, sessionStart = next.startedAt ?: now, now = now)
        }
    }

    suspend fun finish(): CompletedRecording? {
        val completed = synchronized(lock) {
            val current = _state.value
            if (!current.isRecording || current.points.isEmpty()) return null
            val now = System.currentTimeMillis()
            val start = current.startedAt ?: now
            val pausedFor = if (current.isPaused && pauseStartedAt != null) {
                (now - pauseStartedAt!!).coerceAtLeast(0)
            } else {
                0L
            }
            val durationMs = (now - start - accumulatedPausedMs - pausedFor).coerceAtLeast(0)
            val duration = (durationMs / 1000.0).roundToLong()
            val completed = CompletedRecording(
                points = current.points,
                distanceMeters = current.distanceMeters,
                elevationGain = current.elevationGain,
                durationSeconds = duration,
                startedAt = start,
                finishedAt = now,
                createdAt = now,
            )
            resetForFinish()
            completed
        }
        stopTicker()
        persistenceMutex.withLock {
            sessionRepository.clear()
        }
        stopService()
        return completed
    }

    suspend fun loadActiveSession(): ActiveSession? = sessionRepository.load()

    fun restore(session: ActiveSession, startService: Boolean = true) {
        synchronized(lock) {
            resetRouteDeviationLocked()
            accumulatedPausedMs = session.accumulatedPausedMs
            pauseStartedAt = session.pauseStartedAt
            lastSampleTimestamp = session.lastSampleTimestamp
            stationaryCount = session.stationaryCount
            lastValidElevation = session.lastValidElevation
            lastAcceptedPoint = session.lastAcceptedPoint
            val now = System.currentTimeMillis()
            val elapsed = elapsedFor(
                session.sessionStartTime,
                session.accumulatedPausedMs,
                session.isPaused,
                session.pauseStartedAt,
                now,
            )
            _state.value = RecordingState(
                workspaceId = session.workspaceId,
                isRecording = true,
                isPaused = session.isPaused,
                points = session.points,
                distanceMeters = session.totalDistance,
                elevationGain = session.totalElevationGain,
                currentElevation = session.lastValidElevation,
                currentPosition = null,
                gpsState = GpsState.waiting,
                motionState = if (session.isPaused) MotionState.STANDBY else MotionState.MOVING,
                startedAt = session.sessionStartTime,
                elapsedSeconds = elapsed,
            )
        }
        startTicker()
        loadTargetRoute(session.workspaceId)
        RecordingWatchdog.setActive(appContext, true)
        if (startService) startService()
    }

    suspend fun discardSession() {
        stopTicker()
        synchronized(lock) { resetForFinish() }
        persistenceMutex.withLock { sessionRepository.clear() }
        stopService()
    }

    fun refreshTargetRoute(workspaceId: String) {
        loadTargetRoute(workspaceId)
    }

    fun handleLocation(location: Location) {
        val now = System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val speed = if (location.hasSpeed()) location.speed else null
        val elevation = if (location.hasAltitude()) location.altitude.roundToInt() else null
        val position = LivePosition(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = accuracy,
            speed = speed,
            timestamp = now,
        )
        synchronized(lock) {
            val current = _state.value
            val withPosition = current.copy(
                currentPosition = position,
                currentElevation = elevation,
                gpsState = GpsState(
                    status = GpsStatus.ACTIVE,
                    accuracy = accuracy,
                    lastUpdate = now,
                    error = "",
                ),
            )
            _state.value = withPosition
            if (!withPosition.isRecording || withPosition.isPaused) return
            maybeAlertRouteDeviation(position, accuracy, now)
            if (accuracy != null && accuracy > 50f) return

            val minInterval = if (lowPower) 3000L else 1500L
            if (now - lastSampleTimestamp < minInterval && withPosition.points.isNotEmpty()) {
                return
            }

            val newPoint = GeoPoint(
                lat = location.latitude,
                lng = location.longitude,
                ele = elevation,
                time = now,
            )
            val lastPoint = withPosition.points.lastOrNull()
            if (lastPoint == null) {
                lastSampleTimestamp = now
                lastValidElevation = elevation
                addAcceptedPoint(newPoint, 0.0, withPosition, now)
                return
            }

            val deltaMeters = GeoUtils.distanceInMeters(lastPoint, newPoint)
            val consideredStationary = deltaMeters < (if (lowPower) 8.0 else 4.0) &&
                (speed == null || speed < 0.4f)
            if (consideredStationary) {
                stationaryCount += 1
                if (stationaryCount >= 3 && _state.value.motionState != MotionState.STATIONARY) {
                    _state.value = _state.value.copy(motionState = MotionState.STATIONARY)
                }
                return
            }

            stationaryCount = 0
            if (withPosition.motionState != MotionState.MOVING) {
                _state.value = _state.value.copy(motionState = MotionState.MOVING)
            }
            val lastTime = lastPoint.time ?: 0L
            if (deltaMeters > 300.0 && now - lastTime < 5000L) return

            lastSampleTimestamp = now
            addAcceptedPoint(newPoint, deltaMeters, _state.value, now)
        }
    }

    fun updateGpsState(gpsState: GpsState) {
        _state.value = _state.value.copy(gpsState = gpsState)
    }

    fun onAppBackground() {
        synchronized(lock) {
            val current = _state.value
            if (current.isRecording && !current.isPaused) {
                _state.value = current.copy(motionState = MotionState.BACKGROUND)
            }
        }
    }

    fun onAppForeground() {
        synchronized(lock) {
            val current = _state.value
            if (current.isRecording && !current.isPaused &&
                current.motionState == MotionState.BACKGROUND
            ) {
                _state.value = current.copy(motionState = MotionState.MOVING)
            }
        }
    }

    private fun loadTargetRoute(workspaceId: String) {
        scope.launch {
            val points = workspaceRepository.getWorkspace(workspaceId)
                ?.targetTrack?.points.orEmpty()
            val state = _state.value
            if (state.isRecording && state.workspaceId == workspaceId) {
                targetRoute = points
            }
        }
    }

    private fun maybeAlertRouteDeviation(
        position: LivePosition,
        accuracy: Float?,
        now: Long,
    ) {
        val route = targetRoute
        if (!routeDeviationAlertEnabled || route.size < 2) {
            resetRouteDeviationLocked()
            return
        }
        if (now - lastRouteCheckAt < ROUTE_CHECK_INTERVAL_MS) return
        lastRouteCheckAt = now
        if (accuracy != null && accuracy > ROUTE_MAX_ACCURACY_METERS) return
        val distance = GeoUtils.distanceToPolylineMeters(
            GeoPoint(position.lat, position.lng),
            route,
        ) ?: return
        if (distance > ROUTE_DEVIATION_METERS) {
            deviationSamples += 1
            if (deviationSamples >= ROUTE_CONFIRM_SAMPLES &&
                (!routeDeviationActive || now - lastDeviationAlertAt >= ROUTE_ALERT_COOLDOWN_MS)
            ) {
                routeDeviationActive = true
                lastDeviationAlertAt = now
                RecordingWatchdog.speak(appContext, "路径已偏移，请返回目标路线")
            }
        } else {
            deviationSamples = 0
            if (distance <= ROUTE_RECOVERY_METERS) routeDeviationActive = false
        }
    }

    private fun resetRouteDeviationLocked() {
        deviationSamples = 0
        routeDeviationActive = false
        lastDeviationAlertAt = 0L
        lastRouteCheckAt = 0L
    }

    private fun addAcceptedPoint(
        point: GeoPoint,
        deltaMeters: Double,
        current: RecordingState,
        now: Long,
    ) {
        var distance = current.distanceMeters + deltaMeters
        var elevationGain = current.elevationGain
        val elevation = point.ele
        if (elevation != null) {
            val previous = lastValidElevation
            if (previous == null) {
                lastValidElevation = elevation
            } else {
                val delta = elevation - previous
                if (abs(delta) >= 3) {
                    if (delta > 0) elevationGain += delta
                    lastValidElevation = elevation
                }
            }
        }
        lastAcceptedPoint = point
        val next = current.copy(
            points = current.points + point,
            distanceMeters = distance,
            elevationGain = elevationGain,
            motionState = if (current.isPaused) MotionState.STANDBY else MotionState.MOVING,
        )
        _state.value = next
        val sequence = next.points.lastIndex
        val session = buildSession(next, next.startedAt ?: now)
        scope.launch {
            persistenceMutex.withLock {
                sessionRepository.appendPoint(sequence, point, session)
            }
        }
    }

    private fun persist(current: RecordingState, sessionStart: Long, now: Long) {
        val session = buildSession(current, sessionStart)
        scope.launch {
            persistenceMutex.withLock { sessionRepository.save(session) }
        }
    }

    private fun buildSession(current: RecordingState, sessionStart: Long): ActiveSession = ActiveSession(
        workspaceId = current.workspaceId ?: "",
        sessionStartTime = sessionStart,
        accumulatedPausedMs = accumulatedPausedMs,
        totalDistance = current.distanceMeters,
        totalElevationGain = current.elevationGain,
        points = current.points,
        isPaused = current.isPaused,
        pauseStartedAt = pauseStartedAt,
        lastAcceptedPoint = lastAcceptedPoint ?: current.points.lastOrNull(),
        lastSampleTimestamp = lastSampleTimestamp,
        motionState = current.motionState,
        stationaryCount = stationaryCount,
        lastValidElevation = lastValidElevation,
        savedAt = System.currentTimeMillis(),
    )

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _state.value
                if (!current.isRecording) break
                val start = current.startedAt ?: break
                val elapsed = elapsedFor(
                    sessionStart = start,
                    accumulatedPaused = accumulatedPausedMs,
                    isPaused = current.isPaused,
                    pauseStartedAt = pauseStartedAt,
                    now = System.currentTimeMillis(),
                )
                if (elapsed != current.elapsedSeconds) {
                    _state.update { if (it.isRecording) it.copy(elapsedSeconds = elapsed) else it }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun elapsedFor(
        sessionStart: Long,
        accumulatedPaused: Long,
        isPaused: Boolean,
        pauseStartedAt: Long?,
        now: Long,
    ): Long {
        val pausedFor = if (isPaused && pauseStartedAt != null) {
            (now - pauseStartedAt).coerceAtLeast(0)
        } else {
            0L
        }
        val total = (now - sessionStart - accumulatedPaused - pausedFor).coerceAtLeast(0)
        return total / 1000
    }

    @Volatile
    private var accumulatedPausedMs = 0L

    @Volatile
    private var pauseStartedAt: Long? = null
    private var lastSampleTimestamp = 0L
    private var stationaryCount = 0
    private var lastValidElevation: Int? = null
    private var lastAcceptedPoint: GeoPoint? = null

    private fun resetForFinish() {
        accumulatedPausedMs = 0L
        pauseStartedAt = null
        lastSampleTimestamp = 0L
        stationaryCount = 0
        lastValidElevation = null
        lastAcceptedPoint = null
        targetRoute = emptyList()
        resetRouteDeviationLocked()
        _state.value = RecordingState(
            currentElevation = _state.value.currentElevation,
            currentPosition = _state.value.currentPosition,
            gpsState = _state.value.gpsState,
        )
    }

    private fun startService() {
        RecordingWatchdog.setActive(appContext, true)
        val intent = Intent(appContext, RecordingService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopService() {
        RecordingWatchdog.setActive(appContext, false)
        runCatching { appContext.stopService(Intent(appContext, RecordingService::class.java)) }
    }

    private companion object {
        const val ROUTE_DEVIATION_METERS = 50.0
        const val ROUTE_RECOVERY_METERS = 30.0
        const val ROUTE_MAX_ACCURACY_METERS = 35f
        const val ROUTE_CONFIRM_SAMPLES = 3
        const val ROUTE_CHECK_INTERVAL_MS = 2_000L
        const val ROUTE_ALERT_COOLDOWN_MS = 2 * 60 * 1000L
    }
}

object RecordingEngineProvider {
    @Volatile
    var instance: RecordingEngine? = null
}
