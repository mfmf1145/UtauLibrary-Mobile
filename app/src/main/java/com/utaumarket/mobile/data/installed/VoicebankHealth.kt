package com.utaumarket.mobile.data.installed

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.utaumarket.mobile.data.model.InstalledItem
import com.utaumarket.mobile.saf.SafManager

/**
 * 本地声库体检。
 *
 * 用户最常见的问题是「装了但 OpenUtau 里不出现 / 唱不出声」，
 * 而原因往往就三个：目录是空的、缺 character.yaml、没有 oto.ini 或音频。
 * 这里直接扫已授权的 SAF 目录，把这三类问题指出来，不用开电脑排查。
 */
object VoicebankHealth {

    /** 一条体检结果：severity 2 = 基本用不了，1 = 可能有问题 */
    data class Report(
        val item: InstalledItem,
        val severity: Int,
        val problems: List<String>,
    )

    /** 音频/模型文件的常见后缀 */
    private val AUDIO = listOf(".wav", ".flac", ".onnx", ".frq", ".uwav")

    private val CHAR_FILES = listOf("character.yaml", "character.txt", "dsconfig.yaml")

    fun check(context: Context, items: List<InstalledItem>): List<Report> {
        val out = ArrayList<Report>()
        for (item in items) {
            if (!item.isDirectory) continue
            val report = runCatching { checkOne(context, item) }.getOrNull() ?: continue
            if (report.problems.isNotEmpty()) out.add(report)
        }
        return out.sortedWith(compareByDescending<Report> { it.severity }.thenBy { it.item.name })
    }

    private fun checkOne(context: Context, item: InstalledItem): Report {
        val treeUri = Uri.parse(item.parentUri)
        val docId = runCatching {
            DocumentsContract.getDocumentId(Uri.parse(item.uri))
        }.getOrElse { return Report(item, 0, emptyList()) }

        val children = runCatching { SafManager.listChildren(context, treeUri, docId) }
            .getOrDefault(emptyList())
        val lower = children.map { it.name.lowercase() }

        val problems = ArrayList<String>()
        var severity = 0

        if (children.isEmpty()) {
            return Report(item, 2, listOf("目录是空的（没有读到任何文件）"))
        }

        // ① 配置文件：没有它 OpenUtau 里根本不会出现这个歌手
        val hasChar = lower.any { n -> CHAR_FILES.any { n == it } }
        val hasOto = lower.any { it == "oto.ini" || it == "oto_ini.txt" }
        // ② 音频：直接子项里没有就再往下看一层（绝大多数声库都是这样）
        var audioCount = children.count { d -> !d.isDirectory && AUDIO.any { d.name.lowercase().endsWith(it) } }
        if (audioCount == 0) {
            for (dir in children.filter { it.isDirectory }.take(6)) {
                val sub = runCatching { SafManager.listChildren(context, treeUri, dir.docId) }
                    .getOrDefault(emptyList())
                audioCount += sub.count { d -> !d.isDirectory && AUDIO.any { d.name.lowercase().endsWith(it) } }
            }
        }

        if (!hasChar) {
            problems.add("缺少 character.yaml / character.txt：OpenUtau 不会把它认成歌手")
            severity = 2
        }
        if (!hasOto && audioCount > 0) {
            problems.add("缺少 oto.ini：UTAU 引擎无法做音素拼接（DiffSinger 声库可忽略）")
            if (severity < 2) severity = 1
        }
        if (audioCount == 0) {
            problems.add("没有找到音频或模型文件（.wav / .flac / .onnx）")
            severity = 2
        }
        if (item.childCount == 0) {
            problems.add("目录里没有文件")
            severity = 2
        }
        return Report(item, severity, problems)
    }
}
