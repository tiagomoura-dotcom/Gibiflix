package app.gibiflix.data.files

import android.content.Context
import java.io.File
import java.util.UUID

/** Onde ficam os pacotes, as capas e os arquivos temporários. */
class LibraryFiles(context: Context) {
    private val appContext = context.applicationContext
    val libraryDir: File = File(appContext.filesDir, "library").apply { mkdirs() }
    val coversDir: File = File(appContext.filesDir, "covers").apply { mkdirs() }
    val pageCacheDir: File get() = File(appContext.cacheDir, "pages").apply { mkdirs() }

    fun packageFile(id: String) = File(libraryDir, "$id.cbz")
    fun coverFile(id: String) = File(coversDir, "$id.webp")

    fun newWorkDir(prefix: String = "work"): File =
        File(appContext.cacheDir, "$prefix/${UUID.randomUUID()}").apply { mkdirs() }

    /** Apaga sobras de importações interrompidas e temporários de gravação. */
    fun cleanTemp() {
        File(appContext.cacheDir, "work").deleteRecursively()
        File(appContext.cacheDir, "import").deleteRecursively()
        libraryDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
    }
}
