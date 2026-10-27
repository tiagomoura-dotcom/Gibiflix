package app.gibiflix.share

import app.gibiflix.core.sync.BookmarkRecord
import app.gibiflix.core.sync.ComicSummary
import app.gibiflix.core.sync.DeviceInfo
import app.gibiflix.core.sync.PairRequest
import app.gibiflix.core.sync.PairResponse
import app.gibiflix.core.sync.ProgressRecord
import app.gibiflix.core.sync.SyncJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Cliente da API /api/v1 de outro aparelho Gibiflix. */
class ShareClient(host: String, port: Int) {
    private val base = "http://$host:$port/api/v1"
    var token: String? = null

    private fun open(path: String, method: String = "GET"): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 30000
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

    private suspend fun <T> get(path: String, s: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val c = open(path)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            SyncJson.json.decodeFromString(s, c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally { c.disconnect() }
    }

    private suspend fun <A, T> post(path: String, body: A, bs: KSerializer<A>, rs: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val c = open(path, "POST")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(SyncJson.json.encodeToString(bs, body).toByteArray()) }
            if (c.responseCode == 403) throw WrongCodeException()
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            SyncJson.json.decodeFromString(rs, c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally { c.disconnect() }
    }

    suspend fun info(): DeviceInfo = get("/info", DeviceInfo.serializer())

    suspend fun pair(code: String) {
        token = post("/pair", PairRequest(code), PairRequest.serializer(), PairResponse.serializer()).token
    }

    suspend fun comics(): List<ComicSummary> = get("/comics", ListSerializer(ComicSummary.serializer()))
    suspend fun progress(): List<ProgressRecord> = get("/progress", ListSerializer(ProgressRecord.serializer()))
    suspend fun bookmarks(): List<BookmarkRecord> = get("/bookmarks", ListSerializer(BookmarkRecord.serializer()))

    suspend fun sendProgress(list: List<ProgressRecord>): List<ProgressRecord> =
        post("/progress", list, ListSerializer(ProgressRecord.serializer()), ListSerializer(ProgressRecord.serializer()))

    suspend fun sendBookmarks(list: List<BookmarkRecord>): List<BookmarkRecord> =
        post("/bookmarks", list, ListSerializer(BookmarkRecord.serializer()), ListSerializer(BookmarkRecord.serializer()))

    /** Baixa o pacote para [out], informando o progresso (0..1). */
    suspend fun download(id: String, out: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val c = open("/comics/$id/package")
        c.readTimeout = 60000
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: -1L
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
            if (total > 0 && done != total) throw IOException("download incompleto")
        } finally { c.disconnect() }
    }
}

class WrongCodeException : IOException("código errado")
