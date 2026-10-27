package app.gibiflix.core.image

import app.gibiflix.core.settings.ImageSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFiltersTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    @Test fun identityKeepsPixels() {
        val px = IntArray(16) { rgb(it * 10, 50, 200) }
        assertArrayEquals(px, ImageFilters.apply(px, 4, 4, ImageSettings(), mono = false))
    }

    @Test fun contrastSpreadsValues() {
        val px = intArrayOf(rgb(100, 100, 100), rgb(160, 160, 160))
        val out = ImageFilters.apply(px, 2, 1, ImageSettings(contrast = 50), false)
        assertTrue(r(out[0]) < 100 && r(out[1]) > 160)
    }

    @Test fun brightnessShifts() {
        val out = ImageFilters.apply(intArrayOf(rgb(100, 100, 100)), 1, 1, ImageSettings(brightness = 20), false)
        assertTrue(r(out[0]) > 100)
    }

    @Test fun saturationIncreasesChroma() {
        val px = intArrayOf(rgb(150, 100, 100))
        val out = ImageFilters.apply(px, 1, 1, ImageSettings(saturation = 50), false)
        assertTrue(r(out[0]) - g(out[0]) > 50)
    }

    @Test fun monoIsGrayAndIgnoresSaturation() {
        val px = IntArray(9) { rgb(200, 30, 90) }
        val out = ImageFilters.apply(px, 3, 3, ImageSettings(saturation = 100), mono = true)
        out.forEach { assertEquals(r(it), g(it)); assertEquals(g(it), b(it)) }
        assertEquals(ImageFilters.luminance(px[0]), r(out[0]))
    }

    @Test fun midtonesDarkenMiddleGrayButKeepWhite() {
        val px = intArrayOf(rgb(128, 128, 128), rgb(255, 255, 255))
        val out = ImageFilters.apply(px, 2, 1, ImageSettings(midtones = 40), false)
        assertTrue(r(out[0]) < 110)
        assertEquals(255, r(out[1]))
    }

    @Test fun sharpenIncreasesEdgeContrast() {
        val w = 6; val h = 3
        val px = IntArray(w * h) { i -> if (i % w < 3) rgb(100, 100, 100) else rgb(150, 150, 150) }
        val out = ImageFilters.apply(px, w, h, ImageSettings(sharpness = 3), false)
        val left = r(out[w + 2]); val right = r(out[w + 3])
        assertTrue(right - left > 50)
    }

    @Test fun ditherUsesSixteenLevels() {
        val w = 32; val h = 8
        val px = IntArray(w * h) { i -> val v = (i % w) * 8; rgb(v, v, v) }
        val out = ImageFilters.apply(px, w, h, ImageSettings(dither = true), mono = true)
        val levels = out.map { r(it) }.toSet()
        assertTrue(levels.size <= 16)
        assertTrue(levels.all { it % 17 == 0 })
    }
}
