package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.DownloadStatus
import com.utaumarket.mobile.data.model.DownloadTask
import com.utaumarket.mobile.data.model.InstallRule
import com.utaumarket.mobile.data.model.isActive
import com.utaumarket.mobile.data.model.progress
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuDivider
import com.utaumarket.mobile.ui.components.OuListRow
import com.utaumarket.mobile.ui.components.OuProgressBar
import com.utaumarket.mobile.ui.components.OuTag
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.vm.UiState

/** 预计剩余时间的可读写法；暂停或无法估算时返回 null */
@Composable
fun etaText(seconds: Long, running: Boolean): String? {
    if (!running || seconds <= 0) return null
    val m = seconds / 60
    val sec = seconds % 60
    return if (m >= 60) {
        val h = m / 60
        stringResource(R.string.dl_eta_hours, h, m % 60)
    } else if (m > 0) {
        stringResource(R.string.dl_eta_minutes, m, sec)
    } else {
        stringResource(R.string.dl_eta_seconds, sec)
    }
}

@Composable
private fun statusLabel(s: DownloadStatus): String = when (s) {
    DownloadStatus.QUEUED -> stringResource(R.string.dl_status_queued)
    DownloadStatus.DOWNLOADING -> stringResource(R.string.dl_status_downloading)
    DownloadStatus.EXTRACTING -> stringResource(R.string.dl_status_extracting)
    DownloadStatus.INSTALLING -> stringResource(R.string.dl_status_installing)
    DownloadStatus.AWAITING_CONFIRM -> stringResource(R.string.dl_status_awaiting_confirm)
    DownloadStatus.PAUSED -> stringResource(R.string.dl_status_paused)
    DownloadStatus.DONE -> stringResource(R.string.dl_status_done)
    DownloadStatus.FAILED -> stringResource(R.string.dl_status_failed)
    DownloadStatus.CANCELED -> stringResource(R.string.dl_status_canceled)
}

