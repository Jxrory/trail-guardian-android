# TrailGuardian Android

TrailGuardian 的 Android 原生实现，使用 Kotlin、Jetpack Compose、Room、DataStore、WorkManager、MapLibre 和前台定位服务。

## 功能

- 工作空间、目标轨迹、已走轨迹与点位管理
- 后台/锁屏轨迹记录，进程重启后恢复未保存会话
- GPX/KML 导入，GPX 导出
- Esri 卫星底图、参考图层、等高线叠加
- 离线区域规划、瓦片下载与离线地图
- 经纬度换算，支持 WGS84、GCJ-02、BD09 与十进制、度分、度分秒格式
- 配速换算，支持公里/英里，并按里程、时间和配速中的任意两项计算第三项
- 独立户外指南针工具，以及地图上的 GPS、电量和记录状态
- 首页地图三档上拉记录面板，支持概览、记录操作和运动详情

## 构建

要求 JDK 17+、Android SDK 34 和 Gradle 8.7。

```bash
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 模块

- `core:model`：领域模型
- `core:geo`：坐标转换、配速计算、距离、海拔、边界和离线瓦片规划
- `core:gpx`：GPX/KML 解析与 GPX 导出
- `core:data`：Room、DataStore、仓库、瓦片缓存、本地瓦片服务和下载 Worker
- `core:ui`：主题与通用组件
- `service:recording`：定位来源、记录状态机和前台服务
- `feature:tools`：经纬度换算、配速换算和独立指南针界面
- `feature:*`：地图、工作空间、历史、设置、离线地图界面
- `app`：应用壳、导航、权限和依赖容器

## 文档

- 实现基线：`docs/ANDROID_NATIVE_SPEC.md`
- 版本信息：`VERSION.md`
