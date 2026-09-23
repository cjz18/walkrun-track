# walkrun-track

Android 走/跑 GPS 轨迹记录 v0。单应用，三种状态：空闲 / 记录中 / 已结束。地图用高德 Android SDK，历史存在本机 Room。

## 用 Android Studio 打开

1. 安装 Android Studio（带 Android SDK 35，JDK 17 或以上）。
2. **File → Open**，选本仓库根目录（含 `settings.gradle.kts` 的那一层），等 Gradle Sync 结束。
3. 运行配置选 `app`。

命令行编译（需已设置 `ANDROID_HOME` 或 `local.properties` 里的 `sdk.dir`）：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## 设置高德 Key

仓库里的 Key 是占位符 **`YOUR_AMAP_KEY`**，不能拿来出底图或定位。不要把真实 Key 提交进公开仓库。

1. 打开 [高德开放平台控制台](https://console.amap.com/dev/key/app) 创建 **Android** 平台 Key。
2. 包名填 `com.cjz18.walkrun`。
3. 调试证书 SHA1：

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

4. 把 Key 写进下面任一处，然后重新 Sync：
   - `gradle.properties` 里的 `AMAP_API_KEY=YOUR_AMAP_KEY`，或
   - 未纳入 git 的 `local.properties`：`AMAP_API_KEY=你的Key`（优先于 `gradle.properties`）

Key 会注入 `AndroidManifest.xml` 的 `com.amap.api.v2.apikey`。当前依赖是 Maven 上的 3D 地图合包 `com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1`（官方远程依赖同时带了定位和搜索）。本应用只用地图和定位，没有路线推荐或搜索功能。

高德 10.x 起只提供 `armeabi-v7a` 和 `arm64-v8a`。请用真机，或 ARM64 模拟器。x86 模拟器装得上，但地图 native 库加载不了。

## 在手机上运行

1. 打开开发者选项和 USB 调试，用数据线连接。
2. Android Studio 选中这台设备，点 Run。
3. 或：`./gradlew :app:installDebug`

## 怎么用

- 启动只显示地图和「开始」，**不会**申请定位权限。提示文案是「开始走或跑」。
- 点「开始」才申请定位（Android 13+ 同时询问通知，用来显示前台服务通知）。拒绝定位后停在空闲页，文案变为「未开定位，无法画轨迹」，并有「去设置」。
- 记录中：地图画折线，顶部是 `距离 · 时长 · 配速`，底部是「结束」。精度差于 50 米时，灰色提示「信号弱，轨迹可能不准」，没有弹窗。
- 前台定位服务在锁屏后继续往同一条轨迹流追加点。进行中的轨迹在进程内存里，杀掉进程会丢掉这一次；点「保存到历史」之后写入 Room。
- 结束后可「保存到历史」或「不保存」。历史每一行是 `日期 · 距离 · 时长`，点一行会重新画出那条折线。

## 不做

分享、社交、心率、多种运动类型、路线推荐、iOS、Google Maps。
