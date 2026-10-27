package app.gibiflix.core.detect

/** Imagem em tons de cinza (0..255, sem sinal) usada pela detecção. */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init { require(pixels.size == width * height) }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF
    fun at(i: Int): Int = pixels[i].toInt() and 0xFF

    companion object {
        /** Converte ARGB para cinza (Rec. 709). */
        fun fromArgb(argb: IntArray, width: Int, height: Int): GrayImage {
            val out = ByteArray(width * height)
            for (i in out.indices) {
                val p = argb[i]
                val l = (0.2126f * ((p shr 16) and 0xFF) + 0.7152f * ((p shr 8) and 0xFF) + 0.0722f * (p and 0xFF)).toInt()
                out[i] = l.coerceIn(0, 255).toByte()
            }
            return GrayImage(width, height, out)
        }
    }
}
