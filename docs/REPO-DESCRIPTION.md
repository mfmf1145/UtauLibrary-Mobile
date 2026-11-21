# 仓库描述（GitHub 用）

## 一、About 栏（一句简介，350 字符内）

**中文（推荐）**

```
UtauLibrary Mobile —— 面向 OpenUtau Mobile 的声库 / 插件 / 音素器管理器和下载器。收录 997 个声库，多连接下载、断点续传、自动解压归位、安装后校验。独立 APK，不修改 OpenUtau 本体。
```

**English**

```
UtauLibrary Mobile — a voicebank, plugin and phonemizer manager and downloader for OpenUtau Mobile. 997 voicebanks, chunked downloads with resume, automatic extraction and placement, post-install verification. A standalone APK that never modifies OpenUtau itself.
```

**更短（140 字符内，适合列表页）**

```
OpenUtau Mobile 的声库管理器和下载器：997 个声库，一键下载、自动解压、安装校验。独立 APK。
```

---

## 二、Topics（仓库话题，最多 20 个）

```
openutau
utau
voicebank
vocaloid
singing-synthesis
android
kotlin
jetpack-compose
downloader
voicebank-manager
phonemizer
diff-singer
android-app
material-you
resume-download
apk
chinese
japanese
mit-license
```

---

## 三、Repo 详情页「Description」长版

> 适合放在 README 顶部，或作为 Release 的说明。

**UtauLibrary Mobile** 是 **OpenUtau Mobile** 的配套工具：负责找资源、下资源、装资源、查问题。它不替代 OpenUtau 的创作功能，也不修改 OpenUtau 的任何代码。

收录 997 个声库、71 个插件与音素器、8 个分支改版。支持按语言、引擎、录音方式、来源、体积、网络状态组合筛选，罗马音搜索（输入 `teto` 可直接命中「重音テト」）。

下载端做了多连接分块、断点续传与失败自动重试；服务器不支持 Range 时自动回退单连接。下载结束后校验字节数与压缩包条目数，解压写入后再逐项比对文件数与总字节，避免出现「提示装好、实际残缺」。

安装端自动识别声库 / 插件 / 音素器 / DiffSinger 的目录结构并归位，兼容嵌套压缩包与多层目录。已安装页分四类展示，给出真实体积、文件数与体检结果，支持清理残留。

界面沿用 OpenUtau 的视觉语言：0 圆角、1px 描边、扁平、高密度；内置 10 套取自 OpenUtau 原版主题的配色，支持深色与浅色。

技术栈：Kotlin + Jetpack Compose，纯 JVM 解压内核，无 native 依赖；文件访问走 SAF，只申请一个根目录权限；最低 Android 5.0。

---

## 四、关键词（便于被搜到）

声库管理、音源管理、OpenUtau、UTAU、DiffSinger、音素器、插件下载器、断点续传、APK、安卓、Kotlin、Jetpack Compose、开源

voicebank manager, utau voicebank, openutau mobile, phonemizer, downloader, android, kotlin, compose, open source

---

## 五、Release 标题建议

```
v1.0.0 — 首个正式版
```

Release 正文：

```markdown
首个正式版。修复了下载与安装链路的可靠性问题：
下载后校验字节数与压缩包条目数、写入后逐项比对、安装完成立即刷新列表。

- APK：UtauLibrary-Mobile-1.0.0.apk（9.6 MB）
- 最低系统：Android 5.0 (API 21)
- 完整变更见 CHANGELOG.md
```
