# TrailGuardian Android 原生重做规格

- 基线版本：Web / PWA `1.0.0`
- 文档日期：2026-11-21
- 目标平台：仅 Android
- 目标技术栈：Kotlin + Jetpack Compose
- 文档状态：Android 原生实现基线

本文档用于指导后续 Agent 将当前 React Web 版功能平移到 Android 原生 App。实现时以当前代码为事实来源；本文与代码不一致时，先核对代码并在本文中修正差异。除特别标注为“原生建议”的内容外，默认要求保持现有用户可见行为。

## 1. 范围与目标

### 1.1 必须保留的产品能力

- 轨迹工作空间（GPX）是唯一的外出计划容器。
- 工作空间可包含目标运动路径、已走轨迹和标注点位。
- 目标路径可选；没有目标路径时仍可直接记录已走路径。
- 轨迹记录支持开始、暂停、继续、结束。
- 所有记录、目标路径和点位写入都必须发生在已加载的工作空间内。
- 历史工作空间支持新建、加载、详情、导出和删除。
- GPX / KML 目标路径和 GPX 标注点导入。
- 已走轨迹连同工作空间全部标注点导出 GPX。
- Esri 卫星底图、Esri 路名叠加层和本地生成的等高线。
- 1 - 18 级离线地图区域选择、下载、进度、重新下载和删除。
- 设备方向指南针，仅使用设备方向传感器，不得使用 GPS 行驶方向替代。
- 深色 slate / emerald 移动端视觉体系。

### 1.2 Android 原生必须解决的问题

- 锁屏、切后台后仍持续记录轨迹。
- App 进程被系统回收后，已记录点不丢，可恢复到原工作空间。
- 不再使用静音音频、Web Locks 等浏览器保活手段。

### 1.3 非目标

- 不要求支持 iOS。
- 不新增账号、云同步、服务端 API。
- 不自动读取 Web 版 LocalStorage / IndexedDB；Web 数据迁移需单独设计导出导入。
- 不因为迁移到原生而改变工作空间、目标路径和已走路径的颜色语义。

## 2. 领域模型

### 2.1 关系

- 一个工作空间最多包含一个目标运动路径。
- 一个工作空间最多包含一个已走轨迹。
- 一个工作空间可包含任意数量的标注点位。
- 同一时间只有一个“当前工作空间”。
- 同一时间只允许一个轨迹记录会话。
- 每个离线地图区域可关联一个工作空间，也可独立存在。

### 2.2 轨迹点 Point

```json
{
  "lat": 30.245,
  "lng": 120.125,
  "ele": 152,
  "time": 1763880000000
}
```

- `lat`、`lng`：WGS84 十进制度。
- `ele`：米，允许 `null`；实时记录时对 GPS 海拔取整。
- `time`：Unix 毫秒；导入 GPX 时可为 `null`，实时记录必须有值。
- 内部统一使用 WGS84 / 米 / 毫秒，只在展示层格式化。

### 2.3 Workspace

```json
{
  "id": "workspace-1763880000000-a1b2c3",
  "name": "周末大尖山徒步",
  "createdAt": 1763880000000,
  "updatedAt": 1763880000000,
  "targetTrack": null,
  "recordedTrack": null,
  "pois": []
}
```

- `id`：全局唯一。Web 使用 `workspace-{timestamp}-{random6}`，原生可用 UUID 或同格式字符串。
- `name`：去除首尾空白后的显示名；为空时使用默认名。
- 默认名：`外出计划 {MM-DD HH:mm}`，例如 `外出计划 11-21 08:30`。
- `createdAt`、`updatedAt`：Unix 毫秒。
- 任何目标路径、已走轨迹或点位发生变化时更新 `updatedAt`。
- 工作空间列表保持“最新创建的在前”；更新内容不重新排序。
- 新建工作空间后自动设为当前工作空间，并进入“轨迹”页。

### 2.4 TargetTrack

```json
{
  "id": "target-1763880000000",
  "name": "2022-01-15 16 54 49",
  "points": [],
  "pointCount": 878,
  "importedAt": 1763880000000
}
```

- `name`：导入文件名去掉 `.gpx` / `.kml`；无名称时使用 `目标运动路径`。
- `points`：导入的 `<trkpt>` 或 KML `<coordinates>` 点。
- `pointCount`：导入时的点数。
- `importedAt`：Unix 毫秒，用于地图自动聚焦。
- 重新导入且包含轨迹点时，整体替换旧目标路径。
- 目标路径可选，不导入不影响轨迹记录。

### 2.5 RecordedTrack

```json
{
  "id": "recorded-1763880000000",
  "name": "周末大尖山徒步 已走轨迹",
  "points": [],
  "distanceMeters": 5320.4,
  "elevationGain": 286,
  "durationSeconds": 7200,
  "distance": "5.32",
  "eleGain": 286,
  "time": "02:00:00",
  "startedAt": 1763880000000,
  "finishedAt": 1763885400000,
  "createdAt": 1763880000000
}
```

- `points`：仅保存通过记录过滤条件的已走轨迹点。
- `distanceMeters`：累计距离，用于计算和展示。
- `elevationGain`：累计爬升，米。
- `durationSeconds`：运动耗时；不包含暂停时间。
- `distance`：公里字符串，保留两位小数。
- `eleGain`：累计爬升取整。
- `time`：`HH:MM:SS`。
- 结束记录时如果没有任何有效点，提示 `当前没有可保存的轨迹`，不创建工作空间轨迹。

### 2.6 Poi

手动打点：

```json
{
  "id": "poi-1763880000000",
  "type": "camp",
  "name": "1号营地",
  "desc": "地面平整，可容纳两顶帐篷",
  "lat": 30.245,
  "lng": 120.125
}
```

