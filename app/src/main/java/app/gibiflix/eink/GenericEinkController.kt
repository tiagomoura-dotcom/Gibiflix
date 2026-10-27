package app.gibiflix.eink

import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * E-ink sem SDK: o refresh completo é um "flash" (quadro preto por ~60 ms e depois a imagem),
 * que limpa os fantasmas na maioria das telas.
 */
open class GenericEinkController(protected val scope: CoroutineScope) : EinkController {
    override val name: String = "genérico"

    private val _flash = MutableStateFlow(false)
    override val flash: StateFlow<Boolean> = _flash.asStateFlow()

    private var viewRef: WeakReference<View>? = null
    private var changes = 0

    @Volatile var fullRefreshEvery: Int = 6
    @Volatile var flashEnabled: Boolean = true
    @Volatile var flashMillis: Long = 60

    protected val view: View? get() = viewRef?.get()

    override fun attach(view: View?) {
        viewRef = view?.let { WeakReference(it) }
        changes = 0
    }

    override fun configure(fullRefreshEvery: Int, flashClean: Boolean) {
        this.fullRefreshEvery = fullRefreshEvery
        this.flashEnabled = flashClean
    }

    override fun onContentChanged() {
        changes++
        if (fullRefreshEvery > 0 && changes >= fullRefreshEvery) {
            changes = 0
            fullRefresh()
        } else {
            partialRefresh()
        }
    }

    protected open fun partialRefresh() = Unit

    override fun fullRefresh() {
        if (!flashEnabled) {
            view?.postInvalidate()
            return
        }
        flashNow()
    }

    protected fun flashNow() {
        scope.launch(Dispatchers.Main) {
            _flash.value = true
            delay(flashMillis)
            _flash.value = false
        }
    }

    override fun setReadingMode(on: Boolean) = Unit
}
