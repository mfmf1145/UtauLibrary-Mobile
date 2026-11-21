package com.utaumarket.mobile.data.project

import android.content.Context
import com.utaumarket.mobile.data.model.ProjectItem
import com.utaumarket.mobile.data.model.ProjectKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 工程列表缓存。
 *
 * 和 [com.utaumarket.mobile.data.installed.InstalledCache] 同理：
 * 扫工程要遍历目录树 + 读每个文件头 32KB，冷启动时很慢。
 * 先把上次结果读出来立刻显示，后台再扫一遍校正。
 */
object ProjectCache {

    private const val FILE_NAME = "project_cache.json"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun load(ctx: Context, rootKey: String): List<ProjectItem> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(f.readText())
            if (root.optString("root") != rootKey) return emptyList()  // 换了授权目录就作废
            val arr = root.optJSONArray("items") ?: return emptyList()
            (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it) ?: return@mapNotNull null) }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, rootKey: String, items: List<ProjectItem>) {
        runCatching {
            val root = JSONObject().apply {
                put("root", rootKey)
                put("items", JSONArray().also { a -> items.forEach { a.put(toJson(it)) } })
            }
            val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file(ctx))
        }
    }

    private fun toJson(p: ProjectItem): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("fileName", p.fileName)
        put("uri", p.uri)
        put("parentUri", p.parentUri)
        put("sizeBytes", p.sizeBytes)
        put("lastModified", p.lastModified)
        put("trackCount", p.trackCount)
        put("noteCount", p.noteCount)
        put("kind", p.kind.name)
        put("singers", JSONArray().also { a -> p.singers.forEach { a.put(it) } })
    }

    private fun fromJson(o: JSONObject): ProjectItem? = runCatching {
        val singers = ArrayList<String>()
        o.optJSONArray("singers")?.let { a -> for (i in 0 until a.length()) singers.add(a.optString(i)) }
        ProjectItem(
            name = o.optString("name"),
            fileName = o.optString("fileName"),
            uri = o.optString("uri"),
            parentUri = o.optString("parentUri"),
            sizeBytes = o.optLong("sizeBytes"),
            lastModified = o.optLong("lastModified"),
            singers = singers,
            trackCount = o.optInt("trackCount"),
            noteCount = o.optInt("noteCount"),
            kind = runCatching { ProjectKind.valueOf(o.optString("kind")) }.getOrDefault(ProjectKind.USTX),
        )
    }.getOrNull()
}
