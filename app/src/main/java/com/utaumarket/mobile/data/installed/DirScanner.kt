package com.utaumarket.mobile.data.installed

import android.content.Context
import android.net.Uri
import com.utaumarket.mobile.R
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.saf.SafManager

/**
 * 自动扫描用户授权的根目录，认出（必要时创建）OpenUtau 的各个子目录。
 *
 * 目的：用户只需要授权**一个**目录，剩下的 Singers / Plugins 由程序自己找。
 * 识别顺序：
 *   1. 目录名匹配（Singers / Voicebanks / Plugins / 音源 / 插件 …）
 *   2. 根目录本身就是目标目录（里面直接有 character.txt / plugin.txt / .dll）
 *   3. 都没有就按 OpenUtau 的约定创建 Singers / Plugins
 */
object DirScanner {

    private val VOICEBANK_NAMES = listOf(
        "singers", "singer", "voicebank", "voicebanks", "voice", "utau", "音源", "声库", "歌手"
    )
    private val PLUGIN_NAMES = listOf(
        "plugins", "plugin", "resamplers", "插件"
    )
    // 【独立目录】音素器和插件分开放。之前 phonemizerDocId 直接复用 pluginDocId，
    // 结果 .dll 音素器全被写进 Plugins，而「已安装」只列子目录 → 装完看不见（用户反馈）。
    private val PHONEMIZER_NAMES = listOf(
        "phonemizers", "phonemizer", "音素器"
    )
    private val VB_MARKERS = listOf("character.txt", "oto.ini", "dsconfig.yaml", "character.yaml")

    data class Outcome(
        val ok: Boolean,
        val voicebankDocId: String = "",
        val pluginDocId: String = "",
        val phonemizerDocId: String = "",
        val voicebankName: String = "",
        val pluginName: String = "",
        val voicebankCount: Int = 0,
        val pluginCount: Int = 0,
        val summary: String = "",
        val error: String = "",
    )

