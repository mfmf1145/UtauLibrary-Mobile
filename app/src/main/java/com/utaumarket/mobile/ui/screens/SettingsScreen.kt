package com.utaumarket.mobile.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.BuildConfig
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.data.prefs.ThemeMode
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuCheckbox
import com.utaumarket.mobile.ui.components.OuChip
import com.utaumarket.mobile.ui.components.OuDivider
import com.utaumarket.mobile.ui.components.OuGroupBox
import com.utaumarket.mobile.ui.components.OuTag
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.components.OuTextField
import com.utaumarket.mobile.ui.components.OuDialog
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.vm.UiState

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    state: UiState,
    onPickDirectory: (ResourceKind) -> Unit,
    onPickRoot: () -> Unit,
) {
    val c = OuTheme.colors
    val ctx = LocalContext.current
    var token by remember(state.settings.githubToken) { mutableStateOf(state.settings.githubToken) }

    // 横屏时给一点左右边距就够。
    // 之前写了 140.dp（左右各 140），在 2736px 宽的屏幕上把内容挤成中间一条，
    // 看起来非常怪异。改成温和的 28dp，并限制最大宽度避免行太长。
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (isLandscape()) 28.dp else 8.dp,
                vertical = 8.dp,
            ),
    ) {

                // ---------------------------------------------------------------- 设置分类
                // 大类切换：下面的分组按分类显示，避免十几个分组平铺在一起
                val settingsCategories = listOf(
                    stringResource(R.string.cat_download),
                    stringResource(R.string.cat_appearance),
                    stringResource(R.string.cat_dirs),
                    stringResource(R.string.cat_advanced),
                    stringResource(R.string.cat_about),
                )
                // 【必须用 rememberSaveable】切换界面语言会 Activity.recreate()，
                // 普通 remember 会被重置成 0 —— 用户从「关于」切语言后被弹回「下载与安装」。
                var settingsCat by rememberSaveable { mutableStateOf(0) }
                // 「重置所有设置」的二次确认
                var showResetDialog by rememberSaveable { mutableStateOf(false) }
                // 【开发者模式】「关于与风险提示」标题连点 5 次 → 密码 0401
                var aboutTaps by remember { mutableStateOf(0) }
                var showDevPassword by remember { mutableStateOf(false) }
                var showDevTools by remember { mutableStateOf(false) }
                var devHintShown by remember { mutableStateOf(false) }
                // 分类条：下划线式 Tab（等宽、选中项有强调色横线）
                val trackColor = OuTheme.colors.border.copy(alpha = 0.4f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // 底部一条贯穿的细线，让下划线看起来是"贴着轨道"的
                            drawRect(
                                color = trackColor,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                            )
                        },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    settingsCategories.forEachIndexed { idx, name ->
                        val sel = settingsCat == idx
                        // 下划线式分类：不再用整块填色（用户反馈太突兀），
                        // 改成「选中项下方 2dp 强调色横线 + 文字变色」，安静很多。
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { settingsCat = idx },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    name,
                                    style = OuTheme.typography.body,
                                    color = if (sel) OuTheme.colors.accent else OuTheme.colors.foregroundDisabled,
                                    fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.Bold else null,
                                    maxLines = 1,
                                )
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                        if (sel) OuTheme.colors.accent else Color.Transparent,
                                    ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (settingsCat == 0) {
                    Column(Modifier.fillMaxWidth()) {
                    OuGroupBox(title = stringResource(R.string.settings_dl_title), collapsible = true, defaultExpanded = true, summary = stringResource(R.string.settings_dl_summary)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuCheckbox(
                        checked = state.settings.autoInstall,
                        onCheckedChange = { vm.setAutoInstall(it) },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_auto_install), style = OuTheme.typography.body, color = c.foreground)
                }
                Text(
                    stringResource(R.string.settings_auto_install_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_auto_retry, state.settings.autoRetryCount),
                    style = OuTheme.typography.body,
                    color = c.foreground,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 3, 5).forEach { n ->
                        OuChip(
                            if (n == 0) stringResource(R.string.settings_auto_retry_off) else "$n",
                            state.settings.autoRetryCount == n,
                        ) { vm.settings.setAutoRetryCount(n) }
                    }
                }
                Text(
                    stringResource(R.string.settings_auto_retry_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuCheckbox(
                        checked = state.settings.keepPartialOnFail,
                        onCheckedChange = { vm.settings.setKeepPartialOnFail(it) },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.settings_keep_partial),
                        style = OuTheme.typography.body,
                        color = c.foreground,
                    )
                }
                Text(
                    stringResource(R.string.settings_keep_partial_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(10.dp))
                // 与「失败自动重试：N 次」那行完全一致（body + 本体色），
                // 之前用 bodyStrong 显得过粗（用户反馈）
                Text(
                    stringResource(R.string.settings_conn_desc),
                    style = OuTheme.typography.body,
                    color = c.foreground,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    // 档位与 ParallelDownloader.MAX_WORKERS 保持一致：选了就真的用
                    listOf(8, 16, 32, 48, 64).forEach { n ->
                        OuChip(
                            label = "$n",
                            selected = state.settings.connections == n,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { vm.setConnections(n) }
                    }
                }
            }

            OuGroupBox(title = stringResource(R.string.settings_net_title), collapsible = true, defaultExpanded = true, summary = stringResource(R.string.settings_net_summary)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuCheckbox(
                        checked = state.settings.useMirrorFirst,
                        onCheckedChange = { vm.setMirrorFirst(it) },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_mirror_first), style = OuTheme.typography.body, color = c.foreground)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_token_label), style = OuTheme.typography.small, color = c.foregroundDisabled)
                Spacer(Modifier.height(4.dp))
                OuTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = stringResource(R.string.settings_token_hint),
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    OuButton(stringResource(R.string.settings_token_save), onClick = { vm.setToken(token.trim()) }, leadingIconRes = OuIcons.check)
                    Spacer(Modifier.width(6.dp))
                    OuButton(stringResource(R.string.settings_token_clear), onClick = { token = ""; vm.setToken("") }, variant = OuButtonVariant.Clear)
                }
            }

            OuGroupBox(title = stringResource(R.string.settings_route_title), collapsible = true, summary = stringResource(R.string.settings_route_summary)) {
                Text(
                    stringResource(R.string.settings_route_desc),
                    style = OuTheme.typography.small,
                    color = c.foreground,
                )
                Spacer(Modifier.height(8.dp))
                Column {
                    listOf(
                        com.utaumarket.mobile.download.UrlCandidates.MODE_AUTO to stringResource(R.string.settings_route_auto),
                        com.utaumarket.mobile.download.UrlCandidates.MODE_DIRECT to stringResource(R.string.settings_route_direct),
                        com.utaumarket.mobile.download.UrlCandidates.MODE_PROXY_FIRST to stringResource(R.string.settings_route_proxy_first),
                        com.utaumarket.mobile.download.UrlCandidates.MODE_CUSTOM to stringResource(R.string.settings_route_custom),
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            OuCheckbox(
                                checked = state.settings.ghMode == mode,
                                onCheckedChange = { vm.setGhMode(mode) },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = OuTheme.typography.body, color = c.foreground)
                        }
                    }
                }
                if (state.settings.ghMode == com.utaumarket.mobile.download.UrlCandidates.MODE_CUSTOM) {
                    Spacer(Modifier.height(6.dp))
                    OuTextField(
                        value = state.settings.ghCustomPrefix,
                        onValueChange = { vm.setGhCustomPrefix(it) },
                        placeholder = stringResource(R.string.settings_route_prefix_hint),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.settings_route_builtin,
                        com.utaumarket.mobile.download.UrlCandidates.BUILT_IN
                            .joinToString(" · ") { it.second },
                    ),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }

                    }
                }

                if (settingsCat == 1) {
                    Column(Modifier.fillMaxWidth()) {
            OuGroupBox(title = stringResource(R.string.settings_orientation_title), collapsible = true, defaultExpanded = true, summary = stringResource(R.string.settings_orientation_summary)) {
                Text(
                    stringResource(R.string.settings_orientation_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(
                        "auto" to stringResource(R.string.settings_orientation_auto),
                        "landscape" to stringResource(R.string.settings_orientation_landscape),
                        "portrait" to stringResource(R.string.settings_orientation_portrait),
                    ).forEach { (mode, label) ->
                        OuChip(
                            label = label,
                            selected = state.settings.orientation == mode,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { vm.setOrientation(mode) }
                    }
                }
            }

            OuGroupBox(
                title = stringResource(R.string.settings_net_indicator_title),
                collapsible = true,
                defaultExpanded = true,
                summary = stringResource(R.string.settings_net_indicator_summary),
            ) {
                Text(
                    stringResource(R.string.settings_net_indicator_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(
                        "dot" to stringResource(R.string.net_indicator_dot),
                        "emoji" to stringResource(R.string.net_indicator_emoji),
                    ).forEach { (mode, label) ->
                        OuChip(
                            label = label,
                            selected = state.settings.netIndicatorStyle == mode,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { vm.settings.setNetIndicatorStyle(mode) }
                    }
                }
            }

            OuGroupBox(
                title = stringResource(R.string.settings_list_title),
                collapsible = true,
                defaultExpanded = true,
                summary = stringResource(R.string.settings_list_summary),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuCheckbox(
                        checked = state.settings.hideReference,
                        onCheckedChange = { vm.settings.setHideReference(it) },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.settings_hide_reference),
                        style = OuTheme.typography.body,
                        color = c.foreground,
                    )
                }
                Text(
                    stringResource(R.string.settings_hide_reference_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuCheckbox(
                        checked = state.settings.warmScanInstalled,
                        onCheckedChange = { vm.settings.setWarmScanInstalled(it) },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.settings_warm_scan),
                        style = OuTheme.typography.body,
                        color = c.foreground,
                    )
                }
                Text(
                    stringResource(R.string.settings_warm_scan_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }

            OuGroupBox(
                title = "配色方案",
                collapsible = true,
                defaultExpanded = true,
                summary = "改变整个应用的色调。所有方案都保持 OpenUtau 的方角与紧凑布局，只换颜色。",
            ) {
                val curScheme = com.utaumarket.mobile.ui.theme.ColorScheme.fromId(state.settings.colorScheme)
                val isDarkNow = when (state.settings.themeMode) {
                    com.utaumarket.mobile.data.prefs.ThemeMode.DARK -> true
                    com.utaumarket.mobile.data.prefs.ThemeMode.LIGHT -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                // 【预览跟随"实际生效"的明暗】
                // Synthesizer V / VOCALOID 是恒深色方案：即使系统是浅色，界面也是深色。
                // 之前预览卡片用的是 isDarkNow（系统明暗），于是这两套方案下，
                // 其余方案的缩略图仍显示浅色白底 —— 和当前深色界面脱节。
                // 这里改用"当前方案实际是否深色"，切换过去后所有缩略图会一起变深。
                val effectiveDark = com.utaumarket.mobile.ui.theme.paletteFor(curScheme, isDarkNow).isDark
                // 两列网格：每个方案显示一块「实际配色预览」
                val schemes = com.utaumarket.mobile.ui.theme.ColorScheme.entries
                Column(Modifier.fillMaxWidth()) {
                    schemes.chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth()) {
                            rowItems.forEach { sc ->
                                SchemePreviewCard(
                                    scheme = sc,
                                    dark = effectiveDark,
                                    selected = sc == curScheme,
                                    modifier = Modifier.weight(1f),
                                    onClick = { vm.settings.setColorScheme(sc.id) },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            OuGroupBox(
                title = stringResource(com.utaumarket.mobile.R.string.settings_nav_title),
                collapsible = true,
                defaultExpanded = false,
                summary = stringResource(com.utaumarket.mobile.R.string.settings_nav_summary),
            ) {
                // 规范页面列表（id 与 AppRoot 里的 NavSlot 一一对应）
                val allTabs = listOf(
                    "plugins" to stringResource(com.utaumarket.mobile.R.string.nav_plugins),
                    "phonemizers" to stringResource(com.utaumarket.mobile.R.string.nav_phonemizers),
                    "voicebanks" to stringResource(com.utaumarket.mobile.R.string.nav_voicebanks),
                    "forks" to stringResource(com.utaumarket.mobile.R.string.nav_forks),
                    "projects" to stringResource(com.utaumarket.mobile.R.string.nav_projects),
                    "installed" to stringResource(com.utaumarket.mobile.R.string.nav_installed),
                    "settings" to stringResource(com.utaumarket.mobile.R.string.nav_settings),
                )
                val hidden = state.settings.hiddenTabs.split(',').filter { it.isNotBlank() }.toSet()
                Column(Modifier.fillMaxWidth()) {
                    allTabs.forEach { (id, label) ->
                        val visible = id !in hidden
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                // 至少保留一个页面，否则界面会空
                                val next = if (visible) {
                                    if (hidden.size >= allTabs.size - 1) hidden else hidden + id
                                } else {
                                    hidden - id
                                }
                                vm.settings.setHiddenTabs(next.joinToString(","))
                            // 记下"当前有哪些页面"，以后新增的页面就靠这个判断
                            vm.settings.setNavKnownTabs(allTabs.joinToString(",") { it.first })
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 方角勾选框，和下载须知保持一致
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(
                                        if (visible) OuTheme.colors.accent else OuTheme.colors.background,
                                    )
                                    .border(
                                        1.dp,
                                        if (visible) OuTheme.colors.accent else OuTheme.colors.border,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (visible) {
                                    Text(
                                        "✓",
                                        style = OuTheme.typography.small,
                                        color = if (OuTheme.colors.isDark) Color(0xFF101014) else Color.White,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = OuTheme.typography.body, color = OuTheme.colors.foreground)
                        }
                    }
                }
            }

            OuGroupBox(
                title = stringResource(R.string.settings_lang_title),
                collapsible = true,
                defaultExpanded = true,
                summary = stringResource(R.string.settings_lang_summary),
            ) {
                val cur = state.settings.appLanguage
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_lang_choose),
                            style = OuTheme.typography.body,
                            color = c.foreground,
                        )
                        Text(
                            if (cur.isBlank()) {
                                stringResource(R.string.settings_lang_current_system)
                            } else {
                                stringResource(R.string.settings_lang_current, langLabel(cur))
                            },
                            style = OuTheme.typography.small,
                            color = c.foregroundDisabled,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 横向可滚动：5 个选项在窄屏上放不下，"日本語"会被挤成"日本"
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    langOptions().forEach { (tag, label) ->
                        OuChip(label, cur == tag) {
                            vm.settings.setAppLanguage(tag)
                            (ctx as? android.app.Activity)?.recreate()
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }

            OuGroupBox(
                title = stringResource(R.string.settings_anim_title),
                collapsible = true,
                defaultExpanded = true,
                summary = stringResource(R.string.settings_anim_summary),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuCheckbox(
                        checked = state.settings.animationsEnabled,
                        onCheckedChange = { vm.settings.setAnimationsEnabled(it) },
                    )
                    Text(
                        stringResource(R.string.settings_anim_toggle),
                        style = OuTheme.typography.body,
                        color = c.foreground,
                    )
                }
                Text(
                    stringResource(R.string.settings_anim_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }

            OuGroupBox(title = stringResource(R.string.settings_look_title), collapsible = true, defaultExpanded = true, summary = stringResource(R.string.settings_look_summary)) {
                Text(stringResource(R.string.settings_theme_desc), style = OuTheme.typography.small, color = c.foregroundDisabled)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            OuCheckbox(
                                checked = state.settings.themeMode == mode,
                                onCheckedChange = { vm.setTheme(mode) },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(themeLabel(mode)), style = OuTheme.typography.body, color = c.foreground)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(OuIcons.info), null, tint = c.foregroundDisabled, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.settings_theme_credit),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                    )
                }
            }

                    }
                }

                if (settingsCat == 2) {
                    Column(Modifier.fillMaxWidth()) {
            OuGroupBox(title = stringResource(R.string.settings_root_title), collapsible = true, defaultExpanded = true, summary = stringResource(R.string.settings_root_summary)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_root_label), style = OuTheme.typography.body, color = c.foreground, modifier = Modifier.width(72.dp))
                    if (state.settings.rootTreeUri.isBlank()) {
                        OuTag(stringResource(R.string.tag_unauthorized), tone = c.accentColor3)
                    } else if (state.rootInvalid) {
                        OuTag(stringResource(R.string.tag_auth_invalid), tone = c.accentColor3)
                    } else {
                        OuTag(stringResource(R.string.tag_authorized), accent = true)
                    }
                }
                if (state.rootInvalid) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_root_invalid),
                        style = OuTheme.typography.small,
                        color = c.accentColor3,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    state.settings.rootTreeUri.ifBlank { stringResource(R.string.settings_not_selected) },
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                    maxLines = 2,
                )
                // 【路径提示】装了却在 OpenUtau 里找不到，多半是两个 App 指向的目录不同。
                // 这里把实际安装目录写出来，并说明要去 OpenUtau 加同一条路径。
                if (state.settings.rootTreeUri.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    val sub = when {
                        state.settings.voicebankDocId.isNotBlank() -> stringResource(R.string.settings_install_dir_vb)
                        else -> stringResource(R.string.settings_install_dir_unknown)
                    }
                    Text(
                        stringResource(R.string.settings_install_dir, sub),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                    )
                    Text(
                        stringResource(R.string.settings_install_dir_hint),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                    )
                    Spacer(Modifier.height(4.dp))
                    OuButton(
                        stringResource(R.string.settings_copy_dir),
                        onClick = {
                            runCatching {
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                                cm?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("dir", state.settings.rootTreeUri),
                                )
                            }
                        },
                        variant = OuButtonVariant.Clear,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuButton(
                        when {
                            state.settings.rootTreeUri.isBlank() -> stringResource(R.string.settings_btn_pick)
                            state.rootInvalid -> stringResource(R.string.settings_btn_reauth)
                            else -> stringResource(R.string.settings_btn_change)
                        },
                        onClick = { onPickRoot() },
                        variant = if (state.rootInvalid) OuButtonVariant.Accent else OuButtonVariant.Normal,
                        leadingIconRes = OuIcons.folder,
                    )
                    if (state.settings.rootTreeUri.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        OuButton(
                            stringResource(R.string.settings_btn_rescan),
                            onClick = { vm.rescanRoot() },
                            variant = OuButtonVariant.Clear,
                            leadingIconRes = OuIcons.refresh,
                        )
                        Spacer(Modifier.width(6.dp))
                        OuButton(
                            stringResource(R.string.action_open),
                            onClick = { openTree(ctx, state.settings.rootTreeUri) },
                            variant = OuButtonVariant.Clear,
                            leadingIconRes = OuIcons.open,
                        )
                        Spacer(Modifier.width(6.dp))
                        OuButton(stringResource(R.string.settings_btn_release), onClick = { vm.releaseRoot() }, variant = OuButtonVariant.Clear)
                    }
                }

                if (state.settings.lastScanSummary.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(OuIcons.check), null, tint = c.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_scan_result), style = OuTheme.typography.small, color = c.foregroundDisabled)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(state.settings.lastScanSummary, style = OuTheme.typography.body, color = c.foreground)
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_root_help),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }

            OuGroupBox(title = stringResource(R.string.settings_adv_title), collapsible = true, summary = stringResource(R.string.settings_adv_summary)) {
                val hasExplicit = state.settings.voicebankTreeUri.isNotBlank() ||
                    state.settings.pluginTreeUri.isNotBlank() ||
                    state.settings.phonemizerTreeUri.isNotBlank()
                if (hasExplicit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(OuIcons.warning), null, tint = c.accentColor3, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.settings_adv_warn),
                            style = OuTheme.typography.small,
                            color = c.accentColor3,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    stringResource(R.string.settings_adv_help),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(8.dp))
                PathRow(
                    label = stringResource(R.string.settings_dir_voicebank),
                    uri = state.settings.voicebankTreeUri,
                    onPick = { onPickDirectory(ResourceKind.VOICEBANK) },
                    onOpen = {
                        val u = state.settings.voicebankTreeUri
                        if (u.isNotBlank()) openTree(ctx, u)
                    },
                    onClear = { vm.releaseTree(ResourceKind.VOICEBANK) },
                )
                Spacer(Modifier.height(8.dp))
                PathRow(
                    label = stringResource(R.string.settings_dir_plugin),
                    uri = state.settings.pluginTreeUri,
                    onPick = { onPickDirectory(ResourceKind.PLUGIN) },
                    onOpen = {
                        val u = state.settings.pluginTreeUri
                        if (u.isNotBlank()) openTree(ctx, u)
                    },
                    onClear = { vm.releaseTree(ResourceKind.PLUGIN) },
                )
                Spacer(Modifier.height(8.dp))
                PathRow(
                    label = stringResource(R.string.settings_dir_phonemizer),
                    uri = state.settings.phonemizerTreeUri,
                    onPick = { onPickDirectory(ResourceKind.PHONEMIZER) },
                    onOpen = {
                        val u = state.settings.phonemizerTreeUri
                        if (u.isNotBlank()) openTree(ctx, u)
                    },
                    onClear = { vm.releaseTree(ResourceKind.PHONEMIZER) },
                )
            }

                    }
                }

                if (settingsCat == 3) {
                    Column(Modifier.fillMaxWidth()) {
            OuGroupBox(
                title = stringResource(R.string.settings_maint_title),
                collapsible = true,
                summary = stringResource(R.string.settings_maint_summary),
            ) {
                Text(
                    stringResource(R.string.settings_clear_cache_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OuButton(
                        stringResource(R.string.settings_clear_cache),
                        onClick = { vm.clearDownloadCache() },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.delete,
                    )
                    Spacer(Modifier.width(6.dp))
                    OuButton(
                        stringResource(R.string.settings_probe_now),
                        onClick = { vm.refreshNetwork() },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.refresh,
                    )
                }
                Text(
                    stringResource(R.string.settings_probe_now_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }

            Spacer(Modifier.height(12.dp))
            OuGroupBox(
                title = stringResource(R.string.settings_reset_title),
                collapsible = true,
                summary = stringResource(R.string.settings_reset_summary),
            ) {
                Text(
                    stringResource(R.string.settings_reset_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OuButton(
                        stringResource(R.string.settings_reset_button),
                        onClick = { showResetDialog = true },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.refresh,
                    )
                }
            }

                    }
                }

                if (settingsCat == 4) {
                    Column(Modifier.fillMaxWidth()) {
            OuGroupBox(
                title = stringResource(R.string.settings_about_title),
                collapsible = true,
                defaultExpanded = true,
                summary = stringResource(R.string.settings_about_summary),
                onHeaderClick = {
                    aboutTaps++
                    if (aboutTaps >= 5) {
                        aboutTaps = 0
                        showDevPassword = true
                        devHintShown = true
                    }
                },
            ) {
                Text(
                    stringResource(R.string.settings_about_risk),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("UtauLibrary Mobile v${BuildConfig.VERSION_NAME}", style = OuTheme.typography.body, color = c.foreground)
                    Spacer(Modifier.width(6.dp))
                    // 【测试版标记】圆角小方框，紧跟在版本号后面。
                    // 全局风格是直角，这里单独用 4dp 圆角 —— 徽标类元素需要和正文区分开。
                    val betaShape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    Box(
                        Modifier
                            .background(c.accent.copy(alpha = 0.16f), betaShape)
                            .border(1.dp, c.accent, betaShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            stringResource(R.string.about_beta),
                            style = OuTheme.typography.small,
                            color = c.accent,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    OuTag("MIT")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_about_credit),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }

            // ---------------------------------------------------------------- 更新日志
            Spacer(Modifier.height(12.dp))
            OuGroupBox(
                title = stringResource(R.string.changelog_title),
                collapsible = true,
                defaultExpanded = false,
                summary = stringResource(R.string.changelog_summary),
            ) {
                Text(
                    stringResource(R.string.changelog_v101_heading),
                    style = OuTheme.typography.body,
                    color = c.foreground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.changelog_v101_body),
                    style = OuTheme.typography.small,
                    color = c.foreground,
                )
            }

            // ---------------------------------------------------------------- 关于作者
            Spacer(Modifier.height(12.dp))
            OuGroupBox(
                title = stringResource(R.string.about_author_title),
                collapsible = true,
                defaultExpanded = true,
                summary = stringResource(R.string.about_author_summary),
            ) {
                // 作者主页卡片（B 站账号页截图）。点图片和点按钮效果一样，都跳主页。
                val actx = androidx.compose.ui.platform.LocalContext.current
                val openAuthor = {
                    runCatching {
                        actx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://space.bilibili.com/3546839477390121"),
                            )
                        )
                    }
                    Unit
                }
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.author_card),
                    contentDescription = stringResource(R.string.about_author_contact),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.border)
                        // 去掉整块涟漪，和 App 其他卡片一致；点击照样生效
                        .clickable(
                            interactionSource = androidx.compose.runtime.remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                        ) { openAuthor() },
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.about_author_message),
                    style = OuTheme.typography.small,
                    color = c.foreground,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OuButton(
                        stringResource(R.string.about_author_contact),
                        onClick = { openAuthor() },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.open,
                    )
                }
            }

                    }
                }

            // 开发者模式：密码 + 面板
            if (showDevPassword) {
                DeveloperPasswordDialog(
                    onSuccess = {
                        showDevPassword = false
                        showDevTools = true
                    },
                    onDismiss = { showDevPassword = false },
                )
            }
            if (showDevTools) {
                DeveloperScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    onDismiss = { showDevTools = false },
                    onWipeData = { vm.wipeAllUserData() },
                )
            }
            // 连点过程中的轻提示（第 3 次起）
            if (devHintShown && !showDevPassword && !showDevTools && aboutTaps >= 3) {
                Text(
                    stringResource(R.string.dev_taps_hint, 5 - aboutTaps),
                    style = OuTheme.typography.small,
                    color = c.accent,
                )
            }

            // 重置设置的二次确认
            if (showResetDialog) {
                OuDialog(
                    title = stringResource(R.string.settings_reset_title),
                    message = stringResource(R.string.settings_reset_confirm),
                    confirmText = stringResource(R.string.settings_reset_button),
                    dismissText = stringResource(R.string.cancel),
                    destructive = true,
                    onConfirm = {
                        showResetDialog = false
                        vm.resetAllSettings()
                    },
                    onDismiss = { showResetDialog = false },
                )
            }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PathRow(
    label: String,
    uri: String,
    onPick: () -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val c = OuTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = OuTheme.typography.body, color = c.foreground, modifier = Modifier.width(72.dp))
            if (uri.isBlank()) {
                OuTag(stringResource(R.string.tag_unauthorized), tone = c.accentColor3)
            } else {
                OuTag(stringResource(R.string.tag_authorized), accent = true)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            uri.ifBlank { stringResource(R.string.settings_not_selected_dir) },
            style = OuTheme.typography.small,
            color = c.foregroundDisabled,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            OuButton(if (uri.isBlank()) stringResource(R.string.action_authorize) else stringResource(R.string.settings_btn_change), onClick = onPick, leadingIconRes = OuIcons.folder)
            if (uri.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                OuButton(stringResource(R.string.action_open), onClick = onOpen, variant = OuButtonVariant.Clear, leadingIconRes = OuIcons.open)
                Spacer(Modifier.width(6.dp))
                OuButton(stringResource(R.string.settings_btn_release), onClick = onClear, variant = OuButtonVariant.Clear)
            }
        }
    }
}

