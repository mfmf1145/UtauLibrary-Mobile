package com.utaumarket.mobile.data.installed

import android.content.Context
import com.utaumarket.mobile.data.model.InstalledItem
import com.utaumarket.mobile.data.model.ResourceKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 已安装列表的本地缓存。
 *
 * 为什么需要：SAF 每列一次目录都是一次跨进程调用，一个几百个文件的声库目录
 * 光统计大小就要走几百次 IPC。冷启动直接扫真目录，用户会盯着"正在扫描…"好几秒。
 * 所以：
 *   1. 启动先把上次的扫描结果直接读出来（一次文件读，毫秒级）
 *   2. 后台再扫真目录，扫完覆盖缓存
 * 用户看到的是「立刻有内容 → 稍后自动校正」。
 */
object InstalledCache {

    private const val FILE_NAME = "installed_cache.json"

    /** 缓存结构版本：扫描逻辑改动（例如显示名改成 character.txt 的 name=）时 +1，强制重建 */
    private const val SCHEMA = 2

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun load(ctx: Context): Map<ResourceKind, List<InstalledItem>> {
        val f = file(ctx)
        if (!f.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(f.readText())
            // 版本变了就丢掉旧缓存：扫描逻辑改了（例如显示名改成 character.txt 的 name=）
            // 但旧缓存还在的话，界面会一直显示老数据，直到用户手动刷新。
            val cachedVer = root.optString("app_version", "")
            val cachedSchema = root.optInt("schema", 0)
            if (cachedVer != com.utaumarket.mobile.BuildConfig.VERSION_NAME || cachedSchema != SCHEMA) {
                return@runCatching emptyMap()
            }
            val out = LinkedHashMap<ResourceKind, List<InstalledItem>>()
            for (kind in ResourceKind.entries) {
                val arr = root.optJSONArray(kind.name) ?: continue
                out[kind] = (0 until arr.length()).mapNotNull { i ->
                    fromJson(arr.optJSONObject(i) ?: return@mapNotNull null)
                }
            }
            out
        }.getOrDefault(emptyMap())
    }

    fun save(ctx: Context, data: Map<ResourceKind, List<InstalledItem>>) {
        runCatching {
            val root = JSONObject()
            root.put("app_version", com.utaumarket.mobile.BuildConfig.VERSION_NAME)
            root.put("schema", SCHEMA)
            for ((kind, list) in data) {
                val arr = JSONArray()
                list.forEach { arr.put(toJson(it)) }
                root.put(kind.name, arr)
            }
            val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            // 先写临时文件再改名，避免进程被杀时留下半个 JSON
            tmp.renameTo(file(ctx))
        }
    }

    private fun toJson(i: InstalledItem): JSONObject = JSONObject().apply {
        put("uri", i.uri)
        put("parentUri", i.parentUri)
        put("name", i.name)
        put("folderName", i.folderName)
        put("kind", i.kind.name)
        put("isDirectory", i.isDirectory)
        put("sizeBytes", i.sizeBytes)
        put("lastModified", i.lastModified)
        put("childCount", i.childCount)
        put("author", i.author)
        put("version", i.version)
        put("engine", i.engine)
        put("language", i.language)
        put("recordingType", i.recordingType)
        put("iconUri", i.iconUri)
        if (i.detailLines.isNotEmpty()) {
            val d = JSONArray()
            i.detailLines.forEach { (k, v) -> d.put(JSONArray().put(k).put(v)) }
            put("detailLines", d)
        }
    }

    private fun fromJson(o: JSONObject): InstalledItem? = runCatching {
        val detail = ArrayList<Pair<String, String>>()
        o.optJSONArray("detailLines")?.let { arr ->
            for (i in 0 until arr.length()) {
                val pair = arr.optJSONArray(i) ?: continue
                detail.add(pair.optString(0) to pair.optString(1))
            }
        }
        InstalledItem(
            uri = o.getString("uri"),
            parentUri = o.optString("parentUri"),
            name = o.optString("name"),
            folderName = o.optString("folderName").ifBlank { o.optString("name") },
            kind = runCatching { ResourceKind.valueOf(o.optString("kind")) }.getOrDefault(ResourceKind.VOICEBANK),
            isDirectory = o.optBoolean("isDirectory", true),
            sizeBytes = o.optLong("sizeBytes"),
            lastModified = o.optLong("lastModified"),
            childCount = o.optInt("childCount"),
            author = o.optString("author"),
            version = o.optString("version"),
            engine = o.optString("engine"),
            language = o.optString("language"),
            recordingType = o.optString("recordingType"),
            detailLines = detail,
            iconUri = o.optString("iconUri"),
        )
    }.getOrNull()
}
