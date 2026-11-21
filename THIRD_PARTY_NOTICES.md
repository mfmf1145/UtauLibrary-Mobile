# 第三方组件与版权声明

UtauMarket Mobile 自身以 MIT 许可发布。以下组件/资源的版权归原作者所有，本程序按其许可使用。

## 1. openutau/OpenUtau（MIT）

- 仓库：https://github.com/openutau/OpenUtau （原 https://github.com/stakira/OpenUtau）
- 原文版权：`Copyright (c) 2014 StAkira`
- 使用方式：**仅提取 UI 主题资源的具体数值**（颜色、字号、控件高度、内边距、圆角、状态画刷映射），
  在 Kotlin/Compose 中重新实现，未复制其 C# 源码。
- 涉及文件（原件见 `docs/upstream/`）：
  - `OpenUtau/Colors/DarkTheme.axaml`
  - `OpenUtau/Colors/LightTheme.axaml`
  - `OpenUtau/Colors/Brushes.axaml`
  - `OpenUtau/Styles/Styles.axaml`
  - `OpenUtau/App.axaml`

## 2. microsoft/fluentui-system-icons（MIT）

- 仓库：https://github.com/microsoft/fluentui-system-icons
- 原文版权：`Copyright (c) Microsoft Corporation`
- 使用方式：矢量图标（regular / 24px）转换为 Android VectorDrawable，
  生成于 `app/src/main/res/drawable/ou_ic_*.xml`，可用 `tools/generate_icons.py` 复现。
- OpenUtau 使用 Avalonia FluentTheme，其界面内联图标同属 Fluent 图标语言，因此图标风格与原版一致。

## 3. Apache Commons Compress（Apache-2.0）

- https://commons.apache.org/proper/commons-compress/ —— zip / 7z / tar / gzip 解压。

## 4. junrar（UnRAR restriction）

- https://github.com/junrar/junrar —— RAR 解压。
- 注意：junrar 的许可源自 UnRAR 的 "unRAR restriction"，**不得用于开发 RAR 兼容的压缩程序**，但可用于解压。
  本程序只做解压，请在使用/分发前自行确认许可适用性。

## 5. XZ for Java（Public Domain）

- https://tukaani.org/xz/java.html —— 7z 的 LZMA/LZMA2 解码。

## 6. Apache Commons IO（Apache-2.0）

- https://commons.apache.org/proper/commons-io/

## 7. OkHttp（Apache-2.0）

- https://square.github.io/okhttp/

## 8. AndroidX / Jetpack Compose（Apache-2.0）

- https://developer.android.com/jetpack/androidx

## 9. 内置资源清单中的声库 / 插件

`app/src/main/assets/resource_manifest.json` 中的每一条都链向第三方作者的发布页，
版权与使用条款归各自作者（如 Kasane Teto / 東北きりたん 等角色的授权条款）。
本程序只提供链接与自动化安装工具，**不重新分发**这些资源，也不对其授权做任何解释或背书。
使用者需自行遵守各资源的使用条款（是否允许商用、是否允许修改等）。