GPX 导入点额外允许 `ele`、`time`、`sym`、`gpxType`、`source`。

- 点位类型固定为 `camp`、`water`、`danger`、`view`、`marker`。
- 手动打点默认名称 `未命名点`，默认备注为空。
- 点位锚点优先使用当前 GPS 位置；没有 GPS 时使用地图中心；地图未就绪则报错。
- 新点位插入列表最前面。
- GPX 导入点位批量插入到现有点位之前。

点位视觉：

| type | 中文 | 颜色 | 图标语义 |
| --- | --- | --- | --- |
| camp | 营地 | `#10B981` | 帐篷 |
| water | 水源 | `#3B82F6` | 水滴 |
| danger | 危险 | `#EF4444` | 警告三角 |
| view | 观景 | `#F59E0B` | 山峰/观景 |
| marker | 标注 | `#8B5CF6` | 定位点 |

### 2.7 OfflineRegion

```json
{
  "id": "offline-1763880000000",
  "name": "周末大尖山徒步 离线地图",
  "workspaceId": "workspace-1763880000000-a1b2c3",
  "sourceId": "esri-satellite",
  "bounds": {
    "north": 30.31,
    "south": 30.18,
    "east": 120.23,
    "west": 120.05
  },
  "points": [],
  "zooms": [10, 11, 12, 13, 14, 15, 16, 17],
  "includeContour": true,
  "tileCount": 4200,
  "downloadedTiles": 0,
  "failedTiles": 0,
  "estimatedBytes": 103219200,
  "sizeBytes": 0,
  "progress": 0,
  "status": "downloading",
  "createdAt": 1763880000000,
  "completedAt": null
}
```

- `status`：`downloading`、`ready`、`error`。
- `points`：用户圈选的多边形点，仅用于重新下载预填和可视化。
- `bounds`：由圈选点计算出的最小外接矩形；实际下载覆盖矩形，不裁剪多边形。
- `zooms`：用户选择的层级，升序保存。
- `includeContour`：是否同时缓存高程瓦片。
- `tileCount`：计划下载瓦片数量，包含底图和路名叠加层。
- `estimatedBytes`：按每张 24 KiB 估算。
- `sizeBytes`：下载完成后实际/缓存已知大小。
- 下载失败仍保留记录，`status=error`，可通过重新下载替换。

### 2.8 ActiveSession

Web 用于异常关闭恢复的快照：

```json
{
  "workspaceId": "workspace-1763880000000-a1b2c3",
  "sessionStartTime": 1763880000000,
  "accumulatedPausedMs": 0,
  "totalDistance": 0,
  "totalElevationGain": 0,
  "points": [],
  "savedAt": 1763880000000
}
```

原生建议额外保存 `isPaused`、`pauseStartedAt`、`lastAcceptedPoint`、`lastSampleTimestamp`、`motionState`，以便进程死亡后准确恢复暂停时间和统计。

### 2.9 设置

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| 地图源 | `esri-satellite` | 当前只支持 Esri；旧值 `osm` 必须回退到 Esri |
| 等高线开关 | `true` | 地图信息面板控制 |
| 等高线强度 | `0.72` | 范围 `0.1 - 1.0`，步进 `0.05` |
| 低功耗 | `true` | 影响采样间隔和静止判定 |
| 后台音频保活 | `true` | 仅 Web 版字段，Android 不得移植 |

## 3. 核心业务规则

- 没有当前工作空间时，不允许开始记录、导入目标路径或添加点位。
- 没有当前工作空间时，地图仍可浏览并显示实时位置。
- 开始记录前必须有当前工作空间；否则提示并切到“历史轨迹”。
- 记录中不允许新建工作空间。
- 记录中不允许切换到其他工作空间。
- 记录中不允许删除当前正在记录的工作空间。
- 删除工作空间会同时删除其目标路径、已走轨迹和点位，不可撤销。
- 删除当前工作空间后，当前工作空间置空。
- 加载历史工作空间后进入地图；查看详情进入“轨迹”页。
- 目标路径不是开始记录的必需条件。
- 目标路径始终使用蓝色虚线；已走路径始终使用绿色实线。
- 地图信息入口固定左上角，默认折叠；右上角只放定位和打点按钮。
- 底部导航固定为 `地图 / 轨迹 / 历史轨迹 / 设置`。
- 离线地图是独立全屏页面，不属于底部导航页签。

## 4. 应用外壳

### 4.1 顶部栏

除离线地图页面外始终显示。

- 左侧点击区域展示当前工作空间状态圆点、小标题 `当前工作空间`、工作空间名称和运动状态徽标。
- 无工作空间时名称显示 `未加载 · 点击选择`，状态点为琥珀色。
- 运动状态为 `运动中` / `就绪` / `静止` / `后台记录`。
- 点击左侧：有工作空间进入“轨迹”页，无工作空间进入“历史轨迹”页。
- 右侧展示省电模式图标和电池状态；未知电量显示 `--%`。
- Android 原生使用 `BatteryManager` / 广播更新，不依赖浏览器 Battery API。

### 4.2 底部导航

- 高度约 64dp。
- 四个固定项：地图、轨迹、历史轨迹、设置。
- 选中项 emerald，未选中 slate。
- 离线地图页面隐藏导航和顶部栏，返回后恢复进入前的页签。

### 4.3 Toast

- 顶部居中，默认 3200ms。
- 类型：`success` 绿色、`error` 红色、`info` 中性色。
- 最多保留最近 5 条。
- 原生可用 Snackbar 或自定义顶部 Toast，但文案和触发条件保持一致。

## 5. 页面规格

### 5.1 地图页

初始视角：

