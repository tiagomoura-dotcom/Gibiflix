package app.gibiflix.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.importer.ImportOutcome
import app.gibiflix.importer.SourceSniffer
import app.gibiflix.core.util.ArchiveNames
import java.io.File

/**
 * Importa uma lista de URIs (arquivos ou uma pasta inteira). Roda com notificação de progresso.
 * O progresso também aparece na biblioteca via [ImportCenter].
 */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0, "")

    private fun foreground(done: Int, total: Int, name: String): ForegroundInfo {
        val n = Notifications.progress(
            applicationContext, Notifications.CHANNEL_WORK,
            applicationContext.getString(R.string.import_notification_title),
            if (total > 0) applicationContext.getString(R.string.import_notification_progress, done + 1, total, name) else "",
            done, total,
        )
        return ForegroundInfo(Notifications.ID_IMPORT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val center = c.importCenter
        val uris = inputData.getStringArray(KEY_URIS).orEmpty().map { Uri.parse(it) }.toMutableList()
        val deleteAfter = inputData.getBoolean(KEY_DELETE_AFTER, false)
        inputData.getString(KEY_FOLDER)?.let { folder ->
            DocumentFile.fromTreeUri(applicationContext, Uri.parse(folder))?.let { collect(it, uris) }
        }
        if (uris.isEmpty()) return Result.success()

        runCatching { setForeground(foreground(0, uris.size, "")) }
        val results = mutableListOf<ImportOutcome>()
        uris.forEachIndexed { i, uri ->
            val name = c.importer.displayName(uri)
            center.update(ImportCenter.Progress(i, uris.size, name, 0f))
            runCatching { setForeground(foreground(i, uris.size, name)) }
            val outcome = c.importer.import(uri, name) { f -> center.update(ImportCenter.Progress(i, uris.size, name, f)) }
            results += outcome
            if (outcome is ImportOutcome.Imported) {
                c.prepareScheduler.onImported(outcome.comic.id)
            }
            if (deleteAfter && uri.scheme == "file") runCatching { File(uri.path!!).delete() }
        }
        center.update(null)
        center.report(results)
        return Result.success(
            workDataOf(
                KEY_IMPORTED to results.count { it is ImportOutcome.Imported },
                KEY_DUPLICATES to results.count { it is ImportOutcome.Duplicate },
                KEY_FAILED to results.count { it is ImportOutcome.Failed },
            )
        )
    }

    private fun collect(dir: DocumentFile, out: MutableList<Uri>) {
        val children = dir.listFiles().sortedWith { a, b -> app.gibiflix.core.util.NaturalOrder.compare(a.name.orEmpty(), b.name.orEmpty()) }
        for (f in children) {
            if (f.isDirectory) collect(f, out)
            else if (ArchiveNames.extension(f.name.orEmpty()) in SourceSniffer.SUPPORTED_EXTENSIONS) out += f.uri
        }
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_FOLDER = "folder"
        const val KEY_DELETE_AFTER = "deleteAfter"
        const val KEY_IMPORTED = "imported"
        const val KEY_DUPLICATES = "duplicates"
        const val KEY_FAILED = "failed"
        const val TAG = "import"

        fun enqueueUris(context: Context, uris: List<Uri>, deleteAfter: Boolean = false) {
            // Data tem limite de 10 KB: divide em lotes.
            uris.chunked(25).forEach { chunk ->
                enqueue(context, workDataOf(KEY_URIS to chunk.map { it.toString() }.toTypedArray(), KEY_DELETE_AFTER to deleteAfter))
            }
        }

        fun enqueueFolder(context: Context, tree: Uri) {
            enqueue(context, workDataOf(KEY_FOLDER to tree.toString()))
        }

        private fun enqueue(context: Context, data: Data) {
            val req = OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(data)
                .addTag(TAG)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("import-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
