package app.gibiflix.data.repo

import androidx.room.withTransaction
import app.gibiflix.data.db.CollectionEntity
import app.gibiflix.data.db.CollectionItemEntity
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.GibiflixDatabase
import app.gibiflix.data.files.LibraryFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Operações da biblioteca: editar, excluir, coleções, lido/não lido. */
class LibraryRepository(
    private val db: GibiflixDatabase,
    private val files: LibraryFiles,
    private val store: PackageStore,
) {
    val comics: Flow<List<ComicEntity>> = db.comics().observeAll()

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.collections().removeComic(id)
            db.progress().delete(id)
            db.comics().delete(id)
        }
        files.packageFile(id).delete()
        files.coverFile(id).delete()
        store.invalidate(id)
    }

    suspend fun setRead(id: String, read: Boolean) {
        db.comics().setRead(id, read)
        if (!read) db.progress().get(id)?.let { db.progress().upsert(it.copy(finished = false, updatedAt = System.currentTimeMillis())) }
    }

    /** Edita título, série, número e direção. Título e direção também vão para o pacote. */
    suspend fun editInfo(id: String, title: String, series: String?, number: String?, direction: String) {
        val c = db.comics().get(id) ?: return
        val numberSort = number?.replace(',', '.')?.let { Regex("""\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }
        db.comics().upsert(
            c.copy(
                title = title.trim().ifEmpty { c.title },
                series = series?.trim()?.ifEmpty { null },
                number = number?.trim()?.ifEmpty { null },
                numberSort = numberSort,
                readingDirection = direction,
                updatedAt = System.currentTimeMillis(),
            )
        )
        val parsed = store.load(id) ?: return
        if (parsed.doc.title != title.trim() || parsed.doc.readingDirection != direction) {
            store.save(id, parsed.doc.copy(title = title.trim().ifEmpty { c.title }, readingDirection = direction))
        }
    }

    suspend fun createCollection(name: String): Long =
        db.collections().byName(name.trim())?.id ?: db.collections().insert(CollectionEntity(name = name.trim()))

    suspend fun renameCollection(id: Long, name: String) = db.collections().rename(id, name.trim())

    suspend fun deleteCollection(id: Long) = db.withTransaction {
        db.collections().clear(id)
        db.collections().delete(id)
    }

    suspend fun setInCollection(collectionId: Long, comicId: String, inside: Boolean) {
        if (inside) db.collections().addItem(CollectionItemEntity(collectionId, comicId))
        else db.collections().removeItem(collectionId, comicId)
    }

    suspend fun collectionsOf(comicId: String): List<Long> = db.collections().collectionsOf(comicId)

    suspend fun allCollections(): List<CollectionEntity> = db.collections().all()
}
