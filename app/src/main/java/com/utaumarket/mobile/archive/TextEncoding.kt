package com.utaumarket.mobile.archive

import com.utaumarket.mobile.core.L
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 声库元数据的编码处理。
 *
 * 现实情况：日本 UTAU 音源的 character.txt 和 oto.ini 大多是 Shift-JIS(CP932)：
 *   character.txt: 8f64 89b9 ...  → cp932 name=重音テト… / utf-8 非法
 *   oto.ini:       82 98 82 a9    → cp932 xか.wav       / utf-8 非法
 * 而 OpenUtau / OpenUtau Mobile 按 UTF-8 读，于是歌手名变成问号、oto 全部失效
 * —— 这正是装完进 OpenUtau 全是乱码的原因。
 *
 * 所以在安装前把文本元数据就地转成 UTF-8。
 */
object TextEncoding {

    private val TEXT_SUFFIX = listOf(".txt", ".ini", ".yaml", ".yml", ".cfg", ".conf")

    private val UTF8 = Charsets.UTF_8
    private val SJIS: Charset? = runCatching { Charset.forName("MS932") }.getOrNull()
    private val EUCJP: Charset? = runCatching { Charset.forName("EUC-JP") }.getOrNull()

    private fun strictDecode(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    } catch (_: Throwable) {
        null
    }


    /**
     * 按 **OpenUtau 的规则**解码一段文本（用于读取 character.txt 等元数据显示）。
     *
     * 规则：合法 UTF-8（或有 BOM）就用 UTF-8，否则默认 Shift-JIS。
     * 之前 SafManager.readText 无条件按 UTF-8 解码，Shift-JIS 的介绍就全变成乱码 ——
     * OpenUtau 里显示正常、这个程序里是问号，就是这个原因。
     */
    fun decodeLenient(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return String(bytes, 3, bytes.size - 3, UTF8)
        strictDecode(bytes, UTF8)?.let { return it }
        SJIS?.let { cs -> strictDecode(bytes, cs)?.let { return it } }
        EUCJP?.let { cs -> strictDecode(bytes, cs)?.let { return it } }
        // 兜底：用 Shift-JIS 宽松解（它会尽量映射），比 UTF-8 宽松解更接近原意
        return String(bytes, SJIS ?: UTF8)
    }

    /** 用指定编码严格解码；失败返回 null。 */
    fun decode(bytes: ByteArray, cs: Charset): String? = strictDecode(bytes, cs)

    /**
     * 严格照 OpenUtau 的规则判定"这个声库的文本元数据该用什么编码读"。
     *
     * 依据 OpenUtau 源码：
     *   OpenUtau.Core/Classic/VoicebankLoader.cs:107
     *       Encoding encoding = Encoding.GetEncoding("shift_jis");
     *       if (!string.IsNullOrEmpty(bankConfig?.TextFileEncoding))
     *           encoding = Encoding.GetEncoding(bankConfig.TextFileEncoding);
     *   OpenUtau.Core/Classic/VoicebankConfig.cs:48
     *       "Encoding of the voicebank's text files, such as character.txt and oto.ini,
     *        e.g. shift_jis or utf-8. **If unspecified, shift_jis.**
     *        An oto.ini can declare its own with a #Charset: line."
     *
     * 优先级：oto.ini 里的 #Charset: > character.yaml 的 text_file_encoding > 默认 Shift-JIS。
     *
     * 【重要】这个函数只用来"看清楚是什么编码"，**绝不写回文件**。
     * 之前把内容转成 UTF-8 正是乱码的根源 —— OpenUtau 默认就按 Shift-JIS 读。
     */
    fun resolveEncoding(characterTxt: ByteArray?, otoIni: ByteArray?, yamlEncoding: String?): Charset {
        // 1) oto.ini 自己声明的
        if (otoIni != null) {
            val head = runCatching { String(otoIni, 0, minOf(otoIni.size, 512), Charsets.ISO_8859_1) }.getOrDefault("")
            val m = Regex("""#Charset:\s*([A-Za-z0-9_\-]+)""").find(head)
            if (m != null) {
                runCatching { Charset.forName(m.groupValues[1]) }.getOrNull()?.let { return it }
            }
        }
        // 2) character.yaml 声明的
        if (!yamlEncoding.isNullOrBlank()) {
            runCatching { Charset.forName(yamlEncoding) }.getOrNull()?.let { return it }
        }
        // 3) 有 BOM 就是 UTF-8
        if (characterTxt != null && characterTxt.size >= 3 &&
            characterTxt[0] == 0xEF.toByte() && characterTxt[1] == 0xBB.toByte() && characterTxt[2] == 0xBF.toByte()
        ) return UTF8
        // 4) 默认 Shift-JIS（OpenUtau 的默认值）
        return SJIS ?: UTF8
    }

