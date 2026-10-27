package app.gibiflix.core.detect

import kotlin.random.Random

/** Gera páginas sintéticas para os testes (sem binários no repositório). */
class Synth(val w: Int, val h: Int, bg: Int) {
    val px = ByteArray(w * h) { bg.toByte() }
    private val rnd = Random(42)

    fun set(x: Int, y: Int, v: Int) { if (x in 0 until w && y in 0 until h) px[y * w + x] = v.toByte() }

    /** Quadro com moldura e miolo com "desenho" (ruído) e uma área clara (céu) dentro. */
    fun panel(x0: Int, y0: Int, x1: Int, y1: Int, frame: Int = 0, frameWidth: Int = 3) {
        for (y in y0 until y1) for (x in x0 until x1) {
            val edge = x < x0 + frameWidth || x >= x1 - frameWidth || y < y0 + frameWidth || y >= y1 - frameWidth
            val v = when {
                edge -> frame
                y < y0 + (y1 - y0) / 3 -> 250 // céu claro, mas cercado pela moldura
                else -> 60 + rnd.nextInt(140)
            }
            set(x, y, v)
        }
    }

    fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, fill: Int, outline: Int) {
        for (y in cy - ry - 2..cy + ry + 2) for (x in cx - rx - 2..cx + rx + 2) {
            val dx = (x - cx).toDouble() / rx; val dy = (y - cy).toDouble() / ry
            val d = dx * dx + dy * dy
            if (d <= 1.0) set(x, y, fill)
            if (d in 0.85..1.15) set(x, y, outline)
        }
    }

    fun noise(density: Double, value: Int) {
        for (i in px.indices) if (rnd.nextDouble() < density) px[i] = value.toByte()
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, v: Int) { for (y in y0 until y1) for (x in x0 until x1) set(x, y, v) }

    fun image() = GrayImage(w, h, px)

    companion object {
        /** Grade cols×rows com sarjeta [gutter] e margem [margin]; devolve a página e os retângulos. */
        fun grid(cols: Int, rows: Int, w: Int = 800, h: Int = 1200, gutter: Int = 24, margin: Int = 40, bg: Int = 255, frame: Int = 0): Pair<Synth, List<IntArray>> {
            val s = Synth(w, h, bg)
            val cw = (w - 2 * margin - (cols - 1) * gutter) / cols
            val rh = (h - 2 * margin - (rows - 1) * gutter) / rows
            val rects = mutableListOf<IntArray>()
            for (r in 0 until rows) for (c in 0 until cols) {
                val x0 = margin + c * (cw + gutter); val y0 = margin + r * (rh + gutter)
                s.panel(x0, y0, x0 + cw, y0 + rh, frame)
                rects += intArrayOf(x0, y0, x0 + cw, y0 + rh)
            }
            return s to rects
        }
    }
}
