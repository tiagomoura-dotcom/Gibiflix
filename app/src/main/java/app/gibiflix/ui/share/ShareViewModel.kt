package app.gibiflix.ui.share

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.sync.ComicSummary
import app.gibiflix.core.sync.RemoteState
import app.gibiflix.core.sync.SyncRules
import app.gibiflix.importer.ImportOutcome
import app.gibiflix.share.Discovery
import app.gibiflix.share.FoundDevice
import app.gibiflix.share.ShareClient
import app.gibiflix.share.WrongCodeException
import app.gibiflix.work.ImportWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ShareMode { HOME, SEND, RECEIVE }

data class RemoteItem(val summary: ComicSummary, val state: RemoteState, val selected: Boolean, val progress: Float? = null, val done: Boolean = false, val error: String? = null)

data class ShareUi(
    val mode: ShareMode = ShareMode.HOME,
    val devices: List<FoundDevice> = emptyList(),
    val device: FoundDevice? = null,
    val askCode: Boolean = false,
    val connecting: Boolean = false,
    val items: List<RemoteItem> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val exportProgress: Pair<Int, Int>? = null,
)

class ShareViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val _ui = MutableStateFlow(ShareUi())
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()
    private var client: ShareClient? = null
    private var discoveryJob: Job? = null
    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    fun setMode(m: ShareMode) {
        _ui.update { it.copy(mode = m, message = null) }
        if (m == ShareMode.RECEIVE) startDiscovery() else stopDiscovery()
    }

    private fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            Discovery.devices(getApplication()).collect { list -> _ui.update { it.copy(devices = list) } }
        }
    }

    fun stopDiscovery() { discoveryJob?.cancel(); discoveryJob = null }

    fun chooseDevice(d: FoundDevice) = _ui.update { it.copy(device = d, askCode = true, message = null) }
    fun manualDevice(host: String, port: Int) = chooseDevice(FoundDevice(host, host.trim(), port))
    fun cancelCode() = _ui.update { it.copy(askCode = false) }

    fun pair(code: String) {
        val d = _ui.value.device ?: return
        _ui.update { it.copy(connecting = true, askCode = false) }
        viewModelScope.launch {
            try {
                val cl = ShareClient(d.host, d.port)
                cl.pair(code)
                client = cl
                refreshList()
            } catch (e: WrongCodeException) {
                _ui.update { it.copy(message = str(R.string.share_wrong_code), askCode = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(message = str(R.string.share_connect_failed, e.message ?: "")) }
            } finally {
                _ui.update { it.copy(connecting = false) }
            }
        }
    }

    private suspend fun refreshList() {
        val cl = client ?: return
        val remote = cl.comics()
        val local = c.db.comics().all()
        val revs = local.associate { it.id to it.revision }
        val hashes = local.map { it.sourceHash }.toSet()
        val items = remote.map { s ->
            val st = SyncRules.classify(s, revs, hashes)
            RemoteItem(s, st, selected = st != RemoteState.HAVE)
        }.sortedWith(compareBy<RemoteItem> { it.state.ordinal }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.summary.title })
        _ui.update { it.copy(items = items) }
    }

    fun toggle(id: String) = _ui.update { s -> s.copy(items = s.items.map { if (it.summary.id == id) it.copy(selected = !it.selected) else it }) }

    fun downloadSelected() {
        val cl = client ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            var ok = 0
            for (item in _ui.value.items.filter { it.selected && !it.done }) {
                val id = item.summary.id
                setItem(id) { it.copy(progress = 0f, error = null) }
                val dir = c.files.newWorkDir("download")
                try {
                    val f = File(dir, "$id.cbz")
                    cl.download(id, f) { p -> setItem(id) { it.copy(progress = p) } }
                    when (val r = c.importer.importFile(f, item.summary.title + ".cbz")) {
                        is ImportOutcome.Imported -> { ok++; setItem(id) { it.copy(done = true, progress = 1f, selected = false) } }
                        is ImportOutcome.Duplicate -> setItem(id) { it.copy(done = true, selected = false, error = str(R.string.share_already_have)) }
                        is ImportOutcome.Failed -> setItem(id) { it.copy(error = r.reason, progress = null) }
                    }
                } catch (e: Exception) {
                    setItem(id) { it.copy(error = e.message ?: "erro", progress = null) }
                } finally {
                    dir.deleteRecursively()
                }
            }
            val synced = runCatching { syncNow(cl) }.getOrNull()
            runCatching { refreshList() }
            _ui.update {
                it.copy(busy = false, message = str(R.string.share_download_done, ok) + (synced?.let { n -> " " + str(R.string.share_synced, n) } ?: ""))
            }
        }
    }

    /** Só progresso e marcadores, nos dois sentidos. */
    fun syncProgress() {
        val cl = client ?: return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val msg = try { str(R.string.share_synced, syncNow(cl)) } catch (e: Exception) { str(R.string.share_connect_failed, e.message ?: "") }
            _ui.update { it.copy(busy = false, message = msg) }
        }
    }

    private suspend fun syncNow(cl: ShareClient): Int {
        val sync = c.sync
        var changed = sync.mergeProgress(cl.progress())
        changed += sync.mergeBookmarks(cl.bookmarks())
        cl.sendProgress(sync.progress())
        cl.sendBookmarks(sync.bookmarks())
        return changed
    }

    private fun setItem(id: String, f: (RemoteItem) -> RemoteItem) =
        _ui.update { s -> s.copy(items = s.items.map { if (it.summary.id == id) f(it) else it }) }

    fun disconnect() {
        client = null
        _ui.update { it.copy(items = emptyList(), device = null) }
    }

    fun exportToFolder(tree: Uri, ids: List<String>) {
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val msg = try {
                val n = c.sync.exportToFolder(tree, ids) { done, total -> _ui.update { it.copy(exportProgress = done to total) } }
                str(R.string.share_exported, n)
            } catch (e: Exception) { e.message ?: "erro" }
            _ui.update { it.copy(busy = false, exportProgress = null, message = msg) }
        }
    }

    fun importFromFolder(tree: Uri) {
        ImportWorker.enqueueFolder(getApplication(), tree)
        _ui.update { it.copy(message = str(R.string.share_import_folder_started)) }
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}
