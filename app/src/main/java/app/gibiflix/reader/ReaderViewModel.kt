package app.gibiflix.reader

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.device.Profile
import app.gibiflix.core.pkg.GibiflixDoc
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.TextBlock
import app.gibiflix.core.reader.Navigator
import app.gibiflix.core.reader.PlanOptions
import app.gibiflix.core.reader.Position
import app.gibiflix.core.reader.ReadingPlan
import app.gibiflix.core.reader.Screen
import app.gibiflix.core.reader.ViewKind
import app.gibiflix.core.reader.ViewSpec
import app.gibiflix.core.settings.ImageSettings
import app.gibiflix.core.settings.ProfileSettings
import app.gibiflix.core.settings.ReadMode
import app.gibiflix.core.settings.ReaderSettings
import app.gibiflix.data.db.BookmarkEntity
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.ProgressEntity
import app.gibiflix.data.db.ReadingSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Transição de "câmera" no tablet: anima o recorte da página entre duas vistas. */
data class Camera(val page: Bitmap, val from: NRect, val to: NRect, val id: Long)

data class ReaderUi(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val pageCount: Int = 0,
    val pos: Position = Position(0, 0),
    val view: ViewSpec? = null,
    val frame: Bitmap? = null,
    val mode: ReadMode = ReadMode.PANEL,
    val direction: String = "ltr",
    val hasPanels: Boolean = false,
    val viewsOnPage: Int = 1,
    val menu: Boolean = false,
    val showHint: Boolean = false,
    val camera: Camera? = null,
    val bookmarked: Boolean = false,
    val text: List<TextBlock>? = null,
    val showText: Boolean = false,
    val endReached: Boolean = false,
)

class ReaderViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val c = app.container
    val comicId: String = saved.get<String>(ReaderActivity.EXTRA_COMIC_ID).orEmpty()
    private val startPage: Int = saved.get<Int>(ReaderActivity.EXTRA_PAGE) ?: -1
    private val startPanel: Int = saved.get<Int>(ReaderActivity.EXTRA_PANEL) ?: -1

    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()

    val pageCache = PageCache(c.files)
    val renderer = PageRenderer()

    private var comic: ComicEntity? = null
    private var doc: GibiflixDoc? = null
    private var screen: Screen? = null
    private var nav: Navigator? = null
    private var reader = ReaderSettings()
    private var profileSettings: ProfileSettings = app.gibiflix.core.settings.ProfileDefaults.of(c.deviceProfile.profile)
    private var profile: Profile = c.deviceProfile.profile
    private var mode: ReadMode = ReadMode.PANEL
    private var renderJob: Job? = null
    private var preloadJob: Job? = null
    private var deviceId = ""
    private var cameraSeq = 0L
    private var pageBitmaps = HashMap<Int, Bitmap>()
    private var pendingStart: Pair<Int, Int>? = null
    private val realWidths = HashMap<String, Int>()

    // Sessão de leitura (estatísticas).
    private var sessionStart = 0L
    private val sessionPages = HashSet<Int>()
    private var sessionSteps = 0
    private var sessionFinished = false

    val readerSettings: ReaderSettings get() = reader
    val currentProfile: Profile get() = profile

    init {
        viewModelScope.launch { load(initial = true) }
        viewModelScope.launch {
            c.packages.changes.collect { id -> if (id == comicId) load(initial = false) }
        }
        viewModelScope.launch {
            c.settings.user.collect { u ->
                val old = profileSettings
                reader = u.reader
                profile = c.deviceProfile.profile
                profileSettings = u.forProfile(profile)
                pageCache.limitBytes = profileSettings.cacheMb.toLong() * 1024 * 1024
                c.eink.configure(profileSettings.fullRefreshEvery, profileSettings.flashClean)
                if (doc != null && (old.image != profileSettings.image || old.splitWidePanels != profileSettings.splitWidePanels)) {
                    renderer.clear()
                    rebuild(keep = true)
                }
            }
        }
    }

    private suspend fun load(initial: Boolean) {
        val comic = c.db.comics().get(comicId)
        val parsed = c.packages.load(comicId)
        if (comic == null || parsed == null) {
            _ui.update { it.copy(loading = false, error = getApplication<Application>().getString(R.string.reader_error)) }
            return
        }
        this.comic = comic
        this.doc = parsed.doc
        deviceId = c.settings.deviceId()
        val u = c.settings.user.first()
        reader = u.reader
        profile = c.deviceProfile.profile
        profileSettings = u.forProfile(profile)
        pageCache.limitBytes = profileSettings.cacheMb.toLong() * 1024 * 1024
        c.eink.configure(profileSettings.fullRefreshEvery, profileSettings.flashClean)
        if (initial) {
            c.db.comics().markOpened(comicId)
            val progress = c.db.progress().get(comicId)
            mode = when {
                progress != null -> if (progress.mode == "page") ReadMode.PAGE else ReadMode.PANEL
                else -> reader.defaultMode
            }
            pendingStart = when {
                startPage >= 0 -> startPage to startPanel.coerceAtLeast(0)
                progress != null && !progress.finished -> progress.page to progress.panel
                else -> 0 to 0
            }
            val showHint = reader.hintsShown < 3
            if (showHint) c.settings.updateUser { it.copy(reader = it.reader.copy(hintsShown = it.reader.hintsShown + 1)) }
            _ui.update { it.copy(showHint = showHint) }
            startSession()
        } else {
            renderer.clear()
            pageBitmaps.clear()
        }
        pageBitmaps.clear()
        _ui.update {
            it.copy(
                title = displayTitle(comic),
                pageCount = parsed.doc.pages.size,
                direction = parsed.doc.readingDirection,
                hasPanels = parsed.doc.pages.any { p -> p.panels.isNotEmpty() },
                mode = mode,
            )
        }
        rebuild(keep = !initial)
    }

    private fun displayTitle(c: ComicEntity) = when {
        c.series != null && c.number != null -> "${c.series} #${c.number}"
        else -> c.title
    }

    fun setScreen(width: Int, height: Int) {
        val s = Screen(width, height)
        if (s == screen || width <= 0 || height <= 0) return
        screen = s
        renderer.clear()
        pageBitmaps.clear()
        rebuild(keep = true)
    }

    private fun options() = PlanOptions(
        mode = mode,
        pageBefore = reader.pageBeforePanels,
        pageAfter = reader.pageAfterPanels,
        splitWide = profileSettings.splitWidePanels,
        marginPct = reader.panelMarginPct,
        maxZoom = reader.maxZoom,
    )

    /** Recria a navegação (tela, modo ou documento mudou), mantendo a página e o quadro. */
    private fun rebuild(keep: Boolean) {
        val d = doc ?: return
        val s = screen ?: return
        if (d.pages.isEmpty()) return
        val oldView = _ui.value.view
        val n = Navigator(d, options(), s)
        nav = n
        val start = pendingStart
        val pos = when {
            start != null -> { pendingStart = null; n.positionFor(start.first, start.second) }
            keep && oldView != null -> n.positionFor(oldView.page.coerceAtMost(d.pages.lastIndex), oldView.panelIndex.coerceAtLeast(0))
            else -> n.positionFor(0, 0)
        }
        show(pos, animate = false)
    }

    fun next() {
        val n = nav ?: return
        val cur = _ui.value.pos
        val p = n.next(cur)
        if (p == null) { finishReached(); return }
        show(p, animate = true)
    }

    fun prev() {
        val n = nav ?: return
        n.prev(_ui.value.pos)?.let { show(it, animate = true) }
    }

    fun nextPage() {
        val n = nav ?: return
        val p = n.nextPage(_ui.value.pos)
        if (p == null) { finishReached(); return }
        show(p, animate = false)
    }

    fun prevPage() {
        val n = nav ?: return
        val cur = _ui.value.pos
        // Primeiro volta ao início da página atual; depois para a anterior.
        val p = if (cur.view > 0) cur.copy(view = 0) else n.prevPage(cur)
        p?.let { show(it, animate = false) }
    }

    fun goToPage(page: Int) {
        val n = nav ?: return
        show(Position(page.coerceIn(0, n.pageCount - 1), 0), animate = false)
    }

    fun goTo(page: Int, panel: Int) {
        val n = nav ?: return
        show(n.positionFor(page, panel), animate = false)
    }

    fun toggleMode() {
        mode = if (mode == ReadMode.PANEL) ReadMode.PAGE else ReadMode.PANEL
        _ui.update { it.copy(mode = mode) }
        rebuild(keep = true)
    }

    fun setDirection(dir: String) {
        val d = doc ?: return
        if (d.readingDirection == dir) return
        viewModelScope.launch {
            val cm = comic ?: return@launch
            c.library.editInfo(comicId, cm.title, cm.series, cm.number, dir)
        }
    }

    fun toggleMenu() = _ui.update { it.copy(menu = !it.menu) }
    fun hideMenu() = _ui.update { it.copy(menu = false) }
    fun cameraDone(id: Long) = _ui.update { if (it.camera?.id == id) it.copy(camera = null) else it }
    fun dismissEnd() = _ui.update { it.copy(endReached = false) }
    fun toggleText() = _ui.update { it.copy(showText = !it.showText) }

    fun adjustBrightness(delta: Int) = viewModelScope.launch {
        c.settings.updateProfile(profile) { s -> s.copy(image = s.image.copy(brightness = (s.image.brightness + delta).coerceIn(-50, 50))) }
    }

    private fun finishReached() {
        markFinished()
        _ui.update { it.copy(endReached = true) }
    }

    private fun markFinished() {
        if (sessionFinished) return
        sessionFinished = true
        viewModelScope.launch {
            c.db.comics().setRead(comicId, true)
            val p = c.db.progress().get(comicId)
            if (p != null) c.db.progress().upsert(p.copy(finished = true, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun renderKey(v: ViewSpec, scale: Double): String {
        val s = screen
        return "${v.page}:${v.rect}:${"%.4f".format(scale)}:${profileSettings.image.hashCode()}:${profile}:${s?.width}x${s?.height}:${doc?.revision}"
    }

    private suspend fun renderView(v: ViewSpec): Bitmap? {
        val d = doc ?: return null
        val s = screen ?: return null
        val page = d.pages[v.page]
        val scale = ReadingPlan.scaleFor(v, page, s, options())
        val key = renderKey(v, scale)
        renderer.cached(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = pageCache.page(comicId, "${d.id.take(8)}-${d.sourceHash.takeLast(8)}", page.file)
            // A escala usa as dimensões do JSON; o renderizador ajusta às dimensões reais do arquivo.
            val bmp = renderer.render(file, v.rect, scale * page.width / realWidth(file, page.width), profileSettings.image, profile == Profile.EINK_MONO)
            renderer.put(key, bmp)
            bmp
        }
    }

    private fun realWidth(file: java.io.File, fallback: Int): Int = realWidths.getOrPut(file.absolutePath) {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, o)
        if (o.outWidth > 0) o.outWidth else fallback
    }

    private suspend fun pageBitmap(index: Int): Bitmap? {
        pageBitmaps[index]?.let { return it }
        val d = doc ?: return null
        val s = screen ?: return null
        return withContext(Dispatchers.IO) {
            val file = pageCache.page(comicId, "${d.id.take(8)}-${d.sourceHash.takeLast(8)}", d.pages[index].file)
            val long = (maxOf(s.width, s.height) * 1.6).toInt().coerceAtMost(2600)
            renderer.renderWhole(file, long, long, profileSettings.image, profile == Profile.EINK_MONO)
        }.also { bmp ->
            if (pageBitmaps.size > 2) pageBitmaps.clear()
            pageBitmaps[index] = bmp
        }
    }

    private fun show(pos: Position, animate: Boolean) {
        val n = nav ?: return
        val d = doc ?: return
        val view = n.view(pos)
        val previous = _ui.value.view
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val useCamera = animate && !profile.isEink && previous != null && previous.page == view.page &&
                previous.rect != view.rect && _ui.value.frame != null
            val camera = if (useCamera) pageBitmap(view.page)?.let { Camera(it, previous!!.rect, view.rect, ++cameraSeq) } else null
            if (camera != null) _ui.update { it.copy(camera = camera) }
            val frame = try {
                renderView(view)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            val page = d.pages[view.page]
            val blocks = page.panels.getOrNull(view.panelIndex)?.text
            _ui.update {
                it.copy(
                    loading = false,
                    error = null,
                    pos = pos,
                    view = view,
                    frame = frame,
                    viewsOnPage = n.views(pos.page).size,
                    text = blocks,
                    bookmarked = false,
                )
            }
            if (camera == null) c.eink.onContentChanged()
            onPositionShown(pos, view, n)
            preload(pos)
        }
    }

    /** Usado pela UI: e-ink avisado quando a animação acaba. */
    fun onCameraFinished(id: Long) {
        cameraDone(id)
        c.eink.onContentChanged()
    }

    private fun onPositionShown(pos: Position, view: ViewSpec, n: Navigator) {
        sessionPages += pos.page
        sessionSteps++
        val d = doc ?: return
        val page = d.pages[pos.page]
        val last = n.isLast(pos)
        viewModelScope.launch {
            c.db.progress().upsert(
                ProgressEntity(
                    comicId = comicId,
                    page = pos.page,
                    panel = view.panelIndex.coerceAtLeast(0),
                    mode = if (mode == ReadMode.PAGE) "page" else "panel",
                    updatedAt = System.currentTimeMillis(),
                    deviceId = deviceId,
                    finished = last,
                    pageCount = d.pages.size,
                    panelCount = page.panels.size,
                )
            )
            if (last) markFinished()
            val marks = c.db.bookmarks().observeFor(comicId).first()
            _ui.update { s -> s.copy(bookmarked = marks.any { it.page == pos.page && it.panel == view.panelIndex.coerceAtLeast(0) }) }
        }
    }

    private fun preload(pos: Position) {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            val n = nav ?: return@launch
            var p: Position? = pos
            repeat(2) {
                p = p?.let { n.next(it) }
                p?.let { runCatching { renderView(n.view(it)) } }
            }
            // Próxima página no cache de disco.
            val d = doc ?: return@launch
            val np = pos.page + 1
            if (np < d.pages.size) withContext(Dispatchers.IO) {
                runCatching { pageCache.page(comicId, "${d.id.take(8)}-${d.sourceHash.takeLast(8)}", d.pages[np].file) }
            }
        }
    }

    /** Miniatura da página (indicador e barra de páginas). */
    suspend fun thumbnail(index: Int, maxW: Int, maxH: Int): Bitmap? {
        val d = doc ?: return null
        if (index !in d.pages.indices) return null
        val key = "thumb:$index:$maxW:${d.revision}"
        renderer.cached(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = pageCache.page(comicId, "${d.id.take(8)}-${d.sourceHash.takeLast(8)}", d.pages[index].file)
                renderer.renderWhole(file, maxW, maxH).also { renderer.put(key, it) }
            }.getOrNull()
        }
    }

    // ---- Marcadores ----
    fun toggleBookmark(note: String = "") = viewModelScope.launch {
        val v = _ui.value.view ?: return@launch
        val panel = v.panelIndex.coerceAtLeast(0)
        val existing = c.db.bookmarks().observeFor(comicId).first().firstOrNull { it.page == v.page && it.panel == panel }
        val now = System.currentTimeMillis()
        if (existing != null) {
            c.db.bookmarks().upsert(existing.copy(deleted = true, updatedAt = now, deviceId = deviceId))
            _ui.update { it.copy(bookmarked = false) }
        } else {
            c.db.bookmarks().upsert(
                BookmarkEntity(UUID.randomUUID().toString(), comicId, v.page, panel, note.trim(), now, now, false, deviceId)
            )
            _ui.update { it.copy(bookmarked = true) }
        }
    }

    // ---- Sessões ----
    fun startSession() {
        sessionStart = System.currentTimeMillis()
        sessionPages.clear()
        sessionSteps = 0
    }

    fun endSession() {
        val start = sessionStart
        if (start == 0L) return
        val end = System.currentTimeMillis()
        sessionStart = 0L
        if (end - start < 30_000) return
        val pages = sessionPages.size
        val steps = sessionSteps
        val finished = sessionFinished
        c.appScope.launch {
            c.db.sessions().insert(
                ReadingSessionEntity(
                    id = UUID.randomUUID().toString(),
                    comicId = comicId,
                    startedAt = start,
                    endedAt = end,
                    pages = pages,
                    panels = steps,
                    device = c.settings.deviceName(),
                    finishedComic = finished,
                )
            )
        }
    }

    override fun onCleared() {
        endSession()
        super.onCleared()
    }

    fun isTablet() = !profile.isEink
    fun pageInfo(index: Int) = doc?.pages?.getOrNull(index)
    fun docDirection() = doc?.readingDirection ?: "ltr"
    fun viewKind() = _ui.value.view?.kind ?: ViewKind.PAGE
}
