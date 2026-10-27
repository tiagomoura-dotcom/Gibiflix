package app.gibiflix.importer

import android.os.ParcelFileDescriptor
import app.gibiflix.core.util.ArchiveNames
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.File
import java.util.zip.ZipFile

enum class SourceKind { ZIP, RAR, SEVEN_ZIP, PDF, UNKNOWN }

object SourceSniffer {
    fun sniff(file: File, name: String): SourceKind {
        val head = ByteArray(8)
        val n = file.inputStream().use { it.read(head) }
        if (n >= 4) {
            if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return SourceKind.ZIP
            if (head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() && head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte()) return SourceKind.RAR
            if (head[0] == '7'.code.toByte() && head[1] == 'z'.code.toByte() && (head[2].toInt() and 0xFF) == 0xBC && (head[3].toInt() and 0xFF) == 0xAF) return SourceKind.SEVEN_ZIP
            if (head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()) return SourceKind.PDF
        }
        return when (ArchiveNames.extension(name)) {
            "cbz", "zip" -> SourceKind.ZIP
            "cbr", "rar" -> SourceKind.RAR
            "cb7", "7z" -> SourceKind.SEVEN_ZIP
            "pdf" -> SourceKind.PDF
            else -> SourceKind.UNKNOWN
        }
    }

    val SUPPORTED_EXTENSIONS = setOf("cbz", "zip", "cbr", "rar", "cb7", "7z", "pdf")
}

/** Resultado da extração: páginas (na ordem natural do nome original) e ComicInfo.xml, se houver. */
class Extracted(val pages: List<File>, val comicInfo: ByteArray?)

object ArchiveExtractor {

    fun extractZip(source: File, outDir: File, onProgress: (Float) -> Unit): Extracted {
        ZipFile(source).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
            val comicInfo = entries.firstOrNull { ArchiveNames.isComicInfo(it.name) }
                ?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
            val names = ArchiveNames.sortPages(entries.filter { ArchiveNames.isPage(it.name) }.map { it.name })
            val files = names.mapIndexed { i, name ->
                val out = File(outDir, "%05d.%s".format(i, ArchiveNames.extension(name)))
                zip.getInputStream(zip.getEntry(name)).use { input -> out.outputStream().use { input.copyTo(it) } }
                onProgress((i + 1f) / names.size)
                out
            }
            return Extracted(files, comicInfo)
        }
    }

    /** CBR (inclusive RAR5) e CB7 via libarchive. */
    fun extractWithLibarchive(source: File, outDir: File, onProgress: (Float) -> Unit): Extracted {
        val raw = File(outDir, "raw").apply { mkdirs() }
        val found = mutableListOf<Pair<String, File>>()
        var comicInfo: ByteArray? = null
        val archive = Archive.readNew()
        try {
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readOpenFileName(archive, source.absolutePath.toByteArray(), 16 * 1024)
            var index = 0
            while (true) {
                val entry = Archive.readNextHeader(archive)
                if (entry == 0L) break
                val path = ArchiveEntry.pathnameUtf8(entry)
                    ?: ArchiveEntry.pathname(entry)?.let { String(it, Charsets.ISO_8859_1) }
                    ?: ""
                val isFile = ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFREG
                when {
                    isFile && ArchiveNames.isPage(path) -> {
                        val out = File(raw, "%05d.%s".format(index++, ArchiveNames.extension(path)))
                        writeEntry(archive, out)
                        found += path to out
                    }
                    isFile && ArchiveNames.isComicInfo(path) -> {
                        val out = File(raw, "comicinfo.xml")
                        writeEntry(archive, out)
                        comicInfo = out.readBytes()
                    }
                    else -> Archive.readDataSkip(archive)
                }
                onProgress((index % 50) / 50f)
            }
        } finally {
            runCatching { Archive.readFree(archive) }
        }
        val sorted = found.sortedWith { a, b -> app.gibiflix.core.util.NaturalOrder.compare(a.first.replace('\\', '/'), b.first.replace('\\', '/')) }
        return Extracted(sorted.map { it.second }, comicInfo)
    }

    private fun writeEntry(archive: Long, out: File) {
        ParcelFileDescriptor.open(
            out,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        ).use { pfd -> Archive.readDataIntoFd(archive, pfd.fd) }
    }
}
