package app.gibiflix.core.pkg

import app.gibiflix.core.util.ArchiveNames
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Leitura e gravação do pacote .cbz do Gibiflix (sempre atômica). */
object PackageIo {

    /** Uma página a gravar: nome no pacote e arquivo de origem. */
    data class PageFile(val name: String, val file: File)

    /**
     * Grava o pacote: páginas STORED, JSON e ComicInfo com DEFLATE.
     * Escreve num temporário na mesma pasta e renomeia por cima do destino no fim.
     */
    fun write(target: File, pages: List<PageFile>, json: String, comicInfo: ByteArray? = null) {
        atomicWrite(target) { out ->
            ZipOutputStream(out).use { zip ->
                for (p in pages) putStored(zip, p.name, p.file)
                if (comicInfo != null) putDeflated(zip, GibiflixDoc.COMIC_INFO, comicInfo)
                putDeflated(zip, GibiflixDoc.JSON_NAME, json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    /** Troca só o gibiflix.json, copiando o resto sem recomprimir. */
    fun replaceJson(target: File, json: String) {
        ZipFile(target).use { src ->
            atomicWrite(target) { out ->
                ZipOutputStream(out).use { zip ->
                    for (e in src.entries()) {
                        if (e.isDirectory || e.name == GibiflixDoc.JSON_NAME) continue
                        if (e.method == ZipEntry.STORED) {
                            val ne = ZipEntry(e.name).apply {
                                method = ZipEntry.STORED
                                size = e.size
                                compressedSize = e.size
                                crc = e.crc
                            }
                            zip.putNextEntry(ne)
                        } else {
                            zip.putNextEntry(ZipEntry(e.name).apply { method = ZipEntry.DEFLATED })
                        }
                        src.getInputStream(e).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    putDeflated(zip, GibiflixDoc.JSON_NAME, json.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    fun readJson(zipFile: File): String? = ZipFile(zipFile).use { z ->
        z.getEntry(GibiflixDoc.JSON_NAME)?.let { e -> z.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) } }
    }

    fun readComicInfo(zipFile: File): ByteArray? = ZipFile(zipFile).use { z ->
        z.entries().asSequence().firstOrNull { !it.isDirectory && ArchiveNames.isComicInfo(it.name) }
            ?.let { e -> z.getInputStream(e).use { it.readBytes() } }
    }

    /** Páginas de um CBZ qualquer, na ordem natural. */
    fun listPages(zipFile: File): List<String> = ZipFile(zipFile).use { z ->
        ArchiveNames.sortPages(z.entries().asSequence().filter { !it.isDirectory && ArchiveNames.isPage(it.name) }.map { it.name }.toList())
    }

    fun readEntry(zipFile: File, name: String): ByteArray = ZipFile(zipFile).use { z ->
        val e = z.getEntry(name) ?: throw IllegalArgumentException("entrada ausente: $name")
        z.getInputStream(e).use { it.readBytes() }
    }

    fun atomicWrite(target: File, block: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            tmp.outputStream().buffered(256 * 1024).use { out ->
                block(out)
                out.flush()
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw java.io.IOException("não foi possível renomear ${tmp.name}")
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun putStored(zip: ZipOutputStream, name: String, file: File) {
        val crc = CRC32()
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; crc.update(buf, 0, n) }
        }
        val e = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = file.length()
            compressedSize = file.length()
            this.crc = crc.value
        }
        zip.putNextEntry(e)
        file.inputStream().use { it.copyTo(zip, 64 * 1024) }
        zip.closeEntry()
    }

    private fun putDeflated(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
        zip.write(bytes)
        zip.closeEntry()
    }
}

object Hashing {
    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(128 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        return "sha256:" + md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }
}
