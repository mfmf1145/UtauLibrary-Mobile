package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.utaumarket.mobile.BuildConfig
import com.utaumarket.mobile.R
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuDialog
import com.utaumarket.mobile.ui.theme.OuTheme

/**
 * 开发者模式。
 *
 * 入口：设置 → 「关于与风险提示」分组标题连点 5 次 → 输入密码 0401。
 * 用途：调试时快速验证各种弹窗，以及把 App 恢复到「刚安装完」的状态。
 */
@Composable
fun DeveloperScreen(
    versionName: String,
    onDismiss: () -> Unit,
    onWipeData: () -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens

    // 弹窗预览状态
    var showDisclaimer by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }
    var showHostMissing by remember { mutableStateOf(false) }

    // 预览用的假数据：详情弹窗与版本选择弹窗都需要真实的 ResourceItem
    val demoItem = remember {
        com.utaumarket.mobile.data.model.ResourceItem(
            id = "demo",
            kind = com.utaumarket.mobile.data.model.ResourceKind.VOICEBANK,
            name = "示例声库 单独音",
            nameTranslated = "示例声库",
            author = "示例作者",
            language = "ja",
            description = "这是开发者模式生成的示例条目，用来预览资源详情弹窗的排版。",
            keywords = "demo",
            series = "示例声库",
            seriesZh = "示例声库",
        )
    }
    val demoGroup = remember {
        SeriesGroup(
            head = demoItem.copy(name = "示例系列 标准版"),
            versions = listOf(
                demoItem.copy(id = "demo1", name = "示例系列 标准版"),
                demoItem.copy(id = "demo2", name = "示例系列 Lite"),
                demoItem.copy(id = "demo3", name = "示例系列 连续音"),
            ),
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(c.background, RoundedCornerShape(d.overlayCornerRadius))
                .border(d.borderWidth, c.border, RoundedCornerShape(d.overlayCornerRadius))
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.dev_title), style = OuTheme.typography.title, color = c.foreground)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.dev_subtitle, versionName),
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
            )
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                DevSection(stringResource(R.string.dev_dialogs))
                DevButton(stringResource(R.string.dev_test_disclaimer)) { showDisclaimer = true }
                DevButton(stringResource(R.string.dev_test_notice)) { showNotice = true }
                DevButton(stringResource(R.string.dev_test_tutorial)) { showTutorial = true }
                DevButton(stringResource(R.string.dev_test_confirm)) { showConfirm = true }
                DevButton(stringResource(R.string.dev_test_detail)) { showDetail = true }
                DevButton(stringResource(R.string.dev_test_versions)) { showVersions = true }
                DevButton(stringResource(R.string.dev_test_duplicate)) { showDuplicate = true }
                DevButton(stringResource(R.string.dev_test_crash)) { showCrash = true }
                DevButton(stringResource(R.string.dev_test_host_missing)) { showHostMissing = true }

                Spacer(Modifier.height(12.dp))
                DevSection(stringResource(R.string.dev_danger))
                Text(
                    stringResource(R.string.dev_wipe_desc),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(6.dp))
                DevButton(stringResource(R.string.dev_wipe), danger = true) { showWipe = true }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OuButton(stringResource(R.string.close), onClick = onDismiss, variant = OuButtonVariant.Normal)
            }
        }
    }

    // ---------------- 弹窗预览 ----------------
    if (showDisclaimer) {
        DisclaimerDialog(onAccept = { showDisclaimer = false })
    }
    if (showNotice) {
        DownloadNoticeDialog(
            dontShowAgain = false,
            onDontShowAgainChange = {},
            onConfirm = { showNotice = false },
            onDismiss = { showNotice = false },
        )
    }
    if (showTutorial) {
        TutorialDialog(onDismiss = { showTutorial = false })
    }
    if (showConfirm) {
        OuDialog(
            title = stringResource(R.string.dev_test_confirm),
            message = stringResource(R.string.dev_test_confirm_body),
            onConfirm = { showConfirm = false },
            onDismiss = { showConfirm = false },
        )
    }
    if (showDetail) {
        ResourceDetailDialog(
            item = demoItem,
            installed = false,
            onDismiss = { showDetail = false },
            onDownload = { showDetail = false },
        )
    }
    if (showVersions) {
        ForksVersionsDialog(
            group = demoGroup,
            onDismiss = { showVersions = false },
            onPick = { showVersions = false },
        )
    }
    if (showDuplicate) {
        OuDialog(
            title = stringResource(R.string.dlg_exists_title),
            message = stringResource(R.string.dlg_exists_message, demoItem.name),
            confirmText = stringResource(R.string.dlg_continue_overwrite),
            onConfirm = { showDuplicate = false },
            onDismiss = { showDuplicate = false },
        )
    }
    if (showCrash) {
        OuDialog(
            title = stringResource(R.string.crash_title),
            confirmText = stringResource(R.string.crash_clear),
            dismissText = stringResource(R.string.close),
            onConfirm = { showCrash = false },
            onDismiss = { showCrash = false },
        ) {
            Text(
                stringResource(R.string.dev_test_crash_body),
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
            )
        }
    }
    if (showHostMissing) {
        OuDialog(
            title = stringResource(R.string.host_missing_title),
            message = stringResource(R.string.host_missing_message),
            confirmText = stringResource(R.string.host_missing_download),
            dismissText = stringResource(R.string.host_missing_later),
            onConfirm = { showHostMissing = false },
            onDismiss = { showHostMissing = false },
        )
    }
    if (showWipe) {
        OuDialog(
            title = stringResource(R.string.dev_wipe),
            message = stringResource(R.string.dev_wipe_confirm),
            destructive = true,
            confirmText = stringResource(R.string.dev_wipe),
            onConfirm = {
                showWipe = false
                onWipeData()
                onDismiss()
            },
            onDismiss = { showWipe = false },
        )
    }
}

@Composable
private fun DevSection(text: String) {
    val c = OuTheme.colors
    Text(text, style = OuTheme.typography.body, color = c.accent)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DevButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .border(d.borderWidth, if (danger) c.accentColor3 else c.border)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = OuTheme.typography.body,
            color = if (danger) c.accentColor3 else c.foreground,
        )
    }
}

/**
 * 开发者密码输入框。密码正确返回 true。
 * 密码写死在代码里（0401）—— 它只防误触，不是安全边界。
 */
@Composable
fun DeveloperPasswordDialog(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.background, RoundedCornerShape(d.overlayCornerRadius))
                .border(d.borderWidth, c.border, RoundedCornerShape(d.overlayCornerRadius))
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.dev_password_title), style = OuTheme.typography.title, color = c.foreground)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dev_password_desc),
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
            )
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it.filter { ch -> ch.isDigit() }.take(8); error = false },
                singleLine = true,
                textStyle = TextStyle(color = c.foreground, fontSize = OuTheme.typography.body.fontSize),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (error) c.accentColor3 else c.border)
                    .padding(10.dp),
            )
            if (error) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.dev_password_wrong),
                    style = OuTheme.typography.small,
                    color = c.accentColor3,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OuButton(stringResource(R.string.cancel), onClick = onDismiss, variant = OuButtonVariant.Clear)
                Spacer(Modifier.width(8.dp))
                OuButton(
                    stringResource(R.string.ok),
                    onClick = {
                        if (input == "0401") onSuccess() else error = true
                    },
                    variant = OuButtonVariant.Accent,
                )
            }
        }
    }
}
