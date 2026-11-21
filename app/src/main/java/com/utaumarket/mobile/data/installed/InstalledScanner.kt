package com.utaumarket.mobile.data.installed

import android.content.Context
import android.net.Uri
import com.utaumarket.mobile.R
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.model.InstalledItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.saf.DocEntry
import com.utaumarket.mobile.saf.SafManager
import com.utaumarket.mobile.saf.TargetDir

/**
 * 通过 SAF 扫描用户授权的声库/插件目录，生成"已安装"列表。
 *
 * 元数据读取遵循 OpenUtau 的约定：
 *   character.txt  —— UTAU 声库（常见 key=value，可能是 Shift-JIS 或 UTF-8）
 *   character.yaml —— OpenUtau / DiffSinger 声库
 *   dsconfig.yaml  —— DiffSinger 声库
 *   plugin.txt     —— 插件
 */
object InstalledScanner {

    /** UTAU / OpenUtau 声库的特征文件 */
    private val VB_MARKERS = setOf(
        "character.txt", "oto.ini", "oto_ini.txt", "character.yaml", "dsconfig.yaml",
    )
    private val PLUGIN_MARKERS = setOf("plugin.txt")

    /**
     * 这个目录到底是不是我们要的那种资源。
     * 允许"root/角色名/oto.ini"这种套一层的常见结构。
     */
    private fun looksLike(
        context: Context,
        treeUri: Uri,
        docId: String,
        files: List<DocEntry>,
        kind: ResourceKind,
    ): Boolean {
        val names = files.map { it.name.lowercase() }
        return when (kind) {
            ResourceKind.VOICEBANK -> {
                names.any { it in VB_MARKERS } ||
                    subHas(context, treeUri, files, VB_MARKERS) ||
                    // 【对齐安装器】安装器 detectRoots 会往深处找（最多 6 层），
                    // 扫描器原来只看到第二层，于是"装进去了却在已安装里看不到"。
                    // 这里补一层：子目录的子目录里有标记文件也算声库。
                    subSubHas(context, treeUri, files, VB_MARKERS)
            }
            ResourceKind.PLUGIN -> {
                names.any { it in PLUGIN_MARKERS || it.endsWith(".dll") } ||
                    subHas(context, treeUri, files, PLUGIN_MARKERS)
            }
            ResourceKind.PHONEMIZER -> {
                names.any { it.endsWith(".dll") } || subHas(context, treeUri, files) { it.endsWith(".dll") }
            }
        }
    }

    private fun subHas(
        context: Context,
        treeUri: Uri,
        files: List<DocEntry>,
        markers: Set<String> = emptySet(),
        extra: (String) -> Boolean = { false },
    ): Boolean = files.any { d ->
        d.isDirectory && runCatching {
            SafManager.listChildren(context, treeUri, d.docId).any { c ->
                val n = c.name.lowercase()
                n in markers || extra(n)
            }
        }.getOrDefault(false)
    }

    /** 再看一层：子目录的子目录里是否有标记文件 */
    private fun subSubHas(
        context: Context,
        treeUri: Uri,
        files: List<DocEntry>,
        markers: Set<String>,
    ): Boolean = files.any { d ->
        d.isDirectory && runCatching {
            SafManager.listChildren(context, treeUri, d.docId).any { sub ->
                sub.isDirectory && SafManager.listChildren(context, treeUri, sub.docId)
                    .any { c -> c.name.lowercase() in markers }
            }
        }.getOrDefault(false)
    }

    /** 每个条目都要再列一次子目录（一次 SAF IPC），并发做候选列表才不至于卡住 */
    private val scanPool: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newFixedThreadPool(6) { r ->
            Thread(r, "utm-scan").apply { isDaemon = true }
        }

