package app.gibiflix.core.edit

import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.Panel
import kotlin.math.abs

/** Operações do editor de quadros sobre uma lista ordenada de quadros. Tudo puro e testável. */
object PanelOps {
    const val MIN_SIZE = 0.02

    fun add(panels: List<Panel>, r: NRect): List<Panel> {
        val c = normalize(r)
        if (c.w < MIN_SIZE || c.h < MIN_SIZE) return panels
        return panels + Panel.of(c, Panel.SOURCE_MANUAL)
    }

    fun delete(panels: List<Panel>, indices: Set<Int>): List<Panel> = panels.filterIndexed { i, _ -> i !in indices }

    /** Junta os selecionados no lugar do primeiro (na ordem de leitura). */
    fun merge(panels: List<Panel>, indices: Set<Int>): List<Panel> {
        if (indices.size < 2) return panels
        val sorted = indices.filter { it in panels.indices }.sorted()
        val union = sorted.map { panels[it].rect }.reduce { a, b -> a.union(b) }
        val out = mutableListOf<Panel>()
        panels.forEachIndexed { i, p ->
            when {
                i == sorted.first() -> out += Panel.of(union, Panel.SOURCE_MANUAL)
                i in sorted -> Unit
                else -> out += p
            }
        }
        return out
    }

    /**
     * Divide o quadro pela linha traçada (de [a] para [b], normalizados). Linha mais horizontal corta em
     * cima/baixo; mais vertical corta em esquerda/direita (ordem pela direção de leitura).
     */
    fun split(panels: List<Panel>, index: Int, ax: Double, ay: Double, bx: Double, by: Double, direction: String): List<Panel> {
        val p = panels.getOrNull(index) ?: return panels
        val r = p.rect
        val horizontalLine = abs(bx - ax) >= abs(by - ay)
        val parts: List<NRect> = if (horizontalLine) {
            val y = ((ay + by) / 2).coerceIn(r.y + MIN_SIZE, r.bottom - MIN_SIZE)
            if (y <= r.y || y >= r.bottom) return panels
            listOf(NRect(r.x, r.y, r.w, y - r.y), NRect(r.x, y, r.w, r.bottom - y))
        } else {
            val x = ((ax + bx) / 2).coerceIn(r.x + MIN_SIZE, r.right - MIN_SIZE)
            if (x <= r.x || x >= r.right) return panels
            val l = NRect(r.x, r.y, x - r.x, r.h); val rr = NRect(x, r.y, r.right - x, r.h)
            if (direction == "rtl") listOf(rr, l) else listOf(l, rr)
        }
        val out = panels.toMutableList()
        out.removeAt(index)
        out.addAll(index, parts.map { Panel.of(it, Panel.SOURCE_MANUAL) })
        return out
    }

    fun move(panels: List<Panel>, index: Int, dx: Double, dy: Double): List<Panel> = update(panels, index) { r ->
        NRect((r.x + dx).coerceIn(0.0, 1 - r.w), (r.y + dy).coerceIn(0.0, 1 - r.h), r.w, r.h)
    }

    enum class Corner { TL, TR, BL, BR }

    fun resize(panels: List<Panel>, index: Int, corner: Corner, px: Double, py: Double): List<Panel> = update(panels, index) { r ->
        val x = px.coerceIn(0.0, 1.0); val y = py.coerceIn(0.0, 1.0)
        var l = r.x; var t = r.y; var rt = r.right; var b = r.bottom
        when (corner) {
            Corner.TL -> { l = minOf(x, rt - MIN_SIZE); t = minOf(y, b - MIN_SIZE) }
            Corner.TR -> { rt = maxOf(x, l + MIN_SIZE); t = minOf(y, b - MIN_SIZE) }
            Corner.BL -> { l = minOf(x, rt - MIN_SIZE); b = maxOf(y, t + MIN_SIZE) }
            Corner.BR -> { rt = maxOf(x, l + MIN_SIZE); b = maxOf(y, t + MIN_SIZE) }
        }
        NRect(l, t, rt - l, b - t)
    }

    fun reorder(panels: List<Panel>, from: Int, to: Int): List<Panel> {
        if (from !in panels.indices || to !in panels.indices || from == to) return panels
        val out = panels.toMutableList()
        val p = out.removeAt(from)
        out.add(to, p)
        return out
    }

    /** Quadro sob o ponto: o menor que o contém (assim dá para escolher um quadro dentro de outro). */
    fun hit(panels: List<Panel>, x: Double, y: Double): Int =
        panels.withIndex().filter { it.value.rect.contains(x, y) }.minByOrNull { it.value.rect.area }?.index ?: -1

    private fun update(panels: List<Panel>, index: Int, f: (NRect) -> NRect): List<Panel> {
        if (index !in panels.indices) return panels
        val out = panels.toMutableList()
        val p = out[index]
        val r = normalize(f(p.rect))
        out[index] = p.copy(x = r.x, y = r.y, w = r.w, h = r.h, source = Panel.SOURCE_MANUAL)
        return out
    }

    private fun normalize(r: NRect): NRect {
        val l = minOf(r.x, r.right).coerceIn(0.0, 1.0)
        val t = minOf(r.y, r.bottom).coerceIn(0.0, 1.0)
        val rt = maxOf(r.x, r.right).coerceIn(0.0, 1.0)
        val b = maxOf(r.y, r.bottom).coerceIn(0.0, 1.0)
        fun round(v: Double) = Math.round(v * 10000) / 10000.0
        return NRect(round(l), round(t), round(rt - l), round(b - t))
    }
}

/** Desfazer/refazer com limite de passos. */
class UndoStack<T>(private val limit: Int = 20) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Guarda o estado anterior a uma mudança. */
    fun push(before: T) {
        undo.addLast(before)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: T): T? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return prev
    }

    fun redo(current: T): T? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }

    fun clear() { undo.clear(); redo.clear() }
}
