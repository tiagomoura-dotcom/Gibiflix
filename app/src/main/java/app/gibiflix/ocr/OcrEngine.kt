package app.gibiflix.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.core.pkg.Panel
import app.gibiflix.core.pkg.TextBlock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Texto dos balões com o ML Kit empacotado (modelo dentro do app, sem Google Play Services).
 * Caixas gravadas normalizadas em relação ao quadro.
 */
class OcrEngine private constructor(private val recognizer: TextRecognizer) : Closeable {

    fun recognize(zip: ZipFile, page: PageInfo, panels: List<Panel>): List<Panel> {
        val entry = zip.getEntry(page.file) ?: return panels
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return panels
        try {
            return panels.map { p -> p.copy(text = recognizePanel(bmp, p).ifEmpty { null }) }
        } finally {
            bmp.recycle()
        }
    }

    private fun recognizePanel(page: Bitmap, p: Panel): List<TextBlock> {
        val x = (p.x * page.width).roundToInt().coerceIn(0, page.width - 1)
        val y = (p.y * page.height).roundToInt().coerceIn(0, page.height - 1)
        val w = (p.w * page.width).roundToInt().coerceIn(1, page.width - x)
        val h = (p.h * page.height).roundToInt().coerceIn(1, page.height - y)
        if (w < 32 || h < 32) return emptyList()
        val crop = Bitmap.createBitmap(page, x, y, w, h)
        return try {
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)))
            result.textBlocks.mapNotNull { b ->
                val box = b.boundingBox ?: return@mapNotNull null
                val text = b.text.replace(Regex("\\s*\\n\\s*"), " ").trim()
                if (text.length < 2) return@mapNotNull null
                TextBlock(
                    x = round(box.left.toDouble() / w), y = round(box.top.toDouble() / h),
                    w = round(box.width().toDouble() / w), h = round(box.height().toDouble() / h),
                    value = text,
                )
            }
        } catch (e: Exception) {
            Log.w("Gibiflix", "OCR falhou: ${e.message}")
            emptyList()
        } finally {
            if (crop !== page) crop.recycle()
        }
    }

    private fun round(v: Double) = (v.coerceIn(0.0, 1.0) * 1000).roundToInt() / 1000.0

    override fun close() = recognizer.close()

    companion object {
        fun create(context: Context): OcrEngine? = try {
            OcrEngine(TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS))
        } catch (t: Throwable) {
            Log.w("Gibiflix", "OCR indisponível", t)
            null
        }
    }
}
