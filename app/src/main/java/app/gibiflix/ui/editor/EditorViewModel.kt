package app.gibiflix.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gibiflix.container
import app.gibiflix.core.edit.PanelOps
import app.gibiflix.core.edit.UndoStack
import app.gibiflix.core.pkg.DocStats
import app.gibiflix.core.pkg.GibiflixDoc
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.core.pkg.Panel
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.prepare.PageAnalysis
import app.gibiflix.reader.PageCache
import app.gibiflix.reader.PageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

enum class Tool { SELECT, DRAW, JOIN, SPLIT }

data class EditorUi(
    val loading: Boolean = true,
    val comic: ComicEntity? = null,
    val doc: GibiflixDoc? = null,
    val page: Int = 0,
    val selected: Set<Int> = emptySet(),
    val tool: Tool = Tool.SELECT,
    val onlyReview: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val image: Bitmap? = null,
    val tolerance: Int = 40,
    val redetecting: Boolean = false,
    val saving: Boolean = false,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    private var comicId = ""
    private val undo = UndoStack<List<PageInfo>>(20)
    private val pageCache = PageCache(c.files)
    private val renderer = PageRenderer()
    private var saveJob: Job? = null
    private var imageJob: Job? = null
    private var dirty = false
    private var savingNow = false
    private val thumbs = HashMap<Int, Bitmap>()

    fun open(id: String) {
        if (id == comicId) return
        comicId = id
        viewModelScope.launch {
            val tol = c.settings.current().user.prepare.tolerance
            _ui.update { it.copy(tolerance = tol) }
            reload(keepPage = false)
        }
        // A preparação em segundo plano grava o pacote: recarrega se não houver edição pendente.
        viewModelScope.launch {
            c.packages.changes.collect { changed ->
                if (changed != comicId) return@collect
                delay(300)
                if (dirty || savingNow) return@collect
                val rev = c.packages.load(comicId)?.doc?.revision
                if (rev != _ui.value.doc?.revision) reload(keepPage = true)
            }
        }
        viewModelScope.launch {
            c.db.comics().observe(id).collect { cm -> _ui.update { it.copy(comic = cm) } }
        }
    }

    private suspend fun reload(keepPage: Boolean) {
        val parsed = c.packages.load(comicId) ?: run { _ui.update { it.copy(loading = false) }; return }
        val page = if (keepPage) _ui.value.page.coerceIn(0, parsed.doc.pages.lastIndex.coerceAtLeast(0)) else firstToReview(parsed.doc)
        thumbs.clear()
        _ui.update { it.copy(loading = false, doc = parsed.doc, page = page, selected = emptySet()) }
        loadImage(page)
    }

    private fun firstToReview(doc: GibiflixDoc): Int =
        doc.pages.indexOfFirst { DocStats.needsReview(it) }.takeIf { it >= 0 } ?: 0

    private fun pageKey(doc: GibiflixDoc) = "${doc.id.take(8)}-${doc.sourceHash.takeLast(8)}"

    private fun loadImage(index: Int) {
        val doc = _ui.value.doc ?: return
        if (index !in doc.pages.indices) return
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val f = pageCache.page(comicId, pageKey(doc), doc.pages[index].file)
                    renderer.renderWhole(f, 1800, 1800)
                }.getOrNull()
            }
            _ui.update { it.copy(image = bmp) }
        }
    }

    suspend fun thumbnail(index: Int): Bitmap? {
        thumbs[index]?.let { return it }
        val doc = _ui.value.doc ?: return null
        if (index !in doc.pages.indices) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val f = pageCache.page(comicId, pageKey(doc), doc.pages[index].file)
                renderer.renderWhole(f, 160, 240)
            }.getOrNull()
        }?.also { if (thumbs.size > 80) thumbs.clear(); thumbs[index] = it }
    }

    fun goTo(index: Int) {
        val doc = _ui.value.doc ?: return
        if (index !in doc.pages.indices) return
        _ui.update { it.copy(page = index, selected = emptySet(), tool = Tool.SELECT) }
        loadImage(index)
    }

    fun setTool(t: Tool) = _ui.update { it.copy(tool = if (it.tool == t) Tool.SELECT else t, selected = if (t == Tool.JOIN) it.selected else it.selected.take(1).toSet()) }
    fun setOnlyReview(v: Boolean) = _ui.update { it.copy(onlyReview = v) }
    fun setTolerance(v: Int) = _ui.update { it.copy(tolerance = v.coerceIn(10, 90)) }

    fun tap(x: Double, y: Double) {
        val s = _ui.value
        val panels = currentPage()?.panels ?: return
        val hit = PanelOps.hit(panels, x, y)
        _ui.update {
            when {
                s.tool == Tool.JOIN && hit >= 0 -> it.copy(selected = if (hit in s.selected) s.selected - hit else s.selected + hit)
                hit >= 0 -> it.copy(selected = setOf(hit))
                else -> it.copy(selected = emptySet())
            }
        }
    }

    fun select(index: Int) = _ui.update { it.copy(selected = setOf(index)) }

    private fun currentPage(): PageInfo? = _ui.value.doc?.pages?.getOrNull(_ui.value.page)

    /** Aplica uma mudança nos quadros da página atual, com desfazer e salvamento automático. */
    private fun edit(selectAfter: Set<Int>? = null, record: Boolean = true, f: (PageInfo) -> PageInfo) {
        val s = _ui.value
        val doc = s.doc ?: return
        val page = doc.pages.getOrNull(s.page) ?: return
        val changed = f(page)
        if (changed == page) return
        if (record) undo.push(doc.pages)
        val pages = doc.pages.toMutableList().also { it[s.page] = changed }
        _ui.update {
            it.copy(
                doc = doc.copy(pages = pages),
                selected = selectAfter ?: it.selected.filter { i -> i in changed.panels.indices }.toSet(),
                canUndo = undo.canUndo, canRedo = undo.canRedo,
            )
        }
        scheduleSave()
    }

    /** Começo de um arrasto (mover/redimensionar): guarda o estado para desfazer uma vez só. */
    fun beginDrag() {
        _ui.value.doc?.let { undo.push(it.pages) }
        _ui.update { it.copy(canUndo = undo.canUndo, canRedo = undo.canRedo) }
    }

    fun movePanel(index: Int, dx: Double, dy: Double) = edit(record = false) { p -> p.copy(panels = PanelOps.move(p.panels, index, dx, dy)) }

    fun resizePanel(index: Int, corner: PanelOps.Corner, x: Double, y: Double) =
        edit(record = false) { p -> p.copy(panels = PanelOps.resize(p.panels, index, corner, x, y)) }

    fun draw(r: NRect) = edit { p ->
        val out = PanelOps.add(p.panels, r)
        p.copy(panels = out)
    }.also { val n = currentPage()?.panels?.size ?: 0; if (n > 0) _ui.update { it.copy(selected = setOf(n - 1), tool = Tool.SELECT) } }

    fun join() {
        val sel = _ui.value.selected
        if (sel.size < 2) return
        val first = sel.min()
        edit(selectAfter = setOf(first)) { p -> p.copy(panels = PanelOps.merge(p.panels, sel)) }
        _ui.update { it.copy(tool = Tool.SELECT) }
    }

    fun split(ax: Double, ay: Double, bx: Double, by: Double) {
        val s = _ui.value
        val idx = s.selected.singleOrNull() ?: PanelOps.hit(currentPage()?.panels.orEmpty(), (ax + bx) / 2, (ay + by) / 2)
        if (idx < 0) return
        val dir = currentPage()?.direction(s.doc?.readingDirection ?: "ltr") ?: "ltr"
        edit(selectAfter = emptySet()) { p -> p.copy(panels = PanelOps.split(p.panels, idx, ax, ay, bx, by, dir)) }
        _ui.update { it.copy(tool = Tool.SELECT) }
    }

    fun deleteSelected() {
        val sel = _ui.value.selected
        if (sel.isEmpty()) return
        edit(selectAfter = emptySet()) { p -> p.copy(panels = PanelOps.delete(p.panels, sel)) }
    }

    fun reorder(from: Int, to: Int) = edit(selectAfter = setOf(to)) { p -> p.copy(panels = PanelOps.reorder(p.panels, from, to)) }

    fun setBookDirection(dir: String) {
        val doc = _ui.value.doc ?: return
        if (doc.readingDirection == dir) return
        undo.push(doc.pages)
        _ui.update { it.copy(doc = doc.copy(readingDirection = dir)) }
        scheduleSave()
    }

    /** Exceção de direção só para esta página (null = herda do livro). */
    fun setPageDirection(dir: String?) = edit { p -> p.copy(readingDirection = dir) }

    fun undo() {
        val doc = _ui.value.doc ?: return
        val prev = undo.undo(doc.pages) ?: return
        _ui.update { it.copy(doc = doc.copy(pages = prev), selected = emptySet(), canUndo = undo.canUndo, canRedo = undo.canRedo) }
        scheduleSave()
    }

    fun redo() {
        val doc = _ui.value.doc ?: return
        val next = undo.redo(doc.pages) ?: return
        _ui.update { it.copy(doc = doc.copy(pages = next), selected = emptySet(), canUndo = undo.canUndo, canRedo = undo.canRedo) }
        scheduleSave()
    }

    /** Aprova a página (reviewed = true) e vai para a próxima (a próxima a revisar, se o filtro estiver ligado). */
    fun approveAndNext() {
        val s = _ui.value
        edit(selectAfter = emptySet()) { p -> p.copy(reviewed = true) }
        val doc = _ui.value.doc ?: return
        val next = if (s.onlyReview) {
            (s.page + 1 until doc.pages.size).firstOrNull { DocStats.needsReview(doc.pages[it]) }
                ?: (0 until s.page).firstOrNull { DocStats.needsReview(doc.pages[it]) }
        } else (s.page + 1).takeIf { it < doc.pages.size }
        if (next != null) goTo(next)
    }

    /** Redetecta só esta página com a tolerância escolhida (as edições dela são trocadas). */
    fun redetect() {
        val s = _ui.value
        val doc = s.doc ?: return
        val page = doc.pages.getOrNull(s.page) ?: return
        _ui.update { it.copy(redetecting = true) }
        viewModelScope.launch {
            val analyzed = withContext(Dispatchers.IO) {
                runCatching {
                    ZipFile(c.files.packageFile(comicId)).use { z -> PageAnalysis.analyze(z, page, doc.readingDirection, s.tolerance) }
                }.getOrNull()
            }
            _ui.update { it.copy(redetecting = false) }
            if (analyzed != null) edit(selectAfter = emptySet()) { analyzed.copy(readingDirection = page.readingDirection) }
        }
    }

    /** Prepara o livro inteiro em segundo plano (mantém as páginas revisadas, salvo "Refazer tudo"). */
    fun prepareAll(redoAll: Boolean) = viewModelScope.launch {
        flush()
        c.prepareScheduler.prepare(comicId, redoAll)
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            flush()
        }
    }

    /** Grava já o que estiver pendente (sobe a revisão). */
    suspend fun flush() {
        if (!dirty) return
        val doc = _ui.value.doc ?: return
        _ui.update { it.copy(saving = true) }
        dirty = false
        savingNow = true
        try {
            val saved = c.packages.save(comicId, doc)
            _ui.update { s -> s.copy(saving = false, doc = s.doc?.copy(revision = saved.revision, updatedAt = saved.updatedAt)) }
        } finally {
            savingNow = false
        }
    }

    fun flushAsync() { c.appScope.launch { flush() } }

    override fun onCleared() {
        if (dirty) {
            val doc = _ui.value.doc
            if (doc != null) c.appScope.launch { c.packages.save(comicId, doc) }
        }
        super.onCleared()
    }

    val isPreparing: Boolean get() = _ui.value.comic?.status == PrepStatus.PREPARING
}
