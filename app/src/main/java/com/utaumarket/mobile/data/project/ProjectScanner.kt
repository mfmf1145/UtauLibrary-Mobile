package com.utaumarket.mobile.data.project

import android.content.Context
import android.net.Uri
import com.utaumarket.mobile.archive.TextEncoding
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.model.ProjectItem
import com.utaumarket.mobile.saf.DocEntry
import com.utaumarket.mobile.saf.SafManager
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * 扫描已授权目录里的 USTX / UST 工程。
 *
 * 只读文件开头一小段来解析元数据 —— 工程可能好几 MB，全读进来没必要，
 * 而且列表里要的就是"名字 / 用到哪些歌手 / 多少音符 / 什么时候改的"。
 */
object ProjectScanner {

    // 只读 32KB 就够解析 USTX/UST 的头部（曲名 / 歌手 / 音符数）。
    // 之前是 192KB —— 一个工程就要读 192KB，几百个工程就是几十 MB 的 SAF 读取，
    // 这是"工程页加载特别慢"的主因。
    private const val HEAD_BYTES = 32 * 1024
    private const val MAX_DEPTH = 5
    private const val MAX_PROJECTS = 600
    private val EXT = setOf("ustx", "ust", "mid", "midi")

    /**
     * 扫描时**整个跳过的目录**。
     * 用户授权的根目录通常是 /sdcard/aaa，里面 Singers / Plugins 有成千上万个文件，
     * 工程文件绝不可能放在那儿 —— 之前会把它们全遍历一遍，白白拖慢十几秒。
     */
    private val SKIP_DIRS = setOf(
        "singers", "plugins", "phonemizers", "voicebanks",
        "android", "dcim", "download", "downloads", "tencent",
        "cache", "__macosx", "lost.dir", ".thumbnails",
    )

    private val pool: java.util.concurrent.ExecutorService =
        Executors.newFixedThreadPool(8) { r -> Thread(r, "utm-proj").apply { isDaemon = true } }

    fun scan(context: Context, treeUri: Uri, rootDocId: String): List<ProjectItem> {
        val found = ArrayList<DocEntry>()
        collect(context, treeUri, rootDocId, 0, found)
        if (found.isEmpty()) return emptyList()
        val tasks = found.map { e -> Callable { build(context, treeUri, e) } }
        return runCatching {
            pool.invokeAll(tasks).mapNotNull { f ->
                runCatching { f.get() }.onFailure { L.w("解析工程失败", it) }.getOrNull()
            }
        }.getOrElse { emptyList() }.sortedByDescending { it.lastModified }
    }

    private fun collect(context: Context, treeUri: Uri, docId: String, depth: Int, out: MutableList<DocEntry>) {
        if (depth > MAX_DEPTH || out.size >= MAX_PROJECTS) return
        for (c in SafManager.listChildren(context, treeUri, docId)) {
            if (out.size >= MAX_PROJECTS) return
            if (c.isDirectory) {
                if (c.name.startsWith(".")) continue
                // 跳过声库/插件等巨型目录（里面不会有工程文件）
                if (c.name.lowercase() in SKIP_DIRS) continue
                collect(context, treeUri, c.docId, depth + 1, out)
            } else if (c.name.substringAfterLast('.', "").lowercase() in EXT) {
                out.add(c)
            }
        }
    }

    private fun build(context: Context, treeUri: Uri, entry: DocEntry): ProjectItem? {
        val head = runCatching {
            SafManager.openInput(context, treeUri, entry.docId)?.use { ins ->
                val buf = ByteArray(HEAD_BYTES)
                var n = 0
                while (n < buf.size) {
                    val r = ins.read(buf, n, buf.size - n)
                    if (r <= 0) break
                    n += r
                }
                buf.copyOf(n)
            }
        }.getOrNull()
        val text = if (head == null || head.isEmpty()) "" else TextEncoding.decodeLenient(head)
        val uri = SafManager.documentUri(treeUri, entry.docId).toString()
        val kind = com.utaumarket.mobile.data.model.ProjectKind.of(entry.name) ?: return null
        return when (kind) {
            // MIDI 是二进制，不做文本解析；文件名就是曲名
            com.utaumarket.mobile.data.model.ProjectKind.MIDI -> ProjectItem(
                name = entry.name.substringBeforeLast('.'),
                fileName = entry.name,
                uri = uri,
                parentUri = treeUri.toString(),
                sizeBytes = entry.size,
                lastModified = entry.lastModified,
                kind = kind,
            )
            com.utaumarket.mobile.data.model.ProjectKind.UST -> parseUst(entry, text, uri, treeUri.toString())
            com.utaumarket.mobile.data.model.ProjectKind.USTX -> parseUstx(entry, text, uri, treeUri.toString())
        }
    }

    /** USTX 是 YAML；这里只按行挑几个字段，不引入完整 YAML 解析器。 */
    private fun parseUstx(entry: DocEntry, text: String, uri: String, parent: String): ProjectItem {
        var name = ""
        val singers = LinkedHashSet<String>()
        var notes = 0
        for (raw in text.lineSequence()) {
            val t = raw.trim()
            if (name.isEmpty() && t.startsWith("name:")) {
                name = t.removePrefix("name:").trim().trim('\'', '"')
            }
            if (t.startsWith("singer:")) {
                val v = t.removePrefix("singer:").trim().trim('\'', '"')
                if (v.isNotBlank()) singers.add(v)
            }
            if (t.startsWith("- position:")) notes++
        }
        return ProjectItem(
            name = name,
            fileName = entry.name,
            uri = uri,
            parentUri = parent,
            sizeBytes = entry.size,
            lastModified = entry.lastModified,
            singers = singers.toList(),
            trackCount = singers.size,
            noteCount = notes,
            kind = com.utaumarket.mobile.data.model.ProjectKind.USTX,
        )
    }

    /** UST 是传统 key=value 文本，多为 Shift-JIS（TextEncoding 会按 OpenUtau 的规则解码）。 */
    private fun parseUst(entry: DocEntry, text: String, uri: String, parent: String): ProjectItem {
        var name = ""
        val singers = LinkedHashSet<String>()
        var notes = 0
        for (raw in text.lineSequence()) {
            val t = raw.trimEnd()
            when {
                t.startsWith("ProjectName=") -> name = t.removePrefix("ProjectName=").trim()
                !t.startsWith("#") && t.startsWith("Voice") && t.contains('=') -> {
                    val v = t.substringAfter('=').trim()
                    if (v.isNotBlank()) singers.add(v)
                }
                !t.startsWith("#") && t.startsWith("Note") && t.contains('=') -> notes++
            }
        }
        return ProjectItem(
            name = name,
            fileName = entry.name,
            uri = uri,
            parentUri = parent,
            sizeBytes = entry.size,
            lastModified = entry.lastModified,
            singers = singers.toList(),
            trackCount = if (singers.isEmpty()) 1 else singers.size,
            noteCount = notes,
            kind = com.utaumarket.mobile.data.model.ProjectKind.UST,
        )
    }
}
