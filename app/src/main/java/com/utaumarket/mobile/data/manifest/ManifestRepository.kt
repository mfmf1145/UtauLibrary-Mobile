package com.utaumarket.mobile.data.manifest

import android.content.Context
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import org.json.JSONObject

/**
 * 资源清单。
 *
 * 加载顺序：外部清单（可选）→ 内置清单（assets）。
 *
 * 【血泪教训】外部清单**必须单独兜底**。曾经把 externalCatalog 的 readText 直接放在
 * 主 try 里，结果文件存在但 App 没有存储权限读取时抛异常，被外层 catch 吞掉，
 * items 是空的还被写进缓存 —— 表现就是整个音源列表变成空的。
 * 现在的规则：
 *   1. 外部清单读不了/解析失败/条目为空 → 一律回退内置清单；
 *   2. 内置清单也读不出来 → 保留上一次成功的结果，绝不清空；
 *   3. 只有真的拿到条目才会写缓存。
 */
class ManifestRepository(private val context: Context) {

    @Volatile private var cache: List<ResourceItem>? = null

    /** 当前这份列表是从哪来的，界面上会写出来 */
    @Volatile var sourceName: String = "内置清单"
        private set

    /**
     * 外部清单：把新的 catalog.json 放到这里就能覆盖内置清单，不用重装 APK。
     *
     *   Android/data/<包名>/files/catalog.json   ← 免权限，推荐
     *   /sdcard/Download/UtauMarketMobile/catalog.json  ← 需要存储权限，读不到会自动回退
     */
    fun externalCatalog(): java.io.File? = runCatching {
        val dirs = listOfNotNull(
            context.getExternalFilesDir(null),
            java.io.File("/sdcard/Download/UtauMarketMobile"),
        )
        dirs.map { java.io.File(it, "catalog.json") }
            .firstOrNull { it.isFile && it.length() > 16 }
    }.getOrNull()

    fun load(force: Boolean = false): List<ResourceItem> {
        cache?.let { if (!force) return it }

        // 1) 外部清单（单独兜底，失败绝不影响主流程）
        val ext = externalCatalog()
        if (ext != null) {
            val extItems = runCatching { parse(ext.readText()) }
                .onFailure { L.w("外部清单读取/解析失败，回退内置清单: " + ext.absolutePath, it) }
                .getOrDefault(emptyList())
            if (extItems.isNotEmpty()) {
                sourceName = "外部清单 " + ext.absolutePath
                L.d("使用外部清单: " + ext.absolutePath)
                cache = extItems
                return extItems
            }
            L.w("外部清单没有可用条目，回退内置清单: " + ext.absolutePath)
        }

        // 2) 内置清单
        val builtIn = runCatching {
            parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
        }.onFailure { L.e("读取内置资源清单失败", it) }.getOrDefault(emptyList())

        // 3) 连内置都读不出来时，保留上次成功的结果，绝不清空界面
        if (builtIn.isEmpty()) {
            L.w("内置清单为空，保留上一次的结果")
            cache?.let { return it }
        }
        sourceName = "内置清单"
        cache = builtIn
        return builtIn
    }

    private fun parse(text: String): List<ResourceItem> {
        val items = mutableListOf<ResourceItem>()
        val root = JSONObject(text)
        val vb = root.optJSONArray("voicebanks")
        if (vb != null) for (i in 0 until vb.length()) {
            val o = vb.optJSONObject(i) ?: continue
            items += ResourceItem.fromVoicebankJson(o)
        }
        val pl = root.optJSONArray("plugins_manual") ?: root.optJSONArray("plugins")
        if (pl != null) for (i in 0 until pl.length()) {
            val o = pl.optJSONObject(i) ?: continue
            items += ResourceItem.fromPluginJson(o)
        }
        // 「分支 / 改版」：第三方修改过的宿主程序（例如支持 DLL 的 OpenUtau Mobile）。
        // 结构与插件相同，但单独成类，避免和真正的插件混在一起。
        val fk = root.optJSONArray("forks")
        if (fk != null) for (i in 0 until fk.length()) {
            val o = fk.optJSONObject(i) ?: continue
            items += ResourceItem.fromPluginJson(o).copy(isFork = true, kind = ResourceKind.PLUGIN)
        }
        return items
    }

    fun voicebanks(): List<ResourceItem> = load().filter { it.kind == ResourceKind.VOICEBANK }
    fun plugins(): List<ResourceItem> = load().filter { it.kind == ResourceKind.PLUGIN && !it.isFork }
    fun forks(): List<ResourceItem> = load().filter { it.isFork }
    fun phonemizers(): List<ResourceItem> = load().filter { it.kind == ResourceKind.PHONEMIZER }

    fun rawJson(): String = try {
        externalCatalog()?.readText()
            ?: context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
    } catch (t: Throwable) {
        "{}"
    }

    companion object {
        const val ASSET_NAME = "resource_manifest.json"
    }
}