    fun scan(context: Context, target: TargetDir?, kind: ResourceKind): List<InstalledItem> {
        if (target == null) return emptyList()
        val treeUri = Uri.parse(target.treeUri)
        val rootId = target.docId
        val raw = SafManager.listChildren(context, treeUri, rootId)
        // 【互斥过滤】音素器与插件共用过同一个目录，导致两边显示同一批内容。
        // 现在按名字粗分：名字里带 phonemizer 的归音素器，其余归插件。
        fun isPhonemizerName(n: String) = n.lowercase().contains("phonemizer")
        val entries = when (kind) {
            ResourceKind.PLUGIN -> raw.filter { !isPhonemizerName(it.name) }
            ResourceKind.PHONEMIZER -> raw.filter { isPhonemizerName(it.name) }
            else -> raw
        }
        val dirs = entries.filter { it.isDirectory }
        // 【关键】插件 / 音素器常常是**单个 .dll 直接放在目录里**，
        // 之前只扫子目录，装完在「已安装」里完全看不到（用户反馈）。
        val looseFiles = if (kind == ResourceKind.VOICEBANK) emptyList() else {
            entries.filter { !it.isDirectory && it.name.lowercase().endsWith(".dll") }
        }
        val tasks = dirs.map { entry ->
            java.util.concurrent.Callable { buildItem(context, treeUri, entry, kind) }
        } + looseFiles.map { entry ->
            java.util.concurrent.Callable { buildFileItem(treeUri, entry, kind) }
        }
        val items = runCatching {
            scanPool.invokeAll(tasks).mapNotNull { f ->
                runCatching { f.get() }.onFailure { L.w("读取条目失败", it) }.getOrNull()
            }.filterNotNull()
        }.getOrElse { emptyList() }
        return items.sortedBy { it.name.lowercase() }
    }

    /** 单个文件（.dll）当成一条「已安装」记录 */
    private fun buildFileItem(
        treeUri: Uri,
        entry: DocEntry,
        kind: ResourceKind,
    ): InstalledItem = InstalledItem(
        uri = SafManager.documentUri(treeUri, entry.docId).toString(),
        parentUri = treeUri.toString(),
        name = entry.name.substringBeforeLast('.').ifBlank { entry.name },
        kind = kind,
        isDirectory = false,
        sizeBytes = entry.size,
        lastModified = entry.lastModified,
        childCount = 1,
        detailLines = listOf("文件" to entry.name),
    )

    private fun buildItem(
        context: Context,
        treeUri: Uri,
        entry: DocEntry,
        kind: ResourceKind,
    ): InstalledItem? {
        return run {
            val files = SafManager.listChildren(context, treeUri, entry.docId)
            val names = files.map { it.name.lowercase() }
            // 【关键】只认真正的声库/插件目录。
            // 之前把目标目录下的每个子文件夹都当成"已安装声库"，
            // 结果 Singers 里放个杂文件夹、或者目标目录认成了根目录，
            // 就会有一堆根本不是声库的东西混进已安装列表。
            if (!looksLike(context, treeUri, entry.docId, files, kind)) return null
            // 递归统计：声库的 wav 通常全在子目录里，只算直接子项会显示成 0
            val (totalBytes, totalFiles) = dirStats(context, treeUri, entry.docId)
            val meta = readMeta(context, treeUri, entry.docId, files.map { it.name })
            InstalledItem(
                uri = SafManager.documentUri(treeUri, entry.docId).toString(),
                parentUri = treeUri.toString(),
                name = meta.displayName.ifBlank { entry.name },
                // 目录名单独留一份：删除、打开文件夹都要用真实目录名
                folderName = entry.name,
                kind = kind,
                isDirectory = true,
                sizeBytes = totalBytes,
                lastModified = entry.lastModified,
                childCount = totalFiles,
                author = meta.author,
                version = meta.version,
                engine = meta.engine,
                language = meta.language,
                recordingType = detectRecordingType(names, entry.name),
                detailLines = meta.lines,
                iconUri = if (kind == ResourceKind.VOICEBANK) {
                    runCatching {
                        findIcon(context, treeUri, entry.docId, files, meta.imageName)
                    }.getOrDefault("")
                } else "",
            )
        }
    }