- 默认中心：`30.245, 120.125`。
- 默认缩放：`14`。
- 最小缩放：`3`，最大缩放：`19`。

图层顺序，从下到上：

1. Esri 卫星底图。
2. Esri 路名叠加层。
3. 等高线。
4. 目标运动路径。
5. 已走路径。
6. 标注点位。
7. 实时定位精度圈和用户位置指南针。

目标路径：

- 颜色 `#38BDF8`，虚线，Web 为 `dash 7 6`、线宽 3、透明度 0.92。
- 起点圆点蓝色填充，终点圆点深色填充。
- 少于 2 个点不绘制线。

已走路径：

- 颜色 `#10B981`，实线，线宽 4、透明度 0.95。
- 起点实心绿色，终点深色填充。
- 少于 2 个点不绘制线。
- 正在记录时显示实时点；未记录时显示工作空间已保存轨迹。

地图信息面板：

- 左上角按钮显示 `Esri 卫星`，点击展开/收起。
- 默认收起；离开地图页自动收起。
- 面板宽约 208px，最大高度约 65% 屏高，可滚动。
- 包含等高线开关、强度滑杆 `0.1 - 1.0`（默认 `0.72`）、状态文案、AWS Terrain 说明和路径图例。
- 等高线状态：`idle` 等高线已关闭、`loading` 正在生成等高线…、`ready` 等高线已显示、`error` 地形数据加载失败，请检查网络。
- 状态为 ready 时显示图例：整百米黄色、五十米浅色。
- 路径图例：目标运动路径蓝色虚线，已走路径绿色实线。

右上角按钮：

- 定位当前：有实时位置时地图居中并至少放大到 16 级；无位置时提示等待 GPS。
- 打点：无工作空间时提示先加载工作空间；有工作空间时打开打点弹窗。

其他浮层：

- 当前海拔不为空时，右下角显示 `海拔 {n} m`。
- 用户位置精度圈半径取 `min(accuracy, 200m)`。
- 用户位置指南针：外圈、脉冲、固定 `N`、旋转箭头、中心绿点。
- 方向未知时箭头透明度约 0.35，弹窗显示 `方向待获取`。
- 弹窗显示朝向、GPS 精度和海拔。

地图底部操作区：

- 有当前工作空间时显示工作空间名和记录摘要。
- 记录中显示实时公里数和耗时；未记录显示 `等待开始记录`。
- 显示开始 / 暂停或继续 / 结束三按钮。
- 无当前工作空间时显示琥珀色按钮 `未加载工作空间 · 选择或新建轨迹`，点击进入历史轨迹页。

自动聚焦：

- 工作空间有目标路径时优先聚焦目标路径。
- 否则聚焦已走轨迹。
- 聚焦只在新工作空间或新导入/新记录轨迹时触发，避免每次切页都跳转。
- 手动点“在地图查看”点位时定位到该点，缩放 17。

### 5.2 轨迹页（当前工作空间详情）

无当前工作空间时：

- 提示 `尚未加载轨迹工作空间`。
- 说明目标路径、轨迹记录和标注点位都只能在轨迹工作空间中进行。
- 提供 `新建` 和 `历史` 两个按钮。

有当前工作空间时：

顶部：

- 小标题 `当前轨迹工作空间`，显示工作空间名称和创建时间。
- 提供地图、历史轨迹两个快捷按钮。

运动记录卡片：

- 标题 `运动记录`。
- 记录状态徽标：记录中 / 已暂停。
- GPS 徽标：`GPS 已定位 ±n m` / `等待 GPS…` / `GPS 不可用`。
- 四项统计：运动距离、运动耗时、累计爬升 / 当前海拔、已记录轨迹点数量。
- 记录中统计使用实时值，否则使用保存轨迹值。
- 卡片底部显示开始 / 暂停 / 结束控件。

目标运动路径卡片：

- 标题 `目标运动路径`，标注 `可选`。
- 未导入时显示说明和两个按钮：
  - `导入目标路径`，接受 GPX/KML。
  - `导出已走轨迹`，没有已走轨迹时禁用。
- 已导入时显示名称、点数、文字 `蓝色虚线显示`，提供 `重新导入`。
- 重新导入时：
  - 有轨迹点的文件替换目标路径。
  - 文件中的 `<wpt>` 点位批量加入现有点位。
  - 只有点位没有轨迹时，保留旧目标路径，仅追加点位。
  - 完全无有效数据时提示错误。
  - 导入后切换到地图页。

已保存轨迹卡片：

- 仅在存在已走轨迹且当前未记录时显示。
- 显示点数、公里、爬升、耗时。
- 提供 `导出 GPX`。

标注点位卡片：

- 标题 `标注点位` 和数量。
- 提供 `打点`。
- 空状态：`暂无标注点位，可在当前位置或地图中心添加。`
- 每项显示类型颜色块、名称、类型、备注、定位按钮和删除按钮。
- 删除需确认，提示 `标注点位已删除`。

离线地图卡片：

- 标题 `离线地图`。
- 未下载：说明 `进入区域选择，并自动定位到目标路径附近`，按钮 `下载离线地图`。
- 下载中：显示进度，按钮禁用，文案 `下载中 n%`。
- 已完成：显示名称和瓦片数，按钮变为 `重新下载`。
- 关联规则：优先找 `workspaceId` 完全匹配的区域，无论状态；兼容旧数据时找 `status=ready` 且名称等于 `{workspace.name} 离线地图` 的区域。
- 点击下载/重新下载进入离线地图选择页，并预填现有区域、名称、层级、等高线选项。

### 5.3 历史轨迹页

空状态：

- 图标、标题 `空空如也`。
- 说明每次外出计划可建立工作空间。
- 按钮 `新建轨迹`。

