package app.gibiflix

import android.app.Application
import androidx.work.WorkManager
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.work.Notifications
import app.gibiflix.work.PrepareWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GibiflixApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        reconcilePreparing()
    }

    /** "Preparando" sem trabalho na fila (app foi fechado à força): volta ao estado real do pacote. */
    private fun reconcilePreparing() {
        val c = container
        c.appScope.launch {
            runCatching {
                val infos = WorkManager.getInstance(this@GibiflixApp).getWorkInfosByTagFlow("prepare").first()
                val active = infos.filter { !it.state.isFinished }.flatMap { it.tags }.toSet()
                c.db.comics().all().filter { it.status == PrepStatus.PREPARING && PrepareWorker.workName(it.id) !in active }.forEach { comic ->
                    c.packages.load(comic.id)?.let { c.packages.syncIndex(comic.id, it.doc) }
                }
            }
        }
    }
}