    fun scanAndConfigure(
        context: Context,
        rootTreeUri: String,
        createIfMissing: Boolean = true,
    ): Outcome {
        if (rootTreeUri.isBlank()) return Outcome(false, error = context.getString(R.string.ds_no_root))
        val tree = Uri.parse(rootTreeUri)
        val rootId = SafManager.treeDocId(tree)
            ?: return Outcome(false, error = context.getString(R.string.ds_unreadable))

        val children = SafManager.listChildren(context, tree, rootId)
        val dirs = children.filter { it.isDirectory }

        fun match(names: List<String>) = dirs.firstOrNull { d ->
            val n = d.name.lowercase()
            names.any { n == it || n.contains(it) }
        }

        var vbDir = match(VOICEBANK_NAMES)
        var plDir = match(PLUGIN_NAMES)
        var phDir = match(PHONEMIZER_NAMES)

        // 根目录本身就是目标目录？
        val rootFileNames = children.filter { !it.isDirectory }.map { it.name.lowercase() }
        val rootLooksVoicebank = rootFileNames.any { it in VB_MARKERS }
        val rootLooksPlugin = rootFileNames.any { it == "plugin.txt" || it.endsWith(".dll") }

        var vbDocId = when {
            vbDir != null -> vbDir.docId
            rootLooksVoicebank -> rootId
            else -> ""
        }
        var plDocId = when {
            plDir != null -> plDir.docId
            rootLooksPlugin -> rootId
            else -> ""
        }
        var phDocId = when {
            phDir != null -> phDir.docId
            else -> ""
        }

        // 目录里是否已经有一堆声库/插件（deep 一层判断）
        fun countKinds(dirDocId: String, kind: String): Int {
            if (dirDocId.isBlank()) return 0
            val kids = SafManager.listChildren(context, tree, dirDocId)
            return if (kind == "vb") {
                kids.count { sub ->
                    if (!sub.isDirectory) false
                    else SafManager.listChildren(context, tree, sub.docId)
                        .any { it.name.lowercase() in VB_MARKERS }
                }
            } else {
                kids.count { sub ->
                    !sub.isDirectory && (sub.name.lowercase() == "plugin.txt" || sub.name.lowercase().endsWith(".dll"))
                } + kids.count { sub ->
                    sub.isDirectory && SafManager.listChildren(context, tree, sub.docId)
                        .any { it.name.lowercase() == "plugin.txt" }
                }
            }
        }

        // 【重要】只有当根目录**自己**就是声库/插件目录时才认它。
        //
        // 这里以前用的是"子目录里有声库就认根目录"（countKinds），结果是：
        // 用户工作目录（/sdcard/aaa）下恰好放了一个声库文件夹时，
        // 程序就把**整个工作目录**（里面还有一堆 .ustx 工程文件）当成了声库目录。
        // 于是 Singers 子目录永远不会被创建，而设置里又记着旧的
        // primary:aaa/Singers —— targetFor() 返回一个不存在的目录，
        // 写入必然失败，最后被报成"SAF 授权失败"。授权其实一直是好的。
        if (vbDocId.isBlank() && rootLooksVoicebank) vbDocId = rootId
        if (plDocId.isBlank() && rootLooksPlugin) plDocId = rootId

        // 旧的错误启发式可能已经把"根目录"存成了声库目录。如果根目录自己并不是声库，
        // 就把它纠正掉，让下面重新创建并使用 Singers/ 子目录 —— 否则声库会被
        // 直接铺在工作目录（里面有 .ustx 工程等一堆东西）里。
        if (vbDocId == rootId && !rootLooksVoicebank) vbDocId = ""
        if (plDocId == rootId && !rootLooksPlugin) plDocId = ""

        // 建目录
        if (createIfMissing) {
            if (vbDocId.isBlank()) {
                SafManager.createDirectory(context, tree, rootId, "Singers")?.let {
                    vbDocId = it; vbDir = null
                }
            }
            if (plDocId.isBlank()) {
                SafManager.createDirectory(context, tree, rootId, "Plugins")?.let {
                    plDocId = it
                }
            }
            // 音素器单独一个目录；建不出来就退回 Plugins（老行为）。
            // 注意这里判的是「和插件目录相同」而不是只判空：老用户读到的
            // phonemizerDocId 就等于 pluginDocId，两个标签会扫同一个文件夹。
            if (phDocId.isBlank() || phDocId == plDocId) {
                SafManager.createDirectory(context, tree, rootId, "Phonemizers")?.let {
                    phDocId = it; phDir = null
                }
            }
        }

        if (vbDocId.isBlank() && plDocId.isBlank()) {
            return Outcome(false, error = context.getString(R.string.ds_no_subdirs))
        }

        // 给已存在的 Singers / Plugins 补上 .nomedia（装过的老资源也要保护，
        // 不能只在"下次安装"时才加 —— 用户已经反馈图片进相册了）
        runCatching { SafManager.ensureNoMedia(context, tree, vbDocId) }
        runCatching { SafManager.ensureNoMedia(context, tree, plDocId) }

        val after = SafManager.listChildren(context, tree, rootId)
        val currentDir = context.getString(R.string.ds_current_dir)
        fun nameOf(docId: String) = after.firstOrNull { it.docId == docId }?.name ?: currentDir

        val vbCount = countKinds(vbDocId, "vb")
        val plCount = countKinds(plDocId, "pl")
        val vbName = if (vbDocId == rootId) currentDir else nameOf(vbDocId)
        val plName = if (plDocId == rootId) currentDir else nameOf(plDocId)

        val vbSuffix = if (vbCount > 0) context.getString(R.string.ds_count_vb, vbCount) else ""
        val plSuffix = if (plCount > 0) context.getString(R.string.ds_count_pl, plCount) else ""
        val summary = context.getString(R.string.ds_summary, vbName + vbSuffix, plName + plSuffix)
        L.d("扫描结果: $summary")

        return Outcome(
            ok = true,
            voicebankDocId = vbDocId,
            pluginDocId = plDocId,
            phonemizerDocId = when {
                phDocId.isNotBlank() -> phDocId
                plDocId.isNotBlank() -> plDocId
                else -> ""
            },
            voicebankName = vbName,
            pluginName = plName,
            voicebankCount = vbCount,
            pluginCount = plCount,
            summary = summary,
        )
    }
}
