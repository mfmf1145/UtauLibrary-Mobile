package com.utaumarket.mobile.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import com.utaumarket.mobile.R
import com.utaumarket.mobile.core.L
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 一个实际可写入的目标目录：SAF 的 tree uri + 该目录在树里的 documentId。
 * 支持"只授权一个根目录，子目录由程序自动认领"的模型。
 */
data class TargetDir(val treeUri: String, val docId: String, val explicit: Boolean = false)

/** SAF 中的一个文档条目 */
data class DocEntry(
    val docId: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
)

/**
 * Storage Access Framework 封装。
 *
 * 设计要点：
 * - 通过 Intent.ACTION_OPEN_DOCUMENT_TREE 让用户授权目录；
 * - 通过 takePersistableUriPermission 持久化，之后无需重复授权；
 * - 所有读写都走 DocumentsContract，不依赖任何真实文件路径，
 *   因此对 Android 5.0 ~ 14 的 scoped storage 全部适用。
 */
object SafManager {

    const val TREE_REQUEST_CODE_VOICEBANK = 1001
    const val TREE_REQUEST_CODE_PLUGIN = 1002
    const val TREE_REQUEST_CODE_PHONEMIZER = 1003

    fun buildOpenTreeIntent(initial: Uri? = null): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            if (initial != null) {
                try { putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial) } catch (_: Throwable) {}
            }
        }
        return intent
    }

    /** 持久化授权；失败时返回 false，UI 需要提示用户重新授权。 */
    fun persist(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        true
    } catch (t: Throwable) {
        L.w("持久化 SAF 授权失败: $uri", t)
        false
    }

    fun hasPersisted(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    } catch (_: Throwable) { false }

    fun release(context: Context, uri: Uri) = try {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (_: Throwable) {}

    fun treeDocId(treeUri: Uri): String? = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: Throwable) { null }

    fun documentUri(treeUri: Uri, docId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    fun childUri(treeUri: Uri, parentDocId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

    /** 列出某个目录下的直接子项。 */
    fun listChildren(context: Context, treeUri: Uri, parentDocId: String): List<DocEntry> {
        val out = ArrayList<DocEntry>()
        val uri = childUri(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(2) ?: ""
                    out += DocEntry(
                        docId = c.getString(0) ?: continue,
                        name = c.getString(1) ?: context.getString(R.string.saf_unnamed),
                        mimeType = mime,
                        size = if (c.isNull(3)) 0L else c.getLong(3),
                        lastModified = if (c.isNull(4)) 0L else c.getLong(4),
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    )
                }
            }
        } catch (t: Throwable) {
            L.w("列目录失败 tree=$treeUri doc=$parentDocId", t)
        }
        return out
    }

    fun listRoot(context: Context, treeUri: Uri): List<DocEntry> {
        val id = treeDocId(treeUri) ?: return emptyList()
        return listChildren(context, treeUri, id)
    }

    /** 读取小文本文件（character.txt / plugin.txt 等）。 */
    /** 读原始字节（用于编码转换） */
    fun readBytes(
        context: Context,
        treeUri: Uri,
        docId: String,
        limit: Int = 8 * 1024 * 1024,
    ): ByteArray? = try {
        context.contentResolver.openInputStream(documentUri(treeUri, docId))?.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val tmp = ByteArray(64 * 1024)
            var total = 0
            while (total < limit) {
                val n = ins.read(tmp)
                if (n <= 0) break
                out.write(tmp, 0, n)
                total += n
            }
            out.toByteArray()
        }
    } catch (t: Throwable) {
        L.w("读取字节失败", t)
        null
    }

    /** 覆盖写入字节（编码修复用） */
    fun writeBytes(context: Context, treeUri: Uri, docId: String, bytes: ByteArray): Boolean = try {
        context.contentResolver.openOutputStream(documentUri(treeUri, docId), "wt")?.use {
            it.write(bytes)
            it.flush()
        }
        true
    } catch (t: Throwable) {
        L.w("写入字节失败", t)
        false
    }

    fun readText(context: Context, treeUri: Uri, docId: String, limit: Int = 64 * 1024): String? = try {
        context.contentResolver.openInputStream(documentUri(treeUri, docId))?.use { ins ->
            val buf = ByteArray(limit)
            val n = ins.read(buf)
            if (n <= 0) "" else com.utaumarket.mobile.archive.TextEncoding.decodeLenient(buf.copyOf(n))
        }
    } catch (t: Throwable) { null }

    fun openInput(context: Context, treeUri: Uri, docId: String): InputStream? = try {
        context.contentResolver.openInputStream(documentUri(treeUri, docId))
    } catch (t: Throwable) { null }

    fun openOutput(context: Context, treeUri: Uri, docId: String): OutputStream? = try {
        context.contentResolver.openOutputStream(documentUri(treeUri, docId), "wt")
    } catch (t: Throwable) { null }

    fun delete(context: Context, treeUri: Uri, docId: String): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, documentUri(treeUri, docId))
    } catch (t: Throwable) {
        L.w("删除失败 doc=$docId", t)
        false
    }

    fun createDirectory(context: Context, treeUri: Uri, parentDocId: String, name: String): String? = try {
        val uri = DocumentsContract.createDocument(
            context.contentResolver, documentUri(treeUri, parentDocId),
            DocumentsContract.Document.MIME_TYPE_DIR, sanitize(name)
        )
        uri?.let { DocumentsContract.getDocumentId(it) }
    } catch (t: Throwable) {
        L.w("创建目录失败: $name", t); null
    }

    /** 在 SAF 目录下新建文件并返回 docId */
    /**
     * 新建文件，并**校验实际文件名**。
     *
     * 关键坑：部分 ROM 的 SAF 提供者会按 MIME 类型追加扩展名。
     * 例如用 text/plain 建 oto.ini，实际会得到 oto.ini.txt ——
     * OpenUtau 找不到 oto.ini，整个声库就废了（表现为乱码/无 oto）。
     * 所以建完必须比对名字，不一致就改名回来。
     */
    fun createFile(context: Context, treeUri: Uri, parentDocId: String, name: String, mime: String): String? = try {
        val resolver = context.contentResolver
        val wanted = sanitize(name)
        val uri = DocumentsContract.createDocument(
            resolver, documentUri(treeUri, parentDocId), mime, wanted
        )
        var docId = uri?.let { DocumentsContract.getDocumentId(it) }
        if (docId != null && uri != null) {
            val actual = displayNameOf(context, treeUri, docId)
            if (actual != null && !actual.equals(wanted, ignoreCase = true)) {
                // 有些 ROM（实测 Honor）会按 MIME 追加扩展名：oto.ini -> oto.ini.txt
                // 这会让 OpenUtau 找不到 oto.ini，所以必须改回来。
                L.w("系统把 \"$wanted\" 存成了 \"$actual\"，尝试改名")
                val renamed = runCatching {
                    DocumentsContract.renameDocument(resolver, uri, wanted) != null
                }.getOrDefault(false)
                // 【关键】改名之后 docId 可能已经变了。继续用旧 docId 去 openOutputStream
                // 会抛 FileNotFoundException，然后被上层报成"SAF 授权失败"——
                // 之前"安装总是失败"就是这么来的。必须按最终文件名重新查回来。
                val finalName = if (renamed) wanted else actual
                val byName = listChildren(context, treeUri, parentDocId)
                    .firstOrNull { it.name.equals(finalName, ignoreCase = true) }
                docId = byName?.docId ?: docId
                L.d("改名=$renamed 最终名=" + (byName?.name ?: "?") + " docId=" + docId)
            }
        }
        docId
    } catch (t: Throwable) {
        L.w("创建文件失败: $name", t); null
    }

    /** 查某个文档的显示名 */
    fun displayNameOf(context: Context, treeUri: Uri, docId: String): String? = runCatching {
        context.contentResolver.query(
            documentUri(treeUri, docId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> if (c.moveToNext()) c.getString(0) else null }
    }.getOrNull()

    /**
     * 修正在设备上被系统改了名的文件（oto.ini.txt → oto.ini，xxx.txt.txt → xxx.txt）。
     * 用于救已经装坏的声库。返回改了几个。
     */
    fun fixAppendedExtensions(context: Context, treeUri: Uri, dirDocId: String): Int {
        var fixed = 0
        val known = listOf(".ini", ".txt", ".txtx", ".yaml", ".yml", ".bmp", ".wav", ".frq")
        for (entry in listChildren(context, treeUri, dirDocId)) {
            if (entry.isDirectory) {
                fixed += fixAppendedExtensions(context, treeUri, entry.docId)
                continue
            }
            val n = entry.name
            // 只处理"已知扩展名 + .txt"这种被追加上去的形态
            val hit = known.firstOrNull { n.endsWith(it + ".txt", ignoreCase = true) }
            if (hit != null) {
                val target = n.dropLast(4)
                if (rename(context, treeUri, entry.docId, target)) fixed++
            } else if (n.endsWith(".txt.txt", ignoreCase = true)) {
                if (rename(context, treeUri, entry.docId, n.dropLast(4))) fixed++
            }
        }
        return fixed
    }

    /** 把文档改名 */
    fun rename(context: Context, treeUri: Uri, docId: String, newName: String): Boolean = runCatching {
        DocumentsContract.renameDocument(
            context.contentResolver, documentUri(treeUri, docId), newName,
        ) != null
    }.getOrDefault(false)

    fun findChild(context: Context, treeUri: Uri, parentDocId: String, name: String): DocEntry? =
        listChildren(context, treeUri, parentDocId).firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** 把本地文件写入 SAF 目标目录。 */
    fun copyInto(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        local: File,
        targetName: String = local.name,
    ): Boolean {
        val expect = local.length()
        // 写失败或**写少了**都要重试：某些 ROM 的 SAF 在并发写入时会静默截断，
        // 之前只看"没抛异常就算成功"，于是装出来的声库文件数是够的、内容是残的。
        repeat(2) { attempt ->
            val docId = createFile(context, treeUri, parentDocId, targetName, guessMime(targetName)) ?: return false
            val uri = documentUri(treeUri, docId)
            val written = try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    local.inputStream().use { ins -> ins.copyTo(out, 128 * 1024) }
                }
                docSize(context, uri)
            } catch (t: Throwable) {
                L.w("写入文件失败: $targetName", t)
                -1L
            }
            if (written == expect) return true
            L.w("写入字节数不符（第 " + (attempt + 1) + " 次）: $targetName 实际 $written / 期望 $expect")
            com.utaumarket.mobile.core.CrashReporter.appendShared(
                context, "UtauMarketMobile-write.txt",
                "文件写入不符: " + targetName + " 实际=" + written + " 期望=" + expect + "\n",
            )
            runCatching { delete(context, treeUri, docId) }
        }
        return false
    }

    /** 读一个文档的实际大小（字节）；读不到返回 -1 */
    fun docSize(context: Context, docUri: Uri): Long = runCatching {
        context.contentResolver.query(
            docUri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null,
        )?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    /** 递归统计目标目录里的文件数与总字节，用来做"写入后回读校验" */
    fun stats(context: Context, treeUri: Uri, docId: String): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        val stack = ArrayDeque<String>()
        stack.addLast(docId)
        var guard = 0
        while (stack.isNotEmpty() && guard < 200_000) {
            val id = stack.removeLast()
            val kids = runCatching { listChildren(context, treeUri, id) }.getOrDefault(emptyList())
            for (k in kids) {
                guard++
                if (k.isDirectory) stack.addLast(k.docId) else { count++; bytes += k.size }
            }
        }
        return bytes to count
    }

    /** 递归把本地目录复制到 SAF 目录；返回是否全部成功。 */
    /**
     * 写入进度（字节级）。
     *
     * 一个声库常有上千个小文件，只报"第几/共几"用户看不出还要多久；
     * 这里按累计字节上报，安装阶段也能显示一个真实的百分比。
     */
    class ByteTracker(val total: Long, private val onUpdate: (Long, Long) -> Unit) {
        private val done = java.util.concurrent.atomic.AtomicLong(0)
        @Volatile private var lastAt = 0L
        fun add(bytes: Long) {
            val d = done.addAndGet(bytes)
            val now = System.currentTimeMillis()
            if (now - lastAt >= 250) {
                lastAt = now
                onUpdate(d, total)
            }
        }
        fun finish() = onUpdate(done.get(), total)
    }

    /**
     * 在目录里放一个 .nomedia。
     *
     * 用户反馈："下一些声库，里面的图片素材全存到我相册里了"。
     * 原因：声库装在共享存储（/storage/emulated/0/OpenUtau/Singers/…），
     * 安卓的媒体扫描器会把里面的 png/jpg 全部索引进相册。
     * 在**资源根目录**（Singers / Plugins）放一个 .nomedia 就能让扫描器整棵子树都跳过，
     * 又不会影响用户自己那几个目录以外的东西。
     */
    fun ensureNoMedia(context: Context, treeUri: Uri, parentDocId: String): Boolean {
        if (parentDocId.isBlank()) return false
        return runCatching {
            if (findChild(context, treeUri, parentDocId, ".nomedia") != null) return true
            // 用自定义 MIME，避免 Honor 的 SAF 给 .nomedia 追加扩展名
            createFile(context, treeUri, parentDocId, ".nomedia", "application/x-utau-nomedia") != null
        }.getOrDefault(false)
    }

    fun copyDirectory(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        localDir: File,
        targetName: String = localDir.name,
        onProgress: (String) -> Unit = {},
        onBytes: ((Long, Long) -> Unit)? = null,
    ): Boolean {
        val dirDocId = createDirectory(context, treeUri, parentDocId, targetName) ?: run {
            // 已存在同名目录时复用
            findChild(context, treeUri, parentDocId, targetName)?.takeIf { it.isDirectory }?.docId
        } ?: return false
        val tracker = onBytes?.let {
            val total = runCatching {
                localDir.walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() }
            }.getOrDefault(0L)
            ByteTracker(total, it)
        }
        val ok0 = copyDirectoryContents(context, treeUri, dirDocId, localDir, onProgress, tracker)
        tracker?.finish()
        // 【写入回读校验】整棵复制完后，拿目标目录的实际文件数与字节数对一遍。
        // Honor 等 ROM 的 SAF 在大量小文件并发写入时会静默丢文件/截断内容，
        // 之前只看"没抛异常"，装出来的声库就是残的（用户反馈"装完只有 100MB"）。
        if (!ok0) return false
        // 只统计"会被复制"的文件：复制时跳过了 .DS_Store / ._* 以及 __MACOSX 目录，
        // 这里必须用同一套过滤规则，否则永远对不上 —— 全都会被判成安装失败。
        fun counted(f: java.io.File): Boolean =
            f.isFile && f.name != ".DS_Store" && !f.name.startsWith("._") &&
                f.path.split(File.separator).none { it == "__MACOSX" }
        val localBytes = runCatching {
            localDir.walkTopDown().filter { counted(it) }.sumOf { it.length() }
        }.getOrDefault(0L)
        val localCount = runCatching { localDir.walkTopDown().count { counted(it) } }.getOrDefault(0)
        val (dstBytes, dstCount) = stats(context, treeUri, dirDocId)
        if (localCount != dstCount || localBytes != dstBytes) {
            L.w("写入回读不符: 文件 $dstCount/$localCount，字节 $dstBytes/$localBytes（" + targetName + "）")
            com.utaumarket.mobile.core.CrashReporter.appendShared(
                context, "UtauMarketMobile-write.txt",
                "目录回读不符: " + targetName + " 文件 " + dstCount + "/" + localCount + " 字节 " + dstBytes + "/" + localBytes + "\n",
            )
            return false
        }
        return true
    }

    /**
     * 单文件写入要走一次 ContentResolver IPC，一个声库动辄几百上千个小文件，
     * 串行会慢到无法接受。这里对同一目录下的文件做有限并发（默认 6 路）。
     */
    private val copyPool: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newFixedThreadPool(6) { r ->
            Thread(r, "utm-saf-copy").apply { isDaemon = true }
        }

    private fun copyDirectoryContents(
        context: Context,
        treeUri: Uri,
        dirDocId: String,
        localDir: File,
        onProgress: (String) -> Unit,
        tracker: ByteTracker? = null,
    ): Boolean {
        var ok = true
        val children = localDir.listFiles() ?: return true
        val files = children.filter {
            it.isFile && it.name != ".DS_Store" && !it.name.startsWith("._")
        }
        val dirs = children.filter { it.isDirectory && it.name != "__MACOSX" }

        if (files.isNotEmpty()) {
            val futures = files.map { f ->
                copyPool.submit<Boolean> { copyInto(context, treeUri, dirDocId, f, f.name) }
            }
            var n = 0
            var lastReport = 0L
            for ((i, fut) in futures.withIndex()) {
                val r = runCatching { fut.get() }.getOrDefault(false)
                if (!r) ok = false
                else tracker?.add(files[i].length())
                n++
                val now = System.currentTimeMillis()
                if (now - lastReport > 300) {
                    lastReport = now
                    onProgress("写入 $n/${files.size} 个文件")
                }
            }
        }

        for (d in dirs) {
            val sub = createDirectory(context, treeUri, dirDocId, d.name)
                ?: findChild(context, treeUri, dirDocId, d.name)?.takeIf { it.isDirectory }?.docId
            if (sub == null) { ok = false; continue }
            onProgress(d.name)
            if (!copyDirectoryContents(context, treeUri, sub, d, onProgress, tracker)) ok = false
        }
        return ok
    }

    fun deleteTreeItem(context: Context, treeUri: Uri, entry: DocEntry): Boolean = delete(context, treeUri, entry.docId)

    /**
     * 删除 [parentDocId] 下名为 [name] 的子项。
     * 安装写了一半失败时用来回滚 —— 只删自己刚创建的那个目录，不碰别的。
     */
    fun deleteChild(context: Context, treeUri: Uri, parentDocId: String, name: String): Boolean {
        val child = listChildren(context, treeUri, parentDocId)
            .firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        return delete(context, treeUri, child.docId)
    }

    /** 打开“所在文件夹”：优先用系统文件管理器打开该 SAF 目录。 */
    fun openFolderIntent(context: Context, treeUri: Uri, docId: String): Intent {
        val docUri = documentUri(treeUri, docId)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (view.resolveActivity(context.packageManager) != null) return view
        // 回退：直接让用户重新挑选该目录（大多数设备都能定位到）
        val pick = buildOpenTreeIntent(treeUri)
        pick.action = Intent.ACTION_OPEN_DOCUMENT_TREE
        return pick
    }

    /**
     * 选 MIME 类型。
     *
     * 【踩坑记录】某些 ROM（实测 Honor）的文件提供者会**按 MIME 追加扩展名**：
     * 用 text/plain 建 oto.ini，实际会得到 oto.ini.txt —— OpenUtau 找不到 oto.ini，
     * 整个声库就废了。application/octet-stream 同样会变成 oto.ini.bin。
     *
     * 对策：
     *   - 扩展名有"对得上"的标准 MIME 时用标准类型（txt->text/plain、zip->application/zip …）；
     *   - ini / uar / frq 这些在 Android 的 MimeTypeMap 里没有对应项，
     *     就传一个系统认不出来的自定义类型，它就找不到可追加的扩展名。
     */
    /**
     * 检查授权是否还有效。
     *
     * 授权会因为各种原因失效（系统回收、目录被删、重装等），
     * 之前只在"安装"那一刻才报错，用户根本不知道发生了什么。
     */
    fun canAccess(context: Context, treeUri: Uri): Boolean = runCatching {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { it.moveToFirst() }
        true
    }.getOrDefault(false)

    /**
     * 把 SAF 的 documentId 还原成人能看懂的路径，例如
     * primary:aaa/Singers/重音テト  ->  /sdcard/aaa/Singers/重音テト
     */
    fun readablePath(treeUri: Uri, docId: String): String {
        val raw = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return ""
        val rootPart = raw.substringBefore(':')
        val base = if (rootPart.equals("primary", true)) "/sdcard/" else "/storage/$rootPart/"
        val tail = docId.substringAfter(':', docId)
        return base + tail
    }

    fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt", "log", "md" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "wav" -> "audio/x-wav"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            // 其余（ini / yaml / uar / frq / dll / so …）一律走自定义类型，避免被追加扩展名
            else -> "application/x-utau-" + (ext.ifBlank { "bin" })
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180).ifBlank { "untitled" }
}
