package app.gibiflix.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import app.gibiflix.BuildConfig
import app.gibiflix.core.meta.ComicInfoParser
import app.gibiflix.core.meta.ComicMeta
import app.gibiflix.core.meta.FilenameHeuristic
import app.gibiflix.core.pkg.CreatedBy
import app.gibiflix.core.pkg.GibiflixDoc
import app.gibiflix.core.pkg.GibiflixJson
import app.gibiflix.core.pkg.PackageIo
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.core.pkg.ReadResult
import app.gibiflix.core.util.ArchiveNames
import app.gibiflix.data.db.ComicDao
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.data.files.LibraryFiles
import app.gibiflix.data.repo.PackageStore
import app.gibiflix.device.DeviceProfileManager
import app.gibiflix.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface ImportOutcome {
    val name: String
    data class Imported(override val name: String, val comic: ComicEntity) : ImportOutcome
    data class Duplicate(override val name: String, val existing: ComicEntity) : ImportOutcome
    data class Failed(override val name: String, val reason: String) : ImportOutcome
}

/** Converte qualquer entrada (CBZ, CBR, CB7, PDF ou pacote Gibiflix) para o pacote e indexa no banco. */
class ComicImporter(
    private val context: Context,
    private val files: LibraryFiles,
    private val comics: ComicDao,
    private val store: PackageStore,
    private val settings: SettingsRepository,
    private val deviceProfile: DeviceProfileManager,
) {
    fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).name
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "quadrinho"
    }

    suspend fun import(uri: Uri, name: String = displayName(uri), onProgress: (Float) -> Unit = {}): ImportOutcome =
        withContext(Dispatchers.IO) {
            val work = files.newWorkDir()
            try {
                val source = File(work, "source")
                val hash = copyAndHash(uri, source)
                importCopied(source, name, hash, work, onProgress)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ImportOutcome.Failed(name, e.message ?: e.javaClass.simpleName)
            } finally {
                work.deleteRecursively()
            }
        }

    /** Importa um arquivo local (ex.: download do OPDS ou de outro aparelho). */
    suspend fun importFile(file: File, name: String, onProgress: (Float) -> Unit = {}): ImportOutcome =
        withContext(Dispatchers.IO) {
            val work = files.newWorkDir()
            try {
                val hash = app.gibiflix.core.pkg.Hashing.sha256(file)
                importCopied(file, name, hash, work, onProgress)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ImportOutcome.Failed(name, e.message ?: e.javaClass.simpleName)
            } finally {
                work.deleteRecursively()
            }
        }

    private fun copyAndHash(uri: Uri, out: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("não abriu")
        DigestInputStream(input.buffered(256 * 1024), md).use { din ->
            out.outputStream().buffered(256 * 1024).use { din.copyTo(it, 256 * 1024) }
        }
        return "sha256:" + md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun importCopied(
        source: File,
        name: String,
        hash: String,
        work: File,
        onProgress: (Float) -> Unit,
    ): ImportOutcome {
        comics.byHash(hash)?.let { return ImportOutcome.Duplicate(name, it) }
        val kind = SourceSniffer.sniff(source, name)

        if (kind == SourceKind.ZIP) {
            importExistingPackage(source, name, hash)?.let { return it }
        }

        val pagesDir = File(work, "pages").apply { mkdirs() }
        val extracted: Extracted = when (kind) {
            SourceKind.ZIP -> ArchiveExtractor.extractZip(source, pagesDir) { onProgress(it * 0.5f) }
            SourceKind.RAR, SourceKind.SEVEN_ZIP -> ArchiveExtractor.extractWithLibarchive(source, pagesDir) { onProgress(it * 0.5f) }
            SourceKind.PDF -> renderPdf(source, pagesDir) { onProgress(it * 0.7f) }
            SourceKind.UNKNOWN -> return ImportOutcome.Failed(name, "formato não reconhecido")
        }
        coroutineContext.ensureActive()

        // Normaliza páginas: confere se decodifica, converte formatos pouco suportados para JPEG.
        val pageFiles = mutableListOf<PackageIo.PageFile>()
        val pageInfos = mutableListOf<PageInfo>()
        extracted.pages.forEachIndexed { i, f ->
            coroutineContext.ensureActive()
            val normalized = normalizePage(f) ?: return@forEachIndexed
            val (file, w, h) = normalized
            val pkgName = ArchiveNames.packageName(pageFiles.size, ArchiveNames.extension(file.name))
            pageFiles += PackageIo.PageFile(pkgName, file)
            pageInfos += PageInfo(file = pkgName, width = w, height = h, spread = w.toDouble() / h > 1.2)
            onProgress(0.5f + 0.4f * (i + 1) / extracted.pages.size)
        }
        if (pageFiles.isEmpty()) return ImportOutcome.Failed(name, "nenhuma página de imagem encontrada")

        val meta = (extracted.comicInfo?.let { ComicInfoParser.parse(it) } ?: ComicMeta())
            .orElse(FilenameHeuristic.parse(name))
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val direction = meta.readingDirection ?: settings.current().user.reader.defaultDirection
        val title = meta.title ?: meta.series ?: name.substringBeforeLast('.')
        val doc = GibiflixDoc(
            id = id,
            revision = 0,
            sourceHash = hash,
            title = title,
            readingDirection = direction,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy(),
            pages = pageInfos,
        )
        val target = files.packageFile(id)
        PackageIo.write(target, pageFiles, GibiflixJson.write(doc), extracted.comicInfo)
        makeCover(pageFiles.first().file, files.coverFile(id))
        onProgress(1f)
        val entity = entityFor(id, hash, doc, meta, target.length())
        comics.insert(entity)
        return ImportOutcome.Imported(name, entity)
    }

    /** Um .cbz que já é pacote Gibiflix (vindo de outro aparelho ou de uma pasta) entra como está. */
    private suspend fun importExistingPackage(source: File, name: String, fileHash: String): ImportOutcome? {
        val text = runCatching { PackageIo.readJson(source) }.getOrNull() ?: return null
        val parsed = (GibiflixJson.read(text) as? ReadResult.Ok)?.parsed ?: return null
        val doc = parsed.doc
        val existing = comics.get(doc.id)
        if (existing != null) {
            // Mesmo quadrinho com revisão mais nova (veio de outro aparelho): substitui o pacote.
            if (doc.revision <= existing.revision) return ImportOutcome.Duplicate(name, existing)
            replacePackage(source, doc.id)
            store.invalidate(doc.id)
            store.syncIndex(doc.id, doc)
            return ImportOutcome.Imported(name, comics.get(doc.id) ?: existing)
        }
        comics.byHash(doc.sourceHash)?.let { return ImportOutcome.Duplicate(name, it) }
        if (fileHash.isNotEmpty()) comics.byHash(fileHash)?.let { return ImportOutcome.Duplicate(name, it) }
        val target = files.packageFile(doc.id)
        replacePackage(source, doc.id)
        val meta = (PackageIo.readComicInfo(target)?.let { ComicInfoParser.parse(it) } ?: ComicMeta())
            .orElse(FilenameHeuristic.parse(name))
        val first = doc.pages.firstOrNull()?.file
        if (first != null) {
            val tmp = File(files.newWorkDir(), "cover")
            tmp.writeBytes(PackageIo.readEntry(target, first))
            makeCover(tmp, files.coverFile(doc.id))
            tmp.parentFile?.deleteRecursively()
        }
        val entity = entityFor(doc.id, doc.sourceHash, doc, meta.copy(title = meta.title ?: doc.title), target.length())
        comics.insert(entity)
        store.syncIndex(doc.id, doc)
        return ImportOutcome.Imported(name, comics.get(doc.id) ?: entity)
    }

    private fun replacePackage(source: File, id: String) {
        PackageIo.atomicWrite(files.packageFile(id)) { out -> source.inputStream().use { it.copyTo(out) } }
    }

    private fun entityFor(id: String, hash: String, doc: GibiflixDoc, meta: ComicMeta, size: Long) = ComicEntity(
        id = id,
        sourceHash = hash,
        title = doc.title,
        series = meta.series,
        number = meta.number,
        numberSort = meta.numberSort,
        volume = meta.volume,
        year = meta.year,
        writer = meta.writer,
        penciller = meta.penciller,
        summary = meta.summary,
        language = meta.language,
        readingDirection = doc.readingDirection,
        pageCount = doc.pages.size,
        preparedPages = doc.preparedPages,
        reviewedPages = doc.reviewedPages,
        revision = doc.revision,
        status = PrepStatus.NEW,
        sizeBytes = size,
    )

    suspend fun createdBy(): CreatedBy = CreatedBy(
        app = BuildConfig.VERSION_NAME,
        device = settings.deviceName(),
        profile = deviceProfile.profile.name,
    )

    private data class Normalized(val file: File, val width: Int, val height: Int)

    private fun normalizePage(f: File): Normalized? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        val ext = ArchiveNames.extension(f.name)
        val keep = ext in setOf("jpg", "jpeg", "png", "webp")
        if (opts.outWidth > 0 && opts.outHeight > 0 && keep) return Normalized(f, opts.outWidth, opts.outHeight)
        // AVIF, GIF, BMP ou extensão enganosa: decodifica e regrava em JPEG.
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        val out = File(f.parentFile, f.nameWithoutExtension + "_n.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val r = Normalized(out, bmp.width, bmp.height)
        bmp.recycle()
        return r
    }

    private suspend fun renderPdf(source: File, outDir: File, onProgress: (Float) -> Unit): Extracted {
        val longSide = settings.current().user.prepare.pdfLongSide.coerceIn(1000, 5000)
        val pages = mutableListOf<File>()
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    coroutineContext.ensureActive()
                    renderer.openPage(i).use { page ->
                        val scale = longSide.toFloat() / max(page.width, page.height)
                        val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = File(outDir, "%05d.jpg".format(i))
                        out.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        bmp.recycle()
                        pages += out
                    }
                    onProgress((i + 1f) / renderer.pageCount)
                }
            }
        }
        return Extracted(pages, null)
    }

    companion object {
        /** Capa em WebP com ~400 px de largura. */
        fun makeCover(page: File, out: File) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(page.absolutePath, bounds)
            if (bounds.outWidth <= 0) return
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 400) sample *= 2
            val bmp = BitmapFactory.decodeFile(page.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            // Página dupla: a capa é a metade direita (a frente do livro em quadrinhos ocidentais).
            val src = if (bmp.width > bmp.height * 1.2f) Bitmap.createBitmap(bmp, bmp.width / 2, 0, bmp.width / 2, bmp.height) else bmp
            val scale = 400f / src.width
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(src, 400, (src.height * scale).roundToInt().coerceAtLeast(1), true) else src
            out.parentFile?.mkdirs()
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
            if (scaled !== src) scaled.recycle()
            if (src !== bmp) src.recycle()
            bmp.recycle()
        }
    }
}
