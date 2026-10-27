package app.gibiflix.device

import android.content.Context
import android.os.Build
import android.view.WindowManager
import app.gibiflix.core.device.BuildInfo
import app.gibiflix.core.device.Profile
import app.gibiflix.core.device.ProfileChoice
import app.gibiflix.core.device.ProfileDetector
import app.gibiflix.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Único lugar que olha `Build.*` para decidir o perfil (TABLET, EINK_COLOR, EINK_MONO).
 */
class DeviceProfileManager(
    private val context: Context,
    settings: SettingsRepository,
    scope: CoroutineScope,
) {
    val buildInfo = BuildInfo(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        product = Build.PRODUCT.orEmpty(),
    )
    val isOnyx: Boolean = ProfileDetector.isOnyx(buildInfo)
    val detected: Profile = ProfileDetector.detect(buildInfo)

    data class State(
        val profile: Profile,
        val choice: ProfileChoice,
        val needsOnyxConfirmation: Boolean,
        val loaded: Boolean,
    )

    val state: StateFlow<State> = settings.settings
        .map { s ->
            State(
                profile = ProfileDetector.resolve(detected, s.profileChoice, s.confirmedProfile),
                choice = s.profileChoice,
                needsOnyxConfirmation = isOnyx && s.confirmedProfile == null && s.profileChoice == ProfileChoice.AUTO,
                loaded = true,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, State(detected, ProfileChoice.AUTO, false, loaded = false))

    val profile: Profile get() = state.value.profile

    /** Nome amigável do aparelho para a faixa da biblioteca. */
    val displayName: String
        get() {
            val model = buildInfo.model.trim()
            return if (isOnyx && !model.contains("boox", ignoreCase = true)) "Boox $model" else model
        }

    fun diagnostics(appVersion: String, einkName: String): List<Pair<String, String>> {
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = wm.maximumWindowMetrics.bounds
        val dm = context.resources.displayMetrics
        return listOf(
            "MANUFACTURER" to Build.MANUFACTURER.orEmpty(),
            "BRAND" to Build.BRAND.orEmpty(),
            "MODEL" to Build.MODEL.orEmpty(),
            "DEVICE" to Build.DEVICE.orEmpty(),
            "PRODUCT" to Build.PRODUCT.orEmpty(),
            "HARDWARE" to Build.HARDWARE.orEmpty(),
            "DISPLAY" to Build.DISPLAY.orEmpty(),
            "SDK_INT" to Build.VERSION.SDK_INT.toString(),
            "RELEASE" to Build.VERSION.RELEASE.orEmpty(),
            "resolution" to "${bounds.width()} x ${bounds.height()} px",
            "density" to "${dm.densityDpi} dpi (${dm.density}x)",
            "detected" to detected.name,
            "inUse" to profile.name,
            "choice" to state.value.choice.name,
            "eink" to einkName,
            "app" to appVersion,
        )
    }
}
