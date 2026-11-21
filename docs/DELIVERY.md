# 最终交付说明

## 1. 交付物

| 文件 | 大小 | 说明 |
| --- | --- | --- |
| `app/build/outputs/apk/release/app-release.apk` | **2.53 MB** | **最终交付：已签名、可直接安装的自包含发布包** |
| `app/build/outputs/apk/debug/app-debug.apk` | 12.73 MB | 调试包（带工具、未混淆，便于排查） |

同一份 APK 已复制到手机共享存储，方便直接点开安装：

- `/sdcard/Download/UtauMarketMobile-1.0.0.apk`（release，推荐）
- `/sdcard/Download/UtauMarketMobile-1.0.0-debug.apk`（debug）

## 2. 安装

1. 用系统「文件」App 打开 `/sdcard/Download/UtauMarketMobile-1.0.0.apk`；
2. 若提示「未知来源」，允许该文件管理器安装应用；
3. 安装后打开 **UtauMarket**。

首次启动会弹出「先设置两个目录」的引导，按提示授权声库 / 插件目录即可。

## 3. 验证证据（本机实测）

### 3.1 包信息

```
package: name='com.utaumarket.mobile' versionCode='1' versionName='1.0.0'
compileSdkVersion='34' platformBuildVersionName='14'
sdkVersion:'21'
targetSdkVersion:'34'
uses-permission: android.permission.INTERNET
uses-permission: android.permission.ACCESS_NETWORK_STATE
uses-permission: android.permission.FOREGROUND_SERVICE
uses-permission: android.permission.FOREGROUND_SERVICE_DATA_SYNC
uses-permission: android.permission.POST_NOTIFICATIONS
uses-permission: android.permission.WAKE_LOCK
application-label:'UtauMarket'
```

（`aapt2 dump badging`）

### 3.2 签名

```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
Signer #1 certificate DN: CN=UtauMarket Mobile, OU=Companion Tool, O=UtauMarket
Signer #1 key algorithm: RSA / 2048 bits
SHA-256: 87befeb52491c63e627a02f6218d590d8bd877345cccedf336efa54bdd2abd7b
```

（`apksigner verify --verbose --print-certs`）

### 3.3 自包含校验

```
564 files, 2 dex (multidex)
assets/openutau/theme.json       3172 B
assets/resource_manifest.json   15136 B
lib/  -> 不存在
```

- APK 内**没有任何 native 库**（解压全部为纯 JVM 实现），因此不存在 ABI 兼容问题；
- 运行时、Compose、解压库全部打进 APK，**用户不需要安装任何额外依赖**；
- `minSdk 21` 覆盖 Android 5.0 及以上。

### 3.4 内容校验

```
assets/resource_manifest.json -> voicebanks 8, plugins_manual 10
assets/openutau/theme.json    -> dark bg #26232C, light bg #FFFFFF
```

深色 `#26232C` / 浅色 `#FFFFFF` 与 OpenUtau 原版 `DarkTheme.axaml` / `LightTheme.axaml` 完全一致。

### 3.5 内置直链连通性（本机实测，HTTP 状态）

| 资源 | 状态 |
| --- | --- |
| Eugene RE / Eugene JP | 206 ✅ |
| 绮萱 Qixuan v2.7.0 | 206 ✅ |
| 琅 lang V1.0 | 206 ✅ |
| 東北きりたん kiritan V1.0 | 206 ✅ |
| 謡子 yoko V2.0 | 206 ✅ |
| Ria v0.4-tpse | 206 ✅ |
| SPORE v0.1.1 | 206 ✅ |
| 10 个插件条目（含 1 个 .uar、3 个 .dll） | 206 ✅ |

> 说明：探测过程中确实出现过 GitHub API `rate limit exceeded`，以及大文件走 CDN 时的长时间超时，
> 这正好印证了 `docs/RISKS.md` 中的限流与「🟡 直连缓慢」判断。

### 3.6 未完成的验证（如实说明）

**没有在真机上启动过本程序。** 原因：

- 本机的手机桥接只有两条通道：`shiz`（需要 Shizuku）与 `ui`（无障碍）。
- `shiz --status` 返回 `installed=false granted=false`，Shizuku 未安装，无法执行 `pm install`。
- `ui` 通道可用（无障碍服务已开启），但它只能点击屏幕、不能安装 APK；
  且当前屏幕正被用户使用（DSH 应用在前台），按操作规范不抢屏。

因此**安装与首次运行请由你在手机上完成**。若你希望我代为安装，需要先满足其中之一：

1. 安装并授权 **Shizuku**（DSH 应用 → 设置 → 安卓沙箱 → Shizuku），之后我可以用 `pm install -r` 静默安装并截图验证；
2. 或者允许我用无障碍自动点按（会临时占用屏幕约 1 分钟）。

APK 已放在 `/sdcard/Download/`，手动点开即可安装，无需上述任一条件。

## 4. 复现构建

```bash
cd UtauMarketMobile
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleRelease      # 需要 2 分钟左右（首次下载依赖会更久）
```

## 5. 目录索引

- 使用说明与 UI 对照表：[README.md](</root/utaumarket/UtauMarketMobile/README.md>)
- 构建与发布：[docs/BUILD.md](</root/utaumarket/UtauMarketMobile/docs/BUILD.md>)
- 风险提示：[docs/RISKS.md](</root/utaumarket/UtauMarketMobile/docs/RISKS.md>)
- 分阶段记录：[docs/PHASES.md](</root/utaumarket/UtauMarketMobile/docs/PHASES.md>)
- OpenUtau 主题调研（含出处行号）：[docs/openutau-theme.md](</root/utaumarket/UtauMarketMobile/docs/openutau-theme.md>)
- OpenUtau Mobile 调研：[docs/openutau-mobile.md](</root/utaumarket/UtauMarketMobile/docs/openutau-mobile.md>)
- 第三方声明：[THIRD_PARTY_NOTICES.md](</root/utaumarket/UtauMarketMobile/THIRD_PARTY_NOTICES.md>)
