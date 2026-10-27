package app.gibiflix.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.container
import app.gibiflix.ui.theme.GibiflixTheme

/**
 * Leitor. As teclas são tratadas aqui, no nível da Activity, e consumidas: o volume do aparelho não
 * muda e o aviso de volume não aparece. Fora do leitor os botões voltam a controlar o volume.
 */
class ReaderActivity : ComponentActivity() {
    private val vm: ReaderViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    /** Tecla segurada: dispara "página" depois de 600 ms e ignora o soltar. */
    private var heldKey = -1
    private var holdFired = false
    private val holdRunnable = Runnable {
        holdFired = true
        when (direction(heldKey)) {
            1 -> vm.nextPage()
            -1 -> vm.prevPage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deviceProfile = container.deviceProfile
        immersive()
        setContent {
            val state by deviceProfile.state.collectAsStateWithLifecycle()
            GibiflixTheme(state.profile) {
                ReaderScreen(vm, onExit = { finish() })
            }
        }
        container.eink.attach(window.decorView)
    }

    override fun onStart() {
        super.onStart()
        vm.startSession()
        container.eink.attach(window.decorView)
        container.eink.setReadingMode(true)
        if (vm.readerSettings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        vm.endSession()
        container.eink.setReadingMode(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(holdRunnable)
        container.eink.attach(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** +1 próximo, −1 anterior, 0 não é tecla do leitor. */
    private fun direction(keyCode: Int): Int {
        val s = vm.readerSettings
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (!s.useVolumeKeys) 0 else if (s.swapVolumeKeys) -1 else 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (!s.useVolumeKeys) 0 else if (s.swapVolumeKeys) 1 else -1
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SPACE -> 1
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> -1
            else -> 0
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dir = direction(event.keyCode)
        if (dir == 0) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    heldKey = event.keyCode
                    holdFired = false
                    if (vm.readerSettings.holdToTurnPage) handler.postDelayed(holdRunnable, HOLD_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(holdRunnable)
                if (!holdFired && heldKey == event.keyCode) {
                    if (dir > 0) vm.next() else vm.prev()
                }
                heldKey = -1
                holdFired = false
            }
        }
        return true
    }

    companion object {
        const val EXTRA_COMIC_ID = "comicId"
        const val EXTRA_PAGE = "page"
        const val EXTRA_PANEL = "panel"
        private const val HOLD_MS = 600L

        fun intent(context: Context, comicId: String, page: Int = -1, panel: Int = -1): Intent =
            Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_COMIC_ID, comicId)
                .putExtra(EXTRA_PAGE, page)
                .putExtra(EXTRA_PANEL, panel)
    }
}
