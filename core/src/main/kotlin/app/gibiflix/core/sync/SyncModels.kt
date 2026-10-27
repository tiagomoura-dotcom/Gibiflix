package app.gibiflix.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeviceInfo(val name: String, val appVersion: String, val profile: String, val deviceId: String = "")

@Serializable
data class PairRequest(val code: String)

@Serializable
data class PairResponse(val token: String)

@Serializable
data class ComicSummary(
    val id: String,
    val sourceHash: String,
    val title: String,
    val series: String? = null,
    val number: String? = null,
    val sizeBytes: Long,
    val pages: Int,
    val preparedPages: Int,
    val reviewedPages: Int,
    val revision: Int,
    val updatedAt: Long,
)

@Serializable
data class ProgressRecord(
    val comicId: String,
    val page: Int,
    val panel: Int,
    val mode: String,
    val updatedAt: Long,
    val deviceId: String,
    val finished: Boolean = false,
    val pageCount: Int = 0,
    val panelCount: Int = 0,
)

@Serializable
data class BookmarkRecord(
    val id: String,
    val comicId: String,
    val page: Int,
    val panel: Int,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)

/** Conteúdo do gibiflix-sync.json (exportar/importar por pasta). */
@Serializable
data class SyncFile(
    val format: String = "gibiflix-sync",
    val version: Int = 1,
    val device: String,
    val exportedAt: Long,
    val progress: List<ProgressRecord> = emptyList(),
    val bookmarks: List<BookmarkRecord> = emptyList(),
)

object SyncJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
}

enum class RemoteState { NEW, UPDATED, HAVE }

object SyncRules {
    /** Fusão de progresso: por quadrinho, vence o `updatedAt` mais recente. Devolve só o que muda localmente. */
    fun mergeProgress(local: List<ProgressRecord>, remote: List<ProgressRecord>): List<ProgressRecord> {
        val byId = local.associateBy { it.comicId }
        return remote
            .groupBy { it.comicId }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAt } }
            .filter { r -> val l = byId[r.comicId]; l == null || r.updatedAt > l.updatedAt }
    }

    /** Marcadores: por id, vence o `updatedAt` mais recente (exclusões viajam com deleted = true). */
    fun mergeBookmarks(local: List<BookmarkRecord>, remote: List<BookmarkRecord>): List<BookmarkRecord> {
        val byId = local.associateBy { it.id }
        return remote
            .groupBy { it.id }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAt } }
            .filter { r -> val l = byId[r.id]; l == null || r.updatedAt > l.updatedAt }
    }

    /**
     * Novos (não tenho), Atualizados (revisão remota maior que a local) e Já tenho.
     * Um quadrinho com o mesmo arquivo original (sourceHash) conta como "Já tenho".
     */
    fun classify(remote: ComicSummary, localRevisions: Map<String, Int>, localHashes: Set<String>): RemoteState {
        val localRev = localRevisions[remote.id]
        return when {
            localRev == null && remote.sourceHash !in localHashes -> RemoteState.NEW
            localRev != null && remote.revision > localRev -> RemoteState.UPDATED
            else -> RemoteState.HAVE
        }
    }
}
