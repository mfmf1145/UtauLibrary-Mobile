package com.utaumarket.mobile.archive

import android.content.Context
import android.net.Uri
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.model.InstallRule
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.saf.SafManager
import com.utaumarket.mobile.saf.TargetDir
import java.io.File

/**
 * 解压后的目录结构识别与自动安装。
 *
 * 规则（与 OpenUtau 的目录约定一致）：
 *  - UTAU 声库：包含 character.txt 或 oto.ini 的那一层目录；
 *  - DiffSinger 声库：包含 dsconfig.yaml 或 character.yaml 的那一层目录；
 *  - 插件：包含 plugin.txt 的那一层目录；否则取唯一的顶层目录；
 *  - 音素器：任意 .dll。
 */
object Installer {

    private val VOICEBANK_MARKERS = listOf("character.txt", "oto.ini", "dsconfig.yaml", "character.yaml")
    private const val PLUGIN_MARKER = "plugin.txt"

    data class Plan(
        val sourceDir: File,
        val targetName: String,
        val kind: ResourceKind,
        val autoDetected: Boolean,
    )

    /**
     * 按解压出来的**内容**嗅探真实类型，而不是只信清单里声明的类别。
     *
     * 之前的问题：完全按声明类别去找标记文件。一个真声库如果被声明成插件，
     * 就永远找不到 plugin.txt，最后整个包被塞进 Plugins 目录 —— 也就是"声库被识别成插件"。
     *
     * 优先级：声库标记 > plugin.txt > .dll。
     * 声库标记最能说明问题，因为插件/音素器包里不会无缘无故出现 character.txt / oto.ini。
     */
    fun sniffKind(extractDir: File): ResourceKind? {
        var voicebank = false
        var plugin = false
        var phonemizer = false
        walk(extractDir, 0, maxDepth = 3) { _, files, _ ->
            if (files.any { n ->
                    n.equals("character.txt", true) || n.equals("oto.ini", true) ||
                        n.equals("dsconfig.yaml", true) || n.equals("character.yaml", true)
                }
            ) {
                voicebank = true
            }
            if (files.any { it.equals(PLUGIN_MARKER, true) }) plugin = true
            if (files.any { it.endsWith(".dll", true) }) phonemizer = true
        }
        return when {
            voicebank -> ResourceKind.VOICEBANK
            plugin -> ResourceKind.PLUGIN
            phonemizer -> ResourceKind.PHONEMIZER
            else -> null
        }
    }

    /** 在解压目录里寻找符合类别约定的候选根目录。 */
    fun detectRoots(extractDir: File, kind: ResourceKind): List<File> {
        val found = LinkedHashSet<File>()
        // 深度提到 6：有的作者包是 包/角色/版本/音源/… 这种多层，3 层会漏掉
        walk(extractDir, 0, maxDepth = 6) { dir, files, depth ->
            when (kind) {
                ResourceKind.VOICEBANK -> {
                    if (files.any { it.equals("character.txt", true) || it.equals("oto.ini", true) ||
                                it.equals("dsconfig.yaml", true) || it.equals("character.yaml", true) }) {
                        found.add(dir)
                    }
                }
                ResourceKind.PLUGIN -> {
                    if (files.any { it.equals(PLUGIN_MARKER, true) }) found.add(dir)
                }
                ResourceKind.PHONEMIZER -> {
                    if (files.any { it.endsWith(".dll", true) }) found.add(dir)
                }
            }
        }
        return found.toList()
    }

