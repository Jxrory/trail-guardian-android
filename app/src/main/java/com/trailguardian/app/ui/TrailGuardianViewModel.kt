package com.trailguardian.app.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trailguardian.app.BatteryMonitor
import com.trailguardian.app.TrailGuardianApplication
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.gpx.GpxParseException
import com.trailguardian.core.gpx.createGpx
import com.trailguardian.core.gpx.gpxFileName
import com.trailguardian.core.gpx.parseTrackFile
import com.trailguardian.core.model.ActiveSession
import com.trailguardian.core.model.BatteryState
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.GpsState
import com.trailguardian.core.model.GpsStatus
import com.trailguardian.core.model.LivePosition
import com.trailguardian.core.model.MapCamera
import com.trailguardian.core.model.MapFocusRequest
import com.trailguardian.core.model.OfflineRegion
import com.trailguardian.core.model.OfflineRegionInput
import com.trailguardian.core.model.OfflineStatus
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import com.trailguardian.core.model.ToastType
import com.trailguardian.core.model.Workspace
import com.trailguardian.service.recording.LocationSource
import com.trailguardian.service.recording.RecordingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class GpxExport(
    val fileName: String,
    val content: String,
    val poiCount: Int,
)

data class CacheInfo(
    val count: Int = 0,
    val bytes: Long = 0,
)

enum class NavTarget {
    MAP,
    TRACK,
    HISTORY,
}

class TrailGuardianViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TrailGuardianApplication).container
    private val settings = container.settingsRepository
    private val workspaceRepository = container.workspaceRepository
    private val offlineRepository = container.offlineMapRepository
    private val engine = container.recordingEngine
    private val toastCenter = ToastCenter()
    private val batteryMonitor = BatteryMonitor(application)

    val toasts: StateFlow<List<ToastMessage>> = toastCenter.messages
    val recordingState: StateFlow<RecordingState> = engine.state
    val serviceRunning: StateFlow<Boolean> = engine.serviceRunning
    val contourStatus = container.tileServer.contourStatus
    val battery: StateFlow<BatteryState> = batteryMonitor.state

    val workspaces: StateFlow<List<Workspace>> = workspaceRepository.workspaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkspaceId: StateFlow<String?> = settings.activeWorkspaceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeWorkspace: StateFlow<Workspace?> = combine(
        workspaces,
        activeWorkspaceId,
    ) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val offlineRegions: StateFlow<List<OfflineRegion>> = offlineRepository.regions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workspaceOfflineMap: StateFlow<OfflineRegion?> = combine(
        activeWorkspace,
        offlineRegions,
    ) { workspace, regions ->
        if (workspace == null) return@combine null
        regions.firstOrNull { it.workspaceId == workspace.id }
            ?: regions.firstOrNull {
                it.status == OfflineStatus.READY && it.name == "${workspace.name} 离线地图"
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val contourEnabled: StateFlow<Boolean> = settings.contourEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val contourOpacity: StateFlow<Float> = settings.contourOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.72f)
    val lowPower: StateFlow<Boolean> = settings.lowPower
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val mapSourceId: StateFlow<String> = settings.mapSourceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "esri-satellite")

    private val _livePosition = MutableStateFlow<LivePosition?>(null)
    private val _liveElevation = MutableStateFlow<Int?>(null)
    private val _liveGpsState = MutableStateFlow(GpsState())

    val displayPosition: StateFlow<LivePosition?> = combine(
        recordingState,
        _livePosition,
    ) { recording, live -> if (recording.isRecording) recording.currentPosition ?: live else live }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val displayElevation: StateFlow<Int?> = combine(
        recordingState,
        _liveElevation,
    ) { recording, live -> if (recording.isRecording) recording.currentElevation ?: live else live }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val displayGpsState: StateFlow<GpsState> = combine(
        recordingState,
        _liveGpsState,
    ) { recording, live -> if (recording.isRecording) recording.gpsState else live }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpsState())

    val targetPoints: StateFlow<List<GeoPoint>> = activeWorkspace
        .map { it?.targetTrack?.points.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordedPoints: StateFlow<List<GeoPoint>> = combine(
        activeWorkspace,
        recordingState,
    ) { workspace, recording ->
        if (recording.isRecording) recording.points else workspace?.recordedTrack?.points.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mapPois: StateFlow<List<Poi>> = activeWorkspace
        .map { it?.pois.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mapFocusRequest: StateFlow<MapFocusRequest?> = activeWorkspace
        .map { workspace ->
            if (workspace == null) return@map null
            val targetPoints = workspace.targetTrack?.points.orEmpty()
            val recordedPoints = workspace.recordedTrack?.points.orEmpty()
            val points = if (targetPoints.isNotEmpty()) targetPoints else recordedPoints
            if (points.isEmpty()) return@map null
            val stamp = workspace.targetTrack?.importedAt ?: workspace.recordedTrack?.createdAt ?: "empty"
            MapFocusRequest(
                key = "workspace-${workspace.id}-$stamp",
                points = points,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _lastFocusKey = MutableStateFlow<String?>(null)
    val lastFocusKey: StateFlow<String?> = _lastFocusKey.asStateFlow()

    private val _mapCamera = MutableStateFlow(MapCamera())
    val mapCamera: StateFlow<MapCamera> = _mapCamera.asStateFlow()

    private val _cacheInfo = MutableStateFlow(CacheInfo())
    val cacheInfo: StateFlow<CacheInfo> = _cacheInfo.asStateFlow()

    private val _pendingRestore = MutableStateFlow<ActiveSession?>(null)
    val pendingRestore: StateFlow<ActiveSession?> = _pendingRestore.asStateFlow()

    private val _exportRequest = MutableStateFlow<GpxExport?>(null)
    val exportRequest: StateFlow<GpxExport?> = _exportRequest.asStateFlow()

    private val _navigation = MutableSharedFlow<NavTarget>(extraBufferCapacity = 8)
    val navigation = _navigation.asSharedFlow()

    private val locationSource = LocationSource(
        context = application,
        onLocation = ::handleLiveLocation,
        onGpsState = { state ->
            if (!recordingState.value.isRecording) _liveGpsState.value = state
        },
    )

    init {
        locationSource.start()
        refreshCacheInfo()
        viewModelScope.launch { checkPendingRestore() }
        viewModelScope.launch {
            combine(activeWorkspaceId, workspaces) { id, list -> id to list }
                .collect { (id, list) ->
                    if (id != null && list.isNotEmpty() && list.none { it.id == id }) {
                        settings.setActiveWorkspaceId(null)
                    }
                }
        }
        observeOfflineCompletions()
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        toastCenter.show(message, type)
    }

    fun createWorkspace(name: String, onCreated: () -> Unit = {}) {
        if (engine.state.value.isRecording) {
            showToast("请先结束当前轨迹记录，再新建工作空间", ToastType.ERROR)
            return
        }
        viewModelScope.launch {
            val workspace = workspaceRepository.createWorkspace(name)
            settings.setActiveWorkspaceId(workspace.id)
            showToast("已新建工作空间：${workspace.name}", ToastType.SUCCESS)
            onCreated()
        }
    }

    fun activateWorkspace(workspaceId: String): Boolean {
        val currentId = activeWorkspaceId.value
        if (engine.state.value.isRecording && workspaceId != currentId) {
            showToast("请先结束当前轨迹记录，再切换工作空间", ToastType.ERROR)
            return false
        }
        viewModelScope.launch { settings.setActiveWorkspaceId(workspaceId) }
        return true
    }

    fun deleteWorkspace(workspaceId: String, onDeleted: () -> Unit = {}) {
        if (engine.state.value.isRecording && activeWorkspaceId.value == workspaceId) {
            showToast("当前工作空间正在记录轨迹，无法删除", ToastType.ERROR)
            return
        }
        viewModelScope.launch {
            workspaceRepository.deleteWorkspace(workspaceId)
            if (activeWorkspaceId.value == workspaceId) settings.setActiveWorkspaceId(null)
            showToast("工作空间已删除", ToastType.INFO)
            onDeleted()
        }
    }

    fun startRecording(): Boolean {
        val workspace = activeWorkspace.value
        if (workspace == null) {
            showToast("请先新建或加载轨迹工作空间，再开始记录", ToastType.ERROR)
            return false
        }
        viewModelScope.launch {
            engine.start(workspace.id)
            showToast("开始记录轨迹", ToastType.SUCCESS)
        }
        return true
    }

    fun togglePause() {
        val state = engine.state.value
        if (!state.isRecording) return
        if (state.isPaused) {
            engine.resume()
            showToast("继续记录轨迹", ToastType.SUCCESS)
        } else {
            engine.pause()
            showToast("轨迹记录已暂停", ToastType.INFO)
        }
    }

    fun finishRecording() {
        viewModelScope.launch {
            val workspaceId = engine.state.value.workspaceId
            val workspaceName = workspaces.value.firstOrNull { it.id == workspaceId }?.name
                ?: "外出计划"
            val completed = engine.finish()
            if (completed == null) {
                showToast("当前没有可保存的轨迹", ToastType.ERROR)
                return@launch
            }
            if (workspaceId == null) {
                showToast("未能找到轨迹所属的工作空间", ToastType.ERROR)
                return@launch
            }
            workspaceRepository.saveRecordedTrack(workspaceId, workspaceName, completed)
            showToast("轨迹已保存", ToastType.SUCCESS)
        }
    }

    fun addPoi(
        type: PoiType,
        name: String,
        description: String,
        anchor: GeoPoint,
        onSaved: (Poi) -> Unit = {},
    ) {
        val workspace = activeWorkspace.value
        if (workspace == null) {
            showToast("请先加载工作空间", ToastType.ERROR)
            return
        }
        viewModelScope.launch {
            val poi = Poi.manual(type, name, description, anchor.lat, anchor.lng)
            workspaceRepository.addPoi(workspace.id, poi)
            showToast("标注点位已保存", ToastType.SUCCESS)
            onSaved(poi)
        }
    }

    fun deletePoi(poiId: String) {
        val workspace = activeWorkspace.value ?: return
        viewModelScope.launch {
            workspaceRepository.deletePoi(workspace.id, poiId)
            showToast("标注点位已删除", ToastType.INFO)
        }
    }

    fun importTarget(fileName: String, text: String) {
        val workspace = activeWorkspace.value
        if (workspace == null) {
            showToast("请先加载工作空间", ToastType.ERROR)
            return
        }
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) { parseTrackFile(text, fileName) }
                val targetName = fileName.replace(
                    Regex("\\.(gpx|kml)$", RegexOption.IGNORE_CASE), "",
                )
                workspaceRepository.importTarget(
                    workspaceId = workspace.id,
                    name = targetName,
                    points = parsed.points,
                    waypoints = parsed.waypoints,
                )
                val parts = buildList {
                    if (parsed.points.isNotEmpty()) add("${parsed.points.size} 个目标路径点")
                    if (parsed.waypoints.isNotEmpty()) add("${parsed.waypoints.size} 个标注点")
                }
                showToast("已导入 ${parts.joinToString("、")}", ToastType.SUCCESS)
                _navigation.tryEmit(NavTarget.MAP)
            } catch (error: GpxParseException) {
                showToast(error.message ?: "目标路径文件解析失败", ToastType.ERROR)
            } catch (error: Exception) {
                showToast(error.message ?: "目标路径文件解析失败", ToastType.ERROR)
            }
        }
    }

    fun exportWorkspace(workspace: Workspace) {
        val recorded = workspace.recordedTrack
        if (recorded == null || recorded.points.isEmpty()) {
            showToast("该工作空间还没有可导出的已走轨迹", ToastType.ERROR)
            return
        }
        val content = createGpx(workspace.name, recorded.points, workspace.pois)
        _exportRequest.value = GpxExport(
            fileName = gpxFileName(workspace.name),
            content = content,
            poiCount = workspace.pois.size,
        )
    }

    fun consumeExportRequest() {
        _exportRequest.value = null
    }

    fun onExportSuccess(poiCount: Int) {
        showToast("已导出已走轨迹与 $poiCount 个标注点位", ToastType.SUCCESS)
    }

    fun addOfflineRegion(input: OfflineRegionInput) {
        viewModelScope.launch {
            offlineRepository.createRegion(input)
            refreshCacheInfo()
            showToast("离线地图已加入下载队列", ToastType.INFO)
        }
    }

    fun deleteOfflineRegion(region: OfflineRegion) {
        viewModelScope.launch {
            offlineRepository.deleteRegion(region)
            refreshCacheInfo()
            showToast("离线地图已删除", ToastType.INFO)
        }
    }

    fun clearOfflineRegions() {
        viewModelScope.launch {
            offlineRepository.clearAll()
            refreshCacheInfo()
            showToast("全部离线地图已清空", ToastType.INFO)
        }
    }

    fun setContourEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setContourEnabled(enabled) }
        container.tileServer.resetContourStatus(enabled)
    }

    fun setContourOpacity(opacity: Float) {
        viewModelScope.launch { settings.setContourOpacity(opacity) }
    }

    fun setLowPower(enabled: Boolean) {
        viewModelScope.launch { settings.setLowPower(enabled) }
    }

    fun onMapFocusApplied(key: String) {
        _lastFocusKey.value = key
    }

    fun onCameraChanged(camera: MapCamera) {
        _mapCamera.value = camera
    }

    fun onMapReady() {
        Unit
    }

    fun restorePendingSession() {
        val session = _pendingRestore.value ?: return
        viewModelScope.launch {
            val existing = if (session.workspaceId.isNotBlank()) {
                workspaceRepository.getWorkspace(session.workspaceId)
            } else {
                null
            }
            val workspace = existing ?: workspaceRepository.createWorkspace("恢复的外出计划")
            settings.setActiveWorkspaceId(workspace.id)
            _pendingRestore.value = null
            engine.restore(session, startService = true)
            showToast("未保存轨迹已恢复", ToastType.SUCCESS)
            _navigation.tryEmit(NavTarget.MAP)
        }
    }

    fun discardPendingSession() {
        viewModelScope.launch {
            engine.discardSession()
            _pendingRestore.value = null
            showToast("未保存轨迹已放弃", ToastType.INFO)
        }
    }

    fun startLiveLocation() {
        if (!engine.state.value.isRecording) locationSource.start()
    }

    fun refreshCacheInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = offlineRepository.cacheTileCount()
            val bytes = offlineRepository.cacheSizeBytes()
            _cacheInfo.value = CacheInfo(count, bytes)
        }
    }

    private fun handleLiveLocation(location: Location) {
        val now = System.currentTimeMillis()
        _livePosition.value = LivePosition(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            speed = if (location.hasSpeed()) location.speed else null,
            timestamp = now,
        )
        _liveElevation.value = if (location.hasAltitude()) location.altitude.roundToInt() else null
        _liveGpsState.value = GpsState(
            status = GpsStatus.ACTIVE,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            lastUpdate = now,
        )
        if (engine.state.value.isRecording) {
            engine.handleLocation(location)
        }
    }

    private suspend fun checkPendingRestore() {
        delay(300)
        if (engine.serviceRunning.value || engine.state.value.isRecording) return
        val session = engine.loadActiveSession() ?: return
        _pendingRestore.value = session
    }

    private fun observeOfflineCompletions() {
        viewModelScope.launch {
            var previous = emptyMap<String, OfflineStatus>()
            offlineRegions.collect { regions ->
                regions.forEach { region ->
                    val before = previous[region.id]
                    if (before == OfflineStatus.DOWNLOADING) {
                        when (region.status) {
                            OfflineStatus.READY -> {
                                showToast(
                                    "离线地图完成：${region.downloadedTiles} 张瓦片已缓存",
                                    ToastType.SUCCESS,
                                )
                                refreshCacheInfo()
                            }
                            OfflineStatus.ERROR -> {
                                showToast(
                                    "离线地图完成：${region.failedTiles} 张瓦片失败",
                                    ToastType.INFO,
                                )
                                refreshCacheInfo()
                            }
                            else -> Unit
                        }
                    }
                }
                previous = regions.associate { it.id to it.status }
            }
        }
    }

    override fun onCleared() {
        locationSource.stop()
        batteryMonitor.close()
        super.onCleared()
    }
}
