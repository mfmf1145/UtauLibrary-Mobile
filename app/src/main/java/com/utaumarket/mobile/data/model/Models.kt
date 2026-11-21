package com.utaumarket.mobile.data.model

import org.json.JSONObject

/** 三大资源类别。顺序与底部导航一致。 */
enum class ResourceKind(val id: String) {
    PLUGIN("plugin"),
    PHONEMIZER("phonemizer"),
    VOICEBANK("voicebank");

    companion object {
        fun fromId(id: String?): ResourceKind? = entries.firstOrNull { it.id == id }
    }
}

/** 网络状态标记：🟢直连可用 / 🟡直连缓慢 / 🔴国内无法访问 */
enum class NetworkStatus(val id: String, val emoji: String) {
    GREEN("green", "\uD83D\uDFE2"),
    YELLOW("yellow", "\uD83D\uDFE1"),
    RED("red", "\uD83D\uDD34"),
    /** 国外网盘：能不能下取决于你是否开了代理 —— 不该假装"直连缓慢"或"无法访问" */
    PROXY("proxy", "\uD83D\uDD35"),
    UNKNOWN("unknown", "\u26AA");

    companion object {
        fun fromId(id: String?): NetworkStatus = when (id?.lowercase()) {
            // "vpn" = 清单里标注的「国内直连不靠谱」，按黄灯显示。
            // （真正被判红的国外网盘由 NetworkProbe 的 needsProxy 规则处理，
            //   不会走到这里；这里覆盖的是其它需要代理的站点。）
            "vpn" -> YELLOW
            "green", "g" -> GREEN
            "yellow", "y", "slow" -> YELLOW
            "red", "r", "blocked" -> RED
            else -> UNKNOWN
        }
    }
}

enum class EngineType(val id: String) {
    UTAU("utau"),
    DIFFSINGER("diffsinger"),
    OTHER("other");

    companion object {
        fun fromId(id: String?): EngineType = when (id?.lowercase()) {
            "utau" -> UTAU
            "diffsinger", "ds" -> DIFFSINGER
            else -> OTHER
        }
    }
}

/** 插件二级分类 */
enum class PluginCategory(val id: String) {
    LYRICS("lyrics"),
    PITCH("pitch"),
    MARKER("marker"),
    SOUND("sound"),
    UTILITY("utility");

    companion object {
        fun fromId(id: String?): PluginCategory =
            entries.firstOrNull { it.id == id } ?: UTILITY
    }
}

/** 音素器二级分类 */
enum class PhonemizerLanguage(val id: String) {
    JA("ja"),
    ZH("zh"),
    EN("en"),
    OTHER("other");

    companion object {
        fun fromId(id: String?): PhonemizerLanguage = when (id?.lowercase()) {
            "ja", "jp", "japanese" -> JA
            "zh", "cn", "chinese", "zh-cn" -> ZH
            "en", "english" -> EN
            else -> OTHER
        }
    }
}

/** 声库二级分类 */
enum class VoicebankFamily(val id: String) {
    UTAU("utau"),
    DIFFSINGER("diffsinger"),
    OTHER("other");

    companion object {
        fun fromId(id: String?): VoicebankFamily = when (id?.lowercase()) {
            "utau" -> UTAU
            "diffsinger" -> DIFFSINGER
            else -> OTHER
        }
    }
}

/** 语言细分（声库/音素器共用） */
enum class LanguageTag(val id: String) {
    JA("ja"),
    ZH("zh"),
    EN("en"),
    KO("ko"),
    MULTI("multi"),
    OTHER("other");

    companion object {
        fun fromId(id: String?): LanguageTag = when (id?.lowercase()) {
            "ja", "jp" -> JA
            "zh", "cn", "zh-cn" -> ZH
            "en" -> EN
            "ko" -> KO
            "multi", "multilingual" -> MULTI
            else -> OTHER
        }

    }
}

/** 安装规则，决定解压产物如何落到目标目录。 */
enum class InstallRule(val id: String) {
    /** 声库：解压后把包含 character.txt / oto.ini / dsconfig.yaml 的那一层目录整体复制到声库目录 */
    VOICEBANK_AUTO("voicebank_auto"),
    /** 插件：解压后把包含 plugin.txt 的那一层目录复制到插件目录 */
    PLUGIN_AUTO("plugin_auto"),
    /** 插件：解压到插件目录下的独立子文件夹 */
    UNZIP_TO_PLUGINS("unzip_to_plugins"),
    /** 音素器：解压 .dll 到插件目录并可提示不兼容 */
    PHONEMIZER_DLL("phonemizer_dll"),
    /**
     * 分支改版：下载的是一个 **APK 安装包**，不参与解压/写入声库目录，
     * 下载完直接交给系统安装器。
     */
    APK("apk"),
    /** 未知：解压完成后交给用户确认 */
    MANUAL("manual");

