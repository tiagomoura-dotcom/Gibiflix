package app.gibiflix.reader

import app.gibiflix.data.files.LibraryFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Cache em disco das páginas extraídas dos pacotes (LRU por data de acesso, tamanho por perfil).
 */
class PageCache(private val files: LibraryFiles) {
    private val mutex = Mutex()
    @Volatile var limitBytes: Long = 200L * 1024 * 1024

    private fun dirFor(comicId: String, revisionKey: String) = File(files.pageCacheDir, "$comicId-$revisionKey")

    suspend fun page(comicId: String, revisionKey: String, name: String): File = withContext(Dispatchers.IO) {
        val dir = dirFor(comicId, revisionKey)
        val out = File(dir, name)
        if (out.exists() && out.length() > 0) {
            out.setLastModified(System.currentTimeMillis())
            return@withContext out
        }
        mutex.withLock {
            if (!out.exists()) {
                dir.mkdirs()
                val tmp = File(dir, "$name.part")
                ZipFile(files.packageFile(comicId)).use { z ->
                    val e = z.getEntry(name) ?: throw IllegalStateException("página ausente: $name")
                    z.getInputStream(e).use { input -> tmp.outputStream().use { input.copyTo(it, 128 * 1024) } }
                }
                tmp.renameTo(out)
                trim()
            }
        }
        out
    }

    /** Apaga as páginas menos usadas até caber no limite. */
    private fun trim() {
        val all = files.pageCacheDir.walkTopDown().filter { it.isFile }.toList()
        var total = all.sumOf { it.length() }
        if (total <= limitBytes) return
        for (f in all.sortedBy { it.lastModified() }) {
            if (total <= limitBytes * 0.85) break
            total -= f.length()
            f.delete()
        }
        files.pageCacheDir.listFiles()?.filter { it.isDirectory && it.list().isNullOrEmpty() }?.forEach { it.delete() }
    }

    fun clearComic(comicId: String) {
        files.pageCacheDir.listFiles()?.filter { it.name.startsWith("$comicId-") }?.forEach { it.deleteRecursively() }
    }
}