非空：

- 标题 `历史轨迹`，提供 `新建轨迹`。
- 工作空间卡片：
  - 当前工作空间使用 emerald 边框并显示 `已加载`。
  - 显示创建时间；更新过时显示更新时间。
  - 徽标：目标点数、已走点数、点位数。
  - 有目标路径时显示目标名称。
  - 有已走轨迹时显示公里、爬升、耗时；否则显示 `尚未记录已走轨迹`。
- 四个操作按钮：
  - `加载`：设为当前工作空间并进入地图，提示 `已加载历史轨迹工作空间`。
  - `详情`：设为当前工作空间并进入轨迹页。
  - `导出`：没有已走轨迹时禁用；导出该工作空间已走轨迹和全部点位。
  - `删除`：当前工作空间正在记录时禁用；其他情况确认后删除。
- 新建工作空间在记录中必须被阻止。
- 切换工作空间在记录中必须被阻止。

### 5.4 设置页

Web 版包含功耗、保活、离线地图和版本信息。Android 原生要求：

- 保留低功耗开关。
- 不移植“后台音频轨道防杀保活”和 Web Locks。
- 用原生状态替代：前台定位服务是否运行、后台定位权限状态、电池优化是否受限、当前采样策略。
- 电池优化受限时提供系统设置入口。
- 离线地图入口显示已保存区域数量、缓存瓦片数量和大小。
- 版本信息必须读取 `BuildConfig.VERSION_NAME`，不得像 Web 版一样硬编码成错误版本号。

### 5.5 离线地图页

三种模式：列表、区域选择、层级设置。

#### 列表模式

- 标题 `离线地图`，小标题 `独立管理`。
- 返回进入前的设置页或工作空间详情。
- `添加` 进入空白区域选择。
- 展示已保存区域数量。
- 空状态：`暂无离线地图`，提示点击添加并圈选区域。
- 每条记录显示名称、下载层级、图源、是否包含等高线、已下载/总瓦片数、实际或预计大小。
- 下载中显示进度百分比和进度条，不显示删除按钮。
- 非下载中显示删除按钮。
- 底部 `清空全部离线地图`：任意区域下载中时禁用，文案 `下载进行中，完成后可清空`；清空所有区域记录和全部瓦片缓存。

#### 区域选择模式

- 顶部返回、标题 `选择下载区域`。
- 提示 `点击地图，确定区域（至少选择 3 个点）`。
- 地图缩放初始 14，最小 3，最大 19。
- 点击地图追加选择点。
- 选择点按序号显示红色圆形标记。
- 选择 2 个及以上时显示红色虚线多边形和 16% 红色填充。
- 有目标路径时显示蓝色虚线参考轨迹，并自动适配到目标路径附近，最大缩放 14。
- 左下角显示已选点数。
- 底部三按钮：`撤销` 移除最后一个点；`下一步` 少于 3 点禁用；`重置` 清空选择。
- 从工作空间进入时，自动定位到目标路径；无目标路径则定位到已走轨迹；再无则定位到点位。

#### 层级设置模式

- 标题 `选择地图层级`。
- 名称输入框；名称为空时进入本页自动填 `{工作空间名或“户外计划”} 离线地图`。
- 显示图源、总瓦片数、预计大小。
- 层级可选 `1 - 18`，默认选择 `1 - 17`。
- 选择层级可多选，按升序保存。
- 至少选择一个层级。
- `同时下载等高线叠加数据` 可选项，说明高程瓦片仅支持 1 - 15 级；勾选后加入高程瓦片 URL。
- 单次上限 `6000` 张瓦片；超过时禁用确定并显示错误说明。
- 每张瓦片按 24 KiB 估算。
- 确认后加入下载队列并回到列表。
- 从工作空间“重新下载”进入时，预填原名称、选择点、层级、等高线选项；`replaceMapId` 指向旧区域。
- 重新下载时新记录插入列表最前并移除旧记录，删除旧区域全部瓦片后再下载新区域。

### 5.6 打点弹窗

- 标题 `添加路标 / 兴趣点`。
- 显示锚点经纬度，保留 5 位小数。
- 字段：路标类型（默认营地）、路标名称（默认 `未命名点`）、备注说明。
- 名称占位 `例如：1号营地 / 溪流取水点`，备注占位 `路况、水量、营地平整度等说明...`。
- 取消 / 确认添加；保存后提示 `标注点位已保存`。

### 5.7 新建工作空间弹窗

- 标题 `新建轨迹空间`。
- 说明目标路径、已走轨迹和标注点位都会保存在该工作空间。
- 名称默认 `外出计划 {MM-DD HH:mm}`，可编辑。
- 取消 / 新建。
- 新建后提示 `已新建工作空间：{name}`。

### 5.8 未保存轨迹恢复弹窗

- 标题 `发现未保存轨迹`。
- 显示约多少公里、多少个点。
- 操作：`放弃` / `恢复`。
- 恢复规则：优先恢复到快照原工作空间；原工作空间不存在时新建 `恢复的外出计划`；恢复后进入地图页，继续处于记录中。
- 放弃会清除快照。

## 6. 轨迹记录

### 6.1 状态

- `isRecording`：是否处于记录会话。
- `isPaused`：是否暂停。
- `motionState`：`STANDBY` 就绪、`MOVING` 运动中、`STATIONARY` 静止、`BACKGROUND` 后台记录。
- `gpsState`：`waiting` 等待 GPS、`active` 已定位、`denied` 权限拒绝、`error` 其他定位失败、`unsupported` 设备不支持。

### 6.2 开始

