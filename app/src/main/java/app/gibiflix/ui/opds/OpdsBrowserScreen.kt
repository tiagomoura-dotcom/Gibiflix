package app.gibiflix.ui.opds

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.opds.OpdsEntry
import app.gibiflix.core.opds.OpdsParser
import app.gibiflix.importer.ImportOutcome
import app.gibiflix.opds.OpdsClient
import app.gibiflix.ui.components.TopBar
import app.gibiflix.ui.theme.LocalProfile
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class OpdsPage(val url: String, val title: String, val entries: List<OpdsEntry>, val next: String?)

data class OpdsUi(
    val serverName: String = "",
    val stack: List<OpdsPage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val searchTemplate: String? = null,
    val downloads: Map<String, Float> = emptyMap(),
    val done: Set<String> = emptySet(),
    val message: String? = null,
)

class OpdsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val _ui = MutableStateFlow(OpdsUi())
    val ui: StateFlow<OpdsUi> = _ui.asStateFlow()
    var client: OpdsClient? = null
        private set
    private var serverId = -1L

    fun open(id: Long) {
        if (id == serverId) return
        serverId = id
        viewModelScope.launch {
            val s = c.db.servers().get(id) ?: return@launch
            client = OpdsClient(s)
            _ui.update { it.copy(serverName = s.name) }
            navigate(s.url, s.name, root = true)
        }
    }

    fun navigate(url: String, title: String, root: Boolean = false) {
        val cl = client ?: return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val feed = cl.feed(url)
                val page = OpdsPage(url, feed.title.ifBlank { title }, feed.entries, feed.next?.href)
                if (root) {
                    val tpl = runCatching { cl.searchTemplate(feed) }.getOrNull()
                    _ui.update { it.copy(searchTemplate = tpl) }
                }
                _ui.update { it.copy(loading = false, stack = it.stack + page) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun loadMore() {
        val cl = client ?: return
        val top = _ui.value.stack.lastOrNull() ?: return
        val next = top.next ?: return
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val feed = cl.feed(next)
                val merged = top.copy(entries = top.entries + feed.entries, next = feed.next?.href)
                _ui.update { it.copy(loading = false, stack = it.stack.dropLast(1) + merged) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun search(q: String) {
        val tpl = _ui.value.searchTemplate ?: return
        if (q.isBlank()) return
        navigate(OpdsParser.searchUrl(tpl, q.trim()), "“${q.trim()}”")
    }

    fun back(): Boolean {
        if (_ui.value.stack.size <= 1) return false
        _ui.update { it.copy(stack = it.stack.dropLast(1), error = null) }
        return true
    }

    /** Baixar → importar → preparar (conforme o perfil). */
    fun download(e: OpdsEntry) {
        val cl = client ?: return
        val link = e.acquisition ?: return
        if (e.id in _ui.value.downloads) return
        _ui.update { it.copy(downloads = it.downloads + (e.id to 0f), message = null) }
        viewModelScope.launch {
            val dir = c.files.newWorkDir("download")
            try {
                val f = File(dir, "book")
                val suggested = cl.download(link.href, f) { p -> _ui.update { it.copy(downloads = it.downloads + (e.id to p)) } }
                val ext = when {
                    link.type?.contains("pdf") == true -> "pdf"
                    link.type?.contains("rar") == true || link.type?.contains("cbr") == true -> "cbr"
                    link.type?.contains("7z") == true || link.type?.contains("cb7") == true -> "cb7"
                    else -> "cbz"
                }
                val name = suggested ?: link.title?.takeIf { it.contains('.') } ?: "${e.title}.$ext"
                val msg = when (val r = c.importer.importFile(f, name)) {
                    is ImportOutcome.Imported -> {
                        c.prepareScheduler.onImported(r.comic.id)
                        _ui.update { it.copy(done = it.done + e.id) }
                        getApplication<Application>().getString(R.string.opds_imported, e.title)
                    }
                    is ImportOutcome.Duplicate -> getApplication<Application>().getString(R.string.import_duplicate, e.title, r.existing.title)
                    is ImportOutcome.Failed -> getApplication<Application>().getString(R.string.import_failed, e.title, r.reason)
                }
                _ui.update { it.copy(message = msg) }
            } catch (ex: Exception) {
                _ui.update { it.copy(message = getApplication<Application>().getString(R.string.import_failed, e.title, ex.message ?: "")) }
            } finally {
                dir.deleteRecursively()
                _ui.update { it.copy(downloads = it.downloads - e.id) }
            }
        }
    }
}

@Composable
fun OpdsBrowserScreen(serverId: Long, onBack: () -> Unit, vm: OpdsViewModel = viewModel(key = "opds-$serverId")) {
    LaunchedEffect(serverId) { vm.open(serverId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    BackHandler { if (!vm.back()) onBack() }
    val top = ui.stack.lastOrNull()
    Column(Modifier.fillMaxSize()) {
        TopBar(top?.title ?: ui.serverName, onBack = { if (!vm.back()) onBack() }) {
            if (ui.searchTemplate != null) IconButton(onClick = { searching = !searching }) { Icon(Icons.Outlined.Search, stringResource(R.string.library_search)) }
        }
        if (searching) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.opds_search_hint)) })
                TextButton(onClick = { vm.search(query); searching = false }) { Text(stringResource(R.string.library_search)) }
            }
        }
        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { Text(stringResource(R.string.opds_error, it), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        ui.message?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(top?.entries.orEmpty(), key = { it.id + it.title }) { e -> EntryRow(vm, ui, e) }
            if (top?.next != null) item {
                OutlinedButton(onClick = { vm.loadMore() }, enabled = !ui.loading, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.opds_more))
                }
            }
        }
    }
}

@Composable
private fun EntryRow(vm: OpdsViewModel, ui: OpdsUi, e: OpdsEntry) {
    val context = LocalContext.current
    val nav = e.navigation
    val acq = e.acquisition
    val eink = LocalProfile.current.isEink
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = nav != null) { nav?.let { vm.navigate(it.href, e.title) } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = e.cover
        if (cover != null) {
            Box(Modifier.width(56.dp).aspectRatio(2f / 3f).background(MaterialTheme.colorScheme.surfaceContainer)) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(cover.href).apply {
                        vm.client?.authHeader?.let { addHeader("Authorization", it) }
                    }.crossfade(!eink).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.authors.isNotEmpty()) Text(e.authors.joinToString(", "), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            e.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            ui.downloads[e.id]?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), drawStopIndicator = {}) }
        }
        if (acq != null) {
            val done = e.id in ui.done
            Surface(
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                color = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.clickable(enabled = !done && e.id !in ui.downloads) { vm.download(e) },
            ) {
                Text(
                    stringResource(if (done) R.string.opds_downloaded else R.string.opds_download),
                    color = if (done) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
