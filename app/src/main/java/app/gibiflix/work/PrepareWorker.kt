package app.gibiflix.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.detect.DetectorParams
import app.gibiflix.core.detect.GrayImage
import app.gibiflix.core.detect.PanelDetector
import app.gibiflix.core.detect.TrimCalculator
import app.gibiflix.core.pkg.DetectorInfo
import app.gibiflix.core.pkg.GibiflixDoc
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.core.pkg.Panel
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.ocr.OcrEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Preparação (§4): página dupla, quadros, margens, confiança e OCR opcional, página a página.
 * Grava o gibiflix.json de forma atômica; guarda um ponto de retomada para continuar se o app fechar.
 */
class PrepareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @Serializable
    private data class Checkpoint(val redoAll: Boolean, val done: List<Int>)

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("", 0, 0)

    private fun foreground(title: String, done: Int, total: Int): ForegroundInfo {
        val ctx = applicationContext
        val n = Notifications.progress(
            ctx, Notifications.CHANNEL_WORK, ctx.getString(R.string.prepare_notification_title),
            if (total > 0) ctx.getString(R.string.prepare_notification_progress, title, done, total) else title,
            done, total,
        )
        return ForegroundInfo(Notifications.ID_PREPARE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val redoAll = inputData.getBoolean(KEY_REDO_ALL, false)
        val onlyPage = inputData.getInt(KEY_ONLY_PAGE, -1)
        val tolerance = inputData.getInt(KEY_TOLERANCE, -1)
        val comic = c.db.comics().get(id) ?: return Result.success()
        val parsed = c.packages.load(id) ?: return Result.failure()
        val user = c.settings.current().user
        val profile = c.deviceProfile.profile
        val tol = if (tolerance > 0) tolerance else user.prepare.tolerance
        val ocr = if (user.ocrEnabled(profile) && onlyPage < 0) OcrEngine.create(applicationContext) else null

        val cpFile = File(applicationContext.filesDir, "prepare/$id.json")
        val cp = runCatching { Json.decodeFromString(Checkpoint.serializer(), cpFile.readText()) }.getOrNull()
            ?.takeIf { it.redoAll == redoAll && onlyPage < 0 }
        val done = cp?.done?.toMutableSet() ?: mutableSetOf()

        var doc = parsed.doc
        val pages = doc.pages.toMutableList()
        val targets = pages.indices.filter { i ->
            when {
                onlyPage >= 0 -> i == onlyPage
                i in done -> false
                redoAll -> true
                else -> !pages[i].reviewed
            }
        }
        val title = comic.title
        if (onlyPage < 0) c.db.comics().setStatus(id, PrepStatus.PREPARING, percent(done.size, pages.size))
        runCatching { setForeground(foreground(title, done.size, pages.size)) }

        try {
            ZipFile(c.files.packageFile(id)).use { zip ->
                targets.forEachIndexed { k, i ->
                    if (isStopped) return Result.retry()
                    val page = pages[i]
                    val bmp = decodeForDetection(zip, page.file) ?: return@forEachIndexed
                    val w = bmp.width; val h = bmp.height
                    val argb = IntArray(w * h)
                    bmp.getPixels(argb, 0, w, 0, 0, w, h)
                    bmp.recycle()
                    val spread = page.width.toDouble() / page.height > 1.2
                    val result = PanelDetector.detect(
                        GrayImage.fromArgb(argb, w, h),
                        DetectorParams(tolerance = tol, readingDirection = page.direction(doc.readingDirection), spread = spread),
                    )
                    var panels = result.panels.map { Panel.of(it) }
                    if (ocr != null && panels.isNotEmpty()) {
                        panels = runCatching { ocr.recognize(zip, page, panels) }.getOrDefault(panels)
                    }
                    pages[i] = page.copy(
                        spread = spread,
                        panels = if (result.panels.size == 1 && result.panels[0] == app.gibiflix.core.pkg.NRect.FULL) emptyList() else panels,
                        trim = TrimCalculator.compute(result.panels),
                        confidence = (result.confidence * 100).roundToInt() / 100.0,
                        reviewed = false,
                    )
                    done += i
                    val total = if (onlyPage >= 0) 1 else pages.size
                    val doneCount = if (onlyPage >= 0) k + 1 else done.size
                    if (onlyPage < 0) {
                        c.db.comics().setStatus(id, PrepStatus.PREPARING, percent(doneCount, total))
                        runCatching { setProgress(workDataOf(KEY_PROGRESS to percent(doneCount, total))) }
                        if (doneCount % 3 == 0) runCatching { setForeground(foreground(title, doneCount, total)) }
                        // Ponto de retomada a cada 10 páginas (grava o pacote e a lista do que já foi feito).
                        if (doneCount % 10 == 0) {
                            doc = c.packages.save(id, doc.copy(pages = pages.toList()), bump = false, keepPreparing = true)
                            cpFile.parentFile?.mkdirs()
                            cpFile.writeText(Json.encodeToString(Checkpoint.serializer(), Checkpoint(redoAll, done.toList())))
                        }
                    }
                }
            }
            val finalDoc = doc.copy(
                pages = pages.toList(),
                detector = DetectorInfo(PanelDetector.NAME, PanelDetector.VERSION),
            )
            c.packages.save(id, finalDoc, bump = true)
            cpFile.delete()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            c.packages.load(id)?.let { c.packages.syncIndex(id, it.doc) }
            return Result.failure()
        } finally {
            ocr?.close()
        }
        return Result.success()
    }

    /** Página reduzida para o lado maior ≤ 1200 px. */
    private fun decodeForDetection(zip: ZipFile, name: String): Bitmap? {
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
        return Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true).also { if (it !== bmp) bmp.recycle() }
    }

    private fun percent(done: Int, total: Int) = if (total == 0) 100 else done * 100 / total

    companion object {
        const val KEY_ID = "id"
        const val KEY_REDO_ALL = "redoAll"
        const val KEY_ONLY_PAGE = "onlyPage"
        const val KEY_TOLERANCE = "tolerance"
        const val KEY_PROGRESS = "progress"
        const val MAX_SIDE = 1200

        fun workName(id: String) = "prepare-$id"

        fun enqueue(context: Context, id: String, redoAll: Boolean = false) {
            val req = OneTimeWorkRequestBuilder<PrepareWorker>()
                .setInputData(workDataOf(KEY_ID to id, KEY_REDO_ALL to redoAll))
                .addTag("prepare")
                .addTag(workName(id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("prepare-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        /** Redetecta uma página só (editor), com tolerância própria. */
        fun enqueuePage(context: Context, id: String, page: Int, tolerance: Int) {
            val req = OneTimeWorkRequestBuilder<PrepareWorker>()
                .setInputData(workDataOf(KEY_ID to id, KEY_ONLY_PAGE to page, KEY_TOLERANCE to tolerance))
                .addTag("prepare-page")
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
