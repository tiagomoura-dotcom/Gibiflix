package app.gibiflix.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import app.gibiflix.core.image.ImageFilters
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.settings.ImageSettings
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodifica só a região da vista (BitmapRegionDecoder + inSampleSize), redimensiona para o tamanho
 * exato da tela e aplica os filtros do perfil.
 */
class PageRenderer {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun cached(key: String): Bitmap? = memory.get(key)

    fun put(key: String, bmp: Bitmap) { memory.put(key, bmp) }

    fun clear() = memory.evictAll()

    @Suppress("DEPRECATION")
    private fun decoder(file: File): BitmapRegionDecoder =
        if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(file.absolutePath)
        else BitmapRegionDecoder.newInstance(file.absolutePath, false)!!

    /**
     * Renderiza [rect] da página em [scale] (pixels de tela por pixel da página), com filtros.
     */
    fun render(file: File, rect: NRect, scale: Double, filters: ImageSettings, mono: Boolean): Bitmap {
        val dec = decoder(file)
        try {
            val pw = dec.width; val ph = dec.height
            val left = (rect.x * pw).roundToInt().coerceIn(0, pw - 1)
            val top = (rect.y * ph).roundToInt().coerceIn(0, ph - 1)
            val right = (rect.right * pw).roundToInt().coerceIn(left + 1, pw)
            val bottom = (rect.bottom * ph).roundToInt().coerceIn(top + 1, ph)
            val region = Rect(left, top, right, bottom)
            val tw = max(1, (region.width() * scale).roundToInt())
            val th = max(1, (region.height() * scale).roundToInt())
            var sample = 1
            while (region.width() / (sample * 2) >= tw && region.height() / (sample * 2) >= th) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = dec.decodeRegion(region, opts) ?: throw IllegalStateException("falha ao decodificar")
            val scaled = if (decoded.width == tw && decoded.height == th) decoded
            else Bitmap.createScaledBitmap(decoded, tw, th, true).also { if (it !== decoded) decoded.recycle() }
            return applyFilters(scaled, filters, mono)
        } finally {
            dec.recycle()
        }
    }

    /** Página inteira reduzida (para transições, miniaturas e prévia). */
    fun renderWhole(file: File, maxW: Int, maxH: Int, filters: ImageSettings? = null, mono: Boolean = false): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val s = min(maxW.toDouble() / bounds.outWidth, maxH.toDouble() / bounds.outHeight).coerceAtMost(1.0)
        val tw = max(1, (bounds.outWidth * s).roundToInt())
        val th = max(1, (bounds.outHeight * s).roundToInt())
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= tw && bounds.outHeight / (sample * 2) >= th) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("falha ao decodificar")
        val scaled = if (decoded.width == tw && decoded.height == th) decoded
        else Bitmap.createScaledBitmap(decoded, tw, th, true).also { if (it !== decoded) decoded.recycle() }
        return if (filters != null) applyFilters(scaled, filters, mono) else scaled
    }

    fun applyFilters(bmp: Bitmap, filters: ImageSettings, mono: Boolean): Bitmap {
        if (filters.isIdentity && !mono) return bmp
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = ImageFilters.apply(px, w, h, filters, mono)
        val target = if (bmp.isMutable && bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, true).also { bmp.recycle() }
        target.setPixels(out, 0, w, 0, 0, w, h)
        return target
    }
}