    /**
     * 递归统计目录里的文件数与总字节数。
     *
     * 一次 listChildren 是一次 SAF 查询，但**返回整个目录的所有条目**，
     * 所以递归的成本是"每个子目录一次查询"，而不是"每个文件一次查询"，很便宜。
     * 之前 sizeBytes 只算了直接子项，所以有大子目录的声库会显示成 0。
     */
    /**
     * 递归统计大小 / 文件数。
     *
     * 【性能】这里原本会走完整棵树 —— 一个声库几千个 wav，每个文件都要一次 SAF 查询，
     * 一百个声库加起来就是几十万次 IPC，「已安装」页要转很久。
     * 现在加了两个上限：深度 2 层、总条目预算 600 条。
     * 体积显示略微偏小（大目录算不全），但换来了秒开。
     */
    private fun dirStats(
        context: Context,
        treeUri: Uri,
        docId: String,
        depth: Int = 0,
        maxDepth: Int = 4,
        // 之前是 600：大音源（598 个文件以上）会被截断，显示成 107 MB 这种明显偏小的体积。
        // 扫描有缓存 + 后台预热，放宽到 20 万条不会卡界面。
        budget: IntArray = intArrayOf(200_000),
    ): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        for (c in SafManager.listChildren(context, treeUri, docId)) {
            if (budget[0] <= 0) break
            budget[0]--
            if (c.isDirectory) {
                if (depth < maxDepth) {
                    val (b, n) = dirStats(context, treeUri, c.docId, depth + 1, maxDepth, budget)
                    bytes += b
                    count += n
                }
            } else {
                bytes += c.size
                count++
            }
        }
        return bytes to count
    }

    private data class Meta(
        /** character.txt / character.yaml 里的歌手名；为空则用目录名 */
        val displayName: String = "",
        val author: String = "",
        val version: String = "",
        val engine: String = "",
        val language: String = "",
        val lines: List<Pair<String, String>> = emptyList(),
        /** character.txt / character.yaml 里写的图标文件名（可能是相对路径） */
        val imageName: String = "",
    )

    /** 声库里常见的图标文件名（大小写不敏感） */
    private val ICON_NAMES = listOf(
        "icon.png", "icon.jpg", "icon.jpeg", "icon.bmp", "icon.webp", "icon.gif",
        "avatar.png", "avatar.jpg", "portrait.png", "profile.png",
        "头像.png", "头像.jpg", "アイコン.png", "icon.ico",
    )

    /**
     * 在声库目录里找头像文件，返回它的 SAF 文档 URI。
     *
     * 优先级：
     *   1. character.txt / character.yaml 里 image= 指定的文件（最准确）
     *   2. 常见文件名 icon.png / icon.bmp / avatar.png …
     *   3. 如果套了一层子目录，再看一层
     */
    private fun findIcon(
        context: Context,
        treeUri: Uri,
        dirDocId: String,
        files: List<DocEntry>,
        metaImage: String,
    ): String {
        fun match(name: String): DocEntry? {
            val want = name.substringAfterLast('/').substringAfterLast('\\').trim().lowercase()
            return files.firstOrNull { !it.isDirectory && it.name.lowercase() == want }
        }
        // 1) 元数据里指定的
        if (metaImage.isNotBlank()) {
            match(metaImage)?.let { return SafManager.documentUri(treeUri, it.docId).toString() }
        }
        // 2) 常见文件名
        for (n in ICON_NAMES) {
            match(n)?.let { return SafManager.documentUri(treeUri, it.docId).toString() }
        }
        // 3) 只套了一层子目录的声库（root/角色名/icon.png 的上一级）
        for (d in files.filter { it.isDirectory }.take(3)) {
            val kids = runCatching { SafManager.listChildren(context, treeUri, d.docId) }.getOrDefault(emptyList())
            for (n in ICON_NAMES) {
                kids.firstOrNull { !it.isDirectory && it.name.equals(n, true) }
                    ?.let { return SafManager.documentUri(treeUri, it.docId).toString() }
            }
        }
        return ""
    }

    private fun readMeta(
        context: Context,
        treeUri: Uri,
        dirDocId: String,
        fileNames: List<String>,
    ): Meta {
        val lines = ArrayList<Pair<String, String>>()
        var displayName = ""
        var author = ""
        var version = ""
        var engine = ""
        var language = ""
        var imageName = ""
        // 元数据行的标签走资源文件（这些行会原样显示在"已安装"详情里）
        val lName = context.getString(R.string.meta_name)
        val lAuthor = context.getString(R.string.meta_author)
        val lVoice = context.getString(R.string.meta_voice)
        val lVoiceSrc = context.getString(R.string.meta_voice_src)
        val lWeb = context.getString(R.string.meta_web)
        val lVersion = context.getString(R.string.meta_version)
        val lLanguage = context.getString(R.string.meta_language)
        val lType = context.getString(R.string.meta_type)
        val lDescription = context.getString(R.string.meta_description)

        fun readFile(name: String): String? {
            val child = SafManager.findChild(context, treeUri, dirDocId, name) ?: return null
            val raw = SafManager.readText(context, treeUri, child.docId) ?: return null
            return raw
        }

        val characterTxt = fileNames.firstOrNull { it.equals("character.txt", true) }
        if (characterTxt != null) {
            engine = "UTAU"
            readFile(characterTxt)?.let { text ->
                parseKeyValue(text).forEach { (k, v) ->
                    when (k.lowercase()) {
                        "name" -> { displayName = v.trim(); lines += lName to v }
                        "author", "coder" -> { author = v; lines += lAuthor to v }
                        "voice", "sampler" -> lines += lVoice to v
                        "web" -> lines += lWeb to v
                        // UTAU 的 character.txt 用 image= 指定头像文件名
                        "image", "icon", "portrait" -> imageName = v
                        else -> if (v.isNotBlank() && lines.size < 12) lines += k to v
                    }
                }
            }
        }
        val characterYaml = fileNames.firstOrNull { it.equals("character.yaml", true) }
        if (characterYaml != null) {
            engine = engine.ifBlank { "DiffSinger/OpenUtau" }
            readFile(characterYaml)?.let { text ->
                parseKeyValue(text).forEach { (k, v) ->
                    when (k.lowercase()) {
                        "name" -> { displayName = v.trim(); lines += lName to v }
                        "author" -> { author = v; lines += lAuthor to v }
                        "voice" -> lines += lVoiceSrc to v
                        "version" -> { version = v; lines += lVersion to v }
                        "language", "lang" -> { language = v; lines += lLanguage to v }
                        "singer_type", "type" -> lines += lType to v
                        "web" -> lines += lWeb to v
                        else -> if (v.isNotBlank() && lines.size < 14) lines += k to v
                    }
                }
            }
        }
        val dsconfig = fileNames.firstOrNull { it.equals("dsconfig.yaml", true) }
        if (dsconfig != null) {
            engine = engine.ifBlank { "DiffSinger" }
            readFile(dsconfig)?.let { text ->
                parseKeyValue(text).forEach { (k, v) ->
                    when (k.lowercase()) {
                        "name" -> { displayName = v.trim(); lines += lName to v }
                        "author" -> { author = author.ifBlank { v }; lines += lAuthor to v }
                        "language" -> { language = v; lines += lLanguage to v }
                        "version" -> { version = version.ifBlank { v }; lines += lVersion to v }
                        else -> {}
                    }
                }
            }
        }
        val pluginTxt = fileNames.firstOrNull { it.equals("plugin.txt", true) }
        if (pluginTxt != null) {
            engine = engine.ifBlank { "OpenUtau Plugin" }
            readFile(pluginTxt)?.let { text ->
                parseKeyValue(text).forEach { (k, v) ->
                    when (k.lowercase()) {
                        "name" -> { displayName = v.trim(); lines += lName to v }
                        "author", "coder" -> { author = v; lines += lAuthor to v }
                        "version" -> { version = v; lines += lVersion to v }
                        "type" -> lines += lType to v
                        "description", "comment" -> lines += lDescription to v
                        else -> if (v.isNotBlank() && lines.size < 12) lines += k to v
                    }
                }
            }
        }
        return Meta(
            displayName = displayName,
            author = author,
            version = version,
            engine = engine,
            language = language,
            lines = lines,
            imageName = imageName,
        )
    }

    private fun detectRecordingType(names: List<String>, dirName: String): String {
        val s = (names.joinToString(" ") + " " + dirName).lowercase()
        return when {
            s.contains("vcv") -> "VCV"
            s.contains("cvvc") -> "CVVC"
            s.contains("cvc") -> "CVC"
            s.contains("oto.ini") || s.contains("cv") -> "CV"
            else -> ""
        }
    }

    /**
     * 宽松的 key=value / key: value 解析：
     * - 兼容 character.txt 的 key=value 与 yaml 的 key: value
     * - 自动处理 UTF-8 / Shift_JIS 两种常见编码
     */
    private fun parseKeyValue(text: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (raw in decodeBestEffort(text).lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            val eq = line.indexOf('=')
            val colon = line.indexOf(':')
            val idx = when {
                eq > 0 && (colon <= 0 || eq < colon) -> eq
                colon > 0 -> colon
                else -> -1
            }
            if (idx <= 0) continue
            val k = line.substring(0, idx).trim().removePrefix("-").trim()
            val v = line.substring(idx + 1).trim().trim('"', '\'')
            if (k.isNotEmpty() && v.isNotEmpty()) out += k to v
        }
        return out
    }

    private fun decodeBestEffort(text: String): String = text

    /** 将原始字节按 UTF-8 解析，失败则用 Shift_JIS（UTAU 老音源常见）。 */
    private fun decodeBytes(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            try { String(bytes, charset("Shift_JIS")) } catch (_: Throwable) { utf8 }
        } else utf8
    }
}
