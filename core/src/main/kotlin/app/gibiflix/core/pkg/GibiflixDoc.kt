package app.gibiflix.core.pkg

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Retângulo normalizado (0..1) em relação à página (ou ao quadro, no caso do texto). */
@Serializable
data class NRect(val x: Double, val y: Double, val w: Double, val h: Double) {
    val right: Double get() = x + w
    val bottom: Double get() = y + h
    val area: Double get() = w * h
    val cx: Double get() = x + w / 2
    val cy: Double get() = y + h / 2

    fun clamp(): NRect {
        val nx = x.coerceIn(0.0, 1.0)
        val ny = y.coerceIn(0.0, 1.0)
        return NRect(nx, ny, (right.coerceIn(0.0, 1.0) - nx).coerceAtLeast(0.0), (bottom.coerceIn(0.0, 1.0) - ny).coerceAtLeast(0.0))
    }

    fun union(o: NRect): NRect {
        val l = minOf(x, o.x); val t = minOf(y, o.y)
        return NRect(l, t, maxOf(right, o.right) - l, maxOf(bottom, o.bottom) - t)
    }

    fun intersection(o: NRect): Double {
        val w = minOf(right, o.right) - maxOf(x, o.x)
        val h = minOf(bottom, o.bottom) - maxOf(y, o.y)
        return if (w > 0 && h > 0) w * h else 0.0
    }

    fun contains(px: Double, py: Double) = px in x..right && py in y..bottom

    companion object {
        val FULL = NRect(0.0, 0.0, 1.0, 1.0)
    }
}

@Serializable
data class TextBlock(val x: Double, val y: Double, val w: Double, val h: Double, val value: String)

@Serializable
data class Panel(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val source: String = SOURCE_AUTO,
    val text: List<TextBlock>? = null,
    val translations: Map<String, List<TextBlock>>? = null,
) {
    val rect: NRect get() = NRect(x, y, w, h)

    companion object {
        const val SOURCE_AUTO = "auto"
        const val SOURCE_MANUAL = "manual"
        fun of(r: NRect, source: String = SOURCE_AUTO) = Panel(r.x, r.y, r.w, r.h, source)
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PageInfo(
    val file: String,
    val width: Int,
    val height: Int,
    val spread: Boolean = false,
    val readingDirection: String? = null,
    val trim: NRect? = null,
    val confidence: Double? = null,
    val reviewed: Boolean = false,
    val panels: List<Panel> = emptyList(),
) {
    fun direction(book: String): String = readingDirection ?: book
}

@Serializable
data class CreatedBy(val app: String, val device: String, val profile: String)

@Serializable
data class DetectorInfo(val name: String, val version: Int)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GibiflixDoc(
    @EncodeDefault val format: String = FORMAT,
    @EncodeDefault val version: Int = VERSION,
    val id: String,
    val revision: Int = 0,
    val sourceHash: String,
    val title: String,
    val readingDirection: String = LTR,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: CreatedBy? = null,
    val detector: DetectorInfo? = null,
    val pages: List<PageInfo> = emptyList(),
) {
    val preparedPages: Int get() = pages.count { it.confidence != null || it.reviewed }
    val reviewedPages: Int get() = pages.count { it.reviewed }

    companion object {
        const val FORMAT = "gibiflix"
        const val VERSION = 1
        const val LTR = "ltr"
        const val RTL = "rtl"
        const val JSON_NAME = "gibiflix.json"
        const val COMIC_INFO = "ComicInfo.xml"
    }
}

/** Estado de preparação calculado a partir do documento. */
enum class DocState { NEW, REVIEW, READY }

object DocStats {
    const val REVIEW_THRESHOLD = 0.7

    fun isPrepared(p: PageInfo) = p.reviewed || p.confidence != null

    fun needsReview(p: PageInfo) = !p.reviewed && (p.confidence ?: 1.0) < REVIEW_THRESHOLD

    fun state(doc: GibiflixDoc): DocState = when {
        doc.pages.isEmpty() || doc.pages.none(::isPrepared) -> DocState.NEW
        doc.pages.any { isPrepared(it) && needsReview(it) } -> DocState.REVIEW
        doc.pages.all(::isPrepared) -> DocState.READY
        else -> DocState.NEW
    }
}
