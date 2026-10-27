package app.gibiflix.core.detect

import app.gibiflix.core.pkg.NRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelDetectorTest {
    private fun center(r: NRect) = r.cx to r.cy

    private fun assertMatches(expected: List<IntArray>, actual: List<NRect>, w: Int, h: Int, tol: Double = 0.02) {
        assertEquals("quantidade de quadros", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            assertEquals("x0 do quadro $i", e[0].toDouble() / w, a.x, tol)
            assertEquals("y0 do quadro $i", e[1].toDouble() / h, a.y, tol)
            assertEquals("x1 do quadro $i", e[2].toDouble() / w, a.right, tol)
            assertEquals("y1 do quadro $i", e[3].toDouble() / h, a.bottom, tol)
        }
    }

    @Test fun grid2x3WhiteGutter() {
        val (s, rects) = Synth.grid(2, 3)
        val r = PanelDetector.detect(s.image())
        assertMatches(rects, r.panels, s.w, s.h)
        assertTrue("confiança alta: ${r.confidence}", r.confidence >= 0.9)
    }

    @Test fun grid2x3BlackGutter() {
        val (s, rects) = Synth.grid(2, 3, bg = 0, frame = 255)
        val r = PanelDetector.detect(s.image())
        assertTrue(r.debug.background < 10)
        assertMatches(rects, r.panels, s.w, s.h)
    }

    @Test fun bigLeftPanelThenTwoRight() {
        val s = Synth(800, 1200, 255)
        s.panel(40, 40, 380, 1160)
        s.panel(404, 40, 760, 588)
        s.panel(404, 612, 760, 1160)
        val r = PanelDetector.detect(s.image())
        assertMatches(listOf(intArrayOf(40, 40, 380, 1160), intArrayOf(404, 40, 760, 588), intArrayOf(404, 612, 760, 1160)), r.panels, 800, 1200)
    }

    @Test fun balloonCrossingGutterDoesNotMergePanels() {
        val (s, rects) = Synth.grid(2, 3)
        // Balão branco com contorno preto sobre a sarjeta entre a linha 1 e a 2, na coluna da esquerda.
        val gy = (rects[0][3] + rects[2][1]) / 2
        s.ellipse(200, gy, 110, 50, 255, 0)
        val r = PanelDetector.detect(s.image())
        assertEquals(6, r.panels.size)
        // Os quadros 0 e 2 continuam separados (cada um termina perto da sarjeta).
        assertTrue(r.panels[0].bottom < gy.toDouble() / s.h + 0.05)
        assertTrue(r.panels[2].y > gy.toDouble() / s.h - 0.05)
    }

    @Test fun bleedPageIsOnePanel() {
        val s = Synth(800, 1200, 128)
        s.noise(0.6, 0)
        s.noise(0.3, 255)
        val r = PanelDetector.detect(s.image())
        assertEquals(1, r.panels.size)
        assertEquals(NRect.FULL, r.panels[0])
        assertEquals(0.8, r.confidence, 1e-9)
    }

    @Test fun mangaRightToLeftOrder() {
        val (s, rects) = Synth.grid(2, 3)
        val r = PanelDetector.detect(s.image(), DetectorParams(readingDirection = "rtl"))
        val expected = listOf(rects[1], rects[0], rects[3], rects[2], rects[5], rects[4])
        assertMatches(expected, r.panels, s.w, s.h)
    }

    @Test fun noiseAndPageNumberAreIgnored() {
        val (s, rects) = Synth.grid(2, 2, margin = 50)
        // Sujeira esparsa e número de página no rodapé.
        s.noise(0.003, 0)
        s.rect(390, 1165, 410, 1190, 0)
        val r = PanelDetector.detect(s.image())
        assertEquals(4, r.panels.size)
        assertMatches(rects, r.panels, s.w, s.h, tol = 0.03)
        assertTrue(r.confidence >= 0.7)
    }

    @Test fun doublePageReadsLeftPageFirst() {
        val s = Synth(1800, 1200, 255)
        val rects = mutableListOf<IntArray>()
        for (half in 0..1) {
            val ox = half * 900
            for (row in 0..1) for (col in 0..1) {
                val x0 = ox + 40 + col * 420; val y0 = 40 + row * 570
                val x1 = x0 + 396; val y1 = y0 + 546
                s.panel(x0, y0, x1, y1)
                rects += intArrayOf(x0, y0, x1, y1)
            }
        }
        val r = PanelDetector.detect(s.image(), DetectorParams(spread = true))
        assertMatches(rects, r.panels, 1800, 1200)
        val rtl = PanelDetector.detect(s.image(), DetectorParams(spread = true, readingDirection = "rtl"))
        assertTrue(rtl.panels[0].x > 0.5)
    }

    @Test fun irregularPanelsUseComponents() {
        val s = Synth(800, 1200, 255)
        // Dois quadros deslocados que não se separam por corte reto.
        s.panel(40, 40, 500, 600)
        s.panel(300, 640, 760, 1160)
        s.panel(530, 40, 760, 610)
        s.panel(40, 630, 270, 1160)
        val r = PanelDetector.detect(s.image())
        assertEquals(4, r.panels.size)
        val (cx0, cy0) = center(r.panels[0])
        assertTrue(cx0 < 0.5 && cy0 < 0.5)
    }

    @Test fun trimAddsMarginAndSkipsWhenTiny() {
        val t = TrimCalculator.compute(listOf(NRect(0.1, 0.1, 0.3, 0.3), NRect(0.5, 0.5, 0.3, 0.3)))
        assertNotNull(t)
        assertEquals(0.09, t!!.x, 1e-9)
        assertEquals(0.81, t.right, 1e-9)
        assertNull(TrimCalculator.compute(listOf(NRect(0.0, 0.0, 1.0, 1.0))))
    }

    @Test fun fromArgbConvertsToGray() {
        val img = GrayImage.fromArgb(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 2, 1)
        assertEquals(255, img[0, 0]); assertEquals(0, img[1, 0])
    }
}
