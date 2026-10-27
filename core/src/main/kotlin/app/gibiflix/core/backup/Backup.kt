package app.gibiflix.core.backup

import app.gibiflix.core.sync.BookmarkRecord
import app.gibiflix.core.sync.ProgressRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CollectionRecord(val name: String, val createdAt: Long, val updatedAt: Long, val comicIds: List<String>)

@Serializable
data class SessionRecord(
    val id: String,
    val comicId: String,
    val startedAt: Long,
    val endedAt: Long,
    val pages: Int,
    val panels: Int,
    val device: String,
    val finishedComic: Boolean = false,
)

@Serializable
data class ServerRecord(val name: String, val url: String, val username: String? = null, val password: String? = null)

/** backup.json dentro do zip de backup (leve ou completo). */
@Serializable
data class BackupData(
    val format: String = "gibiflix-backup",
    val version: Int = 1,
    val createdAt: Long,
    val device: String,
    val full: Boolean = false,
    val progress: List<ProgressRecord> = emptyList(),
    val bookmarks: List<BookmarkRecord> = emptyList(),
    val collections: List<CollectionRecord> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    /** UserSettings em JSON (formato próprio, para não acoplar versões). */
    val settings: String? = null,
    val profileChoice: String? = null,
    val servers: List<ServerRecord> = emptyList(),
) {
    companion object {
        const val ENTRY = "backup.json"
        const val PACKAGES_DIR = "library/"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    }
}

object BackupMerge {
    /**
     * Coleções casadas pelo nome: une os quadrinhos; o nome/data mais recentes vencem.
     * Devolve as coleções resultantes que mudaram ou são novas.
     */
    fun mergeCollections(local: List<CollectionRecord>, incoming: List<CollectionRecord>): List<CollectionRecord> {
        val byName = local.associateBy { it.name.lowercase() }
        return incoming.mapNotNull { inc ->
            val l = byName[inc.name.lowercase()]
            if (l == null) inc
            else {
                val ids = (l.comicIds + inc.comicIds).distinct()
                if (ids.size == l.comicIds.size) null
                else l.copy(comicIds = ids, updatedAt = maxOf(l.updatedAt, inc.updatedAt))
            }
        }
    }
}