- 必须有当前工作空间。
- 清空当前点、距离、爬升、暂停累计和会话开始时间。
- `sessionStartTime = now`。
- 初始运动状态 `MOVING`。
- 清空旧快照。
- 开始前台定位服务。
- 提示 `开始记录轨迹`。
- 切到地图页。

### 6.3 暂停 / 继续

暂停：

- 记录暂停开始时间。
- 暂停后不再接收或保存轨迹点。
- 已走轨迹保持不变。
- 运动状态改为 `STANDBY`。
- 写入快照。
- 提示 `轨迹记录已暂停`。

继续：

- 将本次暂停时长累加到 `accumulatedPausedMs`。
- 清空暂停开始时间。
- 恢复轨迹点接收。
- 运动状态改为 `MOVING`。
- 提示 `继续记录轨迹`。

暂停期间仍可更新实时位置、当前海拔和 GPS 状态，只是不写入轨迹。

### 6.4 结束

- 没有点则提示 `当前没有可保存的轨迹` 并返回 null。
- 运动耗时：`now - sessionStartTime - accumulatedPausedMs - 当前暂停已持续时长`。
- 创建 RecordedTrack 并保存到记录开始时绑定的工作空间。
- 清空会话、快照和实时统计。
- 停止定位服务。
- 提示 `轨迹已保存`。
- 页面停留位置不变。

### 6.5 采样算法

Web 版行为：

- 定位点精度大于 `50m` 时，不写入轨迹；实时位置仍展示。
- 采样最小间隔：页面隐藏 `10s`、低功耗 `3s`、高性能 `1.5s`。
- 静止判定：与上一点距离小于 `8m`（低功耗）或 `4m`（高性能），且 speed 为空或小于 `0.4m/s`。
- 连续 3 次静止后状态变 `STATIONARY`；移动后恢复 `MOVING`；静止时不新增轨迹点。
- 跳点过滤：新点距上一点大于 `300m` 且间隔小于 `5s` 时丢弃。
- 所有实时位置更新都会更新当前坐标、精度、速度和海拔，无论是否记录。

Android 原生要求：

- 必须去掉“页面隐藏后不再采样”的 Web 行为。
- 低功耗与高性能采样间隔仍以 `3s` / `1.5s` 为基线。
- 后台定位由前台服务持续提供；不得仅在 Activity 可见时记录。
- 建议使用距离过滤和精度阈值控制功耗，但不得直接停止已开始的记录。
- 每个通过过滤的点应立即写入本地数据库，避免只存在内存中。

### 6.6 统计

- 距离：相邻有效点的大圆距离累加，使用平均地球半径约 `6371008.8m`。
- 爬升：只处理非空海拔；与“上一个有效海拔”比较；变化绝对值小于 3m 时忽略；变化绝对值 ≥3m 时更新有效海拔，正变化累加爬升。
- 耗时：排除暂停时间，格式 `HH:MM:SS`。
- 实时耗时每秒刷新；暂停时冻结显示。

### 6.7 快照与恢复

Web 行为：

- 每个新增点后延迟 700ms 合并写快照。
- 快照包含工作空间 ID、开始时间、累计暂停、总距离、累计爬升、全部点、保存时间。
- 异常退出后启动时若发现有点的快照，弹出恢复确认。
- Web 暂停时快照不保存当前暂停状态，恢复后默认继续记录。

Android 要求：

- 使用 Room 保存会话和点，按批提交但不得丢失已确认点。
- 前台服务继续运行时不依赖页面存活。
- App 被杀后服务重启可恢复到当前工作空间并继续记录。
- 如服务未在运行，打开 App 时按快照提供恢复/放弃选择。
- 原生应保存暂停状态，修复 Web 恢复后丢失暂停状态的问题。

### 6.8 Android 后台定位服务

- 使用 `Foreground Service`，类型为 `location`。
- 常驻通知至少显示：正在记录轨迹、当前距离和耗时；暂停/结束入口可作为增强项。
- 通知渠道使用低打扰但不可静默丢失的重要性。
- 记录中 App 去后台或锁屏时，服务继续更新位置。
- 暂停时停止位置更新但保留会话和通知状态；继续时恢复。
- 结束或丢弃会话时停止服务并移除通知。
- 进程被杀后按产品策略使用 `START_STICKY` 或持久会话恢复。
- 用户主动强行停止应用后不保证恢复。
- 需要引导用户将 App 加入电池优化白名单；OEM 省电策略仍可能终止服务。

Android 权限建议：

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`（Android 10+）
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`（Android 13+）
- `WAKE_LOCK`（若使用部分唤醒锁）

权限流程：先请求前台定位；解释锁屏记录用途后请求后台定位；Android 13+ 请求通知权限；权限被拒绝时记录不得伪装为正常进行。

## 7. 指南针

- 只能使用设备方向传感器。
- 不得使用 GPS `bearing`、行进方向或两点夹角模拟手机朝向。
- Android 原生使用 `SensorManager` 的旋转矢量传感器，读取设备朝向。
- 方向未知时保持未知状态，不显示假数据。
- 指南针箭头必须按最短角差连续旋转；跨越 0°/360° 时不得绕大圈。
- Web 平滑策略：最多每 100ms 更新一次，按最短差取 45% 向目标靠近。
- Android 可使用等价低通滤波；滤波后仍必须保证跨正北连续。
- 方位文案：`北`、`东北`、`东`、`东南`、`南`、`西南`、`西`、`西北`，按 45° 分扇区。
- 弹窗显示 `朝向 {方向} {整数角度}°`。
- 支持屏幕旋转时需重新映射传感器坐标，避免横屏方向错误。

## 8. 地图与等高线

### 8.1 底图

当前只保留 Esri：

```text
底图：https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}
路名：https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}
```

