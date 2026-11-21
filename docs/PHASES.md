# 分阶段交付记录

每个阶段都以「可验证的成果」收尾，验证方式写在每节末尾。

---

## 阶段 0 — 构建链（arm64 主机上的 Android 工具链）

**目标**：在没有 x86_64 模拟的前提下，让 Gradle + AGP + aapt2 能在 aarch64 Linux 上产出 APK。

**成果**
- 安装 JDK 17、Gradle 8.7。
- Android SDK：`platforms/android-34`（android.jar）+ `build-tools/34.0.0`，取自 Tencent AndroidSDK 镜像，
  并把其中的 x86_64 `aapt2` / `zipalign` 替换为发行版提供的 **原生 arm64** 版本（`/usr/bin/aapt2` 等），
  通过 `android.aapt2FromMavenOverride` 指向它。
- Maven 依赖走阿里云镜像（google / public / gradle-plugin），回退 Maven Central 与 JitPack。
- 写下 `local.properties`、`gradle.properties`、`settings.gradle.kts`。

**验证**：第一次 `assembleDebug` 失败（DSL 错误），修正后第二次跑通到 Kotlin 编译，
最终阶段 2 完整产出 APK；`aapt2 dump badging` 可正常解析产物。

**已知取舍**：文档里同时给出了 x86_64 主机的标准流程与 arm64 的替换流程（`docs/BUILD.md`）。

---

## 阶段 1 — 提取 OpenUtau 原版主题与图标

**目标**：拿到**真实数值**，不允许凭感觉写颜色。

**成果**
- 从 `openutau/OpenUtau`（MIT，`Copyright (c) 2014 StAkira`）读取：
  `Colors/DarkTheme.axaml`、`Colors/LightTheme.axaml`、`Colors/Brushes.axaml`、
  `Styles/Styles.axaml`、`App.axaml`（原件已放入 `docs/upstream/`）。
- 深色 `#26232C` / 浅色 `#FFFFFF` 两套配色，共 22 个键值，逐条落到
  `app/src/main/assets/openutau/theme.json` 与 `ui/theme/OuTheme.kt`。
- 关键发现并落地：`ControlCornerRadius = 0`、`OverlayCornerRadius = 0` → **全部直角**；
  基础字号 12（小字 11、开关 13）；Button 高 20 / Padding 4,0,4,2；TextBox 高 20 / Padding 4,1；
  ListBoxItem 高 24 / Padding 4；ProgressBar 背景透明、前景 AccentBrush1。
- 图标：OpenUtau 使用 Avalonia FluentTheme，界面内联 `<Path>` 同属 Fluent 图标语言。
  因此从 `microsoft/fluentui-system-icons`（MIT）取 regular/24px，生成 38 个 Android VectorDrawable
  （`res/drawable/ou_ic_*.xml`），脚本 `tools/generate_icons.py` 可复现。**没有自创图标。**

**验证**：`docs/openutau-theme.md` / `.json`（含每个数值的仓库路径与行号）；
`docs/upstream/*.axaml` 为原件；`res/drawable/ou_ic_*.xml` 共 38 个文件。

---

## 阶段 2 — 项目骨架与「空壳 APK」

**目标**：项目结构、Manifest、Gradle 配置、主题系统就位，能产出可安装的 APK。

**成果**
- `settings.gradle.kts` / `build.gradle.kts`（根）/ `app/build.gradle.kts`：AGP 8.5.2 + Kotlin 1.9.24 +
  Compose BOM 2024.06.00，`minSdk 21` / `targetSdk 34`，开启 multidex 与 core library desugaring。
- `AndroidManifest.xml`：INTERNET、ACCESS_NETWORK_STATE、FOREGROUND_SERVICE(_DATA_SYNC)、
  POST_NOTIFICATIONS、WAKE_LOCK；`DownloadService` 声明 `foregroundServiceType="dataSync"`。
- Compose 主题系统：`OuColors` / `OuDimens` / `OuTypography` + CompositionLocal，支持跟随系统/浅色/深色。
- 应用资源：`Theme.UtauMarket`（AppCompat.DayNight.NoActionBar，窗口底色即 OpenUtau 的 BackgroundColor）、
  自适应启动图标。

**验证**：`assembleDebug` → `BUILD SUCCESSFUL`，
`app/build/outputs/apk/debug/app-debug.apk`（12.7 MB）；
`aapt2 dump badging` 显示 `sdkVersion:'21'`、`targetSdkVersion:'34'`、`application-label:'UtauMarket'`；
APK 内无 `lib/`（纯 JVM），`assets/` 内已含清单与主题。

---

## 阶段 3 — 数据层

**目标**：资源清单、模型、GitHub 音素器检索。

**成果**
- `data/model/Models.kt`：`ResourceKind` / `NetworkStatus`(🟢🟡🔴) / `EngineType` / `PluginCategory` /
  `PhonemizerLanguage` / `VoicebankFamily` / `LanguageTag` / `InstallRule` / `ResourceItem`。
- `data/manifest/ManifestRepository.kt`：读取 `assets/resource_manifest.json`。
- `assets/resource_manifest.json`：8 个声库（UTAU + DiffSinger）+ 10 个插件，
  **全部是真实的 GitHub Releases 直链**（含一个 `.uar` 与三个 `.dll` 条目用于验证不同路径），
  字段覆盖需求里要求的全部展示信息（原文名/译名、作者、引擎、语言、录音类型、音域、音阶数、
  格式、大小、版本、发布日期、来源平台、网络标记、授权、简介、备注、安装规则）。
- `data/github/GitHubApi.kt`：Search API（`openutau phonemizer`、`openutau in:name,description,readme`、
  `topic:openutau`）+ Releases API，**只保留真正的 `.dll` 附件，自动过滤 Source code.zip**；
  支持用户 Token；识别 403/429 限流并回报 `X-RateLimit-*`。

