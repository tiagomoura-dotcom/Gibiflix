package app.gibiflix.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Estados de preparação mostrados na biblioteca. "Lido" é guardado à parte, em [ComicEntity.read]. */
object PrepStatus {
    const val NEW = "NEW"
    const val PREPARING = "PREPARING"
    const val REVIEW = "REVIEW"
    const val READY = "READY"
}

@Entity(tableName = "comics", indices = [Index(value = ["sourceHash"], unique = true), Index("series")])
data class ComicEntity(
    @PrimaryKey val id: String,
    val sourceHash: String,
    val title: String,
    val series: String? = null,
    val number: String? = null,
    val numberSort: Double? = null,
    val volume: Int? = null,
    val year: Int? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val summary: String? = null,
    val language: String? = null,
    val readingDirection: String = "ltr",
    val pageCount: Int = 0,
    val preparedPages: Int = 0,
    val reviewedPages: Int = 0,
    val revision: Int = 0,
    val status: String = PrepStatus.NEW,
    val prepareProgress: Int = 0,
    val read: Boolean = false,
    val opened: Boolean = false,
    val sizeBytes: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    /** Texto dos balões (OCR), para a busca. */
    val balloonText: String? = null,
    /** O gibiflix.json tem versão mais nova que a suportada: tratar como CBZ comum. */
    val incompatible: Boolean = false,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "comicId"],
    indices = [Index("comicId")],
)
data class CollectionItemEntity(
    val collectionId: Long,
    val comicId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val comicId: String,
    val page: Int,
    val panel: Int,
    /** "panel" ou "page". */
    val mode: String,
    val updatedAt: Long,
    val deviceId: String,
    val finished: Boolean = false,
    val pageCount: Int = 0,
    /** Quadros da página atual (para "quadro A de B"). */
    val panelCount: Int = 0,
)

@Entity(tableName = "bookmarks", indices = [Index("comicId")])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val comicId: String,
    val page: Int,
    val panel: Int,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

@Entity(tableName = "reading_sessions", indices = [Index("comicId"), Index("startedAt")])
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val comicId: String,
    val startedAt: Long,
    val endedAt: Long,
    val pages: Int,
    val panels: Int,
    val device: String,
    val finishedComic: Boolean = false,
)

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val username: String? = null,
    /** Senha cifrada com a chave do Android Keystore (Base64). */
    val passwordEnc: String? = null,
)