    private fun walk(dir: File, depth: Int, maxDepth: Int, onDir: (File, List<String>, Int) -> Unit) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        val names = children.map { it.name }
        onDir(dir, names, depth)
        for (c in children) if (c.isDirectory) walk(c, depth + 1, maxDepth, onDir)
    }

    /**
     * 生成安装计划。
     * @return 自动识别的计划；若为空则需要用户手动确认（对应需求里"无法自动识别才弹确认窗口"）。
     */
    fun buildPlan(extractDir: File, kind: ResourceKind, fallbackName: String, rule: InstallRule): List<Plan> {
        // 【特例】UTAU 本体包（例如 UTAU0419.zip）里带着 voice/ 文件夹，声库就在那里面。
        // 用户要的只是 voice/ 下的声库，不需要 UTAU 程序本体（exe / res / plugins）。
        run {
            val candidates = buildList {
                add(File(extractDir, "voice"))
                extractDir.listFiles()?.forEach { add(File(it, "voice")) }
            }
            val voiceDir = candidates.firstOrNull { it.isDirectory }
            if (voiceDir != null) {
                val markers = setOf("character.txt", "oto.ini", "character.yaml", "dsconfig.yaml")
                val vbs = voiceDir.listFiles()
                    ?.filter { d -> d.isDirectory && d.listFiles()?.any { it.name.lowercase() in markers } == true }
                    .orEmpty()
                if (vbs.isNotEmpty()) {
                    L.d("在 voice/ 里找到 " + vbs.size + " 个声库，只安装这些，忽略 UTAU 程序本体")
                    return vbs.map { d ->
                        Plan(sourceDir = d, targetName = d.name.ifBlank { fallbackName }, kind = ResourceKind.VOICEBANK, autoDetected = true)
                    }
                }
            }
        }

        // 内容嗅探优先于清单声明：真正的类型以包里的标记文件为准
        val sniffed = sniffKind(extractDir)
        val effective = sniffed ?: kind
        if (sniffed != null && sniffed != kind) {
            L.w("包内容判定为 $sniffed，与清单声明的 $kind 不一致，按内容安装")
        }

        val roots = detectRoots(extractDir, effective)
        if (roots.isNotEmpty()) {
            // 只保留最外层候选，避免同一棵子树被重复安装
            val outermost = roots.filter { r -> roots.none { other -> other != r && r.absolutePath.startsWith(other.absolutePath + File.separator) } }
            return outermost.map { r ->
                // 【防嵌套】有些包是「角色名/内部还有一层同名目录/oto.ini」，
                // 直接装会变成 Singers/角色/角色/oto.ini，扫描器和 OpenUtau 都认不出。
                // 这里判断：如果当前目录自己没有标记文件、且只有一个子目录，就下沉。
                var src = r
                var guard = 0
                while (guard++ < 3) {
                    val kids = src.listFiles()?.filter { it.name != "__MACOSX" && !it.name.startsWith(".") }.orEmpty()
                    val hasMarker = kids.any {
                        it.isFile && VOICEBANK_MARKERS.any { m -> it.name.equals(m, true) }
                    }
                    val onlyDir = kids.filter { it.isDirectory }
                    if (!hasMarker && onlyDir.size == 1) src = onlyDir[0] else break
                }
                Plan(
                    sourceDir = src,
                    targetName = r.name.ifBlank { fallbackName },
                    kind = effective,
                    autoDetected = true,
                )
            }
        }
        // 未识别到标记文件：
        // 【修复】以前直接返回 emptyList，任务就卡在"等待确认"，而且缓存已被清掉 —— 点了重试还是同样的结局。
        // 现在退而求其次：如果解压后只有一个顶层目录，就装它（常见于"包/角色/音源"结构）；
        // 否则装整个解压目录。autoDetected=false，界面会标"未自动识别"。
        L.w("没有找到标记文件，改用兜底计划: " + extractDir.absolutePath)
        val topDirs = extractDir.listFiles()?.filter { it.isDirectory }?.filter {
            it.name != "__MACOSX" && !it.name.startsWith(".")
        }.orEmpty()
        val fallbackSrc = if (topDirs.size == 1) topDirs[0] else extractDir
        return listOf(Plan(fallbackSrc, fallbackName, effective, false))
    }

    /** 把计划中的目录复制进已授权的 SAF 目录。 */
    fun install(
        context: Context,
        target: TargetDir,
        plan: Plan,
        overwrite: Boolean,
        onProgress: (String) -> Unit = {},
        onBytes: ((Long, Long) -> Unit)? = null,
    ): Boolean {
        val treeUri = Uri.parse(target.treeUri)
        val rootDocId = target.docId.ifBlank { SafManager.treeDocId(treeUri) ?: "" }
        if (rootDocId.isBlank()) {
            L.w("SAF 目标目录无效: $target"); return false
        }
        // 装东西之前先给资源根目录打上 .nomedia，别让声库里的图片跑进用户的相册
        runCatching { SafManager.ensureNoMedia(context, treeUri, rootDocId) }
        if (overwrite) {
            val existing = SafManager.findChild(context, treeUri, rootDocId, plan.targetName)
            if (existing != null) {
                onProgress("覆盖已存在的 ${plan.targetName}")
                SafManager.delete(context, treeUri, existing.docId)
            }
        }
        return SafManager.copyDirectory(
            context, treeUri, rootDocId, plan.sourceDir, plan.targetName, onProgress, onBytes,
        )
    }

    /** 判断目标目录下是否已存在同名资源。 */
    fun existsInstalled(context: Context, target: TargetDir, name: String): Boolean {
        val treeUri = Uri.parse(target.treeUri)
        return SafManager.findChild(context, treeUri, target.docId, name) != null
    }
}
