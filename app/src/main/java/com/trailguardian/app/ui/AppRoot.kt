package com.trailguardian.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.trailguardian.app.BuildConfig
import com.trailguardian.core.data.tile.LocalTileServer
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.GpsStatus
import com.trailguardian.core.model.OfflineRegion
import com.trailguardian.core.model.OfflineRegionInput
import com.trailguardian.core.model.OfflineStatus
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import com.trailguardian.core.model.ToastType
import com.trailguardian.core.model.Workspace
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.Dot
import com.trailguardian.core.ui.TrailColors
import com.trailguardian.core.ui.TrailConfirmDialog
import com.trailguardian.feature.history.HistoryScreen
import com.trailguardian.feature.map.MapScreen
import com.trailguardian.feature.map.TrailMapController
import com.trailguardian.feature.offline.OfflineEntryRequest
import com.trailguardian.feature.offline.OfflineMapScreen
import com.trailguardian.feature.settings.SettingsScreen
import com.trailguardian.feature.workspace.WorkspaceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.util.Locale

private enum class Tab(val title: String) {
    MAP("地图"),
    TRACK("轨迹"),
    HISTORY("历史轨迹"),
    SETTINGS("设置"),
    OFFLINE("离线地图"),
}

private fun hasForegroundLocation(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocation(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private fun hasNotifications(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private fun batteryOptimizationRestricted(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun AppRoot(viewModel: TrailGuardianViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()
    val activeWorkspace by viewModel.activeWorkspace.collectAsState()
    val recording by viewModel.recordingState.collectAsState()
    val displayPosition by viewModel.displayPosition.collectAsState()
    val displayElevation by viewModel.displayElevation.collectAsState()
    val displayGpsState by viewModel.displayGpsState.collectAsState()
    val targetPoints by viewModel.targetPoints.collectAsState()
    val recordedPoints by viewModel.recordedPoints.collectAsState()
    val mapPois by viewModel.mapPois.collectAsState()
    val mapFocusRequest by viewModel.mapFocusRequest.collectAsState()
    val lastFocusKey by viewModel.lastFocusKey.collectAsState()
    val mapCamera by viewModel.mapCamera.collectAsState()
    val contourEnabled by viewModel.contourEnabled.collectAsState()
    val contourOpacity by viewModel.contourOpacity.collectAsState()
    val contourStatus by viewModel.contourStatus.collectAsState()
    val offlineRegions by viewModel.offlineRegions.collectAsState()
    val workspaceOfflineMap by viewModel.workspaceOfflineMap.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val lowPower by viewModel.lowPower.collectAsState()
    val toasts by viewModel.toasts.collectAsState()
    val pendingRestore by viewModel.pendingRestore.collectAsState()
    val exportRequest by viewModel.exportRequest.collectAsState()
    val cacheInfo by viewModel.cacheInfo.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(Tab.MAP) }
    var offlineReturnTab by rememberSaveable { mutableStateOf(Tab.SETTINGS) }
    var offlineRequest by remember { mutableStateOf<OfflineEntryRequest?>(null) }
    var workspaceCreateOpen by remember { mutableStateOf(false) }
    var poiAnchor by remember { mutableStateOf<GeoPoint?>(null) }
    var pendingPoiFocus by remember { mutableStateOf<Poi?>(null) }
    var mapController by remember { mutableStateOf<TrailMapController?>(null) }
    var pendingExport by remember { mutableStateOf<GpxExport?>(null) }

    var foregroundGranted by remember { mutableStateOf(hasForegroundLocation(context)) }
    var backgroundGranted by remember { mutableStateOf(hasBackgroundLocation(context)) }
    var notificationsGranted by remember { mutableStateOf(hasNotifications(context)) }
    var batteryRestricted by remember { mutableStateOf(batteryOptimizationRestricted(context)) }
    var pendingStart by remember { mutableStateOf(false) }
    var backgroundDialogOpen by remember { mutableStateOf(false) }

    var startRecordingNow: () -> Unit = {}

    fun refreshPermissions() {
        foregroundGranted = hasForegroundLocation(context)
        backgroundGranted = hasBackgroundLocation(context)
        notificationsGranted = hasNotifications(context)
        batteryRestricted = batteryOptimizationRestricted(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted || hasNotifications(context)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        backgroundGranted = granted || hasBackgroundLocation(context)
        if (pendingStart) {
            if (!backgroundGranted) {
                viewModel.showToast("后台定位未授权，锁屏后可能停止记录", ToastType.ERROR)
            }
            startRecordingNow()
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        refreshPermissions()
        viewModel.startLiveLocation()
        if (pendingStart) {
            if (!foregroundGranted) {
                pendingStart = false
                viewModel.showToast("定位权限被拒绝，无法开始记录", ToastType.ERROR)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundGranted) {
                backgroundDialogOpen = true
            } else {
                startRecordingNow()
            }
        }
    }

    startRecordingNow = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val started = viewModel.startRecording()
        if (started) currentTab = Tab.MAP
        pendingStart = false
    }

    fun tryStartRecording() {
        if (activeWorkspace == null) {
            viewModel.startRecording()
            currentTab = Tab.HISTORY
            return
        }
        if (!foregroundGranted) {
            pendingStart = true
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundGranted) {
            pendingStart = true
            backgroundDialogOpen = true
            return
        }
        startRecordingNow()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) { queryFileName(context, uri) } ?: "目标路径.gpx"
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(stream.reader()).readText()
                    }
                }.getOrNull()
            }
            if (text == null) {
                viewModel.showToast("无法读取该文件", ToastType.ERROR)
                return@launch
            }
            viewModel.importTarget(name, text)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val request = pendingExport
        if (uri != null && request != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(request.content.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                if (success) viewModel.onExportSuccess(request.poiCount)
                else viewModel.showToast("导出失败", ToastType.ERROR)
            }
        }
        pendingExport = null
        viewModel.consumeExportRequest()
    }

    LaunchedEffect(exportRequest) {
        val request = exportRequest
        if (request != null && pendingExport == null) {
            pendingExport = request
            exportLauncher.launch(request.fileName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { target ->
            currentTab = when (target) {
                NavTarget.MAP -> Tab.MAP
                NavTarget.TRACK -> Tab.TRACK
                NavTarget.HISTORY -> Tab.HISTORY
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!foregroundGranted) {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(currentTab, mapController, pendingPoiFocus) {
        if (currentTab == Tab.MAP) {
            val poi = pendingPoiFocus
            if (poi != null && mapController != null) {
                mapController?.centerToPoint(poi.lat, poi.lng, 17.0)
                pendingPoiFocus = null
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                viewModel.startLiveLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openOfflineForWorkspace() {
        val workspace = activeWorkspace
        if (workspace == null) {
            viewModel.showToast("请先加载工作空间", ToastType.ERROR)
            return
        }
        val existing = workspaceOfflineMap
        offlineReturnTab = Tab.TRACK
        offlineRequest = OfflineEntryRequest(
            key = System.currentTimeMillis(),
            startPicker = true,
            points = workspaceFocusPoints(workspace),
            initialSelection = existing?.points ?: emptyList(),
            initialName = existing?.name ?: "",
            initialZooms = existing?.zooms,
            initialIncludeContour = existing?.includeContour ?: false,
            replaceMapId = existing?.id,
            workspaceId = workspace.id,
            suggestedName = workspace.name,
            sourceId = viewModel.mapSourceId.value,
        )
        currentTab = Tab.OFFLINE
    }

    fun openOfflineList() {
        offlineReturnTab = Tab.SETTINGS
        offlineRequest = OfflineEntryRequest(
            key = System.currentTimeMillis(),
            startPicker = false,
            points = activeWorkspace?.let(::workspaceFocusPoints) ?: emptyList(),
            suggestedName = activeWorkspace?.name,
            sourceId = viewModel.mapSourceId.value,
        )
        currentTab = Tab.OFFLINE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrailColors.Background),
    ) {
        if (currentTab != Tab.OFFLINE) {
            AppHeader(
                workspaceName = activeWorkspace?.name,
                motionStateLabel = recording.motionState.label,
                batteryLevel = battery.level,
                charging = battery.charging,
                lowPower = lowPower,
                onClick = {
                    currentTab = if (activeWorkspace != null) Tab.TRACK else Tab.HISTORY
                },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (currentTab) {
                Tab.MAP -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        MapScreen(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            tileBaseUrl = LocalTileServer.baseUrl(),
                            targetPoints = targetPoints,
                            recordedPoints = recordedPoints,
                            pois = mapPois,
                            position = displayPosition,
                            currentElevation = displayElevation,
                            focusRequest = mapFocusRequest,
                            lastFocusKey = lastFocusKey,
                            initialCamera = mapCamera,
                            contourEnabled = contourEnabled,
                            contourOpacity = contourOpacity,
                            contourStatus = contourStatus,
                            onFocusApplied = viewModel::onMapFocusApplied,
                            onToggleContour = viewModel::setContourEnabled,
                            onContourOpacityChange = viewModel::setContourOpacity,
                            onLocateUnavailable = {
                                viewModel.showToast("正在等待 GPS 定位…", ToastType.INFO)
                            },
                            onMapNotReady = {
                                viewModel.showToast("地图尚未就绪，请稍后再试", ToastType.ERROR)
                            },
                            onRequestPoi = { anchor ->
                                if (activeWorkspace == null) {
                                    viewModel.showToast("请先新建或加载轨迹工作空间，再进行标注", ToastType.ERROR)
                                } else {
                                    poiAnchor = anchor
                                }
                            },
                            onCameraChanged = viewModel::onCameraChanged,
                            onPoiSelected = {},
                            onControllerReady = { controller -> mapController = controller },
                            onMapReady = {
                                viewModel.onMapReady()
                                val pending = pendingPoiFocus
                                if (pending != null && mapController != null) {
                                    mapController?.centerToPoint(pending.lat, pending.lng, 17.0)
                                    pendingPoiFocus = null
                                }
                            },
                        )
                        MapActionBar(
                            workspace = activeWorkspace,
                            recording = recording,
                            onStart = ::tryStartRecording,
                            onPause = viewModel::togglePause,
                            onFinish = viewModel::finishRecording,
                            onOpenHistory = { currentTab = Tab.HISTORY },
                        )
                    }
                }

                Tab.TRACK -> WorkspaceScreen(
                    workspace = activeWorkspace,
                    isRecording = recording.isRecording,
                    isPaused = recording.isPaused,
                    distanceMeters = recording.distanceMeters,
                    elevationGain = recording.elevationGain,
                    currentElevation = displayElevation,
                    elapsedSeconds = recording.elapsedSeconds,
                    pointCount = recording.points.size,
                    gpsState = displayGpsState,
                    offlineMap = workspaceOfflineMap,
                    onCreateWorkspace = { workspaceCreateOpen = true },
                    onOpenHistory = { currentTab = Tab.HISTORY },
                    onOpenMap = { currentTab = Tab.MAP },
                    onAddPoi = {
                        val workspace = activeWorkspace
                        if (workspace == null) {
                            viewModel.showToast("请先加载工作空间", ToastType.ERROR)
                        } else {
                            val position = displayPosition
                            poiAnchor = if (position != null) {
                                GeoPoint(position.lat, position.lng)
                            } else {
                                GeoPoint(mapCamera.lat, mapCamera.lng)
                            }
                        }
                    },
                    onDeletePoi = viewModel::deletePoi,
                    onFocusPoi = { poi ->
                        pendingPoiFocus = poi
                        currentTab = Tab.MAP
                    },
                    onExport = {
                        activeWorkspace?.let(viewModel::exportWorkspace)
                    },
                    onImportTarget = {
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    onStart = ::tryStartRecording,
                    onPauseOrResume = viewModel::togglePause,
                    onFinish = viewModel::finishRecording,
                    onOpenOffline = ::openOfflineForWorkspace,
                )

                Tab.HISTORY -> HistoryScreen(
                    workspaces = workspaces,
                    activeWorkspaceId = activeWorkspaceId,
                    isRecording = recording.isRecording,
                    onCreate = {
                        if (recording.isRecording) {
                            viewModel.showToast("请先结束当前轨迹记录，再新建工作空间", ToastType.ERROR)
                        } else {
                            workspaceCreateOpen = true
                        }
                    },
                    onLoad = { workspace ->
                        if (viewModel.activateWorkspace(workspace.id)) {
                            currentTab = Tab.MAP
                            viewModel.showToast("已加载历史轨迹工作空间", ToastType.SUCCESS)
                        }
                    },
                    onViewDetails = { workspace ->
                        if (viewModel.activateWorkspace(workspace.id)) currentTab = Tab.TRACK
                    },
                    onExport = viewModel::exportWorkspace,
                    onDelete = { id -> viewModel.deleteWorkspace(id) },
                )

                Tab.SETTINGS -> SettingsScreen(
                    lowPowerEnabled = lowPower,
                    onLowPowerChange = viewModel::setLowPower,
                    serviceRunning = serviceRunning,
                    backgroundLocationGranted = backgroundGranted,
                    batteryOptimizationRestricted = batteryRestricted,
                    offlineMapCount = offlineRegions.size,
                    cacheCount = cacheInfo.count,
                    cacheSizeBytes = cacheInfo.bytes,
                    versionName = BuildConfig.VERSION_NAME,
                    onOpenOfflineMaps = ::openOfflineList,
                    onRequestBackgroundLocation = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            viewModel.showToast("当前系统无需单独授予后台定位权限", ToastType.INFO)
                        }
                    },
                    onOpenBatterySettings = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { context.startActivity(intent) }.onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                )
                            }
                        }
                    },
                )

                Tab.OFFLINE -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    OfflineMapScreen(
                        request = offlineRequest,
                        regions = offlineRegions,
                        tileBaseUrl = LocalTileServer.baseUrl(),
                        onBack = { currentTab = offlineReturnTab },
                        onAddRegion = viewModel::addOfflineRegion,
                        onDeleteRegion = viewModel::deleteOfflineRegion,
                        onClearAll = viewModel::clearOfflineRegions,
                    )
                }
            }

            ToastHost(
                toasts = toasts,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
        }

        if (currentTab != Tab.OFFLINE) {
            BottomNav(
                currentTab = currentTab,
                onChange = { tab -> currentTab = tab },
            )
        }
    }

    if (workspaceCreateOpen) {
        WorkspaceCreateDialog(
            onDismiss = { workspaceCreateOpen = false },
            onCreate = { name ->
                workspaceCreateOpen = false
                viewModel.createWorkspace(name) { currentTab = Tab.TRACK }
            },
        )
    }

    val anchor = poiAnchor
    if (anchor != null) {
        PoiDialog(
            anchor = anchor,
            onDismiss = { poiAnchor = null },
            onSave = { type, name, description ->
                poiAnchor = null
                viewModel.addPoi(type, name, description, anchor)
            },
        )
    }

    val snapshot = pendingRestore
    if (snapshot != null) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = TrailColors.Panel,
            title = { Text("发现未保存轨迹", color = TrailColors.EmeraldBright) },
            text = {
                Text(
                    "检测到上次未完成的轨迹（约 " +
                        String.format(Locale.US, "%.2f", snapshot.totalDistance / 1000.0) +
                        " km，${snapshot.points.size} 个点），是否恢复到所属工作空间？",
                    color = TrailColors.TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::restorePendingSession) {
                    Text("恢复", color = TrailColors.Emerald)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardPendingSession) {
                    Text("放弃", color = TrailColors.TextSecondary)
                }
            },
        )
    }

    if (backgroundDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                backgroundDialogOpen = false
                pendingStart = false
            },
            containerColor = TrailColors.Panel,
            title = { Text("允许后台定位", color = TrailColors.EmeraldBright) },
            text = {
                Text(
                    "锁屏或切到后台后继续记录轨迹，需要将定位权限设置为“始终允许”。若仅授予前台定位，息屏后可能停止记录。",
                    color = TrailColors.TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backgroundDialogOpen = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            startRecordingNow()
                        }
                    },
                ) {
                    Text("去授权", color = TrailColors.Emerald)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            backgroundDialogOpen = false
                            startRecordingNow()
                        },
                    ) {
                        Text("仅前台记录", color = TrailColors.TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            backgroundDialogOpen = false
                            pendingStart = false
                        },
                    ) {
                        Text("取消", color = TrailColors.TextSecondary)
                    }
                }
            },
        )
    }
}