@Composable
fun DownloadsScreen(vm: AppViewModel, state: UiState) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    // 直接订阅下载控制器：状态一有变化立刻反映到界面，不再经过 UiState 中转
    val tasks by com.utaumarket.mobile.download.DownloadController.tasks
        .collectAsStateWithLifecycle()
    // 【界面友好】用户点了暂停但服务还没回调时，这里也立刻算作「已暂停」——
    // 否则会出现"点了暂停，卡片还写着下载中、进度也不动"的卡死观感。
    fun effStatus(t: com.utaumarket.mobile.data.model.DownloadTask) =
        if (com.utaumarket.mobile.download.DownloadController.isPaused(t.id) &&
            (t.status == DownloadStatus.QUEUED || t.status == DownloadStatus.DOWNLOADING ||
                t.status == DownloadStatus.EXTRACTING || t.status == DownloadStatus.INSTALLING)
        ) DownloadStatus.PAUSED else t.status

    // 按钮内反馈：点完立刻在按钮上显示"暂停中…"，1.5 秒后恢复
    val pendingActions by vm.pendingAction.collectAsStateWithLifecycle()

    val activeCount = tasks.count { it.isActive && effStatus(it) != DownloadStatus.PAUSED }
    val pausedCount = tasks.count { effStatus(it) == DownloadStatus.PAUSED }

    Column(Modifier.fillMaxSize()) {
        // 【分两行】之前统计文字和四个按钮挤在同一行，
        // weight(1f) 的文本被按钮压到只剩几个字符宽，
        // 变成"进行中 0 · 已暂停 0 · 共 0"竖着排（用户报的比例问题）。
        // 现在统计单独一行，按钮单独一行且可横向滚动。
        Column(Modifier.fillMaxWidth().padding(horizontal = d.pagePadding, vertical = 6.dp)) {
            Text(
                stringResource(R.string.dl_stats, activeCount, pausedCount, state.tasks.size),
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            // 批量操作：暂停 / 继续 / 清空已完成 / 全部取消
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OuButton(
                    stringResource(R.string.dl_pause_all),
                    onClick = { vm.pauseAllTasks() },
                    variant = OuButtonVariant.Clear,
                    enabled = activeCount > 0,
                )
                OuButton(
                    stringResource(R.string.dl_resume_all),
                    onClick = { vm.resumeAllTasks() },
                    variant = OuButtonVariant.Clear,
                    enabled = pausedCount > 0 || tasks.any { it.status == DownloadStatus.FAILED },
                )
                OuButton(
                    stringResource(R.string.dl_clear_finished),
                    onClick = { vm.clearFinished() },
                    variant = OuButtonVariant.Clear,
                    enabled = tasks.any {
                        it.status == DownloadStatus.DONE || it.status == DownloadStatus.CANCELED
                    },
                )
                OuButton(
                    stringResource(R.string.dl_cancel_all),
                    onClick = { vm.cancelAllTasks() },
                    variant = OuButtonVariant.Clear,
                    enabled = activeCount > 0 || pausedCount > 0,
                )
            }
        }
        OuDivider()
        // NOTE: 上面多出来的一层花括号是 Column(统计行) 的闭合

        if (tasks.isEmpty()) {
            EmptyHint(stringResource(R.string.dl_empty))
        } else {
            ResourceList(
                items = tasks,
                columns = if (isLandscape()) 2 else 1,
                key = { it.id },
            ) { task ->
                // 用「有效状态」渲染：用户已点暂停但服务还没回调时，直接显示成已暂停
                val shown = if (com.utaumarket.mobile.download.DownloadController.isPaused(task.id) &&
                    (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.DOWNLOADING ||
                        task.status == DownloadStatus.EXTRACTING || task.status == DownloadStatus.INSTALLING)
                ) task.copy(status = DownloadStatus.PAUSED, speedBps = 0) else task
                TaskRow(
                    task = shown,
                    pendingLabel = pendingActions[task.id] ?: "",
                    onPause = { vm.pauseTask(task) },
                    onResume = { vm.resumeTask(task) },
                    onCancel = { vm.cancelTask(task) },
                    onRetry = { vm.retryTask(task) },
                    onRemove = { vm.removeTask(task) },
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    /** 点击后 1.5 秒内的"暂停中…"之类文案，直接显示在按钮上（不用 Toast 弹窗） */
    pendingLabel: String = "",
) {
    val c = OuTheme.colors

    OuListRow(
        accent = when (task.status) {
            DownloadStatus.DONE -> c.accent
            DownloadStatus.FAILED -> c.accentColor3
            DownloadStatus.PAUSED -> c.accentColor2
            else -> c.accentLight1
        },
        contentPadding = 12.dp,
    ) {
        // 第一行：名称 + 类别 + 状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                task.name,
                style = OuTheme.typography.cardTitle,
                color = c.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            // 分支改版本质是 PLUGIN 类型，但用户看到「插件」会误解，
            // 这里单独标成「分支改版」。
            OuTag(
                // 分支改版的判定：清单里的 is_fork，或安装规则是 APK（改版包）
                if (task.fork || task.installRule == InstallRule.APK) stringResource(com.utaumarket.mobile.R.string.nav_forks)
                else task.kind.labelText(),
            )
            Spacer(Modifier.width(4.dp))
            OuTag(
                statusLabel(task.status),
                accent = task.status == DownloadStatus.DOWNLOADING,
                tone = when (task.status) {
                    DownloadStatus.FAILED -> c.accentColor3
                    DownloadStatus.DONE -> c.accent
                    DownloadStatus.PAUSED -> c.accentColor2
                    else -> null
                },
            )
        }

        Spacer(Modifier.height(6.dp))

        // 进度条：下载中/解压中/安装中/暂停都展示
        when (task.status) {
            // 服务器没给 Content-Length 时 totalBytes 为 0，
            // 固定进度的条子会永远停在 0%、看着像卡死 —— 改用不确定态动画。
            DownloadStatus.DOWNLOADING -> if (task.totalBytes > 0) {
                OuProgressBar(task.progress, height = 6.dp)
            } else {
                OuProgressBar(0f, height = 6.dp, indeterminate = true)
            }
            // 暂停时**必须是静止的**：之前这里还用不确定动画，
            // 用户看到"已暂停但进度条还在动"，以为没停下来。
            DownloadStatus.PAUSED -> OuProgressBar(
                if (task.totalBytes > 0) task.progress else 0f,
                height = 6.dp,
                indeterminate = false,
            )
            // 安装阶段现在有真实字节进度，能算百分比的就画确定进度条
            DownloadStatus.INSTALLING -> if (task.totalBytes > 0) {
                OuProgressBar(task.progress, height = 6.dp)
            } else {
                OuProgressBar(0f, height = 6.dp, indeterminate = true)
            }
            DownloadStatus.EXTRACTING, DownloadStatus.QUEUED ->
                OuProgressBar(0f, height = 6.dp, indeterminate = true)
            else -> Unit
        }

        Spacer(Modifier.height(6.dp))

        // 第二行：字节 / 速度 / 百分比 或 状态说明
        when (task.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
                // 文案要先在 composable 上下文里取好：buildString 的 lambda 不是 composable
                val eta = etaText(task.etaSeconds, task.status == DownloadStatus.DOWNLOADING)
                val etaLeft = if (eta != null) stringResource(R.string.dl_eta_remaining, eta) else ""
                Text(
                    buildString {
                        // humanSize(0) 返回空串，直接拼会出现 "/ ? · /s" 这种残句
                        val doneText = humanSize(task.bytesDownloaded).ifBlank { "0 B" }
                        val totalText = if (task.totalBytes > 0) humanSize(task.totalBytes) else "未知"
                        append(doneText).append(" / ").append(totalText)
                        val spd = humanSize(task.speedBps)
                        if (spd.isNotBlank()) {
                            append("  ·  ").append(spd).append("/s")
                        }
                        if (task.totalBytes > 0) {
                            append("  ·  ").append((task.progress * 100).toInt()).append("%")
                        }
                        if (etaLeft.isNotBlank()) {
                            append("  ·  ").append(etaLeft)
                        }
                    },
                    style = OuTheme.typography.small,
                    color = c.foreground,
                )
            }
            DownloadStatus.FAILED -> Text(
                task.error.ifBlank { task.message },
                style = OuTheme.typography.small,
                color = c.accentColor3,
            )
            DownloadStatus.INSTALLING -> if (task.totalBytes > 0) {
                Text(
                    stringResource(
                        R.string.dl_install_progress,
                        humanSize(task.bytesDownloaded),
                        humanSize(task.totalBytes),
                        (task.progress * 100).toInt(),
                    ),
                    style = OuTheme.typography.small,
                    color = c.foreground,
                )
            } else {
                Text(
                    task.message,
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DownloadStatus.DONE -> Text(
                stringResource(R.string.dl_installed_to, task.installedTo),
                style = OuTheme.typography.small,
                color = c.accent,
            )
            else -> Text(
                task.message,
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))

        // 操作区
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点击后 1.5 秒内**被点的那颗按钮**直接换成"暂停中…/继续中…/取消中…"并置灰。
            // 用户要求"要按下按钮的那种反馈，不要弹小窗"，而且取消按钮也要有。
            val isPausePending = pendingLabel.startsWith("暂停")
            val isResumePending = pendingLabel.startsWith("继续")
            val isCancelPending = pendingLabel.startsWith("取消")
            val busyAny = pendingLabel.isNotBlank()
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED,
                DownloadStatus.EXTRACTING, DownloadStatus.INSTALLING -> {
                    OuButton(
                        if (isPausePending) pendingLabel else stringResource(R.string.dl_pause),
                        onClick = onPause,
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = if (isPausePending) null else OuIcons.pause,
                        enabled = !busyAny,
                    )
                    Spacer(Modifier.width(6.dp))
                    OuButton(
                        if (isCancelPending) pendingLabel else stringResource(R.string.action_cancel),
                        onClick = onCancel,
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = if (isCancelPending) null else OuIcons.close,
                        enabled = !busyAny,
                    )
                }
                DownloadStatus.PAUSED -> {
                    OuButton(
                        if (isResumePending) pendingLabel else stringResource(R.string.dl_resume),
                        onClick = onResume,
                        variant = OuButtonVariant.Accent,
                        leadingIconRes = if (isResumePending) null else OuIcons.play,
                        enabled = !busyAny,
                    )
                    Spacer(Modifier.width(6.dp))
                    OuButton(
                        if (isCancelPending) pendingLabel else stringResource(R.string.action_cancel),
                        onClick = onCancel,
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = if (isCancelPending) null else OuIcons.close,
                        enabled = !busyAny,
                    )
                }
                DownloadStatus.FAILED -> {
                    OuButton(stringResource(R.string.dl_retry), onClick = onRetry, variant = OuButtonVariant.Accent, leadingIconRes = OuIcons.refresh)
                    Spacer(Modifier.width(6.dp))
                    OuButton(stringResource(R.string.dl_remove), onClick = onRemove, variant = OuButtonVariant.Clear, leadingIconRes = OuIcons.close)
                }
                DownloadStatus.DONE, DownloadStatus.CANCELED, DownloadStatus.AWAITING_CONFIRM -> {
                    // 【分支改版】下载完会自动拉起安装器；用户若点了取消，
                    // 包还在 cache/utm/apk 里（路径记在 installedTo），这里给一个重装入口，
                    // 不必重新下载几百 MB。
                    val dctx = androidx.compose.ui.platform.LocalContext.current
                    if (task.fork && task.installedTo.isNotBlank() &&
                        java.io.File(task.installedTo).isFile
                    ) {
                        OuButton(
                            stringResource(R.string.dl_reinstall),
                            onClick = {
                                runCatching {
                                    com.utaumarket.mobile.download.ApkInstaller.install(
                                        dctx, java.io.File(task.installedTo),
                                    )
                                }
                            },
                            variant = OuButtonVariant.Accent,
                            leadingIconRes = OuIcons.download,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    OuButton(stringResource(R.string.dl_remove_record), onClick = onRemove, variant = OuButtonVariant.Clear, leadingIconRes = OuIcons.close)
                }
            }
        }
    }
}