    /**
     * 文件里是否含 U+FFFD（替换字符）。
     *
     * 出现它就说明这个文件曾经被"有损解码"过 —— 原始字节已经丢失，
     * 无法用任何编码救回来，只能重新下载。
     */
    fun isCorrupted(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false
        var i = 0
        while (i + 2 < bytes.size) {
            if (bytes[i] == 0xEF.toByte() && bytes[i + 1] == 0xBF.toByte() && bytes[i + 2] == 0xBD.toByte()) return true
            i++
        }
        return false
    }

    /** 猜出这段文本的编码；已经是 UTF-8 就返回 null（表示不用转）。 */
    fun detectCharset(bytes: ByteArray): Charset? {
        if (bytes.isEmpty()) return null
        if (bytes.all { it >= 0 && it < 0x80 }) return null
        if (strictDecode(bytes, UTF8) != null) return null
        if (SJIS != null && strictDecode(bytes, SJIS) != null) return SJIS
        if (EUCJP != null && strictDecode(bytes, EUCJP) != null) return EUCJP
        return null
    }

    /** 把目录里所有文本元数据转成 UTF-8（就地）。返回转换的文件数。 */
    /**
     * 把目录里的文本元数据转回 Shift-JIS（就地）。返回转换的文件数。
     *
     * OpenUtau（含 Mobile）读 character.txt / oto.ini 用的就是 Shift-JIS，
     * 这也是 UTAU 的原生格式。早期版本误判成内容编码不对、把内容转成 UTF-8，
     * 结果 OpenUtau 读出来全是乱码。安装流程已改成完全不动编码，
     * 这个函数专门把已经被转坏的声库修回去。
     */
    fun toSJISInPlace(root: File): Int {
        val sjis = SJIS ?: return 0
        var converted = 0
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var guard = 0
        while (stack.isNotEmpty() && guard < 20000) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                guard++
                if (f.isDirectory) {
                    if (f.name != "__MACOSX") stack.addLast(f)
                    continue
                }
                val lower = f.name.lowercase()
                if (TEXT_SUFFIX.none { lower.endsWith(it) }) continue
                if (f.length() <= 0 || f.length() > 8L * 1024 * 1024) continue
                val bytes = runCatching { f.readBytes() }.getOrNull() ?: continue
                // 只处理当前是合法 UTF-8 且含非 ASCII 的文件；不是 UTF-8 说明没被转坏
                val text = strictDecode(bytes, UTF8) ?: continue
                if (text.all { it.code < 0x80 }) continue
                val out = runCatching { text.toByteArray(sjis) }.getOrNull() ?: continue
                // 能无损转回去才写，避免丢字符
                if (runCatching { String(out, sjis) }.getOrNull() != text) continue
                runCatching {
                    f.writeBytes(out)
                    converted++
                    L.d("编码还原 " + f.name + " : UTF-8 -> Shift-JIS")
                }
            }
        }
        return converted
    }

    fun toUtf8InPlace(root: File): Int {
        var converted = 0
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var guard = 0
        while (stack.isNotEmpty() && guard < 20000) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                guard++
                if (f.isDirectory) {
                    if (f.name != "__MACOSX") stack.addLast(f)
                    continue
                }
                val lower = f.name.lowercase()
                if (TEXT_SUFFIX.none { lower.endsWith(it) }) continue
                if (f.length() <= 0 || f.length() > 8L * 1024 * 1024) continue
                val bytes = runCatching { f.readBytes() }.getOrNull() ?: continue
                val cs = detectCharset(bytes) ?: continue
                val text = strictDecode(bytes, cs) ?: continue
                runCatching {
                    f.writeBytes(text.toByteArray(UTF8))
                    converted++
                    L.d("编码转换 ${f.name}: ${cs.name()} -> UTF-8")
                }
            }
        }
        if (converted > 0) L.d("共转换 $converted 个文本文件为 UTF-8")
        return converted
    }
}
