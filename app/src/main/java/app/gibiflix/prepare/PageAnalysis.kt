package app.gibiflix.prepare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.gibiflix.core.detect.DetectorParams
import app.gibiflix.core.detect.GrayImage
import app.gibiflix.core.detect.PanelDetector
import app.gibiflix.core.detect.TrimCalculator
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.core.pkg.Panel
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.roundToInt

/** Detecção de uma página do pacote (usada na preparação e no "Redetectar" do editor). */
object PageAnalysis {
    const val MAX_SIDE = 1200

    fun analyze(zip: ZipFile, page: PageInfo, bookDirection: String, tolerance: Int): PageInfo? {
        val bmp = decodeForDetection(zip, page.file) ?: return null
        val w = bmp.width; val h = bmp.height
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        bmp.recycle()
        val spread = page.width.toDouble() / page.height > 1.2
        val result = PanelDetector.detect(
            GrayImage.fromArgb(argb, w, h),
            DetectorParams(tolerance = tolerance, readingDirection = page.direction(bookDirection), spread = spread),
        )
        val whole = result.panels.size == 1 && result.panels[0] == NRect.FULL
        return page.copy(
            spread = spread,
            panels = if (whole) emptyList() else result.panels.map { Panel.of(it) },
            trim = if (whole) null else TrimCalculator.compute(result.panels),
            confidence = (result.confidence * 100).roundToInt() / 100.0,
            reviewed = false,
        )
    }

    /** Página reduzida para o lado maior ≤ 1200 px. */
    fun decodeForDetection(zip: ZipFile, name: String): Bitmap? {
        val entry = zip.getEntry(name) ?: return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val long = max(bmp.width, bmp.height)
        if (long <= MAX_SIDE) return bmp
        val s = MAX_SIDE.toFloat() / long
        return Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true)
            .also { if (it !== bmp) bmp.recycle() }
    }
}
