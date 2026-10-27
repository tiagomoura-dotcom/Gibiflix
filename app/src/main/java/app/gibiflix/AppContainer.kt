package app.gibiflix

import android.content.Context
import app.gibiflix.backup.BackupManager
import app.gibiflix.data.db.GibiflixDatabase
import app.gibiflix.data.files.LibraryFiles
import app.gibiflix.data.repo.LibraryRepository
import app.gibiflix.data.repo.PackageStore
import app.gibiflix.device.DeviceProfileManager
import app.gibiflix.eink.EinkControllerFactory
import app.gibiflix.importer.ComicImporter
import app.gibiflix.settings.SettingsRepository
import app.gibiflix.share.SyncRepository
import app.gibiflix.work.ImportCenter
import app.gibiflix.work.PrepareScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dependências do app (injeção manual). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(appContext)
    val deviceProfile = DeviceProfileManager(appContext, settings, appScope)
    val eink = EinkControllerFactory.create(appContext, deviceProfile, settings, appScope)

    val db: GibiflixDatabase by lazy { GibiflixDatabase.create(appContext) }
    val files = LibraryFiles(appContext)
    val packages: PackageStore by lazy { PackageStore(files, db.comics()) }
    val library: LibraryRepository by lazy { LibraryRepository(db, files, packages) }
    val importer: ComicImporter by lazy { ComicImporter(appContext, files, db.comics(), packages, settings, deviceProfile) }
    val importCenter = ImportCenter(appContext)
    val sync: SyncRepository by lazy { SyncRepository(appContext, db, files, settings) }
    val backup: BackupManager by lazy { BackupManager(appContext, this) }
    val prepareScheduler: PrepareScheduler by lazy { PrepareScheduler(appContext) }
}

val Context.container: AppContainer get() = (applicationContext as GibiflixApp).container
