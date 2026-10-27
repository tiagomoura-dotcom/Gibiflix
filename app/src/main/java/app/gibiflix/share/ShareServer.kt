package app.gibiflix.share

import app.gibiflix.BuildConfig
import app.gibiflix.core.sync.BookmarkRecord
import app.gibiflix.core.sync.DeviceInfo
import app.gibiflix.core.sync.PairRequest
import app.gibiflix.core.sync.PairResponse
import app.gibiflix.core.sync.ProgressRecord
import app.gibiflix.core.sync.SyncJson
import app.gibiflix.data.files.LibraryFiles
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Servidor HTTP da rede local (§9): /api/v1 com pareamento por código de 6 dígitos e token.
 */
class ShareServer(
    private val sync: SyncRepository,
    private val files: LibraryFiles,
    private val info: () -> DeviceInfo,
    private val onEvent: (String) -> Unit,
) {
    private val random = SecureRandom()
    val code: String = "%06d".format(random.nextInt(1_000_000))
    private val tokens = ConcurrentHashMap.newKeySet<String>()
    private var server: EmbeddedServer<*, *>? = null

    suspend fun start(): Int {
        val s = embeddedServer(CIO, port = 0, host = "0.0.0.0") {
            routing {
                route("/api/v1") {
                    get("/info") { call.json(DeviceInfo.serializer(), info()) }
                    post("/pair") {
                        val req = runCatching { SyncJson.json.decodeFromString(PairRequest.serializer(), call.receiveText()) }.getOrNull()
                        if (req?.code?.trim() != code) {
                            call.respondText("código errado", status = HttpStatusCode.Forbidden)
                            return@post
                        }
                        val token = ByteArray(24).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
                        tokens += token
                        onEvent("pair")
                        call.json(PairResponse.serializer(), PairResponse(token))
                    }
                    get("/comics") {
                        if (!call.authorized()) return@get
                        call.json(ListSerializer(app.gibiflix.core.sync.ComicSummary.serializer()), sync.summaries())
                    }
                    get("/comics/{id}/package") {
                        if (!call.authorized()) return@get
                        val id = call.parameters["id"].orEmpty()
                        val f = files.packageFile(id)
                        if (id.isEmpty() || id.contains('/') || !f.exists()) {
                            call.respondText("não encontrado", status = HttpStatusCode.NotFound)
                            return@get
                        }
                        onEvent("download")
                        call.respondOutputStream(ContentType.parse("application/vnd.comicbook+zip"), HttpStatusCode.OK, f.length()) {
                            f.inputStream().use { it.copyTo(this, 256 * 1024) }
                        }
                    }
                    get("/progress") {
                        if (!call.authorized()) return@get
                        call.json(ListSerializer(ProgressRecord.serializer()), sync.progress())
                    }
                    post("/progress") {
                        if (!call.authorized()) return@post
                        val list = SyncJson.json.decodeFromString(ListSerializer(ProgressRecord.serializer()), call.receiveText())
                        sync.mergeProgress(list)
                        onEvent("progress")
                        call.json(ListSerializer(ProgressRecord.serializer()), sync.progress())
                    }
                    get("/bookmarks") {
                        if (!call.authorized()) return@get
                        call.json(ListSerializer(BookmarkRecord.serializer()), sync.bookmarks())
                    }
                    post("/bookmarks") {
                        if (!call.authorized()) return@post
                        val list = SyncJson.json.decodeFromString(ListSerializer(BookmarkRecord.serializer()), call.receiveText())
                        sync.mergeBookmarks(list)
                        call.json(ListSerializer(BookmarkRecord.serializer()), sync.bookmarks())
                    }
                }
            }
        }
        s.startSuspend(wait = false)
        server = s
        return s.engine.resolvedConnectors().first().port
    }

    fun stop() {
        runCatching { server?.stop(500, 1500) }
        server = null
    }

    private suspend fun ApplicationCall.authorized(): Boolean {
        val h = request.headers["Authorization"].orEmpty()
        val ok = h.startsWith("Bearer ") && h.removePrefix("Bearer ").trim() in tokens
        if (!ok) respondText("não autorizado", status = HttpStatusCode.Unauthorized)
        return ok
    }

    private suspend fun <T> ApplicationCall.json(serializer: KSerializer<T>, value: T) =
        respondText(SyncJson.json.encodeToString(serializer, value), ContentType.Application.Json)

    companion object {
        const val SERVICE_TYPE = "_gibiflix._tcp."
        fun appVersion() = BuildConfig.VERSION_NAME
    }
}