- 256px 瓦片，最大层级 19，署名 `Tiles © Esri`。
- 历史存储值 `osm` 必须回退到 `esri-satellite`。
- 不重新引入 OSM，除非在目标设备上验证稳定加载且满足瓦片使用政策。

### 8.2 等高线数据

- 数据源 `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`。
- 最大可用原生层级 15，展示最大层级 18。
- Terrarium 解码公式：`R * 256 + G + B / 256 - 32768` 米。
- 每张高程瓦片 256px。
- 本地生成，不走服务端。
- 下载离线包时只下载到最大 15 级。

### 8.3 等高线算法

- 基础间隔：zoom ≥14 为 50m；zoom ≥12 为 100m；zoom ≥10 为 200m；更低为 500m。
- 如果高程范围 / 间隔 > 36，间隔继续翻倍。
- 跳过低于 -50m 的无效值。
- 采样步长：zoom ≥14 为 1px，否则 2px。
- 使用 marching squares 式边交点法生成线段。
- 每个瓦片最多取一条跨度过长的主等高线做高程标签。
- 主等高线间隔：地形起伏 >800m 时基础间隔 ×5，否则 ×2。
- 主等高线黄色，约 `rgba(251,191,36,0.96)`；普通等高线随海拔从暖白向冷色过渡，约 `rgba(255, 200-248, 165-235, 0.82)`。
- 等高线下方绘制深色描边，提高卫星图可读性。

Android 实现建议：

- 用 MapLibre `CustomGeometrySource` 或自定义本地栅格源渲染。
- 高程瓦片先落盘，再按需生成。
- 生成结果缓存，避免平移地图重复计算。
- 离线状态下从本地高程瓦片生成等高线，不依赖网络。

### 8.4 地图自动聚焦

- 普通地图：新目标路径、新已走轨迹或新工作空间加载时适配边界，padding 48px，最大缩放 16。
- 离线区域选择：适配目标路径，padding 42px，最大缩放 14。
- 手动查看点位：缩放 17。
- 定位按钮：至少缩放 16。

### 8.5 瓦片缓存

- Web 以完整 URL 为键缓存瓦片 Blob。
- 在线地图优先读缓存；未命中时请求网络并写缓存。
- 离线地图下载提前填充同一缓存。
- 删除离线区域按该区域 URL 列表删除缓存。
- 清空全部离线地图会清空全部瓦片缓存。
- Android 原生可用 MapLibre OfflineManager，或自建 URL 键瓦片缓存 + 离线元数据表；必须支持按区域删除。
- 原生无浏览器 CORS 限制，但仍需遵守图源使用政策。

## 9. 离线地图

### 9.1 计划生成

- 根据圈选点求最小外接矩形。
- 对每个选择的 zoom：下载底图所有覆盖瓦片、路名叠加层所有覆盖瓦片；若勾选等高线且 zoom ≤15，下载 Terrarium 高程瓦片。
- 去重后得到 URL 列表。
- 瓦片数量 = URL 数量。
- 预计大小 = 数量 × 24 KiB。
- 上限 6000 张。
- 注意：下载范围是矩形外接框，不是多边形裁剪。

### 9.2 下载

- Web 并发 6。
- 单瓦片超时 18s。
- 已缓存瓦片计数为 cached，不重复下载。
- 每完成一张（成功、缓存或失败）更新总进度。
- 进度 = 已完成数 / 总数。
- 完成时 `failedTiles = 失败数`、`downloadedTiles = 总数 - 失败数`、`progress = 100`。
- 无失败 `status=ready`；有失败 `status=error`，仍保留记录。
- 写入 `completedAt` 和实际大小。
- 有失败也提示完成，文案包含失败数量。
- 相同区域重新下载时先删除旧区域缓存，再下载新区域。

Android 实现建议：

- 下载任务使用前台通知展示进度，避免用户离开页面后中断。
- 支持暂停/继续或至少支持失败后按区域重新下载。
- 下载记录和已缓存 URL 落库，进程死亡后可恢复状态。

## 10. GPX / KML

### 10.1 GPX 导入

- 读取 `<trkpt>`：`lat`、`lon` 属性，可选 `<ele>`，可选 `<time>`。
- 读取顶层 `<wpt>`：`lat`、`lon`、`name`、`cmt`、`desc`、`sym`、`type`，可选 `ele`、`time`。
- 无效坐标丢弃。
- 无有效轨迹点且无有效点位时报错 `未解析到有效的目标路径或标注点`。
- GPX XML 解析失败时报错 `GPX 文件格式不正确`。

点位类型推断关键词按以下优先级匹配：

1. `camp`：camp、campground、campsite、camping、tent、shelter、hut、bivouac、lodge、营地、露营、帐篷、庇护。
2. `water`：water、drinking、spring、stream、river、well、fountain、tap、水源、取水、溪、泉、河。
3. `danger`：danger、hazard、warning、risk、rockfall、cliff、avalanche、danger area、危险、警告、悬崖、落石、雪崩。
4. `view`：view、viewpoint、scenic、scenery、overlook、peak、summit、mountain、观景、风景、山顶、山峰。
5. 未匹配归为 `marker`。

点位名称回退顺序：`name` → `cmt` → `desc` → `GPX 标注点 #n`；备注优先 `desc`，否则 `cmt`。

### 10.2 KML 导入

- 文件名以 `.kml` 结尾时按 KML 解析。
- 解析全部 `<coordinates>` 节点。
- 每个元组格式 `lng,lat[,ele]`。
- 只生成轨迹点，不生成点位。
- KML 格式错误时报错 `KML 文件格式不正确`。

### 10.3 GPX 导出

