package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import com.utaumarket.mobile.R
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme

/** 顶部提示条：对应 OpenUtau 的 WarningBrush / Border.tips 使用方式。 */
/**
 * 提示条：直接用 OpenUtau 的 WarningColor 作底色，不做成带边框的盒子，
 * 保持一条细线级的视觉重量。
 */
@Composable
fun PathBanner(text: String, actionLabel: String, onAction: (() -> Unit)?) {
    val c = OuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.warning)
            // 横屏垂直空间紧张，横幅压到一行
            .padding(
                start = 12.dp,
                end = 6.dp,
                top = if (isLandscape()) 1.dp else 4.dp,
                bottom = if (isLandscape()) 1.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(OuIcons.warning), null, tint = c.accentColor3, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = OuTheme.typography.small,
            color = if (c.isDark) c.foregroundPointerOver else c.foreground,
            maxLines = if (isLandscape()) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onAction != null) {
            OuButton(actionLabel, onClick = onAction, variant = OuButtonVariant.Clear)
        }
    }
}

@Composable
fun InfoBox(text: String) {
    val c = OuTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(c.warning)
            .border(1.dp, c.border)
            .padding(8.dp)
    ) {
        Text(text, style = OuTheme.typography.small, color = if (c.isDark) c.foregroundPointerOver else c.foreground)
    }
}

@Composable
fun EmptyHint(text: String) {
    val c = OuTheme.colors
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = OuTheme.typography.body, color = c.foregroundDisabled, textAlign = TextAlign.Center)
    }
}

/**
 * 资源列表容器：竖屏一列，横屏两列。横屏下屏幕很宽，单列会拉得过长，
 * 两列既好读也更好看。
 */
/**
 * 全局界面开关。
 *
 * 用普通单例而不是 CompositionLocal：动画开关只影响"列表项要不要包一层动画"，
 * 没必要为它把整棵树包进 Provider；设置变化时由 AppRoot 直接写这个值。
 */
object UiFlags {
    @Volatile
    var animations: Boolean = true
}

/**
 * 单个列表项的入场动画：淡入 + 轻微上移。
 * 关掉动画时直接渲染内容，不产生任何额外组合开销。
 */
@Composable
private fun AnimatedItem(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    // 【重要】这里不能用 AnimatedVisibility —— 它不可见时**完全不占布局**，
    // 在 LazyColumn 里滚动时就会出现一段段空白。
    // 改用只改 alpha 的做法：布局始终存在，只做淡入。
    val fade = androidx.compose.animation.core.Animatable(0f)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        fade.animateTo(1f, androidx.compose.animation.core.tween(160))
    }
    androidx.compose.foundation.layout.Box(
        Modifier.graphicsLayer { alpha = fade.value },
    ) {
        content()
    }
}

@Composable
fun <T> ResourceList(
    items: List<T>,
    columns: Int,
    key: (T) -> Any,
    /**
     * 变化时把列表滚回顶部。
     * 用户反馈：切换排序方式后还停在原来的位置，看起来像"没排序"。
     */
    resetKey: Any? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val d = OuTheme.dimens
    val animate = UiFlags.animations
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // 【为什么不用 LaunchedEffect(resetKey)】
    // 那样会在"页面从后台恢复"时也触发一次 —— 用户从下载页/设置页切回来，
    // 列表被强制滚回顶部（用户反复反馈的问题）。
    // 这里改成 snapshotFlow + drop(1)：只监听 resetKey **后续的变化**，
    // 初始组合（含状态恢复）那一次直接跳过。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (resetKey != null) {
            androidx.compose.runtime.snapshotFlow { resetKey }
                .drop(1)   // 跳过初始值
                .collect {
                    runCatching {
                        if (columns <= 1) listState.scrollToItem(0) else gridState.scrollToItem(0)
                    }
                }
        }
    }
    if (columns <= 1) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = d.pagePadding, vertical = d.itemGap),
            verticalArrangement = Arrangement.spacedBy(d.itemGap),
        ) {
            // 【性能】长列表不做逐项淡入 —— 快速滑动时每出现一项就启动一个 160ms 动画，
            // 几十个动画并发就是"滑起来卡卡的"主因。短列表才做入场效果。
            val anim = animate && items.size <= 20
            listItems(items, key = key) { item ->
                AnimatedItem(anim) { itemContent(item) }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(horizontal = d.pagePadding, vertical = d.itemGap),
            verticalArrangement = Arrangement.spacedBy(d.itemGap),
            horizontalArrangement = Arrangement.spacedBy(d.itemGap),
        ) {
            val animG = animate && items.size <= 20
            gridItems(items, key = key) { item ->
                AnimatedItem(animG) { itemContent(item) }
            }
        }
    }
}

/** 当前是否横屏 */
@Composable
fun isLandscape(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

/**
 * 列表列数。横屏横向空间很大，用 3 列而不是 2 列 —— 同样的垂直空间能多显示一半条目，
 * 否则竖屏的 2 列在横屏下会显得又挤又空。
 */
@Composable
fun listColumns(): Int = if (isLandscape()) 3 else 1

@Composable
fun LoadingRow(text: String) {
    val c = OuTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = c.accent,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = OuTheme.typography.body, color = c.foreground)
    }
}

/**
 * 目录授权失效时顶部的醒目提示条。
 *
 * 之前失效了只在「安装」那一刻报错，用户看到一句"SAF 授权失败"完全不知道
 * 该干什么。现在放在最顶上，任何页面都能看到，而且点一下就能重新授权。
 */
@Composable
fun RootAuthBanner(onFix: () -> Unit) {
    val c = OuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OuTheme.dimens.pagePadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(OuIcons.warning),
            contentDescription = null,
            tint = c.accentColor3,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.root_banner_invalid),
            style = OuTheme.typography.body,
            color = c.accentColor3,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OuButton(
            stringResource(R.string.settings_btn_reauth),
            onClick = onFix,
            variant = OuButtonVariant.Accent,
            leadingIconRes = OuIcons.folder,
        )
    }
}
