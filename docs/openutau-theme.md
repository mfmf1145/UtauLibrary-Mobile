# OpenUtau 原版 UI 主题与控件样式精确数值

> 目的：供 Kotlin + Jetpack Compose 安卓 App 1:1 复刻 OpenUtau 视觉风格。
> 所有数值均从仓库文件中实际读取，未做任何猜测。每个数值均给出**仓库内相对路径 + 行号**。

## 0. 来源与版本

| 项目 | 值 |
| --- | --- |
| 仓库 | `stakira/OpenUtau`（GitHub 已重定向到 `openutau/OpenUtau`） |
| 分支 | `master` |
| **commit** | `5c3c83bbeced96718e85e4a96d127de3287cd14b` |
| commit 日期 | 2026-10-09 07:24:42 UTC |
| License | **MIT** |
| UI 框架 | [Avalonia UI](https://avaloniaui.net/)（XAML），基础主题 `FluentTheme` |
| 获取方式 | `raw.githubusercontent.com` + `api.github.com`（本机 `git clone` 过慢，改用 API/raw 逐文件抓取） |

⚠️ **Compose 复刻要点**：OpenUtau 的 `Styles/Styles.axaml` 覆盖了 Fluent 主题的部分控件，其余控件（TabControl、默认 ScrollBar、默认 Slider 等）使用 Avalonia FluentTheme 的默认外观。本文中标注了哪些样式是 OpenUtau 显式定义的。

## 1. 主题文件都在哪里

| 文件 | 作用 |
| --- | --- |
| `OpenUtau/Colors/DarkTheme.axaml` | **深色**色板（`<Color>` 资源字典） |
| `OpenUtau/Colors/LightTheme.axaml` | **浅色**色板 |
| `OpenUtau/Colors/CustomTheme.axaml` | 内置默认自定义主题（内容≈浅色，但**缺少 WarningColor**） |
| `OpenUtau/Colors/CustomTheme.axaml.cs` | 运行时按用户 YAML 主题覆写资源；`ThemeYaml` 定义全部可配色字段 |
| `OpenUtau/Colors/Brushes.axaml` | 把 `Color` 键映射为 `SolidColorBrush/LinearGradientBrush` 语义画刷（所有控件通过它取色） |
| `OpenUtau/Styles/Styles.axaml` | 全局控件样式：圆角、内边距、外边距、边框、字号、模板 |
| `OpenUtau/Styles/PianoRollStyles.axaml` | 钢琴卷帘专用控件样式（工具按钮、RadioButton、Slider、ToggleSwitch、TextBox 聚焦） |
| `OpenUtau/App.axaml` | 资源字典与样式的装配入口 |
| `OpenUtau/ThemeManager.cs` | 主题加载、轨道配色表 `TrackColors`、键盘色逻辑 |
| `OpenUtau/ThemeLoader.cs` | 从 Oudep 包加载 YAML 主题 |
| `OpenUtau/Strings/Strings.axaml` | 字体族资源 `ui.fontfamily`（`L1026`） |

**主题切换机制**（`OpenUtau/App.axaml#L29-L34`）：`themes-dark` / `themes-light` / `themes-custom` 三个 ResourceInclude 加上 merged 的 `Brushes.axaml`。运行时先把选定主题的 `Color` 值放进 `Application.Resources`，`Brushes.axaml` 里的画刷用 `{DynamicResource <ColorKey>}` 引用，因此切换主题会即时更新全部画刷。

---

## 2. 深色主题（DarkTheme.axaml）

来源：`OpenUtau/Colors/DarkTheme.axaml`。`IsDarkMode = true`（L4）。

| 资源键 | Hex | 行号 | 用途 |
| --- | --- | --- | --- |
| BackgroundColor | `#26232C` | L6 | 主背景 |
| BackgroundColorPointerOver | `#36313F` | L7 | 背景-悬停 |
| BackgroundColorPressed | `#484255` | L8 | 背景-按下 |
| BackgroundColorDisabled | `#2C2933` | L9 | 背景-禁用 |
| ForegroundColor | `#CDCAD4` | L11 | 主前景/文字 |
| ForegroundColorPointerOver | `#E9E6F0` | L12 | 前景-悬停 |
| ForegroundColorPressed | `#FFFFFF` | L13 | 前景-按下 |
| ForegroundColorDisabled | `#7E778C` | L14 | 前景-禁用 |
| BorderColor | `#544E60` | L16 | 边框 |
| BorderColorPointerOver | `#878096` | L17 | 边框-悬停 |
| **SystemAccentColor** | `#4EA6EA` | L19 | 系统强调色（蓝） |
| SystemAccentColorLight1 | `#90CAF9` | L21 | 强调色-悬停 |
| SystemAccentColorDark1 | `#1E88E5` | L23 | 强调色-按下 |
| NeutralAccentColor | `#878096` | L25 | 中性强调色 |
| NeutralAccentColorPointerOver | `#B0ABBC` | L26 | 中性强调-悬停 |
| AccentColor1 | `#4EA6EA` | L27 | 强调色1（蓝） |
| AccentColor2 | `#FF679D` | L28 | 强调色2（粉） |
| AccentColor3 | `#E62E6E` | L29 | 强调色3（玫红） |
| TickLineColor | `#615B73` | L31 | 时间刻度线 |
| BarNumberColor | `#B0ABBC` | L32 | 小节号文字 |
| FinalPitchColor | `#C0BACD` | L33 | 最终音高曲线 |
| TrackBackgroundAltColor | `#302C38` | L34 | 轨道交替背景 |
| **WarningColor** | `#433519` | L36 | 警告（深色下为暗黄底色） |
| WhiteKeyColorLeft | `#CC527C` | L39 | 白键渐变左 |
| WhiteKeyColorRight | `#FF4D8B` | L40 | 白键渐变右 |
| WhiteKeyNameColor | `#FFFFFF` | L41 | 白键音名 |
| CenterKeyColorLeft | `#CCA5B0` | L43 | 中央 C 键渐变左 |
| CenterKeyColorRight | `#FFCEDC` | L44 | 中央 C 键渐变右 |
| CenterKeyNameColor | `#FF4D8B` | L45 | 中央 C 键音名 |
| BlackKeyColorLeft | `Transparent` | L47 | 黑键渐变左（深色下透明） |
| BlackKeyColorRight | `Transparent` | L48 | 黑键渐变右 |
| BlackKeyNameColor | `#CDCAD4` | L49 | 黑键音名 |

## 3. 浅色主题（LightTheme.axaml）

来源：`OpenUtau/Colors/LightTheme.axaml`。`IsDarkMode = false`（L4）。

| 资源键 | Hex | 行号 | 用途 |
| --- | --- | --- | --- |
| BackgroundColor | `#FFFFFF` | L6 | 主背景 |
| BackgroundColorPointerOver | `#F0F0F0` | L7 | 背景-悬停 |
| BackgroundColorPressed | `#E0E0E0` | L8 | 背景-按下 |
| BackgroundColorDisabled | `#D0D0D0` | L9 | 背景-禁用 |
| ForegroundColor | `#000000` | L11 | 主前景/文字 |
| ForegroundColorPointerOver | `#000000` | L12 | 前景-悬停 |
| ForegroundColorPressed | `#202020` | L13 | 前景-按下 |
| ForegroundColorDisabled | `#808080` | L14 | 前景-禁用 |
| BorderColor | `#707070` | L16 | 边框 |
| BorderColorPointerOver | `#B0B0B0` | L17 | 边框-悬停 |
| **SystemAccentColor** | `#4EA6EA` | L19 | 系统强调色（蓝，两主题一致） |
| SystemAccentColorLight1 | `#90CAF9` | L21 | 强调色-悬停 |
| SystemAccentColorDark1 | `#1E88E5` | L23 | 强调色-按下 |
| NeutralAccentColor | `#ADA1B3` | L25 | 中性强调色 |
| NeutralAccentColorPointerOver | `#948A99` | L26 | 中性强调-悬停 |
| AccentColor1 | `#4EA6EA` | L27 | 强调色1 |
| AccentColor2 | `#FF679D` | L28 | 强调色2 |
| AccentColor3 | `#E62E6E` | L29 | 强调色3 |
| TickLineColor | `#AFA3B5` | L31 | 时间刻度线 |
| BarNumberColor | `#AFA3B5` | L32 | 小节号文字 |
| FinalPitchColor | `#C0C0C0` | L33 | 最终音高曲线 |
| TrackBackgroundAltColor | `#F0F0F0` | L34 | 轨道交替背景 |
| **WarningColor** | `#FFF4CE` | L36 | 警告（浅色下为浅黄底色） |
| WhiteKeyColorLeft | `Transparent` | L39 | 白键渐变左 |
| WhiteKeyColorRight | `Transparent` | L40 | 白键渐变右 |
| WhiteKeyNameColor | `#FF347c` | L41 | 白键音名（原文小写 c） |
| CenterKeyColorLeft | `#FFDDE6` | L43 | 中央 C 键渐变左 |
| CenterKeyColorRight | `#FFCEDC` | L44 | 中央 C 键渐变右 |
| CenterKeyNameColor | `#FF347C` | L45 | 中央 C 键音名 |
| BlackKeyColorLeft | `#FF71A3` | L47 | 黑键渐变左 |
| BlackKeyColorRight | `#FF347C` | L48 | 黑键渐变右 |
| BlackKeyNameColor | `#FFFFFF` | L49 | 黑键音名 |

> **CustomTheme.axaml**（`OpenUtau/Colors/CustomTheme.axaml#L6-L47`）与浅色几乎一致，唯一差别是 **没有 `WarningColor`**。`CustomTheme.ThemeYaml` 的默认字段（`CustomTheme.axaml.cs#L123-L167`）也同样是这套值，且列表里同样没有 Warning。

---

## 4. 语义画刷映射（Brushes.axaml）

来源：`OpenUtau/Colors/Brushes.axaml`。Compose 中建议直接实现这些"语义色"，按主题取对应的 `Color`。

### 4.1 基础映射（`Color` → 画刷）

| 画刷键 | 引用颜色 | Opacity | 行号 |
| --- | --- | --- | --- |
| SystemRegionBrush | BackgroundColor | 1 | L3 |
| SystemControlBackgroundAltHighBrush | BackgroundColor | 1 | L5 |
| BackgroundBrushSemi | BackgroundColor | 0.5 | L7 |
| SystemControlBackgroundAltHighBrushPointerOver | BackgroundColorPointerOver | 1 | L9 |
| SystemControlBackgroundAltHighBrushDisabled | BackgroundColorDisabled | 1 | L11 |
| SystemControlBackgroundBaseLowBrush | BackgroundColorPointerOver | 1 | L13 |
| SystemControlForegroundBaseHighBrush | ForegroundColor | 1 | L15 |
| SystemControlForegroundBaseHighBrushPointerOver | ForegroundColorPointerOver | 1 | L17 |
| SystemControlForegroundBaseHighBrushDisabled | ForegroundColorDisabled | 1 | L19 |
| SystemControlForegroundBaseMediumBrush | BorderColor | 1 | L21 |
| SystemControlHighlightBaseMediumHighBrush | BorderColorPointerOver | 1 | L23 |
| SystemControlDisabledBaseMediumLowBrush | ForegroundColorDisabled | 1 | L25 |
| SystemControlHighlightAltBaseHighBrush | ForegroundColorPointerOver | 1 | L27 |
| SystemControlHighlightBaseHighBrush | ForegroundColorPointerOver | 1 | L29 |
| MenuFlyoutPresenterBackground | BackgroundColor | 1 | L31 |
| MenuFlyoutItemBackground | BackgroundColor | 1 | L33 |
| MenuFlyoutItemBackgroundPointerOver | BackgroundColorPointerOver | 1 | L35 |
| MenuFlyoutItemBackgroundDisabled | BackgroundColorDisabled | 1 | L37 |
| MenuFlyoutItemForeground | ForegroundColor | 1 | L39 |
| MenuFlyoutItemForegroundPointerOver | ForegroundColorPointerOver | 1 | L41 |
| MenuFlyoutItemForegroundDisabled | ForegroundColorDisabled | 1 | L43 |
| SystemControlBackgroundAltMediumLowBrush（combo 背景） | BackgroundColorPointerOver | 1 | L46 |
| SystemControlPageBackgroundAltMediumBrush（combo 悬停） | BackgroundColorPressed | 1 | L49 |
| SystemControlTransientBackgroundBrush（combo flyout 背景） | BackgroundColorPointerOver | 1 | L52 |
| SystemControlHighlightListLowBrush（combo 高亮 overlay） | ForegroundColorPressed | 0.25 | L55 |
| SystemControlDisabledChromeDisabledLowBrush | ForegroundColorDisabled | 1 | L58 |
| TextControlBackgroundDisabled | BackgroundColorDisabled | 1 | L61 |
| SystemControlBackgroundChromeMediumLowBrush | BackgroundColorPointerOver | 1 | L63 |
| DataGridColumnHeaderBackgroundBrush | BackgroundColorPointerOver | 1 | L65 |
| DataGridColumnHeaderForegroundBrush | ForegroundColor | 1 | L67 |
| DataGridScrollBarsSeparatorBackground | BackgroundColor | 1 | L69 |
| ButtonBackgroundPointerOver | ForegroundColorPressed | 0.25 | L72 |
| ButtonBackgroundDisabled | BackgroundColorDisabled | 1 | L74 |
| ButtonForeground | ForegroundColor | 1 | L76 |
| ButtonForegroundPointerOver | ForegroundColorPointerOver | 1 | L78 |
| ToggleButtonForeground | ForegroundColor | 1 | L80 |
| ToggleButtonForegroundPointerOver | ForegroundColorPointerOver | 1 | L82 |
| ComboBoxForeground | ForegroundColor | 1 | L84 |
| ComboBoxBorderBrush | BorderColor | 1 | L86 |
| ComboBoxBorderBrushPointerOver | BorderColorPointerOver | 1 | L88 |
| ComboBoxItemForeground | ForegroundColor | 1 | L90 |
| ComboBoxItemForegroundPointerOver | ForegroundColorPointerOver | 1 | L92 |
| ToggleSwitchContentForeground | ForegroundColor | 1 | L94 |
| ToggleSwitchStrokeOff | ForegroundColor | 1 | L96 |
| ToggleSwitchStrokeOffPointerOver | ForegroundColorPointerOver | 1 | L98 |
| ToggleSwitchKnobFillOff | ForegroundColor | 1 | L100 |
| ToggleSwitchKnobFillOffPointerOver | ForegroundColorPointerOver | 1 | L102 |
| MenuFlyoutPresenterBorderBrush | BorderColor | 1 | L104 |
| TickLineBrush | TickLineColor | 1 | L107 |
| TickLineBrushLow | TickLineColor | 0.5 | L109 |
| BarNumberBrush | BarNumberColor | 1 | L111 |
| FinalPitchBrush | FinalPitchColor | 1 | L113 |
| RealCurveStrokeBrush | AccentColor1 | 0.25 | L115 |
| RealCurveFillBrush（LinearGradientBrush） | AccentColor1 → Transparent | 0.25 | L117-L120 |
| TrackBackgroundAltBrush | TrackBackgroundAltColor | 1 | L121 |
| NeutralAccentBrush | NeutralAccentColor | 1 | L123 |
| NeutralAccentBrushSemi | NeutralAccentColor | 0.5 | L125 |
| NeutralAccentBrushSemi2 | NeutralAccentColor | 0.25 | L127 |
| NeutralAccentBrushPointerOver | NeutralAccentColorPointerOver | 1 | L129 |
| AccentBrush1 | AccentColor1 | 1 | L131 |
| AccentBrush1Semi | AccentColor1 | 0.5 | L133 |
| AccentBrush2 | AccentColor2 | 1 | L135 |
| AccentBrush2Semi | AccentColor2 | 0.5 | L137 |
| AccentBrush3 | AccentColor3 | 1 | L139 |
| AccentBrush3Semi | AccentColor3 | 0.5 | L141 |
| WarningBrush | WarningColor | 1 | L153 |

### 4.2 与主题无关的硬编码画刷（`Brushes.axaml`）

| 画刷键 | 固定颜色 | Opacity | 行号 |
| --- | --- | --- | --- |
| SelectedTrackAccentBrush | `#4EA6EA` | 1 | L143 |
| SelectedTrackAccentLightBrush | `#90CAF9` | 1 | L145 |
| SelectedTrackAccentLightBrushSemi | `#90CAF9` | 0.5 | L147 |
| SelectedTrackAccentDarkBrush | `#1E88E5` | 1 | L149 |
| SelectedTrackCenterKeyBrush | `#C8E5FC` | 1 | L151 |

> `SelectedTrack*` 会被 `ThemeManager.ChangePianorollColor()`（`ThemeManager.cs#L163-L182`）按当前轨道配色覆盖。

### 4.3 键盘（钢琴卷帘）渐变

`WhiteKeyBrush` / `CenterKeyBrush` / `BlackKeyBrush` 都是 `LinearGradientBrush`，`StartPoint=0%,50%`、`EndPoint=100%,50%`（水平），两段 stop 分别取 `...ColorLeft`（Offset 0）与 `...ColorRight`（Offset 1）（`Brushes.axaml#L156-L180`）。

---

## 5. 控件样式与度量

除特别注明外，全部来自 `OpenUtau/Styles/Styles.axaml`。

### 5.1 全局圆角

| 键 | 值 | 行号 |
| --- | --- | --- |
| ControlCornerRadius | `0` | L7 |
| OverlayCornerRadius | `0` | L8 |

> OpenUtau 把 Fluent 默认圆角整体压成 **0**（直角）。这是最关键的视觉差异之一。

### 5.2 Button（`L99-L121`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| BorderThickness | `0` | L100 |
| FontSize | `12` | L101 |
| Height | `20` | L102 |
| Margin | `0,4` | L103 |
| Padding | `4,0,4,2` | L104 |
| HorizontalContentAlignment | `Center` | L105 |
| VerticalContentAlignment | `Center` | L106 |
| 模板 | 仅 `ContentPresenter`（无边框、无缩放动效） | L107-L120 |
| `:pressed` RenderTransform | `scale(1)`（禁用 Fluent 的按压缩放） | L123-L125 |

`Button.clear`（`L126-L138`）：同上但 `Background=Transparent`；`:pointerover` 背景 = `BackgroundColorPointerOver`。

### 5.3 ToggleButton

| 类 | 属性 | 值 | 行号 |
| --- | --- | --- | --- |
| `.normal` | BorderThickness / FontSize / Height / Margin / Padding | `0` / `12` / `20` / `0,4` / `4,0,4,2` | L141-L146 |
| `.normal` | Background | `SystemControlBackgroundAltHighBrush` | L147 |
| `.normal:pointerover` | Background | `NeutralAccentBrush` | L151 |
| `.normal:pressed` | Background | `NeutralAccentBrushPointerOver` | L154 |
| `.normal:checked` | Background | `NeutralAccentBrushSemi` | L157 |
| `.normal:checked:pointerover` | Background | `NeutralAccentBrush` | L160 |
| `.normal:checked:pressed` | Background | `NeutralAccentBrushPointerOver` | L163 |
| `.toolbar` | 同上度量 | — | L167-L175 |
| `.toolbar Path.filled` | Fill | `SystemControlForegroundBaseHighBrush` | L194 |
| `.toolbar Path.stroked` | StrokeThickness / Stroke | `1` / `SystemControlForegroundBaseHighBrush` | L201-L203 |
| `.switch` | FontSize / Padding / BorderThickness | `13` / `6,2` / `0` | L207-L209 |

### 5.4 ToggleSwitch

| 属性 | 值 | 行号 |
| --- | --- | --- |
| checked `Border#SwitchKnobBounds` Background | `AccentBrush1` | L213 |
| checked:pointerover | `SelectedTrackAccentLightBrush` | `PianoRollStyles.axaml#L198` |
| checked:pressed | `SelectedTrackAccentDarkBrush` | `PianoRollStyles.axaml#L201` |

### 5.5 TextBox（`L86-L97`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| MinHeight | `20` | L87 |
| Height | `20` | L88 |
| Padding | `4,1` | L89 |
| Margin | `0,0,0,4` | L90 |
| FontSize | `12` | L91 |
| VerticalAlignment | `Top` | L92 |
| SelectionForegroundBrush | `White` | L93 |
| `:disabled` Foreground | `TextControlForegroundDisabled` | L96 |
| `:focus` BorderBrush | `SelectedTrackAccentBrush` | `PianoRollStyles.axaml#L191` |
| ColorPicker 内 TextBox CornerRadius / Height / Padding | `0,4,4,0` / `32` / `5` | L491-L494 |

### 5.6 ComboBox（`L216-L233`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| Background | `SystemRegionBrush`（= BackgroundColor） | L217 |
| FontSize | `12` | L218 |
| MinHeight / Height | `20` / `20` | L219-L220 |
| Margin | `0,4` | L221 |
| Padding | `4,0,0,2` | L222 |
| DropDownGlyph Fill | `SystemControlForegroundBaseHighBrush` | L232 |
| ComboBoxItem FontSize / MinHeight / Height | `12` / `20` / `20` | L225-L227 |
| ComboBoxItem Margin / Padding | `0` / `4,2,0,0` | L228-L229 |
| ComboBoxItem:selected Background | `SelectedTrackAccentLightBrushSemi` | `PianoRollStyles.axaml#L32` |

### 5.7 ListBox / ListBoxItem

| 属性 | 值 | 来源 |
| --- | --- | --- |
| ListBoxItem MinHeight / Height | `24` / `24` | Styles L236-L237 |
| ListBoxItem Padding / Margin | `4` / `0` | Styles L238-L239 |
| ListBoxItem:selected ContentPresenter Background | `SelectedTrackAccentLightBrushSemi` | PianoRollStyles L41 |
| ListBox.toolbar FontSize / Background | `12` / `SystemControlBackgroundAltHighBrush` | PianoRollStyles L35-L38 |
| ListBoxItem.toolbar BorderThickness / MinHeight / Height / Width / Padding | `0` / `20` / `20` / `20` / `1` | PianoRollStyles L45-L49 |
| ListBoxItem.toolbar Background | `SystemControlBackgroundAltHighBrush` | PianoRollStyles L50 |
| toolbar :pointerover / :pressed / :selected | `NeutralAccentBrush` / `NeutralAccentBrushPointerOver` / `NeutralAccentBrushSemi` | PianoRollStyles L54-L60 |
| 工具图标内层 Grid | `18x18`，Path `Scale(0.55,0.55)` + Translate(-2,-2) | PianoRollStyles L91-L97 |

### 5.8 Menu / MenuItem / Separator

| 属性 | 值 | 行号 |
| --- | --- | --- |
| Menu Height | `24` | L19 |
| MenuItem Background / FontSize / Height / Padding | `Transparent` / `12` / `24` / `8,2,8,2` | L22-L25 |
| ContextMenu MenuItem FontSize / Padding | `12` / `4,2,4,2` | L60-L61 |
| Chevron RenderTransform | `Scale(0.75,0.75)` | L28 |
| Separator Background / HorizontalAlignment / Margin / Height | `SystemControlHighlightBaseMediumHighBrush` / `Stretch` / `2,0` / `1` | L44-L47 |
| MenuFlyoutThemeMinHeight | `20` | L9 |
| MenuFlyoutScrollerMargin | `0` | L10 |
| ComboBoxDropdownContentMargin | `0` | L11 |

### 5.9 CheckBox

| 属性 | 值 | 行号 |
| --- | --- | --- |
| MinHeight / MinWidth | `20` / `20` | L243-L244 |
| 模板 Grid Height / Width | `20` / `20` | L247-L248 |
| checked NormalRectangle Background/BorderBrush | `SelectedTrackAccentBrush` | PianoRollStyles L9 |
| checked:pointerover | `SelectedTrackAccentLightBrush` | PianoRollStyles L13 |
| checked:pressed | `SelectedTrackAccentDarkBrush` | PianoRollStyles L17 |

### 5.10 ProgressBar（`L285-L288`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| Background | `Transparent` | L286 |
| Foreground | `AccentBrush1` | L287 |

### 5.11 TabControl / TabItem

样式仅在 SingersDialog 内定义：`OpenUtau/Views/SingersDialog.axaml#L121-L125`

| 属性 | 值 | 行号 |
| --- | --- | --- |
| FontSize | `13` | L122 |
| MinHeight | `28` | L123 |
| Padding | `10,2` | L124 |

> 无全局 TabItem 样式，其余外观来自 Avalonia FluentTheme。TabControl `Margin=10,0`、`Padding=0`（L119）。

### 5.12 ScrollBar（`ScrollBar.music`，`L330-L378`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| expanded TrackRect Fill | `Transparent` | L331 |
| expanded Thumb Background | `NeutralAccentBrushSemi` | L334 |
| expanded Thumb:pointerover | `NeutralAccentBrush` | L337 |
| line RepeatButton Background | `Transparent`（常态/悬停/按下） | L340-L346 |
| 箭头 Viewbox / Path#Arrow 尺寸 | `24` / `24x24` | L361-L368 |
| 箭头 Fill / pointerover / pressed | `NeutralAccentBrushSemi` / `NeutralAccentBrush` / `NeutralAccentBrush` | L367-L378 |
| 箭头 Path Data（vertical up/down, horizontal up/down） | 三角形几何 | L348-L359 |

### 5.13 Slider（`Slider.fader`，`L410-L467`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| Focusable | `False` | L411 |
| Foreground | `AccentBrush1` | L412 |
| `:pointerover PART_DecreaseButton` Background | `AccentBrush1` | L457 |
| Thumb 模板 | `Border` Height `6` × Width `16`，CornerRadius `2` | L463 |
| Thumb / DecreaseButton 用 `Foreground` 作为填充 | — | L427-L437 |

### 5.14 Expander（`L469-L487`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| Margin / Padding | `4,2` / `4` | L470-L471 |
| IsExpanded 默认 | `True` | L472 |
| MinHeight | `32` | L473 |
| Background | `Transparent` | L474 |
| Header Height / MinHeight | `32` / `32` | L478-L479 |
| Header FontWeight | `Bold` | L479 |
| Chevron Border Height | `20` | L482 |
| Header ContentPresenter Margin | `7,0,0,0` | L485 |

### 5.15 播放条 / Tips / GroupBox / 通知卡

**Border.playback**（`L290-L316`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| CornerRadius | `4` | L291 |
| BorderThickness | `1` | L292 |
| BorderBrush | `NeutralAccentBrushSemi` | L293 |
| Height | `20` | L294 |
| 内 TextBlock Padding / FontSize / FontFamily | `6,0,0,1` / `11` / `Monospace` | L297-L299 |
| 内 Button/ToggleButton Width / Height / Background | `21` / `19` / `Transparent` | L310-L312 |
| checked ContentPresenter Background | `NeutralAccentBrushSemi` | L315 |

**Border.tips**（`L318-L328`）：CornerRadius `4`、Background `SystemControlBackgroundAltHighBrush`、BorderBrush `NeutralAccentBrushSemi`、BorderThickness `1`、Padding `2`；内部 TextBlock `LineHeight=18`（L327）。

**HeaderedContentControl.groupbox**（`L380-L408`）：Margin `5`；标题 Border Padding `2,0,2,0`、Margin `5,0,0,0`；内容 Border BorderThickness `1`、CornerRadius `3`、Padding `12,12,12,8`、Margin `0,8,0,0`。

**NotificationCard**（`L497-L535`）：CornerRadius `6`；BoxShadow `0 4 12 0 #20000000`；左侧强调条宽 `6`、Fill `AccentBrush1`；内容 Margin `14,10`。

### 5.16 DataGrid

| 属性 | 值 | 行号 |
| --- | --- | --- |
| DataGridColumnHeader FontSize / MinHeight | `12` / `20` | L274-L275 |
| DataGridCell FontSize / MinHeight | `12` / `20` | L278-L279 |
| DataGridCell > TextBox Margin | `0` | L282 |

### 5.17 RadioButton（`PianoRollStyles.axaml#L206-L277`）

| 属性 | 值 | 行号 |
| --- | --- | --- |
| Padding / FontSize | `0` / `12` | L207-L208 |
| 模板 Grid ColumnDefinitions | `20,*` | L218 |
| 左列 Grid Height | `32` | L219 |
| OuterEllipse / CheckOuterEllipse 尺寸 | `14x14` | L220-L223 / L229-L232 |
| CheckGlyph 尺寸 | `7x7` | L241-L242 |
| checked 填充 | `SelectedTrackAccentBrush` | L233, L235 |
| pointerover 填充 | `SelectedTrackAccentLightBrush` | L269-L270 |

---

## 6. 字体

来源：`OpenUtau/Strings/Strings.axaml#L1026`（及本地化覆盖），`Window` 上绑定 `FontFamily="{DynamicResource ui.fontfamily}"`（`Styles.axaml#L15`）。

| 场景 | 值 | 来源 |
| --- | --- | --- |
| 默认 UI 字体族 | `Segoe UI,San Francisco,Helvetica Neue` | Strings.axaml L1026 |
| 简体中文 UI 字体族 | `Microsoft YaHei UI,PingFang SC,Segoe UI,San Francisco,Helvetica Neue` | Strings.zh-CN.axaml L912 |
| 日文 UI 字体族 | `Meiryo UI,Hiragino Sans,Segoe UI,San Francisco,Helvetica Neue` | Strings.ja-JP.axaml L848 |
| 泰文 UI 字体族 | `Noto serif Thai,Angsana New,Noto Sans Thai,IBM Plex Sans Thai,Segoe UI,...` | Strings.th-TH.axaml L849 |
| YAML 编辑器等宽字体 | `Cascadia Mono,Consolas,Menlo,DejaVu Sans Mono,monospace` | Controls/YamlEditor.axaml L11 |
| 播放时钟等宽字体 | `Monospace` | Styles.axaml L299 |
| 轨道头音量/声像 | `monospace` | Controls/TrackHeader.axaml L209/L234 |
| 钢琴卷帘标尺 | `monospace` | Controls/PianoRoll.axaml L524 |

**字号体系**（Compose 可映射为 sp/px 直接取用）：

| 用途 | 字号 | 来源 |
| --- | --- | --- |
| 基础 TextBlock / Label | `12` | Styles L81, L84 |
| MenuItem / ContextMenuItem | `12` | Styles L23, L60 |
| TextBox / Button / ComboBox / ComboBoxItem | `12` | Styles L91, L101, L218, L225 |
| ToggleButton.switch | `13` | Styles L207 |
| ListBox.toolbar | `12` | PianoRollStyles L36 |
| TabItem | `13` | SingersDialog L122 |
| DataGrid 表头/单元格 | `12` | Styles L274, L278 |
| 播放条时钟 | `11` | Styles L298 |
| RadioButton | `12` | PianoRollStyles L208 |
| 欢迎页标题 | `20` | MainWindow.axaml L100 |
| 欢迎页按钮标题 | `14` | MainWindow.axaml L122 |
| 轨道头音量/声像 | `9` | TrackHeader.axaml L209/L225 |
| 钢琴卷帘标尺 | `10` | PianoRoll.axaml L524/L534 |
| Expander 标题字重 | `Bold` | Styles L479 |

---

## 7. 窗口尺寸与间距体系

### 7.1 窗口

| 项 | 值 | 来源 |
| --- | --- | --- |
| 主窗口默认尺寸 | `1200 × 650` | `OpenUtau.Core/Util/WindowSize.cs#L3-L4`（`Preferences.MainWindowSize`） |
| 主窗口最小尺寸 | `300 × 200` | `Views/MainWindow.axaml#L12` |
| 主窗口 Icon | `/Assets/open-utau.ico` | MainWindow.axaml L11 |
| 钢琴卷帘独立窗口默认尺寸 | `1200 × 650` | `PianorollWindowSize`（同一 WindowSize 类默认值） |
| SplashWindow | `400 × 180`，不可缩放 | SplashWindow.axaml L5-L9 |
| ThemeEditorWindow | `325 × 500`（MinWidth 325） | ThemeEditorWindow.axaml L10 |
| 主窗口设计画布 | `1200 × 650` | MainWindow.axaml L7（`d:DesignWidth/Height`） |

### 7.2 关键间距

| 项 | 值 | 来源 |
| --- | --- | --- |
| 欢迎页 Grid Margin | `60,50,60,40` | MainWindow.axaml L91 |
| 播放条 Border Height | `20` | Styles L294 |
| 播放条时间框 Width / Margin | `72` / `4,0` | MainWindow.axaml L277 |
| GroupBox Margin / 内容 Padding | `5` / `12,12,12,8` | Styles L381, L399 |
| Expander Margin / Padding | `4,2` / `4` | Styles L470-L471 |
| GridSplitter Height | `10` | SingersDialog.axaml L117 |
| 轨道头默认 Width / Height | `300 × 104` | TrackHeader.axaml L7 |

### 7.3 ViewConstants（`OpenUtau/ViewConstants.cs`）

| 常量 | 值 | 行号 |
| --- | --- | --- |
| TrackHeightMax / Min / Default / Delta | `144` / `44` / `104` / `20` | L15-L18 |
| PianoRollMinHeight | `24` | L23 |
| NoteHeightMax / Min / Default | `128` / `8` / `22` | L25-L27 |
| ResizeMargin | `8` | L33 |
| ExpHeightMin / Max | `132` / `600` | L51-L52 |

### 7.4 轨道配色表（`ThemeManager.TrackColors`，`ThemeManager.cs#L59-L78`）

每项 5 个颜色：**accent / pressed(dark) / pointerOver(light) / background(light@0.5) / centerKey**。

| 名称 | accent | dark | light | centerKey |
| --- | --- | --- | --- | --- |
| Pink | `#F06292` | `#EC407A` | `#F48FB1` | `#FAC7D8` |
| Red | `#EF5350` | `#E53935` | `#E57373` | `#F2B9B9` |
| Orange | `#FF8A65` | `#FF7043` | `#FFAB91` | `#FFD5C8` |
| Yellow | `#FBC02D` | `#F9A825` | `#FDD835` | `#FEF1B6` |
| Light Green | `#CDDC39` | `#C0CA33` | `#DCE775` | `#F2F7CE` |
| Green | `#66BB6A` | `#43A047` | `#A5D6A7` | `#D2EBD3` |
| Light Blue | `#4FC3F7` | `#29B6F6` | `#81D4FA` | `#C0EAFD` |
| Blue（默认） | `#4EA6EA` | `#1E88E5` | `#90CAF9` | `#C8E5FC` |
| Purple | `#BA68C8` | `#AB47BC` | `#CE93D8` | `#E7C9EC` |
| Pink2 | `#E91E63` | `#C2185B` | `#F06292` | `#F8B1C9` |
| Red2 | `#D32F2F` | `#B71C1C` | `#EF5350` | `#F7A9A8` |
| Orange2 | `#FF5722` | `#E64A19` | `#FF7043` | `#FFB8A1` |
| Yellow2 | `#FF8F00` | `#FF7F00` | `#FFB300` | `#FFE097` |
| Light Green2 | `#AFB42B` | `#9E9D24` | `#CDDC39` | `#E6EE9C` |
| Green2 | `#2E7D32` | `#1B5E20` | `#43A047` | `#A1D0A3` |
| Light Blue2 | `#1976D2` | `#0D47A1` | `#2196F3` | `#90CBF9` |
| Blue2 | `#3949AB` | `#283593` | `#5C6BC0` | `#AEB5E0` |
| Purple2 | `#7B1FA2` | `#4A148C` | `#AB47BC` | `#D5A3DE` |

> 未选中的默认（DefColor）轨道使用主题键盘色；`GetTrackColor` 未命中时回退到 `Blue`（`ThemeManager.cs#L291-L296`）。

---

## 8. 图标资源

**结论：OpenUtau 的界面图标全部是 XAML 内联矢量 `<Path Data="...">`（SVG path mini-language），仓库中没有逐图标的 .svg/.png 文件。** 只有应用图标 / Logo 是图片资源。

### 8.1 图片资源清单（`OpenUtau/Assets/` 与 `Logo/`）

| 仓库相对路径 | 用途 |
| --- | --- |
| `OpenUtau/Assets/open-utau.ico` | 所有 Window 的 `Icon`（如 MainWindow.axaml L11） |
| `OpenUtau/Assets/OpenUtau.png` | 光栅应用图标 |
| `OpenUtau/Assets/OpenUtau.icns` | macOS 应用图标 |
| `OpenUtau/Assets/logotype.png` | 浅色 Logo（SplashWindow L23，Height 16） |
| `OpenUtau/Assets/logotype-w.png` | 深色 Logo（SplashWindow L20，Height 16） |
| `OpenUtau/Assets/OpenUtau.desktop` | Linux 桌面项 |
| `OpenUtau/Assets/AppRun` | Linux AppImage 启动器 |
| `OpenUtau/Assets/OpenUTAU.icon/icon.json` | Xcode .icon 描述 |
| `OpenUtau/Assets/OpenUTAU.icon/Assets/Artboard 1 4.svg` | macOS 图标分层 |
| `OpenUtau/Assets/OpenUTAU.icon/Assets/Artboard 1 6.svg` | macOS 图标分层 |
| `OpenUtau/Assets/OpenUTAU.icon/Assets/Artboard 1 7.svg` | macOS 图标分层 |
| `OpenUtau/Assets/OpenUTAU.icon/Assets/Artboard 1 8.svg` | macOS 图标分层 |
| `OpenUtau/Assets/OpenUTAU.icon/Assets/Artboard 1 9.svg` | macOS 图标分层 |
| `Logo/openutau.svg` | 项目 Logo 源文件 |
| `Logo/openutau_logotype.svg` | 项目字标源文件 |

### 8.2 内联矢量图标位置一览

| 区域 | 文件 / 行号 | 图标 |
| --- | --- | --- |
| 搜索栏 | `OpenUtau/Controls/SearchBar.axaml#L26,L43,L60,L77` | arrow-left / arrow-right / new-folder / close（来自 microsoft/vscode-icons，注释在 L25/42/59/76） |
| 欢迎页 | `OpenUtau/Views/MainWindow.axaml#L110,L143` | new-file / go-to-file |
| 播放传输栏 | `OpenUtau/Views/MainWindow.axaml#L250-L272` | seek-start / play / pause / seek-end / loop / metronome（`18×18`） |
| 轨道头 | `OpenUtau/Controls/TrackHeader.axaml#L154,L166,L180,L193,L240` | 歌手头像 / mute / solo / clear / render |
| 添加轨道 | `OpenUtau/Controls/TrackAdder.axaml#L10` | add-track |
| 钢琴卷帘面板 | `OpenUtau/Controls/PianoRoll.axaml#L96,L423-L592` | 设置、吸附、锁定、表情等（filled/stroked） |
| 歌手浮层 | `OpenUtau/Controls/SingerFlyout.axaml#L145-L226` | 收藏/箭头 |
| 偏好设置 | `OpenUtau/Views/PreferencesDialog.axaml#L283,L297,L304` | 文件夹/勾选 |
| 歌手对话框 | `OpenUtau/Views/SingersDialog.axaml#L60,L222,L237` | 搜索/头像/勾选 |
| 菜单勾选 | `OpenUtau/Styles/Styles.axaml#L70-L78` | `Data="M1,5 L4,8 L9,1"`，StrokeThickness 2，12×12 |
| 滚动条箭头 | `OpenUtau/Styles/Styles.axaml#L348-L359` | 上/下/左/右三角 |

### 8.3 钢琴卷帘工具图标（`OpenUtau/Styles/PianoRollStyles.axaml`）

每个工具都是 `Style Selector="ListBoxItem.<name>Tool /template/ Path#PART_ToolIcon"` 下的 `Data=` 内联几何：

| 工具类名 | 行号 |
| --- | --- |
| cursorTool | L122 |
| verticalStretchTool | L125 |
| horizontalStretchTool | L128 |
| verticalShiftTool | L131 |
| horizontalShiftTool | L134 |
| penTool | L137 |
| penPlusTool | L140 |
| eraserTool | L143 |
| pitchPointTool | L146 |
| drawPitchTool | L149 |
| pitchLineTool | L152 |
| knifeTool | L155 |
| pitchSCurveTool | L158 |
| pitchSineWaveTool | L161 |
| pitchSmoothenTool | L164 |

---

## 9. 未能确定的项

1. **错误色 / 成功色**：仓库主题中没有专门的 `ErrorColor` / `SuccessColor` 资源。只有 `WarningColor`（深色 `#433519`、浅色 `#FFF4CE`）。错误/成功态在 Avalonia 中依赖 FluentTheme 默认值，未在 OpenUtau 内覆盖。
2. **未覆盖控件的精确外观**：TabControl 主体、默认（非 `.music`）ScrollBar、默认 Slider、Tooltip、标准 Popup/Flyout、DatePicker 等使用 Avalonia `FluentTheme` 的内置模板，其具体数值不在本仓库源码中（属于 Avalonia 包），因此本报告未给出，也不能从 OpenUtau 仓库读取到确定值。
3. **Hover / 选中态的部分组合**：`Button` 的 hover/pressed 背景由 Fluent 模板控制，OpenUtau 只覆盖了 `Button.clear` 的 hover 背景；普通 Button 的悬停色在仓库中无显式定义（其间接来源 `ButtonBackgroundPointerOver = ForegroundColorPressed @ 0.25` 定义于 `Brushes.axaml#L72`，但普通 Button 模板是否使用该 brush 取决于 Fluent 默认模板）。
4. **图标语义命名**：多数内联 Path 没有 `Name` 属性，也没有注释，无法 100% 确认每个几何对应的功能语义（报告中的功能说明基于所在控件上下文推断，几何数据本身是原文照录）。
5. **主题 YAML 包的默认示例**：`OpenUtau/ThemeLoader.cs` 从已安装 Oudep 包读取，仓库内没有附带示例 YAML 文件，因此自定义主题的样例文件路径无法给出。
6. **字体回退的实际渲染**：字体族是逗号分隔的回退列表，最终由操作系统决定；Android 上的实际可用字体与字号像素换算需要另行映射（原报告只能给出逻辑字号）。
7. **深浅色的“主色”判定**：OpenUtau 没有单一 `PrimaryColor` 之类的命名；本报告以 `SystemAccentColor`/`AccentColor1` 作为强调主色，`BackgroundColor`/`ForegroundColor` 作为背景/前景主色。

---

*生成自 commit `5c3c83bbeced96718e85e4a96d127de3287cd14b`；机器可读版本见 `research/openutau-theme.json`。*