- 只导出已走轨迹，不导出目标路径。
- 同时导出当前工作空间全部有效点位。
- 根节点为 GPX 1.1，creator 为 `TrailGuardian`。
- 轨迹点写入 `<trk>` / `<trkseg>` / `<trkpt>`；点位写入顶层 `<wpt>`。
- 点位包含 `name`、可选 `desc`、`ele`、`time`、`sym`、`type`。
- 点位 sym/type 默认映射：
  - camp → Campground / camp
  - water → Drinking Water / water
  - danger → Danger Area / danger
  - view → Scenic Area / view
  - marker → Waypoint / marker
- 文件名：工作空间名；若没有扩展名则追加 `.gpx`。
- Android 使用 Storage Access Framework 创建文件，不再使用浏览器下载链接。
- 无已走轨迹时禁止导出并提示 `该工作空间还没有可导出的已走轨迹`。
- 导出成功后提示 `已导出已走轨迹与 {n} 个标注点位`。

## 11. 存储与迁移

### 11.1 Web 现状

LocalStorage：

- `trail_workspaces`
- `trail_active_workspace_id`
- `trail_offline_maps`
- `trail_map_source`
- `trail_contour_enabled`
- `trail_contour_opacity`
- `trail_low_power`
- `trail_audio_keepalive`（仅 Web）

旧版迁移：

- `trail_saved_tracks` 转为历史工作空间。
- `trail_pois` 加入第一个工作空间；没有轨迹时建立 `导入的路标集合` 工作空间。
- Web 读取 `osm` 时回退到 `esri-satellite`。

IndexedDB `TrailGuardianDB` v2：

- `tiles`：URL → 瓦片 Blob。
- `active_session`：键 `current_active_track` → 当前轨迹快照。

### 11.2 Android 建议 schema

Room 表建议：

```text
workspaces(id, name, created_at, updated_at)
tracks(id, workspace_id, kind, name, distance_m, elevation_gain_m,
       duration_s, started_at, finished_at, imported_at, created_at)
track_points(track_id, seq, lat, lng, ele, time)
pois(id, workspace_id, type, name, description, lat, lng, ele, time,
     sym, gpx_type, source, created_at)
offline_regions(id, name, workspace_id, source_id, north, south, east, west,
                zooms_json, include_contour, tile_count, downloaded_tiles,
                failed_tiles, estimated_bytes, size_bytes, progress, status,
                created_at, completed_at)
active_session(id, workspace_id, session_start_time, accumulated_paused_ms,
               total_distance_m, total_elevation_gain_m, is_paused,
               pause_started_at, updated_at)
```

- `kind` 为 `target` 或 `recorded`。
- 点位保存为独立表，但 UI 仍按 Workspace 聚合。
- 设置使用 DataStore。
- 瓦片建议使用应用私有文件 + 元数据表，或 MapLibre 自有离线库；不要依赖数据库 BLOB 存大图。
- 所有跨表更新使用事务。
- 当前工作空间 ID 使用 DataStore。

### 11.3 数据迁移限制

- Android App 不能直接读取浏览器 LocalStorage / IndexedDB。
- 当前 Web 导出 GPX 只覆盖“已走轨迹 + 点位”，不包含目标路径，也无法完整重建全部历史工作空间。
- 若需要无损迁移，应先增加 Web 端 JSON 备份导出，再在 Android 实现对应导入。
- 在迁移能力实现前，不得声称 Web 数据会自动迁移。

## 12. 原生架构建议

推荐能力：

- UI：Jetpack Compose + Material 3。
- 地图：MapLibre Native Android；Esri 作为 RasterSource，轨迹为 GeoJSON LineLayer，点位为 SymbolLayer。
- 地理计算：Kotlin 自有 `GeoUtils`，保留与 Web 相同的米制公式。
- 数据：Room + DataStore + Coroutines / Flow。
- 定位：`FusedLocationProviderClient`；无 Google Play 服务设备提供 `LocationManager` 回退。
- 后台记录：前台服务 + 常驻通知 + 本地持久化。
- 离线下载：WorkManager 或带前台通知的下载服务。
- XML：`XmlPullParser` 实现 GPX/KML。
- 文件：Storage Access Framework。
- 传感器：`SensorManager` 旋转矢量。
- 权限：Activity Result API。

建议模块：

```text
:app
:core:model
:core:data
:core:geo
:core:gpx
:feature:map
:feature:workspace
:feature:history
:feature:offline
:feature:settings
:service:recording
```

建议状态边界：

- `WorkspaceRepository`：工作空间 CRUD 与聚合。
- `TrackRepository`：目标/已走轨迹和点。
- `PoiRepository`：点位。
- `RecordingEngine`：状态机、过滤、统计。
- `RecordingService`：前台定位服务。
- `OfflineMapRepository`：区域、计划、下载、缓存删除。
- `MapState`：图源、等高线、聚焦请求。
- `AppSettingsRepository`：DataStore。

## 13. 视觉与交互基线

核心颜色：

- 背景：slate-950 `#020617`。
- 面板：slate-900 `#0F172A`、slate-800 `#1E293B`。
- 边框：slate-700 `#334155`。
- 主色：emerald `#10B981` / `#34D399`。
- 目标路径：sky `#38BDF8`。
- 警告/暂停：amber `#F59E0B`。
- 危险/结束：rose `#EF4444`。

布局：

- 圆角卡片为主，常用 `12 - 16dp` 圆角。
- 地图浮层使用半透明深色背景和模糊。
- 主要按钮高度至少 40dp，地图图标按钮至少 44dp 触控区域。
- 中文文案保持现有措辞，代码标识符保持英文。
- 窄屏下不得让顶部地图信息面板遮挡右上角定位按钮。
- 底部导航和地图操作栏必须适配手势导航栏安全区。

确认类操作：

