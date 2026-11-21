# OpenUtau Mobile 技术调研报告

调研日期：本次会话。GitHub 仓库最近 push 时间约 2026-10-11。
调研方式：GitHub REST API + raw.githubusercontent.com 源码直读。dl.google.com / maven.google.com 不可用，本次结论不依赖 Google Maven。
状态标记：[已验证] = 直接从官方源码/官方文档读取；[未验证] = 未找到直接证据或存在不确定性。

---

## 0. 最重要的结论（TL;DR）

1. 官方仓库是 **vocoder712/OpenUtauMobile**（不是 stakira/OpenUtau 的 mobile 分支；stakira/OpenUtau 已 301 重定向到 openutau/OpenUtau，仅桌面板）。
2. 框架不是 Flutter / Kotlin / 原生 Android，而是 .NET + C#：
   - 第一代（master 分支，即已发布 Release 的版本）= .NET MAUI（net9.0-android），UI 为 XAML + SkiaSharp 自绘。
   - 第二代（dev 分支，V2 重写，canary）= Avalonia 12.1.3 + .NET 10（net10.0-android36.0）。
3. 安卓数据目录是应用私有目录（/data/user/0/<pkg>/files），不是 /sdcard/OpenUtau，也不是 Android/data/<pkg>/files。声库默认在其中的 Singers/ 子目录。
4. 包名（applicationId）随版本变化：
   - MAUI 稳定版（1.0.x-1.1.7）：pers.vocoder712.openutaumobile
   - V2 canary（dev）：pers.vocoder712.openutaumobile.canary，Debug 构建再追加 .debug
5. 移动版音素器 != 桌面版：移动版把托管 .NET 程序集（.dll）放进 Plugins/ 目录后，用 Assembly.LoadFile 加载并反射 PhonemizerAttribute；原生 Windows UTAU 音素器 DLL 会被 IsManagedAssembly 拒绝。内置音素器以内嵌程序集 OpenUtau.Plugin.Builtin 形式加载，不落盘。
6. 主题：MAUI 版主色固定为 #fe71a3（浅色/深色相同）。V2 版改为 Material 3 动态取色，以“系统强调色”或用户自定义种子色生成整包配色。

---

## 1. 官方仓库、框架、最新版本

### 1.1 仓库地址

| 候选 | 结果 |
| --- | --- |
| stakira/OpenUtauMobile | 不存在（HTTP 404） |
| stakira/OpenUtau-Mobile | 不存在（HTTP 404） |
| stakira/OpenUtau | HTTP 301 重定向到 openutau/OpenUtau（桌面板） |
| **vocoder712/OpenUtauMobile** | **官方移动版仓库（搜索命中）** |

GitHub 仓库搜索 “OpenUtau Mobile” 返回 vocoder712/OpenUtauMobile，描述为 “OpenUtau Mobile 是一个面向移动端的开源免费歌声合成软件 / OpenUtau Mobile is a free and open-source singing voice synthesis software for mobile devices.”
URL：https://github.com/vocoder712/OpenUtauMobile

README 明确：“It is an editor based on the OpenUtau Core with some patches applied. It fully supports OpenUtau USTX project files.”
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/README.md

> 另有 vocoder712/OpenUtauMobile-sync，其描述明确写着“不是 OpenUtauMobile 的源码仓库”，仅为内核 split 缓存同步用，不是源码仓库。

### 1.2 框架