    companion object {
        fun fromId(id: String?): InstallRule = entries.firstOrNull { it.id == id } ?: VOICEBANK_AUTO
    }
}

/**
 * 资源条目。声库/插件来自内置 JSON 清单；音素器由 GitHub API 动态构造。
 * 所有展示字段允许为空，UI 保持布局一致。
 */
data class ResourceItem(
    val id: String,
    val kind: ResourceKind,
    val name: String,
    val nameTranslated: String = "",
    /**
     * 各语言的专属名字（清单里可选字段 name_ja / name_en / name_zh）。
     * 没填就回退：[name]（原文），中文再回退到 [nameTranslated]。
     */
    val nameJa: String = "",
    val nameEn: String = "",
    val nameZh: String = "",
    val author: String = "",
    val engine: EngineType = EngineType.OTHER,
    val language: String = "",
    val family: VoicebankFamily = VoicebankFamily.OTHER,
    val pluginCategory: PluginCategory = PluginCategory.UTILITY,
    val phonemizerLanguage: PhonemizerLanguage = PhonemizerLanguage.OTHER,
    val url: String = "",
    val mirrorUrl: String = "",
    val network: NetworkStatus = NetworkStatus.UNKNOWN,
    val format: String = "",
    val fileSize: Long = 0L,
    val version: String = "",
    val releaseDate: String = "",
    val sourcePlatform: String = "",
    val license: String = "",
    val description: String = "",
    val remark: String = "",
    val range: String = "",
    val scaleCount: String = "",
    val recordingType: String = "",
    val installRule: InstallRule = InstallRule.MANUAL,
    val targetSubDir: String = "",
    val fileName: String = "",
    val sha256: String = "",
    /** 移动端兼容性提示，例如 .dll 音素器、.exe 插件 */
    val mobileWarning: String = "",
    /** 是否为「托管 .NET 程序集」类型的 .dll（OpenUtau Mobile 只加载这种） */
    val managedDotnetDll: Boolean = false,
    val detailUrl: String = "",
    /**
     * 萌娘百科条目页的**直达**地址（爬取时确认过存在的）。
     * 有它就不要再退化成搜索页 —— 用户点「萌娘百科」应该直接进条目。
     */
    val moegirlUrl: String = "",
    /** 综合热度（可下载 + 有百科 + 系列规模 + 体积 + 知名歌手加权），用于默认排序 */
    val heat: Float = 0f,
    /** 国内网盘配布地址（百度/夸克/微云…）；有就说明只能在网盘手动下 */
    val netdiskUrl: String = "",
    /** 网盘提取码；界面会显示并允许一键复制 */
    val netdiskPwd: String = "",
    /** 出身：jp=日本 / cn=中国 / other=其他国家（按名字假名与语言字段推断） */
    val origin: String = "",
    /** 手动指定的排序优先级（>0 的排在最前，按数字升序）；0 = 不指定，按体积排 */
    val sortOrder: Int = 0,
    /** 系列在主列表里固定的位置（1 起）；0 = 不固定，按热度排 */
    val seriesPos: Int = 0,
    /** 配布视频地址（B站 / YouTube / Nico）；空表示没有 */
    val videoUrl: String = "",
    /** true = 这是「分支/改版」条目（第三方修改的宿主程序），不是普通插件 */
    val isFork: Boolean = false,
    /** 音源的使用规定 / 利用規約 / Terms of Use 页面地址；空表示没找到 */
    val termsUrl: String = "",
    val stars: Int = 0,
    /**
     * 同一角色的不同分支版本所属的系列名，例如「重音テト」下挂 11 个分支。
     * 为空表示这条本身就是独立的一个声库，不需要二级版本页。
     */
    val series: String = "",
    /** 系列名的中文/罗马字写法（如 重音テト -> 重音Teto），系列卡片第二行用 */
    val seriesZh: String = "",
    /** 作者/角色的 X (Twitter) 主页；只有个别条目有 */
    val xUrl: String = "",
    /** 搜索用的别名：罗马字、常见昵称等，空格分隔。例如「重音テト」→ teto kasane-teto */
    val keywords: String = "",
    /**
     * 资料条目：只在清单里占个位置（带萌娘百科入口），但没有可用的直链。
     * 界面上不显示下载按钮，只显示资料入口，避免给出点不动的按钮。
     */
    val referenceOnly: Boolean = false,
) {
    val displayName: String
        get() = if (nameTranslated.isNotBlank() && nameTranslated != name) "$name（$nameTranslated）" else name

    /**
     * 按界面语言给出"另一种写法"的名字（列表第二行展示）：
     * 中文界面 → name_zh（没填就用 name_translated）；日文 → name_ja；英文 → name_en。
     * 与 [name] 相同则返回空串，避免重复显示。
     *
     * 清单里目前只有 name_translated（中文）有数据，ja/en 两个字段留空即走回退，
     * 之后往 manifest 里补数据就会自动生效，不用改代码。
     */
    fun localizedName(langTag: String): String {
        val alt = when (langTag.lowercase().substringBefore('-')) {
            "zh" -> nameZh.ifBlank { nameTranslated }
            "ja" -> nameJa
            "en" -> nameEn
            else -> ""
        }
        return if (alt.isNotBlank() && alt != name) alt else ""
    }

    /** 非中文界面不显示中文译名（用户要求）。 */
    fun displayName(showTranslated: Boolean): String {
        if (!showTranslated) return name
        // 中文界面优先用 name_zh（例如 重音テト -> 重音Teto），再退回清单里的中文译名
        val alt = nameZh.ifBlank { nameTranslated }
        return if (alt.isNotBlank() && alt != name) "$name（$alt）" else name
    }

    /**
     * 搜索匹配：名称 / 译名 / 别名 / 作者 / 系列名。
     * 忽略大小写，也忽略空格、连字符、下划线 —— 这样 teto、kasane teto、kasane-teto 都能命中。
     */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val needle = q.lowercase()
        val flat = needle.filter { !it.isWhitespace() && it != '-' && it != '_' }
        fun hit(s: String): Boolean {
            if (s.isBlank()) return false
            val l = s.lowercase()
            if (l.contains(needle)) return true
            return l.filter { !it.isWhitespace() && it != '-' && it != '_' }.contains(flat)
        }
        if (hit(name) || hit(nameTranslated) || hit(displayName) ||
            hit(author) || hit(keywords) || hit(series)) return true
        // 罗马音兜底：搜 "teto" 也能命中「重音テト」。
        // 只有查询串全是拉丁字符时才做，避免日文查询白跑一次转换。
        if (flat.none { it.code > 0x7F }) {
            if (romajiKey.isBlank()) return false
            return romajiKey.contains(flat)
        }
        return false
    }

    /**
     * 罗马音检索用的键（只算一次）。
     *
     * 汉字转不了读音，只能覆盖假名部分；对「重音テト」这种名字够了 ——
     * テト → teto，搜 teto 就能命中。
     */
    val romajiKey: String by lazy {
        val raw = listOf(name, series, author, keywords, nameTranslated)
            .joinToString(" ")
        if (!com.utaumarket.mobile.core.Kana.hasKana(raw)) "" else
            com.utaumarket.mobile.core.Kana.toRomaji(raw)
                .lowercase()
                .filter { !it.isWhitespace() && it != '-' && it != '_' }
    }

    val effectiveUrl: String get() = mirrorUrl.ifBlank { url }

    companion object {
        fun fromVoicebankJson(o: JSONObject): ResourceItem = ResourceItem(
            id = o.optString("id").ifBlank { o.optString("name") },
            kind = ResourceKind.VOICEBANK,
            name = o.optString("name"),
            nameTranslated = o.optString("name_translated"),
            nameJa = o.optString("name_ja"),
            nameEn = o.optString("name_en"),
            nameZh = o.optString("name_zh"),
            author = o.optString("author"),
            engine = EngineType.fromId(o.optString("engine")),
            language = o.optString("language"),
            family = VoicebankFamily.fromId(o.optString("family").ifBlank { o.optString("engine") }),
            url = o.optString("url"),
            mirrorUrl = o.optString("mirror_url"),
            network = NetworkStatus.fromId(o.optString("network")),
            format = o.optString("format"),
            fileSize = o.optLong("file_size"),
            version = o.optString("version"),
            releaseDate = o.optString("release_date"),
            sourcePlatform = o.optString("source_platform"),
            license = o.optString("license"),
            description = o.optString("description"),
            remark = o.optString("remark"),
            range = o.optString("range"),
            scaleCount = o.optString("scale_count"),
            recordingType = o.optString("recording_type"),
            installRule = InstallRule.fromId(o.optString("install_rule").ifBlank { "voicebank_auto" }),
            targetSubDir = o.optString("target_subdir"),
            fileName = o.optString("file_name"),
            sha256 = o.optString("sha256"),
            mobileWarning = o.optString("mobile_warning"),
            detailUrl = o.optString("detail_url"),
            moegirlUrl = o.optString("moegirl_url"),
            heat = o.optDouble("heat", 0.0).toFloat(),
            netdiskUrl = o.optString("netdisk_url"),
            netdiskPwd = o.optString("netdisk_pwd"),
            origin = o.optString("origin"),
            sortOrder = o.optInt("sort_order", 0),
            seriesPos = o.optInt("series_pos", 0),
            videoUrl = o.optString("video_url"),
            isFork = o.optBoolean("is_fork", false),
            termsUrl = o.optString("terms_url"),
            // GitHub 星标数：插件页默认按它降序排（之前漏了这行，星标恒为 0，排序等于没做）
            stars = o.optInt("stars", 0),
            series = o.optString("series"),
            seriesZh = o.optString("series_zh"),
            xUrl = o.optString("x_url"),
            keywords = o.optString("keywords"),
            referenceOnly = o.optBoolean("reference_only"),
        )

        fun fromPluginJson(o: JSONObject): ResourceItem = ResourceItem(
            keywords = o.optString("keywords"),
            id = o.optString("id").ifBlank { o.optString("name") },
            // 手动收录的条目里也可能有音素器（例如中文转音素、外部词典编辑）。
            // 之前一律当插件，于是"音素器混在插件页里"。这里按 kind 字段或关键词判定。
            // 只用清单里显式写的 kind 字段判定。
            // 之前还按关键词猜（"音素"/"发音"/"dictionary"…），结果 AutoAlts4Arpasing、
            // context-n 这种普通插件被误判成音素器，插件只剩 6 个。宁可漏判也不要误判。
            kind = if (o.optString("kind").equals("phonemizer", true)) ResourceKind.PHONEMIZER
            else ResourceKind.PLUGIN,
            name = o.optString("name"),
            nameTranslated = o.optString("name_translated"),
            nameJa = o.optString("name_ja"),
            nameEn = o.optString("name_en"),
            nameZh = o.optString("name_zh"),
            author = o.optString("author"),
            pluginCategory = PluginCategory.fromId(o.optString("category")),
            language = o.optString("language"),
            // 之前这里没设 phonemizerLanguage，所有音素器都被归到默认的「其他」，
            // 语言分类标签形同虚设。现在按清单里的 language 字段映射。
            phonemizerLanguage = PhonemizerLanguage.fromId(o.optString("language")),
            url = o.optString("url"),
            mirrorUrl = o.optString("mirror_url"),
            network = NetworkStatus.fromId(o.optString("network")),
            format = o.optString("format"),
            fileSize = o.optLong("file_size"),
            version = o.optString("version"),
            releaseDate = o.optString("release_date"),
            sourcePlatform = o.optString("source_platform"),
            license = o.optString("license"),
            description = o.optString("description"),
            remark = o.optString("remark"),
            installRule = InstallRule.fromId(o.optString("install_rule").ifBlank { "plugin_auto" }),
            targetSubDir = o.optString("target_subdir"),
            fileName = o.optString("file_name"),
            sha256 = o.optString("sha256"),
            mobileWarning = o.optString("mobile_warning"),
            detailUrl = o.optString("detail_url"),
            moegirlUrl = o.optString("moegirl_url"),
            heat = o.optDouble("heat", 0.0).toFloat(),
            netdiskUrl = o.optString("netdisk_url"),
            netdiskPwd = o.optString("netdisk_pwd"),
            origin = o.optString("origin"),
            sortOrder = o.optInt("sort_order", 0),
            seriesPos = o.optInt("series_pos", 0),
            videoUrl = o.optString("video_url"),
            isFork = o.optBoolean("is_fork", false),
            termsUrl = o.optString("terms_url"),
            // GitHub 星标数：插件页默认按它降序排（之前漏了这行，星标恒为 0，排序等于没做）
            stars = o.optInt("stars", 0),
            // 系列归并字段：分支改版页靠它把「同一项目的多个架构/版本」合成一张卡
            // （之前漏了这两行，三个架构各自成一张卡 —— 用户反馈"为什么会有三个"）
            series = o.optString("series"),
            seriesZh = o.optString("series_zh"),
        )
    }
}
