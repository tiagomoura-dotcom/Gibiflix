package app.gibiflix.core.edit

import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.Panel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelOpsTest {
    private val a = Panel.of(NRect(0.0, 0.0, 0.5, 0.5))
    private val b = Panel.of(NRect(0.5, 0.0, 0.5, 0.5))
    private val c = Panel.of(NRect(0.0, 0.5, 1.0, 0.5))

    @Test fun addDeleteAndIgnoreTiny() {
        val p = PanelOps.add(listOf(a), NRect(0.6, 0.6, -0.2, -0.2))
        assertEquals(2, p.size)
        assertEquals(0.4, p[1].x, 1e-9); assertEquals("manual", p[1].source)
        assertEquals(1, PanelOps.add(listOf(a), NRect(0.1, 0.1, 0.001, 0.5)).size)
        assertEquals(listOf(b), PanelOps.delete(listOf(a, b), setOf(0)))
    }

    @Test fun mergeKeepsPositionOfFirst() {
        val m = PanelOps.merge(listOf(a, b, c), setOf(0, 1))
        assertEquals(2, m.size)
        assertEquals(NRect(0.0, 0.0, 1.0, 0.5), m[0].rect)
        assertEquals(c, m[1])
    }

    @Test fun splitHorizontalAndVerticalRtl() {
        val h = PanelOps.split(listOf(c), 0, 0.1, 0.75, 0.9, 0.75, "ltr")
        assertEquals(2, h.size); assertEquals(0.75, h[1].y, 1e-9)
        val v = PanelOps.split(listOf(c), 0, 0.3, 0.55, 0.3, 0.95, "rtl")
        assertEquals(2, v.size); assertTrue(v[0].x > v[1].x)
    }

    @Test fun moveAndResizeStayInsidePage() {
        val m = PanelOps.move(listOf(a), 0, 0.8, 0.0)
        assertEquals(0.5, m[0].x, 1e-9)
        val r = PanelOps.resize(listOf(a), 0, PanelOps.Corner.BR, 0.7, 0.9)
        assertEquals(NRect(0.0, 0.0, 0.7, 0.9), r[0].rect)
        val inverted = PanelOps.resize(listOf(a), 0, PanelOps.Corner.TL, 0.9, 0.9)
        assertTrue(inverted[0].w >= PanelOps.MIN_SIZE - 1e-9)
    }

    @Test fun reorderAndHit() {
        assertEquals(listOf(b, c, a), PanelOps.reorder(listOf(a, b, c), 0, 2))
        val inner = Panel.of(NRect(0.1, 0.1, 0.1, 0.1))
        assertEquals(1, PanelOps.hit(listOf(a, inner), 0.15, 0.15))
        assertEquals(-1, PanelOps.hit(listOf(a), 0.9, 0.9))
    }

    @Test fun undoRedoWithLimit() {
        val u = UndoStack<Int>(limit = 2)
        u.push(1); u.push(2); u.push(3)
        assertEquals(3, u.undo(4)); assertEquals(2, u.undo(3))
        assertNull(u.undo(2))
        assertEquals(3, u.redo(2))
        assertTrue(u.canRedo)
        u.push(9)
        assertFalse(u.canRedo)
    }
}
