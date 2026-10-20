# 连点器 (AutoClicker)

一个 Android 连点器 App：开启无障碍服务后，屏幕上会出现一个红色准星和控制面板。把准星拖到你想点击的位置，点击**「开始连点」**即可在准星处连续模拟点击，再次点击**「停止连点」**即可停止。可调节点击间隔。

> 实现原理：通过 Android **无障碍服务 (AccessibilityService)** 的 `dispatchGesture()` 模拟点击，无需 root。准星与控制面板使用无障碍悬浮窗 (`TYPE_ACCESSIBILITY_OVERLAY`)，无需额外的悬浮窗权限。
>
> ⚠️ 请仅将其用于测试、辅助操作等合法用途，切勿用于违反相关 App/游戏服务条款的场景。

---

## 一、如何得到 APK

本机没有安装 Android 构建工具链，因此需要用下面任意一种方式来编译出 `.apk`。

### 方式 A：GitHub Actions 云端自动编译（推荐，本机无需装任何东西）

1. 在 GitHub 新建一个仓库。
2. 把本项目 `AutoClicker/` 目录下的所有文件推送上去：
   ```bash
   cd AutoClicker
   git init
   git add .
   git commit -m "init autoclicker"
   git branch -M main
   git remote add origin https://github.com/<你的用户名>/<仓库名>.git
   git push -u origin main
   ```
3. 推送后，GitHub 会自动运行 `.github/workflows/build.yml` 进行编译。
4. 打开仓库页面 → **Actions** 标签 → 选择最新一次运行 → 底部 **Artifacts** 里下载 `autoclicker-debug-apk`，解压即得到 `app-debug.apk`。

### 方式 B：Android Studio（图形界面）

1. 安装 [Android Studio](https://developer.android.com/studio)。
2. `File → Open` 打开本项目 `AutoClicker/` 目录，等待 Gradle 同步（会自动下载 SDK/Gradle）。
3. 菜单 `Build → Build Bundle(s) / APK(s) → Build APK(s)`。
4. 完成后点通知里的 `locate` 即可找到 `app/build/outputs/apk/debug/app-debug.apk`。

### 方式 C：命令行

需已安装 JDK 17 与 Android SDK（设置好 `ANDROID_HOME` / `local.properties` 中的 `sdk.dir`）：
```bash
cd AutoClicker
gradle :app:assembleDebug        # 或 ./gradlew :app:assembleDebug（若已生成 wrapper）
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

---

## 二、安装与使用

1. 把 `app-debug.apk` 传到手机并安装（需允许「安装未知来源应用」）。
2. 打开「连点器」App，点击「打开无障碍设置并启用」。
3. 在系统无障碍列表中找到 **连点器**，开启并允许。
4. 返回桌面/目标 App，屏幕上出现红色准星 + 控制面板：
   - 拖动**红色准星**到要点击的位置。
   - 点击面板上的「开始连点」→ 在准星处连续点击；再次点击「停止连点」即可停止。
   - 用「＋ / －」调节点击间隔（毫秒，最小 10ms）。
   - 「关闭连点器」停止服务并移除悬浮窗。

---

## 三、项目结构

```
AutoClicker/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── .github/workflows/build.yml        # 云端自动编译 APK
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/autoclicker/
        │   ├── MainActivity.kt         # 引导开启无障碍服务
        │   └── AutoClickService.kt     # 悬浮控件 + 连续点击核心逻辑
        └── res/
            ├── drawable/ic_launcher.xml
            ├── values/strings.xml
            └── xml/accessibility_service_config.xml
```

## 四、说明

- 最低支持 Android 7.0 (API 24，`dispatchGesture` 从该版本引入)。
- 部分定制系统可能对无障碍服务有额外限制，需在设置中允许其「后台运行」或关闭省电限制。
