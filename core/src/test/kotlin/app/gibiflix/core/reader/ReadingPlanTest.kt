package app.gibiflix.core.reader

import app.gibiflix.core.pkg.GibiflixDoc
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.core.pkg.Panel
import app.gibiflix.core.settings.ReadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPlanTest {
    private val portrait = Screen(1264, 1680)

    private fun doc(pages: List<PageInfo>, dir: String = "ltr") = GibiflixDoc(
        id = "x", sourceHash = "h", title = "t", readingDirection = dir, createdAt = "", updatedAt = "", pages = pages,
    )

    private fun page(panels: List<NRect> = emptyList(), w: Int = 1000, h: Int = 1500, spread: Boolean = false) =
        PageInfo("p.jpg", w, h, spread = spread, panels = panels.map { Panel.of(it) })

    @Test fun pageWithoutPanelsIsShownWhole() {
        val v = ReadingPlan.viewsForPage(doc(listOf(page())), 0, PlanOptions(), portrait)
        assertEquals(1, v.size); assertEquals(ViewKind.PAGE, v[0].kind)
    }

    @Test fun panelsInOrderWithPageBeforeAndAfter() {
        val d = doc(listOf(page(listOf(NRect(0.0, 0.0, 1.0, 0.5), NRect(0.0, 0.5, 1.0, 0.5)))))
        val v = ReadingPlan.viewsForPage(d, 0, PlanOptions(pageBefore = true, pageAfter = true), portrait)
        assertEquals(listOf(ViewKind.PAGE, ViewKind.PANEL, ViewKind.PANEL, ViewKind.PAGE), v.map { it.kind })
        assertEquals(listOf(-1, 0, 1, -1), v.map { it.panelIndex })
    }

    @Test fun pageModeIgnoresPanels() {
        val d = doc(listOf(page(listOf(NRect(0.0, 0.0, 1.0, 0.5)))))
        assertEquals(1, ReadingPlan.viewsForPage(d, 0, PlanOptions(mode = ReadMode.PAGE), portrait).size)
    }

    @Test fun spreadInPortraitSplitsInReadingOrder() {
        val ltr = ReadingPlan.viewsForPage(doc(listOf(page(w = 2000, h = 1500, spread = true))), 0, PlanOptions(), portrait)
        assertEquals(2, ltr.size); assertTrue(ltr[0].rect.x < ltr[1].rect.x)
        val rtl = ReadingPlan.viewsForPage(doc(listOf(page(w = 2000, h = 1500, spread = true)), "rtl"), 0, PlanOptions(), portrait)
        assertTrue(rtl[0].rect.x > rtl[1].rect.x)
        val land = ReadingPlan.viewsForPage(doc(listOf(page(w = 2000, h = 1500, spread = true))), 0, PlanOptions(), Screen(1680, 1264))
        assertEquals(1, land.size)
    }

    @Test fun veryWidePanelIsSplitWithOverlap() {
        val wide = NRect(0.0, 0.0, 1.0, 0.12)
        val d = doc(listOf(page(listOf(wide), w = 1000, h = 1500)))
        val v = ReadingPlan.viewsForPage(d, 0, PlanOptions(splitWide = true), portrait)
        assertTrue(v.size in 2..3)
        assertTrue(v[1].rect.x < v[0].rect.right) // sobreposição
        assertEquals(1.0, v.last().rect.right, 1e-9)
        val noSplit = ReadingPlan.viewsForPage(d, 0, PlanOptions(splitWide = false), portrait)
        assertEquals(1, noSplit.size)
    }

    @Test fun wideSplitIsReversedForRtl() {
        val d = doc(listOf(page(listOf(NRect(0.0, 0.0, 1.0, 0.12)))), "rtl")
        val v = ReadingPlan.viewsForPage(d, 0, PlanOptions(splitWide = true), portrait)
        assertTrue(v[0].rect.x > v[1].rect.x)
    }

    @Test fun maxZoomCapsSmallPanels() {
        val p = page(listOf(NRect(0.4, 0.4, 0.05, 0.05)))
        val view = ReadingPlan.viewsForPage(doc(listOf(p)), 0, PlanOptions(), portrait)[0]
        val s = ReadingPlan.scaleFor(view, p, portrait, PlanOptions(maxZoom = 3f))
        val pageFit = minOf(1264.0 / 1000, 1680.0 / 1500)
        assertEquals(pageFit * 3, s, 1e-9)
    }

    @Test fun navigatorCrossesPages() {
        val d = doc(listOf(page(listOf(NRect(0.0, 0.0, 1.0, 0.5), NRect(0.0, 0.5, 1.0, 0.5))), page()))
        val nav = Navigator(d, PlanOptions(), portrait)
        var pos = Position(0, 0)
        pos = nav.next(pos)!!; assertEquals(Position(0, 1), pos)
        pos = nav.next(pos)!!; assertEquals(Position(1, 0), pos)
        assertTrue(nav.isLast(pos))
        assertNull(nav.next(pos))
        assertEquals(Position(0, 1), nav.prev(pos))
        assertEquals(Position(0, 1), nav.positionFor(0, 1))
    }
}
