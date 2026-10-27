package app.gibiflix.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.gibiflix.AppContainer
import app.gibiflix.core.backup.BackupData
import app.gibiflix.core.backup.BackupMerge
import app.gibiflix.core.backup.CollectionRecord
import app.gibiflix.core.backup.ServerRecord
import app.gibiflix.core.backup.SessionRecord
import app.gibiflix.core.device.ProfileChoice
import app.gibiflix.core.settings.UserSettings
import app.gibiflix.data.db.CollectionEntity
import app.gibiflix.data.db.CollectionItemEntity
import app.gibiflix.data.db.ReadingSessionEntity
import app.gibiflix.data.db.ServerEntity
import app.gibiflix.importer.ImportOutcome
import app.gibiflix.opds.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreResult(val progress: Int, val bookmarks: Int, val collections: Int, val sessions: Int, val comics: Int)

/** Backup leve (dados pessoais) e completo (+ pacotes). Restaurar funde com o que existe. */
class BackupManager(private val context: Context, private val c: AppContainer) {

    suspend fun create(target: Uri, full: Boolean, includePasswords: Boolean, onProgress: (Int, Int) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            val db = c.db
            val collections = db.collections().all()
            val items = db.collections().allItems().groupBy { it.collectionId }
            val data = BackupData(
                createdAt = System.currentTimeMillis(),
                device = c.settings.deviceName(),
                full = full,
                progress = c.sync.progress(),
                bookmarks = c.sync.bookmarks(),
                collections = collections.map { col ->
                    CollectionRecord(col.name, col.createdAt, col.updatedAt, items[col.id].orEmpty().map { it.comicId })
                },
                sessions = db.sessions().all().map {
                    SessionRecord(it.id, it.comicId, it.startedAt, it.endedAt, it.pages, it.panels, it.device, it.finishedComic)
                },
                settings = UserSettings.encode(c.settings.current().user),
                profileChoice = c.settings.current().profileChoice.name,
                servers = db.servers().all().map { s ->
                    ServerRecord(s.name, s.url, s.username, if (includePasswords) s.passwordEnc?.let { SecretBox.decrypt(it) } else null)
                },
            )
            val out = context.contentResolver.openOutputStream(target) ?: throw IllegalStateException("não foi possível gravar")
            ZipOutputStream(out.buffered(256 * 1024)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupData.ENTRY))
                zip.write(BackupData.json.encodeToString(BackupData.serializer(), data).toByteArray())
                zip.closeEntry()
                if (full) {
                    val comics = db.comics().all()
                    comics.forEachIndexed { i, comic ->
                        onProgress(i, comics.size)
                        val f = c.files.packageFile(comic.id)
                        if (!f.exists()) return@forEachIndexed
                        zip.putNextEntry(ZipEntry(BackupData.PACKAGES_DIR + comic.id + ".cbz").apply { method = ZipEntry.DEFLATED })
                        f.inputStream().use { it.copyTo(zip, 256 * 1024) }
                        zip.closeEntry()
                    }
                    onProgress(comics.size, comics.size)
                }
            }
            c.settings.updateUser { it.copy(lastBackupAt = System.currentTimeMillis()) }
        }

    suspend fun restore(source: Uri, onProgress: (String) -> Unit = {}): RestoreResult = withContext(Dispatchers.IO) {
        var data: BackupData? = null
        var comics = 0
        val work = c.files.newWorkDir("restore")
        try {
            // Primeiro os pacotes (backup completo), para o progresso e as coleções acharem os quadrinhos.
            val input = context.contentResolver.openInputStream(source) ?: throw IllegalStateException("não abriu")
            ZipInputStream(input.buffered(256 * 1024)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == BackupData.ENTRY ->
                            data = BackupData.json.decodeFromString(BackupData.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                        e.name.startsWith(BackupData.PACKAGES_DIR) && e.name.endsWith(".cbz") -> {
                            onProgress(e.name.removePrefix(BackupData.PACKAGES_DIR))
                            val tmp = File(work, "p.cbz")
                            tmp.outputStream().use { zip.copyTo(it, 256 * 1024) }
                            if (c.importer.importFile(tmp, e.name.substringAfterLast('/')) is ImportOutcome.Imported) comics++
                            tmp.delete()
                        }
                    }
                }
            }
        } finally {
            work.deleteRecursively()
        }
        val b = data ?: throw IllegalStateException("arquivo de backup inválido")
        val p = c.sync.mergeProgress(b.progress)
        val m = c.sync.mergeBookmarks(b.bookmarks)
        val known = c.db.comics().all().map { it.id }.toSet()
        val cols = restoreCollections(b.collections, known)
        val sessions = b.sessions.filter { it.comicId in known }.map {
            ReadingSessionEntity(it.id, it.comicId, it.startedAt, it.endedAt, it.pages, it.panels, it.device, it.finishedComic)
        }
        c.db.sessions().insertAll(sessions)
        b.settings?.let { s -> c.settings.updateUser { UserSettings.decode(s) } }
        b.profileChoice?.let { pc -> runCatching { ProfileChoice.valueOf(pc) }.getOrNull()?.let { c.settings.setProfileChoice(it) } }
        val existing = c.db.servers().all().map { it.url }.toSet()
        b.servers.filter { it.url !in existing }.forEach { s ->
            c.db.servers().upsert(ServerEntity(name = s.name, url = s.url, username = s.username, passwordEnc = s.password?.let { SecretBox.encrypt(it) }))
        }
        RestoreResult(p, m, cols, sessions.size, comics)
    }

    private suspend fun restoreCollections(incoming: List<CollectionRecord>, known: Set<String>): Int {
        val db = c.db
        val local = db.collections().all()
        val items = db.collections().allItems().groupBy { it.collectionId }
        val localRecords = local.map { CollectionRecord(it.name, it.createdAt, it.updatedAt, items[it.id].orEmpty().map { i -> i.comicId }) }
        val merged = BackupMerge.mergeCollections(localRecords, incoming.map { it.copy(comicIds = it.comicIds.filter { id -> id in known }) })
        db.withTransaction {
            for (rec in merged) {
                val existing = local.firstOrNull { it.name.equals(rec.name, ignoreCase = true) }
                val id = existing?.id ?: db.collections().insert(CollectionEntity(name = rec.name, createdAt = rec.createdAt, updatedAt = rec.updatedAt))
                rec.comicIds.forEach { db.collections().addItem(CollectionItemEntity(id, it)) }
            }
        }
        return merged.size
    }
}
