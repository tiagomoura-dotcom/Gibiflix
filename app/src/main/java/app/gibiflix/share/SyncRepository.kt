package app.gibiflix.share

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import app.gibiflix.core.sync.BookmarkRecord
import app.gibiflix.core.sync.ComicSummary
import app.gibiflix.core.sync.ProgressRecord
import app.gibiflix.core.sync.SyncFile
import app.gibiflix.core.sync.SyncJson
import app.gibiflix.core.sync.SyncRules
import app.gibiflix.data.db.BookmarkEntity
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.GibiflixDatabase
import app.gibiflix.data.db.ProgressEntity
import app.gibiflix.data.files.LibraryFiles
import app.gibiflix.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Progresso, marcadores e lista de quadrinhos no formato da troca entre aparelhos. */
class SyncRepository(
    private val context: Context,
    private val db: GibiflixDatabase,
    private val files: LibraryFiles,
    private val settings: SettingsRepository,
) {
    suspend fun summaries(): List<ComicSummary> = db.comics().all().map { it.toSummary() }

    private fun ComicEntity.toSummary() = ComicSummary(
        id = id, sourceHash = sourceHash, title = title, series = series, number = number,
        sizeBytes = files.packageFile(id).length(), pages = pageCount, preparedPages = preparedPages,
        reviewedPages = reviewedPages, revision = revision, updatedAt = updatedAt,
    )

    suspend fun progress(): List<ProgressRecord> = db.progress().all().map {
        ProgressRecord(it.comicId, it.page, it.panel, it.mode, it.updatedAt, it.deviceId, it.finished, it.pageCount, it.panelCount)
    }

    suspend fun bookmarks(): List<BookmarkRecord> = db.bookmarks().allIncludingDeleted().map {
        BookmarkRecord(it.id, it.comicId, it.page, it.panel, it.note, it.createdAt, it.updatedAt, it.deleted, it.deviceId)
    }

    /** Funde registros vindos de outro aparelho. Devolve quantos mudaram aqui. */
    suspend fun mergeProgress(remote: List<ProgressRecord>): Int {
        val changes = SyncRules.mergeProgress(progress(), remote)
        val known = db.comics().all().map { it.id }.toSet()
        val apply = changes.filter { it.comicId in known }
        db.withTransaction {
            db.progress().upsertAll(apply.map {
                ProgressEntity(it.comicId, it.page, it.panel, it.mode, it.updatedAt, it.deviceId, it.finished, it.pageCount, it.panelCount)
            })
            apply.filter { it.finished }.forEach { db.comics().setRead(it.comicId, true) }
        }
        return apply.size
    }

    suspend fun mergeBookmarks(remote: List<BookmarkRecord>): Int {
        val changes = SyncRules.mergeBookmarks(bookmarks(), remote)
        db.bookmarks().upsertAll(changes.map {
            BookmarkEntity(it.id, it.comicId, it.page, it.panel, it.note, it.createdAt, it.updatedAt, it.deleted, it.deviceId)
        })
        return changes.size
    }

    suspend fun syncFile(): SyncFile = SyncFile(
        device = settings.deviceName(),
        exportedAt = System.currentTimeMillis(),
        progress = progress(),
        bookmarks = bookmarks(),
    )

    /** Exportar para pasta: grava os pacotes escolhidos e o gibiflix-sync.json. */
    suspend fun exportToFolder(tree: Uri, comicIds: List<String>, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: throw IllegalStateException("pasta inválida")
        var count = 0
        comicIds.forEachIndexed { i, id ->
            onProgress(i, comicIds.size)
            val comic = db.comics().get(id) ?: return@forEachIndexed
            val name = safeName(comic) + ".cbz"
            dir.findFile(name)?.delete()
            val out = dir.createFile("application/vnd.comicbook+zip", name) ?: return@forEachIndexed
            context.contentResolver.openOutputStream(out.uri)?.use { o -> files.packageFile(id).inputStream().use { it.copyTo(o) } }
            count++
        }
        dir.findFile(SYNC_FILE)?.delete()
        dir.createFile("application/json", SYNC_FILE)?.let { f ->
            context.contentResolver.openOutputStream(f.uri)?.use { o ->
                o.write(SyncJson.json.encodeToString(SyncFile.serializer(), syncFile()).toByteArray())
            }
        }
        onProgress(comicIds.size, comicIds.size)
        count
    }

    /** Lê o gibiflix-sync.json de uma pasta e funde. Os pacotes entram pela importação de pasta. */
    suspend fun importSyncFromFolder(tree: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return@withContext null
        val f = dir.findFile(SYNC_FILE) ?: return@withContext null
        val text = context.contentResolver.openInputStream(f.uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@withContext null
        val sync = runCatching { SyncJson.json.decodeFromString(SyncFile.serializer(), text) }.getOrNull() ?: return@withContext null
        mergeProgress(sync.progress) to mergeBookmarks(sync.bookmarks)
    }

    companion object {
        const val SYNC_FILE = "gibiflix-sync.json"

        fun safeName(c: ComicEntity): String {
            val base = when {
                c.series != null && c.number != null -> "${c.series} #${c.number}"
                else -> c.title
            }
            return base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        }
    }
}
