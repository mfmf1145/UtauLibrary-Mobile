package com.utaumarket.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.ProjectItem
import com.utaumarket.mobile.data.model.ProjectKind
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuChip
import com.utaumarket.mobile.ui.components.OuDialog
import com.utaumarket.mobile.ui.components.OuDivider
import com.utaumarket.mobile.ui.components.OuFilterLabel
import com.utaumarket.mobile.ui.components.OuIconButton
import com.utaumarket.mobile.ui.components.OuInfoRow
import com.utaumarket.mobile.ui.components.OuListRow
import com.utaumarket.mobile.ui.components.OuTextField
import com.utaumarket.mobile.ui.components.OuTabRow
import com.utaumarket.mobile.ui.components.OuTag
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.vm.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USTX / UST 工程管理。
 *
 * 说明：这个页面**只做管理和查看**，不编辑、不渲染 —— 那些是 OpenUtau 的活。
 * 这里最有价值的是「工程用到哪些歌手」以及**哪些还没装**：
 * 打开老工程发现歌手缺失，是最常见的困扰。
 */
@Composable
fun ProjectsScreen(
    vm: AppViewModel,
    state: UiState,
    onGoSettings: () -> Unit,
    /** 工程缺歌手时，把歌名带到声库页直接搜索 */
    onSearchSinger: (String) -> Unit = {},
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val ctx = LocalContext.current

    var query by rememberSaveable { mutableStateOf("") }
    // 「打开」先弹说明再跳转（直接跳走的话提示留在后台，用户根本看不到）
    var openHint by remember { mutableStateOf<com.utaumarket.mobile.data.model.ProjectItem?>(null) }
    var sort by rememberSaveable { mutableIntStateOf(0) } // 0 最近修改 / 1 名称 / 2 音符数
    // 分类：0 全部 / 1 USTX / 2 UST / 3 MIDI
    var kindTab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ProjectItem?>(null) }
    var pendingDelete by remember { mutableStateOf<ProjectItem?>(null) }

    LaunchedEffect(state.settings.rootTreeUri) {
        if (state.projects.isEmpty()) vm.refreshProjects()
    }

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val list = remember(state.projects, query, sort, kindTab) {
        val q = query.trim()
        var l = state.projects
        if (kindTab > 0) {
            val k = listOf(
                com.utaumarket.mobile.data.model.ProjectKind.USTX,
                com.utaumarket.mobile.data.model.ProjectKind.UST,
                com.utaumarket.mobile.data.model.ProjectKind.MIDI,
            )[kindTab - 1]
            l = l.filter { it.kind == k }
        }
        if (q.isNotBlank()) {
            l = l.filter {
                it.displayName.contains(q, true) || it.fileName.contains(q, true) ||
                    it.singers.any { s -> s.contains(q, true) }
            }
        }
        when (sort) {
            1 -> l.sortedBy { it.displayName.lowercase() }
            2 -> l.sortedByDescending { it.noteCount }
            else -> l.sortedByDescending { it.lastModified }
        }
    }

    // 横屏的宽度统一在 AppRoot 里约束（所有页面一致），这里不再单独加 padding
    Column(Modifier.fillMaxSize()) {
        if (state.settings.rootTreeUri.isBlank()) {
            PathBanner(
                text = stringResource(R.string.proj_no_root),
                actionLabel = stringResource(R.string.action_authorize),
                onAction = onGoSettings,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.pagePadding, vertical = if (isLandscape()) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OuTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.proj_search_hint),
                leadingRes = OuIcons.search,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OuIconButton(
                androidx.compose.ui.res.painterResource(OuIcons.refresh),
                stringResource(R.string.settings_btn_rescan),
                { vm.refreshProjects(force = true) },
            )
        }

        // 分类标签：USTX / UST / MIDI 分开看
        OuTabRow(
            tabs = listOf(
                stringResource(R.string.all),
                "USTX",
                "UST",
                "MIDI",
            ),
            selectedIndex = kindTab,
            onSelect = { kindTab = it },
        )

        // 【紧凑表头】之前"排序"小标题、排序 chips、数量各占一行，把列表挤下去半屏。
        // 现在合成一行：左边排序 chips，右边数量。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.pagePadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                stringResource(R.string.proj_sort_modified) to 0,
                stringResource(R.string.sort_name) to 1,
                stringResource(R.string.proj_sort_notes) to 2,
            ).forEach { (label, i) ->
                OuChip(label, sort == i) { sort = i }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            if (!state.projectsLoading && list.isNotEmpty()) {
                Text(
                    stringResource(R.string.project_count, list.size),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                    maxLines = 1,
                )
            }
        }
        OuDivider()

        if (state.projectsLoading && state.projects.isEmpty()) {
            EmptyHint(stringResource(R.string.project_scanning))
        } else if (list.isEmpty()) {
            EmptyHint(
                if (state.settings.rootTreeUri.isBlank()) {
                    stringResource(R.string.proj_empty_no_root)
                } else {
                    stringResource(R.string.project_empty)
                }
            )
        } else {
            // 横屏跟插件页一样用多列网格（用户要求一致）
            ResourceList(
                items = list,
                columns = listColumns(),
                key = { it.uri + it.fileName },
            ) { p ->
                OuListRow(onClick = { detail = p }, accent = c.accentLight1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.displayName,
                                    style = OuTheme.typography.cardTitle,
                                    color = c.foreground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(6.dp))
                                OuTag(if (p.isUst) "UST" else "USTX")
                            }
                            // buildList 的 lambda 不是 composable，文案必须先在外面取好
                            val notesText = if (p.noteCount > 0) stringResource(R.string.proj_notes_count, p.noteCount) else ""
                            val tracksText = if (p.trackCount > 0) stringResource(R.string.proj_tracks_count, p.trackCount) else ""
                            val meta = buildList {
                                if (p.noteCount > 0) add(notesText)
                                if (p.trackCount > 0) add(tracksText)
                                add(humanSize(p.sizeBytes))
                                if (p.lastModified > 0) add(fmt.format(Date(p.lastModified)))
                            }.joinToString(" · ")
                            Text(
                                meta,
                                style = OuTheme.typography.cardMeta,
                                color = c.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (p.singers.isNotEmpty()) {
                                val missing = p.singers.filter { s -> !vm.isVoicebankInstalled(s) }
                                val singersText = if (missing.isEmpty()) {
                                    stringResource(R.string.proj_singers, p.singers.joinToString("、"))
                                } else {
                                    stringResource(R.string.project_missing_singers, missing.joinToString("、"))
                                }
                                Text(
                                    singersText,
                                    style = OuTheme.typography.small,
                                    color = if (missing.isEmpty()) c.foregroundDisabled else c.accentColor3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // snack 文案在 composable 里取好，openInEditor 不是 composable
                        val openCopiedMsg = stringResource(R.string.proj_snack_no_view, readablePath(p))
                        val openNoAppMsg = stringResource(R.string.proj_snack_no_app, readablePath(p))
                        val a11yOpeningMsg = stringResource(R.string.a11y_opening)
                        OuButton(
                            if (isLandscape()) "" else stringResource(R.string.action_open),
                            onClick = { openHint = p },
                            variant = OuButtonVariant.Clear,
                            leadingIconRes = OuIcons.open,
                        )
                    }
                }
            }
        }
    }

    // 跳转前的说明弹窗：直接跳走的话提示留在后台，用户看不到
    val hp = openHint
    if (hp != null) {
        // 文案必须在 composable 里先取好：onConfirm 的 lambda 不是 composable
        val hintMsg = stringResource(R.string.proj_open_hint, readablePath(hp))
        val hintCopied = stringResource(R.string.proj_snack_no_view, readablePath(hp))
        val hintNoApp = stringResource(R.string.proj_snack_no_app, readablePath(hp))
        OuDialog(
            title = stringResource(R.string.action_open),
            message = hintMsg,
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                openHint = null
                openInEditor(ctx, vm, hp, hintCopied, hintNoApp)
            },
            onDismiss = { openHint = null },
        ) { }
    }

    val p = detail
    if (p != null) {
        val missing = p.singers.filter { s -> !vm.isVoicebankInstalled(s) }
        // snack 文案在 composable 里取好，openInEditor 不是 composable
        val openCopiedMsg = stringResource(R.string.proj_snack_no_view, readablePath(p))
        val openNoAppMsg = stringResource(R.string.proj_snack_no_app, readablePath(p))
        OuDialog(
            title = p.displayName,
            confirmText = stringResource(R.string.close),
            dismissText = null,
            onConfirm = { detail = null },
            onDismiss = { detail = null },
        ) {
            Column(Modifier.fillMaxWidth()) {
                OuInfoRow(stringResource(R.string.proj_field_file), p.fileName)
                OuInfoRow(
                    stringResource(R.string.field_format),
                    if (p.isUst) stringResource(R.string.proj_format_ust) else stringResource(R.string.proj_format_ustx),
                )
                OuInfoRow(stringResource(R.string.field_size), humanSize(p.sizeBytes))
                if (p.lastModified > 0) OuInfoRow(stringResource(R.string.proj_field_modified), fmt.format(Date(p.lastModified)))
                if (p.trackCount > 0) OuInfoRow(stringResource(R.string.proj_field_tracks), "${p.trackCount}")
                if (p.noteCount > 0) OuInfoRow(stringResource(R.string.proj_field_notes), "${p.noteCount}")
                if (p.singers.isNotEmpty()) {
                    OuInfoRow(stringResource(R.string.proj_field_singers), p.singers.joinToString("、"))
                }
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.proj_missing_singers_detail, missing.joinToString("、")),
                        style = OuTheme.typography.small,
                        color = c.accentColor3,
                    )
                    Spacer(Modifier.height(6.dp))
                    // 【一键跳转】直接去声库页按歌手名搜索，省掉手动复制粘贴
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OuButton(
                            stringResource(R.string.proj_search_singer, missing.first()),
                            onClick = {
                                detail = null
                                onSearchSinger(missing.first())
                            },
                            variant = OuButtonVariant.Clear,
                            leadingIconRes = OuIcons.search,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val a11yOpeningMsg = stringResource(R.string.a11y_opening)
                    // 蓝色主按钮 = 打开（有新的无障碍自动打开就直接用，没开就退回"启动+剪贴板"）
                    OuButton(
                        stringResource(R.string.action_open),
                        onClick = {
                            openHint = p
                        },
                        variant = OuButtonVariant.Accent,
                        leadingIconRes = OuIcons.open,
                    )
                    Spacer(Modifier.width(8.dp))
                    OuButton(
                        stringResource(R.string.action_delete),
                        onClick = { pendingDelete = p; detail = null },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.delete,
                    )
                }
            }
        }
    }

    val projectToDelete = pendingDelete
    if (projectToDelete != null) {
        val p = projectToDelete
        OuDialog(
            title = stringResource(R.string.proj_delete_title),
            message = stringResource(R.string.proj_delete_message, p.displayName, p.fileName),
            confirmText = stringResource(R.string.action_delete),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                vm.deleteProject(p)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}


/**
 * 「在 OpenUtau 里打开」。
 *
 * 实测（dumpsys package）OpenUtau Mobile 只注册了 MAIN/LAUNCHER，
 * **没有任何接收 .ustx 的 VIEW/SEND 过滤器** —— 所以用 Intent 把工程文件交给它是不可能的，
 * 隐式 VIEW 只会落到系统选择器、进而被压缩包查看器接走，然后报"无法打开"。
 *
 * 因此这里的做法是：
 * 1) 把工程路径复制到剪贴板；
 * 2) 启动 OpenUtau Mobile；
 * 3) 告诉用户去它自己的「打开」对话框里选这个文件。
 */
/**
 * 「打开」按钮的统一入口：
 * - 无障碍服务已启用 → 交给它自动点 OpenUtau 的打开菜单；
 * - 没启用 → 退回「启动 OpenUtau + 路径复制到剪贴板 + 提示」。
 */
private fun openSmart(
    ctx: android.content.Context,
    vm: com.utaumarket.mobile.ui.vm.AppViewModel,
    p: com.utaumarket.mobile.data.model.ProjectItem,
    path: String,
    copiedMsg: String,
    noAppMsg: String,
    openingMsg: String,
) {
    // 无障碍自动打开已下线（用户决定）：回到「启动 OpenUtau + 路径复制到剪贴板」
    openInEditor(ctx, vm, p, copiedMsg, noAppMsg)
}

private fun openInEditor(
    ctx: android.content.Context,
    vm: AppViewModel,
    p: ProjectItem,
    // 这个函数不是 composable，文案由调用方在 composable 里取好传进来
    msgCopied: String,
    msgNoApp: String,
) {
    val path = readablePath(p)
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    cm?.setPrimaryClip(android.content.ClipData.newPlainText("project", path))

    val launch = ctx.packageManager.getLaunchIntentForPackage(OPENUTAU_PKG)
    if (launch != null) {
        runCatching { ctx.startActivity(launch) }
        vm.snack(msgCopied)
    } else {
        vm.snack(msgNoApp)
    }
}

/** 把 SAF 的 content:// 还原成人类可读的 /storage/... 路径，方便用户去别的程序里找 */
private fun readablePath(p: ProjectItem): String {
    val full = p.uri
    val idx = full.indexOf("/tree/")
    if (idx >= 0) {
        val tree = full.substring(idx + 6).substringBefore("/document/")
        val doc = full.substringAfter("/document/", "")
        if (tree.isNotBlank() && doc.isNotBlank()) {
            return "/storage/emulated/0/" + java.net.URLDecoder.decode(doc, "UTF-8")
                .substringAfter("primary:", doc)
        }
    }
    return p.fileName
}

private const val OPENUTAU_PKG = "pers.vocoder712.openutaumobile"