[已验证] 第一代（master）= .NET MAUI
- OpenUtauMobile/OpenUtauMobile.csproj：TargetFrameworks 为 net9.0-android;net9.0-ios（Windows 下追加 net9.0-windows10.0.19041.0）。
- OpenUtauMobile/MauiProgram.cs：MauiApp.CreateBuilder()、UseMauiCommunityToolkit()、UseSkiaSharp()。
- UI：XAML（Views/*.xaml）+ SkiaSharp 画布；MVVM 采用 ReactiveUI（ReactiveObject、[Reactive]、DynamicData）。
- 证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/OpenUtauMobile.csproj 与 .../MauiProgram.cs

[已验证] 第二代（dev，V2）= Avalonia + .NET 10
- README（dev）：“To solve the performance issues, outdated UI design, and cross-platform limitations of the first-generation implementation, the project is being rebuilt from scratch. The new version is based on: Avalonia 12, .NET 10, MVVM architecture”。
- Directory.Build.props（dev）：AvaloniaVersion 12.1.3。
- OpenUtauMobile.Android/OpenUtauMobile.Android.csproj（dev）：TargetFramework net10.0-android36.0，PackageReference Avalonia.Android。
- OpenUtauMobile.Android/MainActivity.cs（dev）：public class MainActivity : AvaloniaMainActivity。
- 平台工程已拆分：OpenUtauMobile.Android / .iOS / .Windows / .Linux / .MacOS / .Browser。
- 证据：https://github.com/vocoder712/OpenUtauMobile/blob/dev/README.md 、 .../Directory.Build.props 、 .../OpenUtauMobile.Android/MainActivity.cs

### 1.3 最新 release 版本

[已验证] master 分支的正式 Release 最新为 v1.1.7（published_at 2025-12-31T16:25:36Z，非 prerelease），APK 资产：
- openutaumobile-v1.1.7-arm64-v8a.apk（约 181 MB）
- openutaumobile-v1.1.7-armeabi-v7a.apk
- openutaumobile-v1.1.7-x86.apk
- openutaumobile-v1.1.7-x86_64.apk
- openutaumobile-v1.1.7.apk（universal，约 238 MB）

OpenUtauMobile/OpenUtauMobile.csproj（master）：ApplicationDisplayVersion 1.1.7、ApplicationVersion 16。
证据：https://github.com/vocoder712/OpenUtauMobile/releases

[已验证] 存在更新的 V2 tag（v2.0.5-v2.0.8），但 Releases API 中最新正式 Release 仍是 1.1.7；V2 目前主要通过 nightly 分发。
V2 nightly 下载入口（README 提供）：https://nightly.link/vocoder712/OpenUtauMobile/workflows/main/dev/OpenUtauMobile-android-arm64.zip

> 重要：README 顶部有醒目提示 —— master 已标记为 legacy mode，主要开发迁移到 dev 分支。

---

## 2. 安卓设备数据目录结构

### 2.1 MAUI 版（1.x，已发布稳定版）

[已验证] OpenUtau.Core/Util/PathManager.cs 构造函数：

    if (DeviceInfo.Current.Platform == DevicePlatform.Android) {
        RootPath = FileSystem.AppDataDirectory;
        DataPath = FileSystem.AppDataDirectory;
        CachePath = FileSystem.CacheDirectory;
        ...
    }

.NET MAUI FileSystem.AppDataDirectory 在 Android 上映射为应用的私有内部存储：/data/user/0/<包名>/files。FileSystem.CacheDirectory 映射为 Context.CacheDir（Android 上通常为 /data/user/0/<包名>/cache）。
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Util/PathManager.cs

因此路径为（以包名 pers.vocoder712.openutaumobile 计）：

| 用途 | 路径常量（源码） | 实际路径 |
| --- | --- | --- |
| 数据根 | DataPath | /data/user/0/pers.vocoder712.openutaumobile/files/ |
| 声库（Singer）默认安装目录 | SingersPath = DataPath/Singers | .../files/Singers/ |
| 旧版声库兼容目录 | SingersPathOld = DataPath/Content/Singers | .../files/Content/Singers/ |
| 额外声库目录（用户可选） | AdditionalSingersPath | 用户在设置里通过系统文件夹选择器（SAF）指定 |
| 插件/音素器目录 | PluginsPath = DataPath/Plugins | .../files/Plugins/ |
| 依赖 | DependencyPath = DataPath/Dependencies | .../files/Dependencies/ |
| 词典 | DictionariesPath = DataPath/Dictionaries | .../files/Dictionaries/ |
| 模板 | TemplatesPath = DataPath/Templates | .../files/Templates/ |
| 日志 | LogFilePath = DataPath/Logs/log.txt | .../files/Logs/log.txt |
| 偏好 | PrefsFilePath = DataPath/prefs.json | .../files/prefs.json |
| 备份/自动保存 | BackupsPath = DataPath/Backups | .../files/Backups/ |
| Resampler / Wavtool | ResamplersPath, WavtoolsPath | .../files/Resamplers / .../files/Wavtools |

注意：源码中没有 /sdcard/OpenUtau/Voicebanks 这类“共享存储”声库目录。files 目录默认对普通文件管理器不可见（需 root、adb run-as，或应用自身导出）。

外部存储权限：OpenUtauMobile/Platforms/Android/AndroidManifest.xml（MAUI）声明了 MANAGE_EXTERNAL_STORAGE、READ/WRITE_EXTERNAL_STORAGE 等；ExternalStorageService.cs 通过 Environment.IsExternalStorageManager / Settings.ActionManageAllFilesAccessPermission 申请“所有文件访问”，目的是让用户能把额外声库路径指向外置存储。
证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/Platforms/Android/AndroidManifest.xml
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/Platforms/Android/Utils/Permission/ExternalStorageService.cs

额外声库路径的语义 [已验证]：PathManager.SingersInstallPath 在 InstallToAdditionalSingersPath == true 且 AdditionalSingerPath 非空时指向额外路径，否则用默认 SingersPath。Preferences.cs 中 InstallToAdditionalSingersPath 默认 true，但 AdditionalSingerPath 默认空串，所以默认仍装在 .../files/Singers/。UI 提示原文（AppResources.resx）：“Add extra folders for Singer Manager to search singers, so you can extract singer install packages into anywhere you want in External Storage Path.”
证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Util/Preferences.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/Resources/Strings/AppResources.resx

对伴生工具的影响（重要）：若目标是“管理 App 内声库”的独立 App，普通第三方 App 无法直接读写 /data/user/0/pers.vocoder712.openutaumobile/files/（除非设备 root、ADB，或 OpenUtau Mobile 自己通过 SAF/DocumentsProvider 导出）。可行对接方式是让用户在设置中开启“额外歌手路径”指向共享存储（如 /sdcard/OpenUtau/Singers）。这一点决定了伴生工具的整体架构，[建议进一步验证]（例如实机抓取 FileSystem.AppDataDirectory 的确切值、以及额外路径实际落地位置）。

### 2.2 V2 版（dev，Avalonia canary）

[已验证] OpenUtauMobile.Android/MainActivity.cs（dev）InitPathManager()：

    string localData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData); // 注释：data/user/<user_id>/pers.vocoder712.openutaumobile/files
    string cache = Android.App.Application.Context.CacheDir?.AbsolutePath ...
    PathManagerInitialization.Initialize(rootPath: localData, dataPath: localData, cachePath: cache);

源码注释直接给出了路径 data/user/<user_id>/pers.vocoder712.openutaumobile/files。V2 的 PathManager 改为宿主注册制（RegisterPaths），目录常量与 MAUI 版一致（Singers、Plugins、Dependencies、Templates、Themes、Logs、prefs.json、Backups），并新增 ThemesPath。
证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtauMobile.Android/MainActivity.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtau.Core/Util/PathManager.cs

V2 的 AndroidManifest.xml 仍申请 MANAGE_EXTERNAL_STORAGE（写/读外置存储 maxSdkVersion=29），application 设 android:largeHeap="true"；OpenUtauMobile.Android.csproj 设 AndroidMaxHeapSize 2G。

### 2.3 应用包名（applicationId / bundle id）

| 版本 | 包名 | 证据 |
| --- | --- | --- |
| MAUI 1.x（含 1.0.2-1.1.7） | pers.vocoder712.openutaumobile | master/OpenUtauMobile/OpenUtauMobile.csproj ApplicationId |
| V2 canary（dev） | pers.vocoder712.openutaumobile.canary | dev/Directory.Build.props BaseApplicationId |
| V2 Debug 构建 | pers.vocoder712.openutaumobile.canary.debug | dev/OpenUtauMobile.Android/OpenUtauMobile.Android.csproj Debug 块 |

Directory.Build.props（dev）明确写：BaseApplicationId 为 pers.vocoder712.openutaumobile.canary，且注释“全平台应用标识：Android package name / Apple bundle identifier”。
证据：https://github.com/vocoder712/OpenUtauMobile/blob/dev/Directory.Build.props

> 旧版 APK 文件名曾用 pers.vocoder712.openutaumobile-*.apk（1.0.2-1.0.4），与包名一致。
> [未验证]：V2 未来正式（非 canary）版是否会去掉 .canary 后缀。若伴生工具要同时兼容稳定版与 V2，建议运行时枚举多个候选包名/目录。

---

## 3. 关键文件格式约定

### 3.1 声库（Voicebank / Singer）识别

核心常量在 OpenUtau.Core/Classic/VoicebankLoader.cs（MAUI master，dev 同样）：

    public const string kCharTxt = "character.txt";
    public const string kCharYaml = "character.yaml";
    public const string kEnuconfigYaml = "enuconfig.yaml";
    public const string kDsconfigYaml = "dsconfig.yaml";
    public const string kConfigYaml = "config.yaml";
    public const string kOtoIni = "oto.ini";

扫描方式：SearchAll() 默认在声库根目录下递归查找所有 character.txt（Preferences.Default.LoadDeepFolderSinger 默认 true）；关闭时只查声库根目录的直接子目录。也就是说 character.txt 是声库被发现的入口文件。
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Classic/VoicebankLoader.cs

类型判定优先级（LoadInfo）[已验证]：
1. 若同目录存在 character.yaml，读取其 SingerType 字段；
2. 若 SingerType 命中 SingerTypeUtils.SingerTypeFromName 字典（utau / enunu / diffsinger / voicevox），取对应类型；
3. 否则走 legacy detection：存在 enuconfig.yaml -> Enunu；否则存在 dsconfig.yaml -> DiffSinger；否则 -> Classic（UTAU）。
4. character.txt 本身按 shift_jis 解析（可由 character.yaml 的 TextFileEncoding 覆盖），解析 name= / 名前= 等键。

枚举值（OpenUtau.Core/Ustx/USinger.cs）：
[Flags] public enum USingerType { Classic = 0x1, Enunu = 0x2, Vogen = 0x4, DiffSinger = 0x5, Voicevox = 0x6 }
映射：utau->Classic, enunu->Enunu, diffsinger->DiffSinger, voicevox->Voicevox。
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Ustx/USinger.cs

DiffSinger 补充 [已验证]（DiffSingerSinger.cs）：
- 主配置 Location/dsconfig.yaml；缺 languages 字段时会报错（多语言模型需要）；
- 可选：dspitch/dsconfig.yaml（音高预测器）、dsvariance/dsconfig.yaml（方差预测器）、dsvocoder/vocoder.yaml（声码器）；
- 音素表：由 dsconfig.yaml 指向 phonemes/phoneme 文本文件。
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/DiffSinger/DiffSingerSinger.cs

Vogen [已验证]：VogenSingerLoader.cs 在声库目录递归查找 *.vogeon 文件。README 兼容性表列出 DiffSinger / UTAU / Vogen（其它类型“未测试”）。
证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Vogen/VogenSingerLoader.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/README.md

经典 UTAU 声库必备文件：character.txt（入口，含 name=）+ oto.ini（每个音源子目录一份）。oto.ini 解析在 VoicebankLoader.LoadOtoSets（[部分未验证]）。波形通常为 .wav；VoicebankFiles.cs 显示还支持 .llsm / .uspec / .dio / .star / .platinum / .frc / .pmk / .vs4ufrq 等缓存/频率文件，并对非 wav 用 Core.Format.Wave.OpenFile 解码。
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Classic/VoicebankFiles.cs

### 3.2 插件（Legacy UTAU 插件）识别

[已验证] OpenUtau.Core/Classic/PluginLoader.cs：

    Directory.EnumerateFiles(basePath, "plugin.txt", SearchOption.AllDirectories)

- 在 PluginsPath（即 DataPath/Plugins）下递归查找 plugin.txt；
- 解析编码 shift_jis，键包括：name=（支持 Name(&X) 快捷键）、execute=（相对 plugin.txt 所在目录的可执行文件路径，.\ 前缀会被剥掉）、notes=all、shell=use、encoding=；
- name 或 execute 缺失会抛 FileFormatException。

运行方式（OpenUtau.Core/Classic/Plugin.cs）：Process.Start 启动 Executable，Windows 下 .exe/.bat；非 Windows 且配置了 WinePath 时可经 Wine 运行。这在 Android 上基本不可用（无法启动任意外部可执行程序；V2 文档明确 “External UTAU resampler / wavtool programs ... Android: No desktop executable compatibility guarantee”）。
证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Classic/PluginLoader.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Classic/Plugin.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/README.md

> 注意：plugin.txt 是给旧式 UTAU 进程型插件用的；它和 3.3 的“音素器程序集”是两套机制。二者都放在 Plugins/ 下，因此该目录可以同时包含 plugin.txt 插件子目录与 .dll 音素器程序集。

### 3.3 音素器（Phonemizer）识别 ★重点

移动版支持 .dll，但必须是 .NET 托管程序集，不是原生 Windows DLL。

[已验证] OpenUtau.Core/DocManager.cs（MAUI master）SearchAllPlugins() 的逻辑：

    const string kBuiltin = "OpenUtau.Plugin.Builtin.dll";
    files.Add(Path.Combine(Path.GetDirectoryName(AppContext.BaseDirectory), kBuiltin));
    Directory.CreateDirectory(PathManager.Inst.PluginsPath);
    string oldBuiltin = Path.Combine(PathManager.Inst.PluginsPath, kBuiltin);
    if (File.Exists(oldBuiltin)) File.Delete(oldBuiltin);          // 删除旧的落盘内置 DLL
    files.AddRange(Directory.EnumerateFiles(PathManager.Inst.PluginsPath, "*.dll", SearchOption.AllDirectories));
    foreach (var file in files) {
        Assembly assembly;
        try {
            if (!LibraryLoader.IsManagedAssembly(file)) {           // 非托管 DLL 直接跳过
                Log.Information("跳过 " + file + "，因为不是托管程序集");
                continue;
            }
            assembly = Assembly.LoadFile(file);                     // 加载托管 DLL
            foreach (var type in assembly.GetExportedTypes())
                if (!type.IsAbstract && type.IsSubclassOf(typeof(Phonemizer)))
                    phonemizerFactories.Add(PhonemizerFactory.Get(type));
        } catch (Exception e) { Log.Warning(e, "程序集文件 " + file + " 加载失败。"); continue; }
    }
    // 内置音素器（Android 上不是独立 DLL，从当前域加载）
    Assembly assemblyPluginBuiltin = Assembly.Load("OpenUtau.Plugin.Builtin");
    // 当前 Core 程序集里的音素器
    foreach (var type in GetType().Assembly.GetExportedTypes()) ...

要点：
1. 音素器 DLL 的识别 = 遍历 PluginsPath 下所有 *.dll -> 校验为托管程序集 -> Assembly.LoadFile -> 反射出 Phonemizer 的子类。
2. LibraryLoader.IsManagedAssembly(file) 读取 PE 头的 COM Descriptor（CLI header）目录项判断是否托管；原生 UTAU *.dll（C++ 插件）不满足，会被跳过。这直接回答“移动版是否支持桌面 .dll 音素器”：支持托管 .NET 程序集形式，不支持原生桌面 DLL。
3. 内置音素器来自 OpenUtau.Plugin.Builtin 程序集，在 Android 上通过 Assembly.Load("OpenUtau.Plugin.Builtin") 从当前域加载（内置 DLL 会被主动从 Plugins/ 删除，避免重复）；Core 程序集内的音素器也被扫描。
4. 去重：DistinctBy(factory => factory.type)，按 tag 排序。
5. 另有 DocManager.SearchAllLegacyPlugins() 调用 PluginLoader.LoadAll(PluginsPath) 处理 plugin.txt 插件。

PhonemizerFactory 通过 [Phonemizer(name, tag, author, language)] 特性识别（OpenUtau.Core/Api/Phonemizer.cs）；只有带该特性且 name/tag 非空的类型才会成为可选音素器。PhonemizerFactory.GetAll() 返回可用列表，弹窗按 language 分组显示。

证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/DocManager.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Util/LibraryLoader.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Api/PhonemizerFactory.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Api/Phonemizer.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtau.Core/Api/PhonemizerInstaller.cs

V2 的变化 [已验证]：OpenUtauMobile/Services/PluginManagementService.cs（dev）明确把插件管理定义为托管程序集（.dll）管理：
- ListFiles() 只枚举 PluginsPath 下 .dll；
- InstallAsync() 校验扩展名为 .dll、拒绝覆盖内置 OpenUtau.Plugin.Builtin.dll、用 PEReader 校验 HasMetadata && CorHeader != null && metadata.IsAssembly（即必须是 .NET 程序集）；
- 删除采用“下次启动前统一删除”的 pending deletion 机制（Preferences.PendingPluginDeletions）；
- PluginLoadErrorReporter.cs 监听上游日志模板 "Failed to load {File}." / "Failed to search plugins."（上游 Core 中即存在这些日志，进一步佐证加载路径）。
证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtauMobile/Services/PluginManagementService.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtauMobile/Services/PluginLoadErrorReporter.cs

内置音素器清单（用于“内置哪些”的参考）[已验证，来自文件列表]：
OpenUtau.Plugin.Builtin 下含大量 .cs 音素器，例如：
JapaneseVCV / JapaneseCVVC / JapanesePresampSample / Arpasing / ArpasingPlus / EnXSampa / EnglishVCCV / EnglishCpV / ChineseCVV / ChineseCVVC / ChineseCVVPlus / CantoneseCVVC / CantoneseSyo / KoreanCV / KoreanCVC / KoreanVCV / KoreanCVCCV / KoreanCVVCStandardPronunciation / SpanishVCCV / SpanishSyllableBased / SpanishMakkusan / FrenchVCCV / FrenchCVVC / FrenchCMUSphinx / GermanVCCV / GermanDiphone / ItalianCVVC / ItalianSyllableBased / BrazilianPortugueseCVC / RussianVCCV / RussianCVC / PolishCVC / TurkishCVVC / ThaiVCCV / VietnameseVCV / VietnameseCVVC / VietnameseVINA / Filipino / LatinDiphone / Monophone / SyllableBased / ENtoJA / EStoJA / FILtoJA / KOtoJA / PresampSample / SimpleVoicevoxENtoJA 等。
此外 OpenUtau.Core 内含 DiffSinger/Phonemizers/*、Enunu/*、Vogen/*、Voicevox/* 音素器（V2 还新增 Neutrino、若干 DiffSinger 语言音素器）。
证据（文件列表）：
- https://github.com/vocoder712/OpenUtauMobile/tree/master/OpenUtau.Plugin.Builtin
- https://github.com/vocoder712/OpenUtauMobile/tree/master/OpenUtau.Core/DiffSinger/Phonemizers
- https://github.com/vocoder712/OpenUtauMobile/tree/dev/OpenUtau.Plugin.Builtin

结论（第 3 条核心）：
- 移动版不支持桌面版那种原生 .dll（C++）音素器；
- 支持托管 .NET 程序集 .dll（放在 Plugins/，实现 OpenUtau.Api.Phonemizer 抽象类并标注 [Phonemizer(...)]）——这正是“桌面版插件怎么移植到移动版”的方式；
- 内置音素器以内嵌程序集提供，不依赖用户放置文件；
- 旧式 plugin.txt 进程插件在移动端因无法启动外部进程而基本不可用。
- [未验证]：官方是否提供“从 zip 一键安装音素器”的 UI（issue #378/#379 有“添加 DLL 插件管理功能”记录，PluginManagementService 已支持 .dll 安装但似乎是逐个文件选择，未确认 zip 打包安装）。

---

## 4. UI 主题 / 配色

### 4.1 MAUI 版（master，1.x）

[已验证] 不是 Flutter；是 .NET MAUI + XAML。主题色有两处定义：

1. C# / SkiaSharp 主题类：OpenUtauMobile/Utils/ThemeColors.cs
   - 抽象基类 ThemeColors + 具体类 LightThemeColors、DarkThemeColors。
   - 主色（两套主题相同）：Primary = SKColor.Parse("#fe71a3") —— 主色 hex = #fe71a3（粉红）。
   - 浅色：WhitePianoKey=#FFFFFF、BlackPianoKey=#fe71a3、TrackBackground=#ffffff、ActiveNoteEditModeButton=#fe71a3 等。
   - 深色：WhitePianoKey=#CC2A63、BlackPianoKey=#00000000（透明）、TrackBackground=#2C2C2C、ActiveNoteEditModeButton=#FF4081、Primary=#fe71a3 等。
   - ThemeColorsManager.cs 用静态构造按 App.Current.RequestedTheme 选择 DarkThemeColors / LightThemeColors。

2. XAML 资源字典：
   - OpenUtauMobile/Resources/Colors/DarkThemeColors.xaml
   - OpenUtauMobile/Resources/Colors/LightThemeColors.xaml
   - 含 #303030（深色分隔/弹窗/标题栏背景）、#202020、#505050、SnapDivOptionCardBackground=#ff4081（深色）等。
   - App.xaml.cs 根据 Preferences.Default.Theme（0=浅色 / 1=深色 / 2=跟随系统）合并对应字典并设置 UserAppTheme。

证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/Utils/ThemeColors.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/Resources/Colors/DarkThemeColors.xaml
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/Resources/Colors/LightThemeColors.xaml
- https://github.com/vocoder712/OpenUtauMobile/blob/master/OpenUtauMobile/App.xaml.cs

注意：仓库根 openutau-upstream/ 里的 OpenUtau_Colors_*Theme.axaml、brushes.axaml、styles.axaml 属于桌面板（Avalonia），不是移动版 MAUI 主题。移动版主题请看上面的 ThemeColors.cs 与 Resources/Colors/*.xaml。

### 4.2 V2 版（dev，Avalonia）

[已验证] V2 改为 Material 3 动态取色（MaterialColorUtilities）：

- OpenUtauMobile/Themes/OpenUtauMobile/Runtime/Generation/ThemeGenerator.cs：用 MaterialColorUtilities.Palettes.CorePalette.Of(seed) 从种子色生成整包 Scheme，再映射为语义色 Sem.Color.Primary / OnPrimary / PrimaryContainer / Surface / ...，并按 ThemeVariant.Dark/Light 选择 DarkSchemeMapper / LightSchemeMapper。
- 种子色来源：OpenUtauMobile/Themes/OpenUtauMobile/Runtime/Platform/SystemAccentColorProvider.cs
  - ThemeColorMode.FollowSystem（=0，默认）：优先取系统强调色（Avalonia PlatformSettings 的 Accent/AccentColor/SystemAccentColor/...，Android 回退到 Android.Resource.Attribute.ColorAccent）；取不到则用用户自定义色；再取不到默认 #FF0000。
  - ThemeColorMode.Custom（=1）：使用 Preferences.Default.ThemeColorSeedHex（必须为 6 位 hex）。
  - 内置种子色预设 ThemeSeedPresets.All：Tianyi #66CCFF、Rose Red #D32F2F、Coral #FF6F61、Amber #FFB300、Lime #8BC34A、Forest #2E7D32、Jade #009688、Sky #03A9F4、Cobalt #3F51B5、Violet #7E57C2、Magenta #D81B60、Slate #546E7A、Graphite #455A64。
- 旧的 BaseColorsPalette.axaml（微软 WinUI 风格 System*Color）仍在主题目录中，但主色体系已换成 Sem.* + 运行时生成。
- OpenUtauMobile/Themes/OpenUtauMobile/COLOR_ROLES.json 描述各控件资源到语义角色的映射（如 SecondaryContainer、SurfaceContainerHighest、OnSecondaryContainer）。
- Android 启动画面底色：浅色 #FFFFFF、深色 #212121（OpenUtauMobile.Android/Resources/values/colors.xml 与 values-night/colors.xml）。

证据：
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtauMobile/Themes/OpenUtauMobile/Runtime/Generation/ThemeGenerator.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtauMobile/Themes/OpenUtauMobile/Runtime/Platform/SystemAccentColorProvider.cs
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtauMobile/Themes/OpenUtauMobile/COLOR_ROLES.json
- https://github.com/vocoder712/OpenUtauMobile/blob/dev/OpenUtauMobile.Android/Resources/values/colors.xml

> 给伴生工具的配色建议：如果对齐已发布稳定版（MAUI 1.1.7），主色用 #fe71a3，深色背景 #2C2C2C/#303030，强调按钮 #FF4081（深色）。如果对齐 V2，则没有固定主色，应实现 M3 动态取色或提供上表种子色。

---

## 5. OpenUtau 桌面版许可与版权

[已验证] 桌面版仓库为 openutau/OpenUtau（stakira/OpenUtau 现已 301 重定向到此）。许可为 MIT（GitHub license API 返回 spdx_id = MIT）。

LICENSE.txt 原文首行：

    The MIT License (MIT)
    Copyright (c) 2014 StAkira

即 版权声明原文 = “Copyright (c) 2014 StAkira”。
证据：
- https://github.com/openutau/OpenUtau/blob/master/LICENSE.txt
- https://github.com/stakira/OpenUtau （301 -> openutau/OpenUtau）

补充：OpenUtau Mobile 自身使用 Apache-2.0 许可（LICENSE.txt 为 Apache License 2.0），README 注明 “This project is open-source under the Apache 2.0 license. This is NOT the official OpenUtau application and must not impersonate the official OpenUtau.”
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/LICENSE.txt

> 伴生工具若复制/改编 OpenUtau 桌面版代码，需保留 MIT 的 Copyright (c) 2014 StAkira 声明；若仅调用/管理移动版数据，则主要受移动版 Apache-2.0 约束（并注意“不得冒充官方”）。

---

## 6. 稳定性问题（内存、崩溃、官方提醒）

[已验证] 官方 README（master）有明确警告原文：

> “Note: The software is currently very unstable. Due to limitations of the development framework, memory management issues may occur frequently. Therefore, remember to save often. If the app crashes, you can find a recovery file ending in .autosave.ustx in the same directory as your project file.”

即：官方亲自提醒“非常不稳定、内存管理问题频繁、务必频繁保存”，并说明崩溃后可在工程文件同目录找到 .autosave.ustx 恢复文件。
证据：https://github.com/vocoder712/OpenUtauMobile/blob/master/README.md

相关机制与证据：
- DocManager.Initialize() 注册 AppDomain.CurrentDomain.UnhandledException -> CrashSave()（崩溃自动保存）。证据：OpenUtau.Core/DocManager.cs。
- PathManager.BackupsPath = DataPath/Backups（备份目录）。证据：OpenUtau.Core/Util/PathManager.cs。
- V2 针对性能/内存做了重写（README dev 首句：“To solve the performance issues, outdated UI design, and cross-platform limitations of the first-generation implementation, the project is being rebuilt from scratch.”）。
- V2 Android 端显式调大内存：AndroidMaxHeapSize=2G、manifest android:largeHeap="true"；并有 AndroidCrashLogService 收集上次退出日志（MainActivity.cs 调用 crashLogService.CollectPreviousExitAsync()）。
- 存在多个崩溃修复类 issue/PR，例如：
  - #384 “fix: 可能修复 Android 平台上从后台切回时丢失原有绘制表面且找不到新的表面导致黑屏”
  - #360 “fix: 修复渲染波形任务冲突引发的崩溃”
  - #332 “fix: 修复 iOS 启动崩溃”
  - #321 “fix: 修复上游 onnx 错误导致在 Android 上嵌入 GNU onnx 原生库的错误”
  - #354 “[Bug]: V2 依赖项无法正常安装”
  - #379 “feature: 添加 DLL 插件管理功能（音素器等）”
  证据：https://github.com/vocoder712/OpenUtauMobile/issues

[未验证] 未找到一份官方“已知内存占用上限/推荐设备内存”的定量说明；README 仅做定性警告。也未逐条打开所有 issue 正文核对（GitHub API 在本会话后段触发速率限制）。

---

## 7. 对伴生工具（Kotlin + Compose）的落地建议（基于以上证据）

1. 包名/路径探测：优先识别两个包名 pers.vocoder712.openutaumobile（MAUI 稳定版）与 pers.vocoder712.openutaumobile.canary（V2），数据根均为 /data/user/0/<pkg>/files/。由于是私有目录，除 root/ADB 外第三方 App 无法直读 —— [需与产品方确认] 是通过 root/Shizuku/SAF 接入，还是引导用户把声库放到“额外歌手路径”（共享存储）。
2. 声库管理：以 Singers/（及旧 Content/Singers、用户额外路径）为根，递归找 character.txt 判定声库；用 character.yaml.SingerType（utau/diffsinger/vogen/enunu/voicevox）或回退规则（enuconfig.yaml->Enunu，dsconfig.yaml->DiffSinger，否则 Classic）识别类型。
3. 插件/音素器管理：Plugins/ 目录：
   - 音素器 = 托管 .dll（.NET 程序集，含 OpenUtau.Api.Phonemizer 子类 + [Phonemizer] 特性）；安装前可用 PE 头校验是否 .NET 程序集（与 V2 PluginManagementService.ValidateAssembly 一致），避免把原生 DLL 当音素器。
   - 旧式 plugin.txt 进程插件在移动端基本不可用，可在 UI 中标注“不支持”。
   - 禁止/提示不要覆盖内置 OpenUtau.Plugin.Builtin.dll。
4. 主题：稳定版对齐 #fe71a3（深色 #FF4081 强调、背景 #2C2C2C）；V2 采用 M3 动态取色 + 上述 13 个种子色预设。

---

## 附：主要证据文件清单

| 主题 | 仓库文件 |
| --- | --- |
| 框架/版本 | master: OpenUtauMobile/OpenUtauMobile.csproj、MauiProgram.cs；dev: Directory.Build.props、OpenUtauMobile.Android/OpenUtauMobile.Android.csproj、README.md |
| 路径 | master: OpenUtau.Core/Util/PathManager.cs、OpenUtauMobile/Platforms/Android/...；dev: OpenUtau.Core/Util/PathManager.cs、OpenUtauMobile.Android/MainActivity.cs |
| 包名 | master: OpenUtauMobile/OpenUtauMobile.csproj；dev: Directory.Build.props |
| 声库 | OpenUtau.Core/Classic/VoicebankLoader.cs、VoicebankConfig.cs、OpenUtau.Core/Ustx/USinger.cs、OpenUtau.Core/DiffSinger/DiffSingerSinger.cs、OpenUtau.Core/Vogen/VogenSingerLoader.cs |
| 插件 | OpenUtau.Core/Classic/PluginLoader.cs、OpenUtau.Core/Classic/Plugin.cs |
| 音素器 | OpenUtau.Core/DocManager.cs、OpenUtau.Core/Api/PhonemizerFactory.cs、Phonemizer.cs、PhonemizerInstaller.cs、OpenUtau.Core/Util/LibraryLoader.cs；dev: OpenUtauMobile/Services/PluginManagementService.cs |
| 主题 | master: OpenUtauMobile/Utils/ThemeColors.cs、OpenUtauMobile/Resources/Colors/*.xaml、OpenUtauMobile/App.xaml.cs；dev: OpenUtauMobile/Themes/OpenUtauMobile/Runtime/...、SystemAccentColorProvider.cs |
| 许可 | openutau/OpenUtau:LICENSE.txt；vocoder712/OpenUtauMobile:LICENSE.txt |
| 稳定性 | vocoder712/OpenUtauMobile:README.md、Issues #384/#360/#354/#379 等 |

所有 GitHub 源码链接均以 blob/<branch>/（网页视图）或 raw.githubusercontent.com/<repo>/<branch>/<path> 读取；本次未使用本地完整克隆（codeload 下载较慢，已改用 API+raw 直读关键文件）。