private fun openTree(ctx: android.content.Context, uri: String) {
    runCatching {
        val u = android.net.Uri.parse(uri)
        val intent = com.utaumarket.mobile.saf.SafManager.openFolderIntent(
            ctx,
            u,
            com.utaumarket.mobile.saf.SafManager.treeDocId(u) ?: return,
        )
        ctx.startActivity(intent)
    }
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** 语言选项："" 表示跟随系统。标签取字符串资源，随语言切换。 */
@Composable
internal fun langOptions(): List<Pair<String, String>> = listOf(
    "" to stringResource(R.string.settings_lang_system),
    "zh-CN" to stringResource(R.string.lang_zh_cn_label),
    "zh-TW" to stringResource(R.string.lang_zh_tw_label),
    "en" to "English",
    "ja" to "日本語",
)

@Composable
internal fun langLabel(tag: String): String =
    langOptions().firstOrNull { it.first == tag }?.second.orEmpty()


/**
 * 配色方案预览卡片。
 *
 * 参考 PCL2 的主题选择器：直接画出「用这套方案会长什么样」，
 * 而不是只给一个色块 —— 用户能一眼看出背景、卡片、强调色的关系。
 */
@Composable
private fun SchemePreviewCard(
    scheme: com.utaumarket.mobile.ui.theme.ColorScheme,
    dark: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 【预览也要过渡】之前是直接 paletteFor() 取色，切换明暗/方案时缩略图瞬间变色，
    // 和外面已经渐变过去的界面不同步，看着像"框里闪了一下"。
    // 这里给每张卡片单独做 320ms 插值，时机和 OuTheme 一致。
    val targetPal = com.utaumarket.mobile.ui.theme.paletteFor(scheme, dark)
    var prevPal by remember { mutableStateOf(targetPal) }
    val fromPal = remember(targetPal) { prevPal }
    val palAnim = remember(targetPal) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(palAnim) {
        palAnim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 320,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        )
        prevPal = targetPal
    }
    val pal = if (palAnim.value >= 1f) targetPal
              else com.utaumarket.mobile.ui.theme.lerpColors(fromPal, targetPal, palAnim.value)
    val label = when (java.util.Locale.getDefault().language) {
        "ja" -> scheme.ja
        "zh" -> if (java.util.Locale.getDefault().country in listOf("TW", "HK", "MO")) scheme.zhtw else scheme.zh
        "en" -> scheme.en
        else -> scheme.zh
    }
    // 【不再整块铺配色】
    // 之前是把卡片底色直接画成"该方案的背景色"，在深色方案下会出现一排
    // 刺眼的白块 —— 和当前界面完全脱节（用户反馈"太突兀"）。
    // 现在卡片底色跟随**当前主题**，只用三枚色票表达该方案：
    // 底色 / 文字色 / 强调色。整体像同一张界面里的选择器，不会跳出来。
    val cur = OuTheme.colors
    androidx.compose.foundation.layout.Box(
        modifier
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) cur.accent else cur.border,
            )
            .background(cur.backgroundPointerOver)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Column {
            Text(label, style = OuTheme.typography.body, color = cur.foreground, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 【迷你示意图】左边是该方案的"界面缩略"：底色 + 两条文字色横条。
                // 之前把文字色单独做成一块实心色票，浅色方案下就成了一个黑方块，
                // 看着像坏掉的图。改成"底色上画文字条"，颜色关系一眼就懂。
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .background(pal.background)
                        .border(1.dp, cur.border)
                        .padding(4.dp),
                ) {
                    // 【不要用纯文字色】浅色方案的正文字色接近纯黑，画出来是一条死黑的杠。
                    // 改成"背景色 → 文字色"的插值：只表达对比强度，不会撞出纯黑/纯白。
                    val barStrong = androidx.compose.ui.graphics.lerp(pal.background, pal.foreground, 0.55f)
                    val barWeak = androidx.compose.ui.graphics.lerp(pal.background, pal.foreground, 0.30f)
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth(0.66f)
                                .height(3.dp)
                                .background(barStrong),
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.42f)
                                .height(3.dp)
                                .background(barWeak),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                // 强调色票
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .width(22.dp)
                        .height(22.dp)
                        .background(pal.accent)
                        .border(1.dp, cur.border),
                )
            }
        }
    }
}