- 删除工作空间、删除点位、删除离线地图、清空全部离线地图均需二次确认。
- 危险操作按钮使用 rose 色；普通取消使用 slate 色。

## 14. 验收标准

### 14.1 工作空间

- [ ] 无工作空间时地图可浏览，但不能开始记录、导入目标路径或打点。
- [ ] 新建工作空间后自动加载并进入轨迹页。
- [ ] 记录中不能新建、切换或删除当前工作空间。
- [ ] 工作空间删除后目标、轨迹和点位一起删除。
- [ ] 工作空间更新后历史列表展示更新时间。

### 14.2 记录

- [ ] 开始、暂停、继续、结束行为与规格一致。
- [ ] 暂停期间不新增点，但实时位置和海拔仍更新。
- [ ] 距离、爬升、耗时算法与 Web 基线一致。
- [ ] 精度大于 50m 的点不写入轨迹。
- [ ] 静止点不会持续堆积。
- [ ] 结束记录后已走轨迹出现在当前工作空间和历史列表。
- [ ] 无有效点时结束不会创建工作空间轨迹。
- [ ] 锁屏 60 分钟持续记录，轨迹无明显断点。
- [ ] App 切后台 30 分钟持续记录。
- [ ] 进程被杀后已记录点不丢，可恢复。
- [ ] 用户强行停止应用后，恢复策略有明确提示且不伪造连续轨迹。

### 14.3 地图

- [ ] Esri 底图和路名层正常加载。
- [ ] 旧 `osm` 设置回退到 Esri。
- [ ] 目标路径蓝色虚线，已走路径绿色实线。
- [ ] 地图信息默认折叠，左上角按钮可展开/收起。
- [ ] 等高线开关、强度和状态正确。
- [ ] 定位按钮和打点按钮行为正确。
- [ ] 目标路径导入或工作空间加载时自动聚焦。
- [ ] 点位可查看、定位、删除。

### 14.4 指南针

- [ ] 无传感器或无方向时显示未知状态。
- [ ] 普通方向更新平滑。
- [ ] 359° → 1° 和 1° → 359° 均按最短方向旋转。
- [ ] 横竖屏切换后方向仍正确。
- [ ] 不因移动速度或 GPS bearing 改变朝向。

### 14.5 GPX / KML

- [ ] 导入 GPX 轨迹点和 `<wpt>` 点位。
- [ ] 重新导入替换目标路径并追加点位。
- [ ] KML 坐标解析正确。
- [ ] 导出 GPX 包含已走轨迹和当前工作空间全部点位。
- [ ] 无已走轨迹时导出按钮禁用。
- [ ] 导出文件名和扩展名正确。

### 14.6 离线地图

- [ ] 至少 3 点才能进入层级设置。
- [ ] 默认层级为 1 - 17，可改选 1 - 18。
- [ ] 包含底图和路名叠加层。
- [ ] 勾选等高线时只下载 1 - 15 级高程数据。
- [ ] 超过 6000 张时不能确认。
- [ ] 下载进度、失败数、完成状态正确。
- [ ] 重新下载移除旧区域缓存。
- [ ] 删除区域只删除该区域瓦片。
- [ ] 清空全部会清空全部瓦片缓存。
- [ ] 断网后已下载区域和层级可显示。

### 14.7 设置与版本

- [ ] 不出现音频保活、Web Locks 或“后台锁”文案。
- [ ] 显示后台定位服务状态、后台定位权限和电池优化状态。
- [ ] 离线地图数量和缓存大小正确。
- [ ] 版本号来自 `BuildConfig.VERSION_NAME`。

## 15. 实施顺序建议

1. 建立 Compose 应用壳、导航、Room、DataStore 和领域模型。
2. 完成工作空间 CRUD、历史列表、详情页和空状态。
3. 接入 MapLibre、Esri 底图、目标/已走轨迹和点位。
4. 完成前台定位服务、记录状态机、持久化与恢复。
5. 完成 GPX/KML 导入导出。
6. 完成离线区域计划、下载、缓存删除和离线验证。
7. 完成等高线生成和传感器指南针。
8. 真机测试锁屏、后台、进程死亡、OEM 省电策略、权限拒绝和离线场景。
9. 更新 `VERSION.md`、`README.md`、`AGENTS.md` 和发布说明。

## 16. 当前已知差异与待决项

- Web 版锁屏后台记录不可靠，Android 原生必须修复；不能把 Web 的“息屏保活”逻辑照搬。
- Web 版恢复快照会丢失暂停状态，Android 应修复。
- Web 版设置页版本号硬编码为 `v2.0`，与 `package.json` 的 `1.0.0` 不一致；原生必须避免。
- Web 数据不能直接迁移到 Android；无损迁移需要额外设计 JSON 导出/导入。
- Esri / Terrarium 瓦片的使用政策、缓存上限和商业使用许可需在上架前复核。
- Android OEM 电池策略可能导致前台服务被终止，需在真机上给出用户引导。
- 是否把离线地图下载改为可暂停/可续传，作为原生增强项待产品确认。
- 是否在通知栏提供暂停/结束操作，作为原生增强项待产品确认。

## 17. 参考代码

后续 Agent 应以以下 Web 实现为行为基线：

- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/App.jsx`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/hooks/useTrailRecorder.js`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/hooks/useCompassHeading.js`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/components/MapView.jsx`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/components/WorkspaceView.jsx`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/components/WorkspaceHistoryView.jsx`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/components/OfflineMapView.jsx`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/components/SettingsView.jsx`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/services/workspaces.js`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/services/offlineMaps.js`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/services/mapLayers.js`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/services/contourTileLayer.js`
- `/Users/jxrory/Project/TrailGuardian/trail-guardian-web/src/services/gpx.js`




