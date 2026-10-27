package app.gibiflix.work

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Decide o que fazer depois de importar (preparar já, perguntar ou nada). Completa na Fase 3. */
class PrepareScheduler(private val context: Context) {
    private val _asking = MutableStateFlow<List<String>>(emptyList())

    /** Quadrinhos importados esperando a resposta "Preparar agora?". */
    val asking: StateFlow<List<String>> = _asking.asStateFlow()

    suspend fun onImported(comicId: String) = Unit

    fun clearAsking() { _asking.value = emptyList() }
}
