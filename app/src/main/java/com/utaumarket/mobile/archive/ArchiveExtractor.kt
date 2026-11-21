package com.utaumarket.mobile.archive

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.utaumarket.mobile.core.L
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import org.apache.commons.compress.archivers.zip.ZipFile as CcZipFile

/**
 * 内置解压引擎：zip / rar / 7z / tar.gz / .uar（按文件头识别）。
 *
 * 全部使用纯 JVM 实现（java.util.zip + Apache Commons Compress + junrar），
 * 不需要任何 native .so，因此可以完整打包进 APK，用户安装即用。
 */
object ArchiveExtractor {

    /**
     * 头部是不是压缩包。
     * 用途：拦截"长度像下完了、其实内容是空洞/HTML 错误页"的坏文件，
     * 避免把这种东西送去解压后只报一句含糊的"解压失败"。
     */
    /**
     * 是不是一个**完整**的压缩包。
     *
     * 只看头部魔数不够：旧版多线程分块会 setLength(total) 预分配文件，
     * 头部是正常的 PK、长度也对，但中间全是空洞，拿去解压必然失败。
     * zip 的末尾一定有 EOCD 记录（PK\x05\x06），据此判断是否真的下完整了。
     */
    fun isCompleteArchive(file: java.io.File): Boolean {
        if (!looksLikeArchive(file)) return false
        return runCatching {
            val len = file.length()
            if (len < 32) return false
            val head = ByteArray(4)
            java.io.FileInputStream(file).use { it.read(head) }
            val isZip = head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
            if (!isZip) return true
            // zip: 在最后 128KB 里找 EOCD
            val tailLen = minOf(len, 128L * 1024).toInt()
            val tail = ByteArray(tailLen)
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(len - tailLen)
                raf.readFully(tail)
            }
            for (i in tail.size - 22 downTo 0) {
                if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4B.toByte() &&
                    tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()
                ) {
                    return true
                }
            }
            false
        }.getOrDefault(false)
    }

    fun looksLikeArchive(file: java.io.File): Boolean {
        if (!file.isFile || file.length() < 8) return false
        return runCatching {
            val h = ByteArray(8)
            java.io.FileInputStream(file).use { it.read(h) }
            when {
                h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() -> true                 // zip
                h[0] == 0x52.toByte() && h[1] == 0x61.toByte() && h[2] == 0x72.toByte() -> true // rar
                h[0] == 0x37.toByte() && h[1] == 0x7A.toByte() -> true                 // 7z
                h[0] == 0x1F.toByte() && h[1] == 0x8B.toByte() -> true                 // gz
                h[0] == 0x4D.toByte() && h[1] == 0x5A.toByte() -> true                 // exe/self-extract
                else -> false
            }
        }.getOrDefault(false)
    }

    enum class Kind { ZIP, RAR, SEVEN_Z, TAR_GZ, TAR, UNKNOWN }

    const val MAX_TOTAL_BYTES = 6L * 1024 * 1024 * 1024 // 6GB 上限，防止 zip bomb
    const val MAX_ENTRIES = 200_000

    fun detect(file: File): Kind {
        val head = ByteArray(8)
        val n = try {
            RandomAccessFile(file, "r").use { it.read(head) }
        } catch (_: Throwable) { 0 }
        if (n >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()) return Kind.ZIP
        if (n >= 7 && head[0] == 0x52.toByte() && head[1] == 0x61.toByte() && head[2] == 0x72.toByte() &&
            head[3] == 0x21.toByte() && head[4] == 0x1A.toByte() && head[5] == 0x07.toByte()
        ) return Kind.RAR
        if (n >= 6 && head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() && head[2] == 0xBC.toByte() &&
            head[3] == 0xAF.toByte() && head[4] == 0x27.toByte() && head[5] == 0x1C.toByte()
        ) return Kind.SEVEN_Z
        if (n >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()) return Kind.TAR_GZ
        return Kind.UNKNOWN
    }

    /**
     * 解压到 destDir。返回 true 表示成功。
     * 所有条目都会被做路径穿越（zip-slip）检查。
     */
    /**
     * 统计压缩包里的**文件**条目数（不数目录），用来校验解压有没有丢东西。
     *
     * 用户明确要求"下载出来的不能出现损坏或丢失"，所以安装后要拿它对一下
     * 实际解压出的文件数 —— 少一个都要在日志和提示里说出来。
     * 无法识别的格式返回 -1（表示"不校验"）。
     */
    fun entryCount(archive: File): Int = try {
        java.util.zip.ZipFile(archive).use { zf ->
            var n = 0
            val en = zf.entries()
            while (en.hasMoreElements()) {
                if (!en.nextElement().isDirectory) n++
            }
            n
        }
    } catch (t: Throwable) {
        -1
    }

    /** 递归统计目录里的文件数 */
    fun countFiles(dir: File): Int {
        var n = 0
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        var guard = 0
        while (stack.isNotEmpty() && guard < 200000) {
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (f in kids) {
                guard++
                if (f.isDirectory) stack.addLast(f) else n++
            }
        }
        return n
    }

    fun extract(archive: File, destDir: File, onProgress: (String) -> Unit = {}): Boolean {
        if (!destDir.exists() && !destDir.mkdirs()) return false
        return try {
            when (detect(archive)) {
                Kind.ZIP -> extractZip(archive, destDir, onProgress)
                Kind.RAR -> extractRar(archive, destDir, onProgress)
                Kind.SEVEN_Z -> extract7z(archive, destDir, onProgress)
                Kind.TAR_GZ -> extractTarGz(archive, destDir, onProgress)
                else -> {
                    L.w("未知压缩格式: ${archive.name}")
                    false
                }
            }
        } catch (t: Throwable) {
            L.e("解压失败: ${archive.name}", t)
            false
        }
    }

    private const val ENC_UTF8 = "UTF-8"

    /** 日文 Windows 的 Shift-JIS（CP932）。UTAU 音源绝大多数用这个。 */
    private const val ENC_SJIS = "MS932"

    /** 用某个编码解开条目名后，是否出现"坏字符"（说明这个编码不对） */
    private fun namesLookBroken(archive: File, encoding: String): Boolean = runCatching {
        var broken = false
        var checked = 0
        CcZipFile(archive, encoding).use { zf ->
            val en = zf.entries
            while (en.hasMoreElements() && checked < 500) {
                val n = en.nextElement().name
                checked++
                if (n.contains('�') || n.any { it.code in 0x80..0x9F }) {
                    broken = true
                    break
                }
            }
        }
        broken
    }.getOrDefault(false)

    /**
     * 自动为压缩包条目名挑编码。
     *
     * 现实情况：TETOLite081222.zip 这类日本 UTAU 音源，条目名是**裸 Shift-JIS 字节**
     * （例如 82 a0 就是「あ」），而且没置 UTF-8 标志位；Java 默认按 UTF-8 解会解成乱码，
     * 于是解出来的 wav 名和 oto.ini 里写的对不上，装到 OpenUtau 里就是一堆乱码文件名。
     *
     * 判据：先按 UTF-8 解，出现替换字符/控制字符就说明不是 UTF-8；再试 MS932。
     */
    fun detectZipEncoding(archive: File): String {
        if (!namesLookBroken(archive, ENC_UTF8)) return ENC_UTF8
        return if (!namesLookBroken(archive, ENC_SJIS)) ENC_SJIS else ENC_UTF8
    }

    private fun extractZip(archive: File, destDir: File, onProgress: (String) -> Unit): Boolean {
        val encoding = detectZipEncoding(archive)
        L.d("解压 ${archive.name}，条目名编码判定为 $encoding")
        var total = 0L
        var count = 0
        CcZipFile(archive, encoding).use { zf ->
            val entries = zf.entries
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (++count > MAX_ENTRIES) return false
                val out = safeResolve(destDir, e.name) ?: continue
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                total += e.size.coerceAtLeast(0)
                if (total > MAX_TOTAL_BYTES) return false
                zf.getInputStream(e).use { ins ->
                    FileOutputStream(out).use { fos -> ins.copyTo(fos, 128 * 1024) }
                }
                onProgress(e.name)
            }
        }
        return true
    }

    private fun extractRar(archive: File, destDir: File, onProgress: (String) -> Unit): Boolean {
        var total = 0L
        var count = 0
        Archive(archive).use { rar ->
            val headers: List<FileHeader> = rar.fileHeaders
            for (h in headers) {
                if (++count > MAX_ENTRIES) return false
                val name = h.fileName
                val out = safeResolve(destDir, name) ?: continue
                if (h.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                total += h.fullUnpackSize
                if (total > MAX_TOTAL_BYTES) return false
                FileOutputStream(out).use { fos -> rar.extractFile(h, fos) }
                onProgress(name)
            }
        }
        return true
    }

    private fun extract7z(archive: File, destDir: File, onProgress: (String) -> Unit): Boolean {
        var total = 0L
        var count = 0
        SevenZFile.builder().setFile(archive).get().use { sz ->
            var entry = sz.nextEntry
            while (entry != null) {
                if (++count > MAX_ENTRIES) return false
                val out = safeResolve(destDir, entry.name)
                if (out == null) {
                    entry = sz.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    total += entry.size.coerceAtLeast(0)
                    if (total > MAX_TOTAL_BYTES) return false
                    FileOutputStream(out).use { fos ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            val r = sz.read(buf)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                        }
                    }
                    onProgress(entry.name)
                }
                entry = sz.nextEntry
            }
        }
        return true
    }

    private fun extractTarGz(archive: File, destDir: File, onProgress: (String) -> Unit): Boolean {
        var count = 0
        BufferedInputStream(archive.inputStream()).use { bis ->
            GzipCompressorInputStream(bis).use { gz ->
                TarArchiveInputStream(gz).use { tar ->
                    var e = tar.nextEntry
                    while (e != null) {
                        if (++count > MAX_ENTRIES) return false
                        val out = safeResolve(destDir, e.name)
                        if (out != null) {
                            if (e.isDirectory) out.mkdirs()
                            else {
                                out.parentFile?.mkdirs()
                                FileOutputStream(out).use { fos -> tar.copyTo(fos, 128 * 1024) }
                                onProgress(e.name)
                            }
                        }
                        e = tar.nextEntry
                    }
                }
            }
        }
        return true
    }

    /** 防 zip-slip：目标必须落在 destDir 之内。 */
    private fun safeResolve(destDir: File, entryName: String): File? {
        val cleaned = entryName
            .replace('\\', '/')
            .trimStart('/')
            // 解码失败留下的替换字符 / 控制字符，别让它变成真实文件名
            .replace('\uFFFD', '_')
            .replace(Regex("[\\u0000-\\u001F]"), "")
        if (cleaned.isBlank()) return null
        if (cleaned.contains("..")) return null
        if (cleaned.startsWith("__MACOSX/")) return null
        val out = File(destDir, cleaned)
        return try {
            val destCanon = destDir.canonicalPath + File.separator
            val outCanon = out.canonicalPath
            if (!outCanon.startsWith(destCanon)) null else out
        } catch (_: Throwable) { null }
    }
}
