package app.gibiflix.eink

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.Method

/**
 * Refresh pelo SDK da Onyx (onyxsdk-device), chamado por reflexão: se a classe ou o método não
 * existir no aparelho, cada chamada cai no comportamento genérico sem travar o app.
 *
 * - Leitura: modo rápido de atualização parcial (UpdateMode.DU ou GU).
 * - Refresh completo: EpdController.invalidate(view, UpdateMode.GC).
 */
class OnyxEinkController private constructor(
    scope: CoroutineScope,
    private val epd: Class<*>,
    private val updateMode: Class<*>,
) : GenericEinkController(scope) {
    override val name: String = "Onyx (SDK)"

    private val invalidate: Method? = runCatching { epd.getMethod("invalidate", View::class.java, updateMode) }.getOrNull()
    private val setDefaultMode: Method? = runCatching { epd.getMethod("setViewDefaultUpdateMode", View::class.java, updateMode) }.getOrNull()
    private val resetDefaultMode: Method? = runCatching { epd.getMethod("resetViewUpdateMode", View::class.java) }.getOrNull()

    private fun mode(name: String): Any? = runCatching {
        updateMode.enumConstants?.firstOrNull { (it as? Enum<*>)?.name == name }
    }.getOrNull()

    override fun partialRefresh() {
        val v = view ?: return
        val m = mode("GU") ?: return
        safely { invalidate?.invoke(null, v, m) }
    }

    override fun fullRefresh() {
        val v = view
        val gc = mode("GC")
        if (v != null && gc != null && invalidate != null) {
            // Espera o quadro novo ser desenhado antes do refresh completo.
            scope.launch(Dispatchers.Main) {
                delay(16)
                if (!safely { invalidate.invoke(null, v, gc) } && flashEnabled) flashNow()
            }
        } else {
            super.fullRefresh()
        }
    }

    override fun setReadingMode(on: Boolean) {
        val v = view ?: return
        if (on) {
            val m = mode("REGAL") ?: mode("GU") ?: return
            safely { setDefaultMode?.invoke(null, v, m) }
        } else {
            safely { resetDefaultMode?.invoke(null, v) }
        }
    }

    private inline fun safely(block: () -> Unit): Boolean = try {
        block(); true
    } catch (t: Throwable) {
        Log.w("Gibiflix", "Onyx SDK: ${t.message}")
        false
    }

    companion object {
        fun tryCreate(context: Context, scope: CoroutineScope): OnyxEinkController? {
            if (Build.VERSION.SDK_INT >= 28) {
                // O SDK usa APIs ocultas do sistema da Onyx.
                runCatching {
                    Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass")
                        .getMethod("addHiddenApiExemptions", Array<String>::class.java)
                        .invoke(null, arrayOf(""))
                }
            }
            val epd = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val mode = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")
            return OnyxEinkController(scope, epd, mode).takeIf { it.invalidate != null }
        }
    }
}
