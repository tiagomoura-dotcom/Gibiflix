package app.gibiflix.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gibiflix.container
import app.gibiflix.core.settings.LibrarySort
import app.gibiflix.data.db.CollectionRow
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.ProgressEntity
import app.gibiflix.data.db.SeriesRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

enum class LibTab { READING, ALL, SERIES, COLLECTIONS }

data class LibraryState(
    val loaded: Boolean = false,
    val all: List<ComicEntity> = emptyList(),
    val visible: List<ComicEntity> = emptyList(),
    val progress: Map<String, ProgressEntity> = emptyMap(),
    val continueReading: ComicEntity? = null,
    val series: List<SeriesRow> = emptyList(),
    val collections: List<CollectionRow> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENT_READ,
    val query: String = "",
    val columns: Int = 0,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val query = MutableStateFlow("")
    val tab = MutableStateFlow(LibTab.ALL)

    private val base = combine(
        c.library.comics,
        c.db.progress().observeAll(),
        c.settings.user,
        query,
        tab,
    ) { comics, progress, user, q, t ->
        val pmap = progress.associateBy { it.comicId }
        val sort = user.library.sort
        val filtered = if (q.isBlank()) comics else comics.filter { matches(it, q) }
        val list = when (t) {
            LibTab.READING -> filtered.filter { pmap[it.id]?.let { p -> !p.finished } == true && !it.read }
                .sortedByDescending { pmap[it.id]?.updatedAt ?: 0 }
            else -> sortComics(filtered, sort, pmap)
        }
        val cont = comics.filter { pmap[it.id]?.let { p -> !p.finished } == true && !it.read }
            .maxByOrNull { pmap[it.id]?.updatedAt ?: 0 }
        LibraryState(
            loaded = true, all = comics, visible = list, progress = pmap, continueReading = cont,
            sort = sort, query = q, columns = user.library.columns,
        )
    }

    val state: StateFlow<LibraryState> = combine(
        base,
        c.db.comics().observeSeries(),
        c.db.collections().observeRows(),
    ) { s, series, cols -> s.copy(series = series, collections = cols) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryState())

    fun setSort(sort: LibrarySort) = viewModelScope.launch {
        c.settings.updateUser { it.copy(library = it.library.copy(sort = sort)) }
    }

    fun setRead(id: String, read: Boolean) = viewModelScope.launch { c.library.setRead(id, read) }
    fun delete(id: String) = viewModelScope.launch { c.library.delete(id) }
    fun editInfo(id: String, title: String, series: String?, number: String?, direction: String) =
        viewModelScope.launch { c.library.editInfo(id, title, series, number, direction) }

    fun createCollection(name: String, addComic: String? = null) = viewModelScope.launch {
        val id = c.library.createCollection(name)
        if (addComic != null) c.library.setInCollection(id, addComic, true)
    }
    fun renameCollection(id: Long, name: String) = viewModelScope.launch { c.library.renameCollection(id, name) }
    fun deleteCollection(id: Long) = viewModelScope.launch { c.library.deleteCollection(id) }
    fun setInCollection(collectionId: Long, comicId: String, inside: Boolean) =
        viewModelScope.launch { c.library.setInCollection(collectionId, comicId, inside) }

    fun collectionComics(id: Long) = c.db.collections().observeComicIds(id)

    /** Busca por fala: página e quadro do primeiro balão que contém o texto. */
    suspend fun locate(comicId: String, query: String): Pair<Int, Int>? {
        val q = normalize(query.trim())
        if (q.isEmpty()) return null
        val doc = c.packages.load(comicId)?.doc ?: return null
        doc.pages.forEachIndexed { pi, page ->
            page.panels.forEachIndexed { qi, panel ->
                val texts = panel.text.orEmpty() + panel.translations.orEmpty().values.flatten()
                if (texts.any { normalize(it.value).contains(q) }) return pi to qi
            }
        }
        return null
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

        fun matches(c: ComicEntity, q: String): Boolean {
            val n = normalize(q.trim())
            return listOfNotNull(c.title, c.series, c.writer, c.penciller, c.number?.let { "#$it" }, c.balloonText)
                .any { normalize(it).contains(n) }
        }

        fun sortComics(list: List<ComicEntity>, sort: LibrarySort, progress: Map<String, ProgressEntity>): List<ComicEntity> =
            when (sort) {
                LibrarySort.RECENT_READ -> list.sortedWith(
                    compareByDescending<ComicEntity> { progress[it.id]?.updatedAt ?: it.lastReadAt ?: 0L }.thenByDescending { it.addedAt }
                )
                LibrarySort.RECENT_ADDED -> list.sortedByDescending { it.addedAt }
                LibrarySort.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { displayTitle(it) })
                LibrarySort.SERIES -> sortBySeries(list)
            }

        fun sortBySeries(list: List<ComicEntity>): List<ComicEntity> = list.sortedWith(
            compareBy<ComicEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.series ?: it.title }
                .thenBy { it.volume ?: 0 }
                .thenBy { it.numberSort ?: Double.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )

        /** "Série #12" quando há série e número; senão o título. */
        fun displayTitle(c: ComicEntity): String = when {
            c.series != null && c.number != null -> "${c.series} #${c.number}"
            c.series != null && c.volume != null && c.title == c.series -> "${c.series} v${c.volume}"
            else -> c.title
        }

        fun subtitle(c: ComicEntity): String? {
            val main = displayTitle(c)
            return c.title.takeIf { it != main && it != c.series }
        }
    }
}
