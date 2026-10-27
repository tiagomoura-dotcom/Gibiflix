package app.gibiflix.eink

import android.content.Context
import android.util.Log
import android.view.View
import app.gibiflix.device.DeviceProfileManager
import app.gibiflix.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Controle de refresh de telas e-ink. Toda chamada é segura em aparelhos comuns. */
interface EinkController {
    val name: String

    /** Pedido de "flash" (quadro preto) para a UI desenhar; usado pela limpeza genérica. */
    val flash: StateFlow<Boolean>

    fun attach(view: View?)

    /** Trocas entre refreshes completos (0 = nunca) e se a limpeza usa flash. */
    fun configure(fullRefreshEvery: Int, flashClean: Boolean)

    /** Avise a cada troca de conteúdo (página ou quadro). Faz o refresh completo a cada N trocas. */
    fun onContentChanged()

    fun fullRefresh()

    fun setReadingMode(on: Boolean)
}

/** Usado no tablet: não faz nada. */
class NoopEinkController : EinkController {
    override val name = "nenhum (tablet)"
    override val flash: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override fun attach(view: View?) = Unit
    override fun configure(fullRefreshEvery: Int, flashClean: Boolean) = Unit
    override fun onContentChanged() = Unit
    override fun fullRefresh() = Unit
    override fun setReadingMode(on: Boolean) = Unit
}

object EinkControllerFactory {
    fun create(
        context: Context,
        deviceProfile: DeviceProfileManager,
        settings: SettingsRepository,
        scope: CoroutineScope,
    ): EinkController {
        val generic = GenericEinkController(scope)
        val eink: EinkController = if (deviceProfile.isOnyx) {
            try {
                OnyxEinkController.tryCreate(context, scope) ?: generic
            } catch (t: Throwable) {
                Log.w("Gibiflix", "SDK da Onyx indisponível, usando o genérico", t)
                generic
            }
        } else generic
        return ProfileAwareEinkController(deviceProfile, eink)
    }
}

/** Delega ao controlador e-ink só quando o perfil em uso é e-ink. */
class ProfileAwareEinkController(
    private val deviceProfile: DeviceProfileManager,
    private val eink: EinkController,
) : EinkController {
    private val noop = NoopEinkController()
    private val current: EinkController get() = if (deviceProfile.profile.isEink) eink else noop
    override val name: String get() = current.name
    override val flash: StateFlow<Boolean> get() = eink.flash
    override fun attach(view: View?) = eink.attach(view)
    override fun configure(fullRefreshEvery: Int, flashClean: Boolean) = eink.configure(fullRefreshEvery, flashClean)
    override fun onContentChanged() = current.onContentChanged()
    override fun fullRefresh() = current.fullRefresh()
    override fun setReadingMode(on: Boolean) = current.setReadingMode(on)
}