**验证**：GitHub API 实测（本机在连续检索后确实触发 "API rate limit exceeded"，
正好印证了限流风险与 Token 支持的必要性）；清单内容来自 Releases API 与
`releases.atom` / `releases/expanded_assets` 抓取的真实文件名与字节数。

---

## 阶段 4 — SAF 目录授权 + 已安装管理

**目标**：「设置完成后无需重复授权」+「已安装可视化管理」。

**成果**
- `saf/SafManager.kt`：`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`；
  基于 `DocumentsContract` 的列表 / 创建 / 删除 / 递归复制 / 打开所在文件夹；文件名净化。
- `data/prefs/SettingsStore.kt`：SharedPreferences + StateFlow，持久化三个目录 URI、
  主题模式、GitHub Token、镜像优先开关、探测时间。
- `data/installed/InstalledScanner.kt`：扫描三个授权目录，解析
  `character.txt`（含 Shift_JIS 回退）/ `character.yaml` / `dsconfig.yaml` / `plugin.txt`，
  推断引擎、语言、录音类型（CV/VCV/CVVC/CVC），统计文件数与占用大小。
- 已安装页：按「声库 / 插件 / 音素器」分页；展示名称、作者、引擎、录音类型、版本、文件数、
  大小、安装/修改日期、安装路径；单条「详情 / 打开文件夹 / 删除」；
  多选后「批量删除 / 批量打开文件夹」；删除前弹「**将从设备移除，不可恢复**」确认。

**验证**：编译通过；`ui/AppRoot.kt` 的授权回调会调用 `SafManager.persist` 并更新设置，
授权后立即触发 `refreshInstalled()`。

---

## 阶段 5 — 下载 + 解压 + 自动安装

**目标**：「点一下 → 下载 → 解压 → 装好」，全程不跳浏览器、不需手动解压。

**成果**
- `archive/ArchiveExtractor.kt`：按文件头识别 **zip / rar / 7z / tar.gz**，
  纯 JVM 实现（java.util.zip + Apache Commons Compress + junrar + XZ，**零 native .so**）；
  防 zip-slip（canonical path 校验）、跳过 `__MACOSX`、条目上限 20 万、总大小上限 6GB。
  `.uar`（UTAU 插件归档）按文件头落到 zip 或 rar 分支。
- `archive/Installer.kt`：结构识别——声库找 `character.txt`/`oto.ini`/`dsconfig.yaml`/`character.yaml`；
  插件找 `plugin.txt`，否则取唯一顶层目录；音素器找 `.dll`。
  只保留最外层候选，避免重复安装；识别不到时进入「等待确认」而不是乱写文件。
- `download/DownloadService.kt`：**前台服务**，串行队列，OkHttp 流式下载，
  每 500ms 更新下载速度与通知栏进度；下载 → 解压 → 结构识别 → 经 SAF 写入 → 清理临时目录；
  支持单任务取消与全部取消。
- `download/NotificationHelper.kt`：低优先级通知渠道、进度通知、完成通知。
- `download/DownloadQueueFacade.kt`：由 `ResourceItem` 生成请求并入队，按开关选择镜像直链。

**验证**：编译通过；`assets/resource_manifest.json` 中同时包含 zip / dll / uar 三类资源，
可直接用来跑通三条分支。

---

## 阶段 6 — 完整 UI

**目标**：五大板块 + 下载页，视觉严格对齐 OpenUtau。

**成果**
- `ui/components/OuComponents.kt`：OpenUtau 风格控件库 ——
  `OuCard`(groupbox 样式：Background + 1px BorderBrush + 直角)、`OuButton`(Normal/Clear/Accent)、
  `OuIconButton`、`OuTextField`、`OuCheckbox`、`OuProgressBar`、`OuTag`、`OuDivider`、
  `OuTopBar`、`OuBottomNav`、`OuTabRow`、`OuInfoRow`、`OuDialog`。
  **全部关闭 Material 涟漪**（`indication = null`），自绘悬停/按下/选中态，取值即 Brushes.axaml 的映射。
- `ui/screens/`：插件（5 个二级分类）、音素器（4 个语言分类 + 本地过滤 + 限流错误提示）、
  声库（3 个引擎分类 + 语言细分筛选）、已安装（3 分页 + 多选 + 批量操作）、
  设置（目录授权/主题/Token/镜像/风险提示/关于）、下载任务。
- `ui/AppRoot.kt`：底部导航 5 项、右上角下载入口与活跃任务条、首次启动引导、
  覆盖下载确认弹窗（已安装仍可下载）、轻量消息条。
- 资源卡片展示需求要求的全部字段，缺字段时显示占位符且布局不错乱。

**验证**：Kotlin 编译零错误（仅剩未使用变量的警告）。

---

## 阶段 7 — Release 交付

**目标**：可直接安装的签名 APK + 完整文档。

**成果**
- `tools/make-keystore.sh` 生成自签名 release keystore；
  `app/build.gradle.kts` 在 keystore 存在时自动启用签名、`isMinifyEnabled=true`、`isShrinkResources=true`，
  并配套 `proguard-rules.pro`（keep commons-compress / junrar / xz）。
- `README.md`、`docs/BUILD.md`、`docs/RISKS.md`、`THIRD_PARTY_NOTICES.md`、本文件。
- 最终产物：`app/build/outputs/apk/release/app-release.apk`，并复制到 `/sdcard/Download/`。

**验证**：见 `docs/BUILD.md` 的「发布检查清单」；`apksigner verify` 与 `aapt2 dump badging` 的输出记录在最终交付说明中。
