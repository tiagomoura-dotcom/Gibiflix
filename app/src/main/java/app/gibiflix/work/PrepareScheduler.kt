package app.gibiflix.work

import android.content.Context
import app.gibiflix.container
import app.gibiflix.core.settings.PrepareOnImport
import app.gibiflix.data.db.PrepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Decide o que fazer depois de importar: preparar já (tablet), perguntar (e-ink) ou nada. */
class PrepareScheduler(private val context: Context) {
    private val _asking = MutableStateFlow<List<String>>(emptyList())

    /** Quadrinhos importados esperando a resposta "Preparar agora?". */
    val asking: StateFlow<List<String>> = _asking.asStateFlow()

    suspend fun onImported(comicId: String) {
        val c = context.container
        val comic = c.db.comics().get(comicId) ?: return
        if (comic.status != PrepStatus.NEW) return
        val setting = c.settings.current().user.forProfile(c.deviceProfile.profile).prepareOnImport
        when (setting) {
            PrepareOnImport.YES -> prepare(comicId)
            PrepareOnImport.ASK -> _asking.update { it + comicId }
            PrepareOnImport.NO -> Unit
        }
    }

    suspend fun prepare(comicId: String, redoAll: Boolean = false) {
        context.container.db.comics().setStatus(comicId, PrepStatus.PREPARING, 0)
        PrepareWorker.enqueue(context, comicId, redoAll)
    }

    suspend fun answer(prepare: Boolean) {
        val ids = _asking.value
        _asking.value = emptyList()
        if (prepare) ids.forEach { prepare(it) }
    }
}
