package app.gibiflix

import android.content.Context
import app.gibiflix.device.DeviceProfileManager
import app.gibiflix.eink.EinkControllerFactory
import app.gibiflix.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Dependências do app (injeção manual). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(appContext)
    val deviceProfile = DeviceProfileManager(appContext, settings, appScope)
    val eink = EinkControllerFactory.create(appContext, deviceProfile, settings, appScope)
}

val Context.container: AppContainer get() = (applicationContext as GibiflixApp).container
