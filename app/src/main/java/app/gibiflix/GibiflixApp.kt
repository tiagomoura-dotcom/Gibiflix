package app.gibiflix

import android.app.Application
import app.gibiflix.work.Notifications

class GibiflixApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
