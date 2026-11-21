package com.utaumarket.mobile.data.model

/**
 * 一个 USTX / UST 工程（歌曲工程文件）。
 *
 * USTX 是 OpenUtau 的工程格式（YAML），UST 是 UTAU 的传统格式（Shift-JIS 文本）。
 * 这里只解析出列表里想看到的字段 —— 完整解析是 OpenUtau 的事，我们不抢它的活。
 */
/** 工程文件的种类，用于列表分类 */
enum class ProjectKind(val label: String, val ext: String) {
    USTX("USTX", "ustx"),
    UST("UST", "ust"),
    MIDI("MIDI", "mid");

    companion object {
        fun of(fileName: String): ProjectKind? = when (fileName.substringAfterLast('.', "").lowercase()) {
            "ustx" -> USTX
            "ust" -> UST
            "mid", "midi" -> MIDI
            else -> null
        }
    }
}

data class ProjectItem(
    val name: String,
    val fileName: String,
    val uri: String,
    val parentUri: String,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    /** 工程里用到的歌手（可能多个） */
    val singers: List<String> = emptyList(),
    val trackCount: Int = 0,
    val noteCount: Int = 0,
    val kind: ProjectKind = ProjectKind.USTX,
) {
    val isUst: Boolean get() = kind == ProjectKind.UST
    val displayName: String get() = name.ifBlank { fileName.substringBeforeLast('.') }
}
