# AGENTS.md

## 项目

TrailGuardian Android 原生应用。Kotlin + Jetpack Compose，多模块 Gradle 工程。

## 构建与检查

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

要求 JDK 17+（本机可用 Java 21）和 Android SDK Platform 34、Build-Tools 34.0.0。

## 约定

- 保持 `docs/ANDROID_NATIVE_SPEC.md` 描述的用户可见行为。
- 领域模型放在 `core:model`，跨模块共享逻辑放在对应 `core:*` 模块，界面放在 `feature:*`。
- 记录状态与前台服务只在 `service:recording` 中实现；界面通过仓库和状态流读取。
- 版本号只从 `BuildConfig.VERSION_NAME` 读取。
- 不引入音频保活、Web Locks 或类似 Web 后台方案。
- 修改后至少执行 `:app:assembleDebug`。
