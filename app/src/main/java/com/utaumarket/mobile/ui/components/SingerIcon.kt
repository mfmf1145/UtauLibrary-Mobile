package com.utaumarket.mobile.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.ui.theme.OuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地歌手头像。
 *
 * 【原理和 UTAU / OpenUtau 一样】声库目录里本来就带一个图标文件
 * （icon.bmp / icon.png，或 character.txt 里 image= 指定的文件），
 * 直接读用户已授权的 SAF 目录即可，**完全不联网**。
 *
 * 为了列表滚动流畅：
 *   · 解码放在 IO 线程
 *   · 用内存 LRU 缓存（按 URI 缓存 Bitmap）
 *   · 按目标显示尺寸降采样解码，避免把 2000×2000 的立绘整张读进内存
 */
@Composable
fun SingerIcon(
    uri: String,
    size: Dp = 44.dp,
    fallbackText: String = "",
) {
    val c = OuTheme.colors
    val ctx = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }

    val bmp by produceState<Bitmap?>(initialValue = IconCache.get(uri), uri, px) {
        if (value != null || uri.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) { IconCache.load(ctx, uri, px) }
    }

    Box(
        Modifier
            .size(size)
            .background(c.backgroundPointerOver)
            .border(1.dp, c.border),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            // 没有头像时用名字首字占位，比空白方块好看
            Text(
                fallbackText.take(1),
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
            )
        }
    }
}

/** 头像位图的内存缓存（最多 64 张，超出按 LRU 淘汰） */
private object IconCache {
    private val lru = object : android.util.LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val maxKb = 8 * 1024   // 约 8MB 上限

    fun get(uri: String): Bitmap? = if (uri.isBlank()) null else lru.get(uri)

    fun load(context: Context, uri: String, targetPx: Int): Bitmap? = runCatching {
        val u = Uri.parse(uri)
        // 先只读一遍边界，算出降采样比例
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(u)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565   // 头像不需要高色深，省一半内存
        }
        val bmp = context.contentResolver.openInputStream(u)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (bmp != null) lru.put(uri, bmp)
        bmp
    }.getOrNull()

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        if (w <= 0 || h <= 0 || target <= 0) return 1
        var s = 1
        while (w / (s * 2) >= target && h / (s * 2) >= target) s *= 2
        return s
    }
}
