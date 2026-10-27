package app.gibiflix.core.meta

import kotlinx.serialization.Serializable
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

@Serializable
data class ComicMeta(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: Int? = null,
    val year: Int? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val summary: String? = null,
    val language: String? = null,
    /** "ltr", "rtl" ou null (desconhecido). */
    val readingDirection: String? = null,
) {
    /** Preenche o que falta com outro conjunto de metadados. */
    fun orElse(other: ComicMeta) = ComicMeta(
        title = title ?: other.title,
        series = series ?: other.series,
        number = number ?: other.number,
        volume = volume ?: other.volume,
        year = year ?: other.year,
        writer = writer ?: other.writer,
        penciller = penciller ?: other.penciller,
        summary = summary ?: other.summary,
        language = language ?: other.language,
        readingDirection = readingDirection ?: other.readingDirection,
    )

    /** Número como Double para ordenar ("12.5" → 12.5; "Anual 1" → null). */
    val numberSort: Double? get() = number?.replace(',', '.')?.let { Regex("""\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }
}

object ComicInfoParser {
    fun parse(bytes: ByteArray): ComicMeta? = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val root = doc.documentElement
        fun text(tag: String): String? {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                if (el.parentNode == root) return el.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
            return null
        }
        val manga = text("Manga")
        ComicMeta(
            title = text("Title"),
            series = text("Series"),
            number = text("Number"),
            volume = text("Volume")?.toIntOrNull(),
            year = text("Year")?.toIntOrNull()?.takeIf { it > 0 },
            writer = text("Writer"),
            penciller = text("Penciller"),
            summary = text("Summary"),
            language = text("LanguageISO"),
            readingDirection = when {
                manga.equals("YesAndRightToLeft", ignoreCase = true) -> "rtl"
                manga != null -> "ltr"
                else -> null
            },
        )
    } catch (e: Exception) {
        null
    }
}

/** Heurística para nomes de arquivo sem ComicInfo. */
object FilenameHeuristic {
    private val EXT = Regex("""\.(cbz|cbr|cb7|zip|rar|7z|pdf)$""", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("""[(\[]((?:19|20)\d{2})[)\]]""")
    private val BRACKETS = Regex("""\s*[(\[][^)\]]*[)\]]""")

    fun parse(fileName: String): ComicMeta {
        var name = fileName.substringAfterLast('/').replace(EXT, "")
        val year = YEAR.find(name)?.groupValues?.get(1)?.toIntOrNull()
        name = name.replace(BRACKETS, "").replace('_', ' ').replace(Regex("""\s+"""), " ").trim()

        // "Série - Título" quando a primeira parte termina em número fica para os padrões abaixo.
        var title: String? = null
        val dash = Regex("""^(.+?)\s+[-–—]\s+(.+)$""").find(name)
        if (dash != null) {
            val left = dash.groupValues[1].trim()
            val right = dash.groupValues[2].trim()
            if (Regex("""\d""").containsMatchIn(left) || !Regex("""\d""").containsMatchIn(right)) {
                name = left
                title = right
            }
        }

        var series: String? = null
        var number: String? = null
        var volume: Int? = null

        Regex("""^(.*?)\s+(?:v|vol\.?|volume)\s*(\d+)\s*(.*)$""", RegexOption.IGNORE_CASE).find(name)?.let { m ->
            series = m.groupValues[1].trim()
            volume = m.groupValues[2].toInt()
            val rest = m.groupValues[3].trim()
            Regex("""^(?:#|n\.?|ch\.?|cap\.?)?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(rest)?.let { number = normalizeNumber(it.groupValues[1]) }
        }
        if (series == null) Regex("""^(.*?)\s*#\s*(\d+(?:[.,]\d+)?)\s*(.*)$""").find(name)?.let { m ->
            series = m.groupValues[1].trim()
            number = normalizeNumber(m.groupValues[2])
            if (title == null) title = m.groupValues[3].trim().trimStart('-', ' ').takeIf { it.isNotEmpty() }
        }
        if (series == null) Regex("""^(.*?\D)\s*(\d{1,4}(?:[.,]\d+)?)$""").find(name)?.let { m ->
            val s = m.groupValues[1].trim().trimEnd('-', '.', ' ')
            if (s.isNotEmpty()) {
                series = s
                number = normalizeNumber(m.groupValues[2])
            }
        }
        if (series == null && title != null) {
            series = name
        }
        val finalTitle = title ?: if (series == null) name else null
        return ComicMeta(
            title = finalTitle?.takeIf { it.isNotEmpty() },
            series = series?.takeIf { it.isNotEmpty() },
            number = number,
            volume = volume,
            year = year,
        )
    }

    private fun normalizeNumber(n: String): String {
        val s = n.replace(',', '.')
        val parts = s.split('.')
        val intPart = parts[0].trimStart('0').ifEmpty { "0" }
        return if (parts.size > 1) "$intPart.${parts[1]}" else intPart
    }

    /** Título para exibir: "Série #12 — Título" ou o que existir. */
    fun displayTitle(meta: ComicMeta, fallback: String): String {
        val base = meta.series?.let { s -> if (meta.number != null) "$s #${meta.number}" else if (meta.volume != null) "$s v${meta.volume}" else s }
        return when {
            meta.title != null && base != null && meta.title != meta.series -> "$base — ${meta.title}"
            meta.title != null -> meta.title
            base != null -> base
            else -> fallback
        }
    }
}
