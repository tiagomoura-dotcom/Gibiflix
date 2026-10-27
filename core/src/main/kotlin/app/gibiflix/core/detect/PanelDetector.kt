package app.gibiflix.core.detect

import app.gibiflix.core.pkg.NRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DetectorParams(
    /** Diferença máxima para um pixel contar como fundo (sarjeta). */
    val tolerance: Int = 40,
    val readingDirection: String = "ltr",
    /** Página dupla: tenta primeiro separar as duas metades. */
    val spread: Boolean = false,
)

data class DetectionDebug(
    val background: Int,
    val bleed: Boolean,
    val usedComponents: Boolean,
    val overlap: Boolean,
    val coverage: Double,
    val leaves: Int,
)

data class DetectionResult(
    val panels: List<NRect>,
    val confidence: Double,
    val debug: DetectionDebug,
)

/**
 * Detecção de quadros por sarjeta + corte XY recursivo (especificação §5).
 * Trabalha sobre uma imagem em cinza com o lado maior ≤ 1200 px.
 */
object PanelDetector {
    const val NAME = "xycut"
    const val VERSION = 1

    private const val STRICT = 0.98
    private const val SOFT = 0.30
    private const val MIN_PANEL_AREA = 0.015
    private const val BORDER_STRIP = 0.02
    private const val BLEED_VARIATION = 0.25