@Composable
private fun AppHeader(
    workspaceName: String?,
    motionStateLabel: String,
    batteryLevel: Float?,
    charging: Boolean,
    lowPower: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrailColors.Panel)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(
                if (workspaceName != null) TrailColors.Emerald else TrailColors.Amber,
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("当前工作空间", color = TrailColors.TextMuted, fontSize = 9.sp)
                Text(
                    workspaceName ?: "未加载 · 点击选择",
                    color = if (workspaceName != null) TrailColors.EmeraldBright else TrailColors.Amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Badge(motionStateLabel, TrailColors.TextSecondary)
        }
        Spacer(Modifier.size(8.dp))
        Icon(
            if (lowPower) Icons.Filled.Eco else Icons.Filled.Speed,
            contentDescription = if (lowPower) "智能低功耗" else "高性能模式",
            tint = if (lowPower) TrailColors.EmeraldBright else TrailColors.Amber,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(8.dp))
        Row(
            modifier = Modifier
                .background(TrailColors.PanelElevated, RoundedCornerShape(6.dp))
                .border(1.dp, TrailColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                contentDescription = null,
                tint = when {
                    charging -> TrailColors.Amber
                    batteryLevel == null -> TrailColors.TextMuted
                    batteryLevel <= 0.2f -> TrailColors.Rose
                    batteryLevel <= 0.45f -> TrailColors.Amber
                    else -> TrailColors.EmeraldBright
                },
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                batteryLevel?.let { "${(it * 100).toInt()}%" } ?: "--%",
                color = TrailColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BottomNav(currentTab: Tab, onChange: (Tab) -> Unit) {
    val items = listOf(
        Triple(Tab.MAP, Icons.Filled.Map, "地图"),
        Triple(Tab.TRACK, Icons.Filled.Route, "轨迹"),
        Triple(Tab.HISTORY, Icons.Filled.History, "历史轨迹"),
        Triple(Tab.SETTINGS, Icons.Filled.Settings, "设置"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrailColors.Panel)
            .navigationBarsPadding()
            .height(64.dp),
    ) {
        items.forEach { (tab, icon, label) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onChange(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (tab == currentTab) TrailColors.EmeraldBright else TrailColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    label,
                    color = if (tab == currentTab) TrailColors.EmeraldBright else TrailColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun MapActionBar(
    workspace: Workspace?,
    recording: com.trailguardian.service.recording.RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrailColors.Panel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (workspace == null) {
            Button(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF78350F).copy(alpha = 0.5f)),
            ) {
                Text("未加载工作空间 · 选择或新建轨迹", color = TrailColors.Amber, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    workspace.name,
                    color = TrailColors.EmeraldBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (recording.isRecording) {
                        String.format(Locale.US, "%.2f", recording.distanceMeters / 1000.0) +
                            " km · ${GeoUtils.formatDuration(recording.elapsedSeconds)}"
                    } else {
                        "等待开始记录"
                    },
                    color = TrailColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !recording.isRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrailColors.Emerald,
                        disabledContainerColor = TrailColors.PanelElevated,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(3.dp))
                    Text("开始", fontSize = 11.sp)
                }
                Button(
                    onClick = onPause,
                    enabled = recording.isRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording.isPaused) TrailColors.Emerald else TrailColors.Amber,
                        disabledContainerColor = TrailColors.PanelElevated,
                    ),
                ) {
                    Icon(
                        if (recording.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(if (recording.isPaused) "继续" else "暂停", fontSize = 11.sp)
                }
                Button(
                    onClick = onFinish,
                    enabled = recording.isRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrailColors.Rose,
                        disabledContainerColor = TrailColors.PanelElevated,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(3.dp))
                    Text("结束", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ToastHost(toasts: List<ToastMessage>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        toasts.forEach { toast ->
            val color = when (toast.type) {
                ToastType.SUCCESS -> TrailColors.Emerald
                ToastType.ERROR -> TrailColors.Rose
                ToastType.INFO -> TrailColors.PanelElevated
            }
            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(toast.message, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WorkspaceCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf(GeoUtils.defaultWorkspaceName()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrailColors.Panel,
        title = { Text("新建轨迹空间", color = TrailColors.EmeraldBright) },
        text = {
            Column {
                Text(
                    "目标路径、已走轨迹和标注点位都会保存在这个工作空间中。",
                    color = TrailColors.TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("工作空间名称") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrailColors.Emerald,
                        unfocusedBorderColor = TrailColors.Border,
                        focusedTextColor = TrailColors.TextPrimary,
                        unfocusedTextColor = TrailColors.TextPrimary,
                        cursorColor = TrailColors.Emerald,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim().ifBlank { GeoUtils.defaultWorkspaceName() }) }) {
                Text("新建", color = TrailColors.Emerald)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TrailColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun PoiDialog(
    anchor: GeoPoint,
    onDismiss: () -> Unit,
    onSave: (PoiType, String, String) -> Unit,
) {
    var type by remember { mutableStateOf(PoiType.CAMP) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrailColors.Panel,
        title = {
            Column {
                Text("添加路标 / 兴趣点", color = TrailColors.EmeraldBright)
                Text(
                    String.format(Locale.US, "%.5f, %.5f", anchor.lat, anchor.lng),
                    color = TrailColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { typeMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${type.label}（${type.key}）", color = TrailColors.TextPrimary)
                    }
                    DropdownMenu(
                        expanded = typeMenuOpen,
                        onDismissRequest = { typeMenuOpen = false },
                    ) {
                        PoiType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (option) {
                                            PoiType.CAMP -> "露营地"
                                            PoiType.WATER -> "水源点"
                                            PoiType.DANGER -> "危险点"
                                            PoiType.VIEW -> "观景台"
                                            PoiType.MARKER -> "其他标注点"
                                        },
                                        color = TrailColors.TextPrimary,
                                    )
                                },
                                onClick = {
                                    type = option
                                    typeMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("路标名称") },
                    placeholder = { Text("例如：1号营地 / 溪流取水点", color = TrailColors.TextMuted) },
                    colors = poiFieldColors(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明") },
                    placeholder = { Text("路况、水量、营地平整度等说明...", color = TrailColors.TextMuted) },
                    minLines = 2,
                    maxLines = 4,
                    colors = poiFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(type, name.trim().ifBlank { Poi.DEFAULT_NAME }, description.trim())
                },
            ) {
                Text("确认添加", color = TrailColors.Emerald)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TrailColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun poiFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TrailColors.Emerald,
    unfocusedBorderColor = TrailColors.Border,
    focusedTextColor = TrailColors.TextPrimary,
    unfocusedTextColor = TrailColors.TextPrimary,
    cursorColor = TrailColors.Emerald,
    focusedLabelColor = TrailColors.Emerald,
    unfocusedLabelColor = TrailColors.TextMuted,
)

private fun workspaceFocusPoints(workspace: Workspace): List<GeoPoint> {
    workspace.targetTrack?.points?.takeIf { it.isNotEmpty() }?.let { return it }
    workspace.recordedTrack?.points?.takeIf { it.isNotEmpty() }?.let { return it }
    return workspace.pois.map { GeoPoint(it.lat, it.lng) }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
    }
    return name
}
