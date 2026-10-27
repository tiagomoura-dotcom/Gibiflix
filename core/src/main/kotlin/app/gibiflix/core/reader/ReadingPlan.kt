package app.gibiflix.core.reader

import app.gibiflix.core.pkg.GibiflixDoc
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.core.settings.ReadMode
import kotlin.math.min

data class Screen(val width: Int, val height: Int) {
    val portrait: Boolean get() = height >= width
}

data class PlanOptions(
    val mode: ReadMode = ReadMode.PANEL,
    val pageBefore: Boolean = false,
    val pageAfter: Boolean = false,
    val splitWide: Boolean = false,
    val marginPct: Int = 2,
    val maxZoom: Float = 3f,
)

enum class ViewKind { PAGE, HALF, PANEL }

/**
 * Uma "vista" do leitor: um retângulo normalizado da página.
 * [panelIndex] é o quadro (−1 para página inteira); [part]/[parts] numeram as partes de um quadro dividido.
 */
data class ViewSpec(
    val page: Int,
    val rect: NRect,
    val kind: ViewKind,
    val panelIndex: Int = -1,
    val panelCount: Int = 0,
    val part: Int = 0,
    val parts: Int = 1,
)

object ReadingPlan {
    /** Ocupação abaixo da qual um quadro é dividido. */
    const val SPLIT_THRESHOLD = 0.35
    const val OVERLAP = 0.10

    fun viewsForPage(doc: GibiflixDoc, index: Int, opts: PlanOptions, screen: Screen): List<ViewSpec> {
        val page = doc.pages[index]
        val dir = page.direction(doc.readingDirection)
        val pageViews = pageViews(index, page, dir, screen)
        if (opts.mode == ReadMode.PAGE || page.panels.isEmpty()) return pageViews
        val out = mutableListOf<ViewSpec>()
        if (opts.pageBefore) out += pageViews
        page.panels.forEachIndexed { pi, panel ->
            val rect = panel.rect.clamp()
            if (rect.w <= 0 || rect.h <= 0) return@forEachIndexed
            val parts = if (opts.splitWide) splitPanel(rect, page, dir, screen, opts.marginPct) else listOf(rect)
            parts.forEachIndexed { k, r ->
                out += ViewSpec(index, r, ViewKind.PANEL, pi, page.panels.size, k, parts.size)
            }
        }
        if (opts.pageAfter) out += pageViews
        return out.ifEmpty { pageViews }
    }

    /** Página inteira (aparada). Página dupla em tela retrato vira duas metades na ordem de leitura. */
    fun pageViews(index: Int, page: PageInfo, dir: String, screen: Screen): List<ViewSpec> {
        val base = page.trim?.clamp()?.takeIf { it.w > 0 && it.h > 0 } ?: NRect.FULL
        if (page.spread && screen.portrait) {
            val midX = 0.5
            val left = NRect(base.x, base.y, (midX - base.x).coerceAtLeast(0.01), base.h)
            val right = NRect(midX, base.y, (base.right - midX).coerceAtLeast(0.01), base.h)
            val ordered = if (dir == GibiflixDoc.RTL) listOf(right, left) else listOf(left, right)
            return ordered.mapIndexed { i, r -> ViewSpec(index, r, ViewKind.HALF, part = i, parts = 2) }
        }
        return listOf(ViewSpec(index, base, ViewKind.PAGE))
    }

    /**
     * Quadros muito largos (ou altos): se, ajustado à tela, ocupa menos de 35% da altura (ou largura),
     * vira 2 ou 3 vistas com 10% de sobreposição.
     */
    fun splitPanel(rect: NRect, page: PageInfo, dir: String, screen: Screen, marginPct: Int): List<NRect> {
        val pw = rect.w * page.width
        val ph = rect.h * page.height
        if (pw <= 0 || ph <= 0) return listOf(rect)
        val m = 1 - 2 * marginPct / 100.0
        val s = min(screen.width * m / pw, screen.height * m / ph)
        val occH = ph * s / screen.height
        val occW = pw * s / screen.width
        return when {
            occH < SPLIT_THRESHOLD -> {
                val n = if (occH < 0.2) 3 else 2
                val parts = splitRange(rect.x, rect.w, n).map { (x, w) -> NRect(x, rect.y, w, rect.h) }
                if (dir == GibiflixDoc.RTL) parts.reversed() else parts
            }
            occW < SPLIT_THRESHOLD -> {
                val n = if (occW < 0.2) 3 else 2
                splitRange(rect.y, rect.h, n).map { (y, h) -> NRect(rect.x, y, rect.w, h) }
            }
            else -> listOf(rect)
        }
    }

    private fun splitRange(start: Double, length: Double, n: Int): List<Pair<Double, Double>> {
        val part = length / (n - (n - 1) * OVERLAP)
        val step = part * (1 - OVERLAP)
        return (0 until n).map { i -> (start + i * step) to part }
    }

    /**
     * Escala para mostrar uma vista: cabe na tela com margem; quadros não passam de [PlanOptions.maxZoom]
     * vezes o tamanho da página inteira ajustada.
     */
    fun scaleFor(view: ViewSpec, page: PageInfo, screen: Screen, opts: PlanOptions): Double {
        val vw = view.rect.w * page.width
        val vh = view.rect.h * page.height
        if (view.kind != ViewKind.PANEL) return min(screen.width / vw, screen.height / vh)
        val m = 1 - 2 * opts.marginPct.coerceIn(0, 5) / 100.0
        val fit = min(screen.width * m / vw, screen.height * m / vh)
        val pageFit = min(screen.width.toDouble() / page.width, screen.height.toDouble() / page.height)
        return min(fit, pageFit * opts.maxZoom.coerceAtLeast(1f))
    }
}

/** Posição no livro: página e índice da vista dentro da página. */
data class Position(val page: Int, val view: Int)

/** Navegação sobre as vistas, calculando cada página sob demanda. */
class Navigator(
    private val doc: GibiflixDoc,
    private val opts: PlanOptions,
    private val screen: Screen,
) {
    private val cache = HashMap<Int, List<ViewSpec>>()

    val pageCount: Int get() = doc.pages.size

    fun views(page: Int): List<ViewSpec> = cache.getOrPut(page) { ReadingPlan.viewsForPage(doc, page, opts, screen) }

    fun view(pos: Position): ViewSpec = views(pos.page)[pos.view.coerceIn(0, views(pos.page).lastIndex)]

    fun next(pos: Position): Position? {
        if (pos.view + 1 < views(pos.page).size) return pos.copy(view = pos.view + 1)
        return if (pos.page + 1 < pageCount) Position(pos.page + 1, 0) else null
    }

    fun prev(pos: Position): Position? {
        if (pos.view > 0) return pos.copy(view = pos.view - 1)
        return if (pos.page > 0) Position(pos.page - 1, views(pos.page - 1).lastIndex) else null
    }

    fun nextPage(pos: Position): Position? = if (pos.page + 1 < pageCount) Position(pos.page + 1, 0) else null
    fun prevPage(pos: Position): Position? = if (pos.page > 0) Position(pos.page - 1, 0) else null

    fun isLast(pos: Position): Boolean = pos.page == pageCount - 1 && pos.view == views(pos.page).lastIndex

    /** Converte "quadro N da página" (progresso salvo) para a vista correspondente. */
    fun positionFor(page: Int, panel: Int): Position {
        val p = page.coerceIn(0, maxOf(0, pageCount - 1))
        val vs = views(p)
        val idx = vs.indexOfFirst { it.panelIndex == panel && it.part == 0 }
        return Position(p, if (idx >= 0) idx else 0)
    }
}