    private class Rect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w get() = x1 - x0
        val h get() = y1 - y0
        val area get() = w.toLong() * h
        fun isEmpty() = w <= 0 || h <= 0
        fun union(o: Rect) = Rect(min(x0, o.x0), min(y0, o.y0), max(x1, o.x1), max(y1, o.y1))
        fun inter(o: Rect): Long {
            val w = min(x1, o.x1) - max(x0, o.x0)
            val h = min(y1, o.y1) - max(y0, o.y0)
            return if (w > 0 && h > 0) w.toLong() * h else 0L
        }
    }

    private class Ctx(
        val img: GrayImage,
        val gutter: BooleanArray,
        val integral: IntArray,
        val params: DetectorParams,
    ) {
        val w = img.width
        val h = img.height
        val pageArea = w.toLong() * h
        var usedComponents = false
        var leaves = 0

        fun gutterSum(r: Rect): Int {
            val W = w + 1
            return integral[r.y1 * W + r.x1] - integral[r.y0 * W + r.x1] - integral[r.y1 * W + r.x0] + integral[r.y0 * W + r.x0]
        }

        fun content(r: Rect): Long = r.area - gutterSum(r)

        /**
         * Aperta o retângulo ao envolvente do conteúdo. Linhas/colunas da borda com pouquíssimo conteúdo
         * (sujeira esparsa) não seguram o retângulo.
         */
        fun tighten(r: Rect): Rect? {
            if (r.isEmpty()) return null
            var y0 = r.y0; var y1 = r.y1; var x0 = r.x0; var x1 = r.x1
            fun rowEmpty(y: Int) = (x1 - x0) - gutterSum(Rect(x0, y, x1, y + 1)) <= sparse(x1 - x0)
            fun colEmpty(x: Int) = (y1 - y0) - gutterSum(Rect(x, y0, x + 1, y1)) <= sparse(y1 - y0)
            repeat(2) {
                while (y0 < y1 && rowEmpty(y0)) y0++
                while (y1 > y0 && rowEmpty(y1 - 1)) y1--
                if (y0 >= y1) return null
                while (x0 < x1 && colEmpty(x0)) x0++
                while (x1 > x0 && colEmpty(x1 - 1)) x1--
                if (x0 >= x1) return null
            }
            return Rect(x0, y0, x1, y1)
        }

        private fun sparse(len: Int) = (len * 0.015).toInt()
    }

    fun detect(img: GrayImage, params: DetectorParams = DetectorParams()): DetectionResult {
        val w = img.width; val h = img.height
        val strip = max(1, (min(w, h) * BORDER_STRIP).roundToInt())
        val border = IntArray(256)
        var borderCount = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (x < strip || y < strip || x >= w - strip || y >= h - strip) { border[img[x, y]]++; borderCount++ }
        }
        val bg = median(border, borderCount)
        var off = 0
        for (v in 0..255) if (abs(v - bg) > params.tolerance) off += border[v]
        val variation = off.toDouble() / max(1, borderCount)
        if (variation > BLEED_VARIATION) {
            return DetectionResult(listOf(NRect.FULL), 0.8, DetectionDebug(bg, true, false, false, 1.0, 0))
        }

        val gutter = floodGutter(img, bg, params.tolerance)
        val integral = IntArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0
            for (x in 0 until w) {
                if (gutter[y * w + x]) rowSum++
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + rowSum
            }
        }
        val ctx = Ctx(img, gutter, integral, params)
        val root = ctx.tighten(Rect(0, 0, w, h))
            ?: return DetectionResult(emptyList(), 0.5, DetectionDebug(bg, false, false, false, 0.0, 0))

        val raw = mutableListOf<Rect>()
        if (params.spread) {
            val halves = spreadSplit(ctx, root)
            if (halves != null) halves.forEach { cut(ctx, it, raw, 0) } else cut(ctx, root, raw, 0)
        } else {
            cut(ctx, root, raw, 0)
        }

        val panels = cleanup(raw, ctx.pageArea)
        val overlap = hasOverlap(panels, ctx.pageArea)
        val coverage = coverage(ctx, panels)
        var conf = 1.0
        if (ctx.usedComponents) conf -= 0.2
        if (overlap) conf -= 0.2
        if (coverage < 0.7) conf -= 0.3
        if (panels.size > 12) conf -= 0.2
        val norm = panels.map { r -> NRect(r.x0.toDouble() / w, r.y0.toDouble() / h, r.w.toDouble() / w, r.h.toDouble() / h) }
        return DetectionResult(
            norm,
            conf.coerceIn(0.0, 1.0),
            DetectionDebug(bg, false, ctx.usedComponents, overlap, coverage, ctx.leaves),
        )
    }

    private fun median(hist: IntArray, count: Int): Int {
        var acc = 0
        for (v in 0..255) { acc += hist[v]; if (acc * 2 >= count) return v }
        return 255
    }

    /** Flood fill a partir da borda, andando só em pixels parecidos com o fundo. */
    private fun floodGutter(img: GrayImage, bg: Int, tol: Int): BooleanArray {
        val w = img.width; val h = img.height
        val gutter = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun push(i: Int) {
            if (!gutter[i] && abs(img.at(i) - bg) <= tol) { gutter[i] = true; stack[sp++] = i }
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return gutter
    }

    /** Em página dupla, procura a sarjeta vertical perto do meio para ler uma metade de cada vez. */
    private fun spreadSplit(ctx: Ctx, r: Rect): List<Rect>? {
        val mid0 = r.x0 + (r.w * 0.4).toInt()
        val mid1 = r.x0 + (r.w * 0.6).toInt()
        var best = -1; var bestLen = 0; var run = 0
        for (x in mid0 until mid1) {
            val ratio = ctx.gutterSum(Rect(x, r.y0, x + 1, r.y1)).toDouble() / r.h
            if (ratio >= STRICT) { run++; if (run > bestLen) { bestLen = run; best = x - run / 2 } } else run = 0
        }
        if (best < 0) return null
        val left = ctx.tighten(Rect(r.x0, r.y0, best, r.y1)) ?: return null
        val right = ctx.tighten(Rect(best + 1, r.y0, r.x1, r.y1)) ?: return null
        return if (ctx.params.readingDirection == "rtl") listOf(right, left) else listOf(left, right)
    }

    /**
     * Procura faixas de sarjeta ao longo de um eixo. Devolve os segmentos com conteúdo entre as faixas,
     * já apertados, ou null se não houver corte útil.
     */
    private fun segments(ctx: Ctx, r: Rect, horizontal: Boolean, threshold: Double): List<Rect>? {
        val length = if (horizontal) r.h else r.w
        val span = if (horizontal) r.w else r.h
        val minThick = max(1, ((if (horizontal) ctx.h else ctx.w) * 0.004).roundToInt())
        val isGap = BooleanArray(length) { i ->
            val line = if (horizontal) Rect(r.x0, r.y0 + i, r.x1, r.y0 + i + 1) else Rect(r.x0 + i, r.y0, r.x0 + i + 1, r.y1)
            ctx.gutterSum(line).toDouble() / span >= threshold
        }
        val parts = mutableListOf<IntRange>()
        var start = -1
        var i = 0
        while (i < length) {
            if (!isGap[i]) {
                if (start < 0) start = i
                i++
                continue
            }
            var j = i
            while (j < length && isGap[j]) j++
            val interior = i > 0 && j < length
            if (interior && j - i >= minThick && start >= 0) {
                parts += start until i
                start = -1
            }
            i = j
        }
        if (start >= 0) parts += start until length
        if (parts.size < 2) return null
        val rects = parts.mapNotNull { p ->
            val rr = if (horizontal) Rect(r.x0, r.y0 + p.first, r.x1, r.y0 + p.last + 1) else Rect(r.x0 + p.first, r.y0, r.x0 + p.last + 1, r.y1)
            ctx.tighten(rr)
        }
        return rects.takeIf { it.size >= 2 }
    }

    private fun nonTrivial(ctx: Ctx, r: Rect) = ctx.content(r) >= ctx.pageArea * 0.002

    private fun ordered(ctx: Ctx, segs: List<Rect>, horizontal: Boolean): List<Rect> =
        if (!horizontal && ctx.params.readingDirection == "rtl") segs.reversed() else segs

    private fun cut(ctx: Ctx, r: Rect, out: MutableList<Rect>, depth: Int) {
        if (depth > 40) { out += r; return }
        for (horizontal in listOf(true, false)) {
            val segs = segments(ctx, r, horizontal, STRICT) ?: continue
            val big = segs.filter { nonTrivial(ctx, it) }
            if (big.isEmpty()) continue
            if (big.size == 1) {
                // Os outros pedaços são ruído (número de página, sujeira): segue só com o que importa.
                if (big[0].area < r.area) { cut(ctx, big[0], out, depth + 1); return }
                continue
            }
            if (!horizontal) {
                // Colunas cujas sarjetas horizontais se alinham (uma delas interrompida por um balão)
                // são, na verdade, linhas: lê linha por linha.
                val rows = alignedRows(ctx, r, big)
                if (rows != null) { rows.forEach { cut(ctx, it, out, depth + 1) }; return }
            }
            ordered(ctx, big, horizontal).forEach { cut(ctx, it, out, depth + 1) }
            return
        }
        leaf(ctx, r, out, depth)
    }

    /** Faixas horizontais de sarjeta (proporção ≥ [threshold]) internas ao retângulo, em y absoluto. */
    private fun horizontalBands(ctx: Ctx, r: Rect, threshold: Double): List<IntRange> {
        val minThick = max(1, (ctx.h * 0.004).roundToInt())
        val bands = mutableListOf<IntRange>()
        var y = r.y0
        while (y < r.y1) {
            if (ctx.gutterSum(Rect(r.x0, y, r.x1, y + 1)).toDouble() / r.w < threshold) { y++; continue }
            var e = y
            while (e < r.y1 && ctx.gutterSum(Rect(r.x0, e, r.x1, e + 1)).toDouble() / r.w >= threshold) e++
            if (y > r.y0 && e < r.y1 && e - y >= minThick) bands += y until e
            y = e
        }
        return bands
    }

    private fun alignedRows(ctx: Ctx, r: Rect, columns: List<Rect>): List<Rect>? {
        val perColumn = columns.map { c -> horizontalBands(ctx, Rect(c.x0, r.y0, c.x1, r.y1), SOFT) }
        if (perColumn.any { it.isEmpty() }) return null
        val minThick = max(1, (ctx.h * 0.004).roundToInt())
        // Interseção das faixas de todas as colunas.
        var common: List<IntRange> = perColumn[0]
        for (bands in perColumn.drop(1)) {
            common = common.flatMap { a ->
                bands.mapNotNull { b ->
                    val s = max(a.first, b.first); val e = min(a.last, b.last)
                    if (e - s + 1 >= minThick) s..e else null
                }
            }
            if (common.isEmpty()) return null
        }
        val cuts = common.map { (it.first + it.last) / 2 }.sorted()
        val rows = mutableListOf<Rect>()
        var top = r.y0
        for (c in cuts) { ctx.tighten(Rect(r.x0, top, r.x1, c))?.let { rows += it }; top = c + 1 }
        ctx.tighten(Rect(r.x0, top, r.x1, r.y1))?.let { rows += it }
        val big = rows.filter { ctx.content(it) >= ctx.pageArea * MIN_PANEL_AREA }
        return big.takeIf { it.size >= 2 }
    }

    private fun leaf(ctx: Ctx, r: Rect, out: MutableList<Rect>, depth: Int) {
        // Corte "macio": separa quadros ligados por um balão que atravessa a sarjeta.
        for (horizontal in listOf(true, false)) {
            val segs = segments(ctx, r, horizontal, SOFT) ?: continue
            val big = segs.filter { ctx.content(it) >= ctx.pageArea * MIN_PANEL_AREA }
            if (big.size >= 2) {
                ordered(ctx, big, horizontal).forEach { cut(ctx, it, out, depth + 1) }
                return
            }
        }
        ctx.leaves++
        val fraction = ctx.content(r).toDouble() / max(1L, r.area)
        val comps = components(ctx, r)
        val minArea = ctx.pageArea * MIN_PANEL_AREA
        val large = comps.filter { it.second >= minArea }
        if (large.size >= 2 || (fraction < 0.75 && large.isNotEmpty())) {
            ctx.usedComponents = true
            val boxes = large.map { it.first }.toMutableList()
            for ((box, size) in comps) {
                if (size >= minArea) continue
                var best = -1; var bestOverlap = 0L
                boxes.forEachIndexed { k, b -> val o = b.inter(box); if (o > bestOverlap) { bestOverlap = o; best = k } }
                if (best >= 0) boxes[best] = boxes[best].union(box)
            }
            out += orderRows(boxes, ctx.params.readingDirection)
        } else {
            out += r
        }
    }

    /** Componentes conexos (8 vizinhos) de conteúdo dentro do retângulo: envolvente e número de pixels. */
    private fun components(ctx: Ctx, r: Rect): List<Pair<Rect, Long>> {
        val w = ctx.w
        val label = IntArray(r.w * r.h)
        val stack = IntArray(r.w * r.h)
        val result = mutableListOf<Pair<Rect, Long>>()
        var next = 1
        for (y in r.y0 until r.y1) for (x in r.x0 until r.x1) {
            val li = (y - r.y0) * r.w + (x - r.x0)
            if (label[li] != 0 || ctx.gutter[y * w + x]) continue
            var sp = 0
            stack[sp++] = li; label[li] = next
            var minX = x; var maxX = x; var minY = y; var maxY = y; var count = 0L
            while (sp > 0) {
                val cur = stack[--sp]
                val cx = cur % r.w + r.x0; val cy = cur / r.w + r.y0
                count++
                if (cx < minX) minX = cx; if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy; if (cy > maxY) maxY = cy
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx; val ny = cy + dy
                    if (nx < r.x0 || ny < r.y0 || nx >= r.x1 || ny >= r.y1) continue
                    val nl = (ny - r.y0) * r.w + (nx - r.x0)
                    if (label[nl] == 0 && !ctx.gutter[ny * w + nx]) { label[nl] = next; stack[sp++] = nl }
                }
            }
            result += Rect(minX, minY, maxX + 1, maxY + 1) to count
            next++
        }
        return result
    }

    /** Agrupa em linhas (sobreposição vertical > 50%), linhas de cima para baixo, quadros pela direção. */
    private fun orderRows(boxes: List<Rect>, dir: String): List<Rect> {
        val rows = mutableListOf<MutableList<Rect>>()
        for (b in boxes.sortedBy { it.y0 }) {
            val row = rows.firstOrNull { row ->
                row.any { o ->
                    val ov = min(o.y1, b.y1) - max(o.y0, b.y0)
                    ov > 0.5 * min(o.h, b.h)
                }
            }
            if (row != null) row += b else rows += mutableListOf(b)
        }
        return rows.sortedBy { row -> row.minOf { it.y0 } }.flatMap { row ->
            if (dir == "rtl") row.sortedByDescending { it.x1 } else row.sortedBy { it.x0 }
        }
    }

    private fun cleanup(raw: List<Rect>, pageArea: Long): List<Rect> {
        var list = raw.filter { !it.isEmpty() }
        val min = pageArea * MIN_PANEL_AREA
        if (list.any { it.area >= min }) list = list.filter { it.area >= min }
        val result = list.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in result.indices) for (j in i + 1 until result.size) {
                val a = result[i]; val b = result[j]
                val inter = a.inter(b).toDouble()
                if (inter <= 0) continue
                val iou = inter / (a.area + b.area - inter)
                val contain = inter / min(a.area, b.area)
                if (iou > 0.6 || contain > 0.9) {
                    result[i] = a.union(b)
                    result.removeAt(j)
                    merged = true
                    break@loop
                }
            }
        }
        return result
    }

    private fun hasOverlap(panels: List<Rect>, pageArea: Long): Boolean {
        for (i in panels.indices) for (j in i + 1 until panels.size) {
            if (panels[i].inter(panels[j]) > pageArea * 0.001) return true
        }
        return false
    }

    /** Fração dos pixels de conteúdo que caem dentro de algum quadro. */
    private fun coverage(ctx: Ctx, panels: List<Rect>): Double {
        val total = ctx.content(Rect(0, 0, ctx.w, ctx.h))
        if (total <= 0) return 1.0
        val covered = BooleanArray(ctx.w * ctx.h)
        var inside = 0L
        for (p in panels) for (y in p.y0 until p.y1) for (x in p.x0 until p.x1) {
            val i = y * ctx.w + x
            if (!covered[i]) { covered[i] = true; if (!ctx.gutter[i]) inside++ }
        }
        return inside.toDouble() / total
    }
}

/** Margens para o modo página: união dos quadros + 1% de folga, só se cortar ≥ 1% de algum lado. */
object TrimCalculator {
    fun compute(panels: List<NRect>): NRect? {
        if (panels.isEmpty()) return null
        val u = panels.reduce { a, b -> a.union(b) }
        val l = (u.x - 0.01).coerceAtLeast(0.0)
        val t = (u.y - 0.01).coerceAtLeast(0.0)
        val r = (u.right + 0.01).coerceAtMost(1.0)
        val b = (u.bottom + 0.01).coerceAtMost(1.0)
        val cuts = listOf(l, t, 1 - r, 1 - b)
        if (cuts.none { it >= 0.01 }) return null
        return NRect(l, t, r - l, b - t)
    }
}
