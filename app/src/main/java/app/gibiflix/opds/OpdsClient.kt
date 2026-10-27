package app.gibiflix.opds

import android.util.Base64
import app.gibiflix.core.opds.OpdsFeed
import app.gibiflix.core.opds.OpdsParser
import app.gibiflix.data.db.ServerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Acesso a um catálogo OPDS com Basic Auth opcional. */
class OpdsClient(val server: ServerEntity) {
    val authHeader: String? = server.username?.takeIf { it.isNotBlank() }?.let { user ->
        val pass = server.passwordEnc?.let { SecretBox.decrypt(it) }.orEmpty()
        "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(5) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 60000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/atom+xml, application/xml, */*")
                setRequestProperty("User-Agent", "Gibiflix")
                authHeader?.let { setRequestProperty("Authorization", it) }
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("redirecionamento sem destino")
                c.disconnect()
                current = OpdsParser.resolve(current, loc)
            } else {
                if (code == 401) throw IOException("usuário ou senha incorretos (401)")
                if (code !in 200..299) throw IOException("HTTP $code")
                return c
            }
        }
        throw IOException("redirecionamentos demais")
    }

    suspend fun bytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val c = open(url)
        try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
    }

    suspend fun feed(url: String): OpdsFeed {
        val b = bytes(url)
        return withContext(Dispatchers.Default) { OpdsParser.parseFeed(b, url) }
    }

    /** Template de busca: link de OpenSearch ou template direto no feed. */
    suspend fun searchTemplate(feed: OpdsFeed): String? {
        val link = feed.searchLink ?: return null
        if (link.href.contains("{searchTerms}")) return link.href
        if (link.type?.contains("opensearchdescription") == true) {
            return runCatching { OpdsParser.parseOpenSearch(bytes(link.href), link.href) }.getOrNull()
        }
        return null
    }

    /** Baixa para [out]; devolve o nome do arquivo sugerido pelo servidor, se houver. */
    suspend fun download(url: String, out: File, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        val c = open(url)
        try {
            val total = c.contentLengthLong
            val name = c.getHeaderField("Content-Disposition")?.let {
                Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""").find(it)?.groupValues?.get(1)
            }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().buffered(256 * 1024).use { o ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            name
        } finally { c.disconnect() }
    }
}
