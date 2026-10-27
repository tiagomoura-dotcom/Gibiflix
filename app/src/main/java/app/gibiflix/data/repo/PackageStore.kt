package app.gibiflix.data.repo

import android.util.LruCache
import app.gibiflix.core.pkg.DocState
import app.gibiflix.core.pkg.DocStats
import app.gibiflix.core.pkg.GibiflixDoc
import app.gibiflix.core.pkg.GibiflixJson
import app.gibiflix.core.pkg.PackageIo
import app.gibiflix.core.pkg.ParsedDoc
import app.gibiflix.core.pkg.ReadResult
import app.gibiflix.data.db.ComicDao
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.data.files.LibraryFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Acesso ao gibiflix.json dos pacotes, com cache e gravação atômica. */
class PackageStore(private val files: LibraryFiles, private val comics: ComicDao) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cache = LruCache<String, ParsedDoc>(12)
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** Emite o id do quadrinho sempre que o pacote dele é regravado. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    fun lock(id: String): Mutex = locks.getOrPut(id) { Mutex() }

    suspend fun load(id: String): ParsedDoc? = withContext(Dispatchers.IO) {
        cache.get(id)?.let { return@withContext it }
        val f = files.packageFile(id)
        if (!f.exists()) return@withContext null
        val text = PackageIo.readJson(f) ?: return@withContext null
        when (val r = GibiflixJson.read(text)) {
            is ReadResult.Ok -> r.parsed.also { cache.put(id, it) }
            else -> null
        }
    }

    /**
     * Grava o documento no pacote. Sobe a revisão quando [bump] é verdadeiro e atualiza o índice no banco.
     */
    suspend fun save(id: String, doc: GibiflixDoc, bump: Boolean = true, keepPreparing: Boolean = false): GibiflixDoc =
        withContext(Dispatchers.IO) {
            lock(id).withLock {
                val current = load(id)
                val newDoc = doc.copy(
                    revision = if (bump) maxOf(doc.revision, current?.doc?.revision ?: 0) + 1 else doc.revision,
                    updatedAt = Instant.now().toString(),
                )
                val text = GibiflixJson.write(newDoc, current?.raw)
                PackageIo.replaceJson(files.packageFile(id), text)
                cache.remove(id)
                syncIndex(id, newDoc, keepPreparing)
                _changes.tryEmit(id)
                newDoc
            }
        }

    fun invalidate(id: String) {
        cache.remove(id)
        _changes.tryEmit(id)
    }

    suspend fun syncIndex(id: String, doc: GibiflixDoc, keepPreparing: Boolean = false) {
        val c = comics.get(id) ?: return
        val status = when (DocStats.state(doc)) {
            DocState.NEW -> if (keepPreparing) PrepStatus.PREPARING else PrepStatus.NEW
            DocState.REVIEW -> PrepStatus.REVIEW
            DocState.READY -> PrepStatus.READY
        }
        comics.upsert(
            c.copy(
                title = doc.title,
                readingDirection = doc.readingDirection,
                pageCount = doc.pages.size,
                preparedPages = doc.preparedPages,
                reviewedPages = doc.reviewedPages,
                revision = doc.revision,
                status = if (keepPreparing) PrepStatus.PREPARING else status,
                sizeBytes = files.packageFile(id).length(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
