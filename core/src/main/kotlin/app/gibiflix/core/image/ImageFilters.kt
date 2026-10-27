package app.gibiflix.core.image

import app.gibiflix.core.settings.ImageSettings
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Filtros de exibição sobre pixels ARGB já redimensionados para a tela.
 * Ordem: cor/contraste/meios-tons → cinza (mono) → nitidez → pontilhado.
 * Nunca gravados no pacote.
 */
object ImageFilters {
    private val SHARPEN_AMOUNT = floatArrayOf(0f, 0.45f, 0.9f, 1.5f)

    fun apply(pixels: IntArray, width: Int, height: Int, s: ImageSettings, mono: Boolean): IntArray {
        require(pixels.size == width * height) { "tamanho inconsistente" }
        val grayscale = mono || s.grayscale
        if (s.isIdentity && !grayscale) return pixels
        var out = pixels.copyOf()
        colorStage(out, s, applySaturation = !grayscale)
        if (grayscale) toGray(out)
        val amount = SHARPEN_AMOUNT[s.sharpness.coerceIn(0, 3)]
        if (amount > 0f) out = sharpen(out, width, height, amount, grayscale)
        if (s.dither && grayscale) dither(out, width, height, levels = 16)
        return out
    }

    /** Contraste e brilho (linear em torno de 128), saturação e meios-tons num só passe. */
    fun colorStage(px: IntArray, s: ImageSettings, applySaturation: Boolean) {
        val contrast = (100f + s.contrast) / 100f
        val offset = s.brightness * 1.28f
        val lut = IntArray(256) { v -> clamp(((v - 128) * contrast + 128 + offset).roundToInt()) }
        val sat = if (applySaturation) 1f + s.saturation / 100f else 1f
        val gamma = 1f + s.midtones / 100f
        val midLut = if (s.midtones != 0) FloatArray(256) { l ->
            if (l == 0) 1f else (255f * (l / 255f).toDouble().pow(gamma.toDouble()).toFloat()) / l
        } else null
        val identityLut = s.contrast == 0 && s.brightness == 0
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF
            if (!identityLut) { r = lut[r]; g = lut[g]; b = lut[b] }
            if (sat != 1f) {
                val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = clamp((l + (r - l) * sat).roundToInt())
                g = clamp((l + (g - l) * sat).roundToInt())
                b = clamp((l + (b - l) * sat).roundToInt())
            }
            if (midLut != null) {
                val l = clamp((0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt())
                val k = midLut[l]
                r = clamp((r * k).roundToInt()); g = clamp((g * k).roundToInt()); b = clamp((b * k).roundToInt())
            }
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    fun toGray(px: IntArray) {
        for (i in px.indices) {
            val p = px[i]
            val l = luminance(p)
            px[i] = (p and 0xFF000000.toInt()) or (l shl 16) or (l shl 8) or l
        }
    }

    fun luminance(p: Int): Int =
        clamp((0.2126f * ((p shr 16) and 0xFF) + 0.7152f * ((p shr 8) and 0xFF) + 0.0722f * (p and 0xFF)).roundToInt())

    /** Unsharp mask 3×3 (borrão de caixa). */
    fun sharpen(px: IntArray, w: Int, h: Int, amount: Float, gray: Boolean): IntArray {
        if (w < 3 || h < 3) return px
        val out = px.copyOf()
        val channels = if (gray) intArrayOf(0) else intArrayOf(16, 8, 0)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                var result = px[i] and 0xFF000000.toInt()
                for (sh in channels) {
                    var sum = 0
                    for (dy in -1..1) {
                        val r2 = (y + dy) * w
                        sum += (px[r2 + x - 1] shr sh and 0xFF) + (px[r2 + x] shr sh and 0xFF) + (px[r2 + x + 1] shr sh and 0xFF)
                    }
                    val v = px[i] shr sh and 0xFF
                    val blurred = sum / 9f
                    val nv = clamp((v + amount * (v - blurred)).roundToInt())
                    result = if (gray) result or (nv shl 16) or (nv shl 8) or nv else result or (nv shl sh)
                }
                out[i] = result
            }
        }
        return out
    }

    /** Floyd–Steinberg para [levels] níveis de cinza (entrada já em cinza). */
    fun dither(px: IntArray, w: Int, h: Int, levels: Int) {
        val err = FloatArray(px.size) { (px[it] and 0xFF).toFloat() }
        val step = 255f / (levels - 1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = err[i].coerceIn(0f, 255f)
                val q = (old / step).roundToInt() * step
                val e = old - q
                val v = clamp(q.roundToInt())
                px[i] = (px[i] and 0xFF000000.toInt()) or (v shl 16) or (v shl 8) or v
                if (x + 1 < w) err[i + 1] += e * 7 / 16
                if (y + 1 < h) {
                    if (x > 0) err[i + w - 1] += e * 3 / 16
                    err[i + w] += e * 5 / 16
                    if (x + 1 < w) err[i + w + 1] += e * 1 / 16
                }
            }
        }
    }

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
}
