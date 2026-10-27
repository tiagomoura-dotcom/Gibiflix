package app.gibiflix.core.opds

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

data class OpdsLink(val href: String, val rel: String? = null, val type: String? = null, val title: String? = null) {
    val isAcquisition: Boolean get() = rel?.startsWith("http://opds-spec.org/acquisition") == true
    val isImage: Boolean get() = rel == "http://opds-spec.org/image" || rel == "http://opds-spec.org/cover"
    val isThumbnail: Boolean get() = rel == "http://opds-spec.org/image/thumbnail" || rel == "http://opds-spec.org/thumbnail"
    val isNavigation: Boolean
        get() = type?.contains("atom+xml") == true && !isAcquisition && rel != "self" && rel != "search" && rel != "up" && rel != "start"
}

data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val authors: List<String> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
) {
    /** Link para outro feed (pasta, série, biblioteca). */
    val navigation: OpdsLink? get() = links.firstOrNull { it.isNavigation && (it.rel == null || it.rel == "subsection" || it.rel.startsWith("http://opds-spec.org/sort") || it.rel == "alternate" || it.rel.startsWith("http")) }
        ?: links.firstOrNull { it.isNavigation }

    /** Link de download, preferindo CBZ/CBR/PDF. */
    val acquisition: OpdsLink?
        get() {
            val acq = links.filter { it.isAcquisition }
            return PREFERRED.firstNotNullOfOrNull { p -> acq.firstOrNull { it.type?.contains(p) == true } } ?: acq.firstOrNull()
        }

    val cover: OpdsLink? get() = links.firstOrNull { it.isThumbnail } ?: links.firstOrNull { it.isImage }

    companion object {
        private val PREFERRED = listOf("comicbook+zip", "x-cbz", "zip", "x-cbr", "comicbook-rar", "rar", "x-cb7", "7z", "pdf")
    }
}

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val links: List<OpdsLink>,
) {
    val next: OpdsLink? get() = links.firstOrNull { it.rel == "next" }
    val searchLink: OpdsLink? get() = links.firstOrNull { it.rel == "search" }
}

object OpdsParser {
    private const val ATOM = "http://www.w3.org/2005/Atom"

    private fun newBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder()

    /** Lê um feed OPDS 1.2 (Atom). Links ficam absolutos em relação a [baseUrl]. */
    fun parseFeed(xml: ByteArray, baseUrl: String): OpdsFeed {
        val doc = newBuilder().parse(ByteArrayInputStream(xml))
        val root = doc.documentElement
        val entries = children(root, "entry").map { e ->
            OpdsEntry(
                id = text(e, "id") ?: text(e, "title").orEmpty(),
                title = text(e, "title").orEmpty(),
                summary = text(e, "summary") ?: text(e, "content"),
                authors = children(e, "author").mapNotNull { text(it, "name") },
                links = links(e, baseUrl),
            )
        }
        return OpdsFeed(text(root, "title").orEmpty(), entries, links(root, baseUrl))
    }

    /** OpenSearch: devolve o template de busca do tipo Atom ("...{searchTerms}..."). */
    fun parseOpenSearch(xml: ByteArray, baseUrl: String): String? {
        val doc = newBuilder().parse(ByteArrayInputStream(xml))
        val urls = doc.getElementsByTagNameNS("*", "Url")
        val all = (0 until urls.length).mapNotNull { urls.item(it) as? Element }
        val best = all.firstOrNull { it.getAttribute("type").contains("atom") } ?: all.firstOrNull() ?: return null
        val template = best.getAttribute("template").takeIf { it.isNotBlank() } ?: return null
        return resolve(baseUrl, template)
    }

    fun searchUrl(template: String, query: String): String =
        template.replace("{searchTerms}", URLEncoder.encode(query, "UTF-8").replace("+", "%20"))
            .replace(Regex("""\{[a-zA-Z:]+\?}"""), "")

    fun resolve(base: String, href: String): String = try {
        if (href.contains("{")) {
            // Templates não são URIs válidas; resolve só o começo.
            val idx = href.indexOf('{')
            resolve(base, href.substring(0, idx)) + href.substring(idx)
        } else URI(base).resolve(href.replace(" ", "%20")).toString()
    } catch (e: Exception) {
        href
    }

    private fun children(parent: Element, name: String): List<Element> {
        val out = mutableListOf<Element>()
        var n = parent.firstChild
        while (n != null) {
            if (n is Element && (n.localName ?: n.nodeName) == name && (n.namespaceURI == null || n.namespaceURI == ATOM)) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun text(parent: Element, name: String): String? =
        children(parent, name).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun links(parent: Element, base: String): List<OpdsLink> = children(parent, "link").mapNotNull { l ->
        val href = l.getAttribute("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        OpdsLink(
            href = resolve(base, href),
            rel = l.getAttribute("rel").takeIf { it.isNotBlank() },
            type = l.getAttribute("type").takeIf { it.isNotBlank() },
            title = l.getAttribute("title").takeIf { it.isNotBlank() },
        )
    }
}
